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

package io.helidon.integrations.openapi.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.openapi.OpenAPISupport;
import io.helidon.servicecommon.rest.HelidonRestServiceSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.staticcontent.StaticContentSupport;

import io.smallrye.openapi.ui.IndexHtmlCreator;
import io.smallrye.openapi.ui.Option;

/**
 * Support for the OpenAPI U/I component from SmallRye.
 * <p>
 *     This service supports Helidon configuration of the U/I and furnishes Helidon-specific defaults for some settings.
 * </p>
 */
public class OpenApiUiSupport extends HelidonRestServiceSupport {

    /**
     * Config key for OpenAPI U/I settings.
     */
    public static final String OPENAPI_UI_SUBCONFIG_KEY = "ui";

    static final String OPTIONS_CONFIG_KEY = "options";
    static final String DEFAULT_UI_PREFIX = "/openapi-ui";
    private static final String SERVICE_NAME = "OpenAPI U/I";

    private static final String LOGO_RESOURCE = "logo.svg";
    private static final String HELIDON_IO_LINK = "https://helidon.io";

    private static final System.Logger LOGGER = System.getLogger(OpenApiUiSupport.class.getName());

    private final byte[] indexHtml;

    private final Map<Option, String> options;

    /**
     * Creates a new {@code OpenApiUiSupport.Builder} using the provided {@code OpenAPISupport} instance.
     *
     * @param openApiSupport {@code OpenAPISupport} instance from which to gather some needed information
     * @return a new builder for an {@code OpenApiUiSupport}
     */
    public static OpenApiUiSupport.Builder builder(OpenAPISupport openApiSupport) {
        return new Builder(openApiSupport);
    }

    /**
     * Creates a new {@code OpenApiUiSupport} using the specified {@code OpenAPISupport} instance and U/I configuration.
     *
     * @param openApiSupport {@code OpenAPISupport}
     * @param uiConfig OpenAPI U/I {@code Config} node
     * @return new service instance, set up according to the provided configuration
     */
    public static OpenApiUiSupport create(OpenAPISupport openApiSupport, Config uiConfig) {
        return builder(openApiSupport).config(uiConfig).build();
    }

