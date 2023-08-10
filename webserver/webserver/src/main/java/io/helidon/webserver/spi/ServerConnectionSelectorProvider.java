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

package io.helidon.webserver.spi;

import io.helidon.webserver.ProtocolConfigs;

/**
 * {@link java.util.ServiceLoader} provider interface for server connection providers.
 * This interface serves as {@link ServerConnectionSelector} builder
 * which receives requested configuration nodes from the server configuration when server builder
 * is running.
 *
 * @param <T> type of the protocol config
 */
public interface ServerConnectionSelectorProvider<T extends ProtocolConfig> {
    /**
     * Type of configuration supported by this connection provider.
     *
     * @return type of configuration used by this provider
     */
    Class<T> protocolConfigType();

    /**
     * Type of protocol, such as {@code http_1_1}.
     *
     * @return type of this protocol, used in configuration
     */
    String protocolType();

    /**
     * Creates an instance of server connection selector.
     *
     * @param listenerName name of the listener this selector will be active on
     * @param config configuration of this provider
     * @param configs configuration of all protocols of this socket, to be used for nested protocol support, only providers
     *                that do have a configuration available should be created!
     * @return new server connection selector
     */
    ServerConnectionSelector create(String listenerName, T config, ProtocolConfigs configs);

}
