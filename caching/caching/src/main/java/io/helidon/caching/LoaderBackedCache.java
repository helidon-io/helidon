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
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.reactive.Single;

/**
 * A helper class that uses a loader.
 *
 * @param <K> key type
 * @param <V> value type
 */
public class LoaderBackedCache<K, V> implements Cache<K, V> {
    private static final Logger LOGGER = Logger.getLogger(LoaderBackedCache.class.getName());

    private final CacheLoader<K, V> loader;
    private final Cache<K, V> delegate;

    LoaderBackedCache(CacheLoader<K, V> loader, Cache<K, V> delegate) {
        this.loader = loader;
        this.delegate = delegate;
    }

    public static <K, V> LoaderBackedCache<K, V> create(CacheLoader<K, V> loader, Cache<K, V> delegate) {
        return new LoaderBackedCache<>(loader, delegate);
    }

    @Override
    public Single<Optional<V>> get(K key) {
        return delegate.get(key)
                .flatMapSingle(it -> {
                    if (it.isPresent()) {
                        return Single.just(it);
                    } else {
                        return loader.load(key)
                                .peek(loaded -> loaded.ifPresent(value -> {
                                    put(key, value)
                                            .onError(t -> LOGGER.log(Level.WARNING, "Failed to store loaded value", t));
                                }));
                    }
                });
    }

    @Override
    public Single<V> compute(K key, Supplier<V> supplier) {
        return delegate.compute(key, supplier);
    }

    @Override
    public Single<V> computeSingle(K key, Supplier<Single<V>> valueSupplier) {
        return delegate.computeSingle(key, valueSupplier);
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public Single<Void> put(K key, V value) {
        return delegate.put(key, value);
    }

    @Override
    public Single<Void> remove(K key) {
        return delegate.remove(key);
    }

    @Override
    public Single<Void> clear() {
        return delegate.clear();
    }

    @Override
    public <T> T unwrap(Class<T> clazz) {
        return delegate.unwrap(clazz);
    }

    @Override
    public Single<Void> close() {
        return delegate.close();
    }

    @Override
    public boolean isClosed() {
        return delegate.isClosed();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
