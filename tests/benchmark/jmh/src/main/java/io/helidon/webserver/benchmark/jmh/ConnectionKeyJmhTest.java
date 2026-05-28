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

package io.helidon.webserver.benchmark.jmh;

import java.net.URI;
import java.net.UnixDomainSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.helidon.common.task.DeadlineGuard;
import io.helidon.common.tls.Tls;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.WritableHeaders;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.DefaultDnsResolver;
import io.helidon.webclient.api.DnsAddressLookup;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.SniConfig;
import io.helidon.webclient.api.SniMode;
import io.helidon.webclient.spi.DnsResolver;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class ConnectionKeyJmhTest {
    private static final String SCHEME = "https";
    private static final String HOST = "service.example";
    private static final int PORT = 443;
    private static final Tls TLS = Tls.builder().enabled(false).build();
    private static final Tls TLS_ENABLED = Tls.builder().build();
    private static final DnsResolver DNS_RESOLVER = DefaultDnsResolver.create();
    private static final DnsAddressLookup DNS_ADDRESS_LOOKUP = DnsAddressLookup.defaultLookup();
    private static final Proxy PROXY = Proxy.noProxy();
    private static final Duration DEADLINE_TIMEOUT = Duration.ofSeconds(30);
    private static final ClientUri SNI_URI = ClientUri.create(URI.create("https://service.example:443/path"));
    private static final ClientRequestHeaders EMPTY_HEADERS = ClientRequestHeaders.create(WritableHeaders.create());
    private static final ClientRequestHeaders HOST_HEADERS = hostHeaders();
    private static final SniConfig URI_HOST_SNI = SniConfig.create();
    private static final SniConfig HOST_HEADER_SNI = SniConfig.builder()
            .mode(SniMode.HOST_HEADER)
            .build();
    private static final SniConfig EXPLICIT_SNI = SniConfig.builder()
            .mode(SniMode.EXPLICIT)
            .host("explicit.example")
            .build();
    private static final SniConfig DISABLED_SNI = SniConfig.builder()
            .mode(SniMode.DISABLED)
            .build();
    private static final UnixDomainSocketAddress UDS_ADDRESS =
            UnixDomainSocketAddress.of(Path.of("/tmp/helidon-jmh.sock"));
    private static final UnixDomainSocketAddress UDS_MISS_ADDRESS =
            UnixDomainSocketAddress.of(Path.of("/tmp/helidon-jmh-miss.sock"));
    private static final ConnectionKey TCP_KEY = ConnectionKey.create(SCHEME,
                                                                      HOST,
                                                                      PORT,
                                                                      TLS,
                                                                      DNS_RESOLVER,
                                                                      DNS_ADDRESS_LOOKUP,
                                                                      PROXY);
    private static final ConnectionKey UDS_KEY = ConnectionKey.createUnixDomainSocket(SCHEME,
                                                                                     HOST,
                                                                                     PORT,
                                                                                     TLS,
                                                                                     DNS_RESOLVER,
                                                                                     DNS_ADDRESS_LOOKUP,
                                                                                     UDS_ADDRESS);
    private static final ConnectionKey TCP_MISS_KEY = ConnectionKey.create(SCHEME,
                                                                           "miss.example",
                                                                           PORT,
                                                                           TLS,
                                                                           DNS_RESOLVER,
                                                                           DNS_ADDRESS_LOOKUP,
                                                                           PROXY);
    private static final ConnectionKey UDS_MISS_KEY = ConnectionKey.createUnixDomainSocket(SCHEME,
                                                                                          HOST,
                                                                                          PORT,
                                                                                          TLS,
                                                                                          DNS_RESOLVER,
                                                                                          DNS_ADDRESS_LOOKUP,
                                                                                          UDS_MISS_ADDRESS);
    private static final ConnectionKey SNI_KEY = sniKey(EXPLICIT_SNI, EMPTY_HEADERS);
    private static final ConnectionKey SNI_MISS_KEY = sniKey(SniConfig.builder()
                                                               .mode(SniMode.EXPLICIT)
                                                               .host("miss.example")
                                                               .build(),
                                                            EMPTY_HEADERS);
    private static final Map<ConnectionKey, ConnectionKey> CONNECTION_CACHE = connectionCache();

    @Benchmark
    public ConnectionKey tcpConnectionKey() {
        return ConnectionKey.create(SCHEME, HOST, PORT, TLS, DNS_RESOLVER, DNS_ADDRESS_LOOKUP, PROXY);
    }

    @Benchmark
    public ConnectionKey udsConnectionKey() {
        return ConnectionKey.createUnixDomainSocket(SCHEME,
                                                   HOST,
                                                   PORT,
                                                   TLS,
                                                   DNS_RESOLVER,
                                                   DNS_ADDRESS_LOOKUP,
                                                   UDS_ADDRESS);
    }

    @Benchmark
    public ConnectionKey tcpConnectionKeyUriHostSni() {
        return sniKey(URI_HOST_SNI, EMPTY_HEADERS);
    }

    @Benchmark
    public ConnectionKey tcpConnectionKeyHostHeaderSni() {
        return sniKey(HOST_HEADER_SNI, HOST_HEADERS);
    }

    @Benchmark
    public ConnectionKey tcpConnectionKeyExplicitSni() {
        return sniKey(EXPLICIT_SNI, EMPTY_HEADERS);
    }

    @Benchmark
    public ConnectionKey tcpConnectionKeyDisabledSni() {
        return sniKey(DISABLED_SNI, EMPTY_HEADERS);
    }

    @Benchmark
    public ConnectionKey udsConnectionKeyHostHeaderSni() {
        return ConnectionKey.createUnixDomainSocket(SNI_URI,
                                                   HOST_HEADER_SNI,
                                                   TLS_ENABLED,
                                                   DNS_RESOLVER,
                                                   DNS_ADDRESS_LOOKUP,
                                                   UDS_ADDRESS,
                                                   HOST_HEADERS);
    }

    @Benchmark
    public int tcpConnectionKeyHashCode() {
        return TCP_KEY.hashCode();
    }

    @Benchmark
    public int udsConnectionKeyHashCode() {
        return UDS_KEY.hashCode();
    }

    @Benchmark
    public int sniConnectionKeyHashCode() {
        return SNI_KEY.hashCode();
    }

    @Benchmark
    public boolean tcpConnectionKeyEquals() {
        return TCP_KEY.equals(ConnectionKey.create(SCHEME, HOST, PORT, TLS, DNS_RESOLVER, DNS_ADDRESS_LOOKUP, PROXY));
    }

    @Benchmark
    public boolean udsConnectionKeyEquals() {
        return UDS_KEY.equals(ConnectionKey.createUnixDomainSocket(SCHEME,
                                                                   HOST,
                                                                   PORT,
                                                                   TLS,
                                                                   DNS_RESOLVER,
                                                                   DNS_ADDRESS_LOOKUP,
                                                                   UDS_ADDRESS));
    }

    @Benchmark
    public boolean sniConnectionKeyEquals() {
        return SNI_KEY.equals(sniKey(EXPLICIT_SNI, EMPTY_HEADERS));
    }

    @Benchmark
    public ConnectionKey tcpConnectionKeyCacheHit() {
        return CONNECTION_CACHE.get(TCP_KEY);
    }

    @Benchmark
    public ConnectionKey udsConnectionKeyCacheHit() {
        return CONNECTION_CACHE.get(UDS_KEY);
    }

    @Benchmark
    public ConnectionKey tcpConnectionKeyCacheMiss() {
        return CONNECTION_CACHE.get(TCP_MISS_KEY);
    }

    @Benchmark
    public ConnectionKey udsConnectionKeyCacheMiss() {
        return CONNECTION_CACHE.get(UDS_MISS_KEY);
    }

    @Benchmark
    public ConnectionKey sniConnectionKeyCacheHit() {
        return CONNECTION_CACHE.get(SNI_KEY);
    }

    @Benchmark
    public ConnectionKey sniConnectionKeyCreateAndCacheHit() {
        return CONNECTION_CACHE.get(sniKey(EXPLICIT_SNI, EMPTY_HEADERS));
    }

    @Benchmark
    public ConnectionKey sniConnectionKeyCacheMiss() {
        return CONNECTION_CACHE.get(SNI_MISS_KEY);
    }

    @Benchmark
    public boolean deadlineGuardCreateAndCancel() {
        try (DeadlineGuard guard = DeadlineGuard.create(DEADLINE_TIMEOUT, () -> { })) {
            return guard.timedOut();
        }
    }

    private static Map<ConnectionKey, ConnectionKey> connectionCache() {
        Map<ConnectionKey, ConnectionKey> cache = new ConcurrentHashMap<>();
        cache.put(TCP_KEY, TCP_KEY);
        cache.put(UDS_KEY, UDS_KEY);
        cache.put(SNI_KEY, SNI_KEY);
        return cache;
    }

    private static ConnectionKey sniKey(SniConfig sni, ClientRequestHeaders headers) {
        return ConnectionKey.create(SNI_URI,
                                    sni,
                                    TLS_ENABLED,
                                    DNS_RESOLVER,
                                    DNS_ADDRESS_LOOKUP,
                                    PROXY,
                                    headers);
    }

    private static ClientRequestHeaders hostHeaders() {
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        headers.set(HeaderNames.HOST, "authority.example:443");
        return headers;
    }
}
