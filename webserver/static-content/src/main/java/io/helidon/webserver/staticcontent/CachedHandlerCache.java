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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import io.helidon.common.LruCache;

/**
 * LRU cache that retires resources owned by removed handlers.
 */
final class CachedHandlerCache implements LruCache<String, CachedHandler> {
    private final Map<String, CachedHandler> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final ReentrantLock lock = new ReentrantLock();
    private final int capacity;

    CachedHandlerCache(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Capacity must not be negative");
        }
        this.capacity = capacity;
    }

    @Override
    public Optional<CachedHandler> get(String key) {
        Objects.requireNonNull(key);
        lock.lock();
        try {
            return Optional.ofNullable(cache.get(key));
        } finally {
            lock.unlock();
        }
    }

    Optional<CachedHandler> acquire(String key) {
        Objects.requireNonNull(key);
        lock.lock();
        try {
            CachedHandler handler = cache.get(key);
            if (handler != null) {
                handler.acquire();
            }
            return Optional.ofNullable(handler);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public CachedHandler peek(String key, CachedHandler defaultValue) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(defaultValue);
        lock.lock();
        try {
            for (Map.Entry<String, CachedHandler> entry : cache.entrySet()) {
                if (Objects.equals(entry.getKey(), key)) {
                    return entry.getValue();
                }
            }
            return defaultValue;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<CachedHandler> remove(String key) {
        Objects.requireNonNull(key);
        CachedHandler removed;
        lock.lock();
        try {
            removed = cache.remove(key);
        } finally {
            lock.unlock();
        }
        if (removed != null) {
            removed.dispose();
        }
        return Optional.ofNullable(removed);
    }

    @Override
    public Optional<CachedHandler> put(String key, CachedHandler value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        Set<CachedHandler> retired = Collections.newSetFromMap(new IdentityHashMap<>());
        CachedHandler replaced;
        lock.lock();
        try {
            replaced = cache.put(key, value);
            if (replaced != null && replaced != value) {
                retired.add(replaced);
            }
            while (cache.size() > capacity) {
                Map.Entry<String, CachedHandler> eldest = cache.entrySet().iterator().next();
                cache.remove(eldest.getKey());
                retired.add(eldest.getValue());
            }
        } finally {
            lock.unlock();
        }
        retired.forEach(CachedHandler::dispose);
        return Optional.ofNullable(replaced);
    }

    @Override
    public Optional<CachedHandler> computeValue(String key,
                                                Supplier<Optional<CachedHandler>> valueSupplier) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(valueSupplier);
        Optional<CachedHandler> current = get(key);
        if (current.isPresent()) {
            return current;
        }
        Optional<CachedHandler> created = valueSupplier.get();
        created.ifPresent(value -> put(key, value));
        return created;
    }

    @Override
    public int size() {
        lock.lock();
        try {
            return cache.size();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public void clear() {
        Set<CachedHandler> retired = Collections.newSetFromMap(new IdentityHashMap<>());
        lock.lock();
        try {
            retired.addAll(cache.values());
            cache.clear();
        } finally {
            lock.unlock();
        }
        retired.forEach(CachedHandler::dispose);
    }
}
