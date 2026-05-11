/*
 * Copyright (c) 2017, 2026 Oracle and/or its affiliates.
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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.LazyValue;
import io.helidon.common.context.Contexts;
import io.helidon.common.http.AlreadyCompletedException;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.config.SpanTracingConfig;
import io.helidon.tracing.config.TracingConfigUtil;
import io.helidon.webserver.spi.ProtocolUpgradeHandler;

/**
 * Default implementation of {@link Routing}.
 */
class RequestRouting implements Routing {

    private static final Logger LOGGER = Logger.getLogger(RequestRouting.class.getName());

    private final RouteList routes;
    private final List<ErrorHandlerRecord<?>> errorHandlers;
    private final List<Consumer<WebServer>> newWebServerCallbacks;

    /**
     * Creates new instance.
     *
     * @param routes                effective route
     * @param errorHandlers         a list of error handlers
     * @param newWebServerCallbacks a list of callback handlers for registration in new {@link WebServer}. It is copied.
     */
    RequestRouting(RouteList routes, List<ErrorHandlerRecord<?>> errorHandlers, List<Consumer<WebServer>> newWebServerCallbacks) {
        this.routes = routes;
        this.errorHandlers = errorHandlers;
        this.newWebServerCallbacks = new ArrayList<>(newWebServerCallbacks);
    }

    @Override
    public void route(BareRequest bareRequest, BareResponse bareResponse) {
        route(bareRequest, bareResponse, false, null);
    }

    void routeProtocolUpgrade(BareRequest bareRequest,
                              BareResponse bareResponse,
                              Consumer<ServerResponse> terminalHandler) {
        route(bareRequest, bareResponse, true, terminalHandler);
    }

    private void route(BareRequest bareRequest,
                       BareResponse bareResponse,
                       boolean protocolUpgrade,
                       Consumer<ServerResponse> terminalHandler) {

        try {
            WebServer webServer = bareRequest.webServer();
            HashRequestHeaders requestHeaders = new HashRequestHeaders(bareRequest.headers());
            RoutedResponse response = new RoutedResponse(
                    webServer,
                    bareResponse,
                    requestHeaders.acceptedTypes());

            URI uri = bareRequest.uri();
            String path;
            String rawPath;
            String routingPath = uri.getRawPath();
            boolean decodedNormalization = false;
            if (uri.getScheme() == null && uri.getRawAuthority() != null) {
                routingPath = uri.getRawSchemeSpecificPart();
                int queryIndex = routingPath.indexOf('?');
                if (queryIndex >= 0) {
                    routingPath = routingPath.substring(0, queryIndex);
                }
                int fragmentIndex = routingPath.indexOf('#');
                if (fragmentIndex >= 0) {
                    routingPath = routingPath.substring(0, fragmentIndex);
                }
                decodedNormalization = true;
            }
            if (routingPath == null) {
                routingPath = "";
            }
            String pathWithSingleLeadingSlash = collapseLeadingSlashes(routingPath);
            if (pathWithSingleLeadingSlash.length() != routingPath.length()) {
                routingPath = pathWithSingleLeadingSlash;
                decodedNormalization = true;
            }
            boolean rawPathParams = PathHelper.hasRawPathParams(routingPath);
            int percentIndex = routingPath.indexOf('%');
            while (!decodedNormalization && !rawPathParams && percentIndex >= 0 && percentIndex + 2 < routingPath.length()) {
                char first = routingPath.charAt(percentIndex + 1);
                char second = routingPath.charAt(percentIndex + 2);
                decodedNormalization = first == '2'
                        && (second == 'e' || second == 'E' || second == 'f' || second == 'F');
                percentIndex = routingPath.indexOf('%', percentIndex + 1);
            }
            if (decodedNormalization) {
                URI routingUri = URI.create(routingPath);
                path = rawPathParams
                        ? canonicalize(collapseLeadingSlashes(routingUri.normalize().getPath()))
                        : normalizeDecodedPath(routingUri.getPath());
                rawPath = canonicalize(collapseLeadingSlashes(routingUri.normalize().getRawPath()));
            } else {
                URI normalizedUri = uri.normalize();
                path = canonicalize(collapseLeadingSlashes(normalizedUri.getPath()));
                rawPath = canonicalize(collapseLeadingSlashes(normalizedUri.getRawPath()));
            }

            Crawler crawler = new Crawler(routes, path, rawPath, bareRequest.method(), bareRequest.version());
            RoutedRequest nextRequests = new RoutedRequest(bareRequest, response, webServer, crawler, errorHandlers,
                                                           requestHeaders, protocolUpgrade, terminalHandler);
            response.request(nextRequests);
            nextRequests.next();
        } catch (Error | RuntimeException e) {
            LOGGER.log(Level.FINE, "Unexpected error occurred during routing!", e);
            throw e;
        }
    }

