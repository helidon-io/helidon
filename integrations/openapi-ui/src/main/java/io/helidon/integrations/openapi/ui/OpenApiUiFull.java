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

package io.helidon.integrations.openapi.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.openapi.OpenApiUi;
import io.helidon.openapi.OpenApiUiBase;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.staticcontent.StaticContentSupport;

import io.smallrye.openapi.ui.IndexHtmlCreator;
import io.smallrye.openapi.ui.Option;

/**
 * Support for the OpenAPI UI component from SmallRye.
 * <p>
 *     This service supports Helidon configuration of the UI and furnishes Helidon-specific defaults for some settings.
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

    private final byte[] indexHtml;

    private OpenApiUiFull(Builder builder) {
        super(builder, builder.documentPreparer(), builder.openApiSupportWebContext());
        Map<Option, String> options = builder.uiOptions();

        // Apply some Helidon-specific defaults.
        Map.of(Option.title, "Helidon OpenAPI UI",
               Option.logoHref, LOGO_RESOURCE,
               Option.oauth2RedirectUrl, "-", // workaround for a bug in IndexHtmlCreator
               Option.backHref, HELIDON_IO_LINK, // link applied to the rendered logo image
               Option.selfHref, HELIDON_IO_LINK, // link applied to the title if there is no logo (but there is; set this anyway)
               Option.url, builder.openApiSupportWebContext()) // location of the OpenAPI document

                .forEach((key, value) -> {
                    if (!options.containsKey(key)) { // Do not override values the developer provided.
                        options.put(key, value);
                    }
                });
        try {
            indexHtml = IndexHtmlCreator.createIndexHtml(options);
        } catch (IOException e) {
            throw new RuntimeException("Unable to initialize the index.html content for the OpenAPI UI", e);
        }
    }

    @Override
    public MediaType[] supportedMediaTypes() {
        return SUPPORTED_TEXT_MEDIA_TYPES_AT_OPENAPI_ENDPOINT;
    }

    @Override
    public boolean prepareTextResponseFromMainEndpoint(ServerRequest request, ServerResponse response) {
        return request.headers()
                .bestAccepted(SUPPORTED_TEXT_MEDIA_TYPES_AT_OPENAPI_ENDPOINT)
                .map(mediaType -> {
                    if (!isEnabled()) {
                        request.next();
                        return true;
                    } else {
                        return prepareTextResponse(request, response, mediaType);
                    }
                })
                .orElse(false);
    }

    @Override
    public void update(Routing.Rules rules) {
        if (!isEnabled()) {
            return;
        }
        // Serve static content from the external UI component...
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

        private Builder() {
            super();
        }

        @Override
        public OpenApiUiFull build() {
            return new OpenApiUiFull(this);
        }

        /**
         * Converts the recorded options based on {@code String}s to ones based on {@link Option}s.
         *
         * @return {@code Option}-based map of UI options
         */
        Map<Option, String> uiOptions() {
            // Package-private for visibility from tests.
            if (options().containsKey(Option.url.name())) {
                LOGGER.log(Level.WARNING,
                           """
                                   Unexpected setting for the OpenAPI URL; \
                                   overriding the options value of 'url' ({1}) with \
                                   the actual endpoint of the Helidon OpenAPI service ({0})
                                   """,
                           new Object[] {
                                   openApiSupportWebContext() + OpenApiUi.UI_WEB_SUBCONTEXT,
                                   options().get(Option.url.name())}
                );
            }

            Map<Option, String> result = new HashMap<>();
            List<String> unrecognizedKeys = new ArrayList<>();

            for (Map.Entry<String, String> entry : options().entrySet()) {
                boolean matched = false;
                for (Option opt : Option.values()) {
                    if (opt.name().equals(entry.getKey())) {
                        result.put(opt, entry.getValue());
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    unrecognizedKeys.add(entry.getKey());
                }
            }
            if (!unrecognizedKeys.isEmpty()) {
                LOGGER.log(Level.WARNING,
                           "Helidon OpenAPI UI builder found (and will ignore) unrecognized option names: \"{0}\"",
                           unrecognizedKeys);
            }
            return result;
        }
    }

    private void displayIndex(ServerRequest request, ServerResponse response) {
        if (!acceptsHtml(request)) {
            request.next();
            return;
        }
        response.addHeader(Http.Header.CONTENT_TYPE, MediaType.TEXT_HTML.toString())
                .send(indexHtml);
    }

    private void prepareTextResponseFromUiEndpoint(ServerRequest request, ServerResponse response) {
        request.headers()
                .bestAccepted(SUPPORTED_TEXT_MEDIA_TYPES_AT_UI_ENDPOINT)
                .ifPresentOrElse(mediaType -> prepareTextResponse(request, response, mediaType),
                                 request::next);
    }

    private boolean prepareTextResponse(ServerRequest request, ServerResponse response, MediaType mediaType) {
        if (MediaType.TEXT_HTML.test(mediaType)) {
            redirectToIndex(request, response);
        } else {
            sendStaticText(request, response, mediaType);
        }
        return true;
    }

    private void redirectToIndex(ServerRequest request, ServerResponse response) {
        // Redirect to the index.html temporarily because other requests to the UI endpoint
        // might specify other media types.
        response.status(Http.Status.TEMPORARY_REDIRECT_307);
        response.addHeader(Http.Header.LOCATION, webContext() + "/index.html");
        response.send();
    }

    private boolean acceptsHtml(ServerRequest request) {
        return request.headers()
                .bestAccepted(SUPPORTED_TEXT_MEDIA_TYPES_AT_UI_ENDPOINT)
                .map(candidate -> candidate.test(MediaType.TEXT_HTML))
                .orElse(false);
    }
}
