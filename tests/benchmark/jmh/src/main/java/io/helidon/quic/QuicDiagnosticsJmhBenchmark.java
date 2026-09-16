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
import java.lang.management.ManagementFactory;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.common.socket.PeerInfo;
import io.helidon.common.socket.SocketContext;
import io.helidon.common.tls.Tls;
import io.helidon.quic.QuicEndpoint.ChannelType;
import io.helidon.quic.QuicEndpoint.QuicDatagram;
import io.helidon.quic.frame.AckFrame;
import io.helidon.quic.packet.PacketSpace;
import io.helidon.quic.packet.QuicPacket;
import io.helidon.quic.packet.QuicPacket.PacketNumberSpace;

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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Measures real QUIC hot paths with diagnostics disabled or routed to a bounded in-memory handler.
 * Allocation variants count only the JMH worker's measured operations, excluding fixture and writer work.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@Threads(1)
public class QuicDiagnosticsJmhBenchmark {
    static final int SEND_BATCH = 64;
    static final int RECEIVE_BATCH = 64;
    static final int MAX_QUEUE_DEPTH = 4096;

    private static final int DATAGRAM_SIZE = 1200;
    private static final int RECEIVE_SIZE = 64;
    private static final int RECEIVE_SEQUENCE_OFFSET = 24;
    private static final String LOG_TAG = "quic-diagnostics-jmh";

    /**
     * Offers and cancels one event against a reusable timer queue.
     *
     * @param state timer fixture
     * @return whether the event was removed
     */
    @Benchmark
    public boolean timerOffer(TimerState state) {
        state.timer.offer(state.event);
        return state.timer.cancel(state.event);
    }

    /**
     * Counts worker allocation for one offer/cancel cycle.
     *
     * @param state timer fixture
     * @param counters worker allocation counters
     * @return whether the event was removed
     */
    @Benchmark
    public boolean timerOfferAllocation(TimerState state, AllocationCounters counters) {
        long before = counters.before();
        boolean removed = timerOffer(state);
        counters.record(before, 1);
        return removed;
    }

    /**
     * Reschedules, refreshes, and removes one future event from a reusable queue.
     *
     * @param state timer fixture
     * @return the refreshed deadline
     */
    @Benchmark
    public Deadline timerReschedule(TimerState state) {
        state.timer.reschedule(state.event, state.event.deadline());
        Deadline deadline = state.timer.processEventsAndReturnNextDeadline(state.now, Runnable::run);
        state.timer.cancel(state.event);
        return deadline;
    }

    /**
     * Counts worker allocation for one reschedule/refresh/cancel cycle.
     *
     * @param state timer fixture
     * @param counters worker allocation counters
     * @return the refreshed deadline
     */
    @Benchmark
    public Deadline timerRescheduleAllocation(TimerState state, AllocationCounters counters) {
        long before = counters.before();
        Deadline deadline = timerReschedule(state);
        counters.record(before, 1);
        return deadline;
    }

    /**
     * Computes a real packet space's next deadline without changing its prepared workload.
     *
     * @param state packet-space fixture
     * @return next deadline
     */
    @Benchmark
    public Deadline packetDeadline(DeadlineState state) {
        return state.packetSpace.computeNextDeadline();
    }

    /**
     * Counts worker allocation for deadline computation.
     *
     * @param state packet-space fixture
     * @param counters worker allocation counters
     * @return next deadline
     */
    @Benchmark
    public Deadline packetDeadlineAllocation(DeadlineState state, AllocationCounters counters) {
        long before = counters.before();
        Deadline deadline = packetDeadline(state);
        counters.record(before, 1);
        return deadline;
    }

    /**
     * Acquires and releases an actual connection's outgoing buffer.
     *
     * @param state connection fixture
     * @return released buffer
     */
    @Benchmark
    public ByteBuffer outgoingBuffer(BufferState state) {
        ByteBuffer buffer = state.connection.outgoingByteBuffer(DATAGRAM_SIZE);
        state.connection.datagramReleased(QuicDatagram.create(state.connection, state.peer, buffer));
        return buffer;
    }

    /**
     * Counts worker allocation for outgoing-buffer acquisition and release, including its datagram wrapper.
     *
     * @param state connection fixture
     * @param counters worker allocation counters
     * @return released buffer
     */
    @Benchmark
    public ByteBuffer outgoingBufferAllocation(BufferState state, AllocationCounters counters) {
        long before = counters.before();
        ByteBuffer buffer = outgoingBuffer(state);
        counters.record(before, 1);
        return buffer;
    }

    /**
     * Sends one datagram synchronously through the actual endpoint and UDP channel.
     *
     * @param state endpoint fixture
     * @return total sender completions
     */
    @Benchmark
    public long synchronousDatagram(SynchronousState state) {
        state.instance.endpoint.pushDatagram(state.receiver, state.destination, state.buffer);
        return state.receiver.sent;
    }

