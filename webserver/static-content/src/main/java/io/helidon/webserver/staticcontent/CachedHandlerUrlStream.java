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
import io.helidon.http.ServerResponseHeaders;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

record CachedHandlerUrlStream(MediaType mediaType,
                              URL url,
                              ResponseRepresentation representation,
                              SidecarCache sidecarCache) implements CachedHandler {
    private static final System.Logger LOGGER = System.getLogger(CachedHandlerUrlStream.class.getName());

    CachedHandlerUrlStream(MediaType mediaType, URL url) {
        this(mediaType, url, ResponseRepresentation.plain());
    }

    CachedHandlerUrlStream(MediaType mediaType, URL url, ResponseRepresentation representation) {
        this(mediaType, url, representation, SidecarCache.create());
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

        URLConnection urlConnection = url.openConnection();
        long lastModified = urlConnection.getLastModified();
        long contentLength = urlConnection.getContentLengthLong();

        if (lastModified != 0) {
            String etag = representation.etag(String.valueOf(lastModified), contentLength);
            try {
                boolean ifNoneMatchPresent = StaticContentHandler.processEtag(etag,
                                                                              representation.weakEtag(),
                                                                              request.headers(),
                                                                              response.headers());
                StaticContentHandler.processModifyHeaders(Instant.ofEpochMilli(lastModified),
                                                          request.headers(),
                                                          response.headers(),
                                                          ServerResponseHeaders::lastModified,
                                                          !ifNoneMatchPresent);
            } catch (io.helidon.http.HttpException e) {
                representation.apply(e);
                e.header(representation.etagHeader(etag));
                throw e;
            }
        }

        response.headers().contentType(mediaType);

        if (method == Method.HEAD) {
            representation.apply(response);
            response.send();
            return true;
        }

        try (InputStream in = url.openStream()) {
            representation.apply(response);
            try (OutputStream outputStream = representation.outputStream(response.outputStream())) {
                in.transferTo(outputStream);
            }
        }
        return true;
    }

    @Override
    public CachedHandler withRepresentation(ResponseRepresentation representation) {
        return new CachedHandlerUrlStream(mediaType, url, representation, sidecarCache);
    }

    @Override
    public SidecarCache sidecarCache() {
        return sidecarCache;
    }
}
