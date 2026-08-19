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

package io.helidon.webserver.observe.metrics;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.webserver.observe.ObserverConfigBase;
import io.helidon.webserver.observe.spi.ObserveProvider;

/**
 * Metrics Observer configuration.
 */
@Prototype.Blueprint
@Prototype.Configured("metrics")
@Prototype.Provides(ObserveProvider.class)
@Prototype.IncludeDefaultMethods
interface MetricsObserverConfigBlueprint extends ObserverConfigBase, Prototype.Factory<MetricsObserver> {
    @Option.Configured
    @Option.Default("metrics")
    String endpoint();

    @Override
    @Option.Default("metrics")
    String name();

    /**
     * Automatic metrics collection settings.
     *
     * @return auto metrics collection settings
     */
    @Option.Configured()
    default Optional<AutoHttpMetricsConfig> autoHttpMetrics() {
        return Optional.empty();
    }

    /**
     * Assigns metrics settings for the observer endpoint, including whether the endpoint is enabled and its access controls.
     * With a custom {@link io.helidon.metrics.api.MeterRegistry}, the settings also describe the registry-specific behavior.
     * With the shared registry, registry-specific behavior uses the settings from that registry's owning
     * {@link io.helidon.metrics.api.MetricsFactory} while the endpoint settings assigned here still apply.
     *
     * @return metrics settings for the observer endpoint and, when configured, a custom registry
     */
    @Option.Configured(merge = true)
    @Option.DefaultMethod("create")
    MetricsConfig metricsConfig();

    /**
     * If you want to have multiple meter registries with different
     * endpoints, you may create them using
     * {@snippet :
     *      MeterRegistry meterRegistry = io.helidon.service.registry.Services
     *              .get(io.helidon.metrics.api.MetricsFactory.class)
     *              .createMeterRegistry(metricsConfig);
     *      MetricsObserver.builder()
     *              .endpoint("metrics-2")
     *              .metricsConfig(metricsConfig)
     *              .meterRegistry(meterRegistry) // further settings on the observer builder, etc.
     *              .build();
     * }
     * where {@code metricsConfig} can contain registry-specific settings.
     * Configure a different {@link #endpoint()} on each observer.
     * <p>
     * A meter registry has one effective set of registry-specific settings. Multiple metrics observers may share the same
     * registry only when their registry-specific settings are equivalent; endpoint settings may differ. Use separate meter
     * registries for observers that require different registry-specific behavior.
     * <p>
     * A custom meter registry passed to an observer remains caller-owned. Close it after all observers using it have stopped.
     * <p>
     * If this method is not called,
     * {@link MetricsObserver} uses the shared
     * instance as provided by
     * {@link io.helidon.service.registry.Services#get(java.lang.Class)
     * Services.get(MeterRegistry.class)}.
     *
     * @return meterRegistry to use in this metric support
     */
    Optional<MeterRegistry> meterRegistry();
}
