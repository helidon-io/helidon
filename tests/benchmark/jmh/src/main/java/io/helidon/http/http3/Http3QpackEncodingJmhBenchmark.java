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

import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.PrefixedIntegerCodec;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;
import io.helidon.quic.VariableLengthEncoder;

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
 * Benchmarks QPACK request and response encoding with static-only and dynamic-enabled peer settings.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class Http3QpackEncodingJmhBenchmark {
    private static final int CONCURRENT_THREADS = 8;

    /**
     * Encode a complete request HEADERS frame, including Host selection and pseudo-header construction.
     *
     * @param state per-thread connection and prebuilt request inputs
     * @return encoded HEADERS frame
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @Threads(1)
    public byte[] encodeRequestProtocolStaticOnly(RequestProtocolState state) {
        long streamId = state.nextStreamId;
        state.nextStreamId += 4;
        return Http3Protocol.encodeRequestHeaders(state.context, streamId, state.uri, "GET", state.headers);
    }

    @Benchmark
    @Threads(1)
    public byte[] encodeRequestStaticOnly(StaticQpackState state) {
        return state.context.encodeHeaders(0, state.requestHeaders);
    }

    @Benchmark
    @Threads(CONCURRENT_THREADS)
    public byte[] encodeRequestStaticOnlyConcurrent(StaticQpackState state) {
        return state.context.encodeHeaders(0, state.requestHeaders);
    }

    @Benchmark
    @Threads(1)
    public byte[] encodeResponseStaticOnly(StaticQpackState state) {
        return state.context.encodeHeaders(0, state.responseHeaders);
    }

    @Benchmark
    @Threads(CONCURRENT_THREADS)
    public byte[] encodeResponseStaticOnlyConcurrent(StaticQpackState state) {
        return state.context.encodeHeaders(0, state.responseHeaders);
    }

    @Benchmark
    @Threads(1)
    public byte[] encodeResponseDynamic(DynamicQpackState state) {
        return state.encodeResponse();
    }

    @Benchmark
    @Threads(CONCURRENT_THREADS)
    public byte[] encodeResponseDynamicConcurrent(DynamicQpackState state) {
        return state.encodeResponse();
    }

    @Benchmark
    @Threads(1)
    public byte[] encodeResponseDynamicDeep(DeepDynamicQpackState state) {
        return state.encodeResponse();
    }

    @Benchmark
    @Threads(CONCURRENT_THREADS)
    public byte[] encodeResponseDynamicDeepConcurrent(DeepDynamicQpackState state) {
        return state.encodeResponse();
    }

    /**
     * Per-thread connection state and ordinary request headers with a Host field that overrides the URI authority.
     */
    @State(Scope.Thread)
    public static class RequestProtocolState {
        private static final int MAX_HEADERS_SIZE = 16_384;

        /**
         * Whether the prebuilt Host field carries never-index metadata.
         */
        @Param({"false", "true"})
        public boolean sensitiveHost;

        private Http3QpackContext context;
        private URI uri;
        private Headers headers;
        private long nextStreamId;
        private Throwable connectionFailure;

        /**
         * Prepare request inputs, disable the dynamic table, and verify the complete encoded request.
         */
        @Setup(Level.Trial)
        public void setup() {
            uri = URI.create("https://uri.example/baseline2?a=1&b=1");
            Header host = sensitiveHost
                    ? HeaderValues.create(HeaderNames.HOST, false, true, "localhost")
                    : HeaderValues.create(HeaderNames.HOST, "localhost");
            headers = WritableHeaders.create()
                    .add(host)
                    .add(HeaderValues.create("user-agent", "h2load nghttp3/ngtcp2"))
                    .add(HeaderValues.create("accept", "*/*"));
            context = Http3QpackContext.create(0, 0, MAX_HEADERS_SIZE, failure -> connectionFailure = failure);
            try {
                context.peerSettings(0, 0);
                verifyRequest(Http3Protocol.encodeRequestHeaders(context, 0, uri, "GET", headers));
                nextStreamId = 4;
            } catch (RuntimeException | Error failure) {
                context.close(failure);
                context = null;
                throw failure;
            }
        }

        /**
         * Release the connection state after all encoding operations.
         */
        @TearDown(Level.Trial)
        public void tearDown() {
            if (context != null) {
                Throwable failure = connectionFailure;
                context.close(new IllegalStateException("HTTP/3 request encoding benchmark complete"));
                context = null;
                if (failure != null) {
                    throw new IllegalStateException("HTTP/3 request encoding failed", failure);
                }
            }
        }

        private void verifyRequest(byte[] encoded) {
            BufferData frame = BufferData.createReadOnly(encoded, 0, encoded.length);
            long frameType = VariableLengthEncoder.decode(frame);
            long frameLength = VariableLengthEncoder.decode(frame);
            if (frameType != Http3Protocol.FRAME_HEADERS || frameLength != frame.available()) {
                throw new IllegalStateException("Unexpected HTTP/3 request frame");
            }
            Http3QpackContext.Stream stream = context.openStream(0);
            try {
                Headers decoded = stream.decodeHeaders(frame, MAX_HEADERS_SIZE);
                if (decoded.size() != 6 || !frame.consumed()
                        || decoded.contains(HeaderNames.HOST)
                        || !"GET".equals(decoded.first(HeaderNames.create(":method")).orElse(null))
                        || !"https".equals(decoded.first(HeaderNames.create(":scheme")).orElse(null))
                        || !"localhost".equals(decoded.first(HeaderNames.create(":authority")).orElse(null))
                        || !"/baseline2?a=1&b=1".equals(decoded.first(HeaderNames.create(":path")).orElse(null))
                        || !"h2load nghttp3/ngtcp2".equals(decoded.first(HeaderNames.USER_AGENT).orElse(null))
                        || !"*/*".equals(decoded.first(HeaderNames.ACCEPT).orElse(null))) {
                    throw new IllegalStateException("Unexpected HTTP/3 request fields: " + decoded);
                }
                // Older implementations lose Host sensitivity; keep them measurable and record their wire behavior.
                boolean authoritySensitive = decoded.get(HeaderNames.create(":authority")).sensitive();
                System.out.println("HTTP/3 request encoding fixture: sensitiveHost=" + sensitiveHost
                                           + ", encodedAuthoritySensitive=" + authoritySensitive
                                           + ", bytes=" + encoded.length);
            } finally {
                stream.complete();
            }
        }
    }

    /**
     * Shared state isolates static-table lookup by disabling the peer dynamic table.
     */
    @State(Scope.Benchmark)
    public static class StaticQpackState {
        private Http3QpackContext context;
        private List<Header> requestHeaders;
        private List<Header> responseHeaders;

        @Setup
        public void setup() {
            context = Http3QpackContext.create(4_096, 16, 16_384, _ -> {
            });
            context.peerSettings(0, 0);
            requestHeaders = requestHeaders();
            responseHeaders = responseHeaders();
        }
    }

    /**
     * Shared state models a peer that acknowledges dynamic-table insertions and each encoded field section.
     */
    @State(Scope.Benchmark)
    public static class DynamicQpackState extends DynamicQpackStateBase {
        @Setup
        public void setup() {
            setupDynamic(0, 0);
        }
    }

    /**
     * Shared state keeps the response entries live behind enough newer entries to exercise deep dynamic-table lookup.
     */
    @State(Scope.Benchmark)
    public static class DeepDynamicQpackState extends DynamicQpackStateBase {
        private static final int EARLY_CHURN_ENTRIES = 20;
        private static final int LATE_CHURN_ENTRIES = 50;

        @Setup
        public void setup() {
            setupDynamic(EARLY_CHURN_ENTRIES, LATE_CHURN_ENTRIES);
        }
    }

    private abstract static class DynamicQpackStateBase {
        private static final int STREAM_SLOTS = 256;

        private final AtomicInteger nextStreamSlot = new AtomicInteger();
        private Http3QpackContext context;
        private List<Header> responseHeaders;
        private byte[][] sectionAcknowledgments;

        final void setupDynamic(int earlyChurnEntries, int lateChurnEntries) {
            context = Http3QpackContext.create(4_096, 16, 16_384, _ -> {
            });
            sectionAcknowledgments = new byte[STREAM_SLOTS][];
            for (int i = 0; i < sectionAcknowledgments.length; i++) {
                long streamId = i * 4L;
                BufferData acknowledgment = BufferData.growing(16);
                PrefixedIntegerCodec.writeLong(acknowledgment, streamId, 0b1000_0000, 7);
                sectionAcknowledgments[i] = acknowledgment.readBytes();
            }

            Http3QpackContext peer = Http3QpackContext.create(4_096, 16, 16_384, _ -> {
            });
            context.encoderInstructionsSender(peer::onEncoderStreamData);
            peer.decoderInstructionsSender(context::onDecoderStreamData);
            context.peerSettings(4_096, 16);
            responseHeaders = responseHeaders();

            insertChangingDates(0, earlyChurnEntries);
            context.encodeHeaders(0, responseHeaders);
            byte[] dynamicResponse = context.encodeHeaders(0, responseHeaders);
            verifyDynamicResponse(dynamicResponse);
            context.onDecoderStreamData(sectionAcknowledgments[0]);

            if (lateChurnEntries > 0) {
                insertChangingDates(earlyChurnEntries, lateChurnEntries);
                dynamicResponse = context.encodeHeaders(0, responseHeaders);
                verifyDynamicResponse(dynamicResponse);
                context.onDecoderStreamData(sectionAcknowledgments[0]);
            }
        }

        private void insertChangingDates(int start, int count) {
            for (int i = start; i < start + count; i++) {
                context.encodeHeaders(0,
                                      List.of(HeaderValues.create("date",
                                                                  "Thu, 06 Aug 2026 13:00:00 GMT-" + i)));
            }
        }

        private void verifyDynamicResponse(byte[] dynamicResponse) {
            if (dynamicResponse[0] == 0) {
                throw new IllegalStateException("QPACK deep dynamic response was not primed");
            }
        }

        final byte[] encodeResponse() {
            int slot = nextStreamSlot.getAndIncrement() & (STREAM_SLOTS - 1);
            byte[] encoded = context.encodeHeaders(slot * 4L, responseHeaders);
            context.onDecoderStreamData(sectionAcknowledgments[slot]);
            return encoded;
        }
    }

    private static List<Header> requestHeaders() {
        return List.of(HeaderValues.create(":method", "GET"),
                       HeaderValues.create(":scheme", "https"),
                       HeaderValues.create(":authority", "localhost"),
                       HeaderValues.create(":path", "/baseline2?a=1&b=1"),
                       HeaderValues.create("user-agent", "h2load nghttp3/ngtcp2"),
                       HeaderValues.create("accept", "*/*"));
    }

    private static List<Header> responseHeaders() {
        return List.of(HeaderValues.create(":status", "200"),
                       HeaderValues.create("content-type", "text/plain"),
                       HeaderValues.create("content-length", "1"),
                       HeaderValues.create("date", "Wed, 05 Aug 2026 12:00:00 GMT"),
                       HeaderValues.create("server", "helidon"));
    }
}
