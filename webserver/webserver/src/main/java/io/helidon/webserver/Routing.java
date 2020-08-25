/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.common.http.Http;

/**
 * Routing represents composition of HTTP request-response handlers with routing rules.
 * It is a cornerstone of the {@link WebServer}.
 *
 * @see WebServer
 */
public interface Routing {

    /**
     * Process bare minimal request and response using this routing.
     *
     * @param bareRequest HTTP request to process
     * @param bareResponse HTTP response to process
     */
    void route(BareRequest bareRequest, BareResponse bareResponse);

    /**
     * Creates new instance of {@link Builder routing builder}.
     *
     * @return a new instance
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Creates new {@link WebServer} instance with provided configuration and this routing.
     *
     * @param configuration a web server configuration
     * @return new {@link WebServer} instance
     * @throws IllegalStateException if none SPI implementation found
     *
     * @deprecated since 2.0.0 please use {@link WebServer#builder(Routing)} instead
     */
    @Deprecated
    default WebServer createServer(ServerConfiguration configuration) {
        return WebServer.create(configuration, this);
    }

    /**
     * Creates new {@link WebServer} instance with this routing and default configuration.
     *
     * @return new {@link WebServer} instance
     * @throws IllegalStateException if none SPI implementation found
     *
     * @deprecated since 2.0.0 please use {@link WebServer#builder(Routing)} instead
     */
    @Deprecated
    default WebServer createServer() {
        return WebServer.create(this);
    }

    /**
     * An API to define HTTP request routing rules.
     *
     * @see Builder
     */
    interface Rules {
        /**
         * Configuration of tracing for this routing.
         * The configuration may control whether to log specific components,
         *  spans and span logs, either globally, or for a specific path and method combinations.
         *
         * @param webTracingConfig WebServer tracing configuration
         * @return Updated routing configuration
         */
        Rules register(WebTracingConfig webTracingConfig);

        /**
         * Registers builder consumer. It enables to separate complex routing definitions to dedicated classes.
         *
         * @param services services to register
         * @return Updated routing configuration
         */
        Rules register(Service... services);

        /**
         * Registers builder consumer. It enables to separate complex routing definitions to dedicated classes.
         *
         * @param serviceBuilders service builder to register; they will be built as a first step of this
         *                        method execution
         * @return Updated routing configuration
         */
        Rules register(Supplier<? extends Service>... serviceBuilders);

        /**
         * Registers builder consumer. It enables to separate complex routing definitions to dedicated classes.
         *
         * @param pathPattern a URI path pattern. See {@link PathMatcher} for pattern syntax reference.
         * @param services    services to register
         * @return Updated routing configuration
         */
        Rules register(String pathPattern, Service... services);

        /**
         * Registers builder consumer. It enables to separate complex routing definitions to dedicated classes.
         *
         * @param pathPattern     a URI path pattern. See {@link PathMatcher} for pattern syntax reference.
         * @param serviceBuilders service builder to register; they will be built as a first step of this
         *                        method execution
         * @return an updated routing configuration
         */
        Rules register(String pathPattern, Supplier<? extends Service>... serviceBuilders);

        /**
         * Routes all GET requests to provided handler(s). Request handler can call {@link ServerRequest#next()}
         * to continue processing on the next registered handler.
         *
         * @param requestHandlers handlers to process HTTP request
         * @return an updated routing configuration
         */
        Rules get(Handler... requestHandlers);

        /**
         * Routes GET requests with corresponding path to provided handler(s). Request handler can call
         * {@link ServerRequest#next()} to continue processing on the next registered handler.
         *
         * @param pathPattern     a URI path pattern. See {@link PathMatcher} for pattern syntax reference.
         * @param requestHandlers handlers to process HTTP request
         * @return an updated routing configuration
         */
        Rules get(String pathPattern, Handler... requestHandlers);

