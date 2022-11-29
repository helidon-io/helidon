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
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.openapi.OpenApiUi;
import io.helidon.openapi.OpenApiUiBase;
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
class OpenApiUiFull extends OpenApiUiBase {

    /**
     *
     * @return new builder for an {@code OpenApiUiFull} service
     */
    static OpenApiUiFull.Builder builder() {
        return new Builder();
    }

    private static final String LOGO_RESOURCE = "logo.svg";
    private static final String HELIDON_IO_LINK = "https://helidon.io";

    private static final Logger LOGGER = Logger.getLogger(OpenApiUiFull.class.getName());

    private static final MediaType[] SUPPORTED_TEXT_MEDIA_TYPES_AT_UI_ENDPOINT = new MediaType[] {
            MediaType.TEXT_HTML,
            MediaType.TEXT_PLAIN,
            MediaType.TEXT_YAML
    };

    private static final MediaType[] SUPPORTED_TEXT_MEDIA_TYPES_AT_OPENAPI_ENDPOINT = new MediaType[] {
            MediaType.TEXT_HTML,
            MediaType.TEXT_PLAIN
    };

    private byte[] indexHtml;
    private final ReentrantLock indexHtmlAccess = new ReentrantLock();

    private final Map<Option, String> options;

    private OpenApiUiFull(Builder builder) {
        super(builder, builder.documentPreparer(), builder.openApiSupportWebContext());
        options = new HashMap<>(builder.options);

        // Apply some Helidon-specific defaults.
        Map.of(Option.title, "Helidon OpenAPI U/I",
               Option.logoHref, LOGO_RESOURCE,
               Option.oauth2RedirectUrl, "-", // workaround for a bug in IndexHtmlCreator
               Option.backHref, HELIDON_IO_LINK, // link applied to the rendered logo image
               Option.selfHref, HELIDON_IO_LINK) // link applied to the title if there is no logo (but there is; set this anyway)

                .forEach((key, value) -> {
                    if (!options.containsKey(key)) { // Do not override values the developer provided.
                        options.put(key, value);
                    }
                });
    }

    @Override
    public MediaType[] supportedMediaTypes() {
        return SUPPORTED_TEXT_MEDIA_TYPES_AT_OPENAPI_ENDPOINT;
    }

    @Override
    public boolean prepareTextResponseFromMainEndpoint(ServerRequest request, ServerResponse response) {
        // The full impl adds HTML support at the main /openapi endpoint.
        return isEnabled()
                && prepareTextResponse(request, response, SUPPORTED_TEXT_MEDIA_TYPES_AT_OPENAPI_ENDPOINT);
    }

    @Override
    public void update(Routing.Rules rules) {
        if (!isEnabled()) {
            return;
        }
        // Serve static content from the external U/I component...
        StaticContentSupport smallryeUiStaticSupport = StaticContentSupport.builder("META-INF/resources/openapi-ui")
                .build();
        // ...and from here.
        StaticContentSupport helidonOpenApiUiStaticSupport = StaticContentSupport.builder("helidon-openapi-ui")
                .build();
        rules
                .get(webContext() + "[/]", this::prepareTextResponseFromUiEndpoint)
                .get(webContext() + "/index.html", this::displayIndex)
                .register(webContext(), helidonOpenApiUiStaticSupport)
                .register(webContext(), smallryeUiStaticSupport);
    }

    /**
     * Builder for the {@code OpenApiUiFull}.
     */
    public static class Builder extends OpenApiUiBase.Builder<Builder, OpenApiUiFull> {

        private Map<Option, String> options = new HashMap<>();


        private Builder() {
            super();
        }

        @Override
        public OpenApiUiFull build() {
            if (options.containsKey(Option.url)) {
                LOGGER.log(Level.WARNING,
                           """
                                   Unexpected setting for the OpenAPI URL; \
                                   overriding the options value of 'url' ({1}) with \
                                   the actual endpoint of the Helidon OpenAPI service ({0})
                                   """,
                           new Object[] {
                                   openApiSupportWebContext() + OpenApiUi.UI_WEB_SUBCONTEXT,
                                   options.get(Option.url)}
                           );
            }
            return new OpenApiUiFull(this);
        }

        /**
         * Sets the options map the U/I should use. Other settings previously assigned will be respected unless the provided map
         * sets the corresponding value.
         *
         * @param options U/I options map
         * @return updated builder
         */
        @Override
        public Builder options(Map<String, String> options) {
            this.options = convertOptions(options);
            return this;
        }

