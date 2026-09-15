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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.PrefixedIntegerCodec;
import io.helidon.http.Header;

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
 * Benchmarks one complete QPACK encoder-stream literal insertion delivered in prebuilt fragments.
 *
 * <p>Each thread reuses a connection with an active decoder table. Repeated insertions evict old entries; fixture construction,
 * capacity updates, and verification by decoding the newest entry happen outside measurement. Each operation includes every
 * fragment of one instruction, including incomplete-instruction buffering and the final table insertion.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@Threads(1)
public class Http3QpackInstructionIngressJmhBenchmark {
    /**
     * Process every fragment of one encoder instruction on the reusable connection.
     *
     * @param state per-thread encoder-stream state
     * @return number of complete instructions submitted
     */
    @Benchmark
    public long processEncoderInstruction(EncoderState state) {
        return state.insert();
    }

    /**
     * Per-thread connection state and immutable fragments of a plain literal insertion.
     */
    @State(Scope.Thread)
    public static class EncoderState {
        private static final int TABLE_CAPACITY = 4096;
        private static final int MAX_HEADERS_SIZE = 4096;

        /**
         * Header-name length, including short and extended prefixed-integer encodings.
         */
        @Param({"8", "256"})
        public int nameSize;

        /**
         * Literal header-value length.
         */
        @Param({"64", "1024"})
        public int valueSize;

        /**
         * Fragment size in bytes, or {@code whole} for one complete instruction.
         */
        @Param({"1", "64", "whole"})
        public String fragmentation;

        private Http3QpackContext context;
        private byte[][] fragments;
        private String name;
        private String value;
        private long completedInstructions;
        private long nextStreamId;
        private Throwable connectionFailure;

        /**
         * Build the wire fixture, enable the decoder table, and validate insertion through eviction.
         */
        @Setup(Level.Trial)
        public void setup() {
            if (nameSize < 2 || valueSize < 0 || 32L + nameSize + valueSize > TABLE_CAPACITY) {
                throw new IllegalArgumentException("Unsupported literal sizes: name=" + nameSize + ", value=" + valueSize);
            }
            name = "x-" + "n".repeat(nameSize - 2);
            value = "v".repeat(valueSize);
            byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
            byte[] valueBytes = value.getBytes(StandardCharsets.US_ASCII);
            BufferData output = BufferData.growing(nameSize + valueSize + 16);
            PrefixedIntegerCodec.writeLong(output, nameBytes.length, 0b0100_0000, 5);
            output.write(nameBytes);
            PrefixedIntegerCodec.writeLong(output, valueBytes.length, 0, 7);
            output.write(valueBytes);
            byte[] instruction = output.readBytes();
            int fragmentSize = fragmentation.equals("whole") ? instruction.length : Integer.parseInt(fragmentation);
            if (fragmentSize <= 0) {
                throw new IllegalArgumentException("Fragment size must be positive: " + fragmentation);
            }
            int fragmentCount = (instruction.length - 1) / fragmentSize + 1;
            fragments = new byte[fragmentCount][];
            for (int i = 0; i < fragmentCount; i++) {
                int offset = i * fragmentSize;
                fragments[i] = Arrays.copyOfRange(instruction, offset, Math.min(offset + fragmentSize, instruction.length));
            }

            context = Http3QpackContext.create(TABLE_CAPACITY, 0, MAX_HEADERS_SIZE, failure -> connectionFailure = failure);
            context.decoderInstructionsSender(_ -> {
            });
            BufferData capacity = BufferData.growing(8);
            PrefixedIntegerCodec.writeLong(capacity, TABLE_CAPACITY, 0b0010_0000, 5);
            context.onEncoderStreamData(capacity.readBytes());
            int residentEntries = TABLE_CAPACITY / (32 + nameSize + valueSize);
            for (int i = 0; i <= residentEntries; i++) {
                insert();
            }
            verifyNewestEntry();
        }

        /**
         * Validate the measured insertion count through the newest dynamic entry and release the connection state.
         */
        @TearDown(Level.Trial)
        public void tearDown() {
            if (context == null) {
                return;
            }
            try {
                if (connectionFailure != null) {
                    throw new IllegalStateException("QPACK instruction ingress failed", connectionFailure);
                }
                verifyNewestEntry();
            } finally {
                context.close(new IllegalStateException("QPACK instruction ingress benchmark complete"));
            }
        }

        private long insert() {
            for (byte[] fragment : fragments) {
                context.onEncoderStreamData(fragment);
            }
            return ++completedInstructions;
        }

        private void verifyNewestEntry() {
            BufferData section = BufferData.growing(16);
            long fullRange = 2L * (TABLE_CAPACITY / 32);
            PrefixedIntegerCodec.writeLong(section, completedInstructions % fullRange + 1, 0, 8);
            PrefixedIntegerCodec.writeLong(section, 0, 0, 7);
            PrefixedIntegerCodec.writeLong(section, 0, 0b1000_0000, 6);
            Http3QpackContext.Stream stream = context.openStream(nextStreamId);
            nextStreamId += 4;
            try {
                List<Header> decoded = stream.decodeHeaderLines(section, MAX_HEADERS_SIZE);
                if (decoded.size() != 1
                        || !decoded.getFirst().headerName().lowerCase().equals(name)
                        || !decoded.getFirst().get().equals(value)) {
                    throw new IllegalStateException("Unexpected dynamic entry after " + completedInstructions
                                                            + " insertions: " + decoded);
                }
            } finally {
                stream.complete();
            }
        }
    }
}