    private static String canonicalize(String p) {
        String result;
        if (p == null || p.isEmpty() || p.equals("/")) {
            result = "/";
        } else {
            int lastCharIndex = p.length() - 1;
            if (p.charAt(lastCharIndex) == '/') {
                result = p.substring(0, lastCharIndex);
            } else {
                result = p;
            }
        }
        return result;
    }

    private static String normalizeDecodedPath(String path) {
        try {
            String normalizedPath = new URI(null, null, collapseLeadingSlashes(path), null).normalize().getPath();
            while (normalizedPath.equals("/..") || normalizedPath.startsWith("/../")) {
                normalizedPath = normalizedPath.length() == 3 ? "/" : normalizedPath.substring(3);
            }
            return canonicalize(normalizedPath);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid request path", e);
        }
    }

    private static String collapseLeadingSlashes(String path) {
        if (path == null || !path.startsWith("//")) {
            return path;
        }
        int firstNonSlash = 2;
        while (firstNonSlash < path.length() && path.charAt(firstNonSlash) == '/') {
            firstNonSlash++;
        }
        return "/" + path.substring(firstNonSlash);
    }

    private static String remainingPart(String path,
                                        String pathWithoutParams,
                                        String remainingPartWithoutParams,
                                        boolean encodedSemicolon) {
        int matchedLength = "/".equals(remainingPartWithoutParams)
                ? pathWithoutParams.length()
                : pathWithoutParams.length() - remainingPartWithoutParams.length();
        int pathIndex = 0;
        int pathWithoutParamsIndex = 0;
        boolean pathParam = false;
        while (pathIndex < path.length() && pathWithoutParamsIndex < matchedLength) {
            char ch = path.charAt(pathIndex);
            switch (ch) {
            case ';':
                pathParam = true;
                break;
            case '%':
                if (encodedSemicolon && PathHelper.isEncodedSemicolon(path, pathIndex)) {
                    pathParam = true;
                    pathIndex += 2;
                } else if (!pathParam) {
                    pathWithoutParamsIndex++;
                }
                break;
            case '/':
                pathParam = false;
                pathWithoutParamsIndex++;
                break;
            default:
                if (!pathParam) {
                    pathWithoutParamsIndex++;
                }
                break;
            }
            pathIndex++;
        }
        while (pathIndex < path.length() && path.charAt(pathIndex) != '/') {
            pathIndex++;
        }
        return pathIndex == path.length() ? "/" : path.substring(pathIndex);
    }

    /**
     * Fire event, that new {@link WebServer} is created.
     *
     * @param webServer created web server
     */
    void fireNewWebServer(WebServer webServer) {
        for (Consumer<WebServer> callback : newWebServerCallbacks) {
            callback.accept(webServer);
        }
    }

    /**
     * A deep 'iterator' without a {@code hasNext()} method for a tree of {@link Route routes} based on the routing criteria.
     */
    private static class Crawler {

        private final List<Route> routes;
        private final Request.Path contextPath;
        private final String path;
        private final String rawPath;
        private final boolean pathParams;
        private final boolean rawPathParams;
        private final Http.RequestMethod method;
        private final Http.Version version;

        private volatile int index = -1;
        private volatile Crawler subCrawler;
        private String pathWithoutParams;
        private String pathWithoutParamsBeforeNormalization;
        private String rawPathWithoutParams;
        private String decodedRawPathWithoutParams;
        private Boolean pathParamsFallbackAllowed;
        private boolean pathWithoutParamsNormalized;

        /**
         * Creates new instance.
         *
         * @param routes      routs to crawl throw.
         * @param contextPath a path representing URI path context.
         * @param path        an URI path to route.
         * @param rawPath     not decoded URI path to route.
         * @param method      an HTTP method to route.
         * @param version     HTTP protocol version
         */
        private Crawler(List<Route> routes, Request.Path contextPath, String path, String rawPath,
                        Http.RequestMethod method, Http.Version version) {
            this.routes = routes;
            this.path = path;
            this.rawPath = rawPath;
            this.pathParams = path.indexOf(';') >= 0;
            this.rawPathParams = PathHelper.hasRawPathParams(rawPath);
            this.contextPath = contextPath;
            this.method = method;
            this.version = version;
        }

