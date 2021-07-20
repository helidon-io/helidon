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

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import io.helidon.caching.spi.CacheProvider;
import io.helidon.caching.spi.CacheSpi;
import io.helidon.common.reactive.Single;
import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.config.Config;

class CacheManagerImpl implements CacheManager {
    private static final Logger LOGGER = Logger.getLogger(CacheManagerImpl.class.getName());

    private static final List<CacheProvider> PROVIDERS = HelidonServiceLoader.builder(ServiceLoader.load(CacheProvider.class))
            .addService(new InMemoryCacheProvider())
            .build()
            .asList();
    private static final Set<String> SUPPORTED_TYPES = new LinkedHashSet<>();

    static {
        for (CacheProvider provider : PROVIDERS) {
            SUPPORTED_TYPES.add(provider.type());
        }
    }

    private final Map<String, CacheConfig> config;
    private final Map<String, CompletableFuture<CacheImpl<?, ?>>> caches = new HashMap<>();
    private final Map<String, AtomicBoolean> closed = new HashMap<>();
    private final Map<String, AtomicBoolean> requested = new HashMap<>();

    private CacheManagerImpl(Map<String, CacheConfig> config) {
        this.config = config;
        for (String name : config.keySet()) {
            caches.put(name, new CompletableFuture<>());
            closed.put(name, new AtomicBoolean());
            requested.put(name, new AtomicBoolean());
        }
    }

    public static CacheManager create(Config config) {
        Map<String, CacheConfig> configs = new HashMap<>();
        config.asNodeList()
                .ifPresent(nodes -> {
                    for (Config node : nodes) {
                        String name = node.get("name")
                                .asString()
                                .orElseThrow(() -> new CacheException("Key 'name' is required in cache configuration"));
                        String type = node.get("type")
                                .asString()
                                .orElseThrow(() -> new CacheException("Key 'type' is required in cache configuration"));
                        Config properties = node.get("properties");
                        configs.put(name, new CacheConfig(name, type, properties));
                    }
                });
        return new CacheManagerImpl(configs);
    }

    @Override
    public <K, V> Single<Cache<K, V>> cache(String name) {
        return getCache(name, null);
    }


    @SuppressWarnings("unchecked")
    @Override
    public <K, V> Single<Cache<K, V>> cache(String name, CacheLoader<K, V> loader) {
        return getCache(name, loader);
    }

    @SuppressWarnings("unchecked")
    private <K, V> Single<Cache<K, V>> getCache(String name, CacheLoader<K, V> loader) {
        if (requested.containsKey(name)) {
            AtomicBoolean isRequested = requested.get(name);
            if (isRequested.compareAndSet(false, true)) {
                CacheConfig cacheConfig = config.get(name);
                String type = cacheConfig.type;
                Config config = cacheConfig.properties;

                for (CacheProvider provider : PROVIDERS) {
                    if (type.equals(provider.type())) {
                        return provider.createCache(this, config, name)
                                .map(cacheSpi -> wrap(provider, cacheSpi, loader))
                                .peek(cache -> caches.get(name).complete(cache))
                                .map(it -> it);
                    }
                }
                throw new CacheException("Unsupported cache type configured. Configured \"" + type
                                                 + "\", available " + SUPPORTED_TYPES);
            } else {
                return Single.create(caches.get(name))
                        .flatMapSingle(it -> {
                            if (it.isClosed()) {
                                return Single.error(new CacheException("Cache \"" + name + "\" is closed"));
                            }

                            if (loader != null) {
                                CacheLoader<?, ?> configuredLoader = it.getLoader();
                                if (configuredLoader != loader) {
                                    LOGGER.warning("Cache loader should only be configured on the first request to"
                                                           + " obtain a cache. Further loaders are ignored");
                                }
                            }
                            return Single.just((Cache<K, V>) it);
                        });
            }
        } else {
            throw new CacheException("Cache \"" + name + "\" not configured");
        }
    }


    private <K, V> CacheImpl<K, V> wrap(CacheProvider provider,
                                        CacheSpi<Object, Object> cacheSpi,
                                        CacheLoader<K, V> loader) {
        if (loader == null) {
            return new CacheImpl<>(this, provider, cacheSpi);
        } else {
            return new LoaderBackedCacheImpl<>(this, provider, cacheSpi, loader);
        }
    }

    @Override
    public Single<Void> close() {
        Single<Void> result = Single.empty();

        for (Map.Entry<String, AtomicBoolean> close : closed.entrySet()) {
            if (close.getValue().compareAndSet(false, true)) {
                String name = close.getKey();
                if (requested.get(name).get()) {
                    result = result.flatMapSingle(nothing -> Single.create(caches.get(name))
                            .flatMapSingle(Cache::close));
                }
            }
        }

        return result;
    }

    private static class CacheConfig {
        private final String name;
        private final String type;
        private final Config properties;

        private CacheConfig(String name, String type, Config properties) {
            this.name = name;
            this.type = type;
            this.properties = properties;
        }

        @Override
        public String toString() {
            return "CacheConfig{" +
                    "name='" + name + '\'' +
                    ", type='" + type + '\'' +
                    ", properties=" + properties +
                    '}';
        }
    }

}
