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

package io.helidon.webserver.benchmark.jmh;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.PeerInfo;
import io.helidon.common.socket.SocketContext;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2ConnectionWriter;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameListener;
import io.helidon.http.http2.Http2FrameType;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2Headers;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

public class Http2ConnectionWriterJmhTest {
    private static final int MAX_FRAME_SIZE = 16_384;
    private static final int CONCURRENT_THREADS = 8;
    private static final String FRAGMENTED_HEADER_VALUE = "~".repeat(18_000);
    private static final byte[] PARTIAL_RESPONSE_BYTES = {1, 2};
    private static final PeerInfo PEER_INFO = new BenchmarkPeerInfo();
    private static final Runnable NO_OP = () -> { };

    @Benchmark
    @Threads(1)
    public int fundedWindow(ConnectionState connection, FrameState frame) {
        return connection.write(frame, frame.fundedWindow);
    }

    @Benchmark
    @Threads(CONCURRENT_THREADS)
    public int fundedWindowConcurrent(ConnectionState connection, FrameState frame) {
        return connection.write(frame, frame.fundedWindow);
    }

    @Benchmark
    @Threads(1)
    public int exhaustedWindow(ConnectionState connection, FrameState frame) {
        return connection.write(frame, frame.exhaustedWindow);
    }

    @Benchmark
    @Threads(CONCURRENT_THREADS)
    public int exhaustedWindowConcurrent(ConnectionState connection, FrameState frame) {
        return connection.write(frame, frame.exhaustedWindow);
    }

    @Benchmark
    @Threads(1)
    public int partialWindow(ConnectionState connection, FrameState frame) {
        return connection.write(frame, frame.partialData, frame.partialWindow);
    }

    @Benchmark
    @Threads(CONCURRENT_THREADS)
    public int partialWindowConcurrent(ConnectionState connection, FrameState frame) {
        return connection.write(frame, frame.partialData, frame.partialWindow);
    }

    @Benchmark
    @Threads(1)
    public int fragmentedHeaders(ConnectionState connection, FrameState frame) {
        return connection.write(frame, frame.fragmentedHeaders, frame.fundedWindow);
    }

    @Benchmark
    @Threads(CONCURRENT_THREADS)
    public int fragmentedHeadersConcurrent(ConnectionState connection, FrameState frame) {
        return connection.write(frame, frame.fragmentedHeaders, frame.fundedWindow);
    }

    /**
     * Terminal write gate benchmark mode.
     */
    public enum GateMode {
        /** No frame listener. */
        NONE,
        /** Frame listener without locking. */
        OBSERVE,
        /** Frame listener with terminal publication tracking. */
        GATE
    }

    @State(Scope.Benchmark)
    public static class ConnectionState {
        @Param("GATE")
        public GateMode terminalWriteGate;

        private BenchmarkDataWriter dataWriter;
        private Runnable terminalWriteComplete;
        private Http2ConnectionWriter writer;

        @Setup
        public void setup() {
            dataWriter = new BenchmarkDataWriter();
            if (terminalWriteGate == GateMode.NONE) {
                terminalWriteComplete = NO_OP;
                writer = new Http2ConnectionWriter(new BenchmarkSocketContext(), dataWriter, List.of());
            } else {
                BenchmarkTerminalWriteGate gate = new BenchmarkTerminalWriteGate(terminalWriteGate == GateMode.GATE);
                terminalWriteComplete = gate;
                writer = new Http2ConnectionWriter(new BenchmarkSocketContext(), dataWriter, List.of(gate));
            }
        }

        private int write(FrameState frame, FlowControl.Outbound flowControl) {
            return write(frame, frame.headers, frame.data, flowControl);
        }

        private int write(FrameState frame, Http2FrameData data, FlowControl.Outbound flowControl) {
            return write(frame, frame.headers, data, flowControl);
        }

        private int write(FrameState frame, Http2Headers headers, FlowControl.Outbound flowControl) {
            return write(frame, headers, frame.data, flowControl);
        }

        private int write(FrameState frame,
                          Http2Headers headers,
                          Http2FrameData data,
                          FlowControl.Outbound flowControl) {
            try {
                return writer.writeHeaders(headers,
                                           1,
                                           Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                           data,
                                           flowControl,
                                           terminalWriteComplete);
            } finally {
                frame.reset(data, flowControl);
            }
        }
    }

