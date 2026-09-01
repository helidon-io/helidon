/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.http1;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.socket.SocketWriterException;
import io.helidon.http.DateTime;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.Method;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.ServerResponseTrailers;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.MediaContext;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ServerConnectionException;
import io.helidon.webserver.http.ServerResponseBase;
import io.helidon.webserver.http.spi.Sink;
import io.helidon.webserver.http.spi.SinkProvider;
import io.helidon.webserver.http1.spi.Http1UpgradeResponse;

/**
 * An HTTP/1 server response.
 */
class Http1ServerResponse extends ServerResponseBase<Http1ServerResponse> implements Http1UpgradeResponse {
    private static final System.Logger LOGGER = System.getLogger(Http1ServerResponse.class.getName());
    private static final byte[] HTTP_BYTES = "HTTP/1.1 ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] OK_200 = "HTTP/1.1 200 OK\r\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] DATE = "Date: ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TERMINATING_CHUNK = "0\r\n\r\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TERMINATING_CHUNK_TRAILERS = "0\r\n".getBytes(StandardCharsets.UTF_8);

    private static final WritableHeaders<?> EMPTY_HEADERS = WritableHeaders.create();

    private final ConnectionContext ctx;
    private final Http1ConnectionListener sendListener;
    private final DataWriter dataWriter;
    private final Http1ServerRequest request;
    private final ServerResponseHeaders headers;
    private final ServerResponseTrailers trailers;
    private final boolean keepAlive;
    private final boolean validateHeaders;

    private boolean keepConnectionOpen;
    private boolean streamingEntity;
    private boolean isSent;
    private ClosingBufferedOutputStream outputStream;
    private long bytesWritten;
    private String streamResult = "";
    private boolean isNoEntityStatus;

    Http1ServerResponse(ConnectionContext ctx,
                        Http1ConnectionListener sendListener,
                        DataWriter dataWriter,
                        Http1ServerRequest request,
                        boolean keepAlive,
                        boolean validateHeaders) {
        super(ctx, request);

        this.ctx = ctx;
        this.sendListener = sendListener;
        this.dataWriter = dataWriter;
        this.request = request;
        this.headers = ServerResponseHeaders.create();
        this.trailers = ServerResponseTrailers.create();
        this.keepAlive = keepAlive;
        this.keepConnectionOpen = keepAlive;
        this.validateHeaders = validateHeaders;
    }

    static void nonEntityBytes(ServerResponseHeaders headers,
                               Status status,
                               BufferData buffer,
                               boolean keepAlive,
                               boolean validateHeaders) {

        status = status == null ? Status.OK_200 : status;

        normalizeNoEntityHeaders(headers, status);

        // first write status
        if (status == Status.OK_200) {
            buffer.write(OK_200);
        } else {
            buffer.write(HTTP_BYTES);
            String reasonPhrase = status.reasonPhrase() == null || status.reasonPhrase().isEmpty()
                    ? status.codeText() : status.reasonPhrase();
            buffer.write((status.code() + " " + reasonPhrase).getBytes(StandardCharsets.US_ASCII));
            buffer.write('\r');
            buffer.write('\n');
        }
        // date header
        if (!headers.contains(HeaderNames.DATE)) {
            buffer.write(DATE);
            byte[] dateBytes = DateTime.http1Bytes();
            buffer.write(dateBytes);
        }

        // either content-length or chunked encoding
        // if content length - make sure to compare it when writing actual entity (streaming and send(entity))
        if (!keepAlive) {
            // we must override even if user sets keep alive, as close was requested
            headers.set(HeaderValues.CONNECTION_CLOSE);
        }
        /*
        RFC 9112, Section 9.3. HTTP/1.1 makes persistence the default: if the message is HTTP/1.1 and th
        ere is no Connection: close, the connection remains persistent after the response. The same section says a se
        rver that does not support persistent connections must send Connection: close in responses. It does not requi
        re Connection: keep-alive for normal HTTP/1.1 responses. (rfc-editor.org(https://www.rfc-editor.org/rfc/rfc9112.html))
         */

        // write headers followed by empty line
        writeHeaders(headers, buffer, validateHeaders);

        buffer.write('\r');        // "\r\n" - empty line after headers
        buffer.write('\n');
    }

    @Override
    public Http1ServerResponse status(Status status) {
        if (outputStream != null && outputStream.totalBytesWritten() > 0) {
            throw new IllegalStateException("Cannot set response status after response was already sent.");
        }
        // set internal state
        super.status(status);
        isNoEntityStatus = isNoEntityStatus(status);
        if (outputStream != null) {
            outputStream.status(status);
        }

        // check consistency if status code should not include entity
        if (isNoEntityStatus) {
            normalizeNoEntityHeaders(headers, status);
        }
        return this;
    }

    @Override
    public Http1ServerResponse header(Header header) {
        if (streamingEntity) {
            throw new IllegalStateException("Cannot set response header after requesting output stream.");
        }
        if (isSent()) {
            throw new IllegalStateException("Cannot set response header after response was already sent.");
        }
        this.headers.set(header);
        return this;
    }

    /**
     * Actually send the response over the wire, if allowed by status code.
     */
    @Override
    public void send(byte[] bytes) {
        send(bytes, 0, bytes.length);
    }

    @Override
    public void send(byte[] bytes, int position, int length) {
        boolean headRequest = request.prologue().method() == Method.HEAD;
        if (headRequest && length > 0) {
            throw new IllegalStateException("Cannot send response entity for a HEAD request");
        }
        if (headRequest && hasStreamFilter()) {
            prepareFilteredHeadResponse();
        }
        // if no entity status, we cannot send bytes here
        if (isNoEntityStatus && length > 0) {
            status(noEntityInternalError(status()));
            return;
        }

        // send bytes to writer
        beforeSend();

        boolean noEntityResponse = isNoEntityStatus(status());
        if (noEntityResponse) {
            normalizeNoEntityHeaders(headers, status());
        }
        if (noEntityResponse || !hasStreamFilter() && !headers.contains(HeaderNames.TRAILER)) {
            long configuredHeadLength = headRequest ? headers.contentLength().orElse(-1) : -1;
            byte[] entity = noEntityResponse ? entityBytes(BufferData.EMPTY_BYTES) : entityBytes(bytes, position, length);
            if (configuredHeadLength >= 0) {
                headers.contentLength(configuredHeadLength);
            }
            int entityPosition = bytes == entity ? position : 0;
            int entityLength = noEntityResponse ? 0 : bytes == entity ? length : entity.length;
            BufferData bufferData = responseBuffer(entity, entityPosition, entityLength, headRequest);
            bytesWritten = bufferData.available();
            isSent = true;
            request.reset();
            writeResponse(dataWriter, bufferData, "Failed to write response");
            afterSend();
        } else {
            // automatic encoders are skipped for an empty entity, but an explicit encoder still applies
            boolean allowAutomaticEncoding = length > 0;
            try (OutputStream os = outputStream(allowAutomaticEncoding)) {
                os.write(bytes, position, length);
            } catch (IOException e) {
                throw new ServerConnectionException("Failed to write response", e);
            }
            // after send is part of the output stream handling
        }
    }

    @Override
    public boolean isSent() {
        return isSent;
    }

    @Override
    public OutputStream outputStream() {
        beforeSend();
        return outputStream(true);
    }

    @Override
    public long bytesWritten() {
        if (streamingEntity) {
            return outputStream.totalBytesWritten();
        } else {
            return bytesWritten;
        }
    }

    @Override
    public ServerResponseHeaders headers() {
        return headers;
    }

    @Override
    public void send(Status status) {
        status(Objects.requireNonNull(status));
        send();
    }

    @Override
    public void sendSwitchingProtocols(Headers requiredHeaders) {
        Objects.requireNonNull(requiredHeaders);
        if (isSent) {
            throw new IllegalStateException("Response already sent");
        }
        if (streamingEntity) {
            throw new IllegalStateException("Cannot switch protocols after requesting output stream.");
        }

        status(Status.SWITCHING_PROTOCOLS_101);
        headers.from(requiredHeaders);
        headers.remove(HeaderNames.CONTENT_LENGTH);
        headers.remove(HeaderNames.TRANSFER_ENCODING);
        beforeSend();
        headers.from(requiredHeaders);
        headers.remove(HeaderNames.CONTENT_LENGTH);
        headers.remove(HeaderNames.TRANSFER_ENCODING);

        sendListener.status(ctx, Status.SWITCHING_PROTOCOLS_101);
        sendListener.headers(ctx, headers);

        BufferData responseBuffer = BufferData.growing(256);
        nonEntityBytes(headers, Status.SWITCHING_PROTOCOLS_101, responseBuffer, true, validateHeaders);
        bytesWritten = responseBuffer.available();
        isSent = true;
        request.reset();
        sendListener.data(ctx, responseBuffer);
        try {
            dataWriter.writeNow(responseBuffer);
        } catch (SocketWriterException | UncheckedIOException e) {
            throw new ServerConnectionException("Failed to write switching protocols response", e);
        }
        afterSend();
    }

    @Override
    public ServerResponseTrailers trailers() {
        if (request.headers().containsToken(HeaderValues.TE_TRAILERS) || headers.contains(HeaderNames.TRAILER)) {
            return trailers;
        }
        throw new IllegalStateException(
                "Trailers are supported only when request came with 'TE: trailers' header or "
                        + "response headers have trailer names definition 'Trailer: <trailer-name>'");
    }

    @Override
    public void streamResult(String result) {
        this.streamResult = result;
        if (outputStream != null) {
            outputStream.close();
        }
    }

    @Override
    public boolean hasEntity() {
        return isSent || streamingEntity;
    }

    @Override
    public boolean reset() {
        if (isSent || outputStream != null && outputStream.totalBytesWritten() > 0) {
            return false;
        }
        headers.clear();
        keepConnectionOpen = keepAlive;
        streamingEntity = false;
        outputStream = null;
        resetContentEncoding();
        return true;
    }

    @Override
    public boolean resetStream() {
        if (isSent || outputStream != null && outputStream.totalBytesWritten() > 0) {
            return false;
        }
        streamingEntity = false;
        outputStream = null;
        resetAutomaticContentEncoding();
        return true;
    }

    @Override
    public boolean resetEntity() {
        if (!super.resetEntity()) {
            return false;
        }
        streamResult = "";
        trailers.clear();
        return true;
    }

    @Override
    public void commit() {
        if (outputStream != null) {
            outputStream.commit();
        }
    }

    @Override
    public <X extends Sink<?>> X sink(GenericType<X> sinkType) {
        return createSink(findSinkProvider(sinkType));
    }

    final SinkProvider<?> findSinkProvider(GenericType<? extends Sink<?>> sinkType) {
        return findSinkProvider(sinkType, request);
    }

    final <X extends Sink<?>> X createSink(SinkProvider<?> provider) {
        return createSink(provider,
                          request,
                          ctx,
                          this::sinkEntityOutputStream,
                          () -> {
                              if (outputStream == null) {
                                  this.isSent = true;
                                  afterSend();
                                  request.reset();
                              } else {
                                  commit();
                              }
                          },
                          this::flushHeaders);
    }

    protected Optional<OutputStream> sinkEntityOutputStream(Runnable responsePreparation) {
        Objects.requireNonNull(responsePreparation);
        beforeSend();
        responsePreparation.run();
        return Optional.of(outputStream(true));
    }

    void flushHeaders() {
        if (outputStream != null) {
            outputStream.flushHeaders();
        }
    }

    private void handleSinkData(Object data, MediaType mediaType) {
        if (outputStream == null) {
            outputStream();
        }
        try {
            MediaContext mediaContext = mediaContext();

            if (data instanceof byte[] bytes) {
                outputStream.write(bytes);
            } else {
                if (data instanceof String str && mediaType.equals(MediaTypes.TEXT_PLAIN)) {
                    EntityWriter<String> writer = mediaContext.writer(GenericType.STRING, EMPTY_HEADERS, EMPTY_HEADERS);
                    writer.write(GenericType.STRING, str, outputStream, EMPTY_HEADERS, EMPTY_HEADERS);
                } else {
                    GenericType<Object> type = GenericType.create(data);
                    WritableHeaders<?> resHeaders = WritableHeaders.create();
                    resHeaders.set(HeaderNames.CONTENT_TYPE, mediaType.text());
                    EntityWriter<Object> writer = mediaContext.writer(type, EMPTY_HEADERS, resHeaders);
                    writer.write(type, data, outputStream, EMPTY_HEADERS, resHeaders);
                }
            }
        } catch (IOException e) {
            throw new ServerConnectionException("Failed to write sink data", e);
        }
    }

    private static void writeHeaders(io.helidon.http.Headers headers, BufferData buffer, boolean validate) {
        if (validate) {
            headers.forEach(Header::validate);
        }
        for (Header header : headers) {
            header.writeHttp1Header(buffer);
        }
    }

    private static void writeResponse(DataWriter dataWriter, BufferData bufferData, String message) {
        try {
            dataWriter.write(bufferData);
        } catch (SocketWriterException | UncheckedIOException e) {
            throw new ServerConnectionException(message, e);
        }
    }

    private BufferData responseBuffer(byte[] bytes, int position, int length, boolean headRequest) {
        if (isSent) {
            throw new IllegalStateException("Response already sent");
        }
        if (streamingEntity) {
            throw new IllegalStateException("When output stream is used, response is completed by closing the output stream"
                                                    + ", do not call send().");
        }

        boolean forcedChunkedEncoding = false;
        Status usedStatus = status();
        boolean noEntityResponse = isNoEntityStatus(usedStatus);

        if (noEntityResponse) {
            normalizeNoEntityHeaders(headers, usedStatus);
        } else if (headers.contains(HeaderNames.TRANSFER_ENCODING)
                && headers.containsToken(HeaderValues.TRANSFER_ENCODING_CHUNKED)) {
            headers.remove(HeaderNames.CONTENT_LENGTH);
            // chunked enforced (and even if empty entity, will be used)
            forcedChunkedEncoding = true;
        } else if (!headers.contains(HeaderNames.CONTENT_LENGTH)
                && (!headRequest || !suppressImplicitContentLength(length))) {
            headers.contentLength(length);
        }

        sendListener.status(ctx, usedStatus);
        sendListener.headers(ctx, headers);

        // give some space for code and headers + entity
        BufferData responseBuffer = BufferData.growing(256 + (headRequest || noEntityResponse ? 0 : length));

        keepConnectionOpen = resolveKeepConnectionOpen();
        nonEntityBytes(headers, usedStatus, responseBuffer, keepAlive, validateHeaders);
        if (!headRequest && !noEntityResponse && forcedChunkedEncoding) {
            byte[] hex = Integer.toHexString(length).getBytes(StandardCharsets.US_ASCII);
            responseBuffer.write(hex);
            responseBuffer.write('\r');
            responseBuffer.write('\n');
            responseBuffer.write(bytes, position, length);
            responseBuffer.write('\r');
            responseBuffer.write('\n');
            responseBuffer.write(TERMINATING_CHUNK);
        } else if (!headRequest && !noEntityResponse) {
            responseBuffer.write(bytes, position, length);
        }

        sendListener.data(ctx, responseBuffer);

        return responseBuffer;
    }

    private OutputStream outputStream(boolean allowAutomaticEncoding) {
        if (isSent) {
            throw new IllegalStateException("Response already sent");
        }
        if (streamingEntity) {
            throw new IllegalStateException("OutputStream already obtained");
        }
        streamingEntity = true;

        int writeBufferSize = ctx.listenerContext().config().writeBufferSize();
        BlockingOutputStream bos = new BlockingOutputStream(headers,
                                                            trailers,
                                                            beforeTrailers(),
                                                            this::status,
                                                            () -> streamResult,
                                                            dataWriter,
                                                            () -> this.isSent = true,
                                                            () -> {
                                                                this.isSent = true;
                                                                afterSend();
                                                                request.reset();
                                                            },
                                                            ctx,
                                                            sendListener,
                                                            request,
                                                            keepAlive,
                                                            validateHeaders);

        outputStream = new ClosingBufferedOutputStream(bos, writeBufferSize);

        OutputStream encodedOutputStream = contentEncode(outputStream, allowAutomaticEncoding);
        if (!isNoEntityStatus(status())) {
            bos.checkResponseHeaders();     // headers can be augmented by encoders
        }
        OutputStream applicationOutputStream = applyStreamFilters(encodedOutputStream);
        keepConnectionOpen = resolveKeepConnectionOpen();
        if (applicationOutputStream == outputStream) {
            outputStream.applicationFacing();
            return outputStream;
        }
        return new ApplicationOutputStream(applicationOutputStream, outputStream);
    }

    boolean keepConnectionOpen() {
        return keepConnectionOpen;
    }

    private static Status noEntityInternalError(Status status) {
        LOGGER.log(System.Logger.Level.ERROR, "Attempt to send status " + status.text() + " with entity."
                + " Server responded with Internal Server Error. Please fix your routing, this is not allowed "
                + "by HTTP specification, such responses MUST NOT contain an entity.");
        return Status.INTERNAL_SERVER_ERROR_500;
    }

    static boolean isNoEntityStatus(Status status) {
        int code = status.code();
        return code == Status.NO_CONTENT_204.code()
                || code == Status.RESET_CONTENT_205.code()
                || code == Status.NOT_MODIFIED_304.code();
    }

    static void normalizeNoEntityHeaders(ServerResponseHeaders headers, Status status) {
        int statusCode = status.code();
        if (statusCode == Status.NO_CONTENT_204.code()) {
            headers.remove(HeaderNames.CONTENT_LENGTH);
        } else if (statusCode == Status.RESET_CONTENT_205.code()) {
            headers.set(HeaderValues.CONTENT_LENGTH_ZERO);
        }
        if (isNoEntityStatus(status)) {
            headers.remove(HeaderNames.TRANSFER_ENCODING);
            headers.remove(HeaderNames.TRAILER);
        }
    }

    private boolean resolveKeepConnectionOpen() {
        return keepAlive && !headers.containsToken(HeaderValues.CONNECTION_CLOSE);
    }

    static class BlockingOutputStream extends OutputStream {
        private final ServerResponseHeaders headers;
        private final WritableHeaders<?> trailers;
        private final Supplier<Status> status;
        private final DataWriter dataWriter;
        private final Runnable responseSentRunnable;
        private final Runnable responseCloseRunnable;
        private final ConnectionContext ctx;
        private final Http1ConnectionListener sendListener;
        private final Http1ServerRequest request;
        private final boolean keepAlive;
        private final Supplier<String> streamResult;
        private final boolean headRequest;
        private Status writeForbiddenStatus;
        private boolean forcedChunked;

        private BufferData firstBuffer;
        private boolean closed;
        private long bytesWritten;
        private long contentLength;
        private boolean isChunked;
        private boolean firstByte = true;
        private boolean headResponseSent;
        private long responseBytesTotal;
        private boolean closing = false;
        private boolean committing;
        private final boolean validateHeaders;
        private final Consumer<ServerResponseTrailers> beforeTrailers;

        private BlockingOutputStream(ServerResponseHeaders headers,
                                     WritableHeaders<?> trailers,
                                     Consumer<ServerResponseTrailers> beforeTrailers,
                                     Supplier<Status> status,
                                     Supplier<String> streamResult,
                                     DataWriter dataWriter,
                                     Runnable responseSentRunnable,
                                     Runnable responseCloseRunnable,
                                     ConnectionContext ctx,
                                     Http1ConnectionListener sendListener,
                                     Http1ServerRequest request,
                                     boolean keepAlive,
                                     boolean validateHeaders) {
            this.headers = headers;
            this.trailers = trailers;
            this.beforeTrailers = beforeTrailers;
            this.status = status;
            this.streamResult = streamResult;
            this.dataWriter = dataWriter;
            this.responseSentRunnable = responseSentRunnable;
            this.responseCloseRunnable = responseCloseRunnable;
            this.ctx = ctx;
            this.sendListener = sendListener;
            this.contentLength = headers.contentLength().orElse(-1);
            this.request = request;
            this.keepAlive = keepAlive;
            this.validateHeaders = validateHeaders;
            this.headRequest = request.prologue().method() == Method.HEAD;
            Status initialStatus = status.get();
            this.writeForbiddenStatus = isNoEntityStatus(initialStatus) ? initialStatus : null;
        }

        void status(Status status) {
            Status previousWriteForbiddenStatus = writeForbiddenStatus;
            writeForbiddenStatus = isNoEntityStatus(status) ? status : null;
            if (previousWriteForbiddenStatus != null
                    && previousWriteForbiddenStatus.code() == Status.RESET_CONTENT_205.code()
                    && status.code() != Status.RESET_CONTENT_205.code()) {
                headers.remove(HeaderNames.CONTENT_LENGTH);
            }
            if (previousWriteForbiddenStatus != null && writeForbiddenStatus == null) {
                checkResponseHeaders();
            }
        }

        void checkResponseHeaders() {
            if (headers.contains(HeaderNames.TRAILER)) {
                headers.remove(HeaderNames.CONTENT_LENGTH);
                isChunked = true;
                forcedChunked = true;
            } else {
                isChunked = !headers.contains(HeaderNames.CONTENT_LENGTH);
                forcedChunked = headers.containsToken(HeaderValues.TRANSFER_ENCODING_CHUNKED);
            }
            contentLength = headers.contentLength().orElse(-1);
        }

        @Override
        public void write(int b) throws IOException {
            write(BufferData.create(1).write(b));
        }

        @Override
        public void write(byte[] b) throws IOException {
            write(BufferData.create(b));
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            write(BufferData.create(b, off, len));
        }

        /**
         * Last call to flush before closing should be ignored to properly
         * support content length optimization.
         *
         * @throws IOException an I/O exception
         */
        @Override
        public void flush() throws IOException {
            if (closing || committing) {
                return;     // ignore final flush
            }
            if (headRequest) {
                return;
            }
            if (firstByte && firstBuffer != null) {
                write(BufferData.empty());
            }
        }

        /**
         * This is a noop, even when user closes the output stream, we wait for the
         * call to {@link this#commit()}.
         */
        @Override
        public void close() {
            // no-op
        }

        /**
         * Informs output stream that closing phase has started. Special handling
         * for {@link this#flush()}.
         */
        public void closing() {
            closing = true;
        }

        void committing() {
            committing = headRequest;
        }

        void commit() {
            if (closed) {
                return;
            }
            this.closed = true;
            Status usedStatus = status.get();
            boolean noEntityResponse = isNoEntityStatus(usedStatus);
            if (noEntityResponse) {
                firstBuffer = null;
                isChunked = false;
                forcedChunked = false;
            }
            normalizeNoEntityHeaders(headers, usedStatus);
            boolean sendTrailers =
                    (isChunked || forcedChunked)
                    && (request.headers().containsToken(HeaderValues.TE_TRAILERS)
                                || headers.contains(HeaderNames.TRAILER));

            if (noEntityResponse && !headResponseSent) {
                sendNoEntityResponse(usedStatus);
            } else if (headRequest) {
                if (!headResponseSent) {
                    if (sendTrailers && beforeTrailers != null) {
                        trailers.set(STREAM_RESULT_NAME, streamResult.get());
                        beforeTrailers.accept(ServerResponseTrailers.wrap(trailers));
                    }
                    sendHeadResponse(true);
                }
            } else if (firstByte) {
                if (forcedChunked && firstBuffer != null) {
                    // no sense in sending no data, only do this if chunked requested through a header
                    sendHeadersAndPrepare();
                    writeChunked(firstBuffer);
                    terminatingChunk(sendTrailers);
                } else {
                    sendFirstChunkOnly();
                }
            } else if (isChunked) {
                terminatingChunk(sendTrailers);
            }

            if (sendTrailers && !headRequest) {
                // not optimized, trailers enabled: we need to write trailers
                trailers.set(STREAM_RESULT_NAME, streamResult.get());
                if (beforeTrailers != null) {
                    beforeTrailers.accept(ServerResponseTrailers.wrap(trailers));
                }
                BufferData buffer = BufferData.growing(128);
                writeHeaders(trailers, buffer, this.validateHeaders);
                buffer.write('\r');        // "\r\n" - empty line after headers
                buffer.write('\n');
                writeResponse(dataWriter, buffer, "Failed to write response trailers");
            }

            responseCloseRunnable.run();
            try {
                super.close();
            } catch (IOException e) {
                throw new ServerConnectionException("Failed to close server response stream.", e);
            }
        }

        /**
         * Emits the HTTP/1.1 status line and headers while keeping the response stream open.
         * <p>
         * If the first body chunk was already buffered, this delegates to the normal first-write
         * path so header emission and chunk framing stay identical to the regular streaming logic.
         * Otherwise it sends only the headers and leaves payload writes for later.
         */
        void flushHeaders() {
            if (closed) {
                return;
            }
            if (!firstByte) {
                responseSentRunnable.run();
                return;
            }
            if (headRequest) {
                sendHeadResponse(false);
                firstByte = false;
                return;
            }
            Status usedStatus = status.get();
            if (isNoEntityStatus(usedStatus)) {
                normalizeNoEntityHeaders(headers, usedStatus);
                sendNoEntityResponse(usedStatus);
                firstByte = false;
                responseSentRunnable.run();
                return;
            }
            if (firstBuffer != null) {
                try {
                    write(BufferData.empty());
                } catch (IOException e) {
                    throw new ServerConnectionException("Failed to flush server response headers.", e);
                }
                responseSentRunnable.run();
                return;
            }
            if (request.headers().containsToken(HeaderValues.TE_TRAILERS)) {
                headers.add(STREAM_TRAILERS);
            }
            sendHeadersAndPrepare();
            firstByte = false;
            responseSentRunnable.run();
        }

        private void sendNoEntityResponse(Status usedStatus) {
            sendListener.status(ctx, usedStatus);
            sendListener.headers(ctx, headers);
            BufferData bufferData = BufferData.growing(256);
            nonEntityBytes(headers, usedStatus, bufferData, keepAlive, validateHeaders);
            sendListener.data(ctx, bufferData);
            responseBytesTotal += bufferData.available();
            writeResponse(dataWriter, bufferData, "Failed to write response");
            headResponseSent = true;
        }

        long totalBytesWritten() {
            return responseBytesTotal;
        }

        /**
         * Send terminating chunk without trailers {@code  "0\r\n\r\n"} or when trailers are expected {@code  "0\r\n"}.
         *
         * <pre>{@code
         *   chunked-body    = *chunk
         *                     last-chunk
         *                     trailer-section
         *                     CRLF
         *
         *   chunk           = chunk-size [ chunk-ext ] CRLF
         *                     chunk-data CRLF
         *   last-chunk      = 1*("0") [ chunk-ext ] CRLF
         *   trailer-section = *( field-line CRLF )
         *   }</pre>
         *
         * @param trailers whether trailers are expected or not
         * @see <a href="https://www.rfc-editor.org/rfc/rfc9112#section-7.1">rfc9112 §7.1</a>
         */
        private void terminatingChunk(boolean trailers) {
            BufferData terminatingChunk = BufferData.create(trailers ? TERMINATING_CHUNK_TRAILERS : TERMINATING_CHUNK);
            sendListener.data(ctx, terminatingChunk);
            writeResponse(dataWriter, terminatingChunk, "Failed to write terminating chunk");
        }

        private void write(BufferData buffer) throws IOException {
            if (closed) {
                throw new IOException("Stream already closed");
            }
            if (headResponseSent) {
                // Encoder and filter finalization after an early HEAD flush must not produce content.
                return;
            }
            if (writeForbiddenStatus != null) {
                // Discard entity data buffered before the status changed and ignore empty writes.
                return;
            }
            if (headRequest) {
                bytesWritten += buffer.available();
                if (!isChunked) {
                    checkContentLength(buffer);
                }
                return;
            }

            if (!isChunked) {
                if (firstByte) {
                    firstByte = false;
                    Status usedStatus = status.get();
                    sendListener.status(ctx, usedStatus);
                    sendListener.headers(ctx, headers);
                    // write headers and payload part in one buffer to avoid TCP/ACK delay problems
                    BufferData growing = BufferData.growing(256 + buffer.available());
                    nonEntityBytes(headers, usedStatus, growing, keepAlive, validateHeaders);
                    // check not exceeding content-length
                    bytesWritten += buffer.available();
                    checkContentLength(buffer);
                    sendListener.data(ctx, buffer);
                    // write single buffer headers and payload part
                    growing.write(buffer);
                    responseBytesTotal += growing.available();
                    writeResponse(dataWriter, growing, "Failed to write response");
                } else {
                    // if not chunked, always write
                    writeContent(buffer);
                }
                return;
            }

            // try chunked data optimization
            if (firstByte && firstBuffer == null) {
                // if somebody re-uses the byte buffer sent to us, we must copy it
                firstBuffer = buffer.copy();
                return;
            }

            if (firstByte) {
                if (request.headers().containsToken(HeaderValues.TE_TRAILERS)) {
                    // proper stream with multiple buffers, write status amd headers
                    headers.add(STREAM_TRAILERS);
                }
                // this is chunked encoding, if anybody managed to set content length, it would break everything
                if (headers.contains(HeaderNames.CONTENT_LENGTH)
                        && buffer.available() > 0) {
                    LOGGER.log(System.Logger.Level.WARNING, "Content length was set after stream was requested, "
                            + "the response is already chunked, cannot use content-length");
                    headers.remove(HeaderNames.CONTENT_LENGTH);
                }
                sendHeadersAndPrepare();
                firstByte = false;
                BufferData combined = BufferData.create(firstBuffer, buffer);
                writeChunked(combined);
                firstBuffer = null;
            } else {
                writeChunked(buffer);
            }
        }

        private void sendFirstChunkOnly() {
            int contentLength;
            if (firstBuffer == null) {
                headers.set(HeaderValues.CONTENT_LENGTH_ZERO);
                contentLength = 0;
            } else {
                headers.set(HeaderValues.create(HeaderNames.CONTENT_LENGTH, String.valueOf(firstBuffer.available())));
                contentLength = firstBuffer.available();
            }
            isChunked = false;
            headers.remove(HeaderNames.TRANSFER_ENCODING);

            // at this moment, we must send headers
            Status usedStatus = status.get();
            normalizeNoEntityHeaders(headers, usedStatus);
            sendListener.status(ctx, usedStatus);
            sendListener.headers(ctx, headers);
            BufferData bufferData = BufferData.growing(contentLength + 256);
            nonEntityBytes(headers, usedStatus, bufferData, keepAlive, validateHeaders);

            if (firstBuffer != null) {
                bufferData.write(firstBuffer);
            }

            sendListener.data(ctx, bufferData);
            responseBytesTotal += bufferData.available();
            writeResponse(dataWriter, bufferData, "Failed to write response");
        }

        private void sendHeadersAndPrepare() {
            if (headers.contains(HeaderNames.CONTENT_LENGTH)) {
                contentLength = headers.contentLength().orElse(-1);
                isChunked = false;
            } else {
                contentLength = -1;
                // Add chunked encoding, if there is no other transfer-encoding headers
                if (!headers.contains(HeaderNames.TRANSFER_ENCODING)) {
                    headers.set(HeaderValues.TRANSFER_ENCODING_CHUNKED);
                } else {
                    // Add chunked encoding, if it's not part of existing transfer-encoding headers
                    if (!headers.containsToken(HeaderValues.TRANSFER_ENCODING_CHUNKED)) {
                        headers.add(HeaderValues.TRANSFER_ENCODING_CHUNKED);
                    }
                }
            }

            // at this moment, we must send headers
            Status usedStatus = status.get();
            sendListener.status(ctx, usedStatus);
            sendListener.headers(ctx, headers);
            BufferData bufferData = BufferData.growing(256);
            nonEntityBytes(headers, usedStatus, bufferData, keepAlive, validateHeaders);
            sendListener.data(ctx, bufferData);
            responseBytesTotal += bufferData.available();
            writeResponse(dataWriter, bufferData, "Failed to write response headers");
        }

        private void sendHeadResponse(boolean completeRepresentation) {
            if (headResponseSent) {
                return;
            }
            Status usedStatus = status.get();
            normalizeNoEntityHeaders(headers, usedStatus);
            if (!completeRepresentation) {
                headers.remove(HeaderNames.TRAILER);
            }
            if (headers.contains(HeaderNames.TRANSFER_ENCODING) || headers.contains(HeaderNames.TRAILER)) {
                headers.remove(HeaderNames.CONTENT_LENGTH);
                if (!headers.containsToken(HeaderValues.TRANSFER_ENCODING_CHUNKED)) {
                    headers.add(HeaderValues.TRANSFER_ENCODING_CHUNKED);
                }
            } else if (completeRepresentation && !headers.contains(HeaderNames.CONTENT_LENGTH)) {
                headers.contentLength(bytesWritten);
            }

            sendListener.status(ctx, usedStatus);
            sendListener.headers(ctx, headers);
            BufferData bufferData = BufferData.growing(256);
            nonEntityBytes(headers, usedStatus, bufferData, keepAlive, validateHeaders);
            sendListener.data(ctx, bufferData);
            responseBytesTotal += bufferData.available();
            writeResponse(dataWriter, bufferData, "Failed to write response");
            headResponseSent = true;
            responseSentRunnable.run();
        }

        private void checkWriteAllowed(int length) throws IOException {
            if (length > 0 && headRequest) {
                throw new IllegalStateException("Cannot write response entity for a HEAD request");
            }
            Status usedStatus = writeForbiddenStatus;
            if (length > 0 && usedStatus != null) {
                throw new IllegalStateException("Attempting to write data on a response with status " + usedStatus);
            }
        }

        private void writeChunked(BufferData buffer) {
            int available = buffer.available();
            if (available == 0) {
                return;
            }
            byte[] hex = Integer.toHexString(available).getBytes(StandardCharsets.US_ASCII);

            BufferData toWrite = BufferData.create(available + hex.length + 4); // \r\n after size, another after chunk
            toWrite.write(hex);
            toWrite.write('\r');
            toWrite.write('\n');
            toWrite.write(buffer);
            toWrite.write('\r');
            toWrite.write('\n');

            sendListener.data(ctx, toWrite);
            responseBytesTotal += toWrite.available();
            writeResponse(dataWriter, toWrite, "Failed to write chunked response data");
        }

        private void checkContentLength(BufferData ignored) throws IOException {
            if (bytesWritten > contentLength && contentLength != -1) {
                throw new IOException("Content length was set to " + contentLength
                        + ", but you are writing additional " + (bytesWritten - contentLength) + " "
                        + "bytes");
            }
        }

        private void writeContent(BufferData buffer) throws IOException {
            bytesWritten += buffer.available();
            checkContentLength(buffer);
            sendListener.data(ctx, buffer);
            responseBytesTotal += buffer.available();
            writeResponse(dataWriter, buffer, "Failed to write response content");
        }
    }

    /**
     * A special stream that provides buffering for a delegate and special handling
     * of close logic. Note that due to some locking issues in the JDK, this class
     * must use delegation with {@link BufferedOutputStream} instead of subclassing.
     *
     * <p>If the buffer size is less or equal to zero, it will not wrap the
     * {@link io.helidon.webserver.http1.Http1ServerResponse.BlockingOutputStream}
     * with a {@link java.io.BufferedOutputStream}.
     */
    static class ClosingBufferedOutputStream extends OutputStream {

        private final BlockingOutputStream closingDelegate;
        private final OutputStream delegate;
        private boolean applicationFacing;
        private boolean discardWrites;
        private RuntimeException writeFailure;

        ClosingBufferedOutputStream(BlockingOutputStream out, int size) {
            this.closingDelegate = out;
            this.delegate = size <= 0 ? out : new BufferedOutputStream(out, size);
        }

        @Override
        public void write(int b) throws IOException {
            if (applicationFacing) {
                checkApplicationWrite(1);
            }
            if (discardWrites) {
                return;
            }
            try {
                delegate.write(b);
            } catch (IOException e) {
                failedWrite(e);
                throw e;
            } catch (RuntimeException e) {
                failedWrite(e);
                throw e;
            }
        }

        @Override
        public void write(byte[] b) throws IOException {
            if (applicationFacing) {
                checkApplicationWrite(b.length);
            }
            if (discardWrites) {
                return;
            }
            try {
                delegate.write(b);
            } catch (IOException e) {
                failedWrite(e);
                throw e;
            } catch (RuntimeException e) {
                failedWrite(e);
                throw e;
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (applicationFacing) {
                checkApplicationWrite(len);
            }
            if (discardWrites) {
                return;
            }
            try {
                delegate.write(b, off, len);
            } catch (IOException e) {
                failedWrite(e);
                throw e;
            } catch (RuntimeException e) {
                failedWrite(e);
                throw e;
            }
        }

        @Override
        public void flush() throws IOException {
            if (applicationFacing) {
                checkApplicationWrite(0);
            }
            if (discardWrites) {
                return;
            }
            try {
                delegate.flush();
            } catch (IOException e) {
                failedWrite(e);
                throw e;
            } catch (RuntimeException e) {
                failedWrite(e);
                throw e;
            }
        }

        @Override
        public void close() {
            closingDelegate.closing();     // inform of imminent call to close for last flush
            if (discardWrites) {
                closingDelegate.close();
                return;
            }
            try {
                delegate.close();
            } catch (IOException | UncheckedIOException | SocketWriterException e) {
                ServerConnectionException failure =
                        new ServerConnectionException("Failed to close server output stream", e);
                failedWrite(failure);
                throw failure;
            }
        }

        long totalBytesWritten() {
            return closingDelegate.totalBytesWritten();
        }

        void status(Status status) {
            closingDelegate.status(status);
        }

        void applicationFacing() {
            applicationFacing = true;
        }

        private void checkApplicationWrite(int length) throws IOException {
            if (writeFailure != null) {
                throw writeFailure;
            }
            closingDelegate.checkWriteAllowed(length);
        }

        private void failedWrite(IOException e) {
            if (!discardWrites) {
                discardWrites = true;
                writeFailure = new UncheckedIOException(e);
            }
        }

        private void failedWrite(RuntimeException e) {
            if (!discardWrites) {
                discardWrites = true;
                writeFailure = e;
            }
        }

        void commit() {
            if (discardWrites) {
                closingDelegate.close();
                if (writeFailure != null) {
                    throw writeFailure;
                }
                return;
            }
            closingDelegate.committing();
            try {
                flush();
                closingDelegate.commit();
            } catch (IOException | UncheckedIOException | SocketWriterException e) {
                ServerConnectionException failure =
                        new ServerConnectionException("Failed to flush server output stream", e);
                failedWrite(failure);
                throw failure;
            }
        }

        void flushHeaders() {
            try {
                flush();
                closingDelegate.flushHeaders();
            } catch (IOException | UncheckedIOException e) {
                throw new ServerConnectionException("Failed to flush server response headers", e);
            }
        }
    }

    private static class ApplicationOutputStream extends OutputStream {
        private final OutputStream delegate;
        private final ClosingBufferedOutputStream responseOutputStream;

        private ApplicationOutputStream(OutputStream delegate, ClosingBufferedOutputStream responseOutputStream) {
            this.delegate = delegate;
            this.responseOutputStream = responseOutputStream;
        }

        @Override
        public void write(int b) throws IOException {
            responseOutputStream.checkApplicationWrite(1);
            try {
                delegate.write(b);
            } catch (IOException e) {
                responseOutputStream.failedWrite(e);
                throw e;
            } catch (RuntimeException e) {
                responseOutputStream.failedWrite(e);
                throw e;
            }
        }

        @Override
        public void write(byte[] b) throws IOException {
            responseOutputStream.checkApplicationWrite(b.length);
            try {
                delegate.write(b);
            } catch (IOException e) {
                responseOutputStream.failedWrite(e);
                throw e;
            } catch (RuntimeException e) {
                responseOutputStream.failedWrite(e);
                throw e;
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            responseOutputStream.checkApplicationWrite(len);
            try {
                delegate.write(b, off, len);
            } catch (IOException e) {
                responseOutputStream.failedWrite(e);
                throw e;
            } catch (RuntimeException e) {
                responseOutputStream.failedWrite(e);
                throw e;
            }
        }

        @Override
        public void flush() throws IOException {
            responseOutputStream.checkApplicationWrite(0);
            try {
                delegate.flush();
            } catch (IOException e) {
                responseOutputStream.failedWrite(e);
                throw e;
            } catch (RuntimeException e) {
                responseOutputStream.failedWrite(e);
                throw e;
            }
        }

        @Override
        public void close() throws IOException {
            try {
                delegate.close();
            } catch (IOException e) {
                responseOutputStream.failedWrite(e);
                throw e;
            } catch (RuntimeException e) {
                responseOutputStream.failedWrite(e);
                throw e;
            }
        }
    }
}
