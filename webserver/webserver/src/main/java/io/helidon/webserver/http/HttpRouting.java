/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.helidon.builder.api.Prototype;
import io.helidon.http.Method;
import io.helidon.http.PathMatcher;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.Routing;

/**
 * HTTP routing.
 * This routing is capable of handling any HTTP version.
 */
public interface HttpRouting extends Routing, Prototype.Api {

    /**
     * Creates new instance of {@link HttpRouting.Builder router builder}.
     *
     * @return a new instance
     */
    static Builder builder() {
        return HttpRoutingImpl.builder();
    }

    /**
     * Create a default router.
     *
     * @return new default router
     */
    static HttpRouting create() {
        return HttpRouting.builder()
                .route(HttpRoute.builder()
                               .handler((req, res) -> res.send("Helidon WebServer works!"))
                               .build())
                .build();
    }

    /**
     * Empty routing (all requests will return {@link io.helidon.http.Status#NOT_FOUND_404}.
     *
     * @return empty routing
     */
    static HttpRouting empty() {
        return HttpRoutingImpl.empty();
    }

    @Override
    default Class<? extends Routing> routingType() {
        return HttpRouting.class;
    }


    /**
     * Route a request.
     *
     * @param ctx the underlying connection context
     * @param request the request to route
     * @param response the response for the request
     */
    void route(ConnectionContext ctx, RoutingRequest request, RoutingResponse response);


    /**
     * Security associated with this routing.
     *
     * @return security
     */
    HttpSecurity security();


    /**
     * Fluent API builder for {@link HttpRouting}.
     */
    interface Builder extends HttpRules, io.helidon.common.Builder<Builder, HttpRouting> {
        @Override
        Builder register(HttpService... service);

        @Override
        default Builder register(Supplier<? extends HttpService> service) {
            HttpRules.super.register(service);
            return this;
        }

        @Override
        default Builder register(Supplier<? extends HttpService> service1, Supplier<? extends HttpService> service2) {
            HttpRules.super.register(service1, service2);
            return this;
        }

        @Override
        default Builder register(Supplier<? extends HttpService> service1,
                                 Supplier<? extends HttpService> service2,
                                 Supplier<? extends HttpService> service3) {
            HttpRules.super.register(service1, service2, service3);
            return this;
        }

        @Override
        default Builder register(Supplier<? extends HttpService> service1,
                                 Supplier<? extends HttpService> service2,
                                 Supplier<? extends HttpService> service3,
                                 Supplier<? extends HttpService> service4) {
            HttpRules.super.register(service1, service2, service3, service4);
            return this;
        }

        @Override
        default Builder register(Supplier<? extends HttpService> service1,
                                 Supplier<? extends HttpService> service2,
                                 Supplier<? extends HttpService> service3,
                                 Supplier<? extends HttpService> service4,
                                 Supplier<? extends HttpService> service5) {
            HttpRules.super.register(service1, service2, service3, service4, service5);
            return this;
        }

        @Override
        default Builder register(List<Supplier<? extends HttpService>> services) {
            HttpRules.super.register(services);
            return this;
        }

        @Override
        Builder register(String path, HttpService... service);

        @Override
        default Builder register(String pathPattern, Supplier<? extends HttpService> service) {
            HttpRules.super.register(pathPattern, service);
            return this;
        }

        @Override
        default Builder register(String pathPattern,
                                 Supplier<? extends HttpService> service1,
                                 Supplier<? extends HttpService> service2) {
            HttpRules.super.register(pathPattern, service1, service2);
            return this;
        }

        @Override
        default Builder register(String pathPattern,
                                 Supplier<? extends HttpService> service1,
                                 Supplier<? extends HttpService> service2,
                                 Supplier<? extends HttpService> service3) {
            HttpRules.super.register(pathPattern, service1, service2, service3);
            return this;
        }