        /**
         * Routes GET requests with corresponding path to provided handler(s). Request handler can call
         * {@link ServerRequest#next()} to continue processing on the next registered handler.
         *
         * @param pathMatcher     a URI path pattern. See {@link PathMatcher} for pattern syntax reference.
         * @param requestHandlers handlers to process HTTP request
         * @return an updated routing configuration
         */
        Rules get(PathMatcher pathMatcher, Handler... requestHandlers);

        /**
         * Routes all PUT requests to provided handler(s). Request handler can call {@link ServerRequest#next()}
         * to continue processing on the next registered handler.
         *
         * @param requestHandlers handlers to process HTTP request
         * @return an updated routing configuration
         */
        Rules put(Handler... requestHandlers);

        /**
         * Routes PUT requests with corresponding path to provided handler(s). Request handler can call
         * {@link ServerRequest#next()} to continue processing on the next registered handler.
         *
         * @param pathPattern     a URI path pattern. See {@link PathMatcher} for pattern syntax reference.
         * @param requestHandlers handlers to process HTTP request
         * @return an updated routing configuration
         */
        Rules put(String pathPattern, Handler... requestHandlers);

        /**
         * Routes PUT requests with corresponding path to provided handler(s). Request handler can call
         * {@link ServerRequest#next()} to continue processing on the next registered handler.
         *
         * @param pathMatcher     define path for a registered router
         * @param requestHandlers handlers to process HTTP request
         * @return an updated routing configuration
         */
        Rules put(PathMatcher pathMatcher, Handler... requestHandlers);

        /**
         * Routes all POST requests to provided handler(s). Request handler can call {@link ServerRequest#next()}
         * to continue processing on the next registered handler.
         *
         * @param requestHandlers handlers to process HTTP request
         * @return an updated routing configuration
         */
        Rules post(Handler... requestHandlers);

        /**
         * Routes POST requests with corresponding path to provided handler(s). Request handler can call
         * {@link ServerRequest#next()} to continue processing on the next registered handler.
         *
         * @param pathPattern     a URI path pattern. See {@link PathMatcher} for pattern syntax reference.
         * @param requestHandlers handlers to process HTTP request
         * @return an updated routing configuration
         */
        Rules post(String pathPattern, Handler... requestHandlers);

        /**
         * Routes POST requests with corresponding path to provided handler(s). Request handler can call
         * {@link ServerRequest#next()} to continue processing on the next registered handler.
         *
         * @param pathMatcher     define path for registered router
         * @param requestHandlers handlers to process HTTP request
         * @return an updated routing configuration
         */
        Rules post(PathMatcher pathMatcher, Handler... requestHandlers);

        /**
         * Routes all RFC 5789 PATCH requests to provided handler(s). Request handler can call {@link ServerRequest#next()}
         * to continue processing on the next registered handler.
         *
         * @param requestHandlers handlers to process HTTP request
         * @return an updated routing configuration
         */
        Rules patch(Handler... requestHandlers);

        /**
         * Routes RFC 5789 PATCH requests with corresponding path to provided handler(s). Request handler can call
         * {@link ServerRequest#next()} to continue processing on the next registered handler.
         *
         * @param pathPattern     a URI path pattern. See {@link PathMatcher} for pattern syntax reference.
         * @param requestHandlers handlers to process HTTP request
         * @return an updated routing configuration
         */
        Rules patch(String pathPattern, Handler... requestHandlers);

        /**
         * Routes RFC 5789 PATCH requests with corresponding path to provided handler(s). Request handler can call
         * {@link ServerRequest#next()} to continue processing on the next registered handler.
         *
         * @param pathMatcher     define path for registered router
         * @param requestHandlers handlers to process HTTP request
         * @return an updated routing configuration
         */
        Rules patch (PathMatcher pathMatcher, Handler... requestHandlers);

        /**
         * Routes all DELETE requests to provided handler(s). Request handler can call {@link ServerRequest#next()}
         * to continue processing on the next registered handler.
         *
         * @param requestHandlers handlers to process HTTP request
         * @return an updated routing configuration
         */
        Rules delete(Handler... requestHandlers);

