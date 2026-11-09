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

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.http.media.ReadableEntity;
import io.helidon.http.media.ReadableEntityBase;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Compares byte-array entity conversion with and without an exact transport length.
 * Both methods use the production {@link ReadableEntityBase} reader with a fresh entity and finite source per operation.
 * Payload storage is prepared outside measurement; source buffers wrap its slices without copying them.
 * The 1 MiB and 1 MiB plus one cases exercise the eager-allocation boundary and fallback.
 *
 * <p>This isolates entity allocation and copying, without sockets, decoders or filters.
 * It does not measure complete HTTP request throughput. Use an exact class include and the GC profiler to compare
 * {@code gc.alloc.rate.norm} as well as time per conversion.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class EntityByteArrayJmhBenchmark {
    @Param({"64", "16384", "1048576", "1048577"})
    public int bodyBytes;

    @Param({"1024", "16384"})
    public int chunkBytes;

    private byte[] body;

    @Setup
    public void setup() {
        if (bodyBytes < 0 || chunkBytes < 1) {
            throw new IllegalArgumentException("Body length must be nonnegative and source chunk length must be positive");
        }
        body = new byte[bodyBytes];
        for (int i = 0; i < body.length; i++) {
            body[i] = (byte) (i * 31 + 17);
        }
        if (!Arrays.equals(unknownLength(), body) || !Arrays.equals(exactLength(), body)) {
            throw new IllegalStateException("Entity conversion did not preserve the complete payload");
        }
    }

    @Benchmark
    public byte[] unknownLength() {
        return new BenchmarkEntity(new Source(body, chunkBytes), -1).as(byte[].class);
    }

    @Benchmark
    public byte[] exactLength() {
        return new BenchmarkEntity(new Source(body, chunkBytes), body.length).as(byte[].class);
    }

    private static final class BenchmarkEntity extends ReadableEntityBase {
        private final long exactLength;

        private BenchmarkEntity(Source source, long exactLength) {
            super(source, () -> { }, Long.MAX_VALUE);
            this.exactLength = exactLength;
        }

        @Override
        public ReadableEntity copy(Runnable entityProcessedRunnable) {
            throw new UnsupportedOperationException("Benchmark entities are created for each conversion");
        }

        @Override
        protected byte[] readAllBytes() {
            return exactLength < 0 ? super.readAllBytes() : super.readAllBytes(exactLength);
        }

        @Override
        protected <T> T entityAs(GenericType<T> type) {
            throw new UnsupportedOperationException("This benchmark exercises as(byte[].class)");
        }
    }

    private static final class Source implements Function<Integer, BufferData> {
        private final byte[] body;
        private final int chunkBytes;
        private int offset;

        private Source(byte[] body, int chunkBytes) {
            this.body = body;
            this.chunkBytes = chunkBytes;
        }

        @Override
        public BufferData apply(Integer estimate) {
            if (offset == body.length) {
                return BufferData.empty();
            }
            int count = Math.min(chunkBytes, body.length - offset);
            BufferData result = BufferData.createReadOnly(body, offset, count);
            offset += count;
            return result;
        }
    }
}
