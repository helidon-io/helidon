/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webclient.http3;

import java.net.UnixDomainSocketAddress;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.uri.UriAuthority;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpLogConfig;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.http3.Http3FrameListener;
import io.helidon.http.http3.Http3LoggingFrameListener;
import io.helidon.quic.QuicTLSContext;
import io.helidon.webclient.api.AltSvcHeader;
import io.helidon.webclient.api.ClientAltSvcConfig;
import io.helidon.webclient.api.ClientConnectionTarget;
import io.helidon.webclient.api.ClientRequest;
import io.helidon.webclient.api.ClientRequestOrigin;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.FullClientRequest;
import io.helidon.webclient.api.ProxyRoute;
import io.helidon.webclient.api.SniConfig;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.api.WebClientConfig;
import io.helidon.webclient.api.WebClientCookieManager;
import io.helidon.webclient.api.WebClientProtocolResponse;
import io.helidon.webclient.api.WebClientTransportObserverSupport;
import io.helidon.webclient.spi.HttpClientSpi;

/**
 * Implementation of HTTP/3 client.
 */
final class Http3ClientImpl implements Http3Client, HttpClientSpi {
    private static final HeaderName KEEP_ALIVE = HeaderNames.create("Keep-Alive");
    private static final HeaderName PROXY_CONNECTION = HeaderNames.create("Proxy-Connection");

    private final WebClient webClient;
    private final Http3ClientConfig clientConfig;
    private final Http3ClientProtocolConfig protocolConfig;
    private final Http3ConnectionCache connectionCache;
    private final Http3ConnectionCache clientCache;
    private final Http3TlsCompatibility tlsCompatibility =
            new Http3TlsCompatibility(QuicTLSContext::isQuicCompatible);
    private final Http3ExchangeClient.ClientSettings clientSettings;
    private final Http3FrameListener receiveFrameListener;
    private final Http3FrameListener sendFrameListener;
    private final ReentrantLock fallbackClientLock = new ReentrantLock();
    private final CompletableFuture<Void> closeCompletion = new CompletableFuture<>();
    private final Object observerIdentity;
    private final boolean altSvcNotificationsEnabled;
    private final boolean altSvcEnabled;
    private final boolean responseNotificationsManagedByWebClient;
    private final boolean ownsWebClient;
    private volatile WebClient fallbackClient;
    private volatile Lifecycle lifecycle = Lifecycle.OPEN;
    private Thread closingThread;

    Http3ClientImpl(WebClient webClient, Http3ClientConfig clientConfig) {
        this(webClient, clientConfig, false, false);
    }

    Http3ClientImpl(WebClient webClient, Http3ClientConfig clientConfig, boolean ownsWebClient) {
        this(webClient, clientConfig, false, ownsWebClient);
    }

    Http3ClientImpl(WebClient webClient,
                    Http3ClientConfig clientConfig,
                    boolean responseNotificationsManagedByWebClient,
                    boolean ownsWebClient) {
        Http3ConnectionCache.validateConnectionCacheSize(clientConfig.connectionCacheSize());
        this.webClient = webClient;
        this.clientConfig = clientConfig;
        this.protocolConfig = clientConfig.protocolConfig();
        this.clientSettings = Http3ExchangeClient.clientSettings(protocolConfig);
        this.observerIdentity = WebClientTransportObserverSupport.observerIdentity(webClient);
        Optional<ClientAltSvcConfig> altSvc = clientConfig.altSvc()
                .filter(ClientAltSvcConfig::enabled);
        this.altSvcNotificationsEnabled = altSvc.isPresent();
        this.altSvcEnabled = altSvc
                .map(config -> config.protocols().isEmpty()
                        || config.protocols().contains(Http3Client.PROTOCOL_ID))
                .orElse(false);
        this.responseNotificationsManagedByWebClient = responseNotificationsManagedByWebClient;
        this.ownsWebClient = ownsWebClient;
        if (clientConfig.shareConnectionCache()) {
            this.connectionCache = Http3ConnectionCache.shared();
            this.clientCache = null;
        } else {
            this.connectionCache = Http3ConnectionCache.create();
            this.clientCache = connectionCache;
        }

        HttpLogConfig log = protocolConfig.log();
        this.receiveFrameListener = log.receiveLog()
                ? Http3LoggingFrameListener.create(log, "cl-recv")
                : Http3FrameListener.create(List.of());
        this.sendFrameListener = log.sendLog()
                ? Http3LoggingFrameListener.create(log, "cl-send")
                : Http3FrameListener.create(List.of());
    }

