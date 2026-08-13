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

package io.helidon.webserver.staticcontent;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.common.LruCache;
import io.helidon.common.media.type.MediaType;
import io.helidon.http.DateTime;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
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
import io.helidon.webserver.CloseConnectionException;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

/**
 * Base implementation of static content support.
 */
abstract class StaticContentHandler implements HttpService {
    private static final System.Logger LOGGER = System.getLogger(StaticContentHandler.class.getName());

    private final LruCache<String, CachedHandler> handlerCache;
    private final String welcomeFilename;
    private final Function<String, String> resolvePathFunction;
    private final AtomicInteger webServerCounter = new AtomicInteger();
    private final MemoryCache memoryCache;

    StaticContentHandler(BaseHandlerConfig config) {
        this.welcomeFilename = config.welcome().orElse(null);
        this.resolvePathFunction = config.pathMapper();
        this.handlerCache = config.recordCacheCapacity()
                .map(LruCache::<String, CachedHandler>create)
                .orElseGet(LruCache::create);
        this.memoryCache = config.memoryCache().orElseGet(MemoryCache::create);
    }

    static void processPreconditions(String etag,
                                     Instant modified,
                                     ServerRequestHeaders requestHeaders,
                                     ServerResponseHeaders responseHeaders) {
        processPreconditions(etag, modified, requestHeaders, responseHeaders, ServerResponseHeaders::lastModified);
    }

    static void processPreconditions(String etag,
                                     Instant modified,
                                     ServerRequestHeaders requestHeaders,
                                     ServerResponseHeaders responseHeaders,
                                     BiConsumer<ServerResponseHeaders, Instant> setModified) {
        Header newEtag = null;
        if (etag != null && !etag.isEmpty()) {
            etag = unquoteETag(etag);
            newEtag = HeaderValues.create(HeaderNames.ETAG, true, false, '"' + etag + '"');
            responseHeaders.set(newEtag);
        }

        boolean ifMatchPresent = requestHeaders.contains(HeaderNames.IF_MATCH);
        if (ifMatchPresent) {
            // Process If-Match header
            if (!matchesEntityTag(requestHeaders.get(HeaderNames.IF_MATCH), etag, true)) {
                HttpException exception = new HttpException("Not accepted by If-Match header",
                                                            Status.PRECONDITION_FAILED_412,
                                                            true);
                if (newEtag != null) {
                    exception.header(newEtag);
                }
                throw exception;
            }
        }

        if (modified != null) {
            modified = modified.truncatedTo(ChronoUnit.SECONDS);
            // Last-Modified
            setModified.accept(responseHeaders, modified);
            // If-Unmodified-Since
            if (!ifMatchPresent) {
                Optional<Instant> ifUnmodSince = conditionalDate(requestHeaders,
                                                                 HeaderNames.IF_UNMODIFIED_SINCE,
                                                                 requestHeaders::ifUnmodifiedSince);
                if (ifUnmodSince.isPresent() && ifUnmodSince.get().isBefore(modified)) {
                    throw new HttpException("Not valid for If-Unmodified-Since header",
                                            Status.PRECONDITION_FAILED_412,
                                            true);
                }
            }
        }

        // Process If-None-Match header
        boolean ifNoneMatchPresent = requestHeaders.contains(HeaderNames.IF_NONE_MATCH);
        if (ifNoneMatchPresent) {
            if (matchesEntityTag(requestHeaders.get(HeaderNames.IF_NONE_MATCH), etag, false)) {
                // using exception to handle normal flow (same as in reactive static content)
                HttpException exception = new HttpException("Accepted by If-None-Match header",
                                                            Status.NOT_MODIFIED_304,
                                                            true);
                if (newEtag != null) {
                    exception.header(newEtag);
                }
                throw exception;
            }
        }

        if (modified != null && !ifNoneMatchPresent) {
            // If-Modified-Since
            Optional<Instant> ifModSince = conditionalDate(requestHeaders,
                                                           HeaderNames.IF_MODIFIED_SINCE,
                                                           requestHeaders::ifModifiedSince);
            if (ifModSince.isPresent() && !ifModSince.get().isBefore(modified)) {
                throw new HttpException("Not valid for If-Modified-Since header", Status.NOT_MODIFIED_304, true);
            }
        }
    }

