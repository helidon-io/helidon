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
import java.util.Map;
import java.util.Optional;

import io.helidon.common.reactive.Single;

/**
 * Load values for cache in case the cache does not contain them.
 *
 * @param <K>
 * @param <V>
 */
@FunctionalInterface
public interface CacheLoader<K, V> {
    Single<Optional<V>> load(K key);

    default Single<Map<K, V>> load(Iterable<? extends K> keys) {
        Single<Optional<V>> responseSingle = Single.empty();

        Map<K, V> result = new HashMap<>();

        for (K key : keys) {
            responseSingle = responseSingle.flatMapSingle(ignore -> load(key))
                    .peek(value -> value.ifPresent(it -> result.put(key, it)));
        }

        return responseSingle.map(ignore -> result);
    }
}
