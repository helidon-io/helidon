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

package io.helidon.webclient.api;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.Api;
import io.helidon.common.uri.UriAuthority;
import io.helidon.common.uri.UriHost;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;

/**
 * Logical WebClient connection target selected after request services have finalized the URI and headers.
 * <p>
 * This value is safe to use as a cache key. A configured IP-based {@code no-proxy} rule retains the address that
 * selected the direct route so the physical connection cannot use a different DNS answer. That per-acquisition evidence
 * does not participate in target equality or hashing.
 */
@Api.Internal
public final class ClientConnectionTarget {
    private static final HeaderName AUTHORITY = HeaderNames.create(":authority");

    private final ConnectionKey connectionKey;
    private final String scheme;
    private final UriAuthority originAuthorityOverride;
    private final ProxyRoute proxyRoute;
    private final SocketAddress transportAddress;
    private final InetSocketAddress localAddress;
    private final long tlsGeneration;

    private ClientConnectionTarget(ConnectionKey connectionKey,
                                   String scheme,
                                   UriAuthority originAuthority,
                                   ProxyRoute proxyRoute,
                                   SocketAddress transportAddress,
                                   InetSocketAddress localAddress) {
        this(connectionKey,
             scheme,
             originAuthority,
             proxyRoute,
             transportAddress,
             localAddress,
             connectionKey.tls().generation());
    }

    private ClientConnectionTarget(ConnectionKey connectionKey,
                                   String scheme,
                                   UriAuthority originAuthority,
                                   ProxyRoute proxyRoute,
                                   SocketAddress transportAddress,
                                   InetSocketAddress localAddress,
                                   long tlsGeneration) {
        this.connectionKey = Objects.requireNonNull(connectionKey, "connectionKey");
        this.scheme = Objects.requireNonNull(scheme, "scheme").toLowerCase(Locale.ROOT);
        this.originAuthorityOverride = originAuthority;
        this.proxyRoute = Objects.requireNonNull(proxyRoute, "proxyRoute");
        this.transportAddress = transportAddress;
        this.localAddress = localAddress;
        this.tlsGeneration = tlsGeneration;
    }

    /**
     * Create a logical IP connection target from a final request URI and headers.
     *
     * @param connectionKey connection policy and TLS identity
     * @param uri final request URI
     * @param headers final request headers
     * @return logical connection target
     */
    public static ClientConnectionTarget create(ConnectionKey connectionKey,
                                                ClientUri uri,
                                                ClientRequestHeaders headers) {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(headers, "headers");
        ConnectionKey key = Objects.requireNonNull(connectionKey, "connectionKey");
        String scheme = uri.scheme().toLowerCase(Locale.ROOT);
        UriAuthority originAuthority = originAuthority(key, uri, headers, scheme);
        ProxyRoute route = key.proxy().effectiveRoute(scheme,
                                                      key.host(),
                                                      key.port(),
                                                      key.tls().enabled(),
                                                      key.dnsResolver(),
                                                      key.dnsAddressLookup());
        return new ClientConnectionTarget(key, scheme, originAuthority, route, null, null);
    }

    /**
     * Create a logical IP connection target using an already-selected proxy route.
     *
     * @param connectionKey connection policy and TLS identity
     * @param uri final request URI
     * @param headers final request headers
     * @param selectedProxyRoute selected proxy route
     * @return logical connection target
     */
    public static ClientConnectionTarget create(ConnectionKey connectionKey,
                                                ClientUri uri,
                                                ClientRequestHeaders headers,
                                                ProxyRoute selectedProxyRoute) {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(headers, "headers");
        ConnectionKey key = Objects.requireNonNull(connectionKey, "connectionKey");
        String scheme = uri.scheme().toLowerCase(Locale.ROOT);
        UriAuthority originAuthority = originAuthority(key, uri, headers, scheme);
        ProxyRoute route = routeFor(key, scheme, selectedProxyRoute);
        return new ClientConnectionTarget(key, scheme, originAuthority, route, null, null);
    }

    /**
     * Create a logical IP connection target from an already-normalized origin.
     *
     * @param connectionKey connection policy and TLS identity
     * @param scheme origin scheme
     * @param originAuthority normalized effective origin authority
     * @return logical connection target
     */
    public static ClientConnectionTarget create(ConnectionKey connectionKey,
                                                String scheme,
                                                UriAuthority originAuthority) {
        ConnectionKey key = Objects.requireNonNull(connectionKey, "connectionKey");
        ProxyRoute route = key.proxy().effectiveRoute(scheme,
                                                      key.host(),
                                                      key.port(),
                                                      key.tls().enabled(),
                                                      key.dnsResolver(),
                                                      key.dnsAddressLookup());
        return new ClientConnectionTarget(key,
                                          scheme,
                                          canonicalOriginAuthority(key, originAuthority),
                                          route,
                                          null,
                                          null);
    }

