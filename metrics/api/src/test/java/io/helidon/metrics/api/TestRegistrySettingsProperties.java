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

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TestRegistrySettingsProperties {

    private static Config metricsConfigNode;
    private static Config fromYaml;

    @BeforeAll
    static void prep() {
        metricsConfigNode = Config.just(ConfigSources.classpath("registrySettings.properties")).get("metrics");
        fromYaml = Config.create(ConfigSources.classpath("scopeSettings.yaml")).get("metrics");
    }

    @Test
    void testInclude() {
        MetricsConfig metricsConfig = MetricsConfig.create(metricsConfigNode);
        assertThat("'pass.me' metric is enabled",
                   metricsConfig.scoping().scopes().get("vendor").isMeterEnabled("pass.me"),
                   is(true));
    }

    @Test
    // TODO enable once patterns are back
    @Disabled
    void testExclude() {
        MetricsConfig metricsConfig = MetricsConfig.create(metricsConfigNode);
        assertThat("'ignore.me' metric is enabled",
                   metricsConfig.scoping().scopes().get("vendor").isMeterEnabled("ignore.me"),
                   is(false));
    }

    @Test
    void testIncludeYaml() {
        MetricsConfig metricsConfig = MetricsConfig.create(fromYaml);
        assertThat("'pass.me' metric is enabled",
                   metricsConfig.scoping().scopes().get("vendor").isMeterEnabled("pass.me"),
                   is(true));
    }

    // TODO enable once patterns are back
    @Disabled
    @Test
    void testExcludeYaml() {
        MetricsConfig metricsConfig = MetricsConfig.create(fromYaml);
        assertThat("'ignore.me' metric is enabled",
                   metricsConfig.scoping().scopes().get("vendor").isMeterEnabled("ignore.me"),
                   is(false));
    }
}