    @Override
    public Http3ClientRequest method(Method method) {
        ensureOpen();
        ClientUri clientUri = clientConfig.baseUri()
                .map(ClientUri::create)
                .orElseGet(ClientUri::create);

        clientConfig.baseQuery().ifPresent(clientUri.writeableQuery()::from);
        clientConfig.baseFragment().ifPresent(clientUri::fragment);

        return new Http3ClientRequestImpl(this, null, method, clientUri, clientConfig.properties());
    }

    @Override
    public Http3ClientConfig prototype() {
        return clientConfig;
    }

    @Override
    public SupportLevel supports(FullClientRequest<?> clientRequest, ClientUri clientUri) {
        ensureOpen();
        if (!supportsHttp3Transport(clientRequest, clientUri)) {
            return SupportLevel.NOT_SUPPORTED;
        }
        if (!clientConfig.services().isEmpty()) {
            ProxyRoute selectedRoute = clientRequest.selectedProxyRoute().orElseGet(() -> {
                Http3Discovery.EndpointContextKey endpointKey = endpointContextKey(clientRequest,
                                                                                   clientUri,
                                                                                   clientRequest.headers());
                clientRequest.selectedProxyRoute(endpointKey.proxyRoute());
                return endpointKey.proxyRoute();
            });
            if (!selectedRoute.supportsDatagrams()) {
                return SupportLevel.NOT_SUPPORTED;
            }
            boolean shouldAttempt = protocolConfig.priorKnowledge() || allowsDirectHttp3();
            if (!shouldAttempt && altSvcEnabled) {
                Http3Discovery.EndpointContextHint hint = endpointContextHint(clientRequest,
                                                                               clientUri,
                                                                               clientRequest.headers());
                shouldAttempt = connectionCache.discovery().hasAutomaticTarget(hint);
            }
            return shouldAttempt && tlsCompatibility.compatible(clientRequest.tls())
                    ? SupportLevel.SUPPORTED
                    : SupportLevel.NOT_SUPPORTED;
        }

        Http3Discovery.EndpointContextKey endpointKey = endpointContextKey(clientRequest,
                                                                           clientUri,
                                                                           clientRequest.headers());
        clientRequest.selectedProxyRoute(endpointKey.proxyRoute());
        if (!endpointKey.proxyRoute().supportsDatagrams()) {
            return SupportLevel.NOT_SUPPORTED;
        }

        boolean shouldAttempt = protocolConfig.priorKnowledge() || allowsDirectHttp3();
        if (!shouldAttempt && altSvcEnabled) {
            shouldAttempt = connectionCache.discovery()
                    .automaticTarget(endpointKey,
                                     true,
                                     target -> connectionCache.hasSession(connectionCacheKey(endpointKey, target)))
                    .isPresent();
        }
        return shouldAttempt && tlsCompatibility.compatible(clientRequest.tls())
                ? SupportLevel.SUPPORTED
                : SupportLevel.NOT_SUPPORTED;
    }

    @Override
    public boolean isTcp() {
        return false;
    }

    @Override
    public boolean supportsServiceHandoff() {
        return true;
    }

    @Override
    public ClientRequestHeaders normalizedRequestHeaders(ClientRequestHeaders headers) {
        return Http3RequestHeaders.normalize(headers);
    }

