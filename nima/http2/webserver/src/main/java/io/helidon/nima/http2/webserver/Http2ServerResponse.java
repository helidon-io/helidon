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

package io.helidon.nima.http2.webserver;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.Header;
import io.helidon.common.http.Http.HeaderValue;
import io.helidon.common.http.Http.HeaderValues;
import io.helidon.common.http.ServerResponseHeaders;
import io.helidon.nima.http2.FlowControl;
import io.helidon.nima.http2.Http2Flag;
import io.helidon.nima.http2.Http2Flag.DataFlags;
import io.helidon.nima.http2.Http2FrameData;
import io.helidon.nima.http2.Http2FrameHeader;
import io.helidon.nima.http2.Http2FrameTypes;
import io.helidon.nima.http2.Http2Headers;
import io.helidon.nima.http2.Http2StreamWriter;
import io.helidon.nima.webserver.ConnectionContext;
import io.helidon.nima.webserver.http.ServerResponseBase;

class Http2ServerResponse extends ServerResponseBase<Http2ServerResponse> {
    private static final System.Logger LOGGER = System.getLogger(Http2ServerResponse.class.getName());

    private final ConnectionContext ctx;
    private final Http2StreamWriter writer;
    private final int streamId;
    private final ServerResponseHeaders headers;
    private final FlowControl.Outbound flowControl;

    private boolean isSent;
    private boolean streamingEntity;
    private long bytesWritten;
    private BlockingOutputStream outputStream;

    Http2ServerResponse(ConnectionContext ctx,
                        Http2ServerRequest request,
                        Http2StreamWriter writer,
                        int streamId,
                        FlowControl.Outbound flowControl) {
        super(ctx, request);
        this.ctx = ctx;
        this.writer = writer;
        this.streamId = streamId;
        this.flowControl = flowControl;
        this.headers = ServerResponseHeaders.create();
    }

    @Override
    public Http2ServerResponse header(HeaderValue header) {
        headers.set(header);
        return this;
    }

    @Override
    public void send(byte[] entityBytes) {
        if (isSent) {
            throw new IllegalStateException("Response already sent");
        }
        if (streamingEntity) {
            throw new IllegalStateException("When output stream is used, response is completed by closing the output stream"
                                                    + ", do not call send().");
        }
        isSent = true;

        // handle content encoding
        byte[] bytes = entityBytes(entityBytes);

        headers.setIfAbsent(Header.create(Header.CONTENT_LENGTH,
                                          true,
                                          false,
                                          String.valueOf(bytes.length)));
        headers.setIfAbsent(Header.create(Header.DATE, true, false, Http.DateTime.rfc1123String()));

        Http2Headers http2Headers = Http2Headers.create(headers);
        http2Headers.status(status());
        headers.remove(Http2Headers.STATUS_NAME, it -> ctx.log(LOGGER,
                                                               System.Logger.Level.WARNING,
                                                               "Status must be configured on response, "
                                                                       + "do not set HTTP/2 pseudo headers"));

        Http2FrameData frameData = new Http2FrameData(Http2FrameHeader.create(bytes.length,
                                                                              Http2FrameTypes.DATA,
                                                                              DataFlags.create(Http2Flag.END_OF_STREAM),
                                                                              streamId),
                                                      BufferData.create(bytes));

        http2Headers.validateResponse();
        bytesWritten = writer.writeHeaders(http2Headers,
                                           streamId,
                                           Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                           frameData, flowControl);

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

        outputStream = new BlockingOutputStream(headers, writer, streamId, flowControl, status(), () -> {
            this.isSent = true;
            afterSend();
        });
        return contentEncode(outputStream);
    }

    @Override
    public long bytesWritten() {
        return streamingEntity ? outputStream.bytesWritten : bytesWritten;
    }

    @Override
    public ServerResponseHeaders headers() {
        return headers;
    }

    @Override
    public void streamResult(String result) {
        // TODO use this when closing the stream
    }

    @Override
    public boolean hasEntity() {
        return isSent || streamingEntity;
    }

