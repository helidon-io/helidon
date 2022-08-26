/*
 * Copyright (c) 2017, 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.webserver.staticcontent;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.chrono.ChronoZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.common.http.DirectHandler;
import io.helidon.common.http.HeadersServerRequest;
import io.helidon.common.http.HeadersServerResponse;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.Header;
import io.helidon.common.uri.UriQuery;
import io.helidon.nima.webserver.http.HttpException;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.PathMatchers;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

/**
 * Base implementation of static content support.
 */
abstract class StaticContentHandler implements StaticContentSupport {
    private static final System.Logger LOGGER = System.getLogger(StaticContentHandler.class.getName());

    private final String welcomeFilename;
    private final Function<String, String> resolvePathFunction;
    private int webServerCounter = 0;

    StaticContentHandler(StaticContentSupport.Builder<?> builder) {
        this.welcomeFilename = builder.welcomeFileName();
        this.resolvePathFunction = builder.resolvePathFunction();
    }

    /**
     * Put {@code etag} parameter (if provided ) into the response headers, than validates {@code If-Match} and
     * {@code If-None-Match} headers and react accordingly.
     *
     * @param etag            the proposed ETag. If {@code null} then method returns false
     * @param requestHeaders  an HTTP request headers
     * @param responseHeaders an HTTP response headers
     * @throws io.helidon.nima.webserver.http.HttpException if ETag is checked
     */
    static void processEtag(String etag, HeadersServerRequest requestHeaders, HeadersServerResponse responseHeaders) {
        if (etag == null || etag.isEmpty()) {
            return;
        }
        etag = unquoteETag(etag);
        // Put ETag into the response
        responseHeaders.set(Header.ETAG.withValue('"' + etag + '"'));
        // Process If-None-Match header
        if (requestHeaders.contains(Header.IF_NONE_MATCH)) {
            List<String> ifNoneMatches = requestHeaders.get(Header.IF_NONE_MATCH).allValues();
            for (String ifNoneMatch : ifNoneMatches) {
                ifNoneMatch = unquoteETag(ifNoneMatch);
                if ("*".equals(ifNoneMatch) || ifNoneMatch.equals(etag)) {
                    throw HttpException.builder()
                            .message("Accepted by If-None-Match header!")
                            .type(DirectHandler.EventType.OTHER)
                            .status(Http.Status.NOT_MODIFIED_304)
                            .build();
                }
            }
        }

        if (requestHeaders.contains(Header.IF_MATCH)) {
            // Process If-Match header
            List<String> ifMatches = requestHeaders.get(Header.IF_MATCH).allValues();
            if (!ifMatches.isEmpty()) {
                boolean ifMatchChecked = false;
                for (String ifMatch : ifMatches) {
                    ifMatch = unquoteETag(ifMatch);
                    if ("*".equals(ifMatch) || ifMatch.equals(etag)) {
                        ifMatchChecked = true;
                        break;
                    }
                }
                if (!ifMatchChecked) {
                    throw HttpException.builder()
                            .message("Not accepted by If-Match header!")
                            .type(DirectHandler.EventType.OTHER)
                            .status(Http.Status.PRECONDITION_FAILED_412)
                            .build();
                }
            }
        }
    }

