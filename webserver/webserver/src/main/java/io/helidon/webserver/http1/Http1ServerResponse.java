/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import io.helidon.common.GenericType;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.DateTime;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpException;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.ServerResponseTrailers;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.MediaContext;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ServerConnectionException;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http.ServerResponseBase;
import io.helidon.webserver.http.spi.Sink;
import io.helidon.webserver.http.spi.SinkProvider;
import io.helidon.webserver.http.spi.SinkProviderContext;

/**
 * An HTTP/1 server response.
 */
class Http1ServerResponse extends ServerResponseBase<Http1ServerResponse> {
    private static final System.Logger LOGGER = System.getLogger(Http1ServerResponse.class.getName());
    private static final byte[] HTTP_BYTES = "HTTP/1.1 ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] OK_200 = "HTTP/1.1 200 OK\r\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] DATE = "Date: ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TERMINATING_CHUNK = "0\r\n\r\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TERMINATING_CHUNK_TRAILERS = "0\r\n".getBytes(StandardCharsets.UTF_8);

    @SuppressWarnings("rawtypes")
    private static final List<SinkProvider> SINK_PROVIDERS
            = HelidonServiceLoader.builder(ServiceLoader.load(SinkProvider.class)).build().asList();
    private static final WritableHeaders<?> EMPTY_HEADERS = WritableHeaders.create();

    private final ConnectionContext ctx;
    private final Http1ConnectionListener sendListener;
    private final DataWriter dataWriter;
    private final Http1ServerRequest request;
    private final ServerResponseHeaders headers;
    private final ServerResponseTrailers trailers;
    private final boolean keepAlive;

    private boolean streamingEntity;
    private boolean isSent;
    private ClosingBufferedOutputStream outputStream;
    private long bytesWritten;
    private String streamResult = "";
    private boolean isNoEntityStatus;
    private final boolean validateHeaders;

    private UnaryOperator<OutputStream> outputStreamFilter;

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
        this.validateHeaders = validateHeaders;
    }

    static void nonEntityBytes(ServerResponseHeaders headers,
                               Status status,
                               BufferData buffer,
                               boolean keepAlive,
                               boolean validateHeaders) {

        status = status == null ? Status.OK_200 : status;

        if (isNoEntityStatus(status)) {
            // https://www.rfc-editor.org/rfc/rfc9110#status.204
            // A 204 response is terminated by the end of the header section; it cannot contain content or trailers
            // ditto for 205, and 304
            if ((headers.contains(HeaderNames.CONTENT_LENGTH) && !headers.contains(HeaderValues.CONTENT_LENGTH_ZERO))
                         || headers.contains(HeaderValues.TRANSFER_ENCODING_CHUNKED)) {
                status = noEntityInternalError(status);
            }
        }

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
        if (keepAlive) {
            headers.setIfAbsent(HeaderValues.CONNECTION_KEEP_ALIVE);
        } else {
            // we must override even if user sets keep alive, as close was requested
            headers.set(HeaderValues.CONNECTION_CLOSE);
        }

        // write headers followed by empty line
        writeHeaders(headers, buffer, validateHeaders);

        buffer.write('\r');        // "\r\n" - empty line after headers
        buffer.write('\n');
    }

    @Override
    public Http1ServerResponse status(Status status) {
        // set internal state
        super.status(status);
        isNoEntityStatus = isNoEntityStatus(status);

        // check consistency if status code should not include entity
        if (isNoEntityStatus) {
            if (!headers.contains(HeaderNames.CONTENT_LENGTH)) {
                headers.set(HeaderValues.CONTENT_LENGTH_ZERO);
            } else if (headers.get(HeaderNames.CONTENT_LENGTH).asLong().getLong() > 0L) {
                throw new IllegalStateException("Cannot set status to " + status + " with header "
                                                        + HeaderNames.CONTENT_LENGTH + " greater than zero");
            }
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
        // if no entity status, we cannot send bytes here
        if (isNoEntityStatus && bytes.length > 0) {
            status(noEntityInternalError(status()));
            return;
        }

        // send bytes to writer
        if (outputStreamFilter == null && !headers.contains(HeaderNames.TRAILER)) {
            byte[] entity = entityBytes(bytes);
            BufferData bufferData = responseBuffer(entity);
            bytesWritten = bufferData.available();
            isSent = true;
            request.reset();
            dataWriter.write(bufferData);
            afterSend();
        } else {
            // we should skip encoders if no data is written (e.g. for GZIP)
            boolean skipEncoders = (bytes.length == 0);
            try (OutputStream os = outputStream(skipEncoders)) {
                os.write(bytes);
            } catch (IOException e) {
                throw new ServerConnectionException("Failed to write response", e);
            }
        }
    }

    @Override
    public boolean isSent() {
        return isSent;
    }

    @Override
    public OutputStream outputStream() {
        return outputStream(false);
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
    public ServerResponseTrailers trailers() {
        if (request.headers().contains(HeaderValues.TE_TRAILERS) || headers.contains(HeaderNames.TRAILER)) {
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
        streamingEntity = false;
        outputStream = null;
        return true;
    }

    @Override
    public void commit() {
        if (outputStream != null) {
            outputStream.commit();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <X extends Sink<?>> X sink(GenericType<X> sinkType) {
        for (SinkProvider<?> p : SINK_PROVIDERS) {
            if (p.supports(sinkType, request)) {
                try {
                    return (X) p.create(new SinkProviderContext() {
                        @Override
                        public ServerResponse serverResponse() {
                            return Http1ServerResponse.this;
                        }

                        @Override
                        public ConnectionContext connectionContext() {
                            return Http1ServerResponse.this.ctx;
                        }

                        @Override
                        public Runnable closeRunnable() {
                            return () -> {
                                Http1ServerResponse.this.isSent = true;
                                afterSend();
                                request.reset();
                            };
                        }
                    });
                } catch (UnsupportedOperationException e) {
                    // deprecated - will be removed in 5.x
                    return (X) p.create(this, this::handleSinkData, this::commit);
                }
            }
        }
        // Request not acceptable if provider not found
        throw new HttpException("Unable to find sink provider for request", Status.NOT_ACCEPTABLE_406);
    }

    @Override
    public void streamFilter(UnaryOperator<OutputStream> filterFunction) {
        if (isSent) {
            throw new IllegalStateException("Response already sent");
        }
        if (streamingEntity) {
            throw new IllegalStateException("OutputStream already obtained");
        }
        Objects.requireNonNull(filterFunction);
        UnaryOperator<OutputStream> current = this.outputStreamFilter;
        if (current == null) {
            this.outputStreamFilter = filterFunction;
        } else {
            this.outputStreamFilter = it -> filterFunction.apply(current.apply(it));
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

    private BufferData responseBuffer(byte[] bytes) {
        if (isSent) {
            throw new IllegalStateException("Response already sent");
        }
        if (streamingEntity) {
            throw new IllegalStateException("When output stream is used, response is completed by closing the output stream"
                                                    + ", do not call send().");
        }

        int contentLength = bytes.length;
        boolean forcedChunkedEncoding = false;
        headers.setIfAbsent(HeaderValues.CONNECTION_KEEP_ALIVE);

        if (headers.contains(HeaderValues.TRANSFER_ENCODING_CHUNKED)) {
            headers.remove(HeaderNames.CONTENT_LENGTH);
            // chunked enforced (and even if empty entity, will be used)
            forcedChunkedEncoding = true;
        } else {
            if (!headers.contains(HeaderNames.CONTENT_LENGTH)) {
                headers.contentLength(contentLength);
            }
        }

        Status usedStatus = status();
        sendListener.status(ctx, usedStatus);
        sendListener.headers(ctx, headers);

        // give some space for code and headers + entity
        BufferData responseBuffer = BufferData.growing(256 + bytes.length);

        nonEntityBytes(headers, usedStatus, responseBuffer, keepAlive, validateHeaders);
        if (forcedChunkedEncoding) {
            byte[] hex = Integer.toHexString(contentLength).getBytes(StandardCharsets.US_ASCII);
            responseBuffer.write(hex);
            responseBuffer.write('\r');
            responseBuffer.write('\n');
            responseBuffer.write(bytes);
            responseBuffer.write('\r');
            responseBuffer.write('\n');
            responseBuffer.write(TERMINATING_CHUNK);
        } else {
            responseBuffer.write(bytes);
        }

        sendListener.data(ctx, responseBuffer);

        return responseBuffer;
    }

    private OutputStream outputStream(boolean skipEncoders) {
        if (isSent) {
            throw new IllegalStateException("Response already sent");
        }
        if (streamingEntity) {
            throw new IllegalStateException("OutputStream already obtained");
        }
        streamingEntity = true;

        BlockingOutputStream bos = new BlockingOutputStream(headers,
                                                            trailers,
                                                            this::status,
                                                            () -> streamResult,
                                                            dataWriter,
                                                            () -> {
                                                                this.isSent = true;
                                                                afterSend();
                                                                request.reset();
                                                            },
                                                            ctx,
                                                            sendListener,
                                                            request,
                                                            keepAlive,
                                                            validateHeaders,
                                                            isNoEntityStatus);

        int writeBufferSize = ctx.listenerContext().config().writeBufferSize();
        outputStream = new ClosingBufferedOutputStream(bos, writeBufferSize);

        OutputStream encodedOutputStream = outputStream;
        if (!skipEncoders) {
            encodedOutputStream = contentEncode(outputStream);
            bos.checkResponseHeaders();     // headers can be augmented by encoders
        }
        return outputStreamFilter == null ? encodedOutputStream : outputStreamFilter.apply(encodedOutputStream);
    }

    private static Status noEntityInternalError(Status status) {
        LOGGER.log(System.Logger.Level.ERROR, "Attempt to send status " + status.text() + " with entity."
                + " Server responded with Internal Server Error. Please fix your routing, this is not allowed "
                + "by HTTP specification, such responses MUST NOT contain an entity.");
        return Status.INTERNAL_SERVER_ERROR_500;
    }

    private static boolean isNoEntityStatus(Status status) {
        int code = status.code();
        return code == Status.NO_CONTENT_204.code()
                || code == Status.RESET_CONTENT_205.code()
                || code == Status.NOT_MODIFIED_304.code();
    }

    static class BlockingOutputStream extends OutputStream {
        private final ServerResponseHeaders headers;
        private final WritableHeaders<?> trailers;
        private final Supplier<Status> status;
        private final DataWriter dataWriter;
        private final Runnable responseCloseRunnable;
        private final ConnectionContext ctx;
        private final Http1ConnectionListener sendListener;
        private final Http1ServerRequest request;
        private final boolean keepAlive;
        private final Supplier<String> streamResult;
        private boolean forcedChunked;

        private BufferData firstBuffer;
        private boolean closed;
        private long bytesWritten;
        private long contentLength;
        private boolean isChunked;
        private boolean firstByte = true;
        private long responseBytesTotal;
        private boolean closing = false;
        private final boolean validateHeaders;
        private final boolean isNoEntityStatus;

        private BlockingOutputStream(ServerResponseHeaders headers,
                                     WritableHeaders<?> trailers,
                                     Supplier<Status> status,
                                     Supplier<String> streamResult,
                                     DataWriter dataWriter,
                                     Runnable responseCloseRunnable,
                                     ConnectionContext ctx,
                                     Http1ConnectionListener sendListener,
                                     Http1ServerRequest request,
                                     boolean keepAlive,
                                     boolean validateHeaders,
                                     boolean isNoEntityStatus) {
            this.headers = headers;
            this.trailers = trailers;
            this.status = status;
            this.streamResult = streamResult;
            this.dataWriter = dataWriter;
            this.responseCloseRunnable = responseCloseRunnable;
            this.ctx = ctx;
            this.sendListener = sendListener;
            this.contentLength = headers.contentLength().orElse(-1);
            this.request = request;
            this.keepAlive = keepAlive;
            this.validateHeaders = validateHeaders;
            this.isNoEntityStatus = isNoEntityStatus;
        }

        void checkResponseHeaders() {
            if (headers.contains(HeaderNames.TRAILER)) {
                headers.remove(HeaderNames.CONTENT_LENGTH);
                isChunked = true;
                forcedChunked = true;
            } else {
                isChunked = !headers.contains(HeaderNames.CONTENT_LENGTH);
                forcedChunked = headers.contains(HeaderValues.TRANSFER_ENCODING_CHUNKED);
            }
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
            if (closing) {
                return;     // ignore final flush
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

        void commit() {
            if (closed) {
                return;
            }
            this.closed = true;
            boolean sendTrailers =
                    (isChunked || forcedChunked)
                    && (request.headers().contains(HeaderValues.TE_TRAILERS)
                                || headers.contains(HeaderNames.TRAILER));

            if (firstByte) {
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

            if (sendTrailers) {
                // not optimized, trailers enabled: we need to write trailers
                trailers.set(STREAM_RESULT_NAME, streamResult.get());
                BufferData buffer = BufferData.growing(128);
                writeHeaders(trailers, buffer, this.validateHeaders);
                buffer.write('\r');        // "\r\n" - empty line after headers
                buffer.write('\n');
                dataWriter.write(buffer);
            }

            responseCloseRunnable.run();
            try {
                super.close();
            } catch (IOException e) {
                throw new ServerConnectionException("Failed to close server response stream.", e);
            }
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
            dataWriter.write(terminatingChunk);
        }

        private void write(BufferData buffer) throws IOException {
            if (closed) {
                throw new IOException("Stream already closed");
            }
            if (isNoEntityStatus && buffer.available() > 0) {
                throw new IllegalStateException("Attempting to write data on a response with status " + status);
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
                    dataWriter.write(growing);
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
                if (request.headers().contains(HeaderValues.TE_TRAILERS)) {
                    // proper stream with multiple buffers, write status amd headers
                    headers.add(STREAM_TRAILERS);
                }
                // this is chunked encoding, if anybody managed to set content length, it would break everything
                if (headers.contains(HeaderNames.CONTENT_LENGTH)
                        && (isNoEntityStatus || buffer.available() > 0)) {
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
            sendListener.status(ctx, usedStatus);
            sendListener.headers(ctx, headers);
            BufferData bufferData = BufferData.growing(contentLength + 256);
            nonEntityBytes(headers, usedStatus, bufferData, keepAlive, validateHeaders);

            if (firstBuffer != null) {
                bufferData.write(firstBuffer);
            }

            sendListener.data(ctx, bufferData);
            responseBytesTotal += bufferData.available();
            dataWriter.write(bufferData);
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
                    if (!headers.contains(HeaderValues.TRANSFER_ENCODING_CHUNKED)) {
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
            dataWriter.write(bufferData);
        }

        private void writeChunked(BufferData buffer) {
            int available = buffer.available();
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
            dataWriter.write(toWrite);
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
            dataWriter.write(buffer);
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

        ClosingBufferedOutputStream(BlockingOutputStream out, int size) {
            this.closingDelegate = out;
            this.delegate = size <= 0 ? out : new BufferedOutputStream(out, size);
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(byte[] b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() {
            closingDelegate.closing();     // inform of imminent call to close for last flush
            try {
                delegate.close();
            } catch (IOException e) {
                throw new ServerConnectionException("Failed to close server output stream", e);
            }
        }

        long totalBytesWritten() {
            return closingDelegate.totalBytesWritten();
        }

        void commit() {
            try {
                flush();
                closingDelegate.commit();
            } catch (IOException e) {
                throw new ServerConnectionException("Failed to flush server output stream", e);
            }
        }
    }
}