        @Override
        default Builder register(String pathPattern,
                                 Supplier<? extends HttpService> service1,
                                 Supplier<? extends HttpService> service2,
                                 Supplier<? extends HttpService> service3,
                                 Supplier<? extends HttpService> service4) {
            HttpRules.super.register(pathPattern, service1, service2, service3, service4);
            return this;
        }

        @Override
        default Builder register(String pathPattern,
                                 Supplier<? extends HttpService> service1,
                                 Supplier<? extends HttpService> service2,
                                 Supplier<? extends HttpService> service3,
                                 Supplier<? extends HttpService> service4,
                                 Supplier<? extends HttpService> service5) {
            HttpRules.super.register(pathPattern, service1, service2, service3, service4, service5);
            return this;
        }

        @Override
        default Builder register(String pathPattern, List<Supplier<? extends HttpService>> services) {
            HttpRules.super.register(pathPattern, services);
            return this;
        }

        @Override
        Builder route(HttpRoute route);

        @Override
        default Builder route(Supplier<? extends HttpRoute> route) {
            return route(route.get());
        }

        @Override
        default Builder route(Method method, String pathPattern, Handler handler) {
            return route(HttpRoute.builder()
                                 .methods(method)
                                 .path(pathPattern)
                                 .handler(handler));
        }

        @Override
        default Builder route(Method method, PathMatcher pathMatcher, Handler handler) {
            return route(HttpRoute.builder()
                                 .path(pathMatcher)
                                 .methods(method)
                                 .handler(handler));
        }

        @Override
        default Builder route(Predicate<Method> methodPredicate, PathMatcher pathMatcher, Handler handler) {
            return route(HttpRoute.builder()
                                 .path(pathMatcher)
                                 .methods(methodPredicate)
                                 .handler(handler));
        }

        @Override
        default Builder route(Method method, Handler handler) {
            return route(HttpRoute.builder()
                                 .methods(method)
                                 .handler(handler));
        }

        @Override
        default Builder get(String pathPattern, Handler... handlers) {
            for (Handler handler : handlers) {
                route(Method.GET, pathPattern, handler);
            }
            return this;
        }

        @Override
        default Builder get(Handler... handlers) {
            for (Handler handler : handlers) {
                route(Method.GET, handler);
            }
            return this;
        }

        @Override
        default Builder post(String pathPattern, Handler... handlers) {
            for (Handler handler : handlers) {
                route(Method.POST, pathPattern, handler);
            }
            return this;
        }

        @Override
        default Builder post(Handler... handlers) {
            for (Handler handler : handlers) {
                route(Method.POST, handler);
            }
            return this;
        }

        @Override
        default Builder put(String pathPattern, Handler... handlers) {
            for (Handler handler : handlers) {
                route(Method.PUT, pathPattern, handler);
            }
            return this;
        }

        @Override
        default Builder put(Handler... handlers) {
            for (Handler handler : handlers) {
                route(Method.PUT, handler);
            }
            return this;
        }

        @Override
        default Builder delete(String pathPattern, Handler... handlers) {
            for (Handler handler : handlers) {
                route(Method.DELETE, pathPattern, handler);
            }
            return this;
        }

        @Override
        default Builder delete(Handler... handlers) {
            for (Handler handler : handlers) {
                route(Method.DELETE, handler);
            }
            return this;
        }

        @Override
        default Builder head(String pathPattern, Handler... handlers) {
            for (Handler handler : handlers) {
                route(Method.HEAD, pathPattern, handler);
            }
            return this;
        }

        @Override
        default Builder head(Handler... handlers) {
            for (Handler handler : handlers) {
                route(Method.HEAD, handler);
            }
            return this;
        }

        @Override
        default Builder options(String pathPattern, Handler... handlers) {
            for (Handler handler : handlers) {
                route(Method.OPTIONS, pathPattern, handler);
            }
            return this;
        }

