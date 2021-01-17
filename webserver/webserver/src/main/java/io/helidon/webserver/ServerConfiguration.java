/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import io.helidon.common.context.Context;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.DeprecatedConfig;

import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;

/**
 * {@link WebServer} configuration.
 */
public interface ServerConfiguration extends SocketConfiguration {

    /**
     * The default server socket configuration name. All the default server socket
     * configuration (e.g., {@link #port()} or {@link #backlog()}) is accessible through
     * {@link #socket(String)} or {@link #sockets()} with this
     * {@link io.helidon.webserver.WebServer#DEFAULT_SOCKET_NAME default socket name}.
     *
     * @deprecated since 2.0.0, please use {@link WebServer#DEFAULT_SOCKET_NAME}
     */
    @Deprecated
    String DEFAULT_SOCKET_NAME = WebServer.DEFAULT_SOCKET_NAME;

    /**
     * Returns the count of threads in the pool used to process HTTP requests.
     * <p>
     * Default value is {@link Runtime#availableProcessors()}.
     *
     * @return a workers count
     */
    int workersCount();

    /**
     * Returns a server port to listen on with the default server socket. If port is
     * {@code 0} then any available ephemeral port will be used.
     * <p>
     * Additional named server socket configuration is accessible through
     * the {@link #socket(String)} and {@link #sockets()} methods.
     *
     * @return the server port of the default server socket
     */
    @Override
    int port();

    /**
     * Returns local address where the server listens on with the default server socket.
     * If {@code null} then listens an all local addresses.
     * <p>
     * Additional named server socket configuration is accessible through
     * the {@link #socket(String)} and {@link #sockets()} methods.
     *
     * @return an address to bind with the default server socket; {@code null} for all local addresses
     */
    @Override
    InetAddress bindAddress();

    /**
     * Returns a maximum length of the queue of incoming connections on the default server
     * socket.
     * <p>
     * Default value is {@link SocketConfiguration#DEFAULT_BACKLOG_SIZE}.
     * <p>
     * Additional named server socket configuration is accessible through
     * the {@link #socket(String)} and {@link #sockets()} methods.
     *
     * @return a maximum length of the queue of incoming connections
     */
    @Override
    int backlog();

    /**
     * Returns a default server socket timeout in milliseconds or {@code 0} for an infinite timeout.
     * <p>
     * Additional named server socket configuration is accessible through
     * the {@link #socket(String)} and {@link #sockets()} methods.
     *
     * @return a default server socket timeout in milliseconds or {@code 0}
     */
    @Override
    int timeoutMillis();

    /**
     * Returns proposed value of the TCP receive window that is advertised to the remote peer on the
     * default server socket.
     * <p>
     * If {@code 0} then use implementation default.
     * <p>
     * Additional named server socket configuration is accessible through
     * the {@link #socket(String)} and {@link #sockets()} methods.
     *
     * @return a buffer size in bytes of the default server socket or {@code 0}
     */
    @Override
    int receiveBufferSize();

    /**
     * Returns a {@link SSLContext} to use with the default server socket. If not {@code null} then
     * the server enforces an SSL communication.
     * <p>
     * Additional named server socket configuration is accessible through
     * the {@link #socket(String)} and {@link #sockets()} methods.
     *
     * @return a SSL context to use
     */
    @Override
    SSLContext ssl();

    /**
     * A socket configuration of an additional named server socket.
     * <p>
     * An additional named server socket may have a dedicated {@link Routing} configured
     * through {@link io.helidon.webserver.WebServer.Builder#addNamedRouting(String, Routing)}.
     *
     * @param name the name of the additional server socket
     * @return an additional named server socket configuration or {@code null} if there is no such
     * named server socket
     * @deprecated since 2.0.0, please use {@link #namedSocket(String)} instead
     */
    @Deprecated
    default SocketConfiguration socket(String name) {
        return namedSocket(name).orElse(null);
    }

    /**
     * A socket configuration of an additional named server socket.
     * <p>
     * An additional named server socket may have a dedicated {@link Routing} configured
     * through {@link io.helidon.webserver.WebServer.Builder#addNamedRouting(String, Routing)}.
     *
     * @param name the name of the additional server socket
     * @return an additional named server socket configuration or {@code empty} if there is no such
     * named server socket configured
     */
    default Optional<SocketConfiguration> namedSocket(String name) {
        return Optional.ofNullable(sockets().get(name));
    }

