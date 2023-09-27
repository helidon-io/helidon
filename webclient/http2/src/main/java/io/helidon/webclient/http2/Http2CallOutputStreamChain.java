/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;

import static io.helidon.webclient.http2.RedirectionProcessor.checkRedirectHeaders;
import static java.lang.System.Logger.Level.TRACE;

class Http2CallOutputStreamChain extends Http2CallChainBase {
    private static final System.Logger LOGGER = System.getLogger(Http2CallOutputStreamChain.class.getName());
    private final CompletableFuture<WebClientServiceRequest> whenSent;
    private final ClientRequest.OutputStreamHandler streamHandler;

    Http2CallOutputStreamChain(Http2ClientImpl http2Client,
                               Http2ClientRequestImpl http2ClientRequest,
                               CompletableFuture<WebClientServiceRequest> whenSent,
                               CompletableFuture<WebClientServiceResponse> whenComplete,
                               ClientRequest.OutputStreamHandler streamHandler) {
        super(http2Client,
              http2ClientRequest,
              whenComplete,
              req -> req.outputStream(streamHandler));

        this.whenSent = whenSent;
        this.streamHandler = streamHandler;
    }

    @Override
    protected WebClientServiceResponse doProceed(WebClientServiceRequest serviceRequest,
                                                 ClientRequestHeaders headers,
                                                 Http2ClientStream stream) {
        boolean interrupted = false;
        ClientOutputStream outputStream = new ClientOutputStream(stream,
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
            return outputStream.serviceResponse();
        } else if (!outputStream.closed()) {
            throw new IllegalStateException("Output stream was not closed in handler");
        }

        Http2Headers responseHeaders = outputStream.stream.readHeaders();
        stream.ctx().log(LOGGER, TRACE, "client received status %n%s", responseHeaders.status());
        stream.ctx().log(LOGGER, TRACE, "client received headers %n%s", responseHeaders.httpHeaders());

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
            Http2ClientRequestImpl request = new Http2ClientRequestImpl(outputStream.lastRequest,
                                                                        Method.GET,
                                                                        redirectUri,
                                                                        outputStream.lastRequest.properties());
            int numberOfRedirects = outputStream.numberOfRedirects;
            Http2ClientResponseImpl clientResponse = RedirectionProcessor.invokeWithFollowRedirects(request,
                                                                                                    numberOfRedirects,
                                                                                                    BufferData.EMPTY_BYTES);
            return createServiceResponse(serviceRequest,
                                         clientConfig(),
                                         clientResponse.stream(),
                                         whenComplete(),
                                         clientResponse.status(),
                                         clientResponse.headers());
        }

        return createServiceResponse(serviceRequest,
                                     clientConfig(),
                                     outputStream.stream,
                                     whenComplete(),
                                     responseHeaders.status(),
                                     ClientResponseHeaders.create(responseHeaders.httpHeaders()));
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
        private boolean noData = true;
        private boolean closed;
        private boolean interrupted;
        private int numberOfRedirects = 0;
        private Http2ClientStream stream;
        private Http2ClientRequestImpl lastRequest;
        private Http2ClientResponseImpl response;
        private WebClientServiceResponse serviceResponse;

        private ClientOutputStream(Http2ClientStream stream,
                                   WritableHeaders<?> headers,
                                   HttpClientConfig clientConfig,
                                   WebClientServiceRequest request,
                                   Http2ClientRequestImpl originalRequest,
                                   CompletableFuture<WebClientServiceRequest> whenSent,
                                   CompletableFuture<WebClientServiceResponse> whenComplete) {
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
            if (noData) {
                sendHeader();
            }
            if (LOGGER.isLoggable(TRACE)) {
                stream.ctx().log(LOGGER, System.Logger.Level.TRACE, "send data%n%s", TERMINATING.debugDataHex());
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

        private void writeContent(BufferData buffer) throws IOException {
            bytesWritten += buffer.available();
            if (contentLength != -1 && bytesWritten > contentLength) {
                throw new IOException("Content length was set to " + contentLength
                                              + ", but you are writing additional " + (bytesWritten - contentLength) + " "
                                              + "bytes");
            }
            if (LOGGER.isLoggable(TRACE)) {
                stream.ctx().log(LOGGER, System.Logger.Level.TRACE, "send data:%n%s", buffer.debugDataHex());
            }
            stream.writeData(buffer, false);
        }

        private void sendHeader() {
            if (clientConfig.sendExpectContinue() && !noData) {
                headers.set(HeaderValues.EXPECT_100);
            }

            if (LOGGER.isLoggable(TRACE)) {
                stream.ctx().log(LOGGER, System.Logger.Level.TRACE, "send headers:%n%s", headers);
            }
            Http2Headers http2Headers = prepareHeaders(request.method(),
                                                       ClientRequestHeaders.create(headers),
                                                       request.uri());
            stream.writeHeaders(http2Headers, false);
            whenSent.complete(request);

            if (headers.contains(HeaderValues.EXPECT_100)) {
                Status status = stream.waitFor100Continue();

                if (status != Status.CONTINUE_100) {
                    Http2Headers responseHeaders = stream.readHeaders();
                    Status responseStatus = responseHeaders.status();
                    stream.ctx().log(LOGGER, TRACE, "client received headers %n%s", responseHeaders);

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
                                                                     ClientResponseHeaders.create(responseHeaders.httpHeaders()));
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
            for (; numberOfRedirects < clientConfig.maxRedirects(); numberOfRedirects++) {
                URI newUri = URI.create(redirectedUri);
                ClientUri redirectUri = ClientUri.create(newUri);
                if (newUri.getHost() == null) {
                    redirectUri.scheme(lastUri.scheme());
                    redirectUri.host(lastUri.host());
                    redirectUri.port(lastUri.port());
                }
                lastUri = redirectUri;
                stream.close();
                Http2ClientRequestImpl clientRequest = new Http2ClientRequestImpl(originalRequest,
                                                                                  method,
                                                                                  redirectUri,
                                                                                  originalRequest.properties());
                try {
                    Http2ClientResponseImpl response;
                    if (sendEntity) {
                        response = (Http2ClientResponseImpl) clientRequest
                                .outputStreamRedirect(true)
                                .header(HeaderValues.EXPECT_100)
                                .readTimeout(originalRequest.readContinueTimeout())
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
                        if (!sendEntity) {
                            //OS changed its state to interrupted, that means other usage of this OS will result in NOOP actions.
                            this.interrupted = true;
                            this.response = response;
                            //we are not sending anything by this OS, we need to interrupt it.
                            throw new OutputStreamInterruptedException();
                        }
                        return;
                    }
                } catch (StreamTimeoutException ignored) {
                    // we assume this is a timeout exception, if the socket got closed, next read will throw appropriate exception
                    // we treat this as receiving 100-Continue
                    this.stream = ignored.stream();
                    return;
                }

            }
            throw new IllegalStateException("Maximum number of request redirections ("
                                                    + clientConfig.maxRedirects() + ") reached.");
        }

    }


}
