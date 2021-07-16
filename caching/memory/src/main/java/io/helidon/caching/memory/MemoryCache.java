/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.caching.memory;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.caching.Cache;
import io.helidon.caching.CacheException;
import io.helidon.caching.CacheLoader;
import io.helidon.caching.LoaderBackedCache;
import io.helidon.common.configurable.LruCache;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;

public class MemoryCache<K, V> implements Cache<K, V> {
    private final AtomicBoolean closed = new AtomicBoolean();
    private final LruCache<K, V> cache;
    private final String name;

    MemoryCache(Builder<K, V> builder) {
        this.cache = builder.cacheBuilder.build();
        this.name = builder.name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Single<Optional<V>> get(K key) {
        return Single.just(cache.get(key));
    }

    @Override
    public Single<Void> put(K key, V value) {
        cache.put(key, value);
        return Single.empty();
    }

    @Override
    public Single<Void> remove(K key) {
        cache.remove(key);
        return Single.empty();
    }

    @Override
    public Single<Void> clear() {
        return Single.empty();
    }

    @Override
    public <T> T unwrap(Class<T> clazz) {
        if (LruCache.class.equals(clazz)) {
            return clazz.cast(cache);
        }
        throw new CacheException("Invalid class to unwrap: " + clazz.getName()
                                         + ", only supports type " + LruCache.class.getName());
    }

    @Override
    public Single<Void> close() {
        this.closed.set(true);
        return Single.empty();
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public String toString() {
        return "In-memory LRU cache. Size: " + cache.size()  + " of " + cache.capacity();
    }

    static <K, V> Builder<K, V> builder() {
        return new Builder<>();
    }

    static <K, V> Cache<K, V> create(Config config) {
        return MemoryCache.<K, V>builder().config(config).build();
    }

    static <K, V> Cache<K, V> create() {
        return MemoryCache.<K, V>builder().build();
    }

    /**
     * Fluent API builder for {@link io.helidon.caching.memory.MemoryCacheConfig}.
     *
     * @param <K> type of keys
     * @param <V> type of values
     */
    public static class Builder<K, V> implements io.helidon.common.Builder<Cache<K, V>> {
        private final LruCache.Builder<K, V> cacheBuilder = LruCache.builder();
        private CacheLoader<K, V> loader;
        private String name;

        @Override
        public Cache<K, V> build() {
            if (name == null) {
                throw new NullPointerException("Cache name must be configured");
            }
            MemoryCache<K, V> memoryCache = new MemoryCache<>(this);

            if (loader == null) {
                return memoryCache;
            } else {
                return LoaderBackedCache.create(loader, memoryCache);
            }
        }

        public Builder<K, V> name(String name) {
            this.name = name;
            return this;
        }

        public Builder<K, V> config(MemoryCacheConfig<K, V> config) {
            cacheBuilder.capacity(config.capacity());
            config.loader().ifPresent(it -> loader = it);
            return this;
        }

        /**
         * Load configuration of this cache from configuration.
         *
         * @param config configuration
         * @return updated builder instance
         */
        public Builder<K, V> config(Config config) {
            cacheBuilder.config(config);
            return this;
        }

        public Builder<K, V> loader(CacheLoader<K, V> loader) {
            this.loader = loader;
            return this;
        }
    }
}
