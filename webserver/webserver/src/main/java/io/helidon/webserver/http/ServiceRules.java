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

package io.helidon.webserver.http;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.helidon.http.Http;
import io.helidon.http.PathMatcher;
import io.helidon.http.PathMatchers;

class ServiceRules implements HttpRules {
    private static final Predicate<Http.Method> ALWAYS_PREDICATE = new TruePredicate();

    private final HttpService theService;
    private final PathMatcher pathMatcher;
    private final Predicate<Http.Method> methodPredicate;
    private final List<HttpRouteBase> routes = new LinkedList<>();

    ServiceRules() {
        this.theService = new NoOpService();
        this.pathMatcher = PathMatchers.any();
        this.methodPredicate = ALWAYS_PREDICATE;
    }

    ServiceRules(HttpService theService, PathMatcher pathMatcher, Predicate<Http.Method> methodPredicate) {
        this.theService = theService;
        this.pathMatcher = pathMatcher;
        this.methodPredicate = methodPredicate;
    }

    @Override
    public HttpRules register(Supplier<? extends HttpService>... services) {
        for (Supplier<? extends HttpService> service : services) {
            HttpService theService = service.get();
            ServiceRules subRules = new ServiceRules(theService, PathMatchers.any(), ALWAYS_PREDICATE);
            theService.routing(subRules);
            routes.add(subRules.build());
        }

        return this;
    }

    @Override
    public HttpRules register(String pathPattern, Supplier<? extends HttpService>... services) {
        for (Supplier<? extends HttpService> service : services) {
            HttpService theService = service.get();
            ServiceRules subRules = new ServiceRules(theService, PathMatchers.create(pathPattern), ALWAYS_PREDICATE);
            theService.routing(subRules);
            routes.add(subRules.build());
        }

        return this;
    }

    @Override
    public HttpRules route(HttpRoute route) {
        if (route instanceof HttpRouteImpl impl) {
            routes.add(impl);
        } else {
            routes.add(new HttpRouteWrap(route));
        }
        return this;
    }

    ServiceRoute build() {
        return new ServiceRoute(theService, methodPredicate, pathMatcher, routes);
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

    private static final class TruePredicate implements Predicate<Http.Method> {
        @Override
        public boolean test(Http.Method httpMethod) {
            return true;
        }

        @Override
        public String toString() {
            return "any method";
        }
    }
}
