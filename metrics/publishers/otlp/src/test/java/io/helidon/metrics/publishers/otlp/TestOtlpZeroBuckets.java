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

package io.helidon.metrics.publishers.otlp;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import io.helidon.json.JsonObject;
import io.helidon.json.JsonParser;
import io.helidon.json.binding.JsonBinding;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.Timer;
import io.helidon.metrics.providers.helidon.HelidonMetricsFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static io.helidon.metrics.publishers.otlp.OtlpTestSupport.dataPoints;
import static io.helidon.metrics.publishers.otlp.OtlpTestSupport.doubleValues;
import static io.helidon.metrics.publishers.otlp.OtlpTestSupport.longValue;
import static io.helidon.metrics.publishers.otlp.OtlpTestSupport.longValues;
import static io.helidon.metrics.publishers.otlp.OtlpTestSupport.objects;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;

class TestOtlpZeroBuckets {
    private static final String SUMMARY_NAME = "zero.bound.summary";
    private static final String TIMER_NAME = "zero.bound.timer";
    private static final Pattern FIRST_BOUND = Pattern.compile("\"explicitBounds\"\\s*:\\s*\\[\\s*([^,\\]]+)");
    private static final List<SummaryBuckets> SUMMARY_BUCKETS = List.of(
            new SummaryBuckets("negative then positive zero", new double[] {-0D, 0D, 1D}),
            new SummaryBuckets("positive then negative zero", new double[] {0D, -0D, 1D}),
            new SummaryBuckets("negative zero alone", new double[] {-0D, 1D}),
            new SummaryBuckets("positive zero alone", new double[] {0D, 1D}),
            new SummaryBuckets("unsorted repeated zeros", new double[] {1D, 0D, -0D, -0D, 0D}));
    private static final List<TimerBuckets> TIMER_BUCKETS = List.of(
            new TimerBuckets("duplicate zero", new Duration[] {Duration.ZERO, Duration.ZERO, Duration.ofSeconds(1)}),
            new TimerBuckets("reversed duplicate zero", new Duration[] {Duration.ofSeconds(1), Duration.ZERO, Duration.ZERO}),
            new TimerBuckets("single zero", new Duration[] {Duration.ZERO, Duration.ofSeconds(1)}));

    @Test
    void emptySummariesExportCanonicalZeroBounds() {
        assertSummaryCases("empty", new double[0], List.of(0L, 0L, 0L), 0D, 0D);
    }

    @Test
    void negativeZeroObservationsEnterTheZeroBucket() {
        assertSummaryCases("negative zero observation", new double[] {-0D}, List.of(1L, 0L, 0L), 0D, 0D);
    }

    @Test
    void positiveZeroObservationsEnterTheZeroBucket() {
        assertSummaryCases("positive zero observation", new double[] {0D}, List.of(1L, 0L, 0L), 0D, 0D);
    }

    @Test
    void tinyPositiveObservationsStayAboveTheZeroBucket() {
        assertSummaryCases("tiny positive observation", new double[] {Double.MIN_VALUE},
                           List.of(0L, 1L, 0L), Double.MIN_VALUE, Double.MIN_VALUE);
    }

    @Test
    void upperBoundObservationsRemainInclusive() {
        assertSummaryCases("upper-bound observation", new double[] {1D}, List.of(0L, 1L, 0L), 1D, 1D);
    }

    @Test
    void overflowObservationsRemainInTheImplicitBucket() {
        assertSummaryCases("overflow observation", new double[] {2D}, List.of(0L, 0L, 1L), 2D, 2D);
    }

    @Test
    void mixedSummaryObservationsPreserveEveryPopulation() {
        assertSummaryCases("mixed observations", new double[] {-0D, 0D, Double.MIN_VALUE, 1D, 2D},
                           List.of(2L, 2L, 1L), 3D, 2D);
    }

    @Test
    void emptyTimersCollapseDuplicateZeroBounds() {
        assertTimerCases("empty", new Duration[0], List.of(0L, 0L, 0L), 0D, 0D);
    }

    @Test
    void tinyPositiveTimerObservationsStayAboveTheZeroBucket() {
        assertTimerCases("tiny positive observation", new Duration[] {Duration.ofNanos(1)},
                         List.of(0L, 1L, 0L), 1E-9, 1E-9);
    }

    @Test
    void mixedTimerObservationsPreserveEveryPopulation() {
        assertTimerCases("mixed observations",
                         new Duration[] {Duration.ZERO, Duration.ZERO, Duration.ofNanos(1),
                                 Duration.ofSeconds(1), Duration.ofSeconds(2)},
                         List.of(2L, 2L, 1L), 3.000000001, 2D);
    }

