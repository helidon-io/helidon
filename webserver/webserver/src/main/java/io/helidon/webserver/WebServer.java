/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.HelidonFeatures;
import io.helidon.common.HelidonFlavor;
import io.helidon.config.Config;
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
     * Gets effective server configuration.
     *
     * @return Server configuration
     */
    ServerConfiguration configuration();

    /**
     * Starts the server. Has no effect if server is running.
     *
     * @return a completion stage of starting tryProcess
     */
    CompletionStage<WebServer> start();

    /**
     * Completion stage is completed when server is shut down.
     *
     * @return a completion stage of the server
     */
    CompletionStage<WebServer> whenShutdown();

    /**
     * Attempt to gracefully shutdown server. It is possible to use returned {@link CompletionStage} to react.
     * <p>
     * RequestMethod can be called periodically.
     *
     * @return to react on finished shutdown tryProcess
     * @see #start()
     */
    CompletionStage<WebServer> shutdown();

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
    @SuppressWarnings("deprecation")
    io.helidon.common.http.ContextualRegistry context();

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
        return port(ServerConfiguration.DEFAULT_SOCKET_NAME);
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
     * Creates a new instance from a provided configuration and a routing.
     *
     * @param configurationBuilder a server configuration builder that will be built as a first step
     *                             of this method execution; may be {@code null}
     * @param routing a routing instance
     * @return a new web server instance
     * @throws IllegalStateException if none SPI implementation found
     * @throws NullPointerException if 'routing' parameter is {@code null}
     *
     * @deprecated since 2.0.0 - please use {@link #create(io.helidon.webserver.Routing, io.helidon.config.Config)} instead
     *  for instances based on {@link io.helidon.config.Config}, or {@link #builder(io.helidon.webserver.Routing)} to configure
     *  server configuration by hand (as you would on {@link SocketConfiguration.Builder} now.
     */
    @Deprecated
    static WebServer create(Supplier<? extends ServerConfiguration> configurationBuilder, Routing routing) {
        return create(configurationBuilder != null ? configurationBuilder.get() : null, routing);
    }

    /**
     * Creates new instance from provided configuration and routing.
     *
     * @param configurationBuilder a server configuration builder that will be built as a first step
     *                             of this method execution; may be {@code null}
     * @param routingBuilder       a routing builder that will be built as a second step of this method execution
     * @return a new web server instance
     * @throws IllegalStateException if none SPI implementation found
     * @throws NullPointerException  if 'routingBuilder' parameter is {@code null}
     *
     * @deprecated since 2.0.0 - please use {@link #create(java.util.function.Supplier, io.helidon.config.Config)} instead
     *  for instances based on {@link io.helidon.config.Config}, or {@link #builder(java.util.function.Supplier)} to configure
     *  server configuration by hand (as you would on {@link SocketConfiguration.Builder} now.
     */
    @Deprecated
    static WebServer create(Supplier<? extends ServerConfiguration> configurationBuilder,
                            Supplier<? extends Routing> routingBuilder) {
        Objects.requireNonNull(routingBuilder, "Parameter 'routingBuilder' must not be null!");
        return create(configurationBuilder != null ? configurationBuilder.get() : null, routingBuilder.get());
    }

    /**
     * Creates new instance from provided configuration and routing.
     *
     * @param configuration  a server configuration instance
     * @param routingBuilder a routing builder that will be built as a second step of this method execution
     * @return a new web server instance
     * @throws IllegalStateException if none SPI implementation found
     * @throws NullPointerException  if 'routingBuilder' parameter is {@code null}
     *
     * @deprecated since 2.0.0 - please use {@link #create(java.util.function.Supplier, io.helidon.config.Config)} instead
     *  for instances based on {@link io.helidon.config.Config}, or {@link #builder(java.util.function.Supplier)} to configure
     *  server configuration by hand (as you would on {@link SocketConfiguration.Builder} now.
     */
    @Deprecated
    static WebServer create(ServerConfiguration configuration,
                            Supplier<? extends Routing> routingBuilder) {
        Objects.requireNonNull(routingBuilder, "Parameter 'routingBuilder' must not be null!");
        return create(configuration, routingBuilder.get());
    }

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
     * @param configuration a server configuration instance
     * @param routing       a routing instance
     * @return a new web server instance
     * @throws NullPointerException  if 'routing' parameter is {@code null}
     *
     * @deprecated since 2.0.0 - please use {@link #create(Routing, io.helidon.config.Config)} instead
     *  for instances based on {@link io.helidon.config.Config}, or {@link #builder(Routing)} to configure
     *  server configuration by hand (as you would on {@link SocketConfiguration.Builder} now.
     */
    @Deprecated
    static WebServer create(ServerConfiguration configuration, Routing routing) {
        Objects.requireNonNull(routing, "Parameter 'routing' is null!");

        return builder(routing).config(configuration)
                .build();
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
     * Creates a builder of the {@link WebServer}.
     *
     * @param routing the routing; must not be {@code null}
     * @return the builder
     */
    static Builder builder(Routing routing) {
        return new Builder(routing);
    }

    /**
     * WebServer builder class provides a convenient way to set up WebServer with multiple server
     * sockets and optional multiple routings.
     */
    final class Builder implements io.helidon.common.Builder<WebServer>,
                                   SocketConfiguration.SocketConfigurationBuilder<Builder>,
                                   ParentingMediaContextBuilder<Builder>,
                                   MediaContextBuilder<Builder> {

        private static final MediaContext DEFAULT_MEDIA_SUPPORT = MediaContext.create();

        static {
            HelidonFeatures.register(HelidonFlavor.SE, "WebServer");
        }

        private final Map<String, Routing> routings = new HashMap<>();
        private final Routing defaultRouting;
        // internal use - we may keep this even after we remove the public access to ServerConfiguration
        @SuppressWarnings("deprecation")
        private final ServerConfiguration.Builder configurationBuilder = ServerConfiguration.builder();
        // for backward compatibility
        @SuppressWarnings("deprecation")
        private ServerConfiguration explicitConfig;
        private MessageBodyReaderContext readerContext;
        private MessageBodyWriterContext writerContext;

        private Builder(Routing defaultRouting) {
            Objects.requireNonNull(defaultRouting, "Parameter 'default routing' must not be null!");
            this.defaultRouting = defaultRouting;
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
            if (null == explicitConfig) {
                explicitConfig = configurationBuilder.build();
            }

            String unpairedRoutings =
                    routings.keySet()
                            .stream()
                            .filter(routingName -> explicitConfig.namedSocket(routingName).isEmpty())
                            .collect(Collectors.joining(", "));
            if (!unpairedRoutings.isEmpty()) {
                throw new IllegalStateException("No server socket configuration found for named routings: " + unpairedRoutings);
            }

            WebServer result = new NettyWebServer(explicitConfig,
                                                  defaultRouting,
                                                  routings,
                                                  writerContext,
                                                  readerContext);

            if (defaultRouting instanceof RequestRouting) {
                ((RequestRouting) defaultRouting).fireNewWebServer(result);
            }
            return result;
        }

        /**
         * Set a configuration of the {@link WebServer}.
         * Once this method is called, all other methods on this interface related to
         * server configuration are ignored.
         *
         * @param configuration the configuration
         * @return an updated builder
         * @deprecated since 2.0.0 - please use methods on this builder, or {@link #config(io.helidon.config.Config)} instead
         */
        @Deprecated
        public Builder config(ServerConfiguration configuration) {
            this.explicitConfig = configuration;
            return this;
        }

        /**
         * Set a configuration of the {@link WebServer}.
         *
         * @param configurationBuilder the configuration builder
         * @return an updated builder
         * @deprecated since 2.0.0 - see {@link #config(ServerConfiguration)}
         */
        @Deprecated
        public Builder config(Supplier<ServerConfiguration> configurationBuilder) {
            return config(configurationBuilder.get());
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

            routings.put(name, routing);
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
        public Builder tls(TlsConfig tlsConfig) {
            configurationBuilder.tls(tlsConfig);
            return this;
        }
    }
}