    @State(Scope.Thread)
    public static class FrameState {
        @Param("1")
        public int payloadSize;

        @Param("false")
        public boolean readOnlyPayload;

        private final BenchmarkFlowControl partialWindow = new BenchmarkFlowControl(1, 1);
        private BenchmarkFlowControl fundedWindow;
        private BenchmarkFlowControl exhaustedWindow;
        private BenchmarkDataWriter dataWriter;
        private Http2FrameData data;
        private Http2Headers fragmentedHeaders;
        private Http2Headers headers;
        private Http2FrameData partialData;

        @Setup
        public void setup(ConnectionState connection, Blackhole blackhole) {
            if (payloadSize < 1 || payloadSize > MAX_FRAME_SIZE) {
                throw new IllegalArgumentException("Payload size must be between 1 and " + MAX_FRAME_SIZE);
            }
            byte[] responseBytes = new byte[payloadSize];
            for (int i = 0; i < responseBytes.length; i++) {
                responseBytes[i] = (byte) (i * 31 + 1);
            }
            BufferData response = readOnlyPayload
                    ? BufferData.createReadOnly(responseBytes, 0, responseBytes.length)
                    : BufferData.create(responseBytes);
            fundedWindow = new BenchmarkFlowControl(payloadSize, payloadSize);
            exhaustedWindow = new BenchmarkFlowControl(0, payloadSize);
            dataWriter = connection.dataWriter;
            dataWriter.register(blackhole);
            headers = Http2Headers.create(WritableHeaders.create())
                    .status(Status.OK_200);
            WritableHeaders<?> writableHeaders = WritableHeaders.create();
            writableHeaders.set(HeaderNames.create("x-large-header"), FRAGMENTED_HEADER_VALUE);
            fragmentedHeaders = Http2Headers.create(writableHeaders)
                    .status(Status.OK_200);
            data = new Http2FrameData(Http2FrameHeader.create(responseBytes.length,
                                                               Http2FrameTypes.DATA,
                                                               Http2Flag.DataFlags.create(Http2Flag.END_OF_STREAM),
                                                               1),
                                      response);
            partialData = new Http2FrameData(Http2FrameHeader.create(PARTIAL_RESPONSE_BYTES.length,
                                                                      Http2FrameTypes.DATA,
                                                                      Http2Flag.DataFlags.create(
                                                                              Http2Flag.END_OF_STREAM),
                                                                      1),
                                             BufferData.create(PARTIAL_RESPONSE_BYTES));
        }

        @TearDown
        public void tearDown() {
            dataWriter.unregister();
        }

        private void reset(Http2FrameData data, FlowControl.Outbound flowControl) {
            data.data().rewind();
            ((BenchmarkFlowControl) flowControl).reset();
        }
    }

    private static final class BenchmarkFlowControl implements FlowControl.Outbound {
        private final int initialWindowSize;
        private final int updatedWindowSize;
        private boolean windowUpdated;
        private int remainingWindowSize;

        private BenchmarkFlowControl(int initialWindowSize, int updatedWindowSize) {
            this.initialWindowSize = initialWindowSize;
            this.updatedWindowSize = updatedWindowSize;
            this.remainingWindowSize = initialWindowSize;
        }

        @Override
        public void decrementWindowSize(int decrement) {
            remainingWindowSize -= decrement;
            if (remainingWindowSize < 0) {
                throw new IllegalStateException("Flow-control window exhausted");
            }
        }

        @Override
        public void resetStreamWindowSize(int size) {
            remainingWindowSize = size;
        }

        @Override
        public int getRemainingWindowSize() {
            return remainingWindowSize;
        }

        @Override
        public long incrementStreamWindowSize(int increment) {
            remainingWindowSize += increment;
            return remainingWindowSize;
        }

        @Override
        public Http2FrameData[] cut(Http2FrameData frame) {
            return frame.cut(remainingWindowSize);
        }

