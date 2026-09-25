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
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.helidon.common.context.Context;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.dbclient.DbClientServiceBase;
import io.helidon.dbclient.DbClientServiceContext;
import io.helidon.dbclient.DbStatementParameters;
import io.helidon.dbclient.DbStatementType;
import io.helidon.dbclient.metrics.DbClientMetrics;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.DistributionStatisticsConfig;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.FormatterContext;
import io.helidon.metrics.api.FunctionalCounter;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MeterRegistryFormatter;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.metrics.spi.MeterRegistryFormatterProvider;
import io.helidon.metrics.spi.MetricsFactoryProvider;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;
import io.helidon.webserver.observe.metrics.JsonMeterRegistryFormatterProvider;

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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Metrics provider benchmarks comparing the Helidon provider against Micrometer.
 * Fresh-virtual-thread cases record once per new thread into long-lived, preloaded meters, without warming the thread's RNG.
 * Their throughput includes task submission, thread creation, scheduling, recording, and awaiting completion.
 * Plain-meter cases provide matching lifecycle controls; these measurements do not isolate RNG cost.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class MetricsProviderJmhBenchmark {

    private static final String HELIDON_FACTORY_PROVIDER =
            "io.helidon.metrics.providers.helidon.HelidonMetricsFactoryProvider";
    private static final String HELIDON_FORMATTER_PROVIDER =
            "io.helidon.metrics.providers.helidon.HelidonPrometheusFormatterProvider";
    private static final String MICROMETER_FACTORY_PROVIDER =
            "io.helidon.metrics.providers.micrometer.MicrometerMetricsFactoryProvider";
    private static final String MICROMETER_FORMATTER_PROVIDER =
            "io.helidon.metrics.providers.micrometer.MicrometerPrometheusFormatterProvider";
    private static final int MULTI_THREADS = 8;
    private static final int GAUGE_READS_PER_INVOCATION = 64;
    private static final int PERCENTILE_RECORDS_PER_INVOCATION = 16;
    private static final int SNAPSHOT_READS_PER_INVOCATION = 16;
    private static final int DB_METRIC_SERVICE_HITS_PER_INVOCATION = 64;
    private static final int FORMAT_METER_COUNT = 32;
    private static final int PRELOAD_SAMPLE_COUNT = 4096;
    private static final Duration TIMER_MIN = Duration.ofNanos(1);
    private static final Duration TIMER_MAX = Duration.ofMillis(10);
    private static final Duration[] SMALL_TIMER_BUCKETS = {
            Duration.ofNanos(10_000),
            Duration.ofNanos(100_000),
            Duration.ofMillis(1),
            Duration.ofMillis(5),
            Duration.ofMillis(10)
    };
    private static final Duration[] LARGE_TIMER_BUCKETS = {
            Duration.ofNanos(1_000),
            Duration.ofNanos(2_000),
            Duration.ofNanos(4_000),
            Duration.ofNanos(8_000),
            Duration.ofNanos(16_000),
            Duration.ofNanos(32_000),
            Duration.ofNanos(64_000),
            Duration.ofNanos(128_000),
            Duration.ofNanos(256_000),
            Duration.ofNanos(512_000),
            Duration.ofMillis(1),
            Duration.ofMillis(2),
            Duration.ofMillis(4),
            Duration.ofMillis(8),
            Duration.ofMillis(10)
    };
    private static final double[] NO_PERCENTILES = {};
    private static final double[] PERCENTILES = {0.5, 0.95, 0.99};
    private static final double[] SMALL_SUMMARY_BUCKETS = {64D, 256D, 1024D, 4096D};
    private static final double[] LARGE_SUMMARY_BUCKETS = {
            1D, 2D, 4D, 8D, 16D, 32D, 64D, 128D, 256D, 512D, 1024D, 2048D, 4096D
    };
    private static final Duration[] DENSE_TIMER_BUCKETS = denseTimerBuckets();
    private static final double[] DENSE_SUMMARY_BUCKETS = denseSummaryBuckets();

    @Benchmark
    @Threads(1)
    public void helidonCounterSingle(HelidonState state) {
        counter(state);
    }

    @Benchmark
    @Threads(1)
    public void micrometerCounterSingle(MicrometerState state) {
        counter(state);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void helidonCounterMulti(HelidonState state) {
        counter(state);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void micrometerCounterMulti(MicrometerState state) {
        counter(state);
    }

    @Benchmark
    @Threads(1)
    public void helidonFunctionalCounterSingle(HelidonState state, Blackhole blackhole) {
        functionalCounter(state, blackhole);
    }

    @Benchmark
    @Threads(1)
    public void micrometerFunctionalCounterSingle(MicrometerState state, Blackhole blackhole) {
        functionalCounter(state, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void helidonFunctionalCounterMulti(HelidonState state, Blackhole blackhole) {
        functionalCounter(state, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void micrometerFunctionalCounterMulti(MicrometerState state, Blackhole blackhole) {
        functionalCounter(state, blackhole);
    }

    @Benchmark
    @Threads(1)
    @OperationsPerInvocation(GAUGE_READS_PER_INVOCATION)
    public void helidonSupplierGaugeSingle(HelidonState state, Blackhole blackhole) {
        supplierGauge(state, blackhole);
    }

    @Benchmark
    @Threads(1)
    @OperationsPerInvocation(GAUGE_READS_PER_INVOCATION)
    public void micrometerSupplierGaugeSingle(MicrometerState state, Blackhole blackhole) {
        supplierGauge(state, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    @OperationsPerInvocation(GAUGE_READS_PER_INVOCATION)
    public void helidonSupplierGaugeMulti(HelidonState state, Blackhole blackhole) {
        supplierGauge(state, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    @OperationsPerInvocation(GAUGE_READS_PER_INVOCATION)
    public void micrometerSupplierGaugeMulti(MicrometerState state, Blackhole blackhole) {
        supplierGauge(state, blackhole);
    }

    @Benchmark
    @Threads(1)
    @OperationsPerInvocation(GAUGE_READS_PER_INVOCATION)
    public void helidonFunctionGaugeSingle(HelidonState state, Blackhole blackhole) {
        functionGauge(state, blackhole);
    }

    @Benchmark
    @Threads(1)
    @OperationsPerInvocation(GAUGE_READS_PER_INVOCATION)
    public void micrometerFunctionGaugeSingle(MicrometerState state, Blackhole blackhole) {
        functionGauge(state, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    @OperationsPerInvocation(GAUGE_READS_PER_INVOCATION)
    public void helidonFunctionGaugeMulti(HelidonState state, Blackhole blackhole) {
        functionGauge(state, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    @OperationsPerInvocation(GAUGE_READS_PER_INVOCATION)
    public void micrometerFunctionGaugeMulti(MicrometerState state, Blackhole blackhole) {
        functionGauge(state, blackhole);
    }

    @Benchmark
    @Threads(1)
    public void helidonDistributionSummaryPlainSingle(HelidonState state, SampleState sample) {
        distributionSummaryPlain(state, sample);
    }

    @Benchmark
    @Threads(1)
    public void micrometerDistributionSummaryPlainSingle(MicrometerState state, SampleState sample) {
        distributionSummaryPlain(state, sample);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void helidonDistributionSummaryPlainMulti(HelidonState state, SampleState sample) {
        distributionSummaryPlain(state, sample);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void micrometerDistributionSummaryPlainMulti(MicrometerState state, SampleState sample) {
        distributionSummaryPlain(state, sample);
    }

    @Benchmark
    @Threads(1)
    @OperationsPerInvocation(PERCENTILE_RECORDS_PER_INVOCATION)
    public void helidonDistributionSummaryPercentilesSingle(HelidonState state, SampleState sample) {
        distributionSummaryPercentiles(state, sample);
    }

    @Benchmark
    @Threads(1)
    @OperationsPerInvocation(PERCENTILE_RECORDS_PER_INVOCATION)
    public void micrometerDistributionSummaryPercentilesSingle(MicrometerState state, SampleState sample) {
        distributionSummaryPercentiles(state, sample);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    @OperationsPerInvocation(PERCENTILE_RECORDS_PER_INVOCATION)
    public void helidonDistributionSummaryPercentilesMulti(HelidonState state, SampleState sample) {
        distributionSummaryPercentiles(state, sample);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    @OperationsPerInvocation(PERCENTILE_RECORDS_PER_INVOCATION)
    public void micrometerDistributionSummaryPercentilesMulti(MicrometerState state, SampleState sample) {
        distributionSummaryPercentiles(state, sample);
    }

    @Benchmark
    @Threads(1)
    public void helidonDistributionSummaryHistogramSmallSingle(HelidonState state, SampleState sample) {
        distributionSummaryHistogramSmall(state, sample);
    }

    @Benchmark
    @Threads(1)
    public void micrometerDistributionSummaryHistogramSmallSingle(MicrometerState state, SampleState sample) {
        distributionSummaryHistogramSmall(state, sample);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void helidonDistributionSummaryHistogramSmallMulti(HelidonState state, SampleState sample) {
        distributionSummaryHistogramSmall(state, sample);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void micrometerDistributionSummaryHistogramSmallMulti(MicrometerState state, SampleState sample) {
        distributionSummaryHistogramSmall(state, sample);
    }

    @Benchmark
    @Threads(1)
    public void helidonDistributionSummaryHistogramLargeSingle(HelidonState state, SampleState sample) {
        distributionSummaryHistogramLarge(state, sample);
    }

    @Benchmark
    @Threads(1)
    public void micrometerDistributionSummaryHistogramLargeSingle(MicrometerState state, SampleState sample) {
        distributionSummaryHistogramLarge(state, sample);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void helidonDistributionSummaryHistogramLargeMulti(HelidonState state, SampleState sample) {
        distributionSummaryHistogramLarge(state, sample);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void micrometerDistributionSummaryHistogramLargeMulti(MicrometerState state, SampleState sample) {
        distributionSummaryHistogramLarge(state, sample);
    }

    @Benchmark
    @Threads(1)
    public void helidonDistributionSummaryHistogramDenseSingle(HelidonState state, SampleState sample) {
        distributionSummaryHistogramDense(state, sample);
    }

    @Benchmark
    @Threads(1)
    public void micrometerDistributionSummaryHistogramDenseSingle(MicrometerState state, SampleState sample) {
        distributionSummaryHistogramDense(state, sample);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void helidonDistributionSummaryHistogramDenseMulti(HelidonState state, SampleState sample) {
        distributionSummaryHistogramDense(state, sample);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void micrometerDistributionSummaryHistogramDenseMulti(MicrometerState state, SampleState sample) {
        distributionSummaryHistogramDense(state, sample);
    }

    @Benchmark
    @Threads(1)
    public void helidonTimerPlainSingle(HelidonState state, SampleState sample) {
        timerPlain(state, sample);
    }

    @Benchmark
    @Threads(1)
    public void micrometerTimerPlainSingle(MicrometerState state, SampleState sample) {
        timerPlain(state, sample);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void helidonTimerPlainMulti(HelidonState state, SampleState sample) {
        timerPlain(state, sample);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void micrometerTimerPlainMulti(MicrometerState state, SampleState sample) {
        timerPlain(state, sample);
    }

    @Benchmark
    @Threads(1)
    @OperationsPerInvocation(PERCENTILE_RECORDS_PER_INVOCATION)
    public void helidonTimerPercentilesSingle(HelidonState state, SampleState sample) {
        timerPercentiles(state, sample);
    }

    @Benchmark
    @Threads(1)
    @OperationsPerInvocation(PERCENTILE_RECORDS_PER_INVOCATION)
    public void micrometerTimerPercentilesSingle(MicrometerState state, SampleState sample) {
        timerPercentiles(state, sample);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    @OperationsPerInvocation(PERCENTILE_RECORDS_PER_INVOCATION)
    public void helidonTimerPercentilesMulti(HelidonState state, SampleState sample) {
        timerPercentiles(state, sample);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    @OperationsPerInvocation(PERCENTILE_RECORDS_PER_INVOCATION)
    public void micrometerTimerPercentilesMulti(MicrometerState state, SampleState sample) {
        timerPercentiles(state, sample);
    }

    @Benchmark
    @Threads(1)
    public void helidonDistributionSummaryPlainFreshVirtualThreadSingle(HelidonState state, SampleState sample)
            throws InterruptedException, ExecutionException {
        distributionSummaryPlainFreshVirtualThread(state, sample);
    }

    @Benchmark
    @Threads(1)
    public void micrometerDistributionSummaryPlainFreshVirtualThreadSingle(MicrometerState state, SampleState sample)
            throws InterruptedException, ExecutionException {
        distributionSummaryPlainFreshVirtualThread(state, sample);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void helidonDistributionSummaryPlainFreshVirtualThreadMulti(HelidonState state, SampleState sample)
            throws InterruptedException, ExecutionException {
        distributionSummaryPlainFreshVirtualThread(state, sample);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void micrometerDistributionSummaryPlainFreshVirtualThreadMulti(MicrometerState state, SampleState sample)
            throws InterruptedException, ExecutionException {
        distributionSummaryPlainFreshVirtualThread(state, sample);
    }

    @Benchmark
    @Threads(1)
    public void helidonDistributionSummaryPercentilesFreshVirtualThreadSingle(HelidonState state, SampleState sample)
            throws InterruptedException, ExecutionException {
        distributionSummaryPercentilesFreshVirtualThread(state, sample);
    }

    @Benchmark
    @Threads(1)
    public void micrometerDistributionSummaryPercentilesFreshVirtualThreadSingle(MicrometerState state, SampleState sample)
            throws InterruptedException, ExecutionException {
        distributionSummaryPercentilesFreshVirtualThread(state, sample);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void helidonDistributionSummaryPercentilesFreshVirtualThreadMulti(HelidonState state, SampleState sample)
            throws InterruptedException, ExecutionException {
        distributionSummaryPercentilesFreshVirtualThread(state, sample);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void micrometerDistributionSummaryPercentilesFreshVirtualThreadMulti(MicrometerState state, SampleState sample)
            throws InterruptedException, ExecutionException {
        distributionSummaryPercentilesFreshVirtualThread(state, sample);
    }

    @Benchmark
    @Threads(1)
    public void helidonTimerPlainFreshVirtualThreadSingle(HelidonState state, SampleState sample)
            throws InterruptedException, ExecutionException {
        timerPlainFreshVirtualThread(state, sample);
    }

    @Benchmark
    @Threads(1)
    public void micrometerTimerPlainFreshVirtualThreadSingle(MicrometerState state, SampleState sample)
            throws InterruptedException, ExecutionException {
        timerPlainFreshVirtualThread(state, sample);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void helidonTimerPlainFreshVirtualThreadMulti(HelidonState state, SampleState sample)
            throws InterruptedException, ExecutionException {
        timerPlainFreshVirtualThread(state, sample);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void micrometerTimerPlainFreshVirtualThreadMulti(MicrometerState state, SampleState sample)
            throws InterruptedException, ExecutionException {
        timerPlainFreshVirtualThread(state, sample);
    }

    @Benchmark
    @Threads(1)
    public void helidonTimerPercentilesFreshVirtualThreadSingle(HelidonState state, SampleState sample)
            throws InterruptedException, ExecutionException {
        timerPercentilesFreshVirtualThread(state, sample);
    }

    @Benchmark
    @Threads(1)
    public void micrometerTimerPercentilesFreshVirtualThreadSingle(MicrometerState state, SampleState sample)
            throws InterruptedException, ExecutionException {
        timerPercentilesFreshVirtualThread(state, sample);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void helidonTimerPercentilesFreshVirtualThreadMulti(HelidonState state, SampleState sample)
            throws InterruptedException, ExecutionException {
        timerPercentilesFreshVirtualThread(state, sample);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void micrometerTimerPercentilesFreshVirtualThreadMulti(MicrometerState state, SampleState sample)
            throws InterruptedException, ExecutionException {
        timerPercentilesFreshVirtualThread(state, sample);
    }

    @Benchmark
    @Threads(1)
    public void helidonTimerHistogramSmallSingle(HelidonState state, SampleState sample) {
        timerHistogramSmall(state, sample);
    }

    @Benchmark
    @Threads(1)
    public void micrometerTimerHistogramSmallSingle(MicrometerState state, SampleState sample) {
        timerHistogramSmall(state, sample);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void helidonTimerHistogramSmallMulti(HelidonState state, SampleState sample) {
        timerHistogramSmall(state, sample);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void micrometerTimerHistogramSmallMulti(MicrometerState state, SampleState sample) {
        timerHistogramSmall(state, sample);
    }

    @Benchmark
    @Threads(1)
    public void helidonTimerHistogramLargeSingle(HelidonState state, SampleState sample) {
        timerHistogramLarge(state, sample);
    }

    @Benchmark
    @Threads(1)
    public void micrometerTimerHistogramLargeSingle(MicrometerState state, SampleState sample) {
        timerHistogramLarge(state, sample);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void helidonTimerHistogramLargeMulti(HelidonState state, SampleState sample) {
        timerHistogramLarge(state, sample);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void micrometerTimerHistogramLargeMulti(MicrometerState state, SampleState sample) {
        timerHistogramLarge(state, sample);
    }

    @Benchmark
    @Threads(1)
    public void helidonTimerHistogramDenseSingle(HelidonState state, SampleState sample) {
        timerHistogramDense(state, sample);
    }

    @Benchmark
    @Threads(1)
    public void micrometerTimerHistogramDenseSingle(MicrometerState state, SampleState sample) {
        timerHistogramDense(state, sample);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void helidonTimerHistogramDenseMulti(HelidonState state, SampleState sample) {
        timerHistogramDense(state, sample);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void micrometerTimerHistogramDenseMulti(MicrometerState state, SampleState sample) {
        timerHistogramDense(state, sample);
    }

    @Benchmark
    @Threads(1)
    public void helidonGetOrCreateHitSingle(HelidonState state, Blackhole blackhole) {
        getOrCreateHit(state, blackhole);
    }

    @Benchmark
    @Threads(1)
    public void micrometerGetOrCreateHitSingle(MicrometerState state, Blackhole blackhole) {
        getOrCreateHit(state, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void helidonGetOrCreateHitMulti(HelidonState state, Blackhole blackhole) {
        getOrCreateHit(state, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void micrometerGetOrCreateHitMulti(MicrometerState state, Blackhole blackhole) {
        getOrCreateHit(state, blackhole);
    }

    @Benchmark
    @Threads(1)
    public void helidonLookupHitSingle(HelidonState state, Blackhole blackhole) {
        lookupHit(state, blackhole);
    }

    @Benchmark
    @Threads(1)
    public void micrometerLookupHitSingle(MicrometerState state, Blackhole blackhole) {
        lookupHit(state, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void helidonLookupHitMulti(HelidonState state, Blackhole blackhole) {
        lookupHit(state, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void micrometerLookupHitMulti(MicrometerState state, Blackhole blackhole) {
        lookupHit(state, blackhole);
    }

    @Benchmark
    @Threads(1)
    @OperationsPerInvocation(DB_METRIC_SERVICE_HITS_PER_INVOCATION)
    public void helidonDbMetricServiceCachedCounterSingle(HelidonDbMetricServiceState state, Blackhole blackhole) {
        dbMetricServiceCachedCounter(state, blackhole);
    }

    @Benchmark
    @Threads(1)
    @OperationsPerInvocation(DB_METRIC_SERVICE_HITS_PER_INVOCATION)
    public void micrometerDbMetricServiceCachedCounterSingle(MicrometerDbMetricServiceState state, Blackhole blackhole) {
        dbMetricServiceCachedCounter(state, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    @OperationsPerInvocation(DB_METRIC_SERVICE_HITS_PER_INVOCATION)
    public void helidonDbMetricServiceCachedCounterMulti(HelidonDbMetricServiceState state, Blackhole blackhole) {
        dbMetricServiceCachedCounter(state, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    @OperationsPerInvocation(DB_METRIC_SERVICE_HITS_PER_INVOCATION)
    public void micrometerDbMetricServiceCachedCounterMulti(MicrometerDbMetricServiceState state, Blackhole blackhole) {
        dbMetricServiceCachedCounter(state, blackhole);
    }

    @Benchmark
    @Threads(1)
    public void helidonCounterCreateRemoveSingle(HelidonState state, Blackhole blackhole) {
        counterCreateRemove(state, blackhole);
    }

    @Benchmark
    @Threads(1)
    public void micrometerCounterCreateRemoveSingle(MicrometerState state, Blackhole blackhole) {
        counterCreateRemove(state, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void helidonCounterCreateRemoveMulti(HelidonState state, Blackhole blackhole) {
        counterCreateRemove(state, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void micrometerCounterCreateRemoveMulti(MicrometerState state, Blackhole blackhole) {
        counterCreateRemove(state, blackhole);
    }

    @Benchmark
    @Threads(1)
    @OperationsPerInvocation(SNAPSHOT_READS_PER_INVOCATION)
    public void helidonDistributionSummarySnapshotSingle(HelidonState state, Blackhole blackhole) {
        distributionSummarySnapshot(state, blackhole);
    }

    @Benchmark
    @Threads(1)
    @OperationsPerInvocation(SNAPSHOT_READS_PER_INVOCATION)
    public void micrometerDistributionSummarySnapshotSingle(MicrometerState state, Blackhole blackhole) {
        distributionSummarySnapshot(state, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    @OperationsPerInvocation(SNAPSHOT_READS_PER_INVOCATION)
    public void helidonDistributionSummarySnapshotMulti(HelidonState state, Blackhole blackhole) {
        distributionSummarySnapshot(state, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    @OperationsPerInvocation(SNAPSHOT_READS_PER_INVOCATION)
    public void micrometerDistributionSummarySnapshotMulti(MicrometerState state, Blackhole blackhole) {
        distributionSummarySnapshot(state, blackhole);
    }

    @Benchmark
    @Threads(1)
    @OperationsPerInvocation(SNAPSHOT_READS_PER_INVOCATION)
    public void helidonTimerSnapshotSingle(HelidonState state, Blackhole blackhole) {
        timerSnapshot(state, blackhole);
    }

    @Benchmark
    @Threads(1)
    @OperationsPerInvocation(SNAPSHOT_READS_PER_INVOCATION)
    public void micrometerTimerSnapshotSingle(MicrometerState state, Blackhole blackhole) {
        timerSnapshot(state, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    @OperationsPerInvocation(SNAPSHOT_READS_PER_INVOCATION)
    public void helidonTimerSnapshotMulti(HelidonState state, Blackhole blackhole) {
        timerSnapshot(state, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    @OperationsPerInvocation(SNAPSHOT_READS_PER_INVOCATION)
    public void micrometerTimerSnapshotMulti(MicrometerState state, Blackhole blackhole) {
        timerSnapshot(state, blackhole);
    }

    @Benchmark
    @Threads(1)
    public void helidonPrometheusFormatAllSingle(HelidonState state, Blackhole blackhole) {
        formatAll(state, blackhole);
    }

    @Benchmark
    @Threads(1)
    public void micrometerPrometheusFormatAllSingle(MicrometerState state, Blackhole blackhole) {
        formatAll(state, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void helidonPrometheusFormatAllMulti(HelidonState state, Blackhole blackhole) {
        formatAll(state, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void micrometerPrometheusFormatAllMulti(MicrometerState state, Blackhole blackhole) {
        formatAll(state, blackhole);
    }

    @Benchmark
    @Threads(1)
    public void helidonPrometheusFormatFilteredSingle(HelidonState state, Blackhole blackhole) {
        formatFiltered(state, blackhole);
    }

    @Benchmark
    @Threads(1)
    public void micrometerPrometheusFormatFilteredSingle(MicrometerState state, Blackhole blackhole) {
        formatFiltered(state, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void helidonPrometheusFormatFilteredMulti(HelidonState state, Blackhole blackhole) {
        formatFiltered(state, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void micrometerPrometheusFormatFilteredMulti(MicrometerState state, Blackhole blackhole) {
        formatFiltered(state, blackhole);
    }

    @Benchmark
    @Threads(1)
    public void helidonOpenMetricsFormatAllSingle(HelidonState state, Blackhole blackhole) {
        formatOpenMetrics(state, blackhole);
    }

    @Benchmark
    @Threads(1)
    public void micrometerOpenMetricsFormatAllSingle(MicrometerState state, Blackhole blackhole) {
        formatOpenMetrics(state, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void helidonOpenMetricsFormatAllMulti(HelidonState state, Blackhole blackhole) {
        formatOpenMetrics(state, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void micrometerOpenMetricsFormatAllMulti(MicrometerState state, Blackhole blackhole) {
        formatOpenMetrics(state, blackhole);
    }

    @Benchmark
    @Threads(1)
    public void helidonJsonFormatAllSingle(HelidonState state, Blackhole blackhole) {
        formatJson(state, blackhole);
    }

    @Benchmark
    @Threads(1)
    public void micrometerJsonFormatAllSingle(MicrometerState state, Blackhole blackhole) {
        formatJson(state, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void helidonJsonFormatAllMulti(HelidonState state, Blackhole blackhole) {
        formatJson(state, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void micrometerJsonFormatAllMulti(MicrometerState state, Blackhole blackhole) {
        formatJson(state, blackhole);
    }

    @Benchmark
    @Threads(1)
    public void helidonJsonFormatFilteredSingle(HelidonState state, Blackhole blackhole) {
        formatJsonFiltered(state, blackhole);
    }

    @Benchmark
    @Threads(1)
    public void micrometerJsonFormatFilteredSingle(MicrometerState state, Blackhole blackhole) {
        formatJsonFiltered(state, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void helidonJsonFormatFilteredMulti(HelidonState state, Blackhole blackhole) {
        formatJsonFiltered(state, blackhole);
    }

    @Benchmark
    @Threads(MULTI_THREADS)
    public void micrometerJsonFormatFilteredMulti(MicrometerState state, Blackhole blackhole) {
        formatJsonFiltered(state, blackhole);
    }

    private static void counter(MetricsState state) {
        state.counter.increment();
    }

    private static void functionalCounter(MetricsState state, Blackhole blackhole) {
        blackhole.consume(state.functionalCounter.count());
    }

    private static void supplierGauge(MetricsState state, Blackhole blackhole) {
        long total = 0L;
        for (int i = 0; i < GAUGE_READS_PER_INVOCATION; i++) {
            total += state.supplierGauge.value().longValue();
        }
        blackhole.consume(total);
    }

    private static void functionGauge(MetricsState state, Blackhole blackhole) {
        double total = 0D;
        for (int i = 0; i < GAUGE_READS_PER_INVOCATION; i++) {
            total += state.functionGauge.value();
        }
        blackhole.consume(total);
    }

    private static void distributionSummaryPlain(MetricsState state, SampleState sample) {
        state.distributionSummaryPlain.record(sample.amount());
    }

    private static void distributionSummaryPercentiles(MetricsState state, SampleState sample) {
        for (int i = 0; i < PERCENTILE_RECORDS_PER_INVOCATION; i++) {
            state.distributionSummaryPercentiles.record(sample.amount());
        }
    }

    private static void distributionSummaryHistogramSmall(MetricsState state, SampleState sample) {
        state.distributionSummaryHistogramSmall.record(sample.amount());
    }

    private static void distributionSummaryHistogramLarge(MetricsState state, SampleState sample) {
        state.distributionSummaryHistogramLarge.record(sample.amount());
    }

    private static void distributionSummaryHistogramDense(MetricsState state, SampleState sample) {
        state.distributionSummaryHistogramDense.record(sample.amount());
    }

    private static void timerPlain(MetricsState state, SampleState sample) {
        state.timerPlain.record(sample.nanos(), TimeUnit.NANOSECONDS);
    }

    private static void timerPercentiles(MetricsState state, SampleState sample) {
        for (int i = 0; i < PERCENTILE_RECORDS_PER_INVOCATION; i++) {
            state.timerPercentiles.record(sample.nanos(), TimeUnit.NANOSECONDS);
        }
    }

    private static void distributionSummaryPlainFreshVirtualThread(MetricsState state, SampleState sample)
            throws InterruptedException, ExecutionException {
        double amount = sample.amount();
        state.virtualThreads.submit(() -> state.distributionSummaryPlain.record(amount)).get();
    }

    private static void distributionSummaryPercentilesFreshVirtualThread(MetricsState state, SampleState sample)
            throws InterruptedException, ExecutionException {
        double amount = sample.amount();
        state.virtualThreads.submit(() -> state.distributionSummaryPercentiles.record(amount)).get();
    }

    private static void timerPlainFreshVirtualThread(MetricsState state, SampleState sample)
            throws InterruptedException, ExecutionException {
        long nanos = sample.nanos();
        state.virtualThreads.submit(() -> state.timerPlain.record(nanos, TimeUnit.NANOSECONDS)).get();
    }

    private static void timerPercentilesFreshVirtualThread(MetricsState state, SampleState sample)
            throws InterruptedException, ExecutionException {
        long nanos = sample.nanos();
        state.virtualThreads.submit(() -> state.timerPercentiles.record(nanos, TimeUnit.NANOSECONDS)).get();
    }

    private static void timerHistogramSmall(MetricsState state, SampleState sample) {
        state.timerHistogramSmall.record(sample.nanos(), TimeUnit.NANOSECONDS);
    }

    private static void timerHistogramLarge(MetricsState state, SampleState sample) {
        state.timerHistogramLarge.record(sample.nanos(), TimeUnit.NANOSECONDS);
    }

    private static void timerHistogramDense(MetricsState state, SampleState sample) {
        state.timerHistogramDense.record(sample.nanos(), TimeUnit.NANOSECONDS);
    }

    private static Duration[] denseTimerBuckets() {
        Duration[] result = new Duration[128];
        for (int i = 0; i < result.length; i++) {
            result[i] = Duration.ofNanos((i + 1L) * 80_000L);
        }
        return result;
    }

    private static double[] denseSummaryBuckets() {
        double[] result = new double[128];
        for (int i = 0; i < result.length; i++) {
            result[i] = (i + 1D) * 32D;
        }
        return result;
    }

    private static void getOrCreateHit(MetricsState state, Blackhole blackhole) {
        blackhole.consume(state.registry.getOrCreate(state.factory.counterBuilder("lookup.counter")
                                                             .tags(state.lookupTags)));
    }

    private static void lookupHit(MetricsState state, Blackhole blackhole) {
        blackhole.consume(state.registry.meter(Counter.class, "lookup.counter", state.lookupTags));
    }

    private static void dbMetricServiceCachedCounter(DbMetricServiceState state, Blackhole blackhole) {
        for (int i = 0; i < DB_METRIC_SERVICE_HITS_PER_INVOCATION; i++) {
            blackhole.consume(state.service.statement(state.context));
        }
    }

    private static void counterCreateRemove(MetricsState state, Blackhole blackhole) {
        long next = state.dynamicMeterSequence.incrementAndGet();
        List<Tag> tags = List.of(state.tag("id", Long.toString(next)));
        Counter counter = state.registry.getOrCreate(state.factory.counterBuilder("dynamic.counter")
                                                             .tags(tags));
        blackhole.consume(state.registry.remove(counter));
    }

    private static void distributionSummarySnapshot(MetricsState state, Blackhole blackhole) {
        for (int i = 0; i < SNAPSHOT_READS_PER_INVOCATION; i++) {
            blackhole.consume(state.distributionSummaryHistogramLarge.snapshot());
        }
    }

    private static void timerSnapshot(MetricsState state, Blackhole blackhole) {
        for (int i = 0; i < SNAPSHOT_READS_PER_INVOCATION; i++) {
            blackhole.consume(state.timerHistogramLarge.snapshot());
        }
    }

    private static void formatAll(MetricsState state, Blackhole blackhole) {
        blackhole.consume(state.prometheusFormatter.format());
    }

    private static void formatFiltered(MetricsState state, Blackhole blackhole) {
        blackhole.consume(state.prometheusFilteredFormatter.format());
    }

    private static void formatOpenMetrics(MetricsState state, Blackhole blackhole) {
        blackhole.consume(state.openMetricsFormatter.format());
    }

    private static void formatJson(MetricsState state, Blackhole blackhole) {
        blackhole.consume(state.jsonFormatter.format());
    }

    private static void formatJsonFiltered(MetricsState state, Blackhole blackhole) {
        blackhole.consume(state.jsonFilteredFormatter.format());
    }

    private static MetricsFactoryProvider metricsFactoryProvider(String className) {
        return ServiceLoader.load(MetricsFactoryProvider.class)
                .stream()
                .filter(provider -> provider.type().getName().equals(className))
                .map(ServiceLoader.Provider::get)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No metrics factory provider " + className));
    }

    private static MeterRegistryFormatterProvider meterRegistryFormatterProvider(String className) {
        return ServiceLoader.load(MeterRegistryFormatterProvider.class)
                .stream()
                .filter(provider -> provider.type().getName().equals(className))
                .map(ServiceLoader.Provider::get)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No meter registry formatter provider " + className));
    }

    /**
     * Helidon provider benchmark state.
     */
    @State(Scope.Benchmark)
    public static class HelidonState extends MetricsState {
        private static final MetricsFactoryProvider PROVIDER = metricsFactoryProvider(HELIDON_FACTORY_PROVIDER);
        private static final MeterRegistryFormatterProvider FORMATTER_PROVIDER =
                meterRegistryFormatterProvider(HELIDON_FORMATTER_PROVIDER);

        @Override
        MetricsFactory createFactory(ServiceRegistry serviceRegistry) {
            return PROVIDER.create(Config.empty(), MetricsConfig.create(), List.of(), serviceRegistry);
        }

        @Override
        MeterRegistryFormatterProvider formatterProvider() {
            return FORMATTER_PROVIDER;
        }
    }

    /**
     * Micrometer provider benchmark state.
     */
    @State(Scope.Benchmark)
    public static class MicrometerState extends MetricsState {
        private static final MetricsFactoryProvider PROVIDER = metricsFactoryProvider(MICROMETER_FACTORY_PROVIDER);
        private static final MeterRegistryFormatterProvider FORMATTER_PROVIDER =
                meterRegistryFormatterProvider(MICROMETER_FORMATTER_PROVIDER);

        @Override
        MetricsFactory createFactory(ServiceRegistry serviceRegistry) {
            return PROVIDER.create(Config.empty(), MetricsConfig.create(), List.of(), serviceRegistry);
        }

        @Override
        MeterRegistryFormatterProvider formatterProvider() {
            return FORMATTER_PROVIDER;
        }
    }

    /**
     * Helidon provider DB metrics service benchmark state.
     */
    @State(Scope.Benchmark)
    public static class HelidonDbMetricServiceState extends DbMetricServiceState {
        @Override
        MetricsFactoryProvider provider() {
            return metricsFactoryProvider(HELIDON_FACTORY_PROVIDER);
        }
    }

    /**
     * Micrometer provider DB metrics service benchmark state.
     */
    @State(Scope.Benchmark)
    public static class MicrometerDbMetricServiceState extends DbMetricServiceState {
        @Override
        MetricsFactoryProvider provider() {
            return metricsFactoryProvider(MICROMETER_FACTORY_PROVIDER);
        }
    }

    /**
     * Per-thread sample values.
     */
    @State(Scope.Thread)
    public static class SampleState {
        private long next;

        double amount() {
            return (++next & 4095) + 1;
        }

        long nanos() {
            return ((++next & 1023) + 1) * 1000L;
        }
    }

    abstract static class DbMetricServiceState {
        private ServiceRegistryManager factoryServiceRegistryManager;
        private ServiceRegistryManager serviceRegistryManager;
        private ServiceRegistry previousServiceRegistry;
        private MetricsFactory factory;
        private MeterRegistry registry;
        private DbClientServiceBase service;
        private DbClientServiceContext context;

        abstract MetricsFactoryProvider provider();

        @Setup(Level.Trial)
        public void setUp() {
            factoryServiceRegistryManager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                                 .discoverServices(false)
                                                                                 .discoverServicesFromServiceLoader(false)
                                                                                 .build());
            MetricsConfig metricsConfig = MetricsConfig.create();
            factory = provider().create(Config.empty(), metricsConfig, List.of(), factoryServiceRegistryManager.registry());
            registry = factory.createMeterRegistry(metricsConfig);
            serviceRegistryManager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                          .discoverServices(false)
                                                                          .discoverServicesFromServiceLoader(false)
                                                                          .putContractInstance(MeterRegistry.class, registry)
                                                                          .build());
            previousServiceRegistry = GlobalServiceRegistry.registry();
            Services.registry(serviceRegistryManager.registry());
            service = DbClientMetrics.counter().build();
            context = context("cached-statement", CompletableFuture.completedFuture(null));
            service.statement(context);
            Counter recorded = registry.meter(Counter.class, "db.counter.cached-statement", List.of()).orElseThrow();
            if (recorded.count() != 1L) {
                throw new IllegalStateException("DB metrics warmup must increment the selected provider's counter");
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            try {
                registry.close();
            } finally {
                try {
                    factory.close();
                } finally {
                    try {
                        serviceRegistryManager.shutdown();
                    } finally {
                        try {
                            factoryServiceRegistryManager.shutdown();
                        } finally {
                            Services.registry(previousServiceRegistry);
                        }
                    }
                }
            }
        }

        private static DbClientServiceContext context(String statementName, CompletionStage<Void> statementFuture) {
            return new DbClientServiceContext() {
                @Override
                public Context context() {
                    throw unsupported();
                }

                @Override
                public String statement() {
                    throw unsupported();
                }

                @Override
                public String statementName() {
                    return statementName;
                }

                @Override
                public DbStatementType statementType() {
                    return DbStatementType.INSERT;
                }

                @Override
                public DbStatementParameters statementParameters() {
                    throw unsupported();
                }

                @Override
                public String dbType() {
                    throw unsupported();
                }

                @Override
                public CompletionStage<Void> statementFuture() {
                    return statementFuture;
                }

                @Override
                public CompletionStage<Long> resultFuture() {
                    throw unsupported();
                }

                @Override
                public DbClientServiceContext statement(String stmt, List<Object> parameters) {
                    throw unsupported();
                }

                @Override
                public DbClientServiceContext statement(String stmt, Map<String, Object> parameters) {
                    throw unsupported();
                }

                @Override
                public DbClientServiceContext parameters(List<Object> parameters) {
                    throw unsupported();
                }

                @Override
                public DbClientServiceContext parameters(Map<String, Object> parameters) {
                    throw unsupported();
                }

                @Override
                public DbClientServiceContext context(Context context) {
                    throw unsupported();
                }

                @Override
                public DbClientServiceContext statement(String name) {
                    throw unsupported();
                }

                @Override
                public DbClientServiceContext statementName(String name) {
                    throw unsupported();
                }
            };
        }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("Not needed for this benchmark");
        }
    }

    abstract static class MetricsState {
        private ExecutorService virtualThreads;
        private ServiceRegistryManager serviceRegistryManager;
        private MetricsConfig metricsConfig;
        private MetricsFactory factory;
        private MeterRegistry registry;
        private Counter counter;
        private FunctionalCounter functionalCounter;
        private Gauge<Long> supplierGauge;
        private Gauge<Double> functionGauge;
        private DistributionSummary distributionSummaryPlain;
        private DistributionSummary distributionSummaryPercentiles;
        private DistributionSummary distributionSummaryHistogramSmall;
        private DistributionSummary distributionSummaryHistogramLarge;
        private DistributionSummary distributionSummaryHistogramDense;
        private Timer timerPlain;
        private Timer timerPercentiles;
        private Timer timerHistogramSmall;
        private Timer timerHistogramLarge;
        private Timer timerHistogramDense;
        private MeterRegistryFormatter prometheusFormatter;
        private MeterRegistryFormatter prometheusFilteredFormatter;
        private MeterRegistryFormatter openMetricsFormatter;
        private MeterRegistryFormatter jsonFormatter;
        private MeterRegistryFormatter jsonFilteredFormatter;
        private List<Tag> lookupTags;
        private AtomicLong functionalCounterValue;
        private AtomicLong gaugeValue;
        private AtomicLong dynamicMeterSequence;

        abstract MetricsFactory createFactory(ServiceRegistry serviceRegistry);

        abstract MeterRegistryFormatterProvider formatterProvider();

        @Setup(Level.Trial)
        public void setUp() {
            serviceRegistryManager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                          .discoverServices(false)
                                                                          .discoverServicesFromServiceLoader(false)
                                                                          .build());
            factory = createFactory(serviceRegistryManager.registry());
            metricsConfig = MetricsConfig.create();
            registry = factory.createMeterRegistry(metricsConfig);
            functionalCounterValue = new AtomicLong(42);
            gaugeValue = new AtomicLong(42);
            dynamicMeterSequence = new AtomicLong();
            lookupTags = List.of(tag("kind", "lookup"));

            counter = registry.getOrCreate(factory.counterBuilder("counter"));
            functionalCounter = registry.getOrCreate(factory.functionalCounterBuilder("functional.counter",
                                                                                     functionalCounterValue,
                                                                                     AtomicLong::get));
            supplierGauge = registry.getOrCreate(factory.gaugeBuilder("supplier.gauge", gaugeValue::get));
            functionGauge = registry.getOrCreate(factory.gaugeBuilder("function.gauge",
                                                                       gaugeValue,
                                                                       AtomicLong::doubleValue));
            registry.getOrCreate(factory.counterBuilder("lookup.counter")
                                         .tags(lookupTags));

            distributionSummaryPlain = registry.getOrCreate(factory.distributionSummaryBuilder("summary.plain",
                                                                                               plainConfig()));
            distributionSummaryPercentiles = registry.getOrCreate(
                    factory.distributionSummaryBuilder("summary.percentiles",
                                                       percentilesConfig()));
            distributionSummaryHistogramSmall = registry.getOrCreate(
                    factory.distributionSummaryBuilder("summary.histogram.small",
                                                       bucketConfig(SMALL_SUMMARY_BUCKETS))
                            .publishPercentileHistogram(true));
            distributionSummaryHistogramLarge = registry.getOrCreate(
                    factory.distributionSummaryBuilder("summary.histogram.large",
                                                       bucketConfig(LARGE_SUMMARY_BUCKETS))
                            .publishPercentileHistogram(true));
            distributionSummaryHistogramDense = registry.getOrCreate(
                    factory.distributionSummaryBuilder("summary.histogram.dense",
                                                       bucketConfig(DENSE_SUMMARY_BUCKETS))
                            .publishPercentileHistogram(true));
            timerPlain = registry.getOrCreate(factory.timerBuilder("timer.plain")
                                                      .percentiles(NO_PERCENTILES));
            timerPercentiles = registry.getOrCreate(factory.timerBuilder("timer.percentiles")
                                                            .percentiles(PERCENTILES));
            timerHistogramSmall = registry.getOrCreate(factory.timerBuilder("timer.histogram.small")
                                                               .minimumExpectedValue(TIMER_MIN)
                                                               .maximumExpectedValue(TIMER_MAX)
                                                               .percentiles(NO_PERCENTILES)
                                                               .buckets(SMALL_TIMER_BUCKETS)
                                                               .publishPercentileHistogram(true));
            timerHistogramLarge = registry.getOrCreate(factory.timerBuilder("timer.histogram.large")
                                                               .minimumExpectedValue(TIMER_MIN)
                                                               .maximumExpectedValue(TIMER_MAX)
                                                               .percentiles(NO_PERCENTILES)
                                                               .buckets(LARGE_TIMER_BUCKETS)
                                                               .publishPercentileHistogram(true));
            timerHistogramDense = registry.getOrCreate(factory.timerBuilder("timer.histogram.dense")
                                                               .minimumExpectedValue(TIMER_MIN)
                                                               .maximumExpectedValue(TIMER_MAX)
                                                               .percentiles(NO_PERCENTILES)
                                                               .buckets(DENSE_TIMER_BUCKETS)
                                                               .publishPercentileHistogram(true));

            preloadSamples();
            addFormatMeters();
            prometheusFormatter = formatter(MediaTypes.TEXT_PLAIN, List.of());
            prometheusFilteredFormatter = formatter(MediaTypes.TEXT_PLAIN,
                                                   List.of("format.counter", "format.timer", "format.summary"));
            openMetricsFormatter = formatter(MediaTypes.APPLICATION_OPENMETRICS_TEXT, List.of());
            jsonFormatter = jsonFormatter(List.of());
            jsonFilteredFormatter = jsonFormatter(List.of("format.counter", "format.timer", "format.summary"));
            virtualThreads = Executors.newVirtualThreadPerTaskExecutor();
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            try {
                virtualThreads.close();
            } finally {
                try {
                    registry.close();
                } finally {
                    try {
                        factory.close();
                    } finally {
                        serviceRegistryManager.shutdown();
                    }
                }
            }
        }

        private DistributionStatisticsConfig.Builder plainConfig() {
            return factory.distributionStatisticsConfigBuilder()
                    .minimumExpectedValue(1D)
                    .maximumExpectedValue(4096D)
                    .percentiles(NO_PERCENTILES);
        }

        private DistributionStatisticsConfig.Builder percentilesConfig() {
            return factory.distributionStatisticsConfigBuilder()
                    .minimumExpectedValue(1D)
                    .maximumExpectedValue(4096D)
                    .percentiles(PERCENTILES);
        }

        private DistributionStatisticsConfig.Builder bucketConfig(double... buckets) {
            return factory.distributionStatisticsConfigBuilder()
                    .minimumExpectedValue(1D)
                    .maximumExpectedValue(4096D)
                    .percentiles(NO_PERCENTILES)
                    .buckets(buckets);
        }

        private void preloadSamples() {
            for (int i = 0; i < PRELOAD_SAMPLE_COUNT; i++) {
                double amount = (i & 4095) + 1;
                long nanos = ((i & 1023) + 1) * 1000L;
                distributionSummaryPlain.record(amount);
                distributionSummaryPercentiles.record(amount);
                distributionSummaryHistogramSmall.record(amount);
                distributionSummaryHistogramLarge.record(amount);
                distributionSummaryHistogramDense.record(amount);
                timerPlain.record(nanos, TimeUnit.NANOSECONDS);
                timerPercentiles.record(nanos, TimeUnit.NANOSECONDS);
                timerHistogramSmall.record(nanos, TimeUnit.NANOSECONDS);
                timerHistogramLarge.record(nanos, TimeUnit.NANOSECONDS);
                timerHistogramDense.record(nanos, TimeUnit.NANOSECONDS);
            }
        }

        private void addFormatMeters() {
            for (int i = 0; i < FORMAT_METER_COUNT; i++) {
                List<Tag> tags = List.of(tag("id", Integer.toString(i)),
                                         tag("kind", "format"));
                Counter formatCounter = registry.getOrCreate(factory.counterBuilder("format.counter")
                                                                     .tags(tags));
                formatCounter.increment(i + 1);
                DistributionSummary formatSummary = registry.getOrCreate(
                        factory.distributionSummaryBuilder("format.summary",
                                                           bucketConfig(SMALL_SUMMARY_BUCKETS))
                                .tags(tags));
                formatSummary.record(i + 1);
                Timer formatTimer = registry.getOrCreate(factory.timerBuilder("format.timer")
                                                                 .tags(tags)
                                                                 .percentiles(PERCENTILES)
                                                                 .buckets(SMALL_TIMER_BUCKETS));
                formatTimer.record((i + 1L) * 1000L, TimeUnit.NANOSECONDS);
            }
        }

        private Tag tag(String key, String value) {
            return factory.tagCreate(key, value);
        }

        private MeterRegistryFormatter formatter(MediaType mediaType, List<String> nameSelection) {
            FormatterContext context = FormatterContext.builder()
                    .mediaType(mediaType)
                    .metricsConfig(metricsConfig)
                    .nameSelection(nameSelection)
                    .build();
            Optional<MeterRegistryFormatter> formatter = formatterProvider()
                    .formatter(context, registry);
            if (formatter.isEmpty()) {
                throw new IllegalStateException("No formatter for " + mediaType);
            }
            if (formatter.get().format().isEmpty()) {
                throw new IllegalStateException("Formatter produced no output for " + mediaType);
            }
            return formatter.get();
        }

        private MeterRegistryFormatter jsonFormatter(List<String> nameSelection) {
            FormatterContext context = FormatterContext.builder()
                    .mediaType(MediaTypes.APPLICATION_JSON)
                    .metricsConfig(metricsConfig)
                    .nameSelection(nameSelection)
                    .build();
            Optional<MeterRegistryFormatter> formatter = new JsonMeterRegistryFormatterProvider()
                    .formatter(context, registry);
            if (formatter.isEmpty()) {
                throw new IllegalStateException("No JSON formatter");
            }
            if (formatter.get().format().isEmpty()) {
                throw new IllegalStateException("JSON formatter produced no output");
            }
            return formatter.get();
        }
    }

}