    /**
     * Counts worker allocation for a synchronous send, including its completion callback.
     *
     * @param state endpoint fixture
     * @param counters worker allocation counters
     * @return total sender completions
     */
    @Benchmark
    public long synchronousDatagramAllocation(SynchronousState state, AllocationCounters counters) {
        long before = counters.before();
        long sent = synchronousDatagram(state);
        counters.record(before, 1);
        return sent;
    }

    /**
     * Submits a bounded batch while the real asynchronous writer is parked at the selected backlog.
     *
     * @param state endpoint fixture
     * @return total datagrams submitted by the fixture
     */
    @Benchmark
    @OperationsPerInvocation(SEND_BATCH)
    public long queuedDatagrams(QueuedState state) {
        for (int i = 0; i < SEND_BATCH; i++) {
            state.submit();
        }
        return state.submitted;
    }

    /**
     * Counts worker allocation for submissions, excluding asynchronous sends and invocation cleanup.
     *
     * @param state endpoint fixture
     * @param counters worker allocation counters
     * @return total datagrams submitted by the fixture
     */
    @Benchmark
    @OperationsPerInvocation(SEND_BATCH)
    public long queuedDatagramsAllocation(QueuedState state, AllocationCounters counters) {
        long before = counters.before();
        long submitted = queuedDatagrams(state);
        counters.record(before, SEND_BATCH);
        return submitted;
    }

    /**
     * Dispatches a prepared batch through the endpoint's real scheduled read loop on the JMH worker.
     *
     * @param state receive fixture
     * @return total receiver callbacks
     */
    @Benchmark
    @OperationsPerInvocation(RECEIVE_BATCH)
    public long receiveDispatch(ReceiveState state) {
        state.dispatch.run();
        return state.receiver.received + state.receiver.resets;
    }

    /**
     * Counts worker allocation for receive dispatch, excluding network ingress and queue preparation.
     *
     * @param state receive fixture
     * @param counters worker allocation counters
     * @return total receiver callbacks
     */
    @Benchmark
    @OperationsPerInvocation(RECEIVE_BATCH)
    public long receiveDispatchAllocation(ReceiveState state, AllocationCounters counters) {
        long before = counters.before();
        long dispatched = receiveDispatch(state);
        counters.record(before, RECEIVE_BATCH);
        return dispatched;
    }

    private static void validateDepth(int depth) {
        if (depth < 0 || depth > MAX_QUEUE_DEPTH) {
            throw new IllegalArgumentException("queueDepth must be between 0 and " + MAX_QUEUE_DEPTH);
        }
    }

    private static QuicTLSEngine tlsEngine() {
        return (QuicTLSEngine) Proxy.newProxyInstance(QuicTLSEngine.class.getClassLoader(),
                                                     new Class<?>[] {QuicTLSEngine.class},
                                                     (_, method, _) -> switch (method.getName()) {
                                                         case "keysAvailable" -> true;
                                                         case "handshakeState" ->
                                                                 QuicTLSEngine.HandshakeState.HANDSHAKE_CONFIRMED;
                                                         default -> method.getReturnType() == boolean.class ? false : null;
                                                     });
    }

    private static QuicServerHandshakeAdmission.Permit timerPermit(QuicServerHandshakeAdmission admission) {
        long reservation = admission.tryReserve();
        if (reservation == QuicServerHandshakeAdmission.NO_RESERVATION) {
            throw new IllegalStateException("Diagnostic timer admission is full");
        }
        return admission.materializePermit(reservation,
                                           QuicServerRuntime.ConnectionPermit.accepted(() -> { }, () -> { }));
    }

    /**
     * Diagnostic level applied inside each fork.
     */
    public enum LogLevel {
        /**
         * Disable diagnostics.
         */
        OFF,
        /**
         * Enable DEBUG messages without console output.
         */
        DEBUG,
        /**
         * Enable TRACE and DEBUG messages without console output.
         */
        TRACE
    }

    /**
     * Receive branch dispatched from a populated endpoint queue.
     */
    public enum ReceivePath {
        /**
         * A short-header packet with a registered connection ID.
         */
        PACKET,
        /**
         * A stateless reset with a registered peer token.
         */
        STATELESS_RESET
    }

    /**
     * Receiver shape used by the endpoint's diagnostic tag selection.
     */
    public enum ReceiverKind {
        /**
         * Connection-style socket IDs supplied by a socket context.
         */
        SOCKET_CONTEXT,
        /**
         * An ordinary packet receiver using the endpoint's identity fallback.
         */
        FALLBACK
    }

    /**
     * Stable packet-space condition used for repeated deadline computation.
     */
    public enum DeadlineWorkload {
        /**
         * No scheduled work.
         */
        IDLE,
        /**
         * A pending application-space ACK.
         */
        ACK,
        /**
         * The handshake anti-deadlock PTO.
         */
        PTO
    }

    /**
     * Fork-wide logger configuration, restored after dependent fixtures stop.
     */
    @State(Scope.Benchmark)
    public static class LoggingState {
        private final CaptureHandler handler = new CaptureHandler();

