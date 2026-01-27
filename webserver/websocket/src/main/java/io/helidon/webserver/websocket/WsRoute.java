/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

import java.util.function.Supplier;

import io.helidon.http.HttpPrologue;
import io.helidon.http.PathMatcher;
import io.helidon.http.PathMatchers;
import io.helidon.webserver.Route;
import io.helidon.websocket.WsListener;

/**
 * WebSocket route. Result of routing in
 * {@link WsRouting#findRoute(io.helidon.http.HttpPrologue)}.
 */
public class WsRoute implements Route {
    private final PathMatcher pathMatcher;
    private final Supplier<? extends WsListener> listenerSupplier;

    private WsRoute(PathMatcher pathMatcher, Supplier<? extends WsListener> listenerSupplier) {
        this.pathMatcher = pathMatcher;
        this.listenerSupplier = listenerSupplier;
    }

    /**
     * Create a new WebSocket route for a specific path.
     *
     * @param path     path of the websocket endpoint
     * @param listener listener instance to use for all connections
     * @return a new route
     */
    public static WsRoute create(String path, WsListener listener) {
        PathMatcher pathMatcher = PathMatchers.create(path);
        return new WsRoute(pathMatcher, () -> listener);
    }

    /**
     * Create a new WebSocket route for a specific path.
     *
     * @param path     path of the websocket endpoint
     * @param listener supplier of listener instances, a new instance will be used for each connection
     * @return a new route
     */
    public static WsRoute create(String path, Supplier<? extends WsListener> listener) {
        PathMatcher pathMatcher = PathMatchers.create(path);
        return new WsRoute(pathMatcher, listener);
    }

    /**
     * WebSocket listener associated with this route.
     *
     * @return listener
     */
    public WsListener listener() {
        return listenerSupplier.get();
    }

    PathMatchers.MatchResult accepts(HttpPrologue prologue) {
        return pathMatcher.match(prologue.uriPath());
    }

}
