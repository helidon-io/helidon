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
import io.helidon.webserver.http1.spi.Http1UpgradeProvider;
import io.helidon.webserver.spi.ProtocolConfigProvider;
import io.helidon.webserver.websocket.WsProtocolConfigProvider;
import io.helidon.webserver.websocket.WsUpgradeProvider;

/**
 * Helidon WebServer WebSocket Support.
 */
@Feature(value = "WebSocket",
         description = "WebServer WebSocket support",
         in = HelidonFlavor.SE,
         path = {"WebSocket", "WebServer"}
)
module io.helidon.webserver.websocket {
    requires static io.helidon.common.features.api;
    requires static io.helidon.config.metadata;

    requires io.helidon.common;
    requires io.helidon.http;
    requires io.helidon.common.socket;
    requires io.helidon.builder.api;
    requires transitive io.helidon.websocket;
    requires transitive io.helidon.webserver;

    exports io.helidon.webserver.websocket;

    provides Http1UpgradeProvider
            with WsUpgradeProvider;
    provides ProtocolConfigProvider
            with WsProtocolConfigProvider;
}