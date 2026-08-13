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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.UnixDomainSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.tls.Tls;
import io.helidon.common.tls.TlsMaterial;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.WritableHeaders;
import io.helidon.webclient.spi.DnsResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Isolated
class ClientConnectionTargetTest {
    private static final Tls TLS = Tls.builder().build();
    private static final Tls NO_TLS = Tls.builder().enabled(false).build();

    @Test
    void keepsLogicalOriginAndTlsIdentitySeparateFromDnsPeer() {
        AtomicInteger resolutions = new AtomicInteger();
        DnsResolver resolver = (host, lookup) -> {
            resolutions.incrementAndGet();
            return InetAddress.ofLiteral("127.0.0.7");
        };
        ClientUri uri = ClientUri.create(URI.create("https://route.example:8443/path"));
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        headers.set(HeaderNames.HOST, "origin.example:9443");
        ConnectionKey connectionKey = ConnectionKey.create(uri,
                                                           TLS,
                                                           resolver,
                                                           DnsAddressLookup.IPV4,
                                                           Proxy.noProxy());

        ClientConnectionTarget target = ClientConnectionTarget.create(connectionKey, uri, headers);
        ResolvedClientTarget resolved = target.resolve();

        assertThat(target.originAuthority().toString(), is("origin.example:9443"));
        assertThat(resolved.routeAuthority().toString(), is("route.example:8443"));
        assertThat(resolved.peerAddress().getAddress().getHostAddress(), is("127.0.0.7"));
        assertThat(connectionKey.tlsPeerHost(), is("route.example"));
        assertThat(resolutions.get(), is(1));
    }

    @Test
    void usesFinalAuthorityBeforeHost() {
        ClientUri uri = ClientUri.create(URI.create("https://route.example/path"));
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        headers.set(HeaderNames.HOST, "host.example:8443");
        headers.set(HeaderNames.create(":authority"), "authority.example:9443");
        ConnectionKey connectionKey = ConnectionKey.create(uri,
                                                           TLS,
                                                           (_, _) -> InetAddress.getLoopbackAddress(),
                                                           DnsAddressLookup.IPV4,
                                                           Proxy.noProxy());

        ClientConnectionTarget target = ClientConnectionTarget.create(connectionKey, uri, headers);

        assertThat(target.originAuthority().toString(), is("authority.example:9443"));
    }

    @Test
    void fallsBackToUriOriginForInvalidHost() {
        ClientUri uri = ClientUri.create(URI.create("http://route.example:8080/path"));
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        headers.set(HeaderNames.HOST, "route.example:808a");
        ConnectionKey connectionKey = ConnectionKey.create(uri,
                                                           NO_TLS,
                                                           (_, _) -> InetAddress.getLoopbackAddress(),
                                                           DnsAddressLookup.IPV4,
                                                           Proxy.noProxy());

        ClientConnectionTarget target = ClientConnectionTarget.create(connectionKey, uri, headers);

        assertThat(target.originAuthority().toString(), is("route.example:8080"));
        assertThat(headers.get(HeaderNames.HOST).get(), is("route.example:808a"));
    }

    @Test
    void normalizesBracketedIpv6ConnectionHost() {
        ClientUri uri = ClientUri.create(URI.create("http://[::1]:8080/path"));
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        ConnectionKey connectionKey = ConnectionKey.create(uri,
                                                           NO_TLS,
                                                           (host, lookup) -> {
                                                               assertThat(host, is("::1"));
                                                               return InetAddress.ofLiteral("::1");
                                                           },
                                                           DnsAddressLookup.IPV6,
                                                           Proxy.noProxy());

        ResolvedClientTarget target = ClientConnectionTarget.create(connectionKey, uri, headers).resolve();

        assertThat(connectionKey.host(), is("::1"));
        assertThat(target.routeAuthority().toString(), is("[::1]:8080"));
    }

