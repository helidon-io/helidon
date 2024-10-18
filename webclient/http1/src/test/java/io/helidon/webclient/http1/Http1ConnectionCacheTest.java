/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.webclient.http1;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.helidon.common.concurrency.limits.FixedLimit;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.concurrency.limits.LimitAlgorithm;
import io.helidon.common.uri.UriInfo;
import io.helidon.config.Config;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.TcpClientConnection;
import io.helidon.webclient.api.WebClient;

import org.junit.jupiter.api.Test;

import static io.helidon.webclient.http1.Http1ConnectionCache.ConnectionCreationStrategy;
import static io.helidon.webclient.http1.Http1ConnectionCache.UnlimitedConnectionStrategy;
import static io.helidon.webclient.http1.Http1ConnectionCache.LimitedConnectionStrategy;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

class Http1ConnectionCacheTest {

    private static final Http1ClientImpl DEFAULT_CLIENT;

    static {
        WebClient webClient = WebClient.create();
        Http1ClientConfig clientConfig = Http1ClientConfig.create();
        DEFAULT_CLIENT = new Http1ClientImpl(webClient, clientConfig);
    }

    @Test
    void testCacheFromConfig() {
        Config config = Config.create();
        Http1ConnectionCacheConfig cacheConfig = Http1ConnectionCacheConfig.create(config.get("missing"));
        Http1ConnectionCache cache = Http1ConnectionCache.create(cacheConfig);
        ConnectionCreationStrategy strategy = cache.strategy();
        assertThat(strategy, instanceOf(UnlimitedConnectionStrategy.class));

        cacheConfig = Http1ConnectionCacheConfig.create(
                config.get("client-cache-tests.connection-cache-config"));
        cache = Http1ConnectionCache.create(cacheConfig);
        strategy = cache.strategy();
        assertThat(strategy, instanceOf(LimitedConnectionStrategy.class));
    }

    @Test
    void testCacheFromClientConfig() {
        Config config = Config.create();
        Http1ClientImpl client = (Http1ClientImpl) Http1Client.create(config.get("missing"));
        Http1ConnectionCache cache = client.connectionCache();
        ConnectionCreationStrategy strategy = cache.strategy();
        assertThat(strategy, instanceOf(UnlimitedConnectionStrategy.class));

        client = (Http1ClientImpl) Http1Client.create(config.get("client-cache-tests"));
        cache = client.connectionCache();
        strategy = cache.strategy();
        assertThat(strategy, instanceOf(LimitedConnectionStrategy.class));
    }

    @Test
    void testDefaultConnectionCreationStrategyCreation() {
        Http1ConnectionCache cache = Http1ConnectionCache.create();
        assertThat(cache.strategy(), instanceOf(UnlimitedConnectionStrategy.class));
    }

    @Test
    void testLimitedConnectionCreationStrategyCreation() {
        Http1ConnectionCacheConfig connectionCacheConfig = Http1ConnectionCacheConfig.builder()
                .connectionLimit(FixedLimit.create())
                .build();
        Http1ConnectionCache cache = Http1ConnectionCache.create(connectionCacheConfig);
        assertThat(cache.strategy(), instanceOf(LimitedConnectionStrategy.class));
    }

    @Test
    void testDisabledLimits() {
        Http1ConnectionCacheConfig connectionCacheConfig = Http1ConnectionCacheConfig.builder()
                .connectionLimit(FixedLimit.create()) //This would indicate usage of the limited strategy
                .enableConnectionLimits(false) //This line enforces usage of unlimited strategy
                .build();
        Http1ConnectionCache cache = Http1ConnectionCache.create(connectionCacheConfig);
        assertThat(cache.strategy(), instanceOf(UnlimitedConnectionStrategy.class));
    }

