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

package io.helidon.quic;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import io.helidon.common.buffers.BufferData;
import io.helidon.quic.frame.StreamFrame;

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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmarks QUIC STREAM payload transfer into the application-ready queue.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@Threads(1)
public class QuicStreamIngressJmhBenchmark {

    @Benchmark
    public BufferData materializedReadyQueue(IngressState state) {
        ByteBuffer payload = state.heapFrame.payload();
        byte[] bytes = new byte[payload.remaining()];
        payload.get(bytes);
        state.queue.add(BufferData.create(bytes));
        return state.queue.remove();
    }

    @Benchmark
    public BufferData ownedHeapReadyQueue(IngressState state) {
        state.queue.add(state.heapFrame.ownedPayloadData());
        return state.queue.remove();
    }

    @Benchmark
    public BufferData ownedDirectFallbackReadyQueue(IngressState state) {
        state.queue.add(state.directFrame.ownedPayloadData());
        return state.queue.remove();
    }

    /**
     * Per-thread ingress frames and application-ready queue.
     */
    @State(Scope.Thread)
    public static class IngressState {
        /**
         * STREAM payload size.
         */
        @Param({"1024", "16384", "64000"})
        public int payloadSize;

        private StreamFrame heapFrame;
        private StreamFrame directFrame;
        private ConcurrentLinkedQueue<BufferData> queue;

        @Setup(Level.Trial)
        public void setUp() {
            ByteBuffer direct = ByteBuffer.allocateDirect(payloadSize);
            direct.position(payloadSize).flip();
            heapFrame = StreamFrame.createOwned(0, 0, payloadSize, false, ByteBuffer.wrap(new byte[payloadSize]));
            directFrame = StreamFrame.createOwned(0, 0, payloadSize, false, direct);
            queue = new ConcurrentLinkedQueue<>();
        }
    }
}
