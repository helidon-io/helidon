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

package io.helidon.metrics.providers.helidon;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.StreamSupport;

import io.helidon.metrics.api.Bucket;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.HistogramSnapshot;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.Timer;
import io.helidon.metrics.api.ValueAtPercentile;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertAll;

class TestHelidonHistogramRecords {
    private static final List<Integer> AMOUNTS = List.of(0, 1, 2, 3, 4, 5, 6, 9);
    private static final int RESERVOIR_CAPACITY = 4096;

    private HelidonMetricsFactory factory;
    private MeterRegistry registry;
    private DistributionSummary summary;
    private Timer timer;

    @BeforeEach
    void prepareHistograms() {
        factory = HelidonMetricsFactory.create();
        registry = factory.createMeterRegistry(MetricsConfig.create());
        summary = registry.getOrCreate(factory.distributionSummaryBuilder("summary",
                factory.distributionStatisticsConfigBuilder().buckets(2, 5).percentiles(0, 0.5, 1)));
        timer = registry.getOrCreate(factory.timerBuilder("timer")
                                            .buckets(Duration.ofMillis(2), Duration.ofMillis(5))
                                            .percentiles(0, 0.5, 1));
    }

    @AfterEach
    void closeRegistry() {
        factory.close();
    }

    @Test
    void exactBoundariesAndOverflowAccumulateForTimersAndSummaries() {
        for (int amount : AMOUNTS) {
            summary.record(amount);
            timer.record(amount, TimeUnit.MILLISECONDS);
        }

        assertAll(() -> assertHistogram(summary.snapshot(), 8, 30, 9, List.of(2D, 5D), List.of(3L, 6L)),
                  () -> assertHistogram(timer.snapshot(), 8, 30_000_000, 9_000_000,
                                        List.of(2_000_000D, 5_000_000D), List.of(3L, 6L)));
    }

    @Test
    void snapshotsRemainUnchangedAfterLaterRecording() {
        HistogramSnapshot emptySummary = summary.snapshot();
        HistogramSnapshot emptyTimer = timer.snapshot();
        summary.record(1);
        timer.record(1, TimeUnit.MILLISECONDS);
        HistogramSnapshot firstSummary = summary.snapshot();
        HistogramSnapshot firstTimer = timer.snapshot();
        summary.record(9);
        timer.record(9, TimeUnit.MILLISECONDS);

        assertAll(() -> assertHistogram(emptySummary, 0, 0, 0, List.of(2D, 5D), List.of(0L, 0L)),
                  () -> assertHistogram(emptyTimer, 0, 0, 0, List.of(2_000_000D, 5_000_000D), List.of(0L, 0L)),
                  () -> assertPercentiles(emptySummary, Double.NaN),
                  () -> assertPercentiles(emptyTimer, Double.NaN),
                  () -> assertHistogram(firstSummary, 1, 1, 1, List.of(2D, 5D), List.of(1L, 1L)),
                  () -> assertHistogram(firstTimer, 1, 1_000_000, 1_000_000,
                                        List.of(2_000_000D, 5_000_000D), List.of(1L, 1L)),
                  () -> assertPercentiles(firstSummary, 1),
                  () -> assertPercentiles(firstTimer, 1_000_000),
                  () -> assertHistogram(summary.snapshot(), 2, 10, 9, List.of(2D, 5D), List.of(1L, 1L)),
                  () -> assertHistogram(timer.snapshot(), 2, 10_000_000, 9_000_000,
                                        List.of(2_000_000D, 5_000_000D), List.of(1L, 1L)));
    }

