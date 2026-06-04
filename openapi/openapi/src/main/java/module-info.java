/*
 * Copyright (c) 2019, 2026 Oracle and/or its affiliates.
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
import io.helidon.openapi.v30.OpenApi30VersionProvider;

/**
 * Helidon common OpenAPI behavior.
 */
@Features.Name("OpenAPI")
@Features.Description("OpenAPI support")
@Features.Flavor(HelidonFlavor.SE)
module io.helidon.openapi {
    requires static io.helidon.common.features.api;
    requires static io.helidon.config.metadata;

    requires io.helidon.common;
    requires io.helidon.config;
    requires io.helidon.common.media.type;
    requires transitive io.helidon.json.schema;
    requires transitive io.helidon.service.registry;
    requires io.helidon.webserver;

    requires org.yaml.snakeyaml;

    exports io.helidon.openapi;
    exports io.helidon.openapi.spi;
    // this is a multi-package module, as version 3.0 must be supported for backward compatibility, and we cannot extract it
    // into its own module
    exports io.helidon.openapi.v30;

    uses io.helidon.openapi.spi.OpenApiServiceProvider;
    uses io.helidon.openapi.spi.OpenApiManagerProvider;
    uses io.helidon.openapi.spi.OpenApiVersionProvider;

    provides io.helidon.webserver.spi.ServerFeatureProvider
            with io.helidon.openapi.OpenApiFeatureProvider;
    provides io.helidon.openapi.spi.OpenApiVersionProvider
            with OpenApi30VersionProvider;
}
