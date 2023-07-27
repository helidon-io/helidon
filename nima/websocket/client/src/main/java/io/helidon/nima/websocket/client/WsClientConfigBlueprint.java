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

package io.helidon.nima.websocket.client;

import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.nima.webclient.api.HttpClientConfig;

/**
 * WebSocket full webclient configuration.
 * The client configuration also contains all necessary configuration for HTTP, as WebSocket upgrades from HTTP.
 *
 * @see io.helidon.nima.webclient.api.WebClient#client(io.helidon.nima.webclient.spi.Protocol,
 *         io.helidon.nima.webclient.spi.ProtocolConfig)
 */
@Prototype.Blueprint
@Configured
interface WsClientConfigBlueprint extends HttpClientConfig, Prototype.Factory<WsClient> {
    /**
     * WebSocket specific configuration.
     *
     * @return protocol specific configuration
     */
    @ConfiguredOption("create()")
    WsClientProtocolConfig protocolConfig();
}
