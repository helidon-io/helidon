/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.webserver;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.context.Context;
import io.helidon.common.http.DirectHandler;
import io.helidon.config.Config;
import io.helidon.logging.common.LogConfig;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.http.encoding.ContentEncodingContext;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.http.media.MediaSupport;
import io.helidon.nima.webserver.http.DirectHandlers;
import io.helidon.nima.webserver.http.HttpRouting;
import io.helidon.nima.webserver.spi.ServerConnectionProvider;
import io.helidon.nima.webserver.spi.ServerConnectionSelector;
import io.helidon.pico.Contract;

/**
 * Server that opens server sockets and handles requests through routing.
 */
@Contract
public interface WebServer {
    /**
     * The default server socket configuration name. All the default server socket
     * configuration such as {@link WebServer#port(String)}
     * is accessible using this name.
     */
    String DEFAULT_SOCKET_NAME = "@default";

    /**
     * A new builder to set up server.
     *
     * @return builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Starts the server. Has no effect if server is running.
     * The start will fail on a server that is shut down, or that failed to start.
     * In such cases, create a new instance of Web Server.
     *
     * @return a started server
     * @throws IllegalStateException when startup fails, in such a case all channels are shut down
     */
    WebServer start();

    /**
     * Attempt to gracefully shutdown the server.
     *
     * @return a stopped server
     * @see #start()
     */
    WebServer stop();

    /**
     * Returns {@code true} if the server is currently running. Running server in stopping phase returns {@code true} until it
     * is not fully stopped.
     *
     * @return {@code true} if server is running
     */
    boolean isRunning();

    /**
     * Returns a port number the default server socket is bound to and is listening on;
     * or {@code -1} if unknown or not active.
     * <p>
     * It is supported only when server is running.
     *
     * @return a listen port; or {@code -1} if unknown or the default server socket is not active
     */
    default int port() {
        return port(DEFAULT_SOCKET_NAME);
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
        return hasTls(DEFAULT_SOCKET_NAME);
    }

    /**
     * Context associated with the {@code WebServer}, used as a parent for listener contexts.
     *
     * @return a server context
     */
    Context context();

    /**
     * Returns {@code true} if TLS is configured for the named socket.
     *
     * @param socketName the name of a socket
     * @return whether TLS is enabled for the socket, returns {@code false} if the socket does not exists
     */
    boolean hasTls(String socketName);

    /**
     * Reload TLS keystore and truststore configuration for the default socket.
     *
     * @param tls new TLS configuration
     */
    default void reloadTls(Tls tls) {
        reloadTls(DEFAULT_SOCKET_NAME, tls);
    }

    /**
     * Reload TLS keystore and truststore configuration for the named socket.
     *
     * @param socketName socket name to reload TLS configuration on
     * @param tls new TLS configuration
     */
    void reloadTls(String socketName, Tls tls);

    /**
     * Fluent API builder for {@link WebServer}.
     */
    class Builder implements io.helidon.common.Builder<Builder, WebServer>, Router.RouterBuilder<Builder> {

        private static final AtomicInteger WEBSERVER_COUNTER = new AtomicInteger(1);

        static {
            LogConfig.initClass();
        }

        private final Map<String, ListenerConfiguration.Builder> socketBuilder = new HashMap<>();
        private final Map<String, Router.Builder> routers = new HashMap<>();
        private final DirectHandlers.Builder directHandlers = DirectHandlers.builder();

        private final HelidonServiceLoader.Builder<ServerConnectionProvider> connectionProviders
                = HelidonServiceLoader.builder(ServiceLoader.load(ServerConnectionProvider.class));

        private final DefaultServerConfig.Builder configBuilder = DefaultServerConfig.builder();

        private Config providersConfig = Config.empty();
        private MediaContext mediaContext = MediaContext.create();
        private MediaContext.Builder mediaContextBuilder;
        private ContentEncodingContext contentEncodingContext = ContentEncodingContext.create();

        private boolean shutdownHook = true;
        private Context context;
        private boolean inheritThreadLocals = false;

        Builder(Config rootConfig) {
            config(rootConfig.get("server"));
        }

        private Builder() {
            // let's use the configuration
            this(Config.create());
        }

