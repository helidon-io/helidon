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

import java.net.InetAddress;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import io.helidon.common.tls.Tls;
import io.helidon.common.tls.TlsMaterial;
import io.helidon.common.uri.UriAuthority;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpTransportObserver;
import io.helidon.http.WritableHeaders;
import io.helidon.webclient.api.ClientAltSvcConfig;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.DnsAddressLookup;
import io.helidon.webclient.api.Proxy;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class Http3ConnectionCacheTest {
    @Test
    void shouldRejectNonPositiveConnectionCacheSizes() {
        Tls tls = tls();

        assertThrows(IllegalArgumentException.class,
                     () -> Http3Client.builder()
                             .servicesDiscoverServices(false)
                             .shareConnectionCache(false)
                             .tls(tls)
                             .connectionCacheSize(0)
                             .build());
        assertThrows(IllegalArgumentException.class,
                     () -> Http3Client.builder()
                             .servicesDiscoverServices(false)
                             .shareConnectionCache(false)
                             .tls(tls)
                             .connectionCacheSize(-1)
                             .build());
        assertThrows(IllegalArgumentException.class,
                     () -> config(tls,
                                  Duration.ofSeconds(1),
                                  Duration.ofSeconds(1),
                                  false,
                                  16_384,
                                  0));
        assertThrows(IllegalArgumentException.class,
                     () -> config(tls,
                                  Duration.ofSeconds(1),
                                  Duration.ofSeconds(1),
                                  false,
                                  16_384,
                                  -1));
    }

    @Test
    void shouldMapProtocolTimeoutsIndependently() {
        Duration initialResponseTimeout = Duration.ofSeconds(3);
        Duration handshakeTimeout = Duration.ofSeconds(5);
        Duration streamOpenTimeout = Duration.ofSeconds(7);
        Tls tls = tls();
        Http3ClientProtocolConfig protocolConfig = Http3ClientProtocolConfig.builder()
                .initialResponseTimeout(initialResponseTimeout)
                .handshakeTimeout(handshakeTimeout)
                .streamOpenTimeout(streamOpenTimeout)
                .buildPrototype();
        Http3ClientImpl client = (Http3ClientImpl) Http3Client.builder()
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .tls(tls)
                .protocolConfig(protocolConfig)
                .build();
        try {
            Http3ConnectionCache.CacheKey cacheKey = client.connectionCacheKey(endpointKey(tls, protocolConfig), null);

            assertThat(cacheKey.initialResponseTimeout(), is(initialResponseTimeout));
            assertThat(cacheKey.handshakeTimeout(), is(handshakeTimeout));
            assertThat(cacheKey.streamOpenTimeout(), is(streamOpenTimeout));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void shouldRejectConnectionConfigCreatedBeforeTlsReload() {
        Tls tls = tls();
        Http3ConnectionCache cache = Http3ConnectionCache.create();
        Http3ExchangeClient.ConnectionConfig config = config(tls, Duration.ofSeconds(1), Duration.ofSeconds(1));

        try {
            tls.reload(TlsMaterial.builder().trustAll(true).build());

            assertThrows(IllegalStateException.class,
                         () -> cache.requestStream(config,
                                                   mock(Http3ExchangeClient.RequestData.class),
                                                   Runnable::run));
            assertThat(cache.hasSession(config.cacheKey()), is(false));
        } finally {
            cache.closeResource();
        }
    }

    @Test
    void shouldRejectConnectionConfigAfterCacheCloses() {
        Tls tls = tls();
        Http3ConnectionCache cache = Http3ConnectionCache.create();
        Http3ExchangeClient.ConnectionConfig config = config(tls, Duration.ofSeconds(1), Duration.ofSeconds(1));

        cache.closeResource();

        assertThrows(IllegalStateException.class,
                     () -> cache.requestStream(config,
                                               mock(Http3ExchangeClient.RequestData.class),
                                               Runnable::run));
    }

    @Test
    void shouldPartitionSessionsByErrorDetailPolicy() {
        Tls tls = tls();
        Http3ExchangeClient.ConnectionConfig defaultPolicy = config(
                tls,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                false);
        Http3ExchangeClient.ConnectionConfig disclosurePolicy = config(
                tls,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                true);

        assertThat(defaultPolicy.cacheKey().equals(disclosurePolicy.cacheKey()), is(false));
    }

    @Test
    void shouldPartitionSessionsByLocalHeaderLimit() {
        Tls tls = tls();
        Http3ExchangeClient.ConnectionConfig smallerLimit = config(
                tls,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                false,
                8_192);
        Http3ExchangeClient.ConnectionConfig largerLimit = config(
                tls,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                false,
                16_384);

        assertThat(smallerLimit.cacheKey().equals(largerLimit.cacheKey()), is(false));
    }

    @Test
    void defaultRuntimeExecutorKeepsSharedCacheKeyCompatibility() {
        Tls tls = tls();
        Http3ClientImpl first = runtimeClient(tls, Http3ClientProtocolConfig.create());
        Http3ClientImpl second = runtimeClient(tls, Http3ClientProtocolConfig.create());
        try {
            Http3ConnectionCache.CacheKey firstKey = runtimeKey(first);
            Http3ConnectionCache.CacheKey secondKey = runtimeKey(second);

            assertThat(first.connectionCache(), sameInstance(second.connectionCache()));
            assertThat(firstKey.runtimeExecutorKey(), is(Optional.empty()));
            assertThat(secondKey, is(firstKey));
            assertThat(secondKey.hashCode(), is(firstKey.hashCode()));
        } finally {
            first.closeResource();
            second.closeResource();
        }
    }

    @Test
    void sameExplicitExecutorRetainsCacheLookupWithoutCallingExecutorEqualityOrHash() {
        Tls tls = tls();
        var executor = new HostileExecutor();
        Http3ClientProtocolConfig config = Http3ClientProtocolConfig.builder().runtimeExecutor(executor).buildPrototype();
        Http3ClientImpl first = runtimeClient(tls, config);
        Http3ClientImpl second = runtimeClient(tls, config);
        try {
            Http3ConnectionCache.CacheKey firstKey = runtimeKey(first);
            Map<Http3ConnectionCache.CacheKey, String> pools = new HashMap<>();
            pools.put(firstKey, "same executor");
            executor.hash = Integer.MIN_VALUE;
            Http3ConnectionCache.CacheKey secondKey = runtimeKey(second);

            assertThat(secondKey, is(firstKey));
            assertThat(pools.get(secondKey), is("same executor"));
            assertThat(firstKey.runtimeExecutorKey().orElseThrow().executor(), sameInstance(executor));
            Http3Discovery discovery = Http3Discovery.create((_, _) -> { });
            discovery.recordAltSvc(firstKey.endpointKey(), ClientResponseHeaders.create(WritableHeaders.create()
                    .add(HeaderValues.create(HeaderNames.ALT_SVC, "h3=\":9443\""))));
            Http3Discovery.Selection learned = discovery.automaticTarget(secondKey.endpointKey(), true, _ -> false)
                    .orElseThrow();
            assertThat(learned.target().peerPort(), is(9443));
            discovery.recordFailure(learned);
            assertThat(discovery.automaticTarget(firstKey.endpointKey(), true, _ -> false).isEmpty(), is(true));
            assertThat(executor.equalsCalls, is(0));
            assertThat(executor.hashCalls, is(0));
        } finally {
            first.closeResource();
            second.closeResource();
        }
    }

    @Test
    void differentExplicitExecutorsCannotShareCacheKeysEvenWhenTheirEqualsReturnsTrue() {
        Tls tls = tls();
        var firstExecutor = new HostileExecutor();
        var secondExecutor = new HostileExecutor();
        Http3ClientImpl first = runtimeClient(tls, Http3ClientProtocolConfig.builder()
                .runtimeExecutor(firstExecutor).buildPrototype());
        Http3ClientImpl second = runtimeClient(tls, Http3ClientProtocolConfig.builder()
                .runtimeExecutor(secondExecutor).buildPrototype());
        Http3ClientImpl defaults = runtimeClient(tls, Http3ClientProtocolConfig.create());
        try {
            Http3ConnectionCache.CacheKey firstKey = runtimeKey(first);
            Http3ConnectionCache.CacheKey secondKey = runtimeKey(second);
            Map<Http3ConnectionCache.CacheKey, String> pools = new HashMap<>();
            pools.put(firstKey, "first");
            pools.put(secondKey, "second");

            assertThat(first.connectionCache(), sameInstance(second.connectionCache()));
            assertThat(secondKey, not(firstKey));
            assertThat(runtimeKey(defaults), not(firstKey));
            assertThat(pools.size(), is(2));
            assertThat(pools.get(firstKey), is("first"));
            assertThat(pools.get(secondKey), is("second"));
            Http3Discovery discovery = Http3Discovery.create((_, _) -> { });
            ClientResponseHeaders advertisement = ClientResponseHeaders.create(WritableHeaders.create()
                    .add(HeaderValues.create(HeaderNames.ALT_SVC, "h3=\":9443\"")));
            discovery.recordAltSvc(firstKey.endpointKey(), advertisement);
            assertThat(discovery.automaticTarget(secondKey.endpointKey(), true, _ -> false).isEmpty(), is(true));
            discovery.recordAltSvc(secondKey.endpointKey(), advertisement);
            discovery.recordFailure(discovery.automaticTarget(firstKey.endpointKey(), true, _ -> false).orElseThrow());
            assertThat(discovery.automaticTarget(secondKey.endpointKey(), true, _ -> false)
                               .orElseThrow().target().peerPort(), is(9443));
            assertThat(firstExecutor.equalsCalls + secondExecutor.equalsCalls, is(0));
            assertThat(firstExecutor.hashCalls + secondExecutor.hashCalls, is(0));
        } finally {
            first.closeResource();
            second.closeResource();
            defaults.closeResource();
        }
    }

    private static Http3ClientImpl runtimeClient(Tls tls, Http3ClientProtocolConfig config) {
        return (Http3ClientImpl) Http3Client.builder()
                .servicesDiscoverServices(false)
                .baseUri("https://runtime-executor.example")
                .altSvc(ClientAltSvcConfig.create())
                .tls(tls)
                .protocolConfig(config)
                .build();
    }

    private static Http3ConnectionCache.CacheKey runtimeKey(Http3ClientImpl client) {
        var request = (Http3ClientRequestImpl) client.get("/cache-key");
        request.priorKnowledge(true);
        Http3Discovery.Selection selection = client.requestTarget(request, request.resolvedUri(), request.headers(), true)
                .orElseThrow();
        return client.connectionCacheKey(selection.key(), selection.target());
    }

    private static Http3ExchangeClient.ConnectionConfig config(Tls tls,
                                                                Duration handshakeTimeout,
                                                                Duration streamOpenTimeout) {
        return config(tls, handshakeTimeout, streamOpenTimeout, false);
    }

    private static Http3ExchangeClient.ConnectionConfig config(Tls tls,
                                                                Duration handshakeTimeout,
                                                                Duration streamOpenTimeout,
                                                                boolean sendErrorDetails) {
        return config(tls, handshakeTimeout, streamOpenTimeout, sendErrorDetails, 16_384);
    }

    private static Http3ExchangeClient.ConnectionConfig config(Tls tls,
                                                                Duration handshakeTimeout,
                                                                Duration streamOpenTimeout,
                                                                boolean sendErrorDetails,
                                                                int maxHeadersSize) {
        return config(tls,
                      handshakeTimeout,
                      streamOpenTimeout,
                      sendErrorDetails,
                      maxHeadersSize,
                      1);
    }

    private static Http3ExchangeClient.ConnectionConfig config(Tls tls,
                                                                Duration handshakeTimeout,
                                                                Duration streamOpenTimeout,
                                                                boolean sendErrorDetails,
                                                                int maxHeadersSize,
                                                                int connectionCacheSize) {
        Http3Discovery.EndpointContextKey endpointKey = endpointKey(tls, Http3ClientProtocolConfig.create());
        Http3ConnectionCache.CacheKey cacheKey = new Http3ConnectionCache.CacheKey(endpointKey,
                                                                                    null,
                                                                                    0,
                                                                                    null,
                                                                                    maxHeadersSize,
                                                                                    null,
                                                                                    null,
                                                                                    sendErrorDetails,
                                                                                    Duration.ofSeconds(30),
                                                                                    handshakeTimeout,
                                                                                    streamOpenTimeout,
                                                                                    Optional.empty());
        return new Http3ExchangeClient.ConnectionConfig(cacheKey,
                                                        connectionCacheSize,
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        HttpTransportObserver.noop());
    }

    private static Http3Discovery.EndpointContextKey endpointKey(Tls tls,
                                                                  Http3ClientProtocolConfig protocolConfig) {
        ConnectionKey connectionKey = ConnectionKey.create("https",
                                                           "localhost",
                                                           443,
                                                           tls,
                                                           (_, _) -> InetAddress.getLoopbackAddress(),
                                                           DnsAddressLookup.defaultLookup(),
                                                           Proxy.noProxy());
        return new Http3Discovery.EndpointContextKey(
                connectionKey,
                protocolConfig,
                "https",
                UriAuthority.create("localhost:443"),
                tls.generation(),
                HttpTransportObserver.noop());
    }

    private static Tls tls() {
        return Tls.builder().trustAll(true).build();
    }

    private static final class HostileExecutor implements Executor {
        private int hash = 7;
        private int hashCalls;
        private int equalsCalls;

        @Override
        public void execute(Runnable command) {
            throw new AssertionError("Cache-key lookup must not submit transport work");
        }

        @Override
        public boolean equals(Object other) {
            equalsCalls++;
            return other instanceof HostileExecutor;
        }

        @Override
        public int hashCode() {
            hashCalls++;
            return hash;
        }
    }
}