        /**
         * Creates new instance of 'the root crawler'.
         *
         * @param routes routes to crawl.
         * @param path   a URI path to route.
         * @param rawPath not decoded URI path to route.
         * @param method an HTTP method to route.
         * @param version HTTP protocol version
         */
        Crawler(List<Route> routes, String path, String rawPath, Http.RequestMethod method, Http.Version version) {
            this(routes, null, path, rawPath, method, version);
        }

        /**
         * Returns next {@link HandlerRoute} to execute or {@code null} if there are no more acceptable routes.
         * It is not synchronized.
         *
         * @return a next item.
         */
        public Item next() {
            return next(false);
        }

        public Item next(boolean protocolUpgrade) {
            while ((subCrawler != null) || (++index < routes.size())) {
                if (subCrawler != null) {
                    Item result = subCrawler.next(protocolUpgrade);
                    if (result != null) {
                        return result;
                    } else {
                        subCrawler = null;
                    }
                } else {
                    Route route = routes.get(index);
                    if (route.accepts(method)) {
                        if (route instanceof HandlerRoute hr) {
                            PathMatcher.Result match = hr.match(path);
                            if (match.matches() && (pathParams || rawPathParams)) {
                                boolean checkedFallback = false;
                                boolean matchedFallback = false;
                                if (rawPathParams) {
                                    String rawPathWithoutParams = rawPathWithoutParams();
                                    if (!rawPathWithoutParams.equals(rawPath)) {
                                        checkedFallback = true;
                                        matchedFallback = hr.match(decodedRawPathWithoutParams(rawPathWithoutParams))
                                                .matches();
                                    }
                                }
                                if (!matchedFallback && pathParamsFallbackAllowed()) {
                                    String pathWithoutParams = pathWithoutParams();
                                    if (!pathWithoutParams.equals(path)) {
                                        checkedFallback = true;
                                        matchedFallback = hr.match(pathWithoutParams).matches();
                                    }
                                }
                                if (checkedFallback && !matchedFallback) {
                                    match = PathPattern.NOT_MATCHED_RESULT;
                                }
                            }
                            if (!match.matches() && rawPathParams) {
                                String rawPathWithoutParams = rawPathWithoutParams();
                                if (!rawPathWithoutParams.equals(rawPath)) {
                                    match = hr.match(decodedRawPathWithoutParams(rawPathWithoutParams));
                                }
                            }
                            if (!match.matches() && pathParamsFallbackAllowed()) {
                                match = hr.match(pathWithoutParams());
                            }
                            if (match.matches() && hr.matchVersion(version)) {
                                if (protocolUpgrade && !(hr.handler() instanceof ProtocolUpgradeHandler)) {
                                    continue;
                                }
                                return new Item(hr, Request.Path.create(contextPath, path, rawPath, match.params()));
                            }
                        } else if (route instanceof RouteList rl && routeListMatched(rl)) {
                            // do "continue" in order to not log the failure message bellow
                            continue;
                        }

                        LOGGER.finest(() -> "Route candidate '" + route + "' doesn't match path: " + path);
                    } else {
                        LOGGER.finest(() -> "Route candidate '" + route + "' doesn't match method: " + method);
                    }
                }
            }
            return null;
        }

