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

import java.util.function.Predicate;

import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.PathMatcher;
import io.helidon.http.PathMatchers;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.webserver.WebServer;

class HttpRouteImpl extends HttpRouteBase implements HttpRoute {
    private final Handler handler;
    private final Predicate<Method> methodPredicate;
    private final PathMatcher pathMatcher;
    private final Predicate<ServerRequestHeaders> headersPredicate;

    HttpRouteImpl(HttpRoute.Builder builder) {
        this.handler = builder.handler();
        this.methodPredicate = builder.methodPredicate();
        this.pathMatcher = builder.pathPredicate();
        this.headersPredicate = builder.headersPredicate();
    }

    @Override
    public PathMatchers.MatchResult accepts(HttpPrologue prologue) {
        if (!methodPredicate.test(prologue.method())) {
            return PathMatchers.MatchResult.notAccepted();
        }

        return pathMatcher.match(prologue.uriPath());
    }

    @Override
    public PathMatchers.MatchResult accepts(HttpPrologue prologue, ServerRequestHeaders headers) {
        if (!methodPredicate.test(prologue.method())) {
            return PathMatchers.MatchResult.notAccepted();
        }
        if (!headersPredicate.test(headers)) {
            return PathMatchers.MatchResult.notAccepted();
        }

        return pathMatcher.match(prologue.uriPath());
    }

    @Override
    public Handler handler() {
        return handler;
    }

    @Override
    public void beforeStart() {
        handler.beforeStart();
    }

    @Override
    public void afterStart(WebServer webServer) {
        handler.afterStart(webServer);
    }

    @Override
    public void afterStop() {
        handler.afterStop();
    }

    @Override
    public String toString() {
        return methodPredicate + " (" + pathMatcher + "): " + handler;
    }
}
