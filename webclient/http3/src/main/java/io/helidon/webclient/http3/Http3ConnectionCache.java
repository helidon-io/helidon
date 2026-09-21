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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import javax.net.ssl.SNIServerName;

import io.helidon.common.tls.Tls;
import io.helidon.http.HttpLogConfig;
import io.helidon.http.http3.Http3Settings;
import io.helidon.quic.QuicClientInitialTokenCache;
import io.helidon.quic.QuicClientTlsSessionCache;
import io.helidon.quic.QuicConfig;
import io.helidon.quic.QuicStreamLimitException;
import io.helidon.quic.stream.QuicBidiStreamReservation;
import io.helidon.webclient.api.ClientConnectionTarget;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.spi.ClientConnectionCache;

import static io.helidon.webclient.http3.Http3ConnectionCacheSupport.InitialTokenLease;
import static io.helidon.webclient.http3.Http3ConnectionCacheSupport.InitialTokenState;
import static io.helidon.webclient.http3.Http3ConnectionCacheSupport.SessionPoolOwner;
import static io.helidon.webclient.http3.Http3ConnectionCacheSupport.SessionSlot;
import static io.helidon.webclient.http3.Http3ConnectionCacheSupport.TlsGenerationState;
import static io.helidon.webclient.http3.Http3RequestFailureSupport.collectCleanupFailure;

final class Http3ConnectionCache extends ClientConnectionCache {
    private static final Http3ConnectionCache SHARED = new Http3ConnectionCache(true);
    private static final int SESSION_CAPACITY = 1_000;
    private static final int TLS_GENERATION_CAPACITY = 16;
    private static final int INITIAL_TOKEN_SCOPE_CAPACITY = 1_000;

    private final Http3SessionIndex<CacheKey, SessionPool, RouteKey, TlsIdentityKey> sessions =
            new Http3SessionIndex<>(SESSION_CAPACITY);
    private final Map<TlsGenerationKey, TlsGenerationState> tlsGenerations =
            new LinkedHashMap<>(16, 0.75F, true);
    private final Map<InitialTokenScope, InitialTokenState> initialTokenCaches =
            new LinkedHashMap<>(16, 0.75F, true);
    private final Set<SessionSlot> activeSlots = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Http3ExchangeClient.ConnectionSession, SessionSlot> activeSessions = new IdentityHashMap<>();
    private final Http3Discovery discovery;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong cacheEpoch = new AtomicLong();
    private final ReentrantLock evictionLock = new ReentrantLock();
    private final CompletableFuture<Void> closeCompletion = new CompletableFuture<>();

    private Http3ConnectionCache(boolean shared) {
        super(shared);
        discovery = Http3Discovery.create(this::retireRoute);
    }

    static Http3ConnectionCache shared() {
        return SHARED;
    }

    static Http3ConnectionCache create() {
        return new Http3ConnectionCache(false);
    }

    static int validateConnectionCacheSize(int connectionCacheSize) {
        if (connectionCacheSize < 1) {
            throw new IllegalArgumentException("Connection cache size must be greater than zero");
        }
        return connectionCacheSize;
    }

    CompletableFuture<Http3ExchangeClient.RequestStream> requestStream(Http3ExchangeClient.ConnectionConfig config,
                                                                        Http3ExchangeClient.RequestData request,
                                                                        Executor responseExecutor) {
        SessionPool pool = sessionPool(config);
        CompletableFuture<Http3ExchangeClient.RequestStream> result = new CompletableFuture<>();
        selectStream(config, pool, request, responseExecutor, result);
        return result;
    }

    boolean hasSession(CacheKey cacheKey) {
        long epoch = cacheEpoch.get();
        if (closed.get()
                || (epoch & 1) != 0
                || !currentTlsGeneration(cacheKey, cacheKey.connectionKey().tls())) {
            return false;
        }
        SessionPool pool = sessions.get(cacheKey);
        return epoch == cacheEpoch.get()
                && pool != null
                && pool.hasSession(epoch)
                && currentTlsGeneration(cacheKey, cacheKey.connectionKey().tls());
    }

    Http3Discovery discovery() {
        return discovery;
    }

    boolean closeWouldBlockCurrentThread() {
        evictionLock.lock();
        try {
            Thread currentThread = Thread.currentThread();
            for (SessionSlot slot : activeSlots) {
                if (slot.creationPendingOn(currentThread)) {
                    return true;
                }
                Http3ExchangeClient.ConnectionSession currentSession = slot.session();
                if (currentSession != null && currentSession.closeWouldBlockCurrentThread()) {
                    return true;
                }
            }
            return false;
        } finally {
            evictionLock.unlock();
        }
    }

    void remove(CacheKey cacheKey, Http3ExchangeClient.ConnectionSession session) {
        SessionSlot expected = null;
        evictionLock.lock();
        try {
            SessionSlot slot = activeSessions.get(session);
            if (slot != null && slot.pool().cacheKey().equals(cacheKey)) {
                expected = slot;
            }
        } finally {
            evictionLock.unlock();
        }
        if (expected != null) {
            removeFromPool(expected);
            expected.close();
        }
    }

    @Override
    protected void evict() {
        List<SessionSlot> toClose = new ArrayList<>();
        List<PoolActions> poolActions = new ArrayList<>();
        evictionLock.lock();
        try {
            if (closed.get()) {
                return;
            }
            cacheEpoch.incrementAndGet();
            try {
                for (Http3SessionIndex.Entry<CacheKey, SessionPool> entry : sessions.clear()) {
                    PoolDetach detached = entry.value().detachAll();
                    toClose.addAll(detached.slots());
                    poolActions.add(detached.actions());
                }
                tlsGenerations.values().forEach(TlsGenerationState::close);
                tlsGenerations.clear();
                discardInitialTokenCaches();
                discovery.networkChanged();
            } finally {
                cacheEpoch.incrementAndGet();
            }
        } finally {
            evictionLock.unlock();
        }
        poolActions.forEach(PoolActions::execute);
        toClose.forEach(SessionSlot::close);
    }

