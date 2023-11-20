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

package io.helidon.webserver.websocket;

import io.helidon.webserver.ProtocolConfigs;
import io.helidon.webserver.http1.spi.Http1UpgradeProvider;
import io.helidon.webserver.http1.spi.Http1Upgrader;

/**
 * {@link java.util.ServiceLoader} provider implementation for upgrade from HTTP/1.1 to WebSocket.
 */
public class WsUpgradeProvider implements Http1UpgradeProvider<WsConfig> {
    /**
     * WebSocket server connection provider configuration node name.
     */
    protected static final String CONFIG_NAME = "websocket";

    /**
     * Create a new instance with default configuration.
     *
     * @deprecated This constructor is only to be used by {@link java.util.ServiceLoader},
     *      use {@link WsUpgrader#create(WsConfig)} for manual setup
     */
    @Deprecated()
    public WsUpgradeProvider() {
    }

    @Override
    public String protocolType() {
        return CONFIG_NAME;
    }

    @Override
    public Class<WsConfig> protocolConfigType() {
        return WsConfig.class;
    }

    @Override
    public Http1Upgrader create(WsConfig config, ProtocolConfigs configs) {
        return new WsUpgrader(config);
    }
}
