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

package io.helidon.caching.cdi;

import java.util.Map;

import io.helidon.caching.Cache;
import io.helidon.caching.CacheConfig;
import io.helidon.caching.CacheManager;

class Caches {

    private final CacheManager cacheManager;
    private final Map<String, CacheConfig<?, ?>> configMap;

    Caches(CacheManager cacheManager, Map<String, CacheConfig<?, ?>> configMap) {
        this.cacheManager = cacheManager;
        this.configMap = configMap;
    }

    @SuppressWarnings("unchecked")
    Cache<Object, Object> cache(String name) {
        CacheConfig<?, ?> cacheConfig = configMap.get(name);
        if (cacheConfig == null) {
            return cacheManager.cache(name).await();
        } else {
            return (Cache<Object, Object>) cacheManager.cache(name, cacheConfig).await();
        }
    }

    void startup() {
        cacheManager.startup().await();
    }
}
