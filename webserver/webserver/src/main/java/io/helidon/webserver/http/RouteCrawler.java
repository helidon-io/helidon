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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.helidon.common.parameters.Parameters;
import io.helidon.common.uri.UriPath;
import io.helidon.http.HttpPrologue;
import io.helidon.http.PathMatcher;
import io.helidon.http.PathMatchers;
import io.helidon.http.RoutedPath;
import io.helidon.webserver.ConnectionContext;

class RouteCrawler {
    private final ConnectionContext ctx;
    private final RoutingRequest request;
    private final Iterator<HttpRouteBase> routeIterator;
    private final UriPath matchingPath;
    private final RoutedPath parent;
    private final String parentPattern;
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
        this.parentPattern = null;
    }

    RouteCrawler(ConnectionContext ctx,
                 RoutingRequest request,
                 List<HttpRouteBase> rootRoute,
                 RoutedPath parent,
                 UriPath child) {
        this(ctx, request, rootRoute, parent, null, child);
    }

    RouteCrawler(ConnectionContext ctx,
                 RoutingRequest request,
                 List<HttpRouteBase> rootRoute,
                 RoutedPath parent,
                 String parentPattern,
                 UriPath child) {
        this.ctx = ctx;
        this.routeIterator = rootRoute.iterator();
        this.matchingPath = child;
        this.request = request;
        this.parent = parent;
        this.parentPattern = parentPattern;

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
                    next = parentPattern == null ? next.parent(parent) : next.parent(parent, parentPattern);
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
                    boolean requestAwareRoutes = nextRoute.requestAwareRoutes();
                    RoutedPath previousPath = requestAwareRoutes ? request.path() : null;
                    if (requestAwareRoutes || parentPattern != null) {
                        String matchedPattern = matchingElement(nextRoute, accepts);
                        List<HttpRouteBase> routes;
                        if (requestAwareRoutes) {
                            RoutedPath matchedPath = parent == null
                                    ? accepts.matchedPath()
                                    : merge(parent, accepts.matchedPath());
                            String fullMatchedPattern = parentPattern == null
                                    ? matchedPattern
                                    : parentPattern + matchedPattern;
                            routes = nextRoute.routes(ctx, request, matchedPath, fullMatchedPattern);
                        } else {
                            routes = nextRoute.routes();
                        }
                        subCrawler = new RouteCrawler(ctx,
                                                      request,
                                                      routes,
                                                      accepts.matchedPath(),
                                                      matchedPattern,
                                                      accepts.unmatchedPath());
                    } else {
                        subCrawler = new RouteCrawler(ctx,
                                                      request,
                                                      nextRoute.routes(),
                                                      accepts.matchedPath(),
                                                      accepts.unmatchedPath());
                    }
                    if (subCrawler.hasNext()) {
                        next = subCrawler.next();

                        if (parent != null) {
                            next = parentPattern == null ? next.parent(parent) : next.parent(parent, parentPattern);
                        }
                        return true;
                    }
                    // no, this subcrawler was not the one
                    subCrawler = null;
                    if (requestAwareRoutes) {
                        nextRoute.afterNoMatch(request, previousPath);
                    }
                }
            } else {
                PathMatchers.MatchResult accepts = nextRoute.accepts(prologue, request.headers());
                if (accepts.accepted()) {
                    PathMatcher pathMatcher = nextRoute.pathMatcher().orElse(null);
                    next = new CrawlerItem(accepts.path(),
                                           pathMatcher != null ? pathMatcher.matchingElement().orElse("") : "",
                                           nextRoute.handler());

                    if (parent != null) {
                        next = parentPattern == null ? next.parent(parent) : next.parent(parent, parentPattern);
                    }

                    return true;
                }
            }
        }
        // did not find any matching route
        return false;
    }

    private String matchingElement(HttpRouteBase route, PathMatchers.PrefixMatchResult accepts) {
        return route.pathMatcher()
                .flatMap(PathMatcher::matchingElement)
                .orElseGet(() -> accepts.matchedPath().path());
    }

    private static RoutedPath merge(RoutedPath parent, RoutedPath path) {
        return CrawlerItem.merge(parent, path);
    }

    CrawlerItem next() {
        CrawlerItem result = next;
        next = null;
        return result;
    }

    record CrawlerItem(RoutedPath path, String matchingElement, Handler handler) {
        public CrawlerItem parent(RoutedPath parent) {
            RoutedPath result = merge(parent, path);
            return new CrawlerItem(result, parent.path() + matchingElement, handler);
        }

        public CrawlerItem parent(RoutedPath parent, String parentPattern) {
            RoutedPath result = merge(parent, path);
            return new CrawlerItem(result, parentPattern + matchingElement, handler);
        }

        private static RoutedPath merge(RoutedPath parent, RoutedPath path) {
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
            // this is called for each request, optimize qualifiers so the do not get parsed each time
            return new CrawlerRoutedPath(path,
                                         Parameters.create("http/path", newParams, "http", "path"));
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
