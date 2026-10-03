/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.metrics.providers.micrometer;

import java.util.Map;
import java.util.ServiceLoader;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.spi.MetricsPublisherProvider;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;

@Testing.Test(perMethod = true)
class TestOtlpPublisherCoexistence {

    @Test
    void micrometerOwnsFactoryAndOtlpConfigurationWhenBothModulesArePresent() {
        var providerTypes = ServiceLoader.load(MetricsPublisherProvider.class).stream()
                .map(ServiceLoader.Provider::type)
                .map(Class::getName)
                .toList();
        assertThat("Both OTLP configuration providers are available", providerTypes,
                   hasItems(OtlpPublisherProvider.class.getName(),
                            io.helidon.metrics.publishers.otlp.OtlpPublisherProvider.class.getName()));
        assertThat("Explicit Micrometer dependency selects its metrics factory",
                   Services.get(MetricsFactory.class), instanceOf(MicrometerMetricsFactory.class));

        Config config = Config.just(ConfigSources.create(Map.of("metrics.enabled", "false",
                                                               "metrics.publishers.otlp.interval", "PT24H")));
        var publishers = MetricsConfig.create(config.get("metrics")).publishers();

        assertThat("OTLP configuration selects one publisher", publishers, hasSize(1));
        assertThat("Micrometer's OTLP provider takes precedence", publishers.getFirst(), instanceOf(OtlpPublisher.class));
    }
}
