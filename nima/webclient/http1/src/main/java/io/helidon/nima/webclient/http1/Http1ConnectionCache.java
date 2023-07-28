/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.webclient.http1;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.http.Http;
import io.helidon.common.http.WritableHeaders;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.webclient.api.ClientConnection;
import io.helidon.nima.webclient.api.ClientUri;
import io.helidon.nima.webclient.api.ConnectionKey;
import io.helidon.nima.webclient.api.HttpClientConfig;
import io.helidon.nima.webclient.api.Proxy;
import io.helidon.nima.webclient.api.TcpClientConnection;
import io.helidon.nima.webclient.api.WebClient;

import static java.lang.System.Logger.Level.DEBUG;

/**
 * Cache of HTTP/1.1 connections for keep alive.
 */
class Http1ConnectionCache {
    private static final System.Logger LOGGER = System.getLogger(Http1ConnectionCache.class.getName());
    private static final Tls NO_TLS = Tls.builder().enabled(false).build();
    private static final String HTTPS = "https";
    private static final Map<ConnectionKey, LinkedBlockingDeque<TcpClientConnection>> CHANNEL_CACHE = new ConcurrentHashMap<>();
    private static final List<String> ALPN_ID = List.of(Http1Client.PROTOCOL_ID);
    private static final Duration QUEUE_TIMEOUT = Duration.ofMillis(10);

    private Http1ConnectionCache() {
    }

    static ClientConnection connection(WebClient webClient,
                                       HttpClientConfig clientConfig,
                                       Tls tls,
                                       Proxy proxy,
                                       ClientUri uri,
                                       ClientRequestHeaders headers,
                                       boolean defaultKeepAlive) {
        boolean keepAlive = handleKeepAlive(defaultKeepAlive, headers);
        Tls effectiveTls = HTTPS.equals(uri.scheme()) ? tls : NO_TLS;
        if (keepAlive) {
            return keepAliveConnection(webClient, clientConfig, effectiveTls, uri, proxy);
        } else {
            return oneOffConnection(webClient, clientConfig, effectiveTls, uri, proxy);
        }
    }

    private static boolean handleKeepAlive(boolean defaultKeepAlive, WritableHeaders<?> headers) {
        if (headers.contains(Http.HeaderValues.CONNECTION_CLOSE)) {
            return false;
        }
        if (defaultKeepAlive) {
            headers.setIfAbsent(Http.HeaderValues.CONNECTION_KEEP_ALIVE);
            return true;
        }
        if (headers.contains(Http.HeaderValues.CONNECTION_KEEP_ALIVE)) {
            return true;
        }
        headers.set(Http.HeaderValues.CONNECTION_CLOSE);
        return false;
    }

    private static ClientConnection keepAliveConnection(WebClient webClient,
                                                        HttpClientConfig clientConfig,
                                                        Tls tls,
                                                        ClientUri uri,
                                                        Proxy proxy) {
        ConnectionKey connectionKey = new ConnectionKey(uri.scheme(),
                                                        uri.host(),
                                                        uri.port(),
                                                        tls,
                                                        clientConfig.dnsResolver(),
                                                        clientConfig.dnsAddressLookup(),
                                                        proxy);

        var connectionQueue = CHANNEL_CACHE.computeIfAbsent(connectionKey,
                                                            it -> new LinkedBlockingDeque<>(clientConfig.connectionCacheSize()));

        TcpClientConnection connection;
        while ((connection = connectionQueue.poll()) != null && !connection.isConnected()) {
        }

        if (connection == null) {
            connection = TcpClientConnection.create(webClient,
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

    private static ClientConnection oneOffConnection(WebClient webClient, HttpClientConfig clientConfig,
                                                     Tls tls,
                                                     ClientUri uri,
                                                     Proxy proxy) {
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

    private static boolean finishRequest(LinkedBlockingDeque<TcpClientConnection> connectionQueue, TcpClientConnection conn) {
        if (conn.isConnected()) {
            try {
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
}
