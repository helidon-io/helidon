/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.helidon.common.concurrency.limits.FixedLimit;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.concurrency.limits.LimitAlgorithm;
import io.helidon.common.tls.Tls;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HeaderValues;
import io.helidon.http.WritableHeaders;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.TcpClientConnection;
import io.helidon.webclient.spi.ClientConnectionCache;

import static java.lang.System.Logger.Level.DEBUG;

/**
 * Cache of HTTP/1.1 connections for keep alive.
 */
class Http1ConnectionCache extends ClientConnectionCache {
    private static final System.Logger LOGGER = System.getLogger(Http1ConnectionCache.class.getName());
    private static final Tls NO_TLS = Tls.builder().enabled(false).build();
    private static final String HTTPS = "https";
    private static final ConnectionCreationStrategy UNLIMITED_STRATEGY = new UnlimitedConnectionStrategy();
    private static final List<String> ALPN_ID = List.of(Http1Client.PROTOCOL_ID);
    private static final Duration QUEUE_TIMEOUT = Duration.ofMillis(10);
    private static final Http1ConnectionCacheConfig EMPTY_CONFIG = Http1ConnectionCacheConfig.create();
    private static final Http1ConnectionCache SHARED = new Http1ConnectionCache(true,
                                                                                Http1ClientImpl.globalConfig()
                                                                                        .connectionCacheConfig());
    private final ConnectionCreationStrategy connectionCreationStrategy;
    private final Duration keepAliveWaiting;
    private final Map<ConnectionKey, LinkedBlockingDeque<TcpClientConnection>> cache = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    protected Http1ConnectionCache(boolean shared, Http1ConnectionCacheConfig cacheConfig) {
        super(shared);
        if (cacheConfig.enableConnectionLimits()) {
            if (cacheConfig.connectionLimit().isPresent()
                    || cacheConfig.connectionPerHostLimit().isPresent()
                    || !cacheConfig.hostLimits().isEmpty()
                    || !cacheConfig.proxyLimits().isEmpty()) {
                connectionCreationStrategy = new LimitedConnectionStrategy(cacheConfig);
            } else {
                connectionCreationStrategy = UNLIMITED_STRATEGY;
            }
        } else {
            connectionCreationStrategy = UNLIMITED_STRATEGY;
        }
        keepAliveWaiting = cacheConfig.keepAliveTimeout();
    }

    private Http1ConnectionCache(Http1ConnectionCacheConfig clientConfig) {
        this(false, clientConfig);
    }

    static Http1ConnectionCache shared() {
        return SHARED;
    }

    static Http1ConnectionCache create() {
        return new Http1ConnectionCache(EMPTY_CONFIG);
    }

    static Http1ConnectionCache create(Http1ConnectionCacheConfig cacheConfig) {
        return new Http1ConnectionCache(cacheConfig);
    }

    ClientConnection connection(Http1ClientImpl http1Client,
                                Duration requestReadTimeout,
                                Tls tls,
                                Proxy proxy,
                                ClientUri uri,
                                ClientRequestHeaders headers,
                                boolean defaultKeepAlive) {
        boolean keepAlive = handleKeepAlive(defaultKeepAlive, headers);
        Tls effectiveTls = HTTPS.equals(uri.scheme()) ? tls : NO_TLS;
        if (keepAlive) {
            return keepAliveConnection(http1Client, requestReadTimeout, effectiveTls, uri, proxy);
        } else {
            return oneOffConnection(http1Client, effectiveTls, uri, proxy);
        }
    }

    @Override
    public void closeResource() {
        if (closed.getAndSet(true)) {
            return;
        }
        cache.values().stream()
                .flatMap(Collection::stream)
                .forEach(TcpClientConnection::closeResource);
    }

    private boolean handleKeepAlive(boolean defaultKeepAlive, WritableHeaders<?> headers) {
        if (headers.contains(HeaderValues.CONNECTION_CLOSE)) {
            return false;
        }
        if (defaultKeepAlive) {
            headers.setIfAbsent(HeaderValues.CONNECTION_KEEP_ALIVE);
            return true;
        }
        if (headers.contains(HeaderValues.CONNECTION_KEEP_ALIVE)) {
            return true;
        }
        headers.set(HeaderValues.CONNECTION_CLOSE);
        return false;
    }

