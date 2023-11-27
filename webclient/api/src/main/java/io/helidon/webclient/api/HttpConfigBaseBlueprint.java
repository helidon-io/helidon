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

package io.helidon.webclient.api;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.tls.Tls;

/**
 * Common configuration for HTTP protocols.
 */
@Prototype.Configured
@Prototype.Blueprint(builderPublic = false)
interface HttpConfigBaseBlueprint {
    /**
     * Whether to follow redirects.
     *
     * @return whether to follow redirects
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean followRedirects();

    /**
     * Max number of followed redirects.
     * This is ignored if {@link #followRedirects()} option is {@code false}.
     *
     * @return max number of followed redirects
     */
    @Option.Configured
    @Option.DefaultInt(10)
    int maxRedirects();

    /**
     * TLS configuration for any TLS request from this client.
     * TLS can also be configured per request.
     * TLS is used when the protocol is set to {@code https}.
     *
     * @return TLS configuration to use
     */
    @Option.Configured
    Tls tls();


    /**
     * Read timeout.
     *
     * @return read timeout
     * @see io.helidon.common.socket.SocketOptions#readTimeout()
     */
    @Option.Configured
    Optional<Duration> readTimeout();

    /**
     * Connect timeout.
     *
     * @return connect timeout
     * @see io.helidon.common.socket.SocketOptions#connectTimeout()
     */
    @Option.Configured
    Optional<Duration> connectTimeout();

    /**
     * Determines if connection keep alive is enabled (NOT socket keep alive, but HTTP connection keep alive, to re-use
     * the same connection for multiple requests).
     *
     * @return keep alive for this connection
     * @see io.helidon.common.socket.SocketOptions#socketKeepAlive()
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean keepAlive();

    /**
     * Proxy configuration to be used for requests.
     *
     * @return proxy to use, defaults to {@link Proxy#noProxy()}
     */
    @Option.Configured
    Proxy proxy();

    /**
     * Properties configured for this client. These properties are propagated through client request, to be used by
     * services (and possibly for other purposes).
     *
     * @return map of client properties
     */
    @Option.Configured
    @Option.Singular("property")
    Map<String, String> properties();
}
