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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.context.Contexts;
import io.helidon.common.http.AlreadyCompletedException;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.tracing.config.SpanTracingConfig;
import io.helidon.tracing.config.TracingConfigUtil;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;

/**
 * Default (and only provided) implementation of {@link Routing}.
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

        try {
            WebServer webServer = bareRequest.webServer();
            HashRequestHeaders requestHeaders = new HashRequestHeaders(bareRequest.headers());
            RoutedResponse response = new RoutedResponse(
                    webServer,
                    bareResponse,
                    requestHeaders.acceptedTypes());

            // Jersey needs the raw path (not decoded) so we get that too
            String path = canonicalize(bareRequest.uri().normalize().getPath());
            String rawPath = canonicalize(bareRequest.uri().normalize().getRawPath());

            Crawler crawler = new Crawler(routes, path, rawPath, bareRequest.method());
            RoutedRequest nextRequests = new RoutedRequest(bareRequest, response, webServer, crawler, errorHandlers,
                    requestHeaders);

            Contexts.runInContext(nextRequests.context(), (Runnable) nextRequests::next);
        } catch (Error | RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Unexpected error occurred during routing!", e);
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
        private final Http.RequestMethod method;

        private volatile int index = -1;
        private volatile Crawler subCrawler;

        /**
         * Creates new instance.
         *
         * @param routes      routs to crawl throw.
         * @param contextPath a path representing URI path context.
         * @param path        an URI path to route.
         * @param rawPath     not decoded URI path to route.
         * @param method      an HTTP method to route.
         */
        private Crawler(List<Route> routes, Request.Path contextPath, String path, String rawPath,
                        Http.RequestMethod method) {
            this.routes = routes;
            this.path = path;
            this.rawPath = rawPath;
            this.contextPath = contextPath;
            this.method = method;
        }

        /**
         * Creates new instance of 'the root crawler'.
         *
         * @param routes routs to crawl throw.
         * @param path   a URI path to route.
         * @param rawPath not decoded URI path to route.
         * @param method an HTTP method to route.
         */
        Crawler(List<Route> routes, String path, String rawPath, Http.RequestMethod method) {
            this(routes, null, path, rawPath, method);
        }

        /**
         * Returns next {@link HandlerRoute} to execute or {@code null} if there are no more acceptable routes.
         * It is not synchronized.
         *
         * @return a next item.
         */
        public Item next() {
            while ((subCrawler != null) || (++index < routes.size())) {
                if (subCrawler != null) {
                    Item result = subCrawler.next();
                    if (result != null) {
                        return result;
                    } else {
                        subCrawler = null;
                    }
                } else {
                    Route route = routes.get(index);
                    if (route.accepts(method)) {
                        if (route instanceof HandlerRoute) {
                            HandlerRoute hr = (HandlerRoute) route;
                            PathMatcher.Result match = hr.match(path);
                            if (match.matches()) {
                                return new Item(hr, Request.Path.create(contextPath, path, rawPath, match.params()));
                            }
                        } else if (route instanceof RouteList) {
                            RouteList rl = (RouteList) route;
                            PathMatcher.PrefixResult prefixMatch = rl.prefixMatch(path);
                            PathMatcher.PrefixResult rawPrefixMatch = rl.prefixMatch(rawPath);
                            if (prefixMatch.matches()) {
                                subCrawler = new Crawler(rl,
                                                         Request.Path.create(contextPath, path, rawPath, prefixMatch.params()),
                                                         prefixMatch.remainingPart(),
                                                         rawPrefixMatch.remainingPart(),
                                                         method);
                                // do "continue" in order to not log the failure message bellow
                                continue;
                            }
                        }

                        LOGGER.finest(() -> "Route candidate '" + route + "' doesn't match path: " + path);
                    } else {
                        LOGGER.finest(() -> "Route candidate '" + route + "' doesn't match method: " + method);
                    }
                }
            }
            return null;
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

        private final AtomicBoolean nexted = new AtomicBoolean(false);

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
                      HashRequestHeaders headers) {
            super(req, webServer, headers);
            this.crawler = crawler;
            this.errorHandlers = new LinkedList<>(errorHandlers);
            this.path = null;
            this.response = response;
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
            Crawler.Item nextItem = crawler.next();
            if (nextItem == null) {
                // 404 error
                nextNoCheck(new NotFoundException("No handler found for path: " + path()));
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
                            span.log(nextItem.handlerRoute.diagnosticEvent());
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
            LOGGER.finest(() -> "(reqID: " + requestId() + ") Routing error: " + t.getClass());

            for (ErrorHandlerRecord<?> record = errorHandlers.pollFirst(); record != null; record = errorHandlers.pollFirst()) {
                if (record.exceptionClass.isAssignableFrom(t.getClass())) {
                    ErrorRoutedRequest nextErrorRequest = new ErrorRoutedRequest(errorHandlers, t);
                    Span span = span();
                    if (null != span) {
                        span.log(Map.of("event", "error-handler",
                                        "handler.class", record.errorHandler.getClass().getName(),
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
                span.log(Map.of("event", "error-handler",
                                "handler.class", "DEFAULT-ERROR-HANDLER",
                                "handled.error.message", t.toString()));
            }
            String message = null;
            try {
                if (t instanceof HttpException) {
                    response.status(((HttpException) t).status());
                } else if (t.getCause() instanceof HttpException) {
                    response.status(((HttpException) t.getCause()).status());
                } else if (t instanceof RejectedExecutionException
                           || t.getCause() instanceof RejectedExecutionException) {
                    response.status(Http.Status.SERVICE_UNAVAILABLE_503);
                } else {
                    LOGGER.log(t instanceof Error ? Level.SEVERE : Level.WARNING,
                               "Default error handler: Unhandled exception encountered.",
                               new ExecutionException("Unhandled 'cause' of this exception encountered.", t));

                    response.status(Http.Status.INTERNAL_SERVER_ERROR_500);
                }
                message = t.getMessage();
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
            checkNexted();

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

        RoutedResponse(WebServer webServer, BareResponse bareResponse, List<MediaType> acceptedTypes) {
            super(webServer, bareResponse, acceptedTypes);
        }

        RoutedResponse(RoutedResponse response) {
            super(response);
        }

        @Override
        Optional<SpanContext> spanContext() {
            return Contexts.context()
                    .flatMap(ctx -> ctx.get(SpanContext.class));
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
