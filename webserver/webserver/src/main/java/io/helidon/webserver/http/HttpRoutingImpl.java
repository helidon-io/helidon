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

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import io.helidon.common.Weights;
import io.helidon.http.DirectHandler;
import io.helidon.http.HttpException;
import io.helidon.http.HttpPrologue;
import io.helidon.http.NotFoundException;
import io.helidon.http.RequestException;
import io.helidon.http.Status;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ServerLifecycle;
import io.helidon.webserver.WebServer;

final class HttpRoutingImpl implements HttpRouting {
    private static final System.Logger LOGGER = System.getLogger(HttpRoutingImpl.class.getName());
    private static final HttpRoutingImpl EMPTY = builder().build();

    private final Filters filters;
    private final ServiceRoute rootRoute;
    private final List<HttpFeature> features;
    private final int maxReRouteCount;
    private final HttpSecurity security;

    HttpRoutingImpl(RealBuilder builder) {
        ErrorHandlers errorHandlers = ErrorHandlers.create(builder.errorHandlers);
        this.filters = Filters.create(errorHandlers, List.copyOf(builder.filters));
        this.rootRoute = builder.rootRules.build();
        this.features = List.copyOf(builder.features);
        this.maxReRouteCount = builder.maxReRouteCount;
        this.security = builder.security;
    }

    static BuilderImpl builder() {
        return new BuilderImpl();
    }

    /**
     * Empty routing (all requests will return {@link Status#NOT_FOUND_404}.
     *
     * @return empty routing
     */
    static HttpRoutingImpl empty() {
        return EMPTY;
    }

    @Override
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
    public void afterStart(WebServer webServer) {
        filters.afterStart(webServer);
        rootRoute.afterStart(webServer);
        features.forEach(f -> f.afterStart(webServer));
    }

    @Override
    public void afterStop() {
        filters.afterStop();
        rootRoute.afterStop();
        features.forEach(ServerLifecycle::afterStop);
    }

    @Override
    public HttpSecurity security() {
        return security;
    }


    private enum RoutingResult {
        ROUTE,
        FINISH,
        NONE
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
                throw new NotFoundException("Endpoint not found");
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
            throw new NotFoundException("Endpoint not found");
        }

        private RoutingResult doRoute(ConnectionContext ctx, RoutingRequest request, RoutingResponse response) throws Exception {
            HttpPrologue prologue = request.prologue();
            RouteCrawler crawler = rootRoute.crawler(ctx, request);

            while (crawler.hasNext()) {
                response.resetRouting();
                RouteCrawler.CrawlerItem next = crawler.next();
                request.path(next.path());
                request.matchingPattern(next.matchingElement());

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


    private static class BuilderImpl implements Builder {
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
        public HttpRoutingImpl build() {
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

            return new HttpRoutingImpl(realBuilder);
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
