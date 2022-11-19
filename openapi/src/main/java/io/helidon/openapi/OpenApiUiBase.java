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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Function;

import io.helidon.common.LazyValue;
import io.helidon.common.http.MediaType;
import io.helidon.common.serviceloader.HelidonServiceLoader;

/**
 * Common base class for implementations of @link OpenApiUi}.
 */
public abstract class OpenApiUiBase implements OpenApiUi {

    private static final LazyValue<OpenApiUiFactory> UI_FACTORY = LazyValue.create(OpenApiUiBase::loadUiFactory);

    /**
     *
     * @return a builder for the currently-available implementation of {@link OpenApiUi}.
     */
    static OpenApiUi.Builder builder() {
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
    protected OpenApiUiBase(Builder builder, Function<MediaType, String> documentPreparer, String openAPIWebContext) {
        this.documentPreparer = documentPreparer;
        isEnabled = builder.isEnabled;
        webContext = Objects.requireNonNullElse(builder.webContext,
                                                openAPIWebContext + OpenApiUi.DEFAULT_UI_WEB_SUBCONTEXT);
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
    protected String webContent() {
        return webContext;
    }

    /**
     *
     * @return options set for this U/I implementation (unmodifiable)
     */
    protected Map<String, String> options() {
        return Collections.unmodifiableMap(options);
    }

    private static OpenApiUiFactory loadUiFactory() {
        return HelidonServiceLoader.builder(ServiceLoader.load(OpenApiUiFactory.class))
                .addService(OpenApiUiMinimalFactory.create(), Integer.MAX_VALUE)
                .build()
                .iterator()
                .next();
    }

    /**
     * Common base builder implementation for creating a new {@code OpenApiUi}.
     */
    public abstract static class Builder implements OpenApiUi.Builder {

        private final Map<String, String> options = new HashMap<>();
        private boolean isEnabled = true;
        private String webContext;

        @Override
        public OpenApiUi.Builder options(Map<String, String> options) {
            this.options.clear();
            this.options.putAll(options);
            return this;
        }

        @Override
        public OpenApiUi.Builder isEnabled(boolean isEnabled) {
            this.isEnabled = isEnabled;
            return this;
        }

        @Override
        public OpenApiUi.Builder webContext(String webContext) {
            this.webContext = webContext;
            return this;
        }
    }
}
