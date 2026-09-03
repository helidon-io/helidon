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
import java.util.Optional;

import io.helidon.common.LruCache;

record CachedHandlerRepresentation(CachedHandler delegate, ResponseRepresentation representation) implements CachedHandler {
    @Override
    public Optional<PreparedContent> prepare(LruCache<String, CachedHandler> cache,
                                             String requestedResource) throws IOException {
        return delegate.prepare(cache, requestedResource)
                .map(content -> content.withRepresentation(representation));
    }

    @Override
    public Optional<PreparedContent> prepareSidecar(SidecarCache sidecarCache,
                                                    String coding,
                                                    LruCache<String, CachedHandler> cache,
                                                    String requestedResource) throws IOException {
        return delegate.prepareSidecar(sidecarCache, coding, cache, requestedResource)
                .map(content -> content.withRepresentation(representation));
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
