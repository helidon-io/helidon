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
import java.net.URL;
import java.net.URLConnection;
import java.time.Instant;
import java.util.Optional;

import io.helidon.common.LruCache;
import io.helidon.common.media.type.MediaType;

record CachedHandlerUrlStream(URL url,
                              StaticContentMetadata metadata,
                              SidecarCache sidecarCache) implements CachedHandler {
    private static final System.Logger LOGGER = System.getLogger(CachedHandlerUrlStream.class.getName());

    static CachedHandlerUrlStream create(MediaType mediaType, URL url) throws IOException {
        URLConnection urlConnection = ResourceConnections.openConnection(url);
        long lastModified = urlConnection.getLastModified();
        long contentLength = urlConnection.getContentLengthLong();
        StaticContentMetadata metadata = lastModified == 0
                ? StaticContentMetadata.create(mediaType, contentLength)
                : StaticContentMetadata.create(mediaType, Instant.ofEpochMilli(lastModified), contentLength);
        return new CachedHandlerUrlStream(url, metadata, SidecarCache.create());
    }

    @Override
    public Optional<PreparedContent> prepare(LruCache<String, CachedHandler> cache,
                                             String requestedResource) throws IOException {
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG, "Sending static content using stream from classpath: " + url);
        }

        return Optional.of(new PreparedContent(metadata,
                                               null,
                                               () -> PreparedContent.stream(ResourceConnections.openStream(url))));
    }

    @Override
    public SidecarCache sidecarCache() {
        return sidecarCache;
    }
}
