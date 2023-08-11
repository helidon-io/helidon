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

package io.helidon.nima.http2.webclient;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.http.ClientResponseHeaders;
import io.helidon.common.http.Http;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.http.encoding.ContentDecoder;
import io.helidon.nima.http.encoding.ContentEncodingContext;
import io.helidon.nima.http2.Http2Headers;
import io.helidon.nima.webclient.api.ClientUri;
import io.helidon.nima.webclient.api.ConnectionKey;
import io.helidon.nima.webclient.api.HttpClientConfig;
import io.helidon.nima.webclient.api.HttpClientResponse;
import io.helidon.nima.webclient.api.ReleasableResource;
import io.helidon.nima.webclient.api.WebClient;
import io.helidon.nima.webclient.api.WebClientServiceRequest;
import io.helidon.nima.webclient.api.WebClientServiceResponse;
import io.helidon.nima.webclient.http1.Http1ClientRequest;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webclient.spi.WebClientService;

import static io.helidon.common.http.Http.HeaderNames.CONTENT_ENCODING;
import static io.helidon.nima.webclient.api.ClientRequestBase.USER_AGENT_HEADER;

abstract class Http2CallChainBase implements WebClientService.Chain {
    private static final Tls NO_TLS = Tls.builder().enabled(false).build();

    private final WebClient webClient;
    private final HttpClientConfig clientConfig;
    private final Http2ClientProtocolConfig protocolConfig;
    private final Http2ClientRequestImpl clientRequest;
    private final Function<Http1ClientRequest, Http1ClientResponse> http1EntityHandler;
    private final CompletableFuture<WebClientServiceResponse> whenComplete;
    private Http2ClientStream stream;
    private HttpClientResponse response;
    private ClientRequestHeaders requestHeaders;
    private ClientResponseHeaders responseHeaders;
    private Http.Status responseStatus;
    private Http2ConnectionAttemptResult.Result result;

    Http2CallChainBase(WebClient webClient,
                       HttpClientConfig clientConfig,
                       Http2ClientProtocolConfig protocolConfig,
                       Http2ClientRequestImpl clientRequest,
                       CompletableFuture<WebClientServiceResponse> whenComplete,
                       Function<Http1ClientRequest, Http1ClientResponse> http1EntityHandler) {

        this.webClient = webClient;
        this.clientConfig = clientConfig;
        this.protocolConfig = protocolConfig;
        this.clientRequest = clientRequest;
        this.whenComplete = whenComplete;
        this.http1EntityHandler = http1EntityHandler;
    }

    @Override
    public WebClientServiceResponse proceed(WebClientServiceRequest serviceRequest) {
        ClientUri uri = serviceRequest.uri();
        requestHeaders = serviceRequest.headers();

        requestHeaders.setIfAbsent(Http.Headers.create(Http.HeaderNames.HOST, uri.authority()));
        requestHeaders.remove(Http.HeaderNames.CONNECTION, LogHeaderConsumer.INSTANCE);
        requestHeaders.setIfAbsent(USER_AGENT_HEADER);

        Http2ConnectionAttemptResult result = Http2ConnectionCache.newStream(webClient,
                                                                             protocolConfig,
                                                                             connectionKey(serviceRequest),
                                                                             clientRequest,
                                                                             uri,
                                                                             http1EntityHandler);

        this.result = result.result();

        if (result.result() == Http2ConnectionAttemptResult.Result.HTTP_2) {
            // ALPN, prior knowledge, or upgrade success
            this.stream = result.stream();
            return doProceed(serviceRequest, requestHeaders, result.stream());
        } else {
            // upgrade failed
            this.response = result.response();
            return doProceed(serviceRequest, result.response());
        }
    }

    ClientRequestHeaders requestHeaders() {
        return requestHeaders;
    }

    Http.Status responseStatus() {
        return responseStatus;
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
        this.responseHeaders = response.headers();
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
            ContentDecoder decoder = contentDecoder(responseHeaders);
            builder.inputStream(decoder.apply(new RequestingInputStream(stream, whenComplete, response)));
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

    private ContentDecoder contentDecoder(ClientResponseHeaders responseHeaders) {
        ContentEncodingContext encodingSupport = clientConfig.contentEncoding();
        if (encodingSupport.contentDecodingEnabled() && responseHeaders.contains(CONTENT_ENCODING)) {
            String contentEncoding = responseHeaders.get(CONTENT_ENCODING).value();
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

    protected Http2Headers prepareHeaders(Http.Method method, ClientRequestHeaders headers, ClientUri uri) {
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
        return new ConnectionKey(uri.scheme(),
                                 uri.host(),
                                 uri.port(),
                                 "https".equals(uri.scheme()) ? clientRequest.tls() : NO_TLS,
                                 clientConfig.dnsResolver(),
                                 clientConfig.dnsAddressLookup(),
                                 clientRequest.proxy());
    }

    private static final class LogHeaderConsumer implements Consumer<Http.Header> {
        private static final System.Logger LOGGER = System.getLogger(LogHeaderConsumer.class.getName());
        private static final LogHeaderConsumer INSTANCE = new LogHeaderConsumer();

        @Override
        public void accept(Http.Header httpHeader) {
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
