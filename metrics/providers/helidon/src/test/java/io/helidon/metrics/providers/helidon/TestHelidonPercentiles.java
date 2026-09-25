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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.metrics.api.FormatterContext;
import io.helidon.metrics.api.HistogramSnapshot;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.ValueAtPercentile;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;

class TestHelidonPercentiles {
    private static final List<Double> REQUESTED = List.of(1D, 0.5, 1D, 0D, 0.5, 0D);
    private static final List<Double> DISTINCT = List.of(1D, 0.5, 0D);

    private HelidonMetricsFactory factory;
    private MetricsConfig metricsConfig;
    private MeterRegistry registry;

    @BeforeEach
    void prepareRegistry() {
        factory = HelidonMetricsFactory.create();
        metricsConfig = MetricsConfig.create();
        registry = factory.createMeterRegistry(metricsConfig);
    }

    @AfterEach
    void closeRegistry() {
        factory.close();
    }

    @Test
    void timerVarargsPercentilesAreUniqueInSnapshotsAndFormats() {
        double[] requested = {1, 0.5, 1, 0, 0.5, 0};
        var builder = factory.timerBuilder("percentile.timer").percentiles(requested);
        var timer = registry.getOrCreate(builder);

        assertAll(() -> verifyDistribution(timer::snapshot, () -> timer.record(250, TimeUnit.MILLISECONDS),
                                          "percentile_timer_seconds", 250_000_000D, 0.25),
                  () -> assertThat("Caller can reuse the original varargs array",
                                   Arrays.stream(requested).boxed().toList(), is(REQUESTED)),
                  () -> assertThat("Timer builder retains the caller's request", builder.percentiles(),
                                   contains(1D, 0.5, 1D, 0D, 0.5, 0D)));
    }

    @Test
    void summaryVarargsPercentilesAreUniqueInSnapshotsAndFormats() {
        double[] requested = {1, 0.5, 1, 0, 0.5, 0};
        var statistics = factory.distributionStatisticsConfigBuilder().percentiles(requested);
        var summary = registry.getOrCreate(factory.distributionSummaryBuilder("percentile.summary", statistics));

        assertAll(() -> verifyDistribution(summary::snapshot, () -> summary.record(1.25),
                                          "percentile_summary", 1.25, 1.25),
                  () -> assertThat("Caller can reuse the original varargs array",
                                   Arrays.stream(requested).boxed().toList(), is(REQUESTED)),
                  () -> assertThat("Statistics builder retains the caller's request", statistics.percentiles(),
                                   contains(1D, 0.5, 1D, 0D, 0.5, 0D)));
    }

    @Test
    void summaryIterablePercentilesAreUniqueInSnapshotsAndFormats() {
        var requested = new ArrayList<>(REQUESTED);
        var statistics = factory.distributionStatisticsConfigBuilder().percentiles(requested);
        var summary = registry.getOrCreate(factory.distributionSummaryBuilder("percentile.summary", statistics));

        assertAll(() -> verifyDistribution(summary::snapshot, () -> summary.record(1.25),
                                          "percentile_summary", 1.25, 1.25),
                  () -> assertThat("Caller can reuse the original iterable", requested, is(REQUESTED)),
                  () -> assertThat("Statistics builder retains the caller's request", statistics.percentiles(),
                                   contains(1D, 0.5, 1D, 0D, 0.5, 0D)));
    }

    @Test
    void uniquePercentilesKeepSnapshotOrderAndSortedOutput() {
        var timer = registry.getOrCreate(factory.timerBuilder("unique.timer").percentiles(1, 0.5, 0));
        var summary = registry.getOrCreate(factory.distributionSummaryBuilder("unique.summary",
                factory.distributionStatisticsConfigBuilder().percentiles(DISTINCT)));

        assertAll(() -> verifyDistribution(timer::snapshot, () -> timer.record(250, TimeUnit.MILLISECONDS),
                                          "unique_timer_seconds", 250_000_000D, 0.25),
                  () -> verifyDistribution(summary::snapshot, () -> summary.record(1.25),
                                          "unique_summary", 1.25, 1.25));
    }

    @Test
    void repeatedNaNRemainsAcceptedAsOneRequestedPercentile() {
        var timer = registry.getOrCreate(factory.timerBuilder("nan.timer").percentiles(Double.NaN, 0.5, Double.NaN));
        var summary = registry.getOrCreate(factory.distributionSummaryBuilder("nan.summary",
                factory.distributionStatisticsConfigBuilder().percentiles(List.of(Double.NaN, 0.5, Double.NaN))));
        timer.record(250, TimeUnit.MILLISECONDS);
        summary.record(1.25);

        assertAll(() -> verifySnapshot(timer.snapshot(), List.of(Double.NaN, 0.5), 250_000_000D, 1),
                  () -> verifySnapshot(summary.snapshot(), List.of(Double.NaN, 0.5), 1.25, 1));
    }

    private static void verifySnapshot(HistogramSnapshot snapshot,
                                       List<Double> expectedPercentiles,
                                       double expectedValue,
                                       long expectedCount) {
        List<? extends ValueAtPercentile> percentiles = StreamSupport
                .stream(snapshot.percentileValues().spliterator(), false)
                .toList();
        assertAll(() -> assertThat("Snapshot contains each percentile once in first-occurrence order",
                                  percentiles.stream().map(ValueAtPercentile::percentile).toList(), is(expectedPercentiles)),
                  () -> assertThat("Percentile configuration does not change observation counts",
                                   snapshot.count(), is(expectedCount)),
                  () -> assertThat("Snapshot percentile values are exact for empty or single-valued distributions",
                                   percentiles.stream().map(ValueAtPercentile::value).toList(),
                                   is(expectedPercentiles.stream().map(_ -> expectedValue).toList())));
    }

    private static void verifySamples(Map<MediaType, String> outputs, String name, double value) {
        List<String> expected = List.of(name + "{quantile=\"0.0\"} " + value,
                                       name + "{quantile=\"0.5\"} " + value,
                                       name + "{quantile=\"1.0\"} " + value);
        assertAll(outputs.entrySet().stream().map(entry -> () -> assertThat(
                entry.getKey() + " emits each quantile sample exactly once in sorted order",
                entry.getValue().lines().filter(line -> line.startsWith(name + "{")).toList(), is(expected))));
    }

    private void verifyDistribution(Supplier<HistogramSnapshot> snapshot,
                                    Runnable record,
                                    String name,
                                    double snapshotValue,
                                    double exportedValue) {
        HistogramSnapshot empty = snapshot.get();
        Map<MediaType, String> emptyOutput = format();
        record.run();
        HistogramSnapshot populated = snapshot.get();
        Map<MediaType, String> populatedOutput = format();

        assertAll(() -> verifySnapshot(empty, DISTINCT, Double.NaN, 0),
                  () -> verifySnapshot(populated, DISTINCT, snapshotValue, 1),
                  () -> verifySamples(emptyOutput, name, 0D),
                  () -> verifySamples(populatedOutput, name, exportedValue));
    }

    private Map<MediaType, String> format() {
        return List.of(MediaTypes.TEXT_PLAIN, MediaTypes.APPLICATION_OPENMETRICS_TEXT).stream()
                .collect(Collectors.toMap(mediaType -> mediaType, mediaType -> {
                    FormatterContext context = FormatterContext.builder()
                            .mediaType(mediaType)
                            .metricsConfig(metricsConfig)
                            .build();
                    return (String) new HelidonPrometheusFormatterProvider().formatter(context, registry)
                            .orElseThrow().format().orElseThrow();
                }));
    }
}
