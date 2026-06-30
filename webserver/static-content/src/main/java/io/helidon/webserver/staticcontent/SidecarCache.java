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
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

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
    private final ReentrantLock resolutionLock = new ReentrantLock();

    private SidecarCache(ConcurrentMap<String, CachedHandler> entries) {
        this.entries = entries;
    }

    static SidecarCache create() {
        return new SidecarCache(new ConcurrentHashMap<>());
    }

    static SidecarCache disabled() {
        return DISABLED;
    }

    Optional<CachedHandler> resolve(String coding, String suffix, Resolver resolver)
            throws IOException, URISyntaxException {
        if (entries == null) {
            return resolver.resolve(coding, suffix);
        }

        CachedHandler cachedHandler = reusable(coding);
        if (cachedHandler != null) {
            return result(cachedHandler);
        }

        resolutionLock.lock();
        try {
            cachedHandler = reusable(coding);
            if (cachedHandler != null) {
                return result(cachedHandler);
            }

            Optional<CachedHandler> resolved = resolver.resolve(coding, suffix);
            entries.put(coding, resolved.orElse(MissingHandler.INSTANCE));
            return resolved;
        } finally {
            resolutionLock.unlock();
        }
    }

    void remove(String coding) {
        if (entries != null) {
            entries.remove(coding);
        }
    }

    private CachedHandler reusable(String coding) throws IOException {
        CachedHandler cachedHandler = entries.get(coding);
        if (cachedHandler == null
                || cachedHandler == MissingHandler.INSTANCE
                || cachedHandler.available()) {
            return cachedHandler;
        }
        return null;
    }

    private static Optional<CachedHandler> result(CachedHandler handler) {
        return handler == MissingHandler.INSTANCE ? Optional.empty() : Optional.of(handler);
    }

    @FunctionalInterface
    interface Resolver {
        Optional<CachedHandler> resolve(String coding, String suffix) throws IOException, URISyntaxException;
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
