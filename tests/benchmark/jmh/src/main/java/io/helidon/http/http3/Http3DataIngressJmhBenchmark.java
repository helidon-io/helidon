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

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.PeerInfo;
import io.helidon.common.socket.SocketContext;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;
import io.helidon.quic.SequentialScheduler;
import io.helidon.quic.frame.StreamFrame;
import io.helidon.quic.stream.QuicReceiverStream;
import io.helidon.quic.stream.QuicReceiverStream.ReceivingStreamState;
import io.helidon.quic.stream.QuicStream;
import io.helidon.quic.stream.QuicStreamReader;

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
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmarks HTTP/3 DATA transfer from a QUIC-ready buffer to application consumption.
 *
 * <p>The heap compatibility and slice methods use the real HTTP/3 message reader. Legacy methods reproduce only the
 * pre-slice payload materialization after symmetric reader/head setup; they are not full parser-throughput
 * measurements. Methods named {@code Fallback} isolate defensive buffer handoff for mutable or direct origins.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@Threads(1)
public class Http3DataIngressJmhBenchmark {
    private static final Http3FrameListener NO_OP_FRAME_LISTENER = Http3FrameListener.create(List.of());
    private static final HeaderName X_JMH = HeaderNames.create("x-jmh");
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
            return "jmh";
        }

        @Override
        public String childSocketId() {
            return "jmh-0";
        }
    };

    @Benchmark
    public int heapCompatibilityMaterializedBulk(DataState state) {
        byte[] data = state.reader.readEntityDataWithTrailers(state.payloadSize);
        if (data.length != state.payloadSize) {
            throw new IllegalStateException("Materialized DATA length mismatch: " + data.length);
        }
        state.materialized = data;
        System.arraycopy(data, 0, state.destination, 0, data.length);
        state.bulk = true;
        return state.destination[data.length - 1] & 0xFF;
    }

    @Benchmark
    public int heapSliceBulk(DataState state) {
        BufferData data = state.reader.readEntityBufferWithTrailers(state.payloadSize);
        state.handedOff = data;
        int read = data.read(state.destination, 0, state.destination.length);
        if (read != state.destination.length) {
            throw new IllegalStateException("Sliced DATA length mismatch: " + read);
        }
        state.bulk = true;
        return state.destination[state.destination.length - 1] & 0xFF;
    }

    @Benchmark
    public int heapCompatibilityMaterializedSingleByte(DataState state, Blackhole blackhole) {
        int checksum = 0;
        for (int i = 0; i < state.payloadSize; i++) {
            byte[] data = state.reader.readEntityDataWithTrailers(1);
            if (data.length != 1) {
                throw new IllegalStateException("Materialized single-byte DATA length mismatch: " + data.length);
            }
            blackhole.consume(data);
            checksum += data[0] & 0xFF;
        }
        state.checksum = checksum;
        return checksum;
    }

    @Benchmark
    public int heapLegacyMaterializedBulk(DataState state) {
        return state.materializedBulk();
    }

    @Benchmark
    public int heapLegacyMaterializedSingleByte(DataState state, Blackhole blackhole) {
        int checksum = 0;
        for (int i = 0; i < state.payloadSize; i++) {
            byte[] data = new byte[1];
            int read = state.source.read(data, 0, 1);
            if (read != 1) {
                throw new IllegalStateException("Legacy single-byte DATA length mismatch: " + read);
            }
            blackhole.consume(data);
            checksum += data[0] & 0xFF;
        }
        state.checksum = checksum;
        return checksum;
    }

    @Benchmark
    public int heapSliceSingleByte(DataState state, Blackhole blackhole) throws IOException {
        InputStream input = state.reader.inputStreamWithTrailers();
        int checksum = 0;
        for (int i = 0; i < state.payloadSize; i++) {
            int next = input.read();
            if (next == -1) {
                throw new IllegalStateException("Sliced HTTP/3 entity ended after " + i + " bytes");
            }
            blackhole.consume(input);
            checksum += next;
        }
        state.checksum = checksum;
        return checksum;
    }

    @Benchmark
    public int heapSliceTrailersCompletion(DataState state) {
        BufferData data = state.reader.readEntityBufferWithTrailers(state.payloadSize);
        state.handedOff = data;
        int read = data.read(state.destination, 0, state.destination.length);
        if (read != state.destination.length) {
            throw new IllegalStateException("Sliced DATA length mismatch: " + read);
        }
        if (state.reader.readEntityBufferWithTrailers(state.payloadSize).available() != 0) {
            throw new IllegalStateException("HTTP/3 reader exposed entity data after trailers");
        }
        state.bulk = true;
        return state.destination[state.destination.length - 1] & 0xFF;
    }

    @Benchmark
    public int unknownOriginSliceFallbackBulk(DataState state) {
        return state.sliceBulk();
    }

    @Benchmark
    public int directOriginMaterializedFallbackBulk(DataState state) {
        state.prepareDirectSource();
        return state.materializedBulk();
    }

    @Benchmark
    public int directOriginSliceFallbackBulk(DataState state) {
        state.prepareDirectSource();
        return state.sliceBulk();
    }

    /**
     * Per-thread DATA payload and consumption state.
     */
    @State(Scope.Thread)
    public static class DataState {
        private static final int TRAILER_SIZE = 8;
        private static final byte TRAILER_BYTE = 0x5A;

        /**
         * DATA payload size.
         */
        @Param({"1024", "16384", "1048576"})
        public int payloadSize;

        private byte[] expectedPayload;
        private byte[] framedInput;
        private byte[] messageInput;
        private byte[] completeMessageInput;
        private ByteBuffer directInput;
        private byte[] destination;
        private int frameHeaderSize;
        private int expectedChecksum;
        private BufferData source;
        private BufferData handedOff;
        private byte[] materialized;
        private StreamFrame directFrame;
        private Http3MessageReader reader;
        private int checksum;
        private boolean bulk;
        private boolean completeMessage;

        @Setup(Level.Trial)
        public void setUpTrial() {
            expectedPayload = new byte[payloadSize];
            int sum = 0;
            for (int i = 0; i < expectedPayload.length; i++) {
                byte value = (byte) (i * 31 + 7);
                expectedPayload[i] = value;
                sum += value & 0xFF;
            }
            expectedChecksum = sum;
            byte[] frame = Http3Protocol.encodeDataFrame(expectedPayload);
            frameHeaderSize = frame.length - payloadSize;
            framedInput = Arrays.copyOf(frame, frame.length + TRAILER_SIZE);
            Arrays.fill(framedInput, frame.length, framedInput.length, TRAILER_BYTE);
            byte[] headers = Http3Protocol.encodeResponseHeaders(200, WritableHeaders.create());
            messageInput = new byte[headers.length + framedInput.length];
            System.arraycopy(headers, 0, messageInput, 0, headers.length);
            System.arraycopy(framedInput, 0, messageInput, headers.length, framedInput.length);
            byte[] trailers = Http3Protocol.encodeHeadersFrame(WritableHeaders.create()
                                                                       .add(HeaderValues.create("x-jmh", "done")));
            completeMessageInput = new byte[headers.length + frame.length + trailers.length];
            System.arraycopy(headers, 0, completeMessageInput, 0, headers.length);
            System.arraycopy(frame, 0, completeMessageInput, headers.length, frame.length);
            System.arraycopy(trailers,
                             0,
                             completeMessageInput,
                             headers.length + frame.length,
                             trailers.length);
            directInput = ByteBuffer.allocateDirect(framedInput.length);
            directInput.put(framedInput).flip();
            destination = new byte[payloadSize];

            BufferData verificationSource = BufferData.createReadOnly(messageInput, 0, messageInput.length);
            Http3MessageReader verificationReader = newReader(verificationSource);
            verificationReader.readResponseHead(ignored -> {
            });
            BufferData verificationSlice = verificationReader.readEntityBufferWithTrailers(payloadSize);
            if (verificationSource.available() != TRAILER_SIZE
                    || verificationSlice.available() != payloadSize
                    || verificationSlice.get(0) != (expectedPayload[0] & 0xFF)
                    || verificationSlice.get(payloadSize - 1) != (expectedPayload[payloadSize - 1] & 0xFF)) {
                throw new IllegalStateException("HTTP/3 DATA benchmark range setup is invalid");
            }
            expectOutOfBounds(verificationSlice, payloadSize);
            verificationSlice.skip(payloadSize);
            verificationSlice.rewind();
            expectOutOfBounds(verificationSlice, payloadSize);
            verificationReader.close();
        }

        @Setup(Level.Invocation)
        public void setUpInvocation(BenchmarkParams params) {
            String benchmark = params.getBenchmark();
            completeMessage = benchmark.contains("TrailersCompletion");
            if (benchmark.contains("directOrigin") && benchmark.contains("Fallback")) {
                directFrame = StreamFrame.createOwned(0,
                                                      0,
                                                      directInput.remaining(),
                                                      false,
                                                      directInput.duplicate());
            } else if (benchmark.contains("unknownOrigin") && benchmark.contains("Fallback")) {
                source = BufferData.create(framedInput, 0, framedInput.length);
                source.skip(frameHeaderSize);
            } else if (benchmark.contains("Legacy")) {
                source = BufferData.createReadOnly(messageInput, 0, messageInput.length);
                reader = newReader(source);
                reader.readResponseHead(ignored -> {
                });
                source.skip(frameHeaderSize);
            } else {
                byte[] input = completeMessage ? completeMessageInput : messageInput;
                source = BufferData.createReadOnly(input, 0, input.length);
                reader = newReader(source);
                reader.readResponseHead(ignored -> {
                });
            }
            handedOff = null;
            materialized = null;
            checksum = 0;
            bulk = false;
            Arrays.fill(destination, (byte) 0);
        }

        @TearDown(Level.Invocation)
        public void tearDownInvocation() {
            if (source == null) {
                throw new IllegalStateException("HTTP/3 source is not available for validation");
            }
            if (completeMessage) {
                if (!source.consumed()
                        || !reader.messageComplete()
                        || !"done".equals(reader.trailers().first(X_JMH).orElseThrow())) {
                    throw new IllegalStateException("HTTP/3 trailer completion did not consume the complete message");
                }
            } else {
                if (source.available() != TRAILER_SIZE) {
                    throw new IllegalStateException("HTTP/3 source did not retain exactly the trailing guard range");
                }
                if (source.get(0) != (TRAILER_BYTE & 0xFF)) {
                    throw new IllegalStateException("HTTP/3 source advanced beyond the DATA payload");
                }
            }

            if (handedOff != null) {
                if (!handedOff.consumed()) {
                    throw new IllegalStateException("HTTP/3 DATA slice was not fully consumed");
                }
            }
            if (materialized != null && !Arrays.equals(expectedPayload, materialized)) {
                throw new IllegalStateException("Materialized HTTP/3 DATA changed the payload");
            }

            if (bulk) {
                if (!Arrays.equals(expectedPayload, destination)) {
                    throw new IllegalStateException("Bulk HTTP/3 DATA consumption changed the payload");
                }
            } else if (checksum != expectedChecksum) {
                throw new IllegalStateException("Single-byte HTTP/3 DATA consumption changed the payload");
            }
            if (reader != null) {
                reader.close();
            }
            source = null;
            handedOff = null;
            materialized = null;
            directFrame = null;
            reader = null;
        }

        private int materializedBulk() {
            byte[] data = new byte[payloadSize];
            int read = source.read(data, 0, data.length);
            if (read != data.length) {
                throw new IllegalStateException("Materialized DATA length mismatch: " + read);
            }
            materialized = data;
            System.arraycopy(data, 0, destination, 0, data.length);
            bulk = true;
            return destination[data.length - 1] & 0xFF;
        }

        private int sliceBulk() {
            BufferData data = BufferData.readOnlySlice(source, payloadSize);
            handedOff = data;
            int read = data.read(destination, 0, destination.length);
            if (read != destination.length) {
                throw new IllegalStateException("Sliced DATA length mismatch: " + read);
            }
            bulk = true;
            return destination[destination.length - 1] & 0xFF;
        }

        private void prepareDirectSource() {
            source = directFrame.ownedPayloadData();
            source.skip(frameHeaderSize);
        }

        private static Http3MessageReader newReader(BufferData input) {
            return Http3MessageReader.response(new BenchmarkReceiverStream(input),
                                               Http3QpackContext.create(0, 0, 16_384, ignored -> {
                                               }),
                                               SOCKET_CONTEXT,
                                               Method.GET,
                                               16_384,
                                               NO_OP_FRAME_LISTENER);
        }

        private static void expectOutOfBounds(BufferData data, int index) {
            try {
                data.get(index);
                throw new IllegalStateException("Buffer exposed bytes beyond its logical range");
            } catch (IndexOutOfBoundsException expected) {
                // Expected strict upper bound.
            }
        }
    }

    private static final class BenchmarkReceiverStream implements QuicReceiverStream {
        private final BufferData[] buffers;
        private final long dataReceived;
        private BenchmarkStreamReader reader;
        private boolean disconnected;

        private BenchmarkReceiverStream(BufferData input) {
            buffers = new BufferData[] {input, QuicStreamReader.EOF};
            dataReceived = input.available();
        }

        @Override
        public ReceivingStreamState receivingState() {
            return disconnected ? ReceivingStreamState.DATA_READ : ReceivingStreamState.RECV;
        }

        @Override
        public QuicStreamReader connectReader(SequentialScheduler scheduler) {
            if (reader != null && reader.connected()) {
                throw new IllegalStateException("Reader already connected");
            }
            reader = new BenchmarkStreamReader(this, scheduler, buffers);
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
            return 0;
        }

        @Override
        public StreamMode mode() {
            return StreamMode.READ_ONLY;
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
            return false;
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
            return 0x02;
        }

        @Override
        public QuicStream.StreamState state() {
            return receivingState();
        }
    }

    private static final class BenchmarkStreamReader extends QuicStreamReader {
        private final BenchmarkReceiverStream stream;
        private final BufferData[] buffers;
        private int index;
        private boolean connected = true;
        private boolean started;

        private BenchmarkStreamReader(BenchmarkReceiverStream stream,
                                      SequentialScheduler scheduler,
                                      BufferData[] buffers) {
            super(scheduler);
            this.stream = stream;
            this.buffers = buffers;
        }

        @Override
        public ReceivingStreamState receivingState() {
            ensureConnected();
            return stream.receivingState();
        }

        @Override
        public Optional<BufferData> poll() {
            ensureConnected();
            if (!started || index == buffers.length) {
                return Optional.empty();
            }
            return Optional.of(buffers[index++]);
        }

        @Override
        public Optional<BufferData> peek() {
            ensureConnected();
            if (!started || index == buffers.length) {
                return Optional.empty();
            }
            return Optional.of(buffers[index]);
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
}
