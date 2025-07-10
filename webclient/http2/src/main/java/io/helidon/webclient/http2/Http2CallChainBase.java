/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.tls.Tls;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.ClientResponseTrailers;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.http2.Http2Headers;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.HttpClientConfig;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.ReleasableResource;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.http1.Http1ClientRequest;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webclient.spi.WebClientService;

import static io.helidon.http.HeaderNames.CONTENT_ENCODING;
import static io.helidon.webclient.api.ClientRequestBase.USER_AGENT_HEADER;

abstract class Http2CallChainBase implements WebClientService.Chain {
    private static final Tls NO_TLS = Tls.builder().enabled(false).build();

    private final Http2ClientImpl http2Client;
    private final HttpClientConfig clientConfig;
    private final Http2ClientRequestImpl clientRequest;
    private final Function<Http1ClientRequest, Http1ClientResponse> http1EntityHandler;
    private final CompletableFuture<WebClientServiceResponse> whenComplete;
    private Http2ClientStream stream;
    private HttpClientResponse response;
    private ClientRequestHeaders requestHeaders;
    private Status responseStatus;

    Http2CallChainBase(Http2ClientImpl http2Client,
                       Http2ClientRequestImpl clientRequest,
                       CompletableFuture<WebClientServiceResponse> whenComplete,
                       Function<Http1ClientRequest, Http1ClientResponse> http1EntityHandler) {

        this.http2Client = http2Client;
        this.clientConfig = http2Client.clientConfig();
        this.clientRequest = clientRequest;
        this.whenComplete = whenComplete;
        this.http1EntityHandler = http1EntityHandler;
    }

    static WebClientServiceResponse createServiceResponse(WebClientServiceRequest serviceRequest,
                                                          HttpClientConfig clientConfig,
                                                          Http2ClientStream stream,
                                                          CompletableFuture<WebClientServiceResponse> whenComplete,
                                                          Status responseStatus,
                                                          ClientResponseHeaders clientResponseHeaders) {
        WebClientServiceResponse.Builder builder = WebClientServiceResponse.builder();

        // we need an instance to create it, so let's just use a reference
        AtomicReference<WebClientServiceResponse> response = new AtomicReference<>();
        if (stream.hasEntity()) {
            ContentDecoder decoder = contentDecoder(clientResponseHeaders, clientConfig);
            builder.inputStream(decoder.apply(new RequestingInputStream(stream, whenComplete, response)));
        }
        WebClientServiceResponse serviceResponse = builder
                .serviceRequest(serviceRequest)
                .whenComplete(whenComplete)
                .connection(stream)
                .status(responseStatus)
                .headers(clientResponseHeaders)
                .connection(stream)
                .build();

        response.set(serviceResponse);
        return serviceResponse;
    }

    @Override
    public WebClientServiceResponse proceed(WebClientServiceRequest serviceRequest) {
        ClientUri uri = serviceRequest.uri();
        requestHeaders = serviceRequest.headers();

        requestHeaders.setIfAbsent(HeaderValues.create(HeaderNames.HOST, uri.authority()));
        requestHeaders.remove(HeaderNames.CONNECTION, LogHeaderConsumer.INSTANCE);
        requestHeaders.setIfAbsent(USER_AGENT_HEADER);

        ConnectionKey connectionKey = connectionKey(serviceRequest);

        Http2ConnectionAttemptResult result = http2Client.connectionCache()
                .newStream(http2Client, connectionKey, clientRequest, uri, http1EntityHandler);

        try {
            if (result.result() == Http2ConnectionAttemptResult.Result.HTTP_2) {
                // ALPN, prior knowledge, or upgrade success
                this.stream = result.stream();
                return doProceed(serviceRequest, requestHeaders, result.stream());
            } else {
                // upgrade failed
                this.response = result.response();
                return doProceed(serviceRequest, result.response());
            }
        } catch (StreamTimeoutException e){
            //This request was waiting for 100 Continue, but it was very likely not supported by the server.
            //Do not remove connection from the cache in that case.
            if (!clientRequest().outputStreamRedirect()) {
                http2Client.connectionCache().remove(connectionKey);
            }
            throw e;
        }
    }

    ClientRequestHeaders requestHeaders() {
        return requestHeaders;
    }

    Status responseStatus() {
        return responseStatus;
    }

    CompletableFuture<WebClientServiceResponse> whenComplete() {
        return whenComplete;
    }

    /**
     * HTTP/2.
     *
     * @param serviceRequest request
     * @param headers        used request headers
     * @param stream         allocated stream for the current request
     * @return correct response
     */
    protected abstract WebClientServiceResponse doProceed(WebClientServiceRequest serviceRequest,
                                                          ClientRequestHeaders headers,
                                                          Http2ClientStream stream);

    /**
     * HTTP/1 - failed to upgrade to HTTP/2.
     *
     * @param serviceRequest request
     * @param response       HTTP/1 response
     * @return correct response
     */
    protected WebClientServiceResponse doProceed(WebClientServiceRequest serviceRequest, HttpClientResponse response) {
        this.responseStatus = response.status();

        WebClientServiceResponse.Builder builder = WebClientServiceResponse.builder();
        if (response.entity().hasEntity()) {
            builder.inputStream(response.inputStream());
        }
        return builder
                .serviceRequest(serviceRequest)
                .whenComplete(whenComplete)
                .status(response.status())
                .headers(response.headers())
                .connection(new Http2CallEntityChain.Http1ResponseResource(response))
                .build();
    }

