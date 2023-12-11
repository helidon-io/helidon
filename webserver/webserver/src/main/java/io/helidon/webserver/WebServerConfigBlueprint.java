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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.context.Context;
import io.helidon.webserver.spi.ServerFeature;
import io.helidon.webserver.spi.ServerFeatureProvider;

/**
 * WebServer configuration bean.
 * See {@link WebServer#create(java.util.function.Consumer)}.
 */
@Prototype.Blueprint(decorator = WebServerConfigSupport.ServerConfigDecorator.class)
@Prototype.CustomMethods(WebServerConfigSupport.CustomMethods.class)
@Prototype.Configured("server")
interface WebServerConfigBlueprint extends ListenerConfigBlueprint, Prototype.Factory<WebServer> {
    /**
     * When true the webserver registers a shutdown hook with the JVM Runtime.
     * <p>
     * Defaults to true. Set this to false such that a shutdown hook is not registered.
     *
     * @return whether to register a shutdown hook
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean shutdownHook();

    /**
     * Socket configurations.
     * Note that socket named {@value WebServer#DEFAULT_SOCKET_NAME} cannot be used,
     * configure the values on the server directly.
     *
     * @return map of listener configurations, except for the default one
     */
    @Option.Configured
    @Option.Singular
    Map<String, ListenerConfig> sockets();

    /**
     * Routing for additional sockets.
     * Note that socket named {@value WebServer#DEFAULT_SOCKET_NAME} cannot be used,
     * configure the routing on the server directly.
     *
     * @return map of routing
     */
    @Option.Singular
    @Option.Access("")
    Map<String, List<io.helidon.common.Builder<?, ? extends Routing>>> namedRoutings();

    /**
     * Server features allow customization of the server, listeners, or routings.
     *
     * @return server features
     */
    @Option.Configured
    @Option.Singular
    @Option.Provider(ServerFeatureProvider.class)
    List<ServerFeature> features();

    /**
     * Context for the WebServer, if none defined, a new one will be created with global context as the root.
     *
     * @return server context
     */
    Optional<Context> serverContext();

}
