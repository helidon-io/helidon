/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.testing.junit5.spi;

import java.util.Optional;

import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.Router;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;

/**
 * Java {@link java.util.ServiceLoader} provider interface for extending server tests with support for additional injection,
 * such as HTTP/1.1 client.
 */
public interface ServerJunitExtension extends HelidonJunitExtension {
    /**
     * Update WebServer builder.
     *
     * @param builder builder to update, will be used to build server instance
     */
    default void updateServerBuilder(WebServerConfig.Builder builder) {
    }

    /**
     * Called for sockets defined by {@link io.helidon.webserver.testing.junit5.SetUpRoute}.
     *
     * @param socketName      name of the socket
     * @param listenerBuilder listener configuration builder
     * @param routerBuilder   router builder
     */
    default void updateListenerBuilder(String socketName,
                                       ListenerConfig.Builder listenerBuilder,
                                       Router.RouterBuilder<?> routerBuilder) {
    }

    /**
     * Resolve a parameter. Provide an instance of the parameter. Only called if
     * {@link #supportsParameter(org.junit.jupiter.api.extension.ParameterContext,
     * org.junit.jupiter.api.extension.ExtensionContext)}
     * returned {@code true}.
     *
     * @param parameterContext JUnit parameter context
     * @param extensionContext JUnit extension context
     * @param parameterType    type of parameter
     * @param server           webserver instance
     * @return instance of the expected type
     */
    default Object resolveParameter(ParameterContext parameterContext,
                                    ExtensionContext extensionContext,
                                    Class<?> parameterType,
                                    WebServer server) {
        throw new ParameterResolutionException("This parameter cannot be resolved: " + parameterContext);
    }

    /**
     * Check if the type is supported and return a handler for it.
     *
     * @param type type of the parameter to {@link io.helidon.webserver.testing.junit5.SetUpRoute} method
     * @return parameter handler if the type is supported, empty otherwise
     */
    default Optional<ParamHandler<?>> setUpRouteParamHandler(Class<?> type) {
        return Optional.empty();
    }

    /**
     * Handler of server test parameters of methods annotated with {@link io.helidon.webserver.testing.junit5.SetUpRoute}.
     *
     * @param <T> type of the parameter this handler handles
     */
    interface ParamHandler<T> {
        /**
         * Get an instance to be injected.
         *
         * @param socketName      name of a socket this will belong to
         * @param serverBuilder   builder of the webserver
         * @param listenerBuilder builder of the listener associated with the socketName
         * @param routerBuilder   router builder to support additional routings
         * @return a new instance to inject as a parameter to the method
         */
        T get(String socketName,
              WebServerConfig.Builder serverBuilder,
              ListenerConfig.Builder listenerBuilder,
              Router.RouterBuilder<?> routerBuilder);

        /**
         * Handle the value after the method has been called, and its body updated our provided instance.
         *
         * @param socketName      socket name
         * @param serverBuilder   builder of the webserver
         * @param listenerBuilder builder of the listener
         * @param routerBuilder   router builder
         * @param value           the value we provided with
         *                        {@link #get(String, io.helidon.webserver.WebServerConfig.Builder,
         *                        io.helidon.webserver.ListenerConfig.Builder,
         *                        io.helidon.webserver.Router.RouterBuilder)}
         */
        default void handle(String socketName,
                            WebServerConfig.Builder serverBuilder,
                            ListenerConfig.Builder listenerBuilder,
                            Router.RouterBuilder<?> routerBuilder,
                            T value) {
        }
    }
}
