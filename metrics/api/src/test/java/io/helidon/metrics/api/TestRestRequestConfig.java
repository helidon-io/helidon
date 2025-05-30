/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class TestRestRequestConfig {

    @Test
    void testWithHyphenConfigKey() {

        MetricsConfig metricsConfig = MetricsConfig.create(Config.just(
                ConfigSources.create(Map.of("rest-request-enabled", "true")).build()));

        assertThat("REST request enabled using hyphen", metricsConfig.restRequestEnabled(), is(true));
    }

    @Test
    void testWithDotConfigKey() {

        MetricsConfig metricsConfig = MetricsConfig.create(Config.just(
                ConfigSources.create(Map.of("rest-request.enabled", "true")).build()));

        assertThat("REST request enabled using dot", metricsConfig.restRequestEnabled(), is(true));
    }

    @Test
    void testWithExplicitSetting() {
        MetricsConfig.Builder builder = MetricsConfig.builder();
        builder.restRequestEnabled(true);
        MetricsConfig metricsConfig = builder.build();

        assertThat("REST request enabled set explicitly", metricsConfig.restRequestEnabled(), is(true));
    }

    @Test
    void testWithNoSetting() {
        MetricsConfig.Builder builder = MetricsConfig.builder();
        MetricsConfig metricsConfig = builder.build();

        assertThat("REST request enabled unset", metricsConfig.restRequestEnabled(), is(false));
    }
}