    @Test
    void resolvesProxyPeerWithoutResolvingLogicalDestination() {
        AtomicInteger resolutions = new AtomicInteger();
        DnsResolver resolver = (host, lookup) -> {
            resolutions.incrementAndGet();
            assertThat(host, is("proxy.example"));
            return InetAddress.ofLiteral("127.0.0.9");
        };
        Proxy proxy = Proxy.builder().host("proxy.example").port(8181).build();
        ClientUri uri = ClientUri.create(URI.create("http://proxy-only.internal:8080/path"));
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        ConnectionKey connectionKey = ConnectionKey.create(uri,
                                                           NO_TLS,
                                                           resolver,
                                                           DnsAddressLookup.IPV4,
                                                           proxy);

        ResolvedClientTarget resolved = ClientConnectionTarget.create(connectionKey, uri, headers).resolve();

        assertThat(resolved.proxyRoute().kind(), is(ProxyRoute.Kind.HTTP_FORWARD));
        assertThat(resolved.peerAddress().getAddress().getHostAddress(), is("127.0.0.9"));
        assertThat(resolved.destinationAddress().isUnresolved(), is(true));
        assertThat(resolved.destinationAddress().getHostString(), is("proxy-only.internal"));
        assertThat(resolutions.get(), is(1));
    }

    @Test
    void stripsProxyHostnameFromResolvedPeer() throws IOException {
        ProxySelector previous = ProxySelector.getDefault();
        ProxySelector.setDefault(new FixedProxySelector(java.net.Proxy.Type.HTTP));
        try {
            InetAddress namedProxyAddress = InetAddress.getByAddress("proxy.example",
                                                                    new byte[] {127, 0, 0, 9});
            ClientUri uri = ClientUri.create(URI.create("https://origin.example/path"));
            ConnectionKey connectionKey = ConnectionKey.create(uri,
                                                               TLS,
                                                               (_, _) -> namedProxyAddress,
                                                               DnsAddressLookup.IPV4,
                                                               Proxy.create());

            ResolvedClientTarget resolved = ClientConnectionTarget.create(
                    connectionKey,
                    uri,
                    ClientRequestHeaders.create(WritableHeaders.create())).resolve();

            assertThat(resolved.peerAddress().getHostString(), is("127.0.0.9"));
            assertThat(resolved.peerAddress().getAddress().getHostAddress(), is("127.0.0.9"));
            assertThat(resolved.destinationAddress().isUnresolved(), is(true));
        } finally {
            ProxySelector.setDefault(previous);
        }
    }

    @Test
    void resolvesSystemSocksDestinationLocally() {
        ProxySelector previous = ProxySelector.getDefault();
        ProxySelector.setDefault(new FixedProxySelector(java.net.Proxy.Type.SOCKS));
        try {
            AtomicInteger resolutions = new AtomicInteger();
            ClientUri uri = ClientUri.create(URI.create("http://origin.example/path"));
            ConnectionKey connectionKey = ConnectionKey.create(uri,
                                                               NO_TLS,
                                                               (host, _) -> {
                                                                   resolutions.incrementAndGet();
                                                                   return "proxy.example".equals(host)
                                                                           ? InetAddress.ofLiteral("127.0.0.9")
                                                                           : InetAddress.ofLiteral("127.0.0.7");
                                                               },
                                                               DnsAddressLookup.IPV4,
                                                               Proxy.create());

            ResolvedClientTarget resolved = ClientConnectionTarget.create(
                    connectionKey,
                    uri,
                    ClientRequestHeaders.create(WritableHeaders.create())).resolve();

            assertThat(resolved.proxyRoute().kind(), is(ProxyRoute.Kind.SOCKS));
            assertThat(resolved.destinationAddress().isUnresolved(), is(false));
            assertThat(resolved.destinationAddress().getAddress().getHostAddress(), is("127.0.0.7"));
            assertThat(resolutions.get(), is(2));
        } finally {
            ProxySelector.setDefault(previous);
        }
    }

    @Test
    void configuredProxyRouteReflectsActualCapability() {
        Proxy proxy = Proxy.builder()
                .host("proxy.example")
                .port(8181)
                .addNoProxy("direct.example")
                .build();

        ProxyRoute direct = proxy.effectiveRoute("https", "direct.example", 443, true);
        ProxyRoute tunnel = proxy.effectiveRoute("https", "origin.example", 443, true);

        assertThat(direct.kind(), is(ProxyRoute.Kind.DIRECT));
        assertThat(direct.supportsDatagrams(), is(true));
        assertThat(tunnel.kind(), is(ProxyRoute.Kind.HTTP_TUNNEL));
        assertThat(tunnel.forwardProxy(), is(false));
        assertThat(tunnel.supportsDatagrams(), is(false));
    }

