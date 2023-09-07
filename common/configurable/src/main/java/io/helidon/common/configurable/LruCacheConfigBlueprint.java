/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.common.configurable;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Configuration of a LRU Cache.
 *
 * @param <K> type of keys
 * @param <V> type of values
 */
@Prototype.Blueprint
@Prototype.Configured
interface LruCacheConfigBlueprint<K, V> extends Prototype.Factory<LruCache<K, V>> {
    /**
     * Configure capacity of the cache. Defaults to {@value LruCache#DEFAULT_CAPACITY}.
     *
     * @return maximal number of records in the cache before the oldest one is removed
     */
    @Option.DefaultInt(LruCache.DEFAULT_CAPACITY)
    @Option.Configured
    int capacity();
}
