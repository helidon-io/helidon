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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Per-resource sidecar lookup cache.
 */
final class SidecarCache {
    private static final SidecarCache DISABLED = new SidecarCache(null);

    private final ConcurrentMap<String, CachedHandler> entries;
    private final ConcurrentMap<String, AtomicReference<CompletableFuture<Optional<CachedHandler>>>> resolutions =
            new ConcurrentHashMap<>();
    private final Set<CachedHandler> retired = Collections.newSetFromMap(new IdentityHashMap<>());
    private final ReentrantLock lifecycleLock = new ReentrantLock();

    private int users;
    private boolean disposed;

    private SidecarCache(ConcurrentMap<String, CachedHandler> entries) {
        this.entries = entries;
    }

    static SidecarCache create() {
        return new SidecarCache(new ConcurrentHashMap<>());
    }

    static SidecarCache disabled() {
        return DISABLED;
    }

    void acquire() {
        if (entries == null) {
            return;
        }
        lifecycleLock.lock();
        try {
            users++;
        } finally {
            lifecycleLock.unlock();
        }
    }

    void release() {
        if (entries == null) {
            return;
        }
        Set<CachedHandler> removed;
        lifecycleLock.lock();
        try {
            users--;
            removed = drainRetired();
        } finally {
            lifecycleLock.unlock();
        }
        removed.forEach(CachedHandler::dispose);
    }

    void dispose() {
        if (entries == null) {
            return;
        }
        Set<CachedHandler> removed;
        lifecycleLock.lock();
        try {
            disposed = true;
            removed = drainRetired();
        } finally {
            lifecycleLock.unlock();
        }
        removed.forEach(CachedHandler::dispose);
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

        AtomicReference<CompletableFuture<Optional<CachedHandler>>> slot =
                resolutions.computeIfAbsent(coding, _ -> new AtomicReference<>());
        CompletableFuture<Optional<CachedHandler>> existing = slot.get();
        if (existing != null) {
            return await(existing);
        }

        CompletableFuture<Optional<CachedHandler>> resolution = new CompletableFuture<>();
        existing = slot.compareAndExchange(null, resolution);
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
            resolved.ifPresent(handler -> cache(coding, handler));
            resolution.complete(resolved);
            return resolved;
        } catch (IOException | URISyntaxException | RuntimeException | Error e) {
            resolution.completeExceptionally(e);
            throw e;
        } finally {
            slot.compareAndSet(resolution, null);
        }
    }

    void remove(String coding) {
        if (entries == null) {
            return;
        }
        Set<CachedHandler> removed;
        lifecycleLock.lock();
        try {
            CachedHandler handler = entries.remove(coding);
            if (handler != null) {
                retired.add(handler);
            }
            removed = drainRetired();
        } finally {
            lifecycleLock.unlock();
        }
        removed.forEach(CachedHandler::dispose);
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

    private CachedHandler reusable(String coding) {
        return entries.get(coding);
    }

    private void cache(String coding, CachedHandler handler) {
        Set<CachedHandler> removed;
        lifecycleLock.lock();
        try {
            CachedHandler previous = entries.put(coding, handler);
            if (previous != null && previous != handler) {
                retired.add(previous);
            }
            removed = drainRetired();
        } finally {
            lifecycleLock.unlock();
        }
        removed.forEach(CachedHandler::dispose);
    }

    // Called with lifecycleLock held. A retained identity handler also retains its selected sidecars.
    private Set<CachedHandler> drainRetired() {
        if (users != 0) {
            return Set.of();
        }
        if (disposed) {
            retired.addAll(entries.values());
            entries.clear();
            resolutions.clear();
        }
        if (retired.isEmpty()) {
            return Set.of();
        }
        Set<CachedHandler> removed = Collections.newSetFromMap(new IdentityHashMap<>());
        removed.addAll(retired);
        retired.clear();
        return removed;
    }

    @FunctionalInterface
    interface Resolver {
        Optional<CachedHandler> resolve(String coding, String suffix) throws IOException, URISyntaxException;
    }
}