        /**
         * Diagnostic level.
         */
        @Param({"OFF", "TRACE"})
        public LogLevel logLevel;

        private List<LoggerSetting> settings;

        /**
         * Installs a handler retaining only a count and the latest message.
         */
        @Setup(Level.Trial)
        public void setUp() {
            settings = List.of(new LoggerSetting("io.helidon.quic"),
                               new LoggerSetting(QuicTimerQueue.class.getName()),
                               new LoggerSetting(PacketSpaceManager.class.getName()),
                               new LoggerSetting(QuicConnectionImpl.class.getName()),
                               new LoggerSetting(QuicEndpoint.class.getName()));
            for (LoggerSetting setting : settings) {
                setting.install(logLevel, handler);
            }
            boolean trace = logLevel == LogLevel.TRACE;
            boolean debug = logLevel != LogLevel.OFF;
            for (LoggerSetting setting : settings) {
                System.Logger logger = System.getLogger(setting.logger.getName());
                if (logger.isLoggable(System.Logger.Level.TRACE) != trace
                        || logger.isLoggable(System.Logger.Level.DEBUG) != debug) {
                    tearDown();
                    throw new IllegalStateException("System.Logger is not using the configured JUL diagnostic level");
                }
            }
        }

        /**
         * Restores every modified logger setting and handler.
         */
        @TearDown(Level.Trial)
        public void tearDown() {
            for (LoggerSetting setting : settings) {
                setting.restore(handler);
            }
        }

        long messages() {
            return handler.messages.get();
        }

        String lastMessage() {
            return handler.lastMessage;
        }
    }

    /**
     * Timer queue constructed once with a fixed bounded population of future events.
     */
    @State(Scope.Thread)
    public static class TimerState {
        private final List<QuicServerHandshakeAdmission.Permit> background = new ArrayList<>();

        /**
         * Other events retained between operations.
         */
        @Param({"0", "64"})
        public int queueDepth;

        private QuicTimerQueue timer;
        private Deadline now;
        private QuicServerHandshakeAdmission.Permit event;

        /**
         * Prepares future timers without starting a timer thread.
         *
         * @param logging fork logger configuration
         */
        @Setup(Level.Trial)
        public void setUp(LoggingState logging) {
            validateDepth(queueDepth);
            now = TimeSource.now();
            timer = new QuicTimerQueue(() -> { }, () -> LOG_TAG);
            var admission = new QuicServerHandshakeAdmission(Math.max(1, queueDepth), Duration.ofHours(2), _ -> {
                throw new IllegalStateException("Future diagnostic timer unexpectedly fired");
            });
            for (int i = 0; i < queueDepth; i++) {
                var permit = timerPermit(admission);
                background.add(permit);
                timer.offer(permit);
            }
            timer.processEventsAndReturnNextDeadline(now, Runnable::run);
            event = timerPermit(new QuicServerHandshakeAdmission(1, Duration.ofHours(1), _ -> {
                throw new IllegalStateException("Future diagnostic timer unexpectedly fired");
            }));
        }

        /**
         * Clears all scheduled events.
         */
        @TearDown(Level.Trial)
        public void tearDown() {
            timer.stop();
            event.release();
            background.forEach(QuicServerHandshakeAdmission.Permit::release);
        }

        Deadline nextDeadline() {
            return timer.nextDeadline();
        }
    }

    /**
     * Reusable packet space with the production monotonic clock and no invocation fixture.
     */
    @State(Scope.Thread)
    public static class DeadlineState {
        /**
         * Condition whose deadline is repeatedly computed.
         */
        @Param({"IDLE", "ACK", "PTO"})
        public DeadlineWorkload deadlineWorkload;

        private PacketSpaceManager packetSpace;
        private DeadlineEmitter emitter;

        /**
         * Prepares the selected stable deadline condition.
         *
         * @param logging fork logger configuration
         */
        @Setup(Level.Trial)
        public void setUp(LoggingState logging) {
            emitter = new DeadlineEmitter();
            QuicRuntimeConfig config = QuicRuntimeConfig.create(QuicConfig.create());
            QuicRttEstimator rtt = QuicRttEstimator.create(config.recovery());
            // Deadlines are initialized once; diagnostic clock reads remain live.
            packetSpace = new PacketSpaceManager(deadlineWorkload == DeadlineWorkload.PTO
                                                        ? PacketNumberSpace.HANDSHAKE : PacketNumberSpace.APPLICATION,
                                                 emitter,
                                                 TimeSource.source(),
                                                 rtt,
                                                 QuicCubicCongestionController.create(config, LOG_TAG, rtt, DATAGRAM_SIZE),
                                                 tlsEngine(),
                                                 () -> LOG_TAG,
                                                 new PacketSpaceManager.PathRecoveryState(1),
                                                 config.transportParameters().ackDelayExponent(),
                                                 config.transportParameters().maxAckDelay().toMillis(),
                                                 _ -> { });
            if (deadlineWorkload == DeadlineWorkload.ACK) {
                packetSpace.packetReceived(QuicPacket.PacketType.ONERTT, 0, true);
            }
            packetSpace.computeNextDeadline();
        }

