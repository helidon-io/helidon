/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.spi.ClientProtocolProvider;

/**
 * {@link java.util.ServiceLoader} provider implementation for WebSocket protocol.
 * This supports method {@link io.helidon.webclient.api.WebClient#client(io.helidon.webclient.spi.Protocol)}.
 */
public class WsProtocolProvider implements ClientProtocolProvider<WsClient, WsClientProtocolConfig> {
    static final String CONFIG_KEY = "websocket";

    /**
     * Public constructor required by {@link java.util.ServiceLoader}.
     */
    public WsProtocolProvider() {
    }

    @Override
    public String protocolId() {
        return "ws";
    }

    @Override
    public Class<WsClientProtocolConfig> configType() {
        return WsClientProtocolConfig.class;
    }

    @Override
    public WsClientProtocolConfig defaultConfig() {
        return WsClientProtocolConfig.create();
    }

    @Override
    public WsClient protocol(WebClient client, WsClientProtocolConfig config) {
        return new WsClientImpl(client,
                                client.client(Http1Client.PROTOCOL),
                                WsClientConfig.builder().from(client.prototype())
                                        .protocolConfig(config)
                                        .buildPrototype());
    }
}
