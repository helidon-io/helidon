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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.helidon.common.LruCache;
import io.helidon.http.Method;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

/**
 * Per-resource sidecar lookup cache.
 */
final class SidecarCache {
    private static final SidecarCache DISABLED = new SidecarCache(null);

    private final ConcurrentMap<String, CachedHandler> entries;

    private SidecarCache(ConcurrentMap<String, CachedHandler> entries) {
        this.entries = entries;
    }

    static SidecarCache create() {
        return new SidecarCache(new ConcurrentHashMap<>());
    }

    static SidecarCache disabled() {
        return DISABLED;
    }

    CachedHandler get(String coding) {
        if (entries == null) {
            return null;
        }
        return entries.get(coding);
    }

    void put(String coding, CachedHandler handler) {
        if (entries != null) {
            entries.put(coding, handler);
        }
    }

    void putMissing(String coding) {
        put(coding, MissingHandler.INSTANCE);
    }

    void remove(String coding) {
        if (entries != null) {
            entries.remove(coding);
        }
    }

    boolean missing(CachedHandler handler) {
        return handler == MissingHandler.INSTANCE;
    }

    private enum MissingHandler implements CachedHandler {
        INSTANCE;

        @Override
        public boolean handle(LruCache<String, CachedHandler> cache,
                              Method method,
                              ServerRequest request,
                              ServerResponse response,
                              String requestedResource) {
            throw new IllegalStateException("Missing sidecar marker must not be used as a handler");
        }
    }
}
