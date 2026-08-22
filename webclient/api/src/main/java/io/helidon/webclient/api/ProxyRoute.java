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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.Api;

/**
 * Effective proxy route selected for one logical WebClient target.
 * <p>
 * The selected route is distinct from configured proxy policy. For example, a system proxy selector or a configured
 * {@code no-proxy} rule can select a direct route even though the request carries a non-direct {@link Proxy}
 * configuration.
 * Address evidence retained for an IP-based {@code no-proxy} match does not participate in route equality or hashing.
 */
@Api.Internal
public final class ProxyRoute {
    static final ProxyRoute DIRECT = new ProxyRoute();

    private final Proxy source;
    private final Kind kind;
    private final InetSocketAddress proxyAddress;
    private final InetSocketAddress noProxyAddress;
    private final java.net.Proxy.Type systemProxyType;
    private final String scheme;
    private final String host;
    private final int port;
    private final boolean tls;

    private ProxyRoute() {
        this.source = null;
        this.kind = Kind.DIRECT;
        this.proxyAddress = null;
        this.noProxyAddress = null;
        this.systemProxyType = null;
        this.scheme = "";
        this.host = "";
        this.port = 0;
        this.tls = false;
    }

    private ProxyRoute(Proxy source,
                       Kind kind,
                       InetSocketAddress proxyAddress,
                       InetSocketAddress noProxyAddress,
                       java.net.Proxy.Type systemProxyType,
                       String scheme,
                       String host,
                       int port,
                       boolean tls) {
        this.source = Objects.requireNonNull(source, "source");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.proxyAddress = proxyAddress;
        this.noProxyAddress = noProxyAddress;
        this.systemProxyType = systemProxyType;
        this.scheme = Objects.requireNonNull(scheme, "scheme").toLowerCase(Locale.ROOT);
        this.host = Objects.requireNonNull(host, "host").toLowerCase(Locale.ROOT);
        this.port = port;
        this.tls = tls;
    }

    static ProxyRoute direct(Proxy source,
                             String scheme,
                             String host,
                             int port,
                             boolean tls) {
        return new ProxyRoute(source, Kind.DIRECT, null, null, null, scheme, host, port, tls);
    }

    static ProxyRoute direct(Proxy source,
                             String scheme,
                             String host,
                             int port,
                             boolean tls,
                             InetAddress noProxyAddress) {
        InetAddress address = Objects.requireNonNull(noProxyAddress, "noProxyAddress");
        return new ProxyRoute(source,
                              Kind.DIRECT,
                              null,
                              new InetSocketAddress(InetAddress.ofLiteral(address.getHostAddress()), port),
                              null,
                              scheme,
                              host,
                              port,
                              tls);
    }

    static ProxyRoute configuredHttp(Proxy source,
                                     Kind kind,
                                     InetSocketAddress proxyAddress,
                                     String scheme,
                                     String host,
                                     int port,
                                     boolean tls) {
        if (kind != Kind.HTTP_FORWARD && kind != Kind.HTTP_TUNNEL) {
            throw new IllegalArgumentException("Configured HTTP proxy route must be HTTP_FORWARD or HTTP_TUNNEL");
        }
        return new ProxyRoute(source,
                              kind,
                              Objects.requireNonNull(proxyAddress, "proxyAddress"),
                              null,
                              null,
                              scheme,
                              host,
                              port,
                              tls);
    }

    static ProxyRoute system(Proxy source,
                             Kind kind,
                             java.net.Proxy systemProxy,
                             String scheme,
                             String host,
                             int port,
                             boolean tls) {
        if (kind != Kind.HTTP_TUNNEL && kind != Kind.SOCKS) {
            throw new IllegalArgumentException("System proxy route must be HTTP_TUNNEL or SOCKS");
        }
        if (!(systemProxy.address() instanceof InetSocketAddress proxyAddress)) {
            throw new IllegalArgumentException("System proxy address must be an InetSocketAddress");
        }
        return new ProxyRoute(source,
                              kind,
                              proxyAddress,
                              null,
                              systemProxy.type(),
                              scheme,
                              host,
                              port,
                              tls);
    }

