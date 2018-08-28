/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.providers;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Generic cache with eviction and max size.
 *
 * @param <K> type of keys in this cache
 * @param <V> type of values in this cache
 */
public final class EvictableCache<K, V> {
    /**
     * Number of threads in the scheduled thread pool to evict records.
     */
    public static final int EVICT_THREAD_COUNT = 1;
    /**
     * Default timeout of records in minutes (inactivity timeout).
     */
    public static final long CACHE_TIMEOUT_MINUTES = 60;
    /**
     * Default eviction period in minutes (how often to evict records).
     */
    public static final long CACHE_EVICT_PERIOD_MINUTES = 5;
    /**
     * Default eviction delay in minutes (how long to wait after the cache is started).
     */
    public static final long CACHE_EVICT_DELAY_MINUTES = 1;
    /**
     * Maximal number of records in the cache.
     * If the cache is full, no caching is done and the supplier of value is called for every uncached value.
     */
    public static final long CACHE_MAX_SIZE = 100_000;
    /**
     * Parameter to {@link ConcurrentHashMap#forEachKey(long, Consumer)} used for eviction.
     */
    public static final long EVICT_PARALLELISM_THRESHOLD = 10000;

    private static final ScheduledThreadPoolExecutor EXECUTOR;

    static {
        ThreadFactory jf = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, getClass().getSimpleName() + "-cachePurge_" + counter.getAndIncrement());
            }
        };
        EXECUTOR = new ScheduledThreadPoolExecutor(EVICT_THREAD_COUNT, jf);
    }

    private final ConcurrentHashMap<K, CacheRecord<K, V>> cacheMap = new ConcurrentHashMap<>();
    private final long cacheTimoutNanos;
    private final long cacheMaxSize;
    private final long evictParallelismThreshold;
    private final ScheduledFuture<?> evictionFuture;
    private final BiFunction<K, V, Boolean> evictor;

    private EvictableCache(Builder<K, V> builder) {
        cacheMaxSize = builder.cacheMaxSize;
        cacheTimoutNanos = TimeUnit.NANOSECONDS.convert(builder.cacheTimeout, builder.cacheTimeoutUnit);
        evictParallelismThreshold = builder.parallelismThreshold;
        evictor = builder.evictor;

        evictionFuture = EXECUTOR.scheduleAtFixedRate(
                this::evict,
                builder.cacheEvictDelay,
                builder.cacheEvictPeriod,
                builder.cacheEvictTimeUnit);
        EXECUTOR.setRemoveOnCancelPolicy(true);
    }

    /**
     * Create a new builder for a cache.
     *
     * @param <K> type of keys in the cache
     * @param <V> type of values in the cache
     * @return a builder to build the cache
     */
    public static <K, V> Builder<K, V> builder() {
        return new Builder<>();
    }

    /**
     * Create a new cache with default values.
     *
     * @param <K> type of keys in the cache
     * @param <V> type of values in the cache
     * @return new cache built with default values
     */
    public static <K, V> EvictableCache<K, V> create() {
        Builder<K, V> builder = builder();
        return builder.build();
    }

    void evict() {
        cacheMap.forEachKey(evictParallelismThreshold, key -> cacheMap.compute(key, (key1, cacheRecord) -> {
            if ((null == cacheRecord) || evictor.apply(cacheRecord.getKey(), cacheRecord.getValue())) {
                return null;
            } else {
                if (cacheRecord.isValid(cacheTimoutNanos)) {
                    return cacheRecord;
                } else {
                    return null;
                }
            }
        }));
    }

    /**
     * Remove a key from the cache. Return the value if it was cached and valid.
     *
     * @param key key to remove
     * @return value if it was removed and valid, empty otherwise
     */
    public Optional<V> remove(K key) {
        CacheRecord<K, V> removed = cacheMap.remove(key);
        if (null == removed) {
            return Optional.empty();
        }

        return validate(removed).map(CacheRecord::getValue);
    }

    /**
     * Get current cached value if valid.
     *
     * @param key key to use
     * @return current value in the cache or empty if not present (or invalid)
     */
    public Optional<V> get(K key) {
        return getRecord(key).flatMap(this::validate).map(CacheRecord::getValue);
    }

    private Optional<CacheRecord<K, V>> validate(CacheRecord<K, V> record) {
        if (record.isValid(cacheTimoutNanos) && !evictor.apply(record.getKey(), record.getValue())) {
            return Optional.of(record);
        }
        cacheMap.remove(record.key);
        return Optional.empty();
    }

    /**
     * Current size of the cache.
     * As this cache is using {@link ConcurrentHashMap} as backing store, be aware that this value is not
     * guaranteed to be consistent, as puts and removed may be happening in parallel.
     *
     * @return current size of the cache (including valid and invalid - not yet evicted - values)
     */
    public int size() {
        return cacheMap.size();
    }

    /**
     * Either return a cached value or compute it and cache it.
     *
     * @param key           key to check/insert value for
     * @param valueSupplier supplier called if the value is not yet cached, or is invalid
     * @return current value from the cache, or computed value from the supplier
     */
    public Optional<V> computeValue(K key, Supplier<Optional<V>> valueSupplier) {
        try {
            return doComputeValue(key, valueSupplier);
        } catch (CacheFullException e) {
            return valueSupplier.get();
        }
    }

    /**
     * Close this cache.
     * Cancels eviction future and clears the cache.
     */
    public void close() {
        evictionFuture.cancel(true);
        cacheMap.clear();
    }

    private Optional<V> doComputeValue(K key, Supplier<Optional<V>> valueSupplier) {
        CacheRecord<K, V> record = cacheMap.compute(key, (s, cacheRecord) -> {
            if ((null != cacheRecord) && cacheRecord.isValid(cacheTimoutNanos)) {
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
        private volatile long lastAccess = System.nanoTime();

        private CacheRecord(K key, V value) {
            this.key = key;
            this.value = value;
        }

        private void accessed() {
            lastAccess = System.nanoTime();
        }

        private boolean isValid(long timeoutNanos) {
            return (System.nanoTime() - lastAccess) < timeoutNanos;
        }

        private K getKey() {
            return key;
        }

        private V getValue() {
            return value;
        }
    }

    /**
     * Builder to create instances of {@link EvictableCache}.
     *
     * @param <K> types of keys used in the cache
     * @param <V> types of values used in the cache
     */
    public static class Builder<K, V> {
        private long cacheTimeout = CACHE_TIMEOUT_MINUTES;
        private long cacheMaxSize = CACHE_MAX_SIZE;
        private TimeUnit cacheTimeoutUnit = TimeUnit.MINUTES;
        private long cacheEvictDelay = CACHE_EVICT_DELAY_MINUTES;
        private long cacheEvictPeriod = CACHE_EVICT_PERIOD_MINUTES;
        private TimeUnit cacheEvictTimeUnit = TimeUnit.MINUTES;
        private long parallelismThreshold = EVICT_PARALLELISM_THRESHOLD;
        private BiFunction<K, V, Boolean> evictor = (key, value) -> false;

        /**
         * Build a new instance of the cache configured from this builder.
         *
         * @param <K> types of keys used in the cache
         * @param <V> types of values used in the cache
         * @return new cache instance
         */
        @SuppressWarnings("unchecked")
        public <K, V> EvictableCache<K, V> build() {
            return new EvictableCache(this);
        }

        /**
         * Configure record timeout since last modification.
         *
         * @param timeout     timeout value
         * @param timeoutUnit timeout unit
         * @return updated builder instance
         */
        public Builder<K, V> timeout(long timeout, TimeUnit timeoutUnit) {
            this.cacheTimeout = timeout;
            this.cacheTimeoutUnit = timeoutUnit;
            return this;
        }

        /**
         * Configure maximal cache size.
         *
         * @param cacheMaxSize maximal number of records to store in the cache
         * @return updated builder instance
         */
        public Builder<K, V> maxSize(long cacheMaxSize) {
            this.cacheMaxSize = cacheMaxSize;
            return this;
        }

        /**
         * Configure eviction scheduling.
         *
         * @param evictDelay    delay from the creation of the cache to first eviction
         * @param evictPeriod   how often to evict records
         * @param evictTimeUnit time unit to use for these values
         * @return updated builder instance
         */
        public Builder<K, V> evictSchedule(long evictDelay, long evictPeriod, TimeUnit evictTimeUnit) {
            this.cacheEvictDelay = evictDelay;
            this.cacheEvictPeriod = evictPeriod;
            this.cacheEvictTimeUnit = evictTimeUnit;
            return this;
        }

        /**
         * Configure parallelism threshold.
         *
         * @param parallelismThreshold see {@link ConcurrentHashMap#forEachKey(long, Consumer)}
         * @return updated builder instance
         */
        public Builder<K, V> parallelismThreshold(long parallelismThreshold) {
            this.parallelismThreshold = parallelismThreshold;
            return this;
        }

        /**
         * Configure evictor to check if a records is still valid.
         * This should be a fast way to check, as it is happening in a {@link ConcurrentHashMap#forEachKey(long, Consumer)}.
         * This is also called during all get and remove operations to only return valid records.
         *
         * @param evictor evictor to use, return {@code true} for records that should be evicted, {@code false} for records
         *                that should stay in cache
         * @return updated builder instance
         */
        public Builder<K, V> evictor(BiFunction<K, V, Boolean> evictor) {
            this.evictor = evictor;
            return this;
        }
    }

    private static final class CacheFullException extends RuntimeException {
        private CacheFullException() {
        }
    }
}
