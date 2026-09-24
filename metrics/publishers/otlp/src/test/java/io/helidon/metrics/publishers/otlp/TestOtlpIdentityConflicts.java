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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.helidon.json.JsonObject;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.providers.helidon.HelidonMetricsFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static io.helidon.metrics.publishers.otlp.OtlpTestSupport.attributes;
import static io.helidon.metrics.publishers.otlp.OtlpTestSupport.collect;
import static io.helidon.metrics.publishers.otlp.OtlpTestSupport.dataPoints;
import static io.helidon.metrics.publishers.otlp.OtlpTestSupport.longValue;
import static io.helidon.metrics.publishers.otlp.OtlpTestSupport.metrics;
import static io.helidon.metrics.publishers.otlp.OtlpTestSupport.objects;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Isolated
class TestOtlpIdentityConflicts {
    private final ConcurrentLinkedQueue<String> warnings = new ConcurrentLinkedQueue<>();
    private final Logger logger = Logger.getLogger(OtlpEncoder.class.getName());
    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                warnings.add(record.getMessage());
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };
    private Level previousLevel;
    private HelidonMetricsFactory factory;
    private MeterRegistry registry;
    private MetricsConfig metricsConfig;

    @BeforeEach
    void prepareRegistryAndLogging() {
        factory = HelidonMetricsFactory.create();
        metricsConfig = MetricsConfig.create();
        registry = factory.createMeterRegistry(metricsConfig);
        previousLevel = logger.getLevel();
        logger.addHandler(handler);
        logger.setLevel(Level.ALL);
    }

    @AfterEach
    void closeRegistryAndLogging() {
        try {
            factory.close();
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            handler.close();
        }
    }

    @Test
    void conflictingKindsWarnWithoutDroppingPoints() {
        counter("requests", "east", "{request}").increment(7);
        gauge("requests", "west", "{request}");
        var summary = registry.getOrCreate(factory.distributionSummaryBuilder("requests",
                factory.distributionStatisticsConfigBuilder())
                .baseUnit("{request}")
                .addTag(factory.tagCreate("region", "south")));
        summary.record(2);
        summary.record(8);

        try (var encoder = new OtlpEncoder(registry, metricsConfig, Map.of())) {
            List<JsonObject> metrics = metrics(collect(encoder));
            assertThat(metrics, hasSize(3));
            assertThat(metrics.stream().map(TestOtlpIdentityConflicts::dataKind).toList(),
                       containsInAnyOrder("sum", "gauge", "histogram"));
            for (JsonObject metric : metrics) {
                assertThat(metric.stringValue("name").orElseThrow(), is("requests"));
                assertThat(metric.stringValue("unit").orElseThrow(), is("{request}"));
                switch (dataKind(metric)) {
                    case "sum" -> {
                        assertThat(dataPoints(metric, "sum"), hasSize(1));
                        var point = dataPoints(metric, "sum").getFirst();
                        assertThat(longValue(point, "asInt"), is(7L));
                        assertThat(attributes(point).get("region"), is("east"));
                    }
                    case "gauge" -> {
                        assertThat(dataPoints(metric, "gauge"), hasSize(1));
                        var point = dataPoints(metric, "gauge").getFirst();
                        assertThat(longValue(point, "asInt"), is(3L));
                        assertThat(attributes(point).get("region"), is("west"));
                    }
                    case "histogram" -> {
                        assertThat(dataPoints(metric, "histogram"), hasSize(1));
                        var point = dataPoints(metric, "histogram").getFirst();
                        assertThat(longValue(point, "count"), is(2L));
                        assertThat(point.doubleValue("sum").orElseThrow(), is(10D));
                        assertThat(attributes(point).get("region"), is("south"));
                    }
                    default -> throw new AssertionError("Unexpected metric " + metric);
                }
            }
            assertWarning("requests", "SUM", "GAUGE", "HISTOGRAM", "{request}");
            assertThat(metrics(collect(encoder)), hasSize(3));
            assertThat("Unchanged conflicts do not repeat warnings", warnings, empty());
        }
    }

    @Test
    void conflictingUnitsWarnWithoutDroppingPoints() {
        counter("transfer", "east", "bytes").increment(7);
        counter("transfer", "west", "requests").increment(11);
        try (var encoder = new OtlpEncoder(registry, metricsConfig, Map.of())) {
            List<JsonObject> metrics = metrics(collect(encoder));
            assertThat(metrics, hasSize(2));
            assertThat(metrics.stream().collect(Collectors.toMap(metric -> metric.stringValue("unit").orElseThrow(),
                    metric -> longValue(dataPoints(metric, "sum").getFirst(), "asInt"))),
                       is(Map.of("bytes", 7L, "requests", 11L)));
            metrics.forEach(metric -> assertThat(dataPoints(metric, "sum"), hasSize(1)));
            assertWarning("transfer", "SUM", "bytes", "requests");
        }
    }

    @Test
    void compatibleExportedIdentitiesAggregateWithoutWarnings() {
        counter("requests", "east", "{request}").increment(7);
        var count = new AtomicLong(11);
        registry.getOrCreate(factory.functionalCounterBuilder("requests", count, AtomicLong::get)
                                     .baseUnit("{request}")
                                     .addTag(factory.tagCreate("region", "west")));
        registry.getOrCreate(factory.timerBuilder("duration")
                                     .baseUnit(TimeUnit.SECONDS)
                                     .addTag(factory.tagCreate("region", "east")))
                .record(1, TimeUnit.SECONDS);
        registry.getOrCreate(factory.timerBuilder("duration")
                                     .baseUnit(TimeUnit.MILLISECONDS)
                                     .addTag(factory.tagCreate("region", "west")))
                .record(250, TimeUnit.MILLISECONDS);
        try (var encoder = new OtlpEncoder(registry, metricsConfig, Map.of())) {
            List<JsonObject> metrics = metrics(collect(encoder));
            assertThat(metrics, hasSize(2));
            for (JsonObject metric : metrics) {
                if (metric.stringValue("name").orElseThrow().equals("requests")) {
                    assertThat(metric.stringValue("unit").orElseThrow(), is("{request}"));
                    assertThat(dataPoints(metric, "sum").stream().map(point -> longValue(point, "asInt")).toList(),
                               containsInAnyOrder(7L, 11L));
                } else {
                    assertThat(metric.stringValue("name").orElseThrow(), is("duration"));
                    assertThat(metric.stringValue("unit").orElseThrow(), is("s"));
                    assertThat(dataPoints(metric, "histogram").stream()
                                       .map(point -> point.doubleValue("sum").orElseThrow()).toList(),
                               containsInAnyOrder(1D, 0.25D));
                }
            }
            encoder.collect();
            assertThat(warnings, empty());
        }
    }

    @Test
    void warningsFollowIdentitySetsInsteadOfMeterInstancesOrOrder() {
        Counter counter = counter("requests", "east", "{request}");
        gauge("requests", "west", "{request}");
        try (var encoder = new OtlpEncoder(registry, metricsConfig, Map.of())) {
            encoder.collect();
            assertWarning("requests", "SUM", "GAUGE");

            registry.remove(counter);
            counter("requests", "north", "{request}");
            encoder.collect();
            assertThat("Recreating and reordering the same exported identities does not warn", warnings, empty());

            Gauge<Integer> differentUnit = gauge("requests", "south", "bytes");
            encoder.collect();
            assertWarning("requests", "SUM", "GAUGE", "{request}", "bytes");
            registry.remove(differentUnit);
            encoder.collect();
            assertWarning("requests", "SUM", "GAUGE");
            encoder.collect();
            assertThat("A changed set warns once even if it was seen earlier", warnings, empty());
        }
    }

    @Test
    void validAndEmptyCollectionsResetConflictWarnings() {
        Counter counter = counter("requests", "east", "{request}");
        Gauge<Integer> gauge = gauge("requests", "west", "{request}");
        try (var encoder = new OtlpEncoder(registry, metricsConfig, Map.of())) {
            encoder.collect();
            assertWarning("requests", "SUM", "GAUGE");
            registry.remove(gauge);
            assertThat(metrics(collect(encoder)), hasSize(1));
            assertThat(warnings, empty());
            gauge = gauge("requests", "west", "{request}");
            encoder.collect();
            assertWarning("requests", "SUM", "GAUGE");

            registry.remove(counter);
            registry.remove(gauge);
            assertThat(objects(collect(encoder), "resourceMetrics"), empty());
            assertThat(warnings, empty());
            counter("requests", "east", "{request}");
            gauge("requests", "west", "{request}");
            encoder.collect();
            assertWarning("requests", "SUM", "GAUGE");
        }
    }

    @Test
    void conflictWarningsAreIndependentForEachName() {
        counter("requests", "east", "{request}");
        Gauge<Integer> requests = gauge("requests", "west", "{request}");
        counter("responses", "east", "{request}");
        gauge("responses", "west", "{request}");
        try (var encoder = new OtlpEncoder(registry, metricsConfig, Map.of())) {
            assertThat(metrics(collect(encoder)), hasSize(4));
            assertThat(warnings, hasSize(2));
            assertThat(warnings, containsInAnyOrder(containsString("requests"), containsString("responses")));
            warnings.clear();
            registry.remove(requests);
            encoder.collect();
            assertThat("Resolving one name does not repeat another name's warning", warnings, empty());
            gauge("requests", "west", "{request}");
            encoder.collect();
            assertWarning("requests", "SUM", "GAUGE");
        }
    }

    @Test
    void encoderSessionsHaveIndependentWarningsAndRejectCollectionAfterClose() {
        counter("requests", "east", "{request}");
        gauge("requests", "west", "{request}");
        try (var first = new OtlpEncoder(registry, metricsConfig, Map.of());
             var second = new OtlpEncoder(registry, metricsConfig, Map.of())) {
            first.collect();
            assertWarning("requests", "SUM", "GAUGE");
            second.collect();
            assertWarning("requests", "SUM", "GAUGE");
            first.collect();
            second.collect();
            assertThat(warnings, empty());
            first.close();
            assertThrows(IllegalStateException.class, first::collect);
            assertThat(metrics(collect(second)), hasSize(2));
            assertThat(warnings, empty());
        }
    }

    @Test
    void onlyExportedPointsContributeConflictingIdentities() {
        var count = new AtomicLong(-1);
        registry.getOrCreate(factory.functionalCounterBuilder("requests", count, AtomicLong::get)
                                     .baseUnit("{request}")
                                     .addTag(factory.tagCreate("region", "east")));
        gauge("requests", "west", "{request}");
        try (var encoder = new OtlpEncoder(registry, metricsConfig, Map.of())) {
            assertThat(metrics(collect(encoder)), hasSize(1));
            assertThat("An omitted negative counter does not conflict", warnings, empty());
            count.set(1);
            assertThat(metrics(collect(encoder)), hasSize(2));
            assertWarning("requests", "SUM", "GAUGE");
            count.set(-1);
            assertThat(metrics(collect(encoder)), hasSize(1));
            assertThat(warnings, empty());
            count.set(2);
            assertThat(metrics(collect(encoder)), hasSize(2));
            assertWarning("requests", "SUM", "GAUGE");
        }
    }

    private Counter counter(String name, String region, String unit) {
        return registry.getOrCreate(factory.counterBuilder(name)
                                            .baseUnit(unit)
                                            .addTag(factory.tagCreate("region", region)));
    }

    private Gauge<Integer> gauge(String name, String region, String unit) {
        return registry.getOrCreate(factory.gaugeBuilder(name, () -> 3)
                                            .baseUnit(unit)
                                            .addTag(factory.tagCreate("region", region)));
    }

    private void assertWarning(String... details) {
        assertThat("Exactly one warning per conflicting name and identity set", warnings, hasSize(1));
        String warning = warnings.remove();
        for (String detail : details) {
            assertThat("Conflict diagnostic identifies " + detail, warning, containsString(detail));
        }
    }

    private static String dataKind(JsonObject metric) {
        List<String> kinds = List.of("sum", "gauge", "histogram").stream().filter(metric::containsKey).toList();
        assertThat("Metric contains exactly one data kind: " + metric, kinds, hasSize(1));
        return kinds.getFirst();
    }
}