    private ClientConnection keepAliveConnection(Http1ClientImpl http1Client,
                                                 Duration requestReadTimeout,
                                                 Tls tls,
                                                 ClientUri uri,
                                                 Proxy proxy) {

        if (closed.get()) {
            throw new IllegalStateException("Connection cache is closed");
        }

        Http1ClientConfig clientConfig = http1Client.clientConfig();

        ConnectionKey connectionKey = new ConnectionKey(uri.scheme(),
                                                        uri.host(),
                                                        uri.port(),
                                                        clientConfig.readTimeout().orElse(requestReadTimeout),
                                                        tls,
                                                        clientConfig.dnsResolver(),
                                                        clientConfig.dnsAddressLookup(),
                                                        proxy);

        LinkedBlockingDeque<TcpClientConnection> connectionQueue =
                cache.computeIfAbsent(connectionKey,
                                      it -> new LinkedBlockingDeque<>(clientConfig.connectionCacheSize()));

        TcpClientConnection connection;
        while ((connection = connectionQueue.poll()) != null && !connection.isConnected()) {
        }

        if (connection == null) {
            connection = connectionCreationStrategy.createConnection(connectionKey,
                                                                     http1Client,
                                                                     conn -> finishRequest(connectionQueue, conn),
                                                                     true);
            if (connection == null) {
                try {
                    while ((connection = connectionQueue.poll(keepAliveWaiting.toMillis(), TimeUnit.MILLISECONDS)) != null
                            && !connection.isConnected()) {
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if (connection == null) {
                    throw new IllegalStateException("Could not make a new HTTP connection. "
                                                            + "Maximum number of connections reached.");
                } else {
                    if (LOGGER.isLoggable(DEBUG)) {
                        LOGGER.log(DEBUG, String.format("[%s] client connection obtained %s",
                                                        connection.channelId(),
                                                        Thread.currentThread().getName()));
                    }
                }
            } else {
                connection.connect();
            }
        } else {
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, String.format("[%s] client connection obtained %s",
                                                connection.channelId(),
                                                Thread.currentThread().getName()));
            }
        }
        return connection;
    }

    private ClientConnection oneOffConnection(Http1ClientImpl http1Client,
                                              Tls tls,
                                              ClientUri uri,
                                              Proxy proxy) {
        Http1ClientConfig clientConfig = http1Client.clientConfig();
        ConnectionKey connectionKey = new ConnectionKey(uri.scheme(),
                                                        uri.host(),
                                                        uri.port(),
                                                        clientConfig.readTimeout().orElse(Duration.ZERO),
                                                        tls,
                                                        clientConfig.dnsResolver(),
                                                        clientConfig.dnsAddressLookup(),
                                                        proxy);

        TcpClientConnection connection = connectionCreationStrategy.createConnection(connectionKey,
                                                                                     http1Client,
                                                                                     conn -> false,
                                                                                     false);

        if (connection == null) {
            throw new IllegalStateException("Could not make a new HTTP connection. Maximum number of connections reached.");
        }
        return connection.connect();
    }

