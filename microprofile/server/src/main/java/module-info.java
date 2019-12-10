/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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
    requires transitive io.helidon.microprofile.config;
    requires transitive io.helidon.webserver;
    requires transitive io.helidon.webserver.jersey;
    requires transitive io.helidon.common.context;
    requires transitive io.helidon.jersey.server;

    requires transitive cdi.api;
    requires transitive java.ws.rs;
    requires transitive org.glassfish.java.json;

    requires java.logging;
    requires io.helidon.common.serviceloader;

    // there is now a hardcoded dependency on Weld, to configure additional bean defining annotation
    requires weld.se.core;
    requires weld.core.impl;
    requires weld.environment.common;

    exports io.helidon.microprofile.server;
    exports io.helidon.microprofile.server.spi;

    uses io.helidon.microprofile.server.spi.MpService;
    provides javax.enterprise.inject.spi.Extension with io.helidon.microprofile.server.ServerCdiExtension;
}
