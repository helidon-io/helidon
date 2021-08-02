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

import io.helidon.common.reactive.Single;
import io.helidon.config.Config;

/**
 * Main entry point to access caches.
 */
public interface CacheManager {
    static CacheManager create(Config config) {
        return CacheManagerImpl.create(config);
    }

    <K, V> Single<Cache<K, V>> cache(String name);

    <K, V> Single<Cache<K, V>> cache(String name, CacheConfig<K, V> config);

    Single<Void> close();

    /**
     * Initialize all provider cache managers (if this method is not called, provider cache
     * managers are initialized lazily as caches are requested).
     */
    Single<Void> startup();
}
