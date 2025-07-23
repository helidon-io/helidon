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
 * Helidon WebServer HTTP/2 Support.
 */
@Feature(value = "HTTP/2",
         description = "WebServer HTTP/2 support",
         in = HelidonFlavor.SE,
         path = {"WebServer", "HTTP/2"}
)
module io.helidon.webserver.http2 {

    requires io.helidon.builder.api;

    requires static io.helidon.common.features.api;

    requires transitive io.helidon.common.socket;
    requires transitive io.helidon.common.task;
    requires transitive io.helidon.common.uri;
    requires transitive io.helidon.common;
    requires transitive io.helidon.http.encoding;
    requires transitive io.helidon.http.http2;
    requires transitive io.helidon.http.media;
    requires transitive io.helidon.http;
    requires transitive io.helidon.webserver;
    requires transitive io.helidon.common.concurrency.limits;
    requires io.helidon.service.registry;

    exports io.helidon.webserver.http2;
    exports io.helidon.webserver.http2.spi;

    // to support prior knowledge for h2c
    provides io.helidon.webserver.spi.ServerConnectionSelectorProvider
            with io.helidon.webserver.http2.Http2ConnectionProvider;
    // to support upgrade requests for h2c
    provides io.helidon.webserver.http1.spi.Http1UpgradeProvider
            with io.helidon.webserver.http2.Http2UpgradeProvider;
    provides io.helidon.webserver.spi.ProtocolConfigProvider
            with io.helidon.webserver.http2.Http2ProtocolConfigProvider;

    uses io.helidon.webserver.http2.spi.Http2SubProtocolProvider;

}
