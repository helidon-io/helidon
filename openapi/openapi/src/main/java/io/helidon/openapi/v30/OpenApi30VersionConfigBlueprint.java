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

package io.helidon.openapi.v30;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;
import io.helidon.openapi.spi.OpenApiVersionProvider;

/**
 * OpenAPI 3.0 version configuration.
 */
@Api.Preview
@Prototype.Blueprint
@Prototype.Configured(value = OpenApi30Version.TYPE, root = false)
@Prototype.Provides(OpenApiVersionProvider.class)
interface OpenApi30VersionConfigBlueprint extends Prototype.Factory<OpenApi30Version> {
    /**
     * Name of this version configuration.
     *
     * @return version implementation name
     */
    @Option.Default(OpenApi30Version.TYPE)
    String name();

    /**
     * Exact OpenAPI 3.0 document version to produce.
     *
     * @return OpenAPI document version
     */
    @Option.Configured
    @Option.Default("3.0.3")
    String version();
}
