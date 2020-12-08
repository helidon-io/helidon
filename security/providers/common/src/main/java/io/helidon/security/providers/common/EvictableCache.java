/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.config.Config;

/**
 * Generic cache with eviction and max size.
 *
 * Cache timeouts:
 * <ul>
 *     <li>{@link io.helidon.security.providers.common.EvictableCache.Builder#overallTimeout(long, java.util.concurrent.TimeUnit)}
 *      defines the timeout of record since its creation</li>
 *     <li>{@link io.helidon.security.providers.common.EvictableCache.Builder#timeout(long, java.util.concurrent.TimeUnit)}
 *      defines the timeout of record since last use (a sliding timeout)</li>
 * </ul>
 *
 * @param <K> type of keys in this cache
 * @param <V> type of values in this cache
 */
public interface EvictableCache<K, V> {
    /**
     * Default timeout of records in minutes (inactivity timeout).
     */
    long CACHE_TIMEOUT_MINUTES = 60;
    /**
     * Default eviction period in minutes (how often to evict records).
     */
    long CACHE_EVICT_PERIOD_MINUTES = 5;
    /**
     * Default eviction delay in minutes (how long to wait after the cache is started).
     */
    long CACHE_EVICT_DELAY_MINUTES = 1;
    /**
     * Maximal number of records in the cache.
     * If the cache is full, no caching is done and the supplier of value is called for every uncached value.
     */
    long CACHE_MAX_SIZE = 100_000;
    /**
     * Parameter to {@link ConcurrentHashMap#forEachKey(long, Consumer)} used for eviction.
     */
    long EVICT_PARALLELISM_THRESHOLD = 10000;

    /**
     * Create a new builder for a cache.
     *
     * @param <K> type of keys in the cache
     * @param <V> type of values in the cache
     * @return a builder to build the cache
     */
    static <K, V> Builder<K, V> builder() {
        return new Builder<>();
    }

    /**
     * Create a new cache with default values.
     *
     * @param <K> type of keys in the cache
     * @param <V> type of values in the cache
     * @return new cache built with default values
     */
    static <K, V> EvictableCache<K, V> create() {
        Builder<K, V> builder = builder();
        return builder.build();
    }

    /**
     * Create a new cache and configure it from the provided configuration.
     * See {@link Builder#config(Config)} for the list of configuration keys.
     *
     * @param config config to read configuration of this cache from
     * @param <K>    type of keys in the cache
     * @param <V>    type of values in the cache
     * @return new cache configured from config
     */
    static <K, V> EvictableCache<K, V> create(Config config) {
        Builder<K, V> builder = builder();

        return builder.config(config)
                .build();
    }

    /**
     * Create a new cache that is not a cache (e.g. never caches, delegates
     * all calls to the {@link Supplier} in {@link #computeValue(Object, Supplier)}.
     *
     * @param <K> Type of keys
     * @param <V> Type of values
     * @return a new instance that is not caching
     */
    @SuppressWarnings("unchecked")
    static <K, V> EvictableCache<K, V> noCache() {
        // there is no support for restricted visibility in interface in current java version
        return (EvictableCache<K, V>) EvictableCacheImpl.NO_CACHE;
    }

    /**
     * Remove a key from the cache. Return the value if it was cached and valid.
     *
     * @param key key to remove
     * @return value if it was removed and valid, empty otherwise
     */
    default Optional<V> remove(K key) {
        return Optional.empty();
    }

    /**
     * Get current cached value if valid.
     *
     * @param key key to use
     * @return current value in the cache or empty if not present (or invalid)
     */
    default Optional<V> get(K key) {
        return Optional.empty();
    }

    /**
     * Current size of the cache.
     * As this cache is using {@link ConcurrentHashMap} as backing store, be aware that this value is not
     * guaranteed to be consistent, as puts and removed may be happening in parallel.
     *
     * @return current size of the cache (including valid and invalid - not yet evicted - values)
     */
    default int size() {
        return 0;
    }

    /**
     * Either return a cached value or compute it and cache it.
     *
     * @param key           key to check/insert value for
     * @param valueSupplier supplier called if the value is not yet cached, or is invalid
     * @return current value from the cache, or computed value from the supplier
     */
    default Optional<V> computeValue(K key, Supplier<Optional<V>> valueSupplier) {
        return valueSupplier.get();
    }

    /**
     * Close this cache.
     * Carry out shutdown tasks (e.g. shutting down eviction thread).
     */
    default void close() {
    }

    /**
     * Builder to create instances of {@link EvictableCache}.
     *
     * @param <K> types of keys used in the cache
     * @param <V> types of values used in the cache
     */
    class Builder<K, V> implements io.helidon.common.Builder<EvictableCache<K, V>> {
        private boolean cacheEnabled = true;
        private long cacheTimeout = CACHE_TIMEOUT_MINUTES;
        private TimeUnit cacheTimeoutUnit = TimeUnit.MINUTES;
        private long overallTimeout = CACHE_TIMEOUT_MINUTES;
        private TimeUnit overallTimeoutUnit = TimeUnit.MINUTES;
        private long cacheMaxSize = CACHE_MAX_SIZE;
        private long cacheEvictDelay = CACHE_EVICT_DELAY_MINUTES;
        private long cacheEvictPeriod = CACHE_EVICT_PERIOD_MINUTES;
        private TimeUnit cacheEvictTimeUnit = TimeUnit.MINUTES;
        private long parallelismThreshold = EVICT_PARALLELISM_THRESHOLD;
        private BiFunction<K, V, Boolean> evictor = (key, value) -> false;

