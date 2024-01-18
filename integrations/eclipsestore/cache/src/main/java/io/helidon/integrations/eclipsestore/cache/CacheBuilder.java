/*
 * Copyright (c) 2021, 2024 Oracle and/or its affiliates.
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

package io.helidon.integrations.eclipsestore.cache;

import javax.cache.Caching;

import io.helidon.config.Config;

import org.eclipse.store.cache.types.Cache;
import org.eclipse.store.cache.types.CacheConfiguration;
import org.eclipse.store.cache.types.CacheManager;
import org.eclipse.store.cache.types.CachingProvider;

/**
 * Builder for a Eclipse Store  JCache instance.
 *
 * @param <K> type of the cache key
 * @param <V> type of the cache value
 */
public class CacheBuilder<K, V> {
    private static final String ECLIPSESTORE_CACHING_PROVIDER = "org.eclipse.store.cache.types.CachingProvider";

    private final CachingProvider provider;
    private final CacheManager cacheManager;
    private final CacheConfiguration<K, V> configuration;

    protected CacheBuilder(CacheConfiguration<K, V> configuration) {
        super();

        this.configuration = configuration;
        this.provider = (CachingProvider) Caching.getCachingProvider(ECLIPSESTORE_CACHING_PROVIDER);
        this.cacheManager = provider.getCacheManager();
    }

    protected CacheBuilder(Class<K> keyType, Class<V> valueType) {
        this.provider = (CachingProvider) Caching.getCachingProvider(ECLIPSESTORE_CACHING_PROVIDER);
        this.cacheManager = provider.getCacheManager();
        this.configuration = CacheConfiguration.Builder(keyType, valueType).build();
    }

    /**
     * Create a new cache builder using the provided eclipstore cache configuration.
     *
     * @param <K>           type of the cache key
     * @param <V>           type of the cache value
     * @param configuration CacheConfiguration
     * @param keyType       class of the cache key
     * @param valueType     class of the cache key
     * @return cache builder
     */
    public static <K, V> CacheBuilder<K, V> builder(CacheConfiguration<K, V> configuration,
                                                    Class<K> keyType,
                                                    Class<V> valueType) {
        return new CacheBuilder<>(configuration);
    }

    /**
     * Create a named cache using the provided helidon configuration.
     *
     * @param name      the cache name
     * @param config    the helidon configuration
     * @param keyType   class of the cache key
     * @param valueType class of the cache key
     * @return the new cache instance
     */
    public static Cache<?, ?> create(String name, Config config, Class<?> keyType, Class<?> valueType) {

        CacheConfiguration<?, ?> cacheConfig = EclipseStoreCacheConfigurationBuilder.builder(config, keyType, valueType)
                .build();
        return new CacheBuilder<>(cacheConfig).build(name);
    }

    /**
     * Set the name of the cache.
     *
     * @param name the cache name
     * @return the new cache instance
     */
    public Cache<K, V> build(String name) {
        return cacheManager.createCache(name, configuration);
    }
}
