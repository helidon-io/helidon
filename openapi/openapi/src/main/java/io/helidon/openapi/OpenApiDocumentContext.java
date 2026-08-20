/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import io.helidon.common.Api;
import io.helidon.openapi.spi.OpenApiVersion;

/**
 * Context for generated OpenAPI document composition.
 */
@Api.Preview
public interface OpenApiDocumentContext {
    /**
     * OpenAPI feature instance name.
     *
     * @return feature name
     */
    String featureName();

    /**
     * OpenAPI endpoint web context.
     *
     * @return web context
     */
    String webContext();

    /**
     * Listener this document is served from.
     *
     * @return listener name
     */
    String listener();

    /**
     * Generated document mode.
     *
     * @return generated mode
     */
    OpenApiGeneratedMode generatedMode();

    /**
     * Selected OpenAPI version implementation.
     *
     * @return OpenAPI version implementation
     */
    OpenApiVersion openApiVersion();
}
