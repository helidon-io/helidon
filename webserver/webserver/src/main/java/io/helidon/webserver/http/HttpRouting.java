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

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.helidon.builder.api.Prototype;
import io.helidon.common.Weights;
import io.helidon.http.DirectHandler;
import io.helidon.http.HttpException;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.NotFoundException;
import io.helidon.http.PathMatcher;
import io.helidon.http.RequestException;
import io.helidon.http.Status;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerLifecycle;

/**
 * HTTP routing.
 * This routing is capable of handling any HTTP version.
 */
public final class HttpRouting implements Routing, Prototype.Api {
    private static final System.Logger LOGGER = System.getLogger(HttpRouting.class.getName());
    private static final HttpRouting EMPTY = HttpRouting.builder().build();

    private final Filters filters;
    private final ServiceRoute rootRoute;
    private final List<HttpFeature> features;
    private final int maxReRouteCount;
    private final HttpSecurity security;

    private HttpRouting(RealBuilder builder) {
        ErrorHandlers errorHandlers = ErrorHandlers.create(builder.errorHandlers);
        this.filters = Filters.create(errorHandlers, List.copyOf(builder.filters));
        this.rootRoute = builder.rootRules.build();
        this.features = List.copyOf(builder.features);
        this.maxReRouteCount = builder.maxReRouteCount;
        this.security = builder.security;
    }

    /**
     * Creates new instance of {@link HttpRouting.Builder router builder}.
     *
     * @return a new instance
     */
    public static Builder builder() {
        return new BuilderImpl();
    }

