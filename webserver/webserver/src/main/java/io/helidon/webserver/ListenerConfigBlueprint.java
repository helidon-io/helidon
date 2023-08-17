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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.common.config.Config;
import io.helidon.common.context.Context;
import io.helidon.common.socket.SocketOptions;
import io.helidon.common.tls.Tls;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.http.RequestedUriDiscoveryContext;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.media.MediaContext;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.spi.ProtocolConfig;
import io.helidon.webserver.spi.ProtocolConfigProvider;
import io.helidon.webserver.spi.ServerConnectionSelector;

/**
 * Configuration of a server listener (server socket).
 */
@Configured
@Prototype.Blueprint(decorator = ListenerConfigBlueprint.ConfigDecorator.class)
interface ListenerConfigBlueprint {
    /**
     * Configuration of protocols. This may be either protocol selectors, or protocol upgraders from HTTP/1.1.
     * As the order is not important (providers are ordered by weight by default), we can use a configuration as an object,
     * such as:
     * <pre>
     * protocols:
     *   providers:
     *     http_1_1:
     *       max-prologue-length: 8192
     *     http_2:
     *       max-frame-size: 4096
     *     websocket:
     *       ....
     * </pre>
     *
     * @return all defined protocol configurations, loaded from service loader by default
     */
    @ConfiguredOption(provider = true, providerType = ProtocolConfigProvider.class)
    @Prototype.Singular
    List<ProtocolConfig> protocols();

    /**
     * Http routing. This will always be added to the resulting {@link io.helidon.webserver.Router}, if defined,
     *  overriding any HTTP routing already present.
     * If a custom listener has routing defined, it will be used, otherwise routing defined on web server will be used.
     *
     * @return HTTP Routing for this listener/server
     */
    Optional<HttpRouting> routing();

    /**
     * List of all routings (possibly for multiple protocols). This allows adding non-http protocols as well,
     * as opposed to {@link #routing()}
     *
     * @return router for this listener/server
     */
    @Prototype.Singular
    List<Routing> routings();

    /**
     * Name of this socket. Defaults to {@code @default}.
     * Must be defined if more than one socket is needed.
     *
     * @return name of the socket
     */
    @ConfiguredOption("@default")
    String name();

    /**
     * Host of the default socket. Defaults to all host addresses ({@code 0.0.0.0}).
     *
     * @return host address to listen on (for the default socket)
     */
    @ConfiguredOption("0.0.0.0")
    String host();

    /**
     * Address to use. If both this and {@link #host()} is configured, this will be used.
     *
     * @return address to use
     */
    InetAddress address();

    /**
     * Port of the default socket.
     * If configured to {@code 0} (the default), server starts on a random port.
     *
     * @return port to listen on (for the default socket)
     */
    @ConfiguredOption("0")
    int port();

    /**
     * Accept backlog.
     *
     * @return backlog
     */
    @ConfiguredOption("1024")
    int backlog();

    /**
     * Maximal number of bytes an entity may have.
     * If {@link io.helidon.http.Http.HeaderNames#CONTENT_LENGTH} is used, this is checked immediately,
     * if {@link io.helidon.http.Http.Headers#TRANSFER_ENCODING_CHUNKED} is used, we will fail when the
     * number of bytes read would exceed the max payload size.
     * Defaults to unlimited ({@code -1}).
     *
     * @return maximal number of bytes of entity
     */
    @ConfiguredOption("-1")
    long maxPayloadSize();

    /**
     * Listener receive buffer size.
     *
     * @return buffer size in bytes
     */
    @ConfiguredOption
    Optional<Integer> receiveBufferSize();

    /**
     * Number of buffers queued for write operations.
     *
     * @return maximal number of queued writes, defaults to 0
     */
    @ConfiguredOption("0")
    int writeQueueLength();

    /**
     * Initial buffer size in bytes of {@link java.io.BufferedOutputStream} created internally to
     * write data to a socket connection. Default is {@code 512}.
     *
     * @return initial buffer size used for writing
     */
    @ConfiguredOption("512")
    int writeBufferSize();

    /**
     * Grace period in ISO 8601 duration format to allow running tasks to complete before listener's shutdown.
     * Default is {@code 500} milliseconds.
     * <p>Configuration file values example: {@code PT0.5S}, {@code PT2S}.
     *
     * @return grace period
     */
    @ConfiguredOption("PT0.5S")
    Duration shutdownGracePeriod();

    /**
     * Configuration of a connection (established from client against our server).
     *
     * @return connection configuration
     */
    Optional<ConnectionConfig> connectionConfig();

    /**
     * Listener TLS configuration.
     *
     * @return tls of this configuration
     */
    @ConfiguredOption
    Optional<Tls> tls();

    /**
     * Indicates whether the TLS should be automatically reloaded.
     * This is only applicable when {@link #tls()} is set with a {@link TlsConfig#managers()} configured.
     *
     * @return tls auto reload configuration
     */
    @ConfiguredOption
    boolean tlsManagerAutoReload();

    /**
     * Configure the listener specific {@link io.helidon.http.encoding.ContentEncodingContext}.
     * This method discards all previously registered ContentEncodingContext.
     * If no content encoding context is registered, content encoding context of the webserver would be used.
     *
     * @return content encoding context
     */
    @ConfiguredOption
    Optional<ContentEncodingContext> contentEncoding();

    /**
     * Configure the listener specific {@link io.helidon.http.media.MediaContext}.
     * This method discards all previously registered MediaContext.
     * If no media context is registered, media context of the webserver would be used.
     *
     * @return media context
     */
    @ConfiguredOption
    Optional<MediaContext> mediaContext();

