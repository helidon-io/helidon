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

package io.helidon.caching;

import java.util.Optional;

import io.helidon.caching.spi.CacheProvider;
import io.helidon.caching.spi.CacheSpi;
import io.helidon.common.configurable.LruCache;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;

class InMemoryCacheProvider implements CacheProvider {
    @Override
    public String type() {
        return "memory";
    }

    @Override
    public <K, V> Single<CacheSpi<K, V>> createCache(CacheManager manager,
                                                     Config config,
                                                     String name,
                                                     CacheConfig<K, V> configuration) {
        return Single.just(new InMemoryCache<>(name,
                                               LruCache.<K, V>builder()
                                                       .config(config)
                                                       .build()));
    }

    @Override
    public Single<Void> closeCache(CacheSpi<?, ?> toClose) {
        return Single.empty();
    }

    private static class InMemoryCache<K, V> implements CacheSpi<K, V> {
        private final String name;
        private final LruCache<K, V> cache;

        InMemoryCache(String name, LruCache<K, V> cache) {
            this.name = name;
            this.cache = cache;
        }

        @Override
        public String toString() {
            return name + ": InMemoryCache-LRU";
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
    }
}