    /**
     * Create a default router.
     *
     * @return new default router
     */
    public static HttpRouting create() {
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
    public static HttpRouting empty() {
        return EMPTY;
    }

    /**
     * Route a request.
     * Handler HTTP filters and finds a route.
     *
     * @param ctx      context
     * @param request  routing request
     * @param response routing response
     */
    public void route(ConnectionContext ctx, RoutingRequest request, RoutingResponse response) {
        RoutingExecutor routingExecutor = new RoutingExecutor(ctx, rootRoute, request, response, maxReRouteCount);
        // we cannot throw an exception to the filters, as then the filter would not have information about actual status
        // code, so error handling is done in routing executor and for each filter
        filters.filter(ctx, request, response, routingExecutor);
    }

    @Override
    public void beforeStart() {
        filters.beforeStart();
        rootRoute.beforeStart();
        features.forEach(ServerLifecycle::beforeStart);
    }

    @Override
    public void afterStop() {
        filters.afterStop();
        rootRoute.afterStop();
        features.forEach(ServerLifecycle::afterStop);
    }

    /**
     * Security associated with this routing.
     *
     * @return security
     */
    public HttpSecurity security() {
        return security;
    }

    private enum RoutingResult {
        ROUTE,
        FINISH,
        NONE
    }

    /**
     * Fluent API builder for {@link HttpRouting}.
     */
    public interface Builder extends HttpRules, io.helidon.common.Builder<Builder, HttpRouting> {
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

    static class BuilderImpl implements Builder {
        // collects features, before build, these will be ordered by weight and registered
        private final List<HttpFeature> features = new ArrayList<>();
        private final HttpRoutingFeature mainRouting = new HttpRoutingFeature();
        private HttpSecurity security = HttpSecurity.create();
        private int maxReRouteCount = 10;

        private BuilderImpl() {
        }

        private BuilderImpl(List<HttpFeature> features, HttpRoutingFeature mainRouting, HttpSecurity security, int maxReroute) {
            this.features.addAll(features);
            this.mainRouting.copyFrom(mainRouting);
            this.security = security;
            this.maxReRouteCount = maxReroute;
        }

        @Override
        public HttpRouting build() {
            features.add(mainRouting);

            Weights.sort(features);

            RealBuilder realBuilder = new RealBuilder(features,
                                                      security,
                                                      maxReRouteCount);

            // now we need to do the final setup in the correct order
            for (HttpFeature feature : features) {
                if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                    LOGGER.log(System.Logger.Level.TRACE, "Setting up feature: " + feature.getClass().getName());
                }
                feature.setup(realBuilder);
            }

            return new HttpRouting(realBuilder);
        }

        @Override
        public Builder register(HttpService... service) {
            mainRouting.service(service);
            return this;
        }

        @Override
        public Builder register(String path, HttpService... service) {
            mainRouting.service(path, service);
            return this;
        }

        @Override
        public Builder route(HttpRoute route) {
            mainRouting.route(route);
            return this;
        }

        @Override
        public Builder addFilter(Filter filter) {
            mainRouting.filter(filter);
            return this;
        }

        @Override
        public Builder addFeature(Supplier<? extends HttpFeature> feature) {
            HttpFeature httpFeature = feature.get();
            features.add(httpFeature);
            return this;
        }

        @Override
        public <T extends Throwable> Builder error(Class<T> exceptionClass, ErrorHandler<? super T> handler) {
            mainRouting.error(exceptionClass, handler);
            return this;
        }

        @Override
        public Builder maxReRouteCount(int maxReRouteCount) {
            this.maxReRouteCount = maxReRouteCount;
            return this;
        }

        @Override
        public Builder security(HttpSecurity security) {
            this.security = security;
            return this;
        }

        @Override
        public Builder copy() {
            return new BuilderImpl(features, mainRouting, security, maxReRouteCount);
        }
    }

    private static final class RoutingExecutor implements Callable<Void> {
        private final ConnectionContext ctx;
        private final RoutingRequest request;
        private final RoutingResponse response;
        private final ServiceRoute rootRoute;
        private final int maxReRouteCount;

        private RoutingExecutor(ConnectionContext ctx,
                                ServiceRoute rootRoute,
                                RoutingRequest request,
                                RoutingResponse response,
                                int maxReRouteCount) {
            this.ctx = ctx;
            this.rootRoute = rootRoute;
            this.request = request;
            this.response = response;
            this.maxReRouteCount = maxReRouteCount;
        }

        @Override
        public Void call() throws Exception {
            // initial attempt - most common case, handled separately
            RoutingResult result = doRoute(ctx, request, response);

            if (result == RoutingResult.FINISH) {
                response.commit();
                return null;
            }
            if (result == RoutingResult.NONE) {
                throw new NotFoundException("Not Found");
            }

            // rerouting, do the more heavyweight while loop
            int counter = 1;
            while (result == RoutingResult.ROUTE) {
                counter++;
                if (counter == maxReRouteCount) {
                    LOGGER.log(System.Logger.Level.ERROR, "Rerouted more than " + maxReRouteCount
                            + " times. Will not attempt further routing");

                    throw new HttpException("Too many reroutes", Status.INTERNAL_SERVER_ERROR_500, true);
                }

                result = doRoute(ctx, request, response);
            }

            // finished and done
            if (result == RoutingResult.FINISH) {
                response.commit();
                return null;
            }
            throw new NotFoundException("Not Found");
        }

        private RoutingResult doRoute(ConnectionContext ctx, RoutingRequest request, RoutingResponse response) throws Exception {
            HttpPrologue prologue = request.prologue();
            RouteCrawler crawler = rootRoute.crawler(ctx, request);

            while (crawler.hasNext()) {
                response.resetRouting();
                RouteCrawler.CrawlerItem next = crawler.next();
                request.path(next.path());

                next.handler().handle(request, response);
                if (response.shouldReroute()) {
                    if (response.isSent()) {
                        LOGGER.log(System.Logger.Level.WARNING, "Request to " + request.prologue()
                                + " in inconsistent state. Request to re-route, but response was already sent. Ignoring "
                                + "reroute.");
                        return RoutingResult.FINISH;
                    }
                    HttpPrologue newPrologue = response.reroutePrologue(prologue);
                    request.prologue(newPrologue);
                    response.resetRouting();
                    return RoutingResult.ROUTE;
                }
                if (response.isNexted()) {
                    if (response.isSent()) {
                        LOGGER.log(System.Logger.Level.WARNING, "Request to " + request.prologue()
                                + " in inconsistent state. Request to next, but response was already sent. "
                                + "Ignoring next().");
                        return RoutingResult.FINISH;
                    }
                    continue;
                }
                if (response.hasEntity()) {
                    return RoutingResult.FINISH;
                }

                // not nexted, not rerouted - invalid state
                // user must send a response within the current thread
                LOGGER.log(System.Logger.Level.WARNING,
                           "A route MUST call either send, reroute, or next on ServerResponse on the request thread. "
                                   + "Neither of these was called for request: " + request.prologue()
                                   + "; Handler: " + next.handler());

                throw RequestException.builder()
                        // we cannot share the information above with a client
                        .message("Internal Server Error")
                        .type(DirectHandler.EventType.INTERNAL_ERROR)
                        .build();
            }

            return RoutingResult.NONE;
        }
    }

    private static final class RealBuilder implements Builder {
        private final List<Filter> filters = new ArrayList<>();
        private final Map<Class<? extends Throwable>, ErrorHandler<?>> errorHandlers = new IdentityHashMap<>();
        private final ServiceRules rootRules = new ServiceRules();
        private final List<HttpFeature> features;

        private HttpSecurity security;
        private int maxReRouteCount;

        private RealBuilder(List<HttpFeature> features,
                            HttpSecurity security,
                            int maxReRouteCount) {

            // we need a new instance, as features may add additional features
            this.features = new ArrayList<>(features);
            this.security = security;
            this.maxReRouteCount = maxReRouteCount;
        }

        @Override
        public HttpRouting build() {
            throw new UnsupportedOperationException("This builder is internal and never built");
        }

        @Override
        public Builder register(HttpService... service) {
            rootRules.register(service);
            return this;
        }

        @Override
        public Builder register(String pathPattern, HttpService... service) {
            rootRules.register(pathPattern, service);
            return this;
        }

        @Override
        public Builder route(HttpRoute route) {
            rootRules.route(route);
            return this;
        }

        @Override
        public Builder addFilter(Filter filter) {
            filters.add(filter);
            return this;
        }

        @Override
        public Builder addFeature(Supplier<? extends HttpFeature> feature) {
            HttpFeature it = feature.get();
            features.add(it);
            it.setup(this);
            return this;
        }

        @Override
        public <T extends Throwable> Builder error(Class<T> exceptionClass, ErrorHandler<? super T> handler) {
            errorHandlers.put(exceptionClass, handler);
            return this;
        }

        @Override
        public Builder maxReRouteCount(int maxReRouteCount) {
            this.maxReRouteCount = maxReRouteCount;
            return this;
        }

        @Override
        public Builder security(HttpSecurity security) {
            this.security = security;
            return this;
        }

        @Override
        public Builder copy() {
            throw new UnsupportedOperationException("This builder should only be used internally by Helidon and never copied");
        }
    }
}