    private static void assertSummaryCases(String scenario,
                                           double[] observations,
                                           List<Long> expectedCounts,
                                           double expectedSum,
                                           double expectedMax) {
        assertAll(scenario, SUMMARY_BUCKETS.stream().<Executable>map(buckets -> () -> {
            var factory = HelidonMetricsFactory.create();
            try {
                MetricsConfig config = MetricsConfig.create();
                MeterRegistry registry = factory.createMeterRegistry(config);
                DistributionSummary summary = registry.getOrCreate(factory.distributionSummaryBuilder(SUMMARY_NAME,
                        factory.distributionStatisticsConfigBuilder().percentiles(new double[0]).buckets(buckets.bounds()))
                                                                           .baseUnit("By"));
                for (double observation : observations) {
                    summary.record(observation);
                }
                try (var encoder = new OtlpEncoder(registry, config, Map.of())) {
                    String context = buckets.name() + ": " + scenario;
                    JsonObject point = histogramPoint(context, encoder, SUMMARY_NAME, "By");
                    assertHistogram(context, point, expectedCounts, observations.length, expectedSum, expectedMax, 0D);
                }
            } finally {
                factory.close();
            }
        }));
    }

    private static void assertTimerCases(String scenario,
                                         Duration[] observations,
                                         List<Long> expectedCounts,
                                         double expectedSum,
                                         double expectedMax) {
        assertAll(scenario, TIMER_BUCKETS.stream().<Executable>map(buckets -> () -> {
            var factory = HelidonMetricsFactory.create();
            try {
                MetricsConfig config = MetricsConfig.create();
                MeterRegistry registry = factory.createMeterRegistry(config);
                Timer timer = registry.getOrCreate(factory.timerBuilder(TIMER_NAME)
                                                           .percentiles(new double[0])
                                                           .buckets(buckets.bounds()));
                for (Duration observation : observations) {
                    timer.record(observation);
                }
                try (var encoder = new OtlpEncoder(registry, config, Map.of())) {
                    String context = buckets.name() + ": " + scenario;
                    JsonObject point = histogramPoint(context, encoder, TIMER_NAME, "s");
                    assertHistogram(context, point, expectedCounts, observations.length, expectedSum, expectedMax, 1E-15);
                }
            } finally {
                factory.close();
            }
        }));
    }

    private static JsonObject histogramPoint(String context, OtlpEncoder encoder, String name, String unit) {
        byte[] bytes = JsonBinding.create().serializeToBytes(encoder.collect());
        JsonObject request = JsonParser.create(bytes).readJsonObject();
        // JsonNumber converts through BigDecimal, which loses the sign of zero; inspect the original number token too.
        var firstBound = FIRST_BOUND.matcher(new String(bytes, StandardCharsets.UTF_8));
        assertThat(context + " explicit bounds are present", firstBound.find(), is(true));
        assertThat(context + " encoded first bound is positive zero",
                   Double.doubleToRawLongBits(Double.parseDouble(firstBound.group(1))), is(0L));
        assertThat(context + " resource count", objects(request, "resourceMetrics").size(), is(1));
        var resource = objects(request, "resourceMetrics").getFirst();
        assertThat(context + " scope count", objects(resource, "scopeMetrics").size(), is(1));
        var scope = objects(resource, "scopeMetrics").getFirst();
        assertThat(context + " metric count", objects(scope, "metrics").size(), is(1));
        var metric = objects(scope, "metrics").getFirst();
        assertAll(context,
                  () -> assertThat("metric name", metric.stringValue("name").orElseThrow(), is(name)),
                  () -> assertThat("metric unit", metric.stringValue("unit").orElseThrow(), is(unit)),
                  () -> assertThat("histogram present", metric.containsKey("histogram"), is(true)),
                  () -> assertThat("histogram temporality",
                                   metric.objectValue("histogram").orElseThrow().intValue("aggregationTemporality").orElseThrow(),
                                   is(2)),
                  () -> assertThat("histogram point count", dataPoints(metric, "histogram").size(), is(1)));
        return dataPoints(metric, "histogram").getFirst();
    }

    private static void assertHistogram(String context,
                                         JsonObject point,
                                         List<Long> expectedCounts,
                                         long expectedCount,
                                         double expectedSum,
                                         double expectedMax,
                                         double sumTolerance) {
        assertAll(context,
                  () -> assertThat("explicit bounds with canonical positive zero",
                                   doubleValues(point, "explicitBounds"), contains(0D, 1D)),
                  () -> assertThat("disjoint populations including overflow", longValues(point, "bucketCounts"), is(expectedCounts)),
                  () -> assertThat("one more population than explicit bounds",
                                   longValues(point, "bucketCounts").size(), is(doubleValues(point, "explicitBounds").size() + 1)),
                  () -> assertThat("observation count", longValue(point, "count"), is(expectedCount)),
                  () -> assertThat("all observations belong to exactly one bucket",
                                   longValues(point, "bucketCounts").stream().mapToLong(Long::longValue).sum(),
                                   is(longValue(point, "count"))),
                  () -> assertThat("sum present", point.containsKey("sum"), is(true)),
                  () -> assertThat("observation sum", point.doubleValue("sum").orElseThrow(), closeTo(expectedSum, sumTolerance)),
                  () -> assertThat("max present only for populated histograms", point.containsKey("max"), is(expectedCount > 0)),
                  () -> {
                      if (expectedCount > 0) {
                          assertThat("maximum observation", point.doubleValue("max").orElseThrow(), is(expectedMax));
                      }
                  });
    }

    private record SummaryBuckets(String name, double[] bounds) {
    }

    private record TimerBuckets(String name, Duration[] bounds) {
    }
}