    @Override
    public void closeResource() {
        boolean closeOwner = false;
        Throwable failure = null;
        List<SessionSlot> slotsToClose = List.of();
        List<PoolActions> poolActions = new ArrayList<>();
        List<CompletionStage<Void>> sessionTerminations = new ArrayList<>();
        evictionLock.lock();
        try {
            if (!closed.getAndSet(true)) {
                closeOwner = true;
                cacheEpoch.incrementAndGet();
                for (Http3SessionIndex.Entry<CacheKey, SessionPool> entry : sessions.clear()) {
                    poolActions.add(entry.value().detachAll().actions());
                }
                for (SessionSlot slot : List.copyOf(activeSlots)) {
                    slot.detach();
                }
                slotsToClose = List.copyOf(activeSlots);
                for (TlsGenerationState generationState : tlsGenerations.values()) {
                    try {
                        generationState.close();
                    } catch (Throwable closeFailure) {
                        failure = collectCleanupFailure(failure, closeFailure);
                    }
                }
                tlsGenerations.clear();
                for (InitialTokenState state : initialTokenCaches.values()) {
                    try {
                        state.discard();
                    } catch (Throwable closeFailure) {
                        failure = collectCleanupFailure(failure, closeFailure);
                    }
                }
                initialTokenCaches.clear();
            }
        } finally {
            evictionLock.unlock();
        }

        if (closeOwner) {
            poolActions.forEach(PoolActions::execute);
            try {
                discovery.clear();
            } catch (Throwable closeFailure) {
                failure = collectCleanupFailure(failure, closeFailure);
            }
            for (SessionSlot slot : slotsToClose) {
                try {
                    sessionTerminations.add(slot.close());
                } catch (Throwable closeFailure) {
                    failure = collectCleanupFailure(failure, closeFailure);
                }
            }
            for (CompletionStage<Void> termination : sessionTerminations) {
                try {
                    Throwable terminationFailure = Http3RequestFailureSupport.completionFailure(termination);
                    if (terminationFailure != null) {
                        failure = collectCleanupFailure(failure, terminationFailure);
                    }
                } catch (Throwable terminationFailure) {
                    failure = collectCleanupFailure(failure, terminationFailure);
                }
            }
            if (failure != null
                    && !(failure instanceof RuntimeException)
                    && !(failure instanceof Error)) {
                failure = new IllegalStateException("Failed to close HTTP/3 connection cache", failure);
            }
            if (failure == null) {
                closeCompletion.complete(null);
            } else {
                closeCompletion.completeExceptionally(failure);
            }
        }

        Throwable closeFailure = Http3RequestFailureSupport.completionFailure(closeCompletion);
        if (closeFailure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (closeFailure instanceof Error error) {
            throw error;
        }
        if (closeFailure != null) {
            throw new IllegalStateException("Failed to close HTTP/3 connection cache", closeFailure);
        }
    }

    void releaseInitialTokenState(InitialTokenState state) {
        evictionLock.lock();
        try {
            state.release();
        } finally {
            evictionLock.unlock();
        }
    }

    private static void completeStream(SessionPool pool,
                                       SessionSlot slot,
                                       Http3ExchangeClient.ConnectionSession session,
                                       Http3RequestStream stream,
                                       CompletableFuture<Http3ExchangeClient.RequestStream> result) {
        pool.prefer(slot);
        if (!result.complete(new Http3ExchangeClient.RequestStream(session, stream))) {
            stream.cancel();
        }
    }

    private static boolean currentTlsGeneration(CacheKey cacheKey, Tls tls) {
        return cacheKey.tlsGeneration() == tls.generation();
    }

    private static void retireDetached(SessionSlot slot, String reason) {
        Http3ExchangeClient.ConnectionSession session = slot.session();
        if (session == null) {
            slot.close();
        } else {
            session.retire(reason);
        }
    }

    private SessionPool sessionPool(Http3ExchangeClient.ConnectionConfig config) {
        CacheKey cacheKey = config.cacheKey();
        while (true) {
            long currentEpoch = availableEpoch(cacheKey, config.tls());
            SessionPool currentPool = sessions.get(cacheKey);
            if (currentPool != null && currentPool.available(currentEpoch)
                    && sessions.get(cacheKey) == currentPool
                    && cacheEpoch.get() == currentEpoch
                    && currentTlsGeneration(cacheKey, config.tls())
                    && (config.selection() == null || discovery.current(config.selection()))) {
                return currentPool;
            }
            long epoch;
            SessionPool selected = null;
            List<PoolRetirement> retirements = new ArrayList<>();
            boolean retry = false;
            Throwable failure = null;
            evictionLock.lock();
            try {
                epoch = availableEpoch(cacheKey, config.tls());
                retirements.addAll(detachStaleTlsGenerations(config.tls()));
                if (config.selection() != null && !discovery.current(config.selection())) {
                    throw new IllegalStateException("HTTP/3 route selection is stale");
                }
                SessionPool pool = sessions.get(cacheKey);
                if (pool != null) {
                    if (!pool.available(epoch)) {
                        if (sessions.remove(cacheKey, pool)) {
                            retirements.add(new PoolRetirement(pool.detachAll(), "cache-epoch"));
                        }
                        retry = true;
                    } else if (!currentTlsGeneration(cacheKey, config.tls())) {
                        throw new IllegalStateException("TLS configuration was reloaded");
                    } else {
                        selected = pool;
                    }
                } else if (config.selection() != null && !config.selection().establishAllowed()) {
                    throw new IllegalStateException("Expired HTTP/3 route has no cached session");
                } else {
                    SessionPool candidate = new SessionPool(cacheKey, epoch, config.connectionCacheSize());
                    Http3SessionIndex.Insertion<CacheKey, SessionPool> insertion = sessions.putIfAbsent(
                            cacheKey,
                            candidate,
                            new RouteKey(cacheKey.endpointKey(), cacheKey.target()),
                            new TlsIdentityKey(config.tls()),
                            cacheKey.tlsGeneration());
                    if (insertion.existing() != null) {
                        retry = true;
                    } else {
                        selected = candidate;
                        Http3SessionIndex.Entry<CacheKey, SessionPool> evicted = insertion.evicted();
                        if (evicted != null) {
                            retirements.add(new PoolRetirement(evicted.value().detachAll(), "cache-capacity"));
                        }
                    }
                }
            } catch (RuntimeException | Error throwable) {
                failure = throwable;
            } finally {
                evictionLock.unlock();
            }
            retirements.forEach(PoolRetirement::complete);
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            if (!retry) {
                return selected;
            }
        }
    }

    private void selectStream(Http3ExchangeClient.ConnectionConfig config,
                              SessionPool pool,
                              Http3ExchangeClient.RequestData request,
                              Executor responseExecutor,
                              CompletableFuture<Http3ExchangeClient.RequestStream> result) {
        if (result.isDone()) {
            return;
        }
        try {
            long epoch = ensureCurrent(config, pool);
            if (pool.hasQueuedRequests(epoch)) {
                waitForCredit(config, pool, request, responseExecutor, result);
                return;
            }
            SessionSlot preferred = pool.preferred(epoch);
            if (preferred == null) {
                scanReadySlots(config, pool, request, responseExecutor, result, null, epoch);
            } else {
                openImmediate(config, pool, request, responseExecutor, result, preferred, epoch);
            }
        } catch (RuntimeException | Error failure) {
            result.completeExceptionally(failure);
        }
    }

    private void openImmediate(Http3ExchangeClient.ConnectionConfig config,
                               SessionPool pool,
                               Http3ExchangeClient.RequestData request,
                               Executor responseExecutor,
                               CompletableFuture<Http3ExchangeClient.RequestStream> result,
                               SessionSlot slot,
                               long epoch) {
        Http3ExchangeClient.ConnectionSession session = slot.session();
        if (session == null || !session.isUsable()) {
            retireSlot(slot, "session-unusable");
            scanReadySlots(config, pool, request, responseExecutor, result, slot, epoch);
            return;
        }
        CompletableFuture<Http3RequestStream> opened = session.openRequestStream(request,
                                                                                  responseExecutor,
                                                                                  Duration.ZERO);
        opened.whenComplete((stream, throwable) -> {
            if (throwable == null) {
                completeStream(pool, slot, session, stream, result);
                return;
            }
            Throwable cause = Http3ExchangeClient.unwrap(throwable);
            if (cause instanceof QuicStreamLimitException) {
                scanReadySlots(config, pool, request, responseExecutor, result, slot, epoch);
            } else if (Http3RequestFailureSupport.isRetryableRequestFailure(cause)) {
                retireSlot(slot, "request-open-rejected");
                scanReadySlots(config, pool, request, responseExecutor, result, slot, epoch);
            } else {
                result.completeExceptionally(new Http3ExchangeClient.RequestStreamOpenException(session, cause));
            }
        });
    }

    private void scanReadySlots(Http3ExchangeClient.ConnectionConfig config,
                                SessionPool pool,
                                Http3ExchangeClient.RequestData request,
                                Executor responseExecutor,
                                CompletableFuture<Http3ExchangeClient.RequestStream> result,
                                SessionSlot excluded,
                                long epoch) {
        ReadySlots readySlots;
        try {
            ensureCurrent(config, pool);
            readySlots = pool.readySlots(excluded, epoch);
        } catch (RuntimeException | Error failure) {
            result.completeExceptionally(failure);
            return;
        }
        scanReadyList(config,
                      pool,
                      request,
                      responseExecutor,
                      result,
                      readySlots,
                      0);
    }

    private void scanReadyList(Http3ExchangeClient.ConnectionConfig config,
                               SessionPool pool,
                               Http3ExchangeClient.RequestData request,
                               Executor responseExecutor,
                               CompletableFuture<Http3ExchangeClient.RequestStream> result,
                               ReadySlots readySlots,
                               int startIndex) {
        List<SessionSlot> slots = readySlots.slots();
        for (int i = startIndex; i < slots.size(); i++) {
            SessionSlot slot = slots.get(i);
            Http3ExchangeClient.ConnectionSession session = slot.session();
            if (session == null || !session.isUsable()) {
                retireSlot(slot, "session-unusable");
                continue;
            }
            CompletableFuture<Http3RequestStream> opened = session.openRequestStream(request,
                                                                                      responseExecutor,
                                                                                      Duration.ZERO);
            if (!opened.isDone()) {
                int nextIndex = i + 1;
                opened.whenComplete((stream, throwable) -> {
                    if (throwable == null) {
                        completeStream(pool, slot, session, stream, result);
                        return;
                    }
                    Throwable cause = Http3ExchangeClient.unwrap(throwable);
                    if (cause instanceof QuicStreamLimitException) {
                        scanReadyList(config,
                                      pool,
                                      request,
                                      responseExecutor,
                                      result,
                                      readySlots,
                                      nextIndex);
                    } else if (Http3RequestFailureSupport.isRetryableRequestFailure(cause)) {
                        retireSlot(slot, "request-open-rejected");
                        scanReadyList(config,
                                      pool,
                                      request,
                                      responseExecutor,
                                      result,
                                      readySlots,
                                      nextIndex);
                    } else {
                        result.completeExceptionally(
                                new Http3ExchangeClient.RequestStreamOpenException(session, cause));
                    }
                });
                return;
            }
            try {
                completeStream(pool, slot, session, opened.join(), result);
                return;
            } catch (RuntimeException failure) {
                Throwable cause = Http3ExchangeClient.unwrap(failure);
                if (cause instanceof QuicStreamLimitException) {
                    continue;
                }
                if (Http3RequestFailureSupport.isRetryableRequestFailure(cause)) {
                    retireSlot(slot, "request-open-rejected");
                    continue;
                }
                result.completeExceptionally(new Http3ExchangeClient.RequestStreamOpenException(session, cause));
                return;
            }
        }
        reserveOrWait(config, pool, request, responseExecutor, result, readySlots.revision());
    }

    private void reserveOrWait(Http3ExchangeClient.ConnectionConfig config,
                               SessionPool pool,
                               Http3ExchangeClient.RequestData request,
                               Executor responseExecutor,
                               CompletableFuture<Http3ExchangeClient.RequestStream> result,
                               long revision) {
        SlotReservation reservation;
        try {
            reservation = reserveSlot(config, pool, revision);
        } catch (RuntimeException | Error failure) {
            result.completeExceptionally(failure);
            return;
        }
        reservation.actions().execute();
        switch (reservation.kind()) {
        case NEW -> {
            createSession(config, pool, reservation.slot());
            waitForReady(config, pool, request, responseExecutor, result, reservation.slot());
        }
        case PENDING -> waitForReady(config, pool, request, responseExecutor, result, reservation.slot());
        case FULL -> waitForCredit(config, pool, request, responseExecutor, result);
        case RETRY -> selectStream(config, pool, request, responseExecutor, result);
        default -> throw new IllegalStateException("Unknown HTTP/3 session-pool reservation: " + reservation.kind());
        }
    }

    private void waitForReady(Http3ExchangeClient.ConnectionConfig config,
                              SessionPool pool,
                              Http3ExchangeClient.RequestData request,
                              Executor responseExecutor,
                              CompletableFuture<Http3ExchangeClient.RequestStream> result,
                              SessionSlot slot) {
        slot.ready().whenComplete((_, throwable) -> {
            if (throwable == null) {
                selectStream(config, pool, request, responseExecutor, result);
            } else {
                result.completeExceptionally(Http3ExchangeClient.unwrap(throwable));
            }
        });
    }

    private void waitForCredit(Http3ExchangeClient.ConnectionConfig config,
                               SessionPool pool,
                               Http3ExchangeClient.RequestData request,
                               Executor responseExecutor,
                               CompletableFuture<Http3ExchangeClient.RequestStream> result) {
        PendingRequest pending = new PendingRequest(config, pool, request, responseExecutor, result);
        result.whenComplete((_, _) -> {
            if (result.isCancelled()) {
                pending.cancel();
            }
        });
        PoolActions actions;
        try {
            long epoch = ensureCurrent(config, pool);
            actions = pool.enqueue(pending, epoch);
        } catch (RuntimeException | Error failure) {
            pending.fail(failure);
            return;
        }
        pending.startDeadline();
        actions.execute();
    }

    private void openReserved(ClaimedReservation claimed) {
        PendingRequest pending = claimed.pending();
        SessionSlot slot = claimed.slot();
        QuicBidiStreamReservation reservation = claimed.reservation();
        if (!pending.pool.beginMaterialization(claimed)) {
            reservation.close();
            pending.pool.waiterTerminated(pending, true).execute();
            return;
        }
        if (pending.deadlineReached()) {
            pending.timeout();
            reservation.close();
            pending.pool.waiterTerminated(pending, true).execute();
            return;
        }
        if (!pending.pool.beginOpen(claimed)) {
            reservation.close();
            pending.pool.waiterTerminated(pending, true).execute();
            return;
        }
        Http3ExchangeClient.ConnectionSession session = slot.session();
        try {
            ensureCurrent(pending.config, pending.pool);
            if (session == null || !session.isUsable()) {
                throw new IllegalStateException("HTTP/3 connection session is unavailable");
            }
            Http3RequestStream stream = session.openReservedRequestStream(pending.request,
                                                                           pending.responseExecutor,
                                                                           reservation);
            pending.complete(slot, session, stream);
        } catch (RuntimeException | Error failure) {
            reservation.close();
            Throwable cause = Http3ExchangeClient.unwrap(failure);
            if (session != null && Http3RequestFailureSupport.isRetryableRequestFailure(cause)) {
                retireSlot(slot, "request-open-rejected");
            }
            if (session == null) {
                pending.failAfterMaterialization(cause);
            } else {
                pending.failAfterMaterialization(new Http3ExchangeClient.RequestStreamOpenException(session, cause));
            }
        }
    }

    private SlotReservation reserveSlot(Http3ExchangeClient.ConnectionConfig config,
                                        SessionPool pool,
                                        long revision) {
        evictionLock.lock();
        try {
            long epoch = ensureCurrent(config, pool);
            SlotReservation reservation = pool.reserve(epoch, revision, () -> tlsGenerationState(config));
            if (reservation.kind() == ReservationKind.NEW) {
                SessionSlot slot = reservation.slot();
                activeSlots.add(slot);
                slot.tlsGenerationState().retainSession();
            }
            return reservation;
        } finally {
            evictionLock.unlock();
        }
    }

    private TlsGenerationState tlsGenerationState(Http3ExchangeClient.ConnectionConfig config) {
        CacheKey cacheKey = config.cacheKey();
        TlsGenerationKey tlsGenerationKey = new TlsGenerationKey(config.tls(), cacheKey.tlsGeneration());
        TlsGenerationState generationState = tlsGenerations.get(tlsGenerationKey);
        if (generationState != null) {
            return generationState;
        }
        if (tlsGenerations.size() >= TLS_GENERATION_CAPACITY) {
            var iterator = tlsGenerations.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (entry.getValue().hasNoSessions()) {
                    entry.getValue().close();
                    iterator.remove();
                    break;
                }
            }
        }
        boolean retained = tlsGenerations.size() < TLS_GENERATION_CAPACITY;
        QuicClientTlsSessionCache sessionCache = retained
                ? QuicClientTlsSessionCache.create(config.tls())
                : QuicClientTlsSessionCache.create(config.tls(), 0);
        generationState = new TlsGenerationState(sessionCache, retained);
        if (retained) {
            tlsGenerations.put(tlsGenerationKey, generationState);
        }
        return generationState;
    }

