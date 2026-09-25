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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;

class TestHelidonHistogramRecords {
    private static final List<Integer> AMOUNTS = List.of(0, 1, 2, 3, 4, 5, 6, 9);

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

    private void recordInvalidObservations() {
        for (double invalid : List.of(-1D, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)) {
            summary.record(invalid);
        }
        timer.record(-1, TimeUnit.MILLISECONDS);
        timer.record(Duration.ofNanos(-1));
    }
}
