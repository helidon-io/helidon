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
import java.util.logging.Logger;

import io.helidon.caching.Cache;
import io.helidon.caching.CacheConfig;
import io.helidon.caching.spi.CacheProviderManager;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;

public class MemoryCacheManager implements CacheProviderManager {
    private static final Logger LOGGER = Logger.getLogger(MemoryCacheManager.class.getName());

    private final Map<String, Config> cacheConfigs;
    private final ConcurrentHashMap<String, Cache<?, ?>> caches = new ConcurrentHashMap<>();

    private MemoryCacheManager(Builder builder) {
        this.cacheConfigs = builder.cacheConfigs;
        LOGGER.info("Started Memory Cache manager");
    }

    public static Builder builder() {
        return new Builder();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <K, V> Single<Cache<K, V>> createCache(String name, CacheConfig<K, V> configuration) {
        return Single.just((Cache<K, V>) caches.computeIfAbsent(name, it -> buildCache(name, configuration)));
    }

    @Override
    public Single<Void> close() {
        caches.forEachValue(1000, Cache::close);
        caches.clear();
        return Single.empty();
    }

    private <V, K> Cache<K, V> buildCache(String name, CacheConfig<K, V> configuration) {
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

        return cacheBuilder.build();
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
