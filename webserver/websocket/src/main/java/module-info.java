/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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

import io.helidon.webserver.spi.UpgradeCodecProvider;

/**
 * WebSocket support for Helidon webserver.
 */
module io.helidon.webserver.websocket {

    exports io.helidon.webserver.websocket;

    requires java.logging;
    requires transitive io.helidon.webserver;
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
            with io.helidon.webserver.websocket.WebsocketUpgradeCodecProvider;
}