    @Override
    public ClientRequest<?> clientRequest(FullClientRequest<?> clientRequest, ClientUri clientUri) {
        ensureOpen();
        Optional<ProxyRoute> selectedProxyRoute = clientRequest.selectedProxyRoute();
        Optional<ClientRequestOrigin> selectedProxyRouteOrigin = clientRequest.inheritedSelectedProxyRouteOrigin();
        Http3ClientRequestImpl request = new Http3ClientRequestImpl(this,
                                                                    clientRequest,
                                                                    clientRequest.method(),
                                                                    clientUri,
                                                                    clientRequest.properties());

        clientRequest.pathParams().forEach(request::pathParam);
        clientRequest.sendExpectContinue().ifPresent(request::sendExpectContinue);
        clientRequest.sni().ifPresent(request::sni);
        request.headers().clear();

        request.readTimeout(clientRequest.readTimeout())
                .readContinueTimeout(clientRequest.readContinueTimeout())
                .followRedirects(clientRequest.followRedirects())
                .maxRedirects(clientRequest.maxRedirects())
                .keepAlive(clientRequest.keepAlive())
                .skipUriEncoding(clientRequest.skipUriEncoding())
                .proxy(clientRequest.proxy())
                .tls(clientRequest.tls())
                .headers(clientRequest.headers())
                .fragment(clientUri.fragment());

        ClientRequestOrigin targetOrigin = ClientRequestOrigin.create(clientUri, normalizedRequestHeaders(request.headers()));
        clientRequest.connection().ifPresent(value -> {
            Optional<ClientRequestOrigin> inheritedOrigin = clientRequest.inheritedConnectionOrigin();
            if (inheritedOrigin.isEmpty()) {
                request.connection(value);
            } else if (inheritedOrigin.get().equals(targetOrigin)) {
                request.inheritedConnection(value, inheritedOrigin.get());
            }
        });
        clientRequest.address().ifPresent(value -> {
            Optional<ClientRequestOrigin> inheritedOrigin = clientRequest.inheritedAddressOrigin();
            if (inheritedOrigin.isEmpty()) {
                request.address(value);
            } else if (inheritedOrigin.get().equals(targetOrigin)) {
                request.inheritedAddress(value, inheritedOrigin.get());
            }
        });
        selectedProxyRoute.ifPresent(value -> {
            if (selectedProxyRouteOrigin.isEmpty()) {
                request.selectedProxyRoute(value);
            } else if (selectedProxyRouteOrigin.get().equals(targetOrigin)) {
                request.inheritedSelectedProxyRoute(value, selectedProxyRouteOrigin.get());
            }
        });

        for (String connectionField : request.headers().values(HeaderNames.CONNECTION)) {
            request.headers().remove(HeaderNames.create(connectionField));
        }
        request.headers().remove(HeaderNames.CONNECTION);
        request.headers().remove(KEEP_ALIVE);
        request.headers().remove(PROXY_CONNECTION);
        request.headers().remove(HeaderNames.TRANSFER_ENCODING);
        request.headers().remove(HeaderNames.UPGRADE);
        return request;
    }

