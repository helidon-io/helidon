/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.quic.spi;

import io.helidon.common.Api;
import io.helidon.webserver.TransportBindingContext;

/**
 * {@link java.util.ServiceLoader} provider interface for QUIC sub-protocols.
 *
 * @param <T> type of the protocol configuration used by the provider
 */
@Api.Internal
public interface QuicSubProtocolProvider<T extends QuicSubProtocolConfig> {
    /**
     * Configuration type key supported by this provider.
     *
     * @return configuration type key
     */
    String configKey();

    /**
     * Type of supported protocol configuration.
     *
     * @return protocol configuration type
     */
    Class<T> protocolConfigType();

    /**
     * Create a runtime for this QUIC sub-protocol.
     *
     * @param context listener binding context
     * @param config sub-protocol configuration
     * @return runtime for the configured QUIC sub-protocol
     */
    QuicSubProtocolRuntime create(TransportBindingContext context, T config);
}
