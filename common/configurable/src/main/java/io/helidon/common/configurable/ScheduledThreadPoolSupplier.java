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

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.context.Contexts;
import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Supplier of a custom scheduled thread pool.
 * The returned thread pool supports {@link io.helidon.common.context.Context} propagation.
 */
public final class ScheduledThreadPoolSupplier implements Supplier<ScheduledExecutorService> {
    private static final int EXECUTOR_DEFAULT_CORE_POOL_SIZE = 16;
    private static final boolean EXECUTOR_DEFAULT_IS_DAEMON = true;
    private static final String EXECUTOR_DEFAULT_THREAD_NAME_PREFIX = "helidon-";
    private static final boolean EXECUTOR_DEFAULT_PRESTART = false;

    private final int corePoolSize;
    private final boolean isDaemon;
    private final String threadNamePrefix;
    private final boolean prestart;
    private final LazyValue<ScheduledExecutorService> lazyValue = LazyValue.create(() -> Contexts.wrap(getThreadPool()));

    private ScheduledThreadPoolSupplier(Builder builder) {
        this.corePoolSize = builder.corePoolSize;
        this.isDaemon = builder.isDaemon;
        this.threadNamePrefix = builder.threadNamePrefix;
        this.prestart = builder.prestart;
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
        return result;
    }

    @Override
    public ScheduledExecutorService get() {
        return lazyValue.get();
    }

    /**
     * A fluent API builder for {@link ScheduledThreadPoolSupplier}.
     */
    @Configured
    public static final class Builder implements io.helidon.common.Builder<ScheduledThreadPoolSupplier> {
        private int corePoolSize = EXECUTOR_DEFAULT_CORE_POOL_SIZE;
        private boolean isDaemon = EXECUTOR_DEFAULT_IS_DAEMON;
        private String threadNamePrefix = EXECUTOR_DEFAULT_THREAD_NAME_PREFIX;
        private boolean prestart = EXECUTOR_DEFAULT_PRESTART;

        private Builder() {
        }

        @Override
        public ScheduledThreadPoolSupplier build() {
            return new ScheduledThreadPoolSupplier(this);
        }

        /**
         * Core pool size of the thread pool executor.
         *
         * @param corePoolSize see {@link ThreadPoolExecutor#getCorePoolSize()}
         * @return updated builder instance
         */
        @ConfiguredOption(defaultValue = "16")
        public Builder corePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
            return this;
        }

        /**
         * Is daemon of the thread pool executor.
         *
         * @param daemon whether the threads are daemon threads
         * @return updated builder instance
         */
        @ConfiguredOption(value = "is-daemon", defaultValue = "true")
        public Builder daemon(boolean daemon) {
            isDaemon = daemon;
            return this;
        }

        /**
         * Name prefix for threads in this thread pool executor.
         *
         * @param threadNamePrefix prefix of a thread name
         * @return updated builder instance
         */
        @ConfiguredOption(defaultValue = "helidon-")
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
        @ConfiguredOption(value = "should-prestart", defaultValue = "true")
        public Builder prestart(boolean prestart) {
            this.prestart = prestart;
            return this;
        }

        /**
         * Load all properties for this thread pool executor from configuration.
         * Expected keys:
         * <ul>
         * <li>core-pool-size</li>
         * <li>is-daemon</li>
         * <li>thread-name-prefix</li>
         * <li>should-prestart</li>
         * </ul>
         *
         * @param config config located on the key of executor-service
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get("core-pool-size").asInt().ifPresent(this::corePoolSize);
            config.get("is-daemon").asBoolean().ifPresent(this::daemon);
            config.get("thread-name-prefix").asString().ifPresent(this::threadNamePrefix);
            config.get("should-prestart").asBoolean().ifPresent(this::prestart);
            return this;
        }
    }
}
