/*
 * Copyright (c) 2017, 2023 Oracle and/or its affiliates.
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

package io.helidon.reactive.webserver.staticcontent;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.chrono.ChronoZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.common.http.Http;
import io.helidon.common.http.HttpException;
import io.helidon.reactive.webserver.RequestHeaders;
import io.helidon.reactive.webserver.ResponseHeaders;
import io.helidon.reactive.webserver.Routing;
import io.helidon.reactive.webserver.ServerRequest;
import io.helidon.reactive.webserver.ServerResponse;

import static io.helidon.common.http.Http.Header.IF_MATCH;
import static io.helidon.common.http.Http.Header.IF_NONE_MATCH;

/**
 * Base implementation of static content support.
 */
abstract class StaticContentHandler implements StaticContentSupport {
    private static final System.Logger LOGGER = System.getLogger(StaticContentHandler.class.getName());

    private final String welcomeFilename;
    private final Function<String, String> resolvePathFunction;

    StaticContentHandler(StaticContentSupport.Builder<?> builder) {
        this.welcomeFilename = builder.welcomeFileName();
        this.resolvePathFunction = builder.resolvePathFunction();
    }

    private int webServerCounter = 0;

    @Override
    public void update(Routing.Rules routing) {
        routing.onNewWebServer(ws -> {
            webServerStarted();
            ws.whenShutdown().thenRun(this::webServerStopped);
        });
        routing.get((req, res) -> handle(Http.Method.GET, req, res));
        routing.head((req, res) -> handle(Http.Method.HEAD, req, res));
    }

    private synchronized void webServerStarted() {
        webServerCounter++;
    }

    private synchronized void webServerStopped() {
        webServerCounter--;
        if (webServerCounter <= 0) {
            webServerCounter = 0;
            releaseCache();
        }
    }

    /**
     * Should release cache (if any exists).
     */
    void releaseCache() {
    }

    /**
     * Do handle for GET and HEAD HTTP methods. It is filtering implementation, prefers {@code response.next()} before NOT_FOUND.
     *
     * @param method   an HTTP method
     * @param request  an HTTP request
     * @param response an HTTP response
     */
    void handle(Http.Method method, ServerRequest request, ServerResponse response) {
        // Check method
        if ((method != Http.Method.GET) && (method != Http.Method.HEAD)) {
            request.next();
            return;
        }
        // Resolve path
        String requestPath = request.path().toString();
        if (requestPath.startsWith("/")) {
            requestPath = requestPath.substring(1);
        }
        requestPath = resolvePathFunction.apply(requestPath);

        // Call doHandle
        try {
            if (!doHandle(method, requestPath, request, response)) {
                request.next();
            }
        } catch (HttpException httpException) {
            if (httpException.status().code() == Http.Status.NOT_FOUND_404.code()) {
                // Prefer to next() before NOT_FOUND
                request.next();
            } else {
                throw httpException;
            }
        } catch (Exception e) {
            LOGGER.log(Level.DEBUG, "Failed to access static resource", e);
            throw new HttpException("Cannot access static resource!", Http.Status.INTERNAL_SERVER_ERROR_500, e);
        }
    }

    /**
     * Do handle for GET and HEAD HTTP methods.
     *
     * @param method   GET or HEAD HTTP method
     * @param requestedPath path to the requested resource
     * @param request  an HTTP request
     * @param response an HTTP response
     * @return {@code true} only if static content was found and processed.
     * @throws java.io.IOException   if resource is not acceptable
     * @throws io.helidon.common.http.HttpException if some known WEB error
     */
    abstract boolean doHandle(Http.Method method, String requestedPath, ServerRequest request, ServerResponse response)
            throws IOException, URISyntaxException;

