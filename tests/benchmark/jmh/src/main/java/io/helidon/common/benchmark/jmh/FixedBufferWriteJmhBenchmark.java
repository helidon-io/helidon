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

package io.helidon.common.benchmark.jmh;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import io.helidon.common.buffers.BufferData;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class FixedBufferWriteJmhBenchmark {
    @Benchmark
    public BufferData write(BufferState state) {
        state.source.rewind();
        state.destination.reset();
        state.destination.write(state.prefix);
        state.destination.write(state.source);
        return state.destination;
    }

    @Benchmark
    public BufferData writeLength(BufferState state) {
        state.source.rewind();
        state.destination.reset();
        state.destination.write(state.prefix);
        state.destination.write(state.source, state.size);
        return state.destination;
    }

    @State(Scope.Thread)
    public static class BufferState {
        @Param({"8192", "16384", "65536"})
        private int size;

        @Param({"fixed", "growing", "readOnly", "composite"})
        private String sourceType;

        @Param("17")
        private int destinationOffset;

        private byte[] payload;
        private byte[] prefix;
        private BufferData source;
        private BufferData destination;

        @Setup(Level.Trial)
        public void setup() {
            payload = new byte[size];
            for (int i = 0; i < size; i++) {
                payload[i] = (byte) (i * 31 + 7);
            }
            prefix = new byte[destinationOffset];
            Arrays.fill(prefix, (byte) 0x5a);
            source = switch (sourceType) {
                case "fixed" -> BufferData.create(payload);
                case "growing" -> {
                    var buffer = BufferData.growing(size);
                    buffer.write(payload);
                    yield buffer;
                }
                case "readOnly" -> BufferData.createReadOnly(payload, 0, size);
                case "composite" -> {
                    int chunkSize = size / 4;
                    var chunks = new BufferData[4];
                    for (int i = 0; i < chunks.length; i++) {
                        chunks[i] = BufferData.create(Arrays.copyOfRange(payload, i * chunkSize, (i + 1) * chunkSize));
                    }
                    yield BufferData.create(chunks);
                }
                default -> throw new IllegalArgumentException("Unknown source type: " + sourceType);
            };
            destination = BufferData.create(size + destinationOffset);
        }

        @TearDown(Level.Iteration)
        public void verify() {
            if (!source.consumed() || destination.available() != size + destinationOffset) {
                throw new IllegalStateException("Copy did not consume the source or fill the destination");
            }
            for (byte expected : prefix) {
                if (destination.read() != (expected & 0xff)) {
                    throw new IllegalStateException("Destination prefix changed");
                }
            }
            for (int i = 0; i < payload.length; i++) {
                if (destination.read() != (payload[i] & 0xff)) {
                    throw new IllegalStateException("Copied byte differs at index " + i);
                }
            }
        }
    }
}