    @Test
    void testUnlimitedConnectionStrategy() {
        Http1ClientRequest request = DEFAULT_CLIENT.get().uri("http://localhost:8080");
        Http1ClientConfig clientConfig = DEFAULT_CLIENT.prototype();
        UriInfo uri = request.resolvedUri();
        ConnectionKey connectionKey = new ConnectionKey(uri.scheme(),
                                                        uri.host(),
                                                        uri.port(),
                                                        clientConfig.readTimeout().orElse(Duration.ZERO),
                                                        clientConfig.tls(),
                                                        clientConfig.dnsResolver(),
                                                        clientConfig.dnsAddressLookup(),
                                                        clientConfig.proxy());
        ConnectionCreationStrategy strategy = new UnlimitedConnectionStrategy();

        for (int i = 0; i < 100; i++) {
            TcpClientConnection connection = strategy.createConnection(connectionKey,
                                                                       DEFAULT_CLIENT,
                                                                       tcpClientConnection -> false,
                                                                       true);
            assertThat(connection, notNullValue());
        }
    }

    @Test
    void testConnectionLimit() {
        Http1ConnectionCacheConfig cacheConfig = Http1ConnectionCacheConfig.builder()
                .connectionLimit(FixedLimit.builder().permits(5).build())
                .build();
        LimitedConnectionStrategy strategy = new LimitedConnectionStrategy(cacheConfig);
        testStrategyLimit("http://localhost:8080", strategy, 5);
        assertThat(strategy.maxConnectionLimit().tryAcquire(false), is(Optional.empty()));

        assertThat(strategy.connectionLimitsPerHost().size(), is(1));
        Limit localhostLimit = strategy.connectionLimitsPerHost().get("localhost");
        assertThat(localhostLimit, notNullValue());
        assertThat(localhostLimit.tryAcquire(false), is(not((Optional.empty()))));
    }

    @Test
    void testPerHostConnectionLimit() {
        Http1ConnectionCacheConfig cacheConfig = Http1ConnectionCacheConfig.builder()
                .connectionPerHostLimit(FixedLimit.builder().permits(5).build())
                .build();
        LimitedConnectionStrategy strategy = new LimitedConnectionStrategy(cacheConfig);
        testStrategyLimit("http://localhost:8080", strategy, 5);
        assertThat(strategy.maxConnectionLimit().tryAcquire(false), is(not((Optional.empty()))));

        assertThat(strategy.connectionLimitsPerHost().size(), is(1));
        Limit localhostLimit = strategy.connectionLimitsPerHost().get("localhost");
        assertThat(localhostLimit, notNullValue());
        assertThat(localhostLimit.tryAcquire(false), is(Optional.empty()));

        testStrategyLimit("http://localhost2:8080", strategy, 5);
        assertThat(strategy.connectionLimitsPerHost().size(), is(2));
    }

    @Test
    void testSpecificHostConnectionLimit() {
        Http1ConnectionCacheConfig cacheConfig = Http1ConnectionCacheConfig.builder()
                .connectionPerHostLimit(FixedLimit.builder().permits(5).build())
                .addHostLimit(builder -> builder.host("localhost").limit(FixedLimit.create(fb -> fb.permits(2))).build())
                .build();
        LimitedConnectionStrategy strategy = new LimitedConnectionStrategy(cacheConfig);
        testStrategyLimit("http://localhost:8080", strategy, 2);
        assertThat(strategy.maxConnectionLimit().tryAcquire(false), is(not((Optional.empty()))));

        Limit localhostLimit = strategy.connectionLimitsPerHost().get("localhost");
        assertThat(localhostLimit, notNullValue());
        assertThat(localhostLimit.tryAcquire(false), is(Optional.empty()));

        testStrategyLimit("http://localhost2:8080", strategy, 5);
        assertThat(strategy.connectionLimitsPerHost().size(), is(2));
    }

    @Test
    void testNonProxyConnectionLimit() {
        Http1ConnectionCacheConfig cacheConfig = Http1ConnectionCacheConfig.builder()
                .nonProxyConnectionLimit(FixedLimit.builder().permits(5).build())
                .addProxyLimit(builder -> builder.authority("localhost:1234")
                        .connectionLimit(FixedLimit.builder().permits(3).build()))
                .build();
        LimitedConnectionStrategy strategy = new LimitedConnectionStrategy(cacheConfig);
        testStrategyLimit("http://localhost:8080", strategy, 5);

        Proxy proxy = Proxy.builder().type(Proxy.ProxyType.HTTP).host("localhost").port(1234).build();
        testStrategyLimit("http://localhost:8080", strategy, 3, proxy);
    }

