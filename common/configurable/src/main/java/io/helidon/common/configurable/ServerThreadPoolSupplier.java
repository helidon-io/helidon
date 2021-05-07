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
import java.util.function.Supplier;

import io.helidon.config.Config;

/**
 * Supplier of a custom thread pool with defaults appropriate for a thread-per-request server.
 * The returned thread pool supports {@link io.helidon.common.context.Context} propagation.
 */
public final class ServerThreadPoolSupplier implements Supplier<ExecutorService> {

    private static final int MINIMUM_CORES = 2;
    private static final int DEFAULT_MIN_THREADS_PER_CORE = 2;
    private static final int DEFAULT_MAX_THREADS_PER_CORE = 8;
    private static final int DEFAULT_QUEUE_CAPACITY = 8192;
    private static final int DEFAULT_GROWTH_THRESHOLD = 256;
    private static final int DEFAULT_GROWTH_RATE = 5;

    private final ThreadPoolSupplier supplier;

    private ServerThreadPoolSupplier(final ThreadPoolSupplier.Builder builder) {
        this.supplier = builder.build();
    }

    @Override
    public ExecutorService get() {
        return supplier.get();
    }

    /**
     * Create a new fluent API builder to build a thread pool supplier.
     *
     * @return a builder instance
     */
    public static ThreadPoolSupplier.Builder builder() {

        // Set defaults appropriate to a thread-per-request model, based on the number of cores.

        final int cores = Math.max(Runtime.getRuntime().availableProcessors(), MINIMUM_CORES);
        final int minPoolSize = DEFAULT_MIN_THREADS_PER_CORE * cores;
        final int maxPoolSize = DEFAULT_MAX_THREADS_PER_CORE * cores;

        return ThreadPoolSupplier.builder()
                                 .corePoolSize(minPoolSize)
                                 .maxPoolSize(maxPoolSize)
                                 .queueCapacity(DEFAULT_QUEUE_CAPACITY)
                                 .growthThreshold(DEFAULT_GROWTH_THRESHOLD)
                                 .growthRate(DEFAULT_GROWTH_RATE);
    }

    /**
     * Create a new thread pool supplier with default configuration.
     *
     * @return a new thread pool supplier with default configuration
     */
    public static ThreadPoolSupplier create() {
        return builder().build();
    }

    /**
     * Create supplier from configuration.
     *
     * @param config config instance
     * @return a new thread pool supplier configured from config
     */
    public static ThreadPoolSupplier create(Config config) {
        return builder().config(config)
                        .build();
    }
}
