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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.common.LruCache;
import io.helidon.common.media.type.MediaType;
import io.helidon.http.DateTime;
import io.helidon.http.ForbiddenException;
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
import io.helidon.http.encoding.AcceptEncoding;
import io.helidon.http.encoding.ContentEncoder;
import io.helidon.http.encoding.ContentEncodingContext;
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
    private static final String SIDECAR_MEMORY_CACHE_PREFIX = "\u0000sidecar\u0000";

    private final LruCache<String, CachedHandler> handlerCache;
    private final String welcomeFilename;
    private final Function<String, String> resolvePathFunction;
    private final AtomicInteger webServerCounter = new AtomicInteger();
    private final MemoryCache memoryCache;
    private final boolean preCompressedEnabled;
    private final boolean preCompressedCrossOriginSourcingEnabled;
    private final Map<String, String> preCompressedEncodings;

    StaticContentHandler(BaseHandlerConfig config) {
        this(config, false);
    }

    StaticContentHandler(BaseHandlerConfig config, boolean preCompressedCrossOriginSourcingEnabled) {
        this.welcomeFilename = config.welcome().orElse(null);
        this.resolvePathFunction = config.pathMapper();
        this.handlerCache = config.recordCacheCapacity()
                .map(LruCache::<String, CachedHandler>create)
                .orElseGet(LruCache::create);
        this.memoryCache = config.memoryCache().orElseGet(MemoryCache::create);
        this.preCompressedEnabled = config.preCompressedEnabled().orElse(true);
        this.preCompressedCrossOriginSourcingEnabled = preCompressedCrossOriginSourcingEnabled;
        this.preCompressedEncodings = StaticContentConfigSupport.normalizePreCompressedEncodings(
                config.preCompressedEncodings()
                        .orElseGet(StaticContentConfigSupport::defaultPreCompressedEncodings));
    }

    static String etag(Instant lastModified, long contentLength) {
        String timestamp = String.valueOf(lastModified.toEpochMilli());
        return contentLength < 0 ? timestamp : timestamp + ";length=" + contentLength;
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
        etag = unquoteETag(etag);
        Header newEtag = null;
        if (etag != null && !etag.isEmpty()) {
            newEtag = HeaderValues.create(HeaderNames.ETAG, true, false, '"' + etag + '"');
        }
        processPreconditions(etag,
                             newEtag,
                             modified,
                             null,
                             requestHeaders,
                             responseHeaders,
                             setModified);
    }

    static void processPreconditions(StaticContentMetadata metadata,
                                     ServerRequestHeaders requestHeaders,
                                     ServerResponseHeaders responseHeaders) {
        processPreconditions(metadata.etag(),
                             metadata.etagHeader(),
                             metadata.lastModified(),
                             metadata.lastModifiedHeader(),
                             requestHeaders,
                             responseHeaders,
                             ServerResponseHeaders::lastModified);
    }

    static void processPreconditions(StaticContentMetadata metadata,
                                     ResponseRepresentation representation,
                                     ServerRequestHeaders requestHeaders,
                                     ServerResponseHeaders responseHeaders) {
        String etag = representationEtag(metadata, representation);
        Header etagHeader = etag == null ? null : representation.etagHeader(etag);
        try {
            processPreconditions(etag,
                                 etagHeader,
                                 metadata.lastModified(),
                                 metadata.lastModifiedHeader(),
                                 requestHeaders,
                                 responseHeaders,
                                 ServerResponseHeaders::lastModified);
        } catch (HttpException e) {
            representation.apply(e);
            throw e;
        }
    }

    static String representationEtag(StaticContentMetadata metadata, ResponseRepresentation representation) {
        String etag = metadata.etag();
        if (etag != null && representation.etagRequiresContentLength()) {
            int lengthIndex = etag.lastIndexOf(";length=");
            if (lengthIndex >= 0) {
                etag = etag.substring(0, lengthIndex);
            }
        }
        if (etag != null) {
            etag = representation.etag(etag, metadata.contentLength());
        }
        return etag;
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

    static String sidecarMemoryCacheKey(String requestedResource, String coding) {
        return SIDECAR_MEMORY_CACHE_PREFIX + coding + '\u0000' + requestedResource;
    }

    static String unquoteETag(String etag) {
        if (etag == null || etag.isEmpty()) {
            return etag;
        }
        if (etag.startsWith("W/") || etag.startsWith("w/")) {
            etag = etag.substring(2);
        }
        if (etag.length() >= 2 && etag.startsWith("\"") && etag.endsWith("\"")) {
            etag = etag.substring(1, etag.length() - 1);
        }
        return etag;
    }

    static boolean isWeakETag(String etag) {
        return etag != null && (etag.startsWith("W/") || etag.startsWith("w/"));
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

    boolean preCompressedCrossOriginSourcingEnabled() {
        return preCompressedCrossOriginSourcingEnabled;
    }

    CachedHandler selectHandler(CachedHandler identityHandler,
                                ServerRequest request,
                                SidecarCache.Resolver sidecarResolver) throws IOException, URISyntaxException {
        AcceptEncoding acceptEncoding = AcceptEncoding.create(request.headers());
        if (!acceptEncoding.valid()) {
            throw new HttpException("Invalid Accept-Encoding header", Status.BAD_REQUEST_400);
        }
        if ((!preCompressedEnabled || preCompressedEncodings.isEmpty())
                && request.prologue().method() == Method.GET
                && request.headers().contains(HeaderNames.RANGE)
                && acceptEncoding.identity().isPresent()) {
            return identityHandler.withRepresentation(ResponseRepresentation.identity(true));
        }

        ResponseRepresentation identityRepresentation = ResponseRepresentation.identity(true);
        if (!acceptEncoding.present()) {
            return identityHandler.withRepresentation(identityRepresentation);
        }

        List<RepresentationCandidate> staticCandidates = new ArrayList<>(preCompressedEncodings.size() + 1);
        RepresentationCandidate identityCandidate = acceptEncoding.identity()
                .map(RepresentationCandidate::identity)
                .orElse(null);
        if (identityCandidate != null) {
            staticCandidates.add(identityCandidate);
        }
        var listenerContext = request.listenerContext();
        ContentEncodingContext contentEncodingContext = listenerContext == null
                ? null
                : listenerContext.contentEncodingContext();

        if (preCompressedEnabled) {
            int order = 0;
            for (Map.Entry<String, String> entry : preCompressedEncodings.entrySet()) {
                String coding = entry.getKey();
                int candidateOrder = order++;
                List<AcceptEncoding.CodingQuality> qualities = sidecarQualities(acceptEncoding,
                                                                                contentEncodingContext,
                                                                                coding,
                                                                                preCompressedEncodings.keySet());
                SidecarSource sidecar = null;
                AcceptEncoding.CodingQuality previousQuality = null;
                for (AcceptEncoding.CodingQuality quality : qualities) {
                    if (quality.equals(previousQuality)) {
                        continue;
                    }
                    previousQuality = quality;
                    if (identityCandidate == null
                            || compareCandidates(CandidateType.SIDECAR, quality, candidateOrder, identityCandidate) < 0) {
                        if (sidecar == null) {
                            sidecar = new SidecarSource(coding, entry.getValue());
                        }
                        staticCandidates.add(RepresentationCandidate.sidecar(quality, candidateOrder, sidecar));
                    }
                }
            }
        }

        return selectCandidate(staticCandidates,
                               acceptEncoding,
                               request,
                               identityHandler,
                               identityRepresentation,
                               sidecarResolver);
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

    CachedHandlerInMemory cacheInMemory(String resource, MediaType contentType, byte[] bytes) {
        int contentLength = bytes.length;
        CachedHandlerInMemory inMemoryResource =
                new CachedHandlerInMemory(StaticContentMetadata.create(contentType, contentLength),
                                          bytes);

        cacheInMemory(resource, inMemoryResource);
        return inMemoryResource;
    }

    CachedHandlerInMemory cacheInMemory(String resource, MediaType contentType, byte[] bytes, Instant lastModified) {
        int contentLength = bytes.length;
        CachedHandlerInMemory inMemoryResource =
                new CachedHandlerInMemory(StaticContentMetadata.create(contentType, lastModified, contentLength),
                                          bytes);

        cacheInMemory(resource, inMemoryResource);
        return inMemoryResource;
    }

    private static void processPreconditions(String etag,
                                             Header newEtag,
                                             Instant modified,
                                             Header lastModifiedHeader,
                                             ServerRequestHeaders requestHeaders,
                                             ServerResponseHeaders responseHeaders,
                                             BiConsumer<ServerResponseHeaders, Instant> setModified) {
        if (newEtag != null) {
            responseHeaders.set(newEtag);
        }
        boolean weakEtag = newEtag != null && isWeakETag(newEtag.get());

        boolean ifMatchPresent = requestHeaders.contains(HeaderNames.IF_MATCH);
        if (ifMatchPresent) {
            // Process If-Match header
            if (!matchesEntityTag(requestHeaders.get(HeaderNames.IF_MATCH), etag, true, weakEtag)) {
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
            if (lastModifiedHeader == null) {
                setModified.accept(responseHeaders, modified);
            } else {
                responseHeaders.set(lastModifiedHeader);
            }
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
            if (matchesEntityTag(requestHeaders.get(HeaderNames.IF_NONE_MATCH), etag, false, weakEtag)) {
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

    private static boolean matchesEntityTag(Header header,
                                            String etag,
                                            boolean strongComparison,
                                            boolean currentWeak) {
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
                                && (!strongComparison || (!weak && !currentWeak))
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
                            && (!strongComparison || (!weak && !currentWeak))
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

    private static List<AcceptEncoding.CodingQuality> sidecarQualities(AcceptEncoding acceptEncoding,
                                                                       ContentEncodingContext contentEncodingContext,
                                                                       String coding,
                                                                       Set<String> sidecarCodings) {
        List<AcceptEncoding.CodingQuality> result = new ArrayList<>();
        acceptEncoding.match(coding, true).ifPresent(result::add);
        switch (coding) {
        case "gzip" -> acceptEncoding.match("x-gzip", false).ifPresent(result::add);
        case "x-gzip" -> acceptEncoding.match("gzip", false).ifPresent(result::add);
        case "compress" -> acceptEncoding.match("x-compress", false).ifPresent(result::add);
        case "x-compress" -> acceptEncoding.match("compress", false).ifPresent(result::add);
        default -> {
        }
        }

        if (contentEncodingContext == null || !contentEncodingContext.contentEncodingEnabled()) {
            return result;
        }

        Optional<String> canonicalCoding = contentEncodingContext.canonicalEncodingId(coding);
        if (canonicalCoding.isEmpty()) {
            return result;
        }

        for (AcceptEncoding.CodingQuality quality : acceptEncoding.acceptedCodings(false)) {
            String acceptedCoding = quality.coding();
            if (coding.equals(acceptedCoding)
                    || sidecarCodings.contains(acceptedCoding)) {
                continue;
            }
            if (canonicalCoding.equals(contentEncodingContext.canonicalEncodingId(acceptedCoding))) {
                result.add(quality);
            }
        }
        return result;
    }

    private static List<RuntimeEncoding> runtimeEncodings(ServerRequest request,
                                                          AcceptEncoding acceptEncoding,
                                                          RepresentationCandidate bestStaticCandidate) {
        var listenerContext = request.listenerContext();
        if (listenerContext == null) {
            return List.of();
        }
        ContentEncodingContext contentEncodingContext = listenerContext.contentEncodingContext();
        if (contentEncodingContext == null || !contentEncodingContext.contentEncodingEnabled()) {
            return List.of();
        }
        if (!runtimeEncodingsNeeded(acceptEncoding, bestStaticCandidate, contentEncodingContext)) {
            return List.of();
        }

        Set<String> seen = new LinkedHashSet<>();
        List<RuntimeEncoding> result = new ArrayList<>();
        for (String id : contentEncodingContext.contentEncodingIds()) {
            addRuntimeEncoding(result, seen, contentEncodingContext, acceptEncoding, bestStaticCandidate, id);
        }
        for (AcceptEncoding.CodingQuality quality : acceptEncoding.acceptedCodings(false)) {
            if (!AcceptEncoding.WILDCARD.equals(quality.coding())) {
                addRuntimeEncoding(result, seen, contentEncodingContext, acceptEncoding, bestStaticCandidate, quality.coding());
            }
        }
        return result;
    }

    private static void addRuntimeEncoding(List<RuntimeEncoding> result,
                                           Set<String> seen,
                                           ContentEncodingContext contentEncodingContext,
                                           AcceptEncoding acceptEncoding,
                                           RepresentationCandidate bestStaticCandidate,
                                           String id) {
        String normalized = id.toLowerCase(Locale.ROOT);
        if (!seen.add(normalized)) {
            return;
        }

        Optional<AcceptEncoding.CodingQuality> quality = acceptEncoding.match(normalized, true);
        if (quality.isEmpty() || !contentEncodingContext.contentEncodingSupported(normalized)) {
            return;
        }

        ContentEncoder encoder = contentEncodingContext.encoder(normalized);
        AcceptEncoding.CodingQuality selectedQuality = quality.get();

        if (runtimeCanBeatStatic(selectedQuality, bestStaticCandidate)) {
            result.add(new RuntimeEncoding(selectedQuality, encoder, normalized));
        }
    }

    private static boolean runtimeEncodingsNeeded(AcceptEncoding acceptEncoding,
                                                  RepresentationCandidate bestStaticCandidate,
                                                  ContentEncodingContext contentEncodingContext) {
        boolean providerMatchNeeded = false;
        for (AcceptEncoding.CodingQuality quality : acceptEncoding.acceptedCodings(true)) {
            if (!runtimeCanBeatStatic(quality, bestStaticCandidate)) {
                continue;
            }
            String coding = quality.coding();
            if (AcceptEncoding.WILDCARD.equals(coding)) {
                return contentEncodingContext.contentEncodingEnabled();
            }
            if (contentEncodingContext.contentEncodingSupported(coding)) {
                return true;
            }
            providerMatchNeeded = true;
        }
        if (!providerMatchNeeded) {
            return false;
        }
        for (String id : contentEncodingContext.contentEncodingIds()) {
            Optional<AcceptEncoding.CodingQuality> quality = acceptEncoding.match(id, true);
            if (quality.isPresent() && runtimeCanBeatStatic(quality.get(), bestStaticCandidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean runtimeCanBeatStatic(AcceptEncoding.CodingQuality quality,
                                                RepresentationCandidate bestStaticCandidate) {
        if (bestStaticCandidate == null) {
            return true;
        }
        return compareCandidates(CandidateType.RUNTIME, quality, 0, bestStaticCandidate) < 0;
    }

    private static int compareCandidates(RepresentationCandidate first, RepresentationCandidate second) {
        return compareCandidates(first.type(), first.quality(), first.order(), second);
    }

    private static int compareCandidates(CandidateType firstType,
                                         AcceptEncoding.CodingQuality firstQuality,
                                         int firstOrder,
                                         RepresentationCandidate second) {
        int q = Double.compare(second.quality().q(), firstQuality.q());
        if (q != 0) {
            return q;
        }
        if (firstQuality.wildcard() != second.quality().wildcard()) {
            return firstQuality.wildcard() ? 1 : -1;
        }
        boolean firstImplicitIdentity = firstType == CandidateType.IDENTITY
                && firstQuality.order() == Integer.MAX_VALUE;
        boolean secondImplicitIdentity = second.type() == CandidateType.IDENTITY
                && second.quality().order() == Integer.MAX_VALUE;
        if (firstImplicitIdentity != secondImplicitIdentity) {
            return firstImplicitIdentity ? 1 : -1;
        }
        int clientOrder = Integer.compare(firstQuality.order(), second.quality().order());
        if (clientOrder != 0) {
            return clientOrder;
        }
        int type = Integer.compare(firstType.priority(), second.type().priority());
        if (type != 0) {
            return type;
        }
        return Integer.compare(firstOrder, second.order());
    }

    private static RepresentationCandidate bestCandidate(List<RepresentationCandidate> candidates) {
        RepresentationCandidate best = null;
        for (RepresentationCandidate candidate : candidates) {
            if (best == null || compareCandidates(candidate, best) < 0) {
                best = candidate;
            }
        }
        return best;
    }

    private static void removeCandidates(List<RepresentationCandidate> candidates, SidecarSource sidecar) {
        for (int i = candidates.size() - 1; i >= 0; i--) {
            if (candidates.get(i).sidecar() == sidecar) {
                candidates.remove(i);
            }
        }
    }

    private CachedHandler selectCandidate(List<RepresentationCandidate> staticCandidates,
                                          AcceptEncoding acceptEncoding,
                                          ServerRequest request,
                                          CachedHandler identityHandler,
                                          ResponseRepresentation identityRepresentation,
                                          SidecarCache.Resolver sidecarResolver) throws IOException, URISyntaxException {
        SidecarCache identitySidecarCache = identityHandler.sidecarCache();
        SidecarCache sidecarCache = identitySidecarCache == null ? SidecarCache.disabled() : identitySidecarCache;
        List<RepresentationCandidate> availableStaticCandidates = staticCandidates;
        List<RepresentationCandidate> fallbackStaticCandidates = null;
        RepresentationCandidate bestStaticCandidate;
        CachedHandlerSelection selectedSidecarHandler = null;

        while (true) {
            bestStaticCandidate = bestCandidate(availableStaticCandidates);
            if (bestStaticCandidate == null || bestStaticCandidate.type() != CandidateType.SIDECAR) {
                break;
            }

            SidecarSource sidecar = bestStaticCandidate.sidecar();
            Optional<CachedHandler> resolved;
            try {
                resolved = sidecarCache.resolve(sidecar.coding(), sidecar.suffix(), sidecarResolver);
            } catch (ForbiddenException | IOException | URISyntaxException e) {
                LOGGER.log(Level.TRACE, "Failed to resolve pre-compressed static resource", e);
                removeCandidates(availableStaticCandidates, sidecar);
                continue;
            }
            if (resolved.isEmpty()) {
                removeCandidates(availableStaticCandidates, sidecar);
                continue;
            }

            selectedSidecarHandler = new CachedHandlerSelection(resolved.get(),
                                                                identityHandler,
                                                                sidecarCache,
                                                                sidecar.coding())
                    .withRepresentation(ResponseRepresentation.encoded(bestStaticCandidate.contentEncoding()));
            fallbackStaticCandidates = new ArrayList<>(availableStaticCandidates);
            removeCandidates(fallbackStaticCandidates, sidecar);
            break;
        }

        List<RuntimeEncoding> runtimeEncodings = runtimeEncodings(request, acceptEncoding, bestStaticCandidate);
        RepresentationCandidate selected = bestStaticCandidate;
        if (!runtimeEncodings.isEmpty()) {
            List<RepresentationCandidate> candidates = new ArrayList<>(runtimeEncodings.size() + 1);
            if (bestStaticCandidate != null) {
                candidates.add(bestStaticCandidate);
            }
            for (int i = 0; i < runtimeEncodings.size(); i++) {
                RuntimeEncoding runtimeEncoding = runtimeEncodings.get(i);
                candidates.add(RepresentationCandidate.runtime(runtimeEncoding.quality(),
                                                               i,
                                                               runtimeEncoding.encoder(),
                                                               runtimeEncoding.contentEncoding()));
            }
            selected = bestCandidate(candidates);
        }

        if (selected == null) {
            return new CachedHandlerNotAcceptable(identityHandler, identityRepresentation);
        }

        if (selected.type() == CandidateType.SIDECAR) {
            CachedHandlerSelection sidecarHandler = selectedSidecarHandler;
            List<RepresentationCandidate> remainingCandidates = fallbackStaticCandidates;
            return sidecarHandler.withFallback(() -> {
                try {
                    return selectCandidate(remainingCandidates,
                                           acceptEncoding,
                                           request,
                                           identityHandler,
                                           identityRepresentation,
                                           sidecarResolver);
                } catch (URISyntaxException e) {
                    throw new IOException("Failed to resolve a fallback sidecar", e);
                }
            });
        }
        if (selected.type() == CandidateType.RUNTIME) {
            return identityHandler.withRepresentation(ResponseRepresentation.runtime(selected.contentEncoding(),
                                                                                    selected.encoder()));
        }
        return identityHandler.withRepresentation(identityRepresentation);
    }

    private enum CandidateType {
        SIDECAR(0),
        RUNTIME(1),
        IDENTITY(2);

        private final int priority;

        CandidateType(int priority) {
            this.priority = priority;
        }

        int priority() {
            return priority;
        }
    }

    @FunctionalInterface
    private interface SidecarFallback {
        CachedHandler handler() throws IOException;
    }

    private record SidecarSource(String coding, String suffix) {
    }

    private record RepresentationCandidate(CandidateType type,
                                           AcceptEncoding.CodingQuality quality,
                                           int order,
                                           ContentEncoder encoder,
                                           String contentEncoding,
                                           SidecarSource sidecar) {
        private static RepresentationCandidate identity(AcceptEncoding.CodingQuality quality) {
            return new RepresentationCandidate(CandidateType.IDENTITY, quality, 0, null, null, null);
        }

        private static RepresentationCandidate sidecar(AcceptEncoding.CodingQuality quality,
                                                       int order,
                                                       SidecarSource sidecar) {
            return new RepresentationCandidate(CandidateType.SIDECAR,
                                               quality,
                                               order,
                                               null,
                                               quality.coding(),
                                               sidecar);
        }

        private static RepresentationCandidate runtime(AcceptEncoding.CodingQuality quality,
                                                       int order,
                                                       ContentEncoder encoder,
                                                       String contentEncoding) {
            return new RepresentationCandidate(CandidateType.RUNTIME,
                                               quality,
                                               order,
                                               encoder,
                                               contentEncoding,
                                               null);
        }
    }

    private record RuntimeEncoding(AcceptEncoding.CodingQuality quality, ContentEncoder encoder, String contentEncoding) {
    }

    private record CachedHandlerSelection(CachedHandler delegate,
                                          CachedHandler identityHandler,
                                          SidecarCache sidecarCache,
                                          String coding,
                                          SidecarFallback fallback,
                                          ResponseRepresentation representation)
            implements CachedHandler {
        private CachedHandlerSelection(CachedHandler delegate,
                                       CachedHandler identityHandler,
                                       SidecarCache sidecarCache,
                                       String coding) {
            this(delegate,
                 identityHandler,
                 sidecarCache,
                 coding,
                 () -> identityHandler,
                 ResponseRepresentation.plain());
        }

        @Override
        public Optional<PreparedContent> prepare(LruCache<String, CachedHandler> cache,
                                                 String requestedResource) throws IOException {
            if (!identityHandler.available()) {
                cache.remove(requestedResource);
                return Optional.empty();
            }
            try {
                Optional<PreparedContent> prepared = delegate.prepareSidecar(sidecarCache,
                                                                              coding,
                                                                              cache,
                                                                              requestedResource);
                if (prepared.isPresent()) {
                    return prepared.map(content -> content.withRepresentation(representation)
                            .withBodyOpenFailureFallback(failure -> {
                                if (failure instanceof IOException ioException) {
                                    LOGGER.log(Level.TRACE, "Failed to open pre-compressed static resource", ioException);
                                }
                                sidecarCache.remove(coding);
                                return fallback.handler().prepare(cache, requestedResource);
                            }));
                }
            } catch (ForbiddenException e) {
                // Fall back below, just as when the sidecar disappears between availability and handling.
            } catch (IOException e) {
                LOGGER.log(Level.TRACE, "Failed to prepare pre-compressed static resource", e);
            }
            sidecarCache.remove(coding);
            return fallback.handler().prepare(cache, requestedResource);
        }

        CachedHandlerSelection withFallback(SidecarFallback fallback) {
            return new CachedHandlerSelection(delegate,
                                              identityHandler,
                                              sidecarCache,
                                              coding,
                                              fallback,
                                              representation);
        }

        @Override
        public CachedHandlerSelection withRepresentation(ResponseRepresentation representation) {
            return new CachedHandlerSelection(delegate,
                                              identityHandler,
                                              sidecarCache,
                                              coding,
                                              fallback,
                                              representation);
        }

        @Override
        public boolean available() throws IOException {
            return delegate.available();
        }

        @Override
        public SidecarCache sidecarCache() {
            return delegate.sidecarCache();
        }
    }

    private record CachedHandlerNotAcceptable(CachedHandler identityHandler,
                                              ResponseRepresentation representation) implements CachedHandler {
        @Override
        public Optional<PreparedContent> prepare(LruCache<String, CachedHandler> cache,
                                                 String requestedResource) throws IOException {
            if (!identityHandler.available()) {
                cache.remove(requestedResource);
                return Optional.empty();
            }
            HttpException exception = new HttpException("No acceptable response content encoding",
                                                        Status.NOT_ACCEPTABLE_406,
                                                        true);
            representation.apply(exception);
            throw exception;
        }
    }
}
