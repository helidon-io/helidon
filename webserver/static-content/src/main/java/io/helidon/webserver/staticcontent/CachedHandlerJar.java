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

import io.helidon.common.LruCache;
import io.helidon.common.media.type.MediaType;
import io.helidon.http.Method;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import static io.helidon.webserver.staticcontent.StaticContentHandler.processPreconditions;

/**
 * Handles a jar file entry.
 * The entry may be extracted into a temporary file (optional).
 */
class CachedHandlerJar implements CachedHandler {
    private static final System.Logger LOGGER = System.getLogger(CachedHandlerJar.class.getName());
    private final StaticContentMetadata metadata;
    private final Path path;
    private final URL url;

    private CachedHandlerJar(MediaType mediaType,
                             URL url,
                             long contentLength,
                             Instant lastModified,
                             Path path) {
        this.url = url;
        this.metadata = StaticContentMetadata.create(mediaType, lastModified, contentLength);
        this.path = path;
    }

    static CachedHandlerJar create(TemporaryStorage tmpStorage,
                                   URL fileUrl,
                                   Instant lastModified,
                                   MediaType mediaType,
                                   long contentLength) {

        var createdTmpFile = tmpStorage.createFile();
        if (createdTmpFile.isPresent()) {
            // extract entry
            Path tmpFile = createdTmpFile.get();
            try (InputStream is = fileUrl.openStream()) {
                long extractedLength = Files.copy(is, tmpFile, StandardCopyOption.REPLACE_EXISTING);
                return new CachedHandlerJar(mediaType,
                                            fileUrl,
                                            extractedLength,
                                            lastModified,
                                            tmpFile);
            } catch (IOException e) {
                LOGGER.log(Level.TRACE, "Failed to create temporary extracted file for " + fileUrl, e);
            }
        }
        // use the entry always
        return new CachedHandlerJar(mediaType,
                                    fileUrl,
                                    contentLength,
                                    lastModified,
                                    null);
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
        processPreconditions(metadata, request.headers(), response.headers());

        metadata.setContentType(response.headers());

        if (method == Method.GET) {
            try {
                if (path != null && Files.exists(path)) {
                    try (var channel = Files.newByteChannel(path)) {
                        FileBasedContentHandler.send(request, response, channel, metadata);
                    }
                    return true;
                }
            } catch (IOException e) {
                if (LOGGER.isLoggable(Level.TRACE)) {
                    LOGGER.log(Level.TRACE, "Failed to send jar entry from extracted path: " + path
                                       + ", will send directly from jar",
                               e);
                }
            }
            try (var in = url.openStream()) {
                // no support for ranges when using jar stream
                metadata.setContentLength(response.headers());
                try (var out = response.outputStream()) {
                    in.transferTo(out);
                }
            }
        } else {
            metadata.setContentLength(response.headers());
            response.send();
        }

        return true;
    }
}
