/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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

import java.util.ServiceLoader;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.LazyValue;
import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.config.Config;
import io.helidon.metrics.api.spi.RegistryFactoryProvider;

/**
 * Manages the creation, caching, and reuse of {@link RegistryFactory} instances according to provided metrics settings or
 * configuration.
 *
 * <p>
 * This class loads a {@link io.helidon.metrics.api.spi.RegistryFactoryProvider} if available via the service loader mechanism,
 * providing a default implementation otherwise (which will return {@code RegistryFactory} instances which yield
 * no-op metrics instances).
 * <p>
 *     The class includes these groups of static methods:
 *     <ul>
 *         <li>The {@code create} methods create a new {@link RegistryFactory} according to the specified settings (or defaults)
 *         but keep no reference to such registry factories.</li>
 *         <li>The {@code getInstance} methods reuse a single {@code RegistryFactory}. The instance, the creation of which is
 *         deferred until the first invocation of one of the {@code getInstance} methods, uses either the default
 *         metrics settings or the metrics settings passed to {@link #getInstance(MetricsSettings)} if that is the first
 *         {@code getInstance} method invoked.
 *         Subsequent calls to {@code getInstance(MetricsSettings)} update that same instance with the settings but do not
 *         create a new instance of the registry factory because previous callers might have saved references to the return
 *         values from previous {@code getInstance} invocations.</li>
 *         <li>The {@link RegistryFactory#getInstance(ComponentMetricsSettings)} method which metrics-capable components invoke,
 *         passing a {@code ComponentMetricsSettings} object to indicate what type of {@code RegistryFactory} they need to use,
 *         based on their component-specific metrics settings.
 *         </li>
 *     </ul>
 * </p>
 * <p>
 *     All the static factory methods use the settings passed as parameters to determine whether the caller wants metrics enabled
 *     or not and to return a suitable {@code RegistryFactory}.
 * </p>
 */
class RegistryFactoryManager {

    private static final Logger LOGGER = Logger.getLogger(RegistryFactoryManager.class.getName());

    private static final LazyValue<RegistryFactoryProvider> LAZY_FACTORY_PROVIDER =
            LazyValue.create(RegistryFactoryManager::loadRegistryFactoryProvider);

    private static final RegistryFactoryProvider NO_OP_FACTORY_PROVIDER = (metricsSettings) -> NoOpRegistryFactory.create();

    // Might be changed via getInstance(MetricsSettings).
    private static MetricsSettings metricsSettings = MetricsSettings.create();

    // Instance managed and returned by the {@link getInstance} methods. Use the latest-provided metrics settings.
    private static final LazyValue<RegistryFactory> INSTANCE = LazyValue.create(() -> create(metricsSettings));

    // If metrics-capable components ask for a no-op factory, reuse the same one.
    private static final RegistryFactory NO_OP_INSTANCE = NoOpRegistryFactory.create();

    private static RegistryFactoryProvider loadRegistryFactoryProvider() {
        RegistryFactoryProvider provider = HelidonServiceLoader.builder(ServiceLoader.load(RegistryFactoryProvider.class))
                .addService(NO_OP_FACTORY_PROVIDER, Integer.MAX_VALUE)
                .build()
                .asList()
                .get(0);
        LOGGER.log(Level.FINE, "Metrics registry factory provider: {0}", provider.getClass().getName());
        return provider;
    }

    private static final Lock SETTINGS_ACCESS = new ReentrantLock(true);

    private RegistryFactoryManager() {
    }

    static RegistryFactory create() {
        return create(MetricsSettings.builder().build());
    }

    static RegistryFactory create(MetricsSettings metricsSettings) {
        return metricsSettings.isEnabled()
                ? LAZY_FACTORY_PROVIDER.get().create(metricsSettings)
                : NO_OP_FACTORY_PROVIDER.create(metricsSettings);
    }

    @Deprecated
    static RegistryFactory create(Config config) {
        return create(MetricsSettings.create(config));
    }

    static RegistryFactory getInstance() {
        return INSTANCE.get();
    }

    static RegistryFactory getInstance(MetricsSettings metricsSettings) {

        return accessMetricsSettings(() -> {
            RegistryFactoryManager.metricsSettings = metricsSettings;
            RegistryFactory result = INSTANCE.get();
            result.update(metricsSettings);
            return result;
        });
    }

    static RegistryFactory getInstance(ComponentMetricsSettings componentMetricsSettings) {

        return accessMetricsSettings(() -> componentMetricsSettings.isEnabled()
                ? INSTANCE.get()
                : NO_OP_INSTANCE);
    }

    @Deprecated
    static RegistryFactory getInstance(Config config) {
        return getInstance(MetricsSettings.create(config));
    }

    private static <T> T accessMetricsSettings(Supplier<T> operation) {
        SETTINGS_ACCESS.lock();
        try {
            return operation.get();
        } finally {
            SETTINGS_ACCESS.unlock();
        }
    }
}