        /**
         * Closes the packet space and timer queue.
         */
        @TearDown(Level.Trial)
        public void tearDown() {
            try {
                packetSpace.close();
            } finally {
                emitter.timer.stop();
            }
        }
    }

    /**
     * Actual connection and endpoint reused for outgoing-buffer cycles.
     */
    @State(Scope.Thread)
    public static class BufferState {
        private final InetSocketAddress peer = new InetSocketAddress(InetAddress.getLoopbackAddress(), 4433);

        /**
         * Whether connection buffers are pooled direct buffers or newly allocated heap buffers.
         */
        @Param({"true", "false"})
        public boolean bufferPool;

        private BenchmarkInstance instance;
        private QuicConnectionImpl connection;

        /**
         * Creates one connection and primes a direct buffer when pooling is enabled.
         *
         * @param logging fork logger configuration
         */
        @Setup(Level.Trial)
        public void setUp(LoggingState logging) {
            instance = new BenchmarkInstance(bufferPool, false, Runnable::run);
            try {
                var parameters = QuicTlsParameters.defaultParameters();
                parameters.setApplicationProtocols(new String[] {"h3"});
                connection = new QuicConnectionImpl(instance, instance.config, peer, "localhost", peer.getPort(),
                                                    parameters, "%s", 1);
                ByteBuffer buffer = connection.outgoingByteBuffer(DATAGRAM_SIZE);
                connection.datagramReleased(QuicDatagram.create(connection, peer, buffer));
            } catch (RuntimeException | Error failure) {
                instance.close();
                throw failure;
            }
        }

        /**
         * Closes connection state, its endpoint, and its timer queue.
         */
        @TearDown(Level.Trial)
        public void tearDown() {
            try {
                connection.shutdown();
                connection.pathManager().close();
            } finally {
                instance.close();
            }
        }
    }

    /**
     * Reusable actual endpoint for synchronous sender-acceptance measurements.
     */
    @State(Scope.Thread)
    public static class SynchronousState {
        private final ByteBuffer buffer = ByteBuffer.allocateDirect(DATAGRAM_SIZE);
        private final CountingReceiver receiver = new CountingReceiver();
        private BenchmarkInstance instance;
        private DatagramChannel sink;
        private InetSocketAddress destination;

        /**
         * Opens sender and sink once per trial.
         *
         * @param logging fork logger configuration
         * @throws IOException if a loopback channel cannot be created
         */
        @Setup(Level.Trial)
        public void setUp(LoggingState logging) throws IOException {
            try {
                sink = DatagramChannel.open();
                sink.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                destination = (InetSocketAddress) sink.getLocalAddress();
                instance = new BenchmarkInstance(true, false, Runnable::run);
            } catch (IOException | RuntimeException | Error failure) {
                tearDown();
                throw failure;
            }
        }

        /**
         * Closes both channels and reports any failed sender completion.
         *
         * @throws IOException if the sink cannot be closed
         */
        @TearDown(Level.Trial)
        public void tearDown() throws IOException {
            try {
                if (instance != null) {
                    instance.close();
                }
            } finally {
                if (sink != null) {
                    sink.close();
                }
            }
            receiver.checkFailure();
        }

        int payloadRemaining() {
            return buffer.remaining();
        }
    }

    /**
     * A real async writer held between batches so queue depth stays bounded and repeatable.
     * Only trial setup fills the backlog; invocation cleanup drains one batch and recycles its buffers.
     */
    @State(Scope.Thread)
    public static class QueuedState {
        private final CountDownLatch startWriter = new CountDownLatch(1);
        private final BatchReceiver receiver = new BatchReceiver();

        /**
         * Datagrams waiting before each measured batch; submission grows this by {@value #SEND_BATCH}.
         */
        @Param({"0", "64"})
        public int queueDepth;

        private ExecutorService writer;
        private BenchmarkInstance instance;
        private DatagramChannel sink;
        private InetSocketAddress destination;
        private ByteBuffer[] buffers;
        private int nextBuffer;
        private long submitted;

        /**
         * Allocates reusable buffers, fills the initial backlog, and parks the writer after one sentinel send.
         *
         * @param logging fork logger configuration
         * @throws IOException if a loopback channel cannot be created
         * @throws InterruptedException if the writer rendezvous is interrupted
         */
        @Setup(Level.Trial)
        public void setUp(LoggingState logging) throws IOException, InterruptedException {
            validateDepth(queueDepth);
            try {
                sink = DatagramChannel.open();
                sink.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                destination = (InetSocketAddress) sink.getLocalAddress();
                writer = Executors.newSingleThreadExecutor(Thread.ofPlatform()
                                                                  .daemon()
                                                                  .name(LOG_TAG + "-writer")
                                                                  .factory());
                instance = new BenchmarkInstance(true, true, task -> writer.execute(() -> {
                    try {
                        if (!startWriter.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out starting the diagnostic writer");
                        }
                        task.run();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        receiver.fail(e);
                    } catch (Throwable failure) {
                        receiver.fail(failure);
                    }
                }));
                buffers = new ByteBuffer[queueDepth + SEND_BATCH + 1];
                for (int i = 0; i < buffers.length; i++) {
                    buffers[i] = ByteBuffer.allocateDirect(DATAGRAM_SIZE);
                }
                for (int i = 0; i <= queueDepth; i++) {
                    submit();
                }
                startWriter.countDown();
                receiver.awaitParked();
                verifyBacklog();
            } catch (IOException | InterruptedException | RuntimeException | Error failure) {
                tearDown();
                throw failure;
            }
        }

