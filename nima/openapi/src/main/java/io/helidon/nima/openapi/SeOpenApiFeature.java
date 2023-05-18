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
package io.helidon.nima.openapi;

import io.helidon.openapi.OpenApiFeature;

/**
 * SE implementation of {@link OpenApiFeature}.
 */
public class SeOpenApiFeature extends OpenApiFeature {

    private static final System.Logger LOGGER = System.getLogger(SeOpenApiFeature.class.getName());

    /**
     * Create a new builder for the feature.
     *
     * @return the new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new instance.
     *
     * @param builder builder for the SE OpenAPI feature
     */
    protected SeOpenApiFeature(Builder builder) {
        super(LOGGER, builder);
    }

    @Override
    protected String openApiContent(io.helidon.openapi.OpenApiFeature.OpenAPIMediaType openApiMediaType) {
        // TODO temporarily supports only static files
        if (staticContent().isPresent()) {
            return staticContent().get().content();
        }
        return null;
    }

    public static class Builder extends OpenApiFeature.Builder<Builder, SeOpenApiFeature> {

        private static final System.Logger LOGGER = System.getLogger(Builder.class.getName());

        @Override
        public SeOpenApiFeature build() {
            return new SeOpenApiFeature(this);
        }

        @Override
        protected System.Logger logger() {
            return LOGGER;
        }
    }
}
