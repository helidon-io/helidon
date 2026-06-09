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

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.uri.UriInfo;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Headers;
import io.helidon.webclient.api.ClientRequest;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.HttpClientConfig;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;

import static io.helidon.webclient.http2.RedirectionProcessor.checkRedirectHeaders;

class Http2CallOutputStreamChain extends Http2CallChainBase {
    private static final System.Logger LOGGER = System.getLogger(Http2CallOutputStreamChain.class.getName());

    private final CompletableFuture<WebClientServiceRequest> whenSent;
    private final ClientRequest.OutputStreamHandler streamHandler;
    private final EntityTrackingOutputStreamHandler fallbackStreamHandler;
    private final Http2ClientImpl client;
    private Http2ClientResponseImpl redirectedResponse;

    Http2CallOutputStreamChain(Http2ClientImpl http2Client,
                               Http2ClientRequestImpl http2ClientRequest,
                               CompletableFuture<WebClientServiceRequest> whenSent,
                               CompletableFuture<WebClientServiceResponse> whenComplete,
                               ClientRequest.OutputStreamHandler streamHandler) {
        this(http2Client,
             http2ClientRequest,
             whenSent,
             whenComplete,
             streamHandler,
             new EntityTrackingOutputStreamHandler(streamHandler));
    }

    Http2CallOutputStreamChain(Http2ClientImpl http2Client,
                               Http2ClientRequestImpl http2ClientRequest,
                               CompletableFuture<WebClientServiceRequest> whenSent,
                               CompletableFuture<WebClientServiceResponse> whenComplete,
                               ClientRequest.OutputStreamHandler streamHandler,
                               EntityTrackingOutputStreamHandler fallbackStreamHandler) {
        super(http2Client,
              http2ClientRequest,
              whenComplete,
              req -> req.outputStream(fallbackStreamHandler));

        this.client = http2Client;
        this.whenSent = whenSent;
        this.streamHandler = streamHandler;
        this.fallbackStreamHandler = fallbackStreamHandler;
    }

