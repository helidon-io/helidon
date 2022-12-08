/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.webserver.staticcontent;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

import io.helidon.common.configurable.LruCache;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpException;
import io.helidon.common.http.ServerRequestHeaders;
import io.helidon.common.http.ServerResponseHeaders;
import io.helidon.common.media.type.MediaType;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

import static io.helidon.nima.webserver.staticcontent.StaticContentHandler.processEtag;
import static io.helidon.nima.webserver.staticcontent.StaticContentHandler.processModifyHeaders;

record CachedHandlerInMemory(MediaType mediaType,
                             Instant lastModified,
                             BiConsumer<ServerResponseHeaders, Instant> setLastModifiedHeader,
                             byte[] bytes,
                             int contentLength,
                             Http.HeaderValue contentLengthHeader) implements CachedHandler {

    @Override
    public boolean handle(LruCache<String, CachedHandler> cache,
                          Http.Method method,
                          ServerRequest request,
                          ServerResponse response,
                          String requestedResource) {
        // etag etc.
        if (lastModified != null) {
            processEtag(String.valueOf(lastModified.toEpochMilli()), request.headers(), response.headers());
            processModifyHeaders(lastModified, request.headers(), response.headers(), setLastModifiedHeader);
        }

        response.headers().contentType(mediaType);

        if (method == Http.Method.GET) {
            send(request, response);
        } else {
            response.headers().set(contentLengthHeader());
            response.send();
        }

        return true;
    }

    private void send(ServerRequest request, ServerResponse response) {
        ServerRequestHeaders headers = request.headers();

        if (headers.contains(Http.Header.RANGE)) {
            long contentLength = contentLength();
            List<ByteRangeRequest> ranges = ByteRangeRequest.parse(request,
                                                                   response,
                                                                   headers.get(Http.Header.RANGE).values(),
                                                                   contentLength);
            if (ranges.size() == 1) {
                // single response
                ByteRangeRequest range = ranges.get(0);

                if (range.offset() > contentLength()) {
                    throw new HttpException("Invalid range offset", Http.Status.REQUESTED_RANGE_NOT_SATISFIABLE_416, true);
                }
                if (range.length() > (contentLength() - range.offset())) {
                    throw new HttpException("Invalid length", Http.Status.REQUESTED_RANGE_NOT_SATISFIABLE_416, true);
                }

                range.setContentRange(response);

                // only send a part of the file
                response.send(Arrays.copyOfRange(bytes(), (int) range.offset(), (int) range.length()));
            } else {
                // not supported, send full
                send(response);
            }
        } else {
            send(response);
        }
    }

    private void send(ServerResponse response) {
        response.headers().set(contentLengthHeader());
        response.send(bytes());
    }
}
