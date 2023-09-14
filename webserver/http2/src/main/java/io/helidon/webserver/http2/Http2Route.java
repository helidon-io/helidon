/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.http2;

import java.util.function.Predicate;

import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.PathMatcher;
import io.helidon.http.PathMatchers;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.HttpRoute;

/**
 * A route for HTTP/2 only.
 * To create a route valid for any version of HTTP, please use {@link io.helidon.webserver.http.HttpRoute} or methods
 * defined on {@link io.helidon.webserver.http.HttpRouting.Builder}.
 */
public class Http2Route implements HttpRoute {
    private final Predicate<Method> methodPredicate;
    private final PathMatcher pathMatcher;
    private final Handler handler;

    private Http2Route(Predicate<Method> methodPredicate, PathMatcher pathMatcher, Handler handler) {
        this.methodPredicate = methodPredicate;
        this.pathMatcher = pathMatcher;
        this.handler = handler;
    }

    /**
     * Create a new HTTP/2 only route.
     *
     * @param method  method to handle
     * @param path    path pattern
     * @param handler handler
     * @return a new route
     */
    public static Http2Route route(Method method, String path, Handler handler) {
        return new Http2Route(Method.predicate(method),
                              PathMatchers.create(path),
                              handler);
    }

    @Override
    public PathMatchers.MatchResult accepts(HttpPrologue prologue) {
        if (!prologue.protocolVersion().equals("2.0")) {
            return PathMatchers.MatchResult.notAccepted();
        }
        if (!methodPredicate.test(prologue.method())) {
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
    public void afterStop() {
        handler.afterStop();
    }
}