    /**
     * Create a logical IP connection target using the connection-key origin.
     *
     * @param connectionKey connection policy and TLS identity
     * @param scheme origin scheme
     * @return logical connection target
     */
    @Api.Internal
    public static ClientConnectionTarget create(ConnectionKey connectionKey, String scheme) {
        ConnectionKey key = Objects.requireNonNull(connectionKey, "connectionKey");
        ProxyRoute route = key.proxy().effectiveRoute(scheme,
                                                      key.host(),
                                                      key.port(),
                                                      key.tls().enabled(),
                                                      key.dnsResolver(),
                                                      key.dnsAddressLookup());
        return new ClientConnectionTarget(key, scheme, null, route, null, null);
    }

    /**
     * Create a logical target from an already-normalized origin and selected route.
     *
     * @param connectionKey connection policy and TLS identity
     * @param scheme origin scheme
     * @param originAuthority normalized effective origin authority
     * @param proxyRoute previously selected proxy route
     * @return logical connection target
     */
    @Api.Internal
    public static ClientConnectionTarget create(ConnectionKey connectionKey,
                                                String scheme,
                                                UriAuthority originAuthority,
                                                ProxyRoute proxyRoute) {
        ConnectionKey key = Objects.requireNonNull(connectionKey, "connectionKey");
        return new ClientConnectionTarget(key,
                                          scheme,
                                          canonicalOriginAuthority(key, originAuthority),
                                          routeFor(key, scheme, proxyRoute),
                                          null,
                                          null);
    }

    /**
     * Create a logical target using the connection-key origin and a selected route.
     *
     * @param connectionKey connection policy and TLS identity
     * @param scheme origin scheme
     * @param proxyRoute previously selected proxy route
     * @return logical connection target
     */
    @Api.Internal
    public static ClientConnectionTarget create(ConnectionKey connectionKey,
                                                String scheme,
                                                ProxyRoute proxyRoute) {
        ConnectionKey key = Objects.requireNonNull(connectionKey, "connectionKey");
        return new ClientConnectionTarget(key,
                                          scheme,
                                          null,
                                          routeFor(key, scheme, proxyRoute),
                                          null,
                                          null);
    }

    /**
     * Create a logical target from an already-normalized origin and selected route.
     *
     * @param connectionKey connection policy and TLS identity
     * @param scheme origin scheme
     * @param originAuthority normalized effective origin authority
     * @param proxyRoute previously selected proxy route
     * @param tlsGeneration captured TLS reload generation
     * @return logical connection target
     */
    public static ClientConnectionTarget create(ConnectionKey connectionKey,
                                                String scheme,
                                                UriAuthority originAuthority,
                                                ProxyRoute proxyRoute,
                                                long tlsGeneration) {
        ConnectionKey key = Objects.requireNonNull(connectionKey, "connectionKey");
        return new ClientConnectionTarget(key,
                                          scheme,
                                          canonicalOriginAuthority(key, originAuthority),
                                          routeFor(key, scheme, proxyRoute),
                                          null,
                                          null,
                                          tlsGeneration);
    }

    /**
     * Recreate a logical target from an already-selected route and captured generations.
     *
     * @param connectionKey connection policy and TLS identity
     * @param scheme origin scheme
     * @param originAuthority normalized effective origin authority
     * @param proxyRoute previously selected proxy route
     * @param localAddress local bind address
     * @param tlsGeneration captured TLS reload generation
     * @return logical connection target
     */
    public static ClientConnectionTarget create(ConnectionKey connectionKey,
                                                String scheme,
                                                UriAuthority originAuthority,
                                                ProxyRoute proxyRoute,
                                                InetSocketAddress localAddress,
                                                long tlsGeneration) {
        ConnectionKey key = Objects.requireNonNull(connectionKey, "connectionKey");
        return new ClientConnectionTarget(key,
                                          scheme,
                                          canonicalOriginAuthority(key, originAuthority),
                                          routeFor(key, scheme, proxyRoute),
                                          null,
                                          Objects.requireNonNull(localAddress, "localAddress"),
                                          tlsGeneration);
    }

