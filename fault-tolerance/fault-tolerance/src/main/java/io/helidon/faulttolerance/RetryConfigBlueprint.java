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
     * Number of calls, including the initial call and retries, which must be at least {@code 1}.
     *
     * @return number of desired calls, must be 1 (means no retries) or higher.
     */
    @Option.Configured
    @Option.DefaultInt(DEFAULT_CALLS)
    int calls();

    /**
     * Non-negative base delay between the initial call and retries, which defaults to {@code 200 ms}.
     *
     * @return delay between retries (combines with retry policy)
     */
    @Option.Configured
    @Option.Default("PT0.2S")
    Duration delay();

    /**
     * Delay multiplier that must be {@code -1} or finite and non-negative; {@code -1} selects {@code 2} unless either
     * jitter option is configured, and an explicit multiplier is applied before jitter.
     *
     * @return delay factor for delaying retry policy
     */
    @Option.Configured
    @Option.DefaultDouble(-1L)
    double delayFactor();

    /**
     * Absolute random jitter that must be {@code PT-1S} (disabled) or non-negative; it cannot be combined with
     * {@code jitter-factor}, is applied after {@code delay-factor}, and is capped by {@code max-delay} when present.
     *
     * @return jitter
     */
    @Option.Configured
    @Option.Default("PT-1S")
    Duration jitter();

    /**
     * Relative random jitter that must be {@code -1} (disabled) or from {@code 0} (inclusive) to {@code 1} (exclusive);
     * it cannot be combined with {@code jitter}, is applied after {@code delay-factor}, and is capped by
     * {@code max-delay} when present.
     * A value of {@code 0.2} applies a random jitter of up to twenty percent in either direction.
     *
     * @return relative jitter factor
     */
    @Option.Configured
    @Option.DefaultDouble(-1.0)
    default double jitterFactor() {
        return -1.0;
    }

    /**
     * Optional non-negative maximum delay applied after jitter; when absent, the delay is not capped.
     *
     * @return maximum delay, if configured
     */
    @Option.Configured
    default Optional<Duration> maxDelay() {
        return Optional.empty();
    }

    /**
     * Positive overall timeout used to bound the complete retry sequence.
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