    @Test
    void testProxyConnectionConfigurationLimit() {
        Http1ConnectionCacheConfig cacheConfig = Http1ConnectionCacheConfig.builder()
                .addProxyLimit(builder -> builder.authority("localhost:1234")
                        .connectionLimit(FixedLimit.builder().permits(4).build())
                        .connectionPerHostLimit(FixedLimit.builder().permits(2).build())
                        .addHostLimit(hb -> hb.host("localhost2").limit(FixedLimit.create(fb -> fb.permits(3))).build()))
                .build();
        LimitedConnectionStrategy strategy = new LimitedConnectionStrategy(cacheConfig);
        Proxy proxy = Proxy.builder().type(Proxy.ProxyType.HTTP).host("localhost").port(1234).build();
        List<TcpClientConnection> connections = testStrategyLimit("http://localhost:8080", strategy, 2, proxy);

        //Only two free connections should be remaining in the max connection size by this proxy
        testStrategyLimit("http://localhost2:8080", strategy, 2, proxy);

        connections.forEach(TcpClientConnection::releaseResource);
        //1 more connections should be remaining to get the full host limit
        testStrategyLimit("http://localhost2:8080", strategy, 1, proxy);

        //1 remaining connection of the total amount of 4 connections permitted by this proxy
        testStrategyLimit("http://localhost:8080", strategy, 1, proxy);
    }

    @Test
    void testNonProxyConnectionReleasing() {
        Http1ConnectionCacheConfig cacheConfig = Http1ConnectionCacheConfig.builder()
                .connectionLimit(FixedLimit.builder().permits(5).build())
                .build();
        LimitedConnectionStrategy strategy = new LimitedConnectionStrategy(cacheConfig);
        List<TcpClientConnection> connections = testStrategyLimit("http://localhost:8080", strategy, 5);
        assertThat(strategy.maxConnectionLimit().tryAcquire(false), is(Optional.empty()));

        connections.forEach(TcpClientConnection::releaseResource);
        Optional<LimitAlgorithm.Token> token = strategy.maxConnectionLimit().tryAcquire(false);
        assertThat(token, is(not(Optional.empty())));
        token.get().ignore();

        testStrategyLimit("http://localhost:8080", strategy, 5);
    }

    private List<TcpClientConnection> testStrategyLimit(String uriString, LimitedConnectionStrategy strategy, int expectedLimit) {
        return testStrategyLimit(uriString, strategy, expectedLimit, null);
    }

    private List<TcpClientConnection> testStrategyLimit(String uriString, LimitedConnectionStrategy strategy, int expectedLimit, Proxy proxy) {
        Http1ClientRequest request = DEFAULT_CLIENT.get().uri(uriString);
        Http1ClientConfig clientConfig = DEFAULT_CLIENT.prototype();
        UriInfo uri = request.resolvedUri();
        ConnectionKey connectionKey = new ConnectionKey(uri.scheme(),
                                                        uri.host(),
                                                        uri.port(),
                                                        clientConfig.readTimeout().orElse(Duration.ZERO),
                                                        clientConfig.tls(),
                                                        clientConfig.dnsResolver(),
                                                        clientConfig.dnsAddressLookup(),
                                                        proxy == null ? clientConfig.proxy() : proxy);
        List<TcpClientConnection> connections = new ArrayList<>();
        for (int i = 1; i <= expectedLimit + 1; i++) {
            TcpClientConnection connection = strategy.createConnection(connectionKey,
                                                                       DEFAULT_CLIENT,
                                                                       tcpClientConnection -> false,
                                                                       //Keep-alive true ensures Limit not to wait
                                                                       true);
            if (i <= expectedLimit) {
                assertThat(connection, notNullValue());
                connections.add(connection);
            } else {
                assertThat(connection, nullValue());
            }
        }
        return connections;
    }

}
