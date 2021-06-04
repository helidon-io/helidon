/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.common.configurable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Logger;

import io.helidon.common.LazyValue;
import io.helidon.common.context.Contexts;
import io.helidon.config.Config;

/**
 * Supplier of a custom thread pool.
 * The returned thread pool supports {@link io.helidon.common.context.Context} propagation.
 */
public final class ThreadPoolSupplier implements Supplier<ExecutorService> {
    private static final Logger LOGGER = Logger.getLogger(ThreadPoolSupplier.class.getName());
    private static final ThreadPool.RejectionHandler DEFAULT_REJECTION_POLICY = new ThreadPool.RejectionHandler();
    private static final AtomicInteger DEFAULT_NAME_COUNTER = new AtomicInteger();
    private static final int DEFAULT_CORE_POOL_SIZE = 10;
    private static final int DEFAULT_MAX_POOL_SIZE = 50;
    private static final int DEFAULT_KEEP_ALIVE_MINUTES = 3;
    private static final int DEFAULT_QUEUE_CAPACITY = 10000;
    private static final boolean DEFAULT_IS_DAEMON = true;
    private static final String DEFAULT_THREAD_NAME_PREFIX = "helidon-";
    private static final boolean DEFAULT_PRESTART = true;
    private static final String DEFAULT_POOL_NAME_PREFIX = "helidon-thread-pool-";
    private static final int DEFAULT_GROWTH_RATE = 0; // Maintain JDK pool behavior when max > core
    private static final int DEFAULT_GROWTH_THRESHOLD = 1000;

    private final int corePoolSize;
    private final int maxPoolSize;
    private final int keepAliveMinutes;
    private final int queueCapacity;
    private final boolean isDaemon;
    private final String threadNamePrefix;
    private final boolean prestart;
    private final String name;
    private final int growthThreshold;
    private final int growthRate;
    private final ThreadPool.RejectionHandler rejectionHandler;
    private final LazyValue<ExecutorService> lazyValue = LazyValue.create(() -> Contexts.wrap(getThreadPool()));

    private ThreadPoolSupplier(Builder builder) {
        this.corePoolSize = builder.corePoolSize;
        this.maxPoolSize = builder.maxPoolSize;
        this.keepAliveMinutes = builder.keepAliveMinutes;
        this.queueCapacity = builder.queueCapacity;
        this.isDaemon = builder.isDaemon;
        this.threadNamePrefix = builder.threadNamePrefix;
        this.prestart = builder.prestart;
        this.name = builder.name == null ? DEFAULT_POOL_NAME_PREFIX + DEFAULT_NAME_COUNTER.incrementAndGet() : builder.name;
        this.growthThreshold = builder.growthThreshold;
        this.growthRate = builder.growthRate;
        this.rejectionHandler = builder.rejectionHandler == null ? DEFAULT_REJECTION_POLICY : builder.rejectionHandler;
    }

    /**
     * Create a new fluent API builder to build thread pool supplier.
     *
     * @return a builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Load supplier from configuration.
     *
     * @param config config instance
     * @return a new thread pool supplier configured from config
     */
    public static ThreadPoolSupplier create(Config config) {
        return builder().config(config)
                .build();
    }

    /**
     * Create a new thread pool supplier with default configuration.
     *
     * @return a new thread pool supplier with default configuration
     */
    public static ThreadPoolSupplier create() {
        return builder().build();
    }

    ThreadPool getThreadPool() {
        ThreadPool result = ThreadPool.create(name,
                                              corePoolSize,
                                              maxPoolSize,
                                              growthThreshold,
                                              growthRate,
                                              keepAliveMinutes,
                                              TimeUnit.MINUTES,
                                              queueCapacity,
                                              threadNamePrefix,
                                              isDaemon,
                                              rejectionHandler);
        if (prestart) {
            result.prestartAllCoreThreads();
        }
        return result;
    }

    @Override
    public ExecutorService get() {
        return lazyValue.get();
    }

    /**
     * Returns size of core pool.
     *
     * @return size of core pool.
     */
    public int corePoolSize() {
        return corePoolSize;
    }