    private boolean finishRequest(LinkedBlockingDeque<TcpClientConnection> connectionQueue, TcpClientConnection conn) {
        if (conn.isConnected()) {
            try {
                //Connection needs to be marked as idle here.
                //This prevents race condition where another thread takes it out of the connection before setting it to
                //idle state.
                conn.helidonSocket().idle(); // mark it as idle to stay blocked at read for closed conn detection
                if (connectionQueue.offer(conn, QUEUE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    if (LOGGER.isLoggable(DEBUG)) {
                        LOGGER.log(DEBUG, "[%s] client connection returned %s",
                                   conn.channelId(),
                                   Thread.currentThread().getName());
                    }
                    return true;
                } else {
                    if (LOGGER.isLoggable(DEBUG)) {
                        LOGGER.log(DEBUG, "[%s] Unable to return client connection because queue is full %s",
                                   conn.channelId(),
                                   Thread.currentThread().getName());
                    }
                }
            } catch (InterruptedException e) {
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG, "[%s] Unable to return client connection due to '%s' %s",
                               conn.channelId(),
                               e.getMessage(),
                               Thread.currentThread().getName());
                }
            }
        }
        return false;
    }

    ConnectionCreationStrategy strategy() {
        return connectionCreationStrategy;
    }

    sealed interface ConnectionCreationStrategy permits UnlimitedConnectionStrategy, LimitedConnectionStrategy {

        TcpClientConnection createConnection(ConnectionKey connectionKey,
                                             Http1ClientImpl http1Client,
                                             Function<TcpClientConnection, Boolean> releaseFunction,
                                             boolean keepAlive);

    }

    static final class UnlimitedConnectionStrategy implements ConnectionCreationStrategy {

        UnlimitedConnectionStrategy() {
        }

        @Override
        public TcpClientConnection createConnection(ConnectionKey connectionKey,
                                                    Http1ClientImpl http1Client,
                                                    Function<TcpClientConnection, Boolean> releaseFunction,
                                                    boolean keepAlive) {
            return TcpClientConnection.create(http1Client.webClient(),
                                              connectionKey,
                                              ALPN_ID,
                                              releaseFunction,
                                              conn -> {});
        }

    }

    static final class LimitedConnectionStrategy implements ConnectionCreationStrategy {

        private static final Limit NOOP = FixedLimit.create();

        private final Lock hostsConnectionLimitLock = new ReentrantLock();
        private final Limit connectionLimit;
        private final Limit nonProxyConnectionLimit;
        private final Limit connectionPerHostLimit;
        private final Map<String, Http1ProxyLimitConfig> proxyConfigs;
        private final Map<String, Limit> proxyConnectionLimits;
        private final Map<String, Limit> connectionLimitsPerHost = new HashMap<>();

        LimitedConnectionStrategy(Http1ConnectionCacheConfig cacheConfig) {
            connectionLimit = cacheConfig.connectionLimit().orElse(NOOP).copy();
            nonProxyConnectionLimit = cacheConfig.nonProxyConnectionLimit().orElse(NOOP).copy();
            connectionPerHostLimit = cacheConfig.connectionPerHostLimit().orElse(NOOP).copy();
            for (Http1HostLimitConfig hostLimit : cacheConfig.hostLimits()) {
                String key = hostLimit.host();
                connectionLimitsPerHost.put(key, hostLimit.limit().copy());
            }
            Map<String, Limit> proxyConnectionLimits = new HashMap<>();
            for (Http1ProxyLimitConfig proxyLimit : cacheConfig.proxyLimits()) {
                proxyLimit.connectionLimit().ifPresent(it -> proxyConnectionLimits.put(proxyLimit.authority(), it.copy()));
                for (Http1HostLimitConfig hostLimit : proxyLimit.hostLimits()) {
                    String key = hostAndProxyKey(hostLimit.host(), proxyLimit.authority());
                    connectionLimitsPerHost.put(key, hostLimit.limit().copy());
                }
            }
            this.proxyConnectionLimits = Map.copyOf(proxyConnectionLimits);
            this.proxyConfigs = cacheConfig.proxyLimits().stream().collect(Collectors.toMap(Http1ProxyLimitConfig::authority,
                                                                                            Function.identity()));
        }

        private static String hostAndProxyKey(String host, String proxyAuthority) {
            return host + "|" + proxyAuthority;
        }

        @Override
        public TcpClientConnection createConnection(ConnectionKey connectionKey,
                                                    Http1ClientImpl http1Client,
                                                    Function<TcpClientConnection, Boolean> releaseFunction,
                                                    boolean keepAlive) {
            //Maximum connections was not reached
            //New connection should be created
            Optional<LimitAlgorithm.Token> maxConnectionToken = connectionLimit.tryAcquire(!keepAlive);
            if (maxConnectionToken.isPresent()) {
                //Maximum connections was not reached
                return checkProxyConnectionLimits(maxConnectionToken.get(),
                                                  connectionKey,
                                                  http1Client,
                                                  releaseFunction,
                                                  keepAlive);
            }
            return null;
        }

        private TcpClientConnection checkProxyConnectionLimits(LimitAlgorithm.Token maxConnectionToken,
                                                               ConnectionKey connectionKey,
                                                               Http1ClientImpl http1Client,
                                                               Function<TcpClientConnection, Boolean> releaseFunction,
                                                               boolean keepAlive) {
            Proxy proxy = connectionKey.proxy();
            Optional<LimitAlgorithm.Token> maxProxyConnectionToken;
            String proxyIdent;
            if (proxy.type() == Proxy.ProxyType.NONE) {
                maxProxyConnectionToken = nonProxyConnectionLimit.tryAcquire(!keepAlive);
                proxyIdent = "";
            } else if (proxy.type() == Proxy.ProxyType.SYSTEM) {
                String scheme = connectionKey.tls().enabled() ? "https" : "http";
                ProxySelector proxySelector = ProxySelector.getDefault();
                if (proxySelector == null) {
                    maxProxyConnectionToken = nonProxyConnectionLimit.tryAcquire(!keepAlive);
                    proxyIdent = "";
                } else {
                    List<java.net.Proxy> proxies = proxySelector
                            .select(URI.create(scheme + "://" + connectionKey.host() + ":" + connectionKey.port()));
                    if (proxies.isEmpty()) {
                        maxProxyConnectionToken = nonProxyConnectionLimit.tryAcquire(!keepAlive);
                        proxyIdent = "";
                    } else {
                        java.net.Proxy jnProxy = proxies.getFirst();
                        if (jnProxy.type() == java.net.Proxy.Type.DIRECT) {
                            maxProxyConnectionToken = nonProxyConnectionLimit.tryAcquire(!keepAlive);
                            proxyIdent = "";
                        } else {
                            SocketAddress proxyAddress = jnProxy.address();
                            if (proxyAddress instanceof InetSocketAddress inetSocketAddress) {
                                proxyIdent = inetSocketAddress.getHostName() + ":" + inetSocketAddress.getPort();
                            } else {
                                proxyIdent = proxyAddress.toString();
                            }
                            Limit proxyConnectionLimit = proxyConnectionLimits.getOrDefault(proxyIdent, NOOP);
                            maxProxyConnectionToken = proxyConnectionLimit.tryAcquire(!keepAlive);
                        }
                    }
                }
            } else {
                proxyIdent = proxy.host() + ":" + proxy.port();
                Limit proxyConnectionLimit = proxyConnectionLimits.getOrDefault(proxyIdent, NOOP);
                maxProxyConnectionToken = proxyConnectionLimit.tryAcquire(!keepAlive);
            }
            if (maxProxyConnectionToken.isPresent()) {
                //Maximum proxy/non-proxy connections was not reached
                return checkHostLimit(maxConnectionToken,
                                      maxProxyConnectionToken.get(),
                                      connectionKey,
                                      http1Client,
                                      releaseFunction,
                                      keepAlive,
                                      proxyIdent);
            } else {
                maxConnectionToken.ignore();
            }
            return null;
        }

        private TcpClientConnection checkHostLimit(LimitAlgorithm.Token maxConnectionToken,
                                                   LimitAlgorithm.Token maxProxyConnectionToken,
                                                   ConnectionKey connectionKey,
                                                   Http1ClientImpl http1Client,
                                                   Function<TcpClientConnection, Boolean> releaseFunction,
                                                   boolean keepAlive,
                                                   String proxyIdent) {
            String hostKey = connectionKey.host();
            if (!proxyIdent.isEmpty()) {
                hostKey = hostAndProxyKey(hostKey, proxyIdent);
            }
            Limit hostLimit;
            try {
                hostsConnectionLimitLock.lock();
                hostLimit = connectionLimitsPerHost.computeIfAbsent(hostKey,
                                                        key -> Optional.ofNullable(proxyConfigs.get(proxyIdent))
                                                                .flatMap(Http1ProxyLimitConfigBlueprint::connectionPerHostLimit)
                                                                .orElse(connectionPerHostLimit)
                                                                .copy());
            } finally {
                hostsConnectionLimitLock.unlock();
            }
            Optional<LimitAlgorithm.Token> maxConnectionPerRouteLimitToken = hostLimit.tryAcquire(!keepAlive);
            if (maxConnectionPerRouteLimitToken.isPresent()) {
                //Maximum host connections was not reached
                //Create new connection
                return TcpClientConnection.create(http1Client.webClient(),
                                                  connectionKey,
                                                  ALPN_ID,
                                                  releaseFunction,
                                                  conn -> {
                                                      //We need to free all the tokens when this connection is closed.
                                                      maxConnectionToken.success();
                                                      maxProxyConnectionToken.success();
                                                      maxConnectionPerRouteLimitToken.get().success();
                                                  });
            } else {
                maxConnectionToken.ignore();
                maxProxyConnectionToken.ignore();
            }
            return null;
        }

        //Getters are here only for testing purposes
        Limit maxConnectionLimit() {
            return connectionLimit;
        }

        Map<String, Limit> connectionLimitsPerHost() {
            return Map.copyOf(connectionLimitsPerHost);
        }
    }

}
