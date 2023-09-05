/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

// TODO enabled once patterns are back
@Disabled
class TestScopeConfig {

    @Test
    void checkEnableAllPattern() {
        ScopeConfig mts = ScopeConfig.builder()
                .name("test")
                .include(".*")
                .build();

        assertThat("Specific metric enabled with global pattern",
                   mts.isMeterEnabled("any.thing.should.work"),
                   is(true));

        assertThat("Metric type overall is enabled", mts.enabled(), is(true));
    }

    @Test
    void checkPrefixPattern() {
        ScopeConfig mts = ScopeConfig.builder()
                .name("test")
                .include("mine\\..*")
                .build();

        assertThat("Specific metric enabled with prefix 'mine.'",
                   mts.isMeterEnabled("should.fail"),
                   is(false));

        assertThat("Specific metric enabled with prefix 'mine.'",
                   mts.isMeterEnabled("mine.should.work"),
                   is(true));

        assertThat("Metric type overall is enabled", mts.enabled(), is(true));
    }

    @Test
    void testSingleValidConfig() {
        Map<String, String> configMap = Map.of("filter.include", "mine\\..*");
        Config config = Config.just(ConfigSources.create(configMap));
        ScopeConfig mts = ScopeConfig.builder()
                .config(config)
                .name("test")
                .build();

        assertThat("Specific metric enabled with prefix 'mine.'",
                   mts.isMeterEnabled("should.fail"),
                   is(false));

        assertThat("Specific metric enabled with prefix 'mine.'",
                   mts.isMeterEnabled("mine.should.work"),
                   is(true));
    }

    @Test
    void testMultipleValidConfig() {
        Map<String, String> configMap = Map.of("filter.include", "mine\\..*|yours\\..*");
        Config config = Config.just(ConfigSources.create(configMap));
        ScopeConfig mts = ScopeConfig.builder()
                .config(config)
                .name("test")
                .build();

        assertThat("Overall metrics for application enabled", mts.enabled(), is(true));

        assertThat("Specific metric 'should.fail' enabled with prefix 'mine.'",
                   mts.isMeterEnabled("should.fail"),
                   is(false));

        assertThat("Specific metric 'mine.should.work' enabled with prefix 'mine.'",
                   mts.isMeterEnabled("mine.should.work"),
                   is(true));

        assertThat("Specific metric 'yours.should.work' enabled with prefix 'yours.'",
                   mts.isMeterEnabled("yours.should.work"),
                   is(true));
    }

    @Test
    // TODO remove after patterns created
    @Disabled
    void testInvalidConfig() {
        Map<String, String> configMap = Map.of("filter.include", "mine\\..*|bad(one");
        Config config = Config.just(ConfigSources.create(configMap));

        Assertions.assertThrows(PatternSyntaxException.class, () -> {
            ScopeConfig.builder()
                    .config(config)
                    .name("test")
                    .build();
        });
    }

    @Test
    void testPassingEmptyListForPatterns() {
        ScopeConfig mts = ScopeConfig.builder()
                .include("")
                .name("test")
                .build();

        assertThat("Specific metric 'should.work'",
                   mts.isMeterEnabled("should.work"),
                   is(true));
    }

    @Test
    void testNullListForPatterns() {
        ScopeConfig mts = ScopeConfig.builder()
                .name("test")
                .build();

        assertThat("Specific metric 'should.work'",
                   mts.isMeterEnabled("should.work"),
                   is(true));
    }

    @Test
    void testSingleNegative() {
        ScopeConfig mts = ScopeConfig.builder()
                .exclude("mine\\..*")
                .name("test")
                .build();

        assertThat("Specific metric 'should.work' with unmatched negative filter",
                   mts.isMeterEnabled("should.work"),
                   is(true));

        assertThat("Specific metric 'mine.should.not.work' with matched negative filter",
                   mts.isMeterEnabled("mine.should.not.work"),
                   is(false));
    }

    @Test
    void testMultipleMixedPatterns() {
        ScopeConfig mts = ScopeConfig.builder()
                .include("mine\\..*")
                .exclude("mine\\.nogood\\..*|yours\\.nogood\\.*")
                .name("test")
                .build();

        assertThat("Metric 'mine.should.fail' with matched negative pattern",
                   mts.isMeterEnabled("mine.nogood.should.fail"),
                   is(false));

        assertThat("Metric 'yours.should.fail' with matched negative pattern",
                   mts.isMeterEnabled("yours.nogood.should.fail"),
                   is(false));

        assertThat("Metrics 'theirs.should.fail' with no matched pattern, positive or negative",
                   mts.isMeterEnabled("theirs.should.fail"),
                   is(false));

        assertThat("Metrics 'mine.should.work' with no matched pattern, positive or negative",
                   mts.isMeterEnabled("mine.should.work"),
                   is(true));
    }

    @Test
    void testWithUnescapedDots() {
        // Users are likely to use dots as literals rather than the regex wildcard; that usage should work so it kind-of does
        // what the user intended.
        ScopeConfig mts = ScopeConfig.builder()
                .include("mine.*")
                .name("test")
                .build();

        assertThat("Metric 'mine.should.work' with matched negative pattern",
                   mts.isMeterEnabled("mine.should.work"),
                   is(true));

        assertThat("Metric 'yours.should.fail' with matched negative pattern",
                   mts.isMeterEnabled("yours.should.fail"),
                   is(false));

    }
}
