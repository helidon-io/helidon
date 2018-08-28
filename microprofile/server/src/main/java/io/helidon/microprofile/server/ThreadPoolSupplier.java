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

package io.helidon.microprofile.server;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.helidon.config.Config;

/**
 * Supplier of a custom thread pool.
 */
public class ThreadPoolSupplier implements Supplier<ExecutorService> {
    private static final int EXECUTOR_DEFAULT_CORE_POOL_SIZE = 10;
    private static final int EXECUTOR_DEFAULT_MAX_POOL_SIZE = 50;
    private static final int EXECUTOR_DEFAULT_KEEP_ALIVE_MINUTES = 3;
    private static final int EXECUTOR_DEFAULT_QUEUE_CAPACITY = 10000;
    private static final boolean EXECUTOR_DEFAULT_IS_DAEMON = true;
    private static final String EXECUTOR_DEFAULT_THREAD_NAME_PREFIX = "helidon-";
    private static final boolean EXECUTOR_DEFAULT_PRESTART = true;

    private final int corePoolSize;
    private final int maxPoolSize;
    private final int keepAliveMinutes;
    private final int queueCapacity;
    private final boolean isDaemon;
    private final String threadNamePrefix;
    private final boolean prestart;
    private volatile ThreadPoolExecutor instance;

    private ThreadPoolSupplier(Builder builder) {
        this.corePoolSize = builder.corePoolSize;
        this.maxPoolSize = builder.maxPoolSize;
        this.keepAliveMinutes = builder.keepAliveMinutes;
        this.queueCapacity = builder.queueCapacity;
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
    public static ThreadPoolSupplier from(Config config) {
        return builder().fromConfig(config.get("server.executor-service"))
                .build();
    }

    @Override
    public synchronized ThreadPoolExecutor get() {
        if (null == instance) {
            instance = new ThreadPoolExecutor(corePoolSize,
                                              maxPoolSize,
                                              keepAliveMinutes,
                                              TimeUnit.MINUTES,
                                              new LinkedBlockingQueue<>(queueCapacity),
                                              new ThreadFactory() {
                                                  private AtomicInteger value = new AtomicInteger();

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
                instance.prestartAllCoreThreads();
            }
        }
        return instance;
    }

    /**
     * A fluent API builder for {@link ThreadPoolSupplier}.
     */
    public static class Builder implements io.helidon.common.Builder<ThreadPoolSupplier> {
        private int corePoolSize = EXECUTOR_DEFAULT_CORE_POOL_SIZE;
        private int maxPoolSize = EXECUTOR_DEFAULT_MAX_POOL_SIZE;
        private int keepAliveMinutes = EXECUTOR_DEFAULT_KEEP_ALIVE_MINUTES;
        private int queueCapacity = EXECUTOR_DEFAULT_QUEUE_CAPACITY;
        private boolean isDaemon = EXECUTOR_DEFAULT_IS_DAEMON;
        private String threadNamePrefix = EXECUTOR_DEFAULT_THREAD_NAME_PREFIX;
        private boolean prestart = EXECUTOR_DEFAULT_PRESTART;

        @Override
        public ThreadPoolSupplier build() {
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
         * Load all properties for this thread pool executor from configuration.
         * Expected keys:
         * <ul>
         * <li>core-pool-size</li>
         * <li>max-pool-size</li>
         * <li>keep-alive-minutes</li>
         * <li>queue-capacity</li>
         * <li>is-daemon</li>
         * <li>thread-name-prefix</li>
         * <li>should-prestart</li>
         * </ul>
         *
         * @param config config located on the key of executor-service
         * @return updated builder instance
         */
        public Builder fromConfig(Config config) {
            config.get("core-pool-size").asOptionalInt().ifPresent(this::corePoolSize);
            config.get("max-pool-size").asOptionalInt().ifPresent(this::maxPoolSize);
            config.get("keep-alive-minutes").asOptionalInt().ifPresent(this::keepAliveMinutes);
            config.get("queue-capacity").asOptionalInt().ifPresent(this::queueCapacity);
            config.get("is-daemon").asOptional(Boolean.class).ifPresent(this::daemon);
            config.get("thread-name-prefix").value().ifPresent(this::threadNamePrefix);
            config.get("should-prestart").asOptional(Boolean.class).ifPresent(this::prestart);

            return this;
        }
    }
}
