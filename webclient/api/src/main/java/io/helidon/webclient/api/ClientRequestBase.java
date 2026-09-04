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

package io.helidon.webclient.api;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.UnixDomainSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.helidon.common.Api;
import io.helidon.common.Version;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.tls.Tls;
import io.helidon.common.uri.UriEncoding;
import io.helidon.common.uri.UriFragment;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;
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
    public static final Header USER_AGENT_HEADER = HeaderValues.createCached(HeaderNames.USER_AGENT,
                                                                             "Helidon " + Version.VERSION);
    /**
     * Proxy connection header.
     */
    public static final Header PROXY_CONNECTION = HeaderValues.createCached(HeaderNames.create("Proxy-Connection"),
                                                                           "keep-alive");
    private static final HeaderName AUTHORITY = HeaderNames.create(":authority");
    private static final Map<String, AtomicLong> COUNTERS = new ConcurrentHashMap<>();
    private static final Set<String> SUPPORTED_SCHEMES = Set.of("https", "http");

    private final Map<String, String> pathParams = new HashMap<>();
    private final HttpClientConfig clientConfig;
    private final WebClientCookieManager cookieManager;
    private final String protocolId;
    private final Method method;
    private final ClientUri clientUri;
    private final Map<String, String> properties;
    private final Set<HeaderName> redirectSensitiveHeaders;
    private final ClientRequestHeaders headers;
    private final String requestId;
    private final MediaContext mediaContext;
    private final boolean filterRedirectHeaders;

    private SocketAddress socketAddress;
    private UriTemplateQuery uriTemplate;
    private ClientUri finalizedEndpointUri;
    private ClientRequestHeaders finalizedRequestHeaders;
    private RedirectSecurityState redirectSecurityState;
    private boolean skipUriEncoding;
    private boolean followRedirects;
    private int maxRedirects;
    private Duration readTimeout;
    private Duration readContinueTimeout;
    private Tls tls;
    private SniConfig sni;
    private Proxy proxy;
    private long tlsGeneration = -1;
    private ProxyRoute selectedProxyRoute;
    private ClientRequestOrigin inheritedSelectedProxyRouteOrigin;
    private ProxyRoute lastSelectedProxyRoute;
    private ClientRequestOrigin inheritedLastSelectedProxyRouteOrigin;
    private WebClientServiceRequest serviceRequestAfterServices;
    private Consumer<WebClientServiceResponse> serviceResponseAfterServices;
    private CompletableFuture<WebClientServiceRequest> whenSentAfterServices;
    private Consumer<String> protocolAfterServices;
    private Consumer<WebClientProtocolResponse> protocolResponseAfterServices;
    private boolean dispatchPreparedAfterServices;
    private boolean responseCookiesDeferred;
    private boolean keepAlive;
    private ClientConnection connection;
    private ClientRequestOrigin inheritedConnectionOrigin;
    private ClientRequestOrigin inheritedAddressOrigin;
    private Boolean sendExpectContinue;

    /**
     * Create a new request.
     *
     * @param clientConfig client configuration
     * @param cookieManager cookie manager
     * @param protocolId protocol identifier
     * @param method HTTP method
     * @param clientUri request URI
     * @param properties request properties
     */
    protected ClientRequestBase(HttpClientConfig clientConfig,
                                WebClientCookieManager cookieManager,
                                String protocolId,
                                Method method,
                                ClientUri clientUri,
                                Map<String, String> properties) {
        this(clientConfig, cookieManager, protocolId, method, clientUri, null, properties);
    }

    /**
     * Create a new request.
     *
     * @param clientConfig client configuration
     * @param cookieManager cookie manager
     * @param protocolId protocol identifier
     * @param method HTTP method
     * @param clientUri request URI
     * @param sendExpectContinue whether to send the {@code Expect: 100-Continue} header
     * @param properties request properties
     */
    protected ClientRequestBase(HttpClientConfig clientConfig,
                                WebClientCookieManager cookieManager,
                                String protocolId,
                                Method method,
                                ClientUri clientUri,
                                Boolean sendExpectContinue,
                                Map<String, String> properties) {
        this(clientConfig, cookieManager, protocolId, method, clientUri, sendExpectContinue, properties, null);
    }

    /**
     * Create a new request.
     *
     * @param clientConfig client configuration
     * @param cookieManager cookie manager
     * @param protocolId protocol identifier
     * @param method HTTP method
     * @param clientUri request URI
     * @param sendExpectContinue whether to send the {@code Expect: 100-Continue} header
     * @param properties request properties
     * @param redirectSourceUri original request URI for redirect handling
     */
    protected ClientRequestBase(HttpClientConfig clientConfig,
                                WebClientCookieManager cookieManager,
                                String protocolId,
                                Method method,
                                ClientUri clientUri,
                                Boolean sendExpectContinue,
                                Map<String, String> properties,
                                ClientUri redirectSourceUri) {
        this.clientConfig = clientConfig;
        this.cookieManager = cookieManager;
        this.protocolId = protocolId;
        this.method = method;
        this.clientUri = clientUri;
        this.sendExpectContinue = sendExpectContinue;
        this.properties = new HashMap<>(properties);
        this.redirectSecurityState = RedirectSecurityState.legacy(redirectSourceUri, false);
        this.filterRedirectHeaders = clientConfig.filterRedirectHeaders();
        this.redirectSensitiveHeaders = clientConfig.redirectSensitiveHeaders();

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

        // this must be after we set clientUri, as it is used from the method
        clientConfig.baseAddress()
                .filter(it -> !(it instanceof UnixDomainSocketAddress)
                        || redirectSourceUri == null)
                .ifPresent(this::address);
    }

    @Override
    public T tls(Tls tls) {
        this.tls = tls;
        return identity();
    }

    @Override
    public T sni(SniConfig sni) {
        this.sni = Objects.requireNonNull(sni);
        return identity();
    }

    @Override
    public T uri(URI uri) {
        this.uriTemplate = null;
        this.clientUri.resolve(uri);
        return identity();
    }

    @Override
    public T address(SocketAddress socketAddress) {
        setAddress(socketAddress);
        inheritedAddressOrigin = null;
        return identity();
    }

    @Override
    public void inheritedAddress(SocketAddress socketAddress, ClientRequestOrigin origin) {
        setAddress(socketAddress);
        inheritedAddressOrigin = Objects.requireNonNull(origin);
    }

    private void setAddress(SocketAddress socketAddress) {
        Objects.requireNonNull(socketAddress);
        if (socketAddress instanceof InetSocketAddress inet) {
            this.clientUri.host(inet.getHostString())
                    .port(inet.getPort());
            this.socketAddress = null;
        } else if (socketAddress instanceof UnixDomainSocketAddress) {
            this.socketAddress = socketAddress;
        } else {
            throw new IllegalArgumentException("Unsupported socket address type: " + socketAddress.getClass().getName());
        }
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
            this.uriTemplate = new UriTemplateQuery(uri);
        } else {
            uri(URI.create(UriEncoding.encodeUri(uri)));
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
        UriTemplateQuery templateQuery = uriTemplate;
        if (templateQuery != null) {
            templateQuery.trackQueryParam(name);
        }
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
        this.connection = Objects.requireNonNull(connection);
        this.inheritedConnectionOrigin = null;
        return identity();
    }

    /**
     * Clear an internally supplied connection without changing other request state.
     */
    @Api.Internal
    public final void clearConnection() {
        this.connection = null;
    }

    @Override
    public void inheritedConnection(ClientConnection connection, ClientRequestOrigin origin) {
        this.connection = Objects.requireNonNull(connection);
        this.inheritedConnectionOrigin = Objects.requireNonNull(origin);
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
        clearSelectedProxyRoute();
        return identity();
    }

    @Override
    public R request() {
        try {
            return requestWithoutRouteCleanup();
        } finally {
            selectedProxyRoute = null;
            inheritedSelectedProxyRouteOrigin = null;
        }
    }

    /**
     * Submit an internal request that participates in the enclosing top-level request's selected route.
     *
     * @return response
     */
    @Api.Internal
    protected final R requestWithoutRouteCleanup() {
        additionalHeaders();
        validateQueryContentType();
        return validateAndSubmit(BufferData.EMPTY_BYTES);
    }

    @Override
    public R submit(Object entity) {
        try {
            if (!(entity instanceof byte[] bytes && bytes.length == 0)) {
                rejectHeadWithEntity();
            }
            additionalHeaders();
            if (entity instanceof byte[]) {
                validateQueryContentType();
            }
            return validateAndSubmit(entity);
        } finally {
            selectedProxyRoute = null;
            inheritedSelectedProxyRouteOrigin = null;
        }
    }

    @Override
    public R outputStream(OutputStreamHandler outputStreamConsumer) {
        try {
            rejectHeadWithEntity();
            additionalHeaders();
            validateQueryContentType();
            validateRequest();
            return doOutputStream(outputStreamConsumer);
        } finally {
            selectedProxyRoute = null;
            inheritedSelectedProxyRouteOrigin = null;
        }
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
    @Api.Internal
    public long tlsGeneration() {
        return tlsGeneration < 0 ? tls.generation() : tlsGeneration;
    }

    @Override
    @Api.Internal
    public void tlsGeneration(long tlsGeneration) {
        this.tlsGeneration = tlsGeneration;
    }

    @Override
    @Api.Internal
    public RedirectSecurityState redirectSecurityState() {
        return redirectSecurityState;
    }

    @Override
    @Api.Internal
    public void redirectSecurityState(RedirectSecurityState state) {
        this.redirectSecurityState = Objects.requireNonNull(state, "state");
    }

    @Override
    public Optional<SniConfig> sni() {
        return Optional.ofNullable(sni);
    }

    @Override
    public Proxy proxy() {
        return proxy;
    }

    @Override
    @Api.Internal
    public Optional<ProxyRoute> selectedProxyRoute() {
        return Optional.ofNullable(selectedProxyRoute);
    }

    @Override
    public Optional<ProxyRoute> lastSelectedProxyRoute() {
        return Optional.ofNullable(lastSelectedProxyRoute);
    }

    @Override
    public Optional<ClientRequestOrigin> inheritedLastSelectedProxyRouteOrigin() {
        return lastSelectedProxyRoute == null
                ? Optional.empty()
                : Optional.ofNullable(inheritedLastSelectedProxyRouteOrigin);
    }

    @Override
    @Api.Internal
    public void selectedProxyRoute(ProxyRoute proxyRoute) {
        this.selectedProxyRoute = Objects.requireNonNull(proxyRoute);
        this.inheritedSelectedProxyRouteOrigin = ClientRequestOrigin.create(resolvedUri(), headers);
        this.lastSelectedProxyRoute = proxyRoute;
        this.inheritedLastSelectedProxyRouteOrigin = inheritedSelectedProxyRouteOrigin;
    }

    @Override
    @Api.Internal
    public void inheritedSelectedProxyRoute(ProxyRoute proxyRoute, ClientRequestOrigin origin) {
        this.selectedProxyRoute = Objects.requireNonNull(proxyRoute);
        this.inheritedSelectedProxyRouteOrigin = Objects.requireNonNull(origin);
        this.lastSelectedProxyRoute = proxyRoute;
        this.inheritedLastSelectedProxyRouteOrigin = origin;
    }

    @Override
    @Api.Internal
    public void clearSelectedProxyRoute() {
        this.selectedProxyRoute = null;
        this.inheritedSelectedProxyRouteOrigin = null;
        this.lastSelectedProxyRoute = null;
        this.inheritedLastSelectedProxyRouteOrigin = null;
    }

    @Override
    public Optional<ClientConnection> connection() {
        return Optional.ofNullable(connection);
    }

    @Override
    public Optional<ClientRequestOrigin> inheritedConnectionOrigin() {
        return connection == null ? Optional.empty() : Optional.ofNullable(inheritedConnectionOrigin);
    }

    @Override
    public Optional<ClientRequestOrigin> inheritedAddressOrigin() {
        return socketAddress == null ? Optional.empty() : Optional.ofNullable(inheritedAddressOrigin);
    }

    @Override
    public Optional<ClientRequestOrigin> inheritedSelectedProxyRouteOrigin() {
        return selectedProxyRoute == null ? Optional.empty() : Optional.ofNullable(inheritedSelectedProxyRouteOrigin);
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

    @Override
    public Optional<SocketAddress> address() {
        return Optional.ofNullable(socketAddress);
    }

    /**
     * Submit a request entity.
     *
     * @param entity request entity
     * @return HTTP client response
     */
    protected abstract R doSubmit(Object entity);

    /**
     * Submit a request entity provided through an output stream.
     *
     * @param outputStreamHandler output stream handler
     * @return HTTP client response
     */
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
        return invokeServices(null,
                              httpCallChain,
                              whenSent,
                              whenComplete,
                              usedUri,
                              protocolId,
                              null,
                              request -> {
                              });
    }

    /**
     * Invoke configured client services.
     *
     * @param whenSent      completable future to be completed when the request is sent over the network
     * @param whenComplete  completable future to be completed when the request/response interaction finishes
     * @param httpCallChain invocation of the HTTP request (the actual network call)
     * @param usedUri       URI configured on the request, combined with the base URI of the client
     * @return web client service response
     */
    @Api.Internal
    protected WebClientServiceResponse invokeServices(WebClientService.WireProtocolChain httpCallChain,
                                                      CompletableFuture<WebClientServiceRequest> whenSent,
                                                      CompletableFuture<WebClientServiceResponse> whenComplete,
                                                      ClientUri usedUri) {
        return invokeServices(null,
                              httpCallChain,
                              whenSent,
                              whenComplete,
                              usedUri,
                              null,
                              Objects.requireNonNull(httpCallChain),
                              request -> {
                              });
    }

    /**
     * Invoke configured client services with a typed wire-protocol chain and a terminal request preparation step.
     *
     * @param httpCallChain terminal wire-protocol chain
     * @param whenSent request-sent completion
     * @param whenComplete request/response completion
     * @param usedUri resolved request URI
     * @param requestPrepare request preparation performed immediately before terminal dispatch
     * @return web client service response
     */
    @Api.Internal
    protected WebClientServiceResponse invokeServices(WebClientService.WireProtocolChain httpCallChain,
                                                      CompletableFuture<WebClientServiceRequest> whenSent,
                                                      CompletableFuture<WebClientServiceResponse> whenComplete,
                                                      ClientUri usedUri,
                                                      Consumer<WebClientServiceRequest> requestPrepare) {
        return invokeServices(null,
                              httpCallChain,
                              whenSent,
                              whenComplete,
                              usedUri,
                              null,
                              Objects.requireNonNull(httpCallChain),
                              requestPrepare);
    }

    /**
     * Invoke configured client services and publish applicable transport response context before they unwind.
     *
     * @param webClient client that owns the transport protocols
     * @param httpCallChain invocation of the HTTP request (the actual network call)
     * @param whenSent completable future to be completed when the request is sent over the network
     * @param whenComplete completable future to be completed when the request/response interaction finishes
     * @param usedUri URI configured on the request, combined with the base URI of the client
     * @return web client service response
     */
    @Api.Internal
    protected WebClientServiceResponse invokeServices(WebClient webClient,
                                                      WebClientService.TransportChain httpCallChain,
                                                      CompletableFuture<WebClientServiceRequest> whenSent,
                                                      CompletableFuture<WebClientServiceResponse> whenComplete,
                                                      ClientUri usedUri) {
        return invokeServices(webClient,
                              httpCallChain,
                              whenSent,
                              whenComplete,
                              usedUri,
                              request -> {
                              });
    }

    /**
     * Invoke configured client services and publish applicable transport response context before they unwind.
     *
     * @param webClient client that owns the transport protocols
     * @param httpCallChain invocation of the HTTP request (the actual network call)
     * @param whenSent completable future to be completed when the request is sent over the network
     * @param whenComplete completable future to be completed when the request/response interaction finishes
     * @param usedUri URI configured on the request, combined with the base URI of the client
     * @param requestPrepare request preparation performed immediately before terminal dispatch
     * @return web client service response
     */
    @Api.Internal
    protected WebClientServiceResponse invokeServices(WebClient webClient,
                                                      WebClientService.TransportChain httpCallChain,
                                                      CompletableFuture<WebClientServiceRequest> whenSent,
                                                      CompletableFuture<WebClientServiceResponse> whenComplete,
                                                      ClientUri usedUri,
                                                      Consumer<WebClientServiceRequest> requestPrepare) {
        WebClient client = Objects.requireNonNull(webClient, "webClient");
        WebClientService.TransportChain chain = Objects.requireNonNull(httpCallChain, "httpCallChain");
        return invokeServices(client,
                              chain,
                              whenSent,
                              whenComplete,
                              usedUri,
                              null,
                              chain,
                              requestPrepare);
    }

    @SuppressWarnings("checkstyle:ParameterNumber") // central service path keeps protocol and transport modes together
    private WebClientServiceResponse invokeServices(WebClient webClient,
                                                    WebClientService.Chain httpCallChain,
                                                    CompletableFuture<WebClientServiceRequest> whenSent,
                                                    CompletableFuture<WebClientServiceResponse> whenComplete,
                                                    ClientUri usedUri,
                                                    String protocolId,
                                                    WebClientService.WireProtocolChain wireProtocolChain,
                                                    Consumer<WebClientServiceRequest> requestPrepare) {
        Objects.requireNonNull(requestPrepare, "requestPrepare");
        finalizedEndpointUri = null;
        finalizedRequestHeaders = null;
        ClientRequestHeaders invocationHeaders = snapshotHeaders(headers);
        CookieDispatchState cookieState = null;
        if (serviceRequestAfterServices == null || !dispatchPreparedAfterServices) {
            // Include cookies for the current effective authority. A service may still rewrite the final target; terminal
            // dispatch sanitizes and rebuilds cookies after all services have run.
            ClientUri cookieUri = ClientRequestOrigin.create(usedUri, invocationHeaders).apply(usedUri);
            List<String> explicitCookies = invocationHeaders.contains(HeaderNames.COOKIE)
                    ? List.copyOf(invocationHeaders.get(HeaderNames.COOKIE).allValues())
                    : List.of();
            boolean crossesOrigin = redirectSecurityState.wouldCrossOrigin(usedUri, invocationHeaders);
            if (redirectSecurityState.automaticCookiesAllowed()) {
                appendManagedCookies(cookieUri,
                                     invocationHeaders,
                                     !filterRedirectHeaders || !crossesOrigin,
                                     redirectSecurityState.suppressedCookieNames());
            }
            List<String> provisionalCookies = invocationHeaders.contains(HeaderNames.COOKIE)
                    ? List.copyOf(invocationHeaders.get(HeaderNames.COOKIE).allValues())
                    : List.of();
            List<String> managerCookies = cookiePairs(provisionalCookies);
            cookiePairs(explicitCookies).forEach(managerCookies::remove);
            cookieState = new CookieDispatchState(ClientUri.create(cookieUri),
                                                  cookieSnapshot(invocationHeaders),
                                                  List.copyOf(managerCookies));
        }
        if (serviceRequestAfterServices != null) {
            whenSent.whenComplete((request, failure) -> {
                if (failure == null) {
                    protocolAfterServices.accept(wireProtocolChain == null
                                                         ? protocolId
                                                         : wireProtocolChain.protocolId());
                    whenSentAfterServices.complete(request);
                } else {
                    whenSentAfterServices.completeExceptionally(failure);
                }
            });
            serviceRequestAfterServices.headers().clear();
            invocationHeaders.forEach(serviceRequestAfterServices.headers()::set);
            try {
                if (dispatchPreparedAfterServices) {
                    ClientRequestHeaders preparedHeaders = serviceRequestAfterServices.headers();
                    normalizeAuthority(preparedHeaders);
                    ClientRequestOrigin originBeforePreparation =
                            ClientRequestOrigin.create(serviceRequestAfterServices.uri(), preparedHeaders);
                    CookieSnapshot cookiesBeforePreparation = cookieSnapshot(preparedHeaders);
                    requestPrepare.accept(serviceRequestAfterServices);
                    normalizeAuthority(preparedHeaders);
                    ClientRequestOrigin originAfterPreparation =
                            ClientRequestOrigin.create(serviceRequestAfterServices.uri(), preparedHeaders);
                    if (!originBeforePreparation.equals(originAfterPreparation)
                            || !cookiesBeforePreparation.equals(cookieSnapshot(preparedHeaders))) {
                        throw new IllegalStateException("A prepared request handoff changed effective origin or Cookie");
                    }
                } else {
                    prepareForDispatch(serviceRequestAfterServices,
                                       requestPrepare,
                                       Objects.requireNonNull(cookieState));
                }
                ClientRequestHeaderSupport.validate(serviceRequestAfterServices.headers());
                captureFinalizedRequest(serviceRequestAfterServices);
                WebClientServiceResponse response = httpCallChain.proceed(serviceRequestAfterServices);
                captureFinalizedRequest(response.serviceRequest());
                publishProtocolResponse(webClient, wireProtocolChain, response);
                serviceResponseAfterServices.accept(response);
                return response;
            } catch (RuntimeException | Error failure) {
                try {
                    captureFinalizedRequest(serviceRequestAfterServices);
                } catch (RuntimeException snapshotFailure) {
                    if (snapshotFailure != failure) {
                        failure.addSuppressed(snapshotFailure);
                    }
                }
                whenSent.completeExceptionally(failure);
                throw failure;
            }
        }
        CookieDispatchState dispatchCookieState = Objects.requireNonNull(cookieState);

        WebClientServiceRequest serviceRequest = new ServiceRequestImpl(usedUri,
                                                                        method,
                                                                        protocolId,
                                                                        wireProtocolChain,
                                                                        invocationHeaders,
                                                                        Contexts.context().orElseGet(Context::create),
                                                                        requestId,
                                                                        whenComplete,
                                                                        whenSent,
                                                                        properties);

        AtomicReference<TerminalDispatchState> terminalDispatch =
                new AtomicReference<>(TerminalDispatchState.NOT_INVOKED);
        WebClientService.Chain last = request -> {
            terminalDispatch.set(TerminalDispatchState.IN_PROGRESS);
            try {
                captureFinalizedRequest(request);
                prepareForDispatch(request, requestPrepare, dispatchCookieState);
                captureFinalizedRequest(request);
                WebClientServiceResponse response = httpCallChain.proceed(request);
                captureFinalizedRequest(response.serviceRequest());
                publishProtocolResponse(webClient, wireProtocolChain, response);
                terminalDispatch.set(TerminalDispatchState.RETURNED);
                return response;
            } catch (RuntimeException | Error failure) {
                try {
                    captureFinalizedRequest(request);
                    redirectSecurityState(redirectSecurityState.finalized(request.uri(), request.headers()));
                } catch (RuntimeException snapshotFailure) {
                    if (snapshotFailure != failure) {
                        failure.addSuppressed(snapshotFailure);
                    }
                }
                terminalDispatch.set(TerminalDispatchState.FAILED);
                whenSent.completeExceptionally(failure);
                throw failure;
            }
        };

        List<WebClientService> services = clientConfig.services();
        ListIterator<WebClientService> serviceIterator = services.listIterator(services.size());
        while (serviceIterator.hasPrevious()) {
            last = new ServiceChainImpl(last, serviceIterator.previous());
        }

        WebClientServiceResponse response = last.proceed(serviceRequest);
        WebClientServiceRequest responseRequest = response.serviceRequest();
        if (terminalDispatch.get() == TerminalDispatchState.NOT_INVOKED) {
            ManagedCookiePolicy cookiePolicy = new ManagedCookiePolicy(dispatchCookieState.managerCookies(),
                                                                       redirectSecurityState.automaticCookiesAllowed(),
                                                                       redirectSecurityState.suppressedCookieNames());
            cookiePolicy.observe(dispatchCookieState.provisionalCookies(), cookieSnapshot(responseRequest.headers()));
            redirectSecurityState(redirectSecurityState.cookiePolicy(cookiePolicy.automaticAllowed(),
                                                                      cookiePolicy.suppressedNames()));
            redirectSecurityState(redirectSecurityState.finalized(responseRequest.uri(), responseRequest.headers()));
            captureFinalizedRequest(responseRequest);
            whenSent.complete(responseRequest);
        }
        ClientUri responseEndpointUri = finalizedEndpointUri();
        ClientUri responseCookieUri = ClientRequestOrigin.create(responseEndpointUri, finalizedRequestHeaders)
                .apply(responseEndpointUri);
        if (!responseCookiesDeferred) {
            cookieManager.response(responseCookieUri, response.headers());
        }

        return response;
    }

    private void publishProtocolResponse(WebClient webClient,
                                         WebClientService.WireProtocolChain wireProtocolChain,
                                         WebClientServiceResponse response) {
        if (wireProtocolChain instanceof WebClientService.TransportChain transportChain) {
            Optional<WebClientProtocolResponse> protocolResponse = transportChain.protocolResponse(response);
            Consumer<WebClientProtocolResponse> responseConsumer = protocolResponseAfterServices;
            if (responseConsumer != null) {
                protocolResponse.ifPresent(responseConsumer);
            } else if (webClient != null) {
                protocolResponse.ifPresent(webClient::responseReceived);
            }
        }
    }

    private void captureFinalizedRequest(WebClientServiceRequest request) {
        finalizedEndpointUri = ClientUri.create(request.uri());
        finalizedRequestHeaders = snapshotHeaders(request.headers());
    }

    private void prepareForDispatch(WebClientServiceRequest request,
                                    Consumer<WebClientServiceRequest> requestPrepare,
                                    CookieDispatchState cookieState) {
        ClientRequestHeaders requestHeaders = request.headers();
        normalizeAuthority(requestHeaders);
        ManagedCookiePolicy cookiePolicy = new ManagedCookiePolicy(cookieState.managerCookies(),
                                                                   redirectSecurityState.automaticCookiesAllowed(),
                                                                   redirectSecurityState.suppressedCookieNames());
        CookieSnapshot serviceCookies = cookieSnapshot(requestHeaders);
        cookiePolicy.observe(cookieState.provisionalCookies(), serviceCookies);
        redirectSecurityState(redirectSecurityState.cookiePolicy(cookiePolicy.automaticAllowed(),
                                                                  cookiePolicy.suppressedNames()));
        try {
            requestPrepare.accept(request);
        } catch (RuntimeException | Error failure) {
            try {
                cookiePolicy.observe(serviceCookies, cookieSnapshot(requestHeaders));
                redirectSecurityState(redirectSecurityState.cookiePolicy(cookiePolicy.automaticAllowed(),
                                                                          cookiePolicy.suppressedNames()));
            } catch (RuntimeException cookieFailure) {
                if (cookieFailure != failure) {
                    failure.addSuppressed(cookieFailure);
                }
            }
            throw failure;
        }
        normalizeAuthority(requestHeaders);
        cookiePolicy.observe(serviceCookies, cookieSnapshot(requestHeaders));
        redirectSecurityState(redirectSecurityState.cookiePolicy(cookiePolicy.automaticAllowed(),
                                                                  cookiePolicy.suppressedNames()));

        RedirectSecurityState candidateState = redirectSecurityState.finalized(request.uri(), requestHeaders);
        boolean stripHeaders = filterRedirectHeaders && candidateState.crossedOrigin();
        if (stripHeaders) {
            redirectSensitiveHeaders.forEach(requestHeaders::remove);
        }

        ClientRequestOrigin finalOrigin = ClientRequestOrigin.create(request.uri(), requestHeaders);
        if (inheritedConnectionOrigin != null && !inheritedConnectionOrigin.equals(finalOrigin)) {
            connection = null;
            inheritedConnectionOrigin = null;
        }
        if (inheritedAddressOrigin != null && !inheritedAddressOrigin.equals(finalOrigin)) {
            socketAddress = null;
            inheritedAddressOrigin = null;
        }
        if (inheritedSelectedProxyRouteOrigin != null && !inheritedSelectedProxyRouteOrigin.equals(finalOrigin)) {
            selectedProxyRoute = null;
            inheritedSelectedProxyRouteOrigin = null;
            lastSelectedProxyRoute = null;
            inheritedLastSelectedProxyRouteOrigin = null;
        }
        ClientUri finalCookieUri = finalOrigin.apply(request.uri());
        ClientUri provisionalCookieUri = cookieState.provisionalUri();
        String provisionalCookiePath = provisionalCookieUri.path().rawPath();
        if (provisionalCookiePath.isEmpty()) {
            provisionalCookiePath = "/";
        }
        String finalCookiePath = finalCookieUri.path().rawPath();
        if (finalCookiePath.isEmpty()) {
            finalCookiePath = "/";
        }
        boolean retargetCookies = !ClientRequestOrigin.create(provisionalCookieUri).equals(finalOrigin)
                || !provisionalCookiePath.equals(finalCookiePath);
        if (stripHeaders) {
            redirectSecurityState(candidateState.finalized(request.uri(), requestHeaders));
            if (cookiePolicy.automaticAllowed()) {
                appendManagedCookies(finalCookieUri,
                                     requestHeaders,
                                     false,
                                     cookiePolicy.suppressedNames());
            }
        } else {
            redirectSecurityState(candidateState);
            if (retargetCookies) {
                removeManagerCookies(requestHeaders, cookiePolicy.survivingManagerCookies());
                if (cookiePolicy.automaticAllowed()) {
                    appendManagedCookies(finalCookieUri,
                                         requestHeaders,
                                         true,
                                         cookiePolicy.suppressedNames());
                }
            }
        }
        ClientRequestHeaderSupport.validate(requestHeaders);
        if (redirectSecurityState.replayingEntity()
                && !clientConfig.followCrossOriginEntityRedirects()
                && redirectSecurityState.crossedOrigin()) {
            throw new IllegalStateException("Cross-origin redirect with request entity is disabled.");
        }
    }

    private void appendManagedCookies(ClientUri uri,
                                      ClientRequestHeaders requestHeaders,
                                      boolean includeDefaultCookies,
                                      Set<String> suppressedNames) {
        ClientRequestHeaders managedHeaders = ClientRequestHeaders.create(WritableHeaders.create());
        cookieManager.request(uri, managedHeaders, includeDefaultCookies);
        if (!managedHeaders.contains(HeaderNames.COOKIE)) {
            return;
        }
        Header managedCookieHeader = managedHeaders.get(HeaderNames.COOKIE);
        List<String> retainedValues = new ArrayList<>(managedCookieHeader.valueCount());
        for (String value : managedCookieHeader.allValues()) {
            List<String> retainedPairs = new ArrayList<>();
            for (String pair : cookiePairs(List.of(value))) {
                if (!suppressedNames.contains(cookieName(pair))) {
                    retainedPairs.add(pair);
                }
            }
            if (!retainedPairs.isEmpty()) {
                retainedValues.add(String.join("; ", retainedPairs));
            }
        }
        if (!retainedValues.isEmpty()) {
            requestHeaders.add(HeaderNames.COOKIE, retainedValues.toArray(String[]::new));
        }
    }

    private static void removeManagerCookies(ClientRequestHeaders requestHeaders, List<String> managerCookies) {
        if (managerCookies.isEmpty() || !requestHeaders.contains(HeaderNames.COOKIE)) {
            return;
        }
        Header currentCookieHeader = requestHeaders.get(HeaderNames.COOKIE);
        List<String> remainingManagerCookies = new ArrayList<>(managerCookies);
        List<String> retainedCookies = new ArrayList<>(currentCookieHeader.valueCount());
        for (String currentCookie : currentCookieHeader.allValues()) {
            List<String> retainedPairs = new ArrayList<>();
            for (String pair : cookiePairs(List.of(currentCookie))) {
                if (!remainingManagerCookies.remove(pair)) {
                    retainedPairs.add(pair);
                }
            }
            if (!retainedPairs.isEmpty()) {
                retainedCookies.add(String.join("; ", retainedPairs));
            }
        }
        requestHeaders.remove(HeaderNames.COOKIE);
        if (!retainedCookies.isEmpty()) {
            requestHeaders.set(HeaderValues.create(HeaderNames.COOKIE,
                                                   currentCookieHeader.changing(),
                                                   currentCookieHeader.sensitive(),
                                                   retainedCookies.toArray(String[]::new)));
        }
    }

    private static void normalizeAuthority(ClientRequestHeaders requestHeaders) {
        if (!requestHeaders.contains(AUTHORITY)) {
            return;
        }
        Header authority = requestHeaders.get(AUTHORITY);
        if (authority.valueCount() != 1) {
            throw new IllegalArgumentException("Request :authority must contain exactly one value");
        }
        requestHeaders.remove(AUTHORITY);
        requestHeaders.set(HeaderValues.create(HeaderNames.HOST,
                                               authority.changing(),
                                               authority.sensitive(),
                                               authority.get()));
    }

    private static ClientRequestHeaders snapshotHeaders(Headers source) {
        ClientRequestHeaders snapshot = ClientRequestHeaders.create(WritableHeaders.create());
        source.forEach(header -> snapshot.set(HeaderValues.create(header.headerName(),
                                                                  header.changing(),
                                                                  header.sensitive(),
                                                                  header.allValues().toArray(String[]::new))));
        return snapshot;
    }

    private static CookieSnapshot cookieSnapshot(Headers headers) {
        return headers.contains(HeaderNames.COOKIE)
                ? new CookieSnapshot(true, List.copyOf(headers.get(HeaderNames.COOKIE).allValues()))
                : CookieSnapshot.ABSENT;
    }

    /**
     * Store cookies from an intermediate response consumed inside a protocol call chain.
     *
     * @param endpointUri endpoint that produced the response
     * @param responseHeaders response headers
     */
    @Api.Internal
    public final void recordResponseCookies(ClientUri endpointUri, ClientResponseHeaders responseHeaders) {
        cookieManager.response(endpointUri, responseHeaders);
    }

    /**
     * Defer storage of the returned response cookies to an enclosing protocol request.
     */
    @Api.Internal
    public final void deferResponseCookies() {
        responseCookiesDeferred = true;
    }

    /**
     * Whether storage of the returned response cookies is deferred to an enclosing protocol request.
     *
     * @return whether response cookie storage is deferred
     */
    @Api.Internal
    public final boolean responseCookiesDeferred() {
        return responseCookiesDeferred;
    }

    /**
     * Reuse a service-finalized request when a protocol is selected through ALPN after services have already run.
     *
     * @param serviceRequest service-finalized request
     */
    @Api.Internal
    @Override
    public boolean serviceRequestAfterServices(WebClientServiceRequest serviceRequest,
                                               Consumer<WebClientServiceResponse> responseConsumer,
                                               CompletableFuture<WebClientServiceRequest> whenSent,
                                               Consumer<String> protocolConsumer) {
        return serviceRequestAfterServices(serviceRequest,
                                           responseConsumer,
                                           whenSent,
                                           protocolConsumer,
                                           false);
    }

    /**
     * Reuse a service-finalized request and identify whether common terminal dispatch preparation has completed.
     *
     * @param serviceRequest service-finalized request
     * @param responseConsumer transport response consumer
     * @param whenSent request-sent completion
     * @param protocolConsumer selected protocol consumer
     * @param dispatchPrepared whether common terminal dispatch preparation has completed
     * @return whether this request accepted the handoff
     */
    @Api.Internal
    @Override
    public boolean serviceRequestAfterServices(WebClientServiceRequest serviceRequest,
                                               Consumer<WebClientServiceResponse> responseConsumer,
                                               CompletableFuture<WebClientServiceRequest> whenSent,
                                               Consumer<String> protocolConsumer,
                                               boolean dispatchPrepared) {
        return configureServiceRequestAfterServices(serviceRequest,
                                                    responseConsumer,
                                                    whenSent,
                                                    protocolConsumer,
                                                    dispatchPrepared,
                                                    null);
    }

    /**
     * Reuse a service-finalized request, identify whether common terminal dispatch preparation has completed, and
     * forward exact transport response context to the enclosing protocol.
     *
     * @param serviceRequest service-finalized request
     * @param responseConsumer transport response consumer
     * @param whenSent request-sent completion
     * @param protocolConsumer selected protocol consumer
     * @param dispatchPrepared whether common terminal dispatch preparation has completed
     * @param protocolResponseConsumer exact transport response context consumer
     * @return whether this request accepted the handoff
     */
    @Api.Internal
    @Override
    public boolean serviceRequestAfterServices(WebClientServiceRequest serviceRequest,
                                               Consumer<WebClientServiceResponse> responseConsumer,
                                               CompletableFuture<WebClientServiceRequest> whenSent,
                                               Consumer<String> protocolConsumer,
                                               boolean dispatchPrepared,
                                               Consumer<WebClientProtocolResponse> protocolResponseConsumer) {
        return configureServiceRequestAfterServices(serviceRequest,
                                                    responseConsumer,
                                                    whenSent,
                                                    protocolConsumer,
                                                    dispatchPrepared,
                                                    Objects.requireNonNull(protocolResponseConsumer));
    }

    private boolean configureServiceRequestAfterServices(WebClientServiceRequest serviceRequest,
                                                         Consumer<WebClientServiceResponse> responseConsumer,
                                                         CompletableFuture<WebClientServiceRequest> whenSent,
                                                         Consumer<String> protocolConsumer,
                                                         boolean dispatchPrepared,
                                                         Consumer<WebClientProtocolResponse> protocolResponseConsumer) {
        this.serviceRequestAfterServices = Objects.requireNonNull(serviceRequest);
        this.serviceResponseAfterServices = Objects.requireNonNull(responseConsumer);
        this.whenSentAfterServices = Objects.requireNonNull(whenSent);
        this.protocolAfterServices = Objects.requireNonNull(protocolConsumer);
        this.protocolResponseAfterServices = protocolResponseConsumer;
        this.dispatchPreparedAfterServices = dispatchPrepared;
        return true;
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
     * Immutable URI snapshot captured immediately before dispatch of the most recent invocation.
     *
     * @return finalized endpoint URI
     */
    protected final ClientUri finalizedEndpointUri() {
        if (finalizedEndpointUri == null) {
            throw new IllegalStateException("Request endpoint has not been finalized");
        }
        return ClientUri.create(finalizedEndpointUri);
    }

    /**
     * Immutable header snapshot captured by the terminal transport of the most recent invocation.
     *
     * @return finalized request headers
     */
    protected final ClientRequestHeaders finalizedRequestHeaders() {
        if (finalizedRequestHeaders == null) {
            throw new IllegalStateException("Request headers have not been finalized");
        }
        return snapshotHeaders(finalizedRequestHeaders);
    }

    /**
     * Create redirect headers from the configured request plus mutations made by services after terminal dispatch.
     * Headers already present at terminal dispatch are not copied, so source-attempt defaults, managed cookies, and
     * entity-writer mutations can be recomputed or conditionally replayed for the target attempt.
     *
     * @param headersAfterServices request headers after the complete service chain returned
     * @return redirect source headers
     */
    protected final ClientRequestHeaders redirectSourceHeaders(Headers headersAfterServices) {
        Objects.requireNonNull(headersAfterServices, "headersAfterServices");
        ClientRequestHeaders redirectHeaders = snapshotHeaders(headers);
        ClientRequestHeaders dispatchedHeaders = finalizedRequestHeaders();
        ClientRequestHeaders postServiceHeaders = snapshotHeaders(headersAfterServices);

        dispatchedHeaders.forEach(dispatchedHeader -> {
            HeaderName name = dispatchedHeader.headerName();
            if (name.equals(HeaderNames.COOKIE)) {
                return;
            }
            if (!postServiceHeaders.contains(name)) {
                redirectHeaders.remove(name);
                if (name.equals(HeaderNames.HOST)) {
                    redirectHeaders.remove(AUTHORITY);
                }
                return;
            }
            Header postServiceHeader = postServiceHeaders.get(name);
            if (dispatchedHeader.changing() != postServiceHeader.changing()
                    || dispatchedHeader.sensitive() != postServiceHeader.sensitive()
                    || !dispatchedHeader.allValues().equals(postServiceHeader.allValues())) {
                redirectHeaders.set(postServiceHeader);
                if (name.equals(HeaderNames.HOST)) {
                    redirectHeaders.remove(AUTHORITY);
                }
            }
        });
        postServiceHeaders.forEach(postServiceHeader -> {
            HeaderName name = postServiceHeader.headerName();
            if (!name.equals(HeaderNames.COOKIE) && !dispatchedHeaders.contains(name)) {
                redirectHeaders.set(postServiceHeader);
                if (name.equals(HeaderNames.HOST)) {
                    redirectHeaders.remove(AUTHORITY);
                }
            }
        });
        return redirectHeaders;
    }

    /**
     * Resolve an HTTP redirect URI reference against the endpoint that produced the response.
     *
     * @param sourceUri actual response endpoint URI
     * @param location Location header field value
     * @return resolved redirect URI
     */
    @Api.Internal
    public final ClientUri resolveRedirectUri(ClientUri sourceUri, String location) {
        URI reference = URI.create(location);
        URI source = sourceUri.toUri();
        URI resolved;
        if (!reference.isAbsolute()
                && reference.getRawAuthority() == null
                && reference.getRawPath().isEmpty()) {
            StringBuilder value = new StringBuilder(source.getScheme())
                    .append("://")
                    .append(source.getRawAuthority())
                    .append(source.getRawPath());
            String query = reference.getRawQuery();
            if (query == null) {
                query = source.getRawQuery();
            }
            if (query != null) {
                value.append('?').append(query);
            }
            if (reference.getRawFragment() != null) {
                value.append('#').append(reference.getRawFragment());
            }
            resolved = URI.create(value.toString());
        } else {
            resolved = source.resolve(reference);
        }
        if (reference.getRawFragment() == null && source.getRawFragment() != null) {
            String value = resolved.toASCIIString();
            int fragmentIndex = value.indexOf('#');
            if (fragmentIndex >= 0) {
                value = value.substring(0, fragmentIndex);
            }
            resolved = URI.create(value + '#' + source.getRawFragment());
        }
        ClientUri redirectUri = ClientUri.create(resolved);
        validateScheme(redirectUri.scheme());
        return redirectUri;
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
        UriTemplateQuery templateQuery = uriTemplate;
        if (templateQuery != null) {
            String resolved = resolvePathParams(templateQuery.template());
            URI uri;
            if (skipUriEncoding) {
                uri = URI.create(resolved);
            } else {
                uri = URI.create(UriEncoding.encodeUri(resolved));
            }
            boolean replayQuery = skipUriEncoding || uri.isAbsolute();
            ClientUri querySource = replayQuery && toResolve == clientUri ? ClientUri.create(clientUri) : clientUri;
            toResolve.resolve(uri);

            if (replayQuery) {
                templateQuery.replay(querySource.query(), toResolve.writeableQuery(), uri.isAbsolute());
            }
        }
        return toResolve;
    }

    private static String nextRequestId(String protocolId) {
        AtomicLong counter = COUNTERS.computeIfAbsent(protocolId, it -> new AtomicLong());
        return "client-" + protocolId + "-" + Long.toHexString(counter.getAndIncrement());
    }

    /**
     * Whether two request URIs have the same normalized origin.
     *
     * @param sourceUri source request URI
     * @param targetUri target request URI
     * @return whether the scheme, host, and effective port are equal
     */
    protected static boolean sameOrigin(ClientUri sourceUri, ClientUri targetUri) {
        return ClientRequestOrigin.create(sourceUri).equals(ClientRequestOrigin.create(targetUri));
    }

    private static List<String> cookiePairs(List<String> headerValues) {
        List<String> result = new ArrayList<>();
        for (String headerValue : headerValues) {
            for (String pair : headerValue.split(";", -1)) {
                pair = pair.trim();
                if (!pair.isEmpty()) {
                    result.add(pair);
                }
            }
        }
        return result;
    }

    private static String cookieName(String pair) {
        int equals = pair.indexOf('=');
        return equals < 0 ? pair : pair.substring(0, equals).trim();
    }

    private static int occurrences(List<String> pairs, String expected) {
        int result = 0;
        for (String pair : pairs) {
            if (expected.equals(pair)) {
                result++;
            }
        }
        return result;
    }

    private record CookieDispatchState(ClientUri provisionalUri,
                                       CookieSnapshot provisionalCookies,
                                       List<String> managerCookies) {
    }

    private record CookieSnapshot(boolean present, List<String> values) {
        private static final CookieSnapshot ABSENT = new CookieSnapshot(false, List.of());
    }

    private static final class ManagedCookiePolicy {
        private final List<String> survivingManagerCookies;
        private final Set<String> suppressedNames;
        private boolean automaticAllowed;

        private ManagedCookiePolicy(List<String> managerCookies,
                                    boolean automaticAllowed,
                                    Set<String> suppressedNames) {
            this.survivingManagerCookies = new ArrayList<>(managerCookies);
            this.automaticAllowed = automaticAllowed;
            this.suppressedNames = new HashSet<>(suppressedNames);
        }

        private void observe(CookieSnapshot before, CookieSnapshot after) {
            if (before.present() && !after.present()) {
                automaticAllowed = false;
            }
            if (survivingManagerCookies.isEmpty()) {
                return;
            }
            List<String> beforePairs = cookiePairs(before.values());
            List<String> afterPairs = cookiePairs(after.values());
            for (String managerCookie : new HashSet<>(survivingManagerCookies)) {
                int removed = occurrences(beforePairs, managerCookie) - occurrences(afterPairs, managerCookie);
                if (removed > 0) {
                    suppressedNames.add(cookieName(managerCookie));
                    while (removed-- > 0) {
                        survivingManagerCookies.remove(managerCookie);
                    }
                }
            }
        }

        private boolean automaticAllowed() {
            return automaticAllowed;
        }

        private Set<String> suppressedNames() {
            return suppressedNames;
        }

        private List<String> survivingManagerCookies() {
            return survivingManagerCookies;
        }
    }

    private enum TerminalDispatchState {
        NOT_INVOKED,
        IN_PROGRESS,
        RETURNED,
        FAILED
    }

    private R validateAndSubmit(Object entity) {
        validateRequest();
        return doSubmit(entity);
    }

    private void validateRequest() {
        validateScheme(uri().scheme());
    }

    private static void validateScheme(String scheme) {
        if (scheme == null || !SUPPORTED_SCHEMES.contains(scheme)) {
            throw new IllegalArgumentException(
                    String.format("Not supported scheme %s, client supported schemes are: %s",
                                  scheme,
                                  String.join(", ", SUPPORTED_SCHEMES)
                    )
            );
        }
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

    private void validateQueryContentType() {
        if (Method.QUERY.equals(this.method) && !headers.contains(HeaderNames.CONTENT_TYPE)) {
            throw new IllegalArgumentException("Content-Type header is required for method '" + Method.QUERY + "'");
        }
    }

    @SuppressWarnings("unchecked")
    private T identity() {
        return (T) this;
    }

}
