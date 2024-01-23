/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
 * Helidon WebClient WebSocket Support.
 */
@Feature(value = "WebSocket Client",
         description = "WebClient WebSocket support",
         in = HelidonFlavor.SE,
         path = {"WebClient", "WebSocket"}
)
module io.helidon.webclient.websocket {

    requires io.helidon.webclient;
    requires io.helidon.websocket;

    requires static io.helidon.common.features.api;

    exports io.helidon.webclient.websocket;

    provides io.helidon.webclient.spi.ClientProtocolProvider
            with io.helidon.webclient.websocket.WsProtocolProvider;
    provides io.helidon.webclient.spi.ProtocolConfigProvider
            with io.helidon.webclient.websocket.WsProtocolConfigProvider;

}