    /**
     * Validates {@code If-Modify-Since} and {@code If-Unmodify-Since} headers and react accordingly.
     * Returns {@code true} only if response was sent.
     *
     * @param modified        the last modification instance. If {@code null} then method just returns {@code false}.
     * @param requestHeaders  an HTTP request headers
     * @param responseHeaders an HTTP response headers
     * @throws io.helidon.nima.webserver.http.HttpException if (un)modify since header is checked
     */
    static void processModifyHeaders(Instant modified,
                                     HeadersServerRequest requestHeaders,
                                     HeadersServerResponse responseHeaders) {
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
            throw HttpException.builder()
                    .message("Not valid for If-Modified-Since header!")
                    .type(DirectHandler.EventType.OTHER)
                    .status(Http.Status.NOT_MODIFIED_304)
                    .build();
        }
        // If-Unmodified-Since
        Optional<Instant> ifUnmodSince = requestHeaders
                .ifUnmodifiedSince()
                .map(ChronoZonedDateTime::toInstant);
        if (ifUnmodSince.isPresent() && ifUnmodSince.get().isBefore(modified)) {
            throw HttpException.builder()
                    .message("Not valid for If-Unmodified-Since header!")
                    .type(DirectHandler.EventType.OTHER)
                    .status(Http.Status.PRECONDITION_FAILED_412)
                    .build();
        }
    }

    /**
     * If provided {@code condition} is {@code true} then throws not found {@link io.helidon.nima.webserver.http.HttpException}.
     *
     * @param condition if condition is true then throws an exception otherwise not
     * @throws io.helidon.nima.webserver.http.HttpException if {@code condition} parameter is {@code true}.
     */
    static void throwNotFoundIf(boolean condition) {
        if (condition) {
            throw HttpException.builder()
                    .message("Static content not found!")
                    .type(DirectHandler.EventType.NOT_FOUND)
                    .build();
        }
    }

    /**
     * Redirects to the given location.
     *
     * @param request  request used to obtain query parameters for redirect
     * @param response a server response to use
     * @param location a location to redirect
     */
    static void redirect(ServerRequest request, ServerResponse response, String location) {
        UriQuery query = request.query();
        String locationWithQuery;
        if (query == null) {
            locationWithQuery = location;
        } else {
            locationWithQuery = location + "?" + query;
        }

        response.status(Http.Status.MOVED_PERMANENTLY_301);
        response.header(Header.LOCATION.withValue(locationWithQuery));
        response.send();
    }

    @Override
    public synchronized void beforeStart() {
        webServerCounter++;
    }

    @Override
    public void afterStop() {
        webServerCounter--;
        if (webServerCounter <= 0) {
            webServerCounter = 0;
            releaseCache();
        }
    }

    @Override
    public void routing(HttpRules rules) {
        rules.route(Http.Method.predicate(Http.Method.GET, Http.Method.HEAD),
                    PathMatchers.any(),
                    this::handle);
    }

    /**
     * Should release cache (if any exists).
     */
    void releaseCache() {
    }

    /**
     * Do handle for GET and HEAD HTTP methods. It is filtering implementation, prefers {@code response.next()} before NOT_FOUND.
     *
     * @param request  an HTTP request
     * @param response an HTTP response
     */
    void handle(ServerRequest request, ServerResponse response) {
        Http.Method method = request.prologue().method();

        // Resolve path
        String requestPath = request.path().rawPathNoParams();
        if (requestPath.startsWith("/")) {
            requestPath = requestPath.substring(1);
        }
        requestPath = resolvePathFunction.apply(requestPath);

        // Call doHandle
        try {
            if (!doHandle(method, requestPath, request, response)) {
                response.next();
            }
        } catch (HttpException httpException) {
            if (httpException.status().code() == Http.Status.NOT_FOUND_404.code()) {
                // Prefer to next() before NOT_FOUND
                response.next();
            } else {
                throw httpException;
            }
        } catch (Exception e) {
            LOGGER.log(Level.TRACE, "Failed to access static resource", e);
            throw HttpException.builder()
                    .message("Cannot access static resource!")
                    .type(DirectHandler.EventType.INTERNAL_ERROR)
                    .cause(e)
                    .build();
        }
    }

    /**
     * Do handle for GET and HEAD HTTP methods.
     *
     * @param method        GET or HEAD HTTP method
     * @param requestedPath path to the requested resource
     * @param request       an HTTP request
     * @param response      an HTTP response
     * @return {@code true} only if static content was found and processed.
     * @throws java.io.IOException                          if resource is not acceptable
     * @throws io.helidon.nima.webserver.http.HttpException if some known WEB error
     */
    abstract boolean doHandle(Http.Method method, String requestedPath, ServerRequest request, ServerResponse response)
            throws IOException, URISyntaxException;

    String welcomePageName() {
        return welcomeFilename;
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
}
