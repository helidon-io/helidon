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

import io.helidon.quic.QuicEndpoint.QuicDatagram;
import io.helidon.quic.frame.AckFrame;
import io.helidon.quic.frame.AckFrame.AckRange;
import io.helidon.quic.frame.PingFrame;
import io.helidon.quic.frame.QuicFrame;
import io.helidon.quic.packet.PacketSpace;
import io.helidon.quic.packet.QuicPacket;
import io.helidon.quic.packet.QuicPacket.HeadersType;
import io.helidon.quic.packet.QuicPacket.PacketNumberSpace;
import io.helidon.quic.packet.QuicPacket.PacketType;

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

    @Benchmark
    public long establishedPathAckRanges(AckState state) {
        state.packetSpace.processAckFrame(state.ackFrame);
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
     */
    @State(Scope.Thread)
    public static class AckState {
        /**
         * Number of ack-eliciting packets outstanding before each measured ACK.
         */
        @Param({"64", "4096"})
        public int inFlightPackets;

        /**
         * Number of ranges in the measured ACK frame.
         */
        @Param({"1", "32"})
        public int ackRangeCount;

        private BenchmarkPacketEmitter emitter;
        private QuicTLSEngine tlsEngine;
        private PacketSpaceManager packetSpace;
        private AckFrame ackFrame;
        private BenchmarkPacket[] packets;

        @Setup(Level.Trial)
        public void setUpTrial() {
            emitter = new BenchmarkPacketEmitter();
            tlsEngine = createTlsEngine();
            packets = new BenchmarkPacket[inFlightPackets];
            for (int i = 0; i < packets.length; i++) {
                packets[i] = new BenchmarkPacket(i);
            }
            List<AckRange> ranges = new ArrayList<>(ackRangeCount);
            ranges.add(AckRange.of(0, ackRangeCount == 1 ? inFlightPackets - 1L : 0));
            for (int i = 1; i < ackRangeCount; i++) {
                ranges.add(AckRange.of(0, 0));
            }
            ackFrame = AckFrame.create(inFlightPackets - 1L, 0, ranges);
        }

        @Setup(Level.Invocation)
        public void setUpInvocation() {
            QuicRuntimeConfig runtimeConfig = QuicRuntimeConfig.create(QuicConfig.create());
            QuicRttEstimator rttEstimator = QuicRttEstimator.create(runtimeConfig.recovery());
            QuicCongestionController congestionController =
                    QuicCubicCongestionController.create(runtimeConfig,
                                                         "quic-path-jmh-ack",
                                                         rttEstimator,
                                                         DATAGRAM_SIZE);
            packetSpace = new PacketSpaceManager(PacketNumberSpace.HANDSHAKE,
                                                 emitter,
                                                 TimeSource.source(),
                                                 rttEstimator,
                                                 congestionController,
                                                 tlsEngine,
                                                 () -> "quic-path-jmh-ack",
                                                 new PacketSpaceManager.PathRecoveryState(1),
                                                 runtimeConfig.transportParameters().ackDelayExponent(),
                                                 runtimeConfig.transportParameters().maxAckDelay().toMillis(),
                                                 _ -> {
                                                 });
            for (BenchmarkPacket packet : packets) {
                long packetNumber = packetSpace.allocateNextPN();
                if (packetNumber != packet.packetNumber()) {
                    throw new IllegalStateException("Unexpected QUIC benchmark packet number " + packetNumber);
                }
                packetSpace.packetSent(packet, -1, packetNumber, 0);
            }
        }

        @TearDown(Level.Invocation)
        public void tearDownInvocation() {
            packetSpace.close();
        }

        @TearDown(Level.Trial)
        public void tearDownTrial() {
            emitter.timer.stop();
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
        private final BenchmarkPacket packet = new BenchmarkPacket(0);
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

    private static final class AckAction implements Runnable {
        private long acknowledged;

        @Override
        public void run() {
            acknowledged++;
        }
    }

    private record BenchmarkPacket(long packetNumber) implements QuicPacket {
        private static final QuicConnectionId CONNECTION_ID = PeerConnectionId.create(new byte[0]);
        private static final List<QuicFrame> FRAMES = List.of(PingFrame.create());

        @Override
        public QuicConnectionId destinationId() {
            return CONNECTION_ID;
        }

        @Override
        public PacketNumberSpace numberSpace() {
            return PacketNumberSpace.APPLICATION;
        }

        @Override
        public int size() {
            return DATAGRAM_SIZE;
        }

        @Override
        public HeadersType headersType() {
            return HeadersType.SHORT;
        }

        @Override
        public PacketType packetType() {
            return PacketType.ONERTT;
        }

        @Override
        public List<QuicFrame> frames() {
            return FRAMES;
        }
    }

    private static final class BenchmarkPacketEmitter implements PacketEmitter {
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
