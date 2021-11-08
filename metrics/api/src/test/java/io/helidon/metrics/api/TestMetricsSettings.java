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

import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class TestMetricsSettings {

    private static final String CONFIG_FILE = "testconfig.yaml";

    private static Config topLevelDisabled;
    private static Config baseDisabled;
    private static Config baseSelectiveDisabled;
    private static Config withKpi;
    private static Config withRESTSettings;
    private static Config withOneRegistrySettings;
    private static Config withTwoRegistrySettings;
    private static Config registrySettingsWithBadFilterSyntax;
    private static Config withSimpleFilter;

    @BeforeAll
    static void loadConfig() throws IOException {
        Config all = Config.just(ConfigSources.classpath(CONFIG_FILE));
        topLevelDisabled = all.get("topLevelDisabled").get("metrics");
        baseDisabled = all.get("baseDisabled").get("metrics");
        baseSelectiveDisabled = all.get("baseSelectiveDisabled").get("metrics");
        withKpi = all.get("withKpi").get("metrics");
        withRESTSettings = all.get("withRESTSettings").get("metrics");
        withOneRegistrySettings = all.get("withOneRegistrySettings").get("metrics");
        withTwoRegistrySettings = all.get("withTwoRegistrySettings").get("metrics");
        registrySettingsWithBadFilterSyntax = all.get("registrySettingsWithBadFilterSyntax").get("metrics");
        withSimpleFilter = all.get("withSimpleFilter").get("metrics");
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
    void testNoRegistrySettings() {
        MetricsSettingsImpl metricsSettings = (MetricsSettingsImpl) MetricsSettings.builder().config(withRESTSettings).build();
        for (MetricRegistry.Type registryType : MetricRegistry.Type.values()) {
            assertThat("Registry settings with no config for " + registryType,
                       metricsSettings.registrySettings().keySet(),
                       not(contains(registryType)));
        }
    }

    @Test
    void testOneRegistrySettings() {
        MetricsSettingsImpl metricsSettings = (MetricsSettingsImpl) MetricsSettings.builder().config(withOneRegistrySettings)
                .build();
        for (MetricRegistry.Type type : Set.of(MetricRegistry.Type.VENDOR, MetricRegistry.Type.BASE)) {
            assertThat("Registry settings lacking config for " + type,
                       metricsSettings.registrySettings().keySet(),
                       not(contains(type)));
        }
        RegistrySettings registrySettings = metricsSettings.registrySettings().get(MetricRegistry.Type.APPLICATION);

        assertThat("Registry settings for " + MetricRegistry.Type.APPLICATION,
                   registrySettings,
                   notNullValue());

        assertThat("Registry settings for application enabled",
                   registrySettings.isEnabled(),
                   is(false));
        assertThat("Metrics enabled for any name via registry settings",
                   registrySettings.isMetricEnabled("anything"),
                   is(false));
        assertThat("Metrics enabled for any name via metrics settings",
                   metricsSettings.isMetricEnabled(MetricRegistry.Type.APPLICATION, "anything"),
                   is(false));
    }

    @Test
    void testTwoRegistrySettings() {
        MetricsSettingsImpl metricsSettings = (MetricsSettingsImpl) MetricsSettings.builder().config(withTwoRegistrySettings)
                .build();
        assertThat("Registry settings lacking config for 'application'",
                   metricsSettings.registrySettings().keySet(),
                   not(contains(MetricRegistry.Type.APPLICATION)));

        RegistrySettings vendorSettings = metricsSettings.registrySettings().get(MetricRegistry.Type.VENDOR);
        assertThat("Vendor settings", vendorSettings, notNullValue());
        assertThat("Vendor enabled", vendorSettings.isEnabled(), is(true));
        assertThat("Rejectable name vendor.nogood.here accepted",
                   vendorSettings.isMetricEnabled("vendor.nogood.here"),
                   is(false));
        assertThat("Acceptable name vendor.ok via registry settings",
                   vendorSettings.isMetricEnabled("vendor.ok"),
                   is(true));
        assertThat("Acceptable name vendor.ok via metrics settings",
                   metricsSettings.isMetricEnabled(MetricRegistry.Type.VENDOR, "vendor.ok"),
                   is(false));

        RegistrySettings baseSettings = metricsSettings.registrySettings().get(MetricRegistry.Type.BASE);
        assertThat("Base settings", baseSettings, notNullValue());
        assertThat("Base enabled", baseSettings.isEnabled(), is(false));

        // Even acceptable names are rejected because the registry is disabled.
        assertThat("Acceptable name base.good.ok", baseSettings.isMetricEnabled("base.good.ok"), is(false));
    }

    @Test
    void testBadFilterSyntaxInConfig() {
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                                        MetricsSettings.builder()
                                                .config(registrySettingsWithBadFilterSyntax)
                                                .build(),
                                "Error parsing bad filter syntax");
    }

    @Test
    void testRejectOthersWithPositivePatterns() {
        MetricsSettingsImpl metricsSettings = (MetricsSettingsImpl) MetricsSettings.builder().config(withSimpleFilter).build();

        assertThat("Approvable name app.ok.go",
                   metricsSettings.isMetricEnabled(MetricRegistry.Type.APPLICATION, "app.ok.go"),
                   is(true));
        assertThat("Rejectable name app.no.please",
                   metricsSettings.isMetricEnabled(MetricRegistry.Type.APPLICATION, "app.no.please"),
                   is(false));
    }
}
