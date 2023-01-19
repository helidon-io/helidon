/*
 * Copyright (c) 2017, 2023 Oracle and/or its affiliates.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.helidon.common.configurable.AllowList;
import io.helidon.common.context.Context;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.media.common.MediaContext;
import io.helidon.media.common.MediaContextBuilder;
import io.helidon.media.common.MediaSupport;
import io.helidon.media.common.MessageBodyReader;
import io.helidon.media.common.MessageBodyReaderContext;
import io.helidon.media.common.MessageBodyStreamReader;
import io.helidon.media.common.MessageBodyStreamWriter;
import io.helidon.media.common.MessageBodyWriter;
import io.helidon.media.common.MessageBodyWriterContext;
import io.helidon.media.common.ParentingMediaContextBuilder;
import io.helidon.tracing.Tracer;

/**
 * Represents a immutably configured WEB server.
 * <p>
 * Provides basic lifecycle and monitoring API.
 * <p>
 * Instance can be created from {@link Routing} and optionally from {@link io.helidon.config.Config} using
 * {@link #create(Routing)}, {@link #create(Routing, io.helidon.config.Config)} or {@link #builder(Routing)}methods and
 * their builder enabled overloads.
 */
public interface WebServer {

    /**
     * The default server socket configuration name. All the default server socket
     * configuration such as {@link Builder#hasSocket(String)} or {@link WebServer#port(String)}
     * is accessible using this name.
     */
    String DEFAULT_SOCKET_NAME = "@default";

    /**
     * Gets effective server configuration.
     *
     * @return Server configuration
     */
    ServerConfiguration configuration();

    /**
     * Starts the server. Has no effect if server is running.
     * The start will fail on a server that is shut down, or that failed to start.
     * In such cases, create a new instance of Web Server.
     *
     * @return a single to react on startup process
     */
    Single<WebServer> start();

    /**
     * Completion stage is completed when server is shut down.
     *
     * @return a completion stage of the server
     */
    Single<WebServer> whenShutdown();

    /**
     * Attempt to gracefully shutdown server. It is possible to use returned {@link io.helidon.common.reactive.Single} to react.
     * <p>
     * RequestMethod can be called periodically.
     *
     * @return a single to react on finished shutdown process
     * @see #start()
     */
    Single<WebServer> shutdown();

    /**
     * Returns {@code true} if the server is currently running. Running server in stopping phase returns {@code true} until it
     * is not fully stopped.
     *
     * @return {@code true} if server is running
     */
    boolean isRunning();

    /**
     * Gets a {@link WebServer} context.
     *
     * @return a server context
     */
    Context context();

    /**
     * Get the parent {@link MessageBodyReaderContext} context.
     *
     * @return media body reader context
     */
    MessageBodyReaderContext readerContext();

    /**
     * Get the parent {@link MessageBodyWriterContext} context.
     *
     * @return media body writer context
     */
    MessageBodyWriterContext writerContext();

    /**
     * Returns a port number the default server socket is bound to and is listening on;
     * or {@code -1} if unknown or not active.
     * <p>
     * It is supported only when server is running.
     *
     * @return a listen port; or {@code -1} if unknown or the default server socket is not active
     */
    default int port() {
        return port(WebServer.DEFAULT_SOCKET_NAME);
    }

    /**
     * Returns a port number an additional named server socket is bound to and is listening on;
     * or {@code -1} if unknown or not active.
     *
     * @param socketName the name of an additional named server socket
     * @return a listen port; or {@code -1} if socket name is unknown or the server socket is not active
     */
    int port(String socketName);

    /**
     * Returns {@code true} if TLS is configured for the default socket.
     *
     * @return whether TLS is enabled for the default socket
     */
    default boolean hasTls() {
        return hasTls(WebServer.DEFAULT_SOCKET_NAME);
    }

    /**
     * Returns {@code true} if TLS is configured for the named socket.
     *
     * @param socketName the name of a socket
     * @return whether TLS is enabled for the socket, returns {@code false} if the socket does not exists
     */
    boolean hasTls(String socketName);

    /**
     * Update the TLS configuration of the default socket {@link WebServer#DEFAULT_SOCKET_NAME}.
     *
     * @param tls new TLS configuration
     * @throws IllegalStateException if {@link WebServerTls#enabled()} returns {@code false} or
     * if {@code SocketConfiguration.tls().sslContext()} returns {@code null}
     */
    void updateTls(WebServerTls tls);

