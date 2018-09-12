/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.chrono.ChronoZonedDateTime;
import java.util.List;
import java.util.Optional;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;

/**
 * Request {@link Handler} processing a static content.
 */
abstract class StaticContentHandler {

    private final String welcomeFilename;
    private final ContentTypeSelector contentTypeSelector;
    private final Path root;

    /**
     * Creates new instance.
     *
     * @param welcomeFilename     a welcome filename
     * @param contentTypeSelector a selector for content type
     * @param root                static content root in path format
     */
    StaticContentHandler(String welcomeFilename, ContentTypeSelector contentTypeSelector, Path root) {
        this.welcomeFilename = welcomeFilename;
        this.contentTypeSelector = contentTypeSelector;
        this.root = root;
    }

    /**
     * Should release cache (if any exists).
     */
    void releaseCache() {}

    /**
     * Do handle for GET and HEAD HTTP methods. It is filtering implementation, prefers {@code response.next()} before NOT_FOUND.
     *
     * @param method   an HTTP method
     * @param request  an HTTP request
     * @param response an HTTP response
     * @throws IOException if resource is not acceptable
     */
    void handle(Http.RequestMethod method, ServerRequest request, ServerResponse response) {
        // Check method
        if (method != Http.Method.GET && method != Http.Method.HEAD) {
            request.next();
            return;
        }
        // Resolve path
        String requestPath = request.path().toString();
        if (requestPath.startsWith("/")) {
            requestPath = requestPath.substring(1);
        }
        Path resolved;
        if (requestPath.isEmpty()) {
            resolved = root;
        } else {
            resolved = root.resolve(Paths.get(requestPath)).normalize();
            if (!resolved.startsWith(root)) {
                request.next();
                return;
            }
        }
        // Call doHandle
        try {
            if (!doHandle(method, resolved, request, response)) {
                request.next();
            }
        } catch (IOException e) {
            throw new HttpException("Cannot access static resource!", Http.Status.INTERNAL_SERVER_ERROR_500, e);
        } catch (HttpException httpException) {
            if (httpException.status().code() == Http.Status.NOT_FOUND_404.code()) {
                // Prefer to next() before NOT_FOUND
                request.next();
            } else {
                throw  httpException;
            }
        }
    }

    /**
     * Do handle for GET and HEAD HTTP methods.
     *
     * @param method   GET or HEAD HTTP method
     * @param path     a resolved path to static content resource - can be invalid
     * @param request  an HTTP request
     * @param response an HTTP response
     * @return {@code true} only if static content was found and processed.
     * @throws IOException   if resource is not acceptable
     * @throws HttpException if some known WEB error
     */
    abstract boolean doHandle(Http.RequestMethod method, Path path, ServerRequest request, ServerResponse response)
            throws IOException;

    /**
     * Put {@code etag} parameter (if provided ) into the response headers, than validates {@code If-Match} and
     * {@code If-None-Match} headers and react accordingly.
     *
     * @param etag the proposed ETag. If {@code null} then method returns false
     * @param requestHeaders an HTTP request headers
     * @param responseHeaders an HTTP response headers
     * @throws HttpException if ETag is checked
     */
    static void processEtag(String etag, RequestHeaders requestHeaders, ResponseHeaders responseHeaders) {
        if (etag == null || etag.isEmpty()) {
            return;
        }
        etag = unquoteETag(etag);
        // Put ETag into the response
        responseHeaders.put(Http.Header.ETAG, '"' + etag + '"');
        // Process If-None-Match header
        List<String> ifNoneMatches = requestHeaders.values(Http.Header.IF_NONE_MATCH);
        for (String ifNoneMatch : ifNoneMatches) {
            ifNoneMatch = unquoteETag(ifNoneMatch);
            if ("*".equals(ifNoneMatch) || ifNoneMatch.equals(etag)) {
                throw new HttpException("Accepted by If-None-Match header!", Http.Status.NOT_MODIFIED_304);
            }
        }
        // Process If-Match header
        List<String> ifMatches = requestHeaders.values(Http.Header.IF_MATCH);
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
     * @throws HttpException if (un)modify since header is checked
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
     * If provided {@code condition} is {@code true} then throws not found {@link HttpException}.
     *
     * @param condition if condition is true then throws an exception otherwise not
     * @throws HttpException if {@code condition} parameter is {@code true}.
     */
    static void throwNotFoundIf(boolean condition) {
        if (condition) {
            throw new HttpException("Content not found!", Http.Status.NOT_FOUND_404);
        }
    }

    /**
     * Redirects to the given location.
     *
     * @param response a server response to use
     * @param location a location to redirect
     */
    static void redirect(ServerResponse response, String location) {
        response.status(Http.Status.MOVED_PERMANENTLY_301);
        response.headers().put(Http.Header.LOCATION, location);
        response.send();
    }

    String getWelcomePageName() {
        return welcomeFilename;
    }

    /**
     * Determines and set a Content-Type header based on filename extension.
     *
     * @param path            a path to the file
     * @param requestHeaders  an HTTP request headers
     * @param responseHeaders an HTTP response headers
     */
    void processContentType(Path path, RequestHeaders requestHeaders, ResponseHeaders responseHeaders) {
        processContentType(getFileName(path), requestHeaders, responseHeaders);
    }

    /**
     * Determines and set a Content-Type header based on filename extension.
     *
     * @param filename        a filename
     * @param requestHeaders  an HTTP request headers
     * @param responseHeaders an HTTP response headers
     */
    void processContentType(String filename, RequestHeaders requestHeaders, ResponseHeaders responseHeaders) {
        // Try to get Content-Type
        MediaType type = contentTypeSelector.determine(filename, requestHeaders);
        if (type != null) {
            responseHeaders.contentType(type);
        }
    }

    // spotbugs... path.getFileName may return null :(
    static String getFileName(Path path) {
        Path fileName = path.getFileName();

        if (null == fileName) {
            return "";
        }

        return fileName.toString();
    }
}