        /**
         * Drains exactly one measured batch and verifies that the chosen backlog remains.
         *
         * @throws InterruptedException if the writer rendezvous is interrupted
         */
        @TearDown(Level.Invocation)
        public void finishBatch() throws InterruptedException {
            receiver.remaining = SEND_BATCH;
            receiver.resume.release();
            receiver.awaitParked();
            verifyBacklog();
        }

        /**
         * Closes the endpoint while its writer is parked and waits for the owned writer to exit.
         *
         * @throws IOException if the loopback sink cannot be closed
         * @throws InterruptedException if writer termination is interrupted
         */
        @TearDown(Level.Trial)
        public void tearDown() throws IOException, InterruptedException {
            receiver.closing = true;
            try {
                if (instance != null) {
                    instance.close();
                }
            } finally {
                startWriter.countDown();
                receiver.resume.release();
                try {
                    if (writer != null) {
                        writer.shutdown();
                        if (!writer.awaitTermination(10, TimeUnit.SECONDS)) {
                            writer.shutdownNow();
                            if (!writer.awaitTermination(10, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Diagnostic writer did not terminate");
                            }
                        }
                    }
                } finally {
                    if (sink != null) {
                        sink.close();
                    }
                }
            }
            receiver.checkFailure();
        }

        long backlog() {
            return submitted - receiver.sent;
        }

        boolean writerTerminated() {
            return writer.isTerminated();
        }

        long dropped() {
            return receiver.dropped;
        }

        private void submit() {
            ByteBuffer buffer = buffers[nextBuffer];
            nextBuffer = (nextBuffer + 1) % buffers.length;
            instance.endpoint.pushDatagram(receiver, destination, buffer);
            submitted++;
        }

        private void verifyBacklog() {
            receiver.checkFailure();
            if (backlog() != queueDepth) {
                throw new IllegalStateException("Expected queue depth " + queueDepth + ", got " + backlog());
            }
        }
    }

    /**
     * Bounded receive batches whose actual queue dispatch runs synchronously on the measured worker.
     * Network ingress, heap copies, route lookup, and batch verification are invocation fixtures.
     */
    @State(Scope.Thread)
    public static class ReceiveState {
        private final DispatchExecutor executor = new DispatchExecutor();
        private final ByteBuffer payload = ByteBuffer.allocate(RECEIVE_SIZE);

        /**
         * Receive branch to dispatch.
         */
        @Param({"PACKET", "STATELESS_RESET"})
        public ReceivePath receivePath;

        /**
         * Shape of the target receiver.
         */
        @Param({"SOCKET_CONTEXT", "FALLBACK"})
        public ReceiverKind receiverKind;

        private BenchmarkInstance instance;
        private DatagramChannel sender;
        private ReceiveReceiver receiver;
        private QuicConnectionId connectionId;
        private SocketAddress source;
        private SocketAddress destination;
        private Runnable dispatch;
        private long expectedReceived;
        private long expectedResets;
        private long expectedSequenceSum;

        /**
         * Creates one selectable endpoint and registers its connection route or peer reset token.
         *
         * @param logging fork logger configuration
         * @throws IOException if a loopback sender cannot be opened
         */
        @Setup(Level.Trial)
        public void setUp(LoggingState logging) throws IOException {
            try {
                sender = DatagramChannel.open();
                sender.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                source = sender.getLocalAddress();
                instance = new BenchmarkInstance(true, false, executor, true);
                destination = instance.endpoint.localAddress();
                connectionId = instance.endpoint.idFactory().newConnectionId();
                receiver = switch (receiverKind) {
                    case SOCKET_CONTEXT ->
                            new SocketReceiveReceiver(instance.instanceId(),
                                                      Long.toString(((InetSocketAddress) source).getPort()));
                    case FALLBACK -> new ReceiveReceiver();
                };
                payload.put((byte) 0x40).put(connectionId.asReadOnlyBuffer());
                if (receivePath == ReceivePath.PACKET) {
                    if (!instance.endpoint.addConnectionId(connectionId, receiver)) {
                        throw new IllegalStateException("Could not register diagnostic receive route");
                    }
                } else {
                    byte[] token = new byte[16];
                    for (int i = 0; i < token.length; i++) {
                        token[i] = (byte) (i + 1);
                    }
                    payload.position(RECEIVE_SIZE - token.length).put(token);
                    instance.endpoint.associateStatelessResetToken(
                            QuicPacketReceiver.PeerResetToken.create(token, source), receiver);
                }
                payload.clear();
            } catch (IOException | RuntimeException | Error failure) {
                tearDown();
                throw failure;
            }
        }

