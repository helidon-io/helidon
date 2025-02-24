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

import java.util.List;
import java.util.function.Predicate;

import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.PathMatcher;
import io.helidon.http.PathMatchers;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.WebServer;

class ServiceRoute extends HttpRouteBase implements HttpRoute {
    private final HttpService theService;
    private final Predicate<Method> methodPredicate;
    private final PathMatcher pathMatcher;
    private final List<HttpRouteBase> routes;

    ServiceRoute(HttpService theService,
                 Predicate<Method> methodPredicate,
                 PathMatcher pathMatcher,
                 List<HttpRouteBase> routes) {
        this.theService = theService;
        this.methodPredicate = methodPredicate;
        this.pathMatcher = pathMatcher;
        this.routes = routes;
    }

    @Override
    public void beforeStart() {
        theService.beforeStart();
        this.routes.forEach(HttpRouteBase::beforeStart);
    }

    @Override
    public void afterStart(WebServer webServer) {
        theService.afterStart(webServer);
        this.routes.forEach(r -> r.afterStart(webServer));
    }

    @Override
    public void afterStop() {
        theService.afterStop();
        this.routes.forEach(HttpRoute::afterStop);
    }

    @Override
    public PathMatchers.MatchResult accepts(HttpPrologue prologue) {
        throw new IllegalStateException("List routes must use acceptsPrefix");
    }

    @Override
    public Handler handler() {
        throw new IllegalStateException("Service routing cannot provide a handler, use list of routes");
    }

    @Override
    public String toString() {
        return methodPredicate + " (" + pathMatcher + ") with " + routes.size() + " routes, service: " + theService;
    }

    @Override
    PathMatchers.PrefixMatchResult acceptsPrefix(HttpPrologue prologue) {
        if (!methodPredicate.test(prologue.method())) {
            return PathMatchers.PrefixMatchResult.notAccepted();
        }

        return pathMatcher.prefixMatch(prologue.uriPath());
    }

    @Override
    List<HttpRouteBase> routes() {
        return routes;
    }

    @Override
    boolean isList() {
        return true;
    }

    RouteCrawler crawler(ConnectionContext ctx, RoutingRequest request) {
        return new RouteCrawler(ctx, request, routes);
    }
}
