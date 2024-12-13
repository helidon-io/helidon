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

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.concurrency.limits.spi.LimitProvider;

/**
 * Configuration of {@link io.helidon.common.concurrency.limits.AimdLimit}.
 */
@Prototype.Blueprint
@Prototype.Configured(value = AimdLimit.TYPE, root = false)
@Prototype.Provides(LimitProvider.class)
interface AimdLimitConfigBlueprint extends Prototype.Factory<AimdLimit> {
    /**
     * Backoff ratio to use for the algorithm.
     * The value must be within [0.5, 1.0).
     *
     * @return backoff ratio
     */
    @Option.Configured
    @Option.DefaultDouble(0.9)
    double backoffRatio();

    /**
     * Initial limit.
     * The value must be within [{@link #minLimit()}, {@link #maxLimit()}].
     *
     * @return initial limit
     */
    @Option.Configured
    @Option.DefaultInt(20)
    int initialLimit();

    /**
     * Maximal limit.
     * The value must be same or higher than {@link #minLimit()}.
     *
     * @return maximal limit
     */
    @Option.Configured
    @Option.DefaultInt(200)
    int maxLimit();

    /**
     * Minimal limit.
     * The value must be same or lower than {@link #maxLimit()}.
     *
     * @return minimal limit
     */
    @Option.Configured
    @Option.DefaultInt(20)
    int minLimit();

    /**
     * Timeout that when exceeded is the same as if the task failed.
     *
     * @return task timeout, defaults to 5 seconds
     */
    @Option.Configured
    @Option.Default("PT5S")
    Duration timeout();

    /**
     * A clock that supplies nanosecond time.
     *
     * @return supplier of current nanoseconds, defaults to {@link java.lang.System#nanoTime()}
     */
    Optional<Supplier<Long>> clock();

    /**
     * Name of this instance.
     *
     * @return name of the instance
     */
    @Option.Default(AimdLimit.TYPE)
    String name();

    /**
     * How many requests can be enqueued waiting for a permit after
     * the {@link #maxLimit()} is reached.
     * Note that this may not be an exact behavior due to concurrent invocations.
     * We use {@link java.util.concurrent.Semaphore#getQueueLength()} in the
     * {@link io.helidon.common.concurrency.limits.AimdLimit} implementation.
     * Default value is {@value AimdLimit#DEFAULT_QUEUE_LENGTH}.
     * If set to {code 0}, there is no queueing.
     *
     * @return number of requests to enqueue
     */
    @Option.Configured
    @Option.DefaultInt(FixedLimit.DEFAULT_QUEUE_LENGTH)
    int queueLength();

    /**
     * How long to wait for a permit when enqueued.
     * Defaults to {@value AimdLimit#DEFAULT_QUEUE_TIMEOUT_DURATION}
     *
     * @return duration of the timeout
     */
    @Option.Configured
    @Option.Default(FixedLimit.DEFAULT_QUEUE_TIMEOUT_DURATION)
    Duration queueTimeout();

    /**
     * Whether the {@link java.util.concurrent.Semaphore} should be {@link java.util.concurrent.Semaphore#isFair()}.
     * Defaults to {@code false}.
     *
     * @return whether this should be a fair semaphore
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean fair();

    /**
     * Whether to collect metrics for the AIMD implementation.
     *
     * @return metrics flag
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean enableMetrics();
}
