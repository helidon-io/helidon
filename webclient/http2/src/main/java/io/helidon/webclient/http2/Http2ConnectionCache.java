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

package io.helidon.webclient.http2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.LruCache;
import io.helidon.webclient.api.ClientConnectionTarget;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.spi.ClientConnectionCache;

/**
 * A cache of HTTP2 connections.
 */
public final class Http2ConnectionCache extends ClientConnectionCache {
    private static final int MAX_TARGETS = 1_000;
    private static final Http2ConnectionCache SHARED = new Http2ConnectionCache(true);
    private final LruCache<ConnectionKey, Boolean> http2Supported = LruCache.create(1000);
    private final Map<ClientConnectionTarget, Http2ClientConnectionHandler> cache =
            new LinkedHashMap<>(16, 0.75F, true);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ReentrantLock cacheLock = new ReentrantLock();

    private Http2ConnectionCache(boolean shared) {
        super(shared);
    }

    /**
     * Returns a reference to the shared connection cache.
     *
     * @return shared connection cache
     */
    public static Http2ConnectionCache shared() {
        return SHARED;
    }

    /**
     * Creates a fresh connection cache.
     *
     * @return new connection cache
     */
    public static Http2ConnectionCache create() {
        return new Http2ConnectionCache(false);
    }

    @Override
    protected void evict() {
        List<Http2ClientConnectionHandler> handlers;
        cacheLock.lock();
        try {
            handlers = List.copyOf(cache.values());
            cache.clear();
            http2Supported.clear();
        } finally {
            cacheLock.unlock();
        }
        handlers.forEach(Http2ClientConnectionHandler::close);
    }

    @Override
    public void closeResource() {
        if (!closed.getAndSet(true)) {
            evict();
        }
    }

    boolean supports(ConnectionKey ck) {
        return http2Supported.get(ck).isPresent();
    }

    void remove(ClientConnectionTarget connectionTarget) {
        if (!closed.get()) {
            Http2ClientConnectionHandler handler;
            cacheLock.lock();
            try {
                handler = cache.remove(connectionTarget);
            } finally {
                cacheLock.unlock();
            }
            if (handler != null) {
                handler.close();
            }
            http2Supported.remove(connectionTarget.connectionKey());
        }
    }

    Http2ConnectionAttemptResult newStream(Http2ClientImpl http2Client,
                                           ClientConnectionTarget connectionTarget,
                                           Http2ClientRequestImpl request,
                                           ClientUri initialUri,
                                           WebClientServiceRequest serviceRequest,
                                           Http1FallbackHandler http1FallbackHandler) {

        if (closed.get()) {
            throw new IllegalStateException("Connection cache is closed");
        }
        if (request.connection().isPresent() && !Http2ClientConnectionHandler.ownsExplicitConnection(request)) {
            return new Http2ClientConnectionHandler().newStream(http2Client,
                                                               connectionTarget,
                                                               request,
                                                               initialUri,
                                                               serviceRequest,
                                                               http1FallbackHandler);
        }

        List<Http2ClientConnectionHandler> stale = null;
        Http2ClientConnectionHandler handler;
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
            handler = cache.computeIfAbsent(connectionTarget, _ -> new Http2ClientConnectionHandler());
            if (cache.size() > MAX_TARGETS) {
                var evictionIterator = cache.entrySet().iterator();
                var evicted = evictionIterator.next();
                evictionIterator.remove();
                if (stale == null) {
                    stale = new ArrayList<>();
                }
                stale.add(evicted.getValue());
            }
            if (!handler.acquire()) {
                throw new IllegalStateException("HTTP/2 connection target was retired during acquisition");
            }
        } finally {
            cacheLock.unlock();
        }
        if (stale != null) {
            stale.forEach(Http2ClientConnectionHandler::retire);
        }

        Http2ConnectionAttemptResult result;
        try {
            result = handler.newStream(http2Client,
                                       connectionTarget,
                                       request,
                                       initialUri,
                                       serviceRequest,
                                       http1FallbackHandler);
        } finally {
            handler.release();
        }
        if (result.result() == Http2ConnectionAttemptResult.Result.HTTP_2
                && (request.connection().isEmpty()
                        || Http2ClientConnectionHandler.ownsExplicitConnection(request))) {
            http2Supported.put(connectionTarget.connectionKey(), true);
        }
        return result;
    }

}
