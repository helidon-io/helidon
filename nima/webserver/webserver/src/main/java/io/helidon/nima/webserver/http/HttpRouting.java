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

package io.helidon.nima.webserver.http;

import java.io.UncheckedIOException;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.helidon.common.http.Http;
import io.helidon.common.http.HttpPrologue;
import io.helidon.nima.webserver.CloseConnectionException;
import io.helidon.nima.webserver.ConnectionContext;
import io.helidon.nima.webserver.Routing;
import io.helidon.nima.webserver.SimpleHandler;

/**
 * HTTP routing.
 * This routing is capable of handling any HTTP version.
 */
public final class HttpRouting implements Routing {
    private static final System.Logger LOGGER = System.getLogger(HttpRouting.class.getName());
    private static final HttpRouting EMPTY = HttpRouting.builder().build();

    private final Filters filters;
    private final ServiceRoute rootRoute;

    private HttpRouting(Builder builder) {
        this.filters = Filters.create(List.copyOf(builder.filters));
        this.rootRoute = builder.rootRules.build();
    }

    /**
     * Creates new instance of {@link io.helidon.nima.webserver.http.HttpRouting.Builder router builder}.
     *
     * @return a new instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a default router.
     *
     * @return new default router
     */
    public static HttpRouting create() {
        return HttpRouting.builder()
                .route(HttpRoute.builder()
                               .handler((req, res) -> res.send("Níma server works!"))
                               .build())
                .build();
    }

    /**
     * Empty routing (all requests will return {@link io.helidon.common.http.Http.Status#NOT_FOUND_404}.
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
        Filters.FilterResult filterResult = filters.filter(request, response);
        if (filterResult == Filters.FilterResult.FINISH) {
            return;
        }

        HttpPrologue prologue = request.prologue();
        response.resetRouting();

        RoutingResult result = RoutingResult.ROUTE;
        int counter = 0;
        while (result == RoutingResult.ROUTE) {
            counter++;
            if (counter == 10) {
                LOGGER.log(System.Logger.Level.ERROR, "Rerouted more than 10 times. Will not attempt further routing");
                throw HttpException.builder()
                        .request(HttpSimpleRequest.create(prologue, request.headers()))
                        .type(SimpleHandler.EventType.INTERNAL_ERROR)
                        .build();
            }
            result = doRoute(ctx, request, response);
        }

        // finished and done
        if (result == RoutingResult.FINISH) {
            return;
        }

        throw HttpException.builder()
                .request(request)
                .response(response)
                .type(SimpleHandler.EventType.NOT_FOUND)
                .message("Endpoint not found")
                .build();
    }

    @Override
    public void beforeStart() {
        rootRoute.beforeStart();
    }

    @Override
    public void afterStop() {
        rootRoute.afterStop();
    }

    // todo we may have a lru cache (or most commonly used - weighted by number of usages) to
    // immediately get a route based on request prologue
    private RoutingResult doRoute(ConnectionContext ctx, RoutingRequest request, RoutingResponse response) {
        HttpPrologue prologue = request.prologue();
        RouteCrawler crawler = rootRoute.crawler(ctx, request);

        while (crawler.hasNext()) {
            response.resetRouting();
            RouteCrawler.CrawlerItem next = crawler.next();
            request.path(next.path());
            try {
                next.handler().handle(request, response);
                if (response.shouldReroute()) {
                    if (response.isSent()) {
                        LOGGER.log(System.Logger.Level.WARNING, "Request to " + request.prologue()
                                + " in inconsistent state. Request for re-router, but response was already sent. Ignoring "
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
                                + " in inconsistent state. Request was nexted, but response was already sent. "
                                + "Ignoring next().");
                        return RoutingResult.FINISH;
                    }
                    continue;
                }
                if (!response.hasEntity()) {
                    // not nexted, not rerouted - just send it!
                    response.send();
                }

                return RoutingResult.FINISH;
            } catch (CloseConnectionException | HttpException e) {
                throw e;
            } catch (Exception thrown) {
                // TODO need to use error handler, error handler may reroute/next
                // prevent infinite loops here
                if (thrown.getCause() instanceof SocketException se) {
                    throw new UncheckedIOException(se);
                }
                if (response.isSent()) {
                    ctx.log(LOGGER, System.Logger.Level.WARNING, "Request failed: " + request.prologue()
                            + ", cannot send error response, as response already sent", thrown);
                } else {
                    boolean keepAlive = true;
                    try {
                        request.content().consume();
                    } catch (Exception e) {
                        keepAlive = request.content().consumed();
                    }
                    // attempt to consume the request entity
                    ctx.log(LOGGER, System.Logger.Level.WARNING, "Request failed", thrown);
                    // we must close connection, as we could not consume request
                    throw HttpException.builder()
                            .type(SimpleHandler.EventType.INTERNAL_ERROR)
                            .cause(thrown)
                            .request(request)
                            .response(response)
                            .message(thrown.getMessage())
                            .setKeepAlive(keepAlive)
                            .build();
                }
            }
        }

        return RoutingResult.NONE;
    }

    private enum RoutingResult {
        ROUTE,
        FINISH,
        NONE
    }

    /**
     * Fluent API builder for {@link io.helidon.nima.webserver.http.HttpRouting}.
     */
    public static class Builder implements HttpRules, io.helidon.common.Builder<Builder, HttpRouting> {
        private final List<Filter> filters = new LinkedList<>();
        private final ServiceRules rootRules = new ServiceRules();

