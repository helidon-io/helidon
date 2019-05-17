/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
 *
 */
package io.helidon.microprofile.openapi;

import java.util.Optional;

import io.helidon.openapi.OpenAPISupport;

import io.smallrye.openapi.api.OpenApiConfig;
import org.jboss.jandex.IndexView;

/**
 * Fluent builder for OpenAPISupport in Helidon MP.
 */
public final class MPOpenAPIBuilder extends OpenAPISupport.Builder {

    private Optional<OpenApiConfig> openAPIConfig;
    private Optional<IndexView> indexView;

    @Override
    public OpenApiConfig openAPIConfig() {
        return openAPIConfig.get();
    }

    @Override
    public IndexView indexView() {
        return indexView.get();
    }

    /**
     * Sets the OpenApiConfig instance to use in governing the behavior of the
     * smallrye OpenApi implementation.
     *
     * @param config {@link OpenApiConfig} instance to control OpenAPI behavior
     * @return updated builder instance
     */
    public MPOpenAPIBuilder openAPIConfig(OpenApiConfig config) {
        this.openAPIConfig = Optional.of(config);
        return this;
    }

    /**
     * Sets the IndexView instance to be passed to the smallrye OpenApi impl for
     * annotation analysis.
     *
     * @param indexView {@link IndexView} instance containing endpoint classes
     * @return updated builder instance
     */
    public MPOpenAPIBuilder indexView(IndexView indexView) {
        this.indexView = Optional.of(indexView);
        return this;
    }

    @Override
    public void validate() throws IllegalStateException {
        if (!openAPIConfig.isPresent()) {
            throw new IllegalStateException("OpenApiConfig has not been set in MPBuilder");
        }
    }

}
