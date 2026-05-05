/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.builder.api.RuntimeType;

/**
 * Memory cache to allow in-memory storage of static content, rather than reading it from file system each time the
 * resource is requested.
 */
public class MemoryCache implements RuntimeType.Api<MemoryCacheConfig> {
    private final MemoryCacheConfig config;
    private final long maxSize;
    // cache is Map<instance of handler -> Map<resource path -> CachedHandlerInMemory>>
    private final Map<StaticContentHandler, Map<String, CachedHandlerInMemory>> cache = new IdentityHashMap<>();
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();

    // Mutations that affect currentSize take sizeLock before cacheLock to keep currentSize in sync with cache entries.
    private final ReentrantLock sizeLock = new ReentrantLock();
    private long currentSize;

    private MemoryCache(MemoryCacheConfig config) {
        this.config = config;
        if (config.enabled()) {
            long configuredMax = config.capacity().toBytes();
            this.maxSize = configuredMax == 0 ? Long.MAX_VALUE : configuredMax;
        } else {
            this.maxSize = 0;
        }
    }

    /**
     * A new builder to configure and create a memory cache.
     *
     * @return a new fluent API builder
     */
    public static MemoryCacheConfig.Builder builder() {
        return MemoryCacheConfig.builder();
    }

    /**
     * Create a new memory cache from its configuration.
     *
     * @param config memory cache configuration
     * @return a new configured memory cache
     */
    public static MemoryCache create(MemoryCacheConfig config) {
        return new MemoryCache(config);
    }

    /**
     * Create a new memory cache customizing its configuration.
     *
     * @param consumer configuration consumer
     * @return a new configured memory cache
     */
    public static MemoryCache create(Consumer<MemoryCacheConfig.Builder> consumer) {
        return builder().update(consumer).build();
    }

    /**
     * Create an in-memory cache with zero capacity. Only
     * {@link #cache(StaticContentHandler, String, CachedHandlerInMemory)} will be stored in it, as these are considered
     * required cache records. All calls to {@link #cache(StaticContentHandler, String, int, java.util.function.Supplier)} will
     * return empty.
     *
     * @return a new memory cache with capacity set to zero
     */
    public static MemoryCache create() {
        return builder().enabled(false).build();
    }

    @Override
    public MemoryCacheConfig prototype() {
        return config;
    }

    void clear(StaticContentHandler staticContentHandler) {
        try {
            sizeLock.lock();
            cacheLock.writeLock().lock();
            Map<String, CachedHandlerInMemory> removed = cache.remove(staticContentHandler);
            if (removed != null) {
                long removedSize = removed.values()
                        .stream()
                        .mapToLong(CachedHandlerInMemory::contentLength)
                        .sum();
                adjustSize(-removedSize);
            }
        } finally {
            cacheLock.writeLock().unlock();
            sizeLock.unlock();
        }
    }

    /**
     * Is there a possibility to cache the bytes.
     * There may be a race, so {@link #cache(StaticContentHandler, String, int, java.util.function.Supplier)} may still return
     * empty.
     *
     * @return if there is space in the cache for the number of bytes requested
     */
    boolean available(int bytes) {
        try {
            sizeLock.lock();
            return maxSize != 0 && (currentSize + bytes) <= maxSize;
        } finally {
            sizeLock.unlock();
        }
    }

    Optional<CachedHandlerInMemory> cache(StaticContentHandler handler,
                                          String resource,
                                          int size,
                                          Supplier<CachedHandlerInMemory> handlerSupplier) {
        if (maxSize == 0) {
            // either we are not enabled, or the size would be bigger than maximal size
            return Optional.empty();
        }
        long oldSize;
        try {
            sizeLock.lock();
            try {
                cacheLock.readLock().lock();
                Map<String, CachedHandlerInMemory> resourceCache = cache.get(handler);
                oldSize = contentLength(resourceCache == null ? null : resourceCache.get(resource));
            } finally {
                cacheLock.readLock().unlock();
            }
            if (currentSize - oldSize + size > maxSize) {
                return Optional.empty();
            }
            adjustSize(size - oldSize);
            CachedHandlerInMemory cachedHandlerInMemory;
            try {
                cachedHandlerInMemory = handlerSupplier.get();
            } catch (RuntimeException | Error e) {
                adjustSize(oldSize - size);
                throw e;
            }
            long newSize = cachedHandlerInMemory.contentLength();
            if (currentSize - size + newSize > maxSize) {
                adjustSize(oldSize - size);
                return Optional.empty();
            }
            cacheLock.writeLock().lock();
            try {
                cache.computeIfAbsent(handler, k -> new HashMap<>())
                        .put(resource, cachedHandlerInMemory);
                adjustSize(newSize - size);
                return Optional.of(cachedHandlerInMemory);
            } finally {
                cacheLock.writeLock().unlock();
            }
        } finally {
            sizeLock.unlock();
        }
    }

    // hard add to cache, even if disabled (for explicitly configured resources to cache in memory)
    void cache(StaticContentHandler handler, String resource, CachedHandlerInMemory inMemoryHandler) {
        try {
            sizeLock.lock();
            cacheLock.writeLock().lock();
            Map<String, CachedHandlerInMemory> resourceCache = cache.computeIfAbsent(handler, k -> new HashMap<>());
            CachedHandlerInMemory oldValue = resourceCache.put(resource, inMemoryHandler);
            adjustSize(inMemoryHandler.contentLength() - contentLength(oldValue));
        } finally {
            cacheLock.writeLock().unlock();
            sizeLock.unlock();
        }
    }

    Optional<CachedHandlerInMemory> get(StaticContentHandler handler, String resource) {
        try {
            cacheLock.readLock().lock();
            Map<String, CachedHandlerInMemory> resourceCache = cache.get(handler);
            if (resourceCache == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(resourceCache.get(resource));
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    private void adjustSize(long sizeDelta) {
        if (maxSize != 0) {
            currentSize = Math.max(0, currentSize + sizeDelta);
        }
    }

    private static long contentLength(CachedHandlerInMemory handler) {
        return handler == null ? 0 : handler.contentLength();
    }
}
