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
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.time.Instant;

import io.helidon.common.LruCache;
import io.helidon.common.media.type.MediaType;
import io.helidon.http.Method;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

record CachedHandlerUrlStream(URL url, StaticContentMetadata metadata) implements CachedHandler {
    private static final System.Logger LOGGER = System.getLogger(CachedHandlerUrlStream.class.getName());

    static CachedHandlerUrlStream create(MediaType mediaType, URL url) throws IOException {
        URLConnection urlConnection = url.openConnection();
        long lastModified = urlConnection.getLastModified();
        long contentLength = urlConnection.getContentLengthLong();
        StaticContentMetadata metadata = lastModified == 0
                ? StaticContentMetadata.create(mediaType, contentLength)
                : StaticContentMetadata.create(mediaType, Instant.ofEpochMilli(lastModified), contentLength);
        return new CachedHandlerUrlStream(url,
                                          metadata);
    }

    @Override
    public boolean handle(LruCache<String, CachedHandler> cache,
                          Method method,
                          ServerRequest request,
                          ServerResponse response,
                          String requestedResource) throws IOException {

        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG, "Sending static content using stream from classpath: " + url);
        }

        StaticContentHandler.processPreconditions(metadata, request.headers(), response.headers());

        metadata.setContentType(response.headers());

        if (method == Method.HEAD) {
            metadata.setContentLength(response.headers());
            response.send();
            return true;
        }

        try (InputStream in = url.openStream()) {
            metadata.setContentLength(response.headers());
            try (OutputStream outputStream = response.outputStream()) {
                in.transferTo(outputStream);
            }
        }
        return true;
    }
}
