/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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
import io.helidon.reactive.webserver.jersey.HelidonHK2InjectionManagerFactory;

import org.glassfish.jersey.internal.inject.InjectionManagerFactory;

/**
 * Jersey integration.
 */
@Feature(value = "Jersey",
        description = "WebServer integration with Jersey",
        in = HelidonFlavor.SE,
        path = {"WebServer", "Jersey"}
)
module io.helidon.reactive.webserver.jersey {
    requires static io.helidon.common.features.api;

    requires transitive jakarta.annotation;
    requires transitive io.helidon.reactive.webserver;
    requires transitive jakarta.ws.rs;
    requires transitive io.helidon.jersey.server;
    requires transitive io.helidon.jersey.client;

    requires io.helidon.common.context;
    requires io.helidon.common.mapper;
    requires io.helidon.common.reactive;
    requires java.logging;
    requires io.netty.buffer;
    requires jersey.common;

    exports io.helidon.reactive.webserver.jersey;

    provides InjectionManagerFactory with HelidonHK2InjectionManagerFactory;

    // reflection access from jersey injection
    opens io.helidon.reactive.webserver.jersey to org.glassfish.hk2.utilities, org.glassfish.hk2.locator, weld.core.impl;
}
