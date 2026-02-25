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

package io.helidon.metrics.providers.micrometer;

import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.providers.micrometer.spi.ConfiguredMeterRegistryProvider;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Micrometer-specific metrics configuration.
 */
@Prototype.Configured
@Prototype.Blueprint(decorator = ConfigSupport.MicrometerMetricsConfigSupport.BuilderDecorator.class)
interface MicrometerMetricsConfigBlueprint extends MetricsConfig {

    /**
     * Settings for configured Micrometer meter registries.
     *
     * @return settings for configured meter registries
     */
    @Option.Configured
    @Option.Access("")
    @Option.Provider(value = ConfiguredMeterRegistryProvider.class, discoverServices = false)
    List<ConfiguredMeterRegistry> registries();

    /**
     * Micrometer meter registries to use.
     *
     * @return Micrometer meter registries
     */
    @Option.Singular
    List<MeterRegistry> meterRegistries();

}
