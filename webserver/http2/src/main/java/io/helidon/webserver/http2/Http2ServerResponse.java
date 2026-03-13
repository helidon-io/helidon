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

package io.helidon.webserver.http2;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import io.helidon.common.GenericType;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.buffers.BufferData;
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
import io.helidon.http.http2.Http2Exception;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.media.EntityWriter;
import io.helidon.webserver.CloseConnectionException;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ServerConnectionException;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http.ServerResponseBase;
import io.helidon.webserver.http.spi.Sink;
import io.helidon.webserver.http.spi.SinkProvider;
import io.helidon.webserver.http.spi.SinkProviderContext;

class Http2ServerResponse extends ServerResponseBase<Http2ServerResponse> {
    private static final System.Logger LOGGER = System.getLogger(Http2ServerResponse.class.getName());

    private final ConnectionContext ctx;
    private final ServerResponseHeaders headers;
    private final ServerResponseTrailers trailers;
    private final Http2ServerRequest request;
    private final Http2ServerStream stream;

    private boolean isSent;
    private boolean streamingEntity;
    private long bytesWritten;
    private static final List<SinkProvider> SINK_PROVIDERS =
            HelidonServiceLoader
                    .builder(ServiceLoader.load(SinkProvider.class))
                    .build()
                    .asList();

    private BlockingOutputStream outputStream;
    private UnaryOperator<OutputStream> outputStreamFilter;
    private String streamResult = null;

    Http2ServerResponse(Http2ServerStream stream,
                        Http2ServerRequest request) {
        super(stream.connectionContext(), request);
        this.ctx = stream.connectionContext();
        this.request = request;
        this.stream = stream;
        this.headers = ServerResponseHeaders.create();
        this.trailers = ServerResponseTrailers.create();
    }

