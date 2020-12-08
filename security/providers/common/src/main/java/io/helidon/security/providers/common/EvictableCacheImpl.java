/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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
package io.helidon.security.providers.common;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Default implementation of {@link EvictableCache}.
 */
class EvictableCacheImpl<K, V> implements EvictableCache<K, V> {
    /**
     * Number of threads in the scheduled thread pool to evict records.
     */
    private static final int EVICT_THREAD_COUNT = 1;
    private static final ScheduledThreadPoolExecutor EXECUTOR;
    /**
     * An implementation that does no caching.
     */
    static final EvictableCache<?, ?> NO_CACHE = new EvictableCache() { };

    static {
        ThreadFactory jf = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread newThread = new Thread(r, getClass().getSimpleName() + "-cachePurge_" + counter.getAndIncrement());
                newThread.setDaemon(true);
                return newThread;
            }
        };
        EXECUTOR = new ScheduledThreadPoolExecutor(EVICT_THREAD_COUNT, jf);
    }

    private final ConcurrentHashMap<K, CacheRecord<K, V>> cacheMap = new ConcurrentHashMap<>();
    private final long cacheTimeoutNanos;
    private final long overallTimeoutNanos;
    private final long cacheMaxSize;
    private final long evictParallelismThreshold;
    private final ScheduledFuture<?> evictionFuture;
    private final BiFunction<K, V, Boolean> evictor;

    EvictableCacheImpl(Builder<K, V> builder) {
        cacheMaxSize = builder.cacheMaxSize();
        cacheTimeoutNanos = TimeUnit.NANOSECONDS.convert(builder.cacheTimeout(), builder.cacheTimeoutUnit());
        overallTimeoutNanos = TimeUnit.NANOSECONDS.convert(builder.overallTimeout(), builder.overallTimeoutUnit());
        evictParallelismThreshold = builder.parallelismThreshold();
        evictor = builder.evictor();

        evictionFuture = EXECUTOR.scheduleAtFixedRate(
                this::evict,
                builder.cacheEvictDelay(),
                builder.cacheEvictPeriod(),
                builder.cacheEvictTimeUnit());
        EXECUTOR.setRemoveOnCancelPolicy(true);
    }

    @Override
    public Optional<V> remove(K key) {
        CacheRecord<K, V> removed = cacheMap.remove(key);
        if (null == removed) {
            return Optional.empty();
        }

        return validate(removed).map(CacheRecord::getValue);
    }

    @Override
    public Optional<V> get(K key) {
        return getRecord(key).flatMap(this::validate).map(CacheRecord::getValue);
    }

    @Override
    public int size() {
        return cacheMap.size();
    }

    @Override
    public Optional<V> computeValue(K key, Supplier<Optional<V>> valueSupplier) {
        try {
            return doComputeValue(key, valueSupplier);
        } catch (CacheFullException e) {
            return valueSupplier.get();
        }
    }

    @Override
    public void close() {
        evictionFuture.cancel(true);
        cacheMap.clear();
    }

    void evict() {
        cacheMap.forEachKey(evictParallelismThreshold, key -> cacheMap.compute(key, (key1, cacheRecord) -> {
            if ((null == cacheRecord) || evictor.apply(cacheRecord.getKey(), cacheRecord.getValue())) {
                return null;
            } else {
                if (cacheRecord.isValid(cacheTimeoutNanos, overallTimeoutNanos)) {
                    return cacheRecord;
                } else {
                    return null;
                }
            }
        }));
    }

    private Optional<CacheRecord<K, V>> validate(CacheRecord<K, V> record) {
        if (record.isValid(cacheTimeoutNanos, overallTimeoutNanos) && !evictor.apply(record.getKey(), record.getValue())) {
            return Optional.of(record);
        }
        cacheMap.remove(record.key);
        return Optional.empty();
    }

    private Optional<V> doComputeValue(K key, Supplier<Optional<V>> valueSupplier) {
        CacheRecord<K, V> record = cacheMap.compute(key, (s, cacheRecord) -> {
            if ((null != cacheRecord) && cacheRecord.isValid(cacheTimeoutNanos, overallTimeoutNanos)) {
                cacheRecord.accessed();
                return cacheRecord;
            }

            if (cacheMap.size() >= cacheMaxSize) {
                throw new CacheFullException();
            }

            return valueSupplier.get()
                    .map(v -> new CacheRecord<>(key, v))
                    .orElse(null);
        });

        if (null == record) {
            return Optional.empty();
        } else {
            return Optional.of(record.value);
        }
    }

    private Optional<CacheRecord<K, V>> getRecord(K key) {
        return Optional.ofNullable(cacheMap.get(key));
    }

    private static final class CacheRecord<K, V> {
        private final K key;
        private final V value;
        private final long created = System.nanoTime();
        private volatile long lastAccess = System.nanoTime();

        private CacheRecord(K key, V value) {
            this.key = key;
            this.value = value;
        }

        private void accessed() {
            lastAccess = System.nanoTime();
        }

        private boolean isValid(long timeoutNanos, long overallTimeout) {
            long nano = System.nanoTime();

            return ((nano - created) < overallTimeout) && ((nano - lastAccess) < timeoutNanos);
        }

        private K getKey() {
            return key;
        }

        private V getValue() {
            return value;
        }
    }

    private static final class CacheFullException extends RuntimeException {
        private CacheFullException() {
        }
    }
}