        /**
         * Build a new instance of the cache based on configuration of this builder.
         *
         * @return a new instance of the cache
         */
        @Override
        public EvictableCache<K, V> build() {
            if (cacheEnabled) {
                return new EvictableCacheImpl<>(this);
            } else {
                return noCache();
            }
        }

        /**
         * Configure record timeout since last access.
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
         * Configure record timeout since its creation.
         *
         * @param timeout timeout value
         * @param timeoutUnit timeout unit
         * @return updated builder instance
         */
        public Builder<K, V> overallTimeout(long timeout, TimeUnit timeoutUnit) {
            this.overallTimeout = timeout;
            this.overallTimeoutUnit = timeoutUnit;
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
         * Configure evictor to check if a record is still valid.
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

        /**
         * If the cacheEnabled is set to false, no caching will be done.
         * Otherwise (default behavior) evictable caching will be used.
         *
         * @param cacheEnabled whether to enable this cache or not (true - enabled by default)
         * @return updated builder instance
         */
        public Builder<K, V> cacheEnabled(boolean cacheEnabled) {
            this.cacheEnabled = cacheEnabled;
            return this;
        }

        /**
         * Update this builder from configuration.
         *
         * Options expected under the current config node:
         * <table border="1">
         * <caption>Configuration parameters</caption>
         * <tr><th>key</th><th>default value</th><th>description</th></tr>
         * <tr><td>cache-enabled</td><td>true</td><td>Set to false to fully disable caching (and ignore other parameters)
         * </td></tr>
         * <tr><td>cache-timeout-millis</td><td>{@value #CACHE_TIMEOUT_MINUTES} minutes</td><td>Timeout of records in the cache
         * in milliseconds</td></tr>
         * <tr><td>cache-evict-delay-millis</td><td>{@value #CACHE_EVICT_DELAY_MINUTES} minutes</td><td>How long to wait with
         * eviction after the cache is created in milliseconds</td></tr>
         * <tr><td>cache-evict-period-millis</td><td>{@value #CACHE_EVICT_PERIOD_MINUTES} minutes</td><td>How often to evict
         * records from the cache in milliseconds</td></tr>
         * <tr><td>parallelism-treshold</td><td>{@value #EVICT_PARALLELISM_THRESHOLD}</td><td>see
         * {@link #parallelismThreshold(long)}</td></tr>
         * <tr><td>evictor-class</td><td></td><td>A class that is instantiated and used as an evictor for this instance</td></tr>
         * </table>
         *
         * @param config Config to use to load configuration options for this builder
         * @return updated builder instance
         */
        public Builder<K, V> config(Config config) {
            config.get("cache-enabled").asBoolean().ifPresent(this::cacheEnabled);
            if (cacheEnabled) {
                config.get("cache-timeout-millis").asLong().ifPresent(timeout -> timeout(timeout, TimeUnit.MILLISECONDS));
                config.get("cache-overall-timeout-millis").asLong()
                        .ifPresent(timeout -> overallTimeout(timeout, TimeUnit.MILLISECONDS));
                long evictDelay = config.get("cache-evict-delay-millis").asLong()
                        .orElse(cacheEvictTimeUnit.toMillis(cacheEvictDelay));
                long evictPeriod = config.get("cache-evict-period-millis").asLong()
                        .orElse(cacheEvictTimeUnit.toMillis(cacheEvictPeriod));
                evictSchedule(evictDelay, evictPeriod, TimeUnit.MILLISECONDS);
                config.get("parallelism-treshold").asLong().ifPresent(this::parallelismThreshold);
                config.get("evictor-class").as(Class.class).ifPresent(this::evictorClass);
            }

            return this;
        }

        @SuppressWarnings("unchecked")
        private Builder<K, V> evictorClass(Class<?> aClass) {
            // attempt to create an instance
            try {
                aClass.getMethod("apply", Object.class, Object.class);
                Object anObject = aClass.getConstructor().newInstance();
                evictor((BiFunction<K, V, Boolean>) anObject);
            } catch (ReflectiveOperationException e) {
                throw new SecurityException("Failed to create an evictor instance. Configured class: " + aClass.getName(), e);
            }
            return this;
        }

        long cacheTimeout() {
            return cacheTimeout;
        }

        TimeUnit cacheTimeoutUnit() {
            return cacheTimeoutUnit;
        }

        long overallTimeout() {
            return overallTimeout;
        }

        TimeUnit overallTimeoutUnit() {
            return overallTimeoutUnit;
        }

        long cacheMaxSize() {
            return cacheMaxSize;
        }

        long cacheEvictDelay() {
            return cacheEvictDelay;
        }

        long cacheEvictPeriod() {
            return cacheEvictPeriod;
        }

        TimeUnit cacheEvictTimeUnit() {
            return cacheEvictTimeUnit;
        }

        long parallelismThreshold() {
            return parallelismThreshold;
        }

        BiFunction<K, V, Boolean> evictor() {
            return evictor;
        }
    }
}
