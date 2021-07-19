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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import io.helidon.caching.spi.CacheProvider;
import io.helidon.caching.spi.CacheProviderManager;
import io.helidon.common.reactive.Single;
import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.config.Config;

class CacheManagerImpl implements CacheManager {
    private static final Logger LOGGER = Logger.getLogger(CacheManagerImpl.class.getName());

    private static final List<CacheProvider> PROVIDERS = HelidonServiceLoader.builder(ServiceLoader.load(CacheProvider.class))
            .build()
            .asList();
    private static final Set<String> SUPPORTED_TYPES = new LinkedHashSet<>();

    static {
        for (CacheProvider provider : PROVIDERS) {
            SUPPORTED_TYPES.add(provider.type());
        }
    }

    private final Map<String, CacheConfiguration> cacheConfigs;
    private final Map<String, CompletableFuture<Cache<?, ?>>> cacheFutures = new HashMap<>();
    private final Map<String, AtomicBoolean> cacheClosed = new HashMap<>();
    private final Map<String, AtomicBoolean> cacheRequested = new HashMap<>();

    private final Map<String, Config> providerConfigs;
    private final Map<String, CompletableFuture<CacheProviderManager>> providerFutures = new HashMap<>();
    private final Map<String, AtomicBoolean> providerRequested = new HashMap<>();

    private CacheManagerImpl(Map<String, Config> providerConfigs, Map<String, CacheConfiguration> cacheConfigs) {
        this.cacheConfigs = cacheConfigs;
        for (String name : cacheConfigs.keySet()) {
            cacheFutures.put(name, new CompletableFuture<>());
            cacheClosed.put(name, new AtomicBoolean());
            cacheRequested.put(name, new AtomicBoolean());
        }
        this.providerConfigs = new HashMap<>(providerConfigs);
        for (String supportedType : SUPPORTED_TYPES) {
            if (providerConfigs.containsKey(supportedType)) {
                continue;
            }
            providerConfigs.put(supportedType, Config.empty());
        }
        for (String type : providerConfigs.keySet()) {
            providerFutures.put(type, new CompletableFuture<>());
            providerRequested.put(type, new AtomicBoolean());
        }
    }

    public static CacheManager create(Config config) {
        Map<String, Config> providerConfigs = new HashMap<>();
        Map<String, CacheConfiguration> cacheConfigs = new HashMap<>();

        Config providerConfig = config.get("providers");
        providerConfig.asNodeList()
                .ifPresent(nodes -> {
                    for (Config node : nodes) {
                        String type = node.get("type")
                                .asString()
                                .orElseThrow(() -> new CacheException("Key 'type' is required in cache provider configuration"));
                        Config properties = node.get("properties");
                        providerConfigs.put(type, properties);
                    }
                });
        Config cacheConfig = config.get("caches");
        cacheConfig.asNodeList()
                .ifPresent(nodes -> {
                    for (Config node : nodes) {
                        String name = node.get("name")
                                .asString()
                                .orElseThrow(() -> new CacheException("Key 'name' is required in cache configuration"));
                        String type = node.get("type")
                                .asString()
                                .orElseThrow(() -> new CacheException("Key 'type' is required in cache configuration"));
                        Config properties = node.get("properties");
                        cacheConfigs.put(name, new CacheConfiguration(name, type, properties));
                    }
                });
        return new CacheManagerImpl(providerConfigs, cacheConfigs);
    }

    @Override
    public <K, V> Single<Cache<K, V>> cache(String name) {
        return getCache(name, CacheConfig.create());
    }

    @SuppressWarnings("unchecked")
    @Override
    public <K, V> Single<Cache<K, V>> cache(String name, CacheConfig<K, V> config) {
        return getCache(name, config);
    }

    @Override
    public Single<Void> close() {
        Single<Void> result = Single.empty();

        for (Map.Entry<String, AtomicBoolean> close : cacheClosed.entrySet()) {
            if (close.getValue().compareAndSet(false, true)) {
                String name = close.getKey();
                if (cacheRequested.get(name).get()) {
                    result = result.flatMapSingle(nothing -> Single.create(cacheFutures.get(name))
                            .flatMapSingle(Cache::close));
                }
            }
        }

        return result;
    }

