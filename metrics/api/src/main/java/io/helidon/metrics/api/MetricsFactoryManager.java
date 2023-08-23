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

import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.StreamSupport;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.common.config.Config;
import io.helidon.common.config.GlobalConfig;
import io.helidon.metrics.spi.MetersProvider;
import io.helidon.metrics.spi.MetricsFactoryProvider;

/**
 * Provides {@link io.helidon.metrics.api.spi.MetricFactory} instances using a highest-weight implementation of
 * {@link io.helidon.metrics.spi.MetricsFactoryProvider}, defaulting to a no-op implementation if no other is available.
 * <p>
 * The {@link #getInstance()} and {@link #getInstance(Config)} methods update and use the most-recently used
 * {@link io.helidon.metrics.api.MetricsConfig} (derived from either the specified {@link io.helidon.common.config.Config}
 * node or, if none, the {@link io.helidon.common.config.GlobalConfig})
 * and the most-recently created {@link io.helidon.metrics.api.MetricsFactory}.
 * </p>
 * <p>
 * The {@link #create(MetricsConfig)} method neither reads nor updates the most-recently used config and factory.
 * </p>
 */
class MetricsFactoryManager {

    private static final System.Logger LOGGER = System.getLogger(MetricsFactoryManager.class.getName());

    /**
     * Instance of the highest-weight implementation of {@link io.helidon.metrics.spi.MetricsFactoryProvider}.
     */
    private static final LazyValue<MetricsFactoryProvider> METRICS_FACTORY_PROVIDER =
            LazyValue.create(() -> {
                MetricsFactoryProvider result = HelidonServiceLoader.builder(ServiceLoader.load(MetricsFactoryProvider.class))
                        .addService(NoOpMetricsFactoryProvider.create(), Double.MIN_VALUE)
                        .build()
                        .iterator()
                        .next();
                LOGGER.log(System.Logger.Level.DEBUG, "Loaded metrics factory provider: {0}",
                           result.getClass().getName());
                return result;
            });
    private static final ReentrantLock LOCK = new ReentrantLock();
    /**
     * The {@link io.helidon.metrics.api.MetricsFactory} most recently created via either {@link #getInstance} method.
     */
    private static MetricsFactory metricsFactory;
    /**
     * The {@link io.helidon.metrics.api.MetricsConfig} used to create {@link #metricsFactory}.
     */
    private static MetricsConfig metricsConfig;
    /**
     * The root {@link io.helidon.common.config.Config} node used to initialize the current metrics factory.
     */
    private static Config rootConfig;

    private MetricsFactoryManager() {
    }

    /**
     * Creates a new {@link io.helidon.metrics.api.MetricsFactory} according to the {@value MetricsConfig#METRICS_CONFIG_KEY}
     * section in the specified config node, deriving and saving the metrics config as the current metrics config, saving the new
     * factory as the current factory, and registering meters via meter providers to the global meter registry of the new factory.
     *
     * @param rootConfig root config node
     * @return new metrics factory
     */
    static MetricsFactory getInstance(Config rootConfig) {
        return access(() -> {
            MetricsFactoryManager.rootConfig = rootConfig;
            metricsConfig = MetricsConfig.create(rootConfig.get(MetricsConfig.METRICS_CONFIG_KEY));
            metricsFactory = METRICS_FACTORY_PROVIDER.get().create(metricsConfig);
            applyProviders(metricsFactory.globalRegistry(), rootConfig);
            return metricsFactory;
        });
    }

    /**
     * Creates a new {@link io.helidon.metrics.api.MetricsFactory} according to the specified
     * {@link io.helidon.metrics.api.MetricsConfig}, saving the metrics config as the current one, saving the
     * new factory as the current one, and registering meters via meter providers to the global meter registry of the new factory.
     *
     * @param metricsConfig root config node
     * @return new metrics factory
     */
    static MetricsFactory getInstance(MetricsConfig metricsConfig) {
        return access(() -> {
            rootConfig = Config.empty();
            MetricsFactoryManager.metricsConfig = metricsConfig;
            metricsFactory = METRICS_FACTORY_PROVIDER.get().create(metricsConfig);
            applyProviders(metricsFactory.globalRegistry(), rootConfig);
            return metricsFactory;
        });
    }

    /**
     * Returns the current {@link io.helidon.metrics.api.MetricsFactory}, creating one if needed using the global configuration
     * and saving the {@link io.helidon.metrics.api.MetricsConfig} from the global config as the current config and the new
     * factory as the current factory.
     *
     * @return current metrics factory
     */
    static MetricsFactory getInstance() {
        return access(() -> metricsFactory = Objects.requireNonNullElseGet(metricsFactory,
                                                                           () -> getInstance(GlobalConfig.config())));

        // TODO remove comment once using the preceding line proves out
        //                                                       () -> {
        //                                                           MetricsFactory result = METRICS_FACTORY_PROVIDER.get()
        //                                                                   .create(ensureMetricsConfig());
        //                                                           applyProviders(result.globalRegistry(),
        //                                                                          rootConfig);
        //                                                           return result;
        //                                                       }));
    }

    /**
     * Creates a new {@link io.helidon.metrics.api.MetricsFactory} using the specified
     * {@link io.helidon.metrics.api.MetricsConfig} with no side effects: neither the config nor the new factory replace
     * the current values stored in this manager.
     *
     * @param metricsConfig metrics config to use in creating the factory
     * @return new metrics factory
     */
    static MetricsFactory create(MetricsConfig metricsConfig) {
        return METRICS_FACTORY_PROVIDER.get().create(metricsConfig);
    }

    private static void applyProviders(MeterRegistry meterRegistry, Config rootConfig) {
        HelidonServiceLoader.create(ServiceLoader.load(MetersProvider.class))
                .stream()
                .map(provider -> provider.meters(rootConfig))
                .map(Iterable::spliterator)
                .flatMap(split -> StreamSupport.stream(split, false))
                // Use raw Meter.Builder below because getOrCreate is generic-typed and here we don't know or really care about
                // the actual type of the builder.
                .forEach(b -> meterRegistry.getOrCreate((Meter.Builder) b));
    }

    private static MetricsConfig ensureMetricsConfig() {
        metricsConfig = Objects.requireNonNullElseGet(metricsConfig,
                                                      () -> MetricsConfig
                                                              .create(ensureRootConfig()
                                                                              .get(MetricsConfig.METRICS_CONFIG_KEY)));
        return metricsConfig;
    }

    private static Config ensureRootConfig() {
        rootConfig = Objects.requireNonNullElseGet(rootConfig, GlobalConfig::config);
        return rootConfig;
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
