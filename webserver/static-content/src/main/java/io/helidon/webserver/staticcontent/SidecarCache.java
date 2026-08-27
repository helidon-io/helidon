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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-resource sidecar lookup cache.
 */
final class SidecarCache {
    private static final SidecarCache DISABLED = new SidecarCache(null);

    private final ConcurrentMap<String, CachedHandler> entries;
    private final ConcurrentMap<String, CompletableFuture<Optional<CachedHandler>>> resolutions = new ConcurrentHashMap<>();

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
            return Optional.of(cachedHandler);
        }

        CompletableFuture<Optional<CachedHandler>> resolution = new CompletableFuture<>();
        CompletableFuture<Optional<CachedHandler>> existing = resolutions.putIfAbsent(coding, resolution);
        if (existing != null) {
            return await(existing);
        }

        try {
            cachedHandler = reusable(coding);
            if (cachedHandler != null) {
                Optional<CachedHandler> resolved = Optional.of(cachedHandler);
                resolution.complete(resolved);
                return resolved;
            }

            Optional<CachedHandler> resolved = resolver.resolve(coding, suffix);
            resolved.ifPresent(handler -> entries.put(coding, handler));
            resolution.complete(resolved);
            return resolved;
        } catch (IOException | URISyntaxException | RuntimeException | Error e) {
            resolution.completeExceptionally(e);
            throw e;
        } finally {
            resolutions.remove(coding, resolution);
        }
    }

    void remove(String coding) {
        if (entries != null) {
            entries.remove(coding);
        }
    }

    private CachedHandler reusable(String coding) {
        return entries.get(coding);
    }

    private static Optional<CachedHandler> await(CompletableFuture<Optional<CachedHandler>> resolution)
            throws IOException, URISyntaxException {
        try {
            return resolution.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            if (cause instanceof URISyntaxException uriSyntaxException) {
                throw uriSyntaxException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Unexpected sidecar resolution failure", cause);
        }
    }

    @FunctionalInterface
    interface Resolver {
        Optional<CachedHandler> resolve(String coding, String suffix) throws IOException, URISyntaxException;
    }
}