    @Override
    public Single<Void> startup() {
        // get a set of all provider types that are explicitly configured
        Set<String> types = new HashSet<>(providerConfigs.keySet());
        for (CacheConfiguration value : cacheConfigs.values()) {
            types.add(value.type);
        }
        CompletableFuture<CacheProviderManager> result = CompletableFuture.completedFuture(null);

        for (String type : types) {
            result = result.thenCompose(nothing -> getProvider(type));
        }

        return Single.create(result.thenApply(it -> null), true);
    }

    @SuppressWarnings("unchecked")
    private <K, V> Single<Cache<K, V>> getCache(String name, CacheConfig<K, V> cacheConfig) {
        if (cacheRequested.containsKey(name)) {
            CompletableFuture<Cache<?, ?>> cacheFuture = cacheFutures.get(name);

            if (cacheRequested.get(name).compareAndSet(false, true)) {
                CacheConfiguration cacheConfiguration = this.cacheConfigs.get(name);
                String type = cacheConfiguration.type;
                Config config = cacheConfiguration.properties;

                // not yet requested
                CacheConfig<K, V> usedConfig = cacheConfig == null ? CacheConfig.create() : cacheConfig;
                getProvider(type)
                        .flatMapSingle(provider -> provider.createCache(name, usedConfig))
                        .onError(cacheFuture::completeExceptionally)
                        .map(this::wrap)
                        .forSingle(cacheFuture::complete);
            }

            return Single.create(cacheFuture)
                    .flatMapSingle(it -> {
                        if (it.isClosed()) {
                            return Single.error(new CacheException("Cache \"" + name + "\" is closed"));
                        }

                        return Single.just((Cache<K, V>) it);
                    });
        } else {
            return Single.error(new CacheException("Cache \"" + name + "\" not configured"));
        }
    }

    private Single<CacheProviderManager> getProvider(String type) {
        if (providerRequested.containsKey(type)) {
            CompletableFuture<CacheProviderManager> providerFuture = providerFutures.get(type);

            if (providerRequested.get(type).compareAndSet(false, true)) {
                for (CacheProvider<?, ?, ?> provider : PROVIDERS) {
                    if (type.equals(provider.type())) {
                        CacheProviderManager.Builder<?, ?, ?> providerBuilder = provider.cacheManagerBuilder()
                                .config(providerConfigs.get(type));
                        for (CacheConfiguration value : cacheConfigs.values()) {
                            if (value.type.equals(type)) {
                                providerBuilder.cacheConfig(value.name, value.properties);
                            }
                        }

                        providerBuilder.build()
                                .onError(providerFuture::completeExceptionally)
                                .forSingle(providerFuture::complete);

                    }
                }
            }

            return Single.create(providerFuture);
        } else {
            return Single.error(new CacheException("Unsupported cache type configured. Configured \"" + type
                                                           + "\", available " + SUPPORTED_TYPES));
        }
    }

    private <K, V> Cache<K, V> wrap(Cache<K, V> cache) {
        return new Cache<>() {
            @Override
            public String name() {
                return cache.name();
            }

            @Override
            public Single<Optional<V>> get(K key) {
                return cache.get(key);
            }

            @Override
            public Single<Void> put(K key, V value) {
                return cache.put(key, value);
            }

            @Override
            public Single<Void> remove(K key) {
                return cache.remove(key);
            }

            @Override
            public Single<Void> clear() {
                return cache.clear();
            }

            @Override
            public <T> T unwrap(Class<T> clazz) {
                return cache.unwrap(clazz);
            }

            @Override
            public Single<Void> close() {
                cacheClosed.get(cache.name()).set(true);
                return cache.close();
            }

            @Override
            public boolean isClosed() {
                return cache.isClosed();
            }

            @Override
            public String toString() {
                return cache.toString();
            }
        };
    }

    private static class CacheConfiguration {
        private final String name;
        private final String type;
        private final Config properties;

        private CacheConfiguration(String name, String type, Config properties) {
            this.name = name;
            this.type = type;
            this.properties = properties;
        }

        @Override
        public String toString() {
            return "CacheConfig{"
                    + "name='" + name + '\''
                    + ", type='" + type + '\''
                    + ", properties=" + properties
                    + '}';
        }
    }

}