    @Test
    void selectsSystemProxyExactlyOnceForTargetSnapshot() {
        ProxySelector previous = ProxySelector.getDefault();
        AtomicInteger selections = new AtomicInteger();
        ProxySelector.setDefault(new ProxySelector() {
            @Override
            public List<java.net.Proxy> select(URI uri) {
                selections.incrementAndGet();
                return List.of(new java.net.Proxy(java.net.Proxy.Type.HTTP,
                                                  InetSocketAddress.createUnresolved("proxy.example", 8181)));
            }

            @Override
            public void connectFailed(URI uri, SocketAddress address, IOException failure) {
            }
        });
        try {
            ClientUri uri = ClientUri.create(URI.create("https://origin.example/path"));
            ConnectionKey connectionKey = ConnectionKey.create(uri,
                                                               TLS,
                                                               (_, _) -> InetAddress.getLoopbackAddress(),
                                                               DnsAddressLookup.IPV4,
                                                               Proxy.create());

            ClientConnectionTarget target = ClientConnectionTarget.create(
                    connectionKey,
                    uri,
                    ClientRequestHeaders.create(WritableHeaders.create()));
            ResolvedClientTarget resolved = target.resolve();

            assertThat(resolved.proxyRoute().kind(), is(ProxyRoute.Kind.HTTP_TUNNEL));
            assertThat(selections.get(), is(1));
        } finally {
            ProxySelector.setDefault(previous);
        }
    }

    @Test
    void forceConnectParticipatesInProxyPolicyIdentity() {
        Proxy forwardProxy = Proxy.builder().host("proxy.example").port(8181).build();
        Proxy tunnelProxy = Proxy.builder()
                .host("proxy.example")
                .port(8181)
                .forceHttpConnect(true)
                .build();

        assertThat(forwardProxy.equals(tunnelProxy), is(false));
        assertThat(forwardProxy.effectiveRoute("http", "origin.example", 80, false).kind(),
                   is(ProxyRoute.Kind.HTTP_FORWARD));
        assertThat(tunnelProxy.effectiveRoute("http", "origin.example", 80, false).kind(),
                   is(ProxyRoute.Kind.HTTP_TUNNEL));
    }

    @Test
    void partitionsConnectionCacheIdentity() {
        ClientUri uri = ClientUri.create(URI.create("https://origin.example/path"));
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        DnsResolver resolver = (_, _) -> InetAddress.getLoopbackAddress();
        ConnectionKey connectionKey = ConnectionKey.create(uri,
                                                           TLS,
                                                           resolver,
                                                           DnsAddressLookup.IPV4,
                                                           Proxy.noProxy());
        ClientConnectionTarget target = ClientConnectionTarget.create(connectionKey, uri, headers);
        ClientConnectionTarget sameTarget = ClientConnectionTarget.create(connectionKey, uri, headers);

        assertThat(target.equals(sameTarget), is(true));
        assertThat(target.hashCode(), is(sameTarget.hashCode()));

        ClientRequestHeaders otherAuthorityHeaders = ClientRequestHeaders.create(WritableHeaders.create());
        otherAuthorityHeaders.set(HeaderNames.HOST, "other.example");
        ClientConnectionTarget otherAuthority = ClientConnectionTarget.create(connectionKey,
                                                                               uri,
                                                                               otherAuthorityHeaders);
        ClientConnectionTarget otherGeneration = ClientConnectionTarget.create(connectionKey,
                                                                                 target.scheme(),
                                                                                 target.originAuthority(),
                                                                                 target.proxyRoute(),
                                                                                 target.tlsGeneration() + 1);
        ClientConnectionTarget localBind = ClientConnectionTarget.create(connectionKey,
                                                                          target.scheme(),
                                                                          target.originAuthority(),
                                                                          target.proxyRoute(),
                                                                          new InetSocketAddress(InetAddress
                                                                                                        .getLoopbackAddress(),
                                                                                                0),
                                                                          target.tlsGeneration());
        UnixDomainSocketAddress unixAddress = UnixDomainSocketAddress.of("target.sock");
        ConnectionKey unixKey = ConnectionKey.createUnixDomainSocket("https",
                                                                      "origin.example",
                                                                      443,
                                                                      TLS,
                                                                      resolver,
                                                                      DnsAddressLookup.IPV4,
                                                                      unixAddress);
        ClientConnectionTarget unixTarget = ClientConnectionTarget.createUnixDomainSocket(unixKey,
                                                                                            uri,
                                                                                            headers,
                                                                                            unixAddress);

        assertThat(target.equals(otherAuthority), is(false));
        assertThat(target.equals(otherGeneration), is(false));
        assertThat(target.equals(localBind), is(false));
        assertThat(target.equals(unixTarget), is(false));
        assertThrows(IllegalStateException.class, unixTarget::resolve);
    }

