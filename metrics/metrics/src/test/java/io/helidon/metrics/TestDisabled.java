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

import io.helidon.metrics.api.MetricsSettings;
import io.helidon.metrics.api.RegistryFilterSettings;
import io.helidon.metrics.api.RegistrySettings;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TestDisabled {

    @Test
    void testDisabledMetric() {
        RegistryFilterSettings.Builder filterBuilder = RegistryFilterSettings.builder()
                .exclude("dormant.*");

        RegistrySettings settings = RegistrySettings.builder()
                .filterSettings(filterBuilder)
                .build();

        MetricsSettings metricsSettings = MetricsSettings.builder()
                .registrySettings(MetricRegistry.APPLICATION_SCOPE, settings)
                .build();

        io.helidon.metrics.api.RegistryFactory registryFactory = io.helidon.metrics.api.RegistryFactory
                .create(metricsSettings);

        MetricRegistry registry = registryFactory.getRegistry(MetricRegistry.APPLICATION_SCOPE);

        Counter activeCounter = registry.counter("activeCounter");
        activeCounter.inc();
        assertThat("Active counter after inc", activeCounter.getCount(), is(1L));

        Counter dormantCounter = registry.counter("dormantCounter");
        dormantCounter.inc();
        assertThat("Dormant counter after inc", dormantCounter.getCount(), is(0L));
    }

    @Test
    void testDisabledRegistry() {

        RegistrySettings settings = RegistrySettings.builder()
                .enabled(false)
                .build();

        MetricsSettings metricsSettings = MetricsSettings.builder()
                .registrySettings(MetricRegistry.APPLICATION_SCOPE, settings)
                .build();

        io.helidon.metrics.api.RegistryFactory registryFactory = io.helidon.metrics.api.RegistryFactory
                .create(metricsSettings);

        MetricRegistry registry = registryFactory.getRegistry(MetricRegistry.APPLICATION_SCOPE);

        Counter activeCounter = registry.counter("activeCounter");
        long original = activeCounter.getCount();
        activeCounter.inc();
        assertThat("Difference in active counter in disabled registry after inc",
                   activeCounter.getCount() - original,
                   is(0L));
    }
}
