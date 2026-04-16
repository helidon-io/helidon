/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

package io.helidon.common.concurrency.limits;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.builder.api.RuntimeType;
import io.helidon.config.Config;

/**
 * Semaphore based limit, that supports queuing for a permit, and timeout on the queue.
 * The default behavior is non-queuing.
 *
 * @see io.helidon.common.concurrency.limits.FixedLimitConfig
 */
public class FixedLimit extends SemaphoreLimitBase implements RuntimeType.Api<FixedLimitConfig> {

    /**
     * Default limit, meaning unlimited execution.
     */
    public static final int DEFAULT_LIMIT = 0;

    /**
     * Default length of the queue.
     */
    public static final int DEFAULT_QUEUE_LENGTH = SemaphoreLimitBase.DEFAULT_QUEUE_LENGTH;

    /**
     * Timeout of a request that is enqueued.
     */
    public static final String DEFAULT_QUEUE_TIMEOUT_DURATION = "PT1S";

    static final String TYPE = "fixed";

    private final FixedLimitConfig config;

    private FixedLimit(FixedLimitConfig config) {
        super(context(config));
        this.config = config;

    }

    /**
     * Create a new fluent API builder to construct {@link FixedLimit}
     * instance.
     *
     * @return fluent API builder
     */
    public static FixedLimitConfig.Builder builder() {
        return FixedLimitConfig.builder();
    }

    /**
     * Create a new instance with all defaults (no limit).
     *
     * @return a new limit instance
     */
    public static FixedLimit create() {
        return builder().build();
    }

    /**
     * Create an instance from the provided semaphore.
     *
     * @param semaphore semaphore to use
     * @return a new fixed limit backed by the provided semaphore
     */
    public static FixedLimit create(Semaphore semaphore) {
        return builder()
                .semaphore(semaphore)
                .build();
    }

    /**
     * Create a new instance from configuration.
     *
     * @param config configuration of the fixed limit
     * @return a new limit instance configured from {@code config}
     */
    public static FixedLimit create(Config config) {
        return builder()
                .config(config)
                .build();
    }

    /**
     * Create a new instance from configuration.
     *
     * @param config configuration of the fixed limit
     * @return a new limit instance configured from {@code config}
     */
    public static FixedLimit create(FixedLimitConfig config) {
        return new FixedLimit(config);
    }

    /**
     * Create a new instance customizing its configuration.
     *
     * @param consumer consumer of configuration builder
     * @return a new limit instance configured from the builder
     */
    public static FixedLimit create(Consumer<FixedLimitConfig.Builder> consumer) {
        return builder()
                .update(consumer)
                .build();
    }

    @Override
    public FixedLimitConfig prototype() {
        return config;
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Limit copy() {
        if (config.semaphore().isPresent()) {
            Semaphore semaphore = config.semaphore().get();

            return FixedLimitConfig.builder()
                    .from(config)
                    .semaphore(new Semaphore(initialPermits(), semaphore.isFair()))
                    .build();
        }
        return config.build();
    }

    private static Context context(FixedLimitConfig config) {
        AtomicInteger concurrentRequests = new AtomicInteger();
        AtomicInteger rejectedRequests = new AtomicInteger();

        LimitHandlers.LimiterHandler limiterHandler;
        var clock = LimitUtil.clock(config);

        if (config.permits() == 0 && config.semaphore().isEmpty()) {
            return new Context(0,
                               0,
                               new LimitHandlers.NoOpSemaphoreHandler(),
                               clock,
                               concurrentRequests,
                               rejectedRequests,
                               new SemaphoreMetrics(config.enableMetrics(),
                                                    null,
                                                    config.name(),
                                                    concurrentRequests,
                                                    rejectedRequests));
        }

        Semaphore semaphore = config.semaphore().orElseGet(() -> new Semaphore(config.permits(), config.fair()));
        int queueLength = Math.max(0, config.queueLength());
        SemaphoreMetrics metrics = new SemaphoreMetrics(config.enableMetrics(),
                                       semaphore,
                                       config.name(),
                                       concurrentRequests,
                                       rejectedRequests);

        Supplier<Token> tokenSupplier = () -> new FixedToken(semaphore, metrics, clock, concurrentRequests);
        limiterHandler = new LimitHandlers.QueuedSemaphoreHandler(semaphore,
                                                                  queueLength,
                                                                  config.queueTimeout(),
                                                                  tokenSupplier);

        return new Context(semaphore.availablePermits(),
                           queueLength,
                           limiterHandler,
                           clock,
                           concurrentRequests,
                           rejectedRequests,
                           metrics);
    }

    private static class FixedToken implements LimitAlgorithm.Token {
        private final Semaphore semaphore;
        private final SemaphoreMetrics metrics;
        private final long startTime;
        private final Supplier<Long> clock;
        private final AtomicInteger concurrentRequests;

        FixedToken(Semaphore semaphore, SemaphoreMetrics metrics, Supplier<Long> clock, AtomicInteger concurrentRequests) {
            this.semaphore = semaphore;
            this.metrics = metrics;
            startTime = clock.get();
            this.clock = clock;
            this.concurrentRequests = concurrentRequests;
            concurrentRequests.incrementAndGet();
        }

        @Override
        public void dropped() {
            try {
                metrics.updateRtt(startTime, clock.get());
            } finally {
                // Dropped work is no longer running, so keep the concurrency gauge accurate.
                concurrentRequests.decrementAndGet();
                semaphore.release();
            }
        }

        @Override
        public void ignore() {
            concurrentRequests.decrementAndGet();
            semaphore.release();
        }

        @Override
        public void success() {
            try {
                metrics.updateRtt(startTime, clock.get());
                concurrentRequests.decrementAndGet();
            } finally {
                semaphore.release();
            }
        }
    }
}
