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

package io.helidon.http.http3.qpack;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.http2.Http2StaticTableBenchmarkAccess;

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
 * Compares actual HPACK and QPACK static-table operations, excluding header construction and wire encoding.
 * Each invocation performs one lookup using prebuilt inputs in a deterministic shuffled sequence.
 * Common HTTP/2 pseudoheader encoding bypasses these searches; exact-hit results describe table primitives only.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3, jvmArgsAppend = {"-Xms512m", "-Xmx512m"})
public class HttpStaticTableJmhBenchmark {
    private static final int INPUT_COUNT = 256;

    @Benchmark
    public Object hpackIndexedGet(IndexedState state) {
        return Http2StaticTableBenchmarkAccess.get(state.inputs[state.next()].hpackIndex());
    }

    @Benchmark
    public Object qpackIndexedGet(IndexedState state) {
        return QpackStaticTable.get(state.inputs[state.next()].qpackIndex());
    }

    /**
     * Benchmark-only array snapshot of the same QPACK entries, for isolating indexed collection access.
     *
     * @param state prebuilt indexed inputs and array
     * @return existing static entry
     */
    @Benchmark
    public Object qpackArrayGetControl(IndexedState state) {
        return state.array[state.inputs[state.next()].qpackIndex()];
    }

    @Benchmark
    public long hpackEncodingLookup(LookupState state) {
        var input = state.inputs[state.next()];
        return Http2StaticTableBenchmarkAccess.find(input.name(), input.value());
    }

    @Benchmark
    public long qpackEncodingLookup(LookupState state) {
        var input = state.inputs[state.next()];
        return qpackLookup(input.name().lowerCase(), input.value());
    }

    private static long qpackLookup(String name, String value) {
        var indices = QpackStaticTable.indices(name);
        if (indices == null) {
            return 0;
        }
        int exactIndex = indices.exactIndex(value);
        if (exactIndex >= 0) {
            return exactIndex + 1L;
        }
        return -(indices.nameIndex() + 1L);
    }

    private static LookupInput input(String name, String value, long hpackResult, long qpackResult) {
        // Use equal but non-interned values, and the same prebuilt HeaderName on both protocol paths.
        String copiedValue = new String(value.toCharArray());
        copiedValue.hashCode();
        return new LookupInput(HeaderNames.createFromLowercase(name), copiedValue, hpackResult, qpackResult);
    }

    /**
     * Valid wire indexes for common indexed fields and literal-name references.
     */
    @State(Scope.Thread)
    public static class IndexedState {
        @Param({"REQUEST", "RESPONSE"})
        public String workload;

        private IndexedInput[] inputs;
        private HeaderField[] array;
        private int cursor;

        @Setup
        public void setup() {
            IndexedInput[] corpus = switch (workload) {
                case "REQUEST" -> new IndexedInput[] {
                        new IndexedInput(":authority", 1, 0),
                        new IndexedInput(":method", 2, 17),
                        new IndexedInput(":scheme", 7, 23),
                        new IndexedInput(":path", 4, 1),
                        new IndexedInput("accept", 19, 29),
                        new IndexedInput("accept-encoding", 16, 31),
                        new IndexedInput("cookie", 32, 5),
                        new IndexedInput("user-agent", 58, 95)
                };
                case "RESPONSE" -> new IndexedInput[] {
                        new IndexedInput(":status", 8, 25),
                        new IndexedInput("content-type", 31, 46),
                        new IndexedInput("content-length", 28, 4),
                        new IndexedInput("cache-control", 24, 39),
                        new IndexedInput("content-encoding", 26, 43),
                        new IndexedInput("date", 33, 6),
                        new IndexedInput("etag", 34, 7),
                        new IndexedInput("server", 54, 92)
                };
                default -> throw new IllegalArgumentException("Unknown indexed workload: " + workload);
            };
            array = new HeaderField[QpackStaticTable.size()];
            for (int i = 0; i < array.length; i++) {
                array[i] = QpackStaticTable.get(i);
            }
            for (var input : corpus) {
                Http2StaticTableBenchmarkAccess.verifyEntry(input.hpackIndex(), input.name());
                HeaderField actual = QpackStaticTable.get(input.qpackIndex());
                if (!actual.name().equals(input.name()) || array[input.qpackIndex()] != actual) {
                    throw new IllegalStateException("Unexpected QPACK entry for " + input + ": " + actual);
                }
            }
            inputs = new IndexedInput[INPUT_COUNT];
            var random = new Random(42);
            for (int i = 0; i < inputs.length; i++) {
                inputs[i] = corpus[random.nextInt(corpus.length)];
            }
        }

