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
import java.util.concurrent.TimeUnit;

import io.helidon.common.buffers.BufferData;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmarks plain QPACK literal decoding, including decoded-size accounting and Latin-1 octets above ASCII.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class Http3QpackLiteralDecodingJmhBenchmark {
    @Benchmark
    public String decodeLiteral(LiteralState state) {
        return QpackCodec.decodeStringBytes(BufferData.create(state.value),
                                           false,
                                           QpackCodec.fieldSectionSizeTracker(state.bounded ? state.length : -1));
    }

    /**
     * Complete string payload, matching the buffer supplied by field-section and encoder-stream decoding.
     */
    @State(Scope.Thread)
    public static class LiteralState {
        @Param({"16", "64", "256"})
        public int length;

        @Param({"ASCII", "LATIN1"})
        public String content;

        @Param({"false", "true"})
        public boolean bounded;

        private byte[] value;

        @Setup
        public void setup() {
            String pattern = switch (content) {
                case "ASCII" -> "header/value-0123456789";
                case "LATIN1" -> "\u0080\u00ff\u00a3\u00e9";
                default -> throw new IllegalArgumentException("Unknown content: " + content);
            };
            String expected = pattern.repeat((length + pattern.length() - 1) / pattern.length()).substring(0, length);
            value = expected.getBytes(StandardCharsets.ISO_8859_1);
            String decoded = QpackCodec.decodeStringBytes(BufferData.create(value),
                                                          false,
                                                          QpackCodec.fieldSectionSizeTracker(bounded ? length : -1));
            if (!decoded.equals(expected)) {
                throw new IllegalStateException("Unexpected QPACK literal: " + decoded);
            }
        }
    }
}