        private boolean routeListMatched(RouteList rl) {
            PathMatcher.PrefixResult prefixMatch = rl.prefixMatch(path);
            String remainingPart = prefixMatch.remainingPart();
            String rawPathToMatch = rawPath;
            String rawPathWithoutParams = null;
            String rawRemainingPartFromFallback = null;
            if (prefixMatch.matches() && (pathParams || rawPathParams)) {
                boolean checkedFallback = false;
                boolean matchedFallback = false;
                if (rawPathParams) {
                    rawPathWithoutParams = rawPathWithoutParams();
                    if (!rawPathWithoutParams.equals(rawPath)) {
                        checkedFallback = true;
                        String decodedRawPathWithoutParams = decodedRawPathWithoutParams(rawPathWithoutParams);
                        matchedFallback = rl.prefixMatch(decodedRawPathWithoutParams).matches();
                    }
                }
                if (!matchedFallback && pathParamsFallbackAllowed()) {
                    String pathWithoutParams = pathWithoutParams();
                    if (!pathWithoutParams.equals(path)) {
                        checkedFallback = true;
                        matchedFallback = rl.prefixMatch(pathWithoutParams).matches();
                    }
                }
                if (checkedFallback && !matchedFallback) {
                    prefixMatch = PathPattern.NOT_MATCHED_RESULT;
                    remainingPart = prefixMatch.remainingPart();
                }
            }
            if (!prefixMatch.matches() && rawPathParams) {
                rawPathWithoutParams = rawPathWithoutParams();
                if (!rawPathWithoutParams.equals(rawPath)) {
                    String decodedRawPathWithoutParams = decodedRawPathWithoutParams(rawPathWithoutParams);
                    prefixMatch = rl.prefixMatch(decodedRawPathWithoutParams);
                    if (prefixMatch.matches()) {
                        PathMatcher.PrefixResult rawStrippedPrefixMatch = rl.prefixMatch(rawPathWithoutParams);
                        String remainingPartWithoutParams = rawStrippedPrefixMatch.matches()
                                ? rawStrippedPrefixMatch.remainingPart()
                                : prefixMatch.remainingPart();
                        if (!rawStrippedPrefixMatch.matches() && !"/".equals(remainingPartWithoutParams)) {
                            int rawSuffixIndex = rawPathWithoutParams.indexOf('/');
                            boolean found = false;
                            while (rawSuffixIndex >= 0) {
                                String rawSuffix = rawPathWithoutParams.substring(rawSuffixIndex);
                                if (remainingPartWithoutParams.equals(URI.create(rawSuffix).getPath())) {
                                    remainingPartWithoutParams = rawSuffix;
                                    found = true;
                                    break;
                                }
                                rawSuffixIndex = rawPathWithoutParams.indexOf('/', rawSuffixIndex + 1);
                            }
                            if (!found) {
                                remainingPart = prefixMatch.remainingPart();
                                rawRemainingPartFromFallback = remainingPart;
                            }
                        }
                        if (rawRemainingPartFromFallback == null) {
                            rawRemainingPartFromFallback = remainingPart(rawPath, rawPathWithoutParams,
                                                                         remainingPartWithoutParams, true);
                            remainingPart = URI.create(rawRemainingPartFromFallback).getPath();
                        }
                    }
                }
            }
            if (!prefixMatch.matches() && pathParamsFallbackAllowed()) {
                String pathWithoutParams = pathWithoutParams();
                prefixMatch = rl.prefixMatch(pathWithoutParams);
                if (prefixMatch.matches()) {
                    remainingPart = pathWithoutParamsNormalized
                            ? remainingPart(path, pathWithoutParamsBeforeNormalization, prefixMatch.remainingPart(), false)
                            : remainingPart(path, pathWithoutParams, prefixMatch.remainingPart(), false);
                }
            }
            if (prefixMatch.matches()) {
                PathMatcher.PrefixResult rawPrefixMatch = rl.prefixMatch(rawPathToMatch);
                boolean rawPrefixMatchedWithoutParams = false;
                if (!rawPrefixMatch.matches() && rawPathParams) {
                    rawPathWithoutParams = rawPathWithoutParams();
                    rawPathToMatch = rawPathWithoutParams;
                    rawPrefixMatch = rl.prefixMatch(rawPathToMatch);
                    rawPrefixMatchedWithoutParams = rawPrefixMatch.matches();
                }
                String rawRemainingPart = rawRemainingPartFromFallback;
                if (rawRemainingPart == null) {
                    rawRemainingPart = rawPrefixMatch.matches()
                            ? rawPrefixMatchedWithoutParams
                                    ? remainingPart(rawPath, rawPathWithoutParams, rawPrefixMatch.remainingPart(), true)
                                    : rawPrefixMatch.remainingPart()
                            : rawPathWithoutParams == null
                                    ? remainingPart
                                    : remainingPart(rawPath,
                                                    rawPathWithoutParams,
                                                    PathHelper.extractRawPathParams(remainingPart),
                                                    true);
                }
                subCrawler = new Crawler(rl,
                                         Request.Path.create(contextPath, path, rawPath, prefixMatch.params()),
                                         remainingPart,
                                         rawRemainingPart,
                                         method,
                                         version);
                return true;
            }
            return false;
        }

