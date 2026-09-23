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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.helidon.common.Errors;
import io.helidon.config.Config;
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
        assertThat(defaults.name(), is("default.timer"));
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
        MeterConfig meter = MeterConfig.builder().name("timer").percentiles(percentiles).build();
        percentiles.clear();

        assertThat(meter.percentiles().orElseThrow(), contains(0.0, 0.5, 1.0));
        assertThrows(UnsupportedOperationException.class, () -> meter.percentiles().orElseThrow().clear());
        assertThat(MeterConfig.builder().name("timer").percentiles(List.of()).build().percentiles().orElseThrow(), empty());
        assertThat(MeterConfig.builder().name("timer").build().percentiles().isEmpty(), is(true));
    }

    @Test
    void matchesExactNamesAndGlobalDisableWins() {
        MetricsConfig config = MetricsConfig.builder()
                .config(Config.empty())
                .addMeter(meter -> meter.name("literal.*").enabled(false))
                .addMeter(meter -> meter.name("enabled").enabled(true))
                .build();

        assertThat(config.isMeterEnabled("literal.*"), is(false));
        assertThat(config.isMeterEnabled("literal.timer"), is(true));
        assertThat(config.isMeterEnabled("enabled"), is(true));
        MetricsConfig disabled = MetricsConfig.builder().from(config).enabled(false).build();
        assertThat(disabled.isMeterEnabled("enabled"), is(false));
        assertThat(disabled.isMeterEnabled("unconfigured"), is(false));
    }

    @Test
    void requiresName() {
        var failure = assertThrows(Errors.ErrorMessagesException.class, () -> MeterConfig.builder().build());
        assertThat(failure.getMessage(), containsString("name"));
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void rejectsBlankName(String name) {
        var failure = assertThrows(IllegalArgumentException.class, () -> MeterConfig.builder().name(name).build());
        assertThat(failure.getMessage(), containsString("name"));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
    void rejectsInvalidPercentile(double percentile) {
        var failure = assertThrows(IllegalArgumentException.class,
                                   () -> MeterConfig.builder().name("timer").percentiles(List.of(percentile)).build());
        assertThat(failure.getMessage(), containsString("percentile"));
        assertThat(failure.getMessage(), containsString(Double.toString(percentile)));
    }

    @Test
    void rejectsDuplicateNamesProgrammatically() {
        var failure = assertThrows(IllegalArgumentException.class,
                                   () -> MetricsConfig.builder()
                                           .config(Config.empty())
                                           .addMeter(meter -> meter.name("same"))
                                           .addMeter(meter -> meter.name("same").enabled(false))
                                           .build());
        assertThat(failure.getMessage(), containsString("Duplicate meter configuration name: same"));
    }

    @Test
    void rejectsDuplicateNamesFromConfig() {
        Config config = Config.just(ConfigSources.create(Map.of("meters.0.name", "same", "meters.1.name", "same")));
        var failure = assertThrows(IllegalArgumentException.class, () -> MetricsConfig.create(config));
        assertThat(failure.getMessage(), containsString("Duplicate meter configuration name: same"));
    }

    @Test
    void rejectsInvalidPercentileFromConfig() {
        Config config = Config.just(ConfigSources.create(Map.of("name", "timer", "percentiles.0", "1.1")));
        var failure = assertThrows(IllegalArgumentException.class, () -> MeterConfig.create(config));
        assertThat(failure.getMessage(), containsString("percentile"));
    }
}
