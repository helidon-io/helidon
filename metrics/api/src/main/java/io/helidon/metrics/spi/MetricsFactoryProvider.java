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
package io.helidon.metrics.spi;

import java.util.Collection;

import io.helidon.common.config.Config;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;

/**
 * Creates new instances of {@link io.helidon.metrics.api.MetricsFactory}.
 */
public interface MetricsFactoryProvider {

    /**
     * Creates a new {@link io.helidon.metrics.api.MetricsFactory} from which the caller can obtain
     * {@link io.helidon.metrics.api.MeterRegistry} and {@link io.helidon.metrics.api.Meter.Builder} instances.
     * <p>
     * The {@code metricsConfig} parameter will have been derived from the {@code rootConfig}. In many cases the
     * new factory will only need to know the metrics configuration so that object is provided as a convenience. The root
     * config node allows the factory to use information from elsewhere in the config tree if needed.
     * </p>
     *
     * @param rootConfig      root {@link Config} node
     * @param metricsConfig   {@link io.helidon.metrics.api.MetricsConfig} settings
     * @param metersProviders group of {@link io.helidon.metrics.spi.MetersProvider} which can furnish
     *                        {@link io.helidon.metrics.api.Meter} instances
     * @return new metrics factory
     */
    MetricsFactory create(Config rootConfig, MetricsConfig metricsConfig, Collection<MetersProvider> metersProviders);
}
