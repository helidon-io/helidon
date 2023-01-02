/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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
 * Helidon SE OpenAPI Support.
 */
@Feature(value = "OpenAPI",
        description = "Open API support",
        in = HelidonFlavor.SE,
        path = "OpenAPI"
)
module io.helidon.openapi {
    requires static io.helidon.common.features.api;

    requires java.logging;

    requires io.helidon.common;
    requires io.helidon.config;

    requires org.jboss.jandex;

    requires smallrye.open.api.core;
    requires jakarta.json;
    requires java.desktop; // for java.beans package
    requires org.yaml.snakeyaml;

    requires transitive microprofile.openapi.api;

    requires static io.helidon.config.metadata;

    exports io.helidon.openapi;
    exports io.helidon.openapi.internal to io.helidon.microprofile.openapi, io.helidon.reactive.openapi, io.helidon.nima.openapi;
}