        private boolean pathParamsFallbackAllowed() {
            if (!pathParams) {
                return false;
            }
            if (!rawPathParams) {
                return true;
            }
            Boolean allowed = pathParamsFallbackAllowed;
            if (allowed == null) {
                int segmentStart = rawPath.startsWith("/") ? 1 : 0;
                allowed = false;
                while (segmentStart < rawPath.length()) {
                    int segmentEnd = rawPath.indexOf('/', segmentStart);
                    if (segmentEnd < 0) {
                        segmentEnd = rawPath.length();
                    }
                    int paramStart = segmentEnd;
                    int semicolon = rawPath.indexOf(';', segmentStart);
                    if (semicolon >= 0 && semicolon < paramStart) {
                        paramStart = semicolon;
                    }
                    int percentIndex = rawPath.indexOf('%', segmentStart);
                    while (percentIndex >= 0 && percentIndex < paramStart) {
                        if (PathHelper.isEncodedSemicolon(rawPath, percentIndex)) {
                            paramStart = percentIndex;
                            break;
                        }
                        percentIndex = rawPath.indexOf('%', percentIndex + 1);
                    }
                    if (paramStart < segmentEnd) {
                        String decodedSegmentName = URI.create("/" + rawPath.substring(segmentStart, paramStart))
                                .getPath()
                                .substring(1);
                        if (".".equals(decodedSegmentName) || "..".equals(decodedSegmentName)) {
                            allowed = true;
                            break;
                        }
                    }
                    segmentStart = segmentEnd + 1;
                }
                pathParamsFallbackAllowed = allowed;
            }
            return allowed;
        }

        private String pathWithoutParams() {
            String pathWithoutParams = this.pathWithoutParams;
            if (pathWithoutParams == null) {
                String extractedPathWithoutParams = PathHelper.extractPathParams(path);
                pathWithoutParamsBeforeNormalization = extractedPathWithoutParams;
                pathWithoutParams = normalizeDecodedPath(extractedPathWithoutParams);
                pathWithoutParamsNormalized = !pathWithoutParams.equals(extractedPathWithoutParams);
                this.pathWithoutParams = pathWithoutParams;
            }
            return pathWithoutParams;
        }

        private String rawPathWithoutParams() {
            String rawPathWithoutParams = this.rawPathWithoutParams;
            if (rawPathWithoutParams == null) {
                rawPathWithoutParams = PathHelper.extractRawPathParams(rawPath);
                this.rawPathWithoutParams = rawPathWithoutParams;
            }
            return rawPathWithoutParams;
        }

        private String decodedRawPathWithoutParams(String rawPathWithoutParams) {
            String decodedRawPathWithoutParams = this.decodedRawPathWithoutParams;
            if (decodedRawPathWithoutParams == null) {
                decodedRawPathWithoutParams = normalizeDecodedPath(URI.create(rawPathWithoutParams).getPath());
                this.decodedRawPathWithoutParams = decodedRawPathWithoutParams;
            }
            return decodedRawPathWithoutParams;
        }

        /**
         * Represents single accepted {@link HandlerRoute} with resolved {@code path parameters}.
         */
        private static class Item {

            private final HandlerRoute handlerRoute;
            private final Request.Path path;

            Item(HandlerRoute handlerRoute, Request.Path path) {
                this.handlerRoute = handlerRoute;
                this.path = path;
            }

        }
    }

    private static class RoutedRequest extends Request {

        private final Crawler crawler;
        private final LinkedList<ErrorHandlerRecord<? extends Throwable>> errorHandlers;
        private final Path path;
        private final RoutedResponse response;
        private final boolean protocolUpgrade;
        private final Consumer<ServerResponse> terminalHandler;

        private final AtomicBoolean nexted = new AtomicBoolean(false);
        private final LazyValue<URI> lazyAbsoluteUri = LazyValue.create(super::absoluteUri);

        /**
         * Creates new instance.
         *
         * @param req           bare request from HTTP SPI implementation
         * @param response      a response implementation
         * @param webServer     the relevant server
         * @param crawler       a crawler to use for {@code next} method implementation
         * @param errorHandlers a list of error handlers
         */
        RoutedRequest(BareRequest req,
                      RoutedResponse response,
                      WebServer webServer,
                      Crawler crawler,
                      List<ErrorHandlerRecord<?>> errorHandlers,
                      HashRequestHeaders headers,
                      boolean protocolUpgrade,
                      Consumer<ServerResponse> terminalHandler) {
            super(req, webServer, headers);
            this.crawler = crawler;
            this.errorHandlers = new LinkedList<>(errorHandlers);
            this.path = null;
            this.response = response;
            this.protocolUpgrade = protocolUpgrade;
            this.terminalHandler = terminalHandler;
        }

