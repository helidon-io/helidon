/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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
import java.util.Objects;

import io.helidon.config.Config;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.service.registry.ServiceRegistry;

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
     * <p>
     * API Note: the default method implementation is provided for backward compatibility
     * and <b>will be removed in the next major version</b>
     *
     * @param rootConfig      root {@link Config} node
     * @param metricsConfig   {@link io.helidon.metrics.api.MetricsConfig} settings
     * @param metersProviders group of {@link io.helidon.metrics.spi.MetersProvider} which can furnish
     *                        {@link io.helidon.metrics.api.Meter.Builder} instances
     * @return new metrics factory
     * @since 4.4.0
     */
    MetricsFactory create(Config rootConfig, MetricsConfig metricsConfig, Collection<MetersProvider> metersProviders);

    /**
     * Creates a new metrics factory using services from the registry which owns the factory.
     *
     * @param rootConfig      root config node
     * @param metricsConfig   metrics settings
     * @param metersProviders providers of built-in meters
     * @param serviceRegistry service registry which owns the new factory
     * @return new metrics factory
     */
    default MetricsFactory create(Config rootConfig,
                                  MetricsConfig metricsConfig,
                                  Collection<MetersProvider> metersProviders,
                                  ServiceRegistry serviceRegistry) {
        Objects.requireNonNull(rootConfig);
        Objects.requireNonNull(metricsConfig);
        Objects.requireNonNull(metersProviders);
        Objects.requireNonNull(serviceRegistry);
        return create(rootConfig, metricsConfig, metersProviders);
    }

    /**
     * No-op retained for compatibility. Service registries own and close the metrics factories they create.
     *
     * @deprecated since 27.0.0, for removal. Metrics factory lifecycle is managed by the service registry.
     */
    @Deprecated(since = "27.0.0", forRemoval = true)
    default void close() {
    }
}
