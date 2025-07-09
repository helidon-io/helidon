/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.tls.Tls;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HeaderValues;
import io.helidon.http.WritableHeaders;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.TcpClientConnection;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.spi.ClientConnectionCache;

import static java.lang.System.Logger.Level.DEBUG;

/**
 * Cache of HTTP/1.1 connections for keep alive.
 */
class Http1ConnectionCache extends ClientConnectionCache {
    private static final System.Logger LOGGER = System.getLogger(Http1ConnectionCache.class.getName());
    private static final Tls NO_TLS = Tls.builder().enabled(false).build();
    private static final String HTTPS = "https";
    private static final Http1ConnectionCache SHARED = new Http1ConnectionCache(true);
    private static final List<String> ALPN_ID = List.of(Http1Client.PROTOCOL_ID);
    private static final Duration QUEUE_TIMEOUT = Duration.ofMillis(10);
    private final Map<ConnectionKey, LinkedBlockingDeque<TcpClientConnection>> cache = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

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
    public void evict() {
        cache.values().stream()
                .flatMap(Collection::stream)
                .forEach(TcpClientConnection::closeResource);
    }

    @Override
    public void closeResource() {
        if (closed.getAndSet(true)) {
            return;
        }
        evict();
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
            connection = TcpClientConnection.create(http1Client.webClient(),
                                                    connectionKey,
                                                    ALPN_ID,
                                                    conn -> finishRequest(connectionQueue, conn),
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
                                              Tls tls,
                                              ClientUri uri,
                                              Proxy proxy) {

        WebClient webClient = http1Client.webClient();
        Http1ClientConfig clientConfig = http1Client.clientConfig();

        return TcpClientConnection.create(webClient,
                                          new ConnectionKey(uri.scheme(),
                                                            uri.host(),
                                                            uri.port(),
                                                            tls,
                                                            clientConfig.dnsResolver(),
                                                            clientConfig.dnsAddressLookup(),
                                                            proxy),
                                          ALPN_ID,
                                          conn -> false, // always close connection
                                          conn -> {
                                          })

                .connect();
    }

    private boolean finishRequest(LinkedBlockingDeque<TcpClientConnection> connectionQueue, TcpClientConnection conn) {
        if (conn.isConnected()) {
            try {
                if (connectionQueue.offer(conn, QUEUE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    conn.helidonSocket().idle(); // mark it as idle to stay blocked at read for closed conn detection
                    if (LOGGER.isLoggable(DEBUG)) {
                        LOGGER.log(DEBUG, String.format("[%s] client connection returned %s",
                                                        conn.channelId(),
                                                        Thread.currentThread().getName()));
                    }
                    return true;
                } else {
                    if (LOGGER.isLoggable(DEBUG)) {
                        LOGGER.log(DEBUG, String.format("[%s] Unable to return client connection because queue is full %s",
                                                        conn.channelId(),
                                                        Thread.currentThread().getName()));
                    }
                }
            } catch (InterruptedException e) {
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG, String.format("[%s] Unable to return client connection due to '%s' %s",
                                                    conn.channelId(),
                                                    e.getMessage(),
                                                    Thread.currentThread().getName()));
                }
            }
        }

        return false;
    }
}
