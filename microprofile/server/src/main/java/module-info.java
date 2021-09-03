/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

/**
 * Implementation of a layer that binds microprofile components together and
 * runs an HTTP server.
 */
module io.helidon.microprofile.server {
    requires transitive io.helidon.webserver;
    requires transitive io.helidon.webserver.jersey;
    requires transitive io.helidon.common.context;
    requires transitive io.helidon.jersey.server;

    requires transitive io.helidon.microprofile.cdi;

    requires io.helidon.config.mp;
    requires io.helidon.microprofile.config;
    requires transitive jakarta.enterprise.cdi.api;
    requires transitive java.ws.rs;
    requires jakarta.interceptor.api;

    requires java.logging;
    requires io.helidon.common.serviceloader;
    requires io.helidon.webserver.staticcontent;

    // there is now a hardcoded dependency on Weld, to configure additional bean defining annotation
    requires java.management;
    requires microprofile.config.api;

    exports io.helidon.microprofile.server;

    provides javax.enterprise.inject.spi.Extension with
            io.helidon.microprofile.server.ServerCdiExtension,
            io.helidon.microprofile.server.JaxRsCdiExtension;

    // needed when running with modules - to make private methods accessible
    opens io.helidon.microprofile.server to weld.core.impl, io.helidon.microprofile.cdi;
}
