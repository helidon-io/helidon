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
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.helidon.quic.OrderedFlow.ReassemblyBudget;
import io.helidon.quic.OrderedFlow.StreamDataFlow;
import io.helidon.quic.frame.StreamFrame;
import io.helidon.quic.packet.QuicPacketDecoder;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmarks QUIC paths whose public contracts explicitly represent an unavailable result.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@Threads(1)
public class QuicNullContractJmhBenchmark {
    private static final int FRAME_COUNT = 1024;
    private static final ReassemblyBudget UNBOUNDED_BUDGET = new ReassemblyBudget() {
        @Override
        public boolean tryAcquire() {
            return true;
        }

        @Override
        public void release(int count) {
        }
    };

    @Benchmark
    @OperationsPerInvocation(FRAME_COUNT)
    public void inOrderFlow(FlowState state, Blackhole blackhole) {
        StreamDataFlow flow = StreamDataFlow.create(UNBOUNDED_BUDGET, 0);
        for (StreamFrame frame : state.frames) {
            blackhole.consume(value(flow.receive(frame)));
        }
    }

    @Benchmark
    @OperationsPerInvocation(FRAME_COUNT)
    public void reorderedFlow(FlowState state, Blackhole blackhole) {
        StreamDataFlow flow = StreamDataFlow.create(UNBOUNDED_BUDGET, 0);
        for (int i = 0; i < state.frames.length; i += 2) {
            blackhole.consume(value(flow.receive(state.frames[i + 1])));
            blackhole.consume(value(flow.receive(state.frames[i])));
            blackhole.consume(value(flow.poll()));
        }
    }

    @Benchmark
    @OperationsPerInvocation(FRAME_COUNT)
    public void reorderedFlowProductionUsage(FlowState state, Blackhole blackhole) {
        StreamDataFlow flow = StreamDataFlow.create(UNBOUNDED_BUDGET, 0);
        for (int i = 0; i < state.frames.length; i += 2) {
            flow.receiveAvailable(state.frames[i + 1]);
            while (flow.hasAvailable()) {
                blackhole.consume(flow.takeAvailable());
                flow.pollAvailable();
            }
            flow.receiveAvailable(state.frames[i]);
            while (flow.hasAvailable()) {
                blackhole.consume(flow.takeAvailable());
                flow.pollAvailable();
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(FRAME_COUNT)
    public void reorderedFlowOptionalUsage(FlowState state, Blackhole blackhole) {
        StreamDataFlow flow = StreamDataFlow.create(UNBOUNDED_BUDGET, 0);
        for (int i = 0; i < state.frames.length; i += 2) {
            var readyFrame = flow.receive(state.frames[i + 1]);
            while (readyFrame.isPresent()) {
                blackhole.consume(readyFrame.orElseThrow());
                readyFrame = flow.poll();
            }
            readyFrame = flow.receive(state.frames[i]);
            while (readyFrame.isPresent()) {
                blackhole.consume(readyFrame.orElseThrow());
                readyFrame = flow.poll();
            }
        }
    }

    @Benchmark
    public Object validLongHeader(HeaderState state) {
        return value(QuicPacketDecoder.peekLongHeader(state.validLongHeader));
    }

    @Benchmark
    public Object validLongHeaderProductionUsage(HeaderState state) {
        return QuicPacketDecoder.peekLongHeader(state.validLongHeader).orElseThrow();
    }

    @Benchmark
    public Object malformedLongHeader(HeaderState state) {
        return value(QuicPacketDecoder.peekLongHeader(state.malformedLongHeader));
    }

    @Benchmark
    public Object validShortConnectionId(HeaderState state) {
        return value(QuicPacketDecoder.peekShortConnectionId(state.validShortHeader, 16));
    }

    @Benchmark
    public Object malformedShortConnectionId(HeaderState state) {
        return value(QuicPacketDecoder.peekShortConnectionId(state.malformedShortHeader, 16));
    }

    private static Object value(Object result) {
        if (result instanceof Optional<?> optional) {
            return optional.orElse(null);
        }
        if (result instanceof QuicPacketDecoder.LongHeaderResult headerResult) {
            return headerResult.isPresent() ? headerResult.orElseThrow() : null;
        }
        return result;
    }

    /**
     * Pre-created STREAM frames for ordered-flow measurements.
     */
    @State(Scope.Thread)
    public static class FlowState {
        private StreamFrame[] frames;

        @Setup(Level.Trial)
        public void setUp() {
            frames = new StreamFrame[FRAME_COUNT];
            for (int i = 0; i < frames.length; i++) {
                frames[i] = StreamFrame.createOwned(0, i, 1, false, ByteBuffer.wrap(new byte[] {(byte) i}));
            }
        }
    }

    /**
     * Stable packet-header buffers for decoder measurements.
     */
    @State(Scope.Thread)
    public static class HeaderState {
        private ByteBuffer validLongHeader;
        private ByteBuffer malformedLongHeader;
        private ByteBuffer validShortHeader;
        private ByteBuffer malformedShortHeader;

        @Setup(Level.Trial)
        public void setUp() {
            validLongHeader = ByteBuffer.allocate(23)
                    .put((byte) 0xc0)
                    .putInt(QuicVersion.QUIC_V1.versionNumber())
                    .put((byte) 8)
                    .putLong(1)
                    .put((byte) 8)
                    .putLong(2)
                    .flip()
                    .asReadOnlyBuffer();
            malformedLongHeader = ByteBuffer.allocate(6).asReadOnlyBuffer();
            validShortHeader = ByteBuffer.allocate(17)
                    .put((byte) 0x40)
                    .put(new byte[16])
                    .flip()
                    .asReadOnlyBuffer();
            malformedShortHeader = ByteBuffer.allocate(16).asReadOnlyBuffer();
        }
    }
}
