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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.http.Http;
import io.helidon.common.http.WritableHeaders;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.webclient.ClientConnection;
import io.helidon.nima.webclient.Proxy;
import io.helidon.nima.webclient.UriHelper;

import static java.lang.System.Logger.Level.DEBUG;

/**
 * Cache of HTTP/1.1 connections for keep alive.
 */
class ConnectionCache {
    private static final System.Logger LOGGER = System.getLogger(ConnectionCache.class.getName());
    private static final String HTTPS = "https";
    private static final Map<KeepAliveKey, LinkedBlockingDeque<Http1ClientConnection>> CHANNEL_CACHE = new ConcurrentHashMap<>();

    private ConnectionCache() {
    }

    static ClientConnection connection(Http1ClientConfig clientConfig,
                                       Tls tls,
                                       Proxy proxy,
                                       UriHelper uri,
                                       ClientRequestHeaders headers) {
        boolean keepAlive = handleKeepAlive(clientConfig.defaultKeepAlive(), headers);
        Tls effectiveTls = HTTPS.equals(uri.scheme()) ? tls : null;
        if (keepAlive) {
            return keepAliveConnection(clientConfig, effectiveTls, uri, proxy);
        } else {
            return oneOffConnection(clientConfig, effectiveTls, uri, proxy);
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

    private static ClientConnection keepAliveConnection(Http1ClientConfig clientConfig,
                                                        Tls tls,
                                                        UriHelper uri,
                                                        Proxy proxy) {
        KeepAliveKey keepAliveKey = new KeepAliveKey(uri.scheme(),
                                                     uri.authority(),
                                                     tls,
                                                     clientConfig.socketOptions().connectTimeout(),
                                                     clientConfig.socketOptions().readTimeout(), proxy);

        var connectionQueue = CHANNEL_CACHE.computeIfAbsent(keepAliveKey,
                                                            it -> new LinkedBlockingDeque<>(clientConfig.connectionQueueSize()));

        Http1ClientConnection connection;
        while ((connection = connectionQueue.poll()) != null && !connection.isConnected()) {
        }

        if (connection == null) {
            connection = new Http1ClientConnection(clientConfig.socketOptions(),
                                                   connectionQueue,
                                                   new ConnectionKey(uri.scheme(),
                                                                     uri.host(),
                                                                     uri.port(),
                                                                     tls,
                                                                     clientConfig.dnsResolver(),
                                                                     clientConfig.dnsAddressLookup(),
                                                                     proxy))
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

    private static ClientConnection oneOffConnection(Http1ClientConfig clientConfig,
                                                     Tls tls,
                                                     UriHelper uri,
                                                     Proxy proxy) {
        return new Http1ClientConnection(clientConfig.socketOptions(), new ConnectionKey(uri.scheme(),
                                                                                         uri.host(),
                                                                                         uri.port(),
                                                                                         tls,
                                                                                         clientConfig.dnsResolver(),
                                                                                         clientConfig.dnsAddressLookup(),
                                                                                         proxy))
                .connect();
    }

    private record KeepAliveKey(String scheme, String authority, Tls tlsConfig, Duration connectTimeout,
            Duration readTimeout, Proxy proxy) {
    }
}