        /**
         * Populates exactly one bounded batch while retaining the scheduled dispatch task.
         *
         * @throws IOException if a loopback datagram cannot be sent
         */
        @Setup(Level.Invocation)
        public void prepareBatch() throws IOException {
            if (instance.endpoint.buffered() != 0 || executor.pending != null) {
                throw new IllegalStateException("Previous receive batch was not completely dispatched");
            }
            expectedReceived = receiver.received;
            expectedResets = receiver.resets;
            expectedSequenceSum = receiver.sequenceSum;
            for (int i = 0; i < RECEIVE_BATCH; i++) {
                long sequence = receiver.received + i + 1;
                payload.clear().putLong(RECEIVE_SEQUENCE_OFFSET, sequence);
                if (sender.send(payload, destination) != RECEIVE_SIZE) {
                    throw new IllegalStateException("Incomplete diagnostic receive fixture send");
                }
                // Drain socket buffers incrementally so the batch does not depend on OS UDP queue capacity.
                instance.endpoint.channelReadLoop();
                if (receivePath == ReceivePath.PACKET) {
                    expectedSequenceSum += sequence;
                }
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            int expectedBytes = RECEIVE_BATCH * RECEIVE_SIZE;
            while (instance.endpoint.buffered() != expectedBytes) {
                if (instance.endpoint.buffered() > expectedBytes || System.nanoTime() >= deadline) {
                    throw new IllegalStateException("Expected " + expectedBytes + " queued receive bytes, got "
                                                            + instance.endpoint.buffered());
                }
                instance.endpoint.channelReadLoop();
                Thread.onSpinWait();
            }
            if (receivePath == ReceivePath.PACKET) {
                expectedReceived += RECEIVE_BATCH;
            } else {
                expectedResets += RECEIVE_BATCH;
            }
            dispatch = executor.pending;
            executor.pending = null;
            if (dispatch == null) {
                throw new IllegalStateException("Endpoint did not schedule the receive batch");
            }
        }

        /**
         * Checks every callback and the packet payload checksum after measurement.
         */
        @TearDown(Level.Invocation)
        public void finishBatch() {
            receiver.checkFailure();
            if (receiver.received != expectedReceived || receiver.resets != expectedResets
                    || receiver.sequenceSum != expectedSequenceSum || instance.endpoint.buffered() != 0
                    || executor.pending != null) {
                throw new IllegalStateException("Diagnostic receive batch lost, duplicated, or misrouted a datagram");
            }
            if (receivePath == ReceivePath.PACKET
                    && (!source.equals(receiver.lastSource)
                            || receiver.lastType != QuicPacket.HeadersType.SHORT
                            || !connectionId.asReadOnlyBuffer().equals(receiver.lastId)
                            || receiver.lastPacket.position() != 0 || receiver.lastPacket.remaining() != RECEIVE_SIZE
                            || receiver.lastPacket.get(0) != 0x40)) {
                throw new IllegalStateException("Diagnostic receive callback changed packet content or metadata");
            }
            dispatch = null;
        }

        /**
         * Closes the endpoint and sender; this fixture creates no background threads.
         *
         * @throws IOException if the sender cannot be closed
         */
        @TearDown(Level.Trial)
        public void tearDown() throws IOException {
            try {
                if (instance != null) {
                    instance.close();
                }
            } finally {
                if (sender != null) {
                    sender.close();
                }
                dispatch = null;
                executor.pending = null;
            }
        }

        int buffered() {
            return instance.endpoint.buffered();
        }

        boolean closed() {
            return !instance.endpoint.channel().isOpen() && !sender.isOpen();
        }
    }

    /**
     * Worker-thread bytes and operation counts; divide the event totals to obtain bytes per operation.
     * Instrumented method timing includes management-counter overhead and is not latency evidence.
     */
    @AuxCounters(AuxCounters.Type.EVENTS)
    @State(Scope.Thread)
    public static class AllocationCounters {
        /**
         * Bytes allocated by measured operations on the JMH worker.
         */
        public long allocatedBytes;

        /**
         * Number of measured operations, counting each queued datagram separately.
         */
        public long operations;

        private ThreadMXBean bean;
        private long threadId;

        /**
         * Enables the supported JVM allocation counter on the actual benchmark worker.
         */
        @Setup(Level.Trial)
        public void setUp() {
            if (!(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean extended)
                    || !extended.isThreadAllocatedMemorySupported()) {
                throw new IllegalStateException("Worker-thread allocation measurement is unavailable");
            }
            bean = extended;
            if (!bean.isThreadAllocatedMemoryEnabled()) {
                bean.setThreadAllocatedMemoryEnabled(true);
            }
            threadId = Thread.currentThread().threadId();
            if (before() < 0) {
                throw new IllegalStateException("Worker-thread allocation counter is unavailable");
            }
        }

