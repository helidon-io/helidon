/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.faulttolerance;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * {@link Retry} configuration bean.
 */
@Prototype.Blueprint(decorator = FtBuilderSupport.RetryBuilderDecorator.class)
@Prototype.Configured("fault-tolerance.retries")
@Prototype.IncludeDefaultMethods({"jitterFactor", "maxDelay"})
interface RetryConfigBlueprint extends Prototype.Factory<Retry> {
    /**
     * Default calls to make.
     * @see #calls()
     */
    int DEFAULT_CALLS = 3;
    /**
     * Default delay between retries.
     * @see #delay()
     */
    Duration DEFAULT_DELAY = Duration.ofMillis(200);
    /**
     * Default overall timeout.
     * @see #overallTimeout()
     */
    Duration DEFAULT_OVERALL_TIMEOUT = Duration.ofSeconds(1);

    /**
     * Name for debugging, error reporting, monitoring.
     *
     * @return name of this retry
     */
    Optional<String> name();

    /**
     * Number of calls (first try + retries).
     *
     * @return number of desired calls, must be 1 (means no retries) or higher.
     */
    @Option.Configured
    @Option.DefaultInt(DEFAULT_CALLS)
    int calls();

    /**
     * Base delay between try and retry.
     * Defaults to {@code 200 ms}.
     *
     * @return delay between retries (combines with retry policy)
     */
    @Option.Configured
    @Option.Default("PT0.2S")
    Duration delay();

    /**
     * Delay retry policy factor. If unspecified (value of {@code -1}), a factor of {@code 2} is used unless jitter is
     * configured, in which case the delay remains constant.
     * <p>
     * When both this option and {@link #jitter()} are configured, the delay factor is applied first and jitter is
     * applied to the resulting delay.
     *
     * @return delay factor for delaying retry policy
     */
    @Option.Configured
    @Option.DefaultDouble(-1L)
    double delayFactor();

    /**
     * Random jitter applied to the delay. If unspecified (value of {@code PT-1S}), jitter is not applied.
     * When both this option and {@link #delayFactor()} are configured, the delay factor is applied first and jitter is
     * applied to the resulting delay. The final delay is capped by {@link #maxDelay()}, when configured.
     *
     * @return jitter
     */
    @Option.Configured
    @Option.Default("PT-1S")
    Duration jitter();

    /**
     * Random jitter relative to the calculated delay. The factor must be from {@code 0} (inclusive) to {@code 1}
     * (exclusive). For example, a value of {@code 0.2} applies a random jitter of up to twenty percent in either
     * direction.
     * <p>
     * This option cannot be combined with an explicitly configured {@link #jitter() absolute jitter}. A value of
     * {@code -1} means relative jitter is not configured.
     *
     * @return relative jitter factor
     */
    @Option.Configured
    @Option.DefaultDouble(-1.0)
    default double jitterFactor() {
        return -1.0;
    }

    /**
     * Maximum delay between invocation attempts, including jitter.
     * <p>
     * When not configured, the delay is not capped.
     *
     * @return maximum delay, if configured
     */
    @Option.Configured
    default Optional<Duration> maxDelay() {
        return Optional.empty();
    }

    /**
     * Overall timeout of all retries combined.
     *
     * @return overall timeout
     */
    @Option.Configured
    @Option.Default("PT1S")
    Duration overallTimeout();

    /**
     * These throwables will not be considered retriable, all other will.
     *
     * @return throwable classes to skip retries
     * @see #applyOn()
     */
    @Option.Singular
    Set<Class<? extends Throwable>> skipOn();

    /**
     * These throwables will be considered retriable.
     *
     * @return throwable classes to trigger retries
     * @see #skipOn()
     */
    @Option.Singular
    Set<Class<? extends Throwable>> applyOn();

    /**
     * Explicitly configured retry policy.
     *
     * @return retry policy
     */
    Optional<Retry.RetryPolicy> retryPolicy();

    /**
     * Flag to enable metrics for this instance. The value of this flag is
     * combined with the global config entry
     * {@link io.helidon.faulttolerance.FaultTolerance#FT_METRICS_DEFAULT_ENABLED}.
     * If either of these flags is {@code true}, then metrics will be enabled
     * for the instance.
     *
     * @return metrics enabled flag
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean enableMetrics();
}