    /**
     * A map of all the configured server sockets; that is the default server socket
     * which is identified by the key {@link #DEFAULT_SOCKET_NAME} and also all the additional
     * named server socket configurations.
     * <p>
     * An additional named server socket may have a dedicated {@link Routing} configured
     * through {@link io.helidon.webserver.WebServer.Builder#addNamedRouting(String, Routing)}.
     *
     * @return a map of all the configured server sockets, never null
     */
    Map<String, SocketConfiguration> sockets();

    /**
     * Returns an <a href="http://opentracing.io">opentracing.io</a> tracer. Default is {@link GlobalTracer}.
     *
     * @return a tracer to use - never {@code null} (defaulting to {@link GlobalTracer}
     */
    Tracer tracer();

    /**
     * The top level {@link io.helidon.common.context.Context} to be used by this webserver.
     * @return a context instance with registered application scoped instances
     */
    Context context();

    /**
     * Returns an {@link ExperimentalConfiguration}.
     *
     * @return Experimental configuration.
     */
    ExperimentalConfiguration experimental();

    /**
     * Returns an optional {@link Transport}.
     *
     * @return an optional {@link Transport}
     */
    default Optional<Transport> transport() {
        return Optional.ofNullable(null);
    }

    /**
     * Whether to print details of {@link io.helidon.common.HelidonFeatures}.
     *
     * @return whether to print details
     */
    boolean printFeatureDetails();

    /**
     * Checks if HTTP/2 is enabled in config.
     *
     * @return Outcome of test.
     */
    default boolean isHttp2Enabled() {
        ExperimentalConfiguration experimental = experimental();
        return experimental != null && experimental.http2() != null && experimental.http2().enable();
    }

    /**
     * Creates new instance with defaults from external configuration source.
     *
     * @param config the externalized configuration
     * @return a new instance
     */
    static ServerConfiguration create(Config config) {
        return builder(config).build();
    }

    /**
     * Creates new instance of a {@link Builder server configuration builder}.
     *
     * @return a new builder instance
     *
     * @deprecated since 2.0.0 - please use {@link io.helidon.webserver.WebServer#builder()} instead
     */
    @Deprecated
    static Builder builder() {
        return new Builder();
    }

    /**
     * Creates new instance of a {@link Builder server configuration builder} with defaults from external configuration source.
     *
     * @param config the externalized configuration
     * @return a new builder instance
     * @deprecated since 2.0.0 - please use {@link io.helidon.webserver.WebServer#builder()}, then
     * {@link WebServer.Builder#config(io.helidon.config.Config)}, or
     * {@link io.helidon.webserver.WebServer#create(Routing, io.helidon.config.Config)}
     */
    @Deprecated
    static Builder builder(Config config) {
        return new Builder().config(config);
    }

