/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.http;

import java.util.Optional;

import io.helidon.http.HttpPrologue;
import io.helidon.http.PathMatcher;
import io.helidon.http.PathMatchers;
import io.helidon.webserver.WebServer;

class HttpRouteWrap extends HttpRouteBase {
    private final HttpRoute route;

    HttpRouteWrap(HttpRoute route) {
        this.route = route;
    }

    @Override
    public void beforeStart() {
        route.beforeStart();
    }

    @Override
    public void afterStart(WebServer webServer) {
        route.afterStart(webServer);
    }

    @Override
    public void afterStop() {
        route.afterStop();
    }

    @Override
    public PathMatchers.MatchResult accepts(HttpPrologue prologue) {
        return route.accepts(prologue);
    }

    @Override
    public Handler handler() {
        return route.handler();
    }

    @Override
    public String toString() {
        return route.toString();
    }

    @Override
    boolean isList() {
        return false;
    }

    @Override
    public Optional<PathMatcher> pathMatcher() {
        return route.pathMatcher();
    }
}
