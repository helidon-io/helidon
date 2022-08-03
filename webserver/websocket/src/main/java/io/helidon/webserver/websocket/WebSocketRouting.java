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
package io.helidon.webserver.websocket;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import io.helidon.webserver.BareRequest;
import io.helidon.webserver.BareResponse;
import io.helidon.webserver.Routing;

import jakarta.websocket.Extension;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * WebSocket specific routing.
 */
public class WebSocketRouting implements Routing {

    private final Set<Extension> extensions;
    private final List<WebSocketRoute> routes;
    private final ExecutorService executorService;

    private WebSocketRouting(Builder builder) {
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

    @Override
    public void route(BareRequest bareRequest, BareResponse bareResponse) {
        throw new UnsupportedOperationException("Not used in case of websocket routing");
    }

    Set<Extension> getExtensions() {
        return extensions;
    }

    List<WebSocketRoute> getRoutes() {
        return routes;
    }

    ExecutorService getExecutorService() {
        return executorService;
    }

    /**
     * Fluent API builder for {@link io.helidon.webserver.websocket.WebSocketRouting}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, Routing> {

        private final List<WebSocketRoute> routes = new ArrayList<>();
        // a purposefully mutable extensions

        private final Set<Extension> extensions = new HashSet<>();
        private ExecutorService executorService;

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
            this.routes.add(new WebSocketRoute(path, endpointClass, null));
            return this;
        }

        /**
         * Add endpoint.
         *
         * @param endpointClass annotated endpoint class
         * @return updated builder
         */
        public Builder endpoint(Class<?> endpointClass) {
            this.routes.add(new WebSocketRoute("/", endpointClass, null));
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
            this.routes.add(new WebSocketRoute(path, null, serverEndpointConfig));
            return this;
        }

        /**
         * Add endpoint.
         *
         * @param serverEndpointConfig Jakarta WebSocket endpoint configuration
         * @return updated builder
         */
        public Builder endpoint(ServerEndpointConfig serverEndpointConfig) {
            this.routes.add(new WebSocketRoute("/", null, serverEndpointConfig));
            return this;
        }

        /**
         * Add Jakarta WebSocket extension.
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
        public WebSocketRouting build() {
            return new WebSocketRouting(this);
        }
    }
}
