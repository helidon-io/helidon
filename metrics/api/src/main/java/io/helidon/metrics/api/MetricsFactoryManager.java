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
import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.Weighted;
import io.helidon.config.Config;
import io.helidon.metrics.spi.MetersProvider;
import io.helidon.metrics.spi.MetricsFactoryProvider;
import io.helidon.metrics.spi.MetricsProgrammaticConfig;
import io.helidon.service.registry.Services;

/**
 * Provides {@link io.helidon.metrics.api.MetricsFactory} instances using a highest-weight implementation of
 * {@link io.helidon.metrics.spi.MetricsFactoryProvider}, defaulting to a no-op implementation if no other is available.
 * <p>
 * The {@link #create(Config)} method neither reads nor updates the most-recently used config and factory.
 */
class MetricsFactoryManager {

    private static final System.Logger LOGGER = System.getLogger(MetricsFactoryManager.class.getName());

    private MetricsFactoryManager() {
    }

    /**
     * This method now simply calls {@link io.helidon.service.registry.Services#get(Class)}.
     *
     * @param ignoredConfig ignored config
     * @return shared metrics factory
     * @deprecated either use {@link io.helidon.service.registry.Services} directly; if the intention is to use a different
     *      shared instance than the default one, create a service factory with a higher than default
     *      {@link io.helidon.common.Weight}, or call {@link io.helidon.service.registry.Services#set(Class, Object[])}
     *      before the application starts
     */
    @Deprecated(since = "27.0.0", forRemoval = true)
    static MetricsFactory getMetricsFactory(Config ignoredConfig) {
        LOGGER.log(Level.WARNING, "Method MetricsFactoryManager.getMetricsFactory(Config) does not work as in "
                + "previous major versions of Helidon, and simply returns the instance from ServiceRegistry. "
                + "This method is now deprecated and will be removed.");

        return Services.get(MetricsFactory.class);
    }

    /**
     * Returns the shared metrics factory from
     * {@link io.helidon.service.registry.Services#get(java.lang.Class) Services.get(MetricsFactory.class)}.
     *
     * @return shared metrics factory
     * @deprecated since 27.0.0, for removal. Use
     * {@link io.helidon.service.registry.Services#get(java.lang.Class) Services.get(MetricsFactory.class)}.
     */
    @Deprecated(since = "27.0.0", forRemoval = true)
    static MetricsFactory getMetricsFactory() {
        LOGGER.log(Level.WARNING, "Method MetricsFactoryManager.getMetricsFactory() does not work as in "
                + "previous major versions of Helidon, and simply returns the instance from ServiceRegistry. "
                + "This method is now deprecated and will be removed.");

        return Services.get(MetricsFactory.class);
    }

    /**
     * Returns the shared metrics factory from
     * {@link io.helidon.service.registry.Services#get(java.lang.Class) Services.get(MetricsFactory.class)}.
     *
     * @param ignoredConfig ignored config
     * @return shared metrics factory
     */
    @Deprecated(since = "27.0.0", forRemoval = true)
    static MetricsFactory getOrCreateMetricsFactory(Config ignoredConfig) {
        LOGGER.log(Level.WARNING, "Method MetricsFactoryManager.getOrCreateMetricsFactory(Config) does not work as in "
                + "previous major versions of Helidon, and simply returns the instance from ServiceRegistry. "
                + "This method is now deprecated and will be removed.");

        return Services.get(MetricsFactory.class);
    }

    /**
     * Creates a new {@link io.helidon.metrics.api.MetricsFactory} using the specified root config.
     *
     * @param rootConfig the root config node to use in creating the metrics factory
     * @return new metrics factory
     */
    static MetricsFactory create(Config rootConfig) {
        MetricsConfig metricsConfig = MetricsConfig.create(rootConfig.get(MetricsConfig.METRICS_CONFIG_KEY));
        Collection<MetricsProgrammaticConfig> metricsConfigOverrides =
                HelidonServiceLoader.builder(ServiceLoader.load(MetricsProgrammaticConfig.class))
                        .addService(new SeMetricsProgrammaticConfig(), Weighted.DEFAULT_WEIGHT - 50)
                        .build()
                        .asList()
                        .reversed();
        for (MetricsProgrammaticConfig programmaticConfig : metricsConfigOverrides) {
            metricsConfig = programmaticConfig.apply(metricsConfig);
        }

        MetricsFactoryProvider metricsFactoryProvider =
                HelidonServiceLoader.builder(ServiceLoader.load(MetricsFactoryProvider.class))
                        .addService(NoOpMetricsFactoryProvider.create(), Double.MIN_VALUE)
                        .build()
                        .iterator()
                        .next();
        LOGGER.log(Level.DEBUG, "Loaded metrics factory provider: {0}", metricsFactoryProvider.getClass().getName());

        Collection<MetersProvider> meterProviders = HelidonServiceLoader.create(ServiceLoader.load(MetersProvider.class))
                .asList();
        return metricsFactoryProvider.create(rootConfig, metricsConfig, meterProviders);
    }
}
