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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import io.helidon.json.JsonObject;
import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.Timer;
import io.helidon.metrics.providers.helidon.HelidonMetricsFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.metrics.publishers.otlp.OtlpTestSupport.attributes;
import static io.helidon.metrics.publishers.otlp.OtlpTestSupport.collect;
import static io.helidon.metrics.publishers.otlp.OtlpTestSupport.dataPoints;
import static io.helidon.metrics.publishers.otlp.OtlpTestSupport.doubleValues;
import static io.helidon.metrics.publishers.otlp.OtlpTestSupport.longValue;
import static io.helidon.metrics.publishers.otlp.OtlpTestSupport.longValues;
import static io.helidon.metrics.publishers.otlp.OtlpTestSupport.metric;
import static io.helidon.metrics.publishers.otlp.OtlpTestSupport.metrics;
import static io.helidon.metrics.publishers.otlp.OtlpTestSupport.objects;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestOtlpEncoder {
    private HelidonMetricsFactory factory;
    private MeterRegistry registry;
    private MetricsConfig metricsConfig;
    private TestClock clock;

    @BeforeEach
    void prepareRegistry() {
        factory = HelidonMetricsFactory.create();
        metricsConfig = MetricsConfig.builder()
                .tags(List.of(factory.tagCreate("deployment", "test")))
                .appTagName("application")
                .appName("encoder")
                .build();
        clock = new TestClock();
        registry = factory.createMeterRegistry(clock, metricsConfig);
    }

    @AfterEach
    void closeRegistry() {
        factory.close();
    }

    @Test
    void groupsCountersAndPreservesAttributesAndResource() {
        registry.getOrCreate(factory.counterBuilder("requests")
                                     .description("Completed requests")
                                     .baseUnit("{request}")
                                     .addTag(factory.tagCreate("method", "GET")))
                .increment(7);
        var functionCount = new AtomicLong(11);
        registry.getOrCreate(factory.functionalCounterBuilder("requests", functionCount, AtomicLong::get)
                                     .description("Completed requests")
                                     .baseUnit("{request}")
                                     .addTag(factory.tagCreate("method", "POST")));

        try (var encoder = new OtlpEncoder(registry, metricsConfig, Map.of("service.name", "example"))) {
            JsonObject request = collect(encoder);
            var resource = objects(request, "resourceMetrics").getFirst();
            assertThat(attributes(resource.objectValue("resource").orElseThrow()), is(Map.of("service.name", "example")));
            var scope = objects(resource, "scopeMetrics").getFirst();
            assertThat(scope.objectValue("scope").orElseThrow().stringValue("name").orElseThrow(), is("io.helidon.metrics"));
            assertThat(objects(scope, "metrics").size(), is(1));
            JsonObject metric = metric(request, "requests");
            assertThat(metric.stringValue("unit").orElseThrow(), is("{request}"));
            assertThat(metric.stringValue("description").orElseThrow(), is("Completed requests"));
            var sum = metric.objectValue("sum").orElseThrow();
            assertThat(sum.intValue("aggregationTemporality").orElseThrow(), is(2));
            assertThat(sum.booleanValue("isMonotonic").orElseThrow(), is(true));
            assertThat(dataPoints(metric, "sum").size(), is(2));
            Map<String, Long> counts = dataPoints(metric, "sum").stream()
                    .collect(Collectors.toMap(point -> attributes(point).get("method"),
                                              point -> longValue(point, "asInt")));
            assertThat(counts, is(Map.of("GET", 7L, "POST", 11L)));
            dataPoints(metric, "sum").forEach(point -> {
                assertThat(attributes(point).get("deployment"), is("test"));
                assertThat(attributes(point).get("application"), is("encoder"));
                assertThat(longValue(point, "timeUnixNano"), is(TimeUnit.MILLISECONDS.toNanos(clock.wallTime())));
                assertThat("Integer oneof excludes asDouble", point.containsKey("asDouble"), is(false));
            });
        }
    }

    @Test
    void preservesGaugePrecisionAndNonfiniteValues() {
        long exactLong = 9_007_199_254_740_993L;
        registry.getOrCreate(factory.gaugeBuilder("integer", () -> exactLong).baseUnit("By"));
        registry.getOrCreate(factory.gaugeBuilder("fraction", () -> 3.25));
        registry.getOrCreate(factory.gaugeBuilder("unavailable", () -> Double.NaN));
        registry.getOrCreate(factory.gaugeBuilder("unbounded", () -> Double.POSITIVE_INFINITY));
        registry.getOrCreate(factory.gaugeBuilder("negative.unbounded", () -> Double.NEGATIVE_INFINITY));

        try (var encoder = new OtlpEncoder(registry, metricsConfig, Map.of())) {
            JsonObject request = collect(encoder);
            var integer = metric(request, "integer");
            assertThat(integer.stringValue("unit").orElseThrow(), is("By"));
            JsonObject integerPoint = dataPoints(integer, "gauge").getFirst();
            assertThat(longValue(integerPoint, "asInt"), is(exactLong));
            assertThat(integerPoint.containsKey("asDouble"), is(false));
            assertThat(integerPoint.stringValue("startTimeUnixNano").orElse("0"), is("0"));
            JsonObject fraction = dataPoints(metric(request, "fraction"), "gauge").getFirst();
            assertThat(fraction.doubleValue("asDouble").orElseThrow(), is(3.25));
            assertThat(fraction.containsKey("asInt"), is(false));
            assertThat(dataPoints(metric(request, "unavailable"), "gauge").getFirst()
                               .stringValue("asDouble").orElseThrow(), is("NaN"));
            assertThat(dataPoints(metric(request, "unbounded"), "gauge").getFirst()
                               .stringValue("asDouble").orElseThrow(), is("Infinity"));
            assertThat(dataPoints(metric(request, "negative.unbounded"), "gauge").getFirst()
                               .stringValue("asDouble").orElseThrow(), is("-Infinity"));
        }
    }

    @Test
    void preservesZeroOneofValuesAndLargeCounterPrecision() {
        long exactLong = 9_007_199_254_740_993L;
        registry.getOrCreate(factory.counterBuilder("zero.counter"));
        registry.getOrCreate(factory.counterBuilder("large.counter")).increment(exactLong);
        registry.getOrCreate(factory.gaugeBuilder("zero.integer", () -> 0L));
        registry.getOrCreate(factory.gaugeBuilder("zero.double", () -> 0D));
        registry.getOrCreate(factory.gaugeBuilder("negative.zero.double", () -> -0D));

        try (var encoder = new OtlpEncoder(registry, metricsConfig, Map.of())) {
            JsonObject request = collect(encoder);
            assertThat(longValue(dataPoints(metric(request, "large.counter"), "sum").getFirst(), "asInt"), is(exactLong));
            for (JsonObject point : List.of(dataPoints(metric(request, "zero.counter"), "sum").getFirst(),
                                            dataPoints(metric(request, "zero.integer"), "gauge").getFirst())) {
                assertThat("Zero integer is explicitly present", longValue(point, "asInt"), is(0L));
                assertThat("Integer oneof excludes asDouble", point.containsKey("asDouble"), is(false));
            }
            JsonObject point = dataPoints(metric(request, "zero.double"), "gauge").getFirst();
            assertThat("Zero double is explicitly present", point.doubleValue("asDouble").orElseThrow(), is(0D));
            assertThat("Double oneof excludes asInt", point.containsKey("asInt"), is(false));
            JsonObject negativeZero = dataPoints(metric(request, "negative.zero.double"), "gauge").getFirst();
            assertThat("The floating-point sign survives ProtoJSON encoding",
                       negativeZero.stringValue("asDouble").orElseThrow(), is("-0.0"));
            assertThat("Negative zero uses the double oneof", negativeZero.containsKey("asInt"), is(false));
        }
    }

    @Test
    void convertsTimerUnitsAndCumulativeBuckets() {
        Timer timer = registry.getOrCreate(factory.timerBuilder("duration")
                                                   .baseUnit(TimeUnit.MILLISECONDS)
                                                   .buckets(Duration.ofMillis(100), Duration.ofMillis(200)));
        timer.record(100, TimeUnit.MILLISECONDS);
        timer.record(150, TimeUnit.MILLISECONDS);
        timer.record(500, TimeUnit.MILLISECONDS);

        try (var encoder = new OtlpEncoder(registry, metricsConfig, Map.of())) {
            JsonObject metric = metric(collect(encoder), "duration");
            assertThat(metric.stringValue("unit").orElseThrow(), is("s"));
            assertThat(metric.objectValue("histogram").orElseThrow().intValue("aggregationTemporality").orElseThrow(), is(2));
            var point = dataPoints(metric, "histogram").getFirst();
            assertThat(longValue(point, "count"), is(3L));
            assertThat(point.doubleValue("sum").orElseThrow(), closeTo(0.75, 1E-12));
            assertThat(point.doubleValue("max").orElseThrow(), closeTo(0.5, 1E-12));
            assertThat(point.containsKey("min"), is(false));
            assertThat(doubleValues(point, "explicitBounds"), contains(0.1, 0.2));
            assertThat(longValues(point, "bucketCounts"), contains(1L, 1L, 1L));
        }
    }

    @Test
    void encodesSummaryHistogramAndUpperInclusiveBoundaries() {
        DistributionSummary summary = registry.getOrCreate(factory.distributionSummaryBuilder("size",
                factory.distributionStatisticsConfigBuilder().buckets(1, 3)).baseUnit("By"));
        for (int amount = 0; amount <= 4; amount++) {
            summary.record(amount);
        }

        try (var encoder = new OtlpEncoder(registry, metricsConfig, Map.of())) {
            JsonObject metric = metric(collect(encoder), "size");
            assertThat(metric.stringValue("unit").orElseThrow(), is("By"));
            var point = dataPoints(metric, "histogram").getFirst();
            assertThat(longValue(point, "count"), is(5L));
            assertThat(point.doubleValue("sum").orElseThrow(), is(10D));
            assertThat(point.doubleValue("max").orElseThrow(), is(4D));
            assertThat(doubleValues(point, "explicitBounds"), contains(1D, 3D));
            assertThat(longValues(point, "bucketCounts"), contains(2L, 2L, 1L));
        }
    }

    @Test
    void exportsDefaultHistogramInfinityAsImplicitOverflowBucket() {
        DistributionSummary summary = registry.getOrCreate(factory.distributionSummaryBuilder("default.histogram",
                factory.distributionStatisticsConfigBuilder()).publishPercentileHistogram(true));
        summary.record(1);
        summary.record(Double.MAX_VALUE);
        assertThat("The provider snapshot includes the default infinite upper bound",
                   StreamSupport.stream(summary.snapshot().histogramCounts().spliterator(), false)
                           .anyMatch(bucket -> bucket.boundary() == Double.POSITIVE_INFINITY), is(true));

        try (var encoder = new OtlpEncoder(registry, metricsConfig, Map.of())) {
            var point = dataPoints(metric(collect(encoder), "default.histogram"), "histogram").getFirst();
            assertValidHistogram(point);
            assertThat("OTLP explicit bounds remain finite", doubleValues(point, "explicitBounds").stream()
                    .allMatch(Double::isFinite), is(true));
            assertThat(doubleValues(point, "explicitBounds").size(), greaterThan(0));
            assertThat(longValue(point, "count"), is(2L));
            assertThat(longValues(point, "bucketCounts").getFirst(), is(1L));
            assertThat("Value above all finite bounds goes to the implicit overflow bucket",
                       longValues(point, "bucketCounts").getLast(), is(1L));
        }
    }

    @Test
    void exportsCountAndSumWithoutInventingBucketsFromPercentiles() {
        DistributionSummary summary = registry.getOrCreate(factory.distributionSummaryBuilder("summary",
                factory.distributionStatisticsConfigBuilder().percentiles(0.5, 0.95)));
        summary.record(2);
        summary.record(8);

        try (var encoder = new OtlpEncoder(registry, metricsConfig, Map.of())) {
            var point = dataPoints(metric(collect(encoder), "summary"), "histogram").getFirst();
            assertThat(longValue(point, "count"), is(2L));
            assertThat(point.doubleValue("sum").orElseThrow(), is(10D));
            assertThat(longValues(point, "bucketCounts"), empty());
            assertThat(doubleValues(point, "explicitBounds"), empty());
        }
    }

    @Test
    void keepsCumulativeStartAndRestartsRecreatedMeters() {
        Counter counter = registry.getOrCreate(factory.counterBuilder("counter"));
        counter.increment(7);
        try (var encoder = new OtlpEncoder(registry, metricsConfig, Map.of())) {
            var first = dataPoints(metric(collect(encoder), "counter"), "sum").getFirst();
            assertThat(longValue(first, "startTimeUnixNano"), is(longValue(first, "timeUnixNano")));
            clock.advance();
            counter.increment(3);
            var second = dataPoints(metric(collect(encoder), "counter"), "sum").getFirst();
            assertThat(longValue(second, "asInt"), is(10L));
            assertThat(longValue(second, "startTimeUnixNano"), is(longValue(first, "startTimeUnixNano")));
            assertThat(longValue(second, "timeUnixNano"), greaterThan(longValue(first, "timeUnixNano")));

            registry.remove(counter);
            registry.getOrCreate(factory.counterBuilder("counter")).increment(2);
            var replacement = dataPoints(metric(collect(encoder), "counter"), "sum").getFirst();
            assertThat(longValue(replacement, "asInt"), is(2L));
            assertThat(longValue(replacement, "startTimeUnixNano"), greaterThan(longValue(second, "timeUnixNano")));
            assertThat(longValue(replacement, "startTimeUnixNano"), is(longValue(replacement, "timeUnixNano")));

            registry.remove("counter", List.of());
            assertThat(objects(collect(encoder), "resourceMetrics"), empty());
        }
    }

    @Test
    void restartsDecreasingFunctionalCounters() {
        var count = new AtomicLong(9);
        registry.getOrCreate(factory.functionalCounterBuilder("counter", count, AtomicLong::get));
        try (var encoder = new OtlpEncoder(registry, metricsConfig, Map.of())) {
            var first = dataPoints(metric(collect(encoder), "counter"), "sum").getFirst();
            count.set(2);
            var second = dataPoints(metric(collect(encoder), "counter"), "sum").getFirst();
            assertThat(longValue(second, "asInt"), is(2L));
            assertThat(longValue(second, "startTimeUnixNano"), greaterThan(longValue(first, "timeUnixNano")));
            count.set(-1);
            assertThat(objects(collect(encoder), "resourceMetrics"), empty());
        }
    }

    @Test
    void omitsOverflowedSumAndEmptyMaximum() {
        DistributionSummary summary = registry.getOrCreate(factory.distributionSummaryBuilder("summary",
                factory.distributionStatisticsConfigBuilder()));
        try (var encoder = new OtlpEncoder(registry, metricsConfig, Map.of())) {
            var empty = dataPoints(metric(collect(encoder), "summary"), "histogram").getFirst();
            assertThat(empty.doubleValue("sum").orElseThrow(), is(0D));
            assertThat(empty.containsKey("max"), is(false));

            summary.record(Double.MAX_VALUE);
            summary.record(Double.MAX_VALUE);
            var overflow = dataPoints(metric(collect(encoder), "summary"), "histogram").getFirst();
            assertThat(longValue(overflow, "count"), is(2L));
            assertThat(overflow.containsKey("sum"), is(false));
            assertThat(overflow.doubleValue("max").orElseThrow(), is(Double.MAX_VALUE));
        }
    }

    @Test
    void concurrentRecordingProducesValidHistogramPopulations() throws Exception {
        DistributionSummary summary = registry.getOrCreate(factory.distributionSummaryBuilder("summary",
                factory.distributionStatisticsConfigBuilder().buckets(1, 5, 10)));
        var start = new CountDownLatch(1);
        try (var encoder = new OtlpEncoder(registry, metricsConfig, Map.of());
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> recordings = new ArrayList<>();
            for (int thread = 0; thread < 4; thread++) {
                recordings.add(executor.submit(() -> {
                    start.await();
                    for (int value = 0; value < 20_000; value++) {
                        summary.record(value % 15);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (int iteration = 0; iteration < 100; iteration++) {
                assertValidHistogram(dataPoints(metric(collect(encoder), "summary"), "histogram").getFirst());
            }
            for (Future<?> recording : recordings) {
                recording.get(10, TimeUnit.SECONDS);
            }
            JsonObject point = dataPoints(metric(collect(encoder), "summary"), "histogram").getFirst();
            assertValidHistogram(point);
            assertThat(longValue(point, "count"), is(80_000L));
        }
    }

    @Test
    void failingGaugeDoesNotSuppressOtherMeters() {
        registry.getOrCreate(factory.gaugeBuilder("failure", () -> {
            throw new IllegalStateException("Cannot sample");
        }));
        registry.getOrCreate(factory.counterBuilder("counter")).increment();
        try (var encoder = new OtlpEncoder(registry, metricsConfig, Map.of())) {
            JsonObject request = collect(encoder);
            assertThat(metrics(request).size(), is(1));
            assertThat(longValue(dataPoints(metric(request, "counter"), "sum").getFirst(), "asInt"), is(1L));
        }
    }

    @Test
    void closeStopsCollection() {
        var encoder = new OtlpEncoder(registry, metricsConfig, Map.of());
        encoder.close();
        assertThrows(IllegalStateException.class, encoder::collect);
    }

    private static void assertValidHistogram(JsonObject point) {
        assertThat(longValue(point, "count"), greaterThanOrEqualTo(0L));
        List<Long> counts = longValues(point, "bucketCounts");
        assertThat(counts.size(), is(doubleValues(point, "explicitBounds").size() + 1));
        counts.forEach(count -> assertThat(count, greaterThanOrEqualTo(0L)));
        assertThat(counts.stream().mapToLong(Long::longValue).sum(), is(longValue(point, "count")));
        if (longValue(point, "count") == 0) {
            assertThat(point.doubleValue("sum").orElseThrow(), is(0D));
        }
    }

    private static final class TestClock implements Clock {
        private long millis = 1_000;

        @Override
        public long wallTime() {
            return millis;
        }

        @Override
        public long monotonicTime() {
            return TimeUnit.MILLISECONDS.toNanos(millis);
        }

        private void advance() {
            millis += 1_000;
        }
    }
}
