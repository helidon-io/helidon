/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

import io.helidon.common.configurable.LruCache;
import io.helidon.common.media.type.MediaType;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpException;
import io.helidon.http.Method;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.Status;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import static io.helidon.webserver.staticcontent.StaticContentHandler.processEtag;
import static io.helidon.webserver.staticcontent.StaticContentHandler.processModifyHeaders;

record CachedHandlerInMemory(MediaType mediaType,
                             Instant lastModified,
                             BiConsumer<ServerResponseHeaders, Instant> setLastModifiedHeader,
                             byte[] bytes,
                             int contentLength,
                             Header contentLengthHeader) implements CachedHandler {

    @Override
    public boolean handle(LruCache<String, CachedHandler> cache,
                          Method method,
                          ServerRequest request,
                          ServerResponse response,
                          String requestedResource) {
        // etag etc.
        if (lastModified != null) {
            processEtag(String.valueOf(lastModified.toEpochMilli()), request.headers(), response.headers());
            processModifyHeaders(lastModified, request.headers(), response.headers(), setLastModifiedHeader);
        }

        response.headers().contentType(mediaType);

        if (method == Method.GET) {
            send(request, response);
        } else {
            response.headers().set(contentLengthHeader());
            response.send();
        }

        return true;
    }

    private void send(ServerRequest request, ServerResponse response) {
        ServerRequestHeaders headers = request.headers();

        if (headers.contains(HeaderNames.RANGE)) {
            long contentLength = contentLength();
            List<ByteRangeRequest> ranges = ByteRangeRequest.parse(request,
                                                                   response,
                                                                   headers.get(HeaderNames.RANGE).values(),
                                                                   contentLength);
            if (ranges.size() == 1) {
                // single response
                ByteRangeRequest range = ranges.getFirst();

                if (range.offset() > contentLength()) {
                    throw new HttpException("Invalid range offset", Status.REQUESTED_RANGE_NOT_SATISFIABLE_416, true);
                }
                if (range.length() > (contentLength() - range.offset())) {
                    throw new HttpException("Invalid length", Status.REQUESTED_RANGE_NOT_SATISFIABLE_416, true);
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
