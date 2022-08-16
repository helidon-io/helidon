/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.webserver.spi;

import java.util.Set;

import io.helidon.common.buffers.BufferData;
import io.helidon.nima.webserver.ConnectionContext;

/**
 * {@link java.util.ServiceLoader} provider interface for server connection providers.
 * Connection provider is given a chance to analyze request bytes and decide whether this is a connection it can accept.
 */
public interface ServerConnectionProvider {
    /**
     * How many bytes are needed to identify this connection.
     *
     * @return number of bytes needed, return 0 if this is not a fixed value
     */
    int bytesToIdentifyConnection();

    /**
     * Does this provider support current server connection.
     * The same buffer will be sent to {@link ServerConnection#handle()}
     *
     * @param data bytes (with available bytes of at least {@link #bytesToIdentifyConnection()})
     * @return support response
     */
    Support supports(BufferData data);

    /**
     * Application protocols supported by this provider, used for example for ALPN negotiation.
     *
     * @return set of supported protocols
     */
    Set<String> supportedApplicationProtocols();

    /**
     * Create a new connection.
     * All methods will be invoked from a SINGLE virtual thread.
     *
     * @param ctx connection context with access to data writer, data reader and other useful information
     * @return a new server connection
     */
    ServerConnection connection(ConnectionContext ctx);

    /**
     * Support by this provider.
     */
    enum Support {
        /**
         * Yes, this is a connection this provider can handle.
         */
        SUPPORTED,
        /**
         * No, this connection is not compatible with this provider.
         */
        UNSUPPORTED,
        /**
         * We do not have enough bytes to decide, please ask this provider again with more bytes.
         */
        UNKNOWN
    }
}
