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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.metrics.api.Bucket;
import io.helidon.metrics.api.FormatterContext;
import io.helidon.metrics.api.HistogramSnapshot;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;

class TestHelidonZeroBuckets {
    private HelidonMetricsFactory factory;
    private MetricsConfig metricsConfig;
    private MeterRegistry registry;

    static Stream<Arguments> summaryCases() {
        return Stream.of(Boundaries.values())
                .flatMap(boundaries -> Stream.of(Route.values()).map(route -> Arguments.of(boundaries, route)));
    }

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

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("summaryCases")
    void summaryZeroBucketsAreCanonicalAndCumulative(Boundaries boundaries, Route route) {
        double[] requestedArray = boundaries.requested.stream().mapToDouble(Double::doubleValue).toArray();
        var requestedList = new ArrayList<>(boundaries.requested);
        var statistics = factory.distributionStatisticsConfigBuilder().percentiles(new double[0]);
        switch (route) {
        case VARARGS -> statistics.buckets(requestedArray);
        case ITERABLE -> statistics.buckets(requestedList);
        }
        var configuration = statistics.build();
        var summary = registry.getOrCreate(factory.distributionSummaryBuilder("zero.summary", statistics));
        HistogramSnapshot empty = summary.snapshot();
        Map<MediaType, String> emptyOutput = format();

        summary.record(-0D);
        summary.record(0D);
        summary.record(Double.MIN_VALUE);
        summary.record(1);
        summary.record(2);
        HistogramSnapshot populated = summary.snapshot();
        Map<MediaType, String> populatedOutput = format();
        List<Double> expectedBoundaries = boundaries == Boundaries.NEGATIVE_ZERO_ONLY ? List.of(0D) : List.of(0D, 1D);
        List<Long> emptyCounts = expectedBoundaries.stream().map(_ -> 0L).toList();
        List<Long> populatedCounts = boundaries == Boundaries.NEGATIVE_ZERO_ONLY ? List.of(2L) : List.of(2L, 4L);

        assertAll(() -> verifySnapshot(empty, expectedBoundaries, emptyCounts, 0),
                  () -> verifySnapshot(populated, expectedBoundaries, populatedCounts, 5),
                  () -> verifyExposition(emptyOutput, "zero_summary", expectedBoundaries.size() == 2, false),
                  () -> verifyExposition(populatedOutput, "zero_summary", expectedBoundaries.size() == 2, true),
                  () -> assertThat("Caller array retains its original zero signs and order",
                                   Arrays.stream(requestedArray).boxed().toList(), is(boundaries.requested)),
                  () -> assertThat("Caller iterable remains reusable", requestedList, is(boundaries.requested)),
                  () -> assertThat("Builder keeps the configured boundaries",
                                   StreamSupport.stream(statistics.buckets().spliterator(), false).toList(),
                                   is(boundaries.requested)),
                  () -> assertThat("Built configuration keeps the configured boundaries",
                                   StreamSupport.stream(configuration.buckets().orElseThrow().spliterator(), false).toList(),
                                   is(boundaries.requested)));
    }

    @Test
    void duplicateTimerZeroDurationsRemainOneCumulativeBucket() {
        Duration[] requested = {Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO, Duration.ZERO};
        List<Duration> original = List.of(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO, Duration.ZERO);
        var builder = factory.timerBuilder("zero.timer").buckets(requested).percentiles(new double[0]);
        var timer = registry.getOrCreate(builder);
        HistogramSnapshot empty = timer.snapshot();
        Map<MediaType, String> emptyOutput = format();

        timer.record(Duration.ZERO);
        timer.record(0, TimeUnit.NANOSECONDS);
        timer.record(1, TimeUnit.NANOSECONDS);
        timer.record(1, TimeUnit.SECONDS);
        timer.record(2, TimeUnit.SECONDS);
        HistogramSnapshot populated = timer.snapshot();
        Map<MediaType, String> populatedOutput = format();

        assertAll(() -> verifySnapshot(empty, List.of(0D, 1_000_000_000D), List.of(0L, 0L), 0),
                  () -> verifySnapshot(populated, List.of(0D, 1_000_000_000D), List.of(2L, 4L), 5),
                  () -> verifyExposition(emptyOutput, "zero_timer_seconds", true, false),
                  () -> verifyExposition(populatedOutput, "zero_timer_seconds", true, true),
                  () -> assertThat("Caller duration array retains its order and duplicates", Arrays.asList(requested), is(original)),
                  () -> assertThat("Timer builder keeps the configured durations",
                                   StreamSupport.stream(builder.buckets().spliterator(), false).toList(), is(original)));
    }

    private static void verifySnapshot(HistogramSnapshot snapshot,
                                       List<Double> expectedBoundaries,
                                       List<Long> expectedCounts,
                                       long count) {
        List<Bucket> buckets = StreamSupport.stream(snapshot.histogramCounts().spliterator(), false).toList();
        assertAll(() -> assertThat("Snapshot has one positive-zero boundary; Double equality distinguishes its sign",
                                  buckets.stream().map(Bucket::boundary).toList(), is(expectedBoundaries)),
                  () -> assertThat("Both zero observations belong to the zero bucket; positive observations do not",
                                   buckets.stream().map(Bucket::count).toList(), is(expectedCounts)),
                  () -> assertThat("Observation count includes overflow", snapshot.count(), is(count)),
                  () -> assertThat("Bucket-only configuration does not request reservoir percentiles",
                                   snapshot.percentileValues(), emptyIterable()));
    }

    private static void verifyExposition(Map<MediaType, String> outputs, String name, boolean positiveBoundary, boolean recorded) {
        List<String> expected = new ArrayList<>();
        expected.add(name + "_bucket{le=\"0.0\"} " + (recorded ? 2 : 0));
        if (positiveBoundary) {
            expected.add(name + "_bucket{le=\"1.0\"} " + (recorded ? 4 : 0));
        }
        expected.add(name + "_bucket{le=\"+Inf\"} " + (recorded ? 5 : 0));
        expected.add(name + "_count " + (recorded ? 5 : 0));
        assertAll(outputs.entrySet().stream().map(entry -> () -> assertThat(
                entry.getKey() + " contains each literal bucket label once, with canonical positive zero",
                entry.getValue().lines()
                        .filter(line -> line.startsWith(name + "_bucket{") || line.startsWith(name + "_count "))
                        .toList(),
                is(expected))));
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

    private enum Boundaries {
        SIGNED_ZERO_PAIR(List.of(-0D, 0D, 1D)),
        REVERSED_DUPLICATES(List.of(1D, 0D, -0D, 0D, -0D, 1D)),
        NEGATIVE_ZERO_ONLY(List.of(-0D)),
        POSITIVE_ZERO_CONTROL(List.of(0D, 1D));

        private final List<Double> requested;

        Boundaries(List<Double> requested) {
            this.requested = requested;
        }
    }

    private enum Route {
        VARARGS,
        ITERABLE
    }
}
