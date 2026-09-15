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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.UnixDomainSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import io.helidon.common.Api;
import io.helidon.common.GenericType;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LruCache;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.tls.Tls;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.ClientResponseTrailers;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.InstanceWriter;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.ReadableEntity;
import io.helidon.webclient.spi.HttpClientSpi;
import io.helidon.webclient.spi.Source;
import io.helidon.webclient.spi.SourceHandlerProvider;
import io.helidon.webclient.spi.WebClientService;

/**
 * Client request of any client that support HTTP protocol.
 * This allows configuration of a request, such as setting headers, query parameters etc.
 * Terminating methods are {@link #submit(Object)} (and its variants), and {@link #request()} (and its variants).
 */
public class HttpClientRequest extends ClientRequestBase<HttpClientRequest, HttpClientResponse> {
    private static final System.Logger LOGGER = System.getLogger(HttpClientRequest.class.getName());
    private static final Tls NO_TLS = Tls.builder().enabled(false).build();
    @SuppressWarnings("rawtypes")
    private static final List<SourceHandlerProvider> SOURCE_HANDLERS = HelidonServiceLoader.builder(
            ServiceLoader.load(SourceHandlerProvider.class)).build().asList();

    private final LruCache<LoomClient.EndpointKey, HttpClientSpi> clientSpiCache;
    private final WebClient webClient;
    private final Map<String, LoomClient.ProtocolSpi> clients;
    private final List<LoomClient.ProtocolSpi> tcpProtocols;
    private final List<String> tcpProtocolIds;
    private final List<LoomClient.ProtocolSpi> protocols;
    private final boolean serviceHandoffSupported;
    private String preferredProtocolId;
    private long tlsGeneration;
    private boolean postServiceProtocolSelection;
    private ClientConnection protocolSelectionConnection;
    private int redirectCount;
    private WebClientServiceRequest handoffServiceRequest;
    private Consumer<WebClientServiceResponse> handoffResponseConsumer;
    private CompletableFuture<WebClientServiceRequest> handoffWhenSent;
    private Consumer<String> handoffProtocolConsumer;
    private Consumer<WebClientProtocolResponse> handoffProtocolResponseConsumer;
    private boolean handoffDispatchPrepared;
    private PreparedEntityHeaders preparedEntityHeaders;

    HttpClientRequest(WebClient webClient,
                      WebClientConfig clientConfig,
                      Method method,
                      ClientUri clientUri,
                      Map<String, LoomClient.ProtocolSpi> protocolsToClients,
                      List<LoomClient.ProtocolSpi> protocols,
                      List<LoomClient.ProtocolSpi> tcpProtocols,
                      List<String> tcpProtocolIds,
                      LruCache<LoomClient.EndpointKey, HttpClientSpi> clientSpiCache) {
        this(webClient, clientConfig, method, clientUri, protocolsToClients, protocols, tcpProtocols,
             tcpProtocolIds, null, clientSpiCache);
    }

    HttpClientRequest(WebClient webClient,
                      WebClientConfig clientConfig,
                      Method method,
                      ClientUri clientUri,
                      Map<String, LoomClient.ProtocolSpi> protocolsToClients,
                      List<LoomClient.ProtocolSpi> protocols,
                      List<LoomClient.ProtocolSpi> tcpProtocols,
                      List<String> tcpProtocolIds,
                      Boolean send100Continue,
                      LruCache<LoomClient.EndpointKey, HttpClientSpi> clientSpiCache) {
        this(webClient,
             clientConfig,
             method,
             clientUri,
             protocolsToClients,
             protocols,
             tcpProtocols,
             tcpProtocolIds,
             send100Continue,
             clientSpiCache,
             clientConfig.properties(),
             RedirectSecurityState.initial());
    }

    private HttpClientRequest(WebClient webClient,
                              WebClientConfig clientConfig,
                              Method method,
                              ClientUri clientUri,
                              Map<String, LoomClient.ProtocolSpi> protocolsToClients,
                              List<LoomClient.ProtocolSpi> protocols,
                              List<LoomClient.ProtocolSpi> tcpProtocols,
                              List<String> tcpProtocolIds,
                              Boolean send100Continue,
                              LruCache<LoomClient.EndpointKey, HttpClientSpi> clientSpiCache,
                              Map<String, String> properties,
                              RedirectSecurityState redirectSecurityState) {
        super(clientConfig, webClient.cookieManager(), "any", method, clientUri,
              send100Continue, properties);
        redirectSecurityState(redirectSecurityState);
        this.webClient = webClient;
        this.clients = protocolsToClients;
        this.protocols = protocols;
        this.tcpProtocols = tcpProtocols;
        this.tcpProtocolIds = tcpProtocolIds;
        this.clientSpiCache = clientSpiCache;
        this.serviceHandoffSupported = !protocols.isEmpty() && protocols.stream()
                .allMatch(protocol -> protocol.spi().supportsServiceHandoff());
    }

    /**
     * Use an explicit version of HTTP by defining its ALPN protocol ID.
     * Constants are defined on each version specific client.
     * For example to use HTTP/2, {@code h2} is the protocol ID. For TLS, we will attempt only {@code h2} negotiation,
     * for plaintext requests, we will either attempt an upgrade from HTTP/1.1, or use prior knowledge according to HTTP/2
     * protocol configuration. This method is a no-op when called on a version specific HTTP client. A selected protocol
     * may still fall back when the request has not been processed and its protocol configuration does not require prior
     * knowledge.
     *
     * @param protocol HTTP protocol ID to use
     * @return updated request
     */
    public HttpClientRequest protocolId(String protocol) {
        this.preferredProtocolId = protocol;
        return this;
    }

    @Override
    @Api.Internal
    public Optional<String> requestedProtocolId() {
        return Optional.ofNullable(preferredProtocolId);
    }

    @Override
    @Api.Internal
    public long tlsGeneration() {
        return tlsGeneration;
    }

    @Override
    @Api.Internal
    public void tlsGeneration(long tlsGeneration) {
        this.tlsGeneration = tlsGeneration;
    }

    @Override
    @Api.Internal
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

    @Override
    @Api.Internal
    public boolean serviceRequestAfterServices(WebClientServiceRequest serviceRequest,
                                               Consumer<WebClientServiceResponse> responseConsumer,
                                               CompletableFuture<WebClientServiceRequest> whenSent,
                                               Consumer<String> protocolConsumer,
                                               boolean dispatchPrepared) {
        if (!super.serviceRequestAfterServices(serviceRequest,
                                               responseConsumer,
                                               whenSent,
                                               protocolConsumer,
                                               dispatchPrepared)) {
            return false;
        }
        handoffServiceRequest = serviceRequest;
        handoffResponseConsumer = responseConsumer;
        handoffWhenSent = whenSent;
        handoffProtocolConsumer = protocolConsumer;
        handoffProtocolResponseConsumer = null;
        handoffDispatchPrepared = dispatchPrepared;
        return true;
    }

