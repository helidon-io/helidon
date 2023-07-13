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
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.HeaderValue;
import io.helidon.common.http.WritableHeaders;
import io.helidon.common.uri.UriEncoding;
import io.helidon.common.uri.UriFragment;
import io.helidon.common.uri.UriPath;
import io.helidon.common.uri.UriQuery;
import io.helidon.common.uri.UriQueryWriteable;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.webclient.ClientConnection;
import io.helidon.nima.webclient.Proxy;
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
    private final MediaContext mediaContext;
    private final Map<String, String> properties;

    private WritableHeaders<?> explicitHeaders = WritableHeaders.create();
    private boolean followRedirects;
    private int maxRedirects;
    private Duration readTimeout;
    private Tls tls;
    private String uriTemplate;
    private ClientConnection connection;
    private UriFragment fragment = UriFragment.empty();
    private Proxy proxy;
    private boolean skipUriEncoding = false;
    private boolean keepAlive;

    ClientRequestImpl(Http1ClientConfig clientConfig,
                      Http.Method method,
                      UriHelper helper,
                      UriQueryWriteable query,
                      Map<String, String> properties) {
        this.method = method;
        this.uri = helper;
        this.properties = new HashMap<>(properties);
        this.readTimeout = clientConfig.socketOptions().readTimeout();

        this.clientConfig = clientConfig;
        this.mediaContext = clientConfig.mediaContext();
        this.followRedirects = clientConfig.followRedirects();
        this.maxRedirects = clientConfig.maxRedirects();
        this.tls = clientConfig.tls().orElse(null);
        this.query = query;
        this.keepAlive = clientConfig.defaultKeepAlive();

        this.requestId = "http1-client-" + COUNTER.getAndIncrement();
        this.explicitHeaders = WritableHeaders.create(clientConfig.defaultHeaders());
    }

    //Copy constructor for redirection purposes
    private ClientRequestImpl(ClientRequestImpl request,
                              Http.Method method,
                              UriHelper helper,
                              UriQueryWriteable query,
                              Map<String, String> properties) {
        this(request.clientConfig, method, helper, query, properties);
        this.followRedirects = request.followRedirects;
        this.maxRedirects = request.maxRedirects;
        this.tls = request.tls;
        this.connection = request.connection;
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
    public Http1ClientRequest headers(Headers headers) {
        for (HeaderValue header : headers) {
            this.explicitHeaders.add(header);
        }
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
    public Http1ClientRequest followRedirects(boolean followRedirects) {
        this.followRedirects = followRedirects;
        return this;
    }

    @Override
    public Http1ClientRequest maxRedirects(int maxRedirects) {
        this.maxRedirects = maxRedirects;
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
        if (followRedirects) {
            return invokeWithFollowRedirectsEntity(entity);
        }
        return invokeRequestWithEntity(entity);
    }

    @Override
    public Http1ClientResponse outputStream(OutputStreamHandler streamHandler) {
        rejectHeadWithEntity();
        CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        WebClientService.Chain callChain = new HttpCallOutputStreamChain(this,
                                                                         clientConfig,
                                                                         connection,
                                                                         tls,
                                                                         proxy,
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

    @Override
    public Http1ClientRequest property(String propertyName, String propertyValue) {
        this.properties.put(propertyName, propertyValue);
        return this;
    }

    @Override
    public Http1ClientRequest keepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
        return this;
    }

    /**
     * Read timeout for this request.
     *
     * @param readTimeout response read timeout
     * @return updated client request
     */
    public Http1ClientRequest readTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
        return this;
    }

    Http1ClientConfig clientConfig() {
        return clientConfig;
    }

    UriHelper uri() {
        return uri;
    }

    boolean keepAlive() {
        return keepAlive;
    }

    @Override
    public ClientRequestHeaders headers() {
        return ClientRequestHeaders.create(explicitHeaders);
    }

    private ClientResponseImpl invokeWithFollowRedirectsEntity(Object entity) {
        //Request object which should be used for invoking the next request. This will change in case of any redirection.
        ClientRequestImpl clientRequest = this;
        //Entity to be sent with the request. Will be changed when redirect happens to prevent entity sending.
        Object entityToBeSent = entity;
        for (int i = 0; i < maxRedirects; i++) {
            ClientResponseImpl clientResponse = clientRequest.invokeRequestWithEntity(entityToBeSent);
            int code = clientResponse.status().code();
            if (code < 300 || code >= 400) {
                return clientResponse;
            } else if (!clientResponse.headers().contains(Http.Header.LOCATION)) {
                throw new IllegalStateException("There is no " + Http.Header.LOCATION + " header present in the response! "
                                                        + "It is not clear where to redirect.");
            }
            String redirectedUri = clientResponse.headers().get(Http.Header.LOCATION).value();
            URI newUri = URI.create(redirectedUri);
            UriQueryWriteable newQuery = UriQueryWriteable.create();
            UriHelper redirectUri = UriHelper.create(newUri, newQuery);
            String uriQuery = newUri.getQuery();
            if (uriQuery != null) {
                newQuery.fromQueryString(uriQuery);
            }
            if (newUri.getHost() == null) {
                //To keep the information about the latest host, we need to use uri from the last performed request
                //Example:
                //request -> my-test.com -> response redirect -> my-example.com
                //new request -> my-example.com -> response redirect -> /login
                //with using the last request uri host etc, we prevent my-test.com/login from happening
                redirectUri.scheme(clientRequest.uri.scheme());
                redirectUri.host(clientRequest.uri.host());
                redirectUri.port(clientRequest.uri.port());
            }
            //Method and entity is required to be the same as with original request with 307 and 308 requests
            if (clientResponse.status() == Http.Status.TEMPORARY_REDIRECT_307
                    || clientResponse.status() == Http.Status.PERMANENT_REDIRECT_308) {
                clientRequest = new ClientRequestImpl(this, method, redirectUri, newQuery, properties);
            } else {
                //It is possible to change to GET and send no entity with all other redirect codes
                entityToBeSent = BufferData.EMPTY_BYTES; //We do not want to send entity after this redirect
                clientRequest = new ClientRequestImpl(this, Http.Method.GET, redirectUri, newQuery, properties);
            }
        }
        throw new IllegalStateException("Maximum number of request redirections ("
                                                + clientConfig.maxRedirects() + ") reached.");
    }

    private ClientResponseImpl invokeRequestWithEntity(Object entity) {
        CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        WebClientService.Chain callChain = new HttpCallEntityChain(this,
                                                                   clientConfig,
                                                                   connection,
                                                                   tls,
                                                                   proxy,
                                                                   whenSent,
                                                                   whenComplete,
                                                                   entity);

        return invokeServices(callChain, whenSent, whenComplete);
    }

    @Override
    public Http.Method httpMethod() {
        return method;
    }

    @Override
    public UriPath uriPath() {
        return UriPath.create(uri.path());
    }

    @Override
    public UriQuery uriQuery() {
        return UriQuery.create(resolvedUri());
    }

    @Override
    public Http1ClientRequest proxy(Proxy proxy) {
        this.proxy = Objects.requireNonNull(proxy);
        return this;
    }

    Duration readTimeout() {
        return readTimeout;
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

        WebClientServiceRequest serviceRequest = new ServiceRequestImpl(uri,
                                                                        method,
                                                                        Http.Version.V1_1,
                                                                        query,
                                                                        UriFragment.empty(),
                                                                        headers,
                                                                        Contexts.context().orElseGet(Context::create),
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
                                      mediaContext,
                                      clientConfig.mediaTypeParserMode(),
                                      uri,
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
