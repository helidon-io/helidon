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

package io.helidon.inject.configdriven.tests.config;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * aka ServerConfiguration.
 * The SocketConfiguration configures a port to listen on and its associated server socket parameters.
 */
@Prototype.Configured
@Prototype.Blueprint
interface FakeSocketConfigBlueprint {

    /**
     * The default backlog size to configure the server sockets with if no other value
     * is provided.
     */
    int DEFAULT_BACKLOG_SIZE = 1024;

    /**
     * Name of this socket.
     * Default to WebServer#DEFAULT_SOCKET_NAME for the main and
     * default server socket. All other sockets must be named.
     *
     * @return name of this socket
     */
    @Option.Configured
    @Option.Default("@default")
    String name();

    /**
     * Returns a server port to listen on with the server socket. If port is
     * {@code 0} then any available ephemeral port will be used.
     *
     * @return the server port of the server socket
     */
    @Option.Configured
    @Option.DefaultInt(0)
    int port();

    @Option.Configured
    Optional<String> bindAddress();

    /**
     * Returns a maximum length of the queue of incoming connections on the server
     * socket.
     * <p>
     * Default value is {@link #DEFAULT_BACKLOG_SIZE}.
     *
     * @return a maximum length of the queue of incoming connections
     */
    @Option.Configured
    @Option.DefaultInt(1024)
    int backlog();

    /**
     * Returns a server socket timeout in milliseconds or {@code 0} for an infinite timeout.
     *
     * @return a server socket timeout in milliseconds or {@code 0}
     */
    @Option.Configured
    @Option.DefaultInt(0)
    int timeoutMillis();

    /**
     * Returns proposed value of the TCP receive window that is advertised to the remote peer on the
     * server socket.
     * <p>
     * If {@code 0} then use implementation default.
     *
     * @return a buffer size in bytes of the server socket or {@code 0}
     */
    @Option.Configured
    @Option.DefaultInt(0)
    int receiveBufferSize();

    /**
     * Return a {@link FakeWebServerTlsConfig} containing server TLS configuration
     * . When empty {@link java.util.Optional} is returned
     * no TLS should be configured.
     *
     * @return web server tls configuration
     */
    @Option.Configured
    Optional<FakeWebServerTlsConfig> tls();

    /**
     * Whether this socket is enabled (and will be opened on server startup), or disabled
     * (and ignored on server startup).
     *
     * @return {@code true} for enabled socket, {@code false} for socket that should not be opened
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Maximal size of all headers combined.
     *
     * @return size in bytes
     */
    @Option.Configured
    @Option.DefaultInt(8192)
    int maxHeaderSize();

    /**
     * Maximal length of the initial HTTP line.
     *
     * @return length
     */
    @Option.Configured
    @Option.DefaultInt(4096)
    int maxInitialLineLength();

    /**
     * Maximum size allowed for an HTTP payload in a client request. A negative
     * value indicates that there is no maximum set.
     *
     * @return maximum payload size
     */
    @Option.Configured
    @Option.DefaultLong(-1)
    long maxPayloadSize();

    /**
     * Maximum length of the content of an upgrade request.
     *
     * @return maximum length of the content of an upgrade request
     */
    @Option.Configured
    @Option.DefaultInt(65536)
    int maxUpgradeContentLength();

}
