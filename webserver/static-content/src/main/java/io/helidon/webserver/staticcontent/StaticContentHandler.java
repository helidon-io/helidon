/*
 * Copyright (c) 2017, 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.staticcontent;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;

import io.helidon.common.configurable.LruCache;
import io.helidon.common.media.type.MediaType;
import io.helidon.http.DateTime;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpException;
import io.helidon.http.InternalServerException;
import io.helidon.http.Method;
import io.helidon.http.NotFoundException;
import io.helidon.http.PathMatchers;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.Status;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

/**
 * Base implementation of static content support.
 */
abstract class StaticContentHandler implements StaticContentService {
    private static final System.Logger LOGGER = System.getLogger(StaticContentHandler.class.getName());

    private final Map<String, CachedHandlerInMemory> inMemoryCache = new ConcurrentHashMap<>();
    private final LruCache<String, CachedHandler> handlerCache;
    private final String welcomeFilename;
    private final Function<String, String> resolvePathFunction;
    private final AtomicInteger webServerCounter = new AtomicInteger();

    StaticContentHandler(StaticContentService.Builder<?> builder) {
        this.welcomeFilename = builder.welcomeFileName();
        this.resolvePathFunction = builder.resolvePathFunction();
        this.handlerCache = builder.handlerCache();
    }