    private static boolean matchesEntityTag(Header header, String etag, boolean strongComparison) {
        boolean matched = false;
        boolean singleValue = header.valueCount() == 1;
        for (String fieldValue : header.allValues()) {
            int tokenStart = 0;
            int fieldLength = fieldValue.length();
            while (tokenStart <= fieldLength) {
                int start = tokenStart;
                while (start < fieldLength && fieldValue.charAt(start) <= ' ') {
                    start++;
                }

                boolean weak = fieldLength - start >= 2
                        && (fieldValue.charAt(start) == 'W' || fieldValue.charAt(start) == 'w')
                        && fieldValue.charAt(start + 1) == '/';
                int entityStart = weak ? start + 2 : start;
                if (entityStart < fieldLength && fieldValue.charAt(entityStart) == '"') {
                    int entityEnd = fieldValue.indexOf('"', entityStart + 1);
                    if (entityEnd < 0) {
                        break;
                    }

                    int separator = entityEnd + 1;
                    while (separator < fieldLength && fieldValue.charAt(separator) <= ' ') {
                        separator++;
                    }
                    if (separator == fieldLength || fieldValue.charAt(separator) == ',') {
                        int length = entityEnd - entityStart - 1;
                        if (etag != null
                                && (!strongComparison || !weak)
                                && length == etag.length()
                                && fieldValue.regionMatches(entityStart + 1, etag, 0, length)) {
                            matched = true;
                        }
                    } else {
                        separator = fieldValue.indexOf(',', separator);
                        if (separator < 0) {
                            break;
                        }
                    }
                    if (separator == fieldLength) {
                        break;
                    }
                    tokenStart = separator + 1;
                } else {
                    int separator = fieldValue.indexOf(',', start);
                    int end = separator < 0 ? fieldLength : separator;
                    while (start < end && fieldValue.charAt(end - 1) <= ' ') {
                        end--;
                    }
                    if (end - start == 1 && fieldValue.charAt(start) == '*') {
                        return singleValue && tokenStart == 0 && separator < 0;
                    }
                    if (weak) {
                        start += 2;
                    }
                    int length = end - start;
                    if (etag != null
                            && (!strongComparison || !weak)
                            && length == etag.length()
                            && fieldValue.regionMatches(start, etag, 0, length)) {
                        matched = true;
                    }
                    if (separator < 0) {
                        break;
                    }
                    tokenStart = separator + 1;
                }
            }
        }
        return matched;
    }

    private static Optional<Instant> conditionalDate(ServerRequestHeaders requestHeaders,
                                                     HeaderName headerName,
                                                     Supplier<Optional<ZonedDateTime>> dateSupplier) {
        if (requestHeaders.contains(headerName) && requestHeaders.get(headerName).valueCount() != 1) {
            return Optional.empty();
        }
        try {
            return dateSupplier.get().map(ChronoZonedDateTime::toInstant);
        } catch (DateTimeParseException _) {
            return Optional.empty();
        }
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

    static String formatLastModified(Instant lastModified) {
        ZonedDateTime dt = ZonedDateTime.ofInstant(lastModified, ZoneId.systemDefault());
        return dt.format(DateTime.RFC_1123_DATE_TIME);
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
        memoryCache.clear(this);
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
        } catch (CloseConnectionException e) {
            throw e;
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
     * @throws java.io.IOException              if resource is not acceptable
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
        memoryCache.cache(this, resource, handler);
    }

    /**
     * Get in memory handler (if one is registered).
     *
     * @param resource resource to find
     * @return handler if found
     */
    Optional<CachedHandlerInMemory> cacheInMemory(String resource) {
        return memoryCache.get(this, resource);
    }

    boolean canCacheInMemory(int size) {
        return memoryCache.available(size);
    }

    Optional<CachedHandlerInMemory> cacheInMemory(String resource, int size, Supplier<CachedHandlerInMemory> supplier) {
        return memoryCache.cache(this, resource, size, supplier);
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
