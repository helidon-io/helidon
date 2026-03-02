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

import java.util.function.Supplier;

import io.helidon.common.config.Config;
import io.helidon.metrics.providers.micrometer.spi.ConfiguredMeterRegistryProvider;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.registry.otlp.OtlpMeterRegistry;

/**
 * Provider for configured OTLP meter registries.
 */
public class ConfiguredOtlpMeterRegistryProvider implements ConfiguredMeterRegistryProvider {

    /**
     * Type (and default name) to use for OTLP meter registry configurations.
     */
    static final String TYPE = "otlp";

    /**
     * For service loading.
     */
    public ConfiguredOtlpMeterRegistryProvider() {
    }

    @Override
    public String configKey() {
        return TYPE;
    }

    @Override
    public ConfiguredMeterRegistry create(Config config, String name) {
        var prototype = OtlpMeterRegistryConfig.builder()
                .config(config)
                .build();

        return new ConfiguredOtlpMeterRegistry(prototype.enabled(),
                                               TYPE,
                                               TYPE,
                                               () -> OtlpMeterRegistry.builder(prototype).build());
    }

    /**
     * The configured OTLP meter registry provided from config.
     *
     * @param isEnabled            if the configured meter registry is enabled
     * @param name                 name for the configured meter registry
     * @param type                 type for the configured meter registry
     * @param meterRegistrySupplier supplier for creating the Micrometer meter registry
     */
    record ConfiguredOtlpMeterRegistry(boolean isEnabled,
                                       String name,
                                       String type,
                                       Supplier<MeterRegistry> meterRegistrySupplier)
            implements ConfiguredMeterRegistry {

    }
}