    /**
     * Update the TLS configuration of the named socket.
     *
     * @param tls new TLS configuration
     * @param socketName specific named socket name
     * @throws IllegalStateException if {@link WebServerTls#enabled()} returns {@code false} or
     * if {@code SocketConfiguration.tls().sslContext()} returns {@code null}
     */
    void updateTls(WebServerTls tls, String socketName);

    /**
     * Creates new instance from provided routing and default configuration.
     *
     * @param routing a routing instance
     * @return a new web server instance
     * @throws IllegalStateException if none SPI implementation found
     * @throws NullPointerException  if 'routing' parameter is {@code null}
     */
    static WebServer create(Routing routing) {
        return builder(routing).build();
    }

    /**
     * Creates new instance from provided configuration and routing.
     *
     * @param routing       a routing instance
     * @param config        configuration located on server configuration node
     * @return a new web server instance
     * @throws NullPointerException  if 'routing' parameter is {@code null}
     *
     * @since 2.0.0
     */
    static WebServer create(Routing routing, Config config) {
        return builder(routing)
                .config(config)
                .build();
    }

    /**
     * Creates new instance from provided configuration and routing.
     *
     * @param routingBuilder  a supplier of routing (such as {@link Routing.Builder}
     * @param config        configuration located on server configuration node
     * @return a new web server instance
     * @throws NullPointerException  if 'routing' parameter is {@code null}
     *
     * @since 2.0.0
     */
    static WebServer create(Supplier<Routing> routingBuilder, Config config) {
        return builder(routingBuilder.get())
                .config(config)
                .build();
    }

    /**
     * Creates new instance from provided routing and default configuration.
     *
     * @param routingBuilder a routing builder instance that will be built as a first step
     *                       of this method execution
     * @return a new web server instance
     * @throws IllegalStateException if none SPI implementation found
     * @throws NullPointerException  if 'routing' parameter is {@code null}
     */
    static WebServer create(Supplier<? extends Routing> routingBuilder) {
        Objects.requireNonNull(routingBuilder, "Parameter 'routingBuilder' must not be null!");
        return create(routingBuilder.get());
    }

    /**
     * Creates a builder of the {@link WebServer}.
     *
     * @param routingBuilder the routing builder; must not be {@code null}
     * @return the builder
     */
    static Builder builder(Supplier<? extends Routing> routingBuilder) {
        Objects.requireNonNull(routingBuilder, "Parameter 'routingBuilder' must not be null!");
        return builder(routingBuilder.get());
    }

    /**
     * Creates a fluent API builder of the {@link io.helidon.webserver.WebServer}.
     * Before calling the {@link io.helidon.webserver.WebServer.Builder#build()} method, you should
     * configure the default routing. If none is configured, all requests will end in {@code 404}.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a builder of the {@link WebServer}.
     *
     * @param routing the routing to use for the default port; must not be {@code null}
     * @return the builder
     * @see #builder()
     */
    static Builder builder(Routing routing) {
        return builder().addRouting(routing);
    }

    /**
     * WebServer builder class provides a convenient way to set up WebServer with multiple server
     * sockets and optional multiple routings.
     */
    @Configured(root = true, prefix = "server", description = "Configuration of the HTTP server.")
    final class Builder implements io.helidon.common.Builder<Builder, WebServer>,
                                   SocketConfiguration.SocketConfigurationBuilder<Builder>,
                                   ParentingMediaContextBuilder<Builder>,
                                   MediaContextBuilder<Builder> {

        private static final Logger LOGGER = Logger.getLogger(Builder.class.getName());
        private static final MediaContext DEFAULT_MEDIA_SUPPORT = MediaContext.create();
        private final Map<String, RouterImpl.Builder> routingBuilders = new HashMap<>();
        private final DirectHandlers.Builder directHandlers = DirectHandlers.builder();
        // internal use - we may keep this even after we remove the public access to ServerConfiguration
        @SuppressWarnings("deprecation")
        private final ServerConfiguration.Builder configurationBuilder = ServerConfiguration.builder();
        // for backward compatibility
        @SuppressWarnings("deprecation")
        private ServerConfiguration explicitConfig;
        private MessageBodyReaderContext readerContext;
        private MessageBodyWriterContext writerContext;

        private Builder() {
            readerContext = MessageBodyReaderContext.create(DEFAULT_MEDIA_SUPPORT.readerContext());
            writerContext = MessageBodyWriterContext.create(DEFAULT_MEDIA_SUPPORT.writerContext());
        }

        /**
         * Builds the {@link WebServer} instance as configured by this builder and its parameters.
         *
         * @return a ready to use {@link WebServer}
         * @throws IllegalStateException if there are unpaired named routings (as described
         *                               at {@link #addNamedRouting(String, Routing)})
         */
        @Override
        public WebServer build() {
            if (routingBuilders.get(WebServer.DEFAULT_SOCKET_NAME) == null) {
                LOGGER.warning("Creating a web server with no default routing configured.");
                routingBuilders.put(WebServer.DEFAULT_SOCKET_NAME, RouterImpl.builder());
            }
            if (explicitConfig == null) {
                explicitConfig = configurationBuilder.build();
            }

            Map<String, Router> routers = routingBuilders.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, v -> v.getValue().build()));

