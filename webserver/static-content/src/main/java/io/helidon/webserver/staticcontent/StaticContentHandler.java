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
import io.helidon.http.BadRequestException;
import io.helidon.http.DateTime;
import io.helidon.http.ForbiddenException;
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
import io.helidon.http.encoding.AcceptEncoding;
import io.helidon.http.encoding.ContentEncoder;
import io.helidon.http.encoding.ContentEncoding;
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

    /**
     * Put {@code etag} parameter (if provided ) into the response headers, than validates {@code If-Match} and
     * {@code If-None-Match} headers and react accordingly.
     *
     * @param etag            the proposed ETag. If {@code null} then method returns false
     * @param requestHeaders  an HTTP request headers
     * @param responseHeaders an HTTP response headers
     * @return whether {@code If-None-Match} was present
     * @throws io.helidon.http.RequestException if ETag is checked
     */
    static boolean processEtag(String etag, ServerRequestHeaders requestHeaders, ServerResponseHeaders responseHeaders) {
        return processEtag(etag, false, requestHeaders, responseHeaders);
    }

    static boolean processEtag(String etag,
                               boolean weak,
                               ServerRequestHeaders requestHeaders,
                               ServerResponseHeaders responseHeaders) {
        if (etag == null || etag.isEmpty()) {
            return false;
        }
        etag = unquoteETag(etag);

        Header newEtag = HeaderValues.create(HeaderNames.ETAG, true, false, (weak ? "W/" : "") + '"' + etag + '"');
        // Put ETag into the response
        responseHeaders.set(newEtag);

        // Process If-None-Match header
        boolean ifNoneMatchPresent = requestHeaders.contains(HeaderNames.IF_NONE_MATCH);
        if (ifNoneMatchPresent) {
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
                    boolean ifMatchWeak = isWeakETag(ifMatch);
                    ifMatch = unquoteETag(ifMatch);
                    if ("*".equals(ifMatch) || (!weak && !ifMatchWeak && ifMatch.equals(etag))) {
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
        return ifNoneMatchPresent;
    }

    static void processModifyHeaders(Instant modified,
                                     ServerRequestHeaders requestHeaders,
                                     ServerResponseHeaders responseHeaders,
                                     BiConsumer<ServerResponseHeaders, Instant> setModified) {
        processModifyHeaders(modified, requestHeaders, responseHeaders, setModified, true);
    }

    static void processModifyHeaders(Instant modified,
                                     ServerRequestHeaders requestHeaders,
                                     ServerResponseHeaders responseHeaders,
                                     BiConsumer<ServerResponseHeaders, Instant> setModified,
                                     boolean processIfModifiedSince) {
        if (modified == null) {
            return;
        }

        // Last-Modified
        setModified.accept(responseHeaders, modified);
        if (processIfModifiedSince) {
            // If-Modified-Since
            Optional<Instant> ifModSince = requestHeaders
                    .ifModifiedSince()
                    .map(ChronoZonedDateTime::toInstant);
            if (ifModSince.isPresent() && !ifModSince.get().isBefore(modified)) {
                throw new HttpException("Not valid for If-Modified-Since header", Status.NOT_MODIFIED_304, true);
            }
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

    boolean preCompressedCrossOriginSourcingEnabled() {
        return preCompressedCrossOriginSourcingEnabled;
    }

    CachedHandler selectHandler(CachedHandler identityHandler,
                                ServerRequest request,
                                SidecarCache.Resolver sidecarResolver) throws IOException, URISyntaxException {
        if (!preCompressedEnabled) {
            if (request.headers().contains(HeaderNames.RANGE)) {
                AcceptEncoding acceptEncoding = AcceptEncoding.create(request.headers());
                if (!acceptEncoding.valid()) {
                    throw new BadRequestException("Invalid Accept-Encoding header");
                }
                if (acceptEncoding.identity().isEmpty()) {
                    throw new HttpException("No acceptable response content encoding", Status.NOT_ACCEPTABLE_406, true);
                }
                return identityHandler.withRepresentation(ResponseRepresentation.identity(acceptEncoding.present()));
            }
            return identityHandler;
        }

        AcceptEncoding acceptEncoding = AcceptEncoding.create(request.headers());
        ResponseRepresentation identityRepresentation = ResponseRepresentation.identity(true);
        if (!acceptEncoding.present()) {
            return new CachedHandlerRepresentation(identityHandler, identityRepresentation);
        }
        if (!acceptEncoding.valid()) {
            throw new BadRequestException("Invalid Accept-Encoding header");
        }

        List<RepresentationCandidate> staticCandidates = new ArrayList<>();
        acceptEncoding.identity()
                .ifPresent(quality -> staticCandidates.add(RepresentationCandidate.identity(quality, identityHandler)));
        var listenerContext = request.listenerContext();
        ContentEncodingContext contentEncodingContext = listenerContext == null
                ? null
                : listenerContext.contentEncodingContext();

        int order = 0;
        for (Map.Entry<String, String> entry : preCompressedEncodings.entrySet()) {
            String coding = entry.getKey();
            List<AcceptEncoding.CodingQuality> qualities = sidecarQualities(acceptEncoding,
                                                                            contentEncodingContext,
                                                                            coding,
                                                                            preCompressedEncodings.keySet());
            if (qualities.isEmpty()) {
                order++;
                continue;
            }
            Optional<CachedHandler> sidecar = sidecarHandler(identityHandler,
                                                            coding,
                                                            entry.getValue(),
                                                            sidecarResolver
            );
            int candidateOrder = order;
            sidecar.ifPresent(handler -> {
                for (AcceptEncoding.CodingQuality quality : qualities) {
                    CachedHandler candidateHandler = handler.withRepresentation(ResponseRepresentation.encoded(quality.coding()));
                    staticCandidates.add(RepresentationCandidate.sidecar(quality, candidateHandler, candidateOrder));
                }
            });
            order++;
        }

        return selectCandidate(staticCandidates, acceptEncoding, request, identityHandler, identityRepresentation);
    }

    private static List<AcceptEncoding.CodingQuality> sidecarQualities(AcceptEncoding acceptEncoding,
                                                                       ContentEncodingContext contentEncodingContext,
                                                                       String coding,
                                                                       Set<String> sidecarCodings) {
        List<AcceptEncoding.CodingQuality> result = new ArrayList<>();
        acceptEncoding.match(coding, true).ifPresent(result::add);

        if (contentEncodingContext == null || !contentEncodingContext.contentEncodingEnabled()) {
            return result;
        }

        for (AcceptEncoding.CodingQuality quality : acceptEncoding.acceptedCodings(false)) {
            String acceptedCoding = quality.coding();
            if (coding.equals(acceptedCoding)
                    || sidecarCodings.contains(acceptedCoding)
                    || !contentEncodingContext.contentEncodingSupported(acceptedCoding)) {
                continue;
            }
            var prototype = contentEncodingContext.prototype();
            if (prototype == null) {
                continue;
            }
            ContentEncoding sidecarEncoding = null;
            ContentEncoding acceptedEncoding = null;
            for (ContentEncoding contentEncoding : prototype.contentEncodings()) {
                if (!contentEncoding.supportsEncoding()) {
                    continue;
                }
                for (String id : contentEncoding.ids()) {
                    if (sidecarEncoding == null && coding.equalsIgnoreCase(id)) {
                        sidecarEncoding = contentEncoding;
                    }
                    if (acceptedEncoding == null && acceptedCoding.equalsIgnoreCase(id)) {
                        acceptedEncoding = contentEncoding;
                    }
                }
                if (sidecarEncoding != null && acceptedEncoding != null) {
                    break;
                }
            }
            if (sidecarEncoding != null && sidecarEncoding == acceptedEncoding) {
                result.add(quality);
            }
        }
        return result;
    }

    private CachedHandler selectCandidate(List<RepresentationCandidate> staticCandidates,
                                          AcceptEncoding acceptEncoding,
                                          ServerRequest request,
                                          CachedHandler identityHandler,
                                          ResponseRepresentation identityRepresentation) throws IOException {
        List<RepresentationCandidate> candidates = new ArrayList<>(staticCandidates);
        RepresentationCandidate bestStaticCandidate = staticCandidates.stream()
                .min(StaticContentHandler::compareCandidates)
                .orElse(null);
        List<RuntimeEncoding> runtimeEncodings = runtimeEncodings(request, acceptEncoding, bestStaticCandidate);
        for (int i = 0; i < runtimeEncodings.size(); i++) {
            RuntimeEncoding runtimeEncoding = runtimeEncodings.get(i);
            candidates.add(RepresentationCandidate.runtime(runtimeEncoding.quality(),
                                                           identityHandler,
                                                           i,
                                                           runtimeEncoding.encoder(),
                                                           runtimeEncoding.contentEncoding()));
        }

        if (candidates.isEmpty()) {
            return new CachedHandlerNotAcceptable(identityHandler, identityRepresentation);
        }

        RepresentationCandidate selected = candidates.stream()
                .min(StaticContentHandler::compareCandidates)
                .orElseThrow();

        if (selected.type() == CandidateType.SIDECAR) {
            return ((CachedHandlerSelection) selected.handler()).withFallback(() -> {
                List<RepresentationCandidate> fallbackCandidates = new ArrayList<>(staticCandidates);
                fallbackCandidates.removeIf(candidate -> candidate == selected);
                return selectCandidate(fallbackCandidates, acceptEncoding, request, identityHandler, identityRepresentation);
            });
        }
        if (selected.type() == CandidateType.RUNTIME) {
            return identityHandler.withRepresentation(ResponseRepresentation.runtime(selected.contentEncoding(),
                                                                                    selected.encoder()));
        }
        return identityHandler.withRepresentation(identityRepresentation);
    }

    private Optional<CachedHandler> sidecarHandler(CachedHandler identityHandler,
                                                  String coding,
                                                  String suffix,
                                                  SidecarCache.Resolver resolver) throws IOException, URISyntaxException {
        SidecarCache identitySidecarCache = identityHandler.sidecarCache();
        SidecarCache sidecarCache = identitySidecarCache == null ? SidecarCache.disabled() : identitySidecarCache;
        Optional<CachedHandler> resolved = sidecarCache.resolve(coding, suffix, resolver);
        return resolved.map(handler -> new CachedHandlerSelection(handler, identityHandler, sidecarCache, coding));
    }

    static String sidecarMemoryCacheKey(String requestedResource, String coding) {
        return SIDECAR_MEMORY_CACHE_PREFIX + coding + '\u0000' + requestedResource;
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
        }
        return false;
    }

    private static boolean runtimeCanBeatStatic(AcceptEncoding.CodingQuality quality,
                                                RepresentationCandidate bestStaticCandidate) {
        if (bestStaticCandidate == null) {
            return true;
        }
        RepresentationCandidate runtimeCandidate = RepresentationCandidate.runtime(quality, null, 0, null, quality.coding());
        return compareCandidates(runtimeCandidate, bestStaticCandidate) < 0;
    }

    private static int compareCandidates(RepresentationCandidate first, RepresentationCandidate second) {
        int q = Double.compare(second.quality().q(), first.quality().q());
        if (q != 0) {
            return q;
        }
        if (first.quality().wildcard() != second.quality().wildcard()) {
            return first.quality().wildcard() ? 1 : -1;
        }
        boolean firstImplicitIdentity = implicitIdentity(first);
        boolean secondImplicitIdentity = implicitIdentity(second);
        if (firstImplicitIdentity != secondImplicitIdentity) {
            return firstImplicitIdentity ? 1 : -1;
        }
        int clientOrder = Integer.compare(first.quality().order(), second.quality().order());
        if (clientOrder != 0) {
            return clientOrder;
        }
        int type = Integer.compare(first.type().priority(), second.type().priority());
        if (type != 0) {
            return type;
        }
        return Integer.compare(first.order(), second.order());
    }

    private static boolean implicitIdentity(RepresentationCandidate candidate) {
        return candidate.type() == CandidateType.IDENTITY && candidate.quality().order() == Integer.MAX_VALUE;
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
                                                         (headers, _) -> headers.set(lastModifiedHeader),
                                                         bytes,
                                                         contentLength,
                                                         contentLengthHeader);
        }

        cacheInMemory(resource, inMemoryResource);
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

    private record RepresentationCandidate(CandidateType type,
                                           AcceptEncoding.CodingQuality quality,
                                           CachedHandler handler,
                                           int order,
                                           ContentEncoder encoder,
                                           String contentEncoding) {
        private static RepresentationCandidate identity(AcceptEncoding.CodingQuality quality, CachedHandler handler) {
            return new RepresentationCandidate(CandidateType.IDENTITY, quality, handler, 0, null, null);
        }

        private static RepresentationCandidate sidecar(AcceptEncoding.CodingQuality quality,
                                                       CachedHandler handler,
                                                       int order) {
            return new RepresentationCandidate(CandidateType.SIDECAR, quality, handler, order, null, null);
        }

        private static RepresentationCandidate runtime(AcceptEncoding.CodingQuality quality,
                                                       CachedHandler handler,
                                                       int order,
                                                       ContentEncoder encoder,
                                                       String contentEncoding) {
            return new RepresentationCandidate(CandidateType.RUNTIME, quality, handler, order, encoder, contentEncoding);
        }
    }

    private record RuntimeEncoding(AcceptEncoding.CodingQuality quality, ContentEncoder encoder, String contentEncoding) {
    }

    @FunctionalInterface
    private interface SidecarFallback {
        CachedHandler handler() throws IOException;
    }

    private record CachedHandlerSelection(CachedHandler delegate,
                                          CachedHandler identityHandler,
                                          SidecarCache sidecarCache,
                                          String coding,
                                          SidecarFallback fallback)
            implements CachedHandler {
        @Override
        public boolean handle(LruCache<String, CachedHandler> cache,
                              Method method,
                              ServerRequest request,
                              ServerResponse response,
                              String requestedResource) throws IOException {
            if (!identityHandler.available()) {
                cache.remove(requestedResource);
                throw new ForbiddenException("Identity resource is not accessible");
            }
            try {
                if (delegate.handleSidecar(sidecarCache, coding, cache, method, request, response, requestedResource)) {
                    return true;
                }
            } catch (ForbiddenException e) {
                // Fall back below, just as when the sidecar disappears between availability and handling.
            }
            sidecarCache.remove(coding);
            clearSelectedRepresentationHeaders(response.headers());
            return fallback.handler().handle(cache, method, request, response, requestedResource);
        }

        private CachedHandlerSelection(CachedHandler delegate,
                                       CachedHandler identityHandler,
                                       SidecarCache sidecarCache,
                                       String coding) {
            this(delegate, identityHandler, sidecarCache, coding, () -> identityHandler);
        }

        CachedHandlerSelection withFallback(SidecarFallback fallback) {
            return new CachedHandlerSelection(delegate, identityHandler, sidecarCache, coding, fallback);
        }

        @Override
        public CachedHandler withRepresentation(ResponseRepresentation representation) {
            return new CachedHandlerSelection(delegate.withRepresentation(representation),
                                              identityHandler,
                                              sidecarCache,
                                              coding,
                                              fallback);
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

    private static void clearSelectedRepresentationHeaders(ServerResponseHeaders headers) {
        headers.remove(HeaderNames.CONTENT_ENCODING);
        headers.remove(HeaderNames.CONTENT_LENGTH);
        headers.remove(HeaderNames.CONTENT_RANGE);
        headers.remove(HeaderNames.CONTENT_TYPE);
        headers.remove(HeaderNames.ETAG);
        headers.remove(HeaderNames.LAST_MODIFIED);
    }

    private record CachedHandlerNotAcceptable(CachedHandler identityHandler,
                                              ResponseRepresentation representation) implements CachedHandler {
        @Override
        public boolean handle(LruCache<String, CachedHandler> cache,
                              Method method,
                              ServerRequest request,
                              ServerResponse response,
                              String requestedResource) throws IOException {
            if (!identityHandler.available()) {
                cache.remove(requestedResource);
                return false;
            }
            representation.apply(response);
            response.status(Status.NOT_ACCEPTABLE_406);
            response.send();
            return true;
        }
    }
}
