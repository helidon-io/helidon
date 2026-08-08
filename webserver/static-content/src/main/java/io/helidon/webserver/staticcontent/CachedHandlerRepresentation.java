/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import io.helidon.common.LruCache;
import io.helidon.http.HttpException;
import io.helidon.http.Method;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

record CachedHandlerRepresentation(CachedHandler delegate, ResponseRepresentation representation) implements CachedHandler {
    @Override
    public boolean handle(LruCache<String, CachedHandler> cache,
                          Method method,
                          ServerRequest request,
                          ServerResponse response,
                          String requestedResource) throws IOException {
        representation.apply(response);
        try {
            return delegate.handle(cache, method, request, response, requestedResource);
        } catch (HttpException e) {
            representation.apply(e);
            throw e;
        }
    }

    @Override
    public boolean handleSidecar(SidecarCache sidecarCache,
                                 String coding,
                                 LruCache<String, CachedHandler> cache,
                                 Method method,
                                 ServerRequest request,
                                 ServerResponse response,
                                 String requestedResource) throws IOException {
        representation.apply(response);
        try {
            return delegate.handleSidecar(sidecarCache, coding, cache, method, request, response, requestedResource);
        } catch (HttpException e) {
            representation.apply(e);
            throw e;
        }
    }

    @Override
    public SidecarCache sidecarCache() {
        return delegate.sidecarCache();
    }

    @Override
    public boolean available() throws IOException {
        return delegate.available();
    }
}
