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
package io.helidon.webserver.servicecommon;

import java.util.Objects;

import io.helidon.config.Config;
import io.helidon.cors.CrossOriginConfig;

/**
 * Implementation of {@link RestServiceSettings}.
 */
class RestServiceSettingsImpl implements RestServiceSettings {

    private final String webContext;
    private final String routing;
    private final CrossOriginConfig crossOriginConfig;
    private final boolean enabled;

    private RestServiceSettingsImpl(Builder builder) {
        this.webContext = Objects.requireNonNull(builder.webContext, "webContext cannot be null");
        if (webContext.isBlank()) {
            throw new IllegalArgumentException("webContext cannot be blank");
        }
        this.routing = builder.routing;
        this.enabled = builder.enabled;
        this.crossOriginConfig = builder.crossOriginConfigBuilder.build();
    }

    @Override
    public String webContext() {
        return webContext;
    }

    @Override
    public String routing() {
        return routing;
    }

    @Override
    public CrossOriginConfig crossOriginConfig() {
        return crossOriginConfig;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    static class Builder implements RestServiceSettings.Builder {

        private String webContext;
        private String routing;
        private boolean enabled = true;
        private CrossOriginConfig.Builder crossOriginConfigBuilder = CrossOriginConfig.builder();

        static Builder create() {
            return new Builder();
        }

        @Override
        public RestServiceSettings.Builder webContext(String webContext) {
            this.webContext = webContext;
            return this;
        }

        @Override
        public RestServiceSettings.Builder routing(String routing) {
            this.routing = routing;
            return this;
        }

        @Override
        public RestServiceSettings.Builder crossOriginConfig(CrossOriginConfig.Builder crossOriginConfigBuilder) {
            this.crossOriginConfigBuilder = crossOriginConfigBuilder;
            return this;
        }

        @Override
        public RestServiceSettings.Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        @Override
        public RestServiceSettings.Builder crossOriginConfig(CrossOriginConfig crossOriginConfig) {
            crossOriginConfigBuilder = CrossOriginConfig.builder(crossOriginConfig);
            return this;
        }

        @Override
        public RestServiceSettings.Builder config(Config serviceConfig) {
            serviceConfig.get(WEB_CONTEXT_CONFIG_KEY)
                    .asString()
                    .ifPresent(this::webContext);
            serviceConfig.get(ROUTING_NAME_CONFIG_KEY)
                    .asString()
                    .ifPresent(this::routing);
            serviceConfig.get("enabled").asBoolean().ifPresent(this::enabled);
            return this;
        }

        @Override
        public RestServiceSettings build() {
            return new RestServiceSettingsImpl(this);
        }
    }
}
