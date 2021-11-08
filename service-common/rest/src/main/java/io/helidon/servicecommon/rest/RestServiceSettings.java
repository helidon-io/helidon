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
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.webserver.cors.CorsEnabledServiceHelper;
import io.helidon.webserver.cors.CrossOriginConfig;

/**
 * Common settings across REST services.
 */
public interface RestServiceSettings {

    /**
     * Creates a new instance with default settings.
     *
     * @return new defaulted settings
     */
    static RestServiceSettings create() {
        return builder().build();
    }

    /**
     * Creates a new instance using values from the provided config.
     *
     * @param config {@code Config} node possibly containing REST service settings
     * @return new initialized settings
     */
    static RestServiceSettings create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Creates a builder to construct a new instance.
     *
     * @return new builder with defaulted settings
     */
    static Builder builder() {
        return RestServiceSettingsImpl.Builder.create();
    }

    /**
     * Returns the web context at which the service's endpoint can be accessed.
     *
     * @return web context for the endpoint
     */
    String webContext();

    /**
     * Returns the routing name to be used for the service's endpoint.
     *
     * @return routing name
     */
    String routing();

    /**
     * Returns the cross-origin config settings to be used for the service's endpoint.
     *
     * @return cross-origin settings
     */
    CrossOriginConfig crossOriginConfig();

    /**
     * Builder for {@link io.helidon.servicecommon.rest.RestServiceSettings}.
     */
    @Configured()
    interface Builder extends io.helidon.common.Builder<RestServiceSettings> {

        /**
         * Config key for the routing name setting.
         */
        String ROUTING_NAME_CONFIG_KEY = "routing";

        /**
         * Config key for the web context setting.
         */
        String WEB_CONTEXT_CONFIG_KEY = "web-context";

        /**
         * Sets the web context to use for the service's endpoint.
         *
         * @param webContext web context
         * @return updated builder
         */
        @ConfiguredOption(key = WEB_CONTEXT_CONFIG_KEY,
                          mergeWithParent = true)
        Builder webContext(String webContext);

        /**
         * Sets the routing name to use for setting up the service's endpoint.
         *
         * @param routing routing name as defined in the server settings
         * @return updated builder
         */
        @ConfiguredOption(key = ROUTING_NAME_CONFIG_KEY,
                          mergeWithParent = true)
        Builder routing(String routing);

        /**
         * Sets the cross-origin config builder for use in establishing CORS support for the service endpoints.
         *
         * @param crossOriginConfigBuilder builder for the CORS settings
         * @return updated builder
         */
        @ConfiguredOption(key = CorsEnabledServiceHelper.CORS_CONFIG_KEY,
                          kind = ConfiguredOption.Kind.MAP)
        Builder crossOriginConfig(CrossOriginConfig.Builder crossOriginConfigBuilder);

        /**
         * Sets the cross-origin settings from existing settings (not from a builder).
         *
         * @param crossOriginConfig existing cross-origin settings
         * @return updated builder
         */
        Builder crossOriginConfig(CrossOriginConfig crossOriginConfig);

        /**
         * Updates settings using the provided {@link io.helidon.config.Config} node for the service of interest.
         *
         * @param serviceConfig config node for the service
         * @return updated builder
         */
        Builder config(Config serviceConfig);

        /**
         * Creates the {@code RestServiceSettings} instance from the builder settings.
         *
         * @return new {@code RestServiceSettings}
         */
        RestServiceSettings build();
    }
}
