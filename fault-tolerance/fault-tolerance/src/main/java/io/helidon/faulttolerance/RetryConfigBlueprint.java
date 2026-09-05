/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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
@Prototype.Blueprint(decorator = RetryConfigBlueprint.BuilderDecorator.class)
@Prototype.Configured("fault-tolerance.retries")
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
    @Option.DefaultDouble(-1L)
    double jitterFactor();

    /**
     * Maximum delay between invocation attempts, including jitter.
     * <p>
     * When not configured, the delay is not capped.
     *
     * @return maximum delay, if configured
     */
    @Option.Configured
    Optional<Duration> maxDelay();

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

    class BuilderDecorator implements Prototype.BuilderDecorator<RetryConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(RetryConfig.BuilderBase<?, ?> target) {
            validate(target);
            if (target.name().isEmpty()) {
                target.config()
                        .ifPresent(cfg -> target.name(cfg.name()));
            }
            if (target.retryPolicy().isEmpty()) {
                target.retryPolicy(retryPolicy(target));
            }
        }

        /**
         * Retry policy created from this configuration.
         *
         * @return retry policy to use
         */
        private Retry.RetryPolicy retryPolicy(RetryConfig.BuilderBase<?, ?> target) {
            boolean jitterConfigured = !target.jitter().equals(Duration.ofSeconds(-1));
            boolean jitterFactorConfigured = target.jitterFactor() != -1;
            Retry.DelayingRetryPolicy.Builder delayBuilder = Retry.DelayingRetryPolicy.builder()
                    .calls(target.calls())
                    .delay(target.delay());

            if (target.delayFactor() != -1) {
                delayBuilder.delayFactor(target.delayFactor());
            } else if (jitterConfigured || jitterFactorConfigured) {
                delayBuilder.delayFactor(1);
            }
            if (jitterConfigured) {
                delayBuilder.jitter(target.jitter());
            }
            if (jitterFactorConfigured) {
                delayBuilder.jitterFactor(target.jitterFactor());
            }
            target.maxDelay().ifPresent(delayBuilder::maxDelay);
            return delayBuilder.build();
        }

        private void validate(RetryConfig.BuilderBase<?, ?> target) {
            if (target.calls() < 1) {
                throw new IllegalArgumentException("Retry calls must be at least 1");
            }
            if (target.delay().isNegative()) {
                throw new IllegalArgumentException("Retry delay must not be negative");
            }
            if (target.overallTimeout().isNegative() || target.overallTimeout().isZero()) {
                throw new IllegalArgumentException("Retry overall timeout must be positive");
            }
            double delayFactor = target.delayFactor();
            if (delayFactor != -1 && (!Double.isFinite(delayFactor) || delayFactor < 0)) {
                throw new IllegalArgumentException("Retry delay factor must be -1 or a finite, non-negative number");
            }
            Duration jitter = target.jitter();
            if (jitter.isNegative() && !jitter.equals(Duration.ofSeconds(-1))) {
                throw new IllegalArgumentException("Retry jitter must be PT-1S or non-negative");
            }
            double jitterFactor = target.jitterFactor();
            if (jitterFactor != -1 && (!Double.isFinite(jitterFactor) || jitterFactor < 0 || jitterFactor >= 1)) {
                throw new IllegalArgumentException("Retry jitter factor must be -1 or from 0 (inclusive) to 1 "
                                                           + "(exclusive)");
            }
            if (!jitter.equals(Duration.ofSeconds(-1)) && jitterFactor != -1) {
                throw new IllegalArgumentException("Retry jitter and jitter factor cannot both be configured");
            }
            target.maxDelay().ifPresent(maxDelay -> {
                if (maxDelay.isNegative()) {
                    throw new IllegalArgumentException("Retry maximum delay must not be negative");
                }
            });
        }
    }
}