    @Test
    void concurrentRecordingRetainsExactTotalsAndCumulativeBuckets() throws Exception {
        var ready = new CountDownLatch(4);
        var start = new CountDownLatch(1);
        List<Future<?>> recordings = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                for (int worker = 0; worker < 4; worker++) {
                    recordings.add(executor.submit(() -> {
                        ready.countDown();
                        assertThat("Recording starts after all callers are ready", start.await(5, TimeUnit.SECONDS), is(true));
                        for (int repetition = 0; repetition < 256; repetition++) {
                            for (int amount : AMOUNTS) {
                                summary.record(amount);
                                timer.record(amount, TimeUnit.MILLISECONDS);
                            }
                        }
                        return null;
                    }));
                }
                assertThat("All recording callers are ready", ready.await(5, TimeUnit.SECONDS), is(true));
                start.countDown();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                for (Future<?> recording : recordings) {
                    recording.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                }
            } finally {
                start.countDown();
                recordings.forEach(recording -> recording.cancel(true));
            }
        }

        assertAll(() -> assertHistogram(summary.snapshot(), 8192, 30_720, 9,
                                        List.of(2D, 5D), List.of(3072L, 6144L)),
                  () -> assertHistogram(timer.snapshot(), 8192, 30_720_000_000D, 9_000_000,
                                        List.of(2_000_000D, 5_000_000D), List.of(3072L, 6144L)));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4})
    void concurrentSnapshotsKeepBucketsWithinObservationCount(int writers) throws Exception {
        var ready = new CountDownLatch(writers);
        var start = new CountDownLatch(1);
        var observed = new CountDownLatch(writers);
        var recording = new AtomicBoolean(true);
        List<Future<Long>> recordings = new ArrayList<>();
        long observations = 0;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                for (int worker = 0; worker < writers; worker++) {
                    recordings.add(executor.submit(() -> {
                        ready.countDown();
                        assertThat("Recording starts after all callers are ready", start.await(5, TimeUnit.SECONDS), is(true));
                        long recorded = 0;
                        do {
                            summary.record(1);
                            timer.record(1, TimeUnit.MILLISECONDS);
                            if (recorded++ == 0) {
                                observed.countDown();
                            }
                        } while (recording.get() && !Thread.currentThread().isInterrupted());
                        return recorded;
                    }));
                }
                assertThat("All recording callers are ready", ready.await(5, TimeUnit.SECONDS), is(true));
                start.countDown();
                assertThat("Every caller records before snapshots are checked", observed.await(5, TimeUnit.SECONDS), is(true));
                for (int sample = 0; sample < 2000; sample++) {
                    assertConcurrentSnapshot("summary", summary.snapshot());
                    assertConcurrentSnapshot("timer", timer.snapshot());
                }
            } finally {
                recording.set(false);
                start.countDown();
                try {
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                    for (Future<Long> result : recordings) {
                        observations += result.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                    }
                } finally {
                    recordings.forEach(result -> result.cancel(true));
                }
            }
        }

        assertHistogram(summary.snapshot(), observations, observations, 1, List.of(2D, 5D),
                        List.of(observations, observations));
        assertHistogram(timer.snapshot(), observations, observations * 1_000_000D, 1_000_000,
                        List.of(2_000_000D, 5_000_000D), List.of(observations, observations));
    }

    @Test
    void singleValuedPercentilesRemainExactBeyondReservoirCapacity() {
        for (int observation = 0; observation < 10_000; observation++) {
            summary.record(1.25);
            timer.record(1250, TimeUnit.MICROSECONDS);
        }

        assertAll(() -> assertHistogram(summary.snapshot(), 10_000, 12_500, 1.25,
                                        List.of(2D, 5D), List.of(10_000L, 10_000L)),
                  () -> assertHistogram(timer.snapshot(), 10_000, 12_500_000_000D, 1_250_000,
                                        List.of(2_000_000D, 5_000_000D), List.of(10_000L, 10_000L)),
                  () -> assertPercentiles(summary.snapshot(), 1.25),
                  () -> assertPercentiles(timer.snapshot(), 1_250_000));
    }

    @ParameterizedTest
    @CsvSource({"512, 1", "512, 4", "4095, 1", "4095, 4", "4096, 1", "4096, 4"})
    void percentilesRetainEveryObservationUntilReservoirCapacity(int observations, int writers) throws Exception {
        preparePercentileHistograms(fullSamplePercentiles());
        var ready = new CountDownLatch(writers);
        var start = new CountDownLatch(1);
        List<Future<?>> recordings = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                for (int writer = 0; writer < writers; writer++) {
                    int first = writer + 1;
                    recordings.add(executor.submit(() -> {
                        ready.countDown();
                        assertThat("Unique observations start after every writer is ready",
                                   start.await(5, TimeUnit.SECONDS), is(true));
                        for (int amount = first; amount <= observations; amount += writers) {
                            summary.record(amount);
                            timer.record(amount, TimeUnit.MILLISECONDS);
                        }
                        return null;
                    }));
                }
                assertThat("All unique-observation writers are ready", ready.await(5, TimeUnit.SECONDS), is(true));
                start.countDown();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                for (Future<?> recording : recordings) {
                    recording.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                }
            } finally {
                start.countDown();
                recordings.forEach(recording -> recording.cancel(true));
            }
        }

        HistogramSnapshot summarySnapshot = summary.snapshot();
        HistogramSnapshot timerSnapshot = timer.snapshot();
        double total = observations * (observations + 1D) / 2;
        assertAll(() -> assertHistogram(summarySnapshot, observations, total, observations,
                                        List.of(2D, 5D), List.of(2L, 5L)),
                  () -> assertHistogram(timerSnapshot, observations, total * 1_000_000, observations * 1_000_000D,
                                        List.of(2_000_000D, 5_000_000D), List.of(2L, 5L)),
                  () -> assertObservedRanks("summary with " + writers + " writers", summarySnapshot, observations, 1),
                  () -> assertObservedRanks("timer with " + writers + " writers",
                                            timerSnapshot, observations, 1_000_000));
    }

    @ParameterizedTest(name = "{0} observations, {1} writers")
    @CsvSource({"4097, 1", "8192, 1", "65536, 1", "4099, 4"})
    void crossingReservoirCapacityRetainsAFullDistinctSample(int observations, int writers) throws Exception {
        preparePercentileHistograms(fullSamplePercentiles());
        for (int amount = 1; amount < RESERVOIR_CAPACITY; amount++) {
            summary.record(amount);
            timer.record(amount, TimeUnit.MILLISECONDS);
        }

        var start = new CyclicBarrier(writers + 1);
        List<Future<?>> recordings = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                for (int writer = 0; writer < writers; writer++) {
                    int first = RESERVOIR_CAPACITY + writer;
                    recordings.add(executor.submit(() -> {
                        start.await(5, TimeUnit.SECONDS);
                        for (int amount = first; amount <= observations; amount += writers) {
                            summary.record(amount);
                            timer.record(amount, TimeUnit.MILLISECONDS);
                        }
                        return null;
                    }));
                }
                start.await(5, TimeUnit.SECONDS);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                for (Future<?> recording : recordings) {
                    recording.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                }
            } finally {
                recordings.forEach(recording -> recording.cancel(true));
            }
        }

        HistogramSnapshot summarySnapshot = summary.snapshot();
        HistogramSnapshot timerSnapshot = timer.snapshot();
        double total = observations * (observations + 1D) / 2;
        assertAll(() -> assertHistogram(summarySnapshot, observations, total, observations,
                                        List.of(2D, 5D), List.of(2L, 5L)),
                  () -> assertHistogram(timerSnapshot, observations, total * 1_000_000, observations * 1_000_000D,
                                        List.of(2_000_000D, 5_000_000D), List.of(2L, 5L)),
                  () -> assertFullDistinctSample("summary", summarySnapshot, observations, 1),
                  () -> assertFullDistinctSample("timer", timerSnapshot, observations, 1_000_000));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4})
    void concurrentSnapshotsRetainFullDistinctSamples(int writers) throws Exception {
        preparePercentileHistograms(fullSamplePercentiles());
        for (int observation = 1; observation <= RESERVOIR_CAPACITY; observation++) {
            summary.record(2 * observation);
            timer.record(2 * observation, TimeUnit.MILLISECONDS);
        }

        int observations = 65_536;
        var batch = new CyclicBarrier(writers + 1);
        List<Future<?>> recordings = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                for (int writer = 0; writer < writers; writer++) {
                    int first = writer + 1;
                    recordings.add(executor.submit(() -> {
                        for (int offset = RESERVOIR_CAPACITY; offset < observations; offset += RESERVOIR_CAPACITY) {
                            batch.await(5, TimeUnit.SECONDS);
                            for (int observation = offset + first;
                                 observation <= offset + RESERVOIR_CAPACITY;
                                 observation += writers) {
                                summary.record(2 * observation);
                                timer.record(2 * observation, TimeUnit.MILLISECONDS);
                            }
                            batch.await(5, TimeUnit.SECONDS);
                        }
                        return null;
                    }));
                }
                for (int offset = RESERVOIR_CAPACITY; offset < observations; offset += RESERVOIR_CAPACITY) {
                    batch.await(5, TimeUnit.SECONDS);
                    for (int sample = 0; sample < 8; sample++) {
                        String context = writers + " writers, batch starting at " + offset + ", snapshot " + sample;
                        assertFullDistinctSample("summary with " + context, summary.snapshot(),
                                                 offset + RESERVOIR_CAPACITY, 2);
                        assertFullDistinctSample("timer with " + context, timer.snapshot(),
                                                 offset + RESERVOIR_CAPACITY, 2_000_000);
                    }
                    batch.await(5, TimeUnit.SECONDS);
                }
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                for (Future<?> recording : recordings) {
                    recording.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                }
            } finally {
                recordings.forEach(recording -> recording.cancel(true));
            }
        }

        HistogramSnapshot summarySnapshot = summary.snapshot();
        HistogramSnapshot timerSnapshot = timer.snapshot();
        double total = observations * (observations + 1D);
        assertAll(() -> assertHistogram(summarySnapshot, observations, total, 2D * observations,
                                        List.of(2D, 5D), List.of(1L, 2L)),
                  () -> assertHistogram(timerSnapshot, observations, total * 1_000_000, observations * 2_000_000D,
                                        List.of(2_000_000D, 5_000_000D), List.of(1L, 2L)),
                  () -> assertFullDistinctSample("summary with " + writers + " writers",
                                                 summarySnapshot, observations, 2),
                  () -> assertFullDistinctSample("timer with " + writers + " writers",
                                                 timerSnapshot, observations, 2_000_000));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void percentilesRepresentLifetimeDistributionAcrossValuePhases(boolean highValuesFirst) {
        preparePercentileHistograms(0.5, 0.7, 0.9);
        for (int phase = 0; phase < 2; phase++) {
            boolean high = (phase == 0) == highValuesFirst;
            int amount = high ? 100 : 1;
            int observations = high ? 16_384 : 65_536;
            for (int observation = 0; observation < observations; observation++) {
                summary.record(amount);
                timer.record(amount, TimeUnit.MILLISECONDS);
            }
        }

        HistogramSnapshot summarySnapshot = summary.snapshot();
        HistogramSnapshot timerSnapshot = timer.snapshot();
        String order = highValuesFirst ? "high then low" : "low then high";
        assertAll(() -> assertHistogram(summarySnapshot, 81_920, 1_703_936, 100,
                                        List.of(2D, 5D), List.of(65_536L, 65_536L)),
                  () -> assertHistogram(timerSnapshot, 81_920, 1_703_936_000_000D, 100_000_000,
                                        List.of(2_000_000D, 5_000_000D), List.of(65_536L, 65_536L)),
                  () -> assertLifetimePercentiles("summary, " + order, summarySnapshot, 1),
                  () -> assertLifetimePercentiles("timer, " + order, timerSnapshot, 1_000_000));
    }

    @Test
    void invalidObservationsLeaveEmptyAndPopulatedHistogramsUnchanged() {
        recordInvalidObservations();
        HistogramSnapshot emptySummary = summary.snapshot();
        HistogramSnapshot emptyTimer = timer.snapshot();
        summary.record(1.25);
        timer.record(1250, TimeUnit.MICROSECONDS);
        recordInvalidObservations();
        HistogramSnapshot populatedSummary = summary.snapshot();
        HistogramSnapshot populatedTimer = timer.snapshot();
        summary.record(1.25);
        timer.record(1250, TimeUnit.MICROSECONDS);

        assertAll(() -> assertHistogram(emptySummary, 0, 0, 0, List.of(2D, 5D), List.of(0L, 0L)),
                  () -> assertHistogram(emptyTimer, 0, 0, 0, List.of(2_000_000D, 5_000_000D), List.of(0L, 0L)),
                  () -> assertPercentiles(emptySummary, Double.NaN),
                  () -> assertPercentiles(emptyTimer, Double.NaN),
                  () -> assertHistogram(populatedSummary, 1, 1.25, 1.25, List.of(2D, 5D), List.of(1L, 1L)),
                  () -> assertHistogram(populatedTimer, 1, 1_250_000, 1_250_000,
                                        List.of(2_000_000D, 5_000_000D), List.of(1L, 1L)),
                  () -> assertPercentiles(populatedSummary, 1.25),
                  () -> assertPercentiles(populatedTimer, 1_250_000),
                  () -> assertHistogram(summary.snapshot(), 2, 2.5, 1.25, List.of(2D, 5D), List.of(2L, 2L)),
                  () -> assertHistogram(timer.snapshot(), 2, 2_500_000, 1_250_000,
                                        List.of(2_000_000D, 5_000_000D), List.of(2L, 2L)));
    }

    @Test
    void summaryScaleAppliesToBucketsTotalsMeanAndPercentiles() {
        var scaled = registry.getOrCreate(factory.distributionSummaryBuilder("scaled.summary",
                factory.distributionStatisticsConfigBuilder().buckets(2, 5).percentiles(0, 0.5, 1))
                .scale(2));
        scaled.record(1.25);
        scaled.record(1.25);
        HistogramSnapshot singleValue = scaled.snapshot();
        scaled.record(1);
        scaled.record(3);
        HistogramSnapshot variedValues = scaled.snapshot();

        assertAll(() -> assertHistogram(singleValue, 2, 5, 2.5, List.of(2D, 5D), List.of(0L, 2L)),
                  () -> assertPercentiles(singleValue, 2.5),
                  () -> assertThat("Single-valued mean uses scaled observations", singleValue.mean(), is(2.5)),
                  () -> assertHistogram(variedValues, 4, 13, 6, List.of(2D, 5D), List.of(1L, 3L)),
                  () -> assertThat("Snapshot mean includes scaled values above all buckets", variedValues.mean(), is(3.25)),
                  () -> assertThat("Summary count tracks observations, not scaled amounts", scaled.count(), is(4L)),
                  () -> assertThat("Summary total includes scaled overflow", scaled.totalAmount(), is(13D)),
                  () -> assertThat("Summary mean agrees with its snapshot", scaled.mean(), is(3.25)));
    }

    @ParameterizedTest(name = "scale {0}")
    @CsvSource({"0.0, 0.0, 2, 0, 2, 2, 0.0",
                "-0.0, -0.0, 2, 0, 2, 2, -0.0",
                "-1, -0.0, 1, 0, 1, 1, -0.0",
                "0.5, 0.0, 2, 2, 2, 2, 2",
                "1, 0.0, 2, 4, 1, 2, 4",
                "2, 0.0, 2, 8, 1, 1, 8"})
    void negativeSummaryAmountsAreRejectedBeforeScaling(double scale,
                                                        double zeroPercentile,
                                                        long expectedCount,
                                                        double expectedTotal,
                                                        long lowerBucket,
                                                        long upperBucket,
                                                        double largestPercentile) {
        DistributionSummary scaled = registry.getOrCreate(factory.distributionSummaryBuilder("signed.scaled.summary",
                factory.distributionStatisticsConfigBuilder().buckets(2, 5).percentiles(0, 0.5, 1))
                .scale(scale));
        List<Double> rejected = List.of(-1D, -Double.MIN_VALUE, Double.NaN,
                                        Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        rejected.forEach(scaled::record);
        HistogramSnapshot empty = scaled.snapshot();
        scaled.record(0);
        rejected.forEach(scaled::record);
        HistogramSnapshot populated = scaled.snapshot();
        scaled.record(4);
        HistogramSnapshot positive = scaled.snapshot();

        assertAll(() -> assertHistogram(empty, 0, 0, 0, List.of(2D, 5D), List.of(0L, 0L)),
                  () -> assertPercentiles(empty, Double.NaN),
                  () -> assertHistogram(populated, 1, 0, 0, List.of(2D, 5D), List.of(1L, 1L)),
                  () -> assertPercentiles(populated, zeroPercentile),
                  () -> assertHistogram(positive, expectedCount, expectedTotal, expectedTotal,
                                        List.of(2D, 5D), List.of(lowerBucket, upperBucket)),
                  () -> assertThat("Valid observations retain their scaled percentile values, including signed zero",
                                   StreamSupport.stream(positive.percentileValues().spliterator(), false)
                                           .map(ValueAtPercentile::value).toList(),
                                   contains(zeroPercentile, zeroPercentile, largestPercentile)));
    }

    @Test
    void positiveSummarySubnormalUnderflowRemainsAnObservation() {
        DistributionSummary scaled = registry.getOrCreate(factory.distributionSummaryBuilder("underflow.summary",
                factory.distributionStatisticsConfigBuilder().buckets(2, 5).percentiles(0, 0.5, 1))
                .scale(0.5));
        scaled.record(Double.MIN_VALUE);
        HistogramSnapshot snapshot = scaled.snapshot();

        assertAll(() -> assertHistogram(snapshot, 1, 0, 0, List.of(2D, 5D), List.of(1L, 1L)),
                  () -> assertPercentiles(snapshot, 0));
    }

    private static double[] fullSamplePercentiles() {
        double[] percentiles = new double[RESERVOIR_CAPACITY];
        for (int index = 0; index < percentiles.length; index++) {
            percentiles[index] = (index + 1D) / RESERVOIR_CAPACITY;
        }
        return percentiles;
    }

    private static void assertObservedRanks(String name, HistogramSnapshot snapshot, int observations, double scale) {
        List<? extends ValueAtPercentile> percentiles = StreamSupport
                .stream(snapshot.percentileValues().spliterator(), false)
                .toList();
        assertThat(name + " reports every requested percentile", percentiles.size(), is(RESERVOIR_CAPACITY));
        for (int index = 0; index < percentiles.size(); index++) {
            double coordinate = (index + 1D) / RESERVOIR_CAPACITY;
            ValueAtPercentile percentile = percentiles.get(index);
            assertThat(name + " percentile coordinate " + index, percentile.percentile(), is(coordinate));
            assertThat(name + " retains the observed rank at percentile " + coordinate + " of " + observations,
                       percentile.value(), is(Math.ceil(coordinate * observations) * scale));
        }
    }

    private static void assertFullDistinctSample(String name,
                                                HistogramSnapshot snapshot,
                                                int observations,
                                                double scale) {
        List<Double> values = StreamSupport.stream(snapshot.percentileValues().spliterator(), false)
                .map(ValueAtPercentile::value)
                .toList();
        assertThat(name + " reports all sample ranks", values.size(), is(RESERVOIR_CAPACITY));
        assertThat(name + " retains a full reservoir of distinct observations after crossing capacity",
                   values.stream().distinct().count(), is((long) RESERVOIR_CAPACITY));
        double previous = 0;
        for (double value : values) {
            assertThat(name + " sample ranks remain strictly increasing", value, greaterThan(previous));
            assertThat(name + " sampled value was observed", value / scale, is(Math.rint(value / scale)));
            assertThat(name + " sampled value is within the recorded range", value,
                       lessThanOrEqualTo(observations * scale));
            previous = value;
        }
    }

    private static void assertLifetimePercentiles(String name, HistogramSnapshot snapshot, double scale) {
        List<? extends ValueAtPercentile> percentiles = StreamSupport
                .stream(snapshot.percentileValues().spliterator(), false)
                .toList();
        assertThat(name + " percentile coordinates",
                   percentiles.stream().map(ValueAtPercentile::percentile).toList(), contains(0.5, 0.7, 0.9));
        assertThat(name + " percentiles reflect the lifetime 80% low / 20% high distribution",
                   percentiles.stream().map(ValueAtPercentile::value).toList(), contains(scale, scale, 100 * scale));
    }

    private static void assertConcurrentSnapshot(String name, HistogramSnapshot snapshot) {
        assertThat(name + " snapshot contains observations", snapshot.count(), greaterThan(0L));
        int buckets = 0;
        long previousCount = 0;
        for (Bucket bucket : snapshot.histogramCounts()) {
            assertThat(name + " cumulative count at boundary " + bucket.boundary(),
                       bucket.count(), greaterThanOrEqualTo(previousCount));
            assertThat(name + " bucket at boundary " + bucket.boundary() + " cannot exceed the snapshot count",
                       bucket.count(), lessThanOrEqualTo(snapshot.count()));
            previousCount = bucket.count();
            buckets++;
        }
        assertThat(name + " snapshot contains both configured buckets", buckets, is(2));
    }

    private static void assertHistogram(HistogramSnapshot snapshot,
                                        long count,
                                        double total,
                                        double max,
                                        List<Double> boundaries,
                                        List<Long> cumulativeCounts) {
        List<Bucket> buckets = StreamSupport.stream(snapshot.histogramCounts().spliterator(), false).toList();
        assertAll(() -> assertThat("All observations, including overflow, contribute to the count", snapshot.count(), is(count)),
                  () -> assertThat("All observations contribute to the total", snapshot.total(), is(total)),
                  () -> assertThat("Largest observed value", snapshot.max(), is(max)),
                  () -> assertThat("Configured histogram boundaries", buckets.stream().map(Bucket::boundary).toList(),
                                   is(boundaries)),
                  () -> assertThat("Each bucket includes observations equal to or below its boundary",
                                   buckets.stream().map(Bucket::count).toList(), is(cumulativeCounts)));
    }

    private static void assertPercentiles(HistogramSnapshot snapshot, double value) {
        List<? extends ValueAtPercentile> percentiles = StreamSupport
                .stream(snapshot.percentileValues().spliterator(), false)
                .toList();
        assertAll(() -> assertThat("Configured percentile coordinates",
                                  percentiles.stream().map(ValueAtPercentile::percentile).toList(), contains(0D, 0.5, 1D)),
                  () -> assertThat("Every percentile of a single-valued distribution is exact",
                                   percentiles.stream().map(ValueAtPercentile::value).toList(), contains(value, value, value)));
    }

    private void preparePercentileHistograms(double... percentiles) {
        summary = registry.getOrCreate(factory.distributionSummaryBuilder("sample.summary",
                factory.distributionStatisticsConfigBuilder().buckets(2, 5).percentiles(percentiles)));
        timer = registry.getOrCreate(factory.timerBuilder("sample.timer")
                                            .buckets(Duration.ofMillis(2), Duration.ofMillis(5))
                                            .percentiles(percentiles));
    }

    private void recordInvalidObservations() {
        for (double invalid : List.of(-1D, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)) {
            summary.record(invalid);
        }
        timer.record(-1, TimeUnit.MILLISECONDS);
        timer.record(Duration.ofNanos(-1));
    }
}
