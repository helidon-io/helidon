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

import io.helidon.common.LruCache;
import io.helidon.common.media.type.MediaType;
import io.helidon.http.ForbiddenException;
import io.helidon.http.Method;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import static io.helidon.webserver.staticcontent.StaticContentHandler.processPreconditions;

record CachedHandlerPath(Path path,
                         StaticContentMetadata metadata,
                         boolean followLinks,
                         Path secureRoot) implements CachedHandler {
    private static final System.Logger LOGGER = System.getLogger(CachedHandlerPath.class.getName());

    static CachedHandlerPath create(Path path,
                                    Path resolvedPath,
                                    MediaType mediaType,
                                    boolean followLinks,
                                    Path secureRoot) throws IOException {
        BasicFileAttributes attributes;
        try {
            attributes = FileBasedContentHandler.attributes(resolvedPath, followLinks, secureRoot);
        } catch (IOException e) {
            throw new ForbiddenException("File is not accessible", e);
        }
        if (!attributes.isRegularFile()
                || !Files.isReadable(resolvedPath)
                || Files.isHidden(path)
                || Files.isHidden(resolvedPath)) {
            throw new ForbiddenException("File is not accessible");
        }

        Instant modified = attributes.lastModifiedTime().toInstant();
        return new CachedHandlerPath(resolvedPath,
                                     StaticContentMetadata.create(mediaType, modified, attributes.size()),
                                     followLinks,
                                     secureRoot);
    }

    @Override
    public boolean handle(LruCache<String, CachedHandler> cache,
                          Method method,
                          ServerRequest request,
                          ServerResponse response,
                          String requestedResource) throws IOException {

        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            LOGGER.log(System.Logger.Level.TRACE, "Sending static content from path: " + path);
        }

        // etag etc.
        processPreconditions(metadata, request.headers(), response.headers());

        metadata.setContentType(response.headers());

        if (method == Method.GET) {
            SeekableByteChannel channel = FileBasedContentHandler.newByteChannel(path, followLinks, secureRoot);
            try (SeekableByteChannel openChannel = channel) {
                FileBasedContentHandler.send(request, response, openChannel, metadata);
            }
        } else {
            metadata.setContentLength(response.headers());
            response.send();
        }

        return true;
    }
}
