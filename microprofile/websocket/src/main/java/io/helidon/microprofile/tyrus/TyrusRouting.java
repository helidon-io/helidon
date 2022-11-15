/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.microprofile.tyrus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.helidon.common.http.HttpPrologue;
import io.helidon.common.http.PathMatchers;
import io.helidon.nima.webserver.Routing;
import jakarta.websocket.Extension;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * WebSocket specific routing.
 */
public class TyrusRouting implements Routing {
    private static final TyrusRouting EMPTY = TyrusRouting.builder().build();

    private final Set<Extension> extensions;
    private final List<TyrusRoute> routes;
    private final ExecutorService executorService;

    private TyrusRouting(Builder builder) {
        this.routes = builder.routes;
        this.extensions = builder.extensions;
        this.executorService = builder.executorService;
    }

    /**
     * Builder for WebSocket routing.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Emtpy WebSocket routing.
     *
     * @return empty routing
     */
    public static TyrusRouting empty() {
        return EMPTY;
    }

    Set<Extension> getExtensions() {
        return extensions;
    }

    List<TyrusRoute> getRoutes() {
        return routes;
    }

    ExecutorService getExecutorService() {
        return executorService;
    }

    /**
     * Returns {@code true} if this route corresponds to one of the registered or
     * discovered WS endpoint.
     *
     * @param prologue HTTP prologue
     * @return outcome of test
     */
    TyrusRoute findRoute(HttpPrologue prologue) {
        for (TyrusRoute route : routes) {
            PathMatchers.MatchResult accepts = route.accepts(prologue);
            if (accepts.accepted()) {
                return route;
            }
        }
        return null;
    }

    /**
     * Fluent API builder for {@link TyrusRouting}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, Routing> {

        private final List<TyrusRoute> routes = new ArrayList<>();
        private final Set<Extension> extensions = new HashSet<>();      // mutable
        private ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

        private Builder() {
        }

        /**
         * Add endpoint.
         *
         * @param path path of the endpoint
         * @param endpointClass annotated endpoint class
         * @return updated builder
         */
        public Builder endpoint(String path, Class<?> endpointClass) {
            this.routes.add(new TyrusRoute(path, endpointClass, null, PathMatchers.pattern(path)));
            return this;
        }

        /**
         * Add endpoint.
         *
         * @param endpointClass annotated endpoint class
         * @return updated builder
         */
        public Builder endpoint(Class<?> endpointClass) {
            ServerEndpoint annot = endpointClass.getAnnotation(ServerEndpoint.class);
            if (annot == null) {
                throw new IllegalArgumentException("Endpoint class " + endpointClass.getName()
                        + " missing @ServerEndpoint");
            }
            this.routes.add(new TyrusRoute("/", endpointClass, null,
                    PathMatchers.pattern(annot.value())));
            return this;
        }

        /**
         * Add endpoint.
         *
         * @param path path of the endpoint
         * @param serverEndpointConfig Jakarta WebSocket endpoint configuration
         * @return updated builder
         */
        public Builder endpoint(String path, ServerEndpointConfig serverEndpointConfig) {
            this.routes.add(new TyrusRoute(path, null, serverEndpointConfig, PathMatchers.pattern(path)));
            return this;
        }

        /**
         * Add endpoint.
         *
         * @param serverEndpointConfig Jakarta WebSocket endpoint configuration
         * @return updated builder
         */
        public Builder endpoint(ServerEndpointConfig serverEndpointConfig) {
            this.routes.add(new TyrusRoute("/", null, serverEndpointConfig,
                    PathMatchers.pattern(serverEndpointConfig.getPath())));
            return this;
        }

        /**
         * Add Jakarta WebSocket extension.
         *
         * @param extension Jakarta WebSocket extension
         * @return updated builder
         */
        public Builder extension(Extension extension) {
            this.extensions.add(extension);
            return this;
        }

        /**
         * ExecutorService supplying threads for execution of endpoint methods.
         *
         * @param executorService executorService supplying threads for execution of endpoint methods
         * @return updated builder
         */
        public Builder executor(ExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        @Override
        public TyrusRouting build() {
            return new TyrusRouting(this);
        }
    }
}
