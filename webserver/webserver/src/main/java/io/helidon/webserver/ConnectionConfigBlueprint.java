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

package io.helidon.webserver;

import java.net.SocketOption;
import java.time.Duration;
import java.util.Map;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Configuration of a server connection (for each connection created by clients).
 */
@Prototype.Blueprint
@Prototype.Configured
interface ConnectionConfigBlueprint {
    /**
     * Default read timeout duration.
     */
    String DEFAULT_READ_TIMEOUT_DURATION = "PT30S";
    /**
     * Default connect timeout duration.
     */
    String DEFAULT_CONNECT_TIMEOUT_DURATION = "PT10S";
    /**
     * Default SO buffer size.
     */
    int DEFAULT_SO_BUFFER_SIZE = 32768;

    /**
     * Read timeout.
     * Default is {@value #DEFAULT_READ_TIMEOUT_DURATION}
     *
     * @return read timeout
     */
    @Option.Configured
    @Option.Default(DEFAULT_READ_TIMEOUT_DURATION)
    Duration readTimeout();

    /**
     * Connect timeout.
     * Default is {@value #DEFAULT_CONNECT_TIMEOUT_DURATION}.
     *
     * @return connect timeout
     */
    @Option.Configured
    @Option.Default(DEFAULT_CONNECT_TIMEOUT_DURATION)
    Duration connectTimeout();

    /**
     * Socket send buffer size.
     * Default is {@value #DEFAULT_SO_BUFFER_SIZE}.
     *
     * @return buffer size, in bytes
     * @see java.net.StandardSocketOptions#SO_SNDBUF
     */
    @Option.Configured
    @Option.DefaultInt(DEFAULT_SO_BUFFER_SIZE)
    int sendBufferSize();

    /**
     * Socket receive buffer size.
     * Default is {@value #DEFAULT_SO_BUFFER_SIZE}.
     *
     * @return buffer size, in bytes
     * @see java.net.StandardSocketOptions#SO_RCVBUF
     */
    @Option.Configured
    @Option.DefaultInt(DEFAULT_SO_BUFFER_SIZE)
    int receiveBufferSize();

    /**
     * Configure socket keep alive.
     * Default is {@code true}.
     *
     * @return keep alive
     * @see java.net.StandardSocketOptions#SO_KEEPALIVE
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean keepAlive();

    /**
     * Socket reuse address.
     * Default is {@code true}.
     *
     * @return whether to reuse address
     * @see java.net.StandardSocketOptions#SO_REUSEADDR
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean reuseAddress();

    /**
     * This option may improve performance on some systems.
     * Default is {@code false}.
     *
     * @return whether to use TCP_NODELAY, defaults to {@code false}
     * @see java.net.StandardSocketOptions#TCP_NODELAY
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean tcpNoDelay();

    /**
     * Set an arbitrary socket option. A mapping of a socket option to its value.
     * Socket options may be system specific. Most commonly supported socket options are available as methods directly.
     *
     * @return socket options
     * @see java.net.StandardSocketOptions
     */
    @Option.Singular
    Map<SocketOption<?>, Object> socketOptions();
}
