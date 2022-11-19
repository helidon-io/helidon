/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.openapi;

import java.util.Map;
import java.util.function.Function;

import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

/**
 * Behavior for OpenAPI U/I implementations.
 */
public interface OpenApiUi extends Service {

    /**
     * Default subcontext within the {@link OpenAPISupport} instance's web context
     * (which itself defaults to {@value OpenAPISupport#DEFAULT_WEB_CONTEXT}.
     */
    String DEFAULT_UI_WEB_SUBCONTEXT = "/ui";

    /**
     * Creates a builder for a new {@code OpenApiUi} instance.
     *
     * @return new builder
     */
    static Builder builder() {
        return OpenApiUiBase.builder();
    }

    /**
     * Gives the U/I an opportunity to respond to a request arriving at the {@code OpenAPISupport} endpoint for which the
     * best-accepted {@link MediaType} was {@code text/html}.
     *
     * @param request the request for HTML content
     * @param response the response which could be prepared and sent
     */
    void prepareTextResponseFromMainEndpoint(ServerRequest request, ServerResponse response);

    /**
     * Builder for an {@code OpenApiUi}.
     */
    @Configured(prefix = Builder.OPENAPI_UI_CONFIG_PREFIX)
    interface Builder {

        /**
         * Config prefix within the {@value OpenAPISupport.Builder#CONFIG_KEY} section containing U/I settings.
         */
        String OPENAPI_UI_CONFIG_PREFIX = "ui";

        /**
         * Config key for the {@code enabled} setting.
         */
        String ENABLED_CONFIG_KEY = "enabled";

        /**
         * Config key for implementation-dependent {@code options} settings.
         */
        String OPTIONS_CONFIG_KEY = "options";

        /**
         * Config key for specifying the entire web context where the U/I responds.
         */
        String WEB_CONTEXT_CONFIG_KEY = "web-context";

        /**
         * Sets implementation-specific U/I options.
         *
         * @param options the options to set for the U/I
         * @return updated builder
         */
        @ConfiguredOption(kind = ConfiguredOption.Kind.MAP)
        Builder options(Map<String, String> options);

        /**
         * Sets whether the U/I should be enabled.
         *
         * @param isEnabled true/false
         * @return updated builder
         */
        @ConfiguredOption(value = "true")
        Builder isEnabled(boolean isEnabled);

        /**
         * Sets the entire web context (not just the suffix) where the U/I response.
         *
         * @param webContext entire web context (path) where the U/I responds
         * @return updated builder
         */
        @ConfiguredOption(description = "web context (path) where the U/I will respond")
        Builder webContext(String webContext);

        /**
         * Updates the builder using the specified config node at {@value OPENAPI_UI_CONFIG_PREFIX} within the
         * {@value OpenAPISupport.Builder#CONFIG_KEY} config section.
         *
         * @param uiConfig config node containing the U/I settings
         * @return updated builder
         */
        default Builder config(Config uiConfig) {
            uiConfig.get(ENABLED_CONFIG_KEY).asBoolean().ifPresent(this::isEnabled);
            uiConfig.get(WEB_CONTEXT_CONFIG_KEY).asString().ifPresent(this::webContext);
            uiConfig.get(OPTIONS_CONFIG_KEY).asMap().ifPresent(this::options);
            return this;
        }

        /**
         * Creates a new {@link OpenApiUi} from the builder.
         *
         * @param documentPreparer function which converts a {@link MediaType} into the corresponding expression of the OpenAPI
         *                        document
         * @param openAPIWebContext web context for the OpenAPI instance
         * @return new {@code OpenApiUi}
         */
        OpenApiUi build(Function<MediaType, String> documentPreparer, String openAPIWebContext);
    }
}
