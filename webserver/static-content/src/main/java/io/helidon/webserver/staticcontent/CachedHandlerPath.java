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
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

import io.helidon.common.LruCache;
import io.helidon.common.media.type.MediaType;
import io.helidon.http.ForbiddenException;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
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
                         Function<Path, Optional<Path>> pathResolver,
                         boolean followLinks,
                         Function<Path, Optional<Path>> secureRootResolver) implements CachedHandler {
    private static final System.Logger LOGGER = System.getLogger(CachedHandlerPath.class.getName());

    @Override
    public boolean handle(LruCache<String, CachedHandler> cache,
                          Method method,
                          ServerRequest request,
                          ServerResponse response,
                          String requestedResource) throws IOException {

        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            LOGGER.log(System.Logger.Level.TRACE, "Sending static content from path: " + path);
        }

        Optional<Path> resolved = pathResolver.apply(path);
        if (resolved.isEmpty()) {
            cache.remove(requestedResource);
            return false;
        }
        Path resolvedPath = resolved.get();
        Path secureRoot = secureRootResolver.apply(resolvedPath).orElse(null);

        // now it exists and is a file
        BasicFileAttributes attributes;
        try {
            attributes = FileBasedContentHandler.attributes(resolvedPath, followLinks, secureRoot);
        } catch (IOException e) {
            cache.remove(requestedResource);
            throw new ForbiddenException("File is not accessible", e);
        }
        if (!attributes.isRegularFile()
                || !Files.isReadable(resolvedPath)
                || Files.isHidden(path)
                || Files.isHidden(resolvedPath)) {
            // check if file still exists (the tmp may have been removed, file may have been removed
            // there is still a race change, but we do not want to keep cached records for invalid files
            cache.remove(requestedResource);
            throw new ForbiddenException("File is not accessible");
        }

        Instant lastModified;
        try {
            if (followLinks && secureRoot == null) {
                lastModified = lastModified().apply(resolvedPath).orElse(null);
            } else {
                lastModified = FileBasedContentHandler.lastModified(resolvedPath, followLinks, secureRoot).orElse(null);
            }
        } catch (IOException e) {
            cache.remove(requestedResource);
            throw new ForbiddenException("File is not accessible", e);
        }

        // etag etc.
        if (lastModified != null) {
            processEtag(String.valueOf(lastModified.toEpochMilli()), request.headers(), response.headers());
            processModifyHeaders(lastModified, request.headers(), response.headers(), setLastModifiedHeader());
        }

        response.headers().contentType(mediaType);

        if (method == Method.GET) {
            SeekableByteChannel channel;
            try {
                channel = FileBasedContentHandler.newByteChannel(resolvedPath, followLinks, secureRoot);
            } catch (IOException e) {
                cache.remove(requestedResource);
                throw new ForbiddenException("File is not accessible", e);
            }
            try (SeekableByteChannel openChannel = channel) {
                FileBasedContentHandler.send(request, response, openChannel);
            }
        } else {
            try {
                long contentLength;
                if (!followLinks || secureRoot != null) {
                    try (SeekableByteChannel channel = FileBasedContentHandler.newByteChannel(resolvedPath,
                                                                                              followLinks,
                                                                                              secureRoot)) {
                        contentLength = channel.size();
                    }
                } else {
                    contentLength = Files.size(resolvedPath);
                }
                response.headers().set(HeaderValues.create(HeaderNames.CONTENT_LENGTH, contentLength));
            } catch (IOException e) {
                cache.remove(requestedResource);
                throw new ForbiddenException("File is not accessible", e);
            }
            response.send();
        }

        return true;
    }
}