        @Override
        default Builder options(Handler... handlers) {
            for (Handler handler : handlers) {
                route(Method.OPTIONS, handler);
            }
            return this;
        }

        @Override
        default Builder trace(String pathPattern, Handler... handlers) {
            for (Handler handler : handlers) {
                route(Method.TRACE, pathPattern, handler);
            }
            return this;
        }

        @Override
        default Builder trace(Handler... handlers) {
            for (Handler handler : handlers) {
                route(Method.TRACE, handler);
            }
            return this;
        }

        @Override
        default Builder patch(String pathPattern, Handler... handlers) {
            for (Handler handler : handlers) {
                route(Method.PATCH, pathPattern, handler);
            }
            return this;
        }

        @Override
        default Builder patch(Handler... handlers) {
            for (Handler handler : handlers) {
                route(Method.PATCH, handler);
            }
            return this;
        }

        @Override
        default Builder any(String pathPattern, Handler... handlers) {
            for (Handler handler : handlers) {
                route(HttpRoute.builder()
                              .path(pathPattern)
                              .handler(handler));
            }
            return this;
        }

        @Override
        default Builder any(Handler... handlers) {
            for (Handler handler : handlers) {
                route(HttpRoute.builder()
                              .handler(handler));
            }
            return this;
        }

        @Override
        default Builder route(Method method, String pathPattern, Consumer<ServerRequest> handler) {
            return route(HttpRoute.builder()
                                 .methods(method)
                                 .path(pathPattern)
                                 .handler(Handler.create(handler)));
        }

        @Override
        default Builder route(Method method, String pathPattern, Function<ServerRequest, ?> handler) {
            return route(HttpRoute.builder()
                                 .methods(method)
                                 .path(pathPattern)
                                 .handler(Handler.create(handler)));
        }

        @Override
        default Builder route(Method method, String pathPattern, Supplier<?> handler) {
            return route(HttpRoute.builder()
                                 .methods(method)
                                 .path(pathPattern)
                                 .handler(Handler.create(handler)));
        }

        /**
         * Add a new filter.
         *
         * @param filter filter to add
         * @return updated builder
         */
        Builder addFilter(Filter filter);

        /**
         * Add a new feature.
         * If a feature is added from within a feature, it will inherit weight of the feature adding it and will be fully
         * registered at the same time.
         *
         * @param feature feature to add
         * @return updated builder
         */
        default Builder addFeature(HttpFeature feature) {
            return addFeature((Supplier<? extends HttpFeature>) feature);
        }

        /**
         * Add a new feature.
         * If a feature is added from within a feature, it will inherit weight of the feature adding it and will be fully
         * registered at the same time.
         *
         * @param feature feature to add
         * @return updated builder
         */
        Builder addFeature(Supplier<? extends HttpFeature> feature);

        /**
         * Registers an error handler that handles the given type of exceptions.
         * This will replace an existing error handler for the same exception class.
         *
         * @param exceptionClass the type of exception to handle by this handler
         * @param handler        the error handler
         * @param <T>            exception type
         * @return updated builder
         */
        <T extends Throwable> Builder error(Class<T> exceptionClass, ErrorHandler<? super T> handler);

        /**
         * Maximal number of allowed re-routes within routing.
         *
         * @param maxReRouteCount maximum number of allowed reroutes
         * @return updated builder
         * @see ServerResponse#reroute(String)
         * @see ServerResponse#reroute(String, io.helidon.common.uri.UriQuery)
         */
        Builder maxReRouteCount(int maxReRouteCount);

        /**
         * Configure security for this routing.
         *
         * @param security security to use
         * @return updated builder
         */
        Builder security(HttpSecurity security);

        /**
         * Create a copy of this builder that has the same routes, but is not backed by the same lists/maps.
         * Modifications to the routes of the copy will not modify routes of this builder.
         *
         * @return builder that is a copy of this builder
         */
        Builder copy();
    }
}
