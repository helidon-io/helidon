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

import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import io.helidon.caching.Cache;
import io.helidon.caching.CacheManager;
import io.helidon.config.Config;

class Caches {
    private final Instance<Object> cdi;
    private final Config config;
    private final CacheManager cacheManager;

    @Inject
    Caches(Instance<Object> cdi, Config config) {
        this.cdi = cdi;
        this.config = config;
        this.cacheManager = CacheManager.create(config.get("caches"));
    }

    Cache<Object, Object> cache(String name) {
        return cacheManager.cache(name).await();
    }
}
