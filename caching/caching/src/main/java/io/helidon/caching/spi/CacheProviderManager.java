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

package io.helidon.caching.spi;

import io.helidon.caching.Cache;
import io.helidon.caching.CacheConfig;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;

/**
 * A manager for caches by a single provider.
 */
public interface CacheProviderManager {
    /**
     * Create a new cache.
     *
     * @param name name of the cache
     * @param configuration cache configuration object, either a provider specific instance, or
     *                      a direct implementation of {@link io.helidon.caching.CacheConfig}
     * @param <K> type of keys of the cache
     * @param <V> type of values of the cache
     *
     * @return a new cache (or an error in case such a cache cannot be created/retrieved)
     */
    <K, V> Single<Cache<K, V>> createCache(String name,
                                           CacheConfig<K, V> configuration);

    Single<Void> close();

    /**
     * A fluent API builder for provider specific cache managers.
     *
     * @param <B> Type of the builder (subclass of this class)
     * @param <C> Type of the provider specific configuration object
     * @param <T> Type of the Cache manager (implementation of {@link CacheProviderManager})
     */
    abstract class Builder<B extends Builder<B, C, T>, C extends CacheProviderConfig, T extends CacheProviderManager>
            implements io.helidon.common.Builder<Single<T>> {
        /**
         * Update builder from configuration.
         *
         * @param config configuration node
         * @return updated builder
         */
        public abstract B config(Config config);

        /**
         * Configuration of a single named cache (if available).
         *
         * @param name cache name
         * @param config configuration
         * @return updated builder
         */
        public abstract B cacheConfig(String name, Config config);

        /**
         * Update builder from provider specific configuration object.
         *
         * @param providerConfig provider configuration
         * @return updated builder
         */
        public abstract B config(C providerConfig);
    }
}