    // Session ownership, publication, and failed-construction cleanup form one cache transaction.
    @SuppressWarnings("checkstyle:MethodLength")
    private void createSession(Http3ExchangeClient.ConnectionConfig config,
                               SessionPool pool,
                               SessionSlot candidate) {
        long epoch = candidate.epoch();
        if (!slotCurrent(config, pool, candidate, epoch)) {
            failUncreatedSlot(candidate, new IllegalStateException("Connection cache is unavailable"));
            return;
        }
        InitialTokenLease tokenLease = null;
        Http3ExchangeClient.ConnectionSession created = null;
        try {
            tokenLease = initialTokenLease(config);
            created = Http3ExchangeClient.ConnectionSession.create(
                    config,
                    candidate.tlsGenerationState().sessionCache(),
                    tokenLease.cache(),
                    () -> {
                        if (removeFromPool(candidate) && candidate.session() == null) {
                            candidate.fail(new IllegalStateException(
                                    "HTTP/3 connection closed before it became ready"));
                        }
                    });
            InitialTokenLease transferredLease = tokenLease;
            created.whenTerminated().whenComplete((_, throwable) -> {
                Throwable terminationFailure = throwable;
                try {
                    transferredLease.close();
                } catch (Throwable closeFailure) {
                    terminationFailure = collectCleanupFailure(terminationFailure, closeFailure);
                }
                try {
                    releaseTlsGeneration(candidate);
                } catch (Throwable closeFailure) {
                    terminationFailure = collectCleanupFailure(terminationFailure, closeFailure);
                }
                completeSlotTermination(candidate, terminationFailure);
            });
            tokenLease = null;
            boolean installed = candidate.install(created);
            if (installed) {
                registerActiveSession(candidate, created);
            }
            if (!slotCurrent(config, pool, candidate, epoch)) {
                removeFromPool(candidate);
                candidate.fail(new IllegalStateException("Connection cache is unavailable"));
                return;
            }
            created.ready().whenComplete((readySession, throwable) -> {
                if (throwable == null) {
                    if (slotCurrent(config, pool, candidate, epoch)) {
                        pool.candidateReady(candidate);
                        candidate.complete(readySession);
                        pool.prefer(candidate);
                        pool.armReadySession(candidate).execute();
                    } else {
                        removeFromPool(candidate);
                        candidate.fail(new IllegalStateException("Connection cache is unavailable"));
                    }
                } else {
                    removeFromPool(candidate);
                    candidate.fail(throwable);
                }
            });
            if (!installed) {
                candidate.fail(new IllegalStateException("HTTP/3 connection cache entry is closed"));
            }
        } catch (RuntimeException | Error failure) {
            removeFromPool(candidate);
            candidate.fail(failure);
            Throwable cleanupFailure = null;
            if (tokenLease != null) {
                try {
                    tokenLease.close();
                } catch (Throwable closeFailure) {
                    cleanupFailure = closeFailure;
                }
            }
            if (created == null) {
                candidate.creationFailed();
                try {
                    releaseTlsGeneration(candidate);
                } catch (Throwable closeFailure) {
                    cleanupFailure = collectCleanupFailure(cleanupFailure, closeFailure);
                }
                completeSlotTermination(candidate, cleanupFailure);
            }
        }
    }

