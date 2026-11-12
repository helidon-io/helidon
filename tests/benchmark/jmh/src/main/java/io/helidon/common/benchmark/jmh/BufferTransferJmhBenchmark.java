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
public class BufferTransferJmhBenchmark {
    private static final int DESTINATION_OFFSET = 17;
    private static final byte[] PREFIX = new byte[DESTINATION_OFFSET];

    static {
        Arrays.fill(PREFIX, (byte) 0x5a);
    }

    @Benchmark
    public BufferData growingWrite(GrowingState state) {
        state.source.rewind();
        state.destination.reset();
        state.destination.write(PREFIX);
        state.destination.write(state.source);
        return state.destination;
    }

    @Benchmark
    public BufferData growingWriteLength(GrowingState state) {
        state.source.rewind();
        state.destination.reset();
        state.destination.write(PREFIX);
        state.destination.write(state.source, state.size);
        return state.destination;
    }

    @Benchmark
    public BufferData writeFromShared(SharedState state) {
        state.destination.reset();
        state.destination.write(PREFIX);
        BufferData.writeFromShared(state.destination, state.payload);
        return state.destination;
    }

    private static byte[] payload(int size) {
        byte[] payload = new byte[size];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i * 31 + 7);
        }
        return payload;
    }

    private static void verifyDestination(BufferData destination, byte[] payload) {
        if (destination.available() != payload.length + DESTINATION_OFFSET) {
            throw new IllegalStateException("Copy did not fill the destination");
        }
        for (byte expected : PREFIX) {
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

    @State(Scope.Thread)
    public static class GrowingState {
        @Param({"8192", "65536"})
        private int size;

        @Param({"fixed", "composite"})
        private String sourceType;

        private byte[] payload;
        private BufferData source;
        private BufferData destination;

        @Setup(Level.Trial)
        public void setup() {
            payload = payload(size);
            source = switch (sourceType) {
                case "fixed" -> BufferData.create(payload);
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
            destination = BufferData.growing(size + DESTINATION_OFFSET);
        }

        @TearDown(Level.Iteration)
        public void verify() {
            if (!source.consumed()) {
                throw new IllegalStateException("Copy did not consume the source");
            }
            verifyDestination(destination, payload);
        }
    }

    @State(Scope.Thread)
    public static class SharedState {
        @Param({"32", "1024"})
        private int sharedSize;

        @Param({"fixed", "growing"})
        private String destinationType;

        private byte[] payload;
        private BufferData destination;

        @Setup(Level.Trial)
        public void setup() {
            payload = payload(sharedSize);
            destination = switch (destinationType) {
                case "fixed" -> BufferData.create(sharedSize + DESTINATION_OFFSET);
                case "growing" -> BufferData.growing(sharedSize + DESTINATION_OFFSET);
                default -> throw new IllegalArgumentException("Unknown destination type: " + destinationType);
            };
        }

        @TearDown(Level.Iteration)
        public void verify() {
            verifyDestination(destination, payload);
        }
    }
}