    /**
     * Options for connections accepted by this listener.
     * This is not used to setup server connection.
     *
     * @return socket options
     */
    @ConfiguredOption
    SocketOptions connectionOptions();

    /**
     * Limits the number of connections that can be opened at a single point in time.
     * Defaults to {@code -1}, meaning "unlimited" - what the system allows.
     *
     * @return number of TCP connections that can be opened to this listener, regardless of protocol
     */
    @ConfiguredOption("-1")
    int maxTcpConnections();

    /**
     * Limits the number of requests that can be executed at the same time (the number of active virtual threads of requests).
     * Defaults to {@code -1}, meaning "unlimited" - what the system allows.
     * Also make sure that this number is higher than the expected time it takes to handle a single request in your application,
     * as otherwise you may stop in-progress requests.
     *
     * @return number of requests that can be processed on this listener, regardless of protocol
     */
    @ConfiguredOption("-1")
    int maxConcurrentRequests();

    /**
     * How long should we wait before closing a connection that has no traffic on it.
     * Defaults to {@code PT5M} (5 minutes). Note that the timestamp is refreshed max. once per second, so this setting
     * would be useless if configured for shorter periods of time (also not a very good support for connection keep alive,
     * if the connections are killed so soon anyway).
     *
     * @return timeout of idle connections
     */
    @ConfiguredOption("PT5M")
    Duration idleConnectionTimeout();

    /**
     * How often should we check for {@link #idleConnectionTimeout()}.
     * Defaults to {@code PT2M} (2 minutes).
     *
     * @return period of checking for idle connections
     */
    @ConfiguredOption("PT2M")
    Duration idleConnectionPeriod();

    /**
     * If the entity is expected to be smaller that this number of bytes, it would be buffered in memory to optimize
     * performance when writing it.
     * If bigger, streaming will be used.
     * <p>
     * Note that for some entity types we cannot use streaming, as they are already fully in memory (String, byte[]), for such
     * cases, this option is ignored.
     * <p>
     * Default is 128Kb.
     *
     * @return maximal number of bytes to buffer in memory for supported writers
     */
    @ConfiguredOption("131072")
    int maxInMemoryEntity();

    /**
     * Server listener socket options.
     * Unless configured through builder, {@code SO_REUSEADDR} is set to {@code true},
     * and {@code SO_RCVBUF} is set to {@code 4096}.
     *
     * @return custom socket options
     */
    @Prototype.Singular
    @Prototype.SameGeneric
    Map<SocketOption<?>, Object> listenerSocketOptions();

    /**
     * Explicitly defined connection selectors to be used with this socket.
     * This list is augmented with the result of {@link #protocols()}, but custom selectors are always used first.
     *
     * @return connection selectors to be used for this socket
     */
    @Prototype.Singular
    List<ServerConnectionSelector> connectionSelectors();

    /**
     * Direct handlers specific for this listener.
     * A direct handler takes care of problems that happen before (or outside of) routing, such as bad request.
     *
     * @return direct handlers
     */
    Optional<DirectHandlers> directHandlers();

    /**
     * Listener scoped context to be used as a parent for webserver request contexts (if used).
     * If an explicit context is used, you need to take care of correctly configuring its parent.
     * It is expected that the parent of this context is the WebServer context. You should also configure explicit
     * WebServer context when using this method
     *
     * @return listener context
     * @see WebServerConfig#serverContext()
     */
    Optional<Context> listenerContext();

    /**
     * Requested URI discovery context.
     *
     * @return discovery context
     */
    RequestedUriDiscoveryContext requestedUriDiscoveryContext();

    /**
     * Update the server socket with configured socket options.
     *
     * @param socket socket to update
     */
    @SuppressWarnings("unchecked")
    default void configureSocket(ServerSocket socket) {
        for (Map.Entry<SocketOption<?>, Object> entry : listenerSocketOptions().entrySet()) {
            try {
                SocketOption<Object> opt = (SocketOption<Object>) entry.getKey();
                socket.setOption(opt, entry.getValue());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    class ConfigDecorator implements Prototype.BuilderDecorator<ListenerConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(ListenerConfig.BuilderBase<?, ?> target) {
            String name = target.name();
            if (name == null && target.config().isPresent()) {
                Config config = target.config().get();
                if (config.exists()) {
                    target.name(config.get("name").asString().orElse(config.name()));
                }
            }
            name = target.name();
            if (name == null) {
                target.name(WebServer.DEFAULT_SOCKET_NAME);
            }

            if (target.connectionOptions().isEmpty()) {
                target.connectionOptions(SocketOptions.create());
            }
            if (target.address().isEmpty()) {
                try {
                    target.address(InetAddress.getByName(target.host()));
                } catch (UnknownHostException e) {
                    throw new IllegalArgumentException("Failed to get address from host: " + target.host(), e);
                }
            }
            Map<SocketOption<?>, Object> socketOptions = target.listenerSocketOptions();
            if (!socketOptions.containsKey(StandardSocketOptions.SO_REUSEADDR)) {
                target.putListenerSocketOption(StandardSocketOptions.SO_REUSEADDR, true);
            }
            if (!socketOptions.containsKey(StandardSocketOptions.SO_RCVBUF)) {
                target.putListenerSocketOption(StandardSocketOptions.SO_RCVBUF, 4096);
            }
            if (target.requestedUriDiscoveryContext().isEmpty()) {
                target.requestedUriDiscoveryContext(RequestedUriDiscoveryContext.builder()
                                                            .socketId(target.name())
                                                            .build());
            }
        }
    }
}
