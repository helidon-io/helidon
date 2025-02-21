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

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;

import io.helidon.common.parameters.Parameters;
import io.helidon.common.uri.UriPath;
import io.helidon.http.HttpException;
import io.helidon.http.RoutedPath;
import io.helidon.http.Status;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ServerLifecycle;
import io.helidon.webserver.WebServer;

/**
 * Handler of HTTP filters.
 */
public final class Filters implements ServerLifecycle {
    private final ErrorHandlers errorHandlers;
    private final List<Filter> filters;
    private final boolean noFilters;

    private Filters(ErrorHandlers errorHandlers, List<Filter> filters) {
        this.errorHandlers = errorHandlers;
        this.filters = filters;
        this.noFilters = filters.isEmpty();
    }

    /**
     * Create filters.
     *
     * @param errorHandlers error handlers to handle thrown exceptions
     * @param filters       list of filters to use
     * @return filters
     */
    public static Filters create(ErrorHandlers errorHandlers, List<Filter> filters) {
        return new Filters(errorHandlers, filters);
    }

    @Override
    public void beforeStart() {
        filters.forEach(Filter::beforeStart);
    }

    @Override
    public void afterStart(WebServer webServer) {
        filters.forEach(f -> f.afterStart(webServer));
    }

    @Override
    public void afterStop() {
        filters.forEach(Filter::afterStop);
    }

    /**
     * Filter request.
     *
     * @param ctx             connection context
     * @param request         request
     * @param response        response
     * @param routingExecutor this handler is called after all filters finish processing
     *                        (unless a filter does not invoke the chain)
     */
    public void filter(ConnectionContext ctx, RoutingRequest request, RoutingResponse response, Callable<Void> routingExecutor) {
        if (noFilters) {
            errorHandlers.runWithErrorHandling(ctx, request, response, routingExecutor);
            return;
        }

        FilterChain chain = new FilterChainImpl(ctx, errorHandlers, filters, request, response, routingExecutor);
        request.path(new FilterRoutedPath(request.prologue().uriPath()));
        errorHandlers.runWithErrorHandling(ctx, request, response, () -> executeFilters(chain));
    }

    private Void executeFilters(FilterChain chain) {
        chain.proceed();
        return null;
    }

    private static final class FilterChainImpl implements FilterChain {
        private final ConnectionContext ctx;
        private final ErrorHandlers errorHandlers;
        private final Iterator<Filter> filters;
        private final Callable<Void> routingExecutor;
        private final RoutingRequest request;
        private final RoutingResponse response;

        private FilterChainImpl(ConnectionContext ctx,
                                ErrorHandlers errorHandlers,
                                List<Filter> filters,
                                RoutingRequest request,
                                RoutingResponse response,
                                Callable<Void> routingExecutor) {
            this.ctx = ctx;
            this.errorHandlers = errorHandlers;
            this.filters = filters.iterator();
            this.request = request;
            this.response = response;
            this.routingExecutor = routingExecutor;
        }

        @Override
        public void proceed() {
            if (response.hasEntity()) {
                return;
            }
            if (filters.hasNext()) {
                filters.next().filter(this, request, response);
            } else {
                errorHandlers.runWithErrorHandling(ctx, request, response, routingExecutor);
                if (!response.isSent()) {
                    // intentionally not using InternalServerException, as we do not have a cause
                    throw new HttpException("Routing finished but response was not sent. Helidon WebServer does not support "
                                                    + "asynchronous responses. Please block until a response can be sent.",
                                            Status.INTERNAL_SERVER_ERROR_500);
                }
            }
        }
    }

    private static final class FilterRoutedPath implements RoutedPath {
        private static final Parameters EMPTY_PARAMS = Parameters.empty("http/path");

        private final UriPath uriPath;

        FilterRoutedPath(UriPath uriPath) {
            this.uriPath = uriPath;
        }

        @Override
        public String rawPath() {
            return uriPath.rawPath();
        }

        @Override
        public String rawPathNoParams() {
            return uriPath.rawPathNoParams();
        }

        @Override
        public String path() {
            return uriPath.path();
        }

        @Override
        public Parameters matrixParameters() {
            return uriPath.matrixParameters();
        }

        @Override
        public void validate() {
            uriPath.validate();
        }

        @Override
        public Parameters pathParameters() {
            return EMPTY_PARAMS;
        }

        @Override
        public RoutedPath absolute() {
            return new FilterRoutedPath(uriPath.absolute());
        }
    }
}