    @Test
    void partitionsEquivalentTlsInstancesByIdentity() {
        ClientUri uri = ClientUri.create(URI.create("http://origin.example/path"));
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        DnsResolver resolver = (_, _) -> InetAddress.getLoopbackAddress();
        ConnectionKey firstKey = ConnectionKey.create(uri,
                                                      Tls.builder().enabled(false).build(),
                                                      resolver,
                                                      DnsAddressLookup.IPV4,
                                                      Proxy.noProxy());
        ConnectionKey secondKey = ConnectionKey.create(uri,
                                                       Tls.builder().enabled(false).build(),
                                                       resolver,
                                                       DnsAddressLookup.IPV4,
                                                       Proxy.noProxy());

        ClientConnectionTarget first = ClientConnectionTarget.create(firstKey, uri, headers);
        ClientConnectionTarget second = ClientConnectionTarget.create(secondKey, uri, headers);

        assertThat(firstKey.equals(secondKey), is(true));
        assertThat(first.equals(second), is(false));
    }

    @Test
    void rejectsResolutionAfterTlsReload() {
        AtomicInteger resolutions = new AtomicInteger();
        Tls tls = Tls.builder().trustAll(true).build();
        ClientUri uri = ClientUri.create(URI.create("https://origin.example/path"));
        ConnectionKey connectionKey = ConnectionKey.create(uri,
                                                           tls,
                                                           (_, _) -> {
                                                               resolutions.incrementAndGet();
                                                               return InetAddress.getLoopbackAddress();
                                                           },
                                                           DnsAddressLookup.IPV4,
                                                           Proxy.noProxy());
        ClientConnectionTarget target = ClientConnectionTarget.create(
                connectionKey,
                uri,
                ClientRequestHeaders.create(WritableHeaders.create()));

        tls.reload(TlsMaterial.builder().trustAll(true).build());

        IllegalStateException failure = assertThrows(IllegalStateException.class, target::resolve);
        assertThat(failure.getMessage(), is("TLS configuration was reloaded before target resolution"));
        assertThat(resolutions.get(), is(0));
    }

    @Test
    void reselectsRouteWhenLogicalTargetChanges() {
        Proxy proxy = Proxy.builder()
                .host("proxy.example")
                .port(8181)
                .addNoProxy("first.example")
                .build();
        ClientUri firstUri = ClientUri.create(URI.create("https://first.example/path"));
        DnsResolver resolver = (_, _) -> InetAddress.getLoopbackAddress();
        ConnectionKey firstKey = ConnectionKey.create(firstUri,
                                                       TLS,
                                                       resolver,
                                                       DnsAddressLookup.IPV4,
                                                       proxy);
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        ProxyRoute firstRoute = ClientConnectionTarget.create(firstKey, firstUri, headers).proxyRoute();

        ClientUri secondUri = ClientUri.create(URI.create("https://second.example/path"));
        ConnectionKey secondKey = ConnectionKey.create(secondUri,
                                                        TLS,
                                                        resolver,
                                                        DnsAddressLookup.IPV4,
                                                        proxy);
        ClientConnectionTarget secondTarget = ClientConnectionTarget.create(secondKey,
                                                                             secondUri,
                                                                             headers,
                                                                             firstRoute);

        assertThat(firstRoute.kind(), is(ProxyRoute.Kind.DIRECT));
        assertThat(secondTarget.proxyRoute().kind(), is(ProxyRoute.Kind.HTTP_TUNNEL));
    }

    @Test
    void resolvesHostnameToMatchIpNoProxyRule() {
        AtomicInteger resolutions = new AtomicInteger();
        Proxy proxy = Proxy.builder()
                .host("proxy.example")
                .port(8181)
                .addNoProxy("127.0.0.1")
                .build();
        ClientUri uri = ClientUri.create(URI.create("https://hostname.example/path"));
        ConnectionKey connectionKey = ConnectionKey.create(uri,
                                                           TLS,
                                                           (_, _) -> {
                                                               return resolutions.getAndIncrement() == 0
                                                                       ? InetAddress.ofLiteral("127.0.0.1")
                                                                       : InetAddress.ofLiteral("127.0.0.2");
                                                           },
                                                           DnsAddressLookup.IPV4,
                                                           proxy);

        ClientConnectionTarget target = ClientConnectionTarget.create(
                connectionKey,
                uri,
                ClientRequestHeaders.create(WritableHeaders.create()));
        ResolvedClientTarget resolved = target.resolve();

        assertThat(target.proxyRoute().kind(), is(ProxyRoute.Kind.DIRECT));
        assertThat(resolved.peerAddress().getAddress(), is(InetAddress.ofLiteral("127.0.0.1")));
        assertThat(resolutions.get(), is(1));
    }

