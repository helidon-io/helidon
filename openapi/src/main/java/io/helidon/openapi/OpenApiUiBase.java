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

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.LazyValue;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.http.Parameters;
import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

/**
 * Common base class for implementations of @link OpenApiUi}.
 */
public abstract class OpenApiUiBase implements OpenApiUi {

    private static final Logger LOGGER = Logger.getLogger(OpenApiUiBase.class.getName());

    private static final LazyValue<OpenApiUiFactory> UI_FACTORY = LazyValue.create(OpenApiUiBase::loadUiFactory);

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
     *
     * @param <T> type of the {@code OpenApiUiBase} to be built
     * @param <B> type of the builder for T
     * @return a builder for the currently-available implementation of {@link OpenApiUi}.
     */
    static <B extends OpenApiUi.Builder<B, T>, T extends OpenApiUi>  B builder() {
        return UI_FACTORY.get().builder();
    }

    private final boolean isEnabled;
    private final Function<MediaType, String> documentPreparer;
    private final String webContext;
    private final Map<String, String> options = new HashMap<>();

    /**
     * Creates a new U/I implementation from the specified builder and document preparer.
     *
     * @param builder the builder containing relevant settings
     * @param documentPreparer function returning an OpenAPI document represented as a specified {@link MediaType}
     * @param openAPIWebContext final web context for the {@code OpenAPISupport} service
     */
    protected OpenApiUiBase(Builder<?, ?> builder, Function<MediaType, String> documentPreparer, String openAPIWebContext) {
        this.documentPreparer = documentPreparer;
        isEnabled = builder.isEnabled;
        webContext = Objects.requireNonNullElse(builder.webContext,
                                                openAPIWebContext + OpenApiUi.UI_WEB_SUBCONTEXT);
        options.putAll(builder.options);
    }

    /**
     *
     * @return whether the U/I is enabled
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
     *
     * @return web context this U/I implementation responds at
     */
    protected String webContext() {
        return webContext;
    }

    /**
     *
     * @return options set for this U/I implementation (unmodifiable)
     */
    protected Map<String, String> options() {
        return Collections.unmodifiableMap(options);
    }

    /**
     * Indicates which media types the concrete implementation handles as static text--that is,
     * by replying with a simple document as formatted by
     * {@link io.helidon.openapi.OpenAPISupport#prepareDocument(io.helidon.common.http.MediaType)} (as opposed
     * to an interactive U/I).
     *
     * @return array of {@link MediaType} to process as normal text
     */
    protected abstract MediaType[] staticTextMediaTypes();

    protected boolean sendText(ServerRequest request, ServerResponse response, MediaType mediaType) {
        try {
            response
                    .addHeader(Http.Header.CONTENT_TYPE, mediaType.toString())
                    .send(prepareDocument(request.queryParams(), mediaType));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error formatting OpenAPI output as " + mediaType, e);
            response.status(Http.Status.INTERNAL_SERVER_ERROR_500)
                    .send("Error formatting OpenAPI output. See server log.");
        }
        return true;
    }

    protected void sendText(ServerRequest request, ServerResponse response) {
        if (!isEnabled()) {
            request.next();
        } else {
            request.headers()
                    .bestAccepted(staticTextMediaTypes())
                    .ifPresentOrElse(mt -> sendText(request, response, mt),
                                     request::next);
        }
    }

    private static OpenApiUiFactory loadUiFactory() {
        return HelidonServiceLoader.builder(ServiceLoader.load(OpenApiUiFactory.class))
                .addService(OpenApiUiMinimalFactory.create(), Integer.MAX_VALUE)
                .build()
                .iterator()
                .next();
    }

    private String prepareDocument(Parameters queryParameters, MediaType mediaType) throws IOException {
        String result = null;
        if (preparedDocuments.containsKey(mediaType)) {
            return preparedDocuments.get(mediaType);
        }
        MediaType resultMediaType = queryParameters
                .first(OpenAPISupport.OPENAPI_ENDPOINT_FORMAT_QUERY_PARAMETER)
                .map(OpenAPISupport.QueryParameterRequestedFormat::chooseFormat)
                .map(OpenAPISupport.QueryParameterRequestedFormat::mediaType)
                .orElse(OpenAPISupport.DEFAULT_RESPONSE_MEDIA_TYPE);

        result = prepareDocument(resultMediaType);
        if (mediaType.test(MediaType.TEXT_HTML)) {
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

        @Override
        public B options(Map<String, String> options) {
            this.options.clear();
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
    }
}
