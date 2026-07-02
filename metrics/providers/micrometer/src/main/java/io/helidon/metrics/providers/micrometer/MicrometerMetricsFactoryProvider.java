/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.util.Collection;

import io.helidon.common.Api;
import io.helidon.config.Config;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.providers.micrometer.spi.SpanContextSupplierProvider;
import io.helidon.metrics.spi.MeterRegistryLifeCycleListener;
import io.helidon.metrics.spi.MetersProvider;
import io.helidon.metrics.spi.MetricsFactoryProvider;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceRegistry;

/**
 * Provides the Micrometer meter registry to use as a delegate for the implementation of the Helidon metrics API.
 */
public class MicrometerMetricsFactoryProvider implements MetricsFactoryProvider {

    /**
     * Required public constructor for {@link java.util.ServiceLoader}.
     */
    @Api.Internal
    public MicrometerMetricsFactoryProvider() {
    }

    @Override
    public MetricsFactory create(Config rootConfig, MetricsConfig metricsConfig, Collection<MetersProvider> metersProviders) {
        return create(rootConfig, metricsConfig, metersProviders, GlobalServiceRegistry.registry());
    }

    @Override
    public MetricsFactory create(Config rootConfig,
                                 MetricsConfig metricsConfig,
                                 Collection<MetersProvider> metersProviders,
                                 ServiceRegistry serviceRegistry) {
        Collection<MeterRegistryLifeCycleListener> lifeCycleListeners =
                serviceRegistry.all(MeterRegistryLifeCycleListener.class);
        SpanContextSupplierProvider spanContextSupplierProvider =
                serviceRegistry.first(SpanContextSupplierProvider.class)
                        .orElseGet(NoOpSpanContextSupplierProvider::new);
        return MicrometerMetricsFactory.create(metricsConfig,
                                               metersProviders,
                                               lifeCycleListeners,
                                               spanContextSupplierProvider);
    }
}
