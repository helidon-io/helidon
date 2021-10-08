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

import io.helidon.common.http.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import io.helidon.config.yaml.YamlConfigParser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TestMetricsSettings {

    private static final String CONFIG_FILE = "testconfig.yaml";

    private static Config topLevelDisabled;
    private static Config baseDisabled;
    private static Config baseSelectiveDisabled;
    private static Config withKpi;
    private static Config withRESTSettings;

    @BeforeAll
    static void loadConfig() throws IOException {
        Config all = Config.just(ConfigSources.classpath(CONFIG_FILE));
        topLevelDisabled = all.get("topLevelDisabled").get("metrics");
        baseDisabled = all.get("baseDisabled").get("metrics");
        baseSelectiveDisabled = all.get("baseSelectiveDisabled").get("metrics");
        withKpi = all.get("withKpi").get("metrics");
        withRESTSettings = all.get("withRESTSettings").get("metrics");
    }

    @Test
    void testConfigDisabled() {
        MetricsSettings metricsSettings = MetricsSettings.builder().config(topLevelDisabled).build();
        assertThat("Top-level metrics enabled", metricsSettings.isEnabled(), is(false));
    }

    @Test
    void testConfigBaseDisabled() {
        MetricsSettings metricsSettings = MetricsSettings.builder().config(baseDisabled).build();
        assertThat("Top-level metrics enabled", metricsSettings.isEnabled(), is(true));
        assertThat("Base metrics enabled", metricsSettings.baseMetricsSettings().isEnabled(), is(false));
    }

    @Test
    void testConfigSelectiveBaseMetricDisabled() {
        MetricsSettings metricsSettings = MetricsSettings.builder().config(baseSelectiveDisabled).build();
        assertThat("Top-level metrics enabled", metricsSettings.isEnabled(), is(true));
        assertThat("Base metrics enabled", metricsSettings.baseMetricsSettings().isEnabled(), is(true));
        assertThat("memory.usedHeap base metric enabled",
                   metricsSettings.baseMetricsSettings().isBaseMetricEnabled("memory.usedHeap"),
                   is(false));
        assertThat("memory.committedHelp base metric enabled",
                   metricsSettings.baseMetricsSettings().isBaseMetricEnabled("memory.committedHeap"),
                   is(true));
    }

    @Test
    void testKpi() {
        MetricsSettings metricsSettings = MetricsSettings.builder().config(withKpi).build();
        assertThat("top-level metrics enabled", metricsSettings.isEnabled(), is(true));
        assertThat("KPI settings extended", metricsSettings.keyPerformanceIndicatorSettings().isExtended(), is(true));
        assertThat("Long-running threshold",
                   metricsSettings.keyPerformanceIndicatorSettings().longRunningRequestThresholdMs(),
                   is(789L));
    }

    @Test
    void testRESTSettings() {
        MetricsSettings metricsSettings = MetricsSettings.builder().config(withRESTSettings).build();
        assertThat("top-level metrics enabled", metricsSettings.isEnabled(), is(true));
        assertThat("KPI settings extended", metricsSettings.keyPerformanceIndicatorSettings().isExtended(), is(false));
        assertThat("Configured routing", metricsSettings.routing(), is("my-routing"));
        assertThat("Configured web-context", metricsSettings.webContext(), is("/mycontext"));
    }
}
