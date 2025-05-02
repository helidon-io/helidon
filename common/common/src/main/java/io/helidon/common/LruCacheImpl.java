/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * Least recently used cache.
 * This cache has a capacity. When the capacity is reached, the oldest record is removed from the cache when a new one
 * is added.
 *
 * @param <K> type of the keys of the map
 * @param <V> type of the values of the map
 */
final class LruCacheImpl<K, V> implements LruCache<K, V> {
    private final SequencedMap<K, V> backingMap = new LinkedHashMap<>();
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock(true);
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    private final int capacity;

    LruCacheImpl(int capacity) {
        this.capacity = capacity;
    }

    @Override
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

            value = backingMap.get(key);
            if (null == value) {
                return Optional.empty();
            }
            //LRU policy
            backingMap.putLast(key, value);

            return Optional.of(value);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Optional<V> remove(K key) {

        writeLock.lock();
        try {
            return Optional.ofNullable(backingMap.remove(key));
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Optional<V> put(K key, V value) {
        writeLock.lock();
        try {
            V oldValue = backingMap.putLast(key, value);
            reduceSize();
            return Optional.ofNullable(oldValue);
        } finally {
            writeLock.unlock();
        }
    }

    private void reduceSize() {

        while (backingMap.size() > capacity) {
            backingMap.pollFirstEntry();
        }
    }

    @Override
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

    @Override
    public int size() {
        readLock.lock();
        try {
            return backingMap.size();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public void clear() {
        writeLock.lock();
        try {
            backingMap.clear();
        } finally {
            writeLock.unlock();
        }
    }
}
