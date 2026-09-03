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
import java.util.Optional;

import io.helidon.common.LruCache;
import io.helidon.http.Method;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

interface CachedHandler {
    default boolean handle(LruCache<String, CachedHandler> cache,
                           Method method,
                           ServerRequest request,
                           ServerResponse response,
                           String requestedResource) throws IOException {
        Optional<PreparedContent> prepared = prepare(cache, requestedResource);
        if (prepared.isEmpty()) {
            return false;
        }
        return StaticContentResponse.send(method, request, response, prepared.get());
    }

    Optional<PreparedContent> prepare(LruCache<String, CachedHandler> cache,
                                      String requestedResource) throws IOException;

    default Optional<PreparedContent> prepareSidecar(SidecarCache sidecarCache,
                                                     String coding,
                                                     LruCache<String, CachedHandler> cache,
                                                     String requestedResource) throws IOException {
        return prepare(cache, requestedResource);
    }

    default CachedHandler withRepresentation(ResponseRepresentation representation) {
        return new CachedHandlerRepresentation(this, representation);
    }

    default boolean available() throws IOException {
        return true;
    }

    default SidecarCache sidecarCache() {
        return SidecarCache.disabled();
    }
}
