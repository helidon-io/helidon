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
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2ConnectionWriter;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameTypes;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;

public class Http2FlowControlJmhTest {
    private static final int FRAME_SIZE = 1024;
    private static final int PARTIAL_WINDOW_SIZE = FRAME_SIZE / 2;
    private static final int CONCURRENT_THREADS = 8;
    private static final PeerInfo PEER_INFO = new BenchmarkPeerInfo();
    private static final Runnable NO_OP = () -> { };
    private static final FlowControl.Outbound WIDE_WINDOW = new BenchmarkFlowControl(false);
    private static final FlowControl.Outbound PARTIAL_WINDOW = new BenchmarkFlowControl(true);

    @Benchmark
    @Threads(1)
    public int wideWindow(ConnectionState connection, FrameState frame) {
        return connection.writer.writeData(frame.frame, WIDE_WINDOW, NO_OP);
    }

    @Benchmark
    @Threads(CONCURRENT_THREADS)
    public int wideWindowConcurrent(ConnectionState connection, FrameState frame) {
        return connection.writer.writeData(frame.frame, WIDE_WINDOW, NO_OP);
    }

    @Benchmark
    @Threads(1)
    public int partialWindow(ConnectionState connection, FrameState frame) {
        return connection.writer.writeData(frame.frame, PARTIAL_WINDOW, NO_OP);
    }

    @Benchmark
    @Threads(CONCURRENT_THREADS)
    public int partialWindowConcurrent(ConnectionState connection, FrameState frame) {
        return connection.writer.writeData(frame.frame, PARTIAL_WINDOW, NO_OP);
    }

    @State(Scope.Benchmark)
    public static class ConnectionState {
        private Http2ConnectionWriter writer;

        @Setup
        public void setup() {
            writer = new Http2ConnectionWriter(new BenchmarkSocketContext(), new BenchmarkDataWriter(), List.of());
        }
    }

    @State(Scope.Thread)
    public static class FrameState {
        private Http2FrameData frame;

        @Setup(Level.Invocation)
        public void setup() {
            frame = new Http2FrameData(Http2FrameHeader.create(FRAME_SIZE,
                                                               Http2FrameTypes.DATA,
                                                               Http2Flag.DataFlags.create(0),
                                                               1),
                                       BufferData.create(new byte[FRAME_SIZE]));
        }
    }

    private static final class BenchmarkFlowControl implements FlowControl.Outbound {
        private final boolean partial;

        private BenchmarkFlowControl(boolean partial) {
            this.partial = partial;
        }

        @Override
        public void decrementWindowSize(int decrement) {
        }

        @Override
        public void resetStreamWindowSize(int size) {
        }

        @Override
        public int getRemainingWindowSize() {
            return Integer.MAX_VALUE;
        }

        @Override
        public long incrementStreamWindowSize(int increment) {
            return Integer.MAX_VALUE;
        }

        @Override
        public Http2FrameData[] cut(Http2FrameData frame) {
            if (partial && frame.header().length() > PARTIAL_WINDOW_SIZE) {
                return frame.cut(PARTIAL_WINDOW_SIZE);
            }
            return new Http2FrameData[] {frame};
        }

        @Override
        public void blockTillUpdate() {
        }

        @Override
        public int maxFrameSize() {
            return FRAME_SIZE;
        }
    }

    private static final class BenchmarkDataWriter implements DataWriter {
        @Override
        public void write(BufferData... buffers) {
        }

        @Override
        public void write(BufferData buffer) {
        }

        @Override
        public void writeNow(BufferData... buffers) {
        }

        @Override
        public void writeNow(BufferData buffer) {
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
