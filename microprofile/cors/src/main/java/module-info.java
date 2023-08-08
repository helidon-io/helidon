/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
 * Support for CORS.
 */
@Feature(value = "CORS",
        description = "CORS support for Server",
        in = HelidonFlavor.MP,
        path = {"Server", "CORS"}
)
module io.helidon.microprofile.cors {
    requires static io.helidon.common.features.api;

    requires jakarta.ws.rs;
    requires io.helidon.config;
    requires io.helidon.config.mp;
    requires io.helidon.webserver.cors;

    // Following to help with JavaDoc...
    requires io.helidon.jersey.common;
    requires io.helidon.webserver;
    requires io.helidon.microprofile.config;

    // ---
    requires jersey.common;
    requires microprofile.config.api;

    requires jakarta.cdi;

    exports io.helidon.microprofile.cors;

    provides org.glassfish.jersey.internal.spi.AutoDiscoverable
            with io.helidon.microprofile.cors.CrossOriginAutoDiscoverable;

    provides jakarta.enterprise.inject.spi.Extension with io.helidon.microprofile.cors.CorsCdiExtension;
}
