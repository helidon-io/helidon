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

package io.helidon.metrics.api;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import io.helidon.common.Errors;
import io.helidon.config.Config;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestMeterConfig {

    @Test
    void preservesAbsentAndEmptyPercentilesFromYaml() {
        MetricsConfig config = MetricsConfig.create(Config.just(ConfigSources.classpath("meter-settings.yaml"))
                                                           .get("metrics"));

        assertThat(config.meters(), hasSize(4));
        MeterConfig defaults = config.meters().get(0);
        assertThat(defaults.namePattern().pattern(), is("default\\.timer"));
        assertThat(defaults.enabled(), is(true));
        assertThat(defaults.percentiles().isEmpty(), is(true));
        assertThat(config.meters().get(1).percentiles().orElseThrow(), empty());
        assertThat(config.meters().get(2).percentiles().orElseThrow(), contains(0.5, 0.99));
        assertThat(config.isMeterEnabled("disabled.timer"), is(false));
        assertThat(config.isMeterEnabled("unrelated.timer"), is(true));
    }

    @Test
    void programmaticSettingsAreImmutableAndPreserveBoundaryPercentiles() {
        var percentiles = new ArrayList<>(List.of(0.0, 0.5, 1.0));
        MeterConfig meter = MeterConfig.builder().namePattern(Pattern.compile("timer")).percentiles(percentiles).build();
        percentiles.clear();

        assertThat(meter.percentiles().orElseThrow(), contains(0.0, 0.5, 1.0));
        assertThrows(UnsupportedOperationException.class, () -> meter.percentiles().orElseThrow().clear());
        assertThat(MeterConfig.builder().namePattern(Pattern.compile("timer")).percentiles(List.of()).build()
                           .percentiles().orElseThrow(), empty());
        assertThat(MeterConfig.builder().namePattern(Pattern.compile("timer")).build().percentiles().isEmpty(), is(true));
    }

    @Test
    void preservesAbsentAndEmptyBucketsAndParsesExpectedDurationsFromYaml() {
        MetricsConfig config = MetricsConfig.create(Config.just(ConfigSources.classpath("meter-settings.yaml"))
                                                           .get("metrics"));
        MeterConfig defaults = config.meters().get(0);
        MeterConfig aggregate = config.meters().get(1);
        MeterConfig custom = config.meters().get(2);

        assertThat(defaults.buckets().isEmpty(), is(true));
        assertThat(defaults.minimumExpectedValue().isEmpty(), is(true));
        assertThat(defaults.maximumExpectedValue().isEmpty(), is(true));
        assertThat(aggregate.buckets().orElseThrow(), empty());
        assertThat(custom.buckets().orElseThrow(), contains(Duration.ofMillis(5), Duration.ofMillis(10)));
        assertThat(custom.minimumExpectedValue().orElseThrow(), is(Duration.ofMillis(2)));
        assertThat(custom.maximumExpectedValue().orElseThrow(), is(Duration.ofMillis(20)));
    }

    @Test
    void bucketSettingsAreImmutableAndAllowBoundariesOutsideExpectedRange() {
        var buckets = new ArrayList<>(List.of(Duration.ofNanos(2), Duration.ofNanos(1)));
        MeterConfig meter = MeterConfig.builder()
                .namePattern(Pattern.compile("timer"))
                .buckets(buckets)
                .minimumExpectedValue(Duration.ofNanos(1))
                .maximumExpectedValue(Duration.ofNanos(1))
                .build();
        buckets.clear();

        assertThat(meter.buckets().orElseThrow(), contains(Duration.ofNanos(2), Duration.ofNanos(1)));
        assertThrows(UnsupportedOperationException.class, () -> meter.buckets().orElseThrow().clear());
        assertThat(meter.minimumExpectedValue().orElseThrow(), is(Duration.ofNanos(1)));
        assertThat(meter.maximumExpectedValue().orElseThrow(), is(Duration.ofNanos(1)));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MAX_VALUE})
    void rejectsInvalidHistogramDurations(long seconds) {
        Duration duration = Duration.ofSeconds(seconds);
        var bucketFailure = assertThrows(IllegalArgumentException.class,
                                         () -> MeterConfig.builder()
                                                 .namePattern(Pattern.compile("timer"))
                                                 .buckets(List.of(duration))
                                                 .build());
        var minFailure = assertThrows(IllegalArgumentException.class,
                                      () -> MeterConfig.builder()
                                              .namePattern(Pattern.compile("timer"))
                                              .minimumExpectedValue(duration)
                                              .build());
        var maxFailure = assertThrows(IllegalArgumentException.class,
                                      () -> MeterConfig.builder()
                                              .namePattern(Pattern.compile("timer"))
                                              .maximumExpectedValue(duration)
                                              .build());

        assertThat(bucketFailure.getMessage(), containsString("bucket"));
        assertThat(minFailure.getMessage(), containsString("minimum-expected-value"));
        assertThat(maxFailure.getMessage(), containsString("maximum-expected-value"));
        for (var failure : List.of(bucketFailure, minFailure, maxFailure)) {
            assertThat(failure.getMessage(), containsString(duration.toString()));
        }
    }

    @Test
    void rejectsInvertedExpectedRange() {
        var failure = assertThrows(IllegalArgumentException.class,
                                   () -> MeterConfig.builder()
                                           .namePattern(Pattern.compile("timer"))
                                           .minimumExpectedValue(Duration.ofMillis(20))
                                           .maximumExpectedValue(Duration.ofMillis(2))
                                           .build());
        assertThat(failure.getMessage(), containsString("minimum-expected-value"));
        assertThat(failure.getMessage(), containsString("maximum-expected-value"));
    }

    @Test
    void rejectsInvalidHistogramDurationsFromConfig() {
        for (String property : List.of("buckets.0", "minimum-expected-value", "maximum-expected-value")) {
            Config config = Config.just(ConfigSources.create(Map.of("name-pattern", "timer", property, "PT0S")));
            var failure = assertThrows(IllegalArgumentException.class, () -> MeterConfig.create(config));
            assertThat(failure.getMessage(), containsString(property.equals("buckets.0") ? "bucket" : property));
        }
    }

    @Test
    void matchesExactNamesAndGlobalDisableWins() {
        MetricsConfig config = MetricsConfig.builder()
                .config(Config.empty())
                .addMeter(meter -> meter.namePattern(Pattern.compile(Pattern.quote("literal.*"))).enabled(false))
                .addMeter(meter -> meter.namePattern(Pattern.compile("enabled")).enabled(true))
                .build();

        assertThat(config.isMeterEnabled("literal.*"), is(false));
        assertThat(config.isMeterEnabled("literal.timer"), is(true));
        assertThat(config.isMeterEnabled("enabled"), is(true));
        MetricsConfig disabled = MetricsConfig.builder().from(config).enabled(false).build();
        assertThat(disabled.isMeterEnabled("enabled"), is(false));
        assertThat(disabled.isMeterEnabled("unconfigured"), is(false));
    }

    @Test
    void requiresNamePattern() {
        var failure = assertThrows(Errors.ErrorMessagesException.class, () -> MeterConfig.builder().build());
        assertThat(failure.getMessage(), containsString("name"));
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void rejectsBlankNamePattern(String name) {
        var failure = assertThrows(IllegalArgumentException.class, () -> MeterConfig.builder().namePattern(Pattern.compile(name)).build());
        assertThat(failure.getMessage(), containsString("name"));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
    void rejectsInvalidPercentile(double percentile) {
        var failure = assertThrows(IllegalArgumentException.class,
                                   () -> MeterConfig.builder()
                                           .namePattern(Pattern.compile("timer"))
                                           .percentiles(List.of(percentile))
                                           .build());
        assertThat(failure.getMessage(), containsString("percentile"));
        assertThat(failure.getMessage(), containsString(Double.toString(percentile)));
    }

    @Test
    void firstMatchingPatternSelectsCompleteSettingsAcrossOverlappingRules() {
        MetricsConfig config = MetricsConfig.builder()
                .config(Config.empty())
                .addMeter(meter -> meter.namePattern(Pattern.compile("http[.]special")).enabled(true))
                .addMeter(meter -> meter.namePattern(Pattern.compile("http[.].*")).enabled(false))
                .addMeter(meter -> meter.namePattern(Pattern.compile("http[.]special")).percentiles(List.of(0.99)))
                .build();

        assertThat(config.isMeterEnabled("http.special"), is(true));
        assertThat(config.isMeterEnabled("http.other"), is(false));
        assertThat(config.isMeterEnabled("prefix.http.other"), is(true));
        assertThat(config.isMeterEnabled("unrelated"), is(true));
        assertThat(config.meterConfig("http.special").orElseThrow().percentiles().isEmpty(), is(true));
        assertThat(config.meterConfig("unrelated").isEmpty(), is(true));
        assertThrows(NullPointerException.class, () -> config.meterConfig(null));
        MetricsConfig disabled = MetricsConfig.builder().from(config).enabled(false).build();
        assertThat(disabled.isMeterEnabled("http.special"), is(false));
        assertThat(disabled.isMeterEnabled("unrelated"), is(false));
        assertThat(disabled.meterConfig("http.special").orElseThrow().enabled(), is(true));
    }

    @Test
    void acceptsPatternFlagsFromBuilderAndConfig() {
        MetricsConfig programmatic = MetricsConfig.builder()
                .config(Config.empty())
                .addMeter(meter -> meter.namePattern(Pattern.compile("http[.].*", Pattern.CASE_INSENSITIVE)).enabled(false))
                .build();
        Config source = Config.just(ConfigSources.create(Map.of("meters.0.name-pattern", "(?i)http[.].*",
                                                                "meters.0.enabled", "false")));
        MetricsConfig configured = MetricsConfig.create(source);

        assertThat(programmatic.meters().getFirst().namePattern().flags(), is(Pattern.CASE_INSENSITIVE));
        for (MetricsConfig config : List.of(programmatic, configured)) {
            assertThat(config.isMeterEnabled("HTTP.requests"), is(false));
            assertThat(config.isMeterEnabled("prefix.HTTP.requests"), is(true));
        }
    }

    @Test
    void rejectsMalformedPatternFromConfig() {
        Config source = Config.just(ConfigSources.create(Map.of("name-pattern", "[")));
        var failure = assertThrows(ConfigMappingException.class, () -> MeterConfig.create(source));
        assertThat(failure.getMessage(), containsString("name-pattern"));
    }

    @Test
    void rejectsInvalidPercentileFromConfig() {
        Config config = Config.just(ConfigSources.create(Map.of("name-pattern", "timer", "percentiles.0", "1.1")));
        var failure = assertThrows(IllegalArgumentException.class, () -> MeterConfig.create(config));
        assertThat(failure.getMessage(), containsString("percentile"));
    }
}
