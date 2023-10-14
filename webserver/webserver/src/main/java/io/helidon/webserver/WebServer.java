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

package io.helidon.webserver;

import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.context.Context;
import io.helidon.common.tls.Tls;

/**
 * Server that opens server sockets and handles requests through routing.
 */
@RuntimeType.PrototypedBy(WebServerConfig.class)
public interface WebServer extends RuntimeType.Api<WebServerConfig> {
    /**
     * The default server socket configuration name. All the default server socket
     * configuration such as {@link WebServer#port(String)}
     * is accessible using this name.
     * The value is {@value}.
     */
    String DEFAULT_SOCKET_NAME = "@default";

    /**
     * Create a new web server from its configuration.
     *
     * @param serverConfig configuration
     * @return a new web server
     */
    static WebServer create(WebServerConfig serverConfig) {
        return new LoomServer(serverConfig);
    }

    /**
     * Create a new webserver customizing its configuration.
     *
     * @param builderConsumer consumer of configuration builder
     * @return a new web server
     */
    static WebServer create(Consumer<WebServerConfig.Builder> builderConsumer) {
        WebServerConfig.Builder b = WebServerConfig.builder();
        builderConsumer.accept(b);
        return b.build();
    }

    /**
     * A new builder to set up server.
     *
     * @return builder
     */
    static WebServerConfig.Builder builder() {
        return WebServerConfig.builder();
    }

    /**
     * Starts the server. Has no effect if server is running.
     * The start will fail on a server that is shut down, or that failed to start.
     * In such cases, create a new instance of WebServer.
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
}