    private void failUncreatedSlot(SessionSlot candidate, IllegalStateException failure) {
        removeFromPool(candidate);
        candidate.creationFailed();
        candidate.fail(failure);
        Throwable cleanupFailure = null;
        try {
            releaseTlsGeneration(candidate);
        } catch (Throwable closeFailure) {
            cleanupFailure = closeFailure;
            failure.addSuppressed(closeFailure);
        }
        completeSlotTermination(candidate, cleanupFailure);
    }

    private boolean removeFromPool(SessionSlot slot) {
        return slot.pool().removeSlot(slot);
    }

    private long ensureCurrent(Http3ExchangeClient.ConnectionConfig config, SessionPool pool) {
        CacheKey cacheKey = config.cacheKey();
        long epoch = availableEpoch(cacheKey, config.tls());
        if (!pool.available(epoch)
                || sessions.get(cacheKey) != pool
                || cacheEpoch.get() != epoch
                || (config.selection() != null && !discovery.current(config.selection()))) {
            throw new IllegalStateException("Connection cache is unavailable");
        }
        return epoch;
    }

    private boolean slotCurrent(Http3ExchangeClient.ConnectionConfig config,
                                SessionPool pool,
                                SessionSlot slot,
                                long epoch) {
        CacheKey cacheKey = config.cacheKey();
        return !closed.get()
                && cacheEpoch.get() == epoch
                && sessions.get(cacheKey) == pool
                && pool.available(epoch)
                && slot.available(epoch)
                && currentTlsGeneration(cacheKey, config.tls())
                && (config.selection() == null || discovery.current(config.selection()));
    }

    private InitialTokenLease initialTokenLease(Http3ExchangeClient.ConnectionConfig config) {
        evictionLock.lock();
        try {
            if (closed.get()) {
                throw new IllegalStateException("Connection cache is closed");
            }
            CacheKey cacheKey = config.cacheKey();
            ConnectionKey connectionKey = cacheKey.connectionKey();
            InitialTokenScope scope = new InitialTokenScope(cacheKey.connectionTarget().originAuthority().toString(),
                                                            connectionKey.tlsPeerHost(),
                                                            connectionKey.tlsPeerPort(),
                                                            config.serverNames().orElseGet(List::of),
                                                            config.selection().networkGeneration());
            InitialTokenState tokenState = initialTokenCaches.get(scope);
            if (tokenState != null) {
                tokenState.retain();
                return new InitialTokenLease(this, tokenState);
            }
            if (initialTokenCaches.size() >= INITIAL_TOKEN_SCOPE_CAPACITY) {
                var iterator = initialTokenCaches.entrySet().iterator();
                while (iterator.hasNext()) {
                    var candidate = iterator.next();
                    if (candidate.getValue().hasNoLeases()) {
                        candidate.getValue().discard();
                        iterator.remove();
                        break;
                    }
                }
            }
            boolean retained = initialTokenCaches.size() < INITIAL_TOKEN_SCOPE_CAPACITY;
            tokenState = new InitialTokenState(retained
                                                       ? QuicClientInitialTokenCache.create()
                                                       : QuicClientInitialTokenCache.create(0),
                                               retained);
            tokenState.retain();
            if (retained) {
                initialTokenCaches.put(scope, tokenState);
            }
            return new InitialTokenLease(this, tokenState);
        } finally {
            evictionLock.unlock();
        }
    }

    private void retireSlot(SessionSlot slot, String reason) {
        if (slot.pool().removeSlot(slot)) {
            retireDetached(slot, reason);
        }
    }

    private void releaseTlsGeneration(SessionSlot slot) {
        if (!slot.releaseTlsGeneration()) {
            return;
        }
        evictionLock.lock();
        try {
            TlsGenerationState generationState = slot.tlsGenerationState();
            if (generationState.releaseSession()) {
                slot.pool().clearTlsGenerationState(generationState);
                if (!generationState.retained()) {
                    generationState.close();
                }
            }
        } finally {
            evictionLock.unlock();
        }
    }

