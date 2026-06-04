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
import java.io.InputStream;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.function.BiConsumer;

import io.helidon.common.LruCache;
import io.helidon.common.media.type.MediaType;
import io.helidon.http.ForbiddenException;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import static io.helidon.webserver.staticcontent.StaticContentHandler.formatLastModified;
import static io.helidon.webserver.staticcontent.StaticContentHandler.processEtag;
import static io.helidon.webserver.staticcontent.StaticContentHandler.processModifyHeaders;

/**
 * Handles a jar file entry.
 * The entry may be extracted into a temporary file (optional).
 */
class CachedHandlerJar implements CachedHandler {
    private static final System.Logger LOGGER = System.getLogger(CachedHandlerJar.class.getName());
    private final MediaType mediaType;
    private final Header contentLength;
    private final Instant lastModified;
    private final BiConsumer<ServerResponseHeaders, Instant> setLastModifiedHeader;
    private final Path path;
    private final URL url;
    private final long contentLengthValue;
    private final ResponseRepresentation representation;
    private final SidecarCache sidecarCache;

    private CachedHandlerJar(MediaType mediaType,
                             URL url,
                             long contentLength,
                             Instant lastModified,
                             BiConsumer<ServerResponseHeaders, Instant> setLastModifiedHeader,
                             Path path,
                             ResponseRepresentation representation,
                             SidecarCache sidecarCache) {
        this.mediaType = mediaType;
        this.url = url;
        this.contentLength = contentLength >= 0
                ? HeaderValues.create(HeaderNames.CONTENT_LENGTH, true, false, contentLength)
                : null;
        this.lastModified = lastModified;
        this.setLastModifiedHeader = setLastModifiedHeader;
        this.path = path;
        this.contentLengthValue = contentLength;
        this.representation = representation;
        this.sidecarCache = sidecarCache;
    }

    static CachedHandlerJar create(TemporaryStorage tmpStorage,
                                   URL fileUrl,
                                   Instant lastModified,
                                   MediaType mediaType,
                                   long contentLength) {
        return create(tmpStorage, fileUrl, lastModified, mediaType, contentLength, ResponseRepresentation.plain());
    }

    static CachedHandlerJar create(TemporaryStorage tmpStorage,
                                   URL fileUrl,
                                   Instant lastModified,
                                   MediaType mediaType,
                                   long contentLength,
                                   ResponseRepresentation representation) {

        BiConsumer<ServerResponseHeaders, Instant> headerHandler = headerHandler(lastModified);
        SidecarCache sidecarCache = SidecarCache.create();

        var createdTmpFile = tmpStorage.createFile();
        if (createdTmpFile.isPresent()) {
            // extract entry
            Path tmpFile = createdTmpFile.get();
            boolean extracted = false;
            long extractedContentLength = contentLength;
            try (InputStream is = ResourceConnections.openStream(fileUrl)) {
                Files.copy(is, tmpFile, StandardCopyOption.REPLACE_EXISTING);
                long tmpFileContentLength = Files.size(tmpFile);
                extracted = contentLength < 0 || tmpFileContentLength == contentLength;
                extractedContentLength = tmpFileContentLength;
            } catch (IOException e) {
                // silently consume the exception, as the tmp file may have been removed, we may throw when reading the file
                LOGGER.log(Level.TRACE, "Failed to create temporary extracted file for " + fileUrl, e);
            }
            if (extracted) {
                return new CachedHandlerJar(mediaType,
                                            fileUrl,
                                            extractedContentLength,
                                            lastModified,
                                            headerHandler,
                                            tmpFile,
                                            representation,
                                            sidecarCache);
            }
            try {
                Files.deleteIfExists(tmpFile);
            } catch (IOException e) {
                LOGGER.log(Level.TRACE, "Failed to delete incomplete temporary extracted file for " + fileUrl, e);
            }
        } else {
            // use the entry always
        }
        return new CachedHandlerJar(mediaType,
                                    fileUrl,
                                    contentLength,
                                    lastModified,
                                    headerHandler,
                                    null,
                                    representation,
                                    sidecarCache);
    }

    @Override
    public boolean handle(LruCache<String, CachedHandler> cache,
                          Method method,
                          ServerRequest request,
                          ServerResponse response,
                          String requestedResource) throws IOException {

        if (LOGGER.isLoggable(Level.TRACE)) {
            LOGGER.log(Level.TRACE, "Sending static content from jar: " + requestedResource);
        }

        // etag etc.
        String etag = null;
        if (lastModified != null) {
            etag = representation.etag(String.valueOf(lastModified.toEpochMilli()), contentLengthValue);
            try {
                boolean ifNoneMatchPresent = processEtag(etag, representation.weakEtag(), request.headers(), response.headers());
                processModifyHeaders(lastModified,
                                     request.headers(),
                                     response.headers(),
                                     setLastModifiedHeader,
                                     !ifNoneMatchPresent);
            } catch (io.helidon.http.HttpException e) {
                representation.apply(e);
                e.header(representation.etagHeader(etag));
                throw e;
            }
        }

        response.headers().contentType(mediaType);

        if (method == Method.GET) {
            try {
                if (path != null && Files.exists(path)) {
                    Long knownContentLength = contentLengthValue >= 0 ? contentLengthValue : null;
                    FileBasedContentHandler.send(request, response, path, representation, knownContentLength, etag, lastModified);
                    return true;
                }
            } catch (IOException | ForbiddenException e) {
                if (LOGGER.isLoggable(Level.TRACE)) {
                    LOGGER.log(Level.TRACE, "Failed to send jar entry from extracted path: " + path
                                       + ", will send directly from jar",
                               e);
                }
            }
            try (var in = ResourceConnections.openStream(url)) {
                representation.apply(response);
                try (var out = representation.outputStream(response.outputStream())) {
                    // no support for ranges when using jar stream
                    in.transferTo(out);
                }
            }
        } else {
            representation.apply(response);
            if (!representation.runtimeEncoded() && contentLength != null) {
                response.headers().set(contentLength);
            }
            response.send();
        }

        return true;
    }

    @Override
    public CachedHandler withRepresentation(ResponseRepresentation representation) {
        return new CachedHandlerJar(mediaType,
                                    url,
                                    contentLengthValue,
                                    lastModified,
                                    setLastModifiedHeader,
                                    path,
                                    representation,
                                    sidecarCache);
    }

    @Override
    public SidecarCache sidecarCache() {
        return sidecarCache;
    }

    private static BiConsumer<ServerResponseHeaders, Instant> headerHandler(Instant lastModified) {
        if (lastModified == null) {
            return (headers, instant) -> {
            };
        }
        Header instantHeader = HeaderValues.create(HeaderNames.LAST_MODIFIED,
                                                   true,
                                                   false,
                                                   formatLastModified(lastModified));
        return (headers, instant) -> headers.set(instantHeader);
    }
}
