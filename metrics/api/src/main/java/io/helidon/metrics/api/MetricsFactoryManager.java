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
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
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
    /**
     * Instance of the highest-weight implementation of {@link io.helidon.metrics.spi.MetricsFactoryProvider}
     * for obtaining new {@link io.helidon.metrics.api.MetricsFactory} instances; this module contains a no-op implementation
     * as a last resort.
     */
    private static final LazyValue<MetricsFactoryProvider> METRICS_FACTORY_PROVIDER =
            io.helidon.common.LazyValue.create(() -> {
                MetricsFactoryProvider result = HelidonServiceLoader.builder(ServiceLoader.load(MetricsFactoryProvider.class))
                        .addService(NoOpMetricsFactoryProvider.create(), Double.MIN_VALUE)
                        .build()
                        .iterator()
                        .next();
                LOGGER.log(Level.DEBUG, "Loaded metrics factory provider: {0}",
                           result.getClass().getName());
                return result;
            });
    /**
     * Config overrides that can change the {@link io.helidon.metrics.api.MetricsConfig} that is read from config sources
     * if there are specific requirements in a given runtime (e.g., MP) for certain settings. For example, the tag name used
     * for recording scope, the app name, etc. We apply all overriding config implementations, so reverse the list after the
     * Helidon service loader computes it so we apply lower-weight implementations first so higher-weight ones can override.
     */
    private static final LazyValue<Collection<MetricsProgrammaticConfig>> METRICS_CONFIG_OVERRIDES =
            io.helidon.common.LazyValue.create(() ->
                       HelidonServiceLoader.builder(ServiceLoader.load(MetricsProgrammaticConfig.class))
                               .addService(new SeMetricsProgrammaticConfig(),
                                           Weighted.DEFAULT_WEIGHT - 50)
                               .build()
                               .asList()
                               .reversed());
    private static final ReentrantLock LOCK = new ReentrantLock();
    /**
     * Providers of meter builders (such as the built-in "base" meters for system performance information). All providers are
     * furnished to all {@link io.helidon.metrics.api.MeterRegistry} instances that are created by any
     * {@link io.helidon.metrics.api.MetricsFactory}.
     */
    private static final LazyValue<Collection<MetersProvider>> METER_PROVIDERS =
            LazyValue.create(() -> HelidonServiceLoader.create(ServiceLoader.load(MetersProvider.class))
                    .asList());

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
        for (MetricsProgrammaticConfig programmaticConfig : METRICS_CONFIG_OVERRIDES.get()) {
            metricsConfig = programmaticConfig.apply(metricsConfig);
        }
        return METRICS_FACTORY_PROVIDER.get().create(rootConfig,
                                                     metricsConfig,
                                                     METER_PROVIDERS.get());
    }

    static void closeAll() {
        METRICS_FACTORY_PROVIDER.get().close();
    }
}
