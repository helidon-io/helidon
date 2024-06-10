/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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
 * CDI extension for MicroProfile OpenAPI implementation.
 *
 * @see org.eclipse.microprofile.openapi
 */
@Feature(value = "Open API",
        description = "MicroProfile Open API spec implementation",
        in = HelidonFlavor.MP,
        path = "Open API"
)
@SuppressWarnings({ "requires-automatic", "requires-transitive-automatic" })
module io.helidon.microprofile.openapi {

    requires io.helidon.microprofile.server;
    requires java.desktop; // for java.beans package
    requires java.logging; // logging required for SnakeYAML logging workaround
    requires microprofile.config.api;
    requires org.jboss.jandex;
    requires smallrye.open.api.core;

    requires static io.helidon.common.features.api;

    requires transitive io.helidon.microprofile.servicecommon;
    requires transitive io.helidon.openapi;
    requires transitive microprofile.openapi.api;
    requires transitive org.yaml.snakeyaml;

    exports io.helidon.microprofile.openapi;

    // this is needed for CDI extensions that use non-public observer methods
    opens io.helidon.microprofile.openapi to weld.core.impl, io.helidon.microprofile.cdi;

    provides jakarta.enterprise.inject.spi.Extension
            with io.helidon.microprofile.openapi.OpenApiCdiExtension;

}