        /**
         * Creates clone of existing instance.
         *
         * @param request       a request to clone
         * @param response      a response implementation
         * @param path          a matched path
         * @param errorHandlers a list of error handlers
         */
        RoutedRequest(RoutedRequest request,
                      RoutedResponse response,
                      Path path,
                      List<ErrorHandlerRecord<?>> errorHandlers) {
            super(request);
            this.crawler = request.crawler;
            this.response = response;
            this.path = path;
            this.errorHandlers = new LinkedList<>(errorHandlers);
            this.protocolUpgrade = request.protocolUpgrade;
            this.terminalHandler = request.terminalHandler;
        }

        Span span() {
            return context().get(ServerRequest.class, Span.class).orElse(null);
        }

        @Override
        public Optional<SpanContext> spanContext() {
            return context().get(ServerRequest.class, SpanContext.class);
        }

        /**
         * Returns {@code true} if this request was nexted - call any {@code next(...)} method.
         *
         * @return {@code true} if this request was nexted
         */
        boolean nexted() {
            return nexted.get();
        }

        @Override
        public void next() {
            checkNexted();
            Crawler.Item nextItem = crawler.next(protocolUpgrade);
            if (nextItem == null) {
                if (terminalHandler == null) {
                    // 404 error
                    nextNoCheck(new NotFoundException("No handler found for path: "
                            + HtmlEncoder.encode(path().toString())));
                } else {
                    terminalHandler.accept(response);
                }
            } else {
                try {
                    RoutedResponse nextResponse = new RoutedResponse(response);
                    RoutedRequest nextRequest = new RoutedRequest(this, nextResponse, nextItem.path, errorHandlers);
                    LOGGER.finest(() -> "(reqID: " + requestId() + ") Routing next: " + nextItem.path);
                    Span span = span();
                    if (null != span) {
                        SpanTracingConfig spanConfig = TracingConfigUtil.spanConfig("web-server",
                                                                                    "HTTP Request",
                                                                                    context());
                        if (spanConfig.spanLog("handler.class").enabled()) {
                            span.addEvent("handler", nextItem.handlerRoute.diagnosticEvent());
                        }
                    }

                    nextItem.handlerRoute
                            .handler()
                            .accept(nextRequest, nextResponse);
                } catch (RuntimeException re) {
                    nextNoCheck(re);
                }
            }
        }

        private void checkNexted() {
            checkNexted(null);
        }

        private void checkNexted(Throwable t) {
            if (!nexted.compareAndSet(false, true)) {
                throw new IllegalStateException("The 'next()' method can be called only once!", t);
            }
        }

        @SuppressWarnings("unchecked")
        private void nextNoCheck(Throwable t) {
            if (t instanceof CompletionException) {
                // completion exception always has a cause
                nextNoCheck(t.getCause());
                return;
            }

            LOGGER.finest(() -> "(reqID: " + requestId() + ") Routing error: " + t.getClass());

            for (ErrorHandlerRecord<?> record = errorHandlers.pollFirst(); record != null; record = errorHandlers.pollFirst()) {
                if (record.exceptionClass.isAssignableFrom(t.getClass())) {
                    ErrorRoutedRequest nextErrorRequest = new ErrorRoutedRequest(errorHandlers, t);
                    Span span = span();
                    if (null != span) {
                        span.addEvent("error-handler", Map.of("handler.class", record.errorHandler.getClass().getName(),
                                        "handled.error.message", t.toString()));
                    }
                    try {
                        // there's no way to avoid this cast
                        ((ErrorHandler<Throwable>) record.errorHandler).accept(nextErrorRequest, response, t);
                    } catch (Throwable e) {
                        // when an exception is thrown in an error handler, the original exception is only logged
                        // the rest of the error handlers is skipped and the new exception is sent directly to the
                        // default error handler.
                        LOGGER.log(Level.WARNING,
                                   "Exception unexpectedly thrown from an error handler. Error handling of this logged "
                                           + "exception was aborted.",
                                   t);
                        defaultHandler(new IllegalStateException("Unexpected exception encountered during error handling.", e));
                        return;
                    }
                    return;
                }
            }

            defaultHandler(t);
        }

