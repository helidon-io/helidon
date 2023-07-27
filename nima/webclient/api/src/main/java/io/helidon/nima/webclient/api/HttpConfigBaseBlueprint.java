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

package io.helidon.nima.webclient.api;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.nima.common.tls.Tls;

@Configured
@Prototype.Blueprint(builderPublic = false)
interface HttpConfigBaseBlueprint {
    /**
     * Whether to follow redirects.
     *
     * @return whether to follow redirects
     */
    @ConfiguredOption("true")
    boolean followRedirects();

    /**
     * Max number of followed redirects.
     * This is ignored if {@link #followRedirects()} option is {@code false}.
     *
     * @return max number of followed redirects
     */
    @ConfiguredOption("10")
    int maxRedirects();

    /**
     * TLS configuration for any TLS request from this client.
     * TLS can also be configured per request.
     * TLS is used when the protocol is set to {@code https}.
     *
     * @return TLS configuration to use
     */
    @ConfiguredOption
    Tls tls();


    /**
     * Read timeout.
     *
     * @return read timeout
     * @see io.helidon.common.socket.SocketOptions#readTimeout()
     */
    @ConfiguredOption
    Optional<Duration> readTimeout();

    /**
     * Connect timeout.
     *
     * @return connect timeout
     * @see io.helidon.common.socket.SocketOptions#connectTimeout()
     */
    @ConfiguredOption
    Optional<Duration> connectTimeout();

    /**
     * Determines if connection keep alive is enabled (NOT socket keep alive, but HTTP connection keep alive, to re-use
     * the same connection for multiple requests).
     *
     * @return keep alive for this connection
     * @see io.helidon.common.socket.SocketOptions#socketKeepAlive()
     */
    @ConfiguredOption("true")
    boolean keepAlive();

    /**
     * Proxy configuration to be used for requests.
     *
     * @return proxy to use, defaults to {@link Proxy#noProxy()}
     */
    @ConfiguredOption
    Proxy proxy();

    /**
     * Properties configured for this client. These properties are propagated through client request, to be used by
     * services (and possibly for other purposes).
     *
     * @return map of client properties
     */
    @ConfiguredOption
    @Prototype.Singular("property")
    Map<String, String> properties();
}
