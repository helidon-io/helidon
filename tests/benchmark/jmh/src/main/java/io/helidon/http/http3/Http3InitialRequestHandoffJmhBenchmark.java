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

package io.helidon.http.http3;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.PeerInfo;
import io.helidon.common.socket.SocketContext;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.WritableHeaders;
import io.helidon.quic.SequentialScheduler;
import io.helidon.quic.stream.QuicReceiverStream;
import io.helidon.quic.stream.QuicStream;
import io.helidon.quic.stream.QuicStreamReader;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Isolates initial HTTP/3 request handoff with and without an empty transport poll.
 *
 * <p>The publish-before-data cells model publishing a newly-created QUIC stream to HTTP/3 before applying the STREAM
 * frame which created it. The virtual request worker performs one empty poll and parks until the frame is delivered.
 * The data-before-publish cells apply the same pre-encoded HEADERS frame before publishing the stream, so the virtual
 * request worker can decode it on its first poll. Both paths use the real {@link Http3MessageReader}; only the QUIC
 * receiver and its reader are deterministic in-memory fixtures.</p>
 *
 * <p>This is a mechanism benchmark. End-to-end effects still need confirmation with the HTTP/3 server benchmark.</p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class Http3InitialRequestHandoffJmhBenchmark {
    private static final int CONCURRENT_WORKERS = 8;

    /**
     * Measures one request worker published before its initial HEADERS data is available.
     *
     * @param state per-JMH-worker handoff state
     * @return decoded request-head checksum
     */
    @Benchmark
    @Threads(1)
    public int publishBeforeData(HandoffState state) {
        return state.run(HandoffOrder.PUBLISH_BEFORE_DATA);
    }

    /**
     * Measures concurrent request workers published before their initial HEADERS data is available.
     *
     * @param state per-JMH-worker handoff state
     * @return decoded request-head checksum
     */
    @Benchmark
    @Threads(CONCURRENT_WORKERS)
    public int publishBeforeDataConcurrent(HandoffState state) {
        return state.run(HandoffOrder.PUBLISH_BEFORE_DATA);
    }

    /**
     * Measures one request worker published after its initial HEADERS data is available.
     *
     * @param state per-JMH-worker handoff state
     * @return decoded request-head checksum
     */
    @Benchmark
    @Threads(1)
    public int dataBeforePublish(HandoffState state) {
        return state.run(HandoffOrder.DATA_BEFORE_PUBLISH);
    }

    /**
     * Measures concurrent request workers published after their initial HEADERS data is available.
     *
     * @param state per-JMH-worker handoff state
     * @return decoded request-head checksum
     */
    @Benchmark
    @Threads(CONCURRENT_WORKERS)
    public int dataBeforePublishConcurrent(HandoffState state) {
        return state.run(HandoffOrder.DATA_BEFORE_PUBLISH);
    }

    /**
     * Per-JMH-worker request bytes, QPACK context, and invocation lifecycle.
     */
    @State(Scope.Thread)
    public static class HandoffState {
        private static final int MAX_HEADERS_SIZE = 16_384;
        private static final URI REQUEST_URI = URI.create("https://localhost/initial-handoff");
        private static final HeaderName X_JMH = HeaderNames.create("x-jmh");
        private static final String X_JMH_VALUE = "initial-handoff";
        private static final Http3FrameListener NO_OP_FRAME_LISTENER = Http3FrameListener.create(List.of());

        private final AtomicReference<Throwable> qpackFailure = new AtomicReference<>();
        private Http3QpackContext encoderContext;
        private Http3QpackContext decoderContext;
        private byte[] encodedRequest;
        private long nextStreamId;
        private BenchmarkReceiverStream stream;
        private RequestOperation operation;
        private HandoffOrder order;

        /**
         * Pre-encodes the request HEADERS frame and creates the connection-level decoder context.
         */
        @Setup(Level.Trial)
        public void setUpTrial() {
            encoderContext = Http3QpackContext.create(0,
                                                      0,
                                                      MAX_HEADERS_SIZE,
                                                      failure -> qpackFailure.compareAndSet(null, failure));
            decoderContext = Http3QpackContext.create(0,
                                                      0,
                                                      MAX_HEADERS_SIZE,
                                                      failure -> qpackFailure.compareAndSet(null, failure));
            encodedRequest = Http3Protocol.encodeRequestHeaders(encoderContext,
                                                                0,
                                                                REQUEST_URI,
                                                                "GET",
                                                                WritableHeaders.create()
                                                                        .add(HeaderValues.create(X_JMH,
                                                                                                 X_JMH_VALUE)));
        }

        /**
         * Creates an empty receiver stream for one measured handoff.
         */
        @Setup(Level.Invocation)
        public void setUpInvocation() {
            if (stream != null || operation != null || order != null) {
                throw new IllegalStateException("HTTP/3 request handoff retained an earlier invocation");
            }
            Throwable failure = qpackFailure.get();
            if (failure != null) {
                throw new IllegalStateException("HTTP/3 request handoff QPACK context failed", failure);
            }
            stream = new BenchmarkReceiverStream(nextStreamId);
            nextStreamId += 4;
        }

        /**
         * Verifies that each invocation decoded exactly one request and retained no worker, signal, or input buffer.
         */
        @TearDown(Level.Invocation)
        public void tearDownInvocation() {
            BenchmarkReceiverStream currentStream = stream;
            RequestOperation currentOperation = operation;
            HandoffOrder currentOrder = order;
            stream = null;
            operation = null;
            order = null;
            if (currentStream == null || currentOperation == null || currentOrder == null) {
                throw new IllegalStateException("HTTP/3 request handoff invocation was not completed");
            }
            currentOperation.stopIfRunning();
            currentOperation.validateResult();
            currentStream.validateDrained(currentOrder);
        }

        /**
         * Closes the connection-level QPACK contexts after all request streams have been cancelled by reader close.
         */
        @TearDown(Level.Trial)
        public void tearDownTrial() {
            Throwable failure = qpackFailure.get();
            encoderContext.close(new IllegalStateException("HTTP/3 request handoff encoder benchmark complete"));
            decoderContext.close(new IllegalStateException("HTTP/3 request handoff decoder benchmark complete"));
            if (failure != null) {
                throw new IllegalStateException("HTTP/3 request handoff QPACK context failed", failure);
            }
        }

        private int run(HandoffOrder requestedOrder) {
            if (order != null || operation != null) {
                throw new IllegalStateException("HTTP/3 request handoff invocation was already started");
            }
            order = requestedOrder;
            if (requestedOrder == HandoffOrder.DATA_BEFORE_PUBLISH) {
                stream.receive(encodedRequest);
            }
            Http3MessageReader messageReader = Http3MessageReader.request(stream,
                                                                          decoderContext,
                                                                          BENCHMARK_SOCKET_CONTEXT,
                                                                          MAX_HEADERS_SIZE,
                                                                          true,
                                                                          NO_OP_FRAME_LISTENER);
            operation = new RequestOperation(messageReader, stream.streamId());
            operation.start();
            if (requestedOrder == HandoffOrder.PUBLISH_BEFORE_DATA) {
                stream.awaitInitialEmptyPoll();
                operation.awaitWorkerParked();
                stream.receive(encodedRequest);
            }
            return operation.awaitResult();
        }
    }

    private enum HandoffOrder {
        PUBLISH_BEFORE_DATA,
        DATA_BEFORE_PUBLISH
    }

    private static final class RequestOperation {
        private static final long AWAIT_MILLIS = TimeUnit.SECONDS.toMillis(5);

        private final Http3MessageReader reader;
        private final long streamId;
        private final AtomicReference<Http3Protocol.DecodedRequestHead> result = new AtomicReference<>();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private Thread worker;

        private RequestOperation(Http3MessageReader reader, long streamId) {
            this.reader = reader;
            this.streamId = streamId;
        }

        private void start() {
            worker = Thread.ofVirtual()
                    .name("http3-initial-request-handoff-jmh-" + streamId)
                    .unstarted(() -> {
                        try {
                            result.set(reader.readRequestHead());
                        } catch (Throwable t) {
                            failure.compareAndSet(null, t);
                        } finally {
                            try {
                                reader.close();
                            } catch (Throwable t) {
                                failure.compareAndSet(null, t);
                            }
                        }
                    });
            worker.start();
        }

        private void awaitWorkerParked() {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(AWAIT_MILLIS);
            while (worker.isAlive()) {
                if (worker.getState() == Thread.State.WAITING) {
                    return;
                }
                if (System.nanoTime() >= deadline) {
                    throw new IllegalStateException("HTTP/3 request worker did not park after its empty poll");
                }
                Thread.onSpinWait();
            }
            throw workerFailure("HTTP/3 request worker stopped before its initial data arrived");
        }

        private int awaitResult() {
            joinWorker();
            validateResult();
            Http3Protocol.DecodedRequestHead head = result.get();
            return head.method().hashCode() ^ head.path().orElseThrow().hashCode();
        }

        private void validateResult() {
            Throwable throwable = failure.get();
            if (throwable != null) {
                throw new IllegalStateException("HTTP/3 request worker failed", throwable);
            }
            Http3Protocol.DecodedRequestHead head = result.get();
            if (head == null) {
                throw new IllegalStateException("HTTP/3 request worker did not decode a request head");
            }
            if (!"GET".equals(head.method())
                    || !Optional.of("https").equals(head.scheme())
                    || !"localhost".equals(head.authority())
                    || !Optional.of("/initial-handoff").equals(head.path())
                    || !HandoffState.X_JMH_VALUE.equals(head.headers().first(HandoffState.X_JMH).orElse(null))) {
                throw new IllegalStateException("HTTP/3 request worker decoded an unexpected request head");
            }
            if (worker.isAlive()) {
                throw new IllegalStateException("HTTP/3 request worker leaked after request-head decode");
            }
        }

        private void stopIfRunning() {
            if (worker != null && worker.isAlive()) {
                worker.interrupt();
                joinWorker();
                throw new IllegalStateException("HTTP/3 request worker remained active after the measured invocation");
            }
        }

        private void joinWorker() {
            try {
                worker.join(AWAIT_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while awaiting the HTTP/3 request worker", e);
            }
            if (worker.isAlive()) {
                worker.interrupt();
                throw new IllegalStateException("Timed out awaiting the HTTP/3 request worker");
            }
        }

        private IllegalStateException workerFailure(String message) {
            Throwable throwable = failure.get();
            return throwable == null ? new IllegalStateException(message) : new IllegalStateException(message, throwable);
        }
    }

    private static final class BenchmarkReceiverStream implements QuicReceiverStream {
        private final long streamId;
        private final ArrayBlockingQueue<BufferData> buffers = new ArrayBlockingQueue<>(1);
        private final CountDownLatch initialEmptyPoll = new CountDownLatch(1);
        private final AtomicInteger emptyPolls = new AtomicInteger();
        private final AtomicInteger signalsStarted = new AtomicInteger();
        private final AtomicInteger signalsCompleted = new AtomicInteger();
        private BenchmarkStreamReader reader;
        private int dataReceived;
        private boolean disconnected;

        private BenchmarkReceiverStream(long streamId) {
            this.streamId = streamId;
        }

        private void receive(byte[] data) {
            if (dataReceived != 0 || !buffers.offer(BufferData.createReadOnly(data, 0, data.length))) {
                throw new IllegalStateException("HTTP/3 request handoff received duplicate initial data");
            }
            dataReceived = data.length;
            BenchmarkStreamReader currentReader = reader;
            if (currentReader != null && currentReader.started()) {
                currentReader.signalDataAvailable();
            }
        }

        private void awaitInitialEmptyPoll() {
            try {
                if (!initialEmptyPoll.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("HTTP/3 request worker did not perform its initial empty poll");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while awaiting the initial empty poll", e);
            }
        }

        private void validateDrained(HandoffOrder order) {
            int expectedEmptyPolls = order == HandoffOrder.PUBLISH_BEFORE_DATA ? 1 : 0;
            if (emptyPolls.get() != expectedEmptyPolls) {
                throw new IllegalStateException("Unexpected initial empty polls: expected=" + expectedEmptyPolls
                                                        + ", actual=" + emptyPolls.get());
            }
            if (signalsStarted.get() != 1 || signalsCompleted.get() != 1) {
                throw new IllegalStateException("Unexpected data signals: started=" + signalsStarted.get()
                                                        + ", completed=" + signalsCompleted.get());
            }
            if (!buffers.isEmpty()) {
                throw new IllegalStateException("HTTP/3 request handoff retained an input buffer");
            }
            if (!disconnected || reader == null || reader.connected()) {
                throw new IllegalStateException("HTTP/3 request handoff retained a connected stream reader");
            }
        }

        @Override
        public QuicReceiverStream.ReceivingStreamState receivingState() {
            return disconnected ? ReceivingStreamState.DATA_READ : ReceivingStreamState.RECV;
        }

        @Override
        public QuicStreamReader connectReader(SequentialScheduler scheduler) {
            if (reader != null && reader.connected()) {
                throw new IllegalStateException("Reader already connected");
            }
            reader = new BenchmarkStreamReader(this, scheduler);
            return reader;
        }

        @Override
        public void disconnectReader(QuicStreamReader reader) {
            if (this.reader != reader || !this.reader.connected()) {
                throw new IllegalStateException("Reader is not connected");
            }
            disconnected = true;
            this.reader.disconnect();
        }

        @Override
        public void requestStopSending(long errorCode) {
            // No peer flow-control action is needed by the in-memory benchmark stream.
        }

        @Override
        public long dataReceived() {
            return dataReceived;
        }

        @Override
        public long maxStreamData() {
            return dataReceived;
        }

        @Override
        public long rcvErrorCode() {
            return -1;
        }

        @Override
        public long streamId() {
            return streamId;
        }

        @Override
        public StreamMode mode() {
            return StreamMode.READ_WRITE;
        }

        @Override
        public boolean isClientInitiated() {
            return true;
        }

        @Override
        public boolean isServerInitiated() {
            return false;
        }

        @Override
        public boolean isBidirectional() {
            return true;
        }

        @Override
        public boolean isLocalInitiated() {
            return false;
        }

        @Override
        public boolean isRemoteInitiated() {
            return true;
        }

        @Override
        public int type() {
            return 0x00;
        }

        @Override
        public QuicStream.StreamState state() {
            return receivingState();
        }
    }

    private static final class BenchmarkStreamReader extends QuicStreamReader {
        private final BenchmarkReceiverStream stream;
        private final SequentialScheduler scheduler;
        private boolean connected = true;
        private boolean started;

        private BenchmarkStreamReader(BenchmarkReceiverStream stream, SequentialScheduler scheduler) {
            super(scheduler);
            this.stream = stream;
            this.scheduler = scheduler;
        }

        @Override
        public QuicReceiverStream.ReceivingStreamState receivingState() {
            ensureConnected();
            return stream.receivingState();
        }

        @Override
        public Optional<BufferData> poll() {
            ensureConnected();
            if (!started) {
                return Optional.empty();
            }
            BufferData buffer = stream.buffers.poll();
            if (buffer == null) {
                stream.emptyPolls.incrementAndGet();
                stream.initialEmptyPoll.countDown();
                return Optional.empty();
            }
            return Optional.of(buffer);
        }

        @Override
        public Optional<BufferData> peek() {
            ensureConnected();
            return started ? Optional.ofNullable(stream.buffers.peek()) : Optional.empty();
        }

        @Override
        public Optional<QuicReceiverStream> stream() {
            return connected ? Optional.of(stream) : Optional.empty();
        }

        @Override
        public boolean connected() {
            return connected;
        }

        @Override
        public boolean started() {
            return started;
        }

        @Override
        public void start() {
            ensureConnected();
            started = true;
            if (!stream.buffers.isEmpty()) {
                signalDataAvailable();
            }
        }

        private void signalDataAvailable() {
            stream.signalsStarted.incrementAndGet();
            try {
                scheduler.runOrSchedule();
            } finally {
                stream.signalsCompleted.incrementAndGet();
            }
        }

        private void disconnect() {
            connected = false;
        }

        private void ensureConnected() {
            if (!connected) {
                throw new IllegalStateException("Reader is disconnected");
            }
        }
    }

    private static final PeerInfo BENCHMARK_PEER = new PeerInfo() {
        private final SocketAddress address = new InetSocketAddress("127.0.0.1", 443);

        @Override
        public SocketAddress address() {
            return address;
        }

        @Override
        public String host() {
            return "localhost";
        }

        @Override
        public int port() {
            return 443;
        }

        @Override
        public Optional<Principal> tlsPrincipal() {
            return Optional.empty();
        }

        @Override
        public Optional<Certificate[]> tlsCertificates() {
            return Optional.empty();
        }
    };

    private static final SocketContext BENCHMARK_SOCKET_CONTEXT = new SocketContext() {
        @Override
        public PeerInfo remotePeer() {
            return BENCHMARK_PEER;
        }

        @Override
        public PeerInfo localPeer() {
            return BENCHMARK_PEER;
        }

        @Override
        public boolean isSecure() {
            return true;
        }

        @Override
        public String socketId() {
            return "http3-initial-request-handoff-jmh";
        }

        @Override
        public String childSocketId() {
            return "http3-initial-request-handoff-jmh-0";
        }
    };
}
