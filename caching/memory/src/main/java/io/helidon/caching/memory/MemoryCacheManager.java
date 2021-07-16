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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.helidon.caching.Cache;
import io.helidon.caching.CacheConfig;
import io.helidon.caching.spi.CacheProviderManager;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;

public class MemoryCacheManager implements CacheProviderManager {
    private final Map<String, Config> cacheConfigs;
    private final ConcurrentHashMap<String, MemoryCache<?, ?>> caches = new ConcurrentHashMap<>();

    private MemoryCacheManager(Builder builder) {
        this.cacheConfigs = builder.cacheConfigs;
    }

    public static Builder builder() {
        return new Builder();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <K, V> Single<Cache<K, V>> createCache(String name, CacheConfig<K, V> configuration) {
        MemoryCache.Builder<K, V> cacheBuilder = MemoryCache.builder();

        cacheBuilder.name(name);

        Config memoryCacheConfig = cacheConfigs.get(name);
        if (memoryCacheConfig != null) {
            cacheBuilder.config(memoryCacheConfig);
        }

        if (configuration instanceof MemoryCacheConfig) {
            // full configuration, we can just use it
            cacheBuilder.config((MemoryCacheConfig<K, V>) configuration);
        } else {
            // general configuration, use only parts
            configuration.loader().ifPresent(cacheBuilder::loader);
        }

        return Single.just(cacheBuilder.build());
    }

    public static class Builder
            extends CacheProviderManager.Builder<Builder, MemoryCacheProvider.MemoryConfig, MemoryCacheManager> {
        private final Map<String, Config> cacheConfigs = new HashMap<>();

        Builder() {
        }

        @Override
        public Single<MemoryCacheManager> build() {
            return Single.just(new MemoryCacheManager(this));
        }

        @Override
        public Builder config(Config config) {
            return this;
        }

        @Override
        public Builder cacheConfig(String name, Config config) {
            cacheConfigs.put(name, config);
            return this;
        }

        @Override
        public Builder config(MemoryCacheProvider.MemoryConfig providerConfig) {
            return this;
        }
    }
}
