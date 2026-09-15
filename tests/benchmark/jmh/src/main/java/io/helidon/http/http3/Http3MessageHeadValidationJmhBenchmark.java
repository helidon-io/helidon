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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.security.MessageDigest;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.PeerInfo;
import io.helidon.common.socket.SocketContext;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;
import io.helidon.quic.SequentialScheduler;
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

/**
 * Measures complete HTTP/3 request and response head parsing from pre-encoded HEADERS frames.
 * <p>
 * Each operation includes receiver and message-reader construction, QPACK decoding, message-head validation, and reader
 * close. Connection QPACK contexts are reused; frame encoding, network I/O, and entity processing are excluded.
 * There are no invocation-level setup or teardown hooks, so timing and allocated bytes cover the same operation.
 * <p>
 * Trial setup resolves public reader factories once. If the older factory signature is present, its validation argument
 * is bound to {@code false}; the same benchmark bytecode can therefore measure the former disabled-validation path and
 * the current mandatory-validation path. Other parser and QPACK changes between the compared revisions remain included.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@Threads(1)
public class Http3MessageHeadValidationJmhBenchmark {
    private static final Http3FrameListener NO_OP_FRAME_LISTENER = Http3FrameListener.create(List.of());
    private static final Consumer<Http3MessageReader.ResponseHead> NO_INFORMATIONAL_RESPONSES = head -> {
        throw new IllegalStateException("Unexpected informational response: " + head.status());
    };
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
            return "jmh-head";
        }

        @Override
        public String childSocketId() {
            return "jmh-head-stream";
        }
    };

    /**
     * Read one complete valid request head and release its stream state.
     *
     * @param state per-worker encoded inputs and connection state
     * @return decoded request head
     * @throws Throwable if reader construction, decoding, or cleanup fails
     */
    @Benchmark
    public Http3Protocol.DecodedRequestHead readRequestHead(HeadState state) throws Throwable {
        return state.readRequest();
    }

    /**
     * Read one complete valid response head and release its stream state.
     *
     * @param state per-worker encoded inputs and connection state
     * @return decoded response head
     * @throws Throwable if reader construction, decoding, or cleanup fails
     */
    @Benchmark
    public Http3MessageReader.ResponseHead readResponseHead(HeadState state) throws Throwable {
        return state.readResponse();
    }

    /**
     * Per-worker input frames and reusable connection QPACK state.
     */
    @State(Scope.Thread)
    public static class HeadState {
        private static final int MAX_HEADERS_SIZE = 65_536;
        private static final URI REQUEST_URI = URI.create("https://localhost/");

        private final AtomicReference<Throwable> qpackFailure = new AtomicReference<>();

        /**
         * Number of regular fields, including {@code content-length: 0}; pseudo-headers are additional.
         */
        @Param({"4", "32"})
        public int headerCount;

        /**
         * Length of each literal regular-field value, excluding the fixed content length.
         */
        @Param({"16", "256"})
        public int valueSize;

        private Http3QpackContext decoderContext;
        private MethodHandle requestFactory;
        private MethodHandle responseFactory;
        private byte[] encodedRequest;
        private byte[] encodedResponse;
        private HeaderName[] literalNames;
        private String literalValue;
        private long nextStreamId;

        /**
         * Encode and validate both inputs and adapt public reader factories outside measurement.
         *
         * @throws Throwable if the selected implementation cannot decode the benchmark inputs
         */
        @Setup(Level.Trial)
        public void setUpTrial() throws Throwable {
            if (headerCount < 2 || valueSize < 1) {
                throw new IllegalArgumentException("headerCount must be at least 2 and valueSize must be positive");
            }
            char[] value = new char[valueSize];
            for (int i = 0; i < value.length; i++) {
                value[i] = (char) ('a' + i % 26);
            }
            literalValue = new String(value);
            literalNames = new HeaderName[headerCount - 1];
            WritableHeaders<?> headers = WritableHeaders.create()
                    .add(HeaderValues.create(HeaderNames.CONTENT_LENGTH, "0"));
            for (int i = 0; i < literalNames.length; i++) {
                literalNames[i] = HeaderNames.create("x-jmh-" + i);
                headers.add(HeaderValues.create(literalNames[i], literalValue));
            }
            Http3QpackContext encoderContext = Http3QpackContext.create(0,
                                                                       0,
                                                                       MAX_HEADERS_SIZE,
                                                                       failure -> {
                                                                           qpackFailure.compareAndSet(null, failure);
                                                                       });
            try {
                encodedRequest = Http3Protocol.encodeRequestHeaders(encoderContext, 0, REQUEST_URI, "GET", headers);
                encodedResponse = Http3Protocol.encodeResponseHeaders(encoderContext, 4, 200, headers);
            } finally {
                encoderContext.close(new IllegalStateException("HTTP/3 head benchmark encoding complete"));
            }
            decoderContext = Http3QpackContext.create(0,
                                                      0,
                                                      MAX_HEADERS_SIZE,
                                                      failure -> qpackFailure.compareAndSet(null, failure));
            try {
                MethodType requestType = MethodType.methodType(Http3MessageReader.class,
                                                                QuicReceiverStream.class,
                                                                Http3QpackContext.class,
                                                                SocketContext.class,
                                                                int.class,
                                                                Http3FrameListener.class);
                requestFactory = MethodHandles.insertArguments(readerFactory("request", requestType, 4),
                                                                 1,
                                                                 decoderContext,
                                                                 SOCKET_CONTEXT,
                                                                 MAX_HEADERS_SIZE,
                                                                 NO_OP_FRAME_LISTENER);
                MethodType responseType = requestType.insertParameterTypes(3, Method.class);
                responseFactory = MethodHandles.insertArguments(readerFactory("response", responseType, 5),
                                                                  1,
                                                                  decoderContext,
                                                                  SOCKET_CONTEXT,
                                                                  Method.GET,
                                                                  MAX_HEADERS_SIZE,
                                                                  NO_OP_FRAME_LISTENER);
                Http3Protocol.DecodedRequestHead request = readRequest();
                if (!"GET".equals(request.method())
                        || !Optional.of("https").equals(request.scheme())
                        || !"localhost".equals(request.authority())
                        || !Optional.of("/").equals(request.path())) {
                    throw new IllegalStateException("Unexpected decoded request pseudo-headers");
                }
                validateHeaders(request.headers());
                Http3MessageReader.ResponseHead response = readResponse();
                if (response.status().code() != 200) {
                    throw new IllegalStateException("Unexpected decoded response status");
                }
                validateHeaders(response.headers());
                checkQpackFailure();
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                System.out.println("HTTP/3 head input SHA-256: request="
                                           + HexFormat.of().formatHex(digest.digest(encodedRequest))
                                           + ", response=" + HexFormat.of().formatHex(digest.digest(encodedResponse)));
            } catch (Throwable failure) {
                decoderContext.close(failure);
                decoderContext = null;
                throw failure;
            }
        }

        /**
         * Release connection QPACK state after all per-operation readers have been closed.
         */
        @TearDown(Level.Trial)
        public void tearDownTrial() {
            Throwable failure = qpackFailure.get();
            if (decoderContext != null) {
                decoderContext.close(new IllegalStateException("HTTP/3 head benchmark complete"));
                decoderContext = null;
            }
            if (failure != null) {
                throw new IllegalStateException("HTTP/3 head benchmark QPACK failure", failure);
            }
        }

        private static MethodHandle readerFactory(String name, MethodType type, int validationIndex)
                throws ReflectiveOperationException {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            try {
                return lookup.findStatic(Http3MessageReader.class, name, type);
            } catch (NoSuchMethodException _) {
                MethodHandle legacy = lookup.findStatic(Http3MessageReader.class,
                                                        name,
                                                        type.insertParameterTypes(validationIndex, boolean.class));
                System.out.println("HTTP/3 head benchmark uses legacy " + name + " with validateHeaderValues=false");
                return MethodHandles.insertArguments(legacy, validationIndex, false);
            }
        }

        private Http3Protocol.DecodedRequestHead readRequest() throws Throwable {
            BenchmarkReceiverStream stream = new BenchmarkReceiverStream(nextStreamId, encodedRequest, false);
            nextStreamId += 4;
            Http3Protocol.DecodedRequestHead head;
            Http3MessageReader reader = (Http3MessageReader) requestFactory.invokeExact((QuicReceiverStream) stream);
            try (reader) {
                head = reader.readRequestHead();
            }
            stream.validateReleased();
            return head;
        }

        private Http3MessageReader.ResponseHead readResponse() throws Throwable {
            BenchmarkReceiverStream stream = new BenchmarkReceiverStream(nextStreamId, encodedResponse, true);
            nextStreamId += 4;
            Http3MessageReader.ResponseHead head;
            Http3MessageReader reader = (Http3MessageReader) responseFactory.invokeExact((QuicReceiverStream) stream);
            try (reader) {
                head = reader.readResponseHead(NO_INFORMATIONAL_RESPONSES);
            }
            stream.validateReleased();
            return head;
        }

        private void validateHeaders(Headers headers) {
            if (headers.size() != headerCount || !"0".equals(headers.first(HeaderNames.CONTENT_LENGTH).orElse(null))) {
                throw new IllegalStateException("Unexpected decoded regular headers");
            }
            for (HeaderName name : literalNames) {
                if (!literalValue.equals(headers.first(name).orElse(null))) {
                    throw new IllegalStateException("Unexpected decoded field: " + name.lowerCase());
                }
            }
        }

        private void checkQpackFailure() {
            Throwable failure = qpackFailure.get();
            if (failure != null) {
                throw new IllegalStateException("HTTP/3 head benchmark QPACK failure", failure);
            }
        }
    }

    private static final class BenchmarkReceiverStream implements QuicReceiverStream {
        private final long streamId;
        private final BufferData input;
        private final int length;
        private final boolean localInitiated;
        private BenchmarkStreamReader reader;
        private boolean disconnected;

        private BenchmarkReceiverStream(long streamId, byte[] input, boolean localInitiated) {
            this.streamId = streamId;
            this.input = BufferData.createReadOnly(input, 0, input.length);
            this.length = input.length;
            this.localInitiated = localInitiated;
        }

        @Override
        public ReceivingStreamState receivingState() {
            return disconnected ? ReceivingStreamState.DATA_READ : ReceivingStreamState.RECV;
        }

        @Override
        public QuicStreamReader connectReader(SequentialScheduler scheduler) {
            if (reader != null) {
                throw new IllegalStateException("Reader already connected");
            }
            reader = new BenchmarkStreamReader(this, scheduler);
            return reader;
        }

        @Override
        public void disconnectReader(QuicStreamReader reader) {
            if (this.reader != reader || !this.reader.connected) {
                throw new IllegalStateException("Reader is not connected");
            }
            this.reader.connected = false;
            disconnected = true;
        }

        @Override
        public void requestStopSending(long errorCode) {
            // No peer flow-control action is needed by the in-memory benchmark stream.
        }

        @Override
        public long dataReceived() {
            return length;
        }

        @Override
        public long maxStreamData() {
            return length;
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
            return localInitiated;
        }

        @Override
        public boolean isRemoteInitiated() {
            return !localInitiated;
        }

        @Override
        public int type() {
            return 0;
        }

        @Override
        public QuicStream.StreamState state() {
            return receivingState();
        }

        private void validateReleased() {
            if (!input.consumed() || !disconnected || reader == null || reader.connected) {
                throw new IllegalStateException("HTTP/3 head operation retained input or a connected reader");
            }
        }
    }

    private static final class BenchmarkStreamReader extends QuicStreamReader {
        private final BenchmarkReceiverStream stream;
        private boolean connected = true;
        private boolean started;
        private int index;

        private BenchmarkStreamReader(BenchmarkReceiverStream stream, SequentialScheduler scheduler) {
            super(scheduler);
            this.stream = stream;
        }

        @Override
        public ReceivingStreamState receivingState() {
            ensureConnected();
            return stream.receivingState();
        }

        @Override
        public Optional<BufferData> poll() {
            Optional<BufferData> next = peek();
            if (next.isPresent()) {
                index++;
            }
            return next;
        }

        @Override
        public Optional<BufferData> peek() {
            ensureConnected();
            if (!started || index > 1) {
                return Optional.empty();
            }
            return Optional.of(index == 0 ? stream.input : EOF);
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

        private void ensureConnected() {
            if (!connected) {
                throw new IllegalStateException("Reader is disconnected");
            }
        }
    }
}
