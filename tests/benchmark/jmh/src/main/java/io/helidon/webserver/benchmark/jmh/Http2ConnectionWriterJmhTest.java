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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.List;
import java.util.Optional;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.PeerInfo;
import io.helidon.common.socket.SocketContext;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2ConnectionWriter;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2Headers;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

public class Http2ConnectionWriterJmhTest {
    private static final int MAX_FRAME_SIZE = 16_384;
    private static final int CONCURRENT_THREADS = 8;
    private static final byte[] RESPONSE_BYTES = {1};
    private static final PeerInfo PEER_INFO = new BenchmarkPeerInfo();

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

    @State(Scope.Benchmark)
    public static class ConnectionState {
        private BenchmarkDataWriter dataWriter;
        private Http2ConnectionWriter writer;

        @Setup
        public void setup() {
            dataWriter = new BenchmarkDataWriter();
            writer = new Http2ConnectionWriter(new BenchmarkSocketContext(), dataWriter, List.of());
        }

        private int write(FrameState frame, FlowControl.Outbound flowControl) {
            try {
                return writer.writeHeaders(frame.headers,
                                           1,
                                           Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                           frame.data,
                                           flowControl);
            } finally {
                frame.reset(flowControl);
            }
        }
    }

    @State(Scope.Thread)
    public static class FrameState {
        private final BenchmarkFlowControl fundedWindow = new BenchmarkFlowControl(RESPONSE_BYTES.length);
        private final BenchmarkFlowControl exhaustedWindow = new BenchmarkFlowControl(0);
        private BenchmarkDataWriter dataWriter;
        private Http2FrameData data;
        private Http2Headers headers;

        @Setup
        public void setup(ConnectionState connection, Blackhole blackhole) {
            dataWriter = connection.dataWriter;
            dataWriter.register(blackhole);
            headers = Http2Headers.create(WritableHeaders.create())
                    .status(Status.OK_200);
            data = new Http2FrameData(Http2FrameHeader.create(RESPONSE_BYTES.length,
                                                               Http2FrameTypes.DATA,
                                                               Http2Flag.DataFlags.create(Http2Flag.END_OF_STREAM),
                                                               1),
                                      BufferData.create(RESPONSE_BYTES));
        }

        private void reset(FlowControl.Outbound flowControl) {
            data.data().rewind();
            ((BenchmarkFlowControl) flowControl).reset();
        }

        @TearDown
        public void tearDown() {
            dataWriter.unregister();
        }
    }

    private static final class BenchmarkFlowControl implements FlowControl.Outbound {
        private final int initialWindowSize;
        private boolean windowUpdated;
        private int remainingWindowSize;

        private BenchmarkFlowControl(int initialWindowSize) {
            this.initialWindowSize = initialWindowSize;
            this.remainingWindowSize = initialWindowSize;
        }

        private void reset() {
            windowUpdated = false;
            remainingWindowSize = initialWindowSize;
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
            remainingWindowSize = RESPONSE_BYTES.length;
        }

        @Override
        public int maxFrameSize() {
            return MAX_FRAME_SIZE;
        }
    }

    private static final class BenchmarkDataWriter implements DataWriter {
        private final ThreadLocal<Blackhole> blackholes = new ThreadLocal<>();

        private void register(Blackhole blackhole) {
            blackholes.set(blackhole);
        }

        private void unregister() {
            blackholes.remove();
        }

        @Override
        public void write(BufferData... buffers) {
            blackhole().consume(buffers);
        }

        @Override
        public void write(BufferData buffer) {
            blackhole().consume(buffer);
        }

        @Override
        public void writeNow(BufferData... buffers) {
            blackhole().consume(buffers);
        }

        @Override
        public void writeNow(BufferData buffer) {
            blackhole().consume(buffer);
        }

        private Blackhole blackhole() {
            Blackhole blackhole = blackholes.get();
            if (blackhole == null) {
                throw new IllegalStateException("No JMH blackhole registered for benchmark thread");
            }
            return blackhole;
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
