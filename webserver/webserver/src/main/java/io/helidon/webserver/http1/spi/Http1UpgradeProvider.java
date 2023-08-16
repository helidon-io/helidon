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

package io.helidon.webserver.http1.spi;

import io.helidon.webserver.ProtocolConfigs;
import io.helidon.webserver.spi.ProtocolConfig;

/**
 * {@link java.util.ServiceLoader} provider interface for HTTP/1.1 connection upgrade provider.
 * This interface serves as {@link Http1Upgrader} builder
 * which receives requested configuration from the server configuration when server builder
 * is running.
 *
 * @param <T> type of the protocol configuration of this upgrade provider
 */
public interface Http1UpgradeProvider<T extends ProtocolConfig> {

    /**
     * Provider's type.
     *
     * @return protocol type (also the type expected in configuration)
     */
    String protocolType();

    /**
     * Type of supported configuration.
     *
     * @return protocol config type
     */
    Class<T> protocolConfigType();

    /**
     * Creates an instance of HTTP/HTTP/1.1 connection upgrader.
     *
     * @param config configuration of this protocol
     * @param configs configuration for possible nested protocols
     * @return new server HTTP/1.1 connection upgrade selector
     */
    Http1Upgrader create(T config, ProtocolConfigs configs);

}
