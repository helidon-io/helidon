/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
import io.helidon.common.LazyValue;
import io.helidon.common.config.Config;
import io.helidon.common.config.GlobalConfig;
import io.helidon.metrics.spi.MetersProvider;
import io.helidon.metrics.spi.MetricsFactoryProvider;
import io.helidon.metrics.spi.MetricsProgrammaticConfig;

/**
 * Provides {@link io.helidon.metrics.api.MetricsFactory} instances using a highest-weight implementation of
 * {@link io.helidon.metrics.spi.MetricsFactoryProvider}, defaulting to a no-op implementation if no other is available.
 * <p>
 * The {@link #getMetricsFactory()} returns the metrics factory most recently created by invoking
 * {@link #getMetricsFactory(Config)}, which creates a new metrics factory using the provided config and also saves the
 * resulting metrics factory as the most recent.
 * <p>
 * Invoking {@code getMetricsFactory()} (no argument) <em>before</em> invoking the variant with the
 * {@link io.helidon.common.config.Config} parameter creates and saves a metrics factory using the
 * {@link io.helidon.common.config.GlobalConfig}.
 * <p>
 * The {@link #create(Config)} method neither reads nor updates the most-recently used config and factory.
 */
@SuppressWarnings("removal")
class MetricsFactoryManager {

    private static final System.Logger LOGGER = System.getLogger(MetricsFactoryManager.class.getName());

    /**
     * Config overrides that can change the {@link io.helidon.metrics.api.MetricsConfig} that is read from config sources
     * if there are specific requirements in a given runtime (e.g., MP) for certain settings. For example, the tag name used
     * for recording scope, the app name, etc.
     */
    private static final LazyValue<Collection<MetricsProgrammaticConfig>> METRICS_CONFIG_OVERRIDES =
            io.helidon.common.LazyValue.create(() ->
                                                       HelidonServiceLoader
                                                               .create(ServiceLoader.load(MetricsProgrammaticConfig.class))
                                                               .asList());

    private MetricsFactoryManager() {
    }

    /**
     * Creates a new {@link io.helidon.metrics.api.MetricsFactory} according to the {@value MetricsConfig#METRICS_CONFIG_KEY}
     * section in the specified config node, deriving and saving the metrics config as the current metrics config, saving the new
     * factory as the current factory, and registering meters via meter providers to the global meter registry of the new factory.
     *
     * @param metricsConfigNode metrics config node
     * @return new metrics factory
     */
    static MetricsFactory getMetricsFactory(Config metricsConfigNode) {
        io.helidon.common.GlobalInstances.set(MetricsConfigHolder.class, new MetricsConfigHolder(metricsConfigNode));

        MetricsFactory metricsFactory = buildMetricsFactory(metricsConfigNode);
        io.helidon.common.GlobalInstances.set(MetricsFactoryHolder.class, new MetricsFactoryHolder(metricsFactory));

        return metricsFactory;
    }

    /**
     * Returns the current {@link io.helidon.metrics.api.MetricsFactory}, creating one if needed using the global configuration
     * and saving the {@link io.helidon.metrics.api.MetricsConfig} from the global config as the current config and the new
     * factory as the current factory.
     *
     * @return current metrics factory
     */
    static MetricsFactory getMetricsFactory() {
        return io.helidon.common.GlobalInstances.get(MetricsFactoryHolder.class,
                                                     () -> new MetricsFactoryHolder(buildMetricsFactory(currentMetricsConfig())))
                .metricsFactory();
    }

    /**
     * Creates a new {@link io.helidon.metrics.api.MetricsFactory} using the specified
     * {@link io.helidon.metrics.api.MetricsConfig} with no side effects: neither the config nor the new factory replace
     * the current values stored in this manager.
     *
     * @param metricsConfigNode the metrics config node to use in creating the metrics factory
     * @return new metrics factory
     */
    static MetricsFactory create(Config metricsConfigNode) {
        return globalMetricsFactoryProvider()
                .create(metricsConfigNode,
                        MetricsConfig.create(
                                metricsConfigNode.get(MetricsConfig.METRICS_CONFIG_KEY)),
                        metersProviders());
    }

