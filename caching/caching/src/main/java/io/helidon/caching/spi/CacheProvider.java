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

/**
 * A cache provider (such as Coherence, JCache etc.).
 * This is a {@link java.util.ServiceLoader} service provider interface.
 *
 * @param <B> type of builder to create a provider cache manager
 * @param <C> type of provider configuration (provider specific configuration class that can be used
 *              to configure this provider programmatically)
 * @param <T> type of provider cache manager
 */
public interface CacheProvider<B extends CacheProviderManager.Builder<B, C, T>, C extends CacheProviderConfig,
        T extends CacheProviderManager> {
    /**
     * Type of the provider, to map to a configuration node
     * for provider and for configured caches.
     *
     * @return type of the provider
     */
    String type();

    /**
     * A builder to create a {@code ProviderCacheManager}.
     *
     * @return a new builder
     */
    B cacheManagerBuilder();
}
