/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import io.helidon.http.HttpPrologue;
import io.helidon.http.PathMatchers;
import io.helidon.webserver.Routing;

import jakarta.websocket.Extension;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * Routing class for Tyrus.
 */
public class TyrusRouting implements Routing {
    private static final TyrusRouting EMPTY = TyrusRouting.builder().build();

    private final Set<Extension> extensions;
    private final List<TyrusRoute> routes;

    private TyrusRouting(Builder builder) {
        this.routes = builder.routes;
        this.extensions = builder.extensions;
    }

    @Override
    public Class<? extends Routing> routingType() {
        return TyrusRouting.class;
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

    Set<Extension> extensions() {
        return extensions;
    }

    List<TyrusRoute> routes() {
        return routes;
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
            this.routes.add(new TyrusRoute(path, endpointClass, null,
                    PathMatchers.pattern(pathConcat(path, serverEndpoint(endpointClass)))));
            return this;
        }

        /**
         * Add endpoint.
         *
         * @param endpointClass annotated endpoint class
         * @return updated builder
         */
        public Builder endpoint(Class<?> endpointClass) {
            return endpoint("/", endpointClass);
        }

        /**
         * Add endpoint.
         *
         * @param path path of the endpoint
         * @param serverEndpointConfig Jakarta WebSocket endpoint configuration
         * @return updated builder
         */
        public Builder endpoint(String path, ServerEndpointConfig serverEndpointConfig) {
            this.routes.add(new TyrusRoute(path, null, serverEndpointConfig,
                    PathMatchers.pattern(pathConcat(path, serverEndpointConfig.getPath()))));
            return this;
        }

        /**
         * Add endpoint.
         *
         * @param serverEndpointConfig Jakarta WebSocket endpoint configuration
         * @return updated builder
         */
        public Builder endpoint(ServerEndpointConfig serverEndpointConfig) {
            return endpoint("/", serverEndpointConfig);
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

        @Override
        public TyrusRouting build() {
            return new TyrusRouting(this);
        }

        private static String pathConcat(String rootPath, String path) {
            StringBuilder result = new StringBuilder();
            if (rootPath.equals("/")) {
                rootPath = "";
            } else if (!rootPath.startsWith("/")) {
                result.append("/");
            }
            result.append(rootPath);
            if (!rootPath.endsWith("/") && !path.startsWith("/")) {
                result.append("/");
            }
            result.append(path);
            return result.toString();
        }

        private static String serverEndpoint(Class<?> endpointClass) {
            ServerEndpoint annot = endpointClass.getAnnotation(ServerEndpoint.class);
            if (annot == null) {
                throw new IllegalArgumentException("Endpoint class " + endpointClass.getName()
                        + " missing @ServerEndpoint");
            }
            return annot.value();
        }
    }
}
