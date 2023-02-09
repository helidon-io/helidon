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
 * Behavior for OpenAPI UI implementations.
 */
public interface OpenApiUi extends Service {

    /**
     * Default subcontext within the {@link OpenAPISupport} instance's web context
     * (which itself defaults to {@value OpenAPISupport#DEFAULT_WEB_CONTEXT}.
     */
    String UI_WEB_SUBCONTEXT = "/ui";

    /**
     * Creates a builder for a new {@code OpenApiUi} instance.
     *
     * @return new builder
     */
    static Builder<?, ?> builder() {
        return OpenApiUiBase.builder();
    }

    /**
     * Indicates the media types the UI implementation itself supports.
     *
     * @return the media types the
     * {@link #prepareTextResponseFromMainEndpoint(io.helidon.webserver.ServerRequest, io.helidon.webserver.ServerResponse)}
     * method responds to
     */
    MediaType[] supportedMediaTypes();

    /**
     * Gives the UI an opportunity to respond to a request arriving at the {@code OpenAPISupport} endpoint for which the
     * best-accepted {@link MediaType} was {@code text/html}.
     * <p>
     *     An implementation should return {@code true} if it is responsible for a particular media type
     *     whether it handled the request itself or delegated the request to the next handler.
     *     For example, even if the implementation is disabled it should still return {@code true} for the HTML media type.
     * </p>
     *
     * @param request the request for HTML content
     * @param response the response which could be prepared and sent
     * @return whether the UI did respond to the request
     */
    boolean prepareTextResponseFromMainEndpoint(ServerRequest request, ServerResponse response);

    /**
     * Builder for an {@code OpenApiUi}.
     *
     * @param <T> type of the {@code OpenApiUi} to be build
     * @param <B> type of the builder for T
     */
    @Configured(prefix = Builder.OPENAPI_UI_CONFIG_KEY)
    interface Builder<B extends Builder<B, T>, T extends OpenApiUi> extends io.helidon.common.Builder<B, T> {

        /**
         * Config prefix within the {@value OpenAPISupport.Builder#CONFIG_KEY} section containing UI settings.
         */
        String OPENAPI_UI_CONFIG_KEY = "ui";

        /**
         * Config key for the {@code enabled} setting.
         */
        String ENABLED_CONFIG_KEY = "enabled";

        /**
         * Config key for implementation-dependent {@code options} settings.
         */
        String OPTIONS_CONFIG_KEY = "options";

        /**
         * Config key for specifying the entire web context where the UI responds.
         */
        String WEB_CONTEXT_CONFIG_KEY = "web-context";

        /**
         * Merges implementation-specific UI options.
         *
         * @param options the options to for the UI to merge
         * @return updated builder
         */
        @ConfiguredOption(kind = ConfiguredOption.Kind.MAP)
        B options(Map<String, String> options);

        /**
         * Sets whether the UI should be enabled.
         *
         * @param isEnabled true/false
         * @return updated builder
         */
        @ConfiguredOption(key = "enabled", value = "true")
        B isEnabled(boolean isEnabled);

        /**
         * Sets the entire web context (not just the suffix) where the UI response.
         *
         * @param webContext entire web context (path) where the UI responds
         * @return updated builder
         */
        @ConfiguredOption(description = "web context (path) where the UI will respond")
        B webContext(String webContext);

        /**
         * Updates the builder using the specified config node at {@value OPENAPI_UI_CONFIG_KEY} within the
         * {@value OpenAPISupport.Builder#CONFIG_KEY} config section.
         *
         * @param uiConfig config node containing the UI settings
         * @return updated builder
         */
        default B config(Config uiConfig) {
            uiConfig.get(ENABLED_CONFIG_KEY).asBoolean().ifPresent(this::isEnabled);
            uiConfig.get(WEB_CONTEXT_CONFIG_KEY).asString().ifPresent(this::webContext);
            uiConfig.get(OPTIONS_CONFIG_KEY).detach().asMap().ifPresent(this::options);
            return identity();
        }

        /**
         *
         * @return correctly-typed self
         */
        @SuppressWarnings("unchecked")
        default B identity() {
            return (B) this;
        }

        /**
         * Assigns how the OpenAPI UI can obtain a formatted document for a given media type.
         * <p>
         *     Developers typically do not invoke this method. Helidon invokes it internally.
         * </p>
         *
         * @param documentPreparer the function for obtaining the formatted document
         * @return updated builder
         */
        B documentPreparer(Function<MediaType, String> documentPreparer);

        /**
         * Assigns the web context the {@code OpenAPISupport} instance uses.
         * <p>
         *     Developers typically do not invoke this method. Helidon invokes it internally.
         * </p>
         * @param openApiWebContext the web context used by the {@code OpenAPISupport} service
         * @return updated builder
         */
        B openApiSupportWebContext(String openApiWebContext);

        /**
         * Creates a new {@link OpenApiUi} from the builder.
         *
         * @param documentPreparer function which converts a {@link MediaType} into the corresponding expression of the OpenAPI
         *                        document
         * @param openAPIWebContext web context for the OpenAPI instance
         * @return new {@code OpenApiUi}
         */
        default OpenApiUi build(Function<MediaType, String> documentPreparer, String openAPIWebContext) {
            documentPreparer(documentPreparer);
            openApiSupportWebContext(openAPIWebContext);
            return build();
        }
    }
}
