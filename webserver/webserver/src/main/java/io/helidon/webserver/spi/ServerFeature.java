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

package io.helidon.webserver.spi;

import java.util.Set;

import io.helidon.common.Builder;
import io.helidon.common.config.NamedService;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;

/**
 * Server features provide a way to add or update server configuration.
 * These features are configured for the whole server and may update listeners and routing configuration.
 * <p>
 * Server features are resolved when the {@link io.helidon.webserver.WebServer} is being created.
 */
public interface ServerFeature extends NamedService {
    /**
     * Set up a server feature. Server features can modify server configuration, right before the server is created.
     * To access listener configuration, or routing, a list of all listeners is provided.
     *
     * @param featureContext to access builders of webserver, listeners, and routing
     */
    void setup(ServerFeatureContext featureContext);

    /**
     * A wrapping object to access various configurable elements of server for {@link io.helidon.webserver.spi.ServerFeature}.
     */
    interface ServerFeatureContext {
        /**
         * Configuration of the server.
         *
         * @return server config builder
         */
        WebServerConfig serverConfig();

        /**
         * List of all configured sockets, does not contain the default socket name.
         *
         * @return additional sockets configured
         * @see io.helidon.webserver.WebServer#DEFAULT_SOCKET_NAME
         */
        Set<String> sockets();

        /**
         * Check if a named socket exists in configuration.
         *
         * @param socketName name of the socket
         * @return {@code true} if the socket already exists in configuration
         */
        boolean socketExists(String socketName);

        /**
         * Listener builders for a named socket.
         *
         * @param socketName name of the socket
         * @return builders for the named listener
         * @throws java.util.NoSuchElementException in case the named socket does not exist
         * @see #socketExists(String)
         */
        SocketBuilders socket(String socketName);
    }

    /**
     * Access to builders of various routing types.
     */
    interface RoutingBuilders {
        /**
         * Check whether a specific routing builder is available.
         *
         * @param builderType type of the routing builder
         * @return {@code true} if such a routing exists
         */
        boolean hasRouting(Class<?> builderType);

        /**
         * Obtain the routing builder for the provided type.
         *
         * @param builderType type of the routing builder
         * @param <T>         type that is expected
         * @return instance of the routing builder
         * @throws java.util.NoSuchElementException in case the routing is not avialable
         * @see #hasRouting(Class)
         */
        <T extends Builder<T, ?>> T routingBuilder(Class<T> builderType);
    }

    /**
     * A wrapping object to access listener related builders.
     */
    interface SocketBuilders {
        /**
         * Configuration of the listener.
         *
         * @return listener config
         */
        ListenerConfig listener();

        /**
         * HTTP routing builder.
         *
         * @return http routing
         */
        HttpRouting.Builder httpRouting();

        /**
         * Routing builders.
         *
         * @return builders
         */
        RoutingBuilders routingBuilders();

    }
}
