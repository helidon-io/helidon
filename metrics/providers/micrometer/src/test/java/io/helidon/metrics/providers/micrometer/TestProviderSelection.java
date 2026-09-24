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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.metrics.api.FormatterContext;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MeterRegistryFormatter;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.providers.helidon.HelidonMetricsFactoryProvider;
import io.helidon.metrics.spi.MeterRegistryFormatterProvider;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test(perMethod = true)
class TestProviderSelection {

    @Test
    void serviceRegistryUsesMicrometerWhenBothProvidersArePresent() {
        assertThat(Services.get(MetricsFactory.class), instanceOf(MicrometerMetricsFactory.class));
    }

    @Test
    void formatterProviderDeclinesHelidonRegistry() {
        MetricsFactory helidonFactory = new HelidonMetricsFactoryProvider().create(Config.empty(),
                                                                                   MetricsConfig.create(),
                                                                                   List.of());
        MeterRegistryFormatterProvider provider = new MicrometerPrometheusFormatterProvider();
        FormatterContext context = FormatterContext.builder()
                .mediaType(MediaTypes.APPLICATION_OPENMETRICS_TEXT)
                .metricsConfig(MetricsConfig.create())
                .build();

        try {
            assertThat("Micrometer formatter declines Helidon registry",
                       provider.formatter(context, helidonFactory.globalRegistry()),
                       is(Optional.empty()));
        } finally {
            helidonFactory.close();
        }
    }

    @Test
    void formatterProviderAcceptsMicrometerRegistry() {
        MetricsFactory micrometerFactory = Services.get(MetricsFactory.class);
        MeterRegistryFormatterProvider provider = new MicrometerPrometheusFormatterProvider();
        FormatterContext context = FormatterContext.builder()
                .mediaType(MediaTypes.APPLICATION_OPENMETRICS_TEXT)
                .metricsConfig(MetricsConfig.create())
                .build();

        Optional<MeterRegistryFormatter> formatter =
                provider.formatter(context, micrometerFactory.globalRegistry());

        assertThat("Micrometer formatter accepts Micrometer registry", formatter.isPresent(), is(true));
    }

    @Test
    @SuppressWarnings("removal")
    void formatterProviderRejectsNullContextAndDeprecatedApiValues() {
        MetricsFactory micrometerFactory = Services.get(MetricsFactory.class);
        MeterRegistryFormatterProvider provider = new MicrometerPrometheusFormatterProvider();
        MetricsConfig metricsConfig = MetricsConfig.create();
        MeterRegistry meterRegistry = micrometerFactory.globalRegistry();
        FormatterContext context = FormatterContext.builder()
                .mediaType(MediaTypes.APPLICATION_OPENMETRICS_TEXT)
                .metricsConfig(metricsConfig)
                .build();

        assertThrows(NullPointerException.class, () -> provider.formatter(null, meterRegistry));
        assertThrows(NullPointerException.class, () -> provider.formatter(context, null));

        assertThrows(NullPointerException.class,
                     () -> provider.formatter(null,
                                              metricsConfig,
                                              meterRegistry,
                                              Map.of(),
                                              List.of()));
        assertThrows(NullPointerException.class,
                     () -> provider.formatter(MediaTypes.APPLICATION_OPENMETRICS_TEXT,
                                              null,
                                              meterRegistry,
                                              Map.of(),
                                              List.of()));
        assertThrows(NullPointerException.class,
                     () -> provider.formatter(MediaTypes.APPLICATION_OPENMETRICS_TEXT,
                                              metricsConfig,
                                              null,
                                              Map.of(),
                                              List.of()));
        assertThrows(NullPointerException.class,
                     () -> provider.formatter(MediaTypes.APPLICATION_OPENMETRICS_TEXT,
                                              metricsConfig,
                                              meterRegistry,
                                              null,
                                              List.of()));
        assertThrows(NullPointerException.class,
                     () -> provider.formatter(MediaTypes.APPLICATION_OPENMETRICS_TEXT,
                                              metricsConfig,
                                              meterRegistry,
                                              Map.of(),
                                              null));
    }
}
