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

package io.helidon.webserver.websocket;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import io.helidon.http.HttpPrologue;
import io.helidon.http.NotFoundException;
import io.helidon.http.PathMatchers;
import io.helidon.webserver.Routing;
import io.helidon.websocket.WsListener;

/**
 * WebSocket specific routing.
 */
public class WsRouting implements Routing {
    private static final WsRouting EMPTY = WsRouting.builder().build();
    private final List<WsRoute> routes;

    private WsRouting(Builder builder) {
        this.routes = new ArrayList<>(builder.routes);
    }

    @Override
    public Class<? extends Routing> routingType() {
        return WsRouting.class;
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
     * Empty WebSocket routing.
     *
     * @return empty routing
     */
    public static WsRouting empty() {
        return EMPTY;
    }

    @Override
    public void beforeStart() {
        for (WsRoute route : routes) {
            route.beforeStart();
        }
    }

    @Override
    public void afterStop() {
        for (WsRoute route : routes) {
            route.afterStop();
        }
    }

    /**
     * Find a route based on the provided prologue.
     *
     * @param prologue prologue with path and other request information
     * @return found route
     */
    public WsRoute findRoute(HttpPrologue prologue) {
        for (WsRoute route : routes) {
            PathMatchers.MatchResult accepts = route.accepts(prologue);
            if (accepts.accepted()) {
                return route;
            }
        }

        throw new NotFoundException("No WebSocket route available for " + prologue);
    }

    /**
     * Fluent API builder for {@link WsRouting}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, WsRouting> {
        private final List<WsRoute> routes = new ArrayList<>();

        private Builder() {
        }

        @Override
        public WsRouting build() {
            return new WsRouting(this);
        }

        /**
         * Add endpoint.
         *
         * @param path     path of the endpoint
         * @param listener listener to use, the same instance will be used for all connections
         * @return updated builder
         */
        public Builder endpoint(String path, WsListener listener) {
            return route(WsRoute.create(path, listener));
        }

        /**
         * Add endpoint.
         *
         * @param path     path of the endpoint
         * @param listener listener supplier, a new instance will be used for each connection
         * @return updated builder
         */
        public Builder endpoint(String path, Supplier<WsListener> listener) {
            return route(WsRoute.create(path, listener));
        }

        private Builder route(WsRoute wsRoute) {
            routes.add(wsRoute);
            return this;
        }
    }
}
