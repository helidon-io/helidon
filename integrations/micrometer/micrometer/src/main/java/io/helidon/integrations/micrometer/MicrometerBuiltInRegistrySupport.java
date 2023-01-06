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
import java.util.function.Predicate;

import io.helidon.config.Config;
import io.helidon.config.ConfigValue;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterRegistryConfig;
import io.micrometer.prometheus.PrometheusConfig;

/**
 * Framework for supporting Micrometer registry types.
 *
 */
abstract class MicrometerBuiltInRegistrySupport<REQ, HAND> {

    /**
     * Creates an instance of MicrometerPrometheusRegistrySupport.
     * @param <REQ> The server request.
     * @param <HAND> The server handler.
     * @param handlerFilter The predicate to apply the handler.
     * @param handlerFn The instance of the handler.
     * @param type The built in registry type.
     * @param node The configuration value.
     * @return an instance of MicrometerPrometheusRegistrySupport.
     */
    public static <REQ, HAND> MicrometerPrometheusRegistrySupport<REQ, HAND> create(
            Predicate<REQ> handlerFilter, Function<MeterRegistry, HAND> handlerFn,
            BuiltInRegistryType type, ConfigValue<Config> node) {
        switch (type) {
            case PROMETHEUS:
                return create(handlerFilter, handlerFn, type,
                        MicrometerPrometheusRegistrySupport.PrometheusConfigImpl.registryConfig(node));
            default:
                throw new IllegalArgumentException(unrecognizedMessage(type));
        }
    }

    /**
     * Creates an instance of MicrometerPrometheusRegistrySupport.
     * @param <REQ> The server request.
     * @param <HAND> The server handler.
     * @param handlerFilter The predicate to apply the handler.
     * @param handlerFn The instance of the handler.
     * @param type The built in registry type.
     * @param meterRegistryConfig The meter registry config.
     * @return an instance of MicrometerPrometheusRegistrySupport.
     */
    public static <REQ, HAND> MicrometerPrometheusRegistrySupport<REQ, HAND> create(
            Predicate<REQ> handlerFilter, Function<MeterRegistry, HAND> handlerFn,
            BuiltInRegistryType type, MeterRegistryConfig meterRegistryConfig) {
        switch (type) {
            case PROMETHEUS:
                return new MicrometerPrometheusRegistrySupport<>(handlerFilter, handlerFn, meterRegistryConfig);
            default:
                throw new IllegalArgumentException(unrecognizedMessage(type));
        }
    }

    /**
     * Creates an instance of MicrometerPrometheusRegistrySupport.
     * @param <REQ> The server request.
     * @param <HAND> The server handler.
     * @param handlerFilter The predicate to apply the handler.
     * @param handlerFn The instance of the handler.
     * @param type The built in registry type.
     * @return an instance of MicrometerPrometheusRegistrySupport.
     */
    public static <REQ, HAND> MicrometerPrometheusRegistrySupport<REQ, HAND> create(
            Predicate<REQ> handlerFilter, Function<MeterRegistry, HAND> handlerFn, BuiltInRegistryType type) {
        MeterRegistryConfig meterRegistryConfig;
        switch (type) {
            case PROMETHEUS:
                meterRegistryConfig = PrometheusConfig.DEFAULT;
                break;

            default:
                throw new IllegalArgumentException(unrecognizedMessage(type));
        }
        return create(handlerFilter, handlerFn, type, meterRegistryConfig);
    }

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

    private final MeterRegistry registry;
    private final Predicate<REQ> handlerFilter;
    private final Function<MeterRegistry, HAND> handlerFn;

    protected MicrometerBuiltInRegistrySupport(Predicate<REQ> handlerFilter,
            Function<MeterRegistry, HAND> handlerFn, MeterRegistryConfig meterRegistryConfig) {
        this.handlerFilter = handlerFilter;
        this.handlerFn = handlerFn;
        this.registry = createRegistry(meterRegistryConfig);
    }

    protected abstract MeterRegistry createRegistry(MeterRegistryConfig meterRegistryConfig);

    Function<REQ, Optional<HAND>> requestToHandlerFn(MeterRegistry registry) {
        /*
         * Deal with a request if the MediaType is text/plain or the query parameter "type" specifies "prometheus".
         */
        return (REQ req) -> {
            if (handlerFilter().test(req)) {
                return Optional.of(handlerFn().apply(registry));
            } else {
                return Optional.empty();
            }
        };
    }


    public MeterRegistry registry() {
        return registry;
    }

    protected static String unrecognizedMessage(BuiltInRegistryType type) {
        return String.format("Built-in registry type %s recognized but no support found", type.name());
    }

    Predicate<REQ> handlerFilter() {
        return handlerFilter;
    }

    Function<MeterRegistry, HAND> handlerFn() {
        return handlerFn;
    }
}
