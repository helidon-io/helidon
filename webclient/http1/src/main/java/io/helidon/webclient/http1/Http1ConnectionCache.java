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

package io.helidon.webclient.http1;

import java.net.UnixDomainSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.tls.Tls;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HeaderValues;
import io.helidon.http.WritableHeaders;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientConnectionTarget;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.FullClientRequest;
import io.helidon.webclient.api.SniConfig;
import io.helidon.webclient.api.TcpClientConnection;
import io.helidon.webclient.api.UnixDomainSocketClientConnection;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.spi.ClientConnectionCache;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;

/**
 * Cache of HTTP/1.1 connections for keep alive.
 */
class Http1ConnectionCache extends ClientConnectionCache {
    private static final System.Logger LOGGER = System.getLogger(Http1ConnectionCache.class.getName());
    private static final int MAX_TARGETS = 1_000;
    private static final Tls NO_TLS = Tls.builder().enabled(false).build();
    private static final String HTTPS = "https";
    private static final Http1ConnectionCache SHARED = new Http1ConnectionCache(true);
    private static final List<String> ALPN_ID = List.of(Http1Client.PROTOCOL_ID);

    private final Map<ClientConnectionTarget, ConnectionPool> cache = new LinkedHashMap<>(16, 0.75F, true);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ReentrantLock cacheLock = new ReentrantLock();

    protected Http1ConnectionCache(boolean shared) {
        super(shared);
    }

    static Http1ConnectionCache shared() {
        return SHARED;
    }

    static Http1ConnectionCache create() {
        return new Http1ConnectionCache(false);
    }

    ClientConnection connection(Http1ClientImpl http1Client,
                                ClientConnectionTarget connectionTarget,
                                ClientRequestHeaders headers,
                                boolean defaultKeepAlive,
                                UnixDomainSocketAddress address) {

        if (!connectionTarget.transportAddress().filter(address::equals).isPresent()) {
            throw new IllegalArgumentException("UNIX domain socket address does not match the connection target");
        }

        boolean keepAlive = handleKeepAlive(defaultKeepAlive, headers);
        if (keepAlive) {
            return keepAliveUnixDomainConnection(http1Client, connectionTarget);
        } else {
            observeTls(connectionTarget.connectionKey().tls());
            return UnixDomainSocketClientConnection.create(http1Client.webClient(),
                                                           connectionTarget,
                                                           ALPN_ID,
                                                           it -> false,
                                                           it -> {
                                                           })
                    .connect();
        }
    }

    ClientConnection connection(Http1ClientImpl http1Client,
                                ClientConnectionTarget connectionTarget,
                                ClientRequestHeaders headers,
                                boolean defaultKeepAlive) {
        boolean keepAlive = handleKeepAlive(defaultKeepAlive, headers);
        if (keepAlive) {
            return keepAliveConnection(http1Client, connectionTarget);
        } else {
            observeTls(connectionTarget.connectionKey().tls());
            return oneOffConnection(http1Client, connectionTarget);
        }
    }

    ClientConnection connection(Http1ClientImpl http1Client,
                                FullClientRequest<?> request,
                                ClientUri uri,
                                ClientRequestHeaders headers,
                                boolean defaultKeepAlive) {
        ConnectionKey connectionKey = connectionKey(request, uri, headers, http1Client.clientConfig());
        ClientConnectionTarget connectionTarget = request.selectedProxyRoute()
                .map(route -> ClientConnectionTarget.create(connectionKey, uri, headers, route))
                .orElseGet(() -> ClientConnectionTarget.create(connectionKey, uri, headers));
        return connection(http1Client, connectionTarget, headers, defaultKeepAlive);
    }

    @Override
    public void evict() {
        List<ConnectionPool> pools;
        cacheLock.lock();
        try {
            pools = List.copyOf(cache.values());
            cache.clear();
        } finally {
            cacheLock.unlock();
        }
        pools.forEach(ConnectionPool::retire);
    }

