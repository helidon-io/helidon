/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.integrations.micrometer;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.common.config.Config;
import io.helidon.common.config.ConfigValue;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterRegistryConfig;
import io.micrometer.prometheus.PrometheusConfig;

/**
 * Framework for supporting Micrometer registry types.
 */
abstract class MicrometerBuiltInRegistrySupport {
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

    static MicrometerBuiltInRegistrySupport create(MeterRegistryFactory.BuiltInRegistryType type,
            ConfigValue<Config> node) {
        switch (type) {
            case PROMETHEUS:
                return create(type, MicrometerPrometheusRegistrySupport.PrometheusConfigImpl.registryConfig(node));

            default:
                throw new IllegalArgumentException(unrecognizedMessage(type));
        }
    }

    static MicrometerBuiltInRegistrySupport create(MeterRegistryFactory.BuiltInRegistryType type,
            MeterRegistryConfig meterRegistryConfig) {
        switch (type) {
            case PROMETHEUS:
                return new MicrometerPrometheusRegistrySupport(meterRegistryConfig);

            default:
                throw new IllegalArgumentException(unrecognizedMessage(type));
        }
    }

    static MicrometerBuiltInRegistrySupport create(MeterRegistryFactory.BuiltInRegistryType type) {
        MeterRegistryConfig meterRegistryConfig;
        switch (type) {
            case PROMETHEUS:
                meterRegistryConfig = PrometheusConfig.DEFAULT;
                break;

            default:
                throw new IllegalArgumentException(unrecognizedMessage(type));
        }
        return create(type, meterRegistryConfig);
    }

    private final MeterRegistry registry;

    MicrometerBuiltInRegistrySupport(MeterRegistryConfig meterRegistryConfig) {
        registry = createRegistry(meterRegistryConfig);
    }

    abstract MeterRegistry createRegistry(MeterRegistryConfig meterRegistryConfig);

    abstract Function<io.helidon.nima.webserver.http.ServerRequest,
            Optional<io.helidon.nima.webserver.http.Handler>> requestToHandlerFn(
            MeterRegistry meterRegistry);


    MeterRegistry registry() {
        return registry;
    }

    private static String unrecognizedMessage(MeterRegistryFactory.BuiltInRegistryType type) {
        return String.format("Built-in registry type %s recognized but no support found", type.name());
    }
}
