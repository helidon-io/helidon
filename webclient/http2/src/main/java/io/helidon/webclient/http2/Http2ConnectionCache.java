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
import java.util.function.Predicate;

import io.helidon.common.LruCache;
import io.helidon.webclient.api.AltSvcHeader;
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
    private final Http2AltSvcCache altSvc;
    private final Predicate<Http2AltSvcCache.Selection> establishedAlternative = this::hasEstablishedAlternative;
    private final Predicate<Http2AltSvcCache.Generation> establishedAlternativeGeneration =
            this::hasEstablishedAlternative;

    private Http2ConnectionCache(boolean shared) {
        super(shared);
        this.altSvc = Http2AltSvcCache.create(this::retireAlternative);
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
    public void suspend() {
        altSvc.networkChanged();
        evict();
    }

    @Override
    public void closeResource() {
        if (!closed.getAndSet(true)) {
            altSvc.close();
            evict();
        }
    }

    boolean supports(ConnectionKey ck) {
        return http2Supported.get(ck).isPresent();
    }

    boolean mayContainAlternative(String host, boolean explicitConnection) {
        return !explicitConnection && altSvc.mayContain(host);
    }

    void markSupported(ConnectionKey connectionKey) {
        http2Supported.put(connectionKey, true);
    }

    void markSupported(ClientConnectionTarget connectionTarget) {
        Http2ClientConnectionHandler handler = cache.get(connectionTarget);
        markSupported(connectionTarget, handler);
    }

    private void markSupported(ClientConnectionTarget connectionTarget,
                               Http2ClientConnectionHandler expectedHandler) {
        Http2ClientConnectionHandler handler = cache.get(connectionTarget);
        if (handler == expectedHandler && handler != null) {
            handler.markDirectHttp2Supported();
            markSupported(connectionTarget.connectionKey());
        }
    }

    boolean alternativeAvailable(ClientConnectionTarget connectionTarget, boolean explicitConnection) {
        return altSvc.available(connectionTarget,
                                explicitConnection,
                                establishedAlternative,
                                establishedAlternativeGeneration);
    }

    Http2AltSvcCache.Candidate currentAlternative(ClientConnectionTarget.LookupKey lookupKey,
                                                  boolean explicitConnection) {
        return altSvc.selectRoute(lookupKey, explicitConnection, establishedAlternativeGeneration);
    }

    Http2AltSvcCache.Selection currentAlternative(ClientConnectionTarget connectionTarget,
                                                   boolean explicitConnection) {
        return altSvc.select(connectionTarget,
                             explicitConnection,
                             establishedAlternative,
                             establishedAlternativeGeneration);
    }

    void recordAlternative(ClientConnectionTarget connectionTarget,
                           AltSvcHeader header,
                           boolean secureOrigin,
                           boolean explicitConnection) {
        altSvc.record(connectionTarget, header, secureOrigin, explicitConnection);
    }

    void recordAlternativeFailure(Http2AltSvcCache.Selection selection) {
        altSvc.recordFailure(selection);
    }

    void recordAlternativeMisdirected(Http2AltSvcCache.Selection selection) {
        altSvc.recordMisdirected(selection);
    }

    void removeAlternative(Http2AltSvcCache.Selection selection) {
        retireAlternative(selection);
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
                    markSupported(result.connectionTarget(), result.handler());
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

    Http2ConnectionAttemptResult newAlternativeStream(Http2ClientImpl http2Client,
                                                      Http2AltSvcCache.Selection selection,
                                                      Http2ClientRequestImpl request,
                                                      Http1FallbackHandler http1FallbackHandler) {
        if (closed.get()) {
            throw new IllegalStateException("Connection cache is closed");
        }
        if (!altSvc.current(selection)) {
            throw new AlternativeConnectionException(
                    selection,
                    AlternativeConnectionException.Reason.STALE,
                    new IllegalStateException("HTTP/2 alternative route selection is stale"));
        }

        Http2ClientConnectionHandler handler = acquireHandler(http2Client, selection.originTarget());
        try {
            return handler.newAlternativeStream(http2Client, selection, request, http1FallbackHandler);
        } finally {
            handler.release();
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
                                                       serviceRequest,
                                                       http1FallbackHandler,
                                                       null);
        }

        Http2ClientConnectionHandler handler = acquireHandler(http2Client, connectionTarget);
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
            markSupported(connectionTarget, result.handler());
        }
        return result;
    }

    private Http2ClientConnectionHandler acquireHandler(Http2ClientImpl http2Client,
                                                        ClientConnectionTarget connectionTarget) {
        List<Http2ClientConnectionHandler> retiredHandlers = null;
        try {
            while (true) {
                if (closed.get()) {
                    throw new IllegalStateException("Connection cache is closed");
                }
                if (!connectionTarget.currentTlsGeneration()) {
                    throw staleTlsGeneration();
                }
                Http2ClientConnectionHandler handler = cache.get(connectionTarget);
                if (handler != null) {
                    if (handler.acquire()) {
                        return handler;
                    }
                    continue;
                }

                cacheLock.lock();
                try {
                    if (closed.get()) {
                        throw new IllegalStateException("Connection cache is closed");
                    }
                    if (!connectionTarget.currentTlsGeneration()) {
                        throw staleTlsGeneration();
                    }
                    handler = cache.get(connectionTarget);
                    if (handler != null) {
                        if (handler.acquire()) {
                            return handler;
                        }
                        continue;
                    }

                    retiredHandlers = new ArrayList<>();
                    retireStaleTlsTargets(connectionTarget, retiredHandlers);
                    retireOldestTarget(retiredHandlers);

                    handler = new Http2ClientConnectionHandler(http2Client.clientConfig().connectionCacheSize(),
                                                               altSvc::current);
                    if (!handler.acquire()) {
                        throw new IllegalStateException("New HTTP/2 connection target could not be acquired");
                    }
                    insertionOrder.put(connectionTarget, handler);
                    cache.put(connectionTarget, handler);
                    addLookupHandler(connectionTarget, handler);
                    return handler;
                } finally {
                    cacheLock.unlock();
                }
            }
        } finally {
            if (retiredHandlers != null) {
                retiredHandlers.forEach(Http2ClientConnectionHandler::retire);
            }
        }
    }

    private void retireStaleTlsTargets(ClientConnectionTarget connectionTarget,
                                       List<Http2ClientConnectionHandler> retiredHandlers) {
        var iterator = insertionOrder.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            ClientConnectionTarget cachedTarget = entry.getKey();
            if (cachedTarget.connectionKey().tls() == connectionTarget.connectionKey().tls()
                    && !cachedTarget.currentTlsGeneration()) {
                iterator.remove();
                if (cache.remove(cachedTarget, entry.getValue())) {
                    removeLookupHandler(cachedTarget, entry.getValue());
                    retiredHandlers.add(entry.getValue());
                }
            }
        }
    }

    private static IllegalStateException staleTlsGeneration() {
        return new IllegalStateException("TLS configuration was reloaded before connection-pool acquisition");
    }

    private void retireOldestTarget(List<Http2ClientConnectionHandler> retiredHandlers) {
        if (insertionOrder.size() < MAX_TARGETS) {
            return;
        }
        var evictionIterator = insertionOrder.entrySet().iterator();
        var evicted = evictionIterator.next();
        evictionIterator.remove();
        if (cache.remove(evicted.getKey(), evicted.getValue())) {
            removeLookupHandler(evicted.getKey(), evicted.getValue());
            retiredHandlers.add(evicted.getValue());
        }
    }

    private void addLookupHandler(ClientConnectionTarget connectionTarget,
                                  Http2ClientConnectionHandler handler) {
        if (!connectionTarget.connectionKey().proxy().ipNoProxyConfigured()) {
            return;
        }
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

    private boolean hasEstablishedAlternative(Http2AltSvcCache.Selection selection) {
        Http2ClientConnectionHandler handler = cache.get(selection.originTarget());
        return handler != null && handler.hasAlternative(selection);
    }

    private boolean hasEstablishedAlternative(Http2AltSvcCache.Generation generation) {
        List<Http2ClientConnectionHandler> handlers;
        cacheLock.lock();
        try {
            handlers = List.copyOf(insertionOrder.values());
        } finally {
            cacheLock.unlock();
        }
        for (Http2ClientConnectionHandler handler : handlers) {
            if (handler.hasAlternative(generation)) {
                return true;
            }
        }
        return false;
    }

    private void retireAlternative(Http2AltSvcCache.Selection selection) {
        Http2ClientConnectionHandler handler = cache.get(selection.originTarget());
        if (handler != null) {
            handler.retire(selection);
        }
    }

    private void retireAlternative(Http2AltSvcCache.Generation generation) {
        List<Http2ClientConnectionHandler> handlers;
        cacheLock.lock();
        try {
            handlers = List.copyOf(insertionOrder.values());
        } finally {
            cacheLock.unlock();
        }
        handlers.forEach(handler -> handler.retire(generation));
    }

    private void removeSupportIfUnused(ConnectionKey connectionKey) {
        for (Map.Entry<ClientConnectionTarget, Http2ClientConnectionHandler> entry : cache.entrySet()) {
            if (connectionKey.equals(entry.getKey().connectionKey())
                    && entry.getValue().directHttp2Supported()) {
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
