/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.webclient.http2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.context.Context;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.InstanceWriter;
import io.helidon.http.media.MediaContext;
import io.helidon.webclient.api.ClientRequest;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.EntityWriterPreflight;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;

class Http2CallEntityChain extends Http2CallChainBase {
    private final CompletableFuture<WebClientServiceRequest> whenSent;
    private final RequestEntity requestEntity;

    Http2CallEntityChain(Http2ClientImpl http2Client,
                         Http2ClientRequestImpl request,
                         CompletableFuture<WebClientServiceRequest> whenSent,
                         CompletableFuture<WebClientServiceResponse> whenComplete,
                         Object entity) {
        this(http2Client, request, whenSent, whenComplete, RequestEntity.create(entity));
    }

    Http2CallEntityChain(Http2ClientImpl http2Client,
                         Http2ClientRequestImpl request,
                         CompletableFuture<WebClientServiceRequest> whenSent,
                         CompletableFuture<WebClientServiceResponse> whenComplete,
                         RequestEntity requestEntity) {
        super(http2Client,
              request,
              whenComplete,
              new Http1FallbackHandler(whenSent,
                                       http1Request -> requestEntity.bytes == null
                                               ? http1Request.outputStream(requestEntity::writeTo)
                                               : http1Request.submit(requestEntity.bytes),
                                       requestEntity::isEmpty,
                                       request::responseCookiesDeferred,
                                       request::handoffProtocolResponse));
        this.whenSent = whenSent;
        this.requestEntity = requestEntity;
    }

    @Override
    protected void prepareRequest(WebClientServiceRequest request) {
        requestEntity.prepare(request.headers(),
                              clientConfig().mediaContext(),
                              clientConfig().maxInMemoryEntity(),
                              clientConfig().writeBufferSize(),
                              request.context());
        if (request.method() == Method.QUERY && !request.headers().contains(HeaderNames.CONTENT_TYPE)) {
            throw new IllegalArgumentException("Content-Type header is required for method '" + Method.QUERY + "'");
        }
    }

    @Override
    protected WebClientServiceResponse doProceed(WebClientServiceRequest serviceRequest,
                                                 ClientRequestHeaders headers,
                                                 Http2ClientStream stream) {
        if (!clientRequest().outputStreamRedirect()
                && requestEntity.hasEntity()
                && clientRequest().sendExpectContinue().orElse(clientConfig().sendExpectContinue())) {
            headers.set(HeaderValues.EXPECT_100);
        }
        if (!clientRequest().outputStreamRedirect() && requestEntity.contentLength >= 0) {
            headers.set(HeaderValues.create(HeaderNames.CONTENT_LENGTH, requestEntity.contentLength));
        }

        ClientUri uri = serviceRequest.uri();
        Http2Headers http2Headers = prepareHeaders(serviceRequest.method(), headers, uri);
        stream.writeHeaders(http2Headers, !clientRequest().outputStreamRedirect() && requestEntity.isEmpty());
        stream.incrementInboundWindowSize(clientRequest().requestPrefetch());

        Status continueStatus = waitFor100Continue(stream, clientRequest().readContinueTimeout());
        if (continueStatus != null && continueStatus != Status.CONTINUE_100) {
            whenSent.complete(serviceRequest);
            return clientRequest().outputStreamRedirect()
                    ? readResponse(serviceRequest, stream, clientRequest().readContinueTimeout())
                    : readResponse(serviceRequest, stream);
        }

        if (requestEntity.bytes != null) {
            if (requestEntity.bytes.length != 0) {
                stream.writeData(BufferData.create(requestEntity.bytes), true);
            }
        } else {
            EntityOutputStream outputStream = new EntityOutputStream(stream, requestEntity.contentLength);
            try {
                requestEntity.writeTo(outputStream);
            } catch (IOException e) {
                throw new UncheckedIOException("HTTP/2 request entity production failed", e);
            }
            if (!outputStream.closed) {
                throw new IllegalStateException("HTTP/2 request entity writer did not close the output stream");
            }
        }
        whenSent.complete(serviceRequest);

        return clientRequest().outputStreamRedirect()
                ? readResponse(serviceRequest, stream, clientRequest().readContinueTimeout())
                : readResponse(serviceRequest, stream);
    }

    static final class RequestEntity {
        private final AtomicBoolean streamHandlerClaimed = new AtomicBoolean();
        private Object entity;
        private byte[] bytes;
        private ClientRequest.OutputStreamHandler streamHandler;
        private EntityWriterPreflight preflight;
        private EntityWriterPreflight.HeaderChanges headerChanges;
        private EntityWriterPreflight.Application currentApplication;
        private long contentLength = -1;
        private boolean prepared;

        private RequestEntity(Object entity) {
            if (entity instanceof byte[] entityBytes) {
                this.bytes = entityBytes;
                this.contentLength = entityBytes.length;
                this.prepared = true;
            } else {
                this.entity = entity;
            }
        }

        static RequestEntity create(Object entity) {
            return new RequestEntity(entity);
        }

        byte[] bytes() {
            if (bytes == null) {
                throw new IllegalStateException("Request entity has not been prepared");
            }
            return bytes;
        }

        boolean hasEntity() {
            return bytes == null ? contentLength != 0 : bytes.length > 0;
        }

        boolean canStartAttempt() {
            if (preflight != null) {
                return preflight.canAttach();
            }
            return prepared ? bytes != null || !streamHandlerClaimed.get() : true;
        }

        boolean isEmpty() {
            return bytes != null && bytes.length == 0;
        }

        void discard() {
            cancelIfUnattached();
            entity = BufferData.EMPTY_BYTES;
            bytes = BufferData.EMPTY_BYTES;
            streamHandler = null;
            preflight = null;
            contentLength = 0;
            headerChanges = null;
            currentApplication = null;
            prepared = true;
        }

