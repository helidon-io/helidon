/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.common.Weighted;
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
    /**
     * The metrics {@link io.helidon.common.config.Config} node used to initialize the current metrics factory.
     */
    private static Config metricsConfigNode;
    /**
     * The {@link io.helidon.metrics.api.MetricsFactory} most recently created via either {@link #getMetricsFactory} method.
     */
    private static MetricsFactory metricsFactory;

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

        MetricsFactoryManager.metricsConfigNode = metricsConfigNode;

        MetricsConfig metricsConfig = MetricsConfig.create(metricsConfigNode);

        metricsFactory = access(() -> completeGetInstance(metricsConfig, metricsConfigNode));

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
        return access(() -> {
            metricsConfigNode = Objects.requireNonNullElseGet(metricsConfigNode,
                                                              MetricsFactoryManager::externalMetricsConfig);
            metricsFactory = Objects.requireNonNullElseGet(metricsFactory,
                                                           () -> getMetricsFactory(metricsConfigNode));
            return metricsFactory;
        });
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
        return METRICS_FACTORY_PROVIDER.get().create(metricsConfigNode,
                                                     MetricsConfig.create(
                                                             metricsConfigNode.get(MetricsConfig.METRICS_CONFIG_KEY)),
                                                     METER_PROVIDERS.get());
    }

    static void closeAll() {
        METRICS_FACTORY_PROVIDER.get().close();
        metricsFactory = null;
    }

    private static Config externalMetricsConfig() {
        Config serverFeaturesMetricsConfig = GlobalConfig.config().get("server.features.observe.observers.metrics");
        if (!serverFeaturesMetricsConfig.exists()) {
            serverFeaturesMetricsConfig = GlobalConfig.config().get("metrics");
        }
        return serverFeaturesMetricsConfig;
    }

    private static MetricsFactory completeGetInstance(MetricsConfig metricsConfig, Config metricsConfigNode) {

        metricsConfig = applyOverrides(metricsConfig);

        SystemTagsManager.instance(metricsConfig);
        metricsFactory = METRICS_FACTORY_PROVIDER.get().create(metricsConfigNode, metricsConfig, METER_PROVIDERS.get());

        return metricsFactory;
    }

    private static MetricsConfig applyOverrides(MetricsConfig metricsConfig) {
        MetricsConfig.Builder metricsConfigBuilder = MetricsConfig.builder(metricsConfig);
        METRICS_CONFIG_OVERRIDES.get().forEach(override -> override.apply(metricsConfigBuilder));
        return metricsConfigBuilder.build();
    }

    private static <T> T access(Callable<T> c) {
        LOCK.lock();
        try {
            return c.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            LOCK.unlock();
        }
    }
}
