/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
import io.helidon.webserver.http.spi.SinkProvider;
import io.helidon.webserver.http1.Http1ConnectionProvider;
import io.helidon.webserver.http1.Http1ProtocolConfigProvider;
import io.helidon.webserver.http1.spi.Http1UpgradeProvider;
import io.helidon.webserver.spi.ProtocolConfigProvider;
import io.helidon.webserver.spi.ServerConnectionSelectorProvider;

/**
 * Helidon WebServer.
 */
@Feature(value = "WebServer",
         description = "Helidon WebServer",
         in = HelidonFlavor.SE
)
module io.helidon.webserver {
    requires transitive io.helidon.common.buffers;
    requires transitive io.helidon.common.socket;
    requires transitive io.helidon.http.encoding;
    requires transitive io.helidon.http.media;
    requires transitive io.helidon.common.tls;
    requires transitive io.helidon.config;
    requires transitive io.helidon.common.context;
    requires transitive io.helidon.common.security;
    requires io.helidon.logging.common;
    requires io.helidon.builder.api;
    requires io.helidon.common.features.api;
    requires io.helidon.common.features;
    requires io.helidon.common.task;

    requires java.management;
    // only used to keep logging active until shutdown hook finishes
    requires java.logging;

    requires jakarta.annotation;
    requires io.helidon.common.uri;

    requires static io.helidon.config.metadata;
    requires static io.helidon.inject.configdriven.runtime;
    requires static jakarta.inject;
    // to compile @Generated
    requires static java.compiler;

    // needed to compile injection generated classes
    requires io.helidon.inject.api;
    requires static io.helidon.inject.runtime;

    // provides multiple packages due to intentional cyclic dependency
    // we want to support HTTP/1.1 by default (we could fully separate it, but the API would be harder to use
    // for the basic routes); this would also require 4 modules (API + SPI, HTTP, HTTP/1.1, WebServer)
    // and these modules would be used mostly together (both WebSocket and HTTP/2 require HTTP/1.1 to upgrade from)
    exports io.helidon.webserver;
    exports io.helidon.webserver.spi;
    exports io.helidon.webserver.http;
    exports io.helidon.webserver.http.spi;
    exports io.helidon.webserver.http1;
    exports io.helidon.webserver.http1.spi;

    uses Http1UpgradeProvider;
    uses ServerConnectionSelectorProvider;
    uses SinkProvider;
    uses ProtocolConfigProvider;

    provides ProtocolConfigProvider
            with Http1ProtocolConfigProvider;
    provides ServerConnectionSelectorProvider with Http1ConnectionProvider;
    provides io.helidon.inject.api.ModuleComponent with io.helidon.webserver.Injection$$Module;
}