            String unpairedRoutings =
                    routingBuilders.keySet()
                            .stream()
                            .filter(routingName -> explicitConfig.namedSocket(routingName).isEmpty())
                            .collect(Collectors.joining(", "));

            if (!unpairedRoutings.isEmpty()) {
                throw new IllegalStateException("No server socket configuration found for named routings: " + unpairedRoutings);
            }

            routers.values().forEach(Router::beforeStart);

            WebServer result = new NettyWebServer(explicitConfig,
                                                  routers,
                                                  writerContext,
                                                  readerContext,
                                                  directHandlers.build());

            RequestRouting defaultRouting = routers.get(WebServer.DEFAULT_SOCKET_NAME).routing(RequestRouting.class, null);

            if (defaultRouting != null) {
                defaultRouting.fireNewWebServer(result);
            }
            return result;
        }

        /**
         * Configure listener for the default socket.
         *
         * @param socket socket configuration builder consumer
         * @return updated builder
         */
        public Builder defaultSocket(Consumer<SocketConfiguration.Builder> socket){
            socket.accept(this.configurationBuilder.defaultSocketBuilder());
            return this;
        }

        /**
         * Configure the transport to be used by this server.
         *
         * @param transport transport to use
         * @return updated builder instance
         */
        public Builder transport(Transport transport) {
            configurationBuilder.transport(transport);
            return this;
        }

        /**
         * Configure the default routing of this WebServer. Default routing is the one
         * available on the default listen {@link #bindAddress(String) host} and {@link #port() port} of the WebServer
         *
         * @param defaultRouting new default routing; if already configured, this instance would replace the existing instance
         * @return updated builder instance
         * @deprecated Use {@link WebServer.Builder#addRouting(Routing)}
         * or {@link WebServer.Builder#addRouting(Routing)} instead.
         */
        @Deprecated(since = "3.0.0", forRemoval = true)
        public Builder routing(Routing defaultRouting) {
            addRouting(defaultRouting);
            return this;
        }

        /**
         * Configure the default routing of this WebServer. Default routing is the one
         * available on the default listen {@link #bindAddress(String) host} and {@link #port() port} of the WebServer
         *
         * @param routing new default routing; if already configured, this instance would replace the existing instance
         * @return updated builder instance
         */
        public Builder addRouting(Routing routing) {
            Objects.requireNonNull(routing);
            routingBuilders.computeIfAbsent(WebServer.DEFAULT_SOCKET_NAME, s -> RouterImpl.builder())
                    .addRouting(routing);
            return this;
        }

        /**
         * Configure the default routing of this WebServer. Default routing is the one
         * available on the default listen {@link #bindAddress(String) host} and {@link #port() port} of the WebServer
         *
         * @param routingSupplier new default routing; if already configured, this instance would replace the existing instance
         * @return updated builder instance
         */
        public Builder addRouting(Supplier<Routing> routingSupplier) {
            Objects.requireNonNull(routingSupplier);
            addRouting(routingSupplier.get());
            return this;
        }

        /**
         * Configure the default routing of this WebServer. Default routing is the one
         * available on the default listen {@link #bindAddress(String) host} and {@link #port() port} of the WebServer
         *
         * @param defaultRouting new default routing; if already configured, this instance would replace the existing instance
         * @return updated builder instance
         */
        public Builder routing(Supplier<Routing> defaultRouting) {
            addRouting(Objects.requireNonNull(defaultRouting).get());
            return this;
        }

        /**
         * Configure the default routing of this WebServer. Default routing is the one
         * available on the default listen {@link #bindAddress(String) host} and {@link #port() port} of the WebServer
         *
         * @param routing new default routing; if already configured, this instance would replace the existing instance
         * @return updated builder instance
         */
        public Builder routing(Consumer<Routing.Builder> routing) {
            Routing.Builder builder = Routing.builder();
            Objects.requireNonNull(routing).accept(builder);
            routingBuilders.computeIfAbsent(WebServer.DEFAULT_SOCKET_NAME, s -> RouterImpl.builder())
                    .addRoutingBuilder(RequestRouting.class, builder);
            return this;
        }

        /**
         * Update this server configuration from the config provided.
         *
         * @param config config located on server node
         * @return an updated builder
         * @since 2.0.0
         */
        public Builder config(Config config) {
            this.configurationBuilder.config(config);
            return this;
        }

        /**
         * Associates a dedicated routing with an additional server socket configuration.
         * <p>
         * The additional server socket configuration must be set as per
         * {@link ServerConfiguration.Builder#addSocket(String, SocketConfiguration)}. If there is no such
         * named server socket configuration, a {@link IllegalStateException} is thrown by the
         * {@link #build()} method.
         *
         * @param name    the named server socket configuration to associate the provided routing with
         * @param routing the routing to associate with the provided name of a named server socket
         *                configuration
         * @return an updated builder
         */
        public Builder addNamedRouting(String name, Routing routing) {
            Objects.requireNonNull(name, "Parameter 'name' must not be null!");
            Objects.requireNonNull(routing, "Parameter 'routing' must not be null!");
            routingBuilders.computeIfAbsent(name, s -> RouterImpl.builder())
                    .addRouting(routing);
            return this;
        }

        /**
         * Associates a dedicated routing with an additional server socket configuration.
         * <p>
         * The additional server socket configuration must be set as per
         * {@link ServerConfiguration.Builder#addSocket(String, SocketConfiguration)}. If there is no such
         * named server socket configuration, a {@link IllegalStateException} is thrown by the
         * {@link #build()} method.
         *
         * @param name           the named server socket configuration to associate the provided routing with
         * @param routingBuilder the routing builder to associate with the provided name of a named server socket
         *                       configuration; will be built as a first step of this method execution
         * @return an updated builder
         */
        public Builder addNamedRouting(String name, Supplier<Routing> routingBuilder) {
            Objects.requireNonNull(name, "Parameter 'name' must not be null!");
            Objects.requireNonNull(routingBuilder, "Parameter 'routingBuilder' must not be null!");

            return addNamedRouting(name, routingBuilder.get());
        }

        @Override
        public Builder mediaContext(MediaContext mediaContext) {
            Objects.requireNonNull(mediaContext);
            this.readerContext = MessageBodyReaderContext.create(mediaContext.readerContext());
            this.writerContext = MessageBodyWriterContext.create(mediaContext.writerContext());
            return this;
        }

        @Override
        public Builder addMediaSupport(MediaSupport mediaSupport) {
            Objects.requireNonNull(mediaSupport);
            mediaSupport.register(readerContext, writerContext);
            return this;
        }

        @Override
        public Builder addReader(MessageBodyReader<?> reader) {
            readerContext.registerReader(reader);
            return this;
        }

        @Override
        public Builder addStreamReader(MessageBodyStreamReader<?> streamReader) {
            readerContext.registerReader(streamReader);
            return this;
        }

        @Override
        public Builder addWriter(MessageBodyWriter<?> writer) {
            writerContext.registerWriter(writer);
            return this;
        }

        @Override
        public Builder addStreamWriter(MessageBodyStreamWriter<?> streamWriter) {
            writerContext.registerWriter(streamWriter);
            return this;
        }

        @Override
        public Builder port(int port) {
            configurationBuilder.port(port);
            return this;
        }

        @Override
        public Builder bindAddress(InetAddress bindAddress) {
            configurationBuilder.bindAddress(bindAddress);
            return this;
        }

        @Override
        public Builder backlog(int backlog) {
            configurationBuilder.backlog(backlog);
            return this;
        }

        @Override
        public Builder timeout(long amount, TimeUnit unit) {
            configurationBuilder.timeout(amount, unit);
            return this;
        }

        @Override
        public Builder receiveBufferSize(int receiveBufferSize) {
            configurationBuilder.receiveBufferSize(receiveBufferSize);
            return this;
        }

        @Override
        public Builder tls(WebServerTls webServerTls) {
            configurationBuilder.tls(webServerTls);
            return this;
        }

        @Override
        public Builder maxHeaderSize(int size) {
            configurationBuilder.maxHeaderSize(size);
            return this;
        }

        @Override
        public Builder maxInitialLineLength(int length) {
            configurationBuilder.maxInitialLineLength(length);
            return this;
        }

        @Override
        public Builder enableCompression(boolean value) {
            configurationBuilder.enableCompression(value);
            return this;
        }

        @Override
        public Builder maxPayloadSize(long size) {
            configurationBuilder.maxPayloadSize(size);
            return this;
        }

        @Override
        public Builder backpressureBufferSize(long backpressureBufferSize) {
            configurationBuilder.backpressureBufferSize(backpressureBufferSize);
            return this;
        }

        @Override
        public Builder backpressureStrategy(BackpressureStrategy backpressureStrategy) {
            configurationBuilder.backpressureStrategy(backpressureStrategy);
            return this;
        }

        @Override
        public Builder continueImmediately(boolean continueImmediately) {
            configurationBuilder.continueImmediately(continueImmediately);
            return this;
        }

        @Override
        public Builder maxUpgradeContentLength(int size) {
            configurationBuilder.maxUpgradeContentLength(size);
            return this;
        }

        @Override
        public Builder addRequestedUriDiscoveryType(SocketConfiguration.RequestedUriDiscoveryType type) {
            defaultSocket(it -> it.addRequestedUriDiscoveryType(type));
            return this;
        }

        @Override
        public Builder requestedUriDiscoveryTypes(List<SocketConfiguration.RequestedUriDiscoveryType> types) {
            defaultSocket(it -> it.requestedUriDiscoveryTypes(types));
            return this;
        }

        @Override
        public Builder requestedUriDiscoveryEnabled(boolean enabled) {
            defaultSocket(it -> it.requestedUriDiscoveryEnabled(enabled));
            return this;
        }

        @Override
        public Builder trustedProxies(AllowList trustedProxies) {
            defaultSocket(it -> it.trustedProxies(trustedProxies));
            return this;
        }

        /**
         * A helper method to support fluentAPI when invoking another method.
         * <p>
         * Example:
         * <pre>
         *     WebServer.Builder builder = WebServer.builder();
         *     updateBuilder(builder);
         *     return builder.build();
         * </pre>
         * Can be changed to:
         * <pre>
         *     return WebServer.builder()
         *              .update(this::updateBuilder)
         *              .build();
         * </pre>
         *
         *
         * @param updateFunction function to update this builder
         * @return an updated builder
         */
        public Builder update(Consumer<Builder> updateFunction) {
            updateFunction.accept(this);
            return this;
        }

        /**
         * Adds an additional named server socket configuration. As a result, the server will listen
         * on multiple ports.
         * <p>
         * An additional named server socket may have a dedicated {@link Routing} configured
         * through {@link io.helidon.webserver.WebServer.Builder#addNamedRouting(String, Routing)}.
         *
         * @param config the additional named server socket configuration, never null
         * @return an updated builder
         */
        @ConfiguredOption(key = "sockets", kind = ConfiguredOption.Kind.LIST)
        public Builder addSocket(SocketConfiguration config) {
            configurationBuilder.addSocket(config.name(), config);
            return this;
        }

        /**
         * Adds or augment existing named server socket configuration.
         * <p>
         * An additional named server socket may have a dedicated {@link Routing} configured
         * through {@link io.helidon.webserver.WebServer.Builder#addNamedRouting(String, Routing)}.
         *
         * @param name socket name
         * @param socket new or existing configuration builder
         * @return an updated builder
         */
        public Builder socket(String name, Consumer<SocketConfiguration.Builder> socket) {
            socket.accept(configurationBuilder.socketBuilder(name));
            return this;
        }

        /**
         * Adds or augment existing named server socket configuration.
         * <p>
         * An additional named server socket have a dedicated {@link Routing} configured
         * through supplied routing builder.
         *
         * @param socketName socket name
         * @param builders consumer with socket configuration and dedicated routing builders
         * @return an updated builder
         */
        public Builder socket(String socketName, BiConsumer<SocketConfiguration.Builder, Router.Builder> builders) {
            builders.accept(
                    this.configurationBuilder.socketBuilder(socketName),
                    routingBuilders.computeIfAbsent(socketName, s -> RouterImpl.builder())
            );
            return this;
        }

        /**
         * Adds an additional named server socket configuration builder. As a result, the server will listen
         * on multiple ports.
         * <p>
         * An additional named server socket may have a dedicated {@link Routing} configured
         * through {@link io.helidon.webserver.WebServer.Builder#addNamedRouting(String, Routing)}.
         *
         * @param socketConfigurationBuilder the additional named server socket configuration builder; will be built as
         *                                   a first step of this method execution
         * @return an updated builder
         */
        public Builder addSocket(Supplier<SocketConfiguration> socketConfigurationBuilder) {
            SocketConfiguration socketConfiguration = socketConfigurationBuilder.get();

            configurationBuilder.addSocket(socketConfiguration.name(), socketConfiguration);
            return this;
        }

        /**
         * Add a named socket and routing.
         *
         * @param socketConfiguration named configuration of the socket
         * @param routing routing to use for this socket
         *
         * @return an updated builder
         */
        public Builder addSocket(SocketConfiguration socketConfiguration, Routing routing) {
            addSocket(socketConfiguration);
            addNamedRouting(socketConfiguration.name(), routing);
            return this;
        }


        /**
         * Sets a tracer.
         *
         * @param tracer a tracer to set
         * @return an updated builder
         */
        public Builder tracer(Tracer tracer) {
            configurationBuilder.tracer(tracer);
            return this;
        }

        /**
         * Sets a tracer.
         *
         * @param tracerBuilder a tracer builder to set; will be built as a first step of this method execution
         * @return updated builder
         */
        public Builder tracer(Supplier<? extends Tracer> tracerBuilder) {
            configurationBuilder.tracer(tracerBuilder);
            return this;
        }

        /**
         * A method to validate a named socket configuration exists in this builder.
         *
         * @param socketName name of the socket, using {@link io.helidon.webserver.WebServer#DEFAULT_SOCKET_NAME}
         *                   will always return {@code true}
         * @return {@code true} in case the named socket is configured in this builder
         */
        public boolean hasSocket(String socketName) {
            return DEFAULT_SOCKET_NAME.equals(socketName)
                    || configurationBuilder.sockets().containsKey(socketName);
        }

        /**
         * Configure the application scoped context to be used as a parent for webserver request contexts.
         * @param context top level context
         * @return an updated builder
         */
        public Builder context(Context context) {
            configurationBuilder.context(context);
            return this;
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
        @ConfiguredOption(key = "worker-count")
        public Builder workersCount(int workers) {
            configurationBuilder.workersCount(workers);
            return this;
        }

        /**
         * Set to {@code true} to print detailed feature information on startup.
         *
         * @param shouldPrint whether to print details or not
         * @return updated builder instance
         * @see io.helidon.common.HelidonFeatures
         */
        @ConfiguredOption(key = "features.print-details", value = "false")
        public Builder printFeatureDetails(boolean shouldPrint) {
            configurationBuilder.printFeatureDetails(shouldPrint);
            return this;
        }

        /**
         * Provide a custom handler for events that bypass routing.
         * The handler can customize status, headers and message.
         * <p>
         * Examples of bad request ({@link DirectHandler.EventType#BAD_REQUEST}:
         * <ul>
         *     <li>Invalid character in path</li>
         *     <li>Content-Length header set to a non-integer value</li>
         *     <li>Invalid first line of the HTTP request</li>
         * </ul>
         * @param handler direct handler to use
         * @param types event types to handle with the provided handler
         * @return updated builder
         */
        public Builder directHandler(DirectHandler handler, DirectHandler.EventType... types) {
            for (DirectHandler.EventType type : types) {
                directHandlers.addHandler(type, handler);
            }

            return this;
        }
    }
}
