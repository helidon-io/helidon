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

package io.helidon.http.benchmark.jmh;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.util.concurrent.TimeUnit;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class HeaderSerializationJmhBenchmark {

    @Benchmark
    public int writeIndexedHeader(IndexedHeaderState state) {
        BufferData buffer = state.buffer;
        buffer.clear();
        state.header.writeHttp1Header(buffer);
        return buffer.available();
    }

    @Benchmark
    public int writeCachedHeader(CachedHeaderState state) {
        BufferData buffer = state.buffer;
        buffer.clear();
        state.header.writeHttp1Header(buffer);
        return buffer.available();
    }

    @Benchmark
    public int writeCustomHeader(CustomHeaderState state) {
        state.output.reset();
        state.header.writeHttp1Header(state.buffer);
        return state.output.size();
    }

    @State(Scope.Thread)
    public static class IndexedHeaderState {
        @Param({"CONTENT_LENGTH", "CONTENT_TYPE", "DATE", "SERVER", "CONNECTION"})
        public String headerName;

        private Header header;
        private BufferData buffer;

        @Setup
        public void setup() {
            buffer = BufferData.growing(128);
            header = switch (headerName) {
                case "CONTENT_LENGTH" -> HeaderValues.create(HeaderNames.CONTENT_LENGTH, "128");
                case "CONTENT_TYPE" -> HeaderValues.create(HeaderNames.CONTENT_TYPE, "application/json");
                case "DATE" -> HeaderValues.create(HeaderNames.DATE, "Fri, 05 Sep 2026 17:30:00 GMT");
                case "SERVER" -> HeaderValues.create(HeaderNames.SERVER, "Helidon");
                case "CONNECTION" -> HeaderValues.create(HeaderNames.CONNECTION, "keep-alive");
                default -> throw new IllegalArgumentException("Unknown header: " + headerName);
            };
        }
    }

    @State(Scope.Thread)
    public static class CachedHeaderState {
        @Param({"CONTENT_LENGTH_ZERO", "CONTENT_TYPE_JSON", "CONNECTION_KEEP_ALIVE", "ACCEPT_JSON",
                "X_CONTENT_TYPE_OPTIONS_NOSNIFF"})
        public String headerName;

        private Header header;
        private BufferData buffer;

        @Setup
        public void setup() {
            buffer = BufferData.growing(128);
            header = switch (headerName) {
                case "CONTENT_LENGTH_ZERO" -> HeaderValues.CONTENT_LENGTH_ZERO;
                case "CONTENT_TYPE_JSON" -> HeaderValues.CONTENT_TYPE_JSON;
                case "CONNECTION_KEEP_ALIVE" -> HeaderValues.CONNECTION_KEEP_ALIVE;
                case "ACCEPT_JSON" -> HeaderValues.ACCEPT_JSON;
                case "X_CONTENT_TYPE_OPTIONS_NOSNIFF" -> HeaderValues.X_CONTENT_TYPE_OPTIONS_NOSNIFF;
                default -> throw new IllegalArgumentException("Unknown header: " + headerName);
            };
        }
    }

    @State(Scope.Thread)
    public static class CustomHeaderState {
        private Header header;
        private BufferData buffer;
        private ByteArrayOutputStream output;

        @Setup
        public void setup() {
            header = HeaderValues.create("X-Custom", "v");
            output = new ByteArrayOutputStream(128);
            buffer = (BufferData) Proxy.newProxyInstance(BufferData.class.getClassLoader(),
                                                         new Class<?>[] {BufferData.class},
                                                         (proxy, _, arguments) -> {
                                                             Object value = arguments[0];
                                                             if (value instanceof byte[] bytes) {
                                                                 output.writeBytes(bytes);
                                                                 return null;
                                                             }
                                                             output.write((Integer) value);
                                                             return proxy;
                                                         });
        }
    }
}