    @Override
    @Api.Internal
    public boolean serviceRequestAfterServices(WebClientServiceRequest serviceRequest,
                                               Consumer<WebClientServiceResponse> responseConsumer,
                                               CompletableFuture<WebClientServiceRequest> whenSent,
                                               Consumer<String> protocolConsumer,
                                               boolean dispatchPrepared,
                                               Consumer<WebClientProtocolResponse> protocolResponseConsumer) {
        if (!super.serviceRequestAfterServices(serviceRequest,
                                               responseConsumer,
                                               whenSent,
                                               protocolConsumer,
                                               dispatchPrepared,
                                               protocolResponseConsumer)) {
            return false;
        }
        handoffServiceRequest = serviceRequest;
        handoffResponseConsumer = responseConsumer;
        handoffWhenSent = whenSent;
        handoffProtocolConsumer = protocolConsumer;
        handoffProtocolResponseConsumer = protocolResponseConsumer;
        handoffDispatchPrepared = dispatchPrepared;
        return true;
    }

    @Override
    protected HttpClientResponse doSubmit(Object entity) {
        return invokeBody(entity instanceof RequestBody body ? body : RequestBody.create(entity));
    }

    @Override
    protected HttpClientResponse doOutputStream(OutputStreamHandler outputStreamConsumer) {
        return invokeBody(RequestBody.create(outputStreamConsumer, headers().contentLength().orElse(-1)));
    }

    @Override
    protected ClientRequestHeaders normalizedRequestHeaders(ClientRequestHeaders requestHeaders) {
        ClientRequestHeaders normalized = requestHeaders;
        for (LoomClient.ProtocolSpi protocol : protocols) {
            normalized = protocol.spi().normalizedRequestHeaders(normalized);
        }
        return normalized;
    }

    private HttpClientResponse invokeBody(RequestBody body) {
        if (preparedEntityHeaders != null && preparedEntityHeaders.body() != body) {
            preparedEntityHeaders = null;
        }
        tlsGeneration = tls().generation();
        RedirectSecurityState invocationSecurityState = redirectSecurityState();
        if (body.contentLength >= 0) {
            headers().contentLength(body.contentLength);
        }
        ClientUri resolvedUri = resolvedUri();
        Optional<Header> originalHost = headers().contains(HeaderNames.HOST)
                ? Optional.of(headers().get(HeaderNames.HOST))
                : Optional.empty();
        try {
            if (servicesPending()) {
                HttpClientRequest attempt = copyForUri(method(),
                                                       resolvedUri,
                                                       true,
                                                       true,
                                                       redirectSecurityState());
                attempt.tlsGeneration = tlsGeneration;
                HttpClientResponse response = attempt.invokeAfterServices(resolvedUri, body);
                HttpClientResponse result = attempt.followRedirect(response, body);
                tlsGeneration = attempt.tlsGeneration;
                clearSelectedProxyRoute();
                retainLastSelectedProxyRoute(attempt);
                return result;
            }
            if (clientConfig().services().isEmpty()) {
                body.prepare(this, Contexts.context().orElseGet(Context::create));
            }
            ClientRequest<?> implementation = discoverHttpImplementation(resolvedUri);
            if (implementation == this) {
                HttpClientRequest attempt = copyForUri(method(),
                                                       resolvedUri,
                                                       true,
                                                       true,
                                                       redirectSecurityState());
                attempt.tlsGeneration = tlsGeneration;
                HttpClientResponse response = attempt.invokeAfterServices(resolvedUri, body);
                HttpClientResponse result = attempt.followRedirect(response, body);
                tlsGeneration = attempt.tlsGeneration;
                clearSelectedProxyRoute();
                retainLastSelectedProxyRoute(attempt);
                return result;
            }
            if (handoffServiceRequest != null && !handoffAfterServices(implementation,
                                                                      handoffServiceRequest,
                                                                      handoffResponseConsumer,
                                                                      handoffWhenSent,
                                                                      handoffProtocolConsumer,
                                                                      handoffDispatchPrepared)) {
                if (implementation instanceof FullClientRequest<?> fullRequest) {
                    fullRequest.connection().ifPresent(ClientConnection::closeResource);
                }
                throw new IllegalStateException("Selected protocol request does not support post-service dispatch: "
                                                        + implementation.getClass().getName());
            }
            implementation.followRedirects(false);
            HttpClientResponse response = body.submit(implementation);
            if (implementation instanceof FullClientRequest<?> fullRequest) {
                redirectSecurityState(fullRequest.redirectSecurityState());
            }
            return followRedirect(response, body);
        } finally {
            body.cancelIfUnattached();
            preparedEntityHeaders = null;
            redirectSecurityState(invocationSecurityState);
            restoreHost(originalHost);
        }
    }

    private HttpClientResponse followRedirect(HttpClientResponse response, RequestBody body) {
        if (!followRedirects()
                || response.status().code() == Status.NOT_MODIFIED_304.code()
                || response.status().family() != Status.Family.REDIRECTION) {
            return response;
        }

        Status status = response.status();
        ClientUri sourceUri = response.lastEndpointUri();
        String location;
        try (response) {
            if (!response.headers().contains(HeaderNames.LOCATION)) {
                throw new IllegalStateException("There is no " + HeaderNames.LOCATION
                                                        + " header present in the response. It is not clear where to redirect.");
            }
            location = response.headers().get(HeaderNames.LOCATION).get();
        }

        if (redirectCount >= maxRedirects()) {
            throw new IllegalStateException("Maximum number of request redirections ("
                                                    + maxRedirects() + ") reached.");
        }

        int statusCode = status.code();
        boolean preserveMethod = statusCode == Status.TEMPORARY_REDIRECT_307.code()
                || statusCode == Status.PERMANENT_REDIRECT_308.code()
                || (Method.QUERY.equals(method())
                        && (statusCode == Status.MOVED_PERMANENTLY_301.code()
                        || statusCode == Status.FOUND_302.code()));
        ClientUri redirectUri = resolveRedirectUri(sourceUri, location);
        if (preserveMethod && !body.canStartAttempt()) {
            throw new IllegalStateException("Cannot replay a one-shot request body after redirect status "
                                                    + status.code() + ".");
        }
        if (preserveMethod && !body.prepared()) {
            if (!(response instanceof ServicedHttpClientResponse servicedResponse)) {
                throw new IllegalStateException("Cannot prepare an unconsumed request body after redirect status "
                                                        + status.code() + ".");
            }
            body.prepare(mediaContext(),
                         clientConfig().maxInMemoryEntity(),
                         clientConfig().writeBufferSize(),
                         ClientRequestHeaders.create((Headers) servicedResponse.requestHeaders),
                         servicedResponse.serviceResponse.serviceRequest().context());
        }

        boolean sameOrigin = sameOrigin(sourceUri, redirectUri);
        boolean replayingEntity = preserveMethod && !body.isEmpty();
        HttpClientRequest redirectRequest = copyForUri(preserveMethod ? method() : Method.GET,
                                                       redirectUri,
                                                       sameOrigin,
                                                       false,
                                                       redirectSecurityState().forRedirect(replayingEntity));
        if (redirectRequest.preparedEntityHeaders != null
                && redirectRequest.preparedEntityHeaders.body() == body) {
            redirectRequest.preparedEntityHeaders.application().rollback(redirectRequest.headers());
            redirectRequest.preparedEntityHeaders = null;
        }
        if (!preserveMethod) {
            redirectRequest.headers().remove(HeaderNames.CONTENT_TYPE);
            redirectRequest.headers().remove(HeaderNames.CONTENT_ENCODING);
            redirectRequest.headers().remove(HeaderNames.CONTENT_LANGUAGE);
            redirectRequest.headers().remove(HeaderNames.CONTENT_LOCATION);
        }
        redirectRequest.skipUriEncoding(false);
        redirectRequest.redirectCount = redirectCount + 1;
        redirectRequest.headers().remove(HeaderNames.CONTENT_LENGTH);
        redirectRequest.headers().remove(HeaderNames.TRANSFER_ENCODING);
        redirectRequest.headers().remove(HeaderNames.EXPECT);
        if (!sameOrigin) {
            ClientRequestHeaders redirectHeaders = normalizedRequestHeaders(redirectRequest.headers());
            if (redirectHeaders != redirectRequest.headers()) {
                redirectRequest.headers().clear();
                redirectRequest.headers(redirectHeaders);
            }
            redirectRequest.headers().remove(HeaderNames.HOST);
            if (clientConfig().filterRedirectHeaders()) {
                clientConfig().redirectSensitiveHeaders().forEach(redirectRequest.headers()::remove);
            }
        }

        HttpClientResponse result;
        if (preserveMethod && method() != Method.HEAD) {
            result = redirectRequest.submit(body);
        } else {
            result = redirectRequest.request();
        }
        tlsGeneration = redirectRequest.tlsGeneration;
        clearSelectedProxyRoute();
        retainLastSelectedProxyRoute(redirectRequest);
        return result;
    }

