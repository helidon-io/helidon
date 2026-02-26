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

import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.common.config.Config;
import io.helidon.metrics.providers.micrometer.spi.ConfiguredMeterRegistryProvider;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exemplars.DefaultExemplarSampler;
import io.prometheus.client.exemplars.tracer.common.SpanContextSupplier;

/**
 * Provider for configured Prometheus meter registries.
 */
public class ConfiguredPrometheusMeterRegistryProvider implements ConfiguredMeterRegistryProvider {

    /**
     * Type (and default name) to use for Prometheus meter registry configurations.
     */
    static final String TYPE = "prometheus";

    @Override
    public String configKey() {
        return TYPE;
    }

    @Override
    public ConfiguredMeterRegistry create(Config config, String name) {
        var prototype = PrometheusMeterRegistryConfig.builder()
                .config(config)
                .build();

        return ConfiguredPrometheusMeterRegistry.create(prototype);
    }

    record ConfiguredPrometheusMeterRegistry(boolean isEnabled,
                                             String name,
                                             String type,
                                             Supplier<MeterRegistry> meterRegistrySupplier,
                                             Function<SpanContextSupplier, MeterRegistry> meterRegistryFunction)
            implements ConfiguredMeterRegistry {

        static ConfiguredPrometheusMeterRegistry create(PrometheusMeterRegistryConfig prototype) {
            return new ConfiguredPrometheusMeterRegistry(
                    prototype.enabled(),
                    TYPE,
                    TYPE,
                    () -> new PrometheusMeterRegistry(prototype),
                    spanContextSupplier -> new PrometheusMeterRegistry(prototype,
                                                                       new CollectorRegistry(),
                                                                       io.micrometer.core.instrument.Clock.SYSTEM,
                                                                       new DefaultExemplarSampler(
                                                                               spanContextSupplier)));
        }

        /**
         * Creates a configured registry from an existing, fully-formed registry.
         *
         * @param meterRegistry existing meter registry
         * @return configured registry for the fully-formed one
         */
        static ConfiguredPrometheusMeterRegistry create(MeterRegistry meterRegistry) {
            return new ConfiguredPrometheusMeterRegistry(true,
                                                         TYPE,
                                                         TYPE,
                                                         () -> meterRegistry,
                                                         spanContextSupplier -> meterRegistry);
        }

    }
}
