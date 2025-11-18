/*
 * Copyright (c) 2018, 2025 Oracle and/or its affiliates.
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
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.LazyValue;
import io.helidon.common.config.Config;
import io.helidon.common.context.Contexts;

/**
 * Supplier of a custom thread pool.
 * The returned thread pool supports {@link io.helidon.common.context.Context} propagation.
 */
public final class ThreadPoolSupplier implements Supplier<ExecutorService>, RuntimeType.Api<ThreadPoolConfig> {
    // this type is used on config blueprint, as the default value for rejection policy
    static final ThreadPool.RejectionHandler DEFAULT_REJECTION_POLICY = new ThreadPool.RejectionHandler();

    private static final System.Logger LOGGER = System.getLogger(ThreadPoolSupplier.class.getName());
    private static final AtomicInteger DEFAULT_NAME_COUNTER = new AtomicInteger();

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
    private final boolean useVirtualThreads;
    private final ThreadPoolConfig config;

    private ThreadPoolSupplier(ThreadPoolConfig config) {
        this.config = config;
        this.corePoolSize = config.corePoolSize();
        this.maxPoolSize = config.maxPoolSize();
        this.keepAliveMinutes = config.keepAlive().toMinutesPart();
        this.queueCapacity = config.queueCapacity();
        this.isDaemon = config.daemon();
        this.prestart = config.shouldPrestart();
        this.growthThreshold = config.growthThreshold();
        this.growthRate = config.growthRate();
        this.rejectionHandler = config.rejectionHandler();
        this.useVirtualThreads = config.virtualThreads();

        String poolName = config.name().orElse(null);
        String threadNamePrefix = config.threadNamePrefix().orElse(null);
        if (poolName == null) {
            if (config.threadNamePrefix().isEmpty()) {
                threadNamePrefix = ThreadPoolConfigBlueprint.DEFAULT_THREAD_NAME_PREFIX;
                LOGGER.log(System.Logger.Level.WARNING, "Neither a thread name prefix nor a pool name specified");
            }
            poolName = threadNamePrefix + "thread-pool-" + DEFAULT_NAME_COUNTER.incrementAndGet();
        } else if (threadNamePrefix == null) {
            threadNamePrefix = ThreadPoolConfigBlueprint.DEFAULT_THREAD_NAME_PREFIX + poolName + "-";
        }

        this.name = poolName;
        this.threadNamePrefix = threadNamePrefix;
        ObserverManager.registerSupplier(this, name, "general");
    }

    /**
     * Create a new fluent API builder to build thread pool supplier.
     *
     * @return a builder instance
     */
    public static ThreadPoolConfig.Builder builder() {
        return ThreadPoolConfig.builder();
    }

    /**
     * Load supplier from configuration.
     *
     * @param config config instance
     * @param name thread pool name
     * @return a new thread pool supplier configured from config
     */
    public static ThreadPoolSupplier create(Config config, String name) {
        return builder().name(name)
                .config(config)
                .build();
    }

    /**
     * Create a new thread pool supplier with default configuration and
     * a given name.
     *
     * @param name thread pool name
     * @return a new thread pool supplier with default configuration
     */
    public static ThreadPoolSupplier create(String name) {
        return builder().name(name).build();
    }

    /**
     * Create a new thread pool supplier based on its configuration.
     *
     * @param config configuration
     * @return a new thread pool supplier
     */
    public static ThreadPoolSupplier create(ThreadPoolConfig config) {
        return new ThreadPoolSupplier(config);
    }

    /**
     * Create a new thread pool supplier customizing its configuration.
     *
     * @param consumer configuration consumer
     * @return a new thread pool supplier
     */
    public static ThreadPoolSupplier create(Consumer<ThreadPoolConfig.Builder> consumer) {
        return builder()
                .update(consumer)
                .build();
    }

    @Override
    public ThreadPoolConfig prototype() {
        return config;
    }

    ExecutorService getThreadPool() {
        if (useVirtualThreads) {
            ThreadFactory factory = Thread.ofVirtual().name(name + "-", 0).factory();
            return ObserverManager.registerExecutorService(this, Executors.newThreadPerTaskExecutor(factory));
        }

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
        return ObserverManager.registerExecutorService(this, result);
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
}
