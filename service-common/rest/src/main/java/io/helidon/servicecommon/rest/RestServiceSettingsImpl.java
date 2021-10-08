/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.servicecommon.rest;

import io.helidon.config.Config;
import io.helidon.webserver.cors.CrossOriginConfig;

/**
 * Implementation of {@link io.helidon.servicecommon.rest.RestServiceSettings}.
 */
class RestServiceSettingsImpl implements RestServiceSettings {

    private final String webContext;
    private final String routing;
    private final CrossOriginConfig crossOriginConfig;

    private RestServiceSettingsImpl(Builder builder) {
        this.webContext = builder.webContext;
        this.routing = builder.routing;
        crossOriginConfig = builder.crossOriginConfigBuilder.build();
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

    static class Builder implements RestServiceSettings.Builder {

        static Builder create() {
            return new Builder();
        }

        private String webContext;
        private String routing;
        private CrossOriginConfig.Builder crossOriginConfigBuilder = CrossOriginConfig.builder();

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
        public RestServiceSettings.Builder config(Config serviceConfig) {
            serviceConfig.get(Builder.WEB_CONTEXT_CONFIG_KEY)
                    .asString()
                    .ifPresent(this::webContext);
            serviceConfig.get(Builder.ROUTING_NAME_CONFIG_KEY)
                    .asString()
                    .ifPresent(this::routing);
            return this;
        }

        @Override
        public RestServiceSettings.Builder crossOriginConfig(CrossOriginConfig.Builder crossOriginConfigBuilder) {
            this.crossOriginConfigBuilder = crossOriginConfigBuilder;
            return this;
        }

        @Override
        public RestServiceSettings build() {
            return new RestServiceSettingsImpl(this);
        }
    }
}
