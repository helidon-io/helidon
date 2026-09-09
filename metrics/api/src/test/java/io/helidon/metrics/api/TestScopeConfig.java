/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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
import java.util.regex.Pattern;

import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.config.Config;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("removal")
class TestScopeConfig {

    @Test
    void retainsProgrammaticSettingsButIgnoresEnablement() {
        Pattern include = Pattern.compile("mine\\..*");
        Pattern exclude = Pattern.compile("mine\\.excluded\\..*");
        ScopeConfig scopeConfig = ScopeConfig.builder()
                .name("test")
                .enabled(false)
                .include(include)
                .exclude(exclude)
                .build();

        assertThat(scopeConfig.name(), is("test"));
        assertThat(scopeConfig.enabled(), is(false));
        assertThat(scopeConfig.include(), OptionalMatcher.optionalValue(is(include)));
        assertThat(scopeConfig.exclude(), OptionalMatcher.optionalValue(is(exclude)));
        assertThat("Deprecated scope settings do not disable a meter",
                   scopeConfig.isMeterEnabled("mine.excluded.meter"),
                   is(true));
    }

    @Test
    void retainsConfiguredSettingsButIgnoresEnablement() {
        Config config = Config.just(ConfigSources.create(Map.of("filter.include", "mine\\..*",
                                                                "filter.exclude", "mine\\.excluded\\..*")));
        ScopeConfig scopeConfig = ScopeConfig.builder()
                .config(config)
                .name("test")
                .build();

        assertThat(scopeConfig.include().orElseThrow().pattern(), is("mine\\..*"));
        assertThat(scopeConfig.exclude().orElseThrow().pattern(), is("mine\\.excluded\\..*"));
        assertThat("Deprecated scope filters do not disable a meter",
                   scopeConfig.isMeterEnabled("outside.include"),
                   is(true));
    }

    @Test
    void rejectsInvalidPatternWhileParsingRetainedConfig() {
        Config config = Config.just(ConfigSources.create(Map.of("filter.include", "mine\\..*|bad(one")));

        assertThrows(ConfigMappingException.class,
                     () -> ScopeConfig.builder()
                             .config(config)
                             .name("test")
                             .build());
    }
}
