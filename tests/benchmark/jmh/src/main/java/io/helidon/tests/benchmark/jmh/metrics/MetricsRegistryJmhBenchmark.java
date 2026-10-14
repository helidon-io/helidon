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

package io.helidon.tests.benchmark.jmh.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;

import io.helidon.config.Config;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.spi.MetricsFactoryProvider;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

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
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.infra.ThreadParams;

/**
 * Registry operations through the same Helidon API with explicitly selected Helidon and Micrometer providers.
 * Each registry contains {@code meterCount} counters with one shared name and two tags per counter.
 * One or eight JMH caller threads rotate through those identities for registration reuse and lookup hits;
 * lookup misses use absent tag values under the same registered name.
 * Create/remove uses one disjoint, reusable identity per caller, so cardinality is bounded by
 * {@code meterCount} plus the caller count. Each create/remove operation includes both registration and removal.
 * Tag identities are prepared before measurement, while counter builders are created within registration operations.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class MetricsRegistryJmhBenchmark {
    private static final String HELIDON_FACTORY_PROVIDER =
            "io.helidon.metrics.providers.helidon.HelidonMetricsFactoryProvider";
    private static final String MICROMETER_FACTORY_PROVIDER =
            "io.helidon.metrics.providers.micrometer.MicrometerMetricsFactoryProvider";
    private static final String METER_NAME = "registry.counter";
    private static final int MULTI_THREADS = 8;

    @Benchmark
    @Threads(1)
    public void helidonRegistryGetOrCreateHitSingle(HelidonState state, CallerState caller, Blackhole blackhole) {
        getOrCreateHit(state, caller, blackhole);
    }

    @Benchmark
    @Threads(1)
    public void micrometerRegistryGetOrCreateHitSingle(MicrometerState state, CallerState caller, Blackhole blackhole) {
        getOrCreateHit(state, caller, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void helidonRegistryGetOrCreateHitMulti(HelidonState state, CallerState caller, Blackhole blackhole) {
        getOrCreateHit(state, caller, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void micrometerRegistryGetOrCreateHitMulti(MicrometerState state, CallerState caller, Blackhole blackhole) {
        getOrCreateHit(state, caller, blackhole);
    }

    @Benchmark
    @Threads(1)
    public void helidonRegistryLookupHitSingle(HelidonState state, CallerState caller, Blackhole blackhole) {
        lookupHit(state, caller, blackhole);
    }

    @Benchmark
    @Threads(1)
    public void micrometerRegistryLookupHitSingle(MicrometerState state, CallerState caller, Blackhole blackhole) {
        lookupHit(state, caller, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void helidonRegistryLookupHitMulti(HelidonState state, CallerState caller, Blackhole blackhole) {
        lookupHit(state, caller, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void micrometerRegistryLookupHitMulti(MicrometerState state, CallerState caller, Blackhole blackhole) {
        lookupHit(state, caller, blackhole);
    }

    @Benchmark
    @Threads(1)
    public void helidonRegistryLookupMissSingle(HelidonState state, CallerState caller, Blackhole blackhole) {
        lookupMiss(state, caller, blackhole);
    }

    @Benchmark
    @Threads(1)
    public void micrometerRegistryLookupMissSingle(MicrometerState state, CallerState caller, Blackhole blackhole) {
        lookupMiss(state, caller, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void helidonRegistryLookupMissMulti(HelidonState state, CallerState caller, Blackhole blackhole) {
        lookupMiss(state, caller, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void micrometerRegistryLookupMissMulti(MicrometerState state, CallerState caller, Blackhole blackhole) {
        lookupMiss(state, caller, blackhole);
    }

    @Benchmark
    @Threads(1)
    public void helidonRegistryCreateRemoveSingle(HelidonState state, CallerState caller, Blackhole blackhole) {
        createRemove(state, caller, blackhole);
    }

    @Benchmark
    @Threads(1)
    public void micrometerRegistryCreateRemoveSingle(MicrometerState state, CallerState caller, Blackhole blackhole) {
        createRemove(state, caller, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void helidonRegistryCreateRemoveMulti(HelidonState state, CallerState caller, Blackhole blackhole) {
        createRemove(state, caller, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void micrometerRegistryCreateRemoveMulti(MicrometerState state, CallerState caller, Blackhole blackhole) {
        createRemove(state, caller, blackhole);
    }

    private static void getOrCreateHit(RegistryState state, CallerState caller, Blackhole blackhole) {
        List<Tag> tags = state.meterTags.get(caller.nextIndex(state.meterCount));
        blackhole.consume(state.registry.getOrCreate(state.factory.counterBuilder(METER_NAME).tags(tags)));
    }

    private static void lookupHit(RegistryState state, CallerState caller, Blackhole blackhole) {
        List<Tag> tags = state.meterTags.get(caller.nextIndex(state.meterCount));
        blackhole.consume(state.registry.meter(Counter.class, METER_NAME, tags));
    }

    private static void lookupMiss(RegistryState state, CallerState caller, Blackhole blackhole) {
        List<Tag> tags = state.missingTags.get(caller.nextIndex(state.meterCount));
        blackhole.consume(state.registry.meter(Counter.class, METER_NAME, tags));
    }

    private static void createRemove(RegistryState state, CallerState caller, Blackhole blackhole) {
        List<Tag> tags = state.createRemoveTags.get(caller.threadIndex);
        Counter counter = state.registry.getOrCreate(state.factory.counterBuilder(METER_NAME).tags(tags));
        blackhole.consume(state.registry.remove(counter));
    }

    private static MetricsFactoryProvider provider(String className) {
        return ServiceLoader.load(MetricsFactoryProvider.class)
                .stream()
                .filter(candidate -> candidate.type().getName().equals(className))
                .map(ServiceLoader.Provider::get)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No metrics factory provider " + className));
    }

    /**
     * Helidon provider registry state.
     */
    @State(Scope.Benchmark)
    public static class HelidonState extends RegistryState {
        @Override
        MetricsFactoryProvider provider() {
            return MetricsRegistryJmhBenchmark.provider(HELIDON_FACTORY_PROVIDER);
        }
    }

    /**
     * Micrometer provider registry state.
     */
    @State(Scope.Benchmark)
    public static class MicrometerState extends RegistryState {
        @Override
        MetricsFactoryProvider provider() {
            return MetricsRegistryJmhBenchmark.provider(MICROMETER_FACTORY_PROVIDER);
        }
    }

    /**
     * Per-caller identity and lookup cursor, with no shared sequence updates.
     */
    @State(Scope.Thread)
    public static class CallerState {
        private int threadIndex;
        private int cursor;

        @Setup(Level.Trial)
        public void setUp(ThreadParams threadParams, BenchmarkParams benchmarkParams) {
            threadIndex = threadParams.getThreadIndex();
            cursor = threadIndex % Integer.parseInt(benchmarkParams.getParam("meterCount"));
        }

        private int nextIndex(int meterCount) {
            int result = cursor;
            cursor = cursor + 1 == meterCount ? 0 : cursor + 1;
            return result;
        }
    }

    /**
     * Shared registry setup, validation, and lifecycle for both providers.
     */
    @State(Scope.Benchmark)
    public abstract static class RegistryState {
        /**
         * Number of prepopulated counter identities.
         */
        @Param({"32", "1024"})
        public int meterCount;

        private ServiceRegistryManager serviceRegistryManager;
        private MetricsFactory factory;
        private MeterRegistry registry;
        private List<List<Tag>> meterTags;
        private List<List<Tag>> missingTags;
        private List<List<Tag>> createRemoveTags;

        abstract MetricsFactoryProvider provider();

        @Setup(Level.Trial)
        public void setUp(BenchmarkParams benchmarkParams) {
            if (meterCount < 1) {
                throw new IllegalArgumentException("meterCount must be positive: " + meterCount);
            }
            try {
                serviceRegistryManager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                              .discoverServices(false)
                                                                              .discoverServicesFromServiceLoader(false)
                                                                              .build());
                MetricsConfig metricsConfig = MetricsConfig.create();
                factory = provider().create(Config.empty(), metricsConfig, List.of(), serviceRegistryManager.registry());
                registry = factory.createMeterRegistry(metricsConfig);
                meterTags = new ArrayList<>(meterCount);
                missingTags = new ArrayList<>(meterCount);
                for (int i = 0; i < meterCount; i++) {
                    List<Tag> tags = tags("stable", Integer.toString(i));
                    meterTags.add(tags);
                    missingTags.add(tags("stable", "missing-" + i));
                    Counter counter = registry.getOrCreate(factory.counterBuilder(METER_NAME).tags(tags));
                    if (registry.getOrCreate(factory.counterBuilder(METER_NAME).tags(tags)) != counter) {
                        throw new IllegalStateException("Registration did not reuse counter " + tags);
                    }
                    if (registry.meter(Counter.class, METER_NAME, tags).orElseThrow() != counter) {
                        throw new IllegalStateException("Lookup did not return registered counter " + tags);
                    }
                }
                createRemoveTags = new ArrayList<>(benchmarkParams.getThreads());
                for (int i = 0; i < benchmarkParams.getThreads(); i++) {
                    List<Tag> tags = tags("dynamic", Integer.toString(i));
                    createRemoveTags.add(tags);
                    Counter counter = registry.getOrCreate(factory.counterBuilder(METER_NAME).tags(tags));
                    if (registry.remove(counter).orElseThrow() != counter) {
                        throw new IllegalStateException("Removal did not return registered counter " + tags);
                    }
                }
                verifyCardinality();
            } catch (RuntimeException | Error failure) {
                try {
                    close();
                } catch (RuntimeException | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            try {
                verifyCardinality();
            } finally {
                close();
            }
        }

        private List<Tag> tags(String kind, String id) {
            return List.of(factory.tagCreate("kind", kind), factory.tagCreate("id", id));
        }

        private void verifyCardinality() {
            int actualCount = registry.meters().size();
            if (actualCount != meterCount) {
                throw new IllegalStateException("Expected " + meterCount + " registered counters, found " + actualCount);
            }
            for (List<Tag> tags : meterTags) {
                if (registry.meter(Counter.class, METER_NAME, tags).isEmpty()) {
                    throw new IllegalStateException("Missing prepopulated counter " + tags);
                }
            }
            for (List<Tag> tags : missingTags) {
                if (registry.meter(Counter.class, METER_NAME, tags).isPresent()) {
                    throw new IllegalStateException("Lookup miss matched a registered counter " + tags);
                }
            }
            for (List<Tag> tags : createRemoveTags) {
                if (registry.meter(Counter.class, METER_NAME, tags).isPresent()) {
                    throw new IllegalStateException("Removed counter is still registered " + tags);
                }
            }
        }

        private void close() {
            try {
                if (registry != null) {
                    registry.close();
                }
            } finally {
                try {
                    if (factory != null) {
                        factory.close();
                    }
                } finally {
                    if (serviceRegistryManager != null) {
                        serviceRegistryManager.shutdown();
                    }
                }
            }
        }
    }
}
