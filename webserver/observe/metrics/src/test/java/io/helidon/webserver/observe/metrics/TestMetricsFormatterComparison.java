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

package io.helidon.webserver.observe.metrics;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonValue;
import io.helidon.json.JsonValueType;
import io.helidon.metrics.api.DistributionStatisticsConfig;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.FormatterContext;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MeterRegistryFormatter;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.metrics.spi.MeterRegistryFormatterProvider;
import io.helidon.metrics.spi.MetricsFactoryProvider;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.DirectClient;

import io.micrometer.core.instrument.config.InvalidConfigurationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Compares provider output using identical Helidon API calls.
 */
class TestMetricsFormatterComparison {
    private static final Pattern SAMPLE = Pattern.compile("([a-zA-Z_:][a-zA-Z0-9_:]*)(?:\\{(.*)\\})?\\s+(\\S+)");
    private static final Pattern LABEL = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)=\"((?:\\\\.|[^\"\\\\])*)\"(?:,|$)");
    private static final String LABEL_VALUE = "quote\" slash\\ newline\n";
    private static final List<String> SELECTED_NAMES = List.of("comparison.counter", "comparison.summary", "comparison.timer");
    private static final long[] VALUES = {0, 1, 4, 5, 16, 17, 20};

    @ParameterizedTest(name = "{0}, {1}, {2}, recorded={3}")
    @MethodSource("outputCases")
    void sameMetricsProduceSameOutput(MediaType mediaType, Selection selection, Histogram histogram, boolean recorded) {
        try (Fixture helidon = new Fixture("helidon", "Helidon");
             Fixture micrometer = new Fixture("micrometer", "Micrometer")) {
            helidon.populate(histogram, recorded, false);
            micrometer.populate(histogram, recorded, false);
            Object actual = helidon.format(mediaType, selection);
            Object expected = micrometer.format(mediaType, selection);
            if (mediaType.equals(MediaTypes.APPLICATION_JSON)) {
                Map<List<String>, Double> helidonValues = jsonValues((JsonObject) actual);
                Map<List<String>, Double> micrometerValues = jsonValues((JsonObject) expected);
                assertAll("JSON output",
                          () -> assertValues(helidonValues, micrometerValues),
                          () -> verifyJsonTotals(helidonValues, selection, recorded),
                          () -> verifyJsonTotals(micrometerValues, selection, recorded));
            } else {
                PrometheusOutput helidonOutput = prometheusOutput((String) actual);
                PrometheusOutput micrometerOutput = prometheusOutput((String) expected);
                assertAll("Prometheus output",
                          () -> assertThat("Family help and types", helidonOutput.metadata(), is(micrometerOutput.metadata())),
                          () -> assertValues(helidonOutput.samples(), micrometerOutput.samples()),
                          () -> assertThat("OpenMetrics terminator", helidonOutput.eof(), is(micrometerOutput.eof())),
                          () -> verifyTotals(helidonOutput, selection, recorded),
                          () -> verifyTotals(micrometerOutput, selection, recorded));
            }
        }
    }

    @ParameterizedTest(name = "{0}, {1}, recorded={2}")
    @MethodSource("percentileCases")
    void sameSingleValuedDistributionProducesSamePercentiles(MediaType mediaType, Histogram histogram, boolean recorded) {
        try (Fixture helidon = new Fixture("helidon", "Helidon");
             Fixture micrometer = new Fixture("micrometer", "Micrometer")) {
            // One is exactly representable by both histogram implementations; no random samples or approximation tolerance.
            helidon.populate(histogram, recorded, true);
            micrometer.populate(histogram, recorded, true);
            Object actual = helidon.format(mediaType, Selection.NAMES);
            Object expected = micrometer.format(mediaType, Selection.NAMES);
            if (mediaType.equals(MediaTypes.APPLICATION_JSON)) {
                Map<List<String>, Double> helidonValues = jsonValues((JsonObject) actual);
                Map<List<String>, Double> micrometerValues = jsonValues((JsonObject) expected);
                assertValues(helidonValues, micrometerValues);
                List<List<String>> percentileKeys = helidonValues.keySet().stream()
                        .filter(path -> path.size() == 2 && path.getLast().startsWith("p0.")).toList();
                assertThat("JSON retains every configured percentile", percentileKeys.size(), is(12));
                for (List<String> key : percentileKeys) {
                    assertThat("Exact single-valued percentile: " + key, helidonValues.get(key),
                               is(recorded ? (key.getFirst().equals("comparison.timer") ? 1E-9 : 1D) : 0D));
                }
            } else {
                PrometheusOutput helidonOutput = prometheusOutput((String) actual);
                PrometheusOutput micrometerOutput = prometheusOutput((String) expected);
                assertAll("Percentile output",
                          () -> assertThat("Family help and types", helidonOutput.metadata(), is(micrometerOutput.metadata())),
                          () -> assertValues(helidonOutput.samples(), micrometerOutput.samples()),
                          () -> assertThat("OpenMetrics terminator", helidonOutput.eof(), is(micrometerOutput.eof())),
                          () -> assertThat("Summary quantiles are present; histogram quantiles are omitted by default",
                                           helidonOutput.samples().keySet().stream()
                                                   .filter(id -> id.labels().containsKey("quantile")).count(),
                                           is(histogram == Histogram.PLAIN ? 12L : 0L)));
            }
        }
    }

    @ParameterizedTest(name = "{0}, {1}, recorded={2}")
    @MethodSource("distributionCountCases")
    void sameDistributionCountsHaveSameTextRepresentation(MediaType mediaType, Histogram histogram, boolean recorded) {
        try (Fixture helidon = new Fixture("helidon", "Helidon");
             Fixture micrometer = new Fixture("micrometer", "Micrometer")) {
            helidon.populate(histogram, recorded, false);
            micrometer.populate(histogram, recorded, false);
            PrometheusOutput actual = prometheusOutput((String) helidon.format(mediaType, Selection.NAMES));
            PrometheusOutput expected = prometheusOutput((String) micrometer.format(mediaType, Selection.NAMES));

            assertThat("Timer and summary count samples are present", expected.integerSamples().size(), greaterThan(0));
            assertValues(actual.integerSamples(), expected.integerSamples());
        }
    }

    @ParameterizedTest(name = "{0}, {2}, {3}, {4}")
    @MethodSource("gaugeFailureCases")
    void failingGaugePreservesOtherSamples(String packageName,
                                          String classPrefix,
                                          MediaType mediaType,
                                          GaugeForm form,
                                          GaugeFailure failure) {
        try (Fixture fixture = new Fixture(packageName, classPrefix)) {
            AtomicBoolean available = fixture.populateFailingGauge(form, failure);
            verifyGaugeSamples((String) fixture.format(mediaType, Selection.ALL), mediaType, Double.NaN);

            available.set(true);
            verifyGaugeSamples((String) fixture.format(mediaType, Selection.ALL), mediaType, 17D);
        }
    }

    @ParameterizedTest(name = "{0}, {2}, {3}, {4}")
    @MethodSource("gaugeFailureCases")
    void observerScrapesSurviveGaugeFailure(String packageName,
                                          String classPrefix,
                                          MediaType mediaType,
                                          GaugeForm form,
                                          GaugeFailure failure) {
        try (Fixture fixture = new Fixture(packageName, classPrefix)) {
            AtomicBoolean available = fixture.populateFailingGauge(form, failure);
            DirectClient client = fixture.observerClient();
            try {
                verifyGaugeSamples(scrape(client, mediaType), mediaType, Double.NaN);

                available.set(true);
                verifyGaugeSamples(scrape(client, mediaType), mediaType, 17D);
            } finally {
                client.close();
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("collisionCases")
    void micrometerRejectsConflictingPrometheusNames(Collision collision) {
        try (Fixture fixture = new Fixture("micrometer", "Micrometer")) {
            fixture.register(collision.firstKind(), collision.firstName(), "west");
            String secondRegion = collision.firstName().equals(collision.secondName()) ? "east" : "west";
            assertThrows(IllegalArgumentException.class,
                         () -> fixture.register(collision.secondKind(), collision.secondName(), secondRegion));
        }
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("collisionFormatCases")
    void helidonRejectsConflictingPrometheusNames(Collision collision, MediaType mediaType) {
        try (Fixture fixture = new Fixture("helidon", "Helidon")) {
            fixture.register(collision.firstKind(), collision.firstName(), "west");
            String secondRegion = collision.firstName().equals(collision.secondName()) ? "east" : "west";
            fixture.register(collision.secondKind(), collision.secondName(), secondRegion);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                              () -> fixture.format(mediaType, Selection.ALL));
            assertAll("Collision identifies the output name and both source names",
                      () -> assertThat(exception.getMessage(), containsString(collision.outputName())),
                      () -> assertThat(exception.getMessage(), containsString(collision.firstName())),
                      () -> assertThat(exception.getMessage(), containsString(collision.secondName())));
            if (collision.firstName().equals(collision.secondName())) {
                if (collision.firstKind() == MeterKind.DASH_UNIT_COUNTER
                        || collision.firstKind() == MeterKind.UNDERSCORE_UNIT_COUNTER) {
                    assertAll("Same-name sources identify their different base units",
                              () -> assertThat(exception.getMessage(), containsString("baseUnit=bytes-per-second")),
                              () -> assertThat(exception.getMessage(), containsString("baseUnit=bytes_per_second")));
                } else {
                    assertAll("Same-name sources identify their different meter types",
                              () -> assertThat(exception.getMessage(), containsString("type=COUNTER")),
                              () -> assertThat(exception.getMessage(), containsString("type=GAUGE")));
                }
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("prometheusMediaTypes")
    void tagVariantsWithDifferentTimerUnitsShareExportedFamily(MediaType mediaType) {
        try (Fixture helidon = new Fixture("helidon", "Helidon");
             Fixture micrometer = new Fixture("micrometer", "Micrometer")) {
            for (Fixture fixture : List.of(helidon, micrometer)) {
                fixture.registry.getOrCreate(fixture.factory.timerBuilder("latency")
                                                     .description("Latency")
                                                     .baseUnit(TimeUnit.SECONDS)
                                                     .percentiles(new double[0])
                                                     .tags(List.of(fixture.factory.tagCreate("region", "west"))))
                        .record(Duration.ofSeconds(1));
                fixture.registry.getOrCreate(fixture.factory.timerBuilder("latency")
                                                     .description("Latency")
                                                     .baseUnit(TimeUnit.MILLISECONDS)
                                                     .percentiles(new double[0])
                                                     .tags(List.of(fixture.factory.tagCreate("region", "east"))))
                        .record(Duration.ofSeconds(2));
            }
            PrometheusOutput actual = prometheusOutput((String) helidon.format(mediaType, Selection.ALL));
            PrometheusOutput expected = prometheusOutput((String) micrometer.format(mediaType, Selection.ALL));
            assertValues(actual.samples(), expected.samples());
            assertAll("Both tag variants report seconds in one timer family",
                      () -> assertThat(actual.metadata(), is(expected.metadata())),
                      () -> assertThat(actual.samples().get(new SampleId("latency_seconds_sum", Map.of("region", "west"))),
                                       is(1D)),
                      () -> assertThat(actual.samples().get(new SampleId("latency_seconds_sum", Map.of("region", "east"))),
                                       is(2D)));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("prometheusMediaTypes")
    void collisionChecksFollowSelectedMetersAndRemoval(MediaType mediaType) {
        try (Fixture fixture = new Fixture("helidon", "Helidon")) {
            var first = fixture.registry.getOrCreate(fixture.factory.counterBuilder("requests.sent")
                                                            .tags(List.of(fixture.factory.tagCreate("region", "west"))));
            first.increment(1);
            MeterRegistryFormatter formatter = fixture.textFormatter.formatter(FormatterContext.builder()
                                                                                       .metricsConfig(fixture.config)
                                                                                       .mediaType(mediaType)
                                                                                       .build(), fixture.registry)
                    .orElseThrow();
            assertThat(prometheusOutput((String) formatter.format().orElseThrow()).samples(),
                       is(Map.of(new SampleId("requests_sent_total", Map.of("region", "west")), 1D)));

            var second = fixture.registry.getOrCreate(fixture.factory.counterBuilder("requests_sent")
                                                             .tags(List.of(fixture.factory.tagCreate("region", "east"))));
            second.increment(2);
            assertThrows(IllegalArgumentException.class, formatter::format);

            for (FormatterContext selected : List.of(FormatterContext.builder()
                                                             .metricsConfig(fixture.config)
                                                             .mediaType(mediaType)
                                                             .nameSelection(List.of("requests.sent"))
                                                             .build(),
                                                     FormatterContext.builder()
                                                             .metricsConfig(fixture.config)
                                                             .mediaType(mediaType)
                                                             .tagSelections(Map.of("region", List.of("west")))
                                                             .build())) {
                Object output = fixture.textFormatter.formatter(selected, fixture.registry).orElseThrow()
                        .format().orElseThrow();
                assertThat("An excluded name cannot collide with the selected series",
                           prometheusOutput((String) output).samples(),
                           is(Map.of(new SampleId("requests_sent_total", Map.of("region", "west")), 1D)));
            }

            fixture.registry.remove(first);
            assertThat("The same formatter accepts the surviving source after removal",
                       prometheusOutput((String) formatter.format().orElseThrow()).samples(),
                       is(Map.of(new SampleId("requests_sent_total", Map.of("region", "east")), 2D)));
            fixture.registry.remove(second);
            fixture.registry.getOrCreate(fixture.factory.counterBuilder("requests.sent")).increment(3);
            assertThat("The same formatter accepts the original source when registered again",
                       prometheusOutput((String) formatter.format().orElseThrow()).samples(),
                       is(Map.of(new SampleId("requests_sent_total", Map.of()), 3D)));
        }
    }

    @ParameterizedTest(name = "{0}, {1}, recorded={2}")
    @MethodSource("duplicateBoundaryCases")
    void duplicateBoundariesProduceUniqueCumulativeSamples(DistributionRoute route, MediaType mediaType, boolean recorded) {
        try (Fixture helidon = new Fixture("helidon", "Helidon");
             Fixture micrometer = new Fixture("micrometer", "Micrometer")) {
            for (Fixture fixture : List.of(helidon, micrometer)) {
                Meter meter = fixture.registerDuplicateBoundaries(route);
                if (recorded) {
                    for (long value : new long[] {0, 1, 2, 3}) {
                        recordDistribution(meter, value);
                    }
                }
            }
            PrometheusOutput expected = prometheusOutput((String) micrometer.format(mediaType, Selection.ALL));
            verifyDistributionBuckets(expected, route, recorded);
            PrometheusOutput actual = prometheusOutput((String) helidon.format(mediaType, Selection.ALL));
            assertAll("Deduplicated cumulative buckets",
                      () -> assertValues(actual.samples(), expected.samples()),
                      () -> assertValues(actual.integerSamples(), expected.integerSamples()),
                      () -> assertThat(actual.metadata(), is(expected.metadata())),
                      () -> assertThat(actual.eof(), is(expected.eof())),
                      () -> verifyDistributionBuckets(actual, route, recorded));
        }
    }

    @ParameterizedTest(name = "{0}, {1}, percentile={2}")
    @MethodSource("invalidPercentileCases")
    void invalidPercentilesRejectRegistration(String classPrefix, DistributionRoute route, double percentile) {
        try (Fixture fixture = new Fixture(classPrefix.toLowerCase(Locale.ROOT), classPrefix)) {
            Supplier<Meter> registration = fixture.distributionRegistration(route, percentile);
            Class<? extends RuntimeException> expectedException = classPrefix.equals("Helidon")
                    ? IllegalArgumentException.class : InvalidConfigurationException.class;
            assertThrows(expectedException, registration::get);

            Meter replacement = fixture.distributionRegistration(route, 0, 1).get();
            recordDistribution(replacement, 1);
            verifyPercentileEndpoints(prometheusOutput((String) fixture.format(MediaTypes.TEXT_PLAIN, Selection.ALL)), route);
        }
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("percentileEndpointCases")
    void inclusivePercentilesMatchBothProviders(DistributionRoute route, MediaType mediaType) {
        try (Fixture helidon = new Fixture("helidon", "Helidon");
             Fixture micrometer = new Fixture("micrometer", "Micrometer")) {
            for (Fixture fixture : List.of(helidon, micrometer)) {
                recordDistribution(fixture.distributionRegistration(route, 0, 1).get(), 1);
            }
            PrometheusOutput actual = prometheusOutput((String) helidon.format(mediaType, Selection.ALL));
            PrometheusOutput expected = prometheusOutput((String) micrometer.format(mediaType, Selection.ALL));
            assertAll("Inclusive percentile endpoints",
                      () -> assertValues(actual.samples(), expected.samples()),
                      () -> assertThat(actual.metadata(), is(expected.metadata())),
                      () -> assertThat(actual.eof(), is(expected.eof())),
                      () -> verifyPercentileEndpoints(actual, route),
                      () -> verifyPercentileEndpoints(expected, route));
        }
    }

    private static Stream<Arguments> duplicateBoundaryCases() {
        return Stream.of(DistributionRoute.values())
                .flatMap(route -> prometheusMediaTypes()
                        .flatMap(mediaType -> Stream.of(false, true).map(recorded -> Arguments.of(route, mediaType, recorded))));
    }

    private static Stream<Arguments> invalidPercentileCases() {
        return Stream.of("Helidon", "Micrometer")
                .flatMap(provider -> Stream.of(DistributionRoute.values())
                        .flatMap(route -> Stream.of(-0.1, 1.1, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)
                                .map(percentile -> Arguments.of(provider, route, percentile))));
    }

    private static Stream<Arguments> percentileEndpointCases() {
        return Stream.of(DistributionRoute.values())
                .flatMap(route -> prometheusMediaTypes().map(mediaType -> Arguments.of(route, mediaType)));
    }

    private static void recordDistribution(Meter meter, long value) {
        switch (meter) {
        case Timer timer -> timer.record(Duration.ofNanos(value));
        case DistributionSummary summary -> summary.record(value);
        default -> throw new IllegalArgumentException("Expected a timer or distribution summary: " + meter);
        }
    }

    private static void verifyDistributionBuckets(PrometheusOutput output, DistributionRoute route, boolean recorded) {
        String name = route == DistributionRoute.TIMER ? "distribution_seconds" : "distribution";
        double scale = route == DistributionRoute.TIMER ? 1_000_000_000D : 1D;
        Map<SampleId, Double> expected = Map.of(
                new SampleId(name + "_bucket", Map.of("le", Double.toString(1 / scale))), recorded ? 2D : 0D,
                new SampleId(name + "_bucket", Map.of("le", Double.toString(2 / scale))), recorded ? 3D : 0D,
                new SampleId(name + "_bucket", Map.of("le", "Infinity")), recorded ? 4D : 0D,
                new SampleId(name + "_count", Map.of()), recorded ? 4D : 0D,
                new SampleId(name + "_sum", Map.of()), recorded ? 6 / scale : 0D,
                new SampleId(name + "_max", Map.of()), recorded ? 3 / scale : 0D);
        assertValues(output.samples(), expected);
    }

    private static void verifyPercentileEndpoints(PrometheusOutput output, DistributionRoute route) {
        String name = route == DistributionRoute.TIMER ? "distribution_seconds" : "distribution";
        double value = route == DistributionRoute.TIMER ? 1E-9 : 1D;
        assertAll("Both inclusive endpoints are usable",
                  () -> assertThat(output.samples().get(new SampleId(name, Map.of("quantile", "0.0"))), is(value)),
                  () -> assertThat(output.samples().get(new SampleId(name, Map.of("quantile", "1.0"))), is(value)),
                  () -> assertThat(output.samples().get(new SampleId(name + "_count", Map.of())), is(1D)));
    }

    private static Stream<Collision> collisionCases() {
        Stream<Collision> aliases = Stream.of(
                new Collision(MeterKind.COUNTER, "1requests", MeterKind.COUNTER, "2requests", "_requests"),
                new Collision(MeterKind.COUNTER, "requests.sent", MeterKind.COUNTER, "requests_sent", "requests_sent"),
                new Collision(MeterKind.COUNTER, "requests", MeterKind.COUNTER, "requests.total", "requests"),
                new Collision(MeterKind.BYTES_GAUGE, "payload", MeterKind.GAUGE, "payload.bytes", "payload_bytes"),
                new Collision(MeterKind.COUNTER, "requests", MeterKind.GAUGE, "requests", "requests"),
                new Collision(MeterKind.DASH_UNIT_COUNTER, "throughput", MeterKind.UNDERSCORE_UNIT_COUNTER,
                              "throughput", "throughput_bytes_per_second"));
        Stream<Collision> generated = Stream.of("count", "sum", "max")
                .flatMap(suffix -> Stream.of(
                        new Collision(MeterKind.TIMER, "latency", MeterKind.GAUGE,
                                      "latency.seconds." + suffix, "latency_seconds_" + suffix),
                        new Collision(MeterKind.SUMMARY, "sizes", MeterKind.GAUGE,
                                      "sizes." + suffix, "sizes_" + suffix)));
        return Stream.concat(aliases, generated)
                .flatMap(collision -> Stream.of(collision, new Collision(collision.secondKind(), collision.secondName(),
                                                                        collision.firstKind(), collision.firstName(),
                                                                        collision.outputName())));
    }

    private static Stream<Arguments> collisionFormatCases() {
        return collisionCases().flatMap(collision -> prometheusMediaTypes().map(mediaType -> Arguments.of(collision, mediaType)));
    }

    private static Stream<MediaType> prometheusMediaTypes() {
        return Stream.of(MediaTypes.TEXT_PLAIN, MediaTypes.APPLICATION_OPENMETRICS_TEXT);
    }

    private static Stream<Arguments> gaugeFailureCases() {
        return Stream.of("Helidon", "Micrometer")
                .flatMap(provider -> Stream.of(MediaTypes.TEXT_PLAIN, MediaTypes.APPLICATION_OPENMETRICS_TEXT)
                        .flatMap(mediaType -> Stream.of(GaugeForm.values())
                                .flatMap(form -> Stream.of(GaugeFailure.values())
                                        // A function gauge returns a primitive double and cannot return null.
                                        .filter(failure -> form == GaugeForm.SUPPLIER || failure != GaugeFailure.NULL_VALUE)
                                        .map(failure -> Arguments.of(provider.toLowerCase(Locale.ROOT),
                                                                     provider, mediaType, form, failure)))));
    }

    private static String scrape(DirectClient client, MediaType mediaType) {
        try (Http1ClientResponse response = client.get("/metrics").accept(mediaType).request()) {
            assertThat("Metrics endpoint remains available when a gauge fails", response.status().code(), is(200));
            return response.as(String.class);
        }
    }

    private static void verifyGaugeSamples(String text, MediaType mediaType, double expectedGaugeValue) {
        PrometheusOutput output = prometheusOutput(text);
        assertAll("Gauge failure isolation and recovery",
                  () -> assertThat("Failing or recovered gauge sample",
                                   output.samples().get(new SampleId("comparison_failed", Map.of())), is(expectedGaugeValue)),
                  () -> assertThat("Healthy counter remains in the scrape",
                                   output.samples().get(new SampleId("comparison_healthy_total", Map.of())), is(3D)),
                  () -> assertThat("OpenMetrics terminator remains present",
                                   output.eof(), is(mediaType.equals(MediaTypes.APPLICATION_OPENMETRICS_TEXT))));
    }

    private static Stream<Arguments> outputCases() {
        return mediaTypes().flatMap(mediaType -> Stream.of(Selection.values())
                .flatMap(selection -> Stream.of(Histogram.values())
                        .flatMap(histogram -> Stream.of(false, true)
                                .map(recorded -> Arguments.of(mediaType, selection, histogram, recorded)))));
    }

    private static Stream<Arguments> percentileCases() {
        return mediaTypes().flatMap(mediaType -> Stream.of(Histogram.PLAIN, Histogram.EXPLICIT)
                .flatMap(histogram -> Stream.of(false, true).map(recorded -> Arguments.of(mediaType, histogram, recorded))));
    }

    private static Stream<Arguments> distributionCountCases() {
        return Stream.of(MediaTypes.TEXT_PLAIN, MediaTypes.APPLICATION_OPENMETRICS_TEXT)
                .flatMap(mediaType -> Stream.of(Histogram.values())
                        .flatMap(histogram -> Stream.of(false, true)
                                .map(recorded -> Arguments.of(mediaType, histogram, recorded))));
    }

    private static Stream<MediaType> mediaTypes() {
        return Stream.of(MediaTypes.TEXT_PLAIN, MediaTypes.APPLICATION_OPENMETRICS_TEXT, MediaTypes.APPLICATION_JSON);
    }

    private static <K, V> void assertValues(Map<K, V> helidon, Map<K, V> micrometer) {
        List<String> differences = new ArrayList<>();
        micrometer.forEach((key, expected) -> {
            V actual = helidon.get(key);
            if (!Objects.equals(actual, expected)) {
                differences.add(key + ": Micrometer=" + expected + ", Helidon=" + actual);
            }
        });
        helidon.forEach((key, actual) -> {
            if (!micrometer.containsKey(key)) {
                differences.add(key + ": absent in Micrometer, Helidon=" + actual);
            }
        });
        assertThat("Number of field differences; first eight: " + differences.stream().sorted().limit(8).toList(),
                   differences.size(), is(0));
    }

    private static void verifyTotals(PrometheusOutput output, Selection selection, boolean recorded) {
        for (SampleId sample : output.samples().keySet()) {
            if (selection == Selection.NAMES) {
                assertThat("Selected sample name: " + sample,
                           SELECTED_NAMES.stream().anyMatch(name -> sample.name().startsWith(name.replace('.', '_') + "_")),
                           is(true));
            }
            if (selection == Selection.TAGS) {
                assertThat("Selected sample tags: " + sample, sample.labels().get("region"), is("west"));
            }
        }
        List<String> regions = selection == Selection.TAGS ? List.of("west") : List.of("west", "east");
        for (String region : regions) {
            Map<String, String> tags = Map.of("region", region, "label", "quote\\\" slash\\\\ newline\\n");
            assertThat("Counter total for " + region,
                       output.samples().get(new SampleId("comparison_counter_total", tags)), is(recorded ? 7D : 0D));
            assertThat("Summary count for " + region,
                       output.samples().get(new SampleId("comparison_summary_count", tags)), is(recorded ? 7D : 0D));
            assertThat("Summary sum for " + region,
                       output.samples().get(new SampleId("comparison_summary_sum", tags)), is(recorded ? 63D : 0D));
            assertThat("Timer count for " + region,
                       output.samples().get(new SampleId("comparison_timer_seconds_count", tags)), is(recorded ? 7D : 0D));
            assertThat("Timer seconds for " + region,
                       output.samples().get(new SampleId("comparison_timer_seconds_sum", tags)), is(recorded ? 63E-9 : 0D));
        }
    }

    private static Map<List<String>, Double> jsonValues(JsonObject object) {
        Map<List<String>, Double> result = new HashMap<>();
        for (String name : object.keysAsStrings()) {
            JsonValue value = object.value(name).orElseThrow();
            if (value.type() == JsonValueType.OBJECT) {
                JsonObject fields = value.asObject();
                for (String field : fields.keysAsStrings()) {
                    result.put(List.of(name, field), fields.numberValue(field).orElseThrow().doubleValue());
                }
            } else {
                result.put(List.of(name), value.asNumber().doubleValue());
            }
        }
        return result;
    }

    private static void verifyJsonTotals(Map<List<String>, Double> values, Selection selection, boolean recorded) {
        long expectedIdentities = selection == Selection.TAGS ? 1 : 2;
        assertThat("Selected counter identities", values.keySet().stream()
                .filter(path -> path.getFirst().startsWith("comparison.counter;")).count(), is(expectedIdentities));
        assertThat("Selected summary identities", values.keySet().stream()
                .filter(path -> path.getFirst().equals("comparison.summary") && path.getLast().startsWith("count;")).count(),
                   is(expectedIdentities));
        assertThat("Selected timer identities", values.keySet().stream()
                .filter(path -> path.getFirst().equals("comparison.timer") && path.getLast().startsWith("count;")).count(),
                   is(expectedIdentities));
        values.forEach((path, value) -> {
            String name = path.getFirst();
            String field = path.getLast();
            if (selection == Selection.NAMES) {
                assertThat("Name selection: " + path,
                           SELECTED_NAMES.stream().anyMatch(selected -> name.equals(selected) || name.startsWith(selected + ";")),
                           is(true));
            }
            if (selection == Selection.TAGS) {
                assertThat("Tag selection: " + path, field.contains(";region=west"), is(true));
            }
            if (name.startsWith("comparison.counter;") || field.startsWith("count;")) {
                assertThat("Counter or observation count: " + path, value, is(recorded ? 7D : 0D));
            } else if (name.equals("comparison.summary") && field.startsWith("total;")) {
                assertThat("Summary total: " + path, value, is(recorded ? 63D : 0D));
            } else if (name.equals("comparison.timer") && field.startsWith("elapsedTime;")) {
                assertThat("Timer total seconds: " + path, value, is(recorded ? 63E-9 : 0D));
            }
        });
    }

    private static PrometheusOutput prometheusOutput(String text) {
        Map<String, String> metadata = new TreeMap<>();
        Map<SampleId, Double> samples = new HashMap<>();
        Map<SampleId, String> integerSamples = new HashMap<>();
        boolean eof = false;
        for (String line : text.lines().toList()) {
            if (line.equals("# EOF")) {
                eof = true;
            } else if (line.startsWith("# HELP ") || line.startsWith("# TYPE ")) {
                String[] fields = line.split(" ", 4);
                assertThat("Complete metadata line: " + line, fields.length, is(4));
                assertThat("Unique metadata: " + line, metadata.put(fields[1] + " " + fields[2], fields[3]) == null, is(true));
            } else if (!line.isBlank()) {
                Matcher sample = SAMPLE.matcher(line);
                assertThat("Valid sample: " + line, sample.matches(), is(true));
                Map<String, String> labels = new TreeMap<>();
                if (sample.group(2) != null) {
                    Matcher label = LABEL.matcher(sample.group(2));
                    int end = 0;
                    while (label.find()) {
                        assertThat("Contiguous labels: " + line, label.start(), is(end));
                        String value = label.group(2);
                        if (label.group(1).equals("le") || label.group(1).equals("quantile")) {
                            value = Double.toString(number(value));
                        }
                        assertThat("Unique label: " + line, labels.put(label.group(1), value) == null, is(true));
                        end = label.end();
                    }
                    assertThat("Complete labels: " + line, end, is(sample.group(2).length()));
                }
                SampleId id = new SampleId(sample.group(1), labels);
                assertThat("Unique sample: " + line, samples.put(id, number(sample.group(3))) == null, is(true));
                if (id.name().endsWith("_count") || id.name().endsWith("_bucket")) {
                    integerSamples.put(id, sample.group(3));
                }
            }
        }
        assertThat("Non-empty samples", samples.size(), greaterThan(0));
        return new PrometheusOutput(metadata, samples, integerSamples, eof);
    }

    private static double number(String text) {
        return switch (text) {
        case "+Inf" -> Double.POSITIVE_INFINITY;
        case "-Inf" -> Double.NEGATIVE_INFINITY;
        default -> Double.parseDouble(text);
        };
    }

    private static <T> T provider(Class<T> contract, String name) {
        return ServiceLoader.load(contract).stream()
                .filter(provider -> provider.type().getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing provider " + name))
                .get();
    }

    private enum Selection {
        ALL,
        NAMES,
        TAGS
    }

    private enum Histogram {
        PLAIN,
        EXPLICIT,
        GENERATED,
        DEFAULT_GENERATED,
        COMBINED
    }

    private enum GaugeForm {
        SUPPLIER,
        FUNCTION
    }

    private enum GaugeFailure {
        RUNTIME_EXCEPTION,
        ASSERTION_ERROR,
        NULL_VALUE
    }

    private enum MeterKind {
        COUNTER,
        DASH_UNIT_COUNTER,
        UNDERSCORE_UNIT_COUNTER,
        GAUGE,
        BYTES_GAUGE,
        TIMER,
        SUMMARY
    }

    private enum DistributionRoute {
        TIMER,
        SUMMARY_VARARGS,
        SUMMARY_ITERABLE
    }

    private record Collision(MeterKind firstKind, String firstName,
                             MeterKind secondKind, String secondName, String outputName) {
    }

    private record SampleId(String name, Map<String, String> labels) {
    }

    private record PrometheusOutput(Map<String, String> metadata,
                                    Map<SampleId, Double> samples,
                                    Map<SampleId, String> integerSamples,
                                    boolean eof) {
    }

    private static final class Fixture implements AutoCloseable {
        private final ServiceRegistryManager services;
        private final MetricsConfig config = MetricsConfig.create();
        private final MetricsFactory factory;
        private final MeterRegistry registry;
        private final MeterRegistryFormatterProvider textFormatter;
        private final AtomicLong counterValue = new AtomicLong(11);
        private final AtomicLong gaugeValue = new AtomicLong(17);

        private Fixture(String packageName, String classPrefix) {
            services = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                             .discoverServices(false)
                                                             .discoverServicesFromServiceLoader(false)
                                                             .build());
            String prefix = "io.helidon.metrics.providers." + packageName + "." + classPrefix;
            factory = provider(MetricsFactoryProvider.class, prefix + "MetricsFactoryProvider")
                    .create(Config.empty(), config, List.of(), services.registry());
            registry = factory.createMeterRegistry(config);
            textFormatter = provider(MeterRegistryFormatterProvider.class, prefix + "PrometheusFormatterProvider");
        }

        private void populate(Histogram histogram, boolean recorded, boolean percentiles) {
            for (String region : List.of("west", "east")) {
                List<Tag> tags = List.of(factory.tagCreate("region", region), factory.tagCreate("label", LABEL_VALUE));
                var counter = registry.getOrCreate(factory.counterBuilder("comparison.counter")
                                                           .description("Comparison counter")
                                                           .tags(tags));
                registry.getOrCreate(factory.functionalCounterBuilder("comparison.functional", counterValue, AtomicLong::get)
                                             .description("Comparison functional counter")
                                             .tags(tags));
                registry.getOrCreate(factory.gaugeBuilder("comparison.supplier", gaugeValue::get)
                                             .description("Comparison supplier gauge")
                                             .tags(tags));
                registry.getOrCreate(factory.gaugeBuilder("comparison.function", gaugeValue, AtomicLong::doubleValue)
                                             .description("Comparison function gauge")
                                             .tags(tags));
                double[] quantiles = percentiles ? new double[] {0.5, 0.95, 0.99} : new double[0];
                DistributionStatisticsConfig.Builder summaryConfig = factory.distributionStatisticsConfigBuilder()
                        .percentiles(quantiles);
                Timer.Builder timerBuilder = factory.timerBuilder("comparison.timer")
                        .description("Comparison timer")
                        .tags(tags)
                        .percentiles(quantiles);
                if (histogram != Histogram.DEFAULT_GENERATED) {
                    summaryConfig.minimumExpectedValue(1D).maximumExpectedValue(16D);
                    timerBuilder.minimumExpectedValue(Duration.ofNanos(1)).maximumExpectedValue(Duration.ofNanos(16));
                }
                if (histogram == Histogram.EXPLICIT || histogram == Histogram.COMBINED) {
                    // Include an SLO beyond the expected maximum to check the complete union of configured buckets.
                    summaryConfig.buckets(1D, 4D, 17D);
                    timerBuilder.buckets(Duration.ofNanos(1), Duration.ofNanos(4), Duration.ofNanos(17));
                }
                boolean generated = histogram == Histogram.GENERATED
                        || histogram == Histogram.DEFAULT_GENERATED
                        || histogram == Histogram.COMBINED;
                DistributionSummary summary = registry.getOrCreate(factory.distributionSummaryBuilder("comparison.summary",
                                                                                                      summaryConfig)
                                                                            .description("Comparison summary")
                                                                            .tags(tags)
                                                                            .publishPercentileHistogram(generated));
                Timer timer = registry.getOrCreate(timerBuilder.publishPercentileHistogram(generated));
                if (recorded) {
                    counter.increment(7);
                    for (long value : percentiles ? new long[] {1, 1, 1, 1, 1, 1, 1} : VALUES) {
                        summary.record(value);
                        timer.record(Duration.ofNanos(value));
                    }
                }
            }
        }

        private AtomicBoolean populateFailingGauge(GaugeForm form, GaugeFailure failure) {
            var available = new AtomicBoolean();
            Supplier<Double> value = () -> {
                if (available.get()) {
                    return 17D;
                }
                return switch (failure) {
                case RUNTIME_EXCEPTION -> throw new IllegalStateException("Gauge value is unavailable");
                case ASSERTION_ERROR -> throw new AssertionError("Gauge callback failed");
                case NULL_VALUE -> null;
                };
            };
            switch (form) {
            case SUPPLIER -> registry.getOrCreate(factory.gaugeBuilder("comparison.failed", value));
            case FUNCTION -> registry.getOrCreate(factory.gaugeBuilder("comparison.failed", value, Supplier::get));
            }
            registry.getOrCreate(factory.counterBuilder("comparison.healthy")).increment(3);
            return available;
        }

        private Meter register(MeterKind kind, String name, String region) {
            List<Tag> tags = List.of(factory.tagCreate("region", region));
            return switch (kind) {
            case COUNTER -> registry.getOrCreate(factory.counterBuilder(name).tags(tags));
            case DASH_UNIT_COUNTER -> registry.getOrCreate(factory.counterBuilder(name).tags(tags).baseUnit("bytes-per-second"));
            case UNDERSCORE_UNIT_COUNTER -> registry.getOrCreate(factory.counterBuilder(name).tags(tags).baseUnit("bytes_per_second"));
            case GAUGE -> registry.getOrCreate(factory.gaugeBuilder(name, gaugeValue::get).tags(tags));
            case BYTES_GAUGE -> registry.getOrCreate(factory.gaugeBuilder(name, gaugeValue::get).tags(tags).baseUnit("bytes"));
            case TIMER -> registry.getOrCreate(factory.timerBuilder(name).tags(tags).percentiles(new double[0]));
            case SUMMARY -> registry.getOrCreate(factory.distributionSummaryBuilder(name,
                                                                                   factory.distributionStatisticsConfigBuilder()
                                                                                           .percentiles(new double[0]))
                                                        .tags(tags));
            };
        }

        private Meter registerDuplicateBoundaries(DistributionRoute route) {
            if (route == DistributionRoute.TIMER) {
                return registry.getOrCreate(factory.timerBuilder("distribution")
                                                    .description("Distribution")
                                                    .percentiles(new double[0])
                                                    .buckets(Duration.ofNanos(2), Duration.ofNanos(1),
                                                             Duration.ofNanos(2), Duration.ofNanos(1)));
            }
            DistributionStatisticsConfig.Builder statistics = factory.distributionStatisticsConfigBuilder()
                    .percentiles(new double[0]);
            if (route == DistributionRoute.SUMMARY_VARARGS) {
                statistics.buckets(2D, 1D, 2D, 1D);
            } else {
                statistics.buckets(List.of(2D, Double.POSITIVE_INFINITY, 1D, 2D, Double.POSITIVE_INFINITY, 1D));
            }
            return registry.getOrCreate(factory.distributionSummaryBuilder("distribution", statistics)
                                                .description("Distribution"));
        }

        private Supplier<Meter> distributionRegistration(DistributionRoute route, double... percentiles) {
            if (route == DistributionRoute.TIMER) {
                var builder = factory.timerBuilder("distribution")
                        .description("Distribution")
                        .percentiles(percentiles);
                return () -> registry.getOrCreate(builder);
            }
            DistributionStatisticsConfig.Builder statistics = factory.distributionStatisticsConfigBuilder();
            if (route == DistributionRoute.SUMMARY_VARARGS) {
                statistics.percentiles(percentiles);
            } else {
                statistics.percentiles(Arrays.stream(percentiles).boxed().toList());
            }
            var builder = factory.distributionSummaryBuilder("distribution", statistics).description("Distribution");
            return () -> registry.getOrCreate(builder);
        }

        private DirectClient observerClient() {
            var feature = new MetricsFeature(MetricsObserverConfig.builder()
                                                     .metricsConfig(config)
                                                     .meterRegistry(registry)
                                                     .buildPrototype(),
                                             () -> registry,
                                             () -> List.of(textFormatter));
            var routing = HttpRouting.builder();
            feature.register(routing, "/metrics");
            return new DirectClient(routing);
        }

        private Object format(MediaType mediaType, Selection selection) {
            FormatterContext.Builder context = FormatterContext.builder().mediaType(mediaType).metricsConfig(config);
            if (selection == Selection.NAMES) {
                context.nameSelection(SELECTED_NAMES);
            } else if (selection == Selection.TAGS) {
                context.tagSelections(Map.of("region", List.of("west")));
            }
            MeterRegistryFormatterProvider formatterProvider = mediaType.equals(MediaTypes.APPLICATION_JSON)
                    ? new JsonMeterRegistryFormatterProvider() : textFormatter;
            Object result = formatterProvider.formatter(context.build(), registry).orElseThrow().format().orElseThrow();
            assertThat("Formatter result", result, notNullValue());
            return result;
        }

        @Override
        public void close() {
            try {
                registry.close();
            } finally {
                try {
                    factory.close();
                } finally {
                    services.shutdown();
                }
            }
        }
    }
}
