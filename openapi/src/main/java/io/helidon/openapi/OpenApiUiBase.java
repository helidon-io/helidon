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

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Function;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.uri.UriQuery;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

/**
 * Common base class for implementations of @link OpenApiUi}.
 */
public abstract class OpenApiUiBase implements OpenApiUi {

    private static final System.Logger LOGGER = System.getLogger(OpenApiUiBase.class.getName());

    private static final LazyValue<OpenApiUiFactory<?, ?>> UI_FACTORY = LazyValue.create(OpenApiUiBase::loadUiFactory);

    private static final String HTML_PREFIX = """
            <!doctype html>
            <html lang="en-US">
                <head>
                    <meta charset="utf-8"/>
                    <title>OpenAPI Document</title>
                </head>
                <body>
                    <pre>
            """;
    private static final String HTML_SUFFIX = """
                    </pre>
                </body>
            </html>
            """;
    private final Map<MediaType, String> preparedDocuments = new HashMap<>();

    /**
     * Returns a builder for the UI.
     *
     * @return a builder for the currently-available implementation of {@link io.helidon.openapi.OpenApiUi}.
     */
    static OpenApiUi.Builder<?, ?>  builder() {
        return UI_FACTORY.get().builder();
    }

    private final boolean isEnabled;
    private final Function<MediaType, String> documentPreparer;
    private final String webContext;
    private final Map<String, String> options = new HashMap<>();

    /**
     * Creates a new UI implementation from the specified builder and document preparer.
     *
     * @param builder the builder containing relevant settings
     * @param documentPreparer function returning an OpenAPI document represented as a specified {@link MediaType}
     * @param openAPIWebContext final web context for the {@code OpenAPISupport} service
     */
    protected OpenApiUiBase(Builder<?, ?> builder, Function<MediaType, String> documentPreparer, String openAPIWebContext) {
        Objects.requireNonNull(builder.documentPreparer, "Builder's documentPreparer must be non-null");
        Objects.requireNonNull(builder.openApiSupportWebContext,
                               "Builder's OpenAPISupport web context must be non-null");
        this.documentPreparer = documentPreparer;
        isEnabled = builder.isEnabled;
        webContext = Objects.requireNonNullElse(builder.webContext,
                                                openAPIWebContext + OpenApiUi.UI_WEB_SUBCONTEXT);
        options.putAll(builder.options);
    }

    /**
     * Returns whether the UI is enabled.
     *
     * @return whether the UI is enabled
     */
    protected boolean isEnabled() {
        return isEnabled;
    }

    /**
     * Prepares a representation of the OpenAPI document in the specified media type.
     *
     * @param mediaType media type in which to express the document
     * @return representation of the OpenAPI document
     */
    protected String prepareDocument(MediaType mediaType) {
        return documentPreparer.apply(mediaType);
    }

    /**
     * Returns the web context for the UI.
     *
     * @return web context this UI implementation responds at
     */
    protected String webContext() {
        return webContext;
    }

    /**
     * Returns the options set for the UI.
     *
     * @return options set for this UI implementation (unmodifiable)
     */
    protected Map<String, String> options() {
        return Collections.unmodifiableMap(options);
    }

    /**
     * Sends a static text response of the given media type.
     *
     * @param request the request to respond to
     * @param response the response
     * @param mediaType the {@code MediaType} with which to respond, if possible
     * @return whether the implementation responded with a static text response
     */
    protected boolean sendStaticText(ServerRequest request, ServerResponse response, HttpMediaType mediaType) {
        try {
            response
                    .header(Http.HeaderNames.CONTENT_TYPE, mediaType.toString())
                    .send(prepareDocument(request.query(), mediaType));
        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.WARNING, "Error formatting OpenAPI output as " + mediaType, e);
            response.status(Http.Status.INTERNAL_SERVER_ERROR_500)
                    .send("Error formatting OpenAPI output. See server log.");
        }
        return true;
    }

    private static OpenApiUiFactory<?, ?> loadUiFactory() {
        return HelidonServiceLoader.builder(ServiceLoader.load(OpenApiUiFactory.class))
                .addService(OpenApiUiNoOpFactory.create(), Integer.MAX_VALUE)
                .build()
                .iterator()
                .next();
    }

    private String prepareDocument(UriQuery queryParameters, HttpMediaType mediaType) throws IOException {
        String result = null;
        if (preparedDocuments.containsKey(mediaType)) {
            return preparedDocuments.get(mediaType);
        }
        MediaType resultMediaType = queryParameters
                .first(OpenApiFeature.OPENAPI_ENDPOINT_FORMAT_QUERY_PARAMETER)
                .map(OpenApiFeature.QueryParameterRequestedFormat::chooseFormat)
                .map(OpenApiFeature.QueryParameterRequestedFormat::mediaType)
                .orElse(mediaType);

        result = prepareDocument(resultMediaType);
        if (mediaType.test(MediaTypes.TEXT_HTML)) {
            result = embedInHtml(result);
        }
        preparedDocuments.put(resultMediaType, result);
        return result;
    }

    private String embedInHtml(String text) {
        return HTML_PREFIX + text + HTML_SUFFIX;
    }

    /**
     * Common base builder implementation for creating a new {@code OpenApiUi}.
     *
     * @param <T> type of the {@code OpenApiUiBase} to be built
     * @param <B> type of the builder for T
     */
    public abstract static class Builder<B extends Builder<B, T>, T extends OpenApiUi> implements OpenApiUi.Builder<B, T> {

        private final Map<String, String> options = new HashMap<>();
        private boolean isEnabled = true;
        private String webContext;
        private Function<MediaType, String> documentPreparer;
        private String openApiSupportWebContext;

        /**
         * Creates a new instance.
         */
        protected Builder() {
        }
        @Override
        public B options(Map<String, String> options) {
            this.options.putAll(options);
            return identity();
        }

        @Override
        public B isEnabled(boolean isEnabled) {
            this.isEnabled = isEnabled;
            return identity();
        }

        @Override
        public B webContext(String webContext) {
            this.webContext = webContext;
            return identity();
        }

        @Override
        public B documentPreparer(Function<MediaType, String> documentPreparer) {
            this.documentPreparer = documentPreparer;
            return identity();
        }

        @Override
        public B openApiSupportWebContext(String openApiWebContext) {
            this.openApiSupportWebContext = openApiWebContext;
            return identity();
        }

        /**
         * Returns the web context for OpenAPI support.
         *
         * @return OpenAPI web context
         */
        public String openApiSupportWebContext() {
            return openApiSupportWebContext;
        }

        /**
         * Returns the document preparer for the UI.
         *
         * @return document preparer
         */
        public Function<MediaType, String> documentPreparer() {
            return documentPreparer;
        }

        /**
         * Returns options settings for the UI.
         *
         * @return options for the UI
         */
        protected Map<String, String> options() {
            return options;
        }
    }
}
