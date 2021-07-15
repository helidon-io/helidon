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
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.caching.spi.CacheProvider;
import io.helidon.caching.spi.CacheSpi;
import io.helidon.common.reactive.Single;

class LoaderBackedCacheImpl<K, V> extends CacheImpl<K, V> {
    private static final Logger LOGGER = Logger.getLogger(LoaderBackedCacheImpl.class.getName());

    private final CacheLoader<K, V> loader;

    LoaderBackedCacheImpl(CacheManagerImpl cacheManager,
                          CacheProvider provider,
                          CacheSpi<K, V> cacheSpi,
                          CacheLoader<K, V> loader) {
        super(cacheManager, provider, cacheSpi);
        this.loader = loader;
    }

    @Override
    public Single<Optional<V>> get(K key) {
        return super.get(key)
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
    protected CacheLoader<K, V> getLoader() {
        return loader;
    }
}
