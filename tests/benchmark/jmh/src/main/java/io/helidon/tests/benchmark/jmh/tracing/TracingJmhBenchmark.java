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

package io.helidon.tests.benchmark.jmh.tracing;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.helidon.common.LazyValue;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.service.registry.Services;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.providers.opentelemetry.OpenTelemetryDataPropagationProvider;
import io.helidon.tracing.providers.opentelemetry.OpenTelemetryDataPropagationProvider.OpenTelemetryContext;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmarks for tracing lookup paths used by request and client integrations.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
@Fork(1)
public class TracingJmhBenchmark {

    @Benchmark
    public Tracer servicesGetTracer() {
        return Services.get(Tracer.class);
    }

    @Benchmark
    public Tracer cachedTracer(TracingState state) {
        return state.tracer;
    }

    @Benchmark
    public Tracer contextFallbackCachedTracer(TracingState state) {
        return state.context.get(Tracer.class).orElseGet(state.applicationTracer);
    }

    @Benchmark
    public void servicesGetTracerSpanBuilder(Blackhole blackhole) {
        blackhole.consume(Services.get(Tracer.class).spanBuilder("benchmark"));
    }

    @Benchmark
    public void cachedTracerSpanBuilder(TracingState state, Blackhole blackhole) {
        blackhole.consume(state.tracer.spanBuilder("benchmark"));
    }

    @Benchmark
    public OpenTelemetryContext dataPropagationProviderData(TracingState state) {
        return state.dataPropagationProvider.data();
    }

    @Benchmark
    public OpenTelemetryContext dataPropagationProviderDataWithContextTracer(TracingState state) {
        return Contexts.runInContext(state.contextWithTracer, state.dataPropagationProvider::data);
    }

    @Benchmark
    public void contextAwareExecutorSubmit(ExecutorState state, Blackhole blackhole) throws Exception {
        blackhole.consume(state.executor.submit(state.task).get());
    }

    @Benchmark
    @Threads(4)
    public void contextAwareExecutorSubmitContended(ExecutorState state, Blackhole blackhole) throws Exception {
        blackhole.consume(state.executor.submit(state.task).get());
    }

    @State(Scope.Benchmark)
    public static class TracingState {
        private OpenTelemetryDataPropagationProvider dataPropagationProvider;
        private LazyValue<Tracer> applicationTracer;
        private Context context;
        private Context contextWithTracer;
        private Tracer tracer;

        @Setup(Level.Trial)
        public void setUp() {
            dataPropagationProvider = new OpenTelemetryDataPropagationProvider();
            applicationTracer = LazyValue.create(() -> Services.get(Tracer.class));
            context = Context.create();
            tracer = Services.get(Tracer.class);
            contextWithTracer = Context.create();
            contextWithTracer.register(tracer);
        }
    }

    @State(Scope.Benchmark)
    public static class ExecutorState {
        private ExecutorService delegate;
        private ExecutorService executor;
        private Runnable task;

        @Setup(Level.Trial)
        public void setUp() {
            delegate = Executors.newFixedThreadPool(4);
            executor = Contexts.wrap(delegate);
            task = () -> {
            };
            Services.get(Tracer.class);
        }

        @TearDown(Level.Trial)
        public void tearDown() throws InterruptedException {
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }
}
