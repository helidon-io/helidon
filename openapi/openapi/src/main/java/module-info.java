/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;

/**
 * Helidon common OpenAPI behavior.
 */
@Feature(value = "OpenAPI",
         description = "OpenAPI support",
         in = HelidonFlavor.SE
)
module io.helidon.openapi {
    requires static io.helidon.common.features.api;

    requires io.helidon.common;
    requires io.helidon.common.config;
    requires io.helidon.common.media.type;
    requires io.helidon.servicecommon;
    requires static io.helidon.config.metadata;

    requires org.yaml.snakeyaml;
    requires io.helidon.inject.api;

    exports io.helidon.openapi;
    exports io.helidon.openapi.spi;

    uses io.helidon.openapi.spi.OpenApiServiceProvider;
    uses io.helidon.openapi.spi.OpenApiManagerProvider;
}
