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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.time.Instant;

import io.helidon.common.configurable.LruCache;
import io.helidon.common.media.type.MediaType;
import io.helidon.http.Method;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

record CachedHandlerUrlStream(MediaType mediaType, URL url) implements CachedHandler {
    private static final System.Logger LOGGER = System.getLogger(CachedHandlerUrlStream.class.getName());

    @Override
    public boolean handle(LruCache<String, CachedHandler> cache,
                          Method method,
                          ServerRequest request,
                          ServerResponse response,
                          String requestedResource) throws IOException {

        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG, "Sending static content using stream from classpath: " + url);
        }

        URLConnection urlConnection = url.openConnection();
        long lastModified = urlConnection.getLastModified();

        if (lastModified != 0) {
            StaticContentHandler.processEtag(String.valueOf(lastModified), request.headers(), response.headers());
            StaticContentHandler.processModifyHeaders(Instant.ofEpochMilli(lastModified), request.headers(), response.headers());
        }

        response.headers().contentType(mediaType);

        if (method == Method.HEAD) {
            response.send();
            return true;
        }

        try (InputStream in = url.openStream(); OutputStream outputStream = response.outputStream()) {
            in.transferTo(outputStream);
        }
        return true;
    }
}
