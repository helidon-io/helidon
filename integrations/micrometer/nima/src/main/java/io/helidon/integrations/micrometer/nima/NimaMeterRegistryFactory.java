/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.integrations.micrometer.nima;

import java.util.Optional;
import java.util.function.Function;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.config.ConfigValue;
import io.helidon.integrations.micrometer.BuiltInRegistryType;
import io.helidon.integrations.micrometer.MeterRegistryFactory;
import io.helidon.integrations.micrometer.MicrometerPrometheusRegistrySupport;
import io.helidon.nima.webserver.http.Handler;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterRegistryConfig;

/**
 * Nima implementation of {@link MeterRegistryFactory}
 *
 */
public final class NimaMeterRegistryFactory extends MeterRegistryFactory<ServerRequest, ServerResponse, Handler> {

    private static NimaMeterRegistryFactory instance = create();

    /**
     * Creates a new factory using default settings (no config).
     *
     * @return initialized MeterRegistryFactory
     */
    public static NimaMeterRegistryFactory create() {
        return create(Config.empty());
    }

    /**
     * Creates a new factory using the specified config.
     *
     * @param config the config to use in initializing the factory
     * @return initialized MeterRegistryFactory
     */
    public static NimaMeterRegistryFactory create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Returns the singleton instance of the factory.
     *
     * @return factory singleton
     */
    public static NimaMeterRegistryFactory getInstance() {
        return instance;
    }

    /**
     * Creates and saves as the singleton a new factory.
     *
     * @param builder the Builder to use in constructing the new singleton instance
     *
     * @return NimaMeterRegistryFactory using the Builder
     */
    public static NimaMeterRegistryFactory getInstance(Builder builder) {
        instance = builder.build();
        return instance;
    }

    /**
     * Returns a new {@code Builder} for constructing a {@code MeterRegistryFactory}.
     *
     * @return initialized builder
     */
    public static Builder builder() {
        return new Builder();
    }

    private NimaMeterRegistryFactory(Builder builder) {
        super(builder);
    }

    @Override
    protected Handler notMatch(ServerRequest request, ServerResponse response) {
        return (req, res) -> res
                .status(Http.Status.NOT_ACCEPTABLE_406)
                .send(NO_MATCHING_REGISTRY_ERROR_MESSAGE);
    }

    /**
     * Builder for constructing {@code MeterRegistryFactory} instances.
     */
    public static class Builder extends MeterRegistryFactory.Builder<ServerRequest, ServerResponse, Handler> {

        private Builder() {
        }

        @Override
        public NimaMeterRegistryFactory build() {
            return new NimaMeterRegistryFactory(this);
        }

        @Override
        public Builder config(Config config) {
            return (Builder) super.config(config);
        }

        @Override
        public Builder enrollBuiltInRegistry(
                BuiltInRegistryType builtInRegistryType, MeterRegistryConfig meterRegistryConfig) {
            return (Builder) super.enrollBuiltInRegistry(builtInRegistryType, meterRegistryConfig);
        }

        @Override
        public Builder enrollBuiltInRegistry(
                BuiltInRegistryType builtInRegistryType) {
            return (Builder) super.enrollBuiltInRegistry(builtInRegistryType);
        }

        @Override
        public Builder enrollRegistry(MeterRegistry meterRegistry,
                Function<ServerRequest, Optional<Handler>> handlerFunction) {
            return (Builder) super.enrollRegistry(meterRegistry, handlerFunction);
        }

        @Override
        protected MicrometerPrometheusRegistrySupport<ServerRequest, Handler> createPrometheus(
                BuiltInRegistryType builtInRegistryType, Optional<MeterRegistryConfig> meterRegistryConfig) {
            if (meterRegistryConfig.isPresent()) {
                return NimaMicrometerPrometheusRegistrySupport.create(builtInRegistryType, meterRegistryConfig.get());
            } else {
                return NimaMicrometerPrometheusRegistrySupport.create(builtInRegistryType);
            }
        }

        @Override
        protected MicrometerPrometheusRegistrySupport<ServerRequest, Handler> create(BuiltInRegistryType type,
                ConfigValue<Config> node) {
            return NimaMicrometerPrometheusRegistrySupport.create(type, node);
        }
    }

}
