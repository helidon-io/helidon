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

package io.helidon.metrics.providers.helidon;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import io.helidon.common.Api;
import io.helidon.config.Config;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.spi.MeterBuilderCustomizer;
import io.helidon.metrics.spi.MeterRegistryLifeCycleListener;
import io.helidon.metrics.spi.MetersProvider;
import io.helidon.metrics.spi.MetricsFactoryProvider;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceRegistry;

/**
 * Provider of the Helidon metrics factory.
 */
@Api.Internal
public class HelidonMetricsFactoryProvider implements MetricsFactoryProvider {
    /**
     * Required public constructor for {@link java.util.ServiceLoader}.
     */
    public HelidonMetricsFactoryProvider() {
    }

    @Override
    public MetricsFactory create(Config rootConfig,
                                 MetricsConfig metricsConfig,
                                 Collection<MetersProvider> metersProviders) {
        return create(rootConfig, metricsConfig, metersProviders, GlobalServiceRegistry.registry());
    }

    @Override
    public MetricsFactory create(Config rootConfig,
                                 MetricsConfig metricsConfig,
                                 Collection<MetersProvider> metersProviders,
                                 ServiceRegistry serviceRegistry) {
        Objects.requireNonNull(rootConfig);
        Objects.requireNonNull(metricsConfig);
        Objects.requireNonNull(metersProviders);
        Objects.requireNonNull(serviceRegistry);
        return HelidonMetricsFactory.builder()
                .metricsConfig(metricsConfig)
                .metersProviders(List.copyOf(metersProviders))
                .meterBuilderCustomizers(serviceRegistry.all(MeterBuilderCustomizer.class))
                .meterRegistryLifeCycleListeners(serviceRegistry.all(MeterRegistryLifeCycleListener.class))
                .build();
    }
}
