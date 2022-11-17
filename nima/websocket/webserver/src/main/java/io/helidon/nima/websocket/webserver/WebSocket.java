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

import java.util.function.Supplier;

import io.helidon.common.http.HttpPrologue;
import io.helidon.common.http.PathMatcher;
import io.helidon.common.http.PathMatchers;
import io.helidon.nima.webserver.Route;
import io.helidon.nima.websocket.WsListener;

class WebSocket implements Route {
    private final PathMatcher pathMatcher;
    private final Supplier<WsListener> listenerSupplier;

    private WebSocket(PathMatcher pathMatcher, Supplier<WsListener> listenerSupplier) {
        this.pathMatcher = pathMatcher;
        this.listenerSupplier = listenerSupplier;
    }

    static WebSocket create(String path, WsListener listener) {
        PathMatcher pathMatcher = PathMatchers.create(path);
        return new WebSocket(pathMatcher, () -> listener);
    }

    static WebSocket create(String path, Supplier<WsListener> listener) {
        PathMatcher pathMatcher = PathMatchers.create(path);
        return new WebSocket(pathMatcher, listener);
    }

    PathMatchers.MatchResult accepts(HttpPrologue prologue) {
        return pathMatcher.match(prologue.uriPath());
    }

    WsListener listener() {
        return listenerSupplier.get();
    }

}
