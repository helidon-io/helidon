/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import java.util.Map;
import java.util.regex.PatternSyntaxException;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class TestRegistrySettings {

    @Test
    void checkEnableAllPattern() {
        RegistrySettings mts = RegistrySettings.builder()
                .filterSettings(RegistryFilterSettings.builder()
                                        .include(".*"))
                .build();

        assertThat("Specific metric enabled with global pattern",
                   mts.isMetricEnabled("any.thing.should.work"),
                   is(true));

        assertThat("Metric type overall is enabled", mts.isEnabled(), is(true));
    }

    @Test
    void checkPrefixPattern() {
        RegistrySettings mts = RegistrySettings.builder()
                .filterSettings(RegistryFilterSettings.builder()
                                        .include("mine\\..*"))
                .build();

        assertThat("Specific metric enabled with prefix 'mine.'",
                   mts.isMetricEnabled("should.fail"),
                   is(false));

        assertThat("Specific metric enabled with prefix 'mine.'",
                   mts.isMetricEnabled("mine.should.work"),
                   is(true));

        assertThat("Metric type overall is enabled", mts.isEnabled(), is(true));
    }

    @Test
    void testSingleValidConfig() {
        Map<String, String> configMap = Map.of("filter.include", "mine\\..*");
        Config config = Config.just(ConfigSources.create(configMap));
        RegistrySettings mts = RegistrySettings.builder()
                .config(config)
                .build();

        assertThat("Specific metric enabled with prefix 'mine.'",
                   mts.isMetricEnabled("should.fail"),
                   is(false));

        assertThat("Specific metric enabled with prefix 'mine.'",
                   mts.isMetricEnabled("mine.should.work"),
                   is(true));
    }

    @Test
    void testMultipleValidConfig() {
        Map<String, String> configMap = Map.of("filter.include", "mine\\..*|yours\\..*");
        Config config = Config.just(ConfigSources.create(configMap));
        RegistrySettings mts = RegistrySettings.builder()
                .config(config)
                .build();

        assertThat("Overall metrics for application enabled", mts.isEnabled(), is(true));

        assertThat("Specific metric 'should.fail' enabled with prefix 'mine.'",
                   mts.isMetricEnabled("should.fail"),
                   is(false));

        assertThat("Specific metric 'mine.should.work' enabled with prefix 'mine.'",
                   mts.isMetricEnabled("mine.should.work"),
                   is(true));

        assertThat("Specific metric 'yours.should.work' enabled with prefix 'yours.'",
                   mts.isMetricEnabled("yours.should.work"),
                   is(true));
    }

    @Test
    void testInvalidConfig() {
        Map<String, String> configMap = Map.of("filter.include", "mine\\..*|bad(one");
        Config config = Config.just(ConfigSources.create(configMap));

        Assertions.assertThrows(PatternSyntaxException.class, () -> {
            RegistrySettings.builder()
                    .config(config)
                    .build();
        });
    }

    @Test
    void testPassingEmptyListForPatterns() {
        RegistrySettings mts = RegistrySettings.builder()
                .filterSettings(RegistryFilterSettings.builder()
                                        .include(""))
                .build();

        assertThat("Specific metric 'should.work'",
                   mts.isMetricEnabled("should.work"),
                   is(true));
    }

    @Test
    void testNullListForPatterns() {
        RegistrySettings mts = RegistrySettings.builder()
                .build();

        assertThat("Specific metric 'should.work'",
                   mts.isMetricEnabled("should.work"),
                   is(true));
    }

    @Test
    void testSingleNegative() {
        RegistrySettings mts = RegistrySettings.builder()
                .filterSettings(RegistryFilterSettings.builder()
                                        .exclude("mine\\..*"))
                .build();

        assertThat("Specific metric 'should.work' with unmatched negative filter",
                   mts.isMetricEnabled("should.work"),
                   is(true));

        assertThat("Specific metric 'mine.should.not.work' with matched negative filter",
                   mts.isMetricEnabled("mine.should.not.work"),
                   is(false));
    }

    @Test
    void testMultipleMixedPatterns() {
        RegistrySettings mts = RegistrySettings.builder()
                .filterSettings(RegistryFilterSettings.builder()
                                .include("mine\\..*")
                                .exclude("mine\\.nogood\\..*|yours\\.nogood\\.*"))
                .build();

        assertThat("Metric 'mine.should.fail' with matched negative pattern",
                   mts.isMetricEnabled("mine.nogood.should.fail"),
                   is(false));

        assertThat("Metric 'yours.should.fail' with matched negative pattern",
                   mts.isMetricEnabled("yours.nogood.should.fail"),
                   is(false));

        assertThat("Metrics 'theirs.should.fail' with no matched pattern, positive or negative",
                   mts.isMetricEnabled("theirs.should.fail"),
                   is(false));

        assertThat("Metrics 'mine.should.work' with no matched pattern, positive or negative",
                   mts.isMetricEnabled("mine.should.work"),
                   is(true));
    }

    @Test
    void testWithUnescapedDots() {
        // Users are likely to use dots as literals rather than the regex wildcard; that usage should work so it kind-of does
        // what the user intended.
        RegistrySettings mts = RegistrySettings.builder()
                .filterSettings(RegistryFilterSettings.builder()
                                        .include("mine.*"))
                .build();

        assertThat("Metric 'mine.should.work' with matched negative pattern",
                   mts.isMetricEnabled("mine.should.work"),
                   is(true));

        assertThat("Metric 'yours.should.fail' with matched negative pattern",
                   mts.isMetricEnabled("yours.should.fail"),
                   is(false));

    }
}
