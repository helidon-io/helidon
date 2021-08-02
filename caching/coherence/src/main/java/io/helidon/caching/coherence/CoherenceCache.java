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

package io.helidon.caching.coherence;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.caching.Cache;
import io.helidon.caching.CacheException;
import io.helidon.common.reactive.Single;

import com.tangosol.net.AsyncNamedCache;
import com.tangosol.net.NamedCache;

public class CoherenceCache<K, V> implements Cache<K, V> {
    private final AtomicBoolean closed = new AtomicBoolean();

    private final NamedCache<K, V> namedCache;
    private final AsyncNamedCache<K, V> asyncNamedCache;
    private final ExecutorService executor;

    CoherenceCache(NamedCache<K, V> namedCache, AsyncNamedCache<K, V> asyncNamedCache, ExecutorService executor) {
        this.namedCache = namedCache;
        this.asyncNamedCache = asyncNamedCache;
        this.executor = executor;
    }

    @Override
    public String name() {
        return namedCache.getCacheName();
    }

    @Override
    public Single<Optional<V>> get(K key) {
        return Single.create(asyncNamedCache.get(key)
                                     .thenApply(Optional::ofNullable));
    }

    @Override
    public Single<Void> put(K key, V value) {
        return Single.create(asyncNamedCache.put(key, value), true);
    }

    @Override
    public Single<Void> remove(K key) {
        return Single.create(asyncNamedCache.remove(key), true)
                .flatMapSingle(it -> Single.empty());
    }

    @Override
    public Single<Void> clear() {
        return Single.create(asyncNamedCache.clear(), true);
    }

    @Override
    public <T> T unwrap(Class<T> clazz) {
        if (clazz.isAssignableFrom(namedCache.getClass())) {
            return clazz.cast(namedCache);
        }
        if (clazz.isAssignableFrom(asyncNamedCache.getClass())) {
            return clazz.cast(asyncNamedCache);
        }
        throw new CacheException("Class " + clazz + " is not a valid cache class for a NamedCache or AsyncNamedCache");
    }

    @Override
    public Single<Void> close() {
        closed.set(true);
        return execute(namedCache::close);
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    private Single<Void> execute(Runnable runnable) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        executor.submit(() -> {
            try {
                runnable.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });

        return Single.create(future, true);
    }
}
