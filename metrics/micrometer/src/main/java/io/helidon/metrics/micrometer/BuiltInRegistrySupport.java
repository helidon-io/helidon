/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.metrics.micrometer;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.config.Config;
import io.helidon.config.ConfigValue;
import io.helidon.webserver.Handler;
import io.helidon.webserver.ServerRequest;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterRegistryConfig;

/**
 * Framework for supporting Micrometer registry types.
 */
abstract class BuiltInRegistrySupport {

    abstract static class AbstractMeterRegistryConfig implements MeterRegistryConfig {
        private final Map<String, String> settings;

        AbstractMeterRegistryConfig(Config config) {
            settings = config
                    .detach()
                    .asMap()
                    .orElse(Collections.emptyMap());
        }

        @Override
        public String get(String key) {
            return settings.get(key);
        }
    }

    static BuiltInRegistrySupport create(MicrometerSupport.BuiltInRegistryType type,
            ConfigValue<Config> node) {
        switch (type) {
            case PROMETHEUS:
                return create(type, PrometheusRegistrySupport.PrometheusConfigImpl.registryConfig(node));

            default:
                throw new IllegalArgumentException(unrecognizedMessage(type));
        }
    }

    static BuiltInRegistrySupport create(MicrometerSupport.BuiltInRegistryType type,
            MeterRegistryConfig meterRegistryConfig) {
        switch (type) {
            case PROMETHEUS:
                return new PrometheusRegistrySupport(meterRegistryConfig);

            default:
                throw new IllegalArgumentException(unrecognizedMessage(type));
        }
    }

    private final MeterRegistry registry;

    BuiltInRegistrySupport(MeterRegistryConfig meterRegistryConfig) {
        registry = createRegistry(meterRegistryConfig);
    }

    abstract MeterRegistry createRegistry(MeterRegistryConfig meterRegistryConfig);

    abstract Function<ServerRequest, Optional<Handler>> requestToHandlerFn(MeterRegistry registry);

    MeterRegistry registry() {
        return registry;
    }

    private static String unrecognizedMessage(MicrometerSupport.BuiltInRegistryType type) {
        return String.format("Built-in registry type %s recognized but no support found", type.name());
    }
}
