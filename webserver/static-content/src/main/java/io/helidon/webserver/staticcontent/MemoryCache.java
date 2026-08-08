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
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final Map<CachedHandlerInMemory, Integer> handlerReferences = new IdentityHashMap<>();
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();

    // Mutations that affect currentSize take sizeLock before cacheLock to keep currentSize in sync with cache entries.
    private final ReentrantLock sizeLock = new ReentrantLock();
    private final Map<StaticContentHandler, Set<Reservation>> reservations = new IdentityHashMap<>();
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
            Set<Reservation> removedReservations = reservations.remove(staticContentHandler);
            if (removedReservations != null) {
                for (Reservation reservation : removedReservations) {
                    currentSize -= reservation.size;
                }
            }
            Map<String, CachedHandlerInMemory> removed = cache.remove(staticContentHandler);
            if (removed != null) {
                long released = 0;
                for (CachedHandlerInMemory cached : removed.values()) {
                    released += removeReference(cached);
                }
                currentSize -= released;
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
        Reservation reservation = reserve(handler, size);
        if (reservation == null) {
            return Optional.empty();
        }

        CachedHandlerInMemory cachedHandlerInMemory;
        try {
            cachedHandlerInMemory = handlerSupplier.get();
        } catch (RuntimeException | Error e) {
            releaseReservation(reservation);
            throw e;
        }
        try {
            sizeLock.lock();
            if (!removeReservation(reservation)) {
                return Optional.empty();
            }
            try {
                cacheLock.writeLock().lock();
                Map<String, CachedHandlerInMemory> resourceCache = cache.computeIfAbsent(handler, k -> new HashMap<>());
                CachedHandlerInMemory previous = resourceCache.get(resource);
                long updatedSize = currentSize - reservation.size + sizeDelta(previous, cachedHandlerInMemory);
                if (updatedSize > maxSize) {
                    currentSize -= reservation.size;
                    return Optional.empty();
                }
                resourceCache.put(resource, cachedHandlerInMemory);
                updateReferences(previous, cachedHandlerInMemory, updatedSize);
                return Optional.of(cachedHandlerInMemory);
            } finally {
                cacheLock.writeLock().unlock();
            }
        } finally {
            sizeLock.unlock();
        }
    }

    private Reservation reserve(StaticContentHandler handler, int size) {
        if (maxSize == 0 || size < 0) {
            return null;
        }
        try {
            sizeLock.lock();
            if (currentSize + size > maxSize) {
                return null;
            }
            Reservation reservation = new Reservation(handler, size);
            currentSize += size;
            reservations.computeIfAbsent(handler, _ -> new HashSet<>()).add(reservation);
            return reservation;
        } finally {
            sizeLock.unlock();
        }
    }

    private void releaseReservation(Reservation reservation) {
        try {
            sizeLock.lock();
            if (removeReservation(reservation)) {
                currentSize -= reservation.size;
            }
        } finally {
            sizeLock.unlock();
        }
    }

    private boolean removeReservation(Reservation reservation) {
        Set<Reservation> handlerReservations = reservations.get(reservation.handler);
        if (handlerReservations == null || !handlerReservations.remove(reservation)) {
            return false;
        }
        if (handlerReservations.isEmpty()) {
            reservations.remove(reservation.handler);
        }
        return true;
    }

    // hard add to cache, even if disabled (for explicitly configured resources to cache in memory)
    void cache(StaticContentHandler handler, String resource, CachedHandlerInMemory inMemoryHandler) {
        try {
            sizeLock.lock();
            cacheLock.writeLock().lock();
            CachedHandlerInMemory previous = cache.computeIfAbsent(handler, k -> new HashMap<>())
                    .put(resource, inMemoryHandler);
            updateReferences(previous, inMemoryHandler, currentSize + sizeDelta(previous, inMemoryHandler));
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

    private long sizeDelta(CachedHandlerInMemory previous, CachedHandlerInMemory next) {
        if (previous == next) {
            return 0;
        }

        long result = 0;
        if (previous != null && handlerReferences.getOrDefault(previous, 0) == 1) {
            result -= previous.contentLength();
        }
        if (handlerReferences.getOrDefault(next, 0) == 0) {
            result += next.contentLength();
        }
        return result;
    }

    private void updateReferences(CachedHandlerInMemory previous, CachedHandlerInMemory next, long updatedSize) {
        if (previous != next) {
            removeReference(previous);
            addReference(next);
        }
        currentSize = updatedSize;
    }

    private void addReference(CachedHandlerInMemory handler) {
        handlerReferences.merge(handler, 1, Integer::sum);
    }

    private long removeReference(CachedHandlerInMemory handler) {
        if (handler == null) {
            return 0;
        }
        Integer count = handlerReferences.get(handler);
        if (count == null) {
            return 0;
        }
        if (count == 1) {
            handlerReferences.remove(handler);
            return handler.contentLength();
        }
        handlerReferences.put(handler, count - 1);
        return 0;
    }

    private static final class Reservation {
        private final StaticContentHandler handler;
        private final long size;

        private Reservation(StaticContentHandler handler, long size) {
            this.handler = handler;
            this.size = size;
        }
    }
}
