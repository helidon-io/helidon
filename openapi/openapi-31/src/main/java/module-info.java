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

import io.helidon.common.features.api.Features;
import io.helidon.common.features.api.HelidonFlavor;

/**
 * Helidon OpenAPI 3.1 document version support.
 */
@Features.Name("OpenAPI 3.1")
@Features.Description("OpenAPI 3.1 document version support")
@Features.Flavor(HelidonFlavor.SE)
@Features.Path({"OpenAPI", "3.1"})
module io.helidon.openapi.v31 {
    requires static io.helidon.common.features.api;
    requires static io.helidon.config.metadata;

    requires transitive io.helidon.builder.api;
    requires io.helidon.common;
    requires transitive io.helidon.common.media.type;
    requires transitive io.helidon.config;
    requires io.helidon.json;
    requires transitive io.helidon.openapi;
    requires io.helidon.service.registry;

    requires org.yaml.snakeyaml;

    exports io.helidon.openapi.v31;

    provides io.helidon.openapi.spi.OpenApiVersionProvider
            with io.helidon.openapi.v31.OpenApi31VersionProvider;
}