    /**
     * Put {@code etag} parameter (if provided ) into the response headers, than validates {@code If-Match} and
     * {@code If-None-Match} headers and react accordingly.
     *
     * @param etag            the proposed ETag. If {@code null} then method returns false
     * @param requestHeaders  an HTTP request headers
     * @param responseHeaders an HTTP response headers
     * @throws io.helidon.http.RequestException if ETag is checked
     */
    static void processEtag(String etag, ServerRequestHeaders requestHeaders, ServerResponseHeaders responseHeaders) {
        if (etag == null || etag.isEmpty()) {
            return;
        }
        etag = unquoteETag(etag);

        Header newEtag = HeaderValues.create(HeaderNames.ETAG, true, false, '"' + etag + '"');
        // Put ETag into the response
        responseHeaders.set(newEtag);

        // Process If-None-Match header
        if (requestHeaders.contains(HeaderNames.IF_NONE_MATCH)) {
            List<String> ifNoneMatches = requestHeaders.get(HeaderNames.IF_NONE_MATCH).allValues();
            for (String ifNoneMatch : ifNoneMatches) {
                ifNoneMatch = unquoteETag(ifNoneMatch);
                if ("*".equals(ifNoneMatch) || ifNoneMatch.equals(etag)) {
                    // using exception to handle normal flow (same as in reactive static content)
                    throw new HttpException("Accepted by If-None-Match header", Status.NOT_MODIFIED_304, true)
                            .header(newEtag);
                }
            }
        }

        if (requestHeaders.contains(HeaderNames.IF_MATCH)) {
            // Process If-Match header
            List<String> ifMatches = requestHeaders.get(HeaderNames.IF_MATCH).allValues();
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
                    throw new HttpException("Not accepted by If-Match header", Status.PRECONDITION_FAILED_412, true)
                            .header(newEtag);
                }
            }
        }
    }

    static void processModifyHeaders(Instant modified,
                                     ServerRequestHeaders requestHeaders,
                                     ServerResponseHeaders responseHeaders,
                                     BiConsumer<ServerResponseHeaders, Instant> setModified) {
        if (modified == null) {
            return;
        }

        // Last-Modified
        setModified.accept(responseHeaders, modified);
        // If-Modified-Since
        Optional<Instant> ifModSince = requestHeaders
                .ifModifiedSince()
                .map(ChronoZonedDateTime::toInstant);
        if (ifModSince.isPresent() && !ifModSince.get().isBefore(modified)) {
            throw new HttpException("Not valid for If-Modified-Since header", Status.NOT_MODIFIED_304, true);
        }
        // If-Unmodified-Since
        Optional<Instant> ifUnmodSince = requestHeaders
                .ifUnmodifiedSince()
                .map(ChronoZonedDateTime::toInstant);
        if (ifUnmodSince.isPresent() && ifUnmodSince.get().isBefore(modified)) {
            throw new HttpException("Not valid for If-Unmodified-Since header", Status.PRECONDITION_FAILED_412, true);
        }
    }

    /**
     * Validates {@code If-Modify-Since} and {@code If-Unmodify-Since} headers and react accordingly.
     * Returns {@code true} only if response was sent.
     *
     * @param modified        the last modification instance. If {@code null} then method just returns {@code false}.
     * @param requestHeaders  an HTTP request headers
     * @param responseHeaders an HTTP response headers
     * @throws io.helidon.http.RequestException if (un)modify since header is checked
     */
    static void processModifyHeaders(Instant modified,
                                     ServerRequestHeaders requestHeaders,
                                     ServerResponseHeaders responseHeaders) {
        processModifyHeaders(modified, requestHeaders, responseHeaders, ServerResponseHeaders::lastModified);
    }

    /**
     * If provided {@code condition} is {@code true} then throws not found {@link io.helidon.http.RequestException}.
     *
     * @param condition if condition is true then throws an exception otherwise not
     * @throws io.helidon.http.RequestException if {@code condition} parameter is {@code true}.
     */
    static void throwNotFoundIf(boolean condition) {
        if (condition) {
            throw new NotFoundException("Static content not found");
        }
    }

    @Override
    public void beforeStart() {
        webServerCounter.incrementAndGet();
    }

    @Override
    public void afterStop() {
        int i = webServerCounter.decrementAndGet();

        if (i <= 0) {
            webServerCounter.set(0);
            releaseCache();
        }
    }

    @Override
    public void routing(HttpRules rules) {
        rules.route(Method.predicate(Method.GET, Method.HEAD),
                    PathMatchers.any(),
                    this::handle);
    }

    /**
     * Should release cache (if any exists).
     */
    void releaseCache() {
        handlerCache.clear();
        inMemoryCache.clear();
    }

    /**
     * Do handle for GET and HEAD HTTP methods. It is filtering implementation, prefers {@code response.next()} before NOT_FOUND.
     *
     * @param request  an HTTP request
     * @param response an HTTP response
     */
    void handle(ServerRequest request, ServerResponse response) {
        Method method = request.prologue().method();

        // Resolve path
        String requestPath = request.path().rawPathNoParams();
        if (requestPath.startsWith("/")) {
            requestPath = requestPath.substring(1);
        }
        String origPath = requestPath;
        requestPath = resolvePathFunction.apply(requestPath);
        boolean mapped = !requestPath.equals(origPath);

        // Call doHandle
        try {
            if (!doHandle(method, requestPath, request, response, mapped)) {
                response.next();
            }
        } catch (HttpException httpException) {
            if (httpException.status().code() == Status.NOT_FOUND_404.code()) {
                // Prefer to next() before NOT_FOUND
                response.next();
            } else {
                throw httpException;
            }
        } catch (Exception e) {
            LOGGER.log(Level.TRACE, "Failed to access static resource", e);
            throw new InternalServerException("Cannot access static resource", e);
        }
    }

    /**
     * Do handle for GET and HEAD HTTP methods.
     *
     * @param method        GET or HEAD HTTP method
     * @param requestedPath path to the requested resource
     * @param request       an HTTP request
     * @param response      an HTTP response
     * @param mapped        whether the requestedPath is mapped using a mapping function (and differs from defined path)
     * @return {@code true} only if static content was found and processed.
     * @throws java.io.IOException                          if resource is not acceptable
     * @throws io.helidon.http.RequestException if some known WEB error
     */
    abstract boolean doHandle(Method method,
                              String requestedPath,
                              ServerRequest request,
                              ServerResponse response,
                              boolean mapped)
            throws IOException, URISyntaxException;

    String welcomePageName() {
        return welcomeFilename;
    }

    /**
     * Cache in memory.
     * Only use when explicitly requested by a user, we NEVER clear the cache during runtime. If you cache too much,
     * you run out of memory.
     *
     * @param resource resource identifier (such as relative path), MUST be normalized and MUST exist to prevent caching
     *                 records based on user's requests (that could cause us to cache the same resource multiple time using
     *                 relative paths)
     * @param handler  in memory handler
     */
    void cacheInMemory(String resource, CachedHandlerInMemory handler) {
        inMemoryCache.put(resource, handler);
    }

    /**
     * Get in memory handler (if one is registered).
     *
     * @param resource resource to find
     * @return handler if found
     */
    Optional<CachedHandlerInMemory> cacheInMemory(String resource) {
        return Optional.ofNullable(inMemoryCache.get(resource));
    }

    /**
     * Find either in-memory cache or cached record.
     *
     * @param resource resource to locate cache record for
     * @return cached handler
     */

    Optional<CachedHandler> cacheHandler(String resource) {
        return cacheInMemory(resource)
                .map(CachedHandler.class::cast)
                .or(() -> handlerCache.get(resource));
    }

    void cacheHandler(String resource, CachedHandler cachedResource) {
        handlerCache.put(resource, cachedResource);
    }

    LruCache<String, CachedHandler> handlerCache() {
        return handlerCache;
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

    void cacheInMemory(String resource, MediaType contentType, byte[] bytes, Optional<Instant> lastModified) {
        int contentLength = bytes.length;
        Header contentLengthHeader = HeaderValues.create(HeaderNames.CONTENT_LENGTH, contentLength);

        CachedHandlerInMemory inMemoryResource;
        if (lastModified.isEmpty()) {
            inMemoryResource = new CachedHandlerInMemory(contentType,
                                                         null,
                                                         null,
                                                         bytes,
                                                         contentLength,
                                                         contentLengthHeader);
        } else {
            // we can cache this, as this is a jar record
            Header lastModifiedHeader = HeaderValues.create(HeaderNames.LAST_MODIFIED,
                                                            true,
                                                            false,
                                                            formatLastModified(lastModified.get()));

            inMemoryResource = new CachedHandlerInMemory(contentType,
                                                         lastModified.get(),
                                                         (headers, instant) -> headers.set(lastModifiedHeader),
                                                         bytes,
                                                         contentLength,
                                                         contentLengthHeader);
        }

        cacheInMemory(resource, inMemoryResource);
    }

    static String formatLastModified(Instant lastModified) {
        ZonedDateTime dt = ZonedDateTime.ofInstant(lastModified, ZoneId.systemDefault());
        return dt.format(DateTime.RFC_1123_DATE_TIME);
    }
}
