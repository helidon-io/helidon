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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.quic.QuicEndpoint.QuicDatagram;
import io.helidon.quic.QuicRttEstimator.QuicRttEstimatorState;
import io.helidon.quic.frame.AckFrame;
import io.helidon.quic.frame.AckFrame.AckRange;
import io.helidon.quic.frame.PingFrame;
import io.helidon.quic.frame.QuicFrame;
import io.helidon.quic.packet.PacketSpace;
import io.helidon.quic.packet.QuicPacket;
import io.helidon.quic.packet.QuicPacket.HeadersType;
import io.helidon.quic.packet.QuicPacket.PacketNumberSpace;
import io.helidon.quic.packet.QuicPacket.PacketType;

import com.sun.management.ThreadMXBean;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmarks the established-path operations added by QUIC path ownership.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class QuicPathJmhBenchmark {
    private static final int DATAGRAM_SIZE = 1200;
    private static final Deadline FIXED_DEADLINE = Deadline.of(Instant.EPOCH);
    private static final TimeLine FIXED_TIME_LINE = () -> FIXED_DEADLINE;
    private static final long VALIDATION_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(1);

    @Benchmark
    public boolean establishedPathOneRttIngress(IngressState state) {
        PathState path = state.nextPath();
        QuicPathManager.ReceiveContext ingress =
                new QuicPathManager.ReceiveContext(path.peerAddress, DATAGRAM_SIZE);
        path.ingressQueue.add(path.manager.receive(ingress));
        QuicPathManager.ReceiveContext context = path.ingressQueue.remove();
        QuicPathManager.ReceiveResult result = path.manager.authenticated(context,
                                                                          ++path.packetNumber,
                                                                          true,
                                                                          path.validationTimeout);
        return result.accepted() && !context.amplificationBudgetIncreased();
    }

    @Benchmark
    public long establishedPathOutboundPermit(IngressState state) {
        PathState path = state.nextPath();
        QuicPathManager.SendPermit permit = path.manager.reserve(DATAGRAM_SIZE).orElseThrow();
        long generation = permit.generation();
        permit.commit();
        return generation;
    }

    @Benchmark
    public long establishedPathAckGeneration(IngressState state) {
        PathState path = state.nextPath();
        if (!path.recoveryState.runIfCurrent(path.manager.generation(), path.ackAction)) {
            throw new IllegalStateException("Established QUIC path generation changed during the benchmark");
        }
        return path.ackAction.acknowledged;
    }

    /**
     * Measures one ACK-processing call, excluding flight preparation and cleanup.
     *
     * @param state packet-space fixture
     * @return largest acknowledged packet number
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public long establishedPathAckRanges(AckState state) {
        state.packetSpace.processAckFrame(state.ackFrame);
        return state.packetSpace.largestPeerAcknowledgedPacketNumber();
    }

    /**
     * Measures worker-thread allocation inside ACK processing, excluding invocation fixtures.
     * The allocation-counter reads make this method's timing unsuitable for latency comparisons.
     *
     * @param state packet-space fixture
     * @param counters allocation and ACK-operation counters
     * @return largest acknowledged packet number
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public long establishedPathAckRangesAllocation(AckState state, AckAllocationCounters counters) {
        long before = counters.allocationBean.getThreadAllocatedBytes(counters.threadId);
        state.packetSpace.processAckFrame(state.ackFrame);
        long allocated = counters.allocationBean.getThreadAllocatedBytes(counters.threadId) - before;
        if (before < 0 || allocated < 0) {
            throw new IllegalStateException("Worker-thread allocation counter is unavailable or moved backwards");
        }
        counters.allocatedBytes += allocated;
        counters.ackOperations++;
        return state.packetSpace.largestPeerAcknowledgedPacketNumber();
    }

    /**
     * Measures ACK recovery with a previous positive RTT sample and PTO backoff already primed.
     *
     * @param state recovery fixture
     * @return largest acknowledged packet number
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public long ackRecovery(AckRecoveryState state) {
        state.packetSpace.processAckFrame(state.ackFrame);
        return state.packetSpace.largestPeerAcknowledgedPacketNumber();
    }

    /**
     * Measures only worker allocation inside the primed ACK-recovery call.
     * Allocation-counter overhead makes this method unsuitable for timing comparisons.
     *
     * @param state recovery fixture
     * @param counters allocation and ACK-operation counters
     * @return largest acknowledged packet number
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public long ackRecoveryAllocation(AckRecoveryState state, AckAllocationCounters counters) {
        long before = counters.allocationBean.getThreadAllocatedBytes(counters.threadId);
        state.packetSpace.processAckFrame(state.ackFrame);
        long allocated = counters.allocationBean.getThreadAllocatedBytes(counters.threadId) - before;
        if (before < 0 || allocated < 0) {
            throw new IllegalStateException("Worker-thread allocation counter is unavailable or moved backwards");
        }
        counters.allocatedBytes += allocated;
        counters.ackOperations++;
        return state.packetSpace.largestPeerAcknowledgedPacketNumber();
    }

    @Benchmark
    public long establishedPathPacketSent(PacketSentState state) {
        state.packetSpace.packetSent(state.packet,
                                     -1,
                                     state.packetNumber,
                                     state.recoveryState.generation());
        state.sent = true;
        return state.packetNumber;
    }

    @Benchmark
    @OperationsPerInvocation(2)
    public long orderedAckPublication(AckPublicationState state) {
        long firstPacketNumber = state.nextPacketNumber++;
        long secondPacketNumber = state.nextPacketNumber++;
        state.packetSpace.packetReceived(PacketType.ONERTT, firstPacketNumber, true);
        state.packetSpace.packetReceived(PacketType.ONERTT, secondPacketNumber, true);
        AckFrame ackFrame = state.packetSpace.nextAckFrame(true).orElseThrow();
        long largestAcknowledged = ackFrame.largestAcknowledged();
        if (largestAcknowledged != secondPacketNumber
                || ackFrame.ackRanges().size() != 1
                || !ackFrame.isRangeAcknowledged(firstPacketNumber, secondPacketNumber)) {
            throw new IllegalStateException("Expected one ACK range through packets "
                                                    + firstPacketNumber + "-" + secondPacketNumber
                                                    + ", got " + ackFrame);
        }
        return largestAcknowledged;
    }

    @Benchmark
    public long establishedPathDatagramSend(OutboundState state,
                                            OutboundThreadState threadState)
            throws InterruptedException {
        QuicPathManager manager = state.nextPath();
        threadState.payload.clear();
        QuicPathManager.SendPermit permit = manager.reserve(DATAGRAM_SIZE).orElseThrow();
        state.endpoint.pushDatagram(threadState.receiver, state.destination, threadState.payload, permit);
        return threadState.receiver.awaitCompletion();
    }

    private static QuicTLSEngine createTlsEngine() {
        return (QuicTLSEngine) Proxy.newProxyInstance(
                QuicTLSEngine.class.getClassLoader(),
                new Class<?>[] {QuicTLSEngine.class},
                (proxy, method, arguments) -> {
                    Class<?> resultType = method.getReturnType();
                    if (!resultType.isPrimitive() || resultType == void.class) {
                        return null;
                    }
                    if (resultType == boolean.class) {
                        return false;
                    }
                    if (resultType == char.class) {
                        return '\0';
                    }
                    if (resultType == byte.class) {
                        return (byte) 0;
                    }
                    if (resultType == short.class) {
                        return (short) 0;
                    }
                    if (resultType == long.class) {
                        return 0L;
                    }
                    if (resultType == float.class) {
                        return 0F;
                    }
                    if (resultType == double.class) {
                        return 0D;
                    }
                    return 0;
                });
    }

    /**
     * ACK workspace lifecycle measured by an ACK-processing invocation.
     */
    public enum AckPacketSpaceLifecycle {
        /**
         * Reuse a packet space whose ACK workspace has already handled the selected shape.
         */
        REUSED,
        /**
         * Process the first ACK on a new packet space, including initial ACK workspace allocation.
         */
        FIRST_ACK
    }

    /**
     * Packet state visible to a measured ACK-processing call.
     */
    public enum AckWorkload {
        /**
         * Newly acknowledge packets from the prepared flight.
         */
        NEW_ACK,
        /**
         * Repeat the ACK after all packets have been acknowledged or discarded.
         */
        DUPLICATE
    }

    /**
     * Connection role and packet space used by the targeted ACK-recovery measurements.
     */
    public enum AckRecoveryScenario {
        /**
         * Client application space after packet-space handshake confirmation, with a normal encoded ACK delay.
         */
        APPLICATION(PacketNumberSpace.APPLICATION, true),
        /**
         * Server Initial space before Handshake keys are available.
         */
        SERVER_INITIAL(PacketNumberSpace.INITIAL, false),
        /**
         * Client Initial space before Handshake keys are available.
         */
        CLIENT_INITIAL(PacketNumberSpace.INITIAL, true);

        private final PacketNumberSpace packetNumberSpace;
        private final boolean client;

        AckRecoveryScenario(PacketNumberSpace packetNumberSpace, boolean client) {
            this.packetNumberSpace = packetNumberSpace;
            this.client = client;
        }
    }

    /**
     * Established-path state for ingress, send-permit, and ACK-generation measurements.
     */
    @State(Scope.Thread)
    public static class IngressState {
        /**
         * Number of independent connections cycled by the benchmark thread.
         */
        @Param({"1", "16"})
        public int connectionCount;

        private PathState[] paths;
        private int nextPath;

        @Setup(Level.Trial)
        public void setUp() {
            InetAddress loopback = InetAddress.getLoopbackAddress();
            InetSocketAddress peer = new InetSocketAddress(loopback, 4433);
            paths = new PathState[connectionCount];
            for (int i = 0; i < paths.length; i++) {
                InetSocketAddress local = new InetSocketAddress(loopback, 10_000 + i);
                QuicPathManager manager = new QuicPathManager(false, local, peer, DATAGRAM_SIZE);
                manager.addressValidated(peer);
                paths[i] = new PathState(manager, peer);
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            for (PathState path : paths) {
                path.manager.close();
            }
        }

        private PathState nextPath() {
            PathState path = paths[nextPath++];
            if (nextPath == paths.length) {
                nextPath = 0;
            }
            return path;
        }
    }

    /**
     * Established-path endpoint state for synchronous and asynchronous sends.
     */
    @State(Scope.Benchmark)
    public static class OutboundState {
        /**
         * Whether the endpoint uses its asynchronous datagram queue.
         */
        @Param({"false", "true"})
        public boolean sendAsync;

        /**
         * Number of independent connections cycled by the benchmark thread.
         */
        @Param({"1", "16"})
        public int connectionCount;

        private ExecutorService executorService;
        private BenchmarkQuicInstance instance;
        private QuicEndpoint endpoint;
        private DatagramChannel sink;
        private InetSocketAddress destination;
        private QuicPathManager[] paths;
        private final AtomicInteger nextPath = new AtomicInteger();

        @Setup(Level.Trial)
        public void setUp() {
            InetAddress loopback = InetAddress.getLoopbackAddress();
            try {
                sink = DatagramChannel.open();
                sink.bind(new InetSocketAddress(loopback, 0));
                destination = (InetSocketAddress) sink.getLocalAddress();
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to initialize the QUIC path benchmark sink.", e);
            }

            Executor executor;
            if (sendAsync) {
                executorService = Executors.newSingleThreadExecutor(Thread.ofPlatform()
                                                                            .daemon()
                                                                            .name("quic-path-jmh-writer")
                                                                            .factory());
                executor = executorService;
            } else {
                executor = Runnable::run;
            }
            instance = new BenchmarkQuicInstance(sendAsync, executor);
            endpoint = QuicEndpoint.QuicEndpointFactory.create()
                    .createVirtualThreadedEndpoint(instance,
                                                   instance.runtimeConfig,
                                                   "quic-path-jmh",
                                                   new InetSocketAddress(loopback, 0),
                                                   new QuicTimerQueue(() -> { }, () -> "quic-path-jmh-timer"));
            instance.endpoint = endpoint;
            InetSocketAddress localAddress = (InetSocketAddress) endpoint.localAddress();
            paths = new QuicPathManager[connectionCount];
            for (int i = 0; i < paths.length; i++) {
                QuicPathManager manager = new QuicPathManager(false, localAddress, destination, DATAGRAM_SIZE);
                manager.addressValidated(destination);
                paths[i] = manager;
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            for (QuicPathManager path : paths) {
                path.close();
            }
            endpoint.close();
            try {
                sink.close();
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to close the QUIC path benchmark sink.", e);
            }
            if (executorService != null) {
                executorService.shutdown();
                try {
                    if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                        executorService.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while stopping the QUIC path benchmark executor.", e);
                }
            }
            instance.checkFailure();
        }

        private QuicPathManager nextPath() {
            return paths[Math.floorMod(nextPath.getAndIncrement(), paths.length)];
        }
    }

    /**
     * Per-thread datagram storage and completion state for a shared endpoint.
     */
    @State(Scope.Thread)
    public static class OutboundThreadState {
        private final BenchmarkReceiver receiver = new BenchmarkReceiver();
        private final ByteBuffer payload = ByteBuffer.allocateDirect(DATAGRAM_SIZE);
    }

    /**
     * Real packet-space workload for ACK-range and in-flight scaling.
     * Invocation setup and cleanup are excluded from timing, but remain visible to process-wide profilers.
     */
    @State(Scope.Thread)
    public static class AckState {
        private static final int MAX_IN_FLIGHT_PACKETS = 16 * 1024 * 1024 / DATAGRAM_SIZE;
        private static final int MAX_ACK_RANGES = 1024;
        private static final long PATH_GENERATION = 1;

        /**
         * Number of ack-eliciting packets in each prepared flight, bounded by the default 16 MiB flight limit.
         */
        @Param({"64"})
        public int inFlightPackets;

        /**
         * Number of ranges spread across the complete flight.
         * Fragmented ACKs acknowledge approximately half the packets.
         */
        @Param({"1"})
        public int ackRangeCount;

        /**
         * Whether the measured ACK uses a primed packet space or a new packet space with unused ACK workspace.
         */
        @Param({"REUSED"})
        public AckPacketSpaceLifecycle ackPacketSpaceLifecycle;

        /**
         * Whether the ACK newly acknowledges the prepared flight or repeats an ACK after all flight state is removed.
         */
        @Param({"NEW_ACK"})
        public AckWorkload ackWorkload;

        private AckPacketEmitter emitter;
        private QuicRuntimeConfig runtimeConfig;
        private QuicTLSEngine tlsEngine;
        private PacketSpaceManager packetSpace;
        private AckFrame ackFrame;
        private List<AckRange> ranges;

        static void validateParameters(int inFlightPackets,
                                       int ackRangeCount,
                                       AckPacketSpaceLifecycle lifecycle,
                                       AckWorkload workload) {
            if (inFlightPackets < 1 || inFlightPackets > MAX_IN_FLIGHT_PACKETS) {
                throw new IllegalArgumentException("inFlightPackets must be between 1 and " + MAX_IN_FLIGHT_PACKETS);
            }
            if (ackRangeCount < 1 || ackRangeCount > MAX_ACK_RANGES
                    || ackRangeCount > (inFlightPackets + 1) / 2) {
                throw new IllegalArgumentException("ackRangeCount must be between 1 and "
                                                           + Math.min(MAX_ACK_RANGES, (inFlightPackets + 1) / 2)
                                                           + " for inFlightPackets=" + inFlightPackets);
            }
            if (lifecycle == AckPacketSpaceLifecycle.FIRST_ACK && workload == AckWorkload.DUPLICATE) {
                throw new IllegalArgumentException("DUPLICATE requires a REUSED packet space");
            }
        }

        static List<AckRange> createRanges(int inFlightPackets, int ackRangeCount) {
            if (ackRangeCount == 1) {
                return List.of(AckRange.of(0, inFlightPackets - 1L));
            }
            int acknowledged = (inFlightPackets + 1) / 2;
            int skipped = inFlightPackets - acknowledged;
            List<AckRange> result = new ArrayList<>(ackRangeCount);
            for (int i = 0; i < ackRangeCount; i++) {
                int rangeLength = acknowledged / ackRangeCount + (i < acknowledged % ackRangeCount ? 1 : 0);
                int gapLength = i == 0 ? 0
                        : skipped / (ackRangeCount - 1) + (i <= skipped % (ackRangeCount - 1) ? 1 : 0);
                result.add(AckRange.of(i == 0 ? 0 : gapLength - 1L, rangeLength - 1L));
            }
            return List.copyOf(result);
        }

        /**
         * Creates the ACK shape, primes reusable packet-space capacity, and prepares duplicate ACK state once.
         */
        @Setup(Level.Trial)
        public void setUpTrial() {
            validateParameters(inFlightPackets, ackRangeCount, ackPacketSpaceLifecycle, ackWorkload);
            runtimeConfig = QuicRuntimeConfig.create(QuicConfig.create());
            tlsEngine = createTlsEngine();
            ranges = createRanges(inFlightPackets, ackRangeCount);
            if (ackPacketSpaceLifecycle == AckPacketSpaceLifecycle.REUSED) {
                createPacketSpace();
                setUpInvocation();
                packetSpace.processAckFrame(ackFrame);
                tearDownInvocation();
            }
        }

        /**
         * Prepares a fresh flight for a new ACK, or reuses the duplicate ACK state prepared during trial setup.
         */
        @Setup(Level.Invocation)
        public void setUpInvocation() {
            if (ackWorkload == AckWorkload.DUPLICATE && ackFrame != null) {
                return;
            }
            if (ackPacketSpaceLifecycle == AckPacketSpaceLifecycle.FIRST_ACK) {
                createPacketSpace();
            }
            // Reset recovery and transmitter flags through the existing cleanup contract, preserving ACK workspace capacity.
            packetSpace.retry();
            emitter.acknowledgedPackets = 0;
            long firstPacketNumber = packetSpace.allocateNextPN();
            packetSpace.packetSent(new BenchmarkPacket(firstPacketNumber, PacketNumberSpace.HANDSHAKE),
                                   -1, firstPacketNumber, PATH_GENERATION);
            long packetNumber = firstPacketNumber;
            for (int i = 1; i < inFlightPackets; i++) {
                packetNumber = packetSpace.allocateNextPN();
                if (packetNumber != firstPacketNumber + i) {
                    throw new IllegalStateException("Unexpected QUIC benchmark packet number " + packetNumber);
                }
                packetSpace.packetSent(new BenchmarkPacket(packetNumber, PacketNumberSpace.HANDSHAKE),
                                       -1, packetNumber, PATH_GENERATION);
            }
            ackFrame = AckFrame.create(packetNumber, 0, ranges);
            if (ackWorkload == AckWorkload.DUPLICATE) {
                packetSpace.processAckFrame(ackFrame);
                packetSpace.retry();
                emitter.acknowledgedPackets = 0;
            }
        }

        /**
         * Verifies callback accounting and removes unacknowledged state from a newly acknowledged flight.
         */
        @TearDown(Level.Invocation)
        public void tearDownInvocation() {
            try {
                int expected = ackWorkload == AckWorkload.DUPLICATE ? 0
                        : ackRangeCount == 1 ? inFlightPackets : (inFlightPackets + 1) / 2;
                if (emitter.acknowledgedPackets != expected) {
                    throw new IllegalStateException("Expected " + expected + " acknowledged packets, got "
                                                            + emitter.acknowledgedPackets);
                }
                if (packetSpace.largestPeerAcknowledgedPacketNumber() != ackFrame.largestAcknowledged()) {
                    throw new IllegalStateException("ACK did not advance to " + ackFrame.largestAcknowledged());
                }
            } finally {
                if (ackPacketSpaceLifecycle == AckPacketSpaceLifecycle.FIRST_ACK) {
                    closePacketSpace();
                } else if (ackWorkload == AckWorkload.NEW_ACK) {
                    packetSpace.discardOutstandingPackets(_ -> true);
                }
            }
        }

        /**
         * Closes the packet space and its bounded timer queue.
         */
        @TearDown(Level.Trial)
        public void tearDownTrial() {
            closePacketSpace();
        }

        private void createPacketSpace() {
            emitter = new AckPacketEmitter();
            QuicRttEstimator rttEstimator = QuicRttEstimator.create(runtimeConfig.recovery());
            QuicCongestionController congestionController = QuicCubicCongestionController.create(runtimeConfig,
                                                                                                 "quic-path-jmh-ack",
                                                                                                 rttEstimator,
                                                                                                 DATAGRAM_SIZE);
            packetSpace = new PacketSpaceManager(PacketNumberSpace.HANDSHAKE,
                                                 emitter,
                                                 FIXED_TIME_LINE,
                                                 rttEstimator,
                                                 congestionController,
                                                 tlsEngine,
                                                 () -> "quic-path-jmh-ack",
                                                 new PacketSpaceManager.PathRecoveryState(PATH_GENERATION),
                                                 runtimeConfig.transportParameters().ackDelayExponent(),
                                                 runtimeConfig.transportParameters().maxAckDelay().toMillis(),
                                                 _ -> {
                                                 });
        }

        private void closePacketSpace() {
            try {
                if (packetSpace != null) {
                    packetSpace.close();
                }
            } finally {
                if (emitter != null) {
                    emitter.timer().stop();
                }
            }
        }
    }

    /**
     * Bounded two-packet ACK fixture with a deterministic 12 ms RTT after a 10 ms sample and PTO backoff four.
     * The application scenario decodes ACK delay 125 with exponent three. Initial scenarios ignore ACK delay.
     * Packet construction, recovery priming, and verification remain outside the measured operation.
     */
    @State(Scope.Thread)
    public static class AckRecoveryState {
        private static final long PATH_GENERATION = 1;
        private static final long ENCODED_ACK_DELAY = 125;
        private static final Deadline SEND_DEADLINE = FIXED_DEADLINE.plusMillis(10);
        private static final Deadline ACK_DEADLINE = SEND_DEADLINE.plusMillis(12);
        private static final List<AckRange> ACK_RANGES = List.of(AckRange.of(0, 0));
        private static final QuicRttEstimatorState APPLICATION_ESTIMATE =
                QuicRttEstimatorState.create(12_000, 10_000, 10_125, 4_000, 2);
        private static final QuicRttEstimatorState INITIAL_ESTIMATE =
                QuicRttEstimatorState.create(12_000, 10_000, 10_250, 4_250, 2);

        /**
         * Packet space and connection role whose ACK-recovery path is measured.
         */
        @Param({"APPLICATION", "SERVER_INITIAL", "CLIENT_INITIAL"})
        public AckRecoveryScenario ackRecoveryScenario;

        private AckRecoveryEmitter emitter;
        private QuicRuntimeConfig runtimeConfig;
        private QuicRttEstimator rttEstimator;
        private PacketSpaceManager packetSpace;
        private QuicTLSEngine tlsEngine;
        private AckFrame ackFrame;
        private Deadline now = FIXED_DEADLINE;

        /**
         * Builds a reusable real TLS role and non-transmitting emitter.
         * No TLS handshake, crypto operation, timer dispatch, or network traffic is measured.
         */
        @Setup(Level.Trial)
        public void setUpTrial() {
            runtimeConfig = QuicRuntimeConfig.create(QuicConfig.create());
            Keys keys = Keys.builder()
                    .keystore(store -> store.keystore(Resource.create("io/helidon/quic/benchmark/server-keystore.p12"))
                            .passphrase("changeit")
                            .keyAlias("server"))
                    .build();
            Tls tls = Tls.builder().privateKey(keys).privateKeyCertChain(keys).sessionCacheSize(0).build();
            tlsEngine = QuicTLSContext.create(tls).createEngine();
            tlsEngine.clientMode(ackRecoveryScenario.client);
            // Initialize the production role implementation before any measured Initial clientMode() call.
            tlsEngine.handshakeState();
            emitter = new AckRecoveryEmitter();
        }

        /**
         * Acknowledges a first packet to prime RTT and ACK workspace, then prepares a second packet with backoff four.
         * A fresh packet space bounds the flight and excludes growing optimistic-ACK packet-number skip history.
         */
        @Setup(Level.Invocation)
        public void setUpInvocation() {
            now = FIXED_DEADLINE;
            rttEstimator = QuicRttEstimator.create(runtimeConfig.recovery());
            QuicCongestionController congestionController =
                    QuicCubicCongestionController.create(runtimeConfig,
                                                         "quic-path-jmh-recovery",
                                                         rttEstimator,
                                                         DATAGRAM_SIZE);
            packetSpace = new PacketSpaceManager(ackRecoveryScenario.packetNumberSpace,
                                                 emitter,
                                                 () -> now,
                                                 rttEstimator,
                                                 congestionController,
                                                 tlsEngine,
                                                 () -> "quic-path-jmh-recovery",
                                                 new PacketSpaceManager.PathRecoveryState(PATH_GENERATION),
                                                 3,
                                                 25,
                                                 _ -> { });
            packetSpace.updatePeerTransportParameters(25, 3);
            long primingPacketNumber = packetSpace.allocateNextPN();
            packetSpace.packetSent(new BenchmarkPacket(primingPacketNumber, ackRecoveryScenario.packetNumberSpace),
                                   -1, primingPacketNumber, PATH_GENERATION);
            if (ackRecoveryScenario == AckRecoveryScenario.APPLICATION) {
                // A nonzero confirmation boundary precedes the second, measured ACK.
                packetSpace.confirmHandshake();
            }
            now = SEND_DEADLINE;
            packetSpace.processAckFrame(AckFrame.create(primingPacketNumber, ENCODED_ACK_DELAY, ACK_RANGES));
            rttEstimator.increasePtoBackoff();
            rttEstimator.increasePtoBackoff();
            emitter.acknowledgedPackets = 0;
            long packetNumber = packetSpace.allocateNextPN();
            packetSpace.packetSent(new BenchmarkPacket(packetNumber, ackRecoveryScenario.packetNumberSpace),
                                   -1, packetNumber, PATH_GENERATION);
            // The measured ACK covers only the second packet; the first has already been acknowledged.
            ackFrame = AckFrame.create(packetNumber, ENCODED_ACK_DELAY, ACK_RANGES);
            now = ACK_DEADLINE;
            if (primingPacketNumber != 0 || packetNumber != 1
                    || rttEstimator.state().rttSampleCount() != 1 || rttEstimator.ptoBackoff() != 4
                    || tlsEngine.clientMode() != ackRecoveryScenario.client) {
                throw new IllegalStateException("ACK recovery fixture was not primed for " + ackRecoveryScenario);
            }
        }

        /**
         * Checks ACK eligibility and RTT without imposing the candidate's server backoff behavior,
         * then closes the packet space.
         */
        @TearDown(Level.Invocation)
        public void tearDownInvocation() {
            try {
                if (emitter.acknowledgedPackets != 1
                        || packetSpace.largestPeerAcknowledgedPacketNumber() != ackFrame.largestAcknowledged()) {
                    throw new IllegalStateException("ACK recovery did not acknowledge the prepared packet");
                }
                QuicRttEstimatorState expected = ackRecoveryScenario == AckRecoveryScenario.APPLICATION
                        ? APPLICATION_ESTIMATE : INITIAL_ESTIMATE;
                QuicRttEstimatorState actual = rttEstimator.state();
                if (!expected.equals(actual)) {
                    throw new IllegalStateException("ACK recovery expected " + expected + ", got " + actual);
                }
            } finally {
                closePacketSpace();
            }
        }

        /**
         * Closes the packet space and the emitter's unused timer queue.
         */
        @TearDown(Level.Trial)
        public void tearDownTrial() {
            try {
                closePacketSpace();
            } finally {
                if (emitter != null) {
                    emitter.timer().stop();
                }
            }
        }

        QuicRttEstimatorState rttState() {
            return rttEstimator.state();
        }

        long ptoBackoff() {
            return rttEstimator.ptoBackoff();
        }

        long packetNumber() {
            return ackFrame.largestAcknowledged();
        }

        PacketNumberSpace packetNumberSpace() {
            return packetSpace.packetNumberSpace();
        }

        boolean clientMode() {
            return tlsEngine.clientMode();
        }

        private void closePacketSpace() {
            if (packetSpace != null) {
                packetSpace.close();
                packetSpace = null;
            }
        }
    }

    /**
     * Worker-thread bytes and ACK counts; their ratio is allocated bytes per ACK.
     * JMH reports both as unnormalized event totals.
     */
    @AuxCounters(AuxCounters.Type.EVENTS)
    @State(Scope.Thread)
    public static class AckAllocationCounters {
        /**
         * Bytes allocated on the JMH worker inside measured ACK calls.
         */
        public long allocatedBytes;

        /**
         * Number of ACK calls bracketed by the allocation counter.
         */
        public long ackOperations;

        private ThreadMXBean allocationBean;
        private long threadId;

        /**
         * Requires a supported, enabled allocation counter on the actual benchmark worker.
         */
        @Setup(Level.Trial)
        public void setUp() {
            var bean = ManagementFactory.getThreadMXBean();
            if (!(bean instanceof ThreadMXBean extendedBean) || !extendedBean.isThreadAllocatedMemorySupported()) {
                throw new IllegalStateException("This JVM does not support worker-thread allocation counters");
            }
            allocationBean = extendedBean;
            if (!allocationBean.isThreadAllocatedMemoryEnabled()) {
                allocationBean.setThreadAllocatedMemoryEnabled(true);
            }
            threadId = Thread.currentThread().threadId();
            if (allocationBean.getThreadAllocatedBytes(threadId) < 0) {
                throw new IllegalStateException("Worker-thread allocation counter is unavailable");
            }
        }
    }

    /**
     * Recovery generation shared by the normal-send benchmark threads.
     */
    @State(Scope.Benchmark)
    public static class PacketSentSharedState {
        private final PacketSpaceManager.PathRecoveryState recoveryState =
                new PacketSpaceManager.PathRecoveryState(1);
    }

    /**
     * Per-thread packet-space state for normal ack-eliciting send accounting.
     */
    @State(Scope.Thread)
    public static class PacketSentState {
        private final BenchmarkPacket packet = new BenchmarkPacket(0, PacketNumberSpace.APPLICATION);
        private BenchmarkPacketEmitter emitter;
        private PacketSpaceManager packetSpace;
        private PacketSpaceManager.PathRecoveryState recoveryState;
        private long packetNumber;
        private boolean sent;

        @Setup(Level.Trial)
        public void setUpTrial(PacketSentSharedState sharedState) {
            emitter = new BenchmarkPacketEmitter();
            QuicRuntimeConfig runtimeConfig = QuicRuntimeConfig.create(QuicConfig.create());
            QuicRttEstimator rttEstimator = QuicRttEstimator.create(runtimeConfig.recovery());
            QuicCongestionController congestionController =
                    QuicCubicCongestionController.create(runtimeConfig,
                                                         "quic-path-jmh-send",
                                                         rttEstimator,
                                                         DATAGRAM_SIZE);
            recoveryState = sharedState.recoveryState;
            packetSpace = new PacketSpaceManager(PacketNumberSpace.APPLICATION,
                                                 emitter,
                                                 TimeSource.source(),
                                                 rttEstimator,
                                                 congestionController,
                                                 createTlsEngine(),
                                                 () -> "quic-path-jmh-send",
                                                 recoveryState,
                                                 runtimeConfig.transportParameters().ackDelayExponent(),
                                                 runtimeConfig.transportParameters().maxAckDelay().toMillis(),
                                                 _ -> {
                                                 });
        }

        @Setup(Level.Invocation)
        public void setUpInvocation() {
            packetNumber = packetSpace.allocateNextPN();
            sent = false;
        }

        @TearDown(Level.Invocation)
        public void tearDownInvocation() {
            if (sent) {
                packetSpace.processAckFrame(AckFrame.create(packetNumber, 0, List.of(AckRange.of(0, 0))));
            }
        }

        @TearDown(Level.Trial)
        public void tearDownTrial() {
            packetSpace.close();
            emitter.timer.stop();
        }
    }

    /**
     * Per-thread application packet space for ordered ACK publication.
     */
    @State(Scope.Thread)
    public static class AckPublicationState {
        private PacketSpaceManager packetSpace;
        private long nextPacketNumber;

        @Setup(Level.Trial)
        public void setUpTrial() {
            AckPublicationEmitter emitter = new AckPublicationEmitter();
            QuicRuntimeConfig runtimeConfig = QuicRuntimeConfig.create(QuicConfig.create());
            QuicRttEstimator rttEstimator = QuicRttEstimator.create(runtimeConfig.recovery());
            QuicCongestionController congestionController =
                    QuicCubicCongestionController.create(runtimeConfig,
                                                         "quic-path-jmh-ack-publication",
                                                         rttEstimator,
                                                         DATAGRAM_SIZE);
            packetSpace = new PacketSpaceManager(PacketNumberSpace.APPLICATION,
                                                 emitter,
                                                 FIXED_TIME_LINE,
                                                 rttEstimator,
                                                 congestionController,
                                                 createTlsEngine(),
                                                 () -> "quic-path-jmh-ack-publication",
                                                 new PacketSpaceManager.PathRecoveryState(1),
                                                 runtimeConfig.transportParameters().ackDelayExponent(),
                                                 runtimeConfig.transportParameters().maxAckDelay().toMillis(),
                                                 _ -> {
                                                 });
        }

        @TearDown(Level.Trial)
        public void tearDownTrial() {
            packetSpace.close();
        }
    }

    private static class PathState {
        private final QuicPathManager manager;
        private final InetSocketAddress peerAddress;
        private final PacketSpaceManager.PathRecoveryState recoveryState;
        private final ConcurrentLinkedQueue<QuicPathManager.ReceiveContext> ingressQueue =
                new ConcurrentLinkedQueue<>();
        private final LongSupplier validationTimeout = () -> VALIDATION_TIMEOUT_NANOS;
        private final AckAction ackAction = new AckAction();
        private long packetNumber;

        private PathState(QuicPathManager manager, InetSocketAddress peerAddress) {
            this.manager = manager;
            this.peerAddress = peerAddress;
            this.recoveryState = new PacketSpaceManager.PathRecoveryState(manager.generation());
        }
    }

    private static final class AckAction implements Runnable {
        private long acknowledged;

        @Override
        public void run() {
            acknowledged++;
        }
    }

    private record BenchmarkPacket(long packetNumber, PacketNumberSpace numberSpace) implements QuicPacket {
        private static final QuicConnectionId CONNECTION_ID = PeerConnectionId.create(new byte[0]);
        private static final List<QuicFrame> FRAMES = List.of(PingFrame.create());

        @Override
        public QuicConnectionId destinationId() {
            return CONNECTION_ID;
        }

        @Override
        public int size() {
            return DATAGRAM_SIZE;
        }

        @Override
        public HeadersType headersType() {
            return switch (numberSpace) {
                case INITIAL, HANDSHAKE -> HeadersType.LONG;
                case APPLICATION, NONE -> HeadersType.SHORT;
            };
        }

        @Override
        public PacketType packetType() {
            return switch (numberSpace) {
                case INITIAL -> PacketType.INITIAL;
                case HANDSHAKE -> PacketType.HANDSHAKE;
                case APPLICATION, NONE -> PacketType.ONERTT;
            };
        }

        @Override
        public List<QuicFrame> frames() {
            return FRAMES;
        }
    }

    private static class BenchmarkPacketEmitter implements PacketEmitter {
        private final QuicTimerQueue timer = new QuicTimerQueue(() -> { }, () -> "quic-path-jmh-ack-timer");

        @Override
        public QuicTimerQueue timer() {
            return timer;
        }

        @Override
        public boolean retransmit(PacketSpace packetSpaceManager, QuicPacket packet, int attempts) {
            return false;
        }

        @Override
        public long emitAckPacket(PacketSpace packetSpaceManager, AckFrame ackFrame, boolean sendPing) {
            return -1;
        }

        @Override
        public void acknowledged(QuicPacket packet) {
        }

        @Override
        public boolean sendData(PacketNumberSpace packetNumberSpace) {
            return false;
        }

        @Override
        public Executor executor() {
            return Runnable::run;
        }

        @Override
        public void checkAbort(PacketNumberSpace packetNumberSpace) {
        }

        @Override
        public boolean isOpen() {
            return true;
        }
    }

    private static final class AckPacketEmitter extends BenchmarkPacketEmitter {
        private int acknowledgedPackets;

        @Override
        public void acknowledged(QuicPacket packet) {
            acknowledgedPackets++;
        }
    }

    private static final class AckRecoveryEmitter extends BenchmarkPacketEmitter {
        private int acknowledgedPackets;

        @Override
        public void acknowledged(QuicPacket packet) {
            acknowledgedPackets++;
        }

        @Override
        public void reschedule(QuicTimedEvent event) {
        }

        @Override
        public void reschedule(QuicTimedEvent event, Deadline deadline) {
        }
    }

    private static final class AckPublicationEmitter implements PacketEmitter {
        private static final Executor DIRECT_EXECUTOR = Runnable::run;

        @Override
        public QuicTimerQueue timer() {
            throw new IllegalStateException("ACK publication benchmark does not schedule timers");
        }

        @Override
        public boolean retransmit(PacketSpace packetSpaceManager, QuicPacket packet, int attempts) {
            return false;
        }

        @Override
        public long emitAckPacket(PacketSpace packetSpaceManager, AckFrame ackFrame, boolean sendPing) {
            throw new IllegalStateException("ACK publication benchmark does not emit packets");
        }

        @Override
        public void acknowledged(QuicPacket packet) {
        }

        @Override
        public boolean sendData(PacketNumberSpace packetNumberSpace) {
            return false;
        }

        @Override
        public Executor executor() {
            return DIRECT_EXECUTOR;
        }

        @Override
        public void reschedule(QuicTimedEvent event) {
        }

        @Override
        public void reschedule(QuicTimedEvent event, Deadline deadline) {
        }

        @Override
        public void checkAbort(PacketNumberSpace packetNumberSpace) {
        }

        @Override
        public boolean isOpen() {
            return false;
        }
    }

    private static final class BenchmarkReceiver implements QuicPacketReceiver {
        private final Semaphore completion = new Semaphore(0);
        private volatile Throwable failure;
        private long completed;

        @Override
        public List<QuicConnectionId> connectionIds() {
            return List.of();
        }

        @Override
        public List<PeerResetToken> activeResetTokens() {
            return List.of();
        }

        @Override
        public void processIncoming(SocketAddress source,
                                    ByteBuffer destinationConnectionId,
                                    QuicPacket.HeadersType headersType,
                                    ByteBuffer buffer) {
        }

        @Override
        public void onWriteError(Throwable failure) {
            this.failure = failure;
        }

        @Override
        public void processStatelessReset() {
        }

        @Override
        public void shutdown() {
        }

        @Override
        public void datagramSent(QuicDatagram datagram) {
            completed++;
            completion.release();
        }

        @Override
        public void datagramDiscarded(QuicDatagram datagram) {
            failure = new IllegalStateException("QUIC benchmark datagram was discarded");
            completion.release();
        }

        @Override
        public void datagramDropped(QuicDatagram datagram) {
            failure = new IllegalStateException("QUIC benchmark datagram was dropped");
            completion.release();
        }

        private long awaitCompletion() throws InterruptedException {
            completion.acquire();
            Throwable currentFailure = failure;
            if (currentFailure != null) {
                throw new IllegalStateException("QUIC benchmark send failed", currentFailure);
            }
            return completed;
        }
    }

    private static final class BenchmarkQuicInstance implements QuicInstance {
        private final QuicConfig config;
        private final QuicRuntimeConfig runtimeConfig;
        private final Executor executor;
        private volatile Throwable failure;
        private QuicEndpoint endpoint;

        private BenchmarkQuicInstance(boolean sendAsync, Executor executor) {
            this.config = QuicConfig.create();
            QuicRuntimeConfig defaults = QuicRuntimeConfig.create(config);
            QuicRuntimeConfig.Endpoint defaultEndpoint = defaults.endpoint();
            this.runtimeConfig = new QuicRuntimeConfig(
                    config,
                    new QuicRuntimeConfig.Endpoint(defaultEndpoint.channelType(),
                                                   defaultEndpoint.selectorThreading(),
                                                   defaultEndpoint.pollerUsePlatformThreads(),
                                                   defaultEndpoint.maxEndpoints(),
                                                   sendAsync,
                                                   defaultEndpoint.maxBufferedHigh(),
                                                   defaultEndpoint.maxBufferedLow(),
                                                   defaultEndpoint.useDirectBufferPool(),
                                                   defaultEndpoint.defaultDatagramSize()),
                    defaults.recovery(),
                    defaults.transportParameters(),
                    defaults.confidentialityLimits());
            this.executor = executor;
        }

        @Override
        public Executor executor() {
            return executor;
        }

        @Override
        public QuicEndpoint endpoint() {
            return endpoint;
        }

        @Override
        public void unmatchedQuicPacket(SocketAddress source,
                                        QuicPacket.HeadersType headersType,
                                        ByteBuffer buffer) {
        }

        @Override
        public void runtimeFailed(Throwable failure) {
            this.failure = failure;
        }

        @Override
        public boolean isVersionAvailable(QuicVersion version) {
            return QuicVersion.QUIC_V1.equals(version);
        }

        @Override
        public List<QuicVersion> availableVersions() {
            return List.of(QuicVersion.QUIC_V1);
        }

        @Override
        public boolean isClient() {
            return true;
        }

        @Override
        public String instanceId() {
            return "quic-path-jmh";
        }

        @Override
        public QuicTLSContext quicTlsContext() {
            throw new UnsupportedOperationException("TLS is not used by the QUIC path benchmark");
        }

        @Override
        public QuicConfig quicConfig() {
            return config;
        }

        private void checkFailure() {
            Throwable currentFailure = failure;
            if (currentFailure != null) {
                throw new IllegalStateException("QUIC endpoint failed during the benchmark", currentFailure);
            }
        }
    }
}
