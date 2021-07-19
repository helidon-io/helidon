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

import io.helidon.common.GenericType;

@SuppressWarnings("unchecked")
public interface CacheConfig<K, V> {
    static <K, V> CacheConfig<K, V> create(CacheLoader<K, V> loader) {
        return new CacheConfig<>() {
            @Override
            public Optional<CacheLoader<K, V>> loader() {
                return Optional.of(loader);
            }
        };
    }

    static <K, V> CacheConfig<K, V> create() {
        return new CacheConfig<>() {
        };
    }

    static <K, V> CacheConfig<K, V> create(Class<K> keyClass, Class<V> valueClass) {
        GenericType<K> keyType = GenericType.create(keyClass);
        GenericType<V> valueType = GenericType.create(valueClass);
        return new CacheConfig<>() {
            @Override
            public GenericType<K> keyType() {
                return keyType;
            }

            @Override
            public GenericType<V> valueType() {
                return valueType;
            }
        };
    }

    default Optional<CacheLoader<K, V>> loader() {
        return Optional.empty();
    }

    default GenericType<K> keyType() {
        return (GenericType<K>) GenericType.create(Object.class);
    }

    default GenericType<V> valueType() {
        return (GenericType<V>) GenericType.create(Object.class);
    }
}