    @Override
    public Http2ServerResponse header(Header header) {
        if (streamingEntity) {
            throw new IllegalStateException("Cannot set response header after requesting output stream.");
        }
        if (isSent()) {
            throw new IllegalStateException("Cannot set response header after response was already sent.");
        }
        headers.set(header);
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <X extends Sink<?>> X sink(GenericType<X> sinkType) {
        for (SinkProvider<?> p : SINK_PROVIDERS) {
            if (p.supports(sinkType, request)) {
                try {
                    X sink = (X) p.create(new SinkProviderContext() {
                        @Override
                        public ServerResponse serverResponse() {
                            return Http2ServerResponse.this;
                        }

                        @Override
                        public ServerRequest serverRequest() {
                            return Http2ServerResponse.this.request;
                        }

                        @Override
                        public ConnectionContext connectionContext() {
                            return Http2ServerResponse.this.ctx;
                        }

                        @Override
                        public Runnable closeRunnable() {
                            return () -> {
                                Http2ServerResponse.this.isSent = true;
                                request.reset();
                            };
                        }
                    });
                    this.isSent = true;
                    return sink;
                } catch (UnsupportedOperationException e) {
                    // deprecated - will be removed in 5.x
                    X sink = (X) p.create(this, this::handleSinkData, this::commit);
                    this.isSent = true;
                    return sink;
                }
            }
        }
        // Request not acceptable if provider not found
        throw new HttpException("Unable to find sink provider for request", Status.NOT_ACCEPTABLE_406);
    }

    @Override
    public void send(byte[] entityBytes) {
        send(entityBytes, 0, entityBytes.length);
    }


    @Override
    public void send(byte[] entityBytes, int position, int length) {
        try {
            if (outputStreamFilter != null) {
                // in this case we must honor user's request to filter the stream
                try (OutputStream os = outputStream()) {
                    os.write(entityBytes, position, length);
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
            int actualLength = length;
            int actualPosition = position;
            byte[] actualBytes = entityBytes(entityBytes, position, length);
            if (entityBytes != actualBytes) {       // encoding happened, new byte array
                actualPosition = 0;
                actualLength = actualBytes.length;
            }

            headers.setIfAbsent(HeaderValues.create(HeaderNames.CONTENT_LENGTH,
                                                    true,
                                                    false,
                                                    String.valueOf(actualLength)));
            headers.setIfAbsent(HeaderValues.create(HeaderNames.DATE, true,
                                                    false,
                                                    DateTime.rfc1123String()));
            Http2Headers http2Headers = Http2Headers.create(headers);
            http2Headers.status(status());
            headers.remove(Http2Headers.STATUS_NAME, it -> ctx.log(LOGGER,
                                                                   System.Logger.Level.WARNING,
                                                                   "Status must be configured on response, "
                                                                           + "do not set HTTP/2 pseudo headers"));

            // we only send trailers if they are present in the response headers, as this indicate there will be
            // trailers set later on
            // even when client sends "te: trailers", we do not send trailers unless we have them
            boolean sendTrailers = sendTrailers(headers);

            beforeSend();

            http2Headers.validateResponse();
            bytesWritten += stream.writeHeadersWithData(http2Headers, actualLength,
                                                        BufferData.create(actualBytes, actualPosition, actualLength),
                                                        !sendTrailers);

            if (sendTrailers) {
                Consumer<ServerResponseTrailers> beforeTrailers = beforeTrailers();
                if (beforeTrailers != null) {
                    beforeTrailers.accept(trailers);
                }
                bytesWritten += stream.writeTrailers(Http2Headers.create(trailers));
            }

            afterSend();
        } catch (Http2Exception e) {
            throw new CloseConnectionException("Failed writing entity", e);
        } catch (UncheckedIOException e) {
            throw new ServerConnectionException("Failed writing entity", e);
        }
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

        beforeSend();

        outputStream = new BlockingOutputStream(request, this, () -> {
            this.isSent = true;
            afterSend();
        }, beforeTrailers());
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
        if (sendTrailers(headers)) {
            return trailers;
        }
        throw new IllegalStateException(
                "Trailers are supported only when response headers have trailer names definition 'Trailer: <trailer-name>'");
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
    public boolean resetStream() {
        if (isSent || outputStream != null && outputStream.bytesWritten > 0) {
            return false;
        }
        streamingEntity = false;
        outputStream = null;
        return true;
    }

    private static final WritableHeaders<?> EMPTY_HEADERS = WritableHeaders.create();

    private void handleSinkData(Object data, MediaType mediaType) {
        if (outputStream == null) {
            outputStream();
        }
        try {
            io.helidon.http.media.MediaContext mediaContext = mediaContext();

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

    private static boolean sendTrailers(ServerResponseHeaders headers) {
        return headers.contains(HeaderNames.TRAILER);
    }

    private static class BlockingOutputStream extends OutputStream {

        private final Http2ServerRequest request;
        private final ServerResponseHeaders headers;
        private final ServerResponseTrailers trailers;
        private final Status status;
        private final Runnable responseCloseRunnable;
        private final Http2ServerResponse response;
        private final Http2ServerStream stream;

        private BufferData firstBuffer;
        private boolean closed;
        private boolean firstByte = true;
        private long bytesWritten;
        private final Consumer<ServerResponseTrailers> beforeTrailers;

        private BlockingOutputStream(Http2ServerRequest request,
                                     Http2ServerResponse response,
                                     Runnable responseCloseRunnable,
                                     Consumer<ServerResponseTrailers> beforeTrailers) {
            this.request = request;
            this.response = response;
            this.headers = response.headers;
            this.trailers = response.trailers;
            this.stream = response.stream;
            this.status = response.status();
            this.responseCloseRunnable = responseCloseRunnable;
            this.beforeTrailers = beforeTrailers;
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
            boolean sendTrailers = Http2ServerResponse.sendTrailers(headers);

            if (firstByte) {
                // if sendTrailers, will not send end-of-stream
                sendFirstChunkOnly(sendTrailers);
                if (sendTrailers) {
                    // send trailers and end-of-stream
                    sendTrailers();
                }
            } else if (sendTrailers) {
                // send trailers and end-of-stream
                sendTrailers();
            } else {
                // just send end-of-stream
                bytesWritten += stream.writeData(BufferData.empty(), true);
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
                bytesWritten += stream.writeData(BufferData.create(firstBuffer, buffer), false);
            } else {
                bytesWritten += stream.writeData(buffer, false);
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
                bytesWritten += stream.writeHeaders(http2Headers, !sendTrailers);
            } else {
                bytesWritten += stream.writeHeadersWithData(http2Headers, contentLength, firstBuffer, !sendTrailers);
            }
        }

        private void sendHeadersAndPrepare() {
            headers.setIfAbsent(HeaderValues.create(HeaderNames.DATE, true, false, DateTime.rfc1123String()));

            Http2Headers http2Headers = Http2Headers.create(headers);
            http2Headers.status(status);
            http2Headers.validateResponse();

            bytesWritten += stream.writeHeaders(http2Headers, false);
        }

        private void sendTrailers(){
            if (headers.contains(HeaderNames.TRAILER)
                    && headers.get(HeaderNames.TRAILER).allValues().contains("stream-result")) {
                // only send if configured
                if (response.streamResult == null) {
                    // we must set the trailer, as we announced it
                    trailers.set(STREAM_RESULT_OK);
                } else {
                    trailers.set(STREAM_RESULT_NAME, response.streamResult);
                }
            }
            if (beforeTrailers != null) {
                beforeTrailers.accept(ServerResponseTrailers.wrap(trailers));
            }

            Http2Headers http2Headers = Http2Headers.create(trailers);
            bytesWritten += stream.writeTrailers(http2Headers);
        }
    }
}
