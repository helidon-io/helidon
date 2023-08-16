/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
import java.util.function.BiConsumer;

import io.helidon.common.configurable.LruCache;
import io.helidon.common.media.type.MediaType;
import io.helidon.http.Http;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import static io.helidon.webserver.staticcontent.StaticContentHandler.processEtag;
import static io.helidon.webserver.staticcontent.StaticContentHandler.processModifyHeaders;

record CachedHandlerJar(Path path,
                        MediaType mediaType,
                        Instant lastModified,
                        BiConsumer<ServerResponseHeaders, Instant> setLastModifiedHeader) implements CachedHandler {
    private static final System.Logger LOGGER = System.getLogger(CachedHandlerJar.class.getName());

    @Override
    public boolean handle(LruCache<String, CachedHandler> cache,
                          Http.Method method,
                          ServerRequest request,
                          ServerResponse response,
                          String requestedResource) throws IOException {

        // check if file still exists (the tmp may have been removed, file may have been removed
        // there is still a race change, but we do not want to keep cached records for invalid files
        if (!Files.exists(path)) {
            cache.remove(requestedResource);
            return false;
        }

        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            LOGGER.log(System.Logger.Level.TRACE, "Sending static content from jar: " + requestedResource);
        }

        // etag etc.
        if (lastModified != null) {
            processEtag(String.valueOf(lastModified.toEpochMilli()), request.headers(), response.headers());
            processModifyHeaders(lastModified, request.headers(), response.headers(), setLastModifiedHeader());
        }

        response.headers().contentType(mediaType);

        if (method == Http.Method.GET) {
            FileBasedContentHandler.send(request, response, path);
        } else {
            response.headers().contentLength(FileBasedContentHandler.contentLength(path));
            response.send();
        }

        return true;
    }
}