        /**
         * Routes DELETE requests with corresponding path to provided handler(s). Request handler can call
         * {@link ServerRequest#next()} to continue processing on the next registered handler.
         *
         * @param pathPattern     a URI path pattern. See {@link PathMatcher} for pattern syntax reference.
         * @param requestHandlers handlers to process HTTP request
         * @return an updated routing configuration
         */
        Rules delete(String pathPattern, Handler... requestHandlers);

        /**
         * Routes DELETE requests with corresponding path to provided handler(s). Request handler can call
         * {@link ServerRequest#next()} to continue processing on the next registered handler.
         *
         * @param pathMatcher     define path for registered router
         * @param requestHandlers handlers to process HTTP request
         * @return an updated routing configuration
         */
        Rules delete(PathMatcher pathMatcher, Handler... requestHandlers);

        /**
         * Routes all OPTIONS requests to provided handler(s). Request handler can call {@link ServerRequest#next()}
         * to continue processing on the next registered handler.
         *
         * @param requestHandlers handlers to process HTTP request
         * @return an updated routing configuration
         */
        Rules options(Handler... requestHandlers);

        /**
         * Routes OPTIONS requests with corresponding path to provided handler(s). Request handler can call
         * {@link ServerRequest#next()} to continue processing on the next registered handler.
         *
         * @param pathPattern     a URI path pattern. See {@link PathMatcher} for pattern syntax reference.
         * @param requestHandlers handlers to process HTTP request
         * @return an updated routing configuration
         */
        Rules options(String pathPattern, Handler... requestHandlers);

        /**
         * Routes OPTIONS requests with corresponding path to provided handler(s). Request handler can call
         * {@link ServerRequest#next()} to continue processing on the next registered handler.
         *
         * @param pathMatcher     define path for registered router
         * @param requestHandlers handlers to process HTTP request
         * @return an updated routing configuration
         */
        Rules options(PathMatcher pathMatcher, Handler... requestHandlers);

        /**
         * Routes all HEAD requests to provided handler(s). Request handler can call {@link ServerRequest#next()}
         * to continue processing on the next registered handler.
         *
         * @param requestHandlers handlers to process HTTP request
         * @return an updated routing configuration
         */
        Rules head(Handler... requestHandlers);

        /**
         * Routes HEAD requests with corresponding path to provided handler(s). Request handler can call
         * {@link ServerRequest#next()} to continue processing on the next registered handler.
         *
         * @param pathPattern     a URI path pattern. See {@link PathMatcher} for pattern syntax reference.
         * @param requestHandlers handlers to process HTTP request
         * @return an updated routing configuration
         */
        Rules head(String pathPattern, Handler... requestHandlers);

        /**
         * Routes HEAD requests with corresponding path to provided handler(s). Request handler can call
         * {@link ServerRequest#next()} to continue processing on the next registered handler.
         *
         * @param pathMatcher     define path for registered router
         * @param requestHandlers handlers to process HTTP request
         * @return an updated routing configuration
         */
        Rules head(PathMatcher pathMatcher, Handler... requestHandlers);

        /**
         * Routes all TRACE requests to provided handler(s). Request handler can call {@link ServerRequest#next()}
         * to continue processing on the next registered handler.
         *
         * @param requestHandlers handlers to process HTTP request
         * @return an updated routing configuration
         */
        Rules trace(Handler... requestHandlers);

        /**
         * Routes TRACE requests with corresponding path to provided handler(s). Request handler can call
         * {@link ServerRequest#next()} to continue processing on the next registered handler.
         *
         * @param pathPattern     a URI path pattern. See {@link PathMatcher} for pattern syntax reference.
         * @param requestHandlers handlers to process HTTP request
         * @return an updated routing configuration
         */
        Rules trace(String pathPattern, Handler... requestHandlers);

