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

package io.helidon.webclient.api;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import io.helidon.common.Version;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.tls.Tls;
import io.helidon.common.uri.UriEncoding;
import io.helidon.common.uri.UriFragment;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.Method;
import io.helidon.http.media.MediaContext;
import io.helidon.webclient.spi.WebClientService;

/**
 * Abstract base implementation of an HTTP client. Provides helpful methods to handle cookies, client services etc.
 *
 * @param <T> type of the request
 * @param <R> type of the response
 */
public abstract class ClientRequestBase<T extends ClientRequest<T>, R extends HttpClientResponse>
        implements FullClientRequest<T> {
    /**
     * Helidon user agent request header.
     */
    public static final Header USER_AGENT_HEADER = HeaderValues.create(HeaderNames.USER_AGENT,
                                                                       "Helidon " + Version.VERSION);
    /**
     * Proxy connection header.
     */
    public static final Header PROXY_CONNECTION = HeaderValues.create("Proxy-Connection", "keep-alive");
    private static final Map<String, AtomicLong> COUNTERS = new ConcurrentHashMap<>();
    private static final Set<String> SUPPORTED_SCHEMES = Set.of("https", "http");

    private final Map<String, String> pathParams = new HashMap<>();
    private final HttpClientConfig clientConfig;
    private final WebClientCookieManager cookieManager;
    private final String protocolId;
    private final Method method;
    private final ClientUri clientUri;
    private final Map<String, String> properties;
    private final ClientRequestHeaders headers;
    private final String requestId;
    private final MediaContext mediaContext;

    private String uriTemplate;
    private boolean skipUriEncoding;
    private boolean followRedirects;
    private int maxRedirects;
    private Duration readTimeout;
    private Duration readContinueTimeout;
    private Tls tls;
    private Proxy proxy;
    private boolean keepAlive;
    private ClientConnection connection;
    private Boolean sendExpectContinue;

    protected ClientRequestBase(HttpClientConfig clientConfig,
                                WebClientCookieManager cookieManager,
                                String protocolId,
                                Method method,
                                ClientUri clientUri,
                                Map<String, String> properties) {
        this(clientConfig, cookieManager, protocolId, method, clientUri, null, properties);
    }

    protected ClientRequestBase(HttpClientConfig clientConfig,
                                WebClientCookieManager cookieManager,
                                String protocolId,
                                Method method,
                                ClientUri clientUri,
                                Boolean sendExpectContinue,
                                Map<String, String> properties) {
        this.clientConfig = clientConfig;
        this.cookieManager = cookieManager;
        this.protocolId = protocolId;
        this.method = method;
        this.clientUri = clientUri;
        this.sendExpectContinue = sendExpectContinue;
        this.properties = new HashMap<>(properties);

        this.headers = clientConfig.defaultRequestHeaders();
        this.readTimeout = clientConfig.socketOptions().readTimeout();
        this.readContinueTimeout = clientConfig.readContinueTimeout();
        this.mediaContext = clientConfig.mediaContext();
        this.followRedirects = clientConfig.followRedirects();
        this.maxRedirects = clientConfig.maxRedirects();
        this.tls = clientConfig.tls();
        this.proxy = clientConfig.proxy();
        this.keepAlive = clientConfig.keepAlive();

        this.requestId = nextRequestId(protocolId);
    }

    @Override
    public T tls(Tls tls) {
        this.tls = tls;
        return identity();
    }

    @Override
    public T uri(URI uri) {
        this.uriTemplate = null;
        this.clientUri.resolve(uri);
        return identity();
    }

    @Override
    public T uri(ClientUri uri) {
        this.uriTemplate = null;
        this.clientUri.resolve(uri);
        return identity();
    }

    @Override
    public T path(String uri) {
        this.clientUri.resolvePath(uri);
        return identity();
    }

    @Override
    public T uri(String uri) {
        if (uri.indexOf('{') > -1) {
            this.uriTemplate = uri;
        } else {
            uri(URI.create(uri));
        }

        return identity();
    }

    @Override
    public ClientUri resolvedUri() {
        // we do not want to update our own URI, as this method may be called multiple times
        return resolveUri(ClientUri.create(this.clientUri));
    }

    @Override
    public ClientRequestHeaders headers() {
        return headers;
    }

    @Override
    public T header(Header header) {
        this.headers.set(header);
        return identity();
    }

    @Override
    public T headers(Headers headers) {
        for (Header header : headers) {
            this.headers.set(header);
        }
        return identity();
    }

    @Override
    public T headers(Consumer<ClientRequestHeaders> headersConsumer) {
        headersConsumer.accept(headers);
        return identity();
    }

    @Override
    public T fragment(UriFragment fragment) {
        this.clientUri.fragment(fragment);
        return identity();
    }

    @Override
    public T skipUriEncoding(boolean skip) {
        this.skipUriEncoding = skip;
        this.clientUri.skipUriEncoding(skip);
        return identity();
    }

    @Override
    public T queryParam(String name, String... values) {
        clientUri.writeableQuery().set(name, values);
        return identity();
    }

    @Override
    public T property(String propertyName, String propertyValue) {
        this.properties.put(propertyName, propertyValue);
        return identity();
    }

    @Override
    public T pathParam(String name, String value) {
        pathParams.put(name, value);
        return identity();
    }

    @Override
    public T followRedirects(boolean followRedirects) {
        this.followRedirects = followRedirects;
        return identity();
    }

    @Override
    public T maxRedirects(int maxRedirects) {
        this.maxRedirects = maxRedirects;
        return identity();
    }

    @Override
    public T connection(ClientConnection connection) {
        this.connection = connection;
        return identity();
    }

    @Override
    public T keepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
        return identity();
    }

    @Override
    public T readTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
        return identity();
    }

    @Override
    public T readContinueTimeout(Duration readContinueTimeout) {
        this.readContinueTimeout = readContinueTimeout;
        return identity();
    }

    @Override
    public T proxy(Proxy proxy) {
        this.proxy = Objects.requireNonNull(proxy);
        return identity();
    }

    @Override
    public R request() {
        additionalHeaders();
        return validateAndSubmit(BufferData.EMPTY_BYTES);
    }

    @Override
    public R submit(Object entity) {
        if (!(entity instanceof byte[] bytes && bytes.length == 0)) {
            rejectHeadWithEntity();
        }
        additionalHeaders();
        return validateAndSubmit(entity);
    }

    @Override
    public R outputStream(OutputStreamHandler outputStreamConsumer) {
        rejectHeadWithEntity();
        additionalHeaders();
        return doOutputStream(outputStreamConsumer);
    }

    @Override
    public T sendExpectContinue(boolean sendExpectContinue) {
        this.sendExpectContinue = sendExpectContinue;
        return identity();
    }

    /**
     * Append additional headers before sending the request.
     */
    protected void additionalHeaders() {
        headers.setIfAbsent(USER_AGENT_HEADER);
    }

    /**
     * HTTP method to be invoked.
     *
     * @return HTTP method
     */
    @Override
    public Method method() {
        return method;
    }

    /**
     * Properties configured by a user or by other components.
     *
     * @return properties
     */
    @Override
    public Map<String, String> properties() {
        return properties;
    }

    @Override
    public boolean followRedirects() {
        return followRedirects;
    }

    @Override
    public int maxRedirects() {
        return maxRedirects;
    }

    @Override
    public Tls tls() {
        return tls;
    }

    @Override
    public Proxy proxy() {
        return proxy;
    }

    @Override
    public Optional<ClientConnection> connection() {
        return Optional.ofNullable(connection);
    }

    @Override
    public Map<String, String> pathParams() {
        return pathParams;
    }

    @Override
    public ClientUri uri() {
        return clientUri;
    }

    @Override
    public String requestId() {
        return requestId;
    }

    @Override
    public Duration readTimeout() {
        return readTimeout;
    }

    @Override
    public Duration readContinueTimeout() {
        return readContinueTimeout;
    }

    @Override
    public boolean keepAlive() {
        return keepAlive;
    }

    @Override
    public boolean skipUriEncoding() {
        return skipUriEncoding;
    }

    @Override
    public Optional<Boolean> sendExpectContinue() {
        return Optional.ofNullable(sendExpectContinue);
    }

    protected abstract R doSubmit(Object entity);

    protected abstract R doOutputStream(OutputStreamHandler outputStreamHandler);

    /**
     * Invoke configured client services.
     *
     * @param whenSent      completable future to be completed when the request is sent over the network
     * @param whenComplete  completable future to be completed when the request/response interaction finishes
     * @param httpCallChain invocation of the HTTP request (the actual network call)
     * @param usedUri       URI configured on the request, combined with the base URI of the client
     * @return web client service response
     */
    protected WebClientServiceResponse invokeServices(WebClientService.Chain httpCallChain,
                                                      CompletableFuture<WebClientServiceRequest> whenSent,
                                                      CompletableFuture<WebClientServiceResponse> whenComplete,
                                                      ClientUri usedUri) {

        // include any stored cookies in request
        cookieManager.request(usedUri, headers);

        WebClientServiceRequest serviceRequest = new ServiceRequestImpl(usedUri,
                                                                        method,
                                                                        protocolId,
                                                                        headers,
                                                                        Contexts.context().orElseGet(Context::create),
                                                                        requestId,
                                                                        whenComplete,
                                                                        whenSent,
                                                                        properties);

        WebClientService.Chain last = httpCallChain;

        List<WebClientService> services = clientConfig.services();
        ListIterator<WebClientService> serviceIterator = services.listIterator(services.size());
        while (serviceIterator.hasPrevious()) {
            last = new ServiceChainImpl(last, serviceIterator.previous());
        }

        WebClientServiceResponse response = last.proceed(serviceRequest);
        cookieManager.response(usedUri, response.headers());

        return response;
    }

    /**
     * Associated client configuration.
     *
     * @return client config
     */
    protected HttpClientConfig clientConfig() {
        return clientConfig;
    }

    /**
     * Media context configured for this request.
     *
     * @return media context
     */
    protected MediaContext mediaContext() {
        return mediaContext;
    }

    /**
     * Resolve possible templated URI definition against the provided {@link ClientUri},
     * extracting possible query information into the provided writable query.
     *
     * @param toResolve client uri to update from the template
     * @return updated client uri
     */
    protected ClientUri resolveUri(ClientUri toResolve) {
        if (uriTemplate != null) {
            String resolved = resolvePathParams(uriTemplate);
            if (skipUriEncoding) {
                toResolve.resolve(URI.create(resolved));
            } else {
                toResolve.resolve(URI.create(UriEncoding.encodeUri(resolved)));
            }
        }
        return toResolve;
    }

    private static String nextRequestId(String protocolId) {
        AtomicLong counter = COUNTERS.computeIfAbsent(protocolId, it -> new AtomicLong());
        return "client-" + protocolId + "-" + Long.toHexString(counter.getAndIncrement());
    }

    private R validateAndSubmit(Object entity) {
        if (!SUPPORTED_SCHEMES.contains(uri().scheme())) {
            throw new IllegalArgumentException(
                    String.format("Not supported scheme %s, client supported schemes are: %s",
                                  uri().scheme(),
                                  String.join(", ", SUPPORTED_SCHEMES)
                    )
            );
        }
        return doSubmit(entity);
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
        if (Method.HEAD.equals(this.method)) {
            throw new IllegalArgumentException("Payload in method '" + Method.HEAD + "' has no defined semantics");
        }
    }

    @SuppressWarnings("unchecked")
    private T identity() {
        return (T) this;
    }
}
