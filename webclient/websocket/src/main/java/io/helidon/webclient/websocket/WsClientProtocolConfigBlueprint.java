/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.webclient.websocket;

import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.webclient.spi.ProtocolConfig;
import io.helidon.websocket.WsProtocolConfig;

/**
 * WebSocket client protocol configuration.
 */
@Prototype.Blueprint(decorator = WsClientProtocolConfigSupport.BuilderDecorator.class)
@Prototype.Configured
@Prototype.IncludeDefaultMethods("maxFrameLength")
interface WsClientProtocolConfigBlueprint extends ProtocolConfig, WsProtocolConfig {
    @Override
    default String type() {
        return WsProtocolProvider.CONFIG_KEY;
    }

    /**
     * Name of this protocol configuration.
     *
     * @return protocol configuration name
     */
    @Option.Configured
    @Option.Default(WsProtocolProvider.CONFIG_KEY)
    @Override
    String name();

    /**
     * WebSocket sub-protocols requested by the client.
     *
     * @return requested WebSocket sub-protocols
     */
    @Option.Configured
    @Option.Singular
    List<String> subProtocols();

    /**
     * Maximum WebSocket frame payload size in bytes accepted by the client, from {@code 1} through
     * {@code Integer.MAX_VALUE}; the default is 1 MiB, replacing the earlier effectively unbounded behavior, and
     * exceeding the configured limit causes the client to close the connection with status code {@code 1009}
     * ({@code Message Too Big}).
     *
     * @return maximum frame payload size in bytes
     */
    @Option.Configured
    @Option.DefaultInt(1024 * 1024)
    default int maxFrameLength() {
        return 1024 * 1024;
    }
}