        private int next() {
            cursor = (cursor + 1) & (INPUT_COUNT - 1);
            return cursor;
        }
    }

    /**
     * Shared semantic inputs with independently specified expected matches for each protocol.
     * Positive results encode exact index plus one, negative results name index plus one, and zero means absent.
     */
    @State(Scope.Thread)
    public static class LookupState {
        @Param({"EXACT_COMMON", "NAME_ONLY", "UNKNOWN", "REQUEST_REGULAR", "RESPONSE_REGULAR"})
        public String workload;

        private LookupInput[] inputs;
        private int cursor;

        @Setup
        public void setup() {
            LookupInput[] corpus = switch (workload) {
                case "EXACT_COMMON" -> new LookupInput[] {
                        input(":method", "GET", 3, 18),
                        input(":method", "POST", 4, 21),
                        input(":scheme", "http", 7, 23),
                        input(":scheme", "https", 8, 24),
                        input(":path", "/", 5, 2),
                        input(":status", "200", 9, 26),
                        input(":status", "204", 10, 65),
                        input(":status", "404", 14, 28)
                };
                case "NAME_ONLY" -> new LookupInput[] {
                        input(":authority", "example.com", -2, -1),
                        input(":path", "/api/items?limit=10", -5, -2),
                        input(":method", "SEARCH", -3, -16),
                        input(":status", "418", -9, -25),
                        input("content-type", "application/octet-stream", -32, -45),
                        input("cache-control", "max-age=60", -25, -37),
                        input("user-agent", "helidon-jmh", -59, -96),
                        input("authorization", "Bearer example-token", -24, -85)
                };
                case "UNKNOWN" -> new LookupInput[] {
                        input("x-request-id", "0123456789abcdef", 0, 0),
                        input("x-trace-id", "fedcba9876543210", 0, 0),
                        input("x-tenant-id", "example", 0, 0),
                        input("x-api-version", "v1", 0, 0),
                        input("x-page-count", "12", 0, 0),
                        input("x-service-name", "catalog", 0, 0),
                        input("x-correlation-id", "benchmark", 0, 0),
                        input("x-feature-version", "2", 0, 0)
                };
                case "REQUEST_REGULAR" -> new LookupInput[] {
                        input("accept", "*/*", -20, 30),
                        input("accept-encoding", "gzip, deflate, br", -17, 32),
                        input("accept-language", "en-US", -18, -73),
                        input("authorization", "Bearer example-token", -24, -85),
                        input("cookie", "session=benchmark", -33, -6),
                        input("user-agent", "helidon-jmh", -59, -96),
                        input("if-none-match", "\"benchmark-v1\"", -42, -10),
                        input("x-request-id", "0123456789abcdef", 0, 0)
                };
                case "RESPONSE_REGULAR" -> new LookupInput[] {
                        input("content-type", "application/json", -32, 47),
                        input("content-length", "128", -29, -5),
                        input("cache-control", "no-cache", -25, 40),
                        input("content-encoding", "gzip", -27, 44),
                        input("date", "Tue, 15 Sep 2026 12:00:00 GMT", -34, -7),
                        input("etag", "\"benchmark-v1\"", -35, -8),
                        input("server", "helidon", -55, -93),
                        input("x-trace-id", "fedcba9876543210", 0, 0)
                };
                default -> throw new IllegalArgumentException("Unknown lookup workload: " + workload);
            };
            for (var input : corpus) {
                long hpack = Http2StaticTableBenchmarkAccess.find(input.name(), input.value());
                long qpack = qpackLookup(input.name().lowerCase(), input.value());
                if (hpack != input.hpackResult() || qpack != input.qpackResult()) {
                    throw new IllegalStateException("Unexpected matches for " + input + ": HPACK=" + hpack
                                                            + ", QPACK=" + qpack);
                }
            }
            inputs = new LookupInput[INPUT_COUNT];
            var random = new Random(42);
            for (int i = 0; i < inputs.length; i++) {
                inputs[i] = corpus[random.nextInt(corpus.length)];
            }
        }

        private int next() {
            cursor = (cursor + 1) & (INPUT_COUNT - 1);
            return cursor;
        }
    }

    private record IndexedInput(String name, int hpackIndex, int qpackIndex) {
    }

    private record LookupInput(HeaderName name, String value, long hpackResult, long qpackResult) {
    }
}
