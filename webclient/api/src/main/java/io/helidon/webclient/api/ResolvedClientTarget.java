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
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.Api;
import io.helidon.common.uri.UriAuthority;
import io.helidon.common.uri.UriHost;
import io.helidon.webclient.spi.DnsResolver;

/**
 * Concrete physical WebClient transport target for one new connection attempt.
 * <p>
 * Logical HTTP and TLS identity remains in {@link #logicalTarget()}; the concrete peer is routing data only.
 */
@Api.Internal
public final class ResolvedClientTarget {
    private final ClientConnectionTarget logicalTarget;
    private final UriAuthority routeAuthority;
    private final InetSocketAddress peerAddress;
    private final InetSocketAddress destinationAddress;
    private final long networkGeneration;

    private ResolvedClientTarget(ClientConnectionTarget logicalTarget,
                                 UriAuthority routeAuthority,
                                 InetSocketAddress peerAddress,
                                 InetSocketAddress destinationAddress,
                                 long networkGeneration) {
        this.logicalTarget = Objects.requireNonNull(logicalTarget, "logicalTarget");
        this.routeAuthority = Objects.requireNonNull(routeAuthority, "routeAuthority");
        this.peerAddress = Objects.requireNonNull(peerAddress, "peerAddress");
        this.destinationAddress = Objects.requireNonNull(destinationAddress, "destinationAddress");
        this.networkGeneration = networkGeneration;
    }

    static ResolvedClientTarget resolve(ClientConnectionTarget logicalTarget,
                                        String routeHost,
                                        int routePort,
                                        long networkGeneration) {
        Objects.requireNonNull(routeHost, "routeHost");
        UriAuthority routeAuthority = UriAuthority.create(UriHost.create(routeHost), routePort);
        ProxyRoute proxyRoute = logicalTarget.proxyRoute();
        InetSocketAddress selectedAddress = proxyRoute.proxyAddress()
                .orElseGet(() -> InetSocketAddress.createUnresolved(routeAuthority.host().value(), routePort));
        ConnectionKey connectionKey = logicalTarget.connectionKey();
        DnsResolver dnsResolver = connectionKey.dnsResolver();
        InetSocketAddress noProxyAddress = proxyRoute.noProxyAddress().orElse(null);
        if (noProxyAddress != null
                && (!connectionKey.routingHost().equalsIgnoreCase(routeHost) || connectionKey.port() != routePort)) {
            throw new IllegalArgumentException("An address-bound no-proxy route cannot use an alternative authority");
        }
        InetAddress peer = noProxyAddress == null ? selectedAddress.getAddress() : noProxyAddress.getAddress();
        if (peer == null) {
            peer = dnsResolver.resolveAddress(selectedAddress.getHostString(), connectionKey.dnsAddressLookup());
        }
        InetAddress numericPeer = InetAddress.ofLiteral(peer.getHostAddress());
        InetSocketAddress destinationAddress;
        if (proxyRoute.kind() == ProxyRoute.Kind.SOCKS) {
            InetAddress destination = dnsResolver.resolveAddress(routeAuthority.host().value(),
                                                                 connectionKey.dnsAddressLookup());
            destinationAddress = new InetSocketAddress(destination, routeAuthority.port());
        } else {
            destinationAddress = InetSocketAddress.createUnresolved(routeAuthority.host().value(),
                                                                    routeAuthority.port());
        }
        return new ResolvedClientTarget(logicalTarget,
                                        routeAuthority,
                                        new InetSocketAddress(numericPeer, selectedAddress.getPort()),
                                        destinationAddress,
                                        networkGeneration);
    }

    static ResolvedClientTarget direct(ClientConnectionTarget logicalTarget,
                                       UriAuthority routeAuthority,
                                       InetSocketAddress peerAddress,
                                       long networkGeneration) {
        if (!logicalTarget.proxyRoute().direct()) {
            throw new IllegalArgumentException("A pre-resolved direct target requires a direct proxy route");
        }
        if (peerAddress.isUnresolved()) {
            throw new IllegalArgumentException("A pre-resolved direct peer must contain an InetAddress");
        }
        InetAddress numericPeer = InetAddress.ofLiteral(peerAddress.getAddress().getHostAddress());
        InetSocketAddress normalizedPeer = new InetSocketAddress(numericPeer, peerAddress.getPort());
        return new ResolvedClientTarget(logicalTarget,
                                        routeAuthority,
                                        normalizedPeer,
                                        InetSocketAddress.createUnresolved(routeAuthority.host().value(),
                                                                           routeAuthority.port()),
                                        networkGeneration);
    }

    /**
     * Logical connection target and identity.
     *
     * @return logical target
     */
    public ClientConnectionTarget logicalTarget() {
        return logicalTarget;
    }

    /**
     * Selected direct or alternative route authority.
     *
     * @return route authority
     */
    public UriAuthority routeAuthority() {
        return routeAuthority;
    }

    /**
     * Concrete socket peer. This is the route peer for a direct connection and the proxy peer for a proxied connection.
     *
     * @return concrete peer
     */
    public InetSocketAddress peerAddress() {
        return peerAddress;
    }

    /**
     * Logical destination supplied to a proxy tunnel. The address is resolved locally for a system SOCKS route to
     * preserve WebClient's existing SOCKS behavior, because the selected SOCKS version is not available through the
     * standard proxy API.
     *
     * @return logical destination address
     */
    public InetSocketAddress destinationAddress() {
        return destinationAddress;
    }

    /**
     * Effective proxy route.
     *
     * @return proxy route
     */
    public ProxyRoute proxyRoute() {
        return logicalTarget.proxyRoute();
    }

    /**
     * Optional requested local bind address.
     *
     * @return local bind address
     */
    public Optional<InetSocketAddress> localAddress() {
        return logicalTarget.localAddress();
    }

    /**
     * Captured TLS reload generation.
     *
     * @return TLS generation
     */
    public long tlsGeneration() {
        return logicalTarget.tlsGeneration();
    }

    /**
     * Network or discovery generation for this physical attempt.
     *
     * @return network generation
     */
    public long networkGeneration() {
        return networkGeneration;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ResolvedClientTarget other)) {
            return false;
        }
        return logicalTarget.equals(other.logicalTarget)
                && routeAuthority.equals(other.routeAuthority)
                && peerAddress.equals(other.peerAddress)
                && destinationAddress.equals(other.destinationAddress)
                && networkGeneration == other.networkGeneration;
    }

    @Override
    public int hashCode() {
        return Objects.hash(logicalTarget, routeAuthority, peerAddress, destinationAddress, networkGeneration);
    }

    @Override
    public String toString() {
        return "ResolvedClientTarget[origin=" + logicalTarget.originAuthority()
                + ", route=" + routeAuthority
                + ", peerAddress=" + peerAddress
                + ", destinationAddress=" + destinationAddress
                + ", proxyRoute=" + proxyRoute()
                + ", tlsGeneration=" + tlsGeneration()
                + ", networkGeneration=" + networkGeneration
                + ']';
    }
}
