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
import java.util.concurrent.CompletableFuture;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.http2.Http2Headers;
import io.helidon.webclient.api.ClientRequest;
import io.helidon.webclient.api.ClientRequestOrigin;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.HttpClientConfig;
import io.helidon.webclient.api.RedirectSecurityState;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;

import static io.helidon.webclient.http2.RedirectionProcessor.checkRedirectHeaders;

class Http2CallOutputStreamChain extends Http2CallChainBase {
    private static final System.Logger LOGGER = System.getLogger(Http2CallOutputStreamChain.class.getName());

    private final CompletableFuture<WebClientServiceRequest> whenSent;
    private final ClientRequest.OutputStreamHandler streamHandler;
    private final Http2ClientImpl client;
    private boolean requestEntitySent;
    private int followedRedirects;

    Http2CallOutputStreamChain(Http2ClientImpl http2Client,
                               Http2ClientRequestImpl http2ClientRequest,
                               CompletableFuture<WebClientServiceRequest> whenSent,
                               CompletableFuture<WebClientServiceResponse> whenComplete,
                               ClientRequest.OutputStreamHandler streamHandler,
                               int followedRedirects) {
        super(http2Client,
              http2ClientRequest,
              whenComplete,
              new Http1FallbackHandler(whenSent,
                                       http1Request -> http1Request.outputStream(streamHandler, followedRedirects),
                                       () -> false,
                                       http2ClientRequest::responseCookiesDeferred,
                                       http2ClientRequest::handoffProtocolResponse));

        this.client = http2Client;
        this.whenSent = whenSent;
        this.streamHandler = streamHandler;
        this.followedRedirects = followedRedirects;
    }

