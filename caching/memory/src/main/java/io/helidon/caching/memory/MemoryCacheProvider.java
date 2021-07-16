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

import io.helidon.caching.spi.CacheProvider;
import io.helidon.caching.spi.CacheProviderConfig;

public class MemoryCacheProvider implements
                            CacheProvider<MemoryCacheManager.Builder, MemoryCacheProvider.MemoryConfig, MemoryCacheManager> {
    @Override
    public String type() {
        return "memory";
    }

    @Override
    public MemoryCacheManager.Builder cacheManagerBuilder() {
        return MemoryCacheManager.builder();
    }

    /**
     * Configuration of in-memory cache provider.
     */
    public static class MemoryConfig implements CacheProviderConfig {
        private MemoryConfig() {
        }
        public static MemoryConfig create() {
            return new MemoryConfig();
        }
    }
}