        private void defaultHandler(Throwable t) {
            Span span = span();
            if (null != span) {
                span.addEvent("error-handler", Map.of("handler.class", "DEFAULT-ERROR-HANDLER",
                                "handled.error.message", t.toString()));
            }
            String message = null;
            try {
                if (t instanceof HttpException) {
                    response.status(((HttpException) t).status());
                    message = t.getMessage();
                } else if (t.getCause() instanceof HttpException) {
                    Http.ResponseStatus status = ((HttpException) t.getCause()).status();
                    if (status.code() == Http.Status.INTERNAL_SERVER_ERROR_500.code()) {
                        response.status(Http.Status.INTERNAL_SERVER_ERROR_500);
                        message = Http.Status.INTERNAL_SERVER_ERROR_500.reasonPhrase();
                    } else {
                        response.status(status);
                        message = t.getMessage();
                    }
                } else if (t instanceof RejectedExecutionException
                        || t.getCause() instanceof RejectedExecutionException) {
                    response.status(Http.Status.SERVICE_UNAVAILABLE_503);
                    message = t.getMessage();
                } else {
                    LOGGER.log(t instanceof Error ? Level.SEVERE : Level.WARNING,
                               "Default error handler: Unhandled exception encountered.",
                               new ExecutionException("Unhandled 'cause' of this exception encountered.", t));

                    response.status(Http.Status.INTERNAL_SERVER_ERROR_500);
                    message = Http.Status.INTERNAL_SERVER_ERROR_500.reasonPhrase();
                }
            } catch (AlreadyCompletedException e) {
                LOGGER.log(Level.WARNING,
                           "Cannot perform error handling of the throwable (see cause of this exception) because headers "
                                   + "were already sent",
                           new IllegalStateException("Headers already sent. Cannot handle the cause of this exception.", t));
            }
            response.send(message).exceptionally(throwable -> {
                LOGGER.log(Level.WARNING, "Default error handler: Response wasn't successfully sent.", throwable);
                return null;
            });
        }

        @Override
        public void next(Throwable t) {
            checkNexted(t);

            nextNoCheck(t);
        }

        @Override
        public ServerRequest.Path path() {
            return path;
        }

        @Override
        public Tracer tracer() {
            return WebTracingConfig.tracer(webServer());
        }

        @Override
        public URI absoluteUri() {
            return lazyAbsoluteUri.get();
        }

        private class ErrorRoutedRequest extends RoutedRequest {
            private final Throwable t;

            ErrorRoutedRequest(LinkedList<ErrorHandlerRecord<?>> errorHandlers, Throwable t) {
                super(RoutedRequest.this, new RoutedResponse(response), path, errorHandlers);
                this.t = t;
            }

            @Override
            public void next() {
                super.next(t);
            }

            @Override
            public ServerRequest.Path path() {
                return super.path().absolute();
            }
        }
    }

    private static class RoutedResponse extends Response {

        private final AtomicReference<RoutedRequest> request = new AtomicReference<>();

        RoutedResponse(WebServer webServer, BareResponse bareResponse, List<MediaType> acceptedTypes) {
            super(webServer, bareResponse, acceptedTypes);
        }

        RoutedResponse(RoutedResponse response) {
            super(response);
            this.request.set(response.request.get());
        }

        @Override
        Optional<SpanContext> spanContext() {
            return Contexts.context()
                    .flatMap(ctx -> ctx.get(SpanContext.class));
        }

        @Override
        public Void send(Throwable content) {
            RoutedRequest routedRequest = request.get();
            if (routedRequest == null) {
                return super.send(content);
            } else {
                routedRequest.nextNoCheck(content);
            }
            return null;
        }

        void request(RoutedRequest request) {
            this.request.set(request);
        }
    }

    static class ErrorHandlerRecord<T extends Throwable> {
        private final Class<T> exceptionClass;
        private final ErrorHandler<T> errorHandler;

        private ErrorHandlerRecord(Class<T> exceptionClass, ErrorHandler<T> errorHandler) {
            this.exceptionClass = exceptionClass;
            this.errorHandler = errorHandler;
        }

        public static <T extends Throwable> ErrorHandlerRecord<T> of(Class<T> exceptionClass, ErrorHandler<T> errorHandler) {
            return new ErrorHandlerRecord<>(exceptionClass, errorHandler);
        }
    }
}
