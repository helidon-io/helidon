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

import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.webserver.spi.ProtocolConfig;

/**
 * WebSocket protocol configuration.
 */
@Prototype.Blueprint
@Configured(provides = ProtocolConfig.class)
interface WsConfigBlueprint extends ProtocolConfig {
    /**
     * WebSocket origins.
     *
     * @return origins
     */
    @ConfiguredOption
    @Option.Singular
    Set<String> origins();

    /**
     * Protocol configuration type.
     *
     * @return type of this configuration
     */
    default String type() {
        return WsUpgradeProvider.CONFIG_NAME;
    }

    /**
     * Name of this configuration.
     *
     * @return configuration name
     */
    @ConfiguredOption(WsUpgradeProvider.CONFIG_NAME)
    @Override
    String name();

    /**
     * Max WebSocket frame size supported by the server on a read operation.
     * Default is 1 MB.
     *
     * @return max frame size to read
     */
    @ConfiguredOption(WsConnection.MAX_FRAME_LENGTH)
    int maxFrameLength();
}