        /**
         * Assigns the settings using the provided OpenAPI U/I {@code Config} node.
         *
         * @param uiConfig OpenAPI U/I config node
         * @return updated builder
         */
        @Override
        public Builder config(Config uiConfig) {
            super.config(uiConfig);
            applyConfigToOptions(uiConfig.get(OPTIONS_CONFIG_KEY));
            return this;
        }

        // For testing.
        Map<Option, String> uiOptions() {
            return options;
        }

        private Map<Option, String> convertOptions(Map<String, String> options) {
            Map<Option, String> result = new HashMap<>();
            List<String> unrecognizedKeys = new ArrayList<>();

            nextKey:
            for (Map.Entry<String, String> entry : options.entrySet()) {
                for (Option opt : Option.values()) {
                    if (opt.name().equals(entry.getKey())) {
                        result.put(opt, entry.getValue());
                        break nextKey;
                    }
                }
                unrecognizedKeys.add(entry.getKey());
            }
            if (!unrecognizedKeys.isEmpty()) {
                LOGGER.log(Level.WARNING,
                           "Helidon OpenAPI U/I builder found (and will ignore) unrecognized option names: \"{0}\"",
                           unrecognizedKeys);
            }
            return result;
        }

        private void applyConfigToOptions(Config optionsConfig) {
            if (!optionsConfig.exists() || optionsConfig.isLeaf()) {
                return;
            }
            optionsConfig.detach()
                    .asMap()
                    .map(this::convertOptions)
                    .ifPresent(options::putAll);
        }
    }

    private void displayIndex(ServerRequest request, ServerResponse response) {
        if (!acceptsHtml(request)) {
            request.next();
            return;
        }
        try {
            response.addHeader(Http.Header.CONTENT_TYPE, MediaType.TEXT_HTML.toString())
                    .send(indexHtml(request));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error generating index.html", e);
            response.status(Http.Status.INTERNAL_SERVER_ERROR_500)
                    .send("Error generating index.html");
        }
    }

    private void prepareTextResponseFromUiEndpoint(ServerRequest request, ServerResponse response) {
        if (!prepareTextResponse(request, response, SUPPORTED_TEXT_MEDIA_TYPES_AT_UI_ENDPOINT)) {
            request.next();
        }
    }

    private boolean prepareTextResponse(ServerRequest request, ServerResponse response, MediaType[] mediaTypes) {
        return request.headers()
                .bestAccepted(mediaTypes)
                .map(mediaType -> {
                                     if (MediaType.TEXT_HTML.test(mediaType)) {
                                         // Redirect to the index.html temporarily because other requests to the U/I endpoint
                                         // might specify other media types.
                                         redirectToIndexTemp(request, response);
                                     } else {
                                         sendStaticText(request, response, mediaType);
                                     }
                                     return true;
                                 })
                .orElse(false);
    }

    private void redirectToIndexTemp(ServerRequest request, ServerResponse response) {
        response.status(Http.Status.TEMPORARY_REDIRECT_307);
        response.addHeader(Http.Header.LOCATION, webContext() + "/index.html");
        response.send();
    }

    private byte[] indexHtml(ServerRequest request) throws IOException {
        indexHtmlAccess.lock();
        try {
            if (indexHtml == null) {
                options.put(Option.url, chooseOpenApiUrl(request));
                LOGGER.log(Level.FINE,
                           "Generated index.html to fetch OpenAPI document from {0}",
                           options.get(Option.url));
                indexHtml = IndexHtmlCreator.createIndexHtml(options);
            }
            return indexHtml;
        } finally {
            indexHtmlAccess.unlock();
        }
    }

    private String chooseOpenApiUrl(ServerRequest request) {
        URI uri = request.absoluteUri();
        return String.format("%s://%s%s",
                             uri.getScheme(),
                             request.headers().first(Http.Header.HOST).orElse("missing-host"),
                             webContext());
    }

    private boolean acceptsHtml(ServerRequest request) {
        return request.headers()
                .bestAccepted(SUPPORTED_TEXT_MEDIA_TYPES_AT_UI_ENDPOINT)
                .map(candidate -> candidate.test(MediaType.TEXT_HTML))
                .orElse(false);
    }
}
