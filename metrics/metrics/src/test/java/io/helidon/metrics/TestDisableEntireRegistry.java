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
package io.helidon.metrics;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.metrics.api.MetricsSettings;
import io.helidon.metrics.api.RegistryFactory;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TestDisableEntireRegistry {
    private static Config metricsConfig;

    @BeforeAll
    static void prep() {
        metricsConfig = Config.create(ConfigSources.classpath("registrySettingsDisable.properties")).get("metrics");
    }

    @Test
    void testApplicationRegistryDisabled() {
        MetricsSettings metricsSettings = MetricsSettings.create(metricsConfig);
        assertThat("Application registry is enabled",
                   metricsSettings.registrySettings(MetricRegistry.APPLICATION_SCOPE).isEnabled(),
                   is(false));

        io.helidon.metrics.api.RegistryFactory registryFactory = io.helidon.metrics.api.RegistryFactory.create(metricsSettings);
        MetricRegistry appRegistry = registryFactory.getRegistry(MetricRegistry.APPLICATION_SCOPE);
        Counter appCounter = appRegistry.counter("shouldNotUpdate");
        appCounter.inc();
        assertThat("Counter in disabled app registry", appCounter.getCount(), is(0L));
    }

    @Test
    void testVendorRegistryEnabled() {
        MetricsSettings metricsSettings = MetricsSettings.create(metricsConfig);
        assertThat("Vendor registry is enabled",
                   metricsSettings.registrySettings(MetricRegistry.VENDOR_SCOPE).isEnabled(),
                   is(true));

        io.helidon.metrics.api.RegistryFactory registryFactory = RegistryFactory.create(metricsSettings);
        MetricRegistry vendorRegistry = registryFactory.getRegistry(MetricRegistry.VENDOR_SCOPE);
        Counter vendorCounter = vendorRegistry.counter("shouldUpdate");
        vendorCounter.inc();
        assertThat("Counter in enabled vendor registry", vendorCounter.getCount(), is(1L));
    }
}