        /**
         * Routes TRACE requests with corresponding path to provided handler(s). Request handler can call
         * {@link ServerRequest#next()} to continue processing on the next registered handler.
         *
         * @param pathMatcher     define path for registered router
         * @param requestHandlers handlers to process HTTP request
         * @return an updated routing configuration
         */
        Rules trace(PathMatcher pathMatcher, Handler... requestHandlers);

        /**
         * Routes all requests to provided handler(s). Request handler can call {@link ServerRequest#next()}
         * to continue processing on the next registered handler.
         *
         * @param requestHandlers handlers to process HTTP request
         * @return an updated routing configuration
         */
        Rules any(Handler... requestHandlers);

        /**
         * Routes all requests with corresponding path to provided handler(s). Request handler can call
         * {@link ServerRequest#next()} to continue processing on the next registered handler.
         *
         * @param pathPattern     a URI path pattern. See {@link PathMatcher} for pattern syntax reference.
         * @param requestHandlers handlers to process HTTP request
         * @return an updated routing configuration
         */
        Rules any(String pathPattern, Handler... requestHandlers);

        /**
         * Routes all requests with corresponding path to provided handler(s). Request handler can call
         * {@link ServerRequest#next()} to continue processing on the next registered handler.
         *
         * @param pathMatcher     define path for registered router
         * @param requestHandlers handlers to process HTTP request
         * @return an updated routing configuration
         */
        Rules any(PathMatcher pathMatcher, Handler... requestHandlers);

        /**
         * Routes requests any specified method to provided handler(s). Request handler can call {@link ServerRequest#next()}
         * to continue processing on the next registered handler.
         *
         * @param methods         HTTP methods
         * @param requestHandlers handlers to process HTTP request
         * @return an updated routing configuration
         */
        Rules anyOf(Iterable<Http.RequestMethod> methods, Handler... requestHandlers);

        /**
         * Routes requests with any specified method and corresponding path to provided handler(s). Request handler can call
         * {@link ServerRequest#next()} to continue processing on the next registered handler.
         *
         * @param methods         HTTP methods
         * @param pathPattern     a URI path pattern. See {@link PathMatcher} for pattern syntax reference.
         * @param requestHandlers handlers to process HTTP request
         * @return an updated routing configuration
         */
        Rules anyOf(Iterable<Http.RequestMethod> methods, String pathPattern, Handler... requestHandlers);

        /**
         * Routes requests with any specified method and corresponding path to provided handler(s). Request handler can call
         * {@link ServerRequest#next()} to continue processing on the next registered handler.
         *
         * @param methods         HTTP methods
         * @param pathMatcher     define path for registered router
         * @param requestHandlers handlers to process HTTP request
         * @return an updated routing configuration
         */
        Rules anyOf(Iterable<Http.RequestMethod> methods, PathMatcher pathMatcher, Handler... requestHandlers);

        /**
         * Registers callback on created new {@link WebServer} instance with this routing.
         *
         * @param webServerConsumer a WebServer creation callback
         * @return updated routing configuration
         */
        Rules onNewWebServer(Consumer<WebServer> webServerConsumer);
    }

    /**
     * A {@link Routing} builder.
     */
    class Builder implements Rules, io.helidon.common.Builder<Routing> {

        private final RouteListRoutingRules delegate = new RouteListRoutingRules();
        private final List<RequestRouting.ErrorHandlerRecord<?>> errorHandlerRecords = new ArrayList<>();
        private boolean tracingRegistered;

        /**
         * Creates new instance.
         */
        private Builder() {
        }

        // --------------- ROUTING API

        @Override
        public Builder register(WebTracingConfig webTracingConfig) {
            this.tracingRegistered = true;
            delegate.register(webTracingConfig);
            return this;
        }

        @Override
        public Builder register(Supplier<? extends Service>... serviceBuilders) {
            delegate.register(serviceBuilders);
            return this;
        }

        @Override
        public Builder register(Service... services) {
            delegate.register(services);
            return this;
        }

