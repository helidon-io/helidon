/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.metrics.providers.helidon;

import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;
import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.spi.MeterBuilderCustomizer;
import io.helidon.metrics.spi.MeterRegistryLifeCycleListener;
import io.helidon.metrics.spi.MetersProvider;

/**
 * Configuration for the Helidon metrics factory.
 */
@Api.Preview
@Prototype.Blueprint
interface HelidonMetricsFactoryConfigBlueprint extends Prototype.Factory<HelidonMetricsFactory> {

    /**
     * Metrics settings.
     *
     * @return metrics settings
     */
    @Option.DefaultMethod("create")
    MetricsConfig metricsConfig();

    /**
     * Clock used by registries when none is assigned explicitly.
     *
     * @return clock
     */
    @Option.DefaultCode("HelidonClock.SYSTEM")
    Clock clock();

    /**
     * Providers of meters registered with the global registry.
     *
     * @return meter providers
     */
    @Option.Singular
    List<MetersProvider> metersProviders();

    /**
     * Customizers applied to meter builders before registration and lookup.
     *
     * @return meter builder customizers
     */
    @Option.Singular
    List<MeterBuilderCustomizer> meterBuilderCustomizers();

    /**
     * Listeners notified when this factory creates its shared registry.
     *
     * @return registry lifecycle listeners
     */
    @Option.Singular
    List<MeterRegistryLifeCycleListener> meterRegistryLifeCycleListeners();
}
