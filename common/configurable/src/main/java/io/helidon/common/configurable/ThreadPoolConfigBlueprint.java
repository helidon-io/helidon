/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import java.time.Duration;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

@Prototype.Blueprint
@Prototype.Configured
interface ThreadPoolConfigBlueprint extends Prototype.Factory<ThreadPoolSupplier> {
    /**
     * Default core pool size ({@value}).
     *
     * @see #corePoolSize()
     */
    int DEFAULT_CORE_POOL_SIZE = 10;
    /**
     * Default max pool size ({@value}).
     *
     * @see #maxPoolSize()
     */
    int DEFAULT_MAX_POOL_SIZE = 50;
    /**
     * Default keep alive (duration format - {@value}).
     *
     * @see #keepAlive()
     */
    String DEFAULT_KEEP_ALIVE = "PT3M";
    /**
     * Default queue capacity ({@value}).
     *
     * @see #queueCapacity()
     */
    int DEFAULT_QUEUE_CAPACITY = 10000;
    /**
     * Default is daemon ({@value}).
     *
     * @see #daemon()
     */
    boolean DEFAULT_IS_DAEMON = true;
    /**
     * Default thread name prefix ({@value}).
     *
     * @see #threadNamePrefix()
     */
    String DEFAULT_THREAD_NAME_PREFIX = "helidon-";
    /**
     * Default prestart of threads ({@value}).
     *
     * @see #shouldPrestart()
     */
    boolean DEFAULT_PRESTART = true;
    /**
     * Default growth rate ({@value}).
     *
     * @see #growthRate()
     */
    int DEFAULT_GROWTH_RATE = 0; // Maintain JDK pool behavior when max > core
    /**
     * Default growth threshold ({@value}).
     *
     * @see #growthThreshold()
     */
    int DEFAULT_GROWTH_THRESHOLD = 1000;

    /**
     * When configured to {@code true}, an unbounded virtual executor service (project Loom) will be used.
     * <p>
     * If enabled, all other configuration options of this executor service are ignored!
     *
     * @return whether to use virtual threads or not, defaults to {@code false}
     */
    @Option.Configured
    boolean virtualThreads();

    /**
     * Core pool size of the thread pool executor.
     * Defaults to {@value #DEFAULT_CORE_POOL_SIZE}.
     *
     * @return corePoolSize see {@link java.util.concurrent.ThreadPoolExecutor#getCorePoolSize()}
     */
    @Option.DefaultInt(DEFAULT_CORE_POOL_SIZE)
    @Option.Configured
    int corePoolSize();

    /**
     * Max pool size of the thread pool executor.
     * Defaults to {@value #DEFAULT_MAX_POOL_SIZE}.
     *
     * @return maxPoolSize see {@link java.util.concurrent.ThreadPoolExecutor#getMaximumPoolSize()}
     */
    @Option.DefaultInt(DEFAULT_MAX_POOL_SIZE)
    @Option.Configured
    int maxPoolSize();

    /**
     * Keep alive of the thread pool executor.
     * Defaults to {@value #DEFAULT_KEEP_ALIVE}.
     *
     * @return keep alive see {@link java.util.concurrent.ThreadPoolExecutor#getKeepAliveTime(java.util.concurrent.TimeUnit)}
     */
    @Option.Default(DEFAULT_KEEP_ALIVE)
    @Option.Configured
    Duration keepAlive();

    /**
     * Queue capacity of the thread pool executor.
     * Defaults to {@value #DEFAULT_QUEUE_CAPACITY}.
     *
     * @return capacity of the queue backing the executor
     */
    @Option.DefaultInt(DEFAULT_QUEUE_CAPACITY)
    @Option.Configured
    int queueCapacity();

    /**
     * Is daemon of the thread pool executor.
     * Defaults to {@value #DEFAULT_IS_DAEMON}.
     *
     * @return whether the threads are daemon threads
     */
    @Option.DefaultBoolean(DEFAULT_IS_DAEMON)
    @Option.Configured("is-daemon")
    boolean daemon();

    /**
     * Name of this thread pool executor.
     *
     * @return the pool name
     */
    @Option.Configured
    Optional<String> name();

    /**
     * The queue size above which pool growth will be considered if the pool is not fixed size.
     * Defaults to {@value #DEFAULT_GROWTH_THRESHOLD}.
     *
     * @return the growth threshold
     */
    @Option.DefaultInt(DEFAULT_GROWTH_THRESHOLD)
    @Option.Configured
    int growthThreshold();

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
     * <p>
     * Defaults to {@value #DEFAULT_GROWTH_RATE}
     *
     * @return the growth rate
     */
    @Option.DefaultInt(DEFAULT_GROWTH_RATE)
    @Option.Configured
    int growthRate();

    /**
     * Rejection policy of the thread pool executor.
     *
     * @return the rejection handler
     */
    @Option.DefaultCode("ThreadPoolSupplier.DEFAULT_REJECTION_POLICY")
    ThreadPool.RejectionHandler rejectionHandler();

    /**
     * Name prefix for threads in this thread pool executor.
     * Defaults to {@value #DEFAULT_THREAD_NAME_PREFIX}.
     *
     * @return prefix of a thread name
     */
    @Option.Configured
    Optional<String> threadNamePrefix();

    /**
     * Whether to prestart core threads in this thread pool executor.
     * Defaults to {@value #DEFAULT_PRESTART}.
     *
     * @return whether to prestart the threads
     */
    @Option.DefaultBoolean(DEFAULT_PRESTART)
    @Option.Configured
    boolean shouldPrestart();
}
