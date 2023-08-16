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

package io.helidon.webserver.http2.spi;

import io.helidon.webserver.ProtocolConfigs;
import io.helidon.webserver.spi.ProtocolConfig;

/**
 * {@link java.util.ServiceLoader} provider interface for HTTP/2 sub-protocols.
 *
 * @param <T> type of the protocol configuration used by the provider
 */
public interface Http2SubProtocolProvider<T extends ProtocolConfig> {

    /**
     * Provider's type, also expected as the configuration node name.
     *
     * @return type of this provider, such as {@code grpc}
     */
    String protocolType();

    /**
     * Type of supported configuration.
     *
     * @return protocol config type
     */
    Class<T> protocolConfigType();

    /**
     * Creates an instance of HTTP/2 sub-protocol selector.
     *
     * @param config configuration of this protocol
     * @param configs configuration for possible nested protocols
     * @return new HTTP/2 sub-protocol selector
     */
    Http2SubProtocolSelector create(T config, ProtocolConfigs configs);

}
