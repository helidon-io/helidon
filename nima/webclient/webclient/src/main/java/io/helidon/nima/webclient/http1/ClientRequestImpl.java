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

package io.helidon.nima.webclient.http1;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.context.Context;
import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.HeaderValue;
import io.helidon.common.http.WritableHeaders;
import io.helidon.common.uri.UriEncoding;
import io.helidon.common.uri.UriFragment;
import io.helidon.common.uri.UriQueryWriteable;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.webclient.ClientConnection;
import io.helidon.nima.webclient.UriHelper;
import io.helidon.nima.webclient.WebClientServiceRequest;
import io.helidon.nima.webclient.WebClientServiceResponse;
import io.helidon.nima.webclient.spi.WebClientService;

class ClientRequestImpl implements Http1ClientRequest {
    private static final AtomicLong COUNTER = new AtomicLong();

    private final UriQueryWriteable query;
    private final Map<String, String> pathParams = new HashMap<>();
    private final Http.Method method;
    private final UriHelper uri;
    private final String requestId;
    private final Http1ClientConfig clientConfig;

    private WritableHeaders<?> explicitHeaders = WritableHeaders.create();
    private Tls tls;
    private String uriTemplate;
    private ClientConnection connection;
    private UriFragment fragment;
    private boolean skipUriEncoding = false;

    ClientRequestImpl(Http1ClientConfig clientConfig,
                      Http.Method method,
                      UriHelper helper,
                      UriQueryWriteable query) {
        this.method = method;
        this.uri = helper;

        this.clientConfig = clientConfig;
        this.tls = clientConfig.tls().orElse(null);
        this.query = query;

        this.requestId = "http1-client-" + COUNTER.getAndIncrement();
        this.fragment = UriFragment.empty();
    }

    @Override
    public Http1ClientRequest uri(String uri) {
        if (uri.indexOf('{') > -1) {
            this.uriTemplate = uri;
        } else {
            if (skipUriEncoding) {
                uri(URI.create(uri));
            } else {
                uri(URI.create(UriEncoding.encodeUri(uri)));
            }
        }

        return this;
    }

    @Override
    public Http1ClientRequest tls(Tls tls) {
        this.tls = tls;
        return this;
    }

    @Override
    public Http1ClientRequest uri(URI uri) {
        this.uriTemplate = null;
        this.uri.resolve(uri, query);
        return this;
    }

    @Override
    public Http1ClientRequest header(HeaderValue header) {
        this.explicitHeaders.set(header);
        return this;
    }

    @Override
    public Http1ClientRequest headers(Function<ClientRequestHeaders, WritableHeaders<?>> headersConsumer) {
        this.explicitHeaders = headersConsumer.apply(ClientRequestHeaders.create(explicitHeaders));
        return this;
    }

    @Override
    public Http1ClientRequest pathParam(String name, String value) {
        pathParams.put(name, value);
        return this;
    }

    @Override
    public Http1ClientRequest queryParam(String name, String... values) {
        query.set(name, values);
        return this;
    }

    @Override
    public Http1ClientRequest fragment(String fragment) {
        this.fragment = UriFragment.createFromDecoded(fragment);
        return this;
    }

    @Override
    public Http1ClientResponse request() {
        return submit(BufferData.EMPTY_BYTES);
    }

    @Override
    public Http1ClientResponse submit(Object entity) {
        if (entity != BufferData.EMPTY_BYTES) {
            rejectHeadWithEntity();
        }

        CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        WebClientService.Chain callChain = new HttpCallEntityChain(clientConfig,
                                                                   connection,
                                                                   tls,
                                                                   whenSent,
                                                                   whenComplete,
                                                                   entity);

        return invokeServices(callChain, whenSent, whenComplete);
    }

