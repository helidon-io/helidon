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
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.LazyValue;
import io.helidon.common.serviceloader.HelidonServiceLoader;

/**
 * Loads the highest-priority implementation of {@link MetricsSupportProvider} via service loading or, if none is found, uses a
 * provider for a no-op {@link MetricsSupport}, then uses the selected provider to create instances of {@code MetricsSupport}.
 * <p>
 *     The {@code MetricsSupport} static factory methods delegate to the package private static methods in this class so we can
 *     hide the provider getInstance we use.
 * </p>
 */
class MetricsSupportManager {

    private static final Logger LOGGER = Logger.getLogger(MetricsSupportManager.class.getName());

    private static final NoOpMetricsSupportProviderImpl NO_OP_PROVIDER = new NoOpMetricsSupportProviderImpl();

    private static final LazyValue<MetricsSupportProvider<?>> LAZY_PROVIDER =
            LazyValue.create(MetricsSupportManager::loadMetricsSupportProvider);

    private MetricsSupportManager() {
    }

    private static MetricsSupportProvider<?> loadMetricsSupportProvider() {
        MetricsSupportProvider<?> provider =
                HelidonServiceLoader.builder(ServiceLoader.load(MetricsSupportProvider.class))
                        .addService(NO_OP_PROVIDER, Integer.MAX_VALUE)
                        .build()
                        .asList()
                        .get(0);
        LOGGER.log(Level.INFO, "MetricsSupport provider: {0}", provider.getClass().getName());
        return provider;
    }

    static MetricsSupport create() {
        return LAZY_PROVIDER.get().builder()
                .build();
    }

    static MetricsSupport.Builder<?> builder() {
        return LAZY_PROVIDER.get().builder();
    }

    static MetricsSupport create(MetricsSettings metricsSettings) {
        return MetricsSupportManager.builder()
                .metricsSettings(MetricsSettings.builder(metricsSettings))
                .build();
    }
}