    /**
     * A fluent API builder for {@link ThreadPoolSupplier}.
     */
    public static final class Builder implements io.helidon.common.Builder<ThreadPoolSupplier> {
        private int corePoolSize = DEFAULT_CORE_POOL_SIZE;
        private int maxPoolSize = DEFAULT_MAX_POOL_SIZE;
        private int keepAliveMinutes = DEFAULT_KEEP_ALIVE_MINUTES;
        private int queueCapacity = DEFAULT_QUEUE_CAPACITY;
        private boolean isDaemon = DEFAULT_IS_DAEMON;
        private String threadNamePrefix = DEFAULT_THREAD_NAME_PREFIX;
        private boolean prestart = DEFAULT_PRESTART;
        private int growthThreshold = DEFAULT_GROWTH_THRESHOLD;
        private int growthRate = DEFAULT_GROWTH_RATE;
        private ThreadPool.RejectionHandler rejectionHandler = DEFAULT_REJECTION_POLICY;
        private String name;

        private Builder() {
        }

        @Override
        public ThreadPoolSupplier build() {
            if (name == null) {
                name = DEFAULT_POOL_NAME_PREFIX + DEFAULT_NAME_COUNTER.incrementAndGet();
            }
            if (rejectionHandler == null) {
                rejectionHandler = DEFAULT_REJECTION_POLICY;
            }

            return new ThreadPoolSupplier(this);
        }

        /**
         * Core pool size of the thread pool executor.
         *
         * @param corePoolSize see {@link ThreadPoolExecutor#getCorePoolSize()}
         * @return updated builder instance
         */
        public Builder corePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
            return this;
        }

