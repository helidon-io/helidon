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

import java.time.Duration;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;

import io.helidon.config.Config;
import io.helidon.metrics.api.Bucket;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.HistogramSnapshot;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Timer;
import io.helidon.metrics.api.ValueAtPercentile;
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

/**
 * Timer and distribution-summary construction through the public Helidon API with explicitly selected providers.
 * Percentile counts cover an empty configuration, the six default percentiles, and 64 unique percentiles in ascending order.
 * Bucket counts cover an empty configuration and 64 explicit ascending boundaries: 1 through 64 for summaries,
 * and 1 through 64 milliseconds for timers. The two parameters form six configurations per meter type and provider.
 * Each single-caller operation creates and removes one meter using a fixed identity, keeping registry cardinality at most one.
 * The measured time and allocation include builders, percentile and bucket configuration copies and validation,
 * meter construction, registration, and removal. Factories, registries, and input arrays are prepared before measurement.
 * Recording and snapshot creation are excluded from the measured operation.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class MetricsHistogramJmhBenchmark {
    private static final String HELIDON_FACTORY_PROVIDER =
            "io.helidon.metrics.providers.helidon.HelidonMetricsFactoryProvider";
    private static final String MICROMETER_FACTORY_PROVIDER =
            "io.helidon.metrics.providers.micrometer.MicrometerMetricsFactoryProvider";
    private static final String TIMER_NAME = "histogram.creation.timer";
    private static final String SUMMARY_NAME = "histogram.creation.summary";

    @Benchmark
    @Threads(1)
    public void helidonHistogramTimerCreateRemoveSingle(HelidonState state, Blackhole blackhole) {
        timerCreateRemove(state, blackhole);
    }

    @Benchmark
    @Threads(1)
    public void micrometerHistogramTimerCreateRemoveSingle(MicrometerState state, Blackhole blackhole) {
        timerCreateRemove(state, blackhole);
    }

    @Benchmark
    @Threads(1)
    public void helidonHistogramDistributionSummaryCreateRemoveSingle(HelidonState state, Blackhole blackhole) {
        distributionSummaryCreateRemove(state, blackhole);
    }

    @Benchmark
    @Threads(1)
    public void micrometerHistogramDistributionSummaryCreateRemoveSingle(MicrometerState state, Blackhole blackhole) {
        distributionSummaryCreateRemove(state, blackhole);
    }

    private static void timerCreateRemove(HistogramState state, Blackhole blackhole) {
        Timer timer = state.createTimer();
        blackhole.consume(timer);
        blackhole.consume(state.registry.remove(timer));
    }

    private static void distributionSummaryCreateRemove(HistogramState state, Blackhole blackhole) {
        DistributionSummary summary = state.createDistributionSummary();
        blackhole.consume(summary);
        blackhole.consume(state.registry.remove(summary));
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
     * Helidon provider histogram state.
     */
    @State(Scope.Benchmark)
    public static class HelidonState extends HistogramState {
        @Override
        MetricsFactoryProvider provider() {
            return MetricsHistogramJmhBenchmark.provider(HELIDON_FACTORY_PROVIDER);
        }
    }

    /**
     * Micrometer provider histogram state.
     */
    @State(Scope.Benchmark)
    public static class MicrometerState extends HistogramState {
        @Override
        MetricsFactoryProvider provider() {
            return MetricsHistogramJmhBenchmark.provider(MICROMETER_FACTORY_PROVIDER);
        }
    }

    /**
     * Shared histogram configuration, validation, and lifecycle for both providers.
     */
    @State(Scope.Benchmark)
    public abstract static class HistogramState {
        /**
         * Number of unique configured percentiles.
         */
        @Param({"0", "6", "64"})
        public int percentileCount;

        /**
         * Number of explicit configured bucket boundaries.
         */
        @Param({"0", "64"})
        public int bucketCount;

        private ServiceRegistryManager serviceRegistryManager;
        private MetricsFactory factory;
        private MeterRegistry registry;
        private double[] percentiles;
        private double[] summaryBuckets;
        private Duration[] timerBuckets;
        private double[] timerBucketNanos;

        abstract MetricsFactoryProvider provider();

        @Setup(Level.Trial)
        public void setUp(BenchmarkParams benchmarkParams) {
            if (benchmarkParams.getThreads() != 1) {
                throw new IllegalArgumentException("Histogram creation benchmarks require exactly one caller");
            }
            percentiles = switch (percentileCount) {
                case 0 -> new double[0];
                case 6 -> new double[] {0.5, 0.75, 0.95, 0.98, 0.99, 0.999};
                case 64 -> {
                    double[] values = new double[percentileCount];
                    for (int i = 0; i < values.length; i++) {
                        values[i] = (i + 1D) / (values.length + 1D);
                    }
                    yield values;
                }
                default -> throw new IllegalArgumentException("Unsupported percentileCount: " + percentileCount);
            };
            if (bucketCount != 0 && bucketCount != 64) {
                throw new IllegalArgumentException("Unsupported bucketCount: " + bucketCount);
            }
            summaryBuckets = new double[bucketCount];
            timerBuckets = new Duration[bucketCount];
            timerBucketNanos = new double[bucketCount];
            for (int i = 0; i < bucketCount; i++) {
                summaryBuckets[i] = i + 1D;
                timerBuckets[i] = Duration.ofMillis(i + 1L);
                timerBucketNanos[i] = timerBuckets[i].toNanos();
            }
            try {
                serviceRegistryManager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                              .discoverServices(false)
                                                                              .discoverServicesFromServiceLoader(false)
                                                                              .build());
                MetricsConfig metricsConfig = MetricsConfig.create();
                factory = provider().create(Config.empty(), metricsConfig, List.of(), serviceRegistryManager.registry());
                registry = factory.createMeterRegistry(metricsConfig);
                verifyEmptyRegistry();

                Timer timer = createTimer();
                verifySnapshot(TIMER_NAME, timer.snapshot(), timerBucketNanos);
                verifyRemoval(timer);
                Timer replacementTimer = createTimer();
                if (replacementTimer == timer) {
                    throw new IllegalStateException("Timer registration reused a removed meter");
                }
                verifyRemoval(replacementTimer);

                DistributionSummary summary = createDistributionSummary();
                verifySnapshot(SUMMARY_NAME, summary.snapshot(), summaryBuckets);
                verifyRemoval(summary);
                DistributionSummary replacementSummary = createDistributionSummary();
                if (replacementSummary == summary) {
                    throw new IllegalStateException("Distribution summary registration reused a removed meter");
                }
                verifyRemoval(replacementSummary);
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
                verifyEmptyRegistry();
            } finally {
                close();
            }
        }

        private Timer createTimer() {
            return registry.getOrCreate(factory.timerBuilder(TIMER_NAME)
                                               .percentiles(percentiles)
                                               .buckets(timerBuckets));
        }

        private DistributionSummary createDistributionSummary() {
            return registry.getOrCreate(factory.distributionSummaryBuilder(SUMMARY_NAME,
                                                                           factory.distributionStatisticsConfigBuilder()
                                                                                   .percentiles(percentiles)
                                                                                   .buckets(summaryBuckets)));
        }

        private void verifySnapshot(String name, HistogramSnapshot snapshot, double[] expectedBoundaries) {
            if (snapshot.count() != 0) {
                throw new IllegalStateException(name + " recorded observations during construction: " + snapshot.count());
            }
            int index = 0;
            for (ValueAtPercentile percentile : snapshot.percentileValues()) {
                if (index >= percentiles.length || Double.compare(percentile.percentile(), percentiles[index]) != 0) {
                    throw new IllegalStateException(name + " exposes an unexpected percentile at index " + index
                                                            + ": " + percentile.percentile());
                }
                index++;
            }
            if (index != percentileCount) {
                throw new IllegalStateException(name + " exposes " + index + " percentiles; expected " + percentileCount);
            }
            index = 0;
            for (Bucket bucket : snapshot.histogramCounts()) {
                if (index >= expectedBoundaries.length || Double.compare(bucket.boundary(), expectedBoundaries[index]) != 0) {
                    throw new IllegalStateException(name + " exposes an unexpected bucket boundary at index " + index
                                                            + ": " + bucket.boundary());
                }
                if (bucket.count() != 0) {
                    throw new IllegalStateException(name + " recorded observations in bucket " + index + ": " + bucket.count());
                }
                index++;
            }
            if (index != bucketCount) {
                throw new IllegalStateException(name + " exposes " + index + " bucket boundaries; expected " + bucketCount);
            }
        }

        private void verifyRemoval(Meter meter) {
            if (registry.meters().size() != 1) {
                throw new IllegalStateException("Expected exactly one registered meter before removal");
            }
            if (registry.remove(meter).orElseThrow() != meter) {
                throw new IllegalStateException("Removal did not return registered meter " + meter.id());
            }
            verifyEmptyRegistry();
        }

        private void verifyEmptyRegistry() {
            int actualCount = registry.meters().size();
            if (actualCount != 0) {
                throw new IllegalStateException("Expected no registered meters, found " + actualCount);
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
