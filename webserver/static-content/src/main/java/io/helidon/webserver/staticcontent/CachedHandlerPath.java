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

package io.helidon.webserver.staticcontent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.helidon.common.LruCache;
import io.helidon.common.media.type.MediaType;
import io.helidon.http.ForbiddenException;
import io.helidon.http.Method;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import static io.helidon.webserver.staticcontent.StaticContentHandler.processEtag;
import static io.helidon.webserver.staticcontent.StaticContentHandler.processModifyHeaders;

record CachedHandlerPath(Path path,
                         MediaType mediaType,
                         IoFunction<Path, Optional<Instant>> lastModified,
                         BiConsumer<ServerResponseHeaders, Instant> setLastModifiedHeader,
                         ResponseRepresentation representation,
                         SidecarCache sidecarCache) implements CachedHandler {
    private static final System.Logger LOGGER = System.getLogger(CachedHandlerPath.class.getName());

    CachedHandlerPath(Path path,
                      MediaType mediaType,
                      IoFunction<Path, Optional<Instant>> lastModified,
                      BiConsumer<ServerResponseHeaders, Instant> setLastModifiedHeader) {
        this(path, mediaType, lastModified, setLastModifiedHeader, ResponseRepresentation.plain());
    }

    CachedHandlerPath(Path path,
                      MediaType mediaType,
                      IoFunction<Path, Optional<Instant>> lastModified,
                      BiConsumer<ServerResponseHeaders, Instant> setLastModifiedHeader,
                      ResponseRepresentation representation) {
        this(path, mediaType, lastModified, setLastModifiedHeader, representation, SidecarCache.create());
    }

    @Override
    public boolean handle(LruCache<String, CachedHandler> cache,
                          Method method,
                          ServerRequest request,
                          ServerResponse response,
                          String requestedResource) throws IOException {

        return handle(method, request, response, cache::remove, requestedResource);
    }

    @Override
    public boolean handleSidecar(SidecarCache sidecarCache,
                                 String coding,
                                 LruCache<String, CachedHandler> cache,
                                 Method method,
                                 ServerRequest request,
                                 ServerResponse response,
                                 String requestedResource) throws IOException {
        return handle(method, request, response, resource -> sidecarCache.remove(coding), requestedResource);
    }

    private boolean handle(Method method,
                           ServerRequest request,
                           ServerResponse response,
                           Consumer<String> invalidate,
                           String requestedResource) throws IOException {
        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            LOGGER.log(System.Logger.Level.TRACE, "Sending static content from path: " + path);
        }

        ensureAvailable(invalidate, requestedResource);

        Instant lastModified;
        try {
            lastModified = lastModified().apply(path).orElse(null);
        } catch (IOException e) {
            throwForbiddenIfUnavailable(invalidate, requestedResource, e);
            throw e;
        }
        Long contentLength = null;

        // etag etc.
        String etag = null;
        if (lastModified != null) {
            long etagContentLength = -1;
            if (representation.etagRequiresContentLength()) {
                contentLength = FileBasedContentHandler.contentLength(path);
                etagContentLength = contentLength;
            }
            etag = representation.etag(String.valueOf(lastModified.toEpochMilli()), etagContentLength);
            try {
                boolean ifNoneMatchPresent = processEtag(etag, representation.weakEtag(), request.headers(), response.headers());
                processModifyHeaders(lastModified,
                                     request.headers(),
                                     response.headers(),
                                     setLastModifiedHeader(),
                                     !ifNoneMatchPresent);
            } catch (io.helidon.http.HttpException e) {
                representation.apply(e);
                e.header(representation.etagHeader(etag));
                throw e;
            }
        }

        response.headers().contentType(mediaType);

        if (method == Method.GET) {
            FileBasedContentHandler.send(request, response, path, representation, contentLength, etag, lastModified);
        } else {
            representation.apply(response);
            if (!representation.runtimeEncoded()) {
                FileBasedContentHandler.processContentLength(FileBasedContentHandler.contentLength(path, contentLength),
                                                            response.headers());
            }
            response.send();
        }

        return true;
    }

    @Override
    public CachedHandler withRepresentation(ResponseRepresentation representation) {
        return new CachedHandlerPath(path, mediaType, lastModified, setLastModifiedHeader, representation, sidecarCache);
    }

    @Override
    public boolean available() throws IOException {
        return isAvailable();
    }

    @Override
    public SidecarCache sidecarCache() {
        return sidecarCache;
    }

    private void ensureAvailable(Consumer<String> invalidate, String requestedResource) {
        if (!isAvailable()) {
            invalidate.accept(requestedResource);
            throw new ForbiddenException("File is not accessible");
        }
    }

    private void throwForbiddenIfUnavailable(Consumer<String> invalidate,
                                             String requestedResource,
                                             IOException cause) {
        if (!isAvailable()) {
            invalidate.accept(requestedResource);
            throw new ForbiddenException("File is not accessible", cause);
        }
    }

    private boolean isAvailable() {
        try {
            return Files.exists(path) && Files.isRegularFile(path) && Files.isReadable(path) && !Files.isHidden(path);
        } catch (IOException e) {
            return false;
        }
    }
}