    @Override
    protected WebClientServiceResponse doProceed(WebClientServiceRequest serviceRequest,
                                                 ClientRequestHeaders headers,
                                                 Http2ClientStream stream) {
        boolean interrupted = false;
        ClientOutputStream outputStream = new ClientOutputStream(client,
                                                                 stream,
                                                                 headers,
                                                                 clientConfig(),
                                                                 serviceRequest,
                                                                 clientRequest(),
                                                                 whenSent,
                                                                 whenComplete());
        try {
            streamHandler.handle(outputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (OutputStreamInterruptedException e) {
            interrupted = true;
        }

        if (interrupted || outputStream.interrupted()) {
            //If cos is marked as interrupted, we know that our interrupted exception has been thrown, but
            //it was intercepted by the user OutputStreamHandler and not rethrown.
            //This is a fallback mechanism to correctly handle such a situations.
            stream(outputStream.stream);
            return outputStream.serviceResponse();
        } else if (!outputStream.closed()) {
            throw new IllegalStateException("Output stream was not closed in handler");
        }

        Http2Headers responseHeaders = readHeaders(outputStream.stream);

        if (clientRequest().followRedirects()
                && RedirectionProcessor.redirectionStatusCode(responseHeaders.status())) {
            checkRedirectHeaders(responseHeaders);
            URI newUri = URI.create(responseHeaders.httpHeaders().get(HeaderNames.LOCATION).get());
            ClientUri redirectUri = ClientUri.create(newUri);
            if (newUri.getHost() == null) {
                UriInfo resolvedUri = outputStream.lastRequest.resolvedUri();
                redirectUri.scheme(resolvedUri.scheme());
                redirectUri.host(resolvedUri.host());
                redirectUri.port(resolvedUri.port());
            }
            boolean sendEntity = RedirectionProcessor.keepsMethodAndEntity(responseHeaders.status());
            ClientRequest.OutputStreamHandler handler = streamHandler;
            if (sendEntity && !outputStream.lastRequest.canReplayEntityTo(redirectUri)) {
                // Replaying a 307/308 output-stream body to a new origin can leak credentials or form data.
                if (outputStream.hasEntity()) {
                    try {
                        outputStream.stream.cancel();
                    } finally {
                        outputStream.stream.close();
                    }
                    throw new IllegalStateException("Cross-origin redirect with request entity is disabled.");
                }
                handler = OutputStream::close;
            }
            Method redirectedMethod = sendEntity ? outputStream.lastRequest.method() : Method.GET;
            Http2ClientRequestImpl request = new Http2ClientRequestImpl(outputStream.lastRequest,
                                                                        redirectedMethod,
                                                                        redirectUri,
                                                                        outputStream.lastRequest.properties());
            request.outputStreamRedirect(false);
            request.readTimeout(outputStream.originalRequest.readTimeout());
            int numberOfRedirects = outputStream.numberOfRedirects() + 1;
            try {
                outputStream.stream.cancel();
            } finally {
                outputStream.stream.close();
            }
            if (numberOfRedirects > outputStream.lastRequest.maxRedirects()) {
                throw RedirectionProcessor.maxRedirectsReached(outputStream.lastRequest.maxRedirects());
            }
            if (sendEntity) {
                request.maxRedirects(outputStream.lastRequest.maxRedirects() - numberOfRedirects);
            }
            redirectedResponse = sendEntity
                    ? (Http2ClientResponseImpl) request.outputStream(handler)
                    : RedirectionProcessor.invokeWithFollowRedirects(request,
                                                                     numberOfRedirects,
                                                                     BufferData.EMPTY_BYTES);
            return redirectedResponse.toServiceResponse(serviceRequest, whenComplete());
        }

        stream(outputStream.stream);
        return createServiceResponse(serviceRequest,
                                     clientConfig(),
                                     outputStream.stream,
                                     whenComplete(),
                                     responseHeaders.status(),
                                     ClientResponseHeaders.create(responseHeaders.httpHeaders()));
    }

    @Override
    protected WebClientServiceResponse doProceed(WebClientServiceRequest serviceRequest, HttpClientResponse response) {
        // h2c fallback returns an HTTP/1 response before an HTTP/2 stream exists; redirect handling continues here.
        if (!clientRequest().followRedirects()
                || !RedirectionProcessor.redirectionStatusCode(response.status())) {
            return super.doProceed(serviceRequest, response);
        }

        ClientUri redirectUri;
        Method method;
        boolean sendEntity;
        try (response) {
            checkRedirectHeaders(response.headers());
            URI newUri = URI.create(response.headers().get(HeaderNames.LOCATION).get());
            redirectUri = ClientUri.create(newUri);
            if (newUri.getHost() == null) {
                UriInfo resolvedUri = clientRequest().resolvedUri();
                redirectUri.scheme(resolvedUri.scheme());
                redirectUri.host(resolvedUri.host());
                redirectUri.port(resolvedUri.port());
            }

            if (RedirectionProcessor.keepsMethodAndEntity(response.status())) {
                method = clientRequest().method();
                sendEntity = true;
            } else {
                method = Method.GET;
                sendEntity = false;
            }
        }

        if (clientRequest().maxRedirects() < 1) {
            throw RedirectionProcessor.maxRedirectsReached(clientRequest().maxRedirects());
        }

        Http2ClientRequestImpl redirectedRequest = new Http2ClientRequestImpl(clientRequest(),
                                                                              method,
                                                                              redirectUri,
                                                                              clientRequest().properties());
        redirectedRequest.readTimeout(clientRequest().readTimeout());
        redirectedRequest.maxRedirects(clientRequest().maxRedirects() - 1);
        if (sendEntity) {
            ClientRequest.OutputStreamHandler handler = streamHandler;
            if (!redirectedRequest.canReplayEntityTo(redirectUri)) {
                if (fallbackStreamHandler.invoked()) {
                    if (fallbackStreamHandler.hasEntity()) {
                        throw new IllegalStateException("Cross-origin redirect with request entity is disabled.");
                    }
                } else {
                    // Probe user code without sending anything; any write would be a cross-origin body replay.
                    verifyOutputStreamHasNoEntity();
                }
                handler = OutputStream::close;
            }
            redirectedResponse = (Http2ClientResponseImpl) redirectedRequest.outputStream(handler);
            return redirectedResponse.toServiceResponse(serviceRequest, whenComplete());
        }
        redirectedResponse = (Http2ClientResponseImpl) redirectedRequest.request();
        return redirectedResponse.toServiceResponse(serviceRequest, whenComplete());
    }

    @Override
    void closeResponse() {
        if (redirectedResponse != null) {
            redirectedResponse.close();
        } else {
            super.closeResponse();
        }
    }

    private void verifyOutputStreamHasNoEntity() {
        DetectingOutputStream detectingOutputStream = new DetectingOutputStream();
        try {
            streamHandler.handle(detectingOutputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (IllegalStateException e) {
            if (!detectingOutputStream.hasEntity()) {
                throw e;
            }
        }
        if (detectingOutputStream.hasEntity()) {
            throw new IllegalStateException("Cross-origin redirect with request entity is disabled.");
        }
    }

    private static final class DetectingOutputStream extends OutputStream {
        private boolean hasEntity;

        @Override
        public void write(int b) {
            hasEntity = true;
            throw new IllegalStateException("Cross-origin redirect with request entity is disabled.");
        }

        @Override
        public void write(byte[] b, int off, int len) {
            if (len > 0) {
                hasEntity = true;
                throw new IllegalStateException("Cross-origin redirect with request entity is disabled.");
            }
        }

        boolean hasEntity() {
            return hasEntity;
        }
    }

    static final class EntityTrackingOutputStreamHandler implements ClientRequest.OutputStreamHandler {
        private final ClientRequest.OutputStreamHandler delegate;
        private boolean invoked;
        private boolean hasEntity;

        EntityTrackingOutputStreamHandler(ClientRequest.OutputStreamHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public void handle(OutputStream stream) throws IOException {
            invoked = true;
            delegate.handle(new EntityTrackingOutputStream(stream, this));
        }

        private void markEntity() {
            hasEntity = true;
        }

        private boolean invoked() {
            return invoked;
        }

        private boolean hasEntity() {
            return hasEntity;
        }
    }

    private static final class EntityTrackingOutputStream extends OutputStream {
        private final OutputStream delegate;
        private final EntityTrackingOutputStreamHandler tracker;

        private EntityTrackingOutputStream(OutputStream delegate, EntityTrackingOutputStreamHandler tracker) {
            this.delegate = delegate;
            this.tracker = tracker;
        }

        @Override
        public void write(int b) throws IOException {
            tracker.markEntity();
            delegate.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (len > 0) {
                tracker.markEntity();
            }
            delegate.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    private static class ClientOutputStream extends OutputStream {

        private static final BufferData TERMINATING = BufferData.empty();
        private final WebClientServiceRequest request;
        private final Http2ClientRequestImpl originalRequest;
        private final CompletableFuture<WebClientServiceRequest> whenSent;
        private final CompletableFuture<WebClientServiceResponse> whenComplete;
        private final HttpClientConfig clientConfig;
        private final WritableHeaders<?> headers;
        private final long contentLength;

        private long bytesWritten;
        private boolean hasEntity;
        private boolean noData = true;
        private boolean closed;
        private boolean interrupted;
        private int numberOfRedirects = 0;
        private final Http2ClientImpl client;
        private Http2ClientStream stream;
        private Http2ClientRequestImpl lastRequest;
        private Http2ClientResponseImpl response;
        private WebClientServiceResponse serviceResponse;

        private ClientOutputStream(Http2ClientImpl client,
                                   Http2ClientStream stream,
                                   WritableHeaders<?> headers,
                                   HttpClientConfig clientConfig,
                                   WebClientServiceRequest request,
                                   Http2ClientRequestImpl originalRequest,
                                   CompletableFuture<WebClientServiceRequest> whenSent,
                                   CompletableFuture<WebClientServiceResponse> whenComplete) {
            this.client = client;
            this.stream = stream;
            this.headers = headers;
            this.clientConfig = clientConfig;
            this.contentLength = headers.contentLength().orElse(-1);
            this.request = request;
            this.originalRequest = originalRequest;
            this.lastRequest = originalRequest;
            this.whenSent = whenSent;
            this.whenComplete = whenComplete;
        }

        @Override
        public void write(int b) throws IOException {
            // this method should not be called, as we are wrapped with a buffered stream
            byte[] data = {(byte) b};
            write(data, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (interrupted) {
                //If this OS was interrupted, it becomes NOOP.
                return;
            } else if (closed) {
                throw new IOException("Output stream already closed");
            }
            if (len == 0) {
                return;
            }

            BufferData data = BufferData.create(b, off, len);
            if (len > 0) {
                hasEntity = true;
            }

            if (noData) {
                noData = false;
                sendHeader();
            }
            writeContent(data);
        }

        @Override
        public void close() throws IOException {
            if (closed || interrupted) {
                return;
            }
            this.closed = true;
            if (noData) {
                sendHeader();
            }
            stream.writeData(TERMINATING, true);
            super.close();
        }

        WebClientServiceResponse serviceResponse() {
            if (serviceResponse != null) {
                return serviceResponse;
            }

            return createServiceResponse(request,
                                         clientConfig,
                                         stream,
                                         whenComplete,
                                         response.status(),
                                         response.headers());
        }

        boolean closed() {
            return closed;
        }

        boolean interrupted() {
            return interrupted;
        }

        boolean hasEntity() {
            return hasEntity;
        }

        int numberOfRedirects() {
            return numberOfRedirects;
        }

        private void writeContent(BufferData buffer) throws IOException {
            bytesWritten += buffer.available();
            if (contentLength != -1 && bytesWritten > contentLength) {
                throw new IOException("Content length was set to " + contentLength
                                              + ", but you are writing additional " + (bytesWritten - contentLength) + " "
                                              + "bytes");
            }
            stream.writeData(buffer, false);
        }

        private void sendHeader() {
            if (originalRequest.sendExpectContinue().orElse(clientConfig.sendExpectContinue()) && !noData) {
                headers.set(HeaderValues.EXPECT_100);
            }

            Http2Headers http2Headers = prepareHeaders(request.method(),
                                                       ClientRequestHeaders.create(headers),
                                                       request.uri());

            stream.writeHeaders(http2Headers, false);
            whenSent.complete(request);

            if (headers.containsToken(HeaderValues.EXPECT_100)) {
                Status status = waitFor100Continue(stream, originalRequest.readContinueTimeout());

                if (status != Status.CONTINUE_100) {
                    Http2Headers responseHeaders = readHeaders(stream);
                    Status responseStatus = responseHeaders.status();

                    if (RedirectionProcessor.redirectionStatusCode(responseStatus) && originalRequest.followRedirects()) {
                        checkRedirectHeaders(responseHeaders);
                        redirect(responseStatus, responseHeaders.httpHeaders());
                    } else {
                        //OS changed its state to interrupted, that means other usage of this OS will result in NOOP actions.
                        this.interrupted = true;
                        this.serviceResponse = createServiceResponse(request,
                                                                     clientConfig,
                                                                     stream,
                                                                     whenComplete,
                                                                     responseHeaders.status(),
                                                                     ClientResponseHeaders.create(
                                                                             responseHeaders.httpHeaders()));
                        //we are not sending anything by this OS, we need to interrupt it.
                        throw new OutputStreamInterruptedException();
                    }
                }
            }
        }

        private void redirect(Status lastStatus, Headers headerValues) {
            String redirectedUri = headerValues.get(HeaderNames.LOCATION).get();
            ClientUri lastUri = originalRequest.uri();
            Method method;
            boolean sendEntity;
            if (lastStatus == Status.TEMPORARY_REDIRECT_307
                    || lastStatus == Status.PERMANENT_REDIRECT_308) {
                method = originalRequest.method();
                sendEntity = true;
            } else {
                method = Method.GET;
                sendEntity = false;
            }
            while (numberOfRedirects < originalRequest.maxRedirects()) {
                numberOfRedirects++;
                URI newUri = URI.create(redirectedUri);
                ClientUri redirectUri = ClientUri.create(newUri);
                if (newUri.getHost() == null) {
                    redirectUri.scheme(lastUri.scheme());
                    redirectUri.host(lastUri.host());
                    redirectUri.port(lastUri.port());
                }
                lastUri = redirectUri;
                // Queue RST_STREAM before releasing this stream's reservation, so redirected HEADERS
                // are serialized after the abandoned upload is reset for max-concurrent-streams peers.
                try {
                    stream.cancel();
                } finally {
                    stream.close();
                }
                boolean sendEmptyEntity = false;
                if (sendEntity && !lastRequest.canReplayEntityTo(redirectUri)) {
                    // User code already provided bytes for the original origin; do not replay them across origins.
                    if (hasEntity) {
                        throw new IllegalStateException("Cross-origin redirect with request entity is disabled.");
                    }
                    sendEmptyEntity = true;
                }
                Http2ClientRequestImpl clientRequest = new Http2ClientRequestImpl(lastRequest,
                                                                                  method,
                                                                                  redirectUri,
                                                                                  lastRequest.properties());
                clientRequest.followRedirects(false);
                clientRequest.readTimeout(originalRequest.readTimeout());
                try {
                    Http2ClientResponseImpl response;
                    if (sendEntity && !sendEmptyEntity) {
                        response = (Http2ClientResponseImpl) clientRequest
                                .outputStreamRedirect(true)
                                .header(HeaderValues.EXPECT_100)
                                .request();
                    } else {
                        response = (Http2ClientResponseImpl) clientRequest.outputStreamRedirect(false)
                                .request();
                    }
                    lastRequest = clientRequest;

                    stream = response.stream();

                    if (RedirectionProcessor.redirectionStatusCode(response.status())) {
                        try (response) {
                            checkRedirectHeaders(response.headers());
                            if (response.status() != Status.TEMPORARY_REDIRECT_307
                                    && response.status() != Status.PERMANENT_REDIRECT_308) {
                                method = Method.GET;
                                sendEntity = false;
                            }
                            redirectedUri = response.headers().get(HeaderNames.LOCATION).get();
                        }
                    } else {
                        if (!sendEntity || sendEmptyEntity) {
                            //OS changed its state to interrupted, that means other usage of this OS will result in NOOP actions.
                            this.interrupted = true;
                            this.response = response;
                            //we are not sending anything by this OS, we need to interrupt it.
                            throw new OutputStreamInterruptedException();
                        }
                        return;
                    }
                } catch (StreamTimeoutException ignored) {
                    // We assume this is a timeout exception; if the socket got closed, the next read will throw the
                    // appropriate exception.
                    // we treat this as receiving 100-Continue
                    this.stream = ignored.stream();
                    return;
                }

            }
            throw RedirectionProcessor.maxRedirectsReached(originalRequest.maxRedirects());
        }

    }


}
