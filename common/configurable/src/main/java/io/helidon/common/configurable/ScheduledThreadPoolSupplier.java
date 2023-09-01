/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.LazyValue;
import io.helidon.common.config.Config;
import io.helidon.common.context.Contexts;

/**
 * Supplier of a custom scheduled thread pool.
 * The returned thread pool supports {@link io.helidon.common.context.Context} propagation.
 */
@RuntimeType.PrototypedBy(ScheduledThreadPoolConfig.class)
public final class ScheduledThreadPoolSupplier implements Supplier<ScheduledExecutorService>,
                                                          RuntimeType.Api<ScheduledThreadPoolConfig> {

    private final int corePoolSize;
    private final boolean isDaemon;
    private final String threadNamePrefix;
    private final boolean prestart;
    private final LazyValue<ScheduledExecutorService> lazyValue = LazyValue.create(() -> Contexts.wrap(getThreadPool()));
    private final ScheduledThreadPoolConfig config;

    private ScheduledThreadPoolSupplier(ScheduledThreadPoolConfig config) {
        this.config = config;
        this.corePoolSize = config.corePoolSize();
        this.isDaemon = config.daemon();
        this.threadNamePrefix = config.threadNamePrefix();
        this.prestart = config.prestart();
        ObserverManager.registerSupplier(this, "scheduled", threadNamePrefix);
    }

    /**
     * Create a new fluent API builder to build thread pool supplier.
     *
     * @return a builder instance
     */
    public static ScheduledThreadPoolConfig.Builder builder() {
        return ScheduledThreadPoolConfig.builder();
    }

    /**
     * Load supplier from configuration.
     *
     * @param config config instance
     * @return a new thread pool supplier configured from config
     */
    public static ScheduledThreadPoolSupplier create(Config config) {
        return builder().config(config)
                .build();
    }

    /**
     * Create a new thread pool supplier with default configuration.
     *
     * @return a new thread pool supplier with default configuration
     */
    public static ScheduledThreadPoolSupplier create() {
        return builder().build();
    }

    /**
     * Create a new instance from programmatic configuration.
     *
     * @param config configuration
     * @return a new thread pool supplier
     */
    public static ScheduledThreadPoolSupplier create(ScheduledThreadPoolConfig config) {
        return new ScheduledThreadPoolSupplier(config);
    }

    /**
     * Create a new instance customizing its configuration.
     *
     * @param consumer configuration consumer
     * @return a new thread pool supplier
     */
    public static ScheduledThreadPoolSupplier create(Consumer<ScheduledThreadPoolConfig.Builder> consumer) {
        return ScheduledThreadPoolConfig.builder()
                .update(consumer)
                .build();
    }

    @Override
    public ScheduledThreadPoolConfig prototype() {
        return config;
    }

    /**
     * Returns size of core pool.
     *
     * @return size of core pool.
     */
    public int corePoolSize() {
        return corePoolSize;
    }

    ScheduledThreadPoolExecutor getThreadPool() {
        ScheduledThreadPoolExecutor result;
        result = new ScheduledThreadPoolExecutor(corePoolSize,
                                                 new ThreadFactory() {
                                                     private final AtomicInteger value = new AtomicInteger();

                                                     @Override
                                                     public Thread newThread(Runnable r) {
                                                         Thread t = new Thread(null,
                                                                               r,
                                                                               threadNamePrefix + value.incrementAndGet());
                                                         t.setDaemon(isDaemon);
                                                         return t;
                                                     }
                                                 });
        if (prestart) {
            result.prestartAllCoreThreads();
        }
        ObserverManager.registerExecutorService(this, result);
        return result;
    }

    @Override
    public ScheduledExecutorService get() {
        return lazyValue.get();
    }
}