    static void closeRedirectStream(Http2ClientStream stream, Throwable failure) {
        try {
            stream.cancel();
        } catch (RuntimeException | Error cleanupFailure) {
            if (failure != cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
        try {
            stream.close();
        } catch (RuntimeException | Error cleanupFailure) {
            if (failure != cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
    }

    @Override
    protected WebClientServiceResponse doProceed(WebClientServiceRequest serviceRequest,
                                                 ClientRequestHeaders headers,
                                                 Http2ClientStream stream) {
        boolean interrupted = false;
        ClientOutputStream outputStream = new ClientOutputStream(client,
                                                                 this,
                                                                 stream,
                                                                 headers,
                                                                 clientConfig(),
                                                                 serviceRequest,
                                                                 clientRequest(),
                                                                 whenSent,
                                                                 whenComplete(),
                                                                 followedRedirects);
        try {
            streamHandler.handle(outputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (OutputStreamInterruptedException e) {
            interrupted = true;
        }
        requestEntitySent = outputStream.bytesWritten > 0;
        followedRedirects = outputStream.numberOfRedirects;

        if (interrupted || outputStream.interrupted()) {
            //If cos is marked as interrupted, we know that our interrupted exception has been thrown, but
            //it was intercepted by the user OutputStreamHandler and not rethrown.
            //This is a fallback mechanism to correctly handle such a situations.
            if (outputStream.redirectFailure != null) {
                throw outputStream.redirectFailure;
            }
            whenSent.complete(serviceRequest);
            requestUri(outputStream.lastEndpointUri);
            if (outputStream.response != null) {
                clientRequest().redirectSecurityState(outputStream.response.redirectSecurityState());
                return useResponse(outputStream.response.serviceRequest(), outputStream.response);
            }
            clientRequest().redirectSecurityState(outputStream.lastRequest.redirectSecurityState());
            stream(outputStream.stream);
            WebClientServiceResponse serviceResponse = outputStream.serviceResponse();
            captureProtocolResponse(serviceResponse.status(), serviceResponse.headers());
            return serviceResponse;
        } else if (!outputStream.closed()) {
            throw new IllegalStateException("Output stream was not closed in handler");
        }

        Http2Headers responseHeaders = readHeaders(outputStream.stream);
        ClientResponseHeaders clientResponseHeaders = ClientResponseHeaders.create(
                responseHeaders.httpHeaders(),
                clientConfig().mediaTypeParserMode());
        captureProtocolResponse(responseHeaders.status(), clientResponseHeaders);

        requestUri(outputStream.lastEndpointUri);
        clientRequest().redirectSecurityState(outputStream.lastRequest.redirectSecurityState());
        stream(outputStream.stream);
        return createServiceResponse(outputStream.lastServiceRequest,
                                     clientConfig(),
                                     outputStream.stream,
                                     whenComplete(),
                                     responseHeaders.status(),
                                     clientResponseHeaders);
    }

    boolean requestEntitySent() {
        return requestEntitySent;
    }

    int followedRedirects() {
        return followedRedirects;
    }

    private static ClientUri responseCookieUri(RedirectSecurityState securityState, ClientUri endpointUri) {
        return securityState.lastEffectiveOrigin()
                .map(origin -> origin.apply(endpointUri))
                .orElseGet(() -> ClientRequestOrigin.create(endpointUri).apply(endpointUri));
    }

    private static class ClientOutputStream extends OutputStream {

        private static final BufferData TERMINATING = BufferData.empty();
        private final WebClientServiceRequest request;
        private final Http2ClientRequestImpl originalRequest;
        private final CompletableFuture<WebClientServiceRequest> whenSent;
        private final CompletableFuture<WebClientServiceResponse> whenComplete;
        private final HttpClientConfig clientConfig;
        private final ClientRequestHeaders headers;
        private final long contentLength;
        private final Http2CallOutputStreamChain callChain;

        private long bytesWritten;
        private boolean noData = true;
        private boolean closed;
        private boolean interrupted;
        private int numberOfRedirects = 0;
        private final Http2ClientImpl client;
        private Http2ClientStream stream;
        private Http2ClientRequestImpl lastRequest;
        private WebClientServiceRequest lastServiceRequest;
        private ClientUri lastEndpointUri;
        private Http2ClientResponseImpl response;
        private WebClientServiceResponse serviceResponse;
        private ByteArrayOutputStream redirectedEntity;
        private Http2ClientRequestImpl redirectedRequest;
        private RuntimeException redirectFailure;

        private ClientOutputStream(Http2ClientImpl client,
                                   Http2CallOutputStreamChain callChain,
                                   Http2ClientStream stream,
                                   ClientRequestHeaders headers,
                                   HttpClientConfig clientConfig,
                                   WebClientServiceRequest request,
                                   Http2ClientRequestImpl originalRequest,
                                   CompletableFuture<WebClientServiceRequest> whenSent,
                                   CompletableFuture<WebClientServiceResponse> whenComplete,
                                   int followedRedirects) {
            this.client = client;
            this.callChain = callChain;
            this.stream = stream;
            this.headers = headers;
            this.clientConfig = clientConfig;
            this.contentLength = headers.contentLength().orElse(-1);
            this.request = request;
            this.originalRequest = originalRequest;
            this.lastRequest = originalRequest;
            this.lastServiceRequest = request;
            this.lastEndpointUri = ClientUri.create(request.uri().toUri());
            this.numberOfRedirects = followedRedirects;
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
            if (redirectedEntity != null) {
                try {
                    if (contentLength > 0 && contentLength != bytesWritten) {
                        throw new IOException("Content length is set to " + contentLength
                                                      + ", but the number of bytes written was " + bytesWritten);
                    }
                    response = RedirectionProcessor.invokeWithFollowRedirectsDeferringResponseCookies(
                            redirectedRequest,
                            numberOfRedirects,
                            redirectedEntity.toByteArray());
                    lastRequest = redirectedRequest;
                    lastServiceRequest = response.serviceRequest();
                    lastEndpointUri = response.lastEndpointUri();
                    whenSent.complete(lastServiceRequest);
                    interrupted = true;
                    super.close();
                    return;
                } catch (IOException e) {
                    redirectFailure = new UncheckedIOException(e);
                    interrupted = true;
                    throw e;
                } catch (RuntimeException e) {
                    redirectFailure = e;
                    interrupted = true;
                    throw e;
                }
            }
            if (noData) {
                sendHeader();
            }
            stream.writeData(TERMINATING, true);
            whenSent.complete(lastServiceRequest);
            super.close();
        }

        WebClientServiceResponse serviceResponse() {
            if (serviceResponse != null) {
                return serviceResponse;
            }

            return createServiceResponse(lastServiceRequest,
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

        private void writeContent(BufferData buffer) throws IOException {
            bytesWritten += buffer.available();
            if (contentLength != -1 && bytesWritten > contentLength) {
                throw new IOException("Content length was set to " + contentLength
                                              + ", but you are writing additional " + (bytesWritten - contentLength) + " "
                                              + "bytes");
            }
            if (redirectedEntity != null) {
                int available = buffer.available();
                if ((long) redirectedEntity.size() + available > clientConfig.maxInMemoryEntity()) {
                    throw new IOException(
                            "Redirected output-stream request entity exceeds the configured in-memory limit of "
                                    + clientConfig.maxInMemoryEntity() + " bytes");
                }
                redirectedEntity.writeBytes(buffer.readBytes());
                return;
            }
            stream.writeData(buffer, false);
        }

        private void sendHeader() {
            if (originalRequest.sendExpectContinue().orElse(clientConfig.sendExpectContinue()) && !noData) {
                headers.set(HeaderValues.EXPECT_100);
            }

            Http2Headers http2Headers = prepareHeaders(request.method(), headers, request.uri());

            stream.writeHeaders(http2Headers, false);

            if (headers.containsToken(HeaderValues.EXPECT_100)) {
                Status status = waitFor100Continue(stream, originalRequest.readContinueTimeout());

                if (status != Status.CONTINUE_100) {
                    Http2Headers responseHeaders = readHeaders(stream);
                    Status responseStatus = responseHeaders.status();
                    ClientResponseHeaders clientResponseHeaders = ClientResponseHeaders.create(
                            responseHeaders.httpHeaders(),
                            clientConfig.mediaTypeParserMode());
                    callChain.captureProtocolResponse(responseStatus, clientResponseHeaders);

                    if (RedirectionProcessor.redirectionStatusCode(responseStatus) && originalRequest.followRedirects()) {
                        callChain.publishProtocolResponse();
                        checkRedirectHeaders(responseHeaders);
                        originalRequest.recordResponseCookies(responseCookieUri(originalRequest.redirectSecurityState(),
                                                                                lastEndpointUri),
                                                              ClientResponseHeaders.create(responseHeaders.httpHeaders()));
                        redirect(responseStatus, responseHeaders.httpHeaders());
                    } else {
                        //OS changed its state to interrupted, that means other usage of this OS will result in NOOP actions.
                        this.interrupted = true;
                        this.serviceResponse = createServiceResponse(request,
                                                                     clientConfig,
                                                                     stream,
                                                                     whenComplete,
                                                                     responseHeaders.status(),
                                                                     clientResponseHeaders);
                        //we are not sending anything by this OS, we need to interrupt it.
                        throw new OutputStreamInterruptedException();
                    }
                }
            }
        }

        private void redirect(Status lastStatus, Headers headerValues) {
            String redirectedUri = headerValues.get(HeaderNames.LOCATION).get();
            Method method;
            boolean sendEntity;
            if (RedirectionProcessor.keepsMethodAndEntity(lastRequest.method(), lastStatus)) {
                method = originalRequest.method();
                sendEntity = true;
            } else {
                method = Method.GET;
                sendEntity = false;
            }
            while (true) {
                ClientUri sourceUri;
                ClientUri redirectUri;
                try {
                    if (numberOfRedirects >= originalRequest.maxRedirects()) {
                        throw new IllegalStateException("Maximum number of request redirections ("
                                                                + originalRequest.maxRedirects() + ") reached.");
                    }
                    sourceUri = lastEndpointUri;
                    redirectUri = lastRequest.resolveRedirectUri(sourceUri, redirectedUri);
                } catch (RuntimeException | Error failure) {
                    if (stream != null) {
                        closeRedirectStream(stream, failure);
                        stream = null;
                    }
                    throw failure;
                }
                numberOfRedirects++;
                // Queue RST_STREAM before releasing this stream's reservation, so redirected HEADERS
                // are serialized after the abandoned upload is reset for max-concurrent-streams peers.
                if (stream != null) {
                    try {
                        stream.cancel();
                    } finally {
                        stream.close();
                    }
                }
                Http2ClientRequestImpl clientRequest = new Http2ClientRequestImpl(lastRequest,
                                                                                  method,
                                                                                  redirectUri,
                                                                                  lastRequest.properties(),
                                                                                  sourceUri,
                                                                                  sendEntity);
                if (!sendEntity) {
                    clientRequest.discardEntityHeaders();
                }
                clientRequest.followRedirects(false);
                clientRequest.readTimeout(originalRequest.readTimeout());
                if (sendEntity && !clientConfig.services().isEmpty()) {
                    clientRequest.redirectedWhenSent(whenSent);
                    redirectedRequest = clientRequest;
                    redirectedEntity = new ByteArrayOutputStream();
                    lastRequest = clientRequest;
                    return;
                }
                clientRequest.deferResponseCookies();
                try {
                    Http2ClientResponseImpl response;
                    if (sendEntity) {
                        // The original handler still owns the pending bytes. Do not negotiate an HTTP/1 fallback that
                        // could consume the redirect request without providing that stream to the handler.
                        clientRequest.priorKnowledge(true);
                        clientRequest.outputStreamRedirect(true)
                                .header(HeaderValues.EXPECT_100);
                        response = clientRequest.redirectProbe();
                    } else {
                        clientRequest.outputStreamRedirect(false);
                        response = clientRequest.redirectProbe();
                    }
                    lastRequest = clientRequest;
                    lastServiceRequest = response.serviceRequest();
                    lastEndpointUri = response.lastEndpointUri();

                    stream = response.protocolId().equals(Http2Client.PROTOCOL_ID) ? response.stream() : null;

                    if (RedirectionProcessor.redirectionStatusCode(response.status())) {
                        try (response) {
                            checkRedirectHeaders(response.headers());
                            ClientUri endpointUri = response.lastEndpointUri();
                            clientRequest.recordResponseCookies(responseCookieUri(response.redirectSecurityState(),
                                                                                  endpointUri),
                                    response.headers());
                            if (!RedirectionProcessor.keepsMethodAndEntity(lastRequest.method(), response.status())) {
                                method = Method.GET;
                                sendEntity = false;
                            }
                            redirectedUri = response.headers().get(HeaderNames.LOCATION).get();
                        }
                    } else {
                        // The server returned a final response without accepting the pending body, or the redirect
                        // changed to a bodyless request. The original handler must not write to that completed stream.
                        this.interrupted = true;
                        this.response = response;
                        throw new OutputStreamInterruptedException();
                    }
                } catch (StreamTimeoutException ignored) {
                    // we assume this is a timeout exception, if the socket got closed, next read will throw appropriate exception
                    // we treat this as receiving 100-Continue
                    this.stream = ignored.stream();
                    this.lastRequest = clientRequest;
                    this.lastServiceRequest = clientRequest.finalServiceRequest();
                    this.lastEndpointUri = clientRequest.finalRequestUri();
                    return;
                }

            }
        }

    }


}
