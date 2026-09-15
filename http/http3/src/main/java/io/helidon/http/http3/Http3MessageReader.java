/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.http.http3;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import io.helidon.common.Api;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.SocketContext;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.quic.stream.QuicReceiverStream;

import static io.helidon.common.buffers.BufferData.EMPTY_BYTES;

/**
 * Stateful inbound HTTP/3 request or response message reader.
 * <p>
 * One reader owns the QPACK lifecycle and framing state of one request stream. Transport data remains in QUIC until a
 * message consumer asks for the next field section or entity bytes.
 */
@Api.Internal
public final class Http3MessageReader implements AutoCloseable {
    private static final Runnable NO_OP = () -> { };
    private static final int DEFAULT_CHUNK_SIZE = 8 * 1024;
    private static final int MAX_FRAME_HEADER_SIZE = 16;
    private static final int MAX_RAW_DATA_CHUNK_SIZE = 8 * 1024;
    private static final Set<String> CONNECTION_SPECIFIC_HEADERS = Set.of("connection",
                                                                          "keep-alive",
                                                                          "proxy-connection",
                                                                          "transfer-encoding",
                                                                          "upgrade");

    private final Http3StreamSupport.StreamInput input;
    private final Http3QpackContext.Stream qpackStream;
    private final long streamId;
    private final SocketContext context;
    private final MessageType messageType;
    private final Method requestMethod;
    private final int maxHeadersSize;
    private final int encodedFieldSectionLimit;
    private final Http3FrameListener frameListener;
    private final ReadOptions readOptions;
    private final byte[] encodedFrameHeader = new byte[MAX_FRAME_HEADER_SIZE];
    private final AtomicBoolean closed = new AtomicBoolean();

    private long currentFrameType = -1;
    private long currentFrameLength;
    private long entityBytesRead;
    private BufferData outstandingEntityBuffer;
    private boolean entityInputStreamRequested;
    private boolean entityAllowed = true;
    private boolean trailersAllowed = true;
    private boolean tunnel;
    private OptionalLong contentLength = OptionalLong.empty();
    private Headers trailers = WritableHeaders.create();
    private Phase phase = Phase.INITIAL_HEADERS;
    private Http3Protocol.DecodedRequestHead requestHead;
    private ResponseHead responseHead;