        @Override
        public Builder register(String pathPattern, Service... services) {
            delegate.register(pathPattern, services);
            return this;
        }

        @Override
        public Builder register(String pathPattern, Supplier<? extends Service>... serviceBuilders) {
            delegate.register(pathPattern, serviceBuilders);
            return this;
        }

        @Override
        public Builder get(Handler... requestHandlers) {
            delegate.get(requestHandlers);
            return this;
        }

        @Override
        public Builder get(String pathPattern, Handler... requestHandlers) {
            delegate.get(pathPattern, requestHandlers);
            return this;
        }

        @Override
        public Builder get(PathMatcher pathMatcher, Handler... requestHandlers) {
            delegate.get(pathMatcher, requestHandlers);
            return this;
        }

        @Override
        public Builder put(Handler... requestHandlers) {
            delegate.put(requestHandlers);
            return this;
        }

        @Override
        public Builder put(String pathPattern, Handler... requestHandlers) {
            delegate.put(pathPattern, requestHandlers);
            return this;
        }

        @Override
        public Builder put(PathMatcher pathMatcher, Handler... requestHandlers) {
            delegate.put(pathMatcher, requestHandlers);
            return this;
        }

        @Override
        public Builder post(Handler... requestHandlers) {
            delegate.post(requestHandlers);
            return this;
        }

        @Override
        public Builder post(String pathPattern, Handler... requestHandlers) {
            delegate.post(pathPattern, requestHandlers);
            return this;
        }

        @Override
        public Builder post(PathMatcher pathMatcher, Handler... requestHandlers) {
            delegate.post(pathMatcher, requestHandlers);
            return this;
        }

        @Override
        public Builder patch(Handler... requestHandlers) {
            delegate.patch(requestHandlers);
            return this;
        }

        @Override
        public Builder patch(String pathPattern, Handler... requestHandlers) {
            delegate.patch(pathPattern, requestHandlers);
            return this;
        }

        @Override
        public Builder patch(PathMatcher pathMatcher, Handler... requestHandlers) {
            delegate.patch(pathMatcher, requestHandlers);
            return this;
        }

        @Override
        public Builder delete(Handler... requestHandlers) {
            delegate.delete(requestHandlers);
            return this;
        }

        @Override
        public Builder delete(String pathPattern, Handler... requestHandlers) {
            delegate.delete(pathPattern, requestHandlers);
            return this;
        }

        @Override
        public Builder delete(PathMatcher pathMatcher,
                              Handler... requestHandlers) {
            delegate.delete(pathMatcher, requestHandlers);
            return this;
        }

        @Override
        public Builder options(Handler... requestHandlers) {
            delegate.options(requestHandlers);
            return this;
        }

        @Override
        public Builder options(String pathPattern, Handler... requestHandlers) {
            delegate.options(pathPattern, requestHandlers);
            return this;
        }

        @Override
        public Builder options(PathMatcher pathMatcher,
                               Handler... requestHandlers) {
            delegate.options(pathMatcher, requestHandlers);
            return this;
        }

        @Override
        public Builder head(Handler... requestHandlers) {
            delegate.head(requestHandlers);
            return this;
        }

        @Override
        public Builder head(String pathPattern, Handler... requestHandlers) {
            delegate.head(pathPattern, requestHandlers);
            return this;
        }

        @Override
        public Builder head(PathMatcher pathMatcher, Handler... requestHandlers) {
            delegate.head(pathMatcher, requestHandlers);
            return this;
        }

        @Override
        public Builder trace(Handler... requestHandlers) {
            delegate.trace(requestHandlers);
            return this;
        }

        @Override
        public Builder trace(String pathPattern, Handler... requestHandlers) {
            delegate.trace(pathPattern, requestHandlers);
            return this;
        }

        @Override
        public Builder trace(PathMatcher pathMatcher, Handler... requestHandlers) {
            delegate.trace(pathMatcher, requestHandlers);
            return this;
        }