    private HttpClientRequest copyForUri(Method method,
                                         ClientUri uri,
                                         boolean retainConnectionOverrides,
                                         boolean retainSelectedRoute,
                                         RedirectSecurityState redirectSecurityState) {
        HttpClientRequest copy = new HttpClientRequest(webClient,
                                                       webClient.prototype(),
                                                       method,
                                                       uri,
                                                       clients,
                                                       protocols,
                                                       tcpProtocols,
                                                       tcpProtocolIds,
                                                       sendExpectContinue().orElse(null),
                                                       clientSpiCache,
                                                       properties(),
                                                       redirectSecurityState);
        copy.preferredProtocolId = preferredProtocolId;
        copy.redirectCount = redirectCount;
        copy.tlsGeneration = tlsGeneration;
        copy.preparedEntityHeaders = preparedEntityHeaders;
        pathParams().forEach(copy::pathParam);
        copy.headers().clear();
        copy.headers(headers());
        copy.proxy(proxy());
        ClientRequestOrigin targetOrigin = ClientRequestOrigin.create(uri, normalizedRequestHeaders(copy.headers()));
        if (retainConnectionOverrides) {
            connection().ifPresent(value -> {
                Optional<ClientRequestOrigin> inheritedOrigin = inheritedConnectionOrigin();
                if (inheritedOrigin.isEmpty()) {
                    copy.connection(value);
                } else if (inheritedOrigin.get().equals(targetOrigin)) {
                    copy.inheritedConnection(value, inheritedOrigin.get());
                }
            });
            address().ifPresent(value -> {
                Optional<ClientRequestOrigin> inheritedOrigin = inheritedAddressOrigin();
                if (inheritedOrigin.isEmpty()) {
                    copy.address(value);
                } else if (inheritedOrigin.get().equals(targetOrigin)) {
                    copy.inheritedAddress(value, inheritedOrigin.get());
                }
            });
        }
        if (retainSelectedRoute) {
            selectedProxyRoute().ifPresent(value -> {
                Optional<ClientRequestOrigin> inheritedOrigin = inheritedSelectedProxyRouteOrigin();
                if (inheritedOrigin.isEmpty()) {
                    copy.selectedProxyRoute(value);
                } else if (inheritedOrigin.get().equals(targetOrigin)) {
                    copy.inheritedSelectedProxyRoute(value, inheritedOrigin.get());
                }
            });
        }
        sni().ifPresent(copy::sni);
        copy.readTimeout(readTimeout())
                .readContinueTimeout(readContinueTimeout())
                .followRedirects(followRedirects())
                .maxRedirects(maxRedirects())
                .keepAlive(keepAlive())
                .skipUriEncoding(skipUriEncoding())
                .tls(tls());
        if (handoffServiceRequest != null) {
            handoffAfterServices(copy,
                                 handoffServiceRequest,
                                 handoffResponseConsumer,
                                 handoffWhenSent,
                                 handoffProtocolConsumer,
                                 handoffDispatchPrepared);
        }
        return copy;
    }

