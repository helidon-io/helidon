/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.webserver.http1;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import io.helidon.common.GenericType;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.DateTime;
import io.helidon.common.http.Http.HeaderName;
import io.helidon.common.http.Http.HeaderValue;
import io.helidon.common.http.Http.HeaderValues;
import io.helidon.common.http.HttpException;
import io.helidon.common.http.ServerResponseHeaders;
import io.helidon.common.http.WritableHeaders;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.nima.http.media.EntityWriter;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.webserver.ConnectionContext;
import io.helidon.nima.webserver.http.ServerResponseBase;
import io.helidon.nima.webserver.http.spi.Sink;
import io.helidon.nima.webserver.http.spi.SinkProvider;

/**
 * An HTTP/1 server response.
 */
class Http1ServerResponse extends ServerResponseBase<Http1ServerResponse> {
    private static final byte[] HTTP_BYTES = "HTTP/1.1 ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] OK_200 = "HTTP/1.1 200 OK\r\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] DATE = "Date: ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TERMINATING_CHUNK = "0\r\n\r\n".getBytes(StandardCharsets.UTF_8);
    private static final HeaderName STREAM_STATUS_NAME = Http.Header.create("stream-status");
    private static final HeaderName STREAM_RESULT_NAME = Http.Header.create("stream-result");
    private static final HeaderValue STREAM_TRAILERS =
            Http.Header.create(Http.Header.TRAILER, STREAM_STATUS_NAME.defaultCase()
                    + "," + STREAM_RESULT_NAME.defaultCase());

    private static final List<SinkProvider> SINK_PROVIDERS
            = HelidonServiceLoader.builder(ServiceLoader.load(SinkProvider.class)).build().asList();
    private static final WritableHeaders<?> EMPTY_HEADERS = WritableHeaders.create();

    private final ConnectionContext ctx;
    private final Http1ConnectionListener sendListener;
    private final DataWriter dataWriter;
    private final Http1ServerRequest request;
    private final ServerResponseHeaders headers;
    private final WritableHeaders<?> trailers = WritableHeaders.create();
    private final boolean keepAlive;

    private boolean streamingEntity;
    private boolean isSent;
    private ClosingBufferedOutputStream outputStream;
    private long entitySize;
    private String streamResult = "";

    Http1ServerResponse(ConnectionContext ctx,
                        Http1ConnectionListener sendListener,
                        DataWriter dataWriter,
                        Http1ServerRequest request,
                        boolean keepAlive) {
        super(ctx, request);

        this.ctx = ctx;
        this.sendListener = sendListener;
        this.dataWriter = dataWriter;
        this.request = request;
        this.headers = ServerResponseHeaders.create();
        this.keepAlive = keepAlive;
    }

    static void nonEntityBytes(ServerResponseHeaders headers,
                               Http.Status status,
                               BufferData buffer,
                               boolean keepAlive) {

        // first write status
        if (status == null || status == Http.Status.OK_200) {
            buffer.write(OK_200);
        } else {
            buffer.write(HTTP_BYTES);
            if (status.reasonPhrase().isEmpty()) {
                buffer.write((status.codeText()).getBytes(StandardCharsets.US_ASCII));
            } else {
                buffer.write((status.code() + " " + status.reasonPhrase()).getBytes(StandardCharsets.US_ASCII));
            }
            buffer.write('\r');
            buffer.write('\n');
        }
        // date header
        if (!headers.contains(Http.Header.DATE)) {
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
        writeHeaders(headers, buffer);

        buffer.write('\r');        // "\r\n" - empty line after headers
        buffer.write('\n');
    }

    @Override
    public Http1ServerResponse header(HeaderValue header) {
        this.headers.set(header);
        return this;
    }

    // actually send the response over the wire
    @Override
    public void send(byte[] bytes) {
        byte[] entity = entityBytes(bytes);
        BufferData bufferData = responseBuffer(entity);
        entitySize = bufferData.available();
        request.reset();
        dataWriter.write(bufferData);
        afterSend();
    }

    @Override
    public boolean isSent() {
        return isSent;
    }

    @Override
    public OutputStream outputStream() {
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
                                                     keepAlive);

        int writeBufferSize = ctx.listenerContext().config().writeBufferSize();
        outputStream = new ClosingBufferedOutputStream(bos, writeBufferSize);
        return contentEncode(outputStream);
    }

    @Override
    public long bytesWritten() {
        if (streamingEntity) {
            return outputStream.totalBytesWritten();
        } else {
            return entitySize;
        }
    }

    @Override
    public ServerResponseHeaders headers() {
        return headers;
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
        for (SinkProvider p : SINK_PROVIDERS) {
            if (p.supports(sinkType, request)) {
                return (X) p.create(this,
                        (e, m) -> handleSinkData(e, (MediaType) m),
                        this::commit);
            }
        }
        // Request not acceptable if provider not found
        throw new HttpException("Unable to find sink provider for request", Http.Status.NOT_ACCEPTABLE_406);
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
                    resHeaders.set(Http.Header.CONTENT_TYPE, mediaType.text());
                    EntityWriter<Object> writer = mediaContext.writer(type, EMPTY_HEADERS, resHeaders);
                    writer.write(type, data, outputStream, EMPTY_HEADERS, resHeaders);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeHeaders(Headers headers, BufferData buffer) {
        for (HeaderValue header : headers) {
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
        isSent = true;

        headers.setIfAbsent(HeaderValues.CONNECTION_KEEP_ALIVE);
        if (!headers.contains(Http.Header.CONTENT_LENGTH)) {
            headers.set(Http.Header.create(Http.Header.CONTENT_LENGTH, String.valueOf(bytes.length)));
        }

        sendListener.headers(ctx, headers);

        // give some space for code and headers + entity
        BufferData responseBuffer = BufferData.growing(256 + bytes.length);

        nonEntityBytes(headers, status(), responseBuffer, keepAlive);
        if (bytes.length > 0) {
            responseBuffer.write(bytes);
        }

        sendListener.data(ctx, responseBuffer);

        return responseBuffer;
    }

    private static class BlockingOutputStream extends OutputStream {
        private final ServerResponseHeaders headers;
        private final WritableHeaders<?> trailers;
        private final Supplier<Http.Status> status;
        private final DataWriter dataWriter;
        private final Runnable responseCloseRunnable;
        private final ConnectionContext ctx;
        private final Http1ConnectionListener sendListener;
        private final Http1ServerRequest request;
        private final boolean keepAlive;
        private final Supplier<String> streamResult;
        private final boolean forcedChunked;

        private BufferData firstBuffer;
        private boolean closed;
        private long bytesWritten;
        private long contentLength;
        private boolean isChunked;
        private boolean firstByte = true;
        private long responseBytesTotal;
        private boolean closing = false;

        private BlockingOutputStream(ServerResponseHeaders headers,
                                     WritableHeaders<?> trailers,
                                     Supplier<Http.Status> status,
                                     Supplier<String> streamResult,
                                     DataWriter dataWriter,
                                     Runnable responseCloseRunnable,
                                     ConnectionContext ctx,
                                     Http1ConnectionListener sendListener,
                                     Http1ServerRequest request,
                                     boolean keepAlive) {
            this.headers = headers;
            this.trailers = trailers;
            this.status = status;
            this.streamResult = streamResult;
            this.dataWriter = dataWriter;
            this.responseCloseRunnable = responseCloseRunnable;
            this.ctx = ctx;
            this.sendListener = sendListener;
            this.isChunked = !headers.contains(Http.Header.CONTENT_LENGTH);
            this.contentLength = headers.contentLength().orElse(-1);
            this.request = request;
            this.keepAlive = keepAlive;
            this.forcedChunked = headers.contains(HeaderValues.TRANSFER_ENCODING_CHUNKED);
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
            if (firstByte) {
                if (forcedChunked && firstBuffer != null) {
                    // no sense in sending no data, only do this if chunked requested through a header
                    sendHeadersAndPrepare();
                    writeChunked(firstBuffer);
                    terminatingChunk();
                } else {
                    sendFirstChunkOnly();
                }
            } else if (isChunked) {
                terminatingChunk();
            }

            if (isChunked || forcedChunked) {
                if (request.headers().contains(HeaderValues.TE_TRAILERS)) {
                    // not optimized, trailers enabled: we need to write trailers
                    trailers.set(STREAM_STATUS_NAME, String.valueOf(status.get().code()));
                    trailers.set(STREAM_RESULT_NAME, streamResult.get());
                    BufferData buffer = BufferData.growing(128);
                    writeHeaders(trailers, buffer);
                    buffer.write('\r');        // "\r\n" - empty line after headers
                    buffer.write('\n');
                    dataWriter.write(buffer);
                }
            }

            responseCloseRunnable.run();
            try {
                super.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        long totalBytesWritten() {
            return responseBytesTotal;
        }

        private void terminatingChunk() {
            BufferData terminatingChunk = BufferData.create(TERMINATING_CHUNK);
            sendListener.data(ctx, terminatingChunk);
            dataWriter.write(terminatingChunk);
        }

        private void write(BufferData buffer) throws IOException {
            if (closed) {
                throw new IOException("Stream already closed");
            }

            if (!isChunked) {
                if (firstByte) {
                    firstByte = false;
                    sendListener.headers(ctx, headers);
                    // write headers and payload part in one buffer to avoid TCP/ACK delay problems
                    BufferData growing = BufferData.growing(256 + buffer.available());
                    nonEntityBytes(headers, status.get(), growing, keepAlive);
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
                headers.set(Http.Header.create(Http.Header.CONTENT_LENGTH, String.valueOf(firstBuffer.available())));
                contentLength = firstBuffer.available();
            }
            isChunked = false;
            headers.remove(Http.Header.TRANSFER_ENCODING);

            // at this moment, we must send headers
            sendListener.headers(ctx, headers);
            BufferData bufferData = BufferData.growing(contentLength + 256);
            nonEntityBytes(headers, status.get(), bufferData, keepAlive);

            if (firstBuffer != null) {
                bufferData.write(firstBuffer);
            }

            sendListener.data(ctx, bufferData);
            responseBytesTotal += bufferData.available();
            dataWriter.write(bufferData);
        }

        private void sendHeadersAndPrepare() {
            if (headers.contains(Http.Header.CONTENT_LENGTH)) {
                contentLength = headers.contentLength().orElse(-1);
                isChunked = false;
            } else {
                contentLength = -1;
                // Add chunked encoding, if there is no other transfer-encoding headers
                if (!headers.contains(Http.Header.TRANSFER_ENCODING)) {
                    headers.set(HeaderValues.TRANSFER_ENCODING_CHUNKED);
                } else {
                    // Add chunked encoding, if it's not part of existing transfer-encoding headers
                    if (!headers.contains(HeaderValues.TRANSFER_ENCODING_CHUNKED)) {
                        headers.add(HeaderValues.TRANSFER_ENCODING_CHUNKED);
                    }
                }
            }

            // at this moment, we must send headers
            sendListener.headers(ctx, headers);
            BufferData bufferData = BufferData.growing(256);
            nonEntityBytes(headers, status.get(), bufferData, keepAlive);
            sendListener.data(ctx, bufferData);
            responseBytesTotal += bufferData.available();
            dataWriter.write(bufferData);
        }

        private void writeChunked(BufferData buffer) {
            int available = buffer.available();
            byte[] hex = Integer.toHexString(available).getBytes(StandardCharsets.UTF_8);

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

        private void checkContentLength(BufferData buffer) throws IOException {
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
     * A buffered output stream that wraps a {@link BlockingOutputStream} and informs
     * it before it is finally flushed and closed.
     */
    static class ClosingBufferedOutputStream extends BufferedOutputStream {

        private final BlockingOutputStream delegate;

        ClosingBufferedOutputStream(BlockingOutputStream out, int size) {
            super(out, size);
            this.delegate = out;
        }

        @Override
        public void close() {
            delegate.closing();     // inform of imminent call to close for last flush
            try {
                super.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        long totalBytesWritten() {
            return delegate.totalBytesWritten();
        }

        void commit() {
            try {
                flush();
                delegate.commit();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