        void prepare(ClientRequestHeaders headers,
                     MediaContext mediaContext,
                     long maxInMemoryEntity,
                     int writeBufferSize,
                     Context context) {
            if (!prepared) {
                EntityWriterPreflight.HeaderRecorder recordingHeaders = EntityWriterPreflight.record(headers);
                Object object = entity;
                GenericType<Object> genericType = GenericType.create(object);
                EntityWriter<Object> writer = mediaContext.writer(genericType, recordingHeaders);
                long configuredContentLength = headers.contentLength().orElse(-1);
                EntityWriterPreflight.Application writerApplication = null;
                if (writer.supportsInstanceWriter()) {
                    InstanceWriter instanceWriter = writer.instanceWriter(genericType, object, recordingHeaders);
                    if (instanceWriter.alwaysInMemory()) {
                        bytes = instanceWriter.instanceBytes();
                    } else {
                        contentLength = instanceWriter.contentLength().orElse(configuredContentLength);
                        if (contentLength < 0 || contentLength > maxInMemoryEntity) {
                            streamHandler = instanceWriter::write;
                        } else {
                            bytes = instanceWriter.instanceBytes();
                        }
                    }
                } else if (configuredContentLength < 0 || configuredContentLength > maxInMemoryEntity) {
                    contentLength = configuredContentLength;
                    int maximum = (int) Math.max(1, Math.min(Integer.MAX_VALUE, maxInMemoryEntity));
                    int desiredBuffer = writeBufferSize <= 1 ? 1024 : writeBufferSize;
                    preflight = EntityWriterPreflight.create(Math.max(1, Math.min(desiredBuffer, maximum)),
                                                             context,
                                                             (outputStream, isolatedHeaders) -> writer.write(
                                                                     genericType,
                                                                     object,
                                                                     outputStream,
                                                                     isolatedHeaders));
                    writerApplication = preflight.prepare(headers);
                } else {
                    ByteArrayOutputStream bufferedEntity = new ByteArrayOutputStream((int) configuredContentLength);
                    OutputStream outputStream = new OutputStream() {
                        @Override
                        public void write(int value) throws IOException {
                            checkCapacity(1);
                            bufferedEntity.write(value);
                        }

                        @Override
                        public void write(byte[] bytes, int offset, int length) throws IOException {
                            checkCapacity(length);
                            bufferedEntity.write(bytes, offset, length);
                        }

                        private void checkCapacity(int additionalBytes) throws IOException {
                            if ((long) bufferedEntity.size() + additionalBytes > maxInMemoryEntity) {
                                throw new IOException("HTTP/2 request entity writer exceeded the configured in-memory "
                                                              + "limit of " + maxInMemoryEntity + " bytes");
                            }
                        }
                    };
                    writer.write(genericType, object, outputStream, recordingHeaders);
                    bytes = bufferedEntity.toByteArray();
                }
                entity = null;
                prepared = true;
                headerChanges = recordingHeaders.changes();
                if (preflight != null) {
                    headerChanges = headerChanges.andThen(preflight.headerChanges());
                }
                currentApplication = recordingHeaders.application();
                if (writerApplication != null) {
                    currentApplication = currentApplication.andThen(writerApplication);
                }
            }

            if (bytes != null) {
                contentLength = bytes.length;
            }
        }

        void applyPreparedHeaders(ClientRequestHeaders headers) {
            restoreHeadersBeforePreparation(headers);
            if (headerChanges != null && !headerChanges.isEmpty()) {
                currentApplication = headerChanges.apply(headers);
            }
        }

        void restoreHeadersBeforePreparation(ClientRequestHeaders headers) {
            if (currentApplication != null) {
                currentApplication.rollback(headers);
                currentApplication = null;
            }
        }

        void writeTo(OutputStream outputStream) throws IOException {
            if (preflight != null) {
                preflight.writeTo(outputStream);
                return;
            }
            if (!streamHandlerClaimed.compareAndSet(false, true)) {
                throw new IllegalStateException("HTTP/2 request entity is one-shot and has already been consumed");
            }
            streamHandler.handle(outputStream);
        }

        void cancelIfUnattached() {
            if (preflight != null) {
                preflight.cancelIfUnattached(new CancellationException(
                        "HTTP/2 request completed before entity writer attachment"));
            }
        }
    }

    private static final class EntityOutputStream extends OutputStream {
        private static final BufferData TERMINATING = BufferData.empty();

        private final Http2ClientStream stream;
        private final long contentLength;
        private long bytesWritten;
        private boolean closed;

        private EntityOutputStream(Http2ClientStream stream, long contentLength) {
            this.stream = stream;
            this.contentLength = contentLength;
        }

        @Override
        public void write(int value) throws IOException {
            byte[] data = {(byte) value};
            write(data, 0, 1);
        }

        @Override
        public void write(byte[] data, int offset, int length) throws IOException {
            if (closed) {
                throw new IOException("HTTP/2 request entity output stream is already closed");
            }
            long newLength = bytesWritten + length;
            if (contentLength >= 0 && newLength > contentLength) {
                throw new IOException("Content length was set to " + contentLength
                                              + ", but the request entity writer produced " + newLength + " bytes");
            }
            stream.writeData(BufferData.create(data, offset, length), false);
            bytesWritten = newLength;
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            if (contentLength >= 0 && bytesWritten != contentLength) {
                throw new IOException("Content length was set to " + contentLength
                                              + ", but the request entity writer produced " + bytesWritten + " bytes");
            }
            stream.writeData(TERMINATING, true);
            closed = true;
        }
    }
}