    @Override
    public void closeResource() {
        if (closed.getAndSet(true)) {
            return;
        }
        evict();
    }

    static ConnectionKey unixConnectionKey(FullClientRequest<?> request,
                                           ClientUri uri,
                                           ClientRequestHeaders headers,
                                           UnixDomainSocketAddress address,
                                           Http1ClientConfig clientConfig) {
        Tls tls = HTTPS.equals(uri.scheme()) ? request.tls() : NO_TLS;
        SniConfig sni = effectiveSni(request, clientConfig);
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

    static ConnectionKey connectionKey(FullClientRequest<?> request,
                                       ClientUri uri,
                                       ClientRequestHeaders headers,
                                       Http1ClientConfig clientConfig) {
        Tls tls = HTTPS.equals(uri.scheme()) ? request.tls() : NO_TLS;
        return connectionKey(request, tls, uri, headers, clientConfig);
    }

    private boolean handleKeepAlive(boolean defaultKeepAlive, WritableHeaders<?> headers) {
        if (headers.containsToken(HeaderValues.CONNECTION_CLOSE)) {
            return false;
        }
        if (defaultKeepAlive) {
            headers.setIfAbsent(HeaderValues.CONNECTION_KEEP_ALIVE);
            return true;
        }
        if (headers.containsToken(HeaderValues.CONNECTION_KEEP_ALIVE)) {
            return true;
        }
        headers.set(HeaderValues.CONNECTION_CLOSE);
        return false;
    }

    private ClientConnection keepAliveUnixDomainConnection(Http1ClientImpl http1Client,
                                                           ClientConnectionTarget connectionTarget) {
        if (closed.get()) {
            throw new IllegalStateException("Connection cache is closed");
        }

        Http1ClientConfig clientConfig = http1Client.clientConfig();

        ConnectionPool connectionPool = connectionPool(connectionTarget, clientConfig.connectionCacheSize());

        ClientConnection connection;
        while ((connection = connectionPool.poll()) != null && !connection.isConnected()) {
            connection.closeResource();
        }

        if (connection == null) {
            connection = UnixDomainSocketClientConnection.create(http1Client.webClient(),
                                                                 connectionTarget,
                                                                 ALPN_ID,
                                                                 connectionPool::release,
                                                                 conn -> {
                                                                 })
                    .connect();
        } else {
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, String.format("[%s] UNIX socket client connection obtained %s",
                                                connection.channelId(),
                                                Thread.currentThread().getName()));
            }
        }
        return connection;
    }

    private ClientConnection keepAliveConnection(Http1ClientImpl http1Client,
                                                 ClientConnectionTarget connectionTarget) {

        if (closed.get()) {
            throw new IllegalStateException("Connection cache is closed");
        }

        Http1ClientConfig clientConfig = http1Client.clientConfig();
        ConnectionPool connectionPool = connectionPool(connectionTarget, clientConfig.connectionCacheSize());

        ClientConnection connection;
        while ((connection = connectionPool.poll()) != null && !connection.isConnected()) {
            connection.closeResource();
        }

        if (connection == null) {
            connection = TcpClientConnection.create(http1Client.webClient(),
                                                    connectionTarget,
                                                    ALPN_ID,
                                                    connectionPool::release,
                                                    conn -> {
                                                    })
                    .connect();
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
                                              ClientConnectionTarget connectionTarget) {

        WebClient webClient = http1Client.webClient();

        return TcpClientConnection.create(webClient,
                                          connectionTarget,
                                          ALPN_ID,
                                          conn -> false, // always close connection
                                          conn -> {
                                          })

                .connect();
    }

    private static ConnectionKey connectionKey(FullClientRequest<?> request,
                                               Tls tls,
                                               ClientUri uri,
                                               ClientRequestHeaders headers,
                                               Http1ClientConfig clientConfig) {
        SniConfig sni = effectiveSni(request, clientConfig);
        if (sni == null) {
            return ConnectionKey.create(uri,
                                        tls,
                                        clientConfig.dnsResolver(),
                                        clientConfig.dnsAddressLookup(),
                                        request.proxy());
        }
        return ConnectionKey.create(uri,
                                    sni,
                                    tls,
                                    clientConfig.dnsResolver(),
                                    clientConfig.dnsAddressLookup(),
                                    request.proxy(),
                                    headers);
    }

    private static SniConfig effectiveSni(FullClientRequest<?> request, Http1ClientConfig clientConfig) {
        return request.sni().or(clientConfig::sni).orElse(null);
    }

    private ConnectionPool connectionPool(ClientConnectionTarget connectionTarget, int capacity) {
        List<ConnectionPool> stale = null;
        ConnectionPool connectionPool;
        cacheLock.lock();
        try {
            if (closed.get()) {
                throw new IllegalStateException("Connection cache is closed");
            }
            if (!connectionTarget.currentTlsGeneration()) {
                throw new IllegalStateException("TLS configuration was reloaded before connection-pool acquisition");
            }
            var iterator = cache.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                ClientConnectionTarget cachedTarget = entry.getKey();
                if (!cachedTarget.equals(connectionTarget)
                        && cachedTarget.connectionKey().tls() == connectionTarget.connectionKey().tls()
                        && !cachedTarget.currentTlsGeneration()) {
                    iterator.remove();
                    if (stale == null) {
                        stale = new ArrayList<>();
                    }
                    stale.add(entry.getValue());
                }
            }
            connectionPool = cache.computeIfAbsent(connectionTarget, _ -> new ConnectionPool(capacity));
            if (cache.size() > MAX_TARGETS) {
                var evictionIterator = cache.entrySet().iterator();
                if (stale == null) {
                    stale = new ArrayList<>();
                }
                stale.add(evictionIterator.next().getValue());
                evictionIterator.remove();
            }
        } finally {
            cacheLock.unlock();
        }
        if (stale != null) {
            stale.forEach(ConnectionPool::retire);
        }
        return connectionPool;
    }

    private void observeTls(Tls tls) {
        List<ConnectionPool> stale = new ArrayList<>();
        cacheLock.lock();
        try {
            var iterator = cache.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                ClientConnectionTarget target = entry.getKey();
                if (target.connectionKey().tls() == tls
                        && target.tlsGeneration() != tls.generation()) {
                    iterator.remove();
                    stale.add(entry.getValue());
                }
            }
        } finally {
            cacheLock.unlock();
        }
        stale.forEach(ConnectionPool::retire);
    }

    private static final class ConnectionPool {
        private final LinkedBlockingDeque<ClientConnection> connections;
        private final ReentrantLock lock = new ReentrantLock();
        private boolean retired;

        private ConnectionPool(int capacity) {
            connections = new LinkedBlockingDeque<>(capacity);
        }

        private ClientConnection poll() {
            lock.lock();
            try {
                return retired ? null : connections.poll();
            } finally {
                lock.unlock();
            }
        }

        private boolean release(ClientConnection connection) {
            if (!connection.isConnected()) {
                return false;
            }
            // This must happen before publishing the connection to another borrower.
            connection.helidonSocket().idle();
            lock.lock();
            try {
                if (retired || !connections.offer(connection)) {
                    return false;
                }
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG, String.format("[%s] client connection returned %s",
                                                    connection.channelId(),
                                                    Thread.currentThread().getName()));
                }
                return true;
            } finally {
                lock.unlock();
            }
        }

        private void retire() {
            List<ClientConnection> toClose;
            lock.lock();
            try {
                if (retired) {
                    return;
                }
                retired = true;
                toClose = new ArrayList<>(connections);
                connections.clear();
            } finally {
                lock.unlock();
            }
            for (ClientConnection connection : toClose) {
                try {
                    connection.closeResource();
                } catch (Throwable e) {
                    LOGGER.log(TRACE, "Failed to close a retired HTTP/1.1 connection.", e);
                }
            }
        }
    }
}
