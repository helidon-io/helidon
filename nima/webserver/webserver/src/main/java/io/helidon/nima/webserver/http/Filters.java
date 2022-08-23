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

import java.util.Iterator;
import java.util.List;

import io.helidon.common.parameters.Parameters;
import io.helidon.common.uri.UriPath;

/**
 * Handler of HTTP filters.
 */
public class Filters {
    private final List<Filter> filters;
    private final boolean noFilters;

    private Filters(List<Filter> filters) {
        this.filters = filters;
        this.noFilters = filters.isEmpty();
    }

    /**
     * Create filters.
     *
     * @param filters list of filters to use
     * @return filters
     */
    public static Filters create(List<Filter> filters) {
        return new Filters(filters);
    }

    /**
     * Filter request.
     *
     * @param request         request
     * @param response        response
     * @param routingExecutor this handler is called after all filters finish processing
     *                        (unless a filter does not invoke the chain)
     */
    public void filter(RoutingRequest request, RoutingResponse response, Runnable routingExecutor) {
        if (noFilters) {
            routingExecutor.run();
            return;
        }

        FilterChain chain = new FilterChainImpl(filters, request, response, routingExecutor);
        request.path(new FilterRoutedPath(request.prologue().uriPath()));
        chain.proceed();
    }

    private static final class FilterChainImpl implements FilterChain {
        private final Iterator<Filter> filters;
        private final Runnable routingExecutor;
        private RoutingRequest request;
        private RoutingResponse response;

        private FilterChainImpl(List<Filter> filters,
                                RoutingRequest request,
                                RoutingResponse response,
                                Runnable routingExecutor) {
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
                routingExecutor.run();
                if (!response.isSent()) {
                    throw HttpException.builder()
                            .request(request)
                            .response(response)
                            .type(SimpleHandler.EventType.INTERNAL_ERROR)
                            .message("Routing finished but response was not sent. Níma does not support asynchronous responses. "
                                             + "Please block until a response is sent.")
                            .build();
                }
            }
        }
    }

    private static final class FilterRoutedPath implements RoutedPath {
        private static final Parameters EMPTY_PARAMS = Parameters.empty("filter-path-template");

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
