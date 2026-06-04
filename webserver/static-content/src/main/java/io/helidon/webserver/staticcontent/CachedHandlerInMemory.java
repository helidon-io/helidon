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
import java.io.OutputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

import io.helidon.common.LruCache;
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
                             Header contentLengthHeader,
                             ResponseRepresentation representation,
                             SidecarCache sidecarCache) implements CachedHandler {

    CachedHandlerInMemory(MediaType mediaType,
                          Instant lastModified,
                          BiConsumer<ServerResponseHeaders, Instant> setLastModifiedHeader,
                          byte[] bytes,
                          int contentLength,
                          Header contentLengthHeader) {
        this(mediaType,
             lastModified,
             setLastModifiedHeader,
             bytes,
             contentLength,
             contentLengthHeader,
             ResponseRepresentation.plain());
    }

    CachedHandlerInMemory(MediaType mediaType,
                          Instant lastModified,
                          BiConsumer<ServerResponseHeaders, Instant> setLastModifiedHeader,
                          byte[] bytes,
                          int contentLength,
                          Header contentLengthHeader,
                          ResponseRepresentation representation) {
        this(mediaType,
             lastModified,
             setLastModifiedHeader,
             bytes,
             contentLength,
             contentLengthHeader,
             representation,
             SidecarCache.create());
    }

    @Override
    public boolean handle(LruCache<String, CachedHandler> cache,
                          Method method,
                          ServerRequest request,
                          ServerResponse response,
                          String requestedResource) throws IOException {
        // etag etc.
        String etag = null;
        if (lastModified != null) {
            etag = representation.etag(String.valueOf(lastModified.toEpochMilli()), contentLength);
            try {
                boolean ifNoneMatchPresent = processEtag(etag, representation.weakEtag(), request.headers(), response.headers());
                processModifyHeaders(lastModified,
                                     request.headers(),
                                     response.headers(),
                                     setLastModifiedHeader,
                                     !ifNoneMatchPresent);
            } catch (HttpException e) {
                representation.apply(e);
                e.header(representation.etagHeader(etag));
                throw e;
            }
        }

        response.headers().contentType(mediaType);

        if (method == Method.GET) {
            if (representation.runtimeEncoded()) {
                representation.apply(response);
                sendRuntimeEncoded(response);
            } else {
                send(request, response, etag);
            }
        } else {
            representation.apply(response);
            if (!representation.runtimeEncoded()) {
                response.headers().set(contentLengthHeader());
            }
            response.send();
        }

        return true;
    }

    @Override
    public CachedHandler withRepresentation(ResponseRepresentation representation) {
        return new CachedHandlerInMemory(mediaType,
                                         lastModified,
                                         setLastModifiedHeader,
                                         bytes,
                                         contentLength,
                                         contentLengthHeader,
                                         representation,
                                         sidecarCache);
    }

    @Override
    public SidecarCache sidecarCache() {
        return sidecarCache;
    }

    private void send(ServerRequest request, ServerResponse response, String etag) {
        ServerRequestHeaders headers = request.headers();

        if (headers.contains(HeaderNames.RANGE)) {
            long contentLength = contentLength();
            try {
                List<ByteRangeRequest> ranges = ByteRangeRequest.parse(request,
                                                                       response,
                                                                       headers.get(HeaderNames.RANGE).values(),
                                                                       contentLength,
                                                                       etag,
                                                                       representation.weakEtag(),
                                                                       lastModified);
                if (ranges.size() == 1) {
                    // single response
                    ByteRangeRequest range = ranges.getFirst();

                    if (range.offset() > contentLength()) {
                        throw new HttpException("Invalid range offset", Status.REQUESTED_RANGE_NOT_SATISFIABLE_416, true);
                    }
                    if (range.length() > (contentLength() - range.offset())) {
                        throw new HttpException("Invalid length", Status.REQUESTED_RANGE_NOT_SATISFIABLE_416, true);
                    }

                    representation.apply(response);
                    range.setContentRange(response);

                    // only send a part of the file
                    response.send(Arrays.copyOfRange(bytes(),
                                                     (int) range.offset(),
                                                     (int) (range.offset() + range.length())));
                } else {
                    // not supported, send full
                    send(response);
                }
            } catch (HttpException e) {
                representation.apply(e);
                throw e;
            }
        } else {
            send(response);
        }
    }

    private void send(ServerResponse response) {
        representation.apply(response);
        response.headers().set(contentLengthHeader());
        response.send(bytes());
    }

    private void sendRuntimeEncoded(ServerResponse response) throws IOException {
        try (OutputStream out = representation.outputStream(response.outputStream())) {
            out.write(bytes);
        }
    }
}