    private OpenApiUiSupport(Builder builder) {
        super(null, builder, SERVICE_NAME); // Using System.Logger now so cannot pass LOGGER as a jul Logger.
        options = new HashMap<>(builder.options);

        // Apply some Helidon-specific defaults.
        Map.of(Option.title, "Helidon OpenAPI U/I",
               Option.logoHref, LOGO_RESOURCE,
               Option.oauth2RedirectUrl, "-", // workaround for a bug in IndexHtmlCreator
               Option.backHref, HELIDON_IO_LINK, // link applied to the rendered logo image
               Option.selfHref, HELIDON_IO_LINK) // link applied to the title if there is no logo (but there is; set this anyway)
                .forEach((key, value) -> {
                    if (!options.containsKey(key)) { // Do not override developer-provided values for the presets.
                        options.put(key, value);
                    }
                });
        String openApiWebContext = builder.openApiWebContext();
        options.put(Option.url, openApiWebContext);

        try {
            indexHtml = IndexHtmlCreator.createIndexHtml(options);
            LOGGER.log(System.Logger.Level.INFO,
                       "Starting OpenAPI U/I at {0}, fetching OpenAPI document from {1}",
                       context(), openApiWebContext);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * A service registers itself by updating the routine rules.
     * @param rules the routing rules.
     */
    @Override
    public void update(Routing.Rules rules) {
        // Serve static content from the external U/I component...
        StaticContentSupport uiStaticSupport = StaticContentSupport.builder("META-INF/resources/openapi-ui")
                        .build();
        // ...and from here.
        StaticContentSupport hereStaticSupport = StaticContentSupport.builder("helidon-openapi-ui")
                .build();
        rules
                .get(context(), this::displayIndex)
                .get(context() + "/", this::displayIndex)
                .get(context() + "/index.html", this::displayIndex)
                .register(context(), hereStaticSupport)
                .register(context(), uiStaticSupport);
    }

    @Override
    protected void postConfigureEndpoint(Routing.Rules defaultRules, Routing.Rules serviceEndpointRoutingRules) {
    }

    // For testing.
    Map<Option, String> options() {
        return options;
    }

    /**
     * Builder for the {@code OpenApiUiSupport}.
     */
    @Configured(prefix = Builder.OPENAPI_UI_CONFIG_KEY)
    public static class Builder extends HelidonRestServiceSupport.Builder<Builder, OpenApiUiSupport> {
        /**
         * Full configuration key for OpenAPI U/I settings.
         */
        public static final String OPENAPI_UI_CONFIG_KEY = OpenAPISupport.Builder.CONFIG_KEY + "." + OPENAPI_UI_SUBCONFIG_KEY;
        private final String openapiUrlFromOpenApi;
        private String openapiUrlFromOpenApiUiConfig;
        private Map<Option, String> options = new HashMap<>();

        private Builder(OpenAPISupport openAPISupport) {
            super(DEFAULT_UI_PREFIX);
            openapiUrlFromOpenApi = openAPISupport.webContext();
        }

        @Override
        public OpenApiUiSupport build() {
            return new OpenApiUiSupport(this);
        }

        /**
         * Sets the options map the U/I should use. Other settings previously assigned will be respected unless the provided map
         * sets the corresponding value.
         *
         * @param options U/I options map
         * @return updated builder
         */
        @ConfiguredOption(key = OPTIONS_CONFIG_KEY,
                          kind = ConfiguredOption.Kind.MAP,
                          description = "U/I options settings")
        public Builder options(Map<Option, String> options) {
            this.options = options;
            return identity();
        }

        /**
         * Assigns the settings using the provided OpenAPI U/I {@code Config} node.
         *
         * @param uiConfig OpenAPI U/I config node
         * @return updated builder
         */
        public Builder config(Config uiConfig) {
            super.config(uiConfig);
            uiConfig.get(Option.url.name()).asString().ifPresent(value -> openapiUrlFromOpenApiUiConfig = value);
            applyConfigToOptions(uiConfig.get(OPTIONS_CONFIG_KEY));
            return identity();
        }

        String openApiWebContext() {
            // Start out with the value from the OpenAPI instance.
            String result = openapiUrlFromOpenApi;

            // Check the config for openapi.ui.url config. Warn if the user configured a setting for the U/I to use that is
            // different from where OpenAPISupport will actually respond.
            if (openapiUrlFromOpenApiUiConfig != null) {
                result = openapiUrlFromOpenApiUiConfig;
                if (openapiUrlFromOpenApi != null
                        && !openapiUrlFromOpenApiUiConfig.equals(openapiUrlFromOpenApi)) {
                    LOGGER.log(System.Logger.Level.WARNING,
                               """
                                       Inconsistent configuration settings for the OpenAPI URL: \
                                       helidon.openapi.web-context is {0}; \
                                       helidon.openapi.ui.url is {1}; \
                                       using {1} \
                                       """);
                }
            }
            return result;
        }

        private void applyConfigToOptions(Config optionsConfig) {
            if (!optionsConfig.exists() || optionsConfig.isLeaf()) {
                return;
            }
            List<String> unrecognizedKeys = new ArrayList<>();
            Map<String, Option> opts = new HashMap<>();
            for (Option opt : Option.values()) {
                opts.put(opt.name(), opt);
            }
            optionsConfig.asNodeList().ifPresent(nodeList -> {
                nodeList.forEach(node -> {
                    Option matchingOption = opts.get(node.name());
                    if (matchingOption != null) {
                        node.asString().ifPresent(value -> options.put(matchingOption, value));
                    } else {
                        unrecognizedKeys.add(node.name());
                    }
                });
            });
            if (!unrecognizedKeys.isEmpty()) {
                LOGGER.log(System.Logger.Level.WARNING,
                           "Helidon OpenAPI U/I builder found unrecognized config keys in \"{0}\": {1}",
                           OPENAPI_UI_CONFIG_KEY + "." + OPTIONS_CONFIG_KEY,
                           unrecognizedKeys);
            }
        }
    }

    private void displayIndex(ServerRequest request, ServerResponse response) {
        response.send(indexHtml);
    }
}
