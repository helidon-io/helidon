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

    /**
     * Creates a new instance.
     *
     * @param logger logger for messages
     * @param builder builder for the SE OpenAPI featureope
     */
    protected SeOpenApiFeature(System.Logger logger, Builder<?, ?> builder) {
        super(logger, builder);
    }

    @Override
    protected String openApiContent(io.helidon.openapi.OpenApiFeature.OpenAPIMediaType openApiMediaType) {
        return null;
    }
}