        /**
         * Max pool size of the thread pool executor.
         *
         * @param maxPoolSize see {@link ThreadPoolExecutor#getMaximumPoolSize()}
         * @return updated builder instance
         */
        public Builder maxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
            return this;
        }

        /**
         * Keep alive minutes of the thread pool executor.
         *
         * @param keepAliveMinutes see {@link ThreadPoolExecutor#getKeepAliveTime(TimeUnit)}
         * @return updated builder instance
         */
        public Builder keepAliveMinutes(int keepAliveMinutes) {
            this.keepAliveMinutes = keepAliveMinutes;
            return this;
        }

        /**
         * Queue capacity of the thread pool executor.
         *
         * @param queueCapacity capacity of the queue backing the executor
         * @return updated builder instance
         */
        public Builder queueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
            return this;
        }

        /**
         * Is daemon of the thread pool executor.
         *
         * @param daemon whether the threads are daemon threads
         * @return updated builder instance
         */
        public Builder daemon(boolean daemon) {
            isDaemon = daemon;
            return this;
        }

        /**
         * Name of this thread pool executor.
         *
         * @param name the pool name
         * @return updated builder instance
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * The queue size above which pool growth will be considered if the pool is not fixed size.
         *
         * @param growthThreshold the growth threshold
         * @return updated builder instance
         */
        Builder growthThreshold(int growthThreshold) {
            this.growthThreshold = growthThreshold;
            return this;
        }

        /**
         * The percentage of task submissions that should result in adding threads, expressed as a value from 1 to 100. The
         * rate applies only when all of the following are true:
         * <ul>
         * <li>the pool size is below the maximum, and</li>
         * <li>there are no idle threads, and</li>
         * <li>the number of tasks in the queue exceeds the {@code growthThreshold}</li>
         * </ul>
         * For example, a rate of 20 means that while these conditions are met one thread will be added for every 5 submitted
         * tasks.
         *
         * @param growthRate the growth rate
         * @return updated builder instance
         */
        Builder growthRate(int growthRate) {
            this.growthRate = growthRate;
            return this;
        }

        /**
         * Rejection policy of the thread pool executor.
         *
         * @param rejectionHandler the rejection policy
         * @return updated builder instance
         */
        public Builder rejectionHandler(ThreadPool.RejectionHandler rejectionHandler) {
            this.rejectionHandler = rejectionHandler;
            return this;
        }

        /**
         * Name prefix for threads in this thread pool executor.
         *
         * @param threadNamePrefix prefix of a thread name
         * @return updated builder instance
         */
        public Builder threadNamePrefix(String threadNamePrefix) {
            this.threadNamePrefix = threadNamePrefix;
            return this;
        }

        /**
         * Whether to prestart core threads in this thread pool executor.
         *
         * @param prestart whether to prestart the threads
         * @return updated builder instance
         */
        public Builder prestart(boolean prestart) {
            this.prestart = prestart;
            return this;
        }

        /**
         * Load all properties for this thread pool from configuration.
         * <br>
         * <table class="config">
         * <caption>Optional Configuration Parameters</caption>
         * <tr>
         *     <th>key</th>
         *     <th>default value</th>
         *     <th>description</th>
         * </tr>
         * <tr>
         *     <td>core-pool-size</td>
         *     <td>10</td>
         *     <td>The number of threads to keep in the pool.</td>
         * </tr>
         * <tr>
         *     <td>max-pool-size</td>
         *     <td>50</td>
         *     <td>The maximum number of threads to allow in the pool.</td>
         * </tr>
         * <tr>
         *     <td>keep-alive-minutes</td>
         *     <td>3</td>
         *     <td>When the number of threads is greater than the core, this is the maximum time that excess idle threads
         *     will wait for new tasks before terminating.</td>
         * </tr>
         * <tr>
         *     <td>queue-capacity</td>
         *     <td>10000</td>
         *     <td>The maximum number of tasks that the queue can contain before new tasks are rejected.</td>
         * </tr>
         * <tr>
         *     <td>is-daemon</td>
         *     <td>{@code true}</td>
         *     <td>Whether or not all threads in the pool should be set as daemon.</td>
         * </tr>
         * <tr>
         *     <td>thread-name-prefix</td>
         *     <td>{@code "helidon-"}</td>
         *     <td>The prefix used to generate names for new threads.</td>
         * </tr>
         * <tr>
         *     <td>should-prestart</td>
         *     <td>{@code true}</td>
         *     <td>Whether or not all core threads should be started when the pool is created.</td>
         * </tr>
         * </table>
         * <br>
         * <table class="config">
         * <caption>Experimental Configuration Parameters (<em>subject to change</em>)</caption>
         * <tr>
         *     <th>key</th>
         *     <th>default value</th>
         *     <th>description</th>
         * </tr>
         * <tr>
         *     <td>growth-threshold</td>
         *     <td>256</td>
         *     <td>The queue size above which pool growth will be considered if the pool is not fixed size.</td>
         * </tr>
         * <tr>
         *     <td>growth-rate</td>
         *     <td>5</td>
         *     <td>The percentage of task submissions that should result in adding a thread, expressed as a value from 0 to 100.
         *     For non-zero values the rate is applied when all of the following are true:
         *     <ul>
         *     <li>the pool size is below the maximum, and</li>
         *     <li>there are no idle threads, and</li>
         *     <li>the number of tasks in the queue exceeds the {@code growthThreshold}</li>
         *     </ul>
         *     <p>For example, a rate of 20 means that while these conditions are met one thread will be added for every 5
         *     submitted
         *     tasks.
         *     <p>A rate of 0 selects the default {@link ThreadPoolExecutor} growth behavior: a thread is added only when a
         *     submitted task is rejected because the queue is full.</td>
         * </tr>
         * </table>
         *
         * @param config config located on the key of executor-service
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get("core-pool-size").asInt().ifPresent(this::corePoolSize);
            config.get("max-pool-size").asInt().ifPresent(this::maxPoolSize);
            config.get("keep-alive-minutes").asInt().ifPresent(this::keepAliveMinutes);
            config.get("queue-capacity").asInt().ifPresent(this::queueCapacity);
            config.get("is-daemon").asBoolean().ifPresent(this::daemon);
            config.get("thread-name-prefix").asString().ifPresent(this::threadNamePrefix);
            config.get("should-prestart").asBoolean().ifPresent(this::prestart);
            config.get("growth-threshold").asInt().ifPresent(value -> {
                warnExperimental("growth-threshold");
                growthThreshold(value);
            });
            config.get("growth-rate").asInt().ifPresent(value -> {
                warnExperimental("growth-rate");
                growthRate(value);

            });
            return this;
        }

        private void warnExperimental(String key) {
            LOGGER.warning(String.format("Config key \"executor-service.%s\" is EXPERIMENTAL and subject to change.", key));
        }
    }
}
