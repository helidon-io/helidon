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

package io.helidon.nima.websocket.webserver;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import io.helidon.common.http.HttpPrologue;
import io.helidon.common.http.PathMatchers;
import io.helidon.nima.webserver.Routing;
import io.helidon.nima.websocket.WsListener;

/**
 * WebSocket specific routing.
 */
public class WebSocketRouting implements Routing {
    private static final WebSocketRouting EMPTY = WebSocketRouting.builder().build();
    private final List<WebSocket> routes;

    private WebSocketRouting(Builder builder) {
        this.routes = new ArrayList<>(builder.routes);
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
    public static WebSocketRouting empty() {
        return EMPTY;
    }

    @Override
    public void beforeStart() {
        for (WebSocket route : routes) {
            route.beforeStart();
        }
    }

    @Override
    public void afterStop() {
        for (WebSocket route : routes) {
            route.afterStop();
        }
    }

    WebSocket findRoute(HttpPrologue prologue) {
        for (WebSocket route : routes) {
            PathMatchers.MatchResult accepts = route.accepts(prologue);
            if (accepts.accepted()) {
                return route;
            }
        }

        return null;
    }

    /**
     * Fluent API builder for {@link io.helidon.nima.websocket.webserver.WebSocketRouting}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, WebSocketRouting> {
        private final List<WebSocket> routes = new ArrayList<>();

        private Builder() {
        }

        @Override
        public WebSocketRouting build() {
            return new WebSocketRouting(this);
        }

        /**
         * Add endpoint.
         *
         * @param path     path of the endpoint
         * @param listener listener to use, the same instance will be used for all connections
         * @return updated builder
         */
        public Builder endpoint(String path, WsListener listener) {
            return route(WebSocket.create(path, listener));
        }

        /**
         * Add endpoint.
         *
         * @param path     path of the endpoint
         * @param listener listener supplier, a new instance will be used for each connection
         * @return updated builder
         */
        public Builder endpoint(String path, Supplier<WsListener> listener) {
            return route(WebSocket.create(path, listener));
        }

        private Builder route(WebSocket wsRoute) {
            routes.add(wsRoute);
            return this;
        }
    }
}
