/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.http3;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import io.helidon.common.Api;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.PathMatcher;
import io.helidon.http.PathMatchers;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.HttpRoute;

/**
 * A route for HTTP/3 only.
 * To create a route valid for any HTTP version, use {@link HttpRoute}.
 */
@Api.Incubating
public class Http3Route implements HttpRoute {
    private final Predicate<Method> methodPredicate;
    private final PathMatcher pathMatcher;
    private final Handler handler;

    private Http3Route(Predicate<Method> methodPredicate, PathMatcher pathMatcher, Handler handler) {
        this.methodPredicate = methodPredicate;
        this.pathMatcher = pathMatcher;
        this.handler = handler;
    }

    /**
     * Create an HTTP/3 specific route.
     *
     * @param method accepted method
     * @param path path pattern
     * @param handler handler
     * @return a new HTTP/3 specific route
     */
    public static Http3Route route(Method method, String path, Handler handler) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(handler, "handler");
        return new Http3Route(Method.predicate(method),
                              PathMatchers.create(path),
                              handler);
    }

    @Override
    public PathMatchers.MatchResult accepts(HttpPrologue prologue) {
        if (!"3".equals(prologue.protocolVersion())) {
            return PathMatchers.MatchResult.notAccepted();
        }
        if (!methodPredicate.test(prologue.method())) {
            return PathMatchers.MatchResult.notAccepted();
        }

        return pathMatcher.match(prologue.uriPath());
    }

    @Override
    public Optional<PathMatcher> pathMatcher() {
        return Optional.of(pathMatcher);
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
}
