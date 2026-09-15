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
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.PeerInfo;
import io.helidon.common.socket.SocketContext;
import io.helidon.quic.SequentialScheduler;
import io.helidon.quic.VariableLengthEncoder;
import io.helidon.quic.stream.QuicSenderStream;
import io.helidon.quic.stream.QuicStreamWriter;

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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmarks HTTP/3 request DATA observation with a raw QUIC control, a permanent no-op listener, and a metadata listener.
 *
 * <p>DATA writes reuse their writer and buffers. The raw control includes the same rewinds, submission calls, and constant-time
 * sink checksum as the observed variants, without packet transmission or logging. Segmented frames split each header byte
 * and divide the payload into two submissions. Writer construction is measured separately, including equivalent scheduler
 * and concrete QUIC-writer allocation in each variant.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@Threads(1)
public class Http3FrameObservationJmhBenchmark {
    private static final Runnable NO_OP = () -> {
    };
    private static final Http3FrameListener NO_OP_LISTENER = Http3FrameListener.create(List.of());
    private static final CompletableFuture<Void> DISPATCH_COMPLETED = CompletableFuture.completedFuture(null);
    private static final PeerInfo PEER = new PeerInfo() {
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
    private static final SocketContext SOCKET_CONTEXT = new SocketContext() {
        @Override
        public PeerInfo remotePeer() {
            return PEER;
        }

        @Override
        public PeerInfo localPeer() {
            return PEER;
        }

        @Override
        public boolean isSecure() {
            return true;
        }

        @Override
        public String socketId() {
            return "http3-frame-observation-jmh";
        }

        @Override
        public String childSocketId() {
            return "http3-frame-observation-jmh-0";
        }
    };

    /**
     * Submit one complete DATA frame through the reusable writer.
     *
     * @param state per-thread frame and writer state
     * @return checksum of consumed bytes and observed metadata
     */
    @Benchmark
    public long writeDataFrame(DataState state) {
        return state.write();
    }

    /**
     * Reconnect a writer without constructing a sender or writing a frame.
     *
     * @param state reusable sender and listener
     * @return newly connected writer
     */
    @Benchmark
    public QuicStreamWriter constructWriter(ConstructionState state) {
        state.stream.disconnectWriter(state.stream.writer);
        return connectWriter(state.stream, state.observation, state.metadata);
    }

    private static QuicStreamWriter connectWriter(SinkStream stream, String observation, MetadataListener metadata) {
        return switch (observation) {
            case "raw" -> stream.connectWriter(scheduler(NO_OP));
            case "noOp" -> Http3StreamSupport.connectWriter(stream, SOCKET_CONTEXT, NO_OP_LISTENER);
            case "metadata" -> Http3StreamSupport.connectWriter(stream, SOCKET_CONTEXT, metadata);
            default -> throw new IllegalArgumentException("Unknown observation: " + observation);
        };
    }

    private static SequentialScheduler scheduler(Runnable mainLoop) {
        return SequentialScheduler.create(new SequentialScheduler.CompleteRestartableTask() {
            @Override
            protected void run() {
                mainLoop.run();
            }
        });
    }

    /**
     * Per-thread DATA writer and reusable whole-frame or segmented buffers.
     */
    @State(Scope.Thread)
    public static class DataState {
        private final SinkStream stream = new SinkStream();
        private final MetadataListener metadata = new MetadataListener();

        /**
         * Raw QUIC control, permanent no-op HTTP/3 listener, or enabled metadata listener.
         */
        @Param({"raw", "noOp", "metadata"})
        public String observation;

        /**
         * Request DATA payload size.
         */
        @Param({"8", "1024"})
        public int payloadSize;

        /**
         * One submission or individually segmented header bytes and two payload submissions.
         */
        @Param({"whole", "segmented"})
        public String layout;

        private QuicStreamWriter writer;
        private BufferData[] fragments;
        private int headerSize;
        private long writes;
        private long expectedChecksum;

        /**
         * Construct reusable frame fragments and validate their bytes and one complete DATA write.
         */
        @Setup(Level.Trial)
        public void setup() {
            if (payloadSize < 2) {
                throw new IllegalArgumentException("DATA payload must allow two nonempty segments: " + payloadSize);
            }
            headerSize = VariableLengthEncoder.encodedSize(Http3Protocol.FRAME_DATA)
                    + VariableLengthEncoder.encodedSize(payloadSize);
            BufferData output = BufferData.create(headerSize + payloadSize);
            VariableLengthEncoder.encode(output, Http3Protocol.FRAME_DATA);
            VariableLengthEncoder.encode(output, payloadSize);
            for (int i = 0; i < payloadSize; i++) {
                output.write((byte) (i * 31 + 7));
            }
            byte[] encoded = output.readBytes();
            switch (layout) {
                case "whole" -> fragments = new BufferData[] {BufferData.createReadOnly(encoded, 0, encoded.length)};
                case "segmented" -> {
                    fragments = new BufferData[headerSize + 2];
                    for (int i = 0; i < headerSize; i++) {
                        fragments[i] = BufferData.createReadOnly(encoded, i, 1);
                    }
                    int firstPayloadSize = payloadSize / 2;
                    fragments[headerSize] = BufferData.createReadOnly(encoded, headerSize, firstPayloadSize);
                    fragments[headerSize + 1] = BufferData.createReadOnly(encoded,
                                                                         headerSize + firstPayloadSize,
                                                                         payloadSize - firstPayloadSize);
                }
                default -> throw new IllegalArgumentException("Unknown frame layout: " + layout);
            }
            int offset = 0;
            for (BufferData fragment : fragments) {
                int length = fragment.available();
                expectedChecksum += length
                        + 31L * (encoded[offset] & 0xFF)
                        + 17L * (encoded[offset + length / 2] & 0xFF)
                        + (encoded[offset + length - 1] & 0xFF);
                for (int i = 0; i < length; i++) {
                    if (fragment.get(i) != (encoded[offset + i] & 0xFF)) {
                        throw new IllegalStateException("DATA fragment mismatch at byte " + (offset + i));
                    }
                }
                offset += length;
            }
            if (offset != encoded.length) {
                throw new IllegalStateException("DATA fragment length mismatch: " + offset + " != " + encoded.length);
            }
            writer = connectWriter(stream, observation, metadata);
            write();
            verify();
        }

        /**
         * Verify all submissions and metadata events, then disconnect the writer.
         */
        @TearDown(Level.Trial)
        public void tearDown() {
            if (writer == null) {
                return;
            }
            try {
                verify();
            } finally {
                stream.disconnectWriter(stream.writer);
            }
        }

        private long write() {
            for (int i = 0; i < fragments.length; i++) {
                BufferData fragment = fragments[i].rewind();
                if (i == fragments.length - 1) {
                    writer.scheduleForWriting(fragment, false);
                } else {
                    writer.queueForWriting(fragment);
                }
            }
            writes++;
            return stream.checksum + metadata.headers + metadata.payloadBytes;
        }

        private void verify() {
            long expectedBytes = writes * (headerSize + payloadSize);
            if (stream.bytes != expectedBytes || stream.checksum != writes * expectedChecksum) {
                throw new IllegalStateException("Unexpected DATA sink after " + writes + " frames: bytes=" + stream.bytes
                                                        + ", expectedBytes=" + expectedBytes + ", checksum=" + stream.checksum
                                                        + ", expectedChecksum=" + writes * expectedChecksum);
            }
            long observedFrames = observation.equals("metadata") ? writes : 0;
            if (metadata.headers != observedFrames
                    || metadata.completedFrames != observedFrames
                    || metadata.declaredBytes != observedFrames * payloadSize
                    || metadata.payloadBytes != observedFrames * payloadSize
                    || metadata.headerBytes != observedFrames * headerSize
                    || metadata.frameTypes != 0) {
                throw new IllegalStateException("Unexpected DATA observation after " + writes + " frames: headers="
                                                        + metadata.headers + ", completed=" + metadata.completedFrames
                                                        + ", declaredBytes=" + metadata.declaredBytes + ", payloadBytes="
                                                        + metadata.payloadBytes + ", headerBytes=" + metadata.headerBytes
                                                        + ", frameTypes=" + metadata.frameTypes);
            }
        }
    }

    /**
     * Reusable sender and listener for measuring each writer connection independently of DATA writes.
     */
    @State(Scope.Thread)
    public static class ConstructionState {
        private final SinkStream stream = new SinkStream();
        private final MetadataListener metadata = new MetadataListener();

        /**
         * Raw QUIC control, permanent no-op HTTP/3 listener, or enabled metadata listener.
         */
        @Param({"raw", "noOp", "metadata"})
        public String observation;

        /**
         * Establish the first writer, so every measured operation performs the same reconnection.
         */
        @Setup(Level.Trial)
        public void setup() {
            connectWriter(stream, observation, metadata);
        }

        /**
         * Disconnect the last writer created by the trial.
         */
        @TearDown(Level.Trial)
        public void tearDown() {
            if (stream.writer != null) {
                stream.disconnectWriter(stream.writer);
            }
        }
    }

    private static final class MetadataListener implements Http3FrameListener {
        private long headers;
        private long completedFrames;
        private long declaredBytes;
        private long payloadBytes;
        private long headerBytes;
        private long frameTypes;

        @Override
        public void frameHeader(SocketContext context, long streamId, long frameType, long frameLength, int encodedLength) {
            headers++;
            declaredBytes += frameLength;
            headerBytes += encodedLength;
            frameTypes += frameType;
        }

        @Override
        public void frameData(SocketContext context, long streamId, int byteCount, boolean last) {
            payloadBytes += byteCount;
            if (last) {
                completedFrames++;
            }
        }

        @Override
        public void rawFrameHeader(SocketContext context, long streamId, byte[] data) {
            throw new IllegalStateException("Raw header callback to metadata-only listener");
        }

        @Override
        public void rawFrameData(SocketContext context, long streamId, byte[] data, boolean last) {
            throw new IllegalStateException("Raw payload callback to metadata-only listener");
        }
    }

    private static final class SinkStream implements QuicSenderStream {
        private final Optional<QuicSenderStream> stream = Optional.of(this);
        private final CompletableFuture<Long> stopSending = new CompletableFuture<>();
        private final CompletableFuture<SendingStreamState> sendingCompletion = new CompletableFuture<>();

        private SinkWriter writer;
        private long bytes;
        private long checksum;
        private long errorCode = -1;

        @Override
        public SendingStreamState sendingState() {
            return errorCode < 0 ? SendingStreamState.SEND : SendingStreamState.RESET_SENT;
        }

        @Override
        public QuicStreamWriter connectWriter(SequentialScheduler scheduler) {
            if (writer != null && writer.connected) {
                throw new IllegalStateException("Writer is already connected");
            }
            writer = new SinkWriter(this, scheduler);
            return writer;
        }

        @Override
        public void disconnectWriter(QuicStreamWriter writer) {
            if (this.writer != writer || !this.writer.connected) {
                throw new IllegalStateException("Writer is not connected");
            }
            this.writer.connected = false;
        }

        @Override
        public void reset(long errorCode) {
            this.errorCode = errorCode;
        }

        @Override
        public long dataSent() {
            return bytes;
        }

        @Override
        public long sndErrorCode() {
            return errorCode;
        }

        @Override
        public boolean stopSendingReceived() {
            return false;
        }

        @Override
        public CompletionStage<Long> whenStopSendingReceived() {
            return stopSending;
        }

        @Override
        public CompletableFuture<SendingStreamState> futureSendingCompletion() {
            return sendingCompletion;
        }

        @Override
        public long streamId() {
            return 0;
        }

        @Override
        public StreamMode mode() {
            return StreamMode.WRITE_ONLY;
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
            return true;
        }

        @Override
        public boolean isRemoteInitiated() {
            return false;
        }

        @Override
        public int type() {
            return 0;
        }

        @Override
        public StreamState state() {
            return sendingState();
        }
    }

    private static final class SinkWriter extends QuicStreamWriter {
        private final SinkStream stream;

        private boolean connected = true;

        private SinkWriter(SinkStream stream, SequentialScheduler scheduler) {
            super(scheduler);
            this.stream = stream;
        }

        @Override
        public QuicSenderStream.SendingStreamState sendingState() {
            return stream.sendingState();
        }

        @Override
        public void scheduleForWriting(BufferData buffer, boolean last) {
            if (!connected || last) {
                throw new IllegalStateException("Expected a connected writer and non-final DATA submission");
            }
            int length = buffer.available();
            if (length > 0) {
                stream.checksum += length
                        + 31L * (buffer.get(0) & 0xFF)
                        + 17L * (buffer.get(length / 2) & 0xFF)
                        + (buffer.get(length - 1) & 0xFF);
                stream.bytes += length;
                buffer.skip(length);
            }
        }

        @Override
        public CompletableFuture<Void> scheduleForWritingAndGetDispatchCompletion(BufferData buffer, boolean last) {
            scheduleForWriting(buffer, last);
            return DISPATCH_COMPLETED;
        }

        @Override
        public void queueForWriting(BufferData buffer) {
            scheduleForWriting(buffer, false);
        }

        @Override
        public long credit() {
            return Long.MAX_VALUE;
        }

        @Override
        public void reset(long errorCode) {
            stream.reset(errorCode);
        }

        @Override
        public Optional<QuicSenderStream> stream() {
            return connected ? stream.stream : Optional.empty();
        }

        @Override
        public boolean connected() {
            return connected;
        }
    }
}
