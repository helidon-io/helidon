/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
 * MP Tyrus Integration
 */
module io.helidon.microprofile.tyrus {
    requires java.logging;
    requires jakarta.inject.api;
    requires jakarta.interceptor.api;

    requires jakarta.enterprise.cdi.api;
    requires transitive jakarta.websocket.api;

    requires io.helidon.common;
    requires io.helidon.config;
    requires io.helidon.microprofile.cdi;
    requires tyrus.core;
    requires io.helidon.microprofile.server;
    requires io.helidon.webserver.tyrus;
    requires tyrus.spi;

    exports io.helidon.microprofile.tyrus;

    // this is needed for CDI extensions that use non-public observer methods
    opens io.helidon.microprofile.tyrus to weld.core.impl, io.helidon.microprofile.cdi;

    provides javax.enterprise.inject.spi.Extension with io.helidon.microprofile.tyrus.WebSocketCdiExtension;
    provides org.glassfish.tyrus.core.ComponentProvider with io.helidon.microprofile.tyrus.HelidonComponentProvider;
}
