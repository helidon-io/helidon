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

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import io.helidon.quic.packet.QuicPacket;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmarks QUIC endpoint-route lifecycle allocation and transitions.
 *
 * <p>The direct lifecycle calls model operations already serialized by the endpoint route lock. They intentionally exclude
 * route-lock acquisition and contention, which are covered by the complete server-runtime benchmarks.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class QuicRouteLifecycleJmhBenchmark {
    private static final IllegalStateException REMOVAL_FAILURE =
            new IllegalStateException("benchmark route removal failure");

    /**
     * Measures construction and registration without a removal observer.
     *
     * @return registered lifecycle
     */
    @Benchmark
    public QuicEndpointRouteLifecycle registerUnobserved() {
        return registeredLifecycle();
    }

    /**
     * Measures removal observation before endpoint registration.
     *
     * @return completed removal
     */
    @Benchmark
    public CompletionStage<Void> observedBeforeRegistration(Blackhole blackhole) {
        QuicEndpointRouteLifecycle lifecycle = new QuicEndpointRouteLifecycle(Receiver.CONNECTION);
        CompletionStage<Void> removed = lifecycle.whenRemoved();
        blackhole.consume(lifecycle);
        return removed;
    }

    /**
     * Measures ownership transfer and successful removal without an observer.
     *
     * @return removed lifecycle
     */
    @Benchmark
    public QuicEndpointRouteLifecycle transferAndRemoveUnobserved() {
        QuicEndpointRouteLifecycle lifecycle = registeredLifecycle();
        if (!lifecycle.transfer(Receiver.CONNECTION, Receiver.CLOSED)
                || !lifecycle.beginRemoval(Receiver.CLOSED)) {
            throw new IllegalStateException("Failed to transfer benchmark lifecycle");
        }
        lifecycle.completeRemoval();
        return lifecycle;
    }

    /**
     * Measures successful removal with an observer installed before completion.
     *
     * @return removal completion
     */
    @Benchmark
    public CompletionStage<Void> observedSuccessfulRemoval(Blackhole blackhole) {
        QuicEndpointRouteLifecycle lifecycle = registeredLifecycle();
        CompletionStage<Void> removed = lifecycle.whenRemoved();
        if (!lifecycle.beginRemoval(Receiver.CONNECTION)) {
            throw new IllegalStateException("Failed to remove benchmark lifecycle");
        }
        lifecycle.completeRemoval();
        blackhole.consume(lifecycle);
        return removed;
    }

    /**
     * Measures exceptional removal observed after completion.
     *
     * @return failed removal completion
     */
    @Benchmark
    public CompletionStage<Void> observedExceptionalRemoval(Blackhole blackhole) {
        QuicEndpointRouteLifecycle lifecycle = registeredLifecycle();
        if (!lifecycle.beginRemoval(Receiver.CONNECTION)) {
            throw new IllegalStateException("Failed to remove benchmark lifecycle");
        }
        lifecycle.completeRemovalExceptionally(REMOVAL_FAILURE);
        CompletionStage<Void> removed = lifecycle.whenRemoved();
        blackhole.consume(lifecycle);
        return removed;
    }

    private static QuicEndpointRouteLifecycle registeredLifecycle() {
        QuicEndpointRouteLifecycle lifecycle = new QuicEndpointRouteLifecycle(Receiver.CONNECTION);
        if (!lifecycle.register(Receiver.CONNECTION, 1)) {
            throw new IllegalStateException("Failed to register benchmark lifecycle");
        }
        return lifecycle;
    }

    private enum Receiver implements QuicPacketReceiver {
        CONNECTION,
        CLOSED;

        @Override
        public List<QuicConnectionId> connectionIds() {
            return List.of();
        }

        @Override
        public List<PeerResetToken> activeResetTokens() {
            return List.of();
        }

        @Override
        public void processIncoming(SocketAddress source,
                                    ByteBuffer destConnId,
                                    QuicPacket.HeadersType headersType,
                                    ByteBuffer buffer) {
        }

        @Override
        public void onWriteError(Throwable t) {
        }

        @Override
        public void processStatelessReset() {
        }

        @Override
        public void shutdown() {
        }
    }
}
