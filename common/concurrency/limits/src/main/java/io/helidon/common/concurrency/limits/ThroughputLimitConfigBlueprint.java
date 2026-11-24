/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;
import io.helidon.common.concurrency.limits.spi.LimitProvider;

/**
 * Configuration of {@link ThroughputLimit}.
 *
 * @see #amount()
 * @see #duration()
 * @see #queueLength()
 * @see #queueTimeout()
 * @see #rateLimitingAlgorithm()
 */
@Prototype.Blueprint
@Prototype.Configured(value = ThroughputLimit.TYPE, root = false)
@Prototype.Provides(LimitProvider.class)
@Api.Preview
interface ThroughputLimitConfigBlueprint extends Prototype.Factory<ThroughputLimit>, ClockConfig {
    /**
     * The rate limiting algorithm to apply.
     * <p>
     * Rate limiting algorithm is by default {@link RateLimitingAlgorithmType#TOKEN_BUCKET}.
     * Rates above one operation per nanosecond retain fractional refill credit and can admit multiple operations
     * at the same clock reading. For {@code FIXED_RATE}, an idle period does not accumulate additional credit
     * beyond the current nanosecond's allowance.
     * @return the rate limiting algorithm to be applied
     */
    @Option.Configured
    @Option.Default("TOKEN_BUCKET")
    RateLimitingAlgorithmType rateLimitingAlgorithm();

    /**
     * Nonnegative number of operations to allow during the time window.
     * Defaults to {@value ThroughputLimit#DEFAULT_AMOUNT}.
     * When set to {@code 0}, we switch to unlimited unless a semaphore is supplied.
     * With a supplied semaphore and {@code 0} amount, permits are returned when work completes
     * and no time-based replenishment is performed.
     *
     * @return number of permits
     */
    @Option.Configured
    @Option.DefaultInt(ThroughputLimit.DEFAULT_AMOUNT)
    int amount();

    /**
     * Duration of the time window, which must be positive when the amount is positive.
     * Ignored when the amount is zero.
     * Defaults to {@value ThroughputLimit#DEFAULT_DURATION}.
     *
     * @return duration of the time window
     */
    @Option.Configured
    @Option.Default(ThroughputLimit.DEFAULT_DURATION)
    Duration duration();

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
     * How many requests can be enqueued waiting for a permit.
     * Note that this may not be an exact behavior due to concurrent invocations.
     * We use {@link java.util.concurrent.Semaphore#getQueueLength()} in the
     * {@link io.helidon.common.concurrency.limits.ThroughputLimit} implementation.
     * Default value is {@value ThroughputLimit#DEFAULT_QUEUE_LENGTH}.
     * If set to {@code 0}, there is no queueing.
     *
     * @return number of requests to enqueue
     */
    @Option.Configured
    @Option.DefaultInt(ThroughputLimit.DEFAULT_QUEUE_LENGTH)
    int queueLength();

    /**
     * How long to wait for a permit when enqueued.
     * Defaults to {@value ThroughputLimit#DEFAULT_QUEUE_TIMEOUT_DURATION}
     *
     * @return duration of the timeout
     */
    @Option.Configured
    @Option.Default(ThroughputLimit.DEFAULT_QUEUE_TIMEOUT_DURATION)
    Duration queueTimeout();

    /**
     * Name of this instance.
     *
     * @return name of the instance
     */
    @Option.Default(ThroughputLimit.TYPE)
    String name();

    /**
     * Explicitly configured semaphore.
     * With {@code 0} amount, limits concurrent work using this semaphore.
     * With a positive amount, uses this semaphore for the configured rate limiting algorithm.
     * Queue, clock, name, and metrics configuration also apply.
     *
     * @return semaphore instance
     */
    Optional<Semaphore> semaphore();

    /**
     * A clock that supplies nanosecond time.
     *
     * @return supplier of current nanoseconds, defaults to {@link java.lang.System#nanoTime()}
     */
    @Override
    Optional<Supplier<Long>> clock();

    /**
     * Whether to collect metrics for the throughput limit implementation.
     *
     * @return metrics flag
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean enableMetrics();

}
