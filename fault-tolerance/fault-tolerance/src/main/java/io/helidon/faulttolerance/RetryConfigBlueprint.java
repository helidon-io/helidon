/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
//@ConfigDriven.ConfigBean
//@ConfigDriven.Repeatable
//@ConfigDriven.WantDefault
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
     * Delay retry policy factor. If unspecified (value of {@code -1}), Jitter retry policy would be used, unless
     * jitter is also unspecified.
     * <p>
     * Default when {@link Retry.DelayingRetryPolicy} is used is {@code 2}.
     *
     * @return delay factor for delaying retry policy
     */
    @Option.Configured
    @Option.DefaultDouble(-1L)
    double delayFactor();

    /**
     * Jitter for {@link Retry.JitterRetryPolicy}. If unspecified (value of {@code -1}),
     * delaying retry policy is used. If both this value, and {@link #delayFactor()} are specified, delaying retry policy
     * would be used.
     *
     * @return jitter
     */
    @Option.Configured
    @Option.Default("PT-1S")
    Duration jitter();

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
            if (target.jitter().toSeconds() == -1) {
                Retry.DelayingRetryPolicy.Builder delayBuilder = Retry.DelayingRetryPolicy.builder()
                        .calls(target.calls())
                        .delay(target.delay());

                if (target.delayFactor() != -1) {
                    delayBuilder.delayFactor(target.delayFactor());
                }
                return delayBuilder.build();
            }
            if (target.delayFactor() != -1) {
                return Retry.DelayingRetryPolicy.builder()
                        .calls(target.calls())
                        .delayFactor(target.delayFactor())
                        .delay(target.delay())
                        .build();
            }
            return Retry.JitterRetryPolicy.builder()
                    .calls(target.calls())
                    .delay(target.delay())
                    .jitter(target.jitter())
                    .build();
        }
    }
}
