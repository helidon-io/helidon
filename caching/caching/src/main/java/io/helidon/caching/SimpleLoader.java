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

import java.util.Optional;
import java.util.function.Function;

import io.helidon.common.reactive.Single;

public final class SimpleLoader<K, V> implements CacheLoader<K, V> {
    private final Function<K, V> function;

    private SimpleLoader(Function<K, V> function) {
        this.function = function;
    }

    public static <K, V> SimpleLoader<K, V> create(Function<K, V> function) {
        return new SimpleLoader<>(function);
    }

    @Override
    public final Single<Optional<V>> load(K key) {
        return Single.just(Optional.of(function.apply(key)));
    }
}
