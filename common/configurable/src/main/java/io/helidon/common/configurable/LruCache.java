/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
package io.helidon.common.configurable;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import io.helidon.config.Config;

/**
 * Least recently used cache.
 * This cache has a capacity. When the capacity is reached, the oldest record is removed from the cache when a new one
 * is added.
 *
 * @param <K> type of the keys of the map
 * @param <V> type of the values of the map
 */
public final class LruCache<K, V> {
    /**
     * Default capacity of the cache: {@value}.
     */
    public static final int DEFAULT_CAPACITY = 10000;

    private final LinkedHashMap<K, V> backingMap = new LinkedHashMap<>();
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock(true);
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    private final int capacity;

    private LruCache(Builder<K, V> builder) {
        this.capacity = builder.capacity;
    }

    /**
     * Create a new builder.
     *
     * @param <K> key type
     * @param <V> value type
     * @return a new fluent API builder instance
     */
    public static <K, V> Builder<K, V> builder() {
        return new Builder<>();
    }

    /**
     * Create an instance with default configuration.
     *
     * @param <K> key type
     * @param <V> value type
     * @return a new cache instance
     * @see #DEFAULT_CAPACITY
     */
    public static <K, V> LruCache<K, V> create() {
        Builder<K, V> builder = builder();
        return builder.build();
    }

    /**
     * Get a value from the cache.
     *
     * @param key key to retrieve
     * @return value if present or empty
     */
    public Optional<V> get(K key) {
        readLock.lock();

        V value;
        try {
            value = backingMap.get(key);
        } finally {
            readLock.unlock();
        }

        if (null == value) {
            return Optional.empty();
        }

        writeLock.lock();
        try {
            // make sure the value is the last in the map (I do ignore a race here, as it is not significant)
            // if some other thread moved another record to the front, we just move ours before it

            // TODO this hurts - we just need to move the key to the last position
            // maybe this should be replaced with a list and a map?
            value = backingMap.get(key);
            if (null == value) {
                return Optional.empty();
            }
            backingMap.remove(key);
            backingMap.put(key, value);

            return Optional.of(value);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Remove a value from the cache.
     *
     * @param key key of the record to remove
     * @return the value that was mapped to the key, or empty if none was
     */
    public Optional<V> remove(K key) {

        writeLock.lock();
        try {
            return Optional.ofNullable(backingMap.remove(key));
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Put a value to the cache.
     *
     * @param key   key to add
     * @param value value to add
     * @return value that was already mapped or empty if the value was not mapped
     */
    public Optional<V> put(K key, V value) {
        writeLock.lock();
        try {
            V currentValue = backingMap.remove(key);
            if (null == currentValue) {
                // need to free space - we did not make the map smaller
                if (backingMap.size() >= capacity) {
                    Iterator<V> iterator = backingMap.values().iterator();
                    iterator.next();
                    iterator.remove();
                }
            }

            backingMap.put(key, value);
            return Optional.ofNullable(currentValue);
        } finally {
            writeLock.unlock();
        }
    }

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
    public Optional<V> computeValue(K key, Supplier<Optional<V>> valueSupplier) {
        // get is properly synchronized
        Optional<V> currentValue = get(key);
        if (currentValue.isPresent()) {
            return currentValue;
        }
        Optional<V> newValue = valueSupplier.get();
        // put is also properly synchronized - nevertheless we may replace the value more then once
        // if called from parallel threads
        newValue.ifPresent(theValue -> put(key, theValue));

        return newValue;
    }

    /**
     * Current size of the map.
     *
     * @return number of records currently cached
     */
    public int size() {
        readLock.lock();
        try {
            return backingMap.size();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Capacity of this cache.
     *
     * @return configured capacity of this cache
     */
    public int capacity() {
        return capacity;
    }

    // for unit testing
    V directGet(K key) {
        return backingMap.get(key);
    }

    /**
     * Fluent API builder for {@link io.helidon.common.configurable.LruCache}.
     *
     * @param <K> type of keys
     * @param <V> type of values
     */
    public static class Builder<K, V> implements io.helidon.common.Builder<LruCache<K, V>> {
        private int capacity = DEFAULT_CAPACITY;

        @Override
        public LruCache<K, V> build() {
            return new LruCache<>(this);
        }

        /**
         * Load configuration of this cache from configuration.
         *
         * @param config configuration
         * @return updated builder instance
         */
        public Builder<K, V> config(Config config) {
            config.get("capacity").asInt().ifPresent(this::capacity);
            return this;
        }

        /**
         * Configure capacity of the cache.
         *
         * @param capacity maximal number of records in the cache before the oldest one is removed
         * @return updated builder instance
         */
        public Builder<K, V> capacity(int capacity) {
            this.capacity = capacity;
            return this;
        }
    }
}