    @Test
    void noProxyResolutionDoesNotPartitionLogicalTargetIdentity() {
        AtomicInteger resolutions = new AtomicInteger();
        Proxy proxy = Proxy.builder()
                .host("proxy.example")
                .port(8181)
                .addNoProxy("127.0.0.1")
                .addNoProxy("127.0.0.2")
                .build();
        ClientUri uri = ClientUri.create(URI.create("https://hostname.example/path"));
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        DnsResolver resolver = (_, _) -> resolutions.getAndIncrement() == 0
                ? InetAddress.ofLiteral("127.0.0.1")
                : InetAddress.ofLiteral("127.0.0.2");
        ConnectionKey firstKey = ConnectionKey.create(uri,
                                                      TLS,
                                                      resolver,
                                                      DnsAddressLookup.IPV4,
                                                      proxy);
        ConnectionKey secondKey = ConnectionKey.create(uri,
                                                       TLS,
                                                       resolver,
                                                       DnsAddressLookup.IPV4,
                                                       proxy);

        ClientConnectionTarget first = ClientConnectionTarget.create(firstKey, uri, headers);
        ClientConnectionTarget second = ClientConnectionTarget.create(secondKey, uri, headers);

        assertThat(first, is(second));
        assertThat(first.hashCode(), is(second.hashCode()));
        assertThat(first.resolve().peerAddress().getAddress(), is(InetAddress.ofLiteral("127.0.0.1")));
        assertThat(second.resolve().peerAddress().getAddress(), is(InetAddress.ofLiteral("127.0.0.2")));
        assertThat(resolutions.get(), is(2));
    }

    @Test
    void bindsLiteralIpNoProxyRouteWithoutDns() {
        Proxy proxy = Proxy.builder()
                .host("proxy.example")
                .port(8181)
                .addNoProxy("127.0.0.1")
                .build();
        ClientUri uri = ClientUri.create(URI.create("http://127.0.0.1/path"));
        ConnectionKey connectionKey = ConnectionKey.create(uri,
                                                           NO_TLS,
                                                           (_, _) -> {
                                                               throw new AssertionError("DNS must not resolve an IP literal");
                                                           },
                                                           DnsAddressLookup.IPV4,
                                                           proxy);

        ClientConnectionTarget target = ClientConnectionTarget.create(
                connectionKey,
                uri,
                ClientRequestHeaders.create(WritableHeaders.create()));

        assertThat(target.resolve().peerAddress().getAddress(), is(InetAddress.ofLiteral("127.0.0.1")));
    }

    @Test
    void rejectsAlternativeAuthorityForAddressBoundNoProxyRoute() {
        Proxy proxy = Proxy.builder()
                .host("proxy.example")
                .port(8181)
                .addNoProxy("127.0.0.1")
                .build();
        ClientUri uri = ClientUri.create(URI.create("https://hostname.example/path"));
        ConnectionKey connectionKey = ConnectionKey.create(uri,
                                                           TLS,
                                                           (_, _) -> InetAddress.ofLiteral("127.0.0.1"),
                                                           DnsAddressLookup.IPV4,
                                                           proxy);
        ClientConnectionTarget target = ClientConnectionTarget.create(
                connectionKey,
                uri,
                ClientRequestHeaders.create(WritableHeaders.create()));

        assertThrows(IllegalArgumentException.class, () -> target.resolve("alternative.example", 443, 0));
        assertThrows(IllegalArgumentException.class, () -> target.resolve("hostname.example", 8443, 0));
    }

    private static final class FixedProxySelector extends ProxySelector {
        private final java.net.Proxy.Type type;

        private FixedProxySelector(java.net.Proxy.Type type) {
            this.type = type;
        }

        @Override
        public List<java.net.Proxy> select(URI uri) {
            return List.of(new java.net.Proxy(type,
                                              InetSocketAddress.createUnresolved("proxy.example", 8181)));
        }

        @Override
        public void connectFailed(URI uri, SocketAddress address, IOException failure) {
        }
    }
}