    private Http3MessageReader(QuicReceiverStream stream,
                               Http3QpackContext qpackContext,
                               SocketContext context,
                               MessageType messageType,
                               Method requestMethod,
                               int maxHeadersSize,
                               ReadOptions readOptions) {
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(messageType, "messageType");
        Objects.requireNonNull(requestMethod, "requestMethod");
        Objects.requireNonNull(readOptions, "readOptions");
        if (maxHeadersSize < 0) {
            throw new IllegalArgumentException("maxHeadersSize must not be negative: " + maxHeadersSize);
        }
        int effectiveEncodedFieldSectionLimit = Http3QpackContext.encodedFieldSectionLimit(maxHeadersSize);
        Http3QpackContext.Stream openedQpackStream = Objects.requireNonNull(qpackContext, "qpackContext")
                .openStream(stream.streamId());
        Http3StreamSupport.StreamInput connectedInput;
        try {
            connectedInput = new Http3StreamSupport.StreamInput(stream, openedQpackStream::cancel, readOptions);
        } catch (RuntimeException | Error e) {
            try {
                openedQpackStream.cancel();
            } catch (RuntimeException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
        this.qpackStream = openedQpackStream;
        this.input = connectedInput;
        this.streamId = stream.streamId();
        this.context = context;
        this.messageType = messageType;
        this.requestMethod = requestMethod;
        this.maxHeadersSize = maxHeadersSize;
        this.encodedFieldSectionLimit = effectiveEncodedFieldSectionLimit;
        this.readOptions = readOptions;
        this.frameListener = readOptions.frameListener;
    }

    /**
     * Create an inbound request reader with QPACK dynamic-table support.
     *
     * @param stream receiver stream
     * @param qpackContext connection QPACK context
     * @param context socket context
     * @param maxHeadersSize hard local decoded-header limit
     * @param frameListener frame listener
     * @return request message reader
     */
    public static Http3MessageReader request(QuicReceiverStream stream,
                                             Http3QpackContext qpackContext,
                                             SocketContext context,
                                             int maxHeadersSize,
                                             Http3FrameListener frameListener) {
        return new Http3MessageReader(stream,
                                      qpackContext,
                                      context,
                                      MessageType.REQUEST,
                                      Method.GET,
                                      maxHeadersSize,
                                      new ReadOptions(Optional.empty(), frameListener));
    }

    /**
     * Create an inbound request reader with QPACK dynamic-table support and a progress timeout.
     *
     * @param stream receiver stream
     * @param qpackContext connection QPACK context
     * @param context socket context
     * @param maxHeadersSize hard local decoded-header limit
     * @param readTimeout maximum time to wait for the next request-stream or QPACK input; zero disables the timeout
     * @param frameListener frame listener
     * @return request message reader
     */
    public static Http3MessageReader request(QuicReceiverStream stream,
                                             Http3QpackContext qpackContext,
                                             SocketContext context,
                                             int maxHeadersSize,
                                             Duration readTimeout,
                                             Http3FrameListener frameListener) {
        Objects.requireNonNull(readTimeout, "readTimeout");
        if (readTimeout.isNegative()) {
            throw new IllegalArgumentException("readTimeout must not be negative: " + readTimeout);
        }
        return new Http3MessageReader(stream,
                                      qpackContext,
                                      context,
                                      MessageType.REQUEST,
                                      Method.GET,
                                      maxHeadersSize,
                                      new ReadOptions(readTimeout.isZero()
                                                              ? Optional.empty()
                                                              : Optional.of(readTimeout),
                                                      frameListener));
    }

    /**
     * Create an inbound response reader with QPACK dynamic-table support.
     *
     * @param stream receiver stream
     * @param qpackContext connection QPACK context
     * @param context socket context
     * @param requestMethod request method associated with the response
     * @param maxHeadersSize hard local decoded-header limit
     * @param frameListener frame listener
     * @return response message reader
     */
    public static Http3MessageReader response(QuicReceiverStream stream,
                                              Http3QpackContext qpackContext,
                                              SocketContext context,
                                              Method requestMethod,
                                              int maxHeadersSize,
                                              Http3FrameListener frameListener) {
        return response(stream,
                        qpackContext,
                        context,
                        requestMethod,
                        maxHeadersSize,
                        new ResponseOptions(Optional.empty(), frameListener));
    }

    /**
     * Create an inbound response reader with QPACK dynamic-table support and response read options.
     *
     * @param stream receiver stream
     * @param qpackContext connection QPACK context
     * @param context socket context
     * @param requestMethod request method associated with the response
     * @param maxHeadersSize hard local decoded-header limit
     * @param options response read options
     * @return response message reader
     */
    public static Http3MessageReader response(QuicReceiverStream stream,
                                              Http3QpackContext qpackContext,
                                              SocketContext context,
                                              Method requestMethod,
                                              int maxHeadersSize,
                                              ResponseOptions options) {
        Objects.requireNonNull(options, "options");
        return new Http3MessageReader(stream,
                                      qpackContext,
                                      context,
                                      MessageType.RESPONSE,
                                      requestMethod,
                                      maxHeadersSize,
                                      options.readOptions);
    }

    /**
     * Validate regular request fields and return the declared content length.
     * HTTP/3 field syntax, protocol invariants, and content length are always enforced.
     *
     * @param headers regular request fields
     * @return declared content length, if present
     */
    public static OptionalLong validateRequestHeaders(Headers headers) {
        return validateHeaders(headers, HeaderSection.REQUEST);
    }

    /**
     * Validate outbound response fields and return the declared content length.
     *
     * @param requestMethod request method associated with the response
     * @param status response status
     * @param headers regular response fields
     * @return declared content length, if present
     */
    public static OptionalLong validateResponseHeaders(Method requestMethod,
                                                       Status status,
                                                       Headers headers) {
        Objects.requireNonNull(requestMethod, "requestMethod");
        Http3ResponseSemantics semantics = Http3ResponseSemantics.create(requestMethod, status);
        if (!semantics.finalResponseAllowed()) {
            throw new IllegalArgumentException("Final HTTP/3 response status must not be informational: " + status.code());
        }
        OptionalLong contentLength = validateHeaders(headers, HeaderSection.RESPONSE);
        if (contentLength.isPresent() && !semantics.contentLengthAllowed()) {
            throw new IllegalArgumentException(semantics.tunnel()
                                                       ? "Successful CONNECT response must not contain Content-Length"
                                                       : "HTTP " + status.code() + " response must not contain Content-Length");
        }
        if (contentLength.isPresent()
                && semantics.contentLengthMustBeZero()
                && contentLength.orElseThrow() != 0) {
            throw new IllegalArgumentException("HTTP 205 response Content-Length must be zero");
        }
        if (!semantics.trailersAllowed() && headers.contains(HeaderNames.TRAILER)) {
            throw new IllegalArgumentException(semantics.tunnel()
                                                       ? "Successful CONNECT response must not contain Trailer"
                                                       : "HTTP " + status.code() + " response must not contain Trailer");
        }
        return contentLength;
    }

    /**
     * Validate trailing fields.
     *
     * @param headers trailing fields
     */
    public static void validateTrailers(Headers headers) {
        validateHeaders(headers, HeaderSection.TRAILERS);
    }

    /**
     * Activate the configured read timeout.
     * <p>
     * A reader remains untimed until this method is invoked. Response readers use delayed activation so request
     * production and the {@code 100 Continue} phase do not consume the response read timeout. Server request readers
     * activate the timeout after request admission and before request-head decoding.
     */
    public void activateReadTimeout() {
        readOptions.activateReadTimeout();
    }

    /**
     * Read and validate the initial request field section.
     *
     * @return decoded request head
     * @throws Http3ProtocolException if the request head or its framing is malformed
     * @throws IllegalStateException if this is not a request reader, the reader is closed, or waiting for stream input or
     *                               QPACK progress is interrupted
     */
    public Http3Protocol.DecodedRequestHead readRequestHead() {
        if (messageType != MessageType.REQUEST) {
            throw new IllegalStateException("Cannot read a request head from an HTTP/3 response reader");
        }
        if (requestHead != null) {
            return requestHead;
        }
        Http3Protocol.DecodedRequestHead decoded;
        try {
            decoded = Http3Protocol.decodeRequestHeaders(readInitialFieldSection());
            contentLength = validateRequestHeaders(decoded.headers());
        } catch (IllegalArgumentException e) {
            throw messageError(e.getMessage(), e);
        }
        requestHead = decoded;
        phase = Phase.DATA;
        if (frameListener.enabled()) {
            frameListener.requestHeaders(context,
                                         streamId,
                                         decoded.method(),
                                         decoded.scheme().orElse(""),
                                         decoded.authority(),
                                         decoded.path().orElse(""),
                                         decoded.headers());
        }
        return decoded;
    }

    /**
     * Read informational response field sections followed by the required final response field section.
     *
     * @param informationalConsumer consumer of validated informational response heads
     * @return final response head
     * @throws Http3ProtocolException if a response head or its framing is malformed
     * @throws IllegalStateException if this is not a response reader, the reader is closed, or waiting for stream input or
     *                               QPACK progress is interrupted
     */
    public ResponseHead readResponseHead(Consumer<ResponseHead> informationalConsumer) {
        Objects.requireNonNull(informationalConsumer, "informationalConsumer");
        if (messageType != MessageType.RESPONSE) {
            throw new IllegalStateException("Cannot read a response head from an HTTP/3 request reader");
        }
        if (responseHead != null) {
            return responseHead;
        }
        for (;;) {
            ResponseHead decoded;
            OptionalLong decodedContentLength;
            try {
                String statusValue = null;
                WritableHeaders<?> headers = WritableHeaders.create();
                boolean regularHeadersSeen = false;
                for (Header header : readInitialFieldSection()) {
                    String headerName = header.headerName().lowerCase();
                    validateLowercaseHeaderName(headerName);
                    if (!headerName.startsWith(":")) {
                        regularHeadersSeen = true;
                        QpackCodec.addDecodedHeader(headers, header);
                        continue;
                    }
                    if (regularHeadersSeen) {
                        throw messageError("HTTP/3 pseudo-header field after regular fields: " + headerName);
                    }
                    if (!":status".equals(headerName)) {
                        throw messageError("Prohibited HTTP/3 response pseudo-header field: " + headerName);
                    }
                    if (statusValue != null || header.valueCount() != 1) {
                        throw messageError("Duplicate HTTP/3 :status pseudo-header field");
                    }
                    statusValue = header.get();
                }
                if (statusValue == null) {
                    throw messageError("Missing required HTTP/3 :status pseudo-header field");
                }
                if (statusValue.length() != 3
                        || statusValue.charAt(0) < '1' || statusValue.charAt(0) > '5'
                        || statusValue.charAt(1) < '0' || statusValue.charAt(1) > '9'
                        || statusValue.charAt(2) < '0' || statusValue.charAt(2) > '9') {
                    throw messageError("Invalid HTTP/3 :status pseudo-header field: " + statusValue);
                }
                int statusCode = (statusValue.charAt(0) - '0') * 100
                        + (statusValue.charAt(1) - '0') * 10
                        + statusValue.charAt(2) - '0';
                if (statusCode == Status.SWITCHING_PROTOCOLS_101.code()) {
                    throw messageError("HTTP status 101 is not permitted in HTTP/3");
                }
                Status status = Status.create(statusCode);
                Http3ResponseSemantics semantics = Http3ResponseSemantics.create(requestMethod, status);
                decodedContentLength = validateResponseHeaders(status,
                                                               headers,
                                                               semantics);
                decoded = new ResponseHead(status, headers);
            } catch (IllegalArgumentException e) {
                throw messageError(e.getMessage(), e);
            }
            if (frameListener.enabled()) {
                frameListener.responseHeaders(context, streamId, decoded.status().code(), decoded.headers());
            }
            if (decoded.status().family() == Status.Family.INFORMATIONAL) {
                informationalConsumer.accept(decoded);
                continue;
            }
            responseHead = decoded;
            Http3ResponseSemantics semantics = Http3ResponseSemantics.create(requestMethod, decoded.status());
            tunnel = semantics.tunnel();
            contentLength = semantics.receivedContentLengthIgnored()
                    ? OptionalLong.empty()
                    : decodedContentLength;
            entityAllowed = semantics.dataAllowed();
            trailersAllowed = semantics.trailersAllowed();
            phase = Phase.DATA;
            return decoded;
        }
    }

    /**
     * Read the next chunk of entity bytes while processing trailers and message completion.
     *
     * @param estimate preferred chunk size hint
     * @return next entity bytes, or an empty array at message completion
     * @throws Http3ProtocolException if message framing or trailing fields are malformed
     * @throws IllegalStateException if the message head has not been read, the reader is closed, or waiting for stream
     *                               input or QPACK progress is interrupted
     */
    public byte[] readEntityDataWithTrailers(int estimate) {
        BufferData data = readEntityBufferWithTrailers(estimate);
        return data.available() == 0 ? EMPTY_BYTES : data.readBytes();
    }

    /**
     * Read the next read-only entity buffer while processing trailers and message completion.
     *
     * <p>The returned buffer has an independent cursor and a logical range containing DATA payload bytes only.
     * It must be consumed before another buffer is requested.
     *
     * @param estimate preferred chunk size hint
     * @return next entity buffer, or an empty buffer at message completion
     * @throws Http3ProtocolException if message framing or trailing fields are malformed
     * @throws IllegalStateException if the message head has not been read, the reader is closed, the previous entity
     *                               buffer has not been consumed, or waiting for stream input or QPACK progress is
     *                               interrupted
     */
    public BufferData readEntityBufferWithTrailers(int estimate) {
        if (phase == Phase.INITIAL_HEADERS) {
            throw new IllegalStateException("HTTP/3 message head has not been read");
        }
        if (entityBufferOutstanding()) {
            throw new IllegalStateException("Previous HTTP/3 entity buffer has not been consumed");
        }
        outstandingEntityBuffer = null;
        int requested = estimate > 0 ? estimate : DEFAULT_CHUNK_SIZE;
        for (;;) {
            if (phase == Phase.FIN) {
                return BufferData.empty();
            }
            if (currentFrameLength > 0) {
                if (currentFrameType == Http3Protocol.FRAME_DATA) {
                    validateDataFrame(currentFrameLength);
                    int chunkSize = (int) Math.min(currentFrameLength, requested);
                    BufferData buffer = input.readBuffer(chunkSize);
                    int dataLength = buffer.available();
                    boolean last = currentFrameLength == dataLength;
                    if (frameListener.enabled()) {
                        frameListener.frameData(context, streamId, dataLength, last);
                    }
                    if (frameListener.rawDataEnabled()) {
                        for (int offset = 0; offset < dataLength; offset += MAX_RAW_DATA_CHUNK_SIZE) {
                            int length = Math.min(dataLength - offset, MAX_RAW_DATA_CHUNK_SIZE);
                            byte[] chunk = new byte[length];
                            for (int i = 0; i < length; i++) {
                                chunk[i] = (byte) buffer.get(offset + i);
                            }
                            frameListener.rawFrameData(context,
                                                       streamId,
                                                       chunk,
                                                       last && offset + length == dataLength);
                        }
                    }
                    currentFrameLength -= dataLength;
                    entityBytesRead += dataLength;
                    if (currentFrameLength == 0) {
                        currentFrameType = -1;
                    }
                    outstandingEntityBuffer = buffer;
                    return buffer;
                }
                if (currentFrameType == Http3Protocol.FRAME_HEADERS) {
                    if (tunnel) {
                        throw unexpectedMessageFrame(currentFrameType,
                                                     "HTTP/3 CONNECT tunnel received a HEADERS frame");
                    }
                    if (!trailersAllowed) {
                        throw messageError("HTTP/3 response without message content received trailers");
                    }
                    if (phase == Phase.TRAILERS) {
                        throw unexpectedMessageFrame(currentFrameType,
                                                     "HTTP/3 message received another field section after trailers");
                    }
                    readTrailers();
                    phase = Phase.TRAILERS;
                    continue;
                }
                if (messageType == MessageType.RESPONSE
                        && currentFrameType == Http3Protocol.FRAME_PUSH_PROMISE) {
                    rejectDisabledPushPromise();
                }
                if (unexpectedMessageFrame(currentFrameType)) {
                    throw unexpectedMessageFrame(currentFrameType, "Unexpected HTTP/3 frame on message stream");
                }
                discardCurrentFrame();
                continue;
            }

            Optional<FrameHeader> frame = nextFrame();
            if (frame.isEmpty()) {
                finishMessage();
                close();
                return BufferData.empty();
            }
            FrameHeader header = frame.orElseThrow();
            if (phase == Phase.TRAILERS
                    && (header.type() == Http3Protocol.FRAME_DATA || header.type() == Http3Protocol.FRAME_HEADERS)) {
                throw unexpectedMessageFrame(header.type(), "HTTP/3 message frame received after trailers");
            }
            if (header.type() == Http3Protocol.FRAME_HEADERS) {
                if (tunnel) {
                    throw unexpectedMessageFrame(header.type(), "HTTP/3 CONNECT tunnel received a HEADERS frame");
                }
                if (!trailersAllowed) {
                    throw messageError("HTTP/3 response without message content received trailers");
                }
                readTrailers();
                phase = Phase.TRAILERS;
                continue;
            }
            if (messageType == MessageType.RESPONSE && header.type() == Http3Protocol.FRAME_PUSH_PROMISE) {
                rejectDisabledPushPromise();
            }
            if (header.type() == Http3Protocol.FRAME_DATA) {
                validateDataFrame(header.length());
            } else if (unexpectedMessageFrame(header.type())) {
                throw unexpectedMessageFrame(header.type(), "Unexpected HTTP/3 frame on message stream");
            }
            if (header.length() == 0) {
                notifyFrameData(EMPTY_BYTES, true);
                currentFrameType = -1;
                currentFrameLength = 0;
            }
        }
    }

    /**
     * Return whether the message can expose entity bytes without violating its request method, status, or declared length.
     *
     * @return whether an entity should be exposed
     */
    public boolean hasEntity() {
        if (phase == Phase.INITIAL_HEADERS) {
            throw new IllegalStateException("HTTP/3 message head has not been read");
        }
        if (!entityAllowed || contentLength.isPresent() && contentLength.orElseThrow() == 0) {
            return false;
        }
        return messageType == MessageType.RESPONSE || !endOfStreamReady();
    }

    /**
     * Return the validated declared content length.
     *
     * @return declared content length, if present
     */
    public OptionalLong contentLength() {
        if (phase == Phase.INITIAL_HEADERS) {
            throw new IllegalStateException("HTTP/3 message head has not been read");
        }
        return contentLength;
    }

    /**
     * Return whether the complete message has been consumed.
     *
     * @return whether the message is complete
     */
    public boolean messageComplete() {
        if (phase == Phase.FIN) {
            return true;
        }
        if (phase != Phase.INITIAL_HEADERS
                && !entityBufferOutstanding()
                && currentFrameLength == 0
                && input.endOfStreamReady()) {
            finishMessage();
            return true;
        }
        return false;
    }

    /**
     * Return a copy of decoded trailing fields.
     *
     * @return decoded trailers
     */
    public Headers trailers() {
        return WritableHeaders.create(trailers);
    }

    /**
     * Create a single entity input stream view over DATA frames and trailers.
     *
     * @return entity input stream
     */
    public InputStream inputStreamWithTrailers() {
        return inputStreamWithTrailers(_ -> {
        }, NO_OP, _ -> {
        });
    }

    /**
     * Create a single entity input stream view over DATA frames and trailers.
     *
     * @param trailersConsumer consumer invoked after trailers and FIN are consumed
     * @param entityFullyReadAction action invoked after complete entity consumption
     * @param protocolFailureConsumer consumer invoked when message processing fails with an HTTP/3 protocol error
     * @return entity input stream
     */
    public InputStream inputStreamWithTrailers(Consumer<Headers> trailersConsumer,
                                               Runnable entityFullyReadAction,
                                               Consumer<Http3ProtocolException> protocolFailureConsumer) {
        Objects.requireNonNull(trailersConsumer, "trailersConsumer");
        Objects.requireNonNull(entityFullyReadAction, "entityFullyReadAction");
        Objects.requireNonNull(protocolFailureConsumer, "protocolFailureConsumer");
        if (entityInputStreamRequested) {
            throw new IllegalStateException("HTTP/3 entity stream has already been requested.");
        }
        entityInputStreamRequested = true;
        return new InputStream() {
            private BufferData current = BufferData.empty();
            private boolean streamClosed;
            private boolean endOfEntity;

            @Override
            public int read() throws IOException {
                if (entityStreamClosed()) {
                    throw new IOException("HTTP/3 entity stream is closed.");
                }
                if (!ensureCurrent(1)) {
                    return -1;
                }
                int result = current.read();
                releaseCurrentIfConsumed();
                return result;
            }

            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException {
                Objects.checkFromIndexSize(offset, length, bytes.length);
                if (length == 0) {
                    return 0;
                }
                if (entityStreamClosed()) {
                    throw new IOException("HTTP/3 entity stream is closed.");
                }
                if (!ensureCurrent(length)) {
                    return -1;
                }
                int bytesToCopy = current.read(bytes, offset, length);
                releaseCurrentIfConsumed();
                return bytesToCopy;
            }

            @Override
            public void close() {
                if (streamClosed) {
                    return;
                }
                streamClosed = true;
                Http3MessageReader.this.close();
            }

            private boolean ensureCurrent(int estimate) throws IOException {
                if (current.available() > 0) {
                    return true;
                }
                if (endOfEntity) {
                    return false;
                }
                try {
                    current = readEntityBufferWithTrailers(Math.max(estimate, DEFAULT_CHUNK_SIZE));
                } catch (Http3ProtocolException failure) {
                    try {
                        protocolFailureConsumer.accept(failure);
                    } catch (RuntimeException | Error cleanupFailure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                    throw failure;
                } catch (UncheckedIOException failure) {
                    throw failure.getCause();
                } catch (Http3StreamSupport.StreamInputException
                         | Http3QpackContext.DecodingInterruptedException failure) {
                    String message = failure.getCause() instanceof InterruptedException
                            ? failure.getMessage()
                            : "HTTP/3 entity stream is closed.";
                    throw new IOException(message, failure);
                }
                if (current.available() > 0) {
                    return true;
                }
                endOfEntity = true;
                entityFullyReadAction.run();
                trailersConsumer.accept(trailers());
                return false;
            }

            private void releaseCurrentIfConsumed() {
                if (current.consumed()) {
                    current = BufferData.empty();
                }
            }

            private boolean entityStreamClosed() {
                return streamClosed || !endOfEntity && input.closed();
            }
        };
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            if (phase == Phase.FIN) {
                qpackStream.complete();
            } else {
                qpackStream.cancel();
            }
        } catch (RuntimeException | Error e) {
            try {
                qpackStream.cancel();
            } catch (RuntimeException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        } finally {
            outstandingEntityBuffer = null;
            input.close();
        }
    }

    /**
     * Validate regular response fields and return the declared content length.
     * HTTP/3 field syntax, protocol invariants, and content length are always enforced.
     *
     * @param status response status
     * @param headers regular response fields
     * @return declared content length, if present
     */
    private static OptionalLong validateResponseHeaders(Status status,
                                                        Headers headers,
                                                        Http3ResponseSemantics semantics) {
        Objects.requireNonNull(status, "status");
        OptionalLong contentLength = validateHeaders(headers,
                                                     HeaderSection.RESPONSE,
                                                     !semantics.receivedContentLengthIgnored());
        if (contentLength.isPresent()) {
            if (!semantics.contentLengthAllowed()) {
                throw new IllegalArgumentException("HTTP " + status.code() + " response must not contain Content-Length");
            }
            if (semantics.contentLengthMustBeZero() && contentLength.orElseThrow() != 0) {
                throw new IllegalArgumentException("HTTP 205 response Content-Length must be zero");
            }
        }
        return contentLength;
    }

    private static OptionalLong validateHeaders(Headers headers,
                                                HeaderSection section) {
        return validateHeaders(headers, section, true);
    }

    private static OptionalLong validateHeaders(Headers headers,
                                                HeaderSection section,
                                                boolean parseContentLength) {
        Objects.requireNonNull(headers, "headers");
        for (Header header : headers) {
            String headerName = header.headerName().lowerCase();
            validateLowercaseHeaderName(headerName);
            if (headerName.startsWith(":")) {
                throw new IllegalArgumentException("HTTP/3 regular field section contains pseudo-header: " + headerName);
            }
            if (CONNECTION_SPECIFIC_HEADERS.contains(headerName)) {
                throw new IllegalArgumentException("Connection-specific field is prohibited in HTTP/3: " + headerName);
            }
            if (HeaderNames.TE.lowerCase().equals(headerName)) {
                if (section != HeaderSection.REQUEST) {
                    throw new IllegalArgumentException("TE is only permitted in initial HTTP/3 request fields");
                }
                List<String> values = headers.values(HeaderNames.TE);
                if (values.isEmpty()) {
                    throw new IllegalArgumentException("HTTP/3 TE field value must be trailers");
                }
                for (String value : values) {
                    if (!"trailers".equalsIgnoreCase(value.trim())) {
                        throw new IllegalArgumentException("HTTP/3 TE field value must be trailers");
                    }
                }
            }
            if (section == HeaderSection.TRAILERS
                    && (HeaderNames.CONTENT_LENGTH.lowerCase().equals(headerName)
                    || HeaderNames.HOST.lowerCase().equals(headerName)
                    || HeaderNames.TRAILER.lowerCase().equals(headerName))) {
                throw new IllegalArgumentException("Field is prohibited in HTTP/3 trailers: " + headerName);
            }
            header.validate();
        }
        if (section == HeaderSection.TRAILERS) {
            return OptionalLong.empty();
        }
        return parseContentLength ? headers.contentLength() : OptionalLong.empty();
    }

    private static void validateLowercaseHeaderName(String headerName) {
        for (int i = 0; i < headerName.length(); i++) {
            char current = headerName.charAt(i);
            if (current >= 'A' && current <= 'Z') {
                throw new IllegalArgumentException("HTTP/3 header field name must be lowercase: " + headerName);
            }
        }
    }

    private static Http3ProtocolException messageError(String message) {
        return Http3ProtocolException.streamError(Http3ErrorCode.MESSAGE_ERROR, message);
    }

    private static Http3ProtocolException messageError(String message, Throwable cause) {
        return Http3ProtocolException.streamError(Http3ErrorCode.MESSAGE_ERROR, message, cause);
    }

    private static Http3ProtocolException frameError(String message) {
        return Http3ProtocolException.connectionError(Http3ErrorCode.FRAME_ERROR, message);
    }

    /**
     * Return whether the reader is positioned at transport end-of-stream with no frame bytes remaining.
     *
     * @return whether transport end-of-stream is ready
     */
    private boolean endOfStreamReady() {
        return !entityBufferOutstanding() && currentFrameLength == 0 && input.endOfStreamReady();
    }

    private List<Header> readInitialFieldSection() {
        for (;;) {
            Optional<FrameHeader> frame = nextFrame();
            if (frame.isEmpty()) {
                throw messageError("Missing required HTTP/3 HEADERS frame");
            }
            FrameHeader header = frame.orElseThrow();
            if (header.type() == Http3Protocol.FRAME_HEADERS) {
                return decodeCurrentFieldSection();
            }
            if (header.type() == Http3Protocol.FRAME_DATA) {
                throw unexpectedMessageFrame(Http3Protocol.FRAME_DATA, "HTTP/3 message received DATA before final HEADERS");
            }
            if (messageType == MessageType.RESPONSE && header.type() == Http3Protocol.FRAME_PUSH_PROMISE) {
                rejectDisabledPushPromise();
            }
            if (unexpectedMessageFrame(header.type())) {
                throw unexpectedMessageFrame(header.type(), "Unexpected HTTP/3 frame on message stream");
            }
            discardCurrentFrame();
        }
    }

    private void readTrailers() {
        WritableHeaders<?> decoded = WritableHeaders.create();
        try {
            List<Header> fieldLines = decodeCurrentFieldSection();
            fieldLines.forEach(header -> QpackCodec.addDecodedHeader(decoded, header));
            validateTrailers(decoded);
        } catch (IllegalArgumentException e) {
            throw messageError(e.getMessage(), e);
        }
        if (decoded.size() > 0 && frameListener.enabled()) {
            frameListener.trailers(context, streamId, decoded);
        }
        trailers = decoded;
    }

    private List<Header> decodeCurrentFieldSection() {
        BufferData fieldSection = BufferData.create(readCurrentFieldSection());
        if (readOptions.readTimeout.isPresent()) {
            return qpackStream.decodeHeaderLines(fieldSection,
                                                 maxHeadersSize,
                                                 readOptions.readTimeout.orElseThrow(),
                                                 readOptions.readTimeoutActivation);
        }
        return qpackStream.decodeHeaderLines(fieldSection, maxHeadersSize);
    }

    private Optional<FrameHeader> nextFrame() {
        DecodedVarInt decodedType = readVarInt(true, encodedFrameHeader, 0);
        if (decodedType.value() < 0) {
            return Optional.empty();
        }
        DecodedVarInt decodedLength = readVarInt(false, encodedFrameHeader, decodedType.encodedLength());
        long frameType = decodedType.value();
        long frameLength = decodedLength.value();
        int headerLength = decodedType.encodedLength() + decodedLength.encodedLength();
        currentFrameType = frameType;
        currentFrameLength = frameLength;
        if (frameListener.enabled()) {
            frameListener.frameHeader(context, streamId, frameType, frameLength, headerLength);
        }
        if (frameListener.rawDataEnabled()) {
            byte[] header = new byte[headerLength];
            System.arraycopy(encodedFrameHeader, 0, header, 0, headerLength);
            frameListener.rawFrameHeader(context, streamId, header);
        }
        return Optional.of(new FrameHeader(frameType, frameLength));
    }

    private void discardCurrentFrame() {
        if (currentFrameLength == 0) {
            notifyFrameData(EMPTY_BYTES, true);
        }
        while (currentFrameLength > 0) {
            int chunkSize = (int) Math.min(currentFrameLength, DEFAULT_CHUNK_SIZE);
            if (frameListener.rawDataEnabled()) {
                byte[] discarded = input.readBuffer(chunkSize).readBytes();
                notifyFrameData(discarded, currentFrameLength == discarded.length);
                currentFrameLength -= discarded.length;
            } else {
                int discarded = input.discardBuffer(chunkSize);
                if (frameListener.enabled()) {
                    frameListener.frameData(context, streamId, discarded, currentFrameLength == discarded);
                }
                currentFrameLength -= discarded;
            }
        }
        currentFrameType = -1;
    }

    private byte[] readCurrentFieldSection() {
        if (currentFrameLength > encodedFieldSectionLimit) {
            throw Http3ProtocolException.streamError(Http3ErrorCode.MESSAGE_ERROR,
                                                      "HTTP/3 encoded field section exceeds the local encoded limit: "
                                                              + currentFrameLength + " > " + encodedFieldSectionLimit);
        }
        byte[] payload = input.readBytes((int) currentFrameLength);
        notifyFrameData(payload, true);
        currentFrameType = -1;
        currentFrameLength = 0;
        return payload;
    }

    private boolean unexpectedMessageFrame(long frameType) {
        if (frameType == Http3Protocol.FRAME_SETTINGS
                || frameType == Http3Protocol.FRAME_GOAWAY
                || frameType == Http3Protocol.FRAME_CANCEL_PUSH
                || frameType == Http3Protocol.FRAME_MAX_PUSH_ID
                || Http3Protocol.isReservedHttp2FrameType(frameType)) {
            return true;
        }
        return frameType == Http3Protocol.FRAME_PUSH_PROMISE;
    }

    private void rejectDisabledPushPromise() {
        if (currentFrameLength == 0) {
            throw Http3ProtocolException.connectionError(Http3ErrorCode.FRAME_ERROR,
                                                         "HTTP/3 PUSH_PROMISE frame is missing its Push ID");
        }
        int first = input.readByte();
        if (first < 0) {
            throw Http3ProtocolException.connectionError(Http3ErrorCode.FRAME_ERROR,
                                                         "HTTP/3 PUSH_PROMISE frame is truncated");
        }
        int pushIdLength = 1 << (first >>> 6);
        if (currentFrameLength < pushIdLength + 2L) {
            throw Http3ProtocolException.connectionError(Http3ErrorCode.FRAME_ERROR,
                                                         "HTTP/3 PUSH_PROMISE frame has a malformed payload");
        }
        byte[] pushId = new byte[pushIdLength];
        pushId[0] = (byte) first;
        if (pushIdLength > 1) {
            byte[] remaining = input.readBytes(pushIdLength - 1);
            System.arraycopy(remaining, 0, pushId, 1, remaining.length);
        }
        if (frameListener.enabled()) {
            frameListener.frameData(context, streamId, pushIdLength, false);
        }
        if (frameListener.rawDataEnabled()) {
            frameListener.rawFrameData(context, streamId, pushId, false);
        }
        throw Http3ProtocolException.connectionError(Http3ErrorCode.ID_ERROR,
                                                     "HTTP/3 PUSH_PROMISE exceeds the unset MAX_PUSH_ID");
    }

    private Http3ProtocolException unexpectedMessageFrame(long frameType, String message) {
        Http3ErrorCode errorCode = messageType == MessageType.RESPONSE && frameType == Http3Protocol.FRAME_PUSH_PROMISE
                ? Http3ErrorCode.ID_ERROR
                : Http3ErrorCode.FRAME_UNEXPECTED;
        return Http3ProtocolException.connectionError(errorCode,
                                                      message + ": " + frameType);
    }

    private DecodedVarInt readVarInt(boolean eofAllowed, byte[] encodedHeader, int headerOffset) {
        int first = input.readByte();
        if (first < 0) {
            if (eofAllowed) {
                return new DecodedVarInt(-1, 0);
            }
            throw frameError("HTTP/3 frame length is truncated");
        }
        int length = 1 << (first >>> 6);
        encodedHeader[headerOffset] = (byte) first;
        long value = first & 0x3F;
        for (int i = 1; i < length; i++) {
            int next = input.readByte();
            if (next < 0) {
                throw frameError("HTTP/3 frame header is truncated");
            }
            encodedHeader[headerOffset + i] = (byte) next;
            value = (value << Byte.SIZE) | next;
        }
        return new DecodedVarInt(value, length);
    }

    private void notifyFrameData(byte[] data, boolean last) {
        if (frameListener.enabled()) {
            frameListener.frameData(context, streamId, data.length, last);
        }
        if (!frameListener.rawDataEnabled()) {
            return;
        }
        if (data.length == 0) {
            frameListener.rawFrameData(context, streamId, data, last);
            return;
        }
        for (int offset = 0; offset < data.length; offset += MAX_RAW_DATA_CHUNK_SIZE) {
            int length = Math.min(data.length - offset, MAX_RAW_DATA_CHUNK_SIZE);
            byte[] chunk = Arrays.copyOfRange(data, offset, offset + length);
            frameListener.rawFrameData(context, streamId, chunk, last && offset + length == data.length);
        }
    }

    private boolean entityBufferOutstanding() {
        return outstandingEntityBuffer != null && !outstandingEntityBuffer.consumed();
    }

    private void validateDataFrame(long frameLength) {
        if (!entityAllowed && frameLength > 0) {
            throw messageError("HTTP/3 response must not contain message content");
        }
        if (frameLength > Long.MAX_VALUE - entityBytesRead) {
            throw messageError("HTTP/3 message content length overflow");
        }
        if (contentLength.isPresent() && entityBytesRead + frameLength > contentLength.orElseThrow()) {
            throw messageError("HTTP/3 message content exceeds declared Content-Length");
        }
    }

    private void finishMessage() {
        if (entityAllowed && contentLength.isPresent() && entityBytesRead != contentLength.orElseThrow()) {
            throw messageError("HTTP/3 message content does not match declared Content-Length");
        }
        phase = Phase.FIN;
    }

    private enum MessageType {
        REQUEST,
        RESPONSE
    }

    private enum HeaderSection {
        REQUEST,
        RESPONSE,
        TRAILERS
    }

    private enum Phase {
        INITIAL_HEADERS,
        DATA,
        TRAILERS,
        FIN
    }

    /**
     * Options for reading an HTTP/3 response stream.
     */
    @Api.Internal
    public static final class ResponseOptions {
        private final ReadOptions readOptions;

        private ResponseOptions(Optional<Duration> readTimeout,
                                Http3FrameListener frameListener) {
            this.readOptions = new ReadOptions(readTimeout, frameListener);
        }

        /**
         * Create timed response read options.
         *
         * @param readTimeout maximum time to wait for the next response or QPACK input; must not be negative
         * @param frameListener frame listener
         * @return response read options
         * @throws IllegalArgumentException if the timeout is negative
         */
        public static ResponseOptions create(Duration readTimeout,
                                             Http3FrameListener frameListener) {
            Objects.requireNonNull(readTimeout, "readTimeout");
            if (readTimeout.isNegative()) {
                throw new IllegalArgumentException("readTimeout must not be negative: " + readTimeout);
            }
            return new ResponseOptions(Optional.of(readTimeout),
                                       frameListener);
        }

    }

    /**
     * Decoded HTTP/3 response head.
     */
    @Api.Internal
    public static final class ResponseHead {
        private final Status status;
        private final Headers headers;

        private ResponseHead(Status status, Headers headers) {
            this.status = Objects.requireNonNull(status, "status");
            this.headers = WritableHeaders.create(Objects.requireNonNull(headers, "headers"));
        }

        /**
         * Return the response status.
         *
         * @return response status
         */
        public Status status() {
            return status;
        }

        /**
         * Return regular response fields.
         *
         * @return response fields
         */
        public Headers headers() {
            return headers;
        }

    }

    /**
     * Shared request and response read options.
     */
    static final class ReadOptions {
        private final Optional<Duration> readTimeout;
        private final Http3FrameListener frameListener;
        private final CompletableFuture<Void> readTimeoutActivation = new CompletableFuture<>();

        private ReadOptions(Optional<Duration> readTimeout,
                            Http3FrameListener frameListener) {
            this.readTimeout = Objects.requireNonNull(readTimeout, "readTimeout");
            this.frameListener = Objects.requireNonNull(frameListener, "frameListener");
        }

        boolean readTimeoutActive() {
            return readTimeout.isPresent() && readTimeoutActivation.isDone();
        }

        Duration readTimeout() {
            return readTimeout.orElseThrow();
        }

        void onReadTimeoutActivation(Runnable action) {
            readTimeoutActivation.thenRun(action);
        }

        private void activateReadTimeout() {
            if (readTimeout.isPresent()) {
                readTimeoutActivation.complete(null);
            }
        }
    }

    private record FrameHeader(long type, long length) {
    }

    private record DecodedVarInt(long value, int encodedLength) {
    }
}