    /**
     * A {@link ServerConfiguration} builder.
     *
     * @deprecated since 2.0.0 - use {@link io.helidon.webserver.WebServer.Builder} instead
     */
    @Deprecated
    final class Builder implements SocketConfiguration.SocketConfigurationBuilder<Builder>,
                                   io.helidon.common.Builder<ServerConfiguration> {

        private static final AtomicInteger WEBSERVER_COUNTER = new AtomicInteger(1);
        private final SocketConfiguration.Builder defaultSocketBuilder = SocketConfiguration.builder();
        private final Map<String, SocketConfiguration> sockets = new HashMap<>();
        private int workers;
        private Tracer tracer;
        private ExperimentalConfiguration experimental;
        private Optional<Transport> transport;
        private Context context;
        private boolean printFeatureDetails;

        private Builder() {
            transport = Optional.ofNullable(null);
        }

        /**
         * Sets {@link SSLContext} to to use with the server. If not {@code null} then server enforce SSL communication.
         *
         * @param sslContext ssl context
         * @return an updated builder
         */
        public Builder ssl(SSLContext sslContext) {
            defaultSocketBuilder.ssl(sslContext);
            return this;
        }

        /**
         * Sets {@link SSLContext} to to use with the server. If not {@code null} then server enforce SSL communication.
         *
         * @param sslContextBuilder ssl context builder; will be built as a first step of this method execution
         * @return an updated builder
         */
        public Builder ssl(Supplier<? extends SSLContext> sslContextBuilder) {
            defaultSocketBuilder.ssl(sslContextBuilder);
            return this;
        }

        /**
         * Sets server port. If port is {@code 0} or less then any available ephemeral port will be used.
         * <p>
         * Configuration key: {@code port}
         *
         * @param port the server port
         * @return an updated builder
         */
        public Builder port(int port) {
            defaultSocketBuilder.port(port);
            return this;
        }

        /**
         * Sets a local address for server to bind. If {@code null} then listens an all local addresses.
         * <p>
         * Configuration key: {@code bind-address}
         *
         * @param bindAddress the address to bind the server or {@code null} for all local addresses
         * @return an updated builder
         */
        public Builder bindAddress(InetAddress bindAddress) {
            this.defaultSocketBuilder.bindAddress(bindAddress);
            return this;
        }

        /**
         * Sets a maximum length of the queue of incoming connections. Default value is {@code 1024}.
         * <p>
         * Configuration key: {@code backlog}
         *
         * @param size the maximum length of the queue of incoming connections
         * @return an updated builder
         */
        public Builder backlog(int size) {
            this.defaultSocketBuilder.backlog(size);
            return this;
        }

        /**
         * Sets a socket timeout in milliseconds or {@code 0} for infinite timeout.
         * <p>
         * Configuration key: {@code timeout}
         *
         * @param milliseconds a socket timeout in milliseconds or {@code 0}
         * @return an updated builder
         */
        public Builder timeout(int milliseconds) {
            this.defaultSocketBuilder.timeoutMillis(milliseconds);
            return this;
        }

        /**
         * Propose value of the TCP receive window that is advertised to the remote peer.
         * If {@code 0} then implementation default is used.
         * <p>
         * Configuration key: {@code receive-buffer}
         *
         * @param bytes a buffer size in bytes or {@code 0}
         * @return an updated builder
         */
        public Builder receiveBufferSize(int bytes) {
            this.defaultSocketBuilder.receiveBufferSize(bytes);
            return this;
        }

        @Override
        public Builder maxHeaderSize(int size) {
            defaultSocketBuilder.maxHeaderSize(size);
            return this;
        }

        @Override
        public Builder maxInitialLineLength(int length) {
            defaultSocketBuilder.maxInitialLineLength(length);
            return this;
        }

        /**
         * Adds an additional named server socket configuration. As a result, the server will listen
         * on multiple ports.
         * <p>
         * An additional named server socket may have a dedicated {@link Routing} configured
         * through {@link io.helidon.webserver.WebServer.Builder#addNamedRouting(String, Routing)}.
         *
         * @param name        the name of the additional server socket configuration
         * @param port        the port to bind; if {@code 0} or less, any available ephemeral port will be used
         * @param bindAddress the address to bind; if {@code null}, all local addresses will be bound
         * @return an updated builder
         *
         * @deprecated since 2.0.0, please use {@link #addSocket(String, SocketConfiguration)} instead
         */
        @Deprecated
        public Builder addSocket(String name, int port, InetAddress bindAddress) {
            Objects.requireNonNull(name, "Parameter 'name' must not be null!");
            return addSocket(name, SocketConfiguration.builder()
                    .port(port)
                    .bindAddress(bindAddress));
        }

        /**
         * Adds an additional named server socket configuration. As a result, the server will listen
         * on multiple ports.
         * <p>
         * An additional named server socket may have a dedicated {@link Routing} configured
         * through {@link io.helidon.webserver.WebServer.Builder#addNamedRouting(String, Routing)}.
         *
         * @param name                the name of the additional server socket configuration
         * @param socketConfiguration the additional named server socket configuration
         * @return an updated builder
         */
        public Builder addSocket(String name, SocketConfiguration socketConfiguration) {
            Objects.requireNonNull(name, "Parameter 'name' must not be null!");
            this.sockets.put(name, socketConfiguration);
            return this;
        }

        /**
         * Adds an additional named server socket configuration builder. As a result, the server will listen
         * on multiple ports.
         * <p>
         * An additional named server socket may have a dedicated {@link Routing} configured
         * through {@link io.helidon.webserver.WebServer.Builder#addNamedRouting(String, Routing)}.
         *
         * @param name                       the name of the additional server socket configuration
         * @param socketConfigurationBuilder the additional named server socket configuration builder; will be built as
         *                                   a first step of this method execution
         * @return an updated builder
         */
        public Builder addSocket(String name, Supplier<SocketConfiguration> socketConfigurationBuilder) {
            Objects.requireNonNull(name, "Parameter 'name' must not be null!");

            return addSocket(name, socketConfigurationBuilder != null ? socketConfigurationBuilder.get() : null);
        }

        /**
         * Sets a count of threads in pool used to process HTTP requests.
         * Default value is {@code CPU_COUNT * 2}.
         * <p>
         * Configuration key: {@code workers}
         *
         * @param workers a workers count
         * @return an updated builder
         */
        public Builder workersCount(int workers) {
            this.workers = workers;
            return this;
        }

        /**
         * Sets an <a href="http://opentracing.io">opentracing.io</a> tracer. (Default is {@link GlobalTracer}.)
         *
         * @param tracer a tracer to set
         * @return an updated builder
         */
        public Builder tracer(Tracer tracer) {
            this.tracer = tracer;
            return this;
        }

        /**
         * Sets an <a href="http://opentracing.io">opentracing.io</a> tracer. (Default is {@link GlobalTracer}.)
         *
         * @param tracerBuilder a tracer builder to set; will be built as a first step of this method execution
         * @return updated builder
         */
        public Builder tracer(Supplier<? extends Tracer> tracerBuilder) {
            return tracer(tracerBuilder.get());
        }

        /**
         * Configures the SSL protocols to enable with the default server socket.
         * @param protocols protocols to enable, if {@code null} enables the
         * default protocols
         * @return an updated builder
         */
        public Builder enabledSSlProtocols(String... protocols) {
            this.defaultSocketBuilder.enabledSSlProtocols(protocols);
            return this;
        }

        /**
         * Configures the SSL protocols to enable with the default server socket.
         * @param protocols protocols to enable, if {@code null} or empty enables
         *  the default protocols
         * @return an updated builder
         */
        public Builder enabledSSlProtocols(List<String> protocols) {
            this.defaultSocketBuilder.enabledSSlProtocols(protocols);
            return this;
        }

        /**
         * Configure maximum client payload size.
         * @param size maximum payload size
         * @return an updated builder
         */
        @Override
        public Builder maxPayloadSize(long size) {
            this.defaultSocketBuilder.maxPayloadSize(size);
            return this;
        }

        /**
         * Configure experimental features.
         * @param experimental experimental configuration
         * @return an updated builder
         */
        public Builder experimental(ExperimentalConfiguration experimental) {
            this.experimental = experimental;
            return this;
        }

        /**
         * Configure transport.
         * @param transport a {@link Transport}
         * @return an updated builder
         */
        public Builder transport(Transport transport) {
            this.transport = Optional.of(transport);
            return this;
        }

        /**
         * Set to {@code true} to print detailed feature information on startup.
         *
         * @param print whether to print details or not
         * @return updated builder instance
         * @see io.helidon.common.HelidonFeatures
         */
        public Builder printFeatureDetails(boolean print) {
            this.printFeatureDetails = print;
            return this;
        }

        /**
         * Configure the application scoped context to be used as a parent for webserver request contexts.
         * @param context top level context
         * @return an updated builder
         */
        public Builder context(Context context) {
            this.context = context;

            return this;
        }

        private InetAddress string2InetAddress(String address) {
            try {
                return InetAddress.getByName(address);
            } catch (UnknownHostException e) {
                throw new ConfigException("Illegal value of 'bind-address' configuration key. Expecting host or ip address!", e);
            }
        }

        /**
         * Sets configuration values included in provided {@link Config} parameter.
         * <p>
         * It can be used for configuration externalisation.
         * <p>
         * All parameters sets before this method call can be seen as <i>defaults</i> and all parameters sets after can be seen
         * as forced.
         *
         * @param config the configuration to use
         * @return an updated builder
         */
        public Builder config(Config config) {
            if (config == null) {
                return this;
            }

            defaultSocketBuilder.config(config);

            DeprecatedConfig.get(config, "worker-count", "workers")
                    .asInt()
                    .ifPresent(this::workersCount);

            config.get("features.print-details").asBoolean().ifPresent(this::printFeatureDetails);

            // sockets
            Config socketsConfig = config.get("sockets");
            if (socketsConfig.exists()) {
                List<Config> socketConfigs = socketsConfig.asNodeList().orElse(List.of());
                for (Config socketConfig : socketConfigs) {
                    // the whole section checking the socket name can be removed
                    // when we remove deprecated methods with socket name on server builder
                    String socketName;

                    String nodeName = socketConfig.name();
                    Optional<String> maybeSocketName = socketConfig.get("name").asString().asOptional();

                    socketName = maybeSocketName.orElse(nodeName);

                    // log warning for deprecated config
                    try {
                        Integer.parseInt(nodeName);
                        if (socketName.equals(nodeName) && maybeSocketName.isEmpty()) {
                            throw new ConfigException("Cannot find \"name\" key for socket configuration " + socketConfig.key());
                        }
                    } catch (NumberFormatException e) {
                        // this is old approach
                        Logger.getLogger(SocketConfigurationBuilder.class.getName())
                                .warning("Socket configuration at " + socketConfig.key() + " is deprecated. Please use an array "
                                                 + "with \"name\" key to define the socket name.");
                    }

                    SocketConfiguration socket = SocketConfiguration.builder()
                            .name(socketName)
                            .config(socketConfig)
                            .build();

                    sockets.put(socket.name(), socket);
                }
            }

            // experimental
            Config experimentalConfig = config.get("experimental");
            if (experimentalConfig.exists()) {
                ExperimentalConfiguration.Builder experimentalBuilder = ExperimentalConfiguration.builder();
                Config http2Config = experimentalConfig.get("http2");
                if (http2Config.exists()) {
                    Http2Configuration.Builder http2Builder = new Http2Configuration.Builder();
                    http2Config.get("enable").asBoolean().ifPresent(http2Builder::enable);
                    http2Config.get("max-content-length").asInt().ifPresent(http2Builder::maxContentLength);
                    experimentalBuilder.http2(http2Builder.build());
                }
                experimental = experimentalBuilder.build();
            }

            return this;
        }

        /**
         * Builds a new configuration instance.
         *
         * @return a new instance
         */
        @Override
        public ServerConfiguration build() {
            if (null == context) {
                // I do not expect "unlimited" number of webservers
                // in case somebody spins a huge number up, the counter will cycle to negative numbers once
                // Integer.MAX_VALUE is reached.
                context = Context.builder()
                        .id("web-" + WEBSERVER_COUNTER.getAndIncrement())
                        .build();
            }

            Optional<Tracer> maybeTracer = context.get(Tracer.class);

            if (null == this.tracer) {
                this.tracer = maybeTracer.orElseGet(GlobalTracer::get);
            }

            if (!maybeTracer.isPresent()) {
                context.register(this.tracer);
            }

            if (workers <= 0) {
                workers = Runtime.getRuntime().availableProcessors();
            }

            if (null == experimental) {
                experimental = ExperimentalConfiguration.builder().build();
            }

            return new ServerBasicConfig(this);
        }

        SocketConfiguration.Builder defaultSocketBuilder() {
            return defaultSocketBuilder;
        }

        Map<String, SocketConfiguration> sockets() {
            return sockets;
        }

        int workers() {
            return workers;
        }

        Tracer tracer() {
            return tracer;
        }

        ExperimentalConfiguration experimental() {
            return experimental;
        }

        Optional<Transport> transport() {
            return transport;
        }

        Context context() {
            return context;
        }

        boolean printFeatureDetails() {
            return printFeatureDetails;
        }

        @Override
        public Builder timeout(long amount, TimeUnit unit) {
            this.defaultSocketBuilder.timeout(amount, unit);
            return this;
        }

        @Override
        public Builder tls(WebServerTls webServerTls) {
            this.defaultSocketBuilder.tls(webServerTls);
            return this;
        }

        @Override
        public Builder enableCompression(boolean value) {
            this.defaultSocketBuilder.enableCompression(true);
            return this;
        }
    }
}
