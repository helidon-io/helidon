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

import io.helidon.common.reactive.Single;

public interface Cache<K, V> extends CacheCommon<K, V> {
    Single<Void> close();

    boolean isClosed();

    default Single<V> compute(K key, Supplier<V> supplier) {
        return CacheUtil.compute(this, key, supplier);
    }

    default Single<V> computeSingle(K key, Supplier<Single<V>> valueSupplier) {
        return get(key).flatMapSingle(it -> it.<Single<? extends V>>map(Single::just)
                .orElseGet(() -> valueSupplier.get()
                        .peek(value -> put(key, value))));
    }
}