    private void completeSlotTermination(SessionSlot slot, Throwable failure) {
        evictionLock.lock();
        try {
            Http3ExchangeClient.ConnectionSession installedSession = slot.installedSession();
            if (installedSession != null) {
                activeSessions.remove(installedSession, slot);
            }
            activeSlots.remove(slot);
        } finally {
            evictionLock.unlock();
        }
        slot.completeTermination(failure);
    }

    private void registerActiveSession(SessionSlot slot, Http3ExchangeClient.ConnectionSession session) {
        evictionLock.lock();
        try {
            if (activeSlots.contains(slot)) {
                SessionSlot previous = activeSessions.put(session, slot);
                if (previous != null && previous != slot) {
                    activeSessions.put(session, previous);
                    throw new IllegalStateException("HTTP/3 connection session has multiple cache owners");
                }
            }
        } finally {
            evictionLock.unlock();
        }
    }

    private List<PoolRetirement> detachStaleTlsGenerations(Tls tls) {
        long generation = tls.generation();
        List<PoolRetirement> retirements = new ArrayList<>();
        List<Http3SessionIndex.Entry<CacheKey, SessionPool>> staleSessions =
                sessions.removeStaleTlsGeneration(new TlsIdentityKey(tls), generation);
        for (Http3SessionIndex.Entry<CacheKey, SessionPool> entry : staleSessions) {
            retirements.add(new PoolRetirement(entry.value().detachAll(), "tls-reload"));
        }
        var iterator = tlsGenerations.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getKey().tls() == tls && entry.getKey().generation() != generation) {
                entry.getValue().close();
                iterator.remove();
            }
        }
        return retirements;
    }

    private void discardInitialTokenCaches() {
        initialTokenCaches.values().forEach(InitialTokenState::discard);
        initialTokenCaches.clear();
    }

    private void retireRoute(Http3Discovery.EndpointContextKey endpointKey, Http3Discovery.Target target) {
        List<PoolRetirement> retirements = new ArrayList<>();
        evictionLock.lock();
        try {
            List<Http3SessionIndex.Entry<CacheKey, SessionPool>> routeSessions =
                    sessions.removeRoute(new RouteKey(endpointKey, target));
            for (Http3SessionIndex.Entry<CacheKey, SessionPool> entry : routeSessions) {
                retirements.add(new PoolRetirement(entry.value().detachAll(), "route-invalidated"));
            }
        } finally {
            evictionLock.unlock();
        }
        retirements.forEach(PoolRetirement::complete);
    }

    private long availableEpoch(CacheKey cacheKey, Tls tls) {
        if (closed.get()) {
            throw new IllegalStateException("Connection cache is closed");
        }
        long epoch = cacheEpoch.get();
        if ((epoch & 1) != 0) {
            throw new IllegalStateException("Connection cache is unavailable");
        }
        if (!currentTlsGeneration(cacheKey, tls)) {
            throw new IllegalStateException("TLS configuration was reloaded");
        }
        return epoch;
    }

    private enum ReservationKind {
        NEW,
        PENDING,
        FULL,
        RETRY
    }

    record CacheKey(Http3Discovery.EndpointContextKey endpointKey,
                    Http3Discovery.Target target,
                    long idleTimeoutMillis,
                    Http3Settings localSettings,
                    int maxHeadersSize,
                    QuicConfig quicConfig,
                    HttpLogConfig logConfig,
                    boolean sendErrorDetails,
                    Duration initialResponseTimeout,
                    Duration handshakeTimeout,
                    Duration streamOpenTimeout) {
        ConnectionKey connectionKey() {
            return endpointKey.connectionKey();
        }

        ClientConnectionTarget connectionTarget() {
            return endpointKey.connectionTarget();
        }

        long tlsGeneration() {
            return endpointKey.tlsGeneration();
        }
    }

    private record TlsIdentityKey(Tls tls) {
        @Override
        public boolean equals(Object obj) {
            return obj instanceof TlsIdentityKey other && tls == other.tls;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(tls);
        }
    }

    private record RouteKey(Http3Discovery.EndpointContextKey endpointKey, Http3Discovery.Target target) {
    }

    private record InitialTokenScope(String originAuthority,
                                     String tlsPeerHost,
                                     int tlsPeerPort,
                                     List<SNIServerName> serverNames,
                                     long networkGeneration) {
    }

    private record TlsGenerationKey(Tls tls, long generation) {
        @Override
        public boolean equals(Object obj) {
            return obj instanceof TlsGenerationKey other
                    && tls == other.tls
                    && generation == other.generation;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(tls) + Long.hashCode(generation);
        }
    }

    private record ReadySlots(List<SessionSlot> slots, long revision) {
    }

    private record SlotReservation(ReservationKind kind, SessionSlot slot, PoolActions actions) {
    }

    private record SlotRemoval(boolean removed, PoolActions actions) {
    }

    private record PoolDetach(List<SessionSlot> slots, PoolActions actions) {
    }

    private record PoolRetirement(PoolDetach detached, String reason) {
        private void complete() {
            detached.actions().execute();
            detached.slots().forEach(slot -> retireDetached(slot, reason));
        }
    }

    private record ClaimedReservation(PendingRequest pending,
                                      SessionSlot slot,
                                      QuicBidiStreamReservation reservation) {
    }

    private record PendingFailure(PendingRequest pending, Throwable failure) {
    }

    private record PendingTimeout(PendingRequest pending,
                                  Http3ExchangeClient.ConnectionSession session) {
    }

    private record Retirement(SessionSlot slot, String reason) {
    }

    private static final class ReservationAttempt {
        private final SessionPool pool;
        private final SessionSlot slot;
        private final AtomicReference<CompletableFuture<QuicBidiStreamReservation>> future = new AtomicReference<>();
        private final AtomicBoolean canceled = new AtomicBoolean();

        private ReservationAttempt(SessionPool pool, SessionSlot slot) {
            this.pool = pool;
            this.slot = slot;
        }

        private void start() {
            if (canceled.get()) {
                return;
            }
            Http3ExchangeClient.ConnectionSession session = slot.session();
            CompletableFuture<QuicBidiStreamReservation> started;
            try {
                started = session == null
                        ? CompletableFuture.failedFuture(new IllegalStateException("HTTP/3 session is unavailable"))
                        : session.reserveRequestStream();
            } catch (RuntimeException | Error failure) {
                started = CompletableFuture.failedFuture(failure);
            }
            if (!future.compareAndSet(null, started)) {
                throw new IllegalStateException("HTTP/3 stream-credit reservation already started");
            }
            if (canceled.get()) {
                started.cancel(true);
            }
            started.whenComplete((reservation, throwable) ->
                    pool.reservationCompleted(this, reservation, throwable).execute());
        }

        private void cancel() {
            if (canceled.compareAndSet(false, true)) {
                CompletableFuture<QuicBidiStreamReservation> current = future.get();
                if (current != null) {
                    current.cancel(true);
                }
            }
        }
    }

    private final class PoolActions {
        private List<ReservationAttempt> starts;
        private List<ReservationAttempt> cancellations;
        private List<QuicBidiStreamReservation> reservationsToClose;
        private List<ClaimedReservation> dispatches;
        private List<PendingRequest> retries;
        private List<PendingFailure> failures;
        private List<PendingTimeout> timeouts;
        private List<Retirement> retirements;

        private void start(ReservationAttempt attempt) {
            if (starts == null) {
                starts = new ArrayList<>();
            }
            starts.add(attempt);
        }

        private void cancel(ReservationAttempt attempt) {
            if (cancellations == null) {
                cancellations = new ArrayList<>();
            }
            cancellations.add(attempt);
        }

        private void close(QuicBidiStreamReservation reservation) {
            if (reservationsToClose == null) {
                reservationsToClose = new ArrayList<>();
            }
            reservationsToClose.add(reservation);
        }

        private void dispatch(ClaimedReservation claimed) {
            if (dispatches == null) {
                dispatches = new ArrayList<>();
            }
            dispatches.add(claimed);
        }

        private void retry(PendingRequest pending) {
            if (retries == null) {
                retries = new ArrayList<>();
            }
            retries.add(pending);
        }

        private void fail(PendingRequest pending, Throwable failure) {
            if (failures == null) {
                failures = new ArrayList<>();
            }
            failures.add(new PendingFailure(pending, failure));
        }

        private void timeout(PendingRequest pending, Http3ExchangeClient.ConnectionSession session) {
            if (timeouts == null) {
                timeouts = new ArrayList<>();
            }
            timeouts.add(new PendingTimeout(pending, session));
        }

        private void retire(SessionSlot slot, String reason) {
            if (retirements == null) {
                retirements = new ArrayList<>();
            }
            retirements.add(new Retirement(slot, reason));
        }

        private void execute() {
            if (cancellations != null) {
                cancellations.forEach(ReservationAttempt::cancel);
            }
            if (reservationsToClose != null) {
                reservationsToClose.forEach(QuicBidiStreamReservation::close);
            }
            if (retirements != null) {
                retirements.forEach(retirement -> retireDetached(retirement.slot(), retirement.reason()));
            }
            if (failures != null) {
                failures.forEach(failure -> failure.pending().completeFailure(failure.failure()));
            }
            if (timeouts != null) {
                timeouts.forEach(timeout -> timeout.pending().completeTimeout(timeout.session()));
            }
            if (retries != null) {
                retries.forEach(PendingRequest::performRetry);
            }
            if (dispatches != null) {
                dispatches.forEach(Http3ConnectionCache.this::openReserved);
            }
            if (starts != null) {
                starts.forEach(ReservationAttempt::start);
            }
        }
    }

    private final class PendingRequest {
        private static final int WAITING = 0;
        private static final int CLAIMED = 1;
        private static final int MATERIALIZING = 2;
        private static final int OPENING = 3;
        private static final int RETRYING = 4;
        private static final int COMPLETED = 5;
        private static final int CANCELED = 6;
        private static final int TIMED_OUT = 7;
        private static final int FAILED = 8;

        private final Http3ExchangeClient.ConnectionConfig config;
        private final SessionPool pool;
        private final Http3ExchangeClient.RequestData request;
        private final Executor responseExecutor;
        private final CompletableFuture<Http3ExchangeClient.RequestStream> result;
        private final CompletableFuture<Void> deadline = new CompletableFuture<>();
        private final AtomicInteger state = new AtomicInteger(WAITING);
        private final long deadlineNanos;

        private PendingRequest(Http3ExchangeClient.ConnectionConfig config,
                               SessionPool pool,
                               Http3ExchangeClient.RequestData request,
                               Executor responseExecutor,
                               CompletableFuture<Http3ExchangeClient.RequestStream> result) {
            this.config = config;
            this.pool = pool;
            this.request = request;
            this.responseExecutor = responseExecutor;
            this.result = result;
            this.deadlineNanos = System.nanoTime() + config.streamOpenTimeout().toNanos();
        }

        private boolean waiting() {
            return state.get() == WAITING;
        }

        private boolean deadlineReached() {
            return deadlineNanos - System.nanoTime() <= 0;
        }

        private Duration remaining() {
            long remainingNanos = deadlineNanos - System.nanoTime();
            return remainingNanos <= 0 ? Duration.ZERO : Duration.ofNanos(remainingNanos);
        }

        private void startDeadline() {
            if (!waiting()) {
                return;
            }
            Duration remaining = remaining();
            if (remaining.isZero()) {
                timeout();
                return;
            }
            deadline.orTimeout(remaining.toNanos(), TimeUnit.NANOSECONDS)
                    .whenComplete((_, throwable) -> {
                        if (throwable instanceof TimeoutException) {
                            timeout();
                        }
                    });
        }

        private boolean markClaimed() {
            return state.compareAndSet(WAITING, CLAIMED);
        }

        private boolean startMaterialization() {
            return state.compareAndSet(CLAIMED, MATERIALIZING);
        }

        private boolean startOpen() {
            return state.compareAndSet(MATERIALIZING, OPENING);
        }

        private boolean markRetrying() {
            return transitionTo(RETRYING);
        }

        private boolean markTimedOut() {
            return transitionTo(TIMED_OUT);
        }

        private boolean markFailed() {
            return transitionTo(FAILED);
        }

        private boolean transitionTo(int target) {
            while (true) {
                int current = state.get();
                if (current >= RETRYING) {
                    return false;
                }
                if (state.compareAndSet(current, target)) {
                    return true;
                }
            }
        }

        private void cancel() {
            if (transitionTo(CANCELED)) {
                pool.waiterTerminated(this, false).execute();
                deadline.complete(null);
            }
        }

        private void timeout() {
            PoolActions actions = pool.timeout(this);
            actions.execute();
        }

        private void fail(Throwable failure) {
            if (markFailed()) {
                pool.waiterTerminated(this, false).execute();
                completeFailure(failure);
            }
        }

        private void completeFailure(Throwable failure) {
            deadline.complete(null);
            result.completeExceptionally(failure);
        }

        private void completeTimeout(Http3ExchangeClient.ConnectionSession session) {
            deadline.complete(null);
            Throwable cause = new QuicStreamLimitException("Timed out waiting for bidirectional stream credit");
            if (session == null) {
                result.completeExceptionally(cause);
            } else {
                result.completeExceptionally(new Http3ExchangeClient.RequestStreamOpenException(session, cause));
            }
        }

        private void performRetry() {
            deadline.complete(null);
            if (!result.isDone()) {
                selectStream(config, pool, request, responseExecutor, result);
            }
        }

        private void complete(SessionSlot slot,
                              Http3ExchangeClient.ConnectionSession session,
                              Http3RequestStream stream) {
            if (deadlineReached()) {
                timeout();
                stream.cancel();
                pool.waiterTerminated(this, true).execute();
                return;
            }
            if (state.get() != OPENING) {
                stream.cancel();
                pool.waiterTerminated(this, true).execute();
                return;
            }
            pool.prefer(slot);
            boolean accepted = result.complete(new Http3ExchangeClient.RequestStream(session, stream));
            state.compareAndSet(OPENING, COMPLETED);
            deadline.complete(null);
            pool.waiterTerminated(this, true).execute();
            if (!accepted) {
                stream.cancel();
            }
        }

        private void failAfterMaterialization(Throwable failure) {
            if (transitionTo(FAILED)) {
                pool.waiterTerminated(this, true).execute();
                completeFailure(failure);
            } else {
                pool.waiterTerminated(this, true).execute();
            }
        }
    }

    private final class SessionPool extends SessionPoolOwner {
        private final CacheKey cacheKey;
        private final long epoch;
        private final int capacity;
        private final List<SessionSlot> slots = new ArrayList<>();
        // PendingRequest uses identity equality; preserve FIFO without scanning on individual removal.
        private final SequencedSet<PendingRequest> queuedRequests = new LinkedHashSet<>();
        private final Set<PendingRequest> activeRequests = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Map<SessionSlot, ReservationAttempt> reservationAttempts = new IdentityHashMap<>();
        private final Map<SessionSlot, ClaimedReservation> claimedReservations = new IdentityHashMap<>();
        private final Map<PendingRequest, ClaimedReservation> waiterReservations = new IdentityHashMap<>();
        private final Map<SessionSlot, ClaimedReservation> materializingReservations = new IdentityHashMap<>();
        private final Map<PendingRequest, ClaimedReservation> materializingRequests = new IdentityHashMap<>();
        private final AtomicInteger queuedRequestCount = new AtomicInteger();
        private final AtomicReference<SessionSlot> preferred = new AtomicReference<>();
        private final AtomicBoolean detached = new AtomicBoolean();
        private final ReentrantLock lock = new ReentrantLock();
        private TlsGenerationState tlsGenerationState;
        private long revision;

        private SessionPool(CacheKey cacheKey, long epoch, int capacity) {
            this.cacheKey = cacheKey;
            this.epoch = epoch;
            this.capacity = validateConnectionCacheSize(capacity);
        }

        @Override
        CacheKey cacheKey() {
            return cacheKey;
        }

        @Override
        boolean removeSlot(SessionSlot slot) {
            SlotRemoval removal = remove(slot);
            removal.actions().execute();
            return removal.removed();
        }

        private boolean available(long currentEpoch) {
            return epoch == currentEpoch && !detached.get();
        }

        private SessionSlot preferred(long currentEpoch) {
            SessionSlot slot = preferred.get();
            return available(currentEpoch) && slot != null && slot.readyUsable(currentEpoch) ? slot : null;
        }

        private ReadySlots readySlots(SessionSlot excluded, long currentEpoch) {
            lock.lock();
            try {
                if (!available(currentEpoch)) {
                    return new ReadySlots(List.of(), revision);
                }
                List<SessionSlot> ready = new ArrayList<>(slots.size());
                for (SessionSlot slot : slots) {
                    if (slot != excluded && slot.readyUsable(currentEpoch)) {
                        ready.add(slot);
                    }
                }
                return new ReadySlots(List.copyOf(ready), revision);
            } finally {
                lock.unlock();
            }
        }

        private boolean hasSession(long currentEpoch) {
            if (preferred(currentEpoch) != null) {
                return true;
            }
            lock.lock();
            try {
                if (!available(currentEpoch)) {
                    return false;
                }
                for (SessionSlot slot : slots) {
                    if (slot.available(currentEpoch)) {
                        return true;
                    }
                }
                return false;
            } finally {
                lock.unlock();
            }
        }

        private boolean hasQueuedRequests(long currentEpoch) {
            return available(currentEpoch) && queuedRequestCount.get() != 0;
        }

        private SlotReservation reserve(long currentEpoch,
                                        long expectedRevision,
                                        Supplier<TlsGenerationState> generationStateSupplier) {
            PoolActions actions = new PoolActions();
            lock.lock();
            try {
                if (!available(currentEpoch)) {
                    throw new IllegalStateException("HTTP/3 session pool is unavailable");
                }
                if (revision != expectedRevision) {
                    return new SlotReservation(ReservationKind.RETRY, null, actions);
                }
                SessionSlot pending = null;
                boolean hasReadySession = false;
                boolean removed = false;
                for (SessionSlot slot : List.copyOf(slots)) {
                    if (slot.detached()) {
                        removed |= removeSlotLocked(slot, actions);
                        continue;
                    }
                    if (slot.readyPending()) {
                        if (pending == null) {
                            pending = slot;
                        }
                        continue;
                    }
                    Http3ExchangeClient.ConnectionSession session = slot.session();
                    if (!slot.readyFailed() && session != null && session.isUsable()) {
                        hasReadySession = true;
                        continue;
                    }
                    if (removeSlotLocked(slot, actions)) {
                        removed = true;
                        actions.retire(slot, "session-unusable");
                    }
                }
                if (removed) {
                    drainQueuedForRetryLocked(actions);
                }
                if (pending != null) {
                    return new SlotReservation(hasReadySession ? ReservationKind.FULL : ReservationKind.PENDING,
                                               pending,
                                               actions);
                }
                if (slots.size() < capacity) {
                    TlsGenerationState generationState = tlsGenerationState;
                    if (generationState == null) {
                        generationState = generationStateSupplier.get();
                        tlsGenerationState = generationState;
                    }
                    SessionSlot candidate = new SessionSlot(this, currentEpoch, generationState);
                    slots.add(candidate);
                    revision++;
                    return new SlotReservation(ReservationKind.NEW, candidate, actions);
                }
                return new SlotReservation(ReservationKind.FULL, null, actions);
            } finally {
                lock.unlock();
            }
        }

        private PoolActions enqueue(PendingRequest pending, long currentEpoch) {
            PoolActions actions = new PoolActions();
            lock.lock();
            try {
                if (!available(currentEpoch)) {
                    if (pending.markFailed()) {
                        actions.fail(pending, new IllegalStateException("HTTP/3 session pool is unavailable"));
                    }
                    return actions;
                }
                if (!pending.waiting()) {
                    return actions;
                }
                activeRequests.add(pending);
                if (queuedRequests.add(pending)) {
                    queuedRequestCount.incrementAndGet();
                }
                addReservationAttemptsLocked(actions, currentEpoch);
                return actions;
            } finally {
                lock.unlock();
            }
        }

        private PoolActions reservationCompleted(ReservationAttempt attempt,
                                                 QuicBidiStreamReservation reservation,
                                                 Throwable throwable) {
            PoolActions actions = new PoolActions();
            lock.lock();
            try {
                SessionSlot slot = attempt.slot;
                if (reservationAttempts.get(slot) != attempt) {
                    if (reservation != null) {
                        actions.close(reservation);
                    }
                    return actions;
                }
                reservationAttempts.remove(slot);
                if (detached.get() || !slots.contains(slot) || slot.detached()) {
                    if (reservation != null) {
                        actions.close(reservation);
                    }
                    return actions;
                }
                if (throwable != null) {
                    Throwable cause = Http3ExchangeClient.unwrap(throwable);
                    if (cause instanceof CancellationException
                            && slot.readyUsable(epoch)) {
                        addReservationAttemptsLocked(actions, epoch);
                    } else if (removeSlotLocked(slot, actions)) {
                        actions.retire(slot, "stream-credit-reservation-failed");
                        drainQueuedForRetryLocked(actions);
                    }
                    return actions;
                }
                PendingRequest pending = firstLiveWaiterLocked(actions);
                if (pending == null) {
                    actions.close(reservation);
                } else {
                    ClaimedReservation claimed = new ClaimedReservation(pending, slot, reservation);
                    claimedReservations.put(slot, claimed);
                    waiterReservations.put(pending, claimed);
                    actions.dispatch(claimed);
                }
                addReservationAttemptsLocked(actions, epoch);
                return actions;
            } finally {
                lock.unlock();
            }
        }

        private boolean beginMaterialization(ClaimedReservation claimed) {
            lock.lock();
            try {
                if (claimedReservations.get(claimed.slot()) != claimed
                        || waiterReservations.get(claimed.pending()) != claimed) {
                    return false;
                }
                claimedReservations.remove(claimed.slot());
                waiterReservations.remove(claimed.pending());
                if (detached.get()
                        || !activeRequests.contains(claimed.pending())
                        || !claimed.pending().startMaterialization()) {
                    return false;
                }
                materializingReservations.put(claimed.slot(), claimed);
                materializingRequests.put(claimed.pending(), claimed);
                return true;
            } finally {
                lock.unlock();
            }
        }

        private boolean beginOpen(ClaimedReservation claimed) {
            lock.lock();
            try {
                if (materializingReservations.get(claimed.slot()) != claimed
                        || materializingRequests.get(claimed.pending()) != claimed) {
                    return false;
                }
                materializingReservations.remove(claimed.slot());
                materializingRequests.remove(claimed.pending());
                return !detached.get()
                        && slots.contains(claimed.slot())
                        && claimed.pending().startOpen();
            } finally {
                lock.unlock();
            }
        }

        private PoolActions waiterTerminated(PendingRequest pending, boolean materializationComplete) {
            PoolActions actions = new PoolActions();
            lock.lock();
            try {
                removeQueuedRequestLocked(pending);
                activeRequests.remove(pending);
                ClaimedReservation claimed = waiterReservations.remove(pending);
                if (claimed != null) {
                    claimedReservations.remove(claimed.slot(), claimed);
                    actions.close(claimed.reservation());
                }
                if (materializationComplete) {
                    ClaimedReservation materializing = materializingRequests.remove(pending);
                    if (materializing != null) {
                        materializingReservations.remove(materializing.slot(), materializing);
                        actions.close(materializing.reservation());
                    }
                }
                if (detached.get()) {
                    cancelReservationAttemptsLocked(actions);
                } else {
                    addReservationAttemptsLocked(actions, epoch);
                }
                return actions;
            } finally {
                lock.unlock();
            }
        }

        private PoolActions timeout(PendingRequest pending) {
            PoolActions actions = new PoolActions();
            lock.lock();
            try {
                if (!pending.markTimedOut()) {
                    return actions;
                }
                Http3ExchangeClient.ConnectionSession session = representativeSessionLocked();
                removeQueuedRequestLocked(pending);
                activeRequests.remove(pending);
                ClaimedReservation claimed = waiterReservations.remove(pending);
                if (claimed != null) {
                    claimedReservations.remove(claimed.slot(), claimed);
                    actions.close(claimed.reservation());
                }
                actions.timeout(pending, session);
                if (detached.get()) {
                    cancelReservationAttemptsLocked(actions);
                } else {
                    addReservationAttemptsLocked(actions, epoch);
                }
                return actions;
            } finally {
                lock.unlock();
            }
        }

        private SlotRemoval remove(SessionSlot slot) {
            PoolActions actions = new PoolActions();
            boolean removed;
            lock.lock();
            try {
                removed = removeSlotLocked(slot, actions);
                if (removed) {
                    drainQueuedForRetryLocked(actions);
                }
            } finally {
                lock.unlock();
            }
            return new SlotRemoval(removed, actions);
        }

        private boolean removeSlotLocked(SessionSlot slot, PoolActions actions) {
            if (!slots.remove(slot)) {
                return false;
            }
            preferred.compareAndSet(slot, null);
            slot.detach();
            revision++;
            ReservationAttempt attempt = reservationAttempts.remove(slot);
            if (attempt != null) {
                actions.cancel(attempt);
            }
            ClaimedReservation claimed = claimedReservations.remove(slot);
            if (claimed != null) {
                waiterReservations.remove(claimed.pending(), claimed);
                actions.close(claimed.reservation());
                if (claimed.pending().markRetrying()) {
                    activeRequests.remove(claimed.pending());
                    actions.retry(claimed.pending());
                }
            }
            ClaimedReservation materializing = materializingReservations.remove(slot);
            if (materializing != null) {
                materializingRequests.remove(materializing.pending(), materializing);
                actions.close(materializing.reservation());
                if (materializing.pending().markRetrying()) {
                    activeRequests.remove(materializing.pending());
                    actions.retry(materializing.pending());
                }
            }
            return true;
        }

        private PendingRequest firstLiveWaiterLocked(PoolActions actions) {
            while (true) {
                PendingRequest pending = pollQueuedRequestLocked();
                if (pending == null) {
                    return null;
                }
                if (pending.deadlineReached()) {
                    activeRequests.remove(pending);
                    if (pending.markTimedOut()) {
                        actions.timeout(pending, representativeSessionLocked());
                    }
                    continue;
                }
                if (pending.markClaimed()) {
                    return pending;
                }
                activeRequests.remove(pending);
            }
        }

        private Http3ExchangeClient.ConnectionSession representativeSessionLocked() {
            SessionSlot preferredSlot = preferred.get();
            if (preferredSlot != null && preferredSlot.readyUsable(epoch)) {
                return preferredSlot.session();
            }
            for (SessionSlot slot : slots) {
                if (slot.readyUsable(epoch)) {
                    return slot.session();
                }
            }
            return null;
        }

        private void addReservationAttemptsLocked(PoolActions actions, long currentEpoch) {
            if (queuedRequests.isEmpty()) {
                cancelReservationAttemptsLocked(actions);
                return;
            }
            for (SessionSlot slot : slots) {
                if (!slot.readyUsable(currentEpoch)) {
                    continue;
                }
                if (!reservationAttempts.containsKey(slot)
                        && !claimedReservations.containsKey(slot)
                        && !materializingReservations.containsKey(slot)) {
                    ReservationAttempt attempt = new ReservationAttempt(this, slot);
                    reservationAttempts.put(slot, attempt);
                    actions.start(attempt);
                }
            }
        }

        private void cancelReservationAttemptsLocked(PoolActions actions) {
            if (reservationAttempts.isEmpty()) {
                return;
            }
            reservationAttempts.values().forEach(actions::cancel);
            reservationAttempts.clear();
        }

        private void drainQueuedForRetryLocked(PoolActions actions) {
            while (!queuedRequests.isEmpty()) {
                PendingRequest pending = pollQueuedRequestLocked();
                if (pending.markRetrying()) {
                    activeRequests.remove(pending);
                    actions.retry(pending);
                }
            }
            cancelReservationAttemptsLocked(actions);
        }

        private PendingRequest pollQueuedRequestLocked() {
            if (queuedRequests.isEmpty()) {
                return null;
            }
            PendingRequest pending = queuedRequests.removeFirst();
            queuedRequestCount.decrementAndGet();
            return pending;
        }

        private void removeQueuedRequestLocked(PendingRequest pending) {
            if (queuedRequests.remove(pending)) {
                queuedRequestCount.decrementAndGet();
            }
        }

        private void candidateReady(SessionSlot candidate) {
            lock.lock();
            try {
                if (!detached.get() && slots.contains(candidate) && !candidate.detached()) {
                    revision++;
                }
            } finally {
                lock.unlock();
            }
        }

        private PoolActions armReadySession(SessionSlot candidate) {
            PoolActions actions = new PoolActions();
            lock.lock();
            try {
                if (!detached.get() && slots.contains(candidate) && candidate.readyUsable(epoch)) {
                    addReservationAttemptsLocked(actions, epoch);
                }
            } finally {
                lock.unlock();
            }
            return actions;
        }

        private void prefer(SessionSlot slot) {
            if (!detached.get() && !slot.detached()) {
                preferred.set(slot);
                if (detached.get() || slot.detached()) {
                    preferred.compareAndSet(slot, null);
                }
            }
        }

        private PoolDetach detachAll() {
            if (!detached.compareAndSet(false, true)) {
                return new PoolDetach(List.of(), new PoolActions());
            }
            PoolActions actions = new PoolActions();
            lock.lock();
            try {
                List<SessionSlot> detachedSlots = List.copyOf(slots);
                slots.clear();
                preferred.set(null);
                tlsGenerationState = null;
                revision++;
                detachedSlots.forEach(SessionSlot::detach);
                cancelReservationAttemptsLocked(actions);
                claimedReservations.values().forEach(claimed -> actions.close(claimed.reservation()));
                claimedReservations.clear();
                waiterReservations.clear();
                materializingReservations.values().forEach(claimed -> actions.close(claimed.reservation()));
                materializingReservations.clear();
                materializingRequests.clear();
                queuedRequests.clear();
                queuedRequestCount.set(0);
                for (PendingRequest pending : activeRequests) {
                    if (pending.markFailed()) {
                        actions.fail(pending, new IllegalStateException("HTTP/3 session pool is unavailable"));
                    }
                }
                activeRequests.clear();
                return new PoolDetach(detachedSlots, actions);
            } finally {
                lock.unlock();
            }
        }

        @Override
        void clearTlsGenerationState(TlsGenerationState expected) {
            lock.lock();
            try {
                if (tlsGenerationState == expected) {
                    tlsGenerationState = null;
                }
            } finally {
                lock.unlock();
            }
        }
    }

}