        @Override
        public WebServer build() {

            if (context == null) {
                // In case somebody spins a huge number up, the counter will cycle to negative numbers once
                // Integer.MAX_VALUE is reached.
                context = Context.builder()
                        .id("web-" + WEBSERVER_COUNTER.getAndIncrement())
                        .build();
            }
            if (mediaContext == null) {
                if (mediaContextBuilder == null) {
                    mediaContext = MediaContext.create();
                } else {
                    mediaContext = mediaContextBuilder.build();
                }
            } else {
                if (mediaContextBuilder != null) {
                    mediaContext = mediaContextBuilder.fallback(mediaContext).build();
                }
            }
            mediaContextBuilder = null;

            return new LoomServer(this, configBuilder.build(), directHandlers.build());
        }

        /**
         * Build and start the server.
         *
         * @return started server instance
         */
        public WebServer start() {
            return build().start();
        }

        /**
         * Update this builder from configuration.
         *
         * @param config configuration to use
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get("host").asString().ifPresent(this::host);
            config.get("port").asInt().ifPresent(this::port);
            config.get("tls").as(Tls::create).ifPresent(this::tls);
            config.get("inherit-thread-locals").asBoolean().ifPresent(this::inheritThreadLocals);

            // now let's configure the sockets
            config.get("sockets")
                    .asNodeList()
                    .orElseGet(List::of)
                    .forEach(listenerConfig -> {
                        String socketName = listenerConfig.get("name").asString()
                                .orElseThrow(() -> new IllegalStateException("Socket name is a required key"));
                        ListenerConfiguration.Builder listener = socket(socketName);

                        // listener specific options
                        listenerConfig.get("host").asString().ifPresent(listener::host);
                        listenerConfig.get("port").asInt().ifPresent(listener::port);
                        listenerConfig.get("backlog").asInt().ifPresent(listener::backlog);
                        listenerConfig.get("receive-buffer-size").asInt().ifPresent(listener::receiveBufferSize);
                        listenerConfig.get("write-queue-length").asInt().ifPresent(listener::writeQueueLength);
                        listenerConfig.get("write-buffer-size").asInt().ifPresent(listener::writeBufferSize);

                        listenerConfig.get("tls").as(Tls::create).ifPresent(listener::tls);

                        // connection specific options
                        listener.connectionOptions(socketOptionsBuilder -> {
                            Config connConfig = listenerConfig.get("connection-options");
                            connConfig.get("read-timeout-seconds").asInt().ifPresent(it -> socketOptionsBuilder.readTimeout(
                                    Duration.ofSeconds(it)));
                            connConfig.get("connect-timeout-seconds").asInt().ifPresent(it -> socketOptionsBuilder.connectTimeout(
                                    Duration.ofSeconds(it)));
                            connConfig.get("send-buffer-size").asInt().ifPresent(socketOptionsBuilder::socketSendBufferSize);
                            connConfig.get("receive-buffer-size").asInt()
                                    .ifPresent(socketOptionsBuilder::socketReceiveBufferSize);
                            connConfig.get("keep-alive").asBoolean().ifPresent(socketOptionsBuilder::socketKeepAlive);
                            connConfig.get("reuse-address").asBoolean().ifPresent(socketOptionsBuilder::socketReuseAddress);
                            connConfig.get("tcp-no-delay").asBoolean().ifPresent(socketOptionsBuilder::tcpNoDelay);
                        });
                    });
            // Configure content encoding
            config.get("content-encoding")
                    .as(ContentEncodingContext::create)
                    .ifPresent(this::contentEncodingContext);
            // Configure media support
            Config mediaSupportConfig = config.get("media-support");
            if (mediaSupportConfig.exists()) {
                // we are directly updating the builder, and we do not need to fallback to defaults
                // also configuration overrides any manual setup
                mediaContext = null;
                mediaContextBuilder = MediaContext.builder();
                mediaSupportConfig.ifExists(mediaContextBuilder::config);
            }

            // Store providers config node for later usage.
            providersConfig = config.get("connection-providers");
            return this;
        }

        /**
         * Configure additional socket with listener configuration using default routing.
         *
         * @param socketName    socket name
         * @param socketBuilder consumer of listener configuration
         * @return updated builder
         */
        public Builder socket(String socketName, Consumer<ListenerConfiguration.Builder> socketBuilder) {
            socketBuilder.accept(socket(socketName));
            return this;
        }