    /**
     * Put {@code etag} parameter (if provided ) into the response headers, than validates {@code If-Match} and
     * {@code If-None-Match} headers and react accordingly.
     *
     * @param etag the proposed ETag. If {@code null} then method returns false
     * @param requestHeaders an HTTP request headers
     * @param responseHeaders an HTTP response headers
     * @throws io.helidon.common.http.HttpException if ETag is checked
     */
    static void processEtag(String etag, RequestHeaders requestHeaders, ResponseHeaders responseHeaders) {
        if (etag == null || etag.isEmpty()) {
            return;
        }
        etag = unquoteETag(etag);
        // Put ETag into the response
        responseHeaders.set(Http.Header.ETAG, '"' + etag + '"');

        // Process If-None-Match header
        if (requestHeaders.contains(IF_NONE_MATCH)) {
            List<String> ifNoneMatches = requestHeaders.values(IF_NONE_MATCH);
            for (String ifNoneMatch : ifNoneMatches) {
                ifNoneMatch = unquoteETag(ifNoneMatch);
                if ("*".equals(ifNoneMatch) || ifNoneMatch.equals(etag)) {
                    throw new HttpException("Accepted by If-None-Match header!", Http.Status.NOT_MODIFIED_304);
                }
            }
        }
        // Process If-Match header
        if (requestHeaders.contains(IF_MATCH)) {
            List<String> ifMatches = requestHeaders.values(Http.Header.IF_MATCH);

            boolean ifMatchChecked = false;
            for (String ifMatch : ifMatches) {
                ifMatch = unquoteETag(ifMatch);
                if ("*".equals(ifMatch) || ifMatch.equals(etag)) {
                    ifMatchChecked = true;
                    break;
                }
            }
            if (!ifMatchChecked) {
                throw new HttpException("Not accepted by If-Match header!", Http.Status.PRECONDITION_FAILED_412);
            }
        }
    }

    private static String unquoteETag(String etag) {
        if (etag == null || etag.isEmpty()) {
            return etag;
        }
        if (etag.startsWith("W/") || etag.startsWith("w/")) {
            etag = etag.substring(2);
        }
        if (etag.startsWith("\"") && etag.endsWith("\"")) {
            etag = etag.substring(1, etag.length() - 1);
        }
        return etag;
    }

    /**
     * Validates {@code If-Modify-Since} and {@code If-Unmodify-Since} headers and react accordingly.
     * Returns {@code true} only if response was sent.
     *
     * @param modified the last modification instance. If {@code null} then method just returns {@code false}.
     * @param requestHeaders an HTTP request headers
     * @param responseHeaders an HTTP response headers
     * @throws io.helidon.common.http.HttpException if (un)modify since header is checked
     */
    static void processModifyHeaders(Instant modified, RequestHeaders requestHeaders, ResponseHeaders responseHeaders) {
        if (modified == null) {
            return;
        }
        // Last-Modified
        responseHeaders.lastModified(modified);
        // If-Modified-Since
        Optional<Instant> ifModSince = requestHeaders
                .ifModifiedSince()
                .map(ChronoZonedDateTime::toInstant);
        if (ifModSince.isPresent() && !ifModSince.get().isBefore(modified)) {
            throw new HttpException("Not valid for If-Modified-Since header!", Http.Status.NOT_MODIFIED_304);
        }
        // If-Unmodified-Since
        Optional<Instant> ifUnmodSince = requestHeaders
                .ifUnmodifiedSince()
                .map(ChronoZonedDateTime::toInstant);
        if (ifUnmodSince.isPresent() && ifUnmodSince.get().isBefore(modified)) {
            throw new HttpException("Not valid for If-Unmodified-Since header!", Http.Status.PRECONDITION_FAILED_412);
        }
    }

    /**
     * If provided {@code condition} is {@code true} then throws not found {@link io.helidon.common.http.HttpException}.
     *
     * @param condition if condition is true then throws an exception otherwise not
     * @throws io.helidon.common.http.HttpException if {@code condition} parameter is {@code true}.
     */
    static void throwNotFoundIf(boolean condition) {
        if (condition) {
            throw new HttpException("Content not found!", Http.Status.NOT_FOUND_404);
        }
    }

    /**
     * Redirects to the given location.
     *
     * @param request request used to obtain query parameters for redirect
     * @param response a server response to use
     * @param location a location to redirect
     */
    static void redirect(ServerRequest request, ServerResponse response, String location) {
        String query = request.query();
        String locationWithQuery;
        if (query == null) {
            locationWithQuery = location;
        } else {
            locationWithQuery = location + "?" + query;
        }

        response.status(Http.Status.MOVED_PERMANENTLY_301);
        response.headers().set(Http.Header.LOCATION, locationWithQuery);
        response.send();
    }

    String welcomePageName() {
        return welcomeFilename;
    }
}