    /**
     * Selected route kind.
     *
     * @return route kind
     */
    public Kind kind() {
        return kind;
    }

    /**
     * Whether this is a direct route.
     *
     * @return whether the route is direct
     */
    public boolean direct() {
        return kind == Kind.DIRECT;
    }

    /**
     * Whether the route supports UDP datagrams.
     * <p>
     * Helidon currently supports datagrams only on a direct route. Ordinary HTTP CONNECT and SOCKS routes do not imply
     * CONNECT-UDP, MASQUE, or SOCKS UDP-associate support.
     *
     * @return whether datagrams are supported
     */
    public boolean supportsDatagrams() {
        return direct();
    }

    /**
     * Whether HTTP/1.1 must use absolute-form request targets on this route.
     *
     * @return whether this is an HTTP forward-proxy route
     */
    public boolean forwardProxy() {
        return kind == Kind.HTTP_FORWARD;
    }

    /**
     * Configured or system-selected proxy address.
     *
     * @return proxy address, empty for a direct route
     */
    public Optional<InetSocketAddress> proxyAddress() {
        return Optional.ofNullable(proxyAddress);
    }

    /**
     * Address whose configured {@code no-proxy} match selected this direct route.
     * <p>
     * When present, the same address must be used for the physical connection so a later DNS answer cannot invalidate
     * the route decision.
     *
     * @return resolved no-proxy address, or empty when route selection did not require DNS
     */
    Optional<InetSocketAddress> noProxyAddress() {
        return Optional.ofNullable(noProxyAddress);
    }

    java.net.Proxy.Type systemProxyType() {
        return systemProxyType;
    }

    boolean belongsTo(Proxy proxy) {
        if (this == DIRECT) {
            return proxy.type() == null || proxy.type() == Proxy.ProxyType.NONE;
        }
        return source.equals(proxy);
    }

    boolean selectedFor(String scheme, String host, int port, boolean tls) {
        if (this == DIRECT) {
            return true;
        }
        return this.scheme.equalsIgnoreCase(scheme)
                && this.host.equalsIgnoreCase(host)
                && this.port == port
                && this.tls == tls;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ProxyRoute other)) {
            return false;
        }
        if (this == DIRECT || other == DIRECT) {
            return false;
        }
        return source.equals(other.source)
                && kind == other.kind
                && Objects.equals(proxyAddress, other.proxyAddress)
                && systemProxyType == other.systemProxyType
                && scheme.equals(other.scheme)
                && host.equals(other.host)
                && port == other.port
                && tls == other.tls;
    }

    @Override
    public int hashCode() {
        if (this == DIRECT) {
            return 1;
        }
        int result = source.hashCode();
        result = 31 * result + kind.hashCode();
        result = 31 * result + Objects.hashCode(proxyAddress);
        result = 31 * result + Objects.hashCode(systemProxyType);
        result = 31 * result + scheme.hashCode();
        result = 31 * result + host.hashCode();
        result = 31 * result + Integer.hashCode(port);
        return 31 * result + Boolean.hashCode(tls);
    }

    @Override
    public String toString() {
        return "ProxyRoute[kind=" + kind
                + (proxyAddress == null ? "" : ", proxyAddress=" + proxyAddress)
                + (noProxyAddress == null ? "" : ", noProxyAddress=" + noProxyAddress)
                + ']';
    }

    /**
     * Effective route kind.
     */
    public enum Kind {
        /**
         * Direct route to the selected target.
         */
        DIRECT,

        /**
         * HTTP forward proxy. HTTP/1.1 uses absolute-form request targets.
         */
        HTTP_FORWARD,

        /**
         * HTTP CONNECT tunnel. Application protocols use ordinary origin semantics inside the tunnel.
         */
        HTTP_TUNNEL,

        /**
         * SOCKS proxy route.
         */
        SOCKS
    }
}