    @Override
    public Http1ClientResponse outputStream(OutputStreamHandler streamHandler) {
        rejectHeadWithEntity();

        CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        WebClientService.Chain callChain = new HttpCallOutputStreamChain(clientConfig,
                                                                         connection,
                                                                         tls,
                                                                         whenSent,
                                                                         whenComplete,
                                                                         streamHandler);

        return invokeServices(callChain, whenSent, whenComplete);
    }

    @Override
    public URI resolvedUri() {
        if (uriTemplate != null) {
            String resolved = resolvePathParams(uriTemplate);
            if (skipUriEncoding) {
                this.uri.resolve(URI.create(resolved), query);
            } else {
                this.uri.resolve(URI.create(UriEncoding.encodeUri(resolved)), query);
            }
        }
        return URI.create(this.uri.scheme() + "://"
                                  + uri.authority()
                                  + uri.pathWithQueryAndFragment(query, fragment));

    }

    @Override
    public Http1ClientRequest connection(ClientConnection connection) {
        this.connection = connection;
        return this;
    }

    @Override
    public Http1ClientRequest skipUriEncoding() {
        this.skipUriEncoding = true;
        this.uri.skipUriEncoding(true);
        return this;
    }

    Http1ClientConfig clientConfig() {
        return clientConfig;
    }

    UriHelper uri() {
        return uri;
    }

    ClientRequestHeaders headers() {
        return ClientRequestHeaders.create(explicitHeaders);
    }

    private ClientResponseImpl invokeServices(WebClientService.Chain callChain,
                                              CompletableFuture<WebClientServiceRequest> whenSent,
                                              CompletableFuture<WebClientServiceResponse> whenComplete) {
        if (uriTemplate != null) {
            String resolved = resolvePathParams(uriTemplate);
            if (skipUriEncoding) {
                this.uri.resolve(URI.create(resolved), query);
            } else {
                this.uri.resolve(URI.create(UriEncoding.encodeUri(resolved)), query);
            }
        }

        ClientRequestHeaders headers = ClientRequestHeaders.create(explicitHeaders);

        Map<String, String> properties = new HashMap<>();

        WebClientServiceRequest serviceRequest = new ServiceRequestImpl(uri,
                                                                        method,
                                                                        Http.Version.V1_1,
                                                                        query,
                                                                        UriFragment.empty(),
                                                                        headers,
                                                                        Context.create(),
                                                                        requestId,
                                                                        whenComplete,
                                                                        whenSent,
                                                                        properties);

        WebClientService.Chain last = callChain;

        List<WebClientService> services = clientConfig.services();
        ListIterator<WebClientService> serviceIterator = services.listIterator(services.size());
        while (serviceIterator.hasPrevious()) {
            last = new ServiceChainImpl(last, serviceIterator.previous());
        }

        WebClientServiceResponse serviceResponse = last.proceed(serviceRequest);

        CompletableFuture<Void> complete = new CompletableFuture<>();
        complete.thenAccept(ignored -> serviceResponse.whenComplete().complete(serviceResponse))
                .exceptionally(throwable -> {
                    serviceResponse.whenComplete().completeExceptionally(throwable);
                    return null;
                });

        return new ClientResponseImpl(serviceResponse.status(),
                                      serviceResponse.serviceRequest().headers(),
                                      serviceResponse.headers(),
                                      serviceResponse.connection(),
                                      serviceResponse.reader(),
                                      complete);
    }

    private String resolvePathParams(String path) {
        String result = path;
        for (Map.Entry<String, String> entry : pathParams.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();

            result = result.replace("{" + name + "}", value);
        }

        if (result.contains("{")) {
            throw new IllegalArgumentException("Not all path parameters are defined. Template after resolving parameters: "
                                                       + result);
        }

        return result;
    }

    private void rejectHeadWithEntity() {
        if (this.method.equals(Http.Method.HEAD)) {
            throw new IllegalArgumentException("Payload in method '" + Http.Method.HEAD + "' has no defined semantics");
        }
    }
}