        private Builder() {
        }

        @Override
        public HttpRouting build() {
            return new HttpRouting(this);
        }

        /**
         * Add a new filter.
         *
         * @param filter filter to add
         * @return updated builder
         */
        public Builder addFilter(Filter filter) {
            filters.add(filter);
            return this;
        }

        @Override
        public Builder register(Supplier<? extends HttpService>... service) {
            rootRules.register(service);
            return this;
        }

        @Override
        public Builder register(String path, Supplier<? extends HttpService>... service) {
            rootRules.register(path, service);
            return this;
        }

        @Override
        public Builder route(HttpRoute route) {
            rootRules.route(route);
            return this;
        }

        @Override
        public Builder route(Supplier<? extends HttpRoute> route) {
            return route(route.get());
        }

        @Override
        public Builder route(Http.Method method, String pathPattern, Handler handler) {
            return route(HttpRoute.builder()
                                 .methods(method)
                                 .path(pathPattern)
                                 .handler(handler));
        }

        @Override
        public Builder route(Http.Method method, PathMatcher pathMatcher, Handler handler) {
            return route(HttpRoute.builder()
                                 .path(pathMatcher)
                                 .methods(method)
                                 .handler(handler));
        }

        @Override
        public HttpRules route(Predicate<Http.Method> methodPredicate, PathMatcher pathMatcher, Handler handler) {
            return route(HttpRoute.builder()
                                 .path(pathMatcher)
                                 .methods(methodPredicate)
                                 .handler(handler));
        }

        /**
         * Add a post route.
         *
         * @param pathPattern path pattern
         * @param handler     handler
         * @return updated builder
         */
        public Builder post(String pathPattern, Handler handler) {
            return route(HttpRoute.builder()
                                 .methods(Http.Method.POST)
                                 .path(pathPattern)
                                 .handler(handler));
        }

        /**
         * Add a route to handle any method.
         *
         * @param handler handler to use
         * @return updated builder
         */
        public Builder any(Handler handler) {
            return route(HttpRoute.builder()
                                 .handler(handler));
        }

        /**
         * Add a route.
         *
         * @param method      method to handle
         * @param pathPattern path pattern
         * @param handler     handler as a consumer of {@link ServerRequest}
         * @return updated builder
         */
        public Builder route(Http.Method method, String pathPattern, Consumer<ServerRequest> handler) {
            return route(HttpRoute.builder()
                                 .methods(method)
                                 .path(pathPattern)
                                 .handler(Handler.create(handler)));
        }

        /**
         * Add a route.
         *
         * @param method      method to handle
         * @param pathPattern path pattern
         * @param handler     handler as a function that gets {@link ServerRequest} and returns an entity
         * @return updated builder
         */
        public Builder route(Http.Method method, String pathPattern, Function<ServerRequest, ?> handler) {
            return route(HttpRoute.builder()
                                 .methods(method)
                                 .path(pathPattern)
                                 .handler(Handler.create(handler)));
        }

        /**
         * Add a route.
         *
         * @param method      method to handle
         * @param pathPattern path pattern
         * @param handler     supplier of entity
         * @return updated builder
         */
        public Builder route(Http.Method method, String pathPattern, Supplier<?> handler) {
            return route(HttpRoute.builder()
                                 .methods(method)
                                 .path(pathPattern)
                                 .handler(Handler.create(handler)));
        }

        /**
         * Add a get route.
         *
         * @param pathPattern path pattern
         * @param handler     handler to use
         * @return updated builder
         */
        public Builder get(String pathPattern, Handler handler) {
            return route(HttpRoute.builder()
                                 .methods(Http.Method.GET)
                                 .path(pathPattern)
                                 .handler(handler));
        }

        /**
         * Add a route to handle any method.
         *
         * @param pattern path pattern
         * @param handler handler
         * @return updated builder
         */
        public Builder any(String pattern, Handler handler) {
            return route(HttpRoute.builder()
                                 .path(pattern)
                                 .handler(handler));
        }
    }
}
