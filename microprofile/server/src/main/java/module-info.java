/*
 * Copyright (c) 2018, 2025 Oracle and/or its affiliates.
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
 * Implementation of a layer that binds microprofile components together and
 * runs an HTTP server.
 */
@Feature(value = "Server",
        description = "Server for Helidon MP",
        in = HelidonFlavor.MP,
        path = "Server"
)
module io.helidon.microprofile.server {

    requires io.helidon.config.mp;
    requires io.helidon.common.resumable;
    requires io.helidon.jersey.media.jsonp;
    requires io.helidon.microprofile.config;
    requires io.helidon.webserver.staticcontent;
    requires java.management; // there is now a hardcoded dependency on Weld, to configure additional bean defining annotation
    requires microprofile.config.api;

    requires static io.helidon.common.features.api;
    requires static io.helidon.config.metadata;

    requires transitive io.helidon.common.configurable;
    requires transitive io.helidon.common.context;
    requires transitive io.helidon.jersey.server;
    requires transitive io.helidon.microprofile.cdi;
    requires transitive io.helidon.webserver.context;
    requires transitive io.helidon.webserver;
    requires transitive io.helidon.webserver.observe;
    requires transitive jakarta.validation;
    requires transitive jakarta.cdi;
    requires transitive jakarta.json;
    requires transitive jakarta.ws.rs;
    requires io.helidon.service.registry;

    exports io.helidon.microprofile.server;

    provides jakarta.enterprise.inject.spi.Extension
            with io.helidon.microprofile.server.ServerCdiExtension, io.helidon.microprofile.server.JaxRsCdiExtension;

    provides org.glassfish.jersey.internal.inject.InjectionManagerFactory
            with io.helidon.microprofile.server.HelidonHK2InjectionManagerFactory;

    // needed when running with modules - to make private methods and types accessible
    opens io.helidon.microprofile.server;

}
