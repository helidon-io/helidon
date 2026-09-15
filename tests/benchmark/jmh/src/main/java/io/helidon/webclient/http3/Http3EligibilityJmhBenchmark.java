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

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.tls.Tls;
import io.helidon.quic.QuicTLSContext;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.ThreadParams;

/**
 * Benchmarks the HTTP/3 route and TLS eligibility lookup used before a request is dispatched.
 *
 * <p>The route-count parameter verifies that request cost is independent of the shared cache cardinality. The benchmark
 * also varies the number of clients and per-request TLS identities. A separate cold-route benchmark covers the
 * unchanged-generation miss path with 10,000 routes sharing one TLS identity. Trial setup and teardown verify observable
 * session-index behavior and benchmark-owned compatibility-check counts.
 */
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class Http3EligibilityJmhBenchmark {
    /**
     * Measures eligibility throughput under concurrent shared-cache access.
     *
     * @param state shared route and client state
     * @param cursor thread-local route cursor
     * @return selected cached session
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public Object eligibilityThroughput(EligibilityState state, RouteCursor cursor) {
        return lookup(state, cursor);
    }

    /**
     * Samples eligibility latency so the JMH result includes tail percentiles.
     *
     * @param state shared route and client state
     * @param cursor thread-local route cursor
     * @return selected cached session
     */
    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public Object eligibilityTailLatency(EligibilityState state, RouteCursor cursor) {
        return lookup(state, cursor);
    }

    /**
     * Measures a cold route insertion after the unchanged TLS generation check on a full 10,000-route TLS bucket.
     *
     * @param state shared cold-route state
     * @return evicted cached session
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public Object unchangedGenerationColdRouteThroughput(ColdRouteState state) {
        state.mutationLock.lock();
        try {
            long generation = state.tls.generation();
            if (!state.sessions.removeStaleTlsGeneration(state.tlsKey, generation).isEmpty()) {
                throw new IllegalStateException("HTTP/3 benchmark retired an unchanged TLS generation");
            }
            int route = state.nextRoute++;
            CacheKey cacheKey = new CacheKey(route);
            Session session = new Session(route);
            Http3SessionIndex.Insertion<CacheKey, Session> insertion = state.sessions.putIfAbsent(
                    cacheKey,
                    session,
                    new RouteKey(route),
                    state.tlsKey,
                    generation);
            Http3SessionIndex.Entry<CacheKey, Session> evicted = insertion.evicted();
            if (insertion.existing() != null
                    || evicted == null
                    || evicted.key().route() != state.oldestRoute
                    || evicted.value().route() != state.oldestRoute
                    || state.sessions.get(cacheKey) != session) {
                throw new IllegalStateException("HTTP/3 benchmark cold-route insertion changed index behavior");
            }
            state.oldestRoute++;
            return evicted.value();
        } finally {
            state.mutationLock.unlock();
        }
    }

    private static Object lookup(EligibilityState state, RouteCursor cursor) {
        int routeIndex = cursor.next(state.routeCount);
        Session session = state.sessions.get(state.cacheKeys[routeIndex]);
        ClientFixture client = state.clients[state.clientByRoute[routeIndex]];
        Tls tls = client.tlsContexts[state.tlsByRoute[routeIndex]];
        if (session == null || !client.compatibility.compatible(tls)) {
            throw new IllegalStateException("HTTP/3 benchmark eligibility lookup failed");
        }
        return session;
    }

    /**
     * Shared bounded cache and client-local TLS compatibility state.
     */
    @State(Scope.Benchmark)
    public static class EligibilityState {
        /**
         * Number of routes retained in the shared session index.
         */
        @Param({"1000", "10000"})
        public int routeCount;

        /**
         * Number of clients sharing the session index.
         */
        @Param({"1", "8"})
        public int clientCount;

        /**
         * Number of request-specific TLS identities retained by each client.
         */
        @Param({"1", "8"})
        public int tlsContextsPerClient;

        private Http3SessionIndex<CacheKey, Session, RouteKey, TlsIdentityKey> sessions;
        private CacheKey[] cacheKeys;
        private ClientFixture[] clients;
        private int[] clientByRoute;
        private int[] tlsByRoute;

        /**
         * Populates all bounded indexes and warms each client-local TLS entry.
         */
        @Setup(Level.Trial)
        public void setUp() {
            int tlsContextCount = Math.multiplyExact(clientCount, tlsContextsPerClient);
            if (routeCount > 10_000 || routeCount < tlsContextCount) {
                throw new IllegalArgumentException(
                        "HTTP/3 benchmark route count must cover TLS contexts and not exceed 10000");
            }

            clients = new ClientFixture[clientCount];
            for (int clientIndex = 0; clientIndex < clients.length; clientIndex++) {
                AtomicInteger compatibilityChecks = new AtomicInteger();
                Http3TlsCompatibility compatibility = new Http3TlsCompatibility(tls -> {
                    compatibilityChecks.incrementAndGet();
                    return QuicTLSContext.isQuicCompatible(tls);
                });
                Tls[] tlsContexts = new Tls[tlsContextsPerClient];
                TlsIdentityKey[] tlsKeys = new TlsIdentityKey[tlsContextsPerClient];
                for (int tlsIndex = 0; tlsIndex < tlsContexts.length; tlsIndex++) {
                    Tls tls = Tls.builder().build();
                    if (!compatibility.compatible(tls)) {
                        throw new IllegalStateException("HTTP/3 benchmark TLS context is not QUIC compatible");
                    }
                    tlsContexts[tlsIndex] = tls;
                    tlsKeys[tlsIndex] = new TlsIdentityKey(tls);
                }
                clients[clientIndex] = new ClientFixture(compatibility,
                                                         compatibilityChecks,
                                                         tlsContexts,
                                                         tlsKeys);
            }

            sessions = new Http3SessionIndex<>(10_000);
            cacheKeys = new CacheKey[routeCount];
            clientByRoute = new int[routeCount];
            tlsByRoute = new int[routeCount];
            for (int routeIndex = 0; routeIndex < routeCount; routeIndex++) {
                int clientIndex = routeIndex % clientCount;
                int tlsIndex = routeIndex / clientCount % tlsContextsPerClient;
                CacheKey cacheKey = new CacheKey(routeIndex);
                ClientFixture client = clients[clientIndex];
                Http3SessionIndex.Insertion<CacheKey, Session> insertion =
                        sessions.putIfAbsent(cacheKey,
                                             new Session(routeIndex),
                                             new RouteKey(routeIndex),
                                             client.tlsKeys[tlsIndex],
                                             client.tlsContexts[tlsIndex].generation());
                if (insertion.existing() != null || insertion.evicted() != null) {
                    throw new IllegalStateException("HTTP/3 benchmark session index unexpectedly replaced a route");
                }
                cacheKeys[routeIndex] = cacheKey;
                clientByRoute[routeIndex] = clientIndex;
                tlsByRoute[routeIndex] = tlsIndex;
            }

            assertObservableState();
        }

        /**
         * Verifies reads did not change observable session or TLS state.
         */
        @TearDown(Level.Trial)
        public void tearDown() {
            assertObservableState();

            List<Http3SessionIndex.Entry<CacheKey, Session>> routeRemoval = sessions.removeRoute(new RouteKey(0));
            if (routeRemoval.size() != 1
                    || routeRemoval.getFirst().key() != cacheKeys[0]
                    || routeRemoval.getFirst().value().route() != 0
                    || sessions.get(cacheKeys[0]) != null) {
                throw new IllegalStateException("HTTP/3 benchmark route index changed");
            }
            int expectedTlsRemovals = 0;
            for (int route = 1; route < routeCount; route++) {
                if (clientByRoute[route] == 0 && tlsByRoute[route] == 0) {
                    expectedTlsRemovals++;
                }
            }
            Tls firstTls = clients[0].tlsContexts[0];
            List<Http3SessionIndex.Entry<CacheKey, Session>> tlsRemoval = sessions.removeStaleTlsGeneration(
                    clients[0].tlsKeys[0],
                    firstTls.generation() + 1);
            if (tlsRemoval.size() != expectedTlsRemovals) {
                throw new IllegalStateException("HTTP/3 benchmark TLS index changed");
            }

            for (ClientFixture client : clients) {
                client.compatibility.close();
            }
        }

        private void assertObservableState() {
            for (int route = 0; route < routeCount; route++) {
                Session session = sessions.get(cacheKeys[route]);
                if (session == null || session.route() != route) {
                    throw new IllegalStateException("HTTP/3 benchmark session index changed");
                }
            }
            for (ClientFixture client : clients) {
                for (Tls tls : client.tlsContexts) {
                    if (!client.compatibility.compatible(tls)) {
                        throw new IllegalStateException("HTTP/3 benchmark TLS compatibility result changed");
                    }
                }
                if (client.compatibilityChecks.get() != tlsContextsPerClient) {
                    throw new IllegalStateException("HTTP/3 benchmark TLS compatibility cache changed");
                }
            }
        }
    }

    /**
     * Shared full session index for the unchanged-generation cold-route path.
     */
    @State(Scope.Benchmark)
    public static class ColdRouteState {
        private static final int ROUTE_COUNT = 10_000;

        private final ReentrantLock mutationLock = new ReentrantLock();
        private Http3SessionIndex<CacheKey, Session, RouteKey, TlsIdentityKey> sessions;
        private Tls tls;
        private TlsIdentityKey tlsKey;
        private int oldestRoute;
        private int nextRoute;

        /**
         * Populates one full TLS bucket with 10,000 independently addressable routes.
         */
        @Setup(Level.Trial)
        public void setUp() {
            tls = Tls.builder().build();
            tlsKey = new TlsIdentityKey(tls);
            sessions = new Http3SessionIndex<>(ROUTE_COUNT);
            long generation = tls.generation();
            for (int route = 0; route < ROUTE_COUNT; route++) {
                Http3SessionIndex.Insertion<CacheKey, Session> insertion = sessions.putIfAbsent(
                        new CacheKey(route),
                        new Session(route),
                        new RouteKey(route),
                        tlsKey,
                        generation);
                if (insertion.existing() != null || insertion.evicted() != null) {
                    throw new IllegalStateException("HTTP/3 benchmark cold-route index replaced a setup route");
                }
            }
            oldestRoute = 0;
            nextRoute = ROUTE_COUNT;
            assertObservableState();
        }

        /**
         * Verifies per-key, route-index, and TLS-index behavior after measured mutations.
         */
        @TearDown(Level.Trial)
        public void tearDown() {
            assertObservableState();

            List<Http3SessionIndex.Entry<CacheKey, Session>> routeRemoval =
                    sessions.removeRoute(new RouteKey(oldestRoute));
            if (routeRemoval.size() != 1
                    || routeRemoval.getFirst().key().route() != oldestRoute
                    || routeRemoval.getFirst().value().route() != oldestRoute) {
                throw new IllegalStateException("HTTP/3 benchmark cold-route index changed");
            }
            List<Http3SessionIndex.Entry<CacheKey, Session>> tlsRemoval = sessions.removeStaleTlsGeneration(
                    tlsKey,
                    tls.generation() + 1);
            if (tlsRemoval.size() != ROUTE_COUNT - 1) {
                throw new IllegalStateException("HTTP/3 benchmark cold-route TLS index changed");
            }
        }

        private void assertObservableState() {
            if (!sessions.removeStaleTlsGeneration(tlsKey, tls.generation()).isEmpty()) {
                throw new IllegalStateException("HTTP/3 benchmark current cold-route TLS generation changed");
            }
            for (int route = oldestRoute; route < nextRoute; route++) {
                Session session = sessions.get(new CacheKey(route));
                if (session == null || session.route() != route) {
                    throw new IllegalStateException("HTTP/3 benchmark cold-route session index changed");
                }
            }
        }
    }

    /**
     * Thread-local route selection without shared benchmark bookkeeping.
     */
    @State(Scope.Thread)
    public static class RouteCursor {
        private int next;

        /**
         * Assigns a distinct initial route to each JMH worker.
         */
        @Setup(Level.Trial)
        public void setUp(ThreadParams threadParams) {
            next = threadParams.getThreadIndex();
        }

        private int next(int routeCount) {
            int current = next;
            if (current >= routeCount) {
                current = 0;
            }
            next = current + 1 == routeCount ? 0 : current + 1;
            return current;
        }
    }

    private record CacheKey(int route) {
    }

    private record RouteKey(int route) {
    }

    private record Session(int route) {
    }

    private record ClientFixture(Http3TlsCompatibility compatibility,
                                 AtomicInteger compatibilityChecks,
                                 Tls[] tlsContexts,
                                 TlsIdentityKey[] tlsKeys) {
    }

    private static final class TlsIdentityKey {
        private final Tls tls;

        private TlsIdentityKey(Tls tls) {
            this.tls = tls;
        }

        @Override
        public boolean equals(Object object) {
            return this == object || object instanceof TlsIdentityKey other && tls == other.tls;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(tls);
        }
    }
}