    static void closeAll() {
        globalMetricsFactoryProvider().close();
        io.helidon.common.GlobalInstances.current(MetricsFactoryHolder.class)
                        .ifPresent(MetricsFactoryHolder::close);
    }

    /*
     * Providers of meter builders (such as the built-in "base" meters for system performance information). All providers are
     * furnished to all {@link io.helidon.metrics.api.MeterRegistry} instances that are created by any
     * {@link io.helidon.metrics.api.MetricsFactory}.
     */
    private static Collection<MetersProvider> metersProviders() {
        return io.helidon.common.GlobalInstances.get(MetersProviders.class, () ->
                        new MetersProviders(HelidonServiceLoader.create(ServiceLoader.load(MetersProvider.class)).asList()))
                .providers();
    }

    /*
     * Instance of the highest-weight implementation of {@link io.helidon.metrics.spi.MetricsFactoryProvider}
     * for obtaining new {@link io.helidon.metrics.api.MetricsFactory} instances; this module contains a no-op implementation
     * as a last resort.
     */
    private static MetricsFactoryProvider metricsFactoryProvider() {
        MetricsFactoryProvider result = HelidonServiceLoader.builder(ServiceLoader.load(MetricsFactoryProvider.class))
                .addService(NoOpMetricsFactoryProvider.create(), Double.MIN_VALUE)
                .build()
                .iterator()
                .next();
        LOGGER.log(Level.DEBUG, "Loaded metrics factory provider: {0}",
                   result.getClass().getName());
        return result;
    }

    private static MetricsFactoryProvider globalMetricsFactoryProvider() {
        return io.helidon.common.GlobalInstances.get(MetricsFactoryProviderHolder.class, () -> {
            return new MetricsFactoryProviderHolder(metricsFactoryProvider());
        }).provider();
    }

    private static Config currentMetricsConfig() {
        return io.helidon.common.GlobalInstances.get(MetricsConfigHolder.class,
                                                     () -> new MetricsConfigHolder(MetricsFactoryManager
                                                                                           .externalMetricsConfig()))
                .config();
    }

    private static MetricsFactory buildMetricsFactory(Config metricsConfigNode) {
        MetricsConfig metricsConfig = applyOverrides(MetricsConfig.create(metricsConfigNode));
        SystemTagsManager.instance(metricsConfig);

        return globalMetricsFactoryProvider()
                .create(metricsConfigNode, metricsConfig, metersProviders());
    }

    private static Config externalMetricsConfig() {
        Config serverFeaturesMetricsConfig = GlobalConfig.config().get("server.features.observe.observers.metrics");
        if (!serverFeaturesMetricsConfig.exists()) {
            serverFeaturesMetricsConfig = GlobalConfig.config().get("metrics");
        }
        return serverFeaturesMetricsConfig;
    }

    private static MetricsConfig applyOverrides(MetricsConfig metricsConfig) {
        MetricsConfig.Builder metricsConfigBuilder = MetricsConfig.builder(metricsConfig);
        METRICS_CONFIG_OVERRIDES.get().forEach(override -> override.apply(metricsConfigBuilder));
        return metricsConfigBuilder.build();
    }

    private record MetersProviders(Collection<MetersProvider> providers)
            implements io.helidon.common.GlobalInstances.GlobalInstance {
        @Override
        public void close() {
        }
    }

    private record MetricsConfigHolder(Config config)
            implements io.helidon.common.GlobalInstances.GlobalInstance {
        @Override
        public void close() {
        }
    }

    private record MetricsFactoryHolder(MetricsFactory metricsFactory)
            implements io.helidon.common.GlobalInstances.GlobalInstance {
        @Override
        public void close() {
            metricsFactory.close();
        }
    }

    private record MetricsFactoryProviderHolder(MetricsFactoryProvider provider)
            implements io.helidon.common.GlobalInstances.GlobalInstance {
        @Override
        public void close() {
            provider.close();
        }
    }
}
