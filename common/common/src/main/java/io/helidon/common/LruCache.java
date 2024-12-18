/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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
package io.helidon.common;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Least recently used cache.
 * This cache has a capacity. When the capacity is reached, the oldest record is removed from the cache when a new one
 * is added.
 *
 * @param <K> type of the keys of the map
 * @param <V> type of the values of the map
 */
public interface LruCache<K, V> {
    /**
     * Default capacity of the cache: {@value}.
     */
    int DEFAULT_CAPACITY = 10000;

    /**
     * Create an instance with default capacity.
     *
     * @param <K> key type
     * @param <V> value type
     * @return a new cache instance
     * @see #DEFAULT_CAPACITY
     */
    static <K, V> LruCache<K, V> create() {
        return new LruCacheImpl<>(DEFAULT_CAPACITY);
    }

    /**
     * Create an instance with custom capacity.
     *
     * @param capacity of the cache
     * @param <K>      key type
     * @param <V>      value type
     * @return a new cache instance
     */
    static <K, V> LruCache<K, V> create(int capacity) {
        return new LruCacheImpl<>(capacity);
    }

    /**
     * Get a value from the cache.
     *
     * @param key key to retrieve
     * @return value if present or empty
     */
    Optional<V> get(K key);

    /**
     * Remove a value from the cache.
     *
     * @param key key of the record to remove
     * @return the value that was mapped to the key, or empty if none was
     */
    Optional<V> remove(K key);

    /**
     * Put a value to the cache.
     *
     * @param key   key to add
     * @param value value to add
     * @return value that was already mapped or empty if the value was not mapped
     */
    Optional<V> put(K key, V value);

    /**
     * Either return a cached value or compute it and cache it.
     * In case this method is called in parallel for the same key, the value actually present in the map may be from
     * any of the calls.
     * This method always returns either the existing value from the map, or the value provided by the supplier. It
     * never returns a result from another thread's supplier.
     *
     * @param key           key to check/insert value for
     * @param valueSupplier supplier called if the value is not yet cached, or is invalid
     * @return current value from the cache, or computed value from the supplier
     */
    Optional<V> computeValue(K key, Supplier<Optional<V>> valueSupplier);

    /**
     * Current size of the map.
     *
     * @return number of records currently cached
     */
    int size();

    /**
     * Capacity of this cache.
     *
     * @return configured capacity of this cache
     */
    int capacity();

    /**
     * Clear all records in the cache.
     */
    void clear();
}