    @Override
    public boolean reset() {
        if (isSent || outputStream != null && outputStream.bytesWritten > 0) {
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

    private static class BlockingOutputStream extends OutputStream {

        private final ServerResponseHeaders headers;
        private final Http2StreamWriter writer;
        private final int streamId;
        private final FlowControl.Outbound flowControl;
        private final Http.Status status;
        private final Runnable responseCloseRunnable;

        private BufferData firstBuffer;
        private boolean closed;
        private boolean firstByte = true;
        private long bytesWritten;

        private BlockingOutputStream(ServerResponseHeaders headers,
                                     Http2StreamWriter writer,
                                     int streamId,
                                     FlowControl.Outbound flowControl,
                                     Http.Status status,
                                     Runnable responseCloseRunnable) {

            this.headers = headers;
            this.writer = writer;
            this.streamId = streamId;
            this.flowControl = flowControl;
            this.status = status;
            this.responseCloseRunnable = responseCloseRunnable;
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
        public void flush() throws IOException {
            if (firstByte && firstBuffer != null) {
                write(BufferData.empty());
            }
        }

        @Override
        public void close() {
            // does nothing, we expect commit(), so we can reset response when no bytes were written to response
        }

        void commit() {
            if (closed) {
                return;
            }
            this.closed = true;
            if (firstByte) {
                sendFirstChunkOnly();
            } else {
                sendEndOfStream();
            }
            responseCloseRunnable.run();
            try {
                super.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private void write(BufferData buffer) throws IOException {
            if (closed) {
                throw new IOException("Stream already closed");
            }
            if (firstByte && firstBuffer == null) {
                firstBuffer = buffer;
                return;
            }

            if (firstByte) {
                sendHeadersAndPrepare();
                firstByte = false;
                writeChunk(BufferData.create(firstBuffer, buffer));
            } else {
                writeChunk(buffer);
            }
        }

        private void sendFirstChunkOnly() {
            int contentLength;
            if (firstBuffer == null) {
                headers.set(HeaderValues.CONTENT_LENGTH_ZERO);
                contentLength = 0;
            } else {
                headers.set(Header.create(Header.CONTENT_LENGTH,
                                          true,
                                          false,
                                          String.valueOf(firstBuffer.available())));
                contentLength = firstBuffer.available();
            }
            headers.setIfAbsent(Header.create(Header.DATE, true, false, Http.DateTime.rfc1123String()));

            Http2Headers http2Headers = Http2Headers.create(headers);
            http2Headers.status(status);
            http2Headers.validateResponse();

            // at this moment, we must send headers
            if (contentLength == 0) {
                int written = writer.writeHeaders(http2Headers,
                                                  streamId,
                                                  Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS
                                                                                       | Http2Flag.END_OF_STREAM),
                                                  flowControl);
                bytesWritten += written;
            } else {
                Http2FrameData frameData = new Http2FrameData(Http2FrameHeader.create(contentLength,
                                                                                      Http2FrameTypes.DATA,
                                                                                      DataFlags.create(Http2Flag.END_OF_STREAM),
                                                                                      streamId),
                                                              firstBuffer);
                int written = writer.writeHeaders(http2Headers,
                                                  streamId,
                                                  Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                                  frameData, flowControl);

                bytesWritten += written;
            }
        }

        private void sendHeadersAndPrepare() {
            headers.setIfAbsent(Header.create(Header.DATE, true, false, Http.DateTime.rfc1123String()));

            Http2Headers http2Headers = Http2Headers.create(headers);
            http2Headers.status(status);
            http2Headers.validateResponse();
            int written = writer.writeHeaders(http2Headers,
                                              streamId,
                                              Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                              flowControl);

            bytesWritten += written;
        }

        private void writeChunk(BufferData buffer) {
            Http2FrameData frameData = new Http2FrameData(Http2FrameHeader.create(buffer.available(),
                                                                                  Http2FrameTypes.DATA,
                                                                                  DataFlags.create(0),
                                                                                  streamId),
                                                          buffer);
            bytesWritten += frameData.header().length();
            bytesWritten += Http2FrameHeader.LENGTH;

            writer.writeData(frameData, flowControl);
        }

        private void sendEndOfStream() {
            Http2FrameData frameData = new Http2FrameData(Http2FrameHeader.create(0,
                                                                                  Http2FrameTypes.DATA,
                                                                                  DataFlags.create(Http2Flag.END_OF_STREAM),
                                                                                  streamId),
                                                          BufferData.empty());

            bytesWritten += frameData.header().length();
            bytesWritten += Http2FrameHeader.LENGTH;
            writer.writeData(frameData, flowControl);
        }
    }
}