    @Override
    public void closeResource() {
        WebClient fallback = null;
        boolean closeOwner = false;
        fallbackClientLock.lock();
        try {
            switch (lifecycle) {
            case OPEN -> {
                if (clientCache != null && clientCache.closeWouldBlockCurrentThread()) {
                    throw new IllegalStateException(
                            "HTTP/3 client must not be closed from a DNS resolver or request task "
                                    + "currently executing on behalf of the client");
                }
                lifecycle = Lifecycle.CLOSING;
                closingThread = Thread.currentThread();
                fallback = fallbackClient;
                fallbackClient = null;
                closeOwner = true;
            }
            case CLOSING -> {
                if (closingThread == Thread.currentThread()) {
                    return;
                }
            }
            case CLOSED -> {
            }
            default -> throw new IllegalStateException("Unknown HTTP/3 client lifecycle: " + lifecycle);
            }
        } finally {
            fallbackClientLock.unlock();
        }
        if (closeOwner) {
            Throwable failure = null;
            try {
                if (fallback != null) {
                    try {
                        fallback.closeResource();
                    } catch (Throwable closeFailure) {
                        failure = closeFailure;
                    }
                }
                if (clientCache != null) {
                    try {
                        clientCache.closeResource();
                    } catch (Throwable closeFailure) {
                        if (failure == null) {
                            failure = closeFailure;
                        } else if (failure != closeFailure) {
                            failure.addSuppressed(closeFailure);
                        }
                    }
                }
                try {
                    tlsCompatibility.close();
                } catch (Throwable closeFailure) {
                    if (failure == null) {
                        failure = closeFailure;
                    } else if (failure != closeFailure) {
                        failure.addSuppressed(closeFailure);
                    }
                }
                if (ownsWebClient) {
                    try {
                        webClient.closeResource();
                    } catch (Throwable closeFailure) {
                        if (failure == null) {
                            failure = closeFailure;
                        } else if (failure != closeFailure) {
                            failure.addSuppressed(closeFailure);
                        }
                    }
                }
            } finally {
                fallbackClientLock.lock();
                try {
                    lifecycle = Lifecycle.CLOSED;
                    closingThread = null;
                } finally {
                    fallbackClientLock.unlock();
                }
            }
            if (failure != null
                    && !(failure instanceof RuntimeException)
                    && !(failure instanceof Error)) {
                failure = new IllegalStateException("Failed to close HTTP/3 client resources", failure);
            }
            if (failure == null) {
                closeCompletion.complete(null);
            } else {
                closeCompletion.completeExceptionally(failure);
            }
        }

        Throwable closeFailure = Http3RequestFailureSupport.completionFailure(closeCompletion);
        if (closeFailure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (closeFailure instanceof Error error) {
            throw error;
        }
        if (closeFailure != null) {
            throw new IllegalStateException("Failed to close HTTP/3 client resources", closeFailure);
        }
    }

    @Override
    public void responseReceived(WebClientProtocolResponse response) {
        if (lifecycle != Lifecycle.OPEN
                || !altSvcEnabled
                || !response.secure()
                || response.explicitConnection()
                || response.status().code() == Status.MISDIRECTED_REQUEST_421_CODE
                || !responseProtocolSupported(response.protocolId())) {
            return;
        }

        ClientConnectionTarget responseTarget = response.target().logicalTarget();
        ConnectionKey responseConnectionKey = responseTarget.connectionKey();
        if (!sameHost(responseTarget.originAuthority().host().value(), responseConnectionKey.tlsPeerHost())
                || !responseTarget.proxyRoute().direct()
                || responseTarget.proxyRoute().addressBound()
                || responseTarget.transportAddress().isPresent()
                || responseTarget.localAddress().isPresent()
                || !responseTarget.currentTlsGeneration()
                || !ClientConnectionTarget.routeMatches(responseConnectionKey,
                                                         responseTarget.scheme(),
                                                         responseTarget.proxyRoute())
                || !tlsCompatibility.compatible(responseConnectionKey.tls())) {
            return;
        }

        var receivedAt = response.receivedAt();
        AltSvcHeader.create(response.headers(), receivedAt)
                .ifPresent(header -> connectionCache.discovery()
                        .recordAltSvc(endpointContextKey(responseTarget), header, receivedAt));
    }

    WebClient webClient() {
        return webClient;
    }

    Http3ClientConfig clientConfig() {
        return clientConfig;
    }

    Http3ClientProtocolConfig protocolConfig() {
        return protocolConfig;
    }

    Http3ConnectionCache connectionCache() {
        return connectionCache;
    }

    Http3FrameListener receiveFrameListener() {
        return receiveFrameListener;
    }

    Http3FrameListener sendFrameListener() {
        return sendFrameListener;
    }

    Http3ExchangeClient.ClientSettings clientSettings() {
        return clientSettings;
    }

    boolean isFullyClosed() {
        return lifecycle == Lifecycle.CLOSED;
    }

    WebClient fallbackClient() {
        if (lifecycle != Lifecycle.OPEN) {
            throw new IllegalStateException("HTTP/3 client is closed");
        }
        WebClient client = fallbackClient;
        if (client == null) {
            fallbackClientLock.lock();
            try {
                if (lifecycle != Lifecycle.OPEN) {
                    throw new IllegalStateException("HTTP/3 client is closed");
                }
                client = fallbackClient;
                if (client == null) {
                    List<String> protocols = fallbackProtocols();
                    if (protocols.isEmpty()) {
                        throw new IllegalStateException("HTTP/3 fallback requires at least one configured TCP protocol.");
                    }
                    client = WebClientConfig.builder(webClient.prototype())
                            .clearServices()
                            .servicesDiscoverServices(false)
                            .addService(WebClientTransportObserverSupport.borrowingService(webClient))
                            .cookieManager(WebClientCookieManager.builder().build())
                            .protocolPreference(protocols)
                            .build();
                    fallbackClient = client;
                }
            } finally {
                fallbackClientLock.unlock();
            }
        }
        return client;
    }

    Optional<Http3Discovery.Selection> requestTarget(FullClientRequest<?> clientRequest,
                                                     ClientUri clientUri,
                                                     ClientRequestHeaders headers,
                                                     boolean allowDirect) {
        ensureOpen();
        if (!supportsDirectHttp3(clientRequest, clientUri)) {
            return Optional.empty();
        }
        Http3Discovery.EndpointContextKey endpointKey = endpointContextKey(clientRequest, clientUri, headers);
        clientRequest.tlsGeneration(endpointKey.tlsGeneration());
        clientRequest.selectedProxyRoute(endpointKey.proxyRoute());
        if (!endpointKey.proxyRoute().supportsDatagrams()) {
            return Optional.empty();
        }
        return connectionCache.discovery().requestTarget(endpointKey,
                                                         clientUri,
                                                         altSvcEnabled,
                                                         allowDirect,
                                                         target -> connectionCache.hasSession(
                                                                 connectionCacheKey(endpointKey, target)));
    }

    void recordSuccess(Http3Discovery.Selection selection) {
        connectionCache.discovery().recordSuccess(selection);
    }

    void recordFailure(Http3Discovery.Selection selection) {
        connectionCache.discovery().recordFailure(selection);
    }

    boolean altSvcNotificationsEnabled() {
        return altSvcNotificationsEnabled;
    }

    boolean altSvcEnabled() {
        return altSvcEnabled;
    }

    boolean responseNotificationsManagedByWebClient() {
        return responseNotificationsManagedByWebClient;
    }

    void publishResponse(WebClientProtocolResponse response) {
        if (responseNotificationsManagedByWebClient) {
            webClient.responseReceived(response);
        } else {
            responseReceived(response);
        }
    }

    ConnectionKey connectionKey(FullClientRequest<?> clientRequest,
                                ClientUri clientUri,
                                ClientRequestHeaders headers) {
        SniConfig sni = clientRequest.sni().or(clientConfig::sni).orElse(null);
        if (sni == null) {
            return ConnectionKey.create(clientUri,
                                        clientRequest.tls(),
                                        clientConfig.dnsResolver(),
                                        clientConfig.dnsAddressLookup(),
                                        clientRequest.proxy());
        }
        return ConnectionKey.create(clientUri,
                                    sni,
                                    clientRequest.tls(),
                                    clientConfig.dnsResolver(),
                                    clientConfig.dnsAddressLookup(),
                                    clientRequest.proxy(),
                                    headers);
    }

    Http3ConnectionCache.CacheKey connectionCacheKey(Http3Discovery.EndpointContextKey endpointKey,
                                                      Http3Discovery.Target target) {
        return new Http3ConnectionCache.CacheKey(endpointKey,
                                                 target,
                                                 clientSettings.idleTimeoutMillis(),
                                                 clientSettings.localSettings(),
                                                 clientSettings.maxHeadersSize(),
                                                 clientSettings.quicConfig(),
                                                 clientSettings.logConfig(),
                                                 protocolConfig.sendErrorDetails(),
                                                 clientSettings.initialResponseTimeout(),
                                                 protocolConfig.handshakeTimeout(),
                                                 protocolConfig.streamOpenTimeout());
    }

    boolean allowsDirectHttp3() {
        return fallbackProtocols().isEmpty();
    }

    boolean supportsDirectHttp3(FullClientRequest<?> clientRequest, ClientUri clientUri) {
        return supportsHttp3Transport(clientRequest, clientUri)
                && tlsCompatibility.compatible(clientRequest.tls());
    }

    private static boolean sameHost(String first, String second) {
        return normalizeHost(first).equals(normalizeHost(second));
    }

    private static String normalizeHost(String host) {
        String normalized = Objects.requireNonNull(host, "host").trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private boolean supportsHttp3Transport(FullClientRequest<?> clientRequest, ClientUri clientUri) {
        if (clientRequest.connection().isPresent() || !"https".equalsIgnoreCase(clientUri.scheme())) {
            return false;
        }
        if (clientRequest.address().filter(UnixDomainSocketAddress.class::isInstance).isPresent()) {
            return false;
        }
        if (!clientRequest.tls().enabled()) {
            return false;
        }
        return true;
    }

    private boolean responseProtocolSupported(String protocolId) {
        return Http3Client.PROTOCOL_ID.equals(protocolId) || webClient.tcpProtocolIds().contains(protocolId);
    }

    private List<String> fallbackProtocols() {
        return webClient.tcpProtocolIds();
    }

    private Http3Discovery.EndpointContextKey endpointContextKey(FullClientRequest<?> clientRequest,
                                                                 ClientUri clientUri,
                                                                 ClientRequestHeaders headers) {
        Http3Discovery.EndpointContextHint hint = endpointContextHint(clientRequest, clientUri, headers);
        ClientConnectionTarget connectionTarget = clientRequest.selectedProxyRoute()
                .map(route -> ClientConnectionTarget.create(hint.connectionKey(),
                                                            hint.scheme(),
                                                            hint.authority(),
                                                            route,
                                                            hint.tlsGeneration()))
                .orElseGet(() -> ClientConnectionTarget.create(hint.connectionKey(),
                                                               hint.scheme(),
                                                               hint.authority()));
        return new Http3Discovery.EndpointContextKey(hint.connectionKey(),
                                                     hint.protocolConfig(),
                                                     hint.scheme(),
                                                     hint.authority(),
                                                     connectionTarget.tlsGeneration(),
                                                     hint.altSvcEnabled(),
                                                     hint.observerIdentity(),
                                                     connectionTarget.proxyRoute());
    }

    private Http3Discovery.EndpointContextKey endpointContextKey(ClientConnectionTarget connectionTarget) {
        return new Http3Discovery.EndpointContextKey(connectionTarget.connectionKey(),
                                                     protocolConfig,
                                                     connectionTarget.scheme(),
                                                     connectionTarget.originAuthority(),
                                                     connectionTarget.tlsGeneration(),
                                                     altSvcEnabled,
                                                     observerIdentity,
                                                     connectionTarget.proxyRoute());
    }

    private Http3Discovery.EndpointContextHint endpointContextHint(FullClientRequest<?> clientRequest,
                                                                   ClientUri clientUri,
                                                                   ClientRequestHeaders headers) {
        ClientRequestHeaders normalizedHeaders = normalizedRequestHeaders(headers);
        ClientRequestOrigin origin = ClientRequestOrigin.create(clientUri, normalizedHeaders);
        String scheme = origin.scheme();
        UriAuthority effectiveAuthority = origin.authority();
        ConnectionKey connectionKey = connectionKey(clientRequest, clientUri, normalizedHeaders);
        return new Http3Discovery.EndpointContextHint(connectionKey,
                                                      protocolConfig,
                                                      scheme,
                                                      effectiveAuthority,
                                                      connectionKey.tls().generation(),
                                                      altSvcEnabled,
                                                      observerIdentity);
    }

    private void ensureOpen() {
        if (lifecycle != Lifecycle.OPEN) {
            throw new IllegalStateException("HTTP/3 client is closed");
        }
    }

    private enum Lifecycle {
        OPEN,
        CLOSING,
        CLOSED
    }
}
