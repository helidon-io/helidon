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
interface MetricsObserverConfigBlueprint extends ObserverConfigBase, Prototype.Factory<MetricsObserver> {
    @Option.Configured
    @Option.Default("metrics")
    String endpoint();

    @Override
    @Option.Default("metrics")
    String name();

    /**
     * Assigns {@code MetricsSettings} which will be used in creating the {@code MetricsSupport} instance at build-time.
     *
     * @return the metrics settings to assign for use in building the {@code MetricsSupport} instance
     */
    @Option.Configured(merge = true)
    @Option.DefaultCode("@io.helidon.metrics.api.MetricsConfig@.create()")
    MetricsConfig metricsConfig();

    /**
     * If you want to have multiple meter registries with different
     * endpoints, you may create them using
     * {@snippet :
     *      MeterRegistry meterRegistry = MetricsFactory.getInstance()
     *              .createMeterRegistry(metricsConfig);
     *      MetricsFeature.builder()
     *              .meterRegistry(meterRegistry) // further settings on the feature builder, etc.
     * }
     * where {@code metricsConfig} in each case has different
     * {@link #endpoint() settings}.
     * <p>
     * If this method is not called,
     * {@link MetricsFeature} would use the shared
     * instance as provided by
     * {@link io.helidon.metrics.api.MetricsFactory#globalRegistry()}.
     *
     * @return meterRegistry to use in this metric support
     */
    Optional<MeterRegistry> meterRegistry();
}
