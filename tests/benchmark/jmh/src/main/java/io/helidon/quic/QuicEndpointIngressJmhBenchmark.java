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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import javax.net.ssl.SSLParameters;

import io.helidon.quic.QuicEndpoint.ChannelType;
import io.helidon.quic.packet.QuicPacket;

import org.openjdk.jmh.annotations.AuxCounters;
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
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.ThreadParams;

/**
 * Benchmarks the complete loopback UDP ingress path of a QUIC endpoint.
 */
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class QuicEndpointIngressJmhBenchmark {
    private static final AtomicReference<CompletedOpenLoopWindow> COMPLETED_OPEN_LOOP_WINDOW = new AtomicReference<>();

    /**
     * Measures matched short-header packet throughput.
     *
     * @param state benchmark state
     * @param counters packet counters
     * @return cumulative routed-packet count
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public long matchedBurstThroughput(MatchedState state, IngressCounters counters) {
        return state.harness.matched(state.batchSize, state.routeAccess, counters);
    }

    /**
     * Measures matched short-header burst-completion latency.
     *
     * @param state benchmark state
     * @return cumulative routed-packet count
     */
    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public long matchedLatency(MatchedState state) {
        return state.harness.matched(state.batchSize, state.routeAccess, null);
    }

    /**
     * Measures supported Initial packet inspection throughput.
     *
     * @param state benchmark state
     * @param counters packet counters
     * @return cumulative admitted-packet count
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public long supportedInitialBurstThroughput(InitialState state, IngressCounters counters) {
        return state.harness.supportedInitial(state.batchSize, counters);
    }

    /**
     * Measures supported Initial burst-completion latency.
     *
     * @param state benchmark state
     * @return cumulative admitted-packet count
     */
    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public long supportedInitialLatency(InitialState state) {
        return state.harness.supportedInitial(state.batchSize, null);
    }

    /**
     * Measures invalid Initial rejection throughput.
     *
     * @param state benchmark state
     * @param counters packet counters
     * @return cumulative rejected-packet count
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public long rejectedInitialBurstThroughput(DatagramState state, IngressCounters counters) {
        return state.harness.rejectedInitial(state.batchSize, counters);
    }

    /**
     * Measures invalid Initial burst-completion latency.
     *
     * @param state benchmark state
     * @return cumulative rejected-packet count
     */
    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public long rejectedInitialLatency(DatagramState state) {
        return state.harness.rejectedInitial(state.batchSize, null);
    }

    /**
     * Measures high-watermark pause, drain, and recovery throughput.
     *
     * @param state benchmark state
     * @param counters packet counters
     * @return cumulative routed-packet count
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public long overloadRecoveryBurstThroughput(OverloadState state, IngressCounters counters) {
        return state.harness.overloadRecovery(state.batchSize, counters);
    }

    /**
     * Offers datagrams while endpoint queue draining is paused, then observes a bounded drain.
     *
     * @param state benchmark state
     * @param counters packet counters
     * @return cumulative routed-packet count
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public long overloadObservationWindowThroughput(OverloadObservationState state, IngressCounters counters) {
        return state.harness.overloadObservation(state.offerMillis, counters);
    }

    /**
     * Measures several JMH workers sending matched packets to one shared endpoint.
     *
     * @param state shared endpoint state
     * @param sender worker-local sender state
     * @param counters packet counters
     * @return cumulative routed-packet count for this worker
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public long sharedMatchedBurstThroughput(SharedEndpointState state,
                                             SharedSenderState sender,
                                             IngressCounters counters) {
        return sender.sendAndAwait(state.harness, counters);
    }

    /**
     * Measures several JMH workers against one raw UDP receive lane.
     *
     * @param state raw UDP receiver state
     * @param sender worker-local sender state
     * @param counters packet counters
     * @return cumulative received-datagram count for this worker
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public long rawUdpBurstThroughput(RawUdpState state,
                                      RawUdpSenderState sender,
                                      IngressCounters counters) {
        return sender.sendAndAwait(state.harness, counters);
    }

    /**
     * Measures one sequence-tagged, fixed-rate endpoint offer and drain window.
     *
     * @param state endpoint open-loop state
     * @return accepted packet count
     */
    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public long pacedMatchedWindow(OpenLoopEndpointState state) {
        CompletedOpenLoopWindow completed = state.senderGroup.run(state.window,
                                                                   state.targetPps,
                                                                   state.offerMillis,
                                                                   state.drainMillis);
        if (!COMPLETED_OPEN_LOOP_WINDOW.compareAndSet(null, completed)) {
            throw new IllegalStateException("Open-loop endpoint result was not collected");
        }
        return completed.acceptedPackets;
    }

    /**
     * Measures one sequence-tagged, fixed-rate raw UDP offer and drain window.
     *
     * @param state raw UDP open-loop state
     * @return accepted packet count
     */
    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public long rawUdpPacedWindow(OpenLoopRawState state) {
        CompletedOpenLoopWindow completed = state.senderGroup.run(state.window,
                                                                   state.targetPps,
                                                                   state.offerMillis,
                                                                   state.drainMillis);
        if (!COMPLETED_OPEN_LOOP_WINDOW.compareAndSet(null, completed)) {
            throw new IllegalStateException("Open-loop raw UDP result was not collected");
        }
        return completed.acceptedPackets;
    }

    static CompletedOpenLoopWindow takeCompletedOpenLoopWindow() {
        return COMPLETED_OPEN_LOOP_WINDOW.getAndSet(null);
    }

    static void clearCompletedOpenLoopWindow() {
        COMPLETED_OPEN_LOOP_WINDOW.set(null);
    }

    private static int plannedPackets(int targetPps, int offerMillis) {
        if (targetPps < 1) {
            throw new IllegalArgumentException("targetPps must be greater than zero");
        }
        if (offerMillis < 1) {
            throw new IllegalArgumentException("offerMillis must be greater than zero");
        }
        long planned = Math.multiplyExact((long) targetPps, offerMillis) / TimeUnit.SECONDS.toMillis(1);
        if (planned < 1 || planned > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Open-loop window packet count is outside the supported range");
        }
        return (int) planned;
    }

    /**
     * Parameters for matched and rejected datagrams.
     */
    @State(Scope.Thread)
    public static class DatagramState {
        /**
         * Endpoint channel strategy.
         */
        @Param({"NIO_SELECTOR", "VIRTUAL_THREAD"})
        public String strategy;

        /**
         * Executor used to drain the endpoint ingress queue.
         */
        @Param({"VIRTUAL_PER_TASK", "PLATFORM_SINGLE"})
        public ExecutorMode executorMode;

        /**
         * Number of registered connection-ID routes.
         */
        @Param({"1", "64", "1024"})
        public int routeCount;

        /**
         * UDP payload size.
         */
        @Param({"64", "1200", "1452"})
        public int packetSize;

        /**
         * Number of datagrams in one measured burst.
         */
        @Param({"1", "32", "512"})
        public int batchSize;

        EndpointHarness harness;

        /**
         * Creates the loopback endpoint.
         */
        @Setup(Level.Trial)
        public void setUp(BenchmarkParams benchmarkParams) {
            if (benchmarkParams.getThreads() != 1) {
                throw new IllegalArgumentException("Isolated endpoint benchmarks require one JMH worker");
            }
            harness = new EndpointHarness(strategy, executorMode, routeCount, packetSize, false);
        }

        /**
         * Closes the loopback endpoint.
         */
        @TearDown(Level.Trial)
        public void tearDown() {
            harness.close();
        }
    }

    /**
     * Parameters for matched datagrams.
     */
    @State(Scope.Thread)
    public static class MatchedState extends DatagramState {
        /**
         * Route access pattern.
         */
        @Param({"HOT", "ROTATING"})
        public RouteAccess routeAccess;
    }

    /**
     * Parameters for valid Initial datagrams.
     */
    @State(Scope.Thread)
    public static class InitialState {
        /**
         * Endpoint channel strategy.
         */
        @Param({"NIO_SELECTOR", "VIRTUAL_THREAD"})
        public String strategy;

        /**
         * Executor used to drain the endpoint ingress queue.
         */
        @Param({"VIRTUAL_PER_TASK", "PLATFORM_SINGLE"})
        public ExecutorMode executorMode;

        /**
         * Number of registered connection-ID routes.
         */
        @Param({"1", "64", "1024"})
        public int routeCount;

        /**
         * UDP payload size.
         */
        @Param({"1200", "1452"})
        public int packetSize;

        /**
         * Number of datagrams in one measured burst.
         */
        @Param({"1", "32", "512"})
        public int batchSize;

        private EndpointHarness harness;

        /**
         * Creates the loopback endpoint.
         */
        @Setup(Level.Trial)
        public void setUp(BenchmarkParams benchmarkParams) {
            if (benchmarkParams.getThreads() != 1) {
                throw new IllegalArgumentException("Isolated endpoint benchmarks require one JMH worker");
            }
            harness = new EndpointHarness(strategy, executorMode, routeCount, packetSize, false);
        }

        /**
         * Closes the loopback endpoint.
         */
        @TearDown(Level.Trial)
        public void tearDown() {
            harness.close();
        }
    }

    /**
     * Parameters for bounded overload and recovery.
     */
    @State(Scope.Thread)
    public static class OverloadState {
        /**
         * Endpoint channel strategy.
         */
        @Param({"NIO_SELECTOR", "VIRTUAL_THREAD"})
        public String strategy;

        /**
         * Executor used to drain the endpoint ingress queue.
         */
        @Param({"VIRTUAL_PER_TASK"})
        public ExecutorMode executorMode;

        /**
         * Number of registered connection-ID routes.
         */
        @Param({"64"})
        public int routeCount;

        /**
         * UDP payload size.
         */
        @Param({"1452"})
        public int packetSize;

        /**
         * Number of datagrams in one offered burst.
         */
        @Param({"16"})
        public int batchSize;

        private EndpointHarness harness;

        /**
         * Creates an endpoint with a gated read executor and a low receive watermark.
         */
        @Setup(Level.Trial)
        public void setUp(BenchmarkParams benchmarkParams) {
            if (benchmarkParams.getThreads() != 1) {
                throw new IllegalArgumentException("Overload recovery requires one JMH worker");
            }
            harness = new EndpointHarness(strategy, executorMode, routeCount, packetSize, true);
        }

        /**
         * Closes the loopback endpoint.
         */
        @TearDown(Level.Trial)
        public void tearDown() {
            harness.close();
        }
    }

    /**
     * Parameters for an open-loop offer window followed by a bounded drain observation.
     */
    @State(Scope.Thread)
    public static class OverloadObservationState {
        /**
         * Endpoint channel strategy.
         */
        @Param({"NIO_SELECTOR", "VIRTUAL_THREAD"})
        public String strategy;

        /**
         * Executor used to drain the endpoint ingress queue.
         */
        @Param({"VIRTUAL_PER_TASK"})
        public ExecutorMode executorMode;

        /**
         * Number of registered connection-ID routes.
         */
        @Param({"64"})
        public int routeCount;

        /**
         * UDP payload size.
         */
        @Param({"1452"})
        public int packetSize;

        /**
         * Duration of the no-response offer window.
         */
        @Param({"100"})
        public int offerMillis;

        private EndpointHarness harness;

        /**
         * Creates an endpoint with a gated read executor and a low receive watermark.
         *
         * @param benchmarkParams JMH benchmark parameters
         */
        @Setup(Level.Trial)
        public void setUp(BenchmarkParams benchmarkParams) {
            if (benchmarkParams.getThreads() != 1) {
                throw new IllegalArgumentException("Overload observation requires one JMH worker");
            }
            harness = new EndpointHarness(strategy, executorMode, routeCount, packetSize, true);
        }

        /**
         * Closes the loopback endpoint.
         */
        @TearDown(Level.Trial)
        public void tearDown() {
            harness.close();
        }
    }

    /**
     * Parameters and lifecycle for one endpoint shared by all JMH workers.
     */
    @State(Scope.Benchmark)
    public static class SharedEndpointState {
        /**
         * Endpoint channel strategy.
         */
        @Param({"NIO_SELECTOR", "VIRTUAL_THREAD"})
        public String strategy;

        /**
         * Executor used to drain the endpoint ingress queue.
         */
        @Param({"VIRTUAL_PER_TASK", "PLATFORM_SINGLE"})
        public ExecutorMode executorMode;

        /**
         * Total number of registered connection-ID routes.
         */
        @Param({"64", "1024", "2048"})
        public int routeCount;

        /**
         * UDP payload size.
         */
        @Param({"64", "1200", "1452"})
        public int packetSize;

        private EndpointHarness harness;

        /**
         * Creates one endpoint and partitions its routes across the JMH workers.
         *
         * @param benchmarkParams JMH benchmark parameters
         */
        @Setup(Level.Trial)
        public void setUp(BenchmarkParams benchmarkParams) {
            harness = new EndpointHarness(strategy,
                                          executorMode,
                                          routeCount,
                                          packetSize,
                                          false,
                                          benchmarkParams.getThreads());
        }

        /**
         * Closes the shared endpoint.
         */
        @TearDown(Level.Trial)
        public void tearDown() {
            harness.close();
        }
    }

    /**
     * Worker-local sender for the shared endpoint workload.
     */
    @State(Scope.Thread)
    public static class SharedSenderState {
        /**
         * Number of datagrams in one measured burst.
         */
        @Param({"1", "32", "512"})
        public int batchSize;

        /**
         * Route access pattern within this worker's route partition.
         */
        @Param({"HOT", "ROTATING"})
        public RouteAccess routeAccess;

        private DatagramChannel sender;
        private ByteBuffer[] packets;
        private AtomicLong received;
        private int packetSize;
        private int nextRoute;

        /**
         * Opens one sender and selects one independent receiver partition.
         *
         * @param state shared endpoint state
         * @param threadParams JMH worker parameters
         */
        @Setup(Level.Trial)
        public void setUp(SharedEndpointState state, ThreadParams threadParams) {
            int workerIndex = threadParams.getThreadIndex();
            packets = state.harness.matchedPacketsByReceiver[workerIndex];
            received = state.harness.receivers[workerIndex].received;
            packetSize = state.packetSize;
            DatagramChannel opened = null;
            try {
                opened = DatagramChannel.open();
                opened.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                opened.connect(state.harness.endpoint.localAddress());
                sender = opened;
            } catch (IOException e) {
                UncheckedIOException setupFailure =
                        new UncheckedIOException("Could not create shared QUIC ingress benchmark sender", e);
                if (opened != null) {
                    try {
                        opened.close();
                    } catch (IOException closeFailure) {
                        setupFailure.addSuppressed(closeFailure);
                    }
                }
                throw setupFailure;
            }
        }

        /**
         * Closes this worker's sender.
         */
        @TearDown(Level.Trial)
        public void tearDown() {
            try {
                sender.close();
            } catch (IOException e) {
                throw new UncheckedIOException("Could not close shared QUIC ingress benchmark sender", e);
            }
        }

        private long sendAndAwait(EndpointHarness harness, IngressCounters counters) {
            long before = received.get();
            int packetIndex = routeAccess == RouteAccess.HOT ? 0 : nextRoute;
            try {
                for (int i = 0; i < batchSize; i++) {
                    ByteBuffer packet = packets[packetIndex];
                    packet.rewind();
                    int written = sender.write(packet);
                    if (written != packetSize) {
                        throw new IllegalStateException("UDP send accepted " + written + " of " + packetSize + " bytes");
                    }
                    if (routeAccess == RouteAccess.ROTATING && ++packetIndex == packets.length) {
                        packetIndex = 0;
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Shared QUIC ingress benchmark send failed", e);
            }
            if (routeAccess == RouteAccess.ROTATING) {
                nextRoute = packetIndex;
            }
            long handled = harness.awaitCounter(received, before + batchSize);
            counters.offeredPackets += batchSize;
            counters.acceptedPackets += batchSize;
            counters.acceptedBytes += Math.multiplyExact((long) batchSize, packetSize);
            counters.routedPackets += handled - before;
            counters.routedConnectionIdBytes += Math.multiplyExact(handled - before,
                                                                   harness.connectionIdLength);
            return handled;
        }
    }

    /**
     * Parameters and lifecycle for one raw UDP receiver shared by all JMH workers.
     */
    @State(Scope.Benchmark)
    public static class RawUdpState {
        /**
         * UDP payload size.
         */
        @Param({"64", "1200", "1452"})
        public int packetSize;

        private RawUdpHarness harness;

        /**
         * Creates one raw UDP receiver.
         *
         * @param benchmarkParams JMH benchmark parameters
         */
        @Setup(Level.Trial)
        public void setUp(BenchmarkParams benchmarkParams) {
            harness = new RawUdpHarness(packetSize, benchmarkParams.getThreads());
        }

        /**
         * Closes the raw UDP receiver.
         */
        @TearDown(Level.Trial)
        public void tearDown() {
            harness.close();
        }
    }

    /**
     * Worker-local sender for the raw UDP control.
     */
    @State(Scope.Thread)
    public static class RawUdpSenderState {
        /**
         * Number of datagrams in one measured burst.
         */
        @Param({"1", "32", "512"})
        public int batchSize;

        private DatagramChannel sender;
        private ByteBuffer packet;
        private int packetSize;
        private int workerIndex;

        /**
         * Opens one sender and embeds the JMH worker index in its reusable datagram.
         *
         * @param state raw UDP receiver state
         * @param threadParams JMH worker parameters
         */
        @Setup(Level.Trial)
        public void setUp(RawUdpState state, ThreadParams threadParams) {
            packetSize = state.packetSize;
            workerIndex = threadParams.getThreadIndex();
            packet = ByteBuffer.allocate(packetSize);
            packet.putInt(workerIndex);
            packet.position(packet.limit());
            packet.flip();
            DatagramChannel opened = null;
            try {
                opened = DatagramChannel.open();
                opened.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                opened.connect(state.harness.localAddress);
                sender = opened;
            } catch (IOException e) {
                UncheckedIOException setupFailure =
                        new UncheckedIOException("Could not create raw UDP benchmark sender", e);
                if (opened != null) {
                    try {
                        opened.close();
                    } catch (IOException closeFailure) {
                        setupFailure.addSuppressed(closeFailure);
                    }
                }
                throw setupFailure;
            }
        }

        /**
         * Closes this worker's sender.
         */
        @TearDown(Level.Trial)
        public void tearDown() {
            try {
                sender.close();
            } catch (IOException e) {
                throw new UncheckedIOException("Could not close raw UDP benchmark sender", e);
            }
        }

        private long sendAndAwait(RawUdpHarness harness, IngressCounters counters) {
            long before = harness.received.get(workerIndex);
            try {
                for (int i = 0; i < batchSize; i++) {
                    packet.rewind();
                    int written = sender.write(packet);
                    if (written != packetSize) {
                        throw new IllegalStateException("UDP send accepted " + written + " of " + packetSize + " bytes");
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Raw UDP benchmark send failed", e);
            }
            long expected = before + batchSize;
            long deadline = System.nanoTime() + EndpointHarness.COMPLETION_TIMEOUT_NANOS;
            int spins = EndpointHarness.SPIN_COUNT;
            long handled;
            while ((handled = harness.received.get(workerIndex)) < expected) {
                Throwable runtimeFailure = harness.failure.get();
                if (runtimeFailure != null) {
                    throw new IllegalStateException("Raw UDP benchmark receiver failed", runtimeFailure);
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new IllegalStateException("Timed out waiting for raw UDP receive: expected="
                                                            + expected + ", observed=" + handled);
                }
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for raw UDP receive");
                }
                if (spins-- > 0) {
                    Thread.onSpinWait();
                } else {
                    LockSupport.parkNanos(Math.min(EndpointHarness.PARK_NANOS, remaining));
                }
            }
            if (handled != expected) {
                throw new IllegalStateException("Raw UDP receive count exceeded its target: expected="
                                                        + expected + ", observed=" + handled);
            }
            counters.offeredPackets += batchSize;
            counters.acceptedPackets += batchSize;
            counters.acceptedBytes += Math.multiplyExact((long) batchSize, packetSize);
            counters.rawReceivedPackets += handled - before;
            return handled;
        }
    }

    /**
     * Parameters and lifecycle for a paced endpoint ingress window.
     */
    @State(Scope.Thread)
    public static class OpenLoopEndpointState {
        /**
         * Endpoint channel strategy.
         */
        @Param({"NIO_SELECTOR", "VIRTUAL_THREAD"})
        public String strategy;

        /**
         * Executor used to drain the endpoint ingress queue.
         */
        @Param({"VIRTUAL_PER_TASK"})
        public ExecutorMode executorMode;

        /**
         * Number of registered connection-ID routes.
         */
        @Param({"2048"})
        public int routeCount;

        /**
         * UDP payload size.
         */
        @Param({"64", "1452"})
        public int packetSize;

        /**
         * Aggregate scheduled packet rate across all senders during the offer window.
         */
        @Param({"50000", "75000", "100000", "125000", "150000"})
        public int targetPps;

        /**
         * Number of persistent paced sender threads and sockets.
         */
        @Param({"1"})
        public int senderCount;

        /**
         * Number of packet buffers rotated by each sender; the endpoint uses one registered route per buffer.
         */
        @Param({"1"})
        public int senderWorkingSet;

        /**
         * Fixed offer-window duration.
         */
        @Param({"1000"})
        public int offerMillis;

        /**
         * Fixed post-offer drain duration.
         */
        @Param({"250"})
        public int drainMillis;

        /**
         * End-to-end latency threshold counted as late.
         */
        @Param({"1000"})
        public int lateMicros;

        private EndpointHarness harness;
        private OpenLoopRecorder recorder;
        private OpenLoopSenderGroup senderGroup;
        private OpenLoopWindow window;

        /**
         * Creates one endpoint and reusable paced senders.
         *
         * @param benchmarkParams JMH benchmark parameters
         */
        @Setup(Level.Trial)
        public void setUp(BenchmarkParams benchmarkParams) {
            if (benchmarkParams.getThreads() != 1) {
                throw new IllegalArgumentException("Open-loop endpoint benchmark requires one JMH worker");
            }
            if (senderCount < 1) {
                throw new IllegalArgumentException("senderCount must be greater than zero");
            }
            if (senderWorkingSet < 1) {
                throw new IllegalArgumentException("senderWorkingSet must be greater than zero");
            }
            int planned = plannedPackets(targetPps, offerMillis);
            if (planned < senderCount) {
                throw new IllegalArgumentException("Planned packet count must be at least senderCount");
            }
            int senderPacketCount = Math.multiplyExact(senderCount, senderWorkingSet);
            if (senderPacketCount > routeCount) {
                throw new IllegalArgumentException("senderCount times senderWorkingSet exceeds routeCount");
            }
            recorder = new OpenLoopRecorder(planned, lateMicros);
            harness = new EndpointHarness(strategy,
                                          executorMode,
                                          routeCount,
                                          packetSize,
                                          false,
                                          1,
                                          recorder);
            try {
                ByteBuffer[] senderPackets = Arrays.copyOf(harness.matchedPackets, senderPacketCount);
                senderGroup = OpenLoopSenderGroup.create(harness.endpoint.localAddress(),
                                                        senderPackets,
                                                        senderCount,
                                                        packetSize,
                                                        1 + harness.connectionIdLength,
                                                        harness.connectionIdLength,
                                                        harness.receiverSocketBufferBytes,
                                                        recorder);
            } catch (RuntimeException | Error failure) {
                try {
                    harness.close();
                } catch (RuntimeException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }

        /**
         * Resets packet accounting outside the measured invocation.
         */
        @Setup(Level.Invocation)
        public void prepareWindow() {
            window = recorder.prepare(plannedPackets(targetPps, offerMillis));
        }

        /**
         * Closes the endpoint and its executors.
         */
        @TearDown(Level.Trial)
        public void tearDown() {
            RuntimeException failure = null;
            try {
                senderGroup.close();
            } catch (RuntimeException e) {
                failure = e;
            }
            try {
                harness.close();
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
            try {
                recorder.checkInactiveArrivals();
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    /**
     * Parameters and lifecycle for a paced raw UDP control window.
     */
    @State(Scope.Thread)
    public static class OpenLoopRawState {
        /**
         * UDP payload size.
         */
        @Param({"64", "1452"})
        public int packetSize;

        /**
         * Aggregate scheduled packet rate across all senders during the offer window.
         */
        @Param({"50000", "75000", "100000", "125000", "150000"})
        public int targetPps;

        /**
         * Number of persistent paced sender threads and sockets.
         */
        @Param({"1"})
        public int senderCount;

        /**
         * Number of packet buffers rotated by each sender.
         */
        @Param({"1"})
        public int senderWorkingSet;

        /**
         * Fixed offer-window duration.
         */
        @Param({"1000"})
        public int offerMillis;

        /**
         * Fixed post-offer drain duration.
         */
        @Param({"250"})
        public int drainMillis;

        /**
         * End-to-end latency threshold counted as late.
         */
        @Param({"1000"})
        public int lateMicros;

        private RawUdpHarness harness;
        private OpenLoopRecorder recorder;
        private OpenLoopSenderGroup senderGroup;
        private OpenLoopWindow window;

        /**
         * Creates one raw UDP receiver and reusable paced senders.
         *
         * @param benchmarkParams JMH benchmark parameters
         */
        @Setup(Level.Trial)
        public void setUp(BenchmarkParams benchmarkParams) {
            if (benchmarkParams.getThreads() != 1) {
                throw new IllegalArgumentException("Open-loop raw UDP benchmark requires one JMH worker");
            }
            if (senderCount < 1) {
                throw new IllegalArgumentException("senderCount must be greater than zero");
            }
            if (senderWorkingSet < 1) {
                throw new IllegalArgumentException("senderWorkingSet must be greater than zero");
            }
            int planned = plannedPackets(targetPps, offerMillis);
            if (planned < senderCount) {
                throw new IllegalArgumentException("Planned packet count must be at least senderCount");
            }
            recorder = new OpenLoopRecorder(planned, lateMicros);
            recorder.configureMetadataOffset(0);
            harness = new RawUdpHarness(packetSize, 1, recorder);
            try {
                ByteBuffer[] packets = new ByteBuffer[Math.multiplyExact(senderCount, senderWorkingSet)];
                for (int i = 0; i < packets.length; i++) {
                    ByteBuffer packet = ByteBuffer.allocate(packetSize);
                    packet.position(packet.limit());
                    packets[i] = packet.flip();
                }
                senderGroup = OpenLoopSenderGroup.create(harness.localAddress,
                                                        packets,
                                                        senderCount,
                                                        packetSize,
                                                        0,
                                                        0,
                                                        harness.receiverSocketBufferBytes,
                                                        recorder);
            } catch (RuntimeException | Error setupFailure) {
                try {
                    harness.close();
                } catch (RuntimeException closeFailure) {
                    setupFailure.addSuppressed(closeFailure);
                }
                throw setupFailure;
            }
        }

        /**
         * Resets packet accounting outside the measured invocation.
         */
        @Setup(Level.Invocation)
        public void prepareWindow() {
            window = recorder.prepare(plannedPackets(targetPps, offerMillis));
        }

        /**
         * Closes the sender and raw UDP receiver.
         */
        @TearDown(Level.Trial)
        public void tearDown() {
            RuntimeException failure = null;
            try {
                senderGroup.close();
            } catch (RuntimeException e) {
                failure = e;
            }
            try {
                harness.close();
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
            try {
                recorder.checkInactiveArrivals();
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    /**
     * Packet-rate counters. JMH normalizes these counters by measurement time.
     */
    @AuxCounters(AuxCounters.Type.OPERATIONS)
    @State(Scope.Thread)
    public static class IngressCounters {
        /**
         * Datagram writes offered to the sending UDP socket.
         */
        public long offeredPackets;
        /**
         * Datagrams accepted by the sending UDP socket.
         */
        public long acceptedPackets;
        /**
         * UDP payload bytes accepted by the sending UDP socket.
         */
        public long acceptedBytes;
        /**
         * Matched datagrams delivered to a registered receiver.
         */
        public long routedPackets;
        /**
         * Datagrams received by the raw UDP control.
         */
        public long rawReceivedPackets;
        /**
         * Connection-ID bytes inspected for datagrams delivered to a registered receiver.
         */
        public long routedConnectionIdBytes;
        /**
         * Accepted datagrams not routed before the bounded quiet-drain observation ended.
         */
        public long unobservedAtDrainEnd;
        /**
         * Supported Initial datagrams admitted by stateless inspection.
         */
        public long admittedPackets;
        /**
         * Invalid Initial datagrams rejected by stateless inspection.
         */
        public long rejectedPackets;
        /**
         * Completed endpoint pause/drain/resume cycles.
         */
        public long recoveryCycles;
    }

    /**
     * Executor used to drain copied ingress datagrams.
     */
    public enum ExecutorMode {
        /**
         * Product-representative virtual thread per drain task.
         */
        VIRTUAL_PER_TASK,
        /**
         * Diagnostic single platform thread.
         */
        PLATFORM_SINGLE
    }

    /**
     * Matched-route access pattern.
     */
    public enum RouteAccess {
        /**
         * Route every packet to the same connection ID.
         */
        HOT,
        /**
         * Rotate continuously through every registered connection ID.
         */
        ROTATING
    }

    static final class CompletedOpenLoopWindow {
        final int plannedPackets;
        final int packetSize;
        final int connectionIdLength;
        final long offerNanos;
        final long drainNanos;
        final long lateNanos;
        final long attemptedPackets;
        final long schedulerSkippedPackets;
        final long socketRejectedPackets;
        final long acceptedPackets;
        final long maxSchedulerStallNanos;
        final long maxResidualScheduleLagNanos;
        final int senderCount;
        final long minAcceptedBySender;
        final long maxAcceptedBySender;
        final double minSchedulerSkipRatioBySender;
        final double maxSchedulerSkipRatioBySender;
        final long senderCompletionSkewNanos;
        final long uniqueReceivedPackets;
        final long duplicatePackets;
        final long crossWindowPackets;
        final long outsideWindowPackets;
        final long drainArrivals;
        final int receiverSocketBufferBytes;
        final int minSenderSocketBufferBytes;
        final int maxSenderSocketBufferBytes;
        final long[] acceptedBits;
        final AtomicLongArray receivedBits;
        final long[] latencyNanos;

        private CompletedOpenLoopWindow(OpenLoopWindow window,
                                        int packetSize,
                                        int connectionIdLength,
                                        long offerNanos,
                                        long drainNanos,
                                        long lateNanos,
                                        SenderSummary senderSummary,
                                        int receiverSocketBufferBytes,
                                        int minSenderSocketBufferBytes,
                                        int maxSenderSocketBufferBytes,
                                        long outsideWindowPackets) {
            this.plannedPackets = window.plannedPackets;
            this.packetSize = packetSize;
            this.connectionIdLength = connectionIdLength;
            this.offerNanos = offerNanos;
            this.drainNanos = drainNanos;
            this.lateNanos = lateNanos;
            this.attemptedPackets = senderSummary.attemptedPackets;
            this.schedulerSkippedPackets = senderSummary.schedulerSkippedPackets;
            this.socketRejectedPackets = senderSummary.socketRejectedPackets;
            this.acceptedPackets = senderSummary.acceptedPackets;
            this.maxSchedulerStallNanos = senderSummary.maxSchedulerStallNanos;
            this.maxResidualScheduleLagNanos = senderSummary.maxResidualScheduleLagNanos;
            this.senderCount = senderSummary.senderCount;
            this.minAcceptedBySender = senderSummary.minAcceptedBySender;
            this.maxAcceptedBySender = senderSummary.maxAcceptedBySender;
            this.minSchedulerSkipRatioBySender = senderSummary.minSchedulerSkipRatioBySender;
            this.maxSchedulerSkipRatioBySender = senderSummary.maxSchedulerSkipRatioBySender;
            this.senderCompletionSkewNanos = senderSummary.senderCompletionSkewNanos;
            this.uniqueReceivedPackets = window.uniqueReceived.get();
            this.duplicatePackets = window.duplicatePackets.get();
            this.crossWindowPackets = window.crossWindowPackets.get();
            this.outsideWindowPackets = outsideWindowPackets;
            this.drainArrivals = window.drainArrivals.get();
            this.receiverSocketBufferBytes = receiverSocketBufferBytes;
            this.minSenderSocketBufferBytes = minSenderSocketBufferBytes;
            this.maxSenderSocketBufferBytes = maxSenderSocketBufferBytes;
            this.acceptedBits = window.acceptedBits;
            this.receivedBits = window.receivedBits;
            this.latencyNanos = window.latencyNanos;
        }
    }

    private static final class OpenLoopSenderGroup implements AutoCloseable {
        private static final int MAGIC = 0x51494333;
        private static final int WINDOW_ID_OFFSET = Integer.BYTES;
        private static final int SEQUENCE_OFFSET = WINDOW_ID_OFFSET + Long.BYTES;
        private static final int SENT_NANOS_OFFSET = SEQUENCE_OFFSET + Integer.BYTES;
        private static final int METADATA_SIZE = SENT_NANOS_OFFSET + Long.BYTES;
        private static final long START_LEAD_NANOS = TimeUnit.MILLISECONDS.toNanos(1);
        private static final long COORDINATION_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5);
        private static final long PARK_GUARD_NANOS = TimeUnit.MICROSECONDS.toNanos(20);
        private static final long PARK_THRESHOLD_NANOS = TimeUnit.MICROSECONDS.toNanos(50);

        private final SenderWorker[] workers;
        private final int packetSize;
        private final int connectionIdLength;
        private final int receiverSocketBufferBytes;
        private final int minSenderSocketBufferBytes;
        private final int maxSenderSocketBufferBytes;
        private final OpenLoopRecorder recorder;
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private boolean closed;

        private OpenLoopSenderGroup(DatagramChannel[] channels,
                                    ByteBuffer[][] packets,
                                    int packetSize,
                                    int metadataOffset,
                                    int connectionIdLength,
                                    int receiverSocketBufferBytes,
                                    int minSenderSocketBufferBytes,
                                    int maxSenderSocketBufferBytes,
                                    OpenLoopRecorder recorder) {
            workers = new SenderWorker[channels.length];
            this.packetSize = packetSize;
            this.connectionIdLength = connectionIdLength;
            this.receiverSocketBufferBytes = receiverSocketBufferBytes;
            this.minSenderSocketBufferBytes = minSenderSocketBufferBytes;
            this.maxSenderSocketBufferBytes = maxSenderSocketBufferBytes;
            this.recorder = recorder;
            int started = 0;
            try {
                for (int i = 0; i < workers.length; i++) {
                    workers[i] = new SenderWorker(i,
                                                  workers.length,
                                                  channels[i],
                                                  packets[i],
                                                  packetSize,
                                                  metadataOffset,
                                                  recorder,
                                                  failure);
                    workers[i].thread.start();
                    started++;
                }
            } catch (RuntimeException | Error startupFailure) {
                for (int i = 0; i < started; i++) {
                    workers[i].closing = true;
                    LockSupport.unpark(workers[i].thread);
                }
                for (DatagramChannel channel : channels) {
                    try {
                        channel.close();
                    } catch (IOException e) {
                        startupFailure.addSuppressed(e);
                    }
                }
                long deadline = System.nanoTime() + COORDINATION_TIMEOUT_NANOS;
                for (int i = 0; i < started; i++) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        startupFailure.addSuppressed(
                                new IllegalStateException("Timed out stopping partially started paced UDP senders"));
                        break;
                    }
                    try {
                        workers[i].thread.join(TimeUnit.NANOSECONDS.toMillis(remaining) + 1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        startupFailure.addSuppressed(
                                new IllegalStateException("Interrupted while stopping partially started paced UDP senders",
                                                          e));
                        break;
                    }
                    if (workers[i].thread.isAlive()) {
                        startupFailure.addSuppressed(
                                new IllegalStateException("Timed out stopping a partially started paced UDP sender"));
                    }
                }
                throw startupFailure;
            }
        }

        private static OpenLoopSenderGroup create(SocketAddress destination,
                                                  ByteBuffer[] packets,
                                                  int senderCount,
                                                  int packetSize,
                                                  int metadataOffset,
                                                  int connectionIdLength,
                                                  int receiverSocketBufferBytes,
                                                  OpenLoopRecorder recorder) {
            if (senderCount < 1) {
                throw new IllegalArgumentException("senderCount must be greater than zero");
            }
            if (packets.length < senderCount) {
                throw new IllegalArgumentException("packet route count must be at least senderCount");
            }
            if (metadataOffset < 0 || packetSize - metadataOffset < METADATA_SIZE) {
                throw new IllegalArgumentException("packetSize is too small for open-loop metadata");
            }
            DatagramChannel[] channels = new DatagramChannel[senderCount];
            try {
                for (int i = 0; i < channels.length; i++) {
                    DatagramChannel channel = DatagramChannel.open();
                    channel.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                    channel.connect(destination);
                    channels[i] = channel;
                }
                int minSenderSocketBufferBytes = Integer.MAX_VALUE;
                int maxSenderSocketBufferBytes = 0;
                for (DatagramChannel channel : channels) {
                    int senderSocketBufferBytes = channel.getOption(StandardSocketOptions.SO_SNDBUF);
                    minSenderSocketBufferBytes = Math.min(minSenderSocketBufferBytes, senderSocketBufferBytes);
                    maxSenderSocketBufferBytes = Math.max(maxSenderSocketBufferBytes, senderSocketBufferBytes);
                }
                int[] packetCounts = new int[senderCount];
                for (int i = 0; i < packets.length; i++) {
                    packetCounts[i % senderCount]++;
                }
                ByteBuffer[][] packetsBySender = new ByteBuffer[senderCount][];
                for (int i = 0; i < packetsBySender.length; i++) {
                    packetsBySender[i] = new ByteBuffer[packetCounts[i]];
                }
                Arrays.fill(packetCounts, 0);
                for (int i = 0; i < packets.length; i++) {
                    int senderIndex = i % senderCount;
                    packetsBySender[senderIndex][packetCounts[senderIndex]++] = packets[i].duplicate();
                }
                System.out.printf("QUIC open-loop senders=%d receiverRcvBuf=%d "
                                          + "senderSndBufMin=%d senderSndBufMax=%d%n",
                                  senderCount,
                                  receiverSocketBufferBytes,
                                  minSenderSocketBufferBytes,
                                  maxSenderSocketBufferBytes);
                return new OpenLoopSenderGroup(channels,
                                               packetsBySender,
                                               packetSize,
                                               metadataOffset,
                                               connectionIdLength,
                                               receiverSocketBufferBytes,
                                               minSenderSocketBufferBytes,
                                               maxSenderSocketBufferBytes,
                                               recorder);
            } catch (IOException e) {
                UncheckedIOException setupFailure =
                        new UncheckedIOException("Could not create paced UDP senders", e);
                for (DatagramChannel channel : channels) {
                    if (channel != null) {
                        try {
                            channel.close();
                        } catch (IOException closeFailure) {
                            setupFailure.addSuppressed(closeFailure);
                        }
                    }
                }
                throw setupFailure;
            } catch (RuntimeException | Error setupFailure) {
                for (DatagramChannel channel : channels) {
                    if (channel != null) {
                        try {
                            channel.close();
                        } catch (IOException closeFailure) {
                            setupFailure.addSuppressed(closeFailure);
                        }
                    }
                }
                throw setupFailure;
            }
        }

        private CompletedOpenLoopWindow run(OpenLoopWindow window,
                                            int targetPps,
                                            int offerMillis,
                                            int drainMillis) {
            int planned = plannedPackets(targetPps, offerMillis);
            if (window.plannedPackets != planned) {
                throw new IllegalStateException("Prepared open-loop window has the wrong packet count");
            }
            if (planned < workers.length) {
                throw new IllegalArgumentException("Planned packet count must be at least senderCount");
            }
            if (drainMillis < 1) {
                throw new IllegalArgumentException("drainMillis must be greater than zero");
            }
            checkFailure();
            long offerNanos = TimeUnit.MILLISECONDS.toNanos(offerMillis);
            long drainNanos = TimeUnit.MILLISECONDS.toNanos(drainMillis);
            SenderCommand command = new SenderCommand(window,
                                                      planned,
                                                      targetPps,
                                                      workers.length);
            for (SenderWorker worker : workers) {
                if (worker.command != null) {
                    throw new IllegalStateException("Previous open-loop sender command is still active");
                }
                worker.command = command;
                LockSupport.unpark(worker.thread);
            }
            await(command.armed,
                  System.nanoTime() + COORDINATION_TIMEOUT_NANOS,
                  "arming paced UDP senders");
            checkFailure();
            long offerStarted = Math.addExact(System.nanoTime(), START_LEAD_NANOS);
            long offerDeadline = Math.addExact(offerStarted, offerNanos);
            window.offerDeadlineNanos = offerDeadline;
            command.offerDeadlineNanos = offerDeadline;
            command.offerStartedNanos = offerStarted;
            for (SenderWorker worker : workers) {
                LockSupport.unpark(worker.thread);
            }
            await(command.completed,
                  Math.addExact(offerDeadline, COORDINATION_TIMEOUT_NANOS),
                  "completing paced UDP sends");
            Throwable commandFailure = command.failure.get();
            if (commandFailure != null) {
                throw new IllegalStateException("Paced UDP sender failed", commandFailure);
            }

            long attempted = 0;
            long schedulerSkipped = 0;
            long socketRejected = 0;
            long accepted = 0;
            long maxSchedulerStallNanos = 0;
            long maxResidualScheduleLagNanos = 0;
            long minAcceptedBySender = Long.MAX_VALUE;
            long maxAcceptedBySender = 0;
            double minSchedulerSkipRatioBySender = Double.POSITIVE_INFINITY;
            double maxSchedulerSkipRatioBySender = 0;
            long firstSenderCompletionNanos = Long.MAX_VALUE;
            long lastSenderCompletionNanos = 0;
            for (SenderResult senderResult : command.results) {
                if (senderResult == null) {
                    throw new IllegalStateException("Paced UDP sender did not publish its result");
                }
                if (senderResult.attemptedPackets + senderResult.schedulerSkippedPackets
                        != senderResult.plannedPackets) {
                    throw new IllegalStateException("Sender pacing accounting does not equal its planned packet count");
                }
                if (senderResult.acceptedPackets + senderResult.socketRejectedPackets
                        != senderResult.attemptedPackets) {
                    throw new IllegalStateException("Sender socket accounting does not equal its attempted packet count");
                }
                long senderAcceptedBits = 0;
                for (int word = 0; word < window.acceptedBits.length; word++) {
                    long senderBits = senderResult.acceptedBits[word];
                    if ((window.acceptedBits[word] & senderBits) != 0) {
                        throw new IllegalStateException("Paced UDP senders accepted the same sequence");
                    }
                    window.acceptedBits[word] |= senderBits;
                    senderAcceptedBits += Long.bitCount(senderBits);
                }
                if (senderAcceptedBits != senderResult.acceptedPackets) {
                    throw new IllegalStateException("Sender accepted bitmap does not equal its accepted packet count");
                }
                attempted += senderResult.attemptedPackets;
                schedulerSkipped += senderResult.schedulerSkippedPackets;
                socketRejected += senderResult.socketRejectedPackets;
                accepted += senderResult.acceptedPackets;
                maxSchedulerStallNanos = Math.max(maxSchedulerStallNanos,
                                                  senderResult.maxSchedulerStallNanos);
                maxResidualScheduleLagNanos = Math.max(maxResidualScheduleLagNanos,
                                                       senderResult.maxResidualScheduleLagNanos);
                minAcceptedBySender = Math.min(minAcceptedBySender, senderResult.acceptedPackets);
                maxAcceptedBySender = Math.max(maxAcceptedBySender, senderResult.acceptedPackets);
                double schedulerSkipRatio = (double) senderResult.schedulerSkippedPackets
                        / senderResult.plannedPackets;
                minSchedulerSkipRatioBySender = Math.min(minSchedulerSkipRatioBySender, schedulerSkipRatio);
                maxSchedulerSkipRatioBySender = Math.max(maxSchedulerSkipRatioBySender, schedulerSkipRatio);
                firstSenderCompletionNanos = Math.min(firstSenderCompletionNanos,
                                                      senderResult.completedNanos);
                lastSenderCompletionNanos = Math.max(lastSenderCompletionNanos,
                                                     senderResult.completedNanos);
            }

            long drainDeadline = Math.addExact(offerDeadline, drainNanos);
            long remaining;
            while ((remaining = drainDeadline - System.nanoTime()) > 0) {
                recorder.checkFailure();
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while draining open-loop datagrams");
                }
                LockSupport.parkNanos(Math.min(TimeUnit.MICROSECONDS.toNanos(50),
                                               remaining));
            }
            SenderSummary senderSummary = new SenderSummary(workers.length,
                                                            attempted,
                                                            schedulerSkipped,
                                                            socketRejected,
                                                            accepted,
                                                            maxSchedulerStallNanos,
                                                            maxResidualScheduleLagNanos,
                                                            minAcceptedBySender,
                                                            maxAcceptedBySender,
                                                            minSchedulerSkipRatioBySender,
                                                            maxSchedulerSkipRatioBySender,
                                                            lastSenderCompletionNanos - firstSenderCompletionNanos);
            return recorder.complete(window,
                                     packetSize,
                                     connectionIdLength,
                                     offerNanos,
                                     drainNanos,
                                     senderSummary,
                                     receiverSocketBufferBytes,
                                     minSenderSocketBufferBytes,
                                     maxSenderSocketBufferBytes);
        }

        private void await(CountDownLatch latch, long deadline, String activity) {
            long remaining;
            try {
                while (latch.getCount() != 0
                        && (remaining = deadline - System.nanoTime()) > 0) {
                    if (latch.await(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(10)),
                                    TimeUnit.NANOSECONDS)) {
                        break;
                    }
                    checkFailure();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while " + activity, e);
            }
            checkFailure();
            if (latch.getCount() != 0) {
                throw new IllegalStateException("Timed out while " + activity);
            }
        }

        private void checkFailure() {
            Throwable current = failure.get();
            if (current != null) {
                throw new IllegalStateException("Paced UDP sender thread failed", current);
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            RuntimeException closeFailure = null;
            for (SenderWorker worker : workers) {
                worker.closing = true;
                try {
                    worker.channel.close();
                } catch (IOException e) {
                    RuntimeException channelFailure =
                            new UncheckedIOException("Could not close paced UDP sender", e);
                    if (closeFailure == null) {
                        closeFailure = channelFailure;
                    } else {
                        closeFailure.addSuppressed(channelFailure);
                    }
                }
                LockSupport.unpark(worker.thread);
            }
            long deadline = System.nanoTime() + COORDINATION_TIMEOUT_NANOS;
            for (SenderWorker worker : workers) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    RuntimeException timeout =
                            new IllegalStateException("Timed out stopping paced UDP sender threads");
                    if (closeFailure == null) {
                        closeFailure = timeout;
                    } else {
                        closeFailure.addSuppressed(timeout);
                    }
                    break;
                }
                try {
                    worker.thread.join(TimeUnit.NANOSECONDS.toMillis(remaining) + 1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    RuntimeException interrupted =
                            new IllegalStateException("Interrupted while stopping paced UDP sender threads", e);
                    if (closeFailure == null) {
                        closeFailure = interrupted;
                    } else {
                        closeFailure.addSuppressed(interrupted);
                    }
                    break;
                }
                if (worker.thread.isAlive()) {
                    RuntimeException timeout =
                            new IllegalStateException("Timed out stopping paced UDP sender thread");
                    if (closeFailure == null) {
                        closeFailure = timeout;
                    } else {
                        closeFailure.addSuppressed(timeout);
                    }
                }
            }
            Throwable senderFailure = failure.get();
            if (senderFailure != null) {
                RuntimeException workerFailure =
                        new IllegalStateException("Paced UDP sender thread failed", senderFailure);
                if (closeFailure == null) {
                    closeFailure = workerFailure;
                } else {
                    closeFailure.addSuppressed(workerFailure);
                }
            }
            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }

    private static final class SenderWorker {
        private final int index;
        private final int senderCount;
        private final DatagramChannel channel;
        private final ByteBuffer[] packets;
        private final int packetSize;
        private final int metadataOffset;
        private final OpenLoopRecorder recorder;
        private final AtomicReference<Throwable> groupFailure;
        private final Thread thread;
        private final long[] acceptedBits;
        private volatile SenderCommand command;
        private volatile boolean closing;
        private int nextPacket;

        private SenderWorker(int index,
                             int senderCount,
                             DatagramChannel channel,
                             ByteBuffer[] packets,
                             int packetSize,
                             int metadataOffset,
                             OpenLoopRecorder recorder,
                             AtomicReference<Throwable> groupFailure) {
            this.index = index;
            this.senderCount = senderCount;
            this.channel = channel;
            this.packets = packets;
            this.packetSize = packetSize;
            this.metadataOffset = metadataOffset;
            this.recorder = recorder;
            this.groupFailure = groupFailure;
            acceptedBits = new long[recorder.acceptedBits.length];
            thread = Thread.ofPlatform()
                    .name("quic-open-loop-sender-" + index)
                    .daemon()
                    .unstarted(this::run);
        }

        private void run() {
            while (!closing) {
                SenderCommand current = command;
                if (current == null) {
                    LockSupport.park();
                    continue;
                }
                SenderResult senderResult = null;
                try {
                    Arrays.fill(acceptedBits, 0);
                    long ownedPackets = current.plannedPackets <= index
                            ? 0
                            : (current.plannedPackets - 1L - index) / senderCount + 1;
                    current.armed.countDown();
                    long offerStarted;
                    while ((offerStarted = current.offerStartedNanos) == 0) {
                        if (closing) {
                            throw new IllegalStateException("Paced UDP sender closed while arming");
                        }
                        LockSupport.parkNanos(TimeUnit.MICROSECONDS.toNanos(50));
                    }
                    long attempted = 0;
                    long socketRejected = 0;
                    long accepted = 0;
                    long maxSchedulerStallNanos = 0;
                    long maxResidualScheduleLagNanos = 0;
                    long sequence = index;
                    while (sequence < current.plannedPackets) {
                        long scheduled = offerStarted
                                + Math.multiplyExact(sequence, TimeUnit.SECONDS.toNanos(1))
                                        / current.targetPps;
                        long now;
                        while ((now = System.nanoTime()) < scheduled) {
                            long remaining = scheduled - now;
                            if (Thread.interrupted()) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException("Interrupted while pacing open-loop datagrams");
                            }
                            if (remaining > OpenLoopSenderGroup.PARK_THRESHOLD_NANOS) {
                                LockSupport.parkNanos(remaining - OpenLoopSenderGroup.PARK_GUARD_NANOS);
                            } else {
                                Thread.onSpinWait();
                            }
                        }
                        if (now >= current.offerDeadlineNanos) {
                            break;
                        }
                        long schedulerStall = now - scheduled;
                        maxSchedulerStallNanos = Math.max(maxSchedulerStallNanos, schedulerStall);
                        long dueSequence = Math.multiplyExact(now - offerStarted, current.targetPps)
                                / TimeUnit.SECONDS.toNanos(1);
                        if (dueSequence > sequence) {
                            long lastPlannedSequence = current.plannedPackets - 1L;
                            long latestDueSequence = Math.min(dueSequence, lastPlannedSequence);
                            long latestDue = index + (latestDueSequence - index) / senderCount * senderCount;
                            if (latestDue > sequence) {
                                sequence = latestDue;
                                scheduled = offerStarted
                                        + Math.multiplyExact(sequence, TimeUnit.SECONDS.toNanos(1))
                                                / current.targetPps;
                            }
                        }
                        long residualScheduleLag = now - scheduled;
                        maxResidualScheduleLagNanos = Math.max(maxResidualScheduleLagNanos,
                                                              residualScheduleLag);

                        ByteBuffer packet = packets[nextPacket];
                        if (++nextPacket == packets.length) {
                            nextPacket = 0;
                        }
                        int sequenceNumber = Math.toIntExact(sequence);
                        packet.putInt(metadataOffset, OpenLoopSenderGroup.MAGIC);
                        packet.putLong(metadataOffset + OpenLoopSenderGroup.WINDOW_ID_OFFSET,
                                       current.window.id);
                        packet.putInt(metadataOffset + OpenLoopSenderGroup.SEQUENCE_OFFSET,
                                      sequenceNumber);
                        packet.putLong(metadataOffset + OpenLoopSenderGroup.SENT_NANOS_OFFSET,
                                       System.nanoTime());
                        packet.rewind();
                        attempted++;
                        int written = channel.write(packet);
                        if (written == packetSize) {
                            int word = sequenceNumber >>> 6;
                            acceptedBits[word] |= 1L << (sequenceNumber & (Long.SIZE - 1));
                            accepted++;
                        } else if (written == 0) {
                            socketRejected++;
                        } else {
                            throw new IllegalStateException("UDP send accepted " + written + " of "
                                                                    + packetSize + " bytes");
                        }
                        sequence += senderCount;
                        recorder.checkFailure();
                    }
                    senderResult = new SenderResult(ownedPackets,
                                                    attempted,
                                                    ownedPackets - attempted,
                                                    socketRejected,
                                                    accepted,
                                                    maxSchedulerStallNanos,
                                                    maxResidualScheduleLagNanos,
                                                    System.nanoTime(),
                                                    acceptedBits);
                } catch (IOException e) {
                    UncheckedIOException sendFailure =
                            new UncheckedIOException("Open-loop UDP send failed", e);
                    current.failure.compareAndSet(null, sendFailure);
                    groupFailure.compareAndSet(null, sendFailure);
                } catch (RuntimeException | Error sendFailure) {
                    current.failure.compareAndSet(null, sendFailure);
                    groupFailure.compareAndSet(null, sendFailure);
                } finally {
                    current.results[index] = senderResult;
                    command = null;
                    current.completed.countDown();
                }
                if (groupFailure.get() != null) {
                    return;
                }
            }
        }
    }

    private static final class SenderCommand {
        private final OpenLoopWindow window;
        private final int plannedPackets;
        private final int targetPps;
        private final CountDownLatch armed;
        private final CountDownLatch completed;
        private final SenderResult[] results;
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private volatile long offerStartedNanos;
        private long offerDeadlineNanos;

        private SenderCommand(OpenLoopWindow window,
                              int plannedPackets,
                              int targetPps,
                              int senderCount) {
            this.window = window;
            this.plannedPackets = plannedPackets;
            this.targetPps = targetPps;
            armed = new CountDownLatch(senderCount);
            completed = new CountDownLatch(senderCount);
            results = new SenderResult[senderCount];
        }
    }

    private static final class SenderResult {
        private final long plannedPackets;
        private final long attemptedPackets;
        private final long schedulerSkippedPackets;
        private final long socketRejectedPackets;
        private final long acceptedPackets;
        private final long maxSchedulerStallNanos;
        private final long maxResidualScheduleLagNanos;
        private final long completedNanos;
        private final long[] acceptedBits;

        private SenderResult(long plannedPackets,
                             long attemptedPackets,
                             long schedulerSkippedPackets,
                             long socketRejectedPackets,
                             long acceptedPackets,
                             long maxSchedulerStallNanos,
                             long maxResidualScheduleLagNanos,
                             long completedNanos,
                             long[] acceptedBits) {
            this.plannedPackets = plannedPackets;
            this.attemptedPackets = attemptedPackets;
            this.schedulerSkippedPackets = schedulerSkippedPackets;
            this.socketRejectedPackets = socketRejectedPackets;
            this.acceptedPackets = acceptedPackets;
            this.maxSchedulerStallNanos = maxSchedulerStallNanos;
            this.maxResidualScheduleLagNanos = maxResidualScheduleLagNanos;
            this.completedNanos = completedNanos;
            this.acceptedBits = acceptedBits;
        }
    }

    private static final class SenderSummary {
        private final int senderCount;
        private final long attemptedPackets;
        private final long schedulerSkippedPackets;
        private final long socketRejectedPackets;
        private final long acceptedPackets;
        private final long maxSchedulerStallNanos;
        private final long maxResidualScheduleLagNanos;
        private final long minAcceptedBySender;
        private final long maxAcceptedBySender;
        private final double minSchedulerSkipRatioBySender;
        private final double maxSchedulerSkipRatioBySender;
        private final long senderCompletionSkewNanos;

        private SenderSummary(int senderCount,
                              long attemptedPackets,
                              long schedulerSkippedPackets,
                              long socketRejectedPackets,
                              long acceptedPackets,
                              long maxSchedulerStallNanos,
                              long maxResidualScheduleLagNanos,
                              long minAcceptedBySender,
                              long maxAcceptedBySender,
                              double minSchedulerSkipRatioBySender,
                              double maxSchedulerSkipRatioBySender,
                              long senderCompletionSkewNanos) {
            this.senderCount = senderCount;
            this.attemptedPackets = attemptedPackets;
            this.schedulerSkippedPackets = schedulerSkippedPackets;
            this.socketRejectedPackets = socketRejectedPackets;
            this.acceptedPackets = acceptedPackets;
            this.maxSchedulerStallNanos = maxSchedulerStallNanos;
            this.maxResidualScheduleLagNanos = maxResidualScheduleLagNanos;
            this.minAcceptedBySender = minAcceptedBySender;
            this.maxAcceptedBySender = maxAcceptedBySender;
            this.minSchedulerSkipRatioBySender = minSchedulerSkipRatioBySender;
            this.maxSchedulerSkipRatioBySender = maxSchedulerSkipRatioBySender;
            this.senderCompletionSkewNanos = senderCompletionSkewNanos;
        }
    }

    private static final class OpenLoopRecorder {
        private static final long QUIESCE_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5);

        private final long[] acceptedBits;
        private final AtomicLongArray receivedBits;
        private final long[] latencyNanos;
        private final long lateNanos;
        private final AtomicReference<OpenLoopWindow> active = new AtomicReference<>();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final AtomicLong outsideWindow = new AtomicLong();
        private final AtomicInteger callbacksInFlight = new AtomicInteger();
        private int metadataOffset = -1;
        private long nextWindowId;
        private long inactiveBaseline;
        private boolean completedWindow;

        private OpenLoopRecorder(int maxPackets, int lateMicros) {
            if (lateMicros < 1) {
                throw new IllegalArgumentException("lateMicros must be greater than zero");
            }
            int words = (maxPackets + Long.SIZE - 1) / Long.SIZE;
            acceptedBits = new long[words];
            receivedBits = new AtomicLongArray(words);
            latencyNanos = new long[maxPackets];
            lateNanos = TimeUnit.MICROSECONDS.toNanos(lateMicros);
        }

        private void configureMetadataOffset(int metadataOffset) {
            if (this.metadataOffset != -1) {
                throw new IllegalStateException("Open-loop metadata offset is already configured");
            }
            if (metadataOffset < 0) {
                throw new IllegalArgumentException("metadataOffset must not be negative");
            }
            this.metadataOffset = metadataOffset;
        }

        private OpenLoopWindow prepare(int planned) {
            checkFailure();
            if (metadataOffset < 0) {
                throw new IllegalStateException("Open-loop metadata offset is not configured");
            }
            if (planned < 1 || planned > latencyNanos.length) {
                throw new IllegalArgumentException("Open-loop packet count exceeds recorder capacity");
            }
            if (active.get() != null) {
                throw new IllegalStateException("Previous open-loop window is still active");
            }
            long outsideWindowNow = outsideWindow.get();
            long inactiveArrivals = completedWindow ? outsideWindowNow - inactiveBaseline : 0;
            Arrays.fill(acceptedBits, 0);
            for (int i = 0; i < receivedBits.length(); i++) {
                receivedBits.set(i, 0);
            }
            OpenLoopWindow window = new OpenLoopWindow(++nextWindowId,
                                                       planned,
                                                       acceptedBits,
                                                       receivedBits,
                                                       latencyNanos,
                                                       outsideWindowNow,
                                                       inactiveArrivals);
            if (!active.compareAndSet(null, window)) {
                throw new IllegalStateException("Could not activate open-loop window");
            }
            return window;
        }

        private void record(ByteBuffer buffer) {
            callbacksInFlight.incrementAndGet();
            try {
                OpenLoopWindow window = active.get();
                if (window == null) {
                    outsideWindow.incrementAndGet();
                    return;
                }
                if (active.get() != window) {
                    window.crossWindowPackets.incrementAndGet();
                    return;
                }
                int offset = buffer.position() + metadataOffset;
                if (buffer.limit() - offset < OpenLoopSenderGroup.METADATA_SIZE) {
                    fail(new IllegalStateException("Open-loop datagram is too small for its metadata"));
                    return;
                }
                if (buffer.getInt(offset) != OpenLoopSenderGroup.MAGIC) {
                    fail(new IllegalStateException("Open-loop datagram has an invalid marker"));
                    return;
                }
                long windowId = buffer.getLong(offset + OpenLoopSenderGroup.WINDOW_ID_OFFSET);
                if (windowId != window.id) {
                    window.crossWindowPackets.incrementAndGet();
                    return;
                }
                int sequence = buffer.getInt(offset + OpenLoopSenderGroup.SEQUENCE_OFFSET);
                if (sequence < 0 || sequence >= window.plannedPackets) {
                    fail(new IllegalStateException("Open-loop datagram has an invalid sequence: " + sequence));
                    return;
                }
                int word = sequence >>> 6;
                long mask = 1L << (sequence & (Long.SIZE - 1));
                while (true) {
                    long received = window.receivedBits.get(word);
                    if ((received & mask) != 0) {
                        window.duplicatePackets.incrementAndGet();
                        return;
                    }
                    if (window.receivedBits.compareAndSet(word, received, received | mask)) {
                        break;
                    }
                }
                long receivedNanos = System.nanoTime();
                long sentNanos = buffer.getLong(offset + OpenLoopSenderGroup.SENT_NANOS_OFFSET);
                if (sentNanos <= 0 || receivedNanos < sentNanos) {
                    fail(new IllegalStateException("Open-loop datagram has an invalid send timestamp"));
                    return;
                }
                window.latencyNanos[sequence] = receivedNanos - sentNanos;
                if (receivedNanos > window.offerDeadlineNanos) {
                    window.drainArrivals.incrementAndGet();
                }
                window.uniqueReceived.incrementAndGet();
            } catch (RuntimeException e) {
                fail(e);
            } finally {
                callbacksInFlight.decrementAndGet();
            }
        }

        private CompletedOpenLoopWindow complete(OpenLoopWindow window,
                                                 int packetSize,
                                                 int connectionIdLength,
                                                 long offerNanos,
                                                 long drainNanos,
                                                 SenderSummary senderSummary,
                                                 int receiverSocketBufferBytes,
                                                 int minSenderSocketBufferBytes,
                                                 int maxSenderSocketBufferBytes) {
            if (!active.compareAndSet(window, null)) {
                throw new IllegalStateException("Open-loop window is no longer active");
            }
            long deadline = System.nanoTime() + QUIESCE_TIMEOUT_NANOS;
            while (callbacksInFlight.get() != 0) {
                if (System.nanoTime() - deadline >= 0) {
                    throw new IllegalStateException("Timed out quiescing open-loop callbacks");
                }
                Thread.onSpinWait();
            }
            checkFailure();
            long outsideWindowNow = outsideWindow.get();
            inactiveBaseline = outsideWindowNow;
            completedWindow = true;
            return new CompletedOpenLoopWindow(window,
                                               packetSize,
                                               connectionIdLength,
                                               offerNanos,
                                               drainNanos,
                                               lateNanos,
                                               senderSummary,
                                               receiverSocketBufferBytes,
                                               minSenderSocketBufferBytes,
                                               maxSenderSocketBufferBytes,
                                               Math.addExact(window.inactiveArrivalsBeforeWindow,
                                                             outsideWindowNow - window.outsideWindowBaseline));
        }

        private void fail(Throwable throwable) {
            failure.compareAndSet(null, throwable);
        }

        private void checkFailure() {
            Throwable current = failure.get();
            if (current != null) {
                throw new IllegalStateException("Open-loop receiver failed", current);
            }
        }

        private void checkInactiveArrivals() {
            checkFailure();
            if (callbacksInFlight.get() != 0) {
                throw new IllegalStateException("Open-loop callbacks remain active after receiver shutdown");
            }
            if (completedWindow && outsideWindow.get() != inactiveBaseline) {
                throw new IllegalStateException("Open-loop datagrams arrived after the final drain deadline");
            }
        }
    }

    private static final class OpenLoopWindow {
        private final long id;
        private final int plannedPackets;
        private final long[] acceptedBits;
        private final AtomicLongArray receivedBits;
        private final long[] latencyNanos;
        private final long outsideWindowBaseline;
        private final long inactiveArrivalsBeforeWindow;
        private final AtomicLong uniqueReceived = new AtomicLong();
        private final AtomicLong duplicatePackets = new AtomicLong();
        private final AtomicLong crossWindowPackets = new AtomicLong();
        private final AtomicLong drainArrivals = new AtomicLong();
        private volatile long offerDeadlineNanos;

        private OpenLoopWindow(long id,
                               int plannedPackets,
                               long[] acceptedBits,
                               AtomicLongArray receivedBits,
                               long[] latencyNanos,
                               long outsideWindowBaseline,
                               long inactiveArrivalsBeforeWindow) {
            this.id = id;
            this.plannedPackets = plannedPackets;
            this.acceptedBits = acceptedBits;
            this.receivedBits = receivedBits;
            this.latencyNanos = latencyNanos;
            this.outsideWindowBaseline = outsideWindowBaseline;
            this.inactiveArrivalsBeforeWindow = inactiveArrivalsBeforeWindow;
        }
    }

    private static final class RawUdpHarness implements AutoCloseable {
        private final int packetSize;
        private final int workerCount;
        private final AtomicLongArray received;
        private final OpenLoopRecorder openLoopRecorder;
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private int receiverSocketBufferBytes;
        private DatagramChannel receiver;
        private InetSocketAddress localAddress;
        private Thread receiverThread;
        private volatile boolean closing;

        private RawUdpHarness(int packetSize, int workerCount) {
            this(packetSize, workerCount, null);
        }

        private RawUdpHarness(int packetSize, int workerCount, OpenLoopRecorder openLoopRecorder) {
            if (packetSize < Integer.BYTES || packetSize > QuicConfigSupport.MAXIMUM_DATAGRAM_SIZE) {
                throw new IllegalArgumentException("packetSize must be between 4 and 65527");
            }
            if (workerCount < 1) {
                throw new IllegalArgumentException("workerCount must be greater than zero");
            }
            this.packetSize = packetSize;
            this.workerCount = workerCount;
            this.openLoopRecorder = openLoopRecorder;
            this.received = new AtomicLongArray(workerCount);
            try {
                receiver = DatagramChannel.open();
                receiver.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                localAddress = (InetSocketAddress) receiver.getLocalAddress();
                receiverSocketBufferBytes = receiver.getOption(StandardSocketOptions.SO_RCVBUF);
                System.out.printf(
                        "Raw UDP ingress setup workers=%d packetSize=%d receiverRcvBuf=%d%n",
                        workerCount,
                        packetSize,
                        receiverSocketBufferBytes);
                receiverThread = Thread.ofPlatform()
                        .name("raw-udp-ingress-receiver")
                        .daemon()
                        .unstarted(this::runReceiver);
                receiverThread.start();
            } catch (IOException e) {
                UncheckedIOException setupFailure =
                        new UncheckedIOException("Could not create raw UDP benchmark receiver", e);
                if (receiver != null) {
                    try {
                        receiver.close();
                    } catch (IOException closeFailure) {
                        setupFailure.addSuppressed(closeFailure);
                    }
                }
                throw setupFailure;
            } catch (RuntimeException | Error setupFailure) {
                if (receiver != null) {
                    try {
                        receiver.close();
                    } catch (IOException closeFailure) {
                        setupFailure.addSuppressed(closeFailure);
                    }
                }
                throw setupFailure;
            }
        }

        private void runReceiver() {
            ByteBuffer buffer = ByteBuffer.allocateDirect(
                    Math.max(QuicConfigSupport.MINIMUM_DATAGRAM_SIZE, packetSize));
            try {
                while (!closing) {
                    buffer.clear();
                    SocketAddress source = receiver.receive(buffer);
                    if (source == null) {
                        continue;
                    }
                    if (openLoopRecorder != null) {
                        buffer.flip();
                        openLoopRecorder.record(buffer);
                        received.incrementAndGet(0);
                        continue;
                    }
                    if (buffer.position() < Integer.BYTES) {
                        throw new IllegalStateException("Raw UDP benchmark received an undersized datagram");
                    }
                    int workerIndex = buffer.getInt(0);
                    if (workerIndex < 0 || workerIndex >= workerCount) {
                        throw new IllegalStateException("Raw UDP benchmark received an invalid worker index: "
                                                                + workerIndex);
                    }
                    received.incrementAndGet(workerIndex);
                }
            } catch (ClosedChannelException e) {
                if (!closing) {
                    failure.compareAndSet(null, e);
                    if (openLoopRecorder != null) {
                        openLoopRecorder.fail(e);
                    }
                }
            } catch (IOException | RuntimeException e) {
                if (!closing) {
                    failure.compareAndSet(null, e);
                    if (openLoopRecorder != null) {
                        openLoopRecorder.fail(e);
                    }
                }
            }
        }

        @Override
        public void close() {
            RuntimeException closeFailure = null;
            closing = true;
            if (receiver != null) {
                try {
                    receiver.close();
                } catch (IOException e) {
                    closeFailure = new UncheckedIOException("Could not close raw UDP benchmark receiver", e);
                }
            }
            if (receiverThread != null) {
                try {
                    receiverThread.join(TimeUnit.SECONDS.toMillis(5));
                    if (receiverThread.isAlive()) {
                        IllegalStateException threadFailure =
                                new IllegalStateException("Raw UDP benchmark receiver thread did not terminate");
                        if (closeFailure == null) {
                            closeFailure = threadFailure;
                        } else {
                            closeFailure.addSuppressed(threadFailure);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    IllegalStateException threadFailure =
                            new IllegalStateException("Interrupted while closing raw UDP benchmark receiver", e);
                    if (closeFailure == null) {
                        closeFailure = threadFailure;
                    } else {
                        closeFailure.addSuppressed(threadFailure);
                    }
                }
            }
            Throwable runtimeFailure = failure.get();
            if (runtimeFailure != null) {
                IllegalStateException receiverFailure =
                        new IllegalStateException("Raw UDP benchmark receiver failed", runtimeFailure);
                if (closeFailure == null) {
                    closeFailure = receiverFailure;
                } else {
                    closeFailure.addSuppressed(receiverFailure);
                }
            }
            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }

    private enum Strategy {
        NIO_SELECTOR,
        VIRTUAL_THREAD,
        BLOCKING_PLATFORM
    }

    private static final class EndpointHarness implements AutoCloseable {
        private static final long COMPLETION_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5);
        private static final long DRAIN_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(1);
        private static final long QUIET_DRAIN_NANOS = TimeUnit.MILLISECONDS.toNanos(10);
        private static final long PARK_NANOS = TimeUnit.MICROSECONDS.toNanos(10);
        private static final int SPIN_COUNT = 64;

        private final int packetSize;
        private final int maxBufferedHigh;
        private int connectionIdLength;
        private int receiverSocketBufferBytes;
        private BenchmarkInstance instance;
        private QuicEndpoint endpoint;
        private QuicSelector<?> selector;
        private DatagramChannel sender;
        private ExecutorService readExecutor;
        private GatedExecutor gatedExecutor;
        private CountingReceiver receiver;
        private CountingReceiver[] receivers;
        private ByteBuffer[] matchedPackets;
        private ByteBuffer[][] matchedPacketsByReceiver;
        private ByteBuffer[] supportedInitialPackets;
        private ByteBuffer[] rejectedInitialPackets;
        private int nextMatchedRoute;

        private EndpointHarness(String strategyName,
                                ExecutorMode executorMode,
                                int routeCount,
                                int packetSize,
                                boolean gated) {
            this(strategyName, executorMode, routeCount, packetSize, gated, 1, null);
        }

        private EndpointHarness(String strategyName,
                                ExecutorMode executorMode,
                                int routeCount,
                                int packetSize,
                                boolean gated,
                                int receiverCount) {
            this(strategyName, executorMode, routeCount, packetSize, gated, receiverCount, null);
        }

        private EndpointHarness(String strategyName,
                                ExecutorMode executorMode,
                                int routeCount,
                                int packetSize,
                                boolean gated,
                                int receiverCount,
                                OpenLoopRecorder openLoopRecorder) {
            Strategy strategy = Strategy.valueOf(strategyName);
            if (routeCount < 1) {
                throw new IllegalArgumentException("routeCount must be greater than zero");
            }
            if (receiverCount < 1 || receiverCount > routeCount) {
                throw new IllegalArgumentException("receiverCount must be between one and routeCount");
            }
            if (openLoopRecorder != null && receiverCount != 1) {
                throw new IllegalArgumentException("Open-loop recording requires one receiver");
            }
            if (packetSize < 21 || packetSize > QuicConfigSupport.MAXIMUM_DATAGRAM_SIZE) {
                throw new IllegalArgumentException("packetSize must be between 21 and 65527");
            }
            this.packetSize = packetSize;
            this.maxBufferedHigh = gated
                    ? Math.multiplyExact(packetSize, 8)
                    : QuicRuntimeConfig.DEFAULT_MAX_BUFFERED_HIGH;
            int maxBufferedLow = gated
                    ? Math.multiplyExact(packetSize, 2)
                    : QuicRuntimeConfig.DEFAULT_MAX_BUFFERED_LOW;

            try {
                readExecutor = switch (executorMode) {
                    case VIRTUAL_PER_TASK ->
                            Executors.newThreadPerTaskExecutor(
                                    Thread.ofVirtual().name("quic-ingress-read-", 1).factory());
                    case PLATFORM_SINGLE ->
                            Executors.newSingleThreadExecutor(
                                    Thread.ofPlatform().name("quic-ingress-read-", 1).daemon().factory());
                };
                gatedExecutor = gated ? new GatedExecutor(readExecutor) : null;
                Executor endpointExecutor = gated ? gatedExecutor : readExecutor;
                QuicConfig userConfig = QuicConfig.builder()
                        .maxUdpPayloadSize(Math.max(QuicConfigSupport.MINIMUM_DATAGRAM_SIZE, packetSize))
                        .build();
                QuicRuntimeConfig defaults = QuicRuntimeConfig.create(userConfig);
                ChannelType channelType = strategy == Strategy.NIO_SELECTOR
                        ? ChannelType.NON_BLOCKING_WITH_SELECTOR
                        : ChannelType.BLOCKING_WITH_VIRTUAL_THREADS;
                QuicSelectorThreading selectorThreading = strategy == Strategy.NIO_SELECTOR
                        ? QuicSelectorThreading.PLATFORM
                        : QuicSelectorThreading.VIRTUAL;
                QuicRuntimeConfig runtimeConfig = new QuicRuntimeConfig(
                        userConfig,
                        new QuicRuntimeConfig.Endpoint(channelType,
                                                       selectorThreading,
                                                       strategy == Strategy.BLOCKING_PLATFORM,
                                                       1,
                                                       false,
                                                       maxBufferedHigh,
                                                       maxBufferedLow,
                                                       true,
                                                       QuicRuntimeConfig.DEFAULT_DATAGRAM_SIZE),
                        defaults.recovery(),
                        defaults.transportParameters(),
                        defaults.confidentialityLimits());
                instance = new BenchmarkInstance(endpointExecutor, userConfig, openLoopRecorder);

                selector = switch (channelType) {
                    case NON_BLOCKING_WITH_SELECTOR ->
                            QuicSelector.createQuicNioSelector(instance, runtimeConfig, "quic-ingress-selector");
                    case BLOCKING_WITH_VIRTUAL_THREADS ->
                            QuicSelector.createQuicVirtualThreadPoller(instance, runtimeConfig, "quic-ingress-poller");
                };
                endpoint = switch (channelType) {
                    case NON_BLOCKING_WITH_SELECTOR ->
                            QuicEndpoint.QuicEndpointFactory.create()
                                    .createSelectableEndpoint(instance,
                                                              runtimeConfig,
                                                              "quic-ingress-endpoint",
                                                              new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                                                              selector.timer());
                    case BLOCKING_WITH_VIRTUAL_THREADS ->
                            QuicEndpoint.QuicEndpointFactory.create()
                                    .createVirtualThreadedEndpoint(instance,
                                                                   runtimeConfig,
                                                                   "quic-ingress-endpoint",
                                                                   new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                                                                   selector.timer());
                };
                instance.endpoint = endpoint;
                connectionIdLength = endpoint.idFactory().connectionIdLength();
                if (openLoopRecorder != null) {
                    openLoopRecorder.configureMetadataOffset(1 + connectionIdLength);
                }

                List<List<QuicConnectionId>> connectionIdsByReceiver = new ArrayList<>(receiverCount);
                for (int i = 0; i < receiverCount; i++) {
                    connectionIdsByReceiver.add(new ArrayList<>());
                }
                for (int i = 0; i < routeCount; i++) {
                    connectionIdsByReceiver.get(i % receiverCount).add(endpoint.idFactory().newConnectionId());
                }
                receivers = new CountingReceiver[receiverCount];
                matchedPacketsByReceiver = new ByteBuffer[receiverCount][];
                for (int receiverIndex = 0; receiverIndex < receiverCount; receiverIndex++) {
                    List<QuicConnectionId> connectionIds = connectionIdsByReceiver.get(receiverIndex);
                    CountingReceiver routeReceiver =
                            new CountingReceiver(connectionIds, receiverIndex == 0 ? openLoopRecorder : null);
                    receivers[receiverIndex] = routeReceiver;
                    ByteBuffer[] routePackets = new ByteBuffer[connectionIds.size()];
                    matchedPacketsByReceiver[receiverIndex] = routePackets;
                    for (int packetIndex = 0; packetIndex < connectionIds.size(); packetIndex++) {
                        QuicConnectionId connectionId = connectionIds.get(packetIndex);
                        if (!endpoint.addConnectionId(connectionId, routeReceiver)) {
                            throw new IllegalStateException("Could not register benchmark connection ID");
                        }
                        ByteBuffer packet = ByteBuffer.allocate(packetSize);
                        packet.put((byte) 0x40);
                        packet.put(connectionId.asReadOnlyBuffer());
                        packet.position(packet.limit());
                        routePackets[packetIndex] = packet.flip();
                    }
                }
                receiver = receivers[0];
                matchedPackets = matchedPacketsByReceiver[0];

                QuicConnectionId unmatchedConnectionId = endpoint.idFactory().newConnectionId();
                ByteBuffer supportedInitial;
                if (packetSize < QuicServerIngress.MIN_INITIAL_DATAGRAM_SIZE) {
                    supportedInitial = ByteBuffer.allocate(packetSize).asReadOnlyBuffer();
                } else {
                    supportedInitial = ByteBuffer.allocate(packetSize);
                    supportedInitial.put((byte) 0xc0);
                    supportedInitial.putInt(QuicVersion.QUIC_V1.versionNumber());
                    supportedInitial.put((byte) unmatchedConnectionId.length());
                    supportedInitial.put(unmatchedConnectionId.asReadOnlyBuffer());
                    supportedInitial.put((byte) Long.BYTES);
                    supportedInitial.putLong(0);
                    VariableLengthEncoder.encode(supportedInitial, 0);
                    int remaining = supportedInitial.capacity() - supportedInitial.position();
                    int encodedLengthSize = VariableLengthEncoder.encodedSize(remaining);
                    int encodedPacketLength = remaining - encodedLengthSize;
                    if (VariableLengthEncoder.encodedSize(encodedPacketLength) != encodedLengthSize) {
                        throw new IllegalArgumentException("packetSize falls on a QUIC variable-integer boundary");
                    }
                    VariableLengthEncoder.encode(supportedInitial, encodedPacketLength);
                    supportedInitial.position(supportedInitial.limit());
                    supportedInitial.flip();
                }
                supportedInitialPackets = new ByteBuffer[] {supportedInitial};

                ByteBuffer rejectedInitial = ByteBuffer.allocate(packetSize);
                rejectedInitial.put((byte) 0xc0);
                rejectedInitial.putInt(QuicVersion.QUIC_V1.versionNumber());
                rejectedInitial.put((byte) unmatchedConnectionId.length());
                rejectedInitial.put(unmatchedConnectionId.asReadOnlyBuffer());
                rejectedInitial.put((byte) 0);
                rejectedInitial.position(rejectedInitial.limit());
                rejectedInitialPackets = new ByteBuffer[] {rejectedInitial.flip()};

                sender = DatagramChannel.open();
                sender.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                sender.connect(endpoint.localAddress());

                QuicEndpoint.registerWithSelector(endpoint, selector);
                selector.start();
                matched(1, RouteAccess.HOT, null);
                if (packetSize >= QuicServerIngress.MIN_INITIAL_DATAGRAM_SIZE) {
                    supportedInitial(1, null);
                }
                rejectedInitial(1, null);
                receiverSocketBufferBytes = endpoint.channel().getOption(StandardSocketOptions.SO_RCVBUF);
                System.out.printf(
                        "QUIC ingress setup strategy=%s executor=%s routes=%d packetSize=%d cidLength=%d "
                                + "endpointRcvBuf=%d senderSndBuf=%d high=%d low=%d%n",
                        strategy,
                        executorMode,
                        routeCount,
                        packetSize,
                        connectionIdLength,
                        receiverSocketBufferBytes,
                        sender.getOption(StandardSocketOptions.SO_SNDBUF),
                        maxBufferedHigh,
                        maxBufferedLow);
            } catch (IOException e) {
                UncheckedIOException failure =
                        new UncheckedIOException("Could not initialize QUIC ingress benchmark sockets", e);
                try {
                    closeResources(false);
                } catch (RuntimeException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            } catch (RuntimeException | Error failure) {
                try {
                    closeResources(false);
                } catch (RuntimeException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }

        private long matched(int batchSize, RouteAccess routeAccess, IngressCounters counters) {
            return sendAndAwait(matchedPackets,
                                batchSize,
                                routeAccess,
                                receiver.received,
                                Metric.ROUTED,
                                counters);
        }

        private long supportedInitial(int batchSize, IngressCounters counters) {
            return sendAndAwait(supportedInitialPackets,
                                batchSize,
                                RouteAccess.HOT,
                                instance.admitted,
                                Metric.ADMITTED,
                                counters);
        }

        private long rejectedInitial(int batchSize, IngressCounters counters) {
            return sendAndAwait(rejectedInitialPackets,
                                batchSize,
                                RouteAccess.HOT,
                                instance.rejected,
                                Metric.REJECTED,
                                counters);
        }

        private long overloadRecovery(int batchSize, IngressCounters counters) {
            if (gatedExecutor == null) {
                throw new IllegalStateException("Overload recovery needs a gated executor");
            }
            long before = receiver.received.get();
            gatedExecutor.arm();
            boolean released = false;
            try {
                send(matchedPackets, batchSize, RouteAccess.HOT);
                awaitReadingPaused(true, "endpoint did not pause at its receive high watermark");
                int buffered = endpoint.buffered();
                if (buffered < maxBufferedHigh || buffered > maxBufferedHigh + packetSize) {
                    throw new IllegalStateException("Unexpected buffered bytes at pause: " + buffered);
                }
                gatedExecutor.release();
                released = true;
                long handled = awaitCounter(receiver.received, before + batchSize);
                awaitReadingPaused(false, "endpoint did not resume after draining");
                record(counters, batchSize, handled - before, Metric.ROUTED);
                if (counters != null) {
                    counters.recoveryCycles++;
                }
                return handled;
            } finally {
                if (!released) {
                    gatedExecutor.release();
                }
            }
        }

        private long overloadObservation(int offerMillis, IngressCounters counters) {
            if (gatedExecutor == null) {
                throw new IllegalStateException("Overload observation needs a gated executor");
            }
            if (offerMillis < 1) {
                throw new IllegalArgumentException("offerMillis must be greater than zero");
            }
            long before = receiver.received.get();
            long offered = 0;
            long accepted = 0;
            long acceptedBytes = 0;
            gatedExecutor.arm();
            boolean released = false;
            try {
                ByteBuffer packet = matchedPackets[0];
                long offerDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(offerMillis);
                do {
                    packet.rewind();
                    offered++;
                    int written = sender.write(packet);
                    if (written == packetSize) {
                        accepted++;
                        acceptedBytes += written;
                    } else if (written != 0) {
                        throw new IllegalStateException("UDP send accepted " + written + " of " + packetSize + " bytes");
                    }
                } while (System.nanoTime() - offerDeadline < 0);

                awaitReadingPaused(true, "endpoint did not pause during the overload observation window");
                gatedExecutor.release();
                released = true;

                long drainDeadline = System.nanoTime() + DRAIN_TIMEOUT_NANOS;
                long quietStarted = System.nanoTime();
                long lastObserved = receiver.received.get();
                while (true) {
                    instance.checkFailure();
                    long observed = receiver.received.get();
                    long now = System.nanoTime();
                    if (observed != lastObserved) {
                        lastObserved = observed;
                        quietStarted = now;
                    }
                    if (observed - before == accepted
                            || (!endpoint.readingPaused()
                            && endpoint.buffered() == 0
                            && now - quietStarted >= QUIET_DRAIN_NANOS)
                            || now - drainDeadline >= 0) {
                        break;
                    }
                    if (Thread.interrupted()) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while observing overload drain");
                    }
                    LockSupport.parkNanos(Math.min(PARK_NANOS, drainDeadline - now));
                }

                long handled = receiver.received.get();
                long handledThisWindow = handled - before;
                if (handledThisWindow > accepted) {
                    throw new IllegalStateException("Overload observation crossed invocation boundaries: accepted="
                                                            + accepted + ", routed=" + handledThisWindow);
                }
                counters.offeredPackets += offered;
                counters.acceptedPackets += accepted;
                counters.acceptedBytes += acceptedBytes;
                counters.routedPackets += handledThisWindow;
                counters.routedConnectionIdBytes += Math.multiplyExact(handledThisWindow, connectionIdLength);
                counters.unobservedAtDrainEnd += accepted - handledThisWindow;
                return handled;
            } catch (IOException e) {
                throw new UncheckedIOException("QUIC ingress overload observation send failed", e);
            } finally {
                if (!released) {
                    gatedExecutor.release();
                }
            }
        }

        private long sendAndAwait(ByteBuffer[] packets,
                                  int batchSize,
                                  RouteAccess routeAccess,
                                  AtomicLong handledCounter,
                                  Metric metric,
                                  IngressCounters counters) {
            long before = handledCounter.get();
            send(packets, batchSize, routeAccess);
            long handled = awaitCounter(handledCounter, before + batchSize);
            record(counters, batchSize, handled - before, metric);
            return handled;
        }

        private void send(ByteBuffer[] packets, int batchSize, RouteAccess routeAccess) {
            if (batchSize < 1) {
                throw new IllegalArgumentException("batchSize must be greater than zero");
            }
            int packetIndex = routeAccess == RouteAccess.HOT ? 0 : nextMatchedRoute;
            try {
                for (int i = 0; i < batchSize; i++) {
                    ByteBuffer packet = packets[packetIndex];
                    packet.rewind();
                    int written = sender.write(packet);
                    if (written != packetSize) {
                        throw new IllegalStateException("UDP send accepted " + written + " of " + packetSize + " bytes");
                    }
                    if (routeAccess == RouteAccess.ROTATING && ++packetIndex == packets.length) {
                        packetIndex = 0;
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("QUIC ingress benchmark send failed", e);
            }
            if (routeAccess == RouteAccess.ROTATING) {
                nextMatchedRoute = packetIndex;
            }
        }

        private long awaitCounter(AtomicLong counter, long expected) {
            long deadline = System.nanoTime() + COMPLETION_TIMEOUT_NANOS;
            int spins = SPIN_COUNT;
            long observed;
            while ((observed = counter.get()) < expected) {
                instance.checkFailure();
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new IllegalStateException("Timed out waiting for ingress callback: expected="
                                                            + expected + ", observed=" + observed);
                }
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for ingress callback");
                }
                if (spins-- > 0) {
                    Thread.onSpinWait();
                } else {
                    LockSupport.parkNanos(Math.min(PARK_NANOS, remaining));
                }
            }
            if (observed != expected) {
                throw new IllegalStateException("Ingress callback count exceeded its target: expected="
                                                        + expected + ", observed=" + observed);
            }
            return observed;
        }

        private void awaitReadingPaused(boolean expected, String message) {
            long deadline = System.nanoTime() + COMPLETION_TIMEOUT_NANOS;
            int spins = SPIN_COUNT;
            while (endpoint.readingPaused() != expected) {
                instance.checkFailure();
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new IllegalStateException(message);
                }
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for endpoint receive state");
                }
                if (spins-- > 0) {
                    Thread.onSpinWait();
                } else {
                    LockSupport.parkNanos(Math.min(PARK_NANOS, remaining));
                }
            }
        }

        private void record(IngressCounters counters,
                            int acceptedPackets,
                            long handled,
                            Metric metric) {
            if (counters == null) {
                return;
            }
            counters.offeredPackets += acceptedPackets;
            counters.acceptedPackets += acceptedPackets;
            counters.acceptedBytes += Math.multiplyExact((long) acceptedPackets, packetSize);
            switch (metric) {
            case ROUTED -> {
                counters.routedPackets += handled;
                counters.routedConnectionIdBytes += Math.multiplyExact(handled, connectionIdLength);
            }
            case ADMITTED -> counters.admittedPackets += handled;
            case REJECTED -> counters.rejectedPackets += handled;
            }
        }

        @Override
        public void close() {
            closeResources(true);
        }

        private void closeResources(boolean checkRuntimeFailure) {
            RuntimeException failure = null;
            if (gatedExecutor != null) {
                try {
                    gatedExecutor.release();
                } catch (RuntimeException e) {
                    failure = e;
                }
            }
            if (sender != null) {
                try {
                    sender.close();
                } catch (IOException e) {
                    UncheckedIOException closeFailure =
                            new UncheckedIOException("Could not close QUIC ingress benchmark sender", e);
                    if (failure == null) {
                        failure = closeFailure;
                    } else {
                        failure.addSuppressed(closeFailure);
                    }
                }
            }
            if (endpoint != null) {
                try {
                    endpoint.close();
                } catch (RuntimeException e) {
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
            if (selector != null) {
                try {
                    if (!selector.close(5, TimeUnit.SECONDS)) {
                        IllegalStateException closeFailure =
                                new IllegalStateException("QUIC ingress benchmark selector did not terminate");
                        if (failure == null) {
                            failure = closeFailure;
                        } else {
                            failure.addSuppressed(closeFailure);
                        }
                    }
                } catch (RuntimeException e) {
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
            if (readExecutor != null) {
                readExecutor.shutdown();
                try {
                    if (!readExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        readExecutor.shutdownNow();
                        if (!readExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                            IllegalStateException closeFailure =
                                    new IllegalStateException("QUIC ingress benchmark executor did not terminate");
                            if (failure == null) {
                                failure = closeFailure;
                            } else {
                                failure.addSuppressed(closeFailure);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    readExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                    IllegalStateException closeFailure =
                            new IllegalStateException("Interrupted while closing QUIC ingress benchmark", e);
                    if (failure == null) {
                        failure = closeFailure;
                    } else {
                        failure.addSuppressed(closeFailure);
                    }
                }
                if (!readExecutor.isTerminated()) {
                    IllegalStateException closeFailure =
                            new IllegalStateException("QUIC ingress benchmark executor remains active");
                    if (failure == null) {
                        failure = closeFailure;
                    } else {
                        failure.addSuppressed(closeFailure);
                    }
                }
            }
            if (checkRuntimeFailure && instance != null) {
                try {
                    instance.checkFailure();
                } catch (RuntimeException e) {
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    private enum Metric {
        ROUTED,
        ADMITTED,
        REJECTED
    }

    private static final class BenchmarkInstance implements QuicInstance {
        private final Executor executor;
        private final QuicConfig quicConfig;
        private final OpenLoopRecorder openLoopRecorder;
        private final QuicServerIngress ingress =
                new QuicServerIngress(List.of(QuicVersion.QUIC_V1), false, null);
        private final AtomicLong admitted = new AtomicLong();
        private final AtomicLong rejected = new AtomicLong();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private volatile QuicEndpoint endpoint;

        private BenchmarkInstance(Executor executor,
                                  QuicConfig quicConfig,
                                  OpenLoopRecorder openLoopRecorder) {
            this.executor = executor;
            this.quicConfig = quicConfig;
            this.openLoopRecorder = openLoopRecorder;
        }

        private void checkFailure() {
            Throwable current = failure.get();
            if (current != null) {
                throw new IllegalStateException("QUIC ingress benchmark endpoint failed", current);
            }
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
                                        QuicPacket.HeadersType type,
                                        ByteBuffer buffer) {
            if (!(source instanceof InetSocketAddress peerAddress) || type != QuicPacket.HeadersType.LONG) {
                rejected.incrementAndGet();
                return;
            }
            QuicServerIngress.Action action = ingress.inspect(peerAddress, endpoint.idFactory(), buffer);
            if (action instanceof QuicServerIngress.Admit) {
                admitted.incrementAndGet();
            } else {
                rejected.incrementAndGet();
            }
        }

        @Override
        public void runtimeFailed(Throwable failure) {
            this.failure.compareAndSet(null, failure);
            if (openLoopRecorder != null) {
                openLoopRecorder.fail(failure);
            }
        }

        @Override
        public boolean isVersionAvailable(QuicVersion quicVersion) {
            return quicVersion == QuicVersion.QUIC_V1;
        }

        @Override
        public List<QuicVersion> availableVersions() {
            return List.of(QuicVersion.QUIC_V1);
        }

        @Override
        public String instanceId() {
            return "quic-endpoint-ingress-benchmark";
        }

        @Override
        public QuicTLSContext quicTlsContext() {
            throw new UnsupportedOperationException("TLS is outside the endpoint ingress benchmark");
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public QuicConfig quicConfig() {
            return quicConfig;
        }
    }

    private static final class CountingReceiver implements QuicPacketReceiver {
        private final List<QuicConnectionId> connectionIds;
        private final OpenLoopRecorder openLoopRecorder;
        private final AtomicLong received = new AtomicLong();

        private CountingReceiver(List<QuicConnectionId> connectionIds, OpenLoopRecorder openLoopRecorder) {
            this.connectionIds = List.copyOf(connectionIds);
            this.openLoopRecorder = openLoopRecorder;
        }

        @Override
        public List<QuicConnectionId> connectionIds() {
            return connectionIds;
        }

        @Override
        public List<PeerResetToken> activeResetTokens() {
            return List.of();
        }

        @Override
        public void processIncoming(SocketAddress source,
                                    ByteBuffer destConnId,
                                    QuicPacket.HeadersType headersType,
                                    ByteBuffer buffer) {
            if (openLoopRecorder != null) {
                openLoopRecorder.record(buffer);
            }
            received.incrementAndGet();
        }

        @Override
        public void onWriteError(Throwable throwable) {
        }

        @Override
        public void processStatelessReset() {
        }

        @Override
        public void shutdown() {
        }
    }

    private static final class GatedExecutor implements Executor {
        private static final int IDLE = 0;
        private static final int ARMED = 1;
        private static final int CLAIMED = 2;
        private static final int RELEASED = 3;

        private final Executor delegate;
        private final AtomicInteger state = new AtomicInteger();
        private volatile Thread blockedThread;

        private GatedExecutor(Executor delegate) {
            this.delegate = delegate;
        }

        private void arm() {
            if (!state.compareAndSet(IDLE, ARMED)) {
                throw new IllegalStateException("QUIC ingress benchmark executor gate is already armed");
            }
        }

        private void release() {
            while (true) {
                int current = state.get();
                switch (current) {
                case IDLE:
                    return;
                case ARMED:
                    if (state.compareAndSet(ARMED, IDLE)) {
                        return;
                    }
                    break;
                case CLAIMED:
                    if (state.compareAndSet(CLAIMED, RELEASED)) {
                        LockSupport.unpark(blockedThread);
                        return;
                    }
                    break;
                case RELEASED:
                    LockSupport.unpark(blockedThread);
                    return;
                default:
                    throw new IllegalStateException("Unknown QUIC ingress benchmark gate state: " + current);
                }
            }
        }

        @Override
        public void execute(Runnable command) {
            delegate.execute(() -> {
                if (state.compareAndSet(ARMED, CLAIMED)) {
                    blockedThread = Thread.currentThread();
                    try {
                        while (state.get() == CLAIMED) {
                            if (Thread.interrupted()) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException("QUIC ingress benchmark executor gate was interrupted");
                            }
                            LockSupport.parkNanos(EndpointHarness.PARK_NANOS);
                        }
                    } finally {
                        blockedThread = null;
                        state.compareAndSet(CLAIMED, IDLE);
                        state.compareAndSet(RELEASED, IDLE);
                    }
                }
                command.run();
            });
        }
    }
}