    private ClientRequest<?> discoverHttpImplementation(ClientUri resolvedUri) {
        ClientRequestHeaders normalizedHeaders = normalizedRequestHeaders(headers());
        ClientRequestHeaderSupport.validate(normalizedHeaders);

        LoomClient.ProtocolSpi preferredProtocol = null;
        if (preferredProtocolId != null) {
            preferredProtocol = clients.get(preferredProtocolId);
            if (preferredProtocol == null) {
                throw new IllegalArgumentException("Requested protocol with id \"" + preferredProtocolId + "\", which is not "
                                                           + "available on classpath. Available protocols: " + clients.keySet());
            }
        }

        Optional<ClientConnection> explicitConnection = connection();
        if (explicitConnection.isPresent()) {
            ClientConnection connection = explicitConnection.get();
            clearSelectedProxyRoute();
            if (servicesPending()) {
                return explicitTcpRequest(connection, preferredProtocol, resolvedUri);
            }
            Optional<ResolvedClientTarget> resolvedTarget = connection instanceof TcpClientConnection tcpConnection
                    ? tcpConnection.resolvedTarget()
                    : Optional.empty();
            if (resolvedTarget.isEmpty()) {
                return explicitTcpRequest(connection, preferredProtocol, resolvedUri);
            }
            HttpClientConfig config = clientConfig();
            Tls currentTls = effectiveTls(resolvedUri, tls());
            ConnectionKey currentKey = connectionKey(resolvedUri,
                                                     effectiveSni(config),
                                                     currentTls,
                                                     config,
                                                     proxy(),
                                                     normalizedHeaders);
            ProxyRoute route = resolvedTarget.get().proxyRoute();
            if (!ClientConnectionTarget.routeMatches(currentKey, resolvedUri.scheme(), route)) {
                return explicitTcpRequest(connection, preferredProtocol, resolvedUri);
            }
            if (!servicesPending()) {
                ClientConnectionTarget.matchingRoute(connection, currentKey, resolvedUri, normalizedHeaders)
                        .ifPresent(this::selectedProxyRoute);
            }
            return explicitTcpRequest(connection, preferredProtocol, resolvedUri);
        }

        if (preferredProtocol != null) {
            return preferredProtocol.spi().clientRequest(this, resolvedUri);
        }

        if (servicesPending()) {
            return this;
        }

        HttpClientConfig clientConfig = clientConfig();
        Tls effectiveTls = effectiveTls(resolvedUri, tls());
        SniConfig effectiveSni = effectiveSni(clientConfig);
        if (requiresFinalHeaders(resolvedUri, effectiveSni, effectiveTls)) {
            return firstTcpProtocol(resolvedUri);
        }

        LoomClient.EndpointKey endpointKey = endpointKey(resolvedUri);
        Optional<HttpClientSpi> spi = clientSpiCache.get(endpointKey);
        if (spi.isPresent()) {
            HttpClientSpi cached = spi.get();
            if (cached.isTcp()) {
                for (LoomClient.ProtocolSpi protocol : protocols) {
                    HttpClientSpi candidate = protocol.spi();
                    if (candidate == cached) {
                        break;
                    }
                    if (candidate.supports(this, resolvedUri) == HttpClientSpi.SupportLevel.SUPPORTED) {
                        clientSpiCache.put(endpointKey, candidate);
                        return candidate.clientRequest(this, resolvedUri);
                    }
                }
            }
            HttpClientSpi.SupportLevel support = cached.supports(this, resolvedUri);
            if (support == HttpClientSpi.SupportLevel.SUPPORTED
                    || support == HttpClientSpi.SupportLevel.COMPATIBLE) {
                return cached.clientRequest(this, resolvedUri);
            }
            clientSpiCache.remove(endpointKey);
        }
        // now use the first protocol that supports the request without condition, store first compatible
        HttpClientSpi compatible = null;
        HttpClientSpi unknown = null;
        for (LoomClient.ProtocolSpi protocol : protocols) {
            // must iterate through list, to maintain weighted ordering

            HttpClientSpi client = protocol.spi();
            HttpClientSpi.SupportLevel supports = client.supports(this, resolvedUri);
            if (supports == HttpClientSpi.SupportLevel.SUPPORTED) {
                clientSpiCache.put(endpointKey, client);
                return client.clientRequest(this, resolvedUri);
            }
            if (supports == HttpClientSpi.SupportLevel.COMPATIBLE && compatible == null) {
                compatible = client;
            }
            if (supports == HttpClientSpi.SupportLevel.UNKNOWN && unknown == null) {
                unknown = client;
            }
        }
        Optional<ClientRequest<?>> negotiatedRequest = negotiateTlsProtocol(resolvedUri,
                                                                             effectiveSni,
                                                                             effectiveTls,
                                                                             clientConfig,
                                                                             endpointKey);
        if (negotiatedRequest.isPresent()) {
            return negotiatedRequest.get();
        }

        if (compatible != null) {
            clientSpiCache.put(endpointKey, compatible);
            return compatible.clientRequest(this, resolvedUri);
        }

        if (unknown != null) {
            // do not cache
            return unknown.clientRequest(this, resolvedUri);
        }

        throw new IllegalArgumentException("Cannot handle request to " + resolvedUri + ", did not discover any HTTP version "
                                                   + "willing to handle it. HTTP versions supported: " + clients.keySet());
    }

