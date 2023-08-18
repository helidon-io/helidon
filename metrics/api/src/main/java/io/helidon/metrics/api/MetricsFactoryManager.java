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

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.common.config.GlobalConfig;
import io.helidon.metrics.spi.MetricsFactoryProvider;

/**
 * Provides {@link io.helidon.metrics.api.spi.MetricFactory} instances using a highest-weight implementation of
 * {@link io.helidon.metrics.spi.MetricsFactoryProvider}, defaulting to a no-op implementation if no other is available.
 * <p>
 *     The {@link #getInstance()} and {@link #getInstance(MetricsConfig)} methods update and use the most-recently used
 *     {@link io.helidon.metrics.api.MetricsConfig} (which could be derived from {@link io.helidon.common.config.GlobalConfig})
 *     and the most-recently created {@link io.helidon.metrics.api.MetricsFactory}.
 * </p>
 * <p>
 *     The {@link #create(MetricsConfig)} method neither reads nor updates the most-recently used config and factory.
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

    /**
     * The {@link io.helidon.metrics.api.MetricsFactory} most recently created via either {@link #getInstance} method.
     */
    private static MetricsFactory metricsFactory;

    /**
     * The {@link io.helidon.metrics.api.MetricsConfig} used to create {@link #metricsFactory}.
     */
    private static MetricsConfig metricsConfig;

    private static final ReentrantLock LOCK = new ReentrantLock();

    /**
     * Creates a new {@link io.helidon.metrics.api.MetricsFactory} according to the specified
     * {@link io.helidon.metrics.api.MetricsConfig}, saving the config as the current config and the new factory as the current
     * factory.
     *
     * @param metricsConfig metrics config
     * @return new metrics factory
     */
    static MetricsFactory getInstance(MetricsConfig metricsConfig) {
        return access(() -> {
            MetricsFactoryManager.metricsConfig = metricsConfig;
            MetricsFactoryManager.metricsFactory = METRICS_FACTORY_PROVIDER.get().create(metricsConfig);
            return MetricsFactoryManager.metricsFactory;
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
                                                                           () -> METRICS_FACTORY_PROVIDER.get()
                                                                                   .create(ensureMetricsConfig())));
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

    private static MetricsConfig ensureMetricsConfig() {
        metricsConfig = Objects.requireNonNullElseGet(metricsConfig,
                                                      () -> MetricsConfig
                                                              .create(GlobalConfig.config()
                                                                              .get(MetricsConfig.METRICS_CONFIG_KEY)));
        return metricsConfig;
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

    private MetricsFactoryManager() {
    }

}
