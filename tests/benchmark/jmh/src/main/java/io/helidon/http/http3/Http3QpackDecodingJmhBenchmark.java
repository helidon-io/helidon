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

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.HuffmanCodec;
import io.helidon.common.buffers.PrefixedIntegerCodec;
import io.helidon.http.Header;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
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
 * Benchmarks limited QPACK decoding of an h2load-shaped Huffman-encoded request field section.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class Http3QpackDecodingJmhBenchmark {
    private static final int CONCURRENT_THREADS = 8;

    @Benchmark
    @Threads(1)
    public List<Header> decodeRequestHuffman(HuffmanRequestState state) {
        return state.decode();
    }

    @Benchmark
    @Threads(CONCURRENT_THREADS)
    public List<Header> decodeRequestHuffmanConcurrent(HuffmanRequestState state) {
        return state.decode();
    }

    /**
     * Shared connection-level decoder state and immutable encoded request bytes.
     */
    @State(Scope.Benchmark)
    public static class HuffmanRequestState {
        private static final int MAX_HEADERS_SIZE = 16_384;
        private static final int STATIC_AUTHORITY = 0;
        private static final int STATIC_PATH = 1;
        private static final int STATIC_METHOD_GET = 17;
        private static final int STATIC_SCHEME_HTTPS = 23;
        private static final int STATIC_ACCEPT_ANY = 29;
        private static final int STATIC_USER_AGENT = 95;

        private final AtomicLong nextStreamId = new AtomicLong();
        private final AtomicReference<Throwable> connectionFailure = new AtomicReference<>();
        private Http3QpackContext context;
        private byte[] encodedRequest;

        @Setup
        public void setup() {
            context = Http3QpackContext.create(0,
                                               0,
                                               MAX_HEADERS_SIZE,
                                               failure -> connectionFailure.compareAndSet(null, failure));
            encodedRequest = encodedRequest();

            List<Header> decoded = decode();
            if (decoded.size() != 6
                    || !decoded.get(2).get().equals("localhost")
                    || !decoded.get(3).get().equals("/baseline2?a=1&b=1")
                    || !decoded.get(4).get().equals("h2load nghttp3/ngtcp2")) {
                throw new IllegalStateException("Unexpected QPACK benchmark request: " + decoded);
            }
        }

        @TearDown
        public void tearDown() {
            Throwable failure = connectionFailure.get();
            context.close(new IllegalStateException("QPACK decoding benchmark complete"));
            if (failure != null) {
                throw new IllegalStateException("QPACK decoding benchmark connection failed", failure);
            }
        }

        private List<Header> decode() {
            Http3QpackContext.Stream stream = context.openStream(nextStreamId.getAndAdd(4));
            try {
                return stream.decodeHeaderLines(BufferData.createReadOnly(encodedRequest, 0, encodedRequest.length),
                                                MAX_HEADERS_SIZE);
            } finally {
                stream.complete();
            }
        }

        private static byte[] encodedRequest() {
            BufferData output = BufferData.growing(128);
            PrefixedIntegerCodec.writeLong(output, 0, 0, 8);
            PrefixedIntegerCodec.writeLong(output, 0, 0, 7);
            writeStaticIndexed(output, STATIC_METHOD_GET);
            writeStaticIndexed(output, STATIC_SCHEME_HTTPS);
            writeHuffmanValue(output, STATIC_AUTHORITY, "localhost");
            writeHuffmanValue(output, STATIC_PATH, "/baseline2?a=1&b=1");
            writeHuffmanValue(output, STATIC_USER_AGENT, "h2load nghttp3/ngtcp2");
            writeStaticIndexed(output, STATIC_ACCEPT_ANY);
            return output.readBytes();
        }

        private static void writeStaticIndexed(BufferData output, long index) {
            PrefixedIntegerCodec.writeLong(output, index, 0b1100_0000, 6);
        }

        private static void writeHuffmanValue(BufferData output, long staticNameIndex, String value) {
            PrefixedIntegerCodec.writeLong(output, staticNameIndex, 0b0101_0000, 4);
            byte[] encodedValue = new byte[HuffmanCodec.encodedLength(value)];
            HuffmanCodec.encode(value, encodedValue);
            PrefixedIntegerCodec.writeLong(output, encodedValue.length, 0b1000_0000, 7);
            output.write(encodedValue);
        }
    }
}
