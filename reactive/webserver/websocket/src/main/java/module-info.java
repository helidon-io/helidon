/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

import io.helidon.common.features.api.Aot;
import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.reactive.webserver.spi.UpgradeCodecProvider;
import io.helidon.reactive.webserver.websocket.WebsocketUpgradeCodecProvider;

/**
 * WebSocket support for Helidon webserver.
 */
@Feature(value = "Websocket",
        description = "Jakarta Websocket implementation",
        in = HelidonFlavor.SE,
        path = {"WebServer", "Websocket"}
)
@Aot(description = "Server only")
module io.helidon.reactive.webserver.websocket {
    requires static io.helidon.common.features.api;

    exports io.helidon.reactive.webserver.websocket;

    requires transitive io.helidon.reactive.webserver;
    requires io.helidon.common.http;
    requires transitive jakarta.websocket;
    requires io.netty.transport;
    requires io.netty.handler;
    requires io.netty.codec.http;
    requires io.netty.buffer;
    requires io.netty.common;

    requires org.glassfish.tyrus.core;
    requires org.glassfish.tyrus.server;
    requires org.glassfish.tyrus.spi;
    requires org.glassfish.tyrus.client;

    provides UpgradeCodecProvider
            with WebsocketUpgradeCodecProvider;
}