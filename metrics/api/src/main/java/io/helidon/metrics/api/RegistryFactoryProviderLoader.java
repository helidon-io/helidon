/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.LazyValue;
import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.config.Config;

/**
 * Loads an available {@link RegistryFactoryProvider} if available via the service loader mechanism, providing a default
 * implementation otherwise (which will return {@code RegistryFactory} instances which yield no-op metrics instances).
 * <p>
 *     The class includes these groups of static methods:
 *     <ul>
 *         <li>The {@code create} methods create a {@link RegistryFactory} according to the specified settings (or defaults) but
 *         keep no reference to such registry factories.</li>
 *         <li>The {@code getInstance} methods provide a {@code RegistryFactory} according to the specified settings (or
 *         defaults) while saving the returned {@link RegistryFactory} as the singleton instance which the {@link #getInstance()}
 *         method returns.</li>
 *         <li>The {@link RegistryFactory#instance(ComponentMetricsSettings)} method which metrics-capable components invoke,
 *         passing a {@code ComponentMetricsSettings} object to indicate what type of {@code RegistryFactory} they need to use,
 *         based on their config and settings.
 *         </li>
 *     </ul>
 * </p>
 * <p>
 *     All the static factory methods use the settings passed as parameters to determine whether the caller wants metrics enabled
 *     or not and to return a suitable {@code RegistryFactory}.
 * </p>
 */
class RegistryFactoryProviderLoader {

    private static final LazyValue<RegistryFactoryProvider> LAZY_FACTORY_PROVIDER =
            LazyValue.create(RegistryFactoryProviderLoader::loadRegistryFactoryProvider);

    private static final RegistryFactoryProvider NO_OP_FACTORY_PROVIDER = (metricsSettings) -> NoOpRegistryFactory.create();

    // Instance managed and returned by the {@link getInstance} methods.
    private static volatile RegistryFactory instance = create();

    // If metrics-capable components ask for a no-op factory, reuse the same one.
    private static final RegistryFactory NO_OP_INSTANCE = NoOpRegistryFactory.create();

    private static RegistryFactoryProvider loadRegistryFactoryProvider() {
        return HelidonServiceLoader.builder(ServiceLoader.load(RegistryFactoryProvider.class))
                .addService(NO_OP_FACTORY_PROVIDER, Integer.MAX_VALUE)
                .build()
                .iterator()
                .next();
    }

    private static final AtomicReference<MetricsSettings> METRICS_SETTINGS = new AtomicReference<>();

    private RegistryFactoryProviderLoader() {
    }

    static RegistryFactory create() {
        return create(MetricsSettingsImpl.DEFAULT);
    }

    static RegistryFactory create(MetricsSettings metricsSettings) {
        return metricsSettings.isEnabled()
                ? LAZY_FACTORY_PROVIDER.get().newRegistryFactory(metricsSettings)
                : NO_OP_FACTORY_PROVIDER.newRegistryFactory(metricsSettings);
    }

    @Deprecated
    static RegistryFactory create(Config config) {
        return create(MetricsSettings.create(config));
    }

    static RegistryFactory getInstance() {
        return instance;
    }

    static synchronized RegistryFactory getInstance(MetricsSettings metricsSettings) {
        instance.update(metricsSettings);
        return instance;
    }

    static synchronized RegistryFactory instance(ComponentMetricsSettings componentMetricsSettings){
        return componentMetricsSettings.isEnabled()
                ? instance
                : NO_OP_INSTANCE;
    }

    @Deprecated
    static RegistryFactory getInstance(Config config) {
        return getInstance(MetricsSettings.create(config));
    }
}
