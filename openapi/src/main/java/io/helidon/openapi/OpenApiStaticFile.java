/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import java.util.Objects;

/**
 * Information about a static OpenAPI file bundled with the application.
 * <p>
 *     There can be up to one of these for each {@link io.helidon.openapi.OpenApiFeature.OpenAPIMediaType} (YAML and JSON).
 * </p>
 */
public class OpenApiStaticFile {

    /**
     * Creates a new static file instance using the given OpenAPI media type and content.
     *
     * @param openApiMediaType OpenAPI media type
     * @param content text content
     * @return static file instance
     */
    static OpenApiStaticFile create(OpenApiFeature.OpenAPIMediaType openApiMediaType, String content) {
        return new Builder().openApiMediaType(openApiMediaType).content(content).build();
    }

    private OpenApiFeature.OpenAPIMediaType openApiMediaType;
    private String content;

    private OpenApiStaticFile(Builder builder) {
        this.content = builder.content;
        this.openApiMediaType = builder.openApiMediaType;
    }

    /**
     * Returns the OpenAPI media type of the static content.
     *
     * @return the OpenAPI media type of the static content
     */
    public OpenApiFeature.OpenAPIMediaType openApiMediaType() {
        return openApiMediaType;
    }

    /**
     * Returns the text content of the static file.
     *
     * @return text static content
     */
    public String content() {
        return content;
    }

    static class Builder implements io.helidon.common.Builder<Builder, OpenApiStaticFile> {

        private OpenApiFeature.OpenAPIMediaType openApiMediaType;
        private String content;

        @Override
        public OpenApiStaticFile build() {
            Objects.requireNonNull(openApiMediaType);
            Objects.requireNonNull(content);
            return new OpenApiStaticFile(this);
        }

        Builder openApiMediaType(OpenApiFeature.OpenAPIMediaType openApiMediaType) {
            this.openApiMediaType = openApiMediaType;
            return this;
        }

        Builder content(String content) {
            this.content = content;
            return this;
        }
    }

    void content(String content) {
        this.content = content;
    }
}