    protected WebClientServiceResponse readResponse(WebClientServiceRequest serviceRequest, Http2ClientStream stream) {
        Http2Headers headers = stream.readHeaders();

        ClientResponseHeaders responseHeaders = ClientResponseHeaders.create(headers.httpHeaders());
        this.responseStatus = headers.status();

        WebClientServiceResponse.Builder builder = WebClientServiceResponse.builder();

        // we need an instance to create it, so let's just use a reference
        AtomicReference<WebClientServiceResponse> response = new AtomicReference<>();
        if (stream.hasEntity()) {
            ContentDecoder decoder = contentDecoder(responseHeaders, clientConfig);
            builder.inputStream(decoder.apply(new RequestingInputStream(stream, whenComplete, response)));
        }

        if (responseHeaders.contains(HeaderNames.TRAILER)) {
            builder.trailers(stream.trailers().thenApply(ClientResponseTrailers::create));
        } else {
            builder.trailers(CompletableFuture.failedFuture(new IllegalStateException("No trailers are expected.")));
        }

        WebClientServiceResponse serviceResponse = builder
                .serviceRequest(serviceRequest)
                .whenComplete(whenComplete)
                .status(responseStatus)
                .headers(responseHeaders)
                .connection(stream)
                .build();

        response.set(serviceResponse);
        return serviceResponse;
    }

    private static ContentDecoder contentDecoder(ClientResponseHeaders responseHeaders, HttpClientConfig clientConfig) {
        ContentEncodingContext encodingSupport = clientConfig.contentEncoding();
        if (encodingSupport.contentDecodingEnabled() && responseHeaders.contains(CONTENT_ENCODING)) {
            String contentEncoding = responseHeaders.get(CONTENT_ENCODING).get();
            if (encodingSupport.contentDecodingSupported(contentEncoding)) {
                return encodingSupport.decoder(contentEncoding);
            } else {
                throw new IllegalStateException("Unsupported content encoding: \n"
                                                        + BufferData.create(contentEncoding.getBytes(StandardCharsets.UTF_8))
                        .debugDataHex());
            }
        }
        return ContentDecoder.NO_OP;
    }

    protected static Http2Headers prepareHeaders(Method method, ClientRequestHeaders headers, ClientUri uri) {
        Http2Headers h2Headers = Http2Headers.create(headers);
        h2Headers.method(method);
        h2Headers.path(uri.pathWithQueryAndFragment());
        h2Headers.scheme(uri.scheme());

        return h2Headers;
    }

    protected HttpClientConfig clientConfig() {
        return clientConfig;
    }

    protected Http2ClientRequestImpl clientRequest() {
        return clientRequest;
    }

    void closeResponse() {
        if (response != null) {
            response.close();
        }
        if (stream != null) {
            try {
                stream.cancel();
            } finally {
                stream.close();
            }
        }
    }

    private ConnectionKey connectionKey(WebClientServiceRequest serviceRequest) {
        ClientUri uri = serviceRequest.uri();
        return ConnectionKey.create(uri.scheme(),
                                    uri.host(),
                                    uri.port(),
                                    "https".equals(uri.scheme()) ? clientRequest.tls() : NO_TLS,
                                    clientConfig.dnsResolver(),
                                    clientConfig.dnsAddressLookup(),
                                    clientRequest.proxy());
    }

    private static final class LogHeaderConsumer implements Consumer<Header> {
        private static final System.Logger LOGGER = System.getLogger(LogHeaderConsumer.class.getName());
        private static final LogHeaderConsumer INSTANCE = new LogHeaderConsumer();

        @Override
        public void accept(Header httpHeader) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                           "HTTP/2 request contains wrong header, removing {0}", httpHeader);
            }
        }
    }

    private static class RequestingInputStream extends InputStream {
        private final Function<Integer, BufferData> bufferFunction;
        private final Runnable entityProcessedRunnable;

        private BufferData currentBuffer;
        private boolean finished;

        RequestingInputStream(Http2ClientStream stream,
                              CompletableFuture<WebClientServiceResponse> whenComplete,
                              AtomicReference<WebClientServiceResponse> response) {
            this.bufferFunction = stream::read;
            // we can only get the response at the time of completion, as the instance is created after this constructor
            // returns
            this.entityProcessedRunnable = () -> whenComplete.complete(response.get());
        }

        @Override
        public int read() {
            if (finished) {
                return -1;
            }
            ensureBuffer(512);
            if (finished || currentBuffer == null) {
                return -1;
            }
            return currentBuffer.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (finished) {
                return -1;
            }
            ensureBuffer(len);
            if (finished || currentBuffer == null) {
                return -1;
            }
            return currentBuffer.read(b, off, len);
        }

        private void ensureBuffer(int estimate) {
            if (currentBuffer != null && currentBuffer.consumed()) {
                currentBuffer = null;
            }
            if (currentBuffer == null) {
                currentBuffer = bufferFunction.apply(estimate);
                if (currentBuffer == null || currentBuffer == BufferData.empty()) {
                    entityProcessedRunnable.run();
                    finished = true;
                }
            }
        }
    }

    protected static class Http1ResponseResource implements ReleasableResource {
        private final HttpClientResponse response;

        Http1ResponseResource(HttpClientResponse response) {
            this.response = response;
        }

        @Override
        public void closeResource() {
            response.close();
        }
    }
}
