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

import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.reactive.Single;

class CacheUtil {
    private static final Logger LOGGER = Logger.getLogger(CacheUtil.class.getName());

    static <K, V> Single<V> compute(Cache<K, V> cache, K key, Supplier<V> supplier) {
        return cache.get(key).flatMapSingle(it -> it.map(Single::just)
                .orElseGet(() -> {
                    V value = supplier.get();
                    cache.put(key, value)
                            .onError(t -> LOGGER.log(Level.WARNING, "Failed to store cache value", t));
                    return Single.just(value);
                }));
    }

}
