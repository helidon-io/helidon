/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import io.helidon.nima.webserver.http1.Http1ConnectionProvider;
import io.helidon.nima.webserver.http1.spi.Http1UpgradeProvider;
import io.helidon.nima.webserver.spi.ServerConnectionProvider;

/**
 * Loom based WebServer.
 */
module io.helidon.nima.webserver {
    requires transitive io.helidon.common.buffers;
    requires transitive io.helidon.common.socket;
    requires transitive io.helidon.nima.http.encoding;
    requires transitive io.helidon.nima.http.media;
    requires transitive io.helidon.nima.common.tls;
    requires transitive io.helidon.config;
    requires io.helidon.logging.common;

    requires java.management;

    requires jakarta.annotation;
    requires io.helidon.common.uri;

    // provides multiple packages due to intentional cyclic dependency
    // we want to support HTTP/1.1 by default (we could fully separate it, but the API would be harder to use
    // for the basic routes); this would also require 4 modules (API + SPI, HTTP, HTTP/1.1, WebServer)
    // and these modules would be used mostly together (both WebSocket and HTTP/2 require HTTP/1.1 to upgrade from)
    exports io.helidon.nima.webserver;
    exports io.helidon.nima.webserver.spi;
    exports io.helidon.nima.webserver.http;
    exports io.helidon.nima.webserver.http1;
    exports io.helidon.nima.webserver.http1.spi;

    uses Http1UpgradeProvider;
    uses ServerConnectionProvider;

    provides ServerConnectionProvider with Http1ConnectionProvider;
}