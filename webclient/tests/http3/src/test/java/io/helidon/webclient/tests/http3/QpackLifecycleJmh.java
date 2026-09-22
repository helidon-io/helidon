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

package io.helidon.webclient.tests.http3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http3.Http3QpackContext;

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
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Threads(1)
public class QpackLifecycleJmh {
    private static final long MAX_TABLE_CAPACITY = 256;

    public static void main(String[] args) throws Exception {
        // Invocation fixtures are excluded from timing, but GC profiling includes their allocations.
        // Do not enable GC profiling automatically for these timing-oriented workloads.
        Options options = new OptionsBuilder()
                .include(QpackLifecycleJmh.class.getSimpleName())
                .shouldFailOnError(true)
                .build();
        new Runner(options).run();
    }

    @Benchmark
    public Headers unblocked(UnblockedState state) {
        Http3QpackContext.Stream stream = state.decoder.openStream(state.nextStreamId);
        state.nextStreamId += 4;
        try {
            return stream.decodeHeaders(BufferData.create(state.fieldSection), -1);
        } finally {
            stream.complete();
        }
    }

    @Benchmark
    public void blockedResumption(BlockedState state, Blackhole blackhole) {
        for (byte[] instruction : state.encoderInstructions) {
            if (state.fragmented) {
                for (byte value : instruction) {
                    state.decoder.onEncoderStreamData(new byte[] {value});
                }
            } else {
                state.decoder.onEncoderStreamData(instruction);
            }
        }
        int decodedFields = 0;
        for (CompletableFuture<Headers> result : state.results) {
            decodedFields += result.join().size();
        }
        state.streams.forEach(Http3QpackContext.Stream::complete);
        blackhole.consume(decodedFields);
    }

    @State(Scope.Thread)
    public static class UnblockedState {
        @Param({"1", "16", "128"})
        int headerCount;

        private Http3QpackContext decoder;
        private byte[] fieldSection;
        private long nextStreamId;

        @Setup(Level.Trial)
        public void setup() {
            decoder = Http3QpackContext.create(0, 0, 16_384, _ -> {
            });
            WritableHeaders<?> headers = WritableHeaders.create();
            for (int i = 0; i < headerCount; i++) {
                headers.add(HeaderValues.create("x-benchmark-" + i, "value-" + i));
            }
            fieldSection = decoder.encodeHeaders(0, headers);
        }
    }

    @State(Scope.Thread)
    public static class BlockedState {
        @Param({"1", "16", "128"})
        int blockedStreams;

        @Param({"false", "true"})
        boolean fragmented;

        private List<byte[]> encoderInstructions;
        private byte[] fieldSection;
        private Http3QpackContext decoder;
        private List<Http3QpackContext.Stream> streams;
        private List<CompletableFuture<Headers>> results;

        @Setup(Level.Trial)
        public void prepareFieldSection() {
            Http3QpackContext encoder = Http3QpackContext.create(0, 0, 16_384, _ -> {
            });
            List<byte[]> instructions = new ArrayList<>();
            encoder.encoderInstructionsSender(instructions::add);
            encoder.peerSettings(MAX_TABLE_CAPACITY, 128);
            WritableHeaders<?> headers = WritableHeaders.create()
                    .add(HeaderValues.create("x-benchmark-dynamic", "value"));
            encoder.encodeHeaders(0, headers);

            Http3QpackContext primingDecoder = Http3QpackContext.create(MAX_TABLE_CAPACITY, 128, 16_384, _ -> {
            });
            primingDecoder.decoderInstructionsSender(encoder::onDecoderStreamData);
            for (byte[] instruction : instructions) {
                primingDecoder.onEncoderStreamData(instruction);
            }
            fieldSection = encoder.encodeHeaders(4, headers);
            encoderInstructions = instructions.stream().map(byte[]::clone).toList();
        }

        @Setup(Level.Invocation)
        public void blockFieldSections() {
            decoder = Http3QpackContext.create(MAX_TABLE_CAPACITY, blockedStreams, 16_384, _ -> {
            });
            decoder.decoderInstructionsSender(_ -> {
            });
            streams = new ArrayList<>(blockedStreams);
            results = new ArrayList<>(blockedStreams);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            for (int i = 0; i < blockedStreams; i++) {
                Http3QpackContext.Stream stream = decoder.openStream(i * 4L);
                CompletableFuture<Headers> result = new CompletableFuture<>();
                streams.add(stream);
                results.add(result);
                Thread thread = Thread.ofVirtual().start(() -> {
                    try {
                        result.complete(stream.decodeHeaders(BufferData.create(fieldSection), -1));
                    } catch (Throwable t) {
                        result.completeExceptionally(t);
                    }
                });
                while (thread.getState() != Thread.State.WAITING
                        && !result.isDone()
                        && System.nanoTime() < deadline) {
                    Thread.onSpinWait();
                }
                if (thread.getState() != Thread.State.WAITING) {
                    throw new IllegalStateException("QPACK benchmark decoder did not block");
                }
            }
        }

        @TearDown(Level.Invocation)
        public void cancelIncompleteStreams() {
            streams.forEach(Http3QpackContext.Stream::cancel);
        }
    }
}