    /**
     * Create a logical Unix-domain-socket target.
     *
     * @param connectionKey connection policy and TLS identity
     * @param uri final request URI
     * @param headers final request headers
     * @param address Unix-domain-socket address
     * @return logical connection target
     */
    public static ClientConnectionTarget createUnixDomainSocket(ConnectionKey connectionKey,
                                                                ClientUri uri,
                                                                ClientRequestHeaders headers,
                                                                UnixDomainSocketAddress address) {
        ClientConnectionTarget target = create(connectionKey, uri, headers);
        return new ClientConnectionTarget(target.connectionKey,
                                          target.scheme,
                                          target.originAuthorityOverride,
                                          target.proxyRoute,
                                          Objects.requireNonNull(address, "address"),
                                          null);
    }

    /**
     * Existing connection policy and TLS identity.
     *
     * @return connection key
     */
    public ConnectionKey connectionKey() {
        return connectionKey;
    }

    /**
     * Normalized origin scheme.
     *
     * @return origin scheme
     */
    public String scheme() {
        return scheme;
    }

    /**
     * Normalized effective HTTP origin authority.
     *
     * @return origin authority
     */
    public UriAuthority originAuthority() {
        return originAuthorityOverride == null
                ? normalizedOriginAuthority(connectionKey)
                : originAuthorityOverride;
    }

    /**
     * Effective proxy route selected for this target.
     *
     * @return effective proxy route
     */
    public ProxyRoute proxyRoute() {
        return proxyRoute;
    }

    /**
     * Physical non-IP transport override.
     *
     * @return transport address, empty for an IP target
     */
    public Optional<SocketAddress> transportAddress() {
        return Optional.ofNullable(transportAddress);
    }

    /**
     * Requested local bind address.
     *
     * @return local bind address, empty for the ordinary wildcard bind
     */
    public Optional<InetSocketAddress> localAddress() {
        return Optional.ofNullable(localAddress);
    }

    /**
     * Captured TLS reload generation.
     *
     * @return TLS generation
     */
    public long tlsGeneration() {
        return tlsGeneration;
    }

    /**
     * Whether this target still represents the current TLS reload generation.
     *
     * @return whether the TLS generation is current
     */
    public boolean currentTlsGeneration() {
        return tlsGeneration == connectionKey.tls().generation();
    }

    /**
     * Whether a retained route was selected for the supplied logical target and proxy policy.
     *
     * @param connectionKey connection policy and target tuple
     * @param scheme logical target scheme
     * @param proxyRoute retained route
     * @return whether the route belongs to and matches the target
     */
    public static boolean routeMatches(ConnectionKey connectionKey, String scheme, ProxyRoute proxyRoute) {
        ConnectionKey key = Objects.requireNonNull(connectionKey, "connectionKey");
        ProxyRoute route = Objects.requireNonNull(proxyRoute, "proxyRoute");
        return route.belongsTo(key.proxy())
                && route.selectedFor(scheme, key.host(), key.port(), key.tls().enabled());
    }

    /**
     * Match an explicit TCP connection to a fully finalized logical target.
     *
     * @param connection explicit connection
     * @param connectionKey finalized connection identity
     * @param uri finalized request URI
     * @param headers finalized request headers
     * @return the connection's route when its complete logical target matches
     */
    public static Optional<ProxyRoute> matchingRoute(ClientConnection connection,
                                                     ConnectionKey connectionKey,
                                                     ClientUri uri,
                                                     ClientRequestHeaders headers) {
        Objects.requireNonNull(connection, "connection");
        ConnectionKey key = Objects.requireNonNull(connectionKey, "connectionKey");
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(headers, "headers");
        if (connection instanceof TcpClientConnection tcpConnection) {
            return tcpConnection.resolvedTarget()
                    .filter(resolved -> routeMatches(key, uri.scheme(), resolved.proxyRoute()))
                    .filter(resolved -> resolved.logicalTarget()
                            .equals(ClientConnectionTarget.create(key, uri, headers, resolved.proxyRoute())))
                    .map(ResolvedClientTarget::proxyRoute);
        }
        return Optional.empty();
    }

    /**
     * Resolve a direct or proxied physical attempt for the connection-key host and port.
     *
     * @return resolved target
     */
    public ResolvedClientTarget resolve() {
        return resolve(connectionKey.host(), connectionKey.port(), 0);
    }

