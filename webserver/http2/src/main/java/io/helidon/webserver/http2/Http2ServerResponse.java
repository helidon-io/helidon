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

package io.helidon.webserver.http2;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.function.UnaryOperator;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.DateTime;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.ServerResponseTrailers;
import io.helidon.http.Status;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2Flag.DataFlags;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2StreamWriter;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.http.ServerResponseBase;

class Http2ServerResponse extends ServerResponseBase<Http2ServerResponse> {
    private static final System.Logger LOGGER = System.getLogger(Http2ServerResponse.class.getName());

    private final ConnectionContext ctx;
    private final Http2StreamWriter writer;
    private final int streamId;
    private final ServerResponseHeaders headers;
    private final ServerResponseTrailers trailers;
    private final FlowControl.Outbound flowControl;
    private final Http2ServerRequest request;

    private boolean isSent;
    private boolean streamingEntity;
    private long bytesWritten;
    private BlockingOutputStream outputStream;
    private UnaryOperator<OutputStream> outputStreamFilter;
    private String streamResult = null;

    Http2ServerResponse(ConnectionContext ctx,
                        Http2ServerRequest request,
                        Http2StreamWriter writer,
                        int streamId,
                        FlowControl.Outbound flowControl) {
        super(ctx, request);
        this.ctx = ctx;
        this.request = request;
        this.writer = writer;
        this.streamId = streamId;
        this.flowControl = flowControl;
        this.headers = ServerResponseHeaders.create();
        this.trailers = ServerResponseTrailers.create();
    }

    @Override
    public Http2ServerResponse header(Header header) {
        headers.set(header);
        return this;
    }

    @Override
    public void send(byte[] entityBytes) {
        if (outputStreamFilter != null) {
            // in this case we must honor user's request to filter the stream
            try (OutputStream os = outputStream()) {
                os.write(entityBytes);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return;
        }

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

        headers.setIfAbsent(HeaderValues.create(HeaderNames.CONTENT_LENGTH,
                                                true,
                                                false,
                                                String.valueOf(bytes.length)));
        headers.setIfAbsent(HeaderValues.create(HeaderNames.DATE, true, false, DateTime.rfc1123String()));

        Http2Headers http2Headers = Http2Headers.create(headers);
        http2Headers.status(status());
        headers.remove(Http2Headers.STATUS_NAME, it -> ctx.log(LOGGER,
                                                               System.Logger.Level.WARNING,
                                                               "Status must be configured on response, "
                                                                       + "do not set HTTP/2 pseudo headers"));

        boolean sendTrailers = request.headers().contains(HeaderValues.TE_TRAILERS) || headers.contains(HeaderNames.TRAILER);

        Http2FrameData frameData =
                new Http2FrameData(Http2FrameHeader.create(bytes.length,
                                                           Http2FrameTypes.DATA,
                                                           DataFlags.create(sendTrailers ? 0 : Http2Flag.END_OF_STREAM),
                                                           streamId),
                                   BufferData.create(bytes));

        http2Headers.validateResponse();
        bytesWritten = writer.writeHeaders(http2Headers,
                                           streamId,
                                           Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                           frameData, flowControl);

        if (sendTrailers) {
            Http2Headers http2trailers = Http2Headers.create(trailers);
            int written = writer.writeHeaders(http2trailers,
                                              streamId,
                                              Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM),
                                              flowControl);
            bytesWritten += written;
        }

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

        if (request.headers().contains(HeaderValues.TE_TRAILERS)) {
            headers.add(STREAM_TRAILERS);
        }

        outputStream = new BlockingOutputStream(request, this, () -> {
            this.isSent = true;
            afterSend();
        });
        if (outputStreamFilter == null) {
            return contentEncode(outputStream);
        } else {
            return outputStreamFilter.apply(contentEncode(outputStream));
        }
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

    private static class BlockingOutputStream extends OutputStream {

        private final Http2ServerRequest request;
        private final ServerResponseHeaders headers;
        private final ServerResponseTrailers trailers;
        private final Http2StreamWriter writer;
        private final int streamId;
        private final FlowControl.Outbound flowControl;
        private final Status status;
        private final Runnable responseCloseRunnable;
        private final Http2ServerResponse response;

        private BufferData firstBuffer;
        private boolean closed;
        private boolean firstByte = true;
        private long bytesWritten;

        private BlockingOutputStream(Http2ServerRequest request,
                                     Http2ServerResponse response,
                                     Runnable responseCloseRunnable) {
            this.request = request;
            this.response = response;
            this.headers = response.headers;
            this.trailers = response.trailers;
            this.writer = response.writer;
            this.streamId = response.streamId;
            this.flowControl = response.flowControl;
            this.status = response.status();
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
            boolean sendTrailers =
                    request.headers().contains(HeaderValues.TE_TRAILERS) || headers.contains(HeaderNames.TRAILER);
            if (firstByte) {
                sendFirstChunkOnly(sendTrailers);
            } else if (sendTrailers) {
                sendTrailers();
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
                // if somebody re-uses the byte buffer sent to us, we must copy it
                firstBuffer = buffer.copy();
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

        private void sendFirstChunkOnly(boolean sendTrailers) {
            int contentLength;
            if (firstBuffer == null) {
                headers.set(HeaderValues.CONTENT_LENGTH_ZERO);
                contentLength = 0;
            } else {
                headers.set(HeaderValues.create(HeaderNames.CONTENT_LENGTH,
                                                true,
                                                false,
                                                String.valueOf(firstBuffer.available())));
                contentLength = firstBuffer.available();
            }
            headers.setIfAbsent(HeaderValues.create(HeaderNames.DATE, true, false, DateTime.rfc1123String()));

            Http2Headers http2Headers = Http2Headers.create(headers);
            http2Headers.status(status);
            http2Headers.validateResponse();

            // at this moment, we must send headers
            if (contentLength == 0) {
                int written = writer.writeHeaders(http2Headers,
                                                  streamId,
                                                  Http2Flag.HeaderFlags.create(
                                                          sendTrailers
                                                                  ? Http2Flag.END_OF_HEADERS
                                                                  : Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM),
                                                  flowControl);
                bytesWritten += written;
            } else {
                Http2FrameData frameData =
                        new Http2FrameData(Http2FrameHeader.create(contentLength,
                                                                   Http2FrameTypes.DATA,
                                                                   DataFlags.create(sendTrailers
                                                                                            ? 0
                                                                                            : Http2Flag.END_OF_STREAM),
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
            headers.setIfAbsent(HeaderValues.create(HeaderNames.DATE, true, false, DateTime.rfc1123String()));

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

        private void sendTrailers(){
            if (response.streamResult != null) {
                trailers.set(STREAM_RESULT_NAME, response.streamResult);
            }
            trailers.set(STREAM_STATUS_NAME, response.status().code());

            Http2Headers http2Headers = Http2Headers.create(trailers);
            int written = writer.writeHeaders(http2Headers,
                                              streamId,
                                              Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS
                                                                                   | Http2Flag.END_OF_STREAM),
                                              flowControl);
            bytesWritten += written;
        }
    }
}
