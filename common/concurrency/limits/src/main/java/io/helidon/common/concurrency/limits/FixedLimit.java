/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.config.Config;

/**
 * Semaphore based limit, that supports queuing for a permit, and timeout on the queue.
 * The default behavior is non-queuing.
 *
 * @see io.helidon.common.concurrency.limits.FixedLimitConfig
 */
@SuppressWarnings("removal")
@RuntimeType.PrototypedBy(FixedLimitConfig.class)
public class FixedLimit implements Limit, SemaphoreLimit, RuntimeType.Api<FixedLimitConfig> {
    /**
     * Default limit, meaning unlimited execution.
     */
    public static final int DEFAULT_LIMIT = 0;
    /**
     * Default length of the queue.
     */
    public static final int DEFAULT_QUEUE_LENGTH = 0;
    /**
     * Timeout of a request that is enqueued.
     */
    public static final String DEFAULT_QUEUE_TIMEOUT_DURATION = "PT1S";

    static final String TYPE = "fixed";

    private final FixedLimitConfig config;
    private final LimitHandlers.LimiterHandler handler;
    private final int initialPermits;

    private FixedLimit(FixedLimitConfig config) {
        this.config = config;
        if (config.permits() == 0 && config.semaphore().isEmpty()) {
            this.handler = new LimitHandlers.NoOpSemaphoreHandler();
            this.initialPermits = 0;
        } else {
            Semaphore semaphore = config.semaphore().orElseGet(() -> new Semaphore(config.permits(), config.fair()));
            this.initialPermits = semaphore.availablePermits();
            if (config.queueLength() == 0) {
                this.handler = new LimitHandlers.RealSemaphoreHandler(semaphore);
            } else {
                this.handler = new LimitHandlers.QueuedSemaphoreHandler(semaphore,
                                                                        config.queueLength(),
                                                                        config.queueTimeout());
            }
        }
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
    public <T> T invoke(Callable<T> callable) throws Exception {
        return handler.invoke(callable);
    }

    @Override
    public void invoke(Runnable runnable) throws Exception {
        handler.invoke(runnable);
    }

    @Override
    public Optional<Token> tryAcquire(boolean wait) {
        return handler.tryAcquire(wait);
    }

    @SuppressWarnings("removal")
    @Override
    public Semaphore semaphore() {
        return handler.semaphore();
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
        return FixedLimit.TYPE;
    }

    @Override
    public Limit copy() {
        if (config.semaphore().isPresent()) {
            Semaphore semaphore = config.semaphore().get();

            return FixedLimitConfig.builder()
                    .from(config)
                    .semaphore(new Semaphore(initialPermits, semaphore.isFair()))
                    .build();
        }
        return config.build();
    }
}
