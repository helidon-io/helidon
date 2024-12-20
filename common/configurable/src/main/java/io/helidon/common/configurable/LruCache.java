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
package io.helidon.common.configurable;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.builder.api.RuntimeType;

/**
 * Least recently used cache.
 * This cache has a capacity. When the capacity is reached, the oldest record is removed from the cache when a new one
 * is added.
 *
 * @param <K> type of the keys of the map
 * @param <V> type of the values of the map
 * @deprecated kindly use {@link io.helidon.common.LruCache}, we are removing this from the configurable module, as cache has
 *         only a single option, and we need it from modules that do not use configuration
 */
@RuntimeType.PrototypedBy(LruCacheConfig.class)
@Deprecated(forRemoval = true, since = "4.2.0")
public final class LruCache<K, V> implements io.helidon.common.LruCache<K, V>, RuntimeType.Api<LruCacheConfig<K, V>> {
    private final LruCacheConfig<K, V> config;
    private final io.helidon.common.LruCache<K, V> delegate;

    private LruCache(LruCacheConfig<K, V> config) {
        this.delegate = io.helidon.common.LruCache.create(config.capacity());
        this.config = config;
    }

    /**
     * Create a new builder.
     *
     * @param <K> key type
     * @param <V> value type
     * @return a new fluent API builder instance
     */
    public static <K, V> LruCacheConfig.Builder<K, V> builder() {
        return LruCacheConfig.builder();
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
        return LruCacheConfig.<K, V>builder().build();
    }

    /**
     * Create an instance with custom configuration.
     *
     * @param config configuration of LRU cache
     * @param <K>    key type
     * @param <V>    value type
     * @return a new cache instance
     */
    public static <K, V> LruCache<K, V> create(LruCacheConfig<K, V> config) {
        return new LruCache<>(config);
    }

    /**
     * Create an instance with custom configuration.
     *
     * @param consumer of custom configuration builder
     * @param <K>      key type
     * @param <V>      value type
     * @return a new cache instance
     */
    public static <K, V> LruCache<K, V> create(Consumer<LruCacheConfig.Builder<K, V>> consumer) {
        LruCacheConfig.Builder<K, V> builder = LruCacheConfig.builder();
        consumer.accept(builder);
        return builder.build();
    }

    @Override
    public LruCacheConfig<K, V> prototype() {
        return config;
    }

    @Override
    public Optional<V> get(K key) {
        return delegate.get(key);
    }

    @Override
    public Optional<V> remove(K key) {
        return delegate.remove(key);
    }

    @Override
    public Optional<V> put(K key, V value) {
        return delegate.put(key, value);
    }

    @Override
    public Optional<V> computeValue(K key, Supplier<Optional<V>> valueSupplier) {
        return delegate.computeValue(key, valueSupplier);
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public int capacity() {
        return delegate.capacity();
    }

    @Override
    public void clear() {
        delegate.clear();
    }
}