    private Optional<ClientRequest<?>> negotiateTlsProtocol(ClientUri resolvedUri,
                                                             SniConfig effectiveSni,
                                                             Tls effectiveTls,
                                                             HttpClientConfig clientConfig,
                                                             LoomClient.EndpointKey endpointKey) {
        if (!"https".equals(resolvedUri.scheme()) || !effectiveTls.enabled() || tcpProtocols.isEmpty()) {
            return Optional.empty();
        }

        // we may use UNIX domain socket here
        UnixDomainSocketAddress unixSocketAddress = null;
        if (address().isPresent()) {
            var address = address().get();
            if (address instanceof UnixDomainSocketAddress udsa) {
                unixSocketAddress = udsa;
            }
        }
        ClientConnection connection;

        if (unixSocketAddress == null) {
            // use ALPN
            ClientConnectionTarget connectionTarget = connectionTarget(resolvedUri,
                                                                        effectiveSni,
                                                                        effectiveTls,
                                                                        clientConfig);

            // this is a temporary connection, used to determine which protocol is supported, next
            // call to the same remote location will be obtained from cache
            connection = TcpClientConnection.create(webClient,
                                                    connectionTarget,
                                                    tcpProtocolIds,
                                                    conn -> false,
                                                    conn -> {
                                                    });
        } else {
            ClientRequestHeaders normalizedHeaders = normalizedRequestHeaders(headers());
            ConnectionKey connectionKey = unixConnectionKey(resolvedUri,
                                                            effectiveSni,
                                                            effectiveTls,
                                                            clientConfig,
                                                            unixSocketAddress,
                                                            normalizedHeaders);
            ClientConnectionTarget connectionTarget = ClientConnectionTarget.createUnixDomainSocket(connectionKey,
                                                                                                       resolvedUri,
                                                                                                       normalizedHeaders,
                                                                                                       unixSocketAddress);
            connection = UnixDomainSocketClientConnection.create(webClient,
                                                                 connectionTarget,
                                                                 tcpProtocolIds,
                                                                 conn -> false,
                                                                 conn -> {
                                                                 });
        }
        if (postServiceProtocolSelection) {
            protocolSelectionConnection = connection;
        }
        connection.connect();
        HelidonSocket socket = connection.helidonSocket();
        if (socket.protocolNegotiated()) {
            String negotiatedProtocol = socket.protocol();
            LoomClient.ProtocolSpi protocolSpi = clients.get(negotiatedProtocol);
            if (protocolSpi != null) {
                clientSpiCache.put(endpointKey, protocolSpi.spi());
                ClientRequest<?> clientRequest = protocolSpi.spi().clientRequest(this, resolvedUri);
                clientRequest.connection(connection);
                return Optional.of(clientRequest);
            }
            if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                LOGGER.log(System.Logger.Level.TRACE, "Attempted to negotiate a protocol (" + tcpProtocolIds + "), "
                        + "but got an unsupported protocol back: " + negotiatedProtocol);
            }
        } else if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            LOGGER.log(System.Logger.Level.TRACE, "Attempted to negotiate a protocol (" + tcpProtocolIds + "), "
                    + "but did not get a negotiated protocol back, ignoring.");
        }
        connection.closeResource();
        protocolSelectionConnection = null;
        return Optional.empty();
    }

    private HttpClientResponse invokeAfterServices(ClientUri resolvedUri, RequestBody body) {
        boolean prepareBodyAfterServices = !body.prepared();
        if (!prepareBodyAfterServices
                && (preparedEntityHeaders == null || preparedEntityHeaders.body() != body)) {
            body.prepare(this, Contexts.context().orElseGet(Context::create));
        }
        CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        AtomicReference<HttpClientResponse> transportResponse = new AtomicReference<>();
        AtomicReference<WebClientServiceResponse> rawTransportResponse = new AtomicReference<>();
        AtomicReference<HttpClientRequest> finalizedRequest = new AtomicReference<>();
        AtomicReference<String> actualProtocol =
                new AtomicReference<>(preferredProtocolId == null ? "any" : preferredProtocolId);

        WebClientService.WireProtocolChain terminalChain = new WebClientService.WireProtocolChain() {
            @Override
            public WebClientServiceResponse proceed(WebClientServiceRequest serviceRequest) {
                HttpClientRequest finalized = new HttpClientRequest(webClient,
                                                                    webClient.prototype(),
                                                                    serviceRequest.method(),
                                                                    serviceRequest.uri(),
                                                                    clients,
                                                                    protocols,
                                                                    tcpProtocols,
                                                                    tcpProtocolIds,
                                                                    sendExpectContinue().orElse(null),
                                                                    clientSpiCache,
                                                                    serviceRequest.properties(),
                                                                    redirectSecurityState());
                finalized.postServiceProtocolSelection = true;
                finalized.preferredProtocolId = preferredProtocolId;
                finalized.tlsGeneration = tlsGeneration;
                pathParams().forEach(finalized::pathParam);
                sni().ifPresent(finalized::sni);
                finalized.readTimeout(readTimeout())
                        .readContinueTimeout(readContinueTimeout())
                        .followRedirects(followRedirects())
                        .maxRedirects(maxRedirects())
                        .keepAlive(keepAlive())
                        .skipUriEncoding(skipUriEncoding())
                        .proxy(proxy())
                        .tls(tls());
                finalized.headers().clear();
                finalized.headers(serviceRequest.headers());
                ClientRequestOrigin finalOrigin = ClientRequestOrigin.create(serviceRequest.uri(),
                                                                              serviceRequest.headers());
                connection().ifPresent(value -> {
                    Optional<ClientRequestOrigin> inheritedOrigin = inheritedConnectionOrigin();
                    if (inheritedOrigin.isEmpty()) {
                        finalized.connection(value);
                    } else if (inheritedOrigin.get().equals(finalOrigin)) {
                        finalized.inheritedConnection(value, inheritedOrigin.get());
                    }
                });
                address().ifPresent(value -> {
                    Optional<ClientRequestOrigin> inheritedOrigin = inheritedAddressOrigin();
                    if (inheritedOrigin.isEmpty()) {
                        finalized.address(value);
                    } else if (inheritedOrigin.get().equals(finalOrigin)) {
                        finalized.inheritedAddress(value, inheritedOrigin.get());
                    }
                });
                selectedProxyRoute().ifPresent(value -> {
                    Optional<ClientRequestOrigin> inheritedOrigin = inheritedSelectedProxyRouteOrigin();
                    if (inheritedOrigin.isEmpty()) {
                        finalized.selectedProxyRoute(value);
                    } else if (inheritedOrigin.get().equals(finalOrigin)) {
                        finalized.inheritedSelectedProxyRoute(value, inheritedOrigin.get());
                    }
                });
                finalizedRequest.set(finalized);

                ClientRequest<?> transportRequest = finalized.discoverHttpImplementation(serviceRequest.uri());
                transportRequest.followRedirects(false);
                if (!handoffAfterServices(transportRequest,
                                          serviceRequest,
                                          rawTransportResponse::set,
                                          whenSent,
                                          actualProtocol::set,
                                          true)) {
                    if (transportRequest instanceof FullClientRequest<?> fullRequest) {
                        fullRequest.connection().ifPresent(ClientConnection::closeResource);
                    }
                    throw new IllegalStateException("Selected protocol request does not support post-service dispatch: "
                                                            + transportRequest.getClass().getName());
                }

                HttpClientResponse response = body.submit(transportRequest);
                if (transportRequest instanceof FullClientRequest<?> fullRequest) {
                    finalized.tlsGeneration = fullRequest.tlsGeneration();
                }
                WebClientServiceResponse raw = rawTransportResponse.get();
                if (raw == null) {
                    response.close();
                    throw new IllegalStateException("Selected protocol did not expose its post-service response");
                }
                WebClientServiceRequest rawRequest = raw.serviceRequest();
                redirectSecurityState(redirectSecurityState().finalized(response.lastEndpointUri(),
                                                                         normalizedRequestHeaders(rawRequest.headers())));
                actualProtocol.set(response.protocolId());
                transportResponse.set(response);
                finalized.protocolSelectionConnection = null;
                return raw;
            }

            @Override
            public String protocolId() {
                return actualProtocol.get();
            }
        };

        WebClientServiceResponse serviceResponse;
        try {
            serviceResponse = invokeServices(terminalChain,
                                             whenSent,
                                             whenComplete,
                                             resolvedUri,
                                             request -> prepareBodyAfterServices(prepareBodyAfterServices, body, request));
        } catch (RuntimeException | Error e) {
            whenSent.completeExceptionally(e);
            whenComplete.completeExceptionally(e);
            closeFailedDispatch(transportResponse.get(), finalizedRequest.get());
            throw e;
        }

        HttpClientResponse response = transportResponse.get();
        HttpClientRequest finalized = finalizedRequest.get();
        HttpClientResponse result = new ServicedHttpClientResponse(response,
                                                                    rawTransportResponse.get(),
                                                                    serviceResponse,
                                                                    finalizedRequestHeaders(),
                                                                    finalizedEndpointUri(),
                                                                    mediaContext(),
                                                                    clientConfig().maxInMemoryEntity(),
                                                                    readTimeout(),
                                                                    whenComplete);
        clearSelectedProxyRoute();
        if (response != null && finalized != null) {
            tlsGeneration = finalized.tlsGeneration;
            retainLastSelectedProxyRoute(finalized);
        }
        return result;
    }

    private void prepareBodyAfterServices(boolean prepareBody, RequestBody body, WebClientServiceRequest request) {
        if (!prepareBody) {
            return;
        }
        body.prepare(mediaContext(),
                     clientConfig().maxInMemoryEntity(),
                     clientConfig().writeBufferSize(),
                     request.headers(),
                     request.context());
    }

    private void retainLastSelectedProxyRoute(FullClientRequest<?> source) {
        source.lastSelectedProxyRoute().ifPresent(route -> source.inheritedLastSelectedProxyRouteOrigin()
                .ifPresentOrElse(origin -> inheritedSelectedProxyRoute(route, origin),
                                 () -> selectedProxyRoute(route)));
    }

    private ClientRequest<?> explicitTcpRequest(ClientConnection connection,
                                                LoomClient.ProtocolSpi preferredProtocol,
                                                ClientUri resolvedUri) {
        if (preferredProtocol != null && preferredProtocol.spi().isTcp()) {
            return preferredProtocol.spi().clientRequest(this, resolvedUri);
        }
        if (connection.helidonSocket().protocolNegotiated()) {
            LoomClient.ProtocolSpi negotiated = clients.get(connection.helidonSocket().protocol());
            if (negotiated != null && negotiated.spi().isTcp()) {
                return negotiated.spi().clientRequest(this, resolvedUri);
            }
            throw new IllegalArgumentException("Explicit connection negotiated unsupported TCP protocol: "
                                                       + connection.helidonSocket().protocol());
        }
        LoomClient.ProtocolSpi http1 = clients.get("http/1.1");
        return http1 != null && http1.spi().isTcp()
                ? http1.spi().clientRequest(this, resolvedUri)
                : firstTcpProtocol(resolvedUri);
    }

    private boolean servicesPending() {
        return serviceHandoffSupported
                && !postServiceProtocolSelection
                && !clientConfig().services().isEmpty();
    }

    private boolean handoffAfterServices(ClientRequest<?> request,
                                         WebClientServiceRequest serviceRequest,
                                         Consumer<WebClientServiceResponse> responseConsumer,
                                         CompletableFuture<WebClientServiceRequest> whenSent,
                                         Consumer<String> protocolConsumer,
                                         boolean dispatchPrepared) {
        Consumer<WebClientProtocolResponse> protocolResponseConsumer = handoffProtocolResponseConsumer;
        if (protocolResponseConsumer == null) {
            return request.serviceRequestAfterServices(serviceRequest,
                                                       responseConsumer,
                                                       whenSent,
                                                       protocolConsumer,
                                                       dispatchPrepared);
        }
        return request.serviceRequestAfterServices(serviceRequest,
                                                   responseConsumer,
                                                   whenSent,
                                                   protocolConsumer,
                                                   dispatchPrepared,
                                                   protocolResponseConsumer);
    }

    private void restoreHost(Optional<Header> originalHost) {
        headers().remove(HeaderNames.HOST);
        originalHost.ifPresent(headers()::set);
    }

    private LoomClient.EndpointKey endpointKey(ClientUri resolvedUri) {
        String transportKey = transportKey();
        HttpClientConfig clientConfig = clientConfig();
        Tls effectiveTls = effectiveTls(resolvedUri, tls());
        SniConfig effectiveSni = effectiveSni(clientConfig);
        ClientRequestHeaders normalizedHeaders = normalizedRequestHeaders(headers());
        SniSupport.Selection sni = sniSelection(resolvedUri, effectiveSni, effectiveTls, normalizedHeaders);
        ClientRequestOrigin origin = ClientRequestOrigin.create(resolvedUri, normalizedHeaders);
        tlsGeneration = effectiveTls.generation();
        return new LoomClient.EndpointKey(origin.scheme(),
                                          origin.authority().toString(),
                                          transportKey,
                                          effectiveTls,
                                          sni.state(),
                                          transportKey == null ? proxy() : Proxy.noProxy());
    }

    private ClientConnectionTarget connectionTarget(ClientUri resolvedUri,
                                                    SniConfig effectiveSni,
                                                    Tls effectiveTls,
                                                    HttpClientConfig clientConfig) {
        ClientRequestHeaders normalizedHeaders = normalizedRequestHeaders(headers());
        ConnectionKey connectionKey = connectionKey(resolvedUri,
                                                    effectiveSni,
                                                    effectiveTls,
                                                    clientConfig,
                                                    proxy(),
                                                    normalizedHeaders);
        Optional<ProxyRoute> selectedRoute = selectedProxyRoute();
        Optional<ProxyRoute> matchingRoute = selectedRoute
                .filter(route -> ClientConnectionTarget.routeMatches(connectionKey, resolvedUri.scheme(), route));
        if (selectedRoute.isPresent() && matchingRoute.isEmpty()) {
            clearSelectedProxyRoute();
        }
        ClientConnectionTarget target = matchingRoute
                .map(route -> ClientConnectionTarget.create(connectionKey, resolvedUri, normalizedHeaders, route))
                .orElseGet(() -> ClientConnectionTarget.create(connectionKey, resolvedUri, normalizedHeaders));
        selectedProxyRoute(target.proxyRoute());
        return target;
    }

    private String transportKey() {
        return address()
                .filter(UnixDomainSocketAddress.class::isInstance)
                .map(UnixDomainSocketAddress.class::cast)
                .map(address -> "unix:" + address.getPath())
                .orElse(null);
    }

    private SniConfig effectiveSni(HttpClientConfig clientConfig) {
        return sni().or(clientConfig::sni).orElse(null);
    }

    private Tls effectiveTls(ClientUri uri, Tls tls) {
        return "https".equals(uri.scheme()) ? tls : NO_TLS;
    }

    private boolean requiresFinalHeaders(ClientUri uri, SniConfig sni, Tls tls) {
        return "https".equals(uri.scheme())
                && tls.enabled()
                && sni != null
                && sni.mode() == SniMode.HOST_HEADER;
    }

    private ClientRequest<?> firstTcpProtocol(ClientUri resolvedUri) {
        if (tcpProtocols.isEmpty()) {
            throw new IllegalArgumentException("Cannot handle request to " + resolvedUri + ", did not discover any HTTP version "
                                                       + "willing to handle it. HTTP versions supported: " + clients.keySet());
        }
        return tcpProtocols.getFirst().spi().clientRequest(this, resolvedUri);
    }

    private static void closeFailedDispatch(HttpClientResponse response, HttpClientRequest request) {
        if (response != null) {
            response.close();
        }
        if (request != null && request.protocolSelectionConnection != null) {
            request.protocolSelectionConnection.closeResource();
            request.protocolSelectionConnection = null;
        }
    }

    private static SniSupport.Selection sniSelection(ClientUri uri,
                                                     SniConfig sni,
                                                     Tls tls,
                                                     ClientRequestHeaders headers) {
        return sni == null ? SniSupport.tlsDefault(uri, tls) : SniSupport.resolve(uri, sni, tls, headers);
    }

    private static ConnectionKey connectionKey(ClientUri uri,
                                               SniConfig sni,
                                               Tls tls,
                                               HttpClientConfig clientConfig,
                                               Proxy proxy,
                                               ClientRequestHeaders headers) {
        if (sni == null) {
            return ConnectionKey.create(uri,
                                        tls,
                                        clientConfig.dnsResolver(),
                                        clientConfig.dnsAddressLookup(),
                                        proxy);
        }
        return ConnectionKey.create(uri,
                                    sni,
                                    tls,
                                    clientConfig.dnsResolver(),
                                    clientConfig.dnsAddressLookup(),
                                    proxy,
                                    headers);
    }

    private static ConnectionKey unixConnectionKey(ClientUri uri,
                                                   SniConfig sni,
                                                   Tls tls,
                                                   HttpClientConfig clientConfig,
                                                   UnixDomainSocketAddress address,
                                                   ClientRequestHeaders headers) {
        if (sni == null) {
            return ConnectionKey.createUnixDomainSocket(uri,
                                                        tls,
                                                        clientConfig.dnsResolver(),
                                                        clientConfig.dnsAddressLookup(),
                                                        address);
        }
        return ConnectionKey.createUnixDomainSocket(uri,
                                                    sni,
                                                    tls,
                                                    clientConfig.dnsResolver(),
                                                    clientConfig.dnsAddressLookup(),
                                                    address,
                                                    headers);
    }

    private static final class RequestBody {
        private final AtomicBoolean streamHandlerClaimed = new AtomicBoolean();
        private Object entity;
        private byte[] bytes;
        private OutputStreamHandler streamHandler;
        private EntityWriterPreflight preflight;
        private EntityWriterPreflight.HeaderChanges headerChanges;
        private long contentLength = -1;
        private boolean prepared;
        private boolean opaqueAttempted;

        private RequestBody(Object entity) {
            if (entity instanceof byte[] entityBytes) {
                this.bytes = entityBytes;
                this.contentLength = entityBytes.length;
                this.prepared = true;
            } else {
                this.entity = entity;
            }
        }

        private RequestBody(OutputStreamHandler streamHandler, long contentLength) {
            if (contentLength < -1) {
                throw new IllegalArgumentException("Content length must be -1 or greater: " + contentLength);
            }
            this.streamHandler = streamHandler;
            this.contentLength = contentLength;
            this.prepared = true;
        }

        private static RequestBody create(Object entity) {
            return new RequestBody(entity);
        }

        private static RequestBody create(OutputStreamHandler streamHandler, long contentLength) {
            return new RequestBody(streamHandler, contentLength);
        }

        private void prepare(HttpClientRequest request, Context context) {
            EntityWriterPreflight.Application application = prepare(request.mediaContext(),
                                                                     request.clientConfig().maxInMemoryEntity(),
                                                                     request.clientConfig().writeBufferSize(),
                                                                     request.headers(),
                                                                     context);
            request.preparedEntityHeaders = headerChanges == null || headerChanges.isEmpty()
                    ? null
                    : new PreparedEntityHeaders(this, application);
        }

        private EntityWriterPreflight.Application prepare(MediaContext mediaContext,
                                                          long maxInMemoryEntity,
                                                          int writeBufferSize,
                                                          ClientRequestHeaders requestHeaders,
                                                          Context context) {
            if (!prepared) {
                EntityWriterPreflight.HeaderRecorder recordingHeaders = EntityWriterPreflight.record(requestHeaders);
                Object object = entity;
                GenericType<Object> genericType = GenericType.create(object);
                EntityWriter<Object> writer = mediaContext.writer(genericType, recordingHeaders);
                long configuredContentLength = requestHeaders.contentLength().orElse(-1);
                EntityWriterPreflight.Application writerApplication = null;
                if (writer.supportsInstanceWriter()) {
                    InstanceWriter instanceWriter = writer.instanceWriter(genericType, object, recordingHeaders);
                    if (instanceWriter.alwaysInMemory()) {
                        bytes = instanceWriter.instanceBytes();
                    } else {
                        contentLength = instanceWriter.contentLength().orElse(configuredContentLength);
                        if (contentLength < 0 || contentLength > maxInMemoryEntity) {
                            streamHandler = instanceWriter::write;
                        } else {
                            bytes = instanceWriter.instanceBytes();
                        }
                    }
                } else if (configuredContentLength < 0 || configuredContentLength > maxInMemoryEntity) {
                    contentLength = configuredContentLength;
                    int maximum = (int) Math.max(1, Math.min(Integer.MAX_VALUE, maxInMemoryEntity));
                    int desiredBuffer = writeBufferSize <= 1 ? 1024 : writeBufferSize;
                    preflight = EntityWriterPreflight.create(Math.max(1, Math.min(desiredBuffer, maximum)),
                                                             context,
                                                             (outputStream, isolatedHeaders) -> writer.write(genericType,
                                                                                                               object,
                                                                                                               outputStream,
                                                                                                               isolatedHeaders));
                    writerApplication = preflight.prepare(requestHeaders);
                } else {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream((int) configuredContentLength);
                    writer.write(genericType, object, outputStream, recordingHeaders);
                    bytes = outputStream.toByteArray();
                }
                entity = null;
                prepared = true;
                headerChanges = recordingHeaders.changes();
                if (preflight != null) {
                    headerChanges = headerChanges.andThen(preflight.headerChanges());
                }
                EntityWriterPreflight.Application application = recordingHeaders.application();
                if (writerApplication != null) {
                    application = application.andThen(writerApplication);
                }
                if (bytes != null) {
                    contentLength = bytes.length;
                }
                if (contentLength >= 0) {
                    requestHeaders.contentLength(contentLength);
                }
                return application;
            }

            EntityWriterPreflight.Application application = headerChanges == null || headerChanges.isEmpty()
                    ? null
                    : headerChanges.apply(requestHeaders);
            if (bytes != null) {
                contentLength = bytes.length;
            }
            if (contentLength >= 0) {
                requestHeaders.contentLength(contentLength);
            }
            return application;
        }

        private boolean prepared() {
            return prepared;
        }

        private boolean canStartAttempt() {
            if (preflight != null) {
                return preflight.canAttach();
            }
            return prepared
                    ? bytes != null || !streamHandlerClaimed.get()
                    : !opaqueAttempted;
        }

        private boolean isEmpty() {
            return prepared && bytes != null && bytes.length == 0;
        }

        private HttpClientResponse submit(ClientRequest<?> request) {
            if (!prepared) {
                opaqueAttempted = true;
                return request.submit(entity);
            }
            if (bytes != null) {
                return request.submit(bytes);
            }
            if (preflight != null) {
                return request.outputStream(preflight::writeTo);
            }
            return request.outputStream(this::writeTo);
        }

        private void writeTo(OutputStream outputStream) throws IOException {
            if (!streamHandlerClaimed.compareAndSet(false, true)) {
                throw new IllegalStateException("Request body is one-shot and has already been consumed.");
            }
            streamHandler.handle(outputStream);
        }

        private void cancelIfUnattached() {
            if (preflight != null) {
                preflight.cancelIfUnattached(new CancellationException("Request completed before entity writer attachment"));
            }
        }
    }

    private record PreparedEntityHeaders(RequestBody body, EntityWriterPreflight.Application application) {
    }

    private static final class ServicedHttpClientResponse implements HttpClientResponse {
        private final HttpClientResponse transportResponse;
        private final WebClientServiceResponse rawServiceResponse;
        private final WebClientServiceResponse serviceResponse;
        private final ClientRequestHeaders requestHeaders;
        private final ClientUri lastEndpointUri;
        private final MediaContext mediaContext;
        private final long fallbackMaxBufferedEntitySize;
        private final Duration readTimeout;
        private final CompletableFuture<WebClientServiceResponse> outerWhenComplete;
        private final ReentrantLock lifecycleLock = new ReentrantLock();
        private LifecycleState lifecycleState = LifecycleState.OPEN;
        private Throwable pendingFailure;
        private boolean entityRequested;

        private ServicedHttpClientResponse(HttpClientResponse transportResponse,
                                           WebClientServiceResponse rawServiceResponse,
                                           WebClientServiceResponse serviceResponse,
                                           ClientRequestHeaders requestHeaders,
                                           ClientUri lastEndpointUri,
                                           MediaContext mediaContext,
                                           long fallbackMaxBufferedEntitySize,
                                           Duration readTimeout,
                                           CompletableFuture<WebClientServiceResponse> outerWhenComplete) {
            this.transportResponse = transportResponse;
            this.rawServiceResponse = rawServiceResponse;
            this.serviceResponse = serviceResponse;
            this.requestHeaders = requestHeaders;
            this.lastEndpointUri = lastEndpointUri;
            this.mediaContext = mediaContext;
            this.fallbackMaxBufferedEntitySize = fallbackMaxBufferedEntitySize;
            this.readTimeout = readTimeout;
            this.outerWhenComplete = outerWhenComplete;
            if (rawServiceResponse != null) {
                rawServiceResponse.whenComplete().whenComplete((_, failure) -> {
                    if (failure != null) {
                        rawResponseFailed(failure);
                    }
                });
            }
        }

        @Override
        public String protocolId() {
            return transportResponse == null
                    ? serviceResponse.serviceRequest().protocolId()
                    : transportResponse.protocolId();
        }

        @Override
        public Status status() {
            return serviceResponse.status();
        }

        @Override
        public ClientResponseHeaders headers() {
            return serviceResponse.headers();
        }

        @Override
        public ClientResponseTrailers trailers() {
            if (!entityRequested) {
                throw new IllegalStateException("Trailers requested before reading entity.");
            }
            if (transportResponse != null
                    && (rawServiceResponse == serviceResponse
                            || rawServiceResponse.trailers() == serviceResponse.trailers())) {
                return transportResponse.trailers();
            }
            try {
                return serviceResponse.trailers().get(readTimeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                IllegalStateException failure = new IllegalStateException(
                        "Timeout " + readTimeout + " reached while waiting for trailers.", e);
                failResponse(failure);
                throw failure;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                IllegalStateException failure = new IllegalStateException("Interrupted while waiting for trailers.", e);
                failResponse(failure);
                throw failure;
            } catch (ExecutionException e) {
                RuntimeException failure = e.getCause() instanceof RuntimeException runtimeException
                        ? runtimeException
                        : new IllegalStateException(e.getCause());
                failResponse(failure);
                throw failure;
            }
        }

        @Override
        public ReadableEntity entity() {
            entityRequested = true;
            if (transportResponse != null && rawServiceResponse == serviceResponse) {
                ReadableEntity entity = transportResponse.entity();
                if (!entity.hasEntity()) {
                    close();
                    return entity;
                }
                InputStream stream = entity.inputStream();
                return ClientResponseEntity.create(estimate -> readBytes(stream, estimate, false),
                                                   this::close,
                                                   requestHeaders,
                                                   serviceResponse.headers(),
                                                   mediaContext,
                                                   maxBufferedEntitySize());
            }
            Optional<InputStream> inputStream = serviceResponse.inputStream();
            if (inputStream.isEmpty()) {
                close();
                return ClientResponseEntity.empty();
            }
            InputStream stream = inputStream.get();
            boolean consumesRawStream = rawServiceResponse != null
                    && rawServiceResponse.inputStream().filter(rawStream -> rawStream == stream).isPresent();
            return ClientResponseEntity.create(estimate -> readBytes(stream, estimate, consumesRawStream),
                                               this::close,
                                               requestHeaders,
                                               serviceResponse.headers(),
                                               mediaContext,
                                               maxBufferedEntitySize());
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Source<?>> void source(GenericType<T> sourceType, T source) {
            for (SourceHandlerProvider provider : SOURCE_HANDLERS) {
                if (provider.supports(sourceType, this)) {
                    try {
                        provider.handle(source, this, mediaContext);
                        close();
                        return;
                    } catch (RuntimeException | Error e) {
                        failResponse(e);
                        throw e;
                    }
                }
            }
            throw new UnsupportedOperationException("No source available for " + sourceType);
        }

        @Override
        public ClientUri lastEndpointUri() {
            return lastEndpointUri;
        }

        @Override
        public long maxBufferedEntitySize() {
            return transportResponse == null
                    ? fallbackMaxBufferedEntitySize
                    : transportResponse.maxBufferedEntitySize();
        }

        @Override
        public void close() {
            if (!beginClosing(null)) {
                return;
            }
            Throwable failure = finishResponse(null);
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (failure instanceof Error error) {
                throw error;
            }
        }

        private BufferData readBytes(InputStream inputStream, int estimate, boolean notifyTransport) {
            try {
                byte[] buffer = new byte[estimate > 0 ? estimate : 16];
                int read = inputStream.read(buffer);
                if (read < 1) {
                    if (notifyTransport && transportResponse != null) {
                        transportResponse.serviceEntityConsumed();
                    }
                    return BufferData.empty();
                }
                return BufferData.create(buffer, 0, read);
            } catch (IOException e) {
                UncheckedIOException failure = new UncheckedIOException(e);
                failResponse(failure);
                throw failure;
            } catch (RuntimeException | Error failure) {
                failResponse(failure);
                throw failure;
            }
        }

        private void failResponse(Throwable failure) {
            if (beginClosing(failure)) {
                finishResponse(failure);
            }
        }

        private void rawResponseFailed(Throwable failure) {
            if (beginClosing(failure)) {
                finishResponse(failure);
            }
        }

        private Throwable finishResponse(Throwable failure) {
            try {
                closeResources();
            } catch (RuntimeException | Error cleanupFailure) {
                failure = mergeFailure(failure, cleanupFailure);
            }
            lifecycleLock.lock();
            try {
                failure = mergeFailure(failure, pendingFailure);
                pendingFailure = null;
                lifecycleState = LifecycleState.DONE;
            } finally {
                lifecycleLock.unlock();
            }
            if (failure == null) {
                serviceResponse.whenComplete().complete(serviceResponse);
                outerWhenComplete.complete(serviceResponse);
            } else {
                serviceResponse.whenComplete().completeExceptionally(failure);
                outerWhenComplete.completeExceptionally(failure);
            }
            return failure;
        }

        private boolean beginClosing(Throwable failure) {
            lifecycleLock.lock();
            try {
                if (lifecycleState == LifecycleState.OPEN) {
                    lifecycleState = LifecycleState.CLOSING;
                    return true;
                }
                if (lifecycleState == LifecycleState.CLOSING) {
                    pendingFailure = mergeFailure(pendingFailure, failure);
                }
                return false;
            } finally {
                lifecycleLock.unlock();
            }
        }

        private static Throwable mergeFailure(Throwable primary, Throwable secondary) {
            if (primary == null) {
                return secondary;
            }
            if (secondary == null || primary == secondary) {
                return primary;
            }
            for (Throwable suppressed : primary.getSuppressed()) {
                if (suppressed == secondary) {
                    return primary;
                }
            }
            primary.addSuppressed(secondary);
            return primary;
        }

        private void closeResources() {
            if (transportResponse == null) {
                serviceResponse.connection().closeResource();
                return;
            }

            Throwable failure = null;
            if (rawServiceResponse == null
                    || serviceResponse.connection() != rawServiceResponse.connection()) {
                try {
                    serviceResponse.connection().closeResource();
                } catch (RuntimeException | Error closeFailure) {
                    failure = closeFailure;
                }
            }
            try {
                transportResponse.close();
            } catch (RuntimeException | Error closeFailure) {
                if (failure == null) {
                    failure = closeFailure;
                } else if (failure != closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (failure instanceof Error error) {
                throw error;
            }
        }

        private enum LifecycleState {
            OPEN,
            CLOSING,
            DONE
        }
    }
}
