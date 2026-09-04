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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.quic.QuicClientInitialTokenCache;
import io.helidon.quic.QuicClientTlsSessionCache;

import static io.helidon.webclient.http3.Http3RequestFailureSupport.collectCleanupFailure;

final class Http3ConnectionCacheSupport {
    private Http3ConnectionCacheSupport() {
    }

    abstract static class SessionPoolOwner {
        abstract Http3ConnectionCache.CacheKey cacheKey();

        abstract boolean removeSlot(SessionSlot slot);

        abstract void clearTlsGenerationState(TlsGenerationState expected);
    }

    static final class TlsGenerationState implements AutoCloseable {
        private final QuicClientTlsSessionCache sessionCache;
        private final boolean retained;
        private final AtomicBoolean closed = new AtomicBoolean();
        private int sessionCount;

        TlsGenerationState(QuicClientTlsSessionCache sessionCache, boolean retained) {
            this.sessionCache = sessionCache;
            this.retained = retained;
        }

        QuicClientTlsSessionCache sessionCache() {
            return sessionCache;
        }

        boolean retained() {
            return retained;
        }

        boolean hasNoSessions() {
            return sessionCount == 0;
        }

        void retainSession() {
            sessionCount++;
        }

        boolean releaseSession() {
            if (sessionCount == 0) {
                throw new IllegalStateException("HTTP/3 TLS generation session count underflow");
            }
            return --sessionCount == 0;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                sessionCache.close();
            }
        }
    }

    static final class InitialTokenState {
        private final QuicClientInitialTokenCache cache;
        private boolean retained;
        private int leases;

        InitialTokenState(QuicClientInitialTokenCache cache, boolean retained) {
            this.cache = cache;
            this.retained = retained;
        }

        QuicClientInitialTokenCache cache() {
            return cache;
        }

        boolean hasNoLeases() {
            return leases == 0;
        }

        void retain() {
            leases++;
        }

        void discard() {
            retained = false;
            if (leases == 0) {
                cache.close();
            }
        }

        void release() {
            if (leases == 0) {
                throw new IllegalStateException("QUIC Initial token cache lease underflow");
            }
            leases--;
            if (!retained && leases == 0) {
                cache.close();
            }
        }
    }

    static final class InitialTokenLease implements AutoCloseable {
        private final Http3ConnectionCache owner;
        private final InitialTokenState state;
        private final AtomicBoolean closed = new AtomicBoolean();

        InitialTokenLease(Http3ConnectionCache owner, InitialTokenState state) {
            this.owner = owner;
            this.state = state;
        }

        QuicClientInitialTokenCache cache() {
            return state.cache();
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                owner.releaseInitialTokenState(state);
            }
        }
    }

    static final class SessionSlot {
        private final SessionPoolOwner pool;
        private final long epoch;
        private final TlsGenerationState tlsGenerationState;
        private final CompletableFuture<Http3ExchangeClient.ConnectionSession> ready = new CompletableFuture<>();
        private final CompletableFuture<Void> termination = new CompletableFuture<>();
        private final CompletableFuture<Void> closeOutcome = new CompletableFuture<>();
        private final CompletableFuture<Optional<Http3ExchangeClient.ConnectionSession>> ownership =
                new CompletableFuture<>();
        private final AtomicReference<Http3ExchangeClient.ConnectionSession> session = new AtomicReference<>();
        private final AtomicBoolean closeStarted = new AtomicBoolean();
        private final AtomicBoolean detached = new AtomicBoolean();
        private final AtomicBoolean tlsGenerationReleased = new AtomicBoolean();
        private volatile Http3ExchangeClient.ConnectionSession installedSession;
        private volatile Thread creationThread;

        SessionSlot(SessionPoolOwner pool, long epoch, TlsGenerationState tlsGenerationState) {
            this.pool = pool;
            this.epoch = epoch;
            this.tlsGenerationState = tlsGenerationState;
            this.creationThread = Thread.currentThread();
        }

        SessionPoolOwner pool() {
            return pool;
        }

        long epoch() {
            return epoch;
        }

        TlsGenerationState tlsGenerationState() {
            return tlsGenerationState;
        }

        CompletableFuture<Http3ExchangeClient.ConnectionSession> ready() {
            return ready;
        }

        boolean readyPending() {
            return !ready.isDone();
        }

        boolean readyFailed() {
            return ready.isCompletedExceptionally();
        }

        Http3ExchangeClient.ConnectionSession session() {
            return session.get();
        }

        Http3ExchangeClient.ConnectionSession installedSession() {
            return installedSession;
        }

        boolean detached() {
            return detached.get();
        }

        boolean creationPendingOn(Thread thread) {
            return !ownership.isDone() && creationThread == thread;
        }

        boolean releaseTlsGeneration() {
            return tlsGenerationReleased.compareAndSet(false, true);
        }

        boolean available(long currentEpoch) {
            if (epoch != currentEpoch || detached.get()) {
                return false;
            }
            Http3ExchangeClient.ConnectionSession current = session.get();
            return current == null ? !ready.isDone() : current.isUsable();
        }

        boolean readyUsable(long currentEpoch) {
            if (epoch != currentEpoch || detached.get() || !ready.isDone() || ready.isCompletedExceptionally()) {
                return false;
            }
            Http3ExchangeClient.ConnectionSession current = session.get();
            return current != null && current.isUsable();
        }

        boolean install(Http3ExchangeClient.ConnectionSession created) {
            if (!session.compareAndSet(null, created)) {
                throw new IllegalStateException("HTTP/3 connection cache entry already owns a session");
            }
            installedSession = created;
            if (!resolveOwnership(Optional.of(created))) {
                session.compareAndSet(created, null);
                throw new IllegalStateException("HTTP/3 connection cache entry ownership is already resolved");
            }
            if (detached.get()) {
                session.compareAndSet(created, null);
                return false;
            }
            return true;
        }

        void complete(Http3ExchangeClient.ConnectionSession readySession) {
            if (detached.get()) {
                ready.completeExceptionally(new IllegalStateException("HTTP/3 connection cache entry is closed"));
            } else {
                ready.complete(readySession);
            }
        }

        void creationFailed() {
            resolveOwnership(Optional.empty());
        }

        private boolean resolveOwnership(Optional<Http3ExchangeClient.ConnectionSession> ownedSession) {
            try {
                return ownership.complete(ownedSession);
            } finally {
                creationThread = null;
            }
        }

        void fail(Throwable throwable) {
            ready.completeExceptionally(throwable);
            close();
        }

        void completeTermination(Throwable failure) {
            if (failure == null) {
                termination.complete(null);
            } else {
                termination.completeExceptionally(failure);
            }
        }

        CompletionStage<Void> close() {
            detach();
            ready.completeExceptionally(new IllegalStateException("HTTP/3 connection cache entry is closed"));
            if (closeStarted.compareAndSet(false, true)) {
                ownership.whenComplete((ownedSession, ownershipFailure) -> {
                    Throwable closeFailure = ownershipFailure;
                    Http3ExchangeClient.ConnectionSession current = ownedSession.orElse(null);
                    if (current != null) {
                        session.compareAndSet(current, null);
                        try {
                            current.close();
                        } catch (Throwable failure) {
                            closeFailure = collectCleanupFailure(closeFailure, failure);
                        }
                    }
                    Throwable initialFailure = closeFailure;
                    termination.whenComplete((_, terminationFailure) -> {
                        Throwable failure = initialFailure;
                        if (terminationFailure != null) {
                            failure = collectCleanupFailure(failure, terminationFailure);
                        }
                        if (failure == null) {
                            closeOutcome.complete(null);
                        } else {
                            closeOutcome.completeExceptionally(failure);
                        }
                    });
                });
            }
            return closeOutcome;
        }

        void detach() {
            detached.set(true);
        }
    }
}
