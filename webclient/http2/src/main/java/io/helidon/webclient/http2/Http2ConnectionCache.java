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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
    private final ConcurrentMap<ClientConnectionTarget, Http2ClientConnectionHandler> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<ClientConnectionTarget.LookupKey, List<Http2ClientConnectionHandler>> lookupCache =
            new ConcurrentHashMap<>();
    // Mutated only while holding cacheLock.
    private final Map<ClientConnectionTarget, Http2ClientConnectionHandler> insertionOrder = new LinkedHashMap<>();
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
            handlers = List.copyOf(insertionOrder.values());
            cache.clear();
            lookupCache.clear();
            insertionOrder.clear();
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

    void markSupported(ConnectionKey connectionKey) {
        http2Supported.put(connectionKey, true);
    }

    void remove(ClientConnectionTarget connectionTarget, Http2ClientConnectionHandler expectedHandler) {
        cacheLock.lock();
        try {
            if (cache.remove(connectionTarget, expectedHandler)) {
                insertionOrder.remove(connectionTarget, expectedHandler);
                removeLookupHandler(connectionTarget, expectedHandler);
                removeSupportIfUnused(connectionTarget.connectionKey());
            }
        } finally {
            cacheLock.unlock();
        }
        // The failed stream still owns this handler even when a successor is now mapped.
        expectedHandler.close();
    }

    Http2ConnectionAttemptResult newStream(Http2ClientImpl http2Client,
                                           ClientConnectionTarget.LookupKey lookupKey,
                                           Http2ClientRequestImpl request,
                                           ClientUri initialUri,
                                           WebClientServiceRequest serviceRequest,
                                           Http1FallbackHandler http1FallbackHandler) {
        if (closed.get()) {
            throw new IllegalStateException("Connection cache is closed");
        }
        if (!lookupKey.currentTlsGeneration()) {
            throw new IllegalStateException("TLS configuration was reloaded before connection-pool acquisition");
        }

        List<Http2ClientConnectionHandler> handlers = lookupCache.get(lookupKey);
        if (handlers != null) {
            for (Http2ClientConnectionHandler handler : handlers) {
                if (!handler.acquire()) {
                    continue;
                }
                Http2ConnectionAttemptResult result;
                try {
                    result = handler.reuseStream(http2Client, request);
                } catch (RuntimeException | Error e) {
                    http1FallbackHandler.completeSentExceptionally(e);
                    throw e;
                } finally {
                    handler.release();
                }
                if (result != null) {
                    markSupported(result.connectionTarget().connectionKey());
                    return result;
                }
            }
        }

        return newStream(http2Client,
                         ClientConnectionTarget.create(lookupKey),
                         request,
                         initialUri,
                         serviceRequest,
                         http1FallbackHandler);
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
        boolean forwardHttp1 = connectionTarget.proxyRoute().forwardProxy() && !request.priorKnowledge();
        if (request.connection().isPresent()
                && (!Http2ClientConnectionHandler.ownsExplicitConnection(request) || forwardHttp1)) {
            return new Http2ClientConnectionHandler(http2Client.clientConfig().connectionCacheSize())
                    .newStream(http2Client,
                               connectionTarget,
                               request,
                               initialUri,
                               serviceRequest,
                               http1FallbackHandler);
        }
        if (forwardHttp1) {
            if (!Http2ClientConnectionHandler.http1FallbackAllowed(request)) {
                throw Http2ClientConnectionHandler.unsupportedHttp1Fallback(initialUri,
                                                                            request,
                                                                            http1FallbackHandler);
            }
            return Http2ClientConnectionHandler.http1(http2Client,
                                                       connectionTarget,
                                                       request,
                                                       initialUri,
                                                       serviceRequest,
                                                       http1FallbackHandler,
                                                       null);
        }

        List<Http2ClientConnectionHandler> retiredHandlers = null;
        Http2ClientConnectionHandler handler;
        while (true) {
            if (closed.get()) {
                throw new IllegalStateException("Connection cache is closed");
            }
            if (!connectionTarget.currentTlsGeneration()) {
                throw new IllegalStateException("TLS configuration was reloaded before connection-pool acquisition");
            }
            handler = cache.get(connectionTarget);
            if (handler != null) {
                if (handler.acquire()) {
                    break;
                }
                continue;
            }

            cacheLock.lock();
            try {
                if (closed.get()) {
                    throw new IllegalStateException("Connection cache is closed");
                }
                if (!connectionTarget.currentTlsGeneration()) {
                    throw new IllegalStateException("TLS configuration was reloaded before connection-pool acquisition");
                }
                handler = cache.get(connectionTarget);
                if (handler != null) {
                    if (handler.acquire()) {
                        break;
                    }
                    continue;
                }

                var iterator = insertionOrder.entrySet().iterator();
                while (iterator.hasNext()) {
                    var entry = iterator.next();
                    ClientConnectionTarget cachedTarget = entry.getKey();
                    if (cachedTarget.connectionKey().tls() == connectionTarget.connectionKey().tls()
                            && !cachedTarget.currentTlsGeneration()) {
                        iterator.remove();
                        if (cache.remove(cachedTarget, entry.getValue())) {
                            removeLookupHandler(cachedTarget, entry.getValue());
                            if (retiredHandlers == null) {
                                retiredHandlers = new ArrayList<>();
                            }
                            retiredHandlers.add(entry.getValue());
                        }
                    }
                }

                if (insertionOrder.size() >= MAX_TARGETS) {
                    var evictionIterator = insertionOrder.entrySet().iterator();
                    var evicted = evictionIterator.next();
                    evictionIterator.remove();
                    if (cache.remove(evicted.getKey(), evicted.getValue())) {
                        removeLookupHandler(evicted.getKey(), evicted.getValue());
                        if (retiredHandlers == null) {
                            retiredHandlers = new ArrayList<>();
                        }
                        retiredHandlers.add(evicted.getValue());
                    }
                }

                handler = new Http2ClientConnectionHandler(http2Client.clientConfig().connectionCacheSize());
                if (!handler.acquire()) {
                    throw new IllegalStateException("New HTTP/2 connection target could not be acquired");
                }
                insertionOrder.put(connectionTarget, handler);
                cache.put(connectionTarget, handler);
                if (connectionTarget.connectionKey().proxy().ipNoProxyConfigured()) {
                    ClientConnectionTarget.LookupKey lookupKey = connectionTarget.lookupKey();
                    List<Http2ClientConnectionHandler> handlers = lookupCache.get(lookupKey);
                    if (handlers == null) {
                        lookupCache.put(lookupKey, List.of(handler));
                    } else {
                        var updatedHandlers = new ArrayList<>(handlers);
                        updatedHandlers.add(handler);
                        lookupCache.put(lookupKey, List.copyOf(updatedHandlers));
                    }
                }
                break;
            } finally {
                cacheLock.unlock();
            }
        }
        if (retiredHandlers != null) {
            retiredHandlers.forEach(Http2ClientConnectionHandler::retire);
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
            markSupported(connectionTarget.connectionKey());
        }
        return result;
    }

    private void removeSupportIfUnused(ConnectionKey connectionKey) {
        for (ClientConnectionTarget cachedTarget : cache.keySet()) {
            if (connectionKey.equals(cachedTarget.connectionKey())) {
                return;
            }
        }
        http2Supported.remove(connectionKey);
    }

    private void removeLookupHandler(ClientConnectionTarget connectionTarget,
                                     Http2ClientConnectionHandler expectedHandler) {
        if (!connectionTarget.connectionKey().proxy().ipNoProxyConfigured()) {
            return;
        }
        lookupCache.computeIfPresent(connectionTarget.lookupKey(), (_, handlers) -> {
            var updatedHandlers = new ArrayList<Http2ClientConnectionHandler>(handlers.size());
            for (Http2ClientConnectionHandler handler : handlers) {
                if (handler != expectedHandler) {
                    updatedHandlers.add(handler);
                }
            }
            if (updatedHandlers.size() == handlers.size()) {
                return handlers;
            }
            return updatedHandlers.isEmpty() ? null : List.copyOf(updatedHandlers);
        });
    }

}