    /**
     * Resolve a physical attempt for a selected route authority.
     *
     * @param routeHost selected direct or alternative route host
     * @param routePort selected direct or alternative route port
     * @param networkGeneration network/discovery generation
     * @return resolved target
     */
    public ResolvedClientTarget resolve(String routeHost, int routePort, long networkGeneration) {
        if (transportAddress != null) {
            throw new IllegalStateException("A Unix-domain-socket target does not resolve to an IP peer");
        }
        if (tlsGeneration != connectionKey.tls().generation()) {
            throw new IllegalStateException("TLS configuration was reloaded before target resolution");
        }
        return ResolvedClientTarget.resolve(this, routeHost, routePort, networkGeneration);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ClientConnectionTarget other)) {
            return false;
        }
        return connectionKey.tls() == other.connectionKey.tls()
                && connectionKey.equals(other.connectionKey)
                && scheme.equals(other.scheme)
                && Objects.equals(originAuthorityOverride, other.originAuthorityOverride)
                && proxyRoute.equals(other.proxyRoute)
                && Objects.equals(transportAddress, other.transportAddress)
                && Objects.equals(localAddress, other.localAddress)
                && tlsGeneration == other.tlsGeneration;
    }

    @Override
    public int hashCode() {
        int result = connectionKey.hashCode();
        result = 31 * result + System.identityHashCode(connectionKey.tls());
        result = 31 * result + scheme.hashCode();
        result = 31 * result + Objects.hashCode(originAuthorityOverride);
        result = 31 * result + proxyRoute.hashCode();
        result = 31 * result + Objects.hashCode(transportAddress);
        result = 31 * result + Objects.hashCode(localAddress);
        return 31 * result + Long.hashCode(tlsGeneration);
    }

    @Override
    public String toString() {
        return "ClientConnectionTarget[scheme=" + scheme
                + ", originAuthority=" + originAuthority()
                + ", proxyRoute=" + proxyRoute
                + (transportAddress == null ? "" : ", transportAddress=" + transportAddress)
                + (localAddress == null ? "" : ", localAddress=" + localAddress)
                + ", tlsGeneration=" + tlsGeneration
                + ']';
    }

    private static UriAuthority normalizedAuthority(String authority, String scheme) {
        UriAuthority parsed = UriAuthority.create(authority);
        int port = parsed.port();
        if (port == UriAuthority.UNDEFINED_PORT) {
            port = "https".equals(scheme) ? 443 : 80;
        }
        return UriAuthority.create(parsed.host(), port);
    }

    private static UriAuthority originAuthority(ConnectionKey connectionKey,
                                                ClientUri uri,
                                                ClientRequestHeaders headers,
                                                String scheme) {
        UriAuthority fallback = canonicalOriginAuthority(connectionKey,
                                                         normalizedAuthority(uri.authority(), scheme));
        String authority = headers.contains(AUTHORITY)
                ? headers.get(AUTHORITY).get()
                : headers.contains(HeaderNames.HOST) ? headers.get(HeaderNames.HOST).get() : null;
        if (authority == null) {
            return fallback;
        }
        try {
            return canonicalOriginAuthority(connectionKey, normalizedAuthority(authority, scheme));
        } catch (IllegalArgumentException _) {
            return fallback;
        }
    }

    private static UriAuthority canonicalOriginAuthority(ConnectionKey connectionKey,
                                                         UriAuthority originAuthority) {
        UriAuthority authority = Objects.requireNonNull(originAuthority, "originAuthority");
        return authority.equals(normalizedOriginAuthority(connectionKey)) ? null : authority;
    }

    private static UriAuthority normalizedOriginAuthority(ConnectionKey connectionKey) {
        return UriAuthority.create(UriHost.create(connectionKey.host()), connectionKey.port());
    }

    private static ProxyRoute routeFor(ConnectionKey connectionKey, String scheme, ProxyRoute proxyRoute) {
        ProxyRoute route = Objects.requireNonNull(proxyRoute, "proxyRoute");
        if (!route.belongsTo(connectionKey.proxy())) {
            return connectionKey.proxy().effectiveRoute(scheme,
                                                        connectionKey.host(),
                                                        connectionKey.port(),
                                                        connectionKey.tls().enabled(),
                                                        connectionKey.dnsResolver(),
                                                        connectionKey.dnsAddressLookup());
        }
        if (!route.selectedFor(scheme,
                               connectionKey.host(),
                               connectionKey.port(),
                               connectionKey.tls().enabled())) {
            return connectionKey.proxy().effectiveRoute(scheme,
                                                        connectionKey.host(),
                                                        connectionKey.port(),
                                                        connectionKey.tls().enabled(),
                                                        connectionKey.dnsResolver(),
                                                        connectionKey.dnsAddressLookup());
        }
        return route;
    }
}
