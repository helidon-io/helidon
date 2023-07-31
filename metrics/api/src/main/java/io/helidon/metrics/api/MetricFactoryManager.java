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
import io.helidon.metrics.spi.MetricFactoryProvider;

/**
 * Locates and makes available the highest-weight implementation of {@link io.helidon.metrics.spi.MetricFactoryProvider},
 * using a default no-op implementation if no other is available.
 */
class MetricFactoryManager {

    /**
     * Instance of the highest-weight implementation of {@code MetricFactory}.
     */
    static final LazyValue<MetricFactoryProvider> INSTANCE =
            LazyValue.create(() -> HelidonServiceLoader.builder(ServiceLoader.load(MetricFactoryProvider.class))
            .addService(NoOpMetricFactoryProvider.create(), Double.MIN_VALUE)
            .build()
            .iterator()
            .next());

    private MetricFactoryManager() {
    }
}
