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

package io.helidon.webserver.http;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;

import io.helidon.http.Method;
import io.helidon.http.PathMatcher;
import io.helidon.http.PathMatchers;
import io.helidon.webserver.http.spi.ProtocolUpgradeHandler;

class ServiceRules implements HttpRules {
    private static final Predicate<Method> ALWAYS_PREDICATE = new TruePredicate();

    private final HttpService theService;
    private final PathMatcher pathMatcher;
    private final Predicate<Method> methodPredicate;
    private final List<HttpRouteBase> routes = new LinkedList<>();
    private final List<HttpRouteBase> protocolUpgradeRoutes = new LinkedList<>();

    ServiceRules() {
        this.theService = new NoOpService();
        this.pathMatcher = PathMatchers.any();
        this.methodPredicate = ALWAYS_PREDICATE;
    }

    ServiceRules(HttpService theService, PathMatcher pathMatcher, Predicate<Method> methodPredicate) {
        this.theService = theService;
        this.pathMatcher = pathMatcher;
        this.methodPredicate = methodPredicate;
    }

    @Override
    public HttpRules register(HttpService... services) {
        for (HttpService service : services) {
            ServiceRules subRules = new ServiceRules(service, PathMatchers.any(), ALWAYS_PREDICATE);
            service.routing(subRules);
            routes.add(subRules.build());
            if (!subRules.protocolUpgradeRoutes.isEmpty()) {
                protocolUpgradeRoutes.add(subRules.buildProtocolUpgrade());
            }
        }
        return this;
    }

    @Override
    public HttpRules registerLocator(HttpServiceLocator locator) {
        routes.add(new ServiceLocatorRoute(locator, PathMatchers.any(), ALWAYS_PREDICATE));
        return this;
    }

    @Override
    public HttpRules register(String pathPattern, HttpService... services) {
        for (HttpService service : services) {
            ServiceRules subRules = new ServiceRules(service, PathMatchers.create(pathPattern), ALWAYS_PREDICATE);
            service.routing(subRules);
            routes.add(subRules.build());
            if (!subRules.protocolUpgradeRoutes.isEmpty()) {
                protocolUpgradeRoutes.add(subRules.buildProtocolUpgrade());
            }
        }
        return this;
    }

    @Override
    public HttpRules registerLocator(String pathPattern, HttpServiceLocator locator) {
        routes.add(new ServiceLocatorRoute(locator, PathMatchers.create(pathPattern), ALWAYS_PREDICATE));
        return this;
    }

    @Override
    public HttpRules route(HttpRoute route) {
        HttpRouteBase actualRoute;
        if (route instanceof HttpRouteImpl impl) {
            actualRoute = impl;
        } else {
            actualRoute = new HttpRouteWrap(route);
        }
        routes.add(actualRoute);

        Handler handler = actualRoute.handler();
        if (handler instanceof ProtocolUpgradeHandler protocolUpgradeHandler) {
            protocolUpgradeRoutes.add(new ProtocolUpgradePolicyRoute(actualRoute, protocolUpgradeHandler));
        }
        return this;
    }

    // Builds the route tree used for ordinary HTTP request routing.
    ServiceRoute build() {
        return new ServiceRoute(theService, methodPredicate, pathMatcher, routes);
    }

    // Builds the policy-only route tree used before completing a routed protocol upgrade.
    ServiceRoute buildProtocolUpgrade() {
        return new ServiceRoute(theService, methodPredicate, pathMatcher, protocolUpgradeRoutes);
    }

    private static final class NoOpService implements HttpService {
        @Override
        public void routing(HttpRules rules) {
        }

        @Override
        public String toString() {
            return "root";
        }
    }

    private static final class TruePredicate implements Predicate<Method> {
        @Override
        public boolean test(Method httpMethod) {
            return true;
        }

        @Override
        public String toString() {
            return "any method";
        }
    }
}