        /**
         * Configure additional socket with listener configuration and custom routing.
         *
         * @param socketName socket name
         * @param builders   consumer of listener and router builders
         * @return updated builder
         */
        public Builder socket(String socketName,
                              BiConsumer<ListenerConfiguration.Builder, Router.RouterBuilder<?>> builders) {
            builders.accept(socket(socketName), router(socketName));
            return this;
        }

        /**
         * Router builder for a named socket.
         *
         * @param socketName name of the socket, when {@link WebServer#DEFAULT_SOCKET_NAME} is used, the same
         *                   builder as used by this instance is returned
         * @return listener builder
         */
        public Router.Builder routerBuilder(String socketName) {
            return router(socketName);
        }

        /**
         * Socket builder for a named socket.
         *
         * @param socketName name of the socket, when {@link WebServer#DEFAULT_SOCKET_NAME} is used, the same
         *                   builder as used by this instance is returned
         * @return listener builder
         */
        public ListenerConfiguration.Builder socketBuilder(String socketName) {
            return socket(socketName);
        }

        /**
         * Configure listener for the default socket.
         *
         * @param socketBuilder listener builder consumer
         * @return updated builder
         */
        public Builder defaultSocket(Consumer<ListenerConfiguration.Builder> socketBuilder) {
            return socket(DEFAULT_SOCKET_NAME, socketBuilder);
        }

        /**
         * Port of the default socket.
         *
         * @param port port to bind to
         * @return updated builder
         */
        public Builder port(int port) {
            socket(DEFAULT_SOCKET_NAME).port(port);
            configBuilder.port(port);
            return this;
        }

        /**
         * Host of the default socket.
         *
         * @param host host or IP address to bind to
         * @return updated builder
         */
        public Builder host(String host) {
            socket(DEFAULT_SOCKET_NAME).host(host);
            configBuilder.host(host);
            return this;
        }

        /**
         * Configure a simple handler.
         *
         * @param handler    handler to use
         * @param eventTypes event types this handler should handle
         * @return updated builder
         */
        public Builder directHandler(DirectHandler handler, DirectHandler.EventType... eventTypes) {
            for (DirectHandler.EventType eventType : eventTypes) {
                directHandlers.addHandler(eventType, handler);
            }

            return this;
        }

        /**
         * Configure default HTTP routing.
         *
         * @param consumer routing consumer
         * @return updated builder
         * @see #addRouting(Routing)
         */
        public Builder routing(Consumer<? super HttpRouting.Builder> consumer) {
            HttpRouting.Builder builder = HttpRouting.builder();
            consumer.accept(builder);
            addRouting(builder.build());
            return this;
        }

        @Override
        public Builder addRouting(Routing routing) {
            router(DEFAULT_SOCKET_NAME).addRouting(routing);
            return this;
        }

        /**
         * Configure TLS for the default socket.
         *
         * @param tls tls to use
         * @return updated builder
         */
        public Builder tls(Tls tls) {
            this.socket(DEFAULT_SOCKET_NAME)
                    .tls(tls);
            return this;
        }

        /**
         * Configure a connection providers. This instance has priority over provider(s) discovered by service loader.
         *
         * @param connectionProvider explicit connection provider
         * @return updated builder
         */
        public Builder addConnectionProvider(ServerConnectionProvider connectionProvider) {
            this.connectionProviders.addService(connectionProvider);
            return this;
        }

        /**
         * A method to validate a named socket configuration exists in this builder.
         *
         * @param socketName name of the socket, using {@link io.helidon.nima.webserver.WebServer#DEFAULT_SOCKET_NAME}
         *                   will always return {@code true}
         * @return {@code true} in case the named socket is configured in this builder
         */
        public boolean hasSocket(String socketName) {
            return DEFAULT_SOCKET_NAME.equals(socketName) || socketBuilder.containsKey(socketName);
        }

