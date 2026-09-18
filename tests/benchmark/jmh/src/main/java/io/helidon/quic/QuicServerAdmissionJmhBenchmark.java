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

package io.helidon.quic;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import io.helidon.common.tls.Tls;
import io.helidon.common.tls.TlsConfig;
import io.helidon.common.tls.TlsManager;
import io.helidon.common.tls.TlsMaterial;
import io.helidon.quic.packet.QuicPacket;

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
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.infra.ThreadParams;

/**
 * Benchmarks the bounded QUIC server handshake-admission and deadline paths.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class QuicServerAdmissionJmhBenchmark {
    private static final String BENCHMARK_KEYSTORE_RESOURCE =
            "/io/helidon/quic/benchmark/server-keystore.p12";
    private static final String BENCHMARK_TRUSTSTORE_RESOURCE =
            "/io/helidon/quic/benchmark/client-truststore.p12";
    private static final String BENCHMARK_KEY_ALIAS = "server";
    private static final String BENCHMARK_STORE_PASSWORD = "changeit";
    private static final int LIFECYCLE_SESSION_CACHE_SIZE = 16;
    static final long LIFECYCLE_TIMEOUT_MILLIS = 10_000;
    static final long INVOCATION_CLEANUP_TIMEOUT_MILLIS = 10_000;
    static final long MINIMUM_LIFECYCLE_RUNNER_TIMEOUT_MILLIS = 70_000;
    private static final QuicServerRuntime.ConnectionPermit NOOP_CONNECTION_PERMIT =
            QuicServerRuntime.ConnectionPermit.accepted(() -> { }, () -> { });
    private static final QuicServerRuntime.ConnectionPermit REJECTED_CONNECTION_PERMIT =
            QuicServerRuntime.ConnectionPermit.rejected();
    private static final QuicCloseCommand BENCHMARK_CLOSE = QuicCloseCommand.silent("benchmark cleanup");
    private static final QuicCloseCommand BENCHMARK_PROTOCOL_CLOSE =
            QuicCloseCommand.transport(QuicTransportErrors.NO_ERROR, "benchmark lifecycle close");
    private static final QuicCloseCommand BENCHMARK_PROTECTED_CLOSE =
            QuicCloseCommand.transport(QuicTransportErrors.NO_ERROR, "benchmark protected cleanup");
    private static final String[] H3_ALPN = {"h3"};

    private enum HandshakeScenario {
        COLD_NO_RETRY(false, false, false),
        COLD_RETRY_TOKEN_DISABLED(false, true, false),
        COLD_RETRY_TOKEN_RETAINED(false, true, true),
        RESUMED_NO_RETRY(true, false, false),
        RESUMED_RETRY_TOKEN_DISABLED(true, true, false),
        RESUMED_RETRY_TOKEN_RETAINED(true, true, true);

        private final boolean resumed;
        private final boolean retryEnabled;
        private final boolean retainInitialToken;

        HandshakeScenario(boolean resumed, boolean retryEnabled, boolean retainInitialToken) {
            this.resumed = resumed;
            this.retryEnabled = retryEnabled;
            this.retainInitialToken = retainInitialToken;
        }

        private boolean expectedRetry() {
            return retryEnabled && !retainInitialToken;
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void listenerRejectedReservationRelease(ListenerRejectedState state, Blackhole blackhole) {
        long reservation = state.admission.tryReserve();
        if (reservation == QuicServerHandshakeAdmission.NO_RESERVATION) {
            throw new IllegalStateException("Failed to reserve benchmark handshake capacity");
        }
        try {
            blackhole.consume(reservation);
        } finally {
            state.admission.releaseReservation(reservation);
        }
    }

    @Benchmark
    public boolean pendingAcquireRelease(AcquireState state) {
        QuicServerHandshakeAdmission.Permit permit = acquirePermit(state.admission);
        if (permit == null) {
            return false;
        }
        permit.release();
        return true;
    }

    @Benchmark
    public boolean pendingRejected(RejectedState state) {
        return state.admission.tryReserve() == QuicServerHandshakeAdmission.NO_RESERVATION;
    }

    @Benchmark
    public long deadlineOfferCancel(TimerState state) {
        QuicServerHandshakeAdmission.Permit permit = acquirePermit(state.admission);
        if (permit == null || !permit.arm(state.timerQueue)) {
            throw new IllegalStateException("Failed to acquire and arm a benchmark handshake permit");
        }
        permit.release();
        return state.notifications.get();
    }

    @Benchmark
    public long sharedDeadlineOfferCancel(SharedTimerState state) {
        QuicServerHandshakeAdmission.Permit permit = acquirePermit(state.admission);
        if (permit == null || !permit.arm(state.timerQueue)) {
            throw new IllegalStateException("Failed to acquire and arm a shared benchmark handshake permit");
        }
        permit.release();
        return state.notifications.get();
    }

    @Benchmark
    public boolean serverConnectionLifecycle(ServerRuntimeState state, ServerInitialState initial) {
        return runServerConnectionLifecycle(state, initial, false);
    }

    @Benchmark
    public boolean serverEstablishedConnectionLifecycle(ServerRuntimeState state, ServerInitialState initial) {
        return runServerConnectionLifecycle(state, initial, true);
    }

    @Benchmark
    public boolean serverListenerRejected(ListenerRejectedRuntimeState state,
                                          ListenerRejectedInvocationState invocation,
                                          ServerInitialState initial) {
        try {
            invocation.reset();
            state.invocation.set(invocation);
            try {
                state.runtime.unmatchedQuicPacket(initial.peerAddress,
                                                  QuicPacket.HeadersType.LONG,
                                                  initial.packet);
            } finally {
                state.invocation.remove();
            }
            if (invocation.admissions != 1 || invocation.connections != 0 || invocation.submittedTasks != 0) {
                throw new IllegalStateException("Listener-rejected benchmark crossed its rejection boundary");
            }
            return true;
        } catch (RuntimeException | Error failure) {
            state.closeAfterFailure(failure);
            throw failure;
        }
    }

    @Benchmark
    public void serverPendingLimitRejected(SaturatedServerRuntimeState state, ServerInitialState initial) {
        state.fixture.runtime.unmatchedQuicPacket(initial.peerAddress,
                                                  QuicPacket.HeadersType.LONG,
                                                  initial.packet);
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public int serverPendingHandshakeExpiry(CapExpiryState state) throws Exception {
        PendingRuntimeFixture fixture = state.fixture;
        try {
            int releasedBefore = fixture.releasedBeforeEstablished.get();
            int releasedEstablished = fixture.releasedEstablished.get();
            fixture.fill();
            fixture.assertRejectedAtCapacity();
            fixture.expireTimers();
            fixture.awaitConnectionCleanup(true);
            fixture.runRetainedPacketTasks();
            fixture.expireTimers();
            fixture.awaitReleaseCount(releasedBefore + state.pendingLimit);
            fixture.assertReleasedSince(releasedBefore, releasedEstablished, state.pendingLimit);
            fixture.admitAndCleanRecoveryProbe(releasedBefore + state.pendingLimit + 1);
            fixture.completeCycle();
            return state.pendingLimit + 1;
        } catch (Exception | Error failure) {
            state.closeAfterFailure(failure);
            throw failure;
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public int serverPendingProtectedClose(CapExpiryState state) throws Exception {
        PendingRuntimeFixture fixture = state.fixture;
        try {
            int releasedBefore = fixture.releasedBeforeEstablished.get();
            int releasedEstablished = fixture.releasedEstablished.get();
            fixture.fill();
            fixture.assertRejectedAtCapacity();
            fixture.connections.forEach(connection -> connection.terminate(BENCHMARK_PROTECTED_CLOSE));
            fixture.awaitConnectionCleanup(false);
            fixture.runRetainedPacketTasks();
            if (fixture.activePermits.get() != state.pendingLimit
                    || fixture.releasedBeforeEstablished.get() != releasedBefore
                    || fixture.releasedEstablished.get() != releasedEstablished) {
                throw new IllegalStateException("Protected-close benchmark released admission before route expiry");
            }
            fixture.expireTimers();
            fixture.awaitReleaseCount(releasedBefore + state.pendingLimit);
            fixture.assertReleasedSince(releasedBefore, releasedEstablished, state.pendingLimit);
            fixture.admitAndCleanRecoveryProbe(releasedBefore + state.pendingLimit + 1);
            fixture.completeCycle();
            return state.pendingLimit + 1;
        } catch (Exception | Error failure) {
            state.closeAfterFailure(failure);
            throw failure;
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public boolean serverValidHandshake(ValidHandshakeState state,
                                        ValidHandshakeInvocationState invocation) throws Exception {
        invocation.owner = state;
        try {
            InetSocketAddress serverAddress = state.server.localAddress();
            HandshakeClient client = state.client();
            state.registerAccept();
            invocation.clientConnection = client.runtime.createConnection(serverAddress,
                                                                           "localhost",
                                                                           serverAddress.getPort(),
                                                                           H3_ALPN);
            invocation.expectedPeerPort = invocation.clientConnection.localPeer().port();
            if (state.expectedServerConnections.putIfAbsent(invocation.expectedPeerPort,
                                                            invocation.serverConnectionFuture) != null) {
                throw new IllegalStateException("Valid-handshake benchmark reused an active client address");
            }
            invocation.clientConnection.startHandshake().get(10, TimeUnit.SECONDS);
            invocation.serverConnection = invocation.serverConnectionFuture.get(10, TimeUnit.SECONDS);
            if (!invocation.clientConnection.isOpen() || !invocation.serverConnection.isOpen()
                    || !invocation.clientConnection.applicationProtocol().filter("h3"::equals).isPresent()
                    || !invocation.serverConnection.applicationProtocol().filter("h3"::equals).isPresent()) {
                throw new IllegalStateException("Valid-handshake benchmark did not negotiate an open h3 connection");
            }
            return true;
        } catch (Exception | Error failure) {
            state.closeAfterFailure(failure);
            throw failure;
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public boolean serverLifecycleHandshakeReady(HandshakeLifecycleState state) throws Exception {
        try {
            long deadline = state.lifecycleDeadline();
            return state.measuredHandshake(deadline).matches(state.handshakeScenario);
        } catch (Exception | Error failure) {
            state.closeAfterFailure(failure);
            throw failure;
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public boolean serverLifecycleReconnectReadyPeerTermination(HandshakeLifecycleState state) throws Exception {
        try {
            long deadline = state.lifecycleDeadline();
            HandshakeObservation observation = state.measuredHandshake(deadline);
            state.reconnectReadyPeerTermination(deadline);
            return observation.matches(state.handshakeScenario);
        } catch (Exception | Error failure) {
            state.closeAfterFailure(failure);
            throw failure;
        }
    }

    private static boolean runServerConnectionLifecycle(ServerRuntimeState state,
                                                        ServerInitialState initial,
                                                        boolean establish) {
        state.createdConnection.remove();
        List<Runnable> deferredTasks = new ArrayList<>();
        QuicServerConnection connection = null;
        try {
            state.deferredTasks.set(deferredTasks);
            try {
                state.runtime.unmatchedQuicPacket(initial.peerAddress,
                                                  QuicPacket.HeadersType.LONG,
                                                  initial.packet);
            } finally {
                state.deferredTasks.remove();
            }
            connection = state.createdConnection.get();
            if (connection == null) {
                throw new IllegalStateException("QUIC server benchmark connection was not created");
            }
            if (deferredTasks.isEmpty()) {
                throw new IllegalStateException("QUIC server benchmark packet processing was not deferred");
            }
            if (establish && !state.runtime.handshakeSucceeded(connection)) {
                throw new IllegalStateException("QUIC server benchmark handshake was not established");
            }
            // The synthetic Initial is structurally admissible but is not cryptographically valid. Keep its packet task
            // deferred so the benchmark measures establishment followed by silent cancellation. Termination stops the
            // scheduled incoming loop and drains the retained datagram before cleanup completes.
            return true;
        } finally {
            state.deferredTasks.remove();
            try {
                if (connection != null) {
                    connection.terminate(BENCHMARK_CLOSE);
                    connection.cleanupComplete().toCompletableFuture().join();
                }
            } finally {
                state.createdConnection.remove();
            }
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public int dueBatchExpiry(BatchExpiryState state) {
        state.dueProcessor.run();
        return state.dispatched.get();
    }

    /**
     * Near-capacity admission state.
     */
    @State(Scope.Thread)
    public static class AcquireState {
        /**
         * Pending-handshake capacity.
         */
        @Param({"1", "256", "1024", "4096"})
        public int pendingLimit;

        private QuicServerHandshakeAdmission admission;
        private List<QuicServerHandshakeAdmission.Permit> occupied;

        @Setup(Level.Trial)
        public void setUp() {
            admission = new QuicServerHandshakeAdmission(pendingLimit, Duration.ofMinutes(1), _ -> { });
            occupied = new ArrayList<>(pendingLimit - 1);
            for (int i = 1; i < pendingLimit; i++) {
                occupied.add(acquirePermit(admission));
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            occupied.forEach(QuicServerHandshakeAdmission.Permit::release);
        }
    }

    /**
     * Near-capacity listener-rejection state.
     */
    @State(Scope.Thread)
    public static class ListenerRejectedState {
        /**
         * Pending-handshake capacity.
         */
        @Param({"1", "256", "1024", "4096"})
        public int pendingLimit;

        private QuicServerHandshakeAdmission admission;
        private List<Long> occupied;

        @Setup(Level.Trial)
        public void setUp() {
            admission = new QuicServerHandshakeAdmission(pendingLimit, Duration.ofMinutes(1), _ -> { });
            occupied = new ArrayList<>(pendingLimit - 1);
            for (int i = 1; i < pendingLimit; i++) {
                long reservation = admission.tryReserve();
                if (reservation == QuicServerHandshakeAdmission.NO_RESERVATION) {
                    throw new IllegalStateException("Failed to reserve benchmark handshake capacity");
                }
                occupied.add(reservation);
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            occupied.forEach(admission::releaseReservation);
        }
    }

    /**
     * Saturated admission state.
     */
    @State(Scope.Thread)
    public static class RejectedState {
        /**
         * Pending-handshake capacity.
         */
        @Param({"1", "256", "1024", "4096"})
        public int pendingLimit;

        private QuicServerHandshakeAdmission admission;
        private List<Long> occupied;

        @Setup(Level.Trial)
        public void setUp() {
            admission = new QuicServerHandshakeAdmission(pendingLimit, Duration.ofMinutes(1), _ -> { });
            occupied = new ArrayList<>(pendingLimit);
            for (int i = 0; i < pendingLimit; i++) {
                long reservation = admission.tryReserve();
                if (reservation == QuicServerHandshakeAdmission.NO_RESERVATION) {
                    throw new IllegalStateException("Failed to reserve benchmark handshake capacity");
                }
                occupied.add(reservation);
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            occupied.forEach(admission::releaseReservation);
        }
    }

    /**
     * Timer queue with a representative pending-handshake population.
     */
    @State(Scope.Thread)
    public static class TimerState {
        /**
         * Number of handshake deadlines already scheduled.
         */
        @Param({"0", "256", "1024", "4096"})
        public int scheduledEvents;

        private final AtomicLong notifications = new AtomicLong();
        private QuicServerHandshakeAdmission admission;
        private QuicTimerQueue timerQueue;
        private List<QuicServerHandshakeAdmission.Permit> scheduled;

        @Setup(Level.Trial)
        public void setUp() {
            admission = new QuicServerHandshakeAdmission(scheduledEvents + 1, Duration.ofMinutes(1), _ -> { });
            timerQueue = new QuicTimerQueue(notifications::incrementAndGet, () -> "quic-server-admission-jmh");
            scheduled = new ArrayList<>(scheduledEvents);
            for (int i = 0; i < scheduledEvents; i++) {
                QuicServerHandshakeAdmission.Permit permit = acquirePermit(admission);
                permit.arm(timerQueue);
                scheduled.add(permit);
            }
            timerQueue.processEventsAndReturnNextDeadline(TimeSource.now(), Runnable::run);
            notifications.set(0);
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            scheduled.forEach(QuicServerHandshakeAdmission.Permit::release);
            timerQueue.stop();
        }
    }

    /**
     * One shared timer queue kept near the configured pending-handshake limit.
     */
    @State(Scope.Benchmark)
    public static class SharedTimerState {
        /**
         * Pending-handshake capacity.
         */
        @Param({"256"})
        public int pendingLimit;

        private final AtomicLong notifications = new AtomicLong();
        private QuicServerHandshakeAdmission admission;
        private QuicTimerQueue timerQueue;
        private List<QuicServerHandshakeAdmission.Permit> occupied;

        @Setup(Level.Trial)
        public void setUp(ThreadParams threadParams) {
            int occupiedPermits = pendingLimit - threadParams.getThreadCount();
            if (occupiedPermits < 0) {
                throw new IllegalStateException("Benchmark thread count exceeds pending-handshake capacity");
            }
            admission = new QuicServerHandshakeAdmission(pendingLimit, Duration.ofMinutes(1), _ -> { });
            timerQueue = new QuicTimerQueue(notifications::incrementAndGet,
                                            () -> "quic-server-admission-shared-jmh");
            occupied = new ArrayList<>(occupiedPermits);
            for (int i = 0; i < occupiedPermits; i++) {
                QuicServerHandshakeAdmission.Permit permit = acquirePermit(admission);
                if (permit == null || !permit.arm(timerQueue)) {
                    throw new IllegalStateException("Failed to populate shared benchmark handshake deadlines");
                }
                occupied.add(permit);
            }
            timerQueue.processEventsAndReturnNextDeadline(TimeSource.now(), Runnable::run);
            notifications.set(0);
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            occupied.forEach(QuicServerHandshakeAdmission.Permit::release);
            if (!Deadline.MAX.equals(timerQueue.nextDeadline())) {
                throw new IllegalStateException("Shared benchmark timer queue retained a deadline");
            }
            List<QuicServerHandshakeAdmission.Permit> capacityProbe = new ArrayList<>(pendingLimit);
            for (int i = 0; i < pendingLimit; i++) {
                QuicServerHandshakeAdmission.Permit permit = acquirePermit(admission);
                if (permit == null) {
                    throw new IllegalStateException("Shared benchmark handshake capacity did not recover");
                }
                capacityProbe.add(permit);
            }
            capacityProbe.forEach(QuicServerHandshakeAdmission.Permit::release);
            timerQueue.stop();
        }
    }

    /**
     * Batch of handshake deadlines sharing one timer queue.
     */
    @State(Scope.Thread)
    public static class BatchExpiryState {
        /**
         * Number of handshake deadlines expiring together.
         */
        @Param({"256", "1024", "4096"})
        public int expiringEvents;

        private final AtomicInteger dispatched = new AtomicInteger();
        private QuicServerHandshakeAdmission admission;
        private QuicTimerQueue timerQueue;
        private List<QuicServerHandshakeAdmission.Permit> permits;
        private List<Runnable> cleanupTasks;
        private Runnable dueProcessor;

        @Setup(Level.Invocation)
        public void setUp() {
            dispatched.set(0);
            cleanupTasks = new ArrayList<>(expiringEvents);
            Executor cleanupExecutor = cleanupTasks::add;
            admission = new QuicServerHandshakeAdmission(expiringEvents,
                                                         Duration.ofMinutes(1),
                                                         permit -> {
                                                             cleanupExecutor.execute(permit::release);
                                                             dispatched.incrementAndGet();
                                                         });
            timerQueue = new QuicTimerQueue(() -> { }, () -> "quic-server-admission-batch-jmh");
            permits = new ArrayList<>(expiringEvents);
            for (int i = 0; i < expiringEvents; i++) {
                QuicServerHandshakeAdmission.Permit permit = acquirePermit(admission);
                permit.arm(timerQueue);
                permits.add(permit);
            }
            timerQueue.processEventsAndReturnNextDeadline(Deadline.MAX, task -> dueProcessor = task);
        }

        @TearDown(Level.Invocation)
        public void tearDown() {
            int dispatchedCount = dispatched.get();
            if (dispatchedCount != expiringEvents) {
                String message = "Expected %d dispatched handshake timeouts, got %d"
                        .formatted(expiringEvents, dispatchedCount);
                throw new IllegalStateException(message);
            }
            cleanupTasks.forEach(Runnable::run);
            permits.forEach(QuicServerHandshakeAdmission.Permit::release);
            timerQueue.stop();
        }
    }

    /**
     * Shared runtime used to measure complete admitted Initial setup and cleanup.
     */
    @State(Scope.Benchmark)
    public static class ServerRuntimeState {
        /**
         * Pending-handshake capacity.
         */
        @Param({"256"})
        public int pendingLimit;

        private final ThreadLocal<QuicServerConnection> createdConnection = new ThreadLocal<>();
        private final ThreadLocal<List<Runnable>> deferredTasks = new ThreadLocal<>();
        private ExecutorService executor;
        private QuicServerRuntime runtime;

        @Setup(Level.Trial)
        public void setUp() throws Exception {
            executor = Executors.newVirtualThreadPerTaskExecutor();
            runtime = serverRuntimeBuilder(task -> {
                        List<Runnable> currentDeferredTasks = deferredTasks.get();
                        if (currentDeferredTasks == null) {
                            executor.execute(task);
                        } else {
                            currentDeferredTasks.add(task);
                        }
                    }, serverTls(), pendingLimit)
                    .observer(new QuicServerRuntime.Observer() {
                        @Override
                        public void connectionCreated(QuicConnection connection) {
                            createdConnection.set((QuicServerConnection) connection);
                        }
                    })
                    .build();
            runtime.start();
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            try {
                if (!runtime.close(Duration.ofSeconds(10))) {
                    throw new IllegalStateException("QUIC server benchmark runtime did not close");
                }
            } finally {
                executor.close();
                createdConnection.remove();
                deferredTasks.remove();
            }
        }
    }

    /**
     * Shared runtime whose owning listener rejects every otherwise admissible Initial.
     */
    @State(Scope.Benchmark)
    public static class ListenerRejectedRuntimeState {
        /**
         * Pending-handshake capacity.
         */
        @Param({"256"})
        public int pendingLimit;

        private final AtomicBoolean closed = new AtomicBoolean();
        private final ThreadLocal<ListenerRejectedInvocationState> invocation = new ThreadLocal<>();
        private ExecutorService executor;
        private QuicServerRuntime runtime;

        @Setup(Level.Trial)
        public void setUp() throws Exception {
            executor = newBenchmarkExecutor("quic-listener-rejected-jmh", 4);
            try {
                runtime = serverRuntimeBuilder(task -> {
                            ListenerRejectedInvocationState current = invocation.get();
                            if (current != null) {
                                current.submittedTasks++;
                            }
                            executor.execute(task);
                        }, serverTls(), pendingLimit)
                        .connectionAdmission(() -> {
                            ListenerRejectedInvocationState current = invocation.get();
                            if (current != null) {
                                current.admissions++;
                            }
                            return REJECTED_CONNECTION_PERMIT;
                        })
                        .observer(new QuicServerRuntime.Observer() {
                            @Override
                            public void connectionCreated(QuicConnection connection) {
                                ListenerRejectedInvocationState current = invocation.get();
                                if (current != null) {
                                    current.connections++;
                                }
                            }
                        })
                        .build();
                runtime.start();
            } catch (Exception | Error failure) {
                closeAfterFailure(failure);
                throw failure;
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            close();
        }

        private void closeAfterFailure(Throwable failure) {
            try {
                close();
            } catch (Throwable cleanupFailure) {
                if (cleanupFailure != failure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
        }

        private void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            Throwable failure = null;
            if (runtime != null) {
                try {
                    if (!runtime.close(Duration.ofSeconds(10))) {
                        throw new IllegalStateException("Listener-rejected benchmark runtime did not close");
                    }
                } catch (Throwable closeFailure) {
                    failure = collectBenchmarkFailure(failure, closeFailure);
                }
            }
            if (executor != null) {
                try {
                    executor.close();
                } catch (Throwable closeFailure) {
                    failure = collectBenchmarkFailure(failure, closeFailure);
                }
            }
            invocation.remove();
            throwBenchmarkFailure(failure, "Listener-rejected benchmark cleanup failed");
        }
    }

    /**
     * Worker-local counters guarding the listener-rejection benchmark boundary.
     */
    @State(Scope.Thread)
    public static class ListenerRejectedInvocationState {
        private int admissions;
        private int connections;
        private int submittedTasks;

        private void reset() {
            admissions = 0;
            connections = 0;
            submittedTasks = 0;
        }
    }

    /**
     * Shared runtime retained at its configured pending-handshake limit.
     */
    @State(Scope.Benchmark)
    public static class SaturatedServerRuntimeState {
        /**
         * Pending-handshake capacity.
         */
        @Param({"256"})
        public int pendingLimit;

        private PendingRuntimeFixture fixture;

        @Setup(Level.Trial)
        public void setUp() throws Exception {
            fixture = PendingRuntimeFixture.create(pendingLimit);
            try {
                fixture.fill();
            } catch (RuntimeException | Error failure) {
                try {
                    fixture.close();
                } catch (Throwable cleanupFailure) {
                    if (cleanupFailure != failure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
                throw failure;
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            fixture.close();
            fixture.assertReleasedSince(0, 0, pendingLimit);
        }
    }

    /**
     * Worker-local cap-sized pending runtime reused across measured lifecycle operations.
     */
    @State(Scope.Thread)
    public static class CapExpiryState {
        /**
         * Number of real pending server connections expired together.
         */
        @Param({"256"})
        public int pendingLimit;

        private PendingRuntimeFixture fixture;

        @Setup(Level.Trial)
        public void setUp(ThreadParams threadParams) throws Exception {
            if (threadParams.getThreadCount() != 1) {
                throw new IllegalStateException("Cap-sized lifecycle benchmarks require exactly one benchmark thread");
            }
            fixture = PendingRuntimeFixture.create(pendingLimit);
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            fixture.close();
            if (fixture.activePermits.get() != 0 || fixture.releasedEstablished.get() != 0) {
                throw new IllegalStateException("Cap-sized benchmark retained admission state");
            }
        }

        private void closeAfterFailure(Throwable failure) {
            try {
                fixture.close();
            } catch (Throwable cleanupFailure) {
                if (cleanupFailure != failure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
        }
    }

    /**
     * Worker-local client and server for classified full, resumed, and Retry handshake measurements.
     */
    @State(Scope.Thread)
    public static class HandshakeLifecycleState {
        /**
         * Handshake and address-validation path measured by this worker.
         */
        @Param({
                "COLD_NO_RETRY",
                "COLD_RETRY_TOKEN_DISABLED",
                "COLD_RETRY_TOKEN_RETAINED",
                "RESUMED_NO_RETRY",
                "RESUMED_RETRY_TOKEN_DISABLED",
                "RESUMED_RETRY_TOKEN_RETAINED"
        })
        public String scenario;

        /**
         * SHA-256 of the verified source manifest, or the functional-smoke classification.
         */
        @Param({"functional-smoke"})
        public String sourceIdentity;

        private HandshakeScenario handshakeScenario;
        private ExecutorService serverExecutor;
        private ExecutorService clientExecutor;
        private QuicServerRuntime server;
        private QuicClientRuntime client;
        private QuicClientTlsSessionCache tlsSessionCache;
        private QuicClientInitialTokenCache initialTokenCache;
        private BenchmarkTrustManager trustManager;
        private InetSocketAddress serverAddress;
        private QuicClientConnection clientConnection;
        private QuicConnection serverConnection;
        private boolean reconnectStatePending;
        private boolean reconnectStatePrimed;

        @Setup(Level.Trial)
        public void setUp(ThreadParams threadParams) throws Exception {
            if (threadParams.getThreadCount() != 1) {
                throw new IllegalStateException("QUIC handshake lifecycle benchmarks require exactly one benchmark worker");
            }
            handshakeScenario = HandshakeScenario.valueOf(scenario);
            if (sourceIdentity.isBlank()) {
                throw new IllegalArgumentException("QUIC lifecycle source identity is blank");
            }
            KeyStore trustStore = loadBenchmarkKeyStore(BENCHMARK_TRUSTSTORE_RESOURCE);
            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            X509ExtendedTrustManager delegate = null;
            for (var candidate : trustManagerFactory.getTrustManagers()) {
                if (candidate instanceof X509ExtendedTrustManager extendedTrustManager) {
                    delegate = extendedTrustManager;
                    break;
                }
            }
            if (delegate == null) {
                throw new IllegalStateException("Lifecycle benchmark did not obtain an engine-aware X.509 trust manager");
            }
            trustManager = new BenchmarkTrustManager(delegate);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new X509TrustManager[] {trustManager}, new SecureRandom());
            Tls clientTls = Tls.builder()
                    .manager(new BenchmarkTlsManager(sslContext, trustManager))
                    .sessionCacheSize(LIFECYCLE_SESSION_CACHE_SIZE)
                    .build();
            tlsSessionCache = QuicClientTlsSessionCache.create(clientTls);
            initialTokenCache = QuicClientInitialTokenCache.create(handshakeScenario.retainInitialToken ? 1 : 0);
            serverExecutor = newBenchmarkExecutor("quic-lifecycle-server-jmh", 8);
            clientExecutor = newBenchmarkExecutor("quic-lifecycle-client-jmh", 4);
            try {
                server = serverRuntimeBuilder(serverExecutor,
                                              serverTls(LIFECYCLE_SESSION_CACHE_SIZE),
                                              QuicServerRuntime.DEFAULT_MAX_PENDING_HANDSHAKES)
                        .retryEnabled(handshakeScenario.retryEnabled)
                        .build();
                server.start();
                serverAddress = server.localAddress();
                client = QuicClientRuntime.builder()
                        .executor(clientExecutor)
                        .tls(clientTls)
                        .tlsSessionCache(tlsSessionCache)
                        .initialTokenCache(initialTokenCache)
                        .quicConfig(benchmarkQuicConfig())
                        .build();

                HandshakeObservation firstPreflight = performHandshake(lifecycleDeadline());
                if (firstPreflight.sessionResumed()
                        || firstPreflight.retryObserved() != handshakeScenario.retryEnabled) {
                    throw new IllegalStateException("Lifecycle benchmark first preflight was misclassified");
                }
                finishHandshakeForNextInvocation();

                if (handshakeScenario.resumed) {
                    trustManager.rejectAdditionalServerChecks();
                } else {
                    tlsSessionCache.delegate().clear();
                }

                HandshakeObservation secondPreflight = performHandshake(lifecycleDeadline());
                if (secondPreflight.sessionResumed() != handshakeScenario.resumed
                        || secondPreflight.retryObserved() != handshakeScenario.expectedRetry()) {
                    throw new IllegalStateException("Lifecycle benchmark second preflight was misclassified");
                }
                finishHandshakeForNextInvocation();
                if (!handshakeScenario.resumed) {
                    tlsSessionCache.delegate().clear();
                }
            } catch (Exception | Error failure) {
                closeAfterFailure(failure);
                throw failure;
            }
        }

        @Setup(Level.Invocation)
        public void setUpInvocation() {
            if (!handshakeScenario.resumed) {
                tlsSessionCache.delegate().clear();
                if (tlsSessionCache.size() != 0) {
                    throw new IllegalStateException("Lifecycle benchmark did not clear the cold client TLS cache");
                }
            } else if (tlsSessionCache.size() != 1) {
                throw new IllegalStateException("Lifecycle benchmark has no retained TLS ticket before reconnect");
            }
            if (handshakeScenario.retainInitialToken && initialTokenCache.size() != 1) {
                throw new IllegalStateException("Lifecycle benchmark has no retained NEW_TOKEN before reconnect");
            }
        }

        private HandshakeObservation measuredHandshake(long deadline) throws Exception {
            HandshakeObservation observation = performHandshake(deadline);
            if (!observation.matches(handshakeScenario)) {
                throw new IllegalStateException("Lifecycle benchmark expected " + handshakeScenario
                                                        + " but observed resumed=" + observation.sessionResumed()
                                                        + ", Retry=" + observation.retryObserved());
            }
            return observation;
        }

        private HandshakeObservation performHandshake(long deadline) throws Exception {
            if (clientConnection != null || serverConnection != null) {
                throw new IllegalStateException("Lifecycle benchmark retained a connection between invocations");
            }
            reconnectStatePending = false;
            if (handshakeScenario.retainInitialToken && reconnectStatePrimed && initialTokenCache.size() != 1) {
                throw new IllegalStateException("Lifecycle benchmark has no retained NEW_TOKEN before reconnect");
            }
            int trustChecksBefore = trustManager.serverChecks();
            CompletableFuture<QuicConnection> accepted = server.accept();
            clientConnection = client.createConnection(serverAddress,
                                                       "localhost",
                                                       serverAddress.getPort(),
                                                       H3_ALPN);
            awaitFuture(clientConnection.startHandshake(), deadline, "client handshake");
            serverConnection = awaitFuture(accepted, deadline, "server accept");
            if (!clientConnection.isOpen() || !serverConnection.isOpen()
                    || !clientConnection.applicationProtocol().filter("h3"::equals).isPresent()
                    || !serverConnection.applicationProtocol().filter("h3"::equals).isPresent()) {
                throw new IllegalStateException("Lifecycle benchmark did not negotiate an open h3 connection");
            }
            QuicConnectionImpl clientImplementation = (QuicConnectionImpl) clientConnection;
            int trustChecks = trustManager.serverChecks() - trustChecksBefore;
            if (trustChecks < 0 || trustChecks > 1) {
                throw new IllegalStateException("Lifecycle benchmark observed an invalid server certificate-check count: "
                                                        + trustChecks);
            }
            QuicTransportParameters peerParameters = clientImplementation.peerTransportParameters()
                    .orElseThrow(() -> new IllegalStateException("Lifecycle benchmark has no peer transport parameters"));
            boolean retryObserved = peerParameters.isPresent(
                    QuicTransportParameters.ParameterId.retry_source_connection_id);
            reconnectStatePending = true;
            return new HandshakeObservation(trustChecks == 0, retryObserved);
        }

        @TearDown(Level.Invocation)
        public void tearDownInvocation() throws Exception {
            finishHandshakeForNextInvocation();
        }

        private static long lifecycleDeadline() {
            return deadlineAfter(LIFECYCLE_TIMEOUT_MILLIS);
        }

        private static long cleanupDeadline() {
            return deadlineAfter(INVOCATION_CLEANUP_TIMEOUT_MILLIS);
        }

        private void awaitReconnectState(long deadline) throws Exception {
            if (!reconnectStatePending) {
                return;
            }
            while (tlsSessionCache.size() != 1) {
                checkReconnectInterrupted();
                LockSupport.parkNanos(Math.min(remainingNanos(deadline, "a reusable TLS session ticket"),
                                               TimeUnit.MICROSECONDS.toNanos(50)));
            }
            if (handshakeScenario.retainInitialToken) {
                while (initialTokenCache.size() != 1) {
                    checkReconnectInterrupted();
                    LockSupport.parkNanos(Math.min(remainingNanos(deadline, "a replacement NEW_TOKEN"),
                                                   TimeUnit.MICROSECONDS.toNanos(50)));
                }
            }
            checkReconnectInterrupted();
            reconnectStatePending = false;
            reconnectStatePrimed = true;
        }

        private static void checkReconnectInterrupted() throws InterruptedException {
            if (Thread.interrupted()) {
                throw new InterruptedException("Lifecycle benchmark reconnect-state wait was interrupted");
            }
        }

        private static long deadlineAfter(long timeoutMillis) {
            return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        }

        private static long remainingNanos(long deadline, String phase) throws TimeoutException {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw new TimeoutException("Lifecycle benchmark timed out waiting for " + phase);
            }
            return remaining;
        }

        private static <T> T awaitFuture(CompletableFuture<T> future, long deadline, String phase) throws Exception {
            try {
                return future.get(remainingNanos(deadline, phase), TimeUnit.NANOSECONDS);
            } catch (TimeoutException timeout) {
                throw new TimeoutException("Lifecycle benchmark timed out waiting for " + phase);
            }
        }

        private void finishHandshakeForNextInvocation() throws Exception {
            boolean interrupted = Thread.interrupted();
            Throwable failure = interrupted
                    ? new InterruptedException("Lifecycle benchmark invocation cleanup was interrupted")
                    : null;
            long deadline = cleanupDeadline();
            try {
                if (!interrupted && reconnectStatePending) {
                    try {
                        awaitReconnectState(deadline);
                    } catch (Throwable cleanupFailure) {
                        interrupted |= cleanupFailure instanceof InterruptedException;
                        failure = collectBenchmarkFailure(failure, cleanupFailure);
                    }
                }
                try {
                    forceCleanUpConnections(deadline);
                } catch (Throwable cleanupFailure) {
                    interrupted |= Thread.interrupted();
                    failure = collectBenchmarkFailure(failure, cleanupFailure);
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            if (failure instanceof Error error) {
                throw error;
            }
            if (failure instanceof Exception exception) {
                throw exception;
            }
            if (failure != null) {
                throw new IllegalStateException("Lifecycle benchmark connection cleanup failed", failure);
            }
        }

        private void reconnectReadyPeerTermination(long deadline) throws Exception {
            awaitReconnectState(deadline);
            QuicClientConnection localConnection = clientConnection;
            QuicConnection peerConnection = serverConnection;
            if (localConnection == null || peerConnection == null) {
                throw new IllegalStateException("Lifecycle benchmark has no connection to close through the peer");
            }
            CompletableFuture<QuicTermination> localTermination =
                    localConnection.whenTerminated().toCompletableFuture();
            CompletableFuture<QuicTermination> peerTermination =
                    peerConnection.whenTerminated().toCompletableFuture();
            localConnection.terminate(BENCHMARK_PROTOCOL_CLOSE);
            QuicTermination localResult = awaitFuture(localTermination, deadline, "local protocol termination");
            QuicTermination peerResult = awaitFuture(peerTermination, deadline, "peer protocol termination");
            assertProtocolClose(localResult, QuicTermination.Origin.LOCAL);
            assertProtocolClose(peerResult, QuicTermination.Origin.PEER);
            clientConnection = null;
            serverConnection = null;
        }

        private static void assertProtocolClose(QuicTermination termination, QuicTermination.Origin expectedOrigin) {
            if (termination.origin() != expectedOrigin
                    || termination.kind() != QuicTermination.Kind.CONNECTION_CLOSE
                    || termination.layer() != QuicTermination.Layer.TRANSPORT
                    || termination.errorCode().orElse(-1) != QuicTransportErrors.NO_ERROR.code()) {
                throw new IllegalStateException("Lifecycle benchmark observed an unexpected QUIC termination");
            }
        }

        private void forceCleanUpConnections(long deadline) throws Exception {
            QuicClientConnection localConnection = clientConnection;
            QuicConnection peerConnection = serverConnection;
            if (localConnection == null && peerConnection == null) {
                reconnectStatePending = false;
                return;
            }
            CompletableFuture<QuicTermination> localTermination = localConnection == null
                    ? null
                    : localConnection.whenTerminated().toCompletableFuture();
            CompletableFuture<QuicTermination> peerTermination = peerConnection == null
                    ? null
                    : peerConnection.whenTerminated().toCompletableFuture();
            boolean interrupted = Thread.interrupted();
            Throwable failure = interrupted
                    ? new InterruptedException("Lifecycle benchmark forced cleanup was interrupted")
                    : null;
            if (localConnection != null) {
                try {
                    localConnection.terminate(BENCHMARK_CLOSE);
                } catch (Throwable cleanupFailure) {
                    failure = collectBenchmarkFailure(failure, cleanupFailure);
                }
            }
            if (peerConnection != null) {
                try {
                    peerConnection.terminate(BENCHMARK_CLOSE);
                } catch (Throwable cleanupFailure) {
                    failure = collectBenchmarkFailure(failure, cleanupFailure);
                }
            }
            try {
                if (localTermination != null) {
                    failure = awaitCleanupTermination(localTermination, deadline, "local", failure);
                    interrupted |= Thread.interrupted();
                }
                if (peerTermination != null) {
                    failure = awaitCleanupTermination(peerTermination, deadline, "peer", failure);
                    interrupted |= Thread.interrupted();
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            boolean terminated = (localTermination == null || localTermination.isDone())
                    && (peerTermination == null || peerTermination.isDone());
            if (terminated) {
                clientConnection = null;
                serverConnection = null;
                reconnectStatePending = false;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            if (failure instanceof Exception exception) {
                throw exception;
            }
            if (failure != null) {
                throw new IllegalStateException("Lifecycle benchmark forced cleanup failed", failure);
            }
        }

        private static Throwable awaitCleanupTermination(CompletableFuture<?> termination,
                                                         long deadline,
                                                         String role,
                                                         Throwable failure) {
            boolean interrupted = false;
            try {
                boolean completionObserved = false;
                while (!completionObserved) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        return collectBenchmarkFailure(
                                failure,
                                new TimeoutException("Lifecycle benchmark " + role
                                                             + " forced cleanup did not complete"));
                    }
                    try {
                        termination.get(remaining, TimeUnit.NANOSECONDS);
                        completionObserved = true;
                    } catch (InterruptedException interruptedFailure) {
                        interrupted = true;
                        failure = collectBenchmarkFailure(failure, interruptedFailure);
                    } catch (Throwable cleanupFailure) {
                        failure = collectBenchmarkFailure(failure, cleanupFailure);
                        completionObserved = termination.isDone();
                    }
                }
                return failure;
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            close();
        }

        private void closeAfterFailure(Throwable failure) {
            try {
                close();
            } catch (Throwable cleanupFailure) {
                if (cleanupFailure != failure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
        }

        private void close() {
            Throwable failure = null;
            try {
                forceCleanUpConnections(cleanupDeadline());
            } catch (Throwable cleanupFailure) {
                failure = collectBenchmarkFailure(failure, cleanupFailure);
            }
            if (client != null) {
                try {
                    client.close();
                } catch (Throwable cleanupFailure) {
                    failure = collectBenchmarkFailure(failure, cleanupFailure);
                }
            }
            if (server != null) {
                try {
                    if (!server.close(Duration.ofSeconds(10))) {
                        throw new IllegalStateException("Lifecycle benchmark server did not close");
                    }
                } catch (Throwable cleanupFailure) {
                    failure = collectBenchmarkFailure(failure, cleanupFailure);
                }
            }
            if (tlsSessionCache != null) {
                try {
                    tlsSessionCache.close();
                } catch (Throwable cleanupFailure) {
                    failure = collectBenchmarkFailure(failure, cleanupFailure);
                }
            }
            if (initialTokenCache != null) {
                try {
                    initialTokenCache.close();
                } catch (Throwable cleanupFailure) {
                    failure = collectBenchmarkFailure(failure, cleanupFailure);
                }
            }
            failure = closeExecutor(clientExecutor, "client", failure);
            failure = closeExecutor(serverExecutor, "server", failure);
            client = null;
            server = null;
            tlsSessionCache = null;
            initialTokenCache = null;
            trustManager = null;
            clientExecutor = null;
            serverExecutor = null;
            throwBenchmarkFailure(failure, "Lifecycle benchmark cleanup failed");
        }

        private static Throwable closeExecutor(ExecutorService executor, String role, Throwable failure) {
            if (executor == null) {
                return failure;
            }
            boolean interrupted = Thread.interrupted();
            if (interrupted) {
                failure = collectBenchmarkFailure(
                        failure,
                        new InterruptedException("Lifecycle benchmark " + role + " executor cleanup was interrupted"));
            }
            try {
                try {
                    executor.shutdown();
                } catch (Throwable cleanupFailure) {
                    failure = collectBenchmarkFailure(failure, cleanupFailure);
                }
                boolean terminated = false;
                long deadline = cleanupDeadline();
                long gracefulDeadline = Math.min(deadline, deadlineAfter(1000));
                while (!terminated) {
                    long remaining = gracefulDeadline - System.nanoTime();
                    if (remaining <= 0) {
                        break;
                    }
                    try {
                        terminated = executor.awaitTermination(remaining, TimeUnit.NANOSECONDS);
                    } catch (InterruptedException interruptedFailure) {
                        interrupted = true;
                        failure = collectBenchmarkFailure(failure, interruptedFailure);
                    }
                }
                if (!terminated) {
                    try {
                        executor.shutdownNow();
                    } catch (Throwable cleanupFailure) {
                        failure = collectBenchmarkFailure(failure, cleanupFailure);
                    }
                    while (!executor.isTerminated()) {
                        long remaining = deadline - System.nanoTime();
                        if (remaining <= 0) {
                            break;
                        }
                        try {
                            executor.awaitTermination(remaining, TimeUnit.NANOSECONDS);
                        } catch (InterruptedException interruptedFailure) {
                            interrupted = true;
                            failure = collectBenchmarkFailure(failure, interruptedFailure);
                        }
                    }
                    if (!executor.isTerminated()) {
                        failure = collectBenchmarkFailure(
                                failure,
                                new IllegalStateException("Lifecycle benchmark " + role + " executor did not terminate"));
                    }
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            return failure;
        }
    }

    private record HandshakeObservation(boolean sessionResumed, boolean retryObserved) {
        private boolean matches(HandshakeScenario scenario) {
            return sessionResumed == scenario.resumed && retryObserved == scenario.expectedRetry();
        }
    }

    private static final class BenchmarkTrustManager extends X509ExtendedTrustManager {
        private final X509ExtendedTrustManager delegate;
        private final AtomicInteger serverChecks = new AtomicInteger();
        private volatile boolean rejectAdditionalServerChecks;

        private BenchmarkTrustManager(X509ExtendedTrustManager delegate) {
            this.delegate = delegate;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            delegate.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new CertificateException("Lifecycle benchmark requires engine-aware server certificate validation");
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            delegate.checkClientTrusted(chain, authType, socket);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            throw new CertificateException("Lifecycle benchmark requires engine-aware server certificate validation");
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            delegate.checkClientTrusted(chain, authType, engine);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            int check = serverChecks.incrementAndGet();
            if (rejectAdditionalServerChecks) {
                throw new CertificateException("A resumed benchmark handshake unexpectedly requested a certificate check: "
                                                       + check);
            }
            delegate.checkServerTrusted(chain, authType, engine);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return delegate.getAcceptedIssuers();
        }

        private int serverChecks() {
            return serverChecks.get();
        }

        private void rejectAdditionalServerChecks() {
            if (serverChecks.get() != 1) {
                throw new IllegalStateException("Lifecycle benchmark expected one preflight certificate check");
            }
            rejectAdditionalServerChecks = true;
        }
    }

    private static final class BenchmarkTlsManager implements TlsManager {
        private final SSLContext sslContext;
        private final X509TrustManager trustManager;

        private BenchmarkTlsManager(SSLContext sslContext, X509TrustManager trustManager) {
            this.sslContext = sslContext;
            this.trustManager = trustManager;
        }

        @Override
        public String name() {
            return "quic-handshake-lifecycle-jmh";
        }

        @Override
        public String type() {
            return "fixed";
        }

        @Override
        public void init(TlsConfig tls) {
        }

        @Override
        public void reload(TlsMaterial material) {
            throw new UnsupportedOperationException("Benchmark TLS manager does not support reload");
        }

        @Override
        public SSLContext sslContext() {
            return sslContext;
        }

        @Override
        public Optional<X509KeyManager> keyManager() {
            return Optional.empty();
        }

        @Override
        public Optional<X509TrustManager> trustManager() {
            return Optional.of(trustManager);
        }
    }

    /**
     * One shared server with one loopback QUIC client runtime per benchmark worker.
     */
    @State(Scope.Benchmark)
    public static class ValidHandshakeState {
        private final AtomicBoolean closed = new AtomicBoolean();
        private final ReentrantLock clientLifecycleLock = new ReentrantLock();
        private final ConcurrentMap<Integer, CompletableFuture<QuicConnection>> expectedServerConnections =
                new ConcurrentHashMap<>();
        private final ThreadLocal<HandshakeClient> clients = ThreadLocal.withInitial(this::newClient);
        private final List<HandshakeClient> allClients = new ArrayList<>();
        private ExecutorService serverExecutor;
        private QuicServerRuntime server;

        @Setup(Level.Trial)
        public void setUp() throws Exception {
            serverExecutor = newBenchmarkExecutor("quic-valid-handshake-server-jmh", 16);
            try {
                server = serverRuntimeBuilder(serverExecutor,
                                              serverTls(),
                                              QuicServerRuntime.DEFAULT_MAX_PENDING_HANDSHAKES)
                        .build();
                server.start();
            } catch (Exception | Error failure) {
                closeAfterFailure(failure);
                throw failure;
            }
        }

        private HandshakeClient client() {
            return clients.get();
        }

        private HandshakeClient newClient() {
            HandshakeClient client = new HandshakeClient();
            boolean closeClient;
            clientLifecycleLock.lock();
            try {
                closeClient = closed.get();
                if (!closeClient) {
                    allClients.add(client);
                }
            } finally {
                clientLifecycleLock.unlock();
            }
            if (closeClient) {
                client.close();
                throw new IllegalStateException("Valid-handshake benchmark state is closed");
            }
            return client;
        }

        private void registerAccept() {
            server.accept().whenComplete(this::acceptedConnection);
        }

        private void acceptedConnection(QuicConnection connection, Throwable failure) {
            if (failure != null) {
                expectedServerConnections.values().forEach(future -> future.completeExceptionally(failure));
                return;
            }
            CompletableFuture<QuicConnection> expected =
                    expectedServerConnections.get(connection.remotePeer().port());
            if (expected != null) {
                expected.complete(connection);
                return;
            }
            IllegalStateException unexpected =
                    new IllegalStateException("Valid-handshake benchmark accepted an unexpected client address");
            connection.terminate(BENCHMARK_CLOSE);
            expectedServerConnections.values().forEach(future -> future.completeExceptionally(unexpected));
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            close();
        }

        private void closeAfterFailure(Throwable failure) {
            try {
                close();
            } catch (Throwable cleanupFailure) {
                if (cleanupFailure != failure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
        }

        private void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            Throwable failure = null;
            IllegalStateException closedFailure = new IllegalStateException("Valid-handshake benchmark state is closing");
            expectedServerConnections.values().forEach(future -> future.completeExceptionally(closedFailure));
            List<HandshakeClient> clientsToClose;
            clientLifecycleLock.lock();
            try {
                clientsToClose = List.copyOf(allClients);
                allClients.clear();
            } finally {
                clientLifecycleLock.unlock();
            }
            for (HandshakeClient client : clientsToClose) {
                try {
                    client.close();
                } catch (Throwable closeFailure) {
                    failure = collectBenchmarkFailure(failure, closeFailure);
                }
            }
            if (server != null) {
                try {
                    if (!server.close(Duration.ofSeconds(10))) {
                        throw new IllegalStateException("Valid-handshake benchmark runtime did not close");
                    }
                } catch (Throwable closeFailure) {
                    failure = collectBenchmarkFailure(failure, closeFailure);
                }
            }
            if (serverExecutor != null) {
                try {
                    serverExecutor.close();
                } catch (Throwable closeFailure) {
                    failure = collectBenchmarkFailure(failure, closeFailure);
                }
            }
            clients.remove();
            expectedServerConnections.clear();
            throwBenchmarkFailure(failure, "Valid-handshake benchmark cleanup failed");
        }
    }

    /**
     * Worker-local successful-handshake resources cleaned outside the measured interval.
     */
    @State(Scope.Thread)
    public static class ValidHandshakeInvocationState {
        private CompletableFuture<QuicConnection> serverConnectionFuture;
        private ValidHandshakeState owner;
        private int expectedPeerPort;
        private QuicClientConnection clientConnection;
        private QuicConnection serverConnection;

        @Setup(Level.Invocation)
        public void setUp() {
            serverConnectionFuture = new CompletableFuture<>();
            owner = null;
            expectedPeerPort = -1;
            clientConnection = null;
            serverConnection = null;
        }

        @TearDown(Level.Invocation)
        public void tearDown() throws Exception {
            Throwable failure = null;
            if (owner != null && expectedPeerPort >= 0) {
                owner.expectedServerConnections.remove(expectedPeerPort, serverConnectionFuture);
            }
            try {
                terminateAndAwait(clientConnection);
            } catch (Throwable cleanupFailure) {
                failure = collectBenchmarkFailure(failure, cleanupFailure);
            }
            try {
                terminateAndAwait(serverConnection);
            } catch (Throwable cleanupFailure) {
                failure = collectBenchmarkFailure(failure, cleanupFailure);
            }
            if (failure != null && owner != null) {
                try {
                    owner.close();
                } catch (Throwable cleanupFailure) {
                    failure = collectBenchmarkFailure(failure, cleanupFailure);
                }
            }
            owner = null;
            expectedPeerPort = -1;
            clientConnection = null;
            serverConnection = null;
            if (failure instanceof Error error) {
                throw error;
            }
            if (failure instanceof Exception exception) {
                throw exception;
            }
            if (failure != null) {
                throw new IllegalStateException("Valid-handshake connection cleanup failed", failure);
            }
        }
    }

    /**
     * Thread-local supported Initial packet and source address.
     */
    @State(Scope.Thread)
    public static class ServerInitialState {
        private InetSocketAddress peerAddress;
        private ByteBuffer packet;

        @Setup(Level.Trial)
        public void setUp(ThreadParams threadParams) {
            int threadIndex = threadParams.getThreadIndex();
            peerAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), 20_000 + threadIndex);
            packet = QuicServerIngressJmhBenchmark.supportedInitial(threadIndex + 1L);
            QuicServerIngress ingress = new QuicServerIngress(List.of(QuicVersion.QUIC_V1), false, null);
            if (!(ingress.inspect(peerAddress, QuicConnectionIdFactory.server(), packet)
                    instanceof QuicServerIngress.Admit)) {
                throw new IllegalStateException("QUIC server benchmark Initial was not admitted");
            }
        }
    }

    private static final class HandshakeClient {
        private final ExecutorService executor;
        private final QuicClientRuntime runtime;
        private final QuicClientTlsSessionCache tlsSessionCache;

        private HandshakeClient() {
            executor = newBenchmarkExecutor("quic-valid-handshake-client-jmh", 4);
            QuicClientTlsSessionCache sessionCache = null;
            try {
                Tls tls = Tls.builder().trustAll(true).build();
                sessionCache = QuicClientTlsSessionCache.create(tls, 0);
                runtime = QuicClientRuntime.builder()
                        .executor(executor)
                        .tls(tls)
                        .tlsSessionCache(sessionCache)
                        .quicConfig(benchmarkQuicConfig())
                        .build();
                tlsSessionCache = sessionCache;
            } catch (RuntimeException | Error failure) {
                if (sessionCache != null) {
                    try {
                        sessionCache.close();
                    } catch (Throwable cleanupFailure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
                try {
                    executor.close();
                } catch (Throwable cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }

        private void close() {
            try {
                runtime.close();
            } finally {
                try {
                    tlsSessionCache.close();
                } finally {
                    executor.close();
                }
            }
        }
    }

    private static final class PendingRuntimeFixture {
        private final int pendingLimit;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean overAdmitted = new AtomicBoolean();
        private final AtomicInteger activePermits = new AtomicInteger();
        private final AtomicInteger releasedBeforeEstablished = new AtomicInteger();
        private final AtomicInteger releasedEstablished = new AtomicInteger();
        private final ThreadLocal<QuicServerConnection> createdConnection = new ThreadLocal<>();
        private final ThreadLocal<List<Runnable>> deferredTasks = new ThreadLocal<>();
        private final List<QuicServerConnection> connections;
        private final List<Runnable> retainedPacketTasks;
        private final List<InetSocketAddress> peerAddresses;
        private final List<ByteBuffer> initialPackets;
        private final InetSocketAddress rejectedPeerAddress;
        private final ByteBuffer rejectedInitialPacket;
        private final InetSocketAddress recoveryPeerAddress;
        private final ByteBuffer recoveryInitialPacket;
        private final ExecutorService executor;
        private final QuicServerRuntime runtime;

        private PendingRuntimeFixture(int pendingLimit,
                                      ExecutorService executor,
                                      QuicServerRuntime runtime) {
            this.pendingLimit = pendingLimit;
            this.connections = new ArrayList<>(pendingLimit);
            this.retainedPacketTasks = new ArrayList<>(pendingLimit);
            this.peerAddresses = new ArrayList<>(pendingLimit);
            this.initialPackets = new ArrayList<>(pendingLimit);
            for (int i = 0; i < pendingLimit; i++) {
                peerAddresses.add(new InetSocketAddress(InetAddress.getLoopbackAddress(), 30_000 + i));
                initialPackets.add(QuicServerIngressJmhBenchmark.supportedInitial(10_000L + i));
            }
            this.rejectedPeerAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), 45_000);
            this.rejectedInitialPacket = QuicServerIngressJmhBenchmark.supportedInitial(50_000L);
            this.recoveryPeerAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), 45_001);
            this.recoveryInitialPacket = QuicServerIngressJmhBenchmark.supportedInitial(50_001L);
            this.executor = executor;
            this.runtime = runtime;
        }

        private static PendingRuntimeFixture create(int pendingLimit) throws Exception {
            Tls tls = serverTls();
            ExecutorService executor = newBenchmarkExecutor("quic-pending-runtime-jmh", 16);
            PendingRuntimeFixture[] fixtureRef = new PendingRuntimeFixture[1];
            QuicServerRuntime runtime = null;
            PendingRuntimeFixture fixture = null;
            try {
                runtime = serverRuntimeBuilder(task -> {
                            PendingRuntimeFixture currentFixture = fixtureRef[0];
                            List<Runnable> currentDeferredTasks = currentFixture.deferredTasks.get();
                            if (currentDeferredTasks == null) {
                                executor.execute(task);
                            } else {
                                currentDeferredTasks.add(task);
                            }
                        }, tls, pendingLimit)
                        .handshakeTimeout(Duration.ofMinutes(5))
                        .connectionAdmission(() -> fixtureRef[0].acquireConnectionPermit())
                        .observer(new QuicServerRuntime.Observer() {
                            @Override
                            public void connectionCreated(QuicConnection connection) {
                                fixtureRef[0].createdConnection.set((QuicServerConnection) connection);
                            }
                        })
                        .build();
                fixture = new PendingRuntimeFixture(pendingLimit, executor, runtime);
                fixtureRef[0] = fixture;
                runtime.start();
                return fixture;
            } catch (Exception | Error failure) {
                if (fixture != null) {
                    try {
                        fixture.close();
                    } catch (Throwable cleanupFailure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                } else {
                    if (runtime != null) {
                        try {
                            runtime.close(Duration.ofSeconds(10));
                        } catch (Throwable cleanupFailure) {
                            failure.addSuppressed(cleanupFailure);
                        }
                    }
                    try {
                        executor.close();
                    } catch (Throwable cleanupFailure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
                throw failure;
            }
        }

        private QuicServerRuntime.ConnectionPermit acquireConnectionPermit() {
            int current = activePermits.getAndIncrement();
            if (current >= pendingLimit) {
                activePermits.decrementAndGet();
                overAdmitted.set(true);
                return REJECTED_CONNECTION_PERMIT;
            }
            return QuicServerRuntime.ConnectionPermit.accepted(this::releaseBeforeEstablished,
                                                               this::releaseEstablished);
        }

        private void releaseBeforeEstablished() {
            activePermits.decrementAndGet();
            releasedBeforeEstablished.incrementAndGet();
        }

        private void releaseEstablished() {
            activePermits.decrementAndGet();
            releasedEstablished.incrementAndGet();
        }

        private void fill() {
            if (!connections.isEmpty()) {
                throw new IllegalStateException("Cap-sized benchmark fixture is already populated");
            }
            if (!retainedPacketTasks.isEmpty() || activePermits.get() != 0) {
                throw new IllegalStateException("Cap-sized benchmark fixture did not reset after its previous cycle");
            }
            deferredTasks.set(retainedPacketTasks);
            try {
                for (int i = 0; i < pendingLimit; i++) {
                    createdConnection.remove();
                    int retainedTasks = retainedPacketTasks.size();
                    runtime.unmatchedQuicPacket(peerAddresses.get(i),
                                                QuicPacket.HeadersType.LONG,
                                                initialPackets.get(i));
                    QuicServerConnection connection = createdConnection.get();
                    if (connection == null) {
                        throw new IllegalStateException("Cap-sized benchmark connection was not created");
                    }
                    if (retainedPacketTasks.size() == retainedTasks) {
                        throw new IllegalStateException("Cap-sized benchmark packet processing was not deferred");
                    }
                    connections.add(connection);
                }
            } finally {
                deferredTasks.remove();
            }
            createdConnection.remove();
            if (activePermits.get() != pendingLimit) {
                throw new IllegalStateException("Cap-sized benchmark did not retain every admission permit");
            }
        }

        private void assertRejectedAtCapacity() {
            createdConnection.remove();
            int retainedTasks = retainedPacketTasks.size();
            deferredTasks.set(retainedPacketTasks);
            try {
                runtime.unmatchedQuicPacket(rejectedPeerAddress,
                                            QuicPacket.HeadersType.LONG,
                                            rejectedInitialPacket);
            } finally {
                deferredTasks.remove();
            }
            if (overAdmitted.get() || createdConnection.get() != null || retainedPacketTasks.size() != retainedTasks
                    || activePermits.get() != pendingLimit) {
                throw new IllegalStateException("Cap-sized benchmark Initial reached listener admission");
            }
            createdConnection.remove();
        }

        private void expireTimers() {
            runtime.endpoint()
                    .timer()
                    .processEventsAndReturnNextDeadline(Deadline.MAX, Runnable::run);
        }

        private void runRetainedPacketTasks() {
            retainedPacketTasks.forEach(Runnable::run);
            retainedPacketTasks.clear();
        }

        private void awaitConnectionCleanup(boolean timeoutExpected) throws Exception {
            long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            for (QuicServerConnection connection : connections) {
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0) {
                    throw new IllegalStateException("Timed out waiting for cap-sized connection cleanup");
                }
                QuicTermination termination = connection.futureTermination()
                        .get(remainingNanos, TimeUnit.NANOSECONDS);
                if (timeoutExpected) {
                    if (termination.cause().filter(TimeoutException.class::isInstance).isEmpty()) {
                        throw new IllegalStateException("Cap-sized benchmark connection did not expire by timeout");
                    }
                } else if (termination.kind() != QuicTermination.Kind.CONNECTION_CLOSE) {
                    throw new IllegalStateException("Cap-sized benchmark connection did not retain a protected close");
                }
            }
        }

        private void awaitReleaseCount(int expected) {
            long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (releasedBeforeEstablished.get() < expected && System.nanoTime() < deadlineNanos) {
                LockSupport.parkNanos(TimeUnit.MICROSECONDS.toNanos(100));
            }
            if (releasedBeforeEstablished.get() != expected) {
                throw new IllegalStateException("Timed out waiting for cap-sized handshake release");
            }
        }

        private void admitAndCleanRecoveryProbe(int expectedReleaseCount) throws Exception {
            createdConnection.remove();
            int retainedTasks = retainedPacketTasks.size();
            deferredTasks.set(retainedPacketTasks);
            try {
                runtime.unmatchedQuicPacket(recoveryPeerAddress,
                                            QuicPacket.HeadersType.LONG,
                                            recoveryInitialPacket);
            } finally {
                deferredTasks.remove();
            }
            QuicServerConnection connection = createdConnection.get();
            if (connection == null || retainedPacketTasks.size() == retainedTasks || activePermits.get() != 1) {
                throw new IllegalStateException("Cap-sized benchmark capacity did not recover");
            }
            connection.terminate(BENCHMARK_CLOSE);
            runRetainedPacketTasks();
            connection.futureTermination().get(10, TimeUnit.SECONDS);
            expireTimers();
            awaitReleaseCount(expectedReleaseCount);
            createdConnection.remove();
        }

        private void assertReleasedSince(int releasedBefore, int establishedBefore, int expected) {
            if (activePermits.get() != 0
                    || releasedBeforeEstablished.get() - releasedBefore != expected
                    || releasedEstablished.get() != establishedBefore) {
                throw new IllegalStateException("Cap-sized benchmark admission accounting did not recover");
            }
        }

        private void completeCycle() {
            if (!retainedPacketTasks.isEmpty() || activePermits.get() != 0) {
                throw new IllegalStateException("Cap-sized benchmark cycle did not complete cleanup");
            }
            connections.clear();
            createdConnection.remove();
            deferredTasks.remove();
        }

        private void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                runRetainedPacketTasks();
            } finally {
                try {
                    if (!runtime.close(Duration.ofSeconds(10))) {
                        throw new IllegalStateException("Cap-sized benchmark runtime did not close");
                    }
                    if (overAdmitted.get()) {
                        throw new IllegalStateException("Cap-sized benchmark reached listener admission above its limit");
                    }
                } finally {
                    retainedPacketTasks.clear();
                    connections.clear();
                    createdConnection.remove();
                    deferredTasks.remove();
                    executor.close();
                }
            }
        }
    }

    private static QuicServerRuntime.Builder serverRuntimeBuilder(Executor executor,
                                                                  Tls serverTls,
                                                                  int pendingLimit) {
        return QuicServerRuntime.builder()
                .executor(executor)
                .tls(serverTls)
                .quicConfig(benchmarkQuicConfig())
                .applicationProtocols(List.of("h3"))
                .bindAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                .maxPendingHandshakes(pendingLimit);
    }

    private static QuicConfig benchmarkQuicConfig() {
        return QuicConfig.builder()
                .availableVersions(List.of(QuicVersion.QUIC_V1))
                .idleTimeout(Duration.ZERO)
                .buildPrototype();
    }

    private static Tls serverTls() throws Exception {
        return serverTls(LIFECYCLE_SESSION_CACHE_SIZE);
    }

    private static Tls serverTls(int sessionCacheSize) throws Exception {
        KeyStore keyStore = loadBenchmarkKeyStore(BENCHMARK_KEYSTORE_RESOURCE);
        if (!(keyStore.getKey(BENCHMARK_KEY_ALIAS, BENCHMARK_STORE_PASSWORD.toCharArray())
                instanceof PrivateKey privateKey)) {
            throw new IllegalStateException("Lifecycle benchmark server key is not a private key");
        }
        Certificate[] certificateChain = keyStore.getCertificateChain(BENCHMARK_KEY_ALIAS);
        if (certificateChain == null || certificateChain.length == 0) {
            throw new IllegalStateException("Lifecycle benchmark server certificate chain is empty");
        }
        List<X509Certificate> x509CertificateChain = new ArrayList<>(certificateChain.length);
        for (Certificate certificate : certificateChain) {
            if (!(certificate instanceof X509Certificate x509Certificate)) {
                throw new IllegalStateException("Lifecycle benchmark server certificate is not X.509");
            }
            x509CertificateChain.add(x509Certificate);
        }
        return Tls.builder()
                .sessionCacheSize(sessionCacheSize)
                .privateKey(privateKey)
                .privateKeyCertChain(x509CertificateChain)
                .build();
    }

    private static KeyStore loadBenchmarkKeyStore(String resource) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream input = QuicServerAdmissionJmhBenchmark.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing lifecycle benchmark key store: " + resource);
            }
            keyStore.load(input, BENCHMARK_STORE_PASSWORD.toCharArray());
        }
        return keyStore;
    }

    private static QuicServerHandshakeAdmission.Permit acquirePermit(QuicServerHandshakeAdmission admission) {
        long reservation = admission.tryReserve();
        if (reservation == QuicServerHandshakeAdmission.NO_RESERVATION) {
            return null;
        }
        return admission.materializePermit(reservation, NOOP_CONNECTION_PERMIT);
    }

    private static ExecutorService newBenchmarkExecutor(String name, int threadCount) {
        return Executors.newFixedThreadPool(threadCount,
                                            Thread.ofPlatform().name(name + "-", 0).factory());
    }

    private static Throwable collectBenchmarkFailure(Throwable existing, Throwable next) {
        if (existing == null) {
            return next;
        }
        if (existing != next) {
            existing.addSuppressed(next);
        }
        return existing;
    }

    private static void throwBenchmarkFailure(Throwable failure, String message) {
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (failure != null) {
            throw new IllegalStateException(message, failure);
        }
    }

    private static void terminateAndAwait(QuicConnection connection) throws Exception {
        if (connection == null) {
            return;
        }
        connection.terminate(BENCHMARK_CLOSE);
        connection.whenTerminated().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

}