        @Override
        public void blockTillUpdate() {
            if (windowUpdated) {
                throw new IllegalStateException("Benchmark flow-control update already applied");
            }
            // Model one WINDOW_UPDATE without including an unbounded external wait in the benchmark.
            windowUpdated = true;
            remainingWindowSize = updatedWindowSize;
        }

        @Override
        public int maxFrameSize() {
            return MAX_FRAME_SIZE;
        }

        private void reset() {
            windowUpdated = false;
            remainingWindowSize = initialWindowSize;
        }
    }

    private static final class BenchmarkDataWriter implements DataWriter {
        private final ThreadLocal<StagingSink> sinks = new ThreadLocal<>();

        @Override
        public void write(BufferData... buffers) {
            StagingSink sink = sink();
            for (BufferData buffer : buffers) {
                sink.consume(buffer);
            }
        }

        @Override
        public void write(BufferData buffer) {
            sink().consume(buffer);
        }

        @Override
        public void writeNow(BufferData... buffers) {
            write(buffers);
        }

        @Override
        public void writeNow(BufferData buffer) {
            write(buffer);
        }

        private void register(Blackhole blackhole) {
            sinks.set(new StagingSink(blackhole));
        }

        private void unregister() {
            sinks.remove();
        }

        private StagingSink sink() {
            StagingSink sink = sinks.get();
            if (sink == null) {
                throw new IllegalStateException("No staging sink registered for benchmark thread");
            }
            return sink;
        }
    }

    private static final class StagingSink {
        private final ByteBuffer staging = ByteBuffer.allocate(8 * 1024);
        private final Blackhole blackhole;

        private StagingSink(Blackhole blackhole) {
            this.blackhole = blackhole;
        }

        private void consume(BufferData buffer) {
            long checksum = 0;
            while (!buffer.consumed()) {
                staging.clear();
                if (buffer.writeTo(staging, buffer.available()) <= 0) {
                    throw new IllegalStateException("Buffer did not provide available data");
                }
                staging.flip();
                while (staging.remaining() >= Long.BYTES) {
                    checksum += staging.getLong();
                }
                while (staging.hasRemaining()) {
                    checksum += staging.get();
                }
            }
            blackhole.consume(checksum);
        }
    }

    private static final class BenchmarkTerminalWriteGate implements Http2FrameListener, Runnable {
        private static final VarHandle STARTED;
        private static final VarHandle COMPLETED;

        static {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                STARTED = lookup.findVarHandle(BenchmarkTerminalWriteGate.class, "started", long.class);
                COMPLETED = lookup.findVarHandle(BenchmarkTerminalWriteGate.class, "completed", long.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private final boolean trackWrites;
        private long started;
        private long completed;
        private volatile Thread waiter;

        private BenchmarkTerminalWriteGate(boolean trackWrites) {
            this.trackWrites = trackWrites;
        }

        @Override
        public void frameHeader(SocketContext ctx, int streamId, Http2FrameHeader header) {
            if (trackWrites
                    && (header.type() == Http2FrameType.DATA || header.type() == Http2FrameType.HEADERS)
                    && (header.flags() & Http2Flag.END_OF_STREAM) != 0) {
                STARTED.setRelease(this, started + 1);
            }
        }

        @Override
        public void run() {
            if (trackWrites) {
                COMPLETED.getAndAddRelease(this, 1L);
                Thread waitingThread = waiter;
                if (waitingThread != null) {
                    LockSupport.unpark(waitingThread);
                }
            }
        }
    }

    private static final class BenchmarkSocketContext implements SocketContext {
        @Override
        public PeerInfo remotePeer() {
            return PEER_INFO;
        }

        @Override
        public PeerInfo localPeer() {
            return PEER_INFO;
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public String socketId() {
            return "benchmark";
        }

        @Override
        public String childSocketId() {
            return "benchmark";
        }
    }

    private static final class BenchmarkPeerInfo implements PeerInfo {
        @Override
        public SocketAddress address() {
            return new InetSocketAddress(0);
        }

        @Override
        public String host() {
            return "benchmark";
        }

        @Override
        public int port() {
            return 0;
        }

        @Override
        public Optional<Principal> tlsPrincipal() {
            return Optional.empty();
        }

        @Override
        public Optional<Certificate[]> tlsCertificates() {
            return Optional.empty();
        }
    }
}
