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

import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.common.config.GlobalConfig;
import io.helidon.metrics.spi.MetricsFactoryProvider;

/**
 * Locates and makes available a highest-weight implementation of {@link io.helidon.metrics.spi.MetricsFactoryProvider},
 * using a default no-op implementation if no other is available.
 */
class MetricsFactoryManager {

    /**
     * Instance of the highest-weight implementation of {@link io.helidon.metrics.spi.MetricsFactoryProvider}.
     */
    private static final LazyValue<MetricsFactoryProvider> METRICS_FACTORY_PROVIDER =
            LazyValue.create(() -> HelidonServiceLoader.builder(ServiceLoader.load(MetricsFactoryProvider.class))
            .addService(NoOpMetricsFactoryProvider.create(), Double.MIN_VALUE)
            .build()
            .iterator()
            .next());

    private static final LazyValue<MetricsFactory> METRICS_FACTORY =
            LazyValue.create(() -> METRICS_FACTORY_PROVIDER.get().create(
                    MetricsConfig.builder()
                            .config(GlobalConfig.config().get(MetricsConfig.METRICS_CONFIG_KEY))
                            .build()));

    static MetricsFactory getInstance() {
        return METRICS_FACTORY.get();
    }

    private MetricsFactoryManager() {
    }
}
