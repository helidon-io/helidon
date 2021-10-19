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
package io.helidon.metrics;

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.metrics.api.MetricsSettings;
import io.helidon.metrics.api.RegistryFactory;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TestSettingsAndConfig {

    private static final Map<String, String> METRICS_DISABLED_SETTINGS = Map.of(
            MetricsSettings.Builder.METRICS_CONFIG_KEY
                    + "." + MetricsSettings.Builder.ENABLED_CONFIG_KEY,
            "false");

    private static final Map<String, String> METRICS_ENABLED_SETTINGS = Map.of(
            MetricsSettings.Builder.METRICS_CONFIG_KEY
                    + "." + MetricsSettings.Builder.ENABLED_CONFIG_KEY,
            "true");

    @Test
    void checkRegistryWithDisabledSettings() {
        MetricsSettings metricsSettings = MetricsSettings.builder()
                .enabled(false)
                .build();

        RegistryFactory registryFactory = RegistryFactory.create(metricsSettings);
        MetricRegistry metricRegistry = registryFactory.getRegistry(MetricRegistry.Type.APPLICATION);
        checkCounterValueAfterInc(metricRegistry, "counter-disabled-via-settings", 0L);
    }

    @Test
    void checkRegistryWithEnabledConfig() {
        Config metricsConfig = Config.just(ConfigSources.create(METRICS_ENABLED_SETTINGS)).get("metrics");
        RegistryFactory registryFactory = RegistryFactory.create(metricsConfig);
        MetricRegistry metricRegistry = registryFactory.getRegistry(MetricRegistry.Type.APPLICATION);
        checkCounterValueAfterInc(metricRegistry, "counter-enabled-via-config", 1L);
    }

    @Test
    void checkRegistryWithDisabledConfig() {
        Config metricsConfig = Config.just(ConfigSources.create(METRICS_DISABLED_SETTINGS)).get("metrics");
        MetricRegistry metricRegistry = RegistryFactory.create(metricsConfig).getRegistry(MetricRegistry.Type.APPLICATION);
        checkCounterValueAfterInc(metricRegistry, "counter-disabled-via-config", 0L);
    }

    private static void checkCounterValueAfterInc(MetricRegistry metricRegistry, String counterName, long expected) {
        Counter counter = metricRegistry.counter(counterName);
        counter.inc();
        assertThat("Counter value after one inc", counter.getCount(), is(expected));
    }
}
