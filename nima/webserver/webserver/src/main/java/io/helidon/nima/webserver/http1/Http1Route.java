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

package io.helidon.nima.webserver.http1;

import java.util.function.Predicate;

import io.helidon.common.http.Http;
import io.helidon.common.http.HttpPrologue;
import io.helidon.nima.webserver.http.Handler;
import io.helidon.nima.webserver.http.HttpRoute;
import io.helidon.nima.webserver.http.PathMatcher;
import io.helidon.nima.webserver.http.PathMatchers;

/**
 * A route for HTTP/1.1 only.
 * To create a route valid for any version of HTTP, please use {@link io.helidon.nima.webserver.http.HttpRoute}.
 */
public class Http1Route implements HttpRoute {
    private final Predicate<Http.Method> methodPredicate;
    private final PathMatcher pathMatcher;
    private final Handler handler;

    private Http1Route(Predicate<Http.Method> methodPredicate, PathMatcher pathMatcher, Handler handler) {
        this.methodPredicate = methodPredicate;
        this.pathMatcher = pathMatcher;
        this.handler = handler;
    }

    /**
     * Create an HTTP/1 specific route.
     *
     * @param method  accepted method
     * @param path    path pattern
     * @param handler handler
     * @return a new HTTP/1.1 specific route
     */
    public static Http1Route route(Http.Method method, String path, Handler handler) {
        return new Http1Route(Http.Method.predicate(method),
                              PathMatchers.create(path),
                              handler);
    }

    @Override
    public PathMatchers.MatchResult accepts(HttpPrologue prologue) {
        if (!prologue.protocolVersion().equals("1.1")) {
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
