/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
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

/**
 * Helidon OpenAPI 3.2 document version support.
 */
module io.helidon.openapi.v32 {
    requires static io.helidon.config.metadata;

    requires io.helidon.builder.api;
    requires io.helidon.common;
    requires io.helidon.common.media.type;
    requires io.helidon.config;
    requires io.helidon.json;
    requires io.helidon.openapi;
    requires io.helidon.service.registry;

    requires org.yaml.snakeyaml;

    exports io.helidon.openapi.v32;

    provides io.helidon.openapi.spi.OpenApiVersionProvider
            with io.helidon.openapi.v32.OpenApi32VersionProvider;
}
