/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.DateTime;
import io.helidon.common.http.Http.HeaderName;
import io.helidon.common.http.Http.HeaderValue;
import io.helidon.common.http.Http.HeaderValues;
import io.helidon.common.http.ServerResponseHeaders;
import io.helidon.common.http.WritableHeaders;
import io.helidon.nima.webserver.ConnectionContext;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.nima.webserver.http.ServerResponseBase;

/*
HTTP/1.1 200 OK
Connection: keep-alive
Content-Length: 2
Date: Fri, 22 Oct 2021 16:47:41 +0200
hi
 */
class Http1ServerResponse extends ServerResponseBase<Http1ServerResponse> {
    private static final byte[] HTTP_BYTES = "HTTP/1.1 ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] OK_200 = "HTTP/1.1 200 OK\r\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] DATE = "Date: ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TERMINATING_CHUNK = "0\r\n\r\n".getBytes(StandardCharsets.UTF_8);
    private static final HeaderName STREAM_STATUS_NAME = Http.Header.create("stream-status");
    private static final HeaderName STREAM_RESULT_NAME = Http.Header.create("stream-result");
    private static final HeaderValue STREAM_TRAILERS =
            HeaderValue.create(Http.Header.TRAILER, STREAM_STATUS_NAME.defaultCase()
                    + "," + STREAM_RESULT_NAME.defaultCase());

    private final ConnectionContext ctx;
    private final Http1ConnectionListener sendListener;
    private final DataWriter dataWriter;
    private final Http1ServerRequest request;
    private final ServerResponseHeaders headers;
    private final WritableHeaders<?> trailers = WritableHeaders.create();
    private final boolean keepAlive;

    private boolean streamingEntity;
    private boolean isSent;
    private BlockingOutputStream outputStream;
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
            buffer.write((status.code() + " " + status.reasonPhrase()).getBytes(StandardCharsets.US_ASCII));
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
    public ServerResponse header(HeaderValue header) {
        this.headers.set(header);
        return this;
    }

    // actually send the response over the wire
    @Override
    public void send(byte[] bytes) {
        byte[] entity = entityBytes(bytes);
        BufferData bufferData = responseBuffer(entity);
        entitySize = bufferData.available();
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

        this.outputStream = new BlockingOutputStream(headers,
                                                     trailers,
                                                     this::status,
                                                     () -> streamResult,
                                                     dataWriter,
                                                     () -> {
                                                         this.isSent = true;
                                                         afterSend();
                                                     },
                                                     ctx,
                                                     sendListener,
                                                     request,
                                                     keepAlive);

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
            headers.set(HeaderValue.create(Http.Header.CONTENT_LENGTH, String.valueOf(bytes.length)));
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

        private BufferData firstBuffer;
        private boolean closed;
        private long bytesWritten;
        private long contentLength;
        private boolean isChunked;
        private boolean firstByte = true;
        private boolean forcedChunked;
        private long responseBytesTotal;

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

        @Override
        public void close() {
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
                // not optimized, we need to write trailers
                trailers.set(STREAM_STATUS_NAME.withValue(status.get().code()));
                trailers.set(STREAM_RESULT_NAME.withValue(streamResult.get()));
                BufferData buffer = BufferData.growing(128);
                writeHeaders(trailers, buffer);
                buffer.write('\r');        // "\r\n" - empty line after headers
                buffer.write('\n');
                dataWriter.write(buffer);
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
                    BufferData growing = BufferData.growing(256 + buffer.available());
                    nonEntityBytes(headers, status.get(), growing, keepAlive);
                    responseBytesTotal += growing.available();
                    dataWriter.write(growing);
                }

                // if not chunked, always write
                writeContent(buffer);
                return;
            }
            // try chunked data optimization
            if (firstByte && firstBuffer == null) {
                // if somebody re-uses the byte buffer sent to us, we must copy it
                firstBuffer = buffer.copy();
                return;
            }

            if (firstByte) {
                // proper stream with multiple buffers, write status amd headers
                headers.add(STREAM_TRAILERS);
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
                headers.set(HeaderValue.create(Http.Header.CONTENT_LENGTH, String.valueOf(firstBuffer.available())));
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
                if (!headers.contains(Http.Header.TRANSFER_ENCODING)) {
                    // todo check if contains other transfer encoding to combine them together
                    headers.set(HeaderValues.TRANSFER_ENCODING_CHUNKED);
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

        private void writeContent(BufferData buffer) throws IOException {
            bytesWritten += buffer.available();
            if (bytesWritten > contentLength && contentLength != -1) {
                throw new IOException("Content length was set to " + contentLength
                                              + ", but you are writing additional " + (bytesWritten - contentLength) + " "
                                              + "bytes");
            }

            sendListener.data(ctx, buffer);
            responseBytesTotal += buffer.available();
            dataWriter.write(buffer);
        }
    }
}
