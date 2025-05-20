/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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
 * Helidon WebServer.
 */
@Feature(value = "WebServer",
         description = "Helidon WebServer",
         in = HelidonFlavor.SE
)
module io.helidon.webserver {

    requires io.helidon.builder.api;
    requires io.helidon.common.features.api;
    requires io.helidon.common.features;
    requires io.helidon.common.task;
    requires io.helidon.common.uri;
    requires io.helidon.common.resumable;
    requires io.helidon.logging.common;
    requires java.management;
    requires io.helidon;

    requires transitive io.helidon.common.buffers;
    requires transitive io.helidon.common.context;
    requires transitive io.helidon.common.security;
    requires transitive io.helidon.common.socket;
    requires transitive io.helidon.common.tls;
    requires transitive io.helidon.config;
    requires transitive io.helidon.http.encoding;
    requires transitive io.helidon.http.media;
    requires transitive io.helidon.common.concurrency.limits;
    requires io.helidon.service.registry;

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

    uses io.helidon.webserver.spi.ServerConnectionSelectorProvider;
    uses io.helidon.webserver.spi.ProtocolConfigProvider;
    uses io.helidon.webserver.spi.ServerFeatureProvider;
    uses io.helidon.webserver.http.spi.SinkProvider;
    uses io.helidon.webserver.http1.spi.Http1UpgradeProvider;
    uses io.helidon.common.concurrency.limits.spi.LimitProvider;

    provides io.helidon.webserver.spi.ProtocolConfigProvider
            with io.helidon.webserver.http1.Http1ProtocolConfigProvider;
    provides io.helidon.webserver.spi.ServerConnectionSelectorProvider with io.helidon.webserver.http1.Http1ConnectionProvider;

}
