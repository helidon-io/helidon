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

package io.helidon.webclient.spi;

import io.helidon.webclient.api.WebClient;

/**
 * Provider interface for client protocols.
 * HTTP protocols need to also implement {@link HttpClientSpiProvider} to
 * be available through the unified HTTP client API.
 *
 * @param <T> type of protocol
 * @param <C> type of the protocol config
 */
public interface ClientProtocolProvider<T, C> {
    /**
     * Protocol id for ALPN (protocol negotiation when using TLS).
     *
     * @return protocol id
     */
    String protocolId();

    /**
     * Type of the config object.
     *
     * @return config type
     */
    Class<C> configType();

    /**
     * Default configuration of this protocol.
     *
     * @return protocol configuration
     */
    C defaultConfig();

    /**
     * Create a protocol client instance.
     *
     * @param client webclient to use
     * @param config configuration of the protocol
     * @return a new protocol client
     */
    T protocol(WebClient client, C config);
}
