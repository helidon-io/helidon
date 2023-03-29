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

package io.helidon.builder.config.testsubjects.fakes;

import java.util.Optional;

import io.helidon.builder.config.ConfigBean;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * aka ServerConfiguration.
 * The SocketConfiguration configures a port to listen on and its associated server socket parameters.
 */
@ConfigBean
public interface FakeSocketConfig {

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
    @ConfiguredOption("@default")
    String name();

    /**
     * Returns a server port to listen on with the server socket. If port is
     * {@code 0} then any available ephemeral port will be used.
     *
     * @return the server port of the server socket
     */
    int port();

    @ConfiguredOption(key = "bind-address")
    String bindAddress();

    /**
     * Returns a maximum length of the queue of incoming connections on the server
     * socket.
     * <p>
     * Default value is {@link #DEFAULT_BACKLOG_SIZE}.
     *
     * @return a maximum length of the queue of incoming connections
     */
    @ConfiguredOption("1024")
    int backlog();

    /**
     * Returns a server socket timeout in milliseconds or {@code 0} for an infinite timeout.
     *
     * @return a server socket timeout in milliseconds or {@code 0}
     */
    @ConfiguredOption(key = "timeout-millis")
    int timeoutMillis();

    /**
     * Returns proposed value of the TCP receive window that is advertised to the remote peer on the
     * server socket.
     * <p>
     * If {@code 0} then use implementation default.
     *
     * @return a buffer size in bytes of the server socket or {@code 0}
     */
    int receiveBufferSize();

    /**
     * Return a {@link FakeWebServerTlsConfig} containing server TLS configuration. When empty {@link java.util.Optional} is returned
     * no TLS should be configured.
     *
     * @return web server tls configuration
     */
    Optional<FakeWebServerTlsConfig> tls();

    /**
     * Whether this socket is enabled (and will be opened on server startup), or disabled
     * (and ignored on server startup).
     *
     * @return {@code true} for enabled socket, {@code false} for socket that should not be opened
     */
    @ConfiguredOption("true")
    boolean enabled();

    /**
     * Maximal size of all headers combined.
     *
     * @return size in bytes
     */
    @ConfiguredOption(key = "max-header-size", value = "8192")
    int maxHeaderSize();

    /**
     * Maximal length of the initial HTTP line.
     *
     * @return length
     */
    @ConfiguredOption("4096")
    int maxInitialLineLength();

    /**
     * Maximum size allowed for an HTTP payload in a client request. A negative
     * value indicates that there is no maximum set.
     *
     * @return maximum payload size
     */
    @ConfiguredOption("-1")
    long maxPayloadSize();

    /**
     * Maximum length of the content of an upgrade request.
     *
     * @return maximum length of the content of an upgrade request
     */
    @ConfiguredOption("65536")
    int maxUpgradeContentLength();

}
