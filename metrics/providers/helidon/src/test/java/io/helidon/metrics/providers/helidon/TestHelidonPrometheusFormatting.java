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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.FormatterContext;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MeterRegistryFormatter;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.Timer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestHelidonPrometheusFormatting {

    @Test
    void formatsOpenMetricsFamilies() {
        MetricsConfig metricsConfig = MetricsConfig.create();
        HelidonMetricsFactory factory = HelidonMetricsFactory.builder()
                .metricsConfig(metricsConfig)
                .build();
        MeterRegistry registry = factory.globalRegistry();

        Counter first = registry.getOrCreate(factory.counterBuilder("repeat.count")
                                                     .addTag(new HelidonTag("color", "blue"))
                                                     .description("line one\nline two\\tail"));
        first.increment();
        Counter second = registry.getOrCreate(factory.counterBuilder("repeat.count")
                                                      .addTag(new HelidonTag("color", "red")));
        second.increment(2);

        Counter byteCounter = registry.getOrCreate(factory.counterBuilder("byte.count")
                                                           .baseUnit(Meter.BaseUnits.BYTES));
        byteCounter.increment(5);

        registry.getOrCreate(factory.gaugeBuilder("memory.used", () -> 7L)
                                     .baseUnit(Meter.BaseUnits.BYTES));
        registry.getOrCreate(factory.gaugeBuilder("already.bytes", () -> 8L)
                                     .baseUnit(Meter.BaseUnits.BYTES));

        Timer timer = registry.getOrCreate(factory.timerBuilder("request.time")
                                                   .description("Request time")
                                                   .percentiles(0.5)
                                                   .buckets(Duration.ofMillis(1), Duration.ofMillis(10)));
        timer.record(2, TimeUnit.MILLISECONDS);
        timer.record(7, TimeUnit.MILLISECONDS);

        Timer millisecondTimer = registry.getOrCreate(factory.timerBuilder("base.unit.timer")
                                                              .baseUnit(TimeUnit.MILLISECONDS));
        millisecondTimer.record(1500, TimeUnit.MILLISECONDS);

        DistributionSummary summary = registry.getOrCreate(
                factory.distributionSummaryBuilder("payload.size",
                                                   factory.distributionStatisticsConfigBuilder()
                                                           .percentiles(0.95, 0.5, 0.99))
                        .baseUnit(Meter.BaseUnits.BYTES));
        summary.record(5);
        summary.record(9);

        String output = format(metricsConfig, registry);

        assertThat(output,
                   allOf(containsString("# HELP repeat_count line one\\nline two\\\\tail\n"),
                         containsString("# TYPE repeat_count counter\n"),
                         containsString("repeat_count_total{color=\"blue\"} 1.0\n"),
                         containsString("repeat_count_total{color=\"red\"} 2.0\n"),
                         containsString("# TYPE byte_count_bytes counter\n"),
                         containsString("byte_count_bytes_total 5.0\n"),
                         containsString("# TYPE memory_used_bytes gauge\n"),
                         containsString("memory_used_bytes 7.0\n"),
                         containsString("already_bytes 8.0\n"),
                         not(containsString("already_bytes_bytes")),
                         containsString("# TYPE request_time_seconds histogram\n"),
                         containsString("request_time_seconds_bucket{le=\"0.001\"} 0\n"),
                         containsString("request_time_seconds_bucket{le=\"0.01\"} 2\n"),
                         containsString("request_time_seconds_bucket{le=\"+Inf\"} 2\n"),
                         containsString("request_time_seconds_sum 0.009\n"),
                         containsString("request_time_seconds_count 2\n"),
                         containsString("# TYPE request_time_seconds_max gauge\n"),
                         containsString("base_unit_timer_seconds_sum 1.5\n"),
                         containsString("base_unit_timer_seconds_count 1\n"),
                         containsString("payload_size_bytes{quantile=\"0.5\"} "),
                         containsString("payload_size_bytes_sum 14.0\n"),
                         containsString("payload_size_bytes_count 2\n"),
                         containsString("# TYPE payload_size_bytes_max gauge\n"),
                         not(containsString("payload_size{")),
                         not(containsString("payload_size_sum")),
                         endsWith("# EOF\n")));
        assertThat("p50 should be emitted before p95",
                   output.indexOf("payload_size_bytes{quantile=\"0.5\"}"),
                   lessThan(output.indexOf("payload_size_bytes{quantile=\"0.95\"}")));
        assertThat("p95 should be emitted before p99",
                   output.indexOf("payload_size_bytes{quantile=\"0.95\"}"),
                   lessThan(output.indexOf("payload_size_bytes{quantile=\"0.99\"}")));
        assertThat("Repeated TYPE line for same counter family",
                   output.indexOf("# TYPE repeat_count counter"),
                   is(output.lastIndexOf("# TYPE repeat_count counter")));
        assertThat("Counter family samples should be grouped",
                   output.indexOf("repeat_count_total{color=\"red\"}"),
                   lessThan(output.indexOf("# HELP request_time_seconds")));
        assertThat("Summary output must not include histogram buckets",
                   output,
                   not(containsString("payload_size_bytes_bucket")));
    }

    @Test
    void localRegistriesKeepOwnSystemTags() {
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        MetricsConfig firstConfig = MetricsConfig.builder()
                .tags(List.of(new HelidonTag("app", "first")))
                .build();
        MeterRegistry firstRegistry = factory.createMeterRegistry(firstConfig);
        firstRegistry.getOrCreate(factory.counterBuilder("registry.counter")).increment();

        MetricsConfig secondConfig = MetricsConfig.builder()
                .tags(List.of(new HelidonTag("app", "second")))
                .build();
        MeterRegistry secondRegistry = factory.createMeterRegistry(secondConfig);
        secondRegistry.getOrCreate(factory.counterBuilder("registry.counter")).increment();

        String firstOutput = format(firstConfig, firstRegistry);
        String secondOutput = format(secondConfig, secondRegistry);

        assertThat(firstOutput, containsString("registry_counter_total{app=\"first\"} 1.0\n"));
        assertThat(firstOutput, not(containsString("second")));
        assertThat(secondOutput, containsString("registry_counter_total{app=\"second\"} 1.0\n"));
        assertThat(secondOutput, not(containsString("first")));
    }

    @Test
    @SuppressWarnings("removal")
    void legacyScopeArgumentsAreIgnored() {
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        MetricsConfig metricsConfig = MetricsConfig.create();
        MeterRegistry registry = factory.createMeterRegistry(metricsConfig);
        registry.getOrCreate(factory.counterBuilder("unscoped.counter")).increment();
        var formatter = new HelidonPrometheusFormatterProvider()
                .formatter(MediaTypes.TEXT_PLAIN,
                           metricsConfig,
                           registry,
                           Optional.of("scope"),
                           List.of("missing"),
                           List.of())
                .orElseThrow();

        assertThat((String) formatter.format().orElseThrow(),
                   allOf(containsString("unscoped_counter_total 1.0\n"), not(containsString("scope="))));
    }

    @Test
    void publishPercentileHistogramUsesDefaultBuckets() {
        MetricsConfig metricsConfig = MetricsConfig.create();
        HelidonMetricsFactory factory = HelidonMetricsFactory.builder()
                .metricsConfig(metricsConfig)
                .build();
        MeterRegistry registry = factory.globalRegistry();

        Timer timer = registry.getOrCreate(factory.timerBuilder("default.histogram.timer")
                                                   .minimumExpectedValue(Duration.ofMillis(1))
                                                   .maximumExpectedValue(Duration.ofMillis(10))
                                                   .publishPercentileHistogram(true));
        timer.record(2, TimeUnit.MILLISECONDS);
        timer.record(11, TimeUnit.MILLISECONDS);
        DistributionSummary summary = registry.getOrCreate(
                factory.distributionSummaryBuilder("default.histogram.summary",
                                                   factory.distributionStatisticsConfigBuilder()
                                                           .minimumExpectedValue(1D)
                                                           .maximumExpectedValue(10D))
                        .publishPercentileHistogram(true));
        summary.record(2D);
        summary.record(11D);

        String output = format(metricsConfig, registry);

        assertThat(output,
                   allOf(containsString("# TYPE default_histogram_timer_seconds histogram\n"),
                         containsString("default_histogram_timer_seconds_bucket"
                                                + "{le=\"0.001\"} 0\n"),
                         containsString("default_histogram_timer_seconds_bucket"
                                                + "{le=\"0.01\"} 1\n"),
                         containsString("default_histogram_timer_seconds_bucket"
                                                + "{le=\"+Inf\"} 2\n"),
                         containsString("# TYPE default_histogram_summary histogram\n"),
                         containsString("default_histogram_summary_bucket"
                                                + "{le=\"1.0\"} 0\n"),
                         containsString("default_histogram_summary_bucket"
                                                + "{le=\"10.0\"} 1\n"),
                         containsString("default_histogram_summary_bucket"
                                                + "{le=\"+Inf\"} 2\n")));
    }

    @Test
    void publishPercentileHistogramUsesFallbackDefaultBuckets() {
        MetricsConfig metricsConfig = MetricsConfig.create();
        HelidonMetricsFactory factory = HelidonMetricsFactory.builder()
                .metricsConfig(metricsConfig)
                .build();
        MeterRegistry registry = factory.globalRegistry();

        Timer timer = registry.getOrCreate(factory.timerBuilder("fallback.histogram.timer")
                                                   .publishPercentileHistogram(true));
        timer.record(2, TimeUnit.MILLISECONDS);
        timer.record(61, TimeUnit.SECONDS);
        DistributionSummary summary = registry.getOrCreate(
                factory.distributionSummaryBuilder("fallback.histogram.summary",
                                                   factory.distributionStatisticsConfigBuilder())
                        .publishPercentileHistogram(true));
        summary.record(2D);

        String output = format(metricsConfig, registry);

        assertThat(output,
                   allOf(containsString("# TYPE fallback_histogram_timer_seconds histogram\n"),
                         containsString("fallback_histogram_timer_seconds_bucket"
                                                + "{le=\"0.001\"} 0\n"),
                         containsString("fallback_histogram_timer_seconds_bucket"
                                                + "{le=\"30.0\"} 1\n"),
                         containsString("fallback_histogram_timer_seconds_bucket"
                                                + "{le=\"+Inf\"} 2\n"),
                         containsString("# TYPE fallback_histogram_summary histogram\n"),
                         containsString("fallback_histogram_summary_bucket"
                                                + "{le=\"1.0\"} 0\n"),
                         containsString("fallback_histogram_summary_bucket"
                                                + "{le=\"+Inf\"} 1\n")));
    }

    @Test
    void histogramQuantileHintAppliesOnlyToClassicPrometheus() {
        MetricsConfig metricsConfig = MetricsConfig.create();
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        try {
            MeterRegistry registry = factory.createMeterRegistry(metricsConfig);
            registry.getOrCreate(factory.timerBuilder("combined.timer")
                                         .percentiles(0.95, 0.5)
                                         .buckets(Duration.ofSeconds(1)))
                    .record(250, TimeUnit.MILLISECONDS);
            registry.getOrCreate(factory.distributionSummaryBuilder("combined.summary",
                                                                   factory.distributionStatisticsConfigBuilder()
                                                                           .percentiles(0.95, 0.5)
                                                                           .buckets(2D))
                                         .baseUnit(Meter.BaseUnits.BYTES))
                    .record(1.25);
            var provider = new HelidonPrometheusFormatterProvider();
            FormatterContext defaultContext = FormatterContext.builder()
                    .mediaType(MediaTypes.TEXT_PLAIN)
                    .metricsConfig(metricsConfig)
                    .build();
            FormatterContext disabledHintContext = FormatterContext.builder()
                    .mediaType(MediaTypes.TEXT_PLAIN)
                    .metricsConfig(metricsConfig)
                    .includeHistogramQuantiles(false)
                    .build();
            FormatterContext hintedContext = FormatterContext.builder()
                    .mediaType(MediaTypes.TEXT_PLAIN)
                    .metricsConfig(metricsConfig)
                    .includeHistogramQuantiles(true)
                    .build();
            String defaultOutput = (String) provider.formatter(defaultContext, registry)
                    .orElseThrow().format().orElseThrow();
            String disabledHintOutput = (String) provider.formatter(disabledHintContext, registry)
                    .orElseThrow().format().orElseThrow();
            String hintedOutput = (String) provider.formatter(hintedContext, registry)
                    .orElseThrow().format().orElseThrow();

            assertThat(disabledHintOutput, is(defaultOutput));
            assertThat(defaultOutput, not(containsString("quantile=")));
            assertThat(hintedOutput,
                       allOf(containsString("# TYPE combined_summary_bytes histogram\n"),
                             containsString("# TYPE combined_timer_seconds histogram\n"),
                             not(containsString("# EOF"))));
            assertThat("Quantiles must precede buckets with each count and sum emitted once",
                       hintedOutput.lines().filter(line -> !line.startsWith("#") && !line.contains("_max")).toList(),
                       is(List.of("combined_summary_bytes{quantile=\"0.5\"} 1.25",
                                  "combined_summary_bytes{quantile=\"0.95\"} 1.25",
                                  "combined_summary_bytes_bucket{le=\"2.0\"} 1",
                                  "combined_summary_bytes_bucket{le=\"+Inf\"} 1",
                                  "combined_summary_bytes_sum 1.25",
                                  "combined_summary_bytes_count 1",
                                  "combined_timer_seconds{quantile=\"0.5\"} 0.25",
                                  "combined_timer_seconds{quantile=\"0.95\"} 0.25",
                                  "combined_timer_seconds_bucket{le=\"1.0\"} 1",
                                  "combined_timer_seconds_bucket{le=\"+Inf\"} 1",
                                  "combined_timer_seconds_sum 0.25",
                                  "combined_timer_seconds_count 1")));

            String openMetricsOutput = format(metricsConfig, registry);
            FormatterContext hintedOpenMetricsContext = FormatterContext.builder()
                    .mediaType(MediaTypes.APPLICATION_OPENMETRICS_TEXT)
                    .metricsConfig(metricsConfig)
                    .includeHistogramQuantiles(true)
                    .build();
            String hintedOpenMetricsOutput = (String) provider.formatter(hintedOpenMetricsContext, registry)
                    .orElseThrow().format().orElseThrow();
            assertThat(hintedOpenMetricsOutput, is(openMetricsOutput));
            assertThat(hintedOpenMetricsOutput, not(containsString("quantile=")));
        } finally {
            factory.close();
        }
    }

    @Test
    void histogramQuantileHintPreservesSelectionsAndEscapedLabels() {
        MetricsConfig metricsConfig = MetricsConfig.builder().tags(List.of(new HelidonTag("app", "test"))).build();
        String labelValue = "quote\"\\\n";
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        try {
            MeterRegistry registry = factory.createMeterRegistry(metricsConfig);
            registry.getOrCreate(factory.timerBuilder("selected.timer")
                                         .addTag(new HelidonTag("http.path", labelValue))
                                         .addTag(new HelidonTag("le", "user-bucket"))
                                         .addTag(new HelidonTag("quantile", "user-quantile"))
                                         .percentiles(0.5)
                                         .buckets(Duration.ofSeconds(1)))
                    .record(250, TimeUnit.MILLISECONDS);
            registry.getOrCreate(factory.distributionSummaryBuilder("selected.summary",
                                                                   factory.distributionStatisticsConfigBuilder()
                                                                           .percentiles(0.5)
                                                                           .buckets(2D))
                                         .addTag(new HelidonTag("http.path", labelValue))
                                         .addTag(new HelidonTag("le", "user-bucket"))
                                         .addTag(new HelidonTag("quantile", "user-quantile")))
                    .record(1.25);
            registry.getOrCreate(factory.timerBuilder("selected.timer")
                                         .addTag(new HelidonTag("http.path", "excluded"))
                                         .percentiles(0.5)
                                         .buckets(Duration.ofSeconds(1)));
            registry.getOrCreate(factory.counterBuilder("other")
                                         .addTag(new HelidonTag("http.path", labelValue)));

            FormatterContext context = FormatterContext.builder()
                    .mediaType(MediaTypes.TEXT_PLAIN)
                    .metricsConfig(metricsConfig)
                    .tagSelections(Map.of("http.path", List.of(labelValue), "app", List.of("test")))
                    .nameSelection(List.of("selected.timer", "selected.summary"))
                    .includeHistogramQuantiles(true)
                    .build();
            String output = (String) new HelidonPrometheusFormatterProvider()
                    .formatter(context, registry).orElseThrow().format().orElseThrow();

            String labels = "http_path=\"quote\\\"\\\\\\n\",app=\"test\"";
            assertThat(output, allOf(not(containsString("excluded")), not(containsString("other"))));
            assertThat("Combined families remove both reserved labels from every sample",
                       output.lines().filter(line -> !line.startsWith("#") && !line.contains("_max")).toList(),
                       is(List.of("selected_summary{" + labels + ",quantile=\"0.5\"} 1.25",
                                  "selected_summary_bucket{" + labels + ",le=\"2.0\"} 1",
                                  "selected_summary_bucket{" + labels + ",le=\"+Inf\"} 1",
                                  "selected_summary_sum{" + labels + "} 1.25",
                                  "selected_summary_count{" + labels + "} 1",
                                  "selected_timer_seconds{" + labels + ",quantile=\"0.5\"} 0.25",
                                  "selected_timer_seconds_bucket{" + labels + ",le=\"1.0\"} 1",
                                  "selected_timer_seconds_bucket{" + labels + ",le=\"+Inf\"} 1",
                                  "selected_timer_seconds_sum{" + labels + "} 0.25",
                                  "selected_timer_seconds_count{" + labels + "} 1")));
        } finally {
            factory.close();
        }
    }

    @Test
    @SuppressWarnings("removal")
    void formatterProviderRejectsNullContextAndDeprecatedApiValues() {
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        MetricsConfig metricsConfig = MetricsConfig.create();
        MeterRegistry registry = factory.createMeterRegistry(metricsConfig);
        var provider = new HelidonPrometheusFormatterProvider();
        FormatterContext context = FormatterContext.builder()
                .mediaType(MediaTypes.TEXT_PLAIN)
                .metricsConfig(metricsConfig)
                .build();

        assertThrows(NullPointerException.class, () -> provider.formatter(null, registry));
        assertThrows(NullPointerException.class, () -> provider.formatter(context, null));

        assertThrows(NullPointerException.class, () -> provider.formatter(null, metricsConfig, registry, Map.of(), List.of()));
        assertThrows(NullPointerException.class,
                     () -> provider.formatter(MediaTypes.TEXT_PLAIN, null, registry, Map.of(), List.of()));
        assertThrows(NullPointerException.class,
                     () -> provider.formatter(MediaTypes.TEXT_PLAIN, metricsConfig, null, Map.of(), List.of()));
        assertThrows(NullPointerException.class,
                     () -> provider.formatter(MediaTypes.TEXT_PLAIN, metricsConfig, registry, null, List.of()));
        assertThrows(NullPointerException.class,
                     () -> provider.formatter(MediaTypes.TEXT_PLAIN, metricsConfig, registry, Map.of(), null));
        assertThrows(NullPointerException.class,
                     () -> provider.formatter(MediaTypes.TEXT_PLAIN, metricsConfig, registry, Map.of(),
                                              Collections.singletonList(null)));
        Map<String, Collection<String>> nullValues = Collections.singletonMap("color", null);
        assertThrows(NullPointerException.class,
                     () -> provider.formatter(MediaTypes.TEXT_PLAIN, metricsConfig, registry, nullValues, List.of()));
        Map<String, Collection<String>> nullKey = Collections.singletonMap(null, List.of("blue"));
        assertThrows(NullPointerException.class,
                     () -> provider.formatter(MediaTypes.TEXT_PLAIN, metricsConfig, registry, nullKey, List.of()));
        Map<String, Collection<String>> nullValue = Map.of("color", Collections.singletonList(null));
        assertThrows(NullPointerException.class,
                     () -> provider.formatter(MediaTypes.TEXT_PLAIN, metricsConfig, registry, nullValue, List.of()));
    }

    @Test
    void formatsOpenMetricsLabelsAndEmptyQuantiles() {
        MetricsConfig metricsConfig = MetricsConfig.create();
        HelidonMetricsFactory factory = HelidonMetricsFactory.builder()
                .metricsConfig(metricsConfig)
                .build();
        MeterRegistry registry = factory.globalRegistry();

        Timer timer = registry.getOrCreate(factory.timerBuilder("reserved.labels")
                                                   .addTag(new HelidonTag("http:method", "GET"))
                                                   .addTag(new HelidonTag("le", "user"))
                                                   .buckets(Duration.ofMillis(10)));
        timer.record(1, TimeUnit.MILLISECONDS);
        DistributionSummary summary = registry.getOrCreate(
                factory.distributionSummaryBuilder("empty.summary",
                                                   factory.distributionStatisticsConfigBuilder()
                                                           .percentiles(0.5))
                        .addTag(new HelidonTag("quantile", "user")));

        String output = format(metricsConfig, registry);

        assertThat(output,
                   allOf(containsString("reserved_labels_seconds_bucket"
                                                + "{http_method=\"GET\",le=\"0.01\"} 1\n"),
                         containsString("reserved_labels_seconds_sum"
                                                + "{http_method=\"GET\"} 0.001\n"),
                         containsString("empty_summary{quantile=\"0.5\"} 0.0\n"),
                         not(containsString("http:method")),
                         not(containsString("reserved_labels_seconds_bucket{http_method=\"GET\",le=\"user\"")),
                         not(containsString("empty_summary{quantile=\"user\""))));
        assertThat("Max gauge family should be emitted after primary timer family",
                   output.indexOf("# HELP reserved_labels_seconds_max"),
                   greaterThan(output.indexOf("reserved_labels_seconds_count")));
        assertThat(summary.count(), is(0L));
        assertThat("An empty snapshot still represents an undefined percentile",
                   Double.isNaN(summary.snapshot().percentileValues().iterator().next().value()), is(true));
    }

    @Test
    @SuppressWarnings("removal")
    void filtersByTagsAndNamesIncludingWholeTimerFamilies() {
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        MetricsConfig metricsConfig = MetricsConfig.builder().tags(List.of(new HelidonTag("app", "test"))).build();
        MeterRegistry registry = factory.createMeterRegistry(metricsConfig);
        registry.getOrCreate(factory.timerBuilder("selected.time")
                                     .addTag(new HelidonTag("color", "blue"))
                                     .addTag(new HelidonTag("scope", "user"))
                                     .buckets(Duration.ofSeconds(1))).record(1, TimeUnit.MILLISECONDS);
        registry.getOrCreate(factory.timerBuilder("selected.time")
                                     .addTag(new HelidonTag("color", "red"))
                                     .addTag(new HelidonTag("scope", "user"))).record(2, TimeUnit.MILLISECONDS);
        registry.getOrCreate(factory.counterBuilder("other").addTag(new HelidonTag("color", "blue"))).increment();
        var provider = new HelidonPrometheusFormatterProvider();
        FormatterContext context = FormatterContext.builder()
                .mediaType(MediaTypes.APPLICATION_OPENMETRICS_TEXT)
                .metricsConfig(metricsConfig)
                .tagSelections(Map.of("color", List.of("blue", "green"),
                                      "scope", List.of("user"),
                                      "app", List.of("test")))
                .nameSelection(List.of("selected.time"))
                .build();
        var formatter = provider.formatter(context, registry).orElseThrow();

        assertThat((String) formatter.format().orElseThrow(),
                   allOf(containsString("selected_time_seconds_bucket{color=\"blue\",scope=\"user\",app=\"test\",le=\"1.0\"}"),
                         containsString("selected_time_seconds_count{color=\"blue\",scope=\"user\",app=\"test\"} 1\n"),
                         containsString("selected_time_seconds_max{color=\"blue\",scope=\"user\",app=\"test\"} 0.001"),
                         not(containsString("red")),
                         not(containsString("other"))));
        var deprecatedFormatter = provider.formatter(context.mediaType(),
                                                      metricsConfig,
                                                      registry,
                                                      context.tagSelections(),
                                                      context.nameSelection()).orElseThrow();
        assertThat("Deprecated formatter preserves tag and name selections",
                   deprecatedFormatter.format(), is(formatter.format()));
        FormatterContext generatedLabelContext = FormatterContext.builder()
                .mediaType(MediaTypes.TEXT_PLAIN)
                .metricsConfig(metricsConfig)
                .tagSelections(Map.of("le", List.of("1.0")))
                .build();
        var generatedLabelSelection = provider.formatter(generatedLabelContext, registry).orElseThrow();
        assertThat("Generated histogram labels must not select meters",
                   generatedLabelSelection.format(), is(Optional.empty()));
    }

    @Test
    void formatsCurrentPrometheusNamesAndSpecialValues() {
        MetricsConfig metricsConfig = MetricsConfig.create();
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        MeterRegistry registry = factory.createMeterRegistry(metricsConfig);
        registry.getOrCreate(factory.counterBuilder("1requests.total.created")).increment();
        registry.getOrCreate(factory.timerBuilder("request.seconds")).record(1, TimeUnit.SECONDS);
        registry.getOrCreate(factory.gaugeBuilder("positive.infinity", () -> Double.POSITIVE_INFINITY)
                                     .addTag(new HelidonTag("__label", "quote\"\\\n")));
        registry.getOrCreate(factory.gaugeBuilder("negative.infinity", () -> Double.NEGATIVE_INFINITY));
        FormatterContext context = FormatterContext.builder()
                .mediaType(MediaTypes.TEXT_PLAIN)
                .metricsConfig(metricsConfig)
                .build();
        var formatter = new HelidonPrometheusFormatterProvider().formatter(context, registry).orElseThrow();

        assertThat((String) formatter.format().orElseThrow(),
                   allOf(containsString("# TYPE _requests_total counter\n"),
                         containsString("_requests_total 1.0\n"),
                         containsString("request_seconds_count 1\n"),
                         containsString("request_seconds_max 1.0\n"),
                         containsString("positive_infinity{_label=\"quote\\\"\\\\\\n\"} +Inf\n"),
                         containsString("negative_infinity -Inf\n"),
                         not(containsString("seconds_seconds")),
                         not(containsString("# EOF"))));
    }

    @Test
    void providerDeclinesNonHelidonRegistry() {
        FormatterContext context = FormatterContext.builder()
                .mediaType(MediaTypes.APPLICATION_OPENMETRICS_TEXT)
                .metricsConfig(MetricsConfig.create())
                .build();
        Optional<MeterRegistryFormatter> formatter = new HelidonPrometheusFormatterProvider()
                .formatter(context, new TestRegistry());

        assertThat("Provider declines a registry from another provider", formatter, is(Optional.empty()));
    }

    private static String format(MetricsConfig metricsConfig, MeterRegistry registry) {
        FormatterContext context = FormatterContext.builder()
                .mediaType(MediaTypes.APPLICATION_OPENMETRICS_TEXT)
                .metricsConfig(metricsConfig)
                .build();
        Optional<MeterRegistryFormatter> formatter = new HelidonPrometheusFormatterProvider()
                .formatter(context, registry);
        assertThat("Provider supplies a formatter for its registry", formatter, not(Optional.empty()));
        Optional<Object> output = formatter.get().format();
        assertThat("Formatter supplies output for the registered meters", output, not(Optional.empty()));
        return (String) output.get();
    }

    private static final class TestRegistry extends NoOpTestRegistry {
    }
}