        @Override
        public Builder any(Handler... requestHandlers) {
            delegate.any(requestHandlers);
            return this;
        }

        @Override
        public Builder any(String pathPattern, Handler... requestHandlers) {
            delegate.any(pathPattern, requestHandlers);
            return this;
        }

        @Override
        public Builder any(PathMatcher pathMatcher, Handler... requestHandlers) {
            delegate.any(pathMatcher, requestHandlers);
            return this;
        }

        @Override
        public Builder anyOf(Iterable<Http.RequestMethod> methods, Handler... requestHandlers) {
            delegate.anyOf(methods, requestHandlers);
            return this;
        }

        @Override
        public Builder anyOf(Iterable<Http.RequestMethod> methods, String pathPattern, Handler... requestHandlers) {
            delegate.anyOf(methods, pathPattern, requestHandlers);
            return this;
        }

        @Override
        public Builder anyOf(Iterable<Http.RequestMethod> methods,
                             PathMatcher pathMatcher,
                             Handler... requestHandlers) {
            delegate.anyOf(methods, pathMatcher, requestHandlers);
            return this;
        }

        @Override
        public Builder onNewWebServer(Consumer<WebServer> webServerConsumer) {
            delegate.onNewWebServer(webServerConsumer);
            return this;
        }
        // --------------- ERROR API

        /**
         * Registers an error handler that handles the given type of exceptions.
         *
         * @param exceptionClass the type of exception to handle by this handler
         * @param errorHandler   the error handler
         * @param <T>            an error handler type
         * @return an updated builder
         */
        public <T extends Throwable> Builder error(Class<T> exceptionClass, ErrorHandler<T> errorHandler) {
            if (errorHandler == null) {
                return this;
            }
            errorHandlerRecords.add(RequestRouting.ErrorHandlerRecord.of(exceptionClass, errorHandler));

            return this;
        }

        // --------------- BUILD API

        /**
         * Builds a new routing instance.
         *
         * @return a new instance
         */
        public Routing build() {
            if (!tracingRegistered) {
                register(WebTracingConfig.create());
            }
            RouteListRoutingRules.Aggregation aggregate = delegate.aggregate();
            return new RequestRouting(aggregate.routeList(), errorHandlerRecords, aggregate.newWebServerCallbacks());
        }

        /**
         * Creates new {@link WebServer} instance with provided configuration and this routing.
         *
         * @param configuration a web server configuration
         * @return new {@link WebServer} instance
         * @throws IllegalStateException if none SPI implementation found
         *
         * @deprecated since 2.0.0, please use {@link io.helidon.webserver.WebServer.Builder#build()} to create
         * a new server, configuring routing using {@link io.helidon.webserver.WebServer.Builder#routing(Routing)}.
         */
        @Deprecated
        public WebServer createServer(ServerConfiguration configuration) {
            return WebServer.create(configuration, this.build());
        }

        /**
         * Creates new {@link WebServer} instance with provided configuration and this routing.
         *
         * @param configurationBuilder a web server configuration builder
         * @return new {@link WebServer} instance
         * @throws IllegalStateException if none SPI implementation found
         *
         * @deprecated since 2.0.0, please use {@link io.helidon.webserver.WebServer.Builder#build()} to create
         * a new server, configuring routing using {@link io.helidon.webserver.WebServer.Builder#routing(Routing)}.
         */
        @Deprecated
        public WebServer createServer(ServerConfiguration.Builder configurationBuilder) {
            return WebServer.create(configurationBuilder.build(), this.build());
        }

        /**
         * Creates new {@link WebServer} instance with this routing and default configuration.
         *
         * @return new {@link WebServer} instance
         * @throws IllegalStateException if none SPI implementation found
         *
         * @deprecated since 2.0.0, please use {@link io.helidon.webserver.WebServer#create(Routing)}.
         */
        @Deprecated
        public WebServer createServer() {
            return WebServer.create(this.build());
        }
    }
}