        private long before() {
            return bean.getThreadAllocatedBytes(threadId);
        }

        private void record(long before, int count) {
            long allocated = bean.getThreadAllocatedBytes(threadId) - before;
            if (before < 0 || allocated < 0) {
                throw new IllegalStateException("Worker-thread allocation counter moved backwards");
            }
            allocatedBytes += allocated;
            operations += count;
        }
    }

    private static final class CaptureHandler extends Handler {
        private final AtomicLong messages = new AtomicLong();
        private volatile String lastMessage;

        @Override
        public void publish(LogRecord record) {
            messages.incrementAndGet();
            lastMessage = record.getMessage();
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    private static final class LoggerSetting {
        private final Logger logger;
        private final java.util.logging.Level level;
        private final Handler[] handlers;
        private final boolean parentHandlers;

        private LoggerSetting(String name) {
            logger = Logger.getLogger(name);
            level = logger.getLevel();
            handlers = logger.getHandlers();
            parentHandlers = logger.getUseParentHandlers();
        }

        private void install(LogLevel level, Handler handler) {
            for (Handler existing : handlers) {
                logger.removeHandler(existing);
            }
            logger.setUseParentHandlers(false);
            logger.addHandler(handler);
            logger.setLevel(switch (level) {
                case OFF -> java.util.logging.Level.OFF;
                case DEBUG -> java.util.logging.Level.FINE;
                case TRACE -> java.util.logging.Level.FINER;
            });
        }

        private void restore(Handler handler) {
            logger.removeHandler(handler);
            for (Handler existing : handlers) {
                logger.addHandler(existing);
            }
            logger.setUseParentHandlers(parentHandlers);
            logger.setLevel(level);
        }
    }

    private static final class DeadlineEmitter implements PacketEmitter {
        private final QuicTimerQueue timer = new QuicTimerQueue(() -> { }, () -> LOG_TAG);

        @Override
        public QuicTimerQueue timer() {
            return timer;
        }

        @Override
        public boolean retransmit(PacketSpace space, QuicPacket packet, int attempts) {
            throw new IllegalStateException("Deadline probe cannot retransmit packets");
        }

        @Override
        public long emitAckPacket(PacketSpace space, AckFrame ack, boolean sendPing) {
            throw new IllegalStateException("Deadline probe cannot emit packets");
        }

        @Override
        public void acknowledged(QuicPacket packet) {
        }

        @Override
        public boolean sendData(PacketNumberSpace space) {
            return false;
        }

        @Override
        public Executor executor() {
            return Runnable::run;
        }

        @Override
        public void checkAbort(PacketNumberSpace space) {
        }

        @Override
        public boolean isOpen() {
            return true;
        }
    }

    private static final class BenchmarkInstance implements QuicInstance, AutoCloseable {
        private final QuicTimerQueue timer = new QuicTimerQueue(() -> { }, () -> LOG_TAG);
        private final QuicTLSContext tlsContext = QuicTLSContext.create(Tls.builder().build());
        private final QuicRuntimeConfig config;
        private final Executor executor;
        private final QuicEndpoint endpoint;

        private BenchmarkInstance(boolean bufferPool, boolean sendAsync, Executor executor) {
            this(bufferPool, sendAsync, executor, false);
        }

        private BenchmarkInstance(boolean bufferPool, boolean sendAsync, Executor executor, boolean selectable) {
            QuicRuntimeConfig defaults = QuicRuntimeConfig.create(QuicConfig.create());
            QuicRuntimeConfig.Endpoint endpointDefaults = defaults.endpoint();
            ChannelType channelType = selectable ? ChannelType.NON_BLOCKING_WITH_SELECTOR : endpointDefaults.channelType();
            QuicSelectorThreading threading = selectable ? QuicSelectorThreading.PLATFORM
                    : endpointDefaults.selectorThreading();
            config = new QuicRuntimeConfig(defaults.userConfig(),
                                           new QuicRuntimeConfig.Endpoint(channelType,
                                                                          threading,
                                                                          endpointDefaults.pollerUsePlatformThreads(),
                                                                          endpointDefaults.maxEndpoints(),
                                                                          sendAsync,
                                                                          endpointDefaults.maxBufferedHigh(),
                                                                          endpointDefaults.maxBufferedLow(),
                                                                          bufferPool,
                                                                          DATAGRAM_SIZE),
                                           defaults.recovery(),
                                           defaults.transportParameters(),
                                           defaults.confidentialityLimits());
            this.executor = executor;
            var factory = QuicEndpoint.QuicEndpointFactory.create();
            var bindAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
            endpoint = selectable
                    ? factory.createSelectableEndpoint(this, config, LOG_TAG, bindAddress, timer)
                    : factory.createVirtualThreadedEndpoint(this, config, LOG_TAG, bindAddress, timer);
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
        public void unmatchedQuicPacket(SocketAddress source, QuicPacket.HeadersType type, ByteBuffer buffer) {
            throw new IllegalStateException("Diagnostic endpoint does not read packets");
        }

        @Override
        public void runtimeFailed(Throwable failure) {
            throw new IllegalStateException("Diagnostic endpoint failed", failure);
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
        public String instanceId() {
            return LOG_TAG;
        }

        @Override
        public QuicTLSContext quicTlsContext() {
            return tlsContext;
        }

        @Override
        public QuicConfig quicConfig() {
            return config.userConfig();
        }

        @Override
        public void close() {
            try {
                endpoint.close();
            } finally {
                timer.stop();
            }
        }
    }

    private static class CountingReceiver implements QuicPacketReceiver {
        protected long sent;
        protected long dropped;

        private volatile Throwable failure;

        @Override
        public List<QuicConnectionId> connectionIds() {
            return List.of();
        }

        @Override
        public List<PeerResetToken> activeResetTokens() {
            return List.of();
        }

        @Override
        public void processIncoming(SocketAddress source, ByteBuffer id, QuicPacket.HeadersType type, ByteBuffer buffer) {
            fail(new IllegalStateException("Diagnostic receiver does not read packets"));
        }

        @Override
        public void onWriteError(Throwable failure) {
            fail(failure);
        }

        @Override
        public void processStatelessReset() {
        }

        @Override
        public void shutdown() {
        }

        @Override
        public void datagramSent(QuicDatagram datagram) {
            sent++;
            datagram.payload().clear();
        }

        @Override
        public void datagramDiscarded(QuicDatagram datagram) {
            fail(new IllegalStateException("Diagnostic datagram was discarded"));
        }

        @Override
        public void datagramDropped(QuicDatagram datagram) {
            dropped++;
            fail(new IllegalStateException("Diagnostic datagram was dropped"));
        }

        void fail(Throwable failure) {
            this.failure = failure;
        }

        void checkFailure() {
            Throwable current = failure;
            if (current != null) {
                throw new IllegalStateException("Diagnostic writer failed", current);
            }
        }
    }

    private static final class DispatchExecutor implements Executor {
        private Runnable pending;

        @Override
        public void execute(Runnable command) {
            if (pending != null) {
                throw new IllegalStateException("Receive fixture scheduled more than one pending task");
            }
            pending = command;
        }
    }

    private static class ReceiveReceiver extends CountingReceiver {
        private long received;
        private long resets;
        private long sequenceSum;
        private SocketAddress lastSource;
        private QuicPacket.HeadersType lastType;
        private ByteBuffer lastId;
        private ByteBuffer lastPacket;

        @Override
        public void processIncoming(SocketAddress source,
                                    ByteBuffer id,
                                    QuicPacket.HeadersType type,
                                    ByteBuffer buffer) {
            received++;
            sequenceSum += buffer.getLong(RECEIVE_SEQUENCE_OFFSET);
            lastSource = source;
            lastType = type;
            lastId = id;
            lastPacket = buffer;
        }

        @Override
        public void processStatelessReset() {
            resets++;
        }
    }

    private static final class SocketReceiveReceiver extends ReceiveReceiver implements SocketContext {
        private final String socketId;
        private final String childSocketId;

        private SocketReceiveReceiver(String socketId, String childSocketId) {
            this.socketId = socketId;
            this.childSocketId = childSocketId;
        }

        @Override
        public PeerInfo remotePeer() {
            throw new UnsupportedOperationException("Receive dispatch does not query peer TLS information");
        }

        @Override
        public PeerInfo localPeer() {
            throw new UnsupportedOperationException("Receive dispatch does not query peer TLS information");
        }

        @Override
        public boolean isSecure() {
            return true;
        }

        @Override
        public String socketId() {
            return socketId;
        }

        @Override
        public String childSocketId() {
            return childSocketId;
        }
    }

    private static final class BatchReceiver extends CountingReceiver {
        private final Semaphore resume = new Semaphore(0);
        private final Semaphore parked = new Semaphore(0);
        private volatile boolean closing;
        private int remaining = 1;

        @Override
        public void datagramSent(QuicDatagram datagram) {
            super.datagramSent(datagram);
            if (--remaining == 0 && !closing) {
                parked.release();
                try {
                    if (!resume.tryAcquire(30, TimeUnit.SECONDS)) {
                        fail(new IllegalStateException("Timed out waiting for the next diagnostic batch"));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    fail(e);
                }
            }
        }

        @Override
        public void datagramDropped(QuicDatagram datagram) {
            if (closing) {
                dropped++;
            } else {
                super.datagramDropped(datagram);
            }
        }

        @Override
        void fail(Throwable failure) {
            super.fail(failure);
            parked.release();
        }

        private void awaitParked() throws InterruptedException {
            if (!parked.tryAcquire(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the diagnostic writer");
            }
            checkFailure();
        }
    }
}
