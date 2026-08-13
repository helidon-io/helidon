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
package io.helidon.metrics.api;

import java.lang.System.Logger.Level;
import java.util.Collection;

import io.helidon.config.Config;
import io.helidon.metrics.spi.MetersProvider;
import io.helidon.metrics.spi.MetricsFactoryProvider;
import io.helidon.metrics.spi.MetricsProgrammaticConfig;
import io.helidon.service.registry.ServiceRegistry;

/**
 * Creates {@link io.helidon.metrics.api.MetricsFactory} instances using a highest-weight implementation of
 * {@link io.helidon.metrics.spi.MetricsFactoryProvider}, defaulting to a no-op implementation if no other is available.
 */
class MetricsFactoryManager {

    private static final System.Logger LOGGER = System.getLogger(MetricsFactoryManager.class.getName());

    private MetricsFactoryManager() {
    }

    /**
     * Creates a new {@link io.helidon.metrics.api.MetricsFactory} using the specified root config.
     *
     * @param rootConfig the root config node to use in creating the metrics factory
     * @return new metrics factory
     */
    static MetricsFactory create(Config rootConfig, ServiceRegistry serviceRegistry) {
        MetricsConfig metricsConfig = MetricsConfig.create(selectMetricsConfigNode(rootConfig));
        Collection<MetricsProgrammaticConfig> metricsConfigOverrides = serviceRegistry
                .all(MetricsProgrammaticConfig.class)
                .reversed();
        for (MetricsProgrammaticConfig programmaticConfig : metricsConfigOverrides) {
            metricsConfig = programmaticConfig.apply(metricsConfig);
        }

        MetricsFactoryProvider metricsFactoryProvider = serviceRegistry.first(MetricsFactoryProvider.class)
                .orElseGet(NoOpMetricsFactoryProvider::create);
        LOGGER.log(Level.DEBUG, "Loaded metrics factory provider: {0}", metricsFactoryProvider.getClass().getName());

        Collection<MetersProvider> meterProviders = serviceRegistry.all(MetersProvider.class);
        return metricsFactoryProvider.create(rootConfig, metricsConfig, meterProviders, serviceRegistry);
    }

    private static Config selectMetricsConfigNode(Config rootConfig) {
        Config metricsConfig = rootConfig.get("server.features.observe.observers.metrics");
        return metricsConfig.exists() ? metricsConfig : rootConfig.get(MetricsConfig.METRICS_CONFIG_KEY);
    }
}