        /**
         * Add an explicit media support to the list.
         * By default, all discovered media supports will be available to the server. Use this method only when
         * the media support is not discoverable by service loader, or when using explicit
         * {@link #mediaContext(io.helidon.nima.http.media.MediaContext)}.
         *
         * @param mediaSupport media support to add
         * @return updated builder
         */
        public Builder addMediaSupport(MediaSupport mediaSupport) {
            Objects.requireNonNull(mediaSupport);
            if (mediaContextBuilder == null) {
                mediaContextBuilder = MediaContext.builder()
                        .discoverServices(false);
            }

            mediaContextBuilder.addMediaSupport(mediaSupport);
            return this;
        }

        /**
         * Configure the {@link MediaContext}.
         * This method discards previously registered {@link io.helidon.nima.http.media.MediaContext}
         * and all previously registered {@link io.helidon.nima.http.media.MediaSupport}.
         * If an explicit media support is configured using {@link #addMediaSupport(io.helidon.nima.http.media.MediaSupport)},
         * this context will be used as a fallback for a new one created from configured values.
         *
         * @param mediaContext media context
         * @return updated instance of the builder
         * @see #addMediaSupport(io.helidon.nima.http.media.MediaSupport)
         */
        public Builder mediaContext(MediaContext mediaContext) {
            Objects.requireNonNull(mediaContext);
            this.mediaContext = mediaContext;
            return this;
        }

        /**
         * Configure the default {@link ContentEncodingContext}.
         * This method discards all previously registered ContentEncodingContext.
         *
         * @param contentEncodingContext content encoding context
         * @return updated instance of the builder
         */
        public Builder contentEncodingContext(ContentEncodingContext contentEncodingContext) {
            Objects.requireNonNull(contentEncodingContext);
            this.contentEncodingContext = contentEncodingContext;
            return this;
        }

        /**
         * Configure the application scoped context to be used as a parent for webserver request contexts.
         *
         * @param context top level context
         * @return an updated builder
         */
        public Builder context(Context context) {
            Objects.requireNonNull(context);
            this.context = context;
            return this;
        }

        /**
         * When true the webserver registers a shutdown hook with the JVM Runtime.
         * <p>
         * Defaults to true. Set this to false such that a shutdown hook is not registered.
         *
         * @param shutdownHook When true register a shutdown hook
         * @return updated builder
         */
        public Builder shutdownHook(boolean shutdownHook) {
            this.shutdownHook = shutdownHook;
            return this;
        }

        /**
         * Configure whether server threads should inherit inheritable thread locals.
         * Default value is {@code false}.
         *
         * @param inheritThreadLocals whether to inherit thread locals
         * @return an updated builder
         */
        public Builder inheritThreadLocals(boolean inheritThreadLocals) {
            this.inheritThreadLocals = inheritThreadLocals;
            return this;
        }

        boolean inheritThreadLocals() {
            return inheritThreadLocals;
        }

        Context context() {
            return context;
        }

        boolean shutdownHook() {
            return shutdownHook;
        }

        MediaContext mediaContext() {
            return this.mediaContext;
        }

        ContentEncodingContext contentEncodingContext() {
            return this.contentEncodingContext;
        }

        Map<String, ListenerConfiguration.Builder> socketBuilders() {
            return socketBuilder;
        }

        /**
         * Map of socket name to router.
         *
         * @return named sockets to router
         */
        Map<String, Router> routers() {
            Map<String, Router> result = new HashMap<>();
            routers.forEach((key, value) -> result.put(key, value.build()));
            return result;
        }

        List<ServerConnectionSelector> connectionProviders() {
            providersConfig.get("discover-services").asBoolean().ifPresent(connectionProviders::useSystemServiceLoader);
            List<ServerConnectionProvider> providers = connectionProviders.build().asList();
            // Send configuration nodes to providers
            return providers.stream()
                    .map(it -> it.create(providersConfig::get))
                    .toList();
        }

        ListenerConfiguration.Builder socket(String socketName) {
            return socketBuilder.computeIfAbsent(socketName, ListenerConfiguration::builder);
        }

        private Router.Builder router(String socketName) {
            return routers.computeIfAbsent(socketName, it -> Router.builder());
        }
    }

}
