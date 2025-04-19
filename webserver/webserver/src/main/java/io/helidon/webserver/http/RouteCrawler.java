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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.helidon.common.parameters.Parameters;
import io.helidon.common.uri.UriPath;
import io.helidon.http.HttpPrologue;
import io.helidon.http.PathMatchers;
import io.helidon.http.RoutedPath;
import io.helidon.webserver.ConnectionContext;

class RouteCrawler {
    private final ConnectionContext ctx;
    private final RoutingRequest request;
    private final Iterator<HttpRouteBase> routeIterator;
    private final UriPath matchingPath;
    private final RoutedPath parent;
    private final HttpPrologue prologue;

    private CrawlerItem next;
    private RouteCrawler subCrawler;

    RouteCrawler(ConnectionContext ctx, RoutingRequest request, List<HttpRouteBase> rootRoute) {
        this.ctx = ctx;
        this.routeIterator = rootRoute.iterator();
        this.matchingPath = request.prologue().uriPath();
        this.prologue = request.prologue();
        this.request = request;
        this.parent = null;
    }

    RouteCrawler(ConnectionContext ctx,
                 RoutingRequest request,
                 List<HttpRouteBase> rootRoute,
                 RoutedPath parent,
                 UriPath child) {
        this.ctx = ctx;
        this.routeIterator = rootRoute.iterator();
        this.matchingPath = child;
        this.request = request;
        this.parent = parent;

        HttpPrologue prologue = request.prologue();
        this.prologue = HttpPrologue.create(prologue.rawProtocol(),
                                            prologue.protocol(),
                                            prologue.protocolVersion(),
                                            prologue.method(),
                                            child,
                                            prologue.query(),
                                            prologue.fragment());
    }

    boolean hasNext() {
        if (next != null) {
            return true;
        }

        // in case we are already processing a sub-crawler, try to use it
        if (subCrawler != null) {
            if (subCrawler.hasNext()) {
                next = subCrawler.next();

                if (parent != null) {
                    next = next.parent(parent);
                }
                return true;
            }
        }

        // otherwise, try to find the next valid route
        while (routeIterator.hasNext()) {
            HttpRouteBase nextRoute = routeIterator.next();
            if (nextRoute.isList()) {
                PathMatchers.PrefixMatchResult accepts = nextRoute.acceptsPrefix(prologue);
                if (accepts.accepted()) {
                    subCrawler = new RouteCrawler(ctx,
                                                  request,
                                                  nextRoute.routes(),
                                                  accepts.matchedPath(),
                                                  accepts.unmatchedPath());
                    if (subCrawler.hasNext()) {
                        next = subCrawler.next();

                        if (parent != null) {
                            next = next.parent(parent);
                        }
                        return true;
                    }
                    // no, this subcrawler was not the one
                    subCrawler = null;
                }
            } else {
                PathMatchers.MatchResult accepts = nextRoute.accepts(prologue, request.headers());
                if (accepts.accepted()) {
                    next = new CrawlerItem(accepts.path(), nextRoute.handler());

                    if (parent != null) {
                        next = next.parent(parent);
                    }

                    return true;
                }
            }
        }
        // did not find any matching route
        return false;
    }

    CrawlerItem next() {
        CrawlerItem result = next;
        next = null;
        return result;
    }

    record CrawlerItem(RoutedPath path, Handler handler) {
        public CrawlerItem parent(RoutedPath parent) {
            Map<String, List<String>> newParams = new HashMap<>();

            Parameters params = parent.pathParameters();
            for (String paramName : params.names()) {
                newParams.put(paramName, params.all(paramName));
            }
            params = path.pathParameters();
            for (String paramName : params.names()) {
                newParams.put(paramName, params.all(paramName));
            }
            newParams.replaceAll((name, values) -> List.copyOf(values));
            RoutedPath result = new CrawlerRoutedPath(path, Parameters.create("http/path", newParams));
            return new CrawlerItem(result, handler);
        }

        private static final class CrawlerRoutedPath implements RoutedPath {
            private final UriPath path;
            private final Parameters templateParams;

            private CrawlerRoutedPath(UriPath path,
                                      Parameters templateParams) {
                this.path = path;
                this.templateParams = templateParams;
            }

            @Override
            public String rawPath() {
                return path.rawPath();
            }

            @Override
            public String rawPathNoParams() {
                return path.rawPathNoParams();
            }

            @Override
            public String path() {
                return path.path();
            }

            @Override
            public Parameters matrixParameters() {
                return path.matrixParameters();
            }

            @Override
            public void validate() {
                path.validate();
            }

            @Override
            public Parameters pathParameters() {
                return templateParams;
            }

            @Override
            public RoutedPath absolute() {
                return new CrawlerRoutedPath(path.absolute(), templateParams);
            }
        }
    }
}
