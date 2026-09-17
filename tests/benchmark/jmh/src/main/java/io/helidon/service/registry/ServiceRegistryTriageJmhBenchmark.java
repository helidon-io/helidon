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

package io.helidon.service.registry;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.helidon.common.types.ResolvedType;

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
import org.openjdk.jmh.annotations.Warmup;

/**
 * Event registration, dispatch, and qualified-instance creation benchmarks.
 * Registration batches include construction of a fresh manager to keep the listener count bounded;
 * the zero-listener case measures that construction alone.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
@Fork(1)
public class ServiceRegistryTriageJmhBenchmark {
    private static final ResolvedType EVENT_TYPE = ResolvedType.create(Payload.class);
    private static final Set<Qualifier> EVENT_QUALIFIERS = Set.of(Qualifier.createNamed("benchmark"));
    private static final Consumer<Payload> LISTENER = payload -> payload.deliveries++;

    @Benchmark
    public long emit(EventState state) {
        state.manager.emit(EVENT_TYPE, state.payload, EVENT_QUALIFIERS);
        return state.payload.deliveries;
    }

    @Benchmark
    public long emitAsyncAwaited(EventState state) {
        return state.manager.emitAsync(EVENT_TYPE, state.payload, EVENT_QUALIFIERS)
                .toCompletableFuture()
                .join()
                .deliveries;
    }

    @Benchmark
    public EventManager createManagerAndRegisterBatch(EventState state) {
        var manager = new EventManagerImpl(List::of, Optional.of(state.executor));
        for (int i = 0; i < state.listenerCount; i++) {
            manager.register(EVENT_TYPE, LISTENER, EVENT_QUALIFIERS);
        }
        return manager;
    }

    @Benchmark
    public EventManager createManagerAndRegisterAsyncBatch(EventState state) {
        var manager = new EventManagerImpl(List::of, Optional.of(state.executor));
        for (int i = 0; i < state.listenerCount; i++) {
            manager.registerAsync(EVENT_TYPE, LISTENER, EVENT_QUALIFIERS);
        }
        return manager;
    }

    @Benchmark
    public Service.QualifiedInstance<Object> qualifiedInstanceImmutableQualifiers(QualifierState state) {
        return Service.QualifiedInstance.create(state.instance, state.immutableQualifiers);
    }

    @Benchmark
    public Service.QualifiedInstance<Object> qualifiedInstanceMutableQualifiers(QualifierState state) {
        return Service.QualifiedInstance.create(state.instance, state.mutableQualifiers);
    }

    @State(Scope.Thread)
    public static class EventState {
        @Param({"0", "1", "8"})
        public int listenerCount;

        private ExecutorService executor;
        private EventManagerImpl manager;
        private Payload payload;

        @Setup(Level.Trial)
        public void setUp() {
            executor = Executors.newVirtualThreadPerTaskExecutor();
            manager = new EventManagerImpl(List::of, Optional.of(executor));
            payload = new Payload();
            for (int i = 0; i < listenerCount; i++) {
                manager.register(EVENT_TYPE, LISTENER, EVENT_QUALIFIERS);
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            executor.close();
        }
    }

    @State(Scope.Thread)
    public static class QualifierState {
        private final Object instance = new Object();
        private final Set<Qualifier> immutableQualifiers = Set.of(Qualifier.createNamed("first"),
                                                                Qualifier.createNamed("second"));
        private final Set<Qualifier> mutableQualifiers = new HashSet<>(immutableQualifiers);
    }

    private static final class Payload {
        private long deliveries;
    }
}
