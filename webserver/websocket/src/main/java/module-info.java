/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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
 * Helidon WebServer WebSocket Support.
 */
@Feature(value = "WebSocket",
         description = "WebServer WebSocket support",
         in = HelidonFlavor.SE,
         path = {"WebSocket", "WebServer"}
)
module io.helidon.webserver.websocket {

    requires io.helidon.builder.api;
    requires io.helidon.common.socket;
    requires io.helidon.common;
    requires io.helidon.http;

    requires static io.helidon.common.features.api;

    requires transitive io.helidon.webserver;
    requires transitive io.helidon.websocket;
    requires transitive io.helidon.common.concurrency.limits;

    exports io.helidon.webserver.websocket;

    provides io.helidon.webserver.http1.spi.Http1UpgradeProvider
            with io.helidon.webserver.websocket.WsUpgradeProvider;
    provides io.helidon.webserver.spi.ProtocolConfigProvider
            with io.helidon.webserver.websocket.WsProtocolConfigProvider;

}