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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.net.ssl.SSLContext;

import io.helidon.common.http.ContextualRegistry;

/**
 * Represents a immutably configured WEB server.
 * <p>
 * Provides basic lifecycle and monitoring API.
 * <p>
 * Instance can be created from {@link Routing} and optionally from {@link ServerConfiguration} using
 * {@link #create(Routing)}, {@link #create(ServerConfiguration, Routing)} or {@link #builder(Routing)}methods and their builder
 * enabled overloads.
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
    ContextualRegistry context();

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
     * Update the TLS configuration of the default socket {@link ServerConfiguration#DEFAULT_SOCKET_NAME}.
     *
     * @param tls new TLS configuration
     * @throws IllegalStateException if {@link WebServerTls#sslContext()} returns {@code null} or
     * if {@link SocketConfiguration#ssl()} returns {@code null}
     */
    void updateTls(WebServerTls tls);

    /**
     * Update the TLS configuration of the named socket.
     *
     * @param tls new TLS configuration
     * @param socketName specific named socket name
     * @throws IllegalStateException if {@link WebServerTls#sslContext()} returns {@code null} or
     * if {@link SocketConfiguration#ssl()} returns {@code null}
     */
    void updateTls(WebServerTls tls, String socketName);


    /**
     * Creates a new instance from a provided configuration and a routing.
     *
     * @param configurationBuilder a server configuration builder that will be built as a first step
     *                             of this method execution; may be {@code null}
     * @param routing a routing instance
     * @return a new web server instance
     * @throws IllegalStateException if none SPI implementation found
     * @throws NullPointerException if 'routing' parameter is {@code null}
     */
    static WebServer create(Supplier<? extends ServerConfiguration> configurationBuilder, Routing routing) {
        return create(configurationBuilder != null ? configurationBuilder.get() : null, routing);
    }

    /**
     * Creates new instance form provided configuration and routing.
     *
     * @param configurationBuilder a server configuration builder that will be built as a first step
     *                             of this method execution; may be {@code null}
     * @param routingBuilder       a routing builder that will be built as a second step of this method execution
     * @return a new web server instance
     * @throws IllegalStateException if none SPI implementation found
     * @throws NullPointerException  if 'routingBuilder' parameter is {@code null}
     */
    static WebServer create(Supplier<? extends ServerConfiguration> configurationBuilder,
                            Supplier<? extends Routing> routingBuilder) {
        Objects.requireNonNull(routingBuilder, "Parameter 'routingBuilder' must not be null!");
        return create(configurationBuilder != null ? configurationBuilder.get() : null, routingBuilder.get());
    }

    /**
     * Creates new instance form provided configuration and routing.
     *
     * @param configuration  a server configuration instance
     * @param routingBuilder a routing builder that will be built as a second step of this method execution
     * @return a new web server instance
     * @throws IllegalStateException if none SPI implementation found
     * @throws NullPointerException  if 'routingBuilder' parameter is {@code null}
     */
    static WebServer create(ServerConfiguration configuration,
                            Supplier<? extends Routing> routingBuilder) {
        Objects.requireNonNull(routingBuilder, "Parameter 'routingBuilder' must not be null!");
        return create(configuration, routingBuilder.get());
    }

    /**
     * Creates new instance form provided routing and default configuration.
     *
     * @param routing a routing instance
     * @return a new web server instance
     * @throws IllegalStateException if none SPI implementation found
     * @throws NullPointerException  if 'routing' parameter is {@code null}
     */
    static WebServer create(Routing routing) {
        return create((ServerConfiguration) null, routing);
    }

    /**
     * Creates new instance form provided configuration and routing.
     *
     * @param configuration a server configuration instance
     * @param routing       a routing instance
     * @return a new web server instance
     * @throws IllegalStateException if none SPI implementation found
     * @throws NullPointerException  if 'routing' parameter is {@code null}
     */
    static WebServer create(ServerConfiguration configuration, Routing routing) {
        Objects.requireNonNull(routing, "Parameter 'routing' is null!");

        return builder(routing).config(configuration)
                               .build();
    }

    /**
     * Creates new instance form provided routing and default configuration.
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
    final class Builder implements io.helidon.common.Builder<WebServer> {

        private final Map<String, Routing> routings = new HashMap<>();
        private final Routing defaultRouting;

        private ServerConfiguration configuration;

        private Builder(Routing defaultRouting) {
            Objects.requireNonNull(defaultRouting, "Parameter 'default routing' must not be null!");

            this.defaultRouting = defaultRouting;
        }

        /**
         * Set a configuration of the {@link WebServer}.
         *
         * @param configuration the configuration
         * @return an updated builder
         */
        public Builder config(ServerConfiguration configuration) {
            this.configuration = configuration;
            return this;
        }

        /**
         * Set a configuration of the {@link WebServer}.
         *
         * @param configurationBuilder the configuration builder
         * @return an updated builder
         */
        public Builder config(Supplier<ServerConfiguration> configurationBuilder) {
            this.configuration = configurationBuilder != null ? configurationBuilder.get() : null;
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

        /**
         * Builds the {@link WebServer} instance as configured by this builder and its parameters.
         *
         * @return a ready to use {@link WebServer}
         * @throws IllegalStateException if there are unpaired named routings (as described
         *                               at {@link #addNamedRouting(String, Routing)})
         */
        @Override
        public WebServer build() {

            String unpairedRoutings =
                    routings.keySet()
                            .stream()
                            .filter(routingName -> configuration == null || configuration.socket(routingName) == null)
                            .collect(Collectors.joining(", "));
            if (!unpairedRoutings.isEmpty()) {
                throw new IllegalStateException("No server socket configuration found for named routings: " + unpairedRoutings);
            }

            WebServer result = new NettyWebServer(configuration == null
                                                          // this is happening once per microservice, no need to store in
                                                          // a constant; also the configuration creates instances of context etc.
                                                          // that should not be initialized unless needed
                                                          ? ServerConfiguration.builder().build()
                                                          : configuration,
                                                  defaultRouting, routings);
            if (defaultRouting instanceof RequestRouting) {
                ((RequestRouting) defaultRouting).fireNewWebServer(result);
            }
            return result;
        }
    }
}
