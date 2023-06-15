/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.faulttolerance;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * {@link Retry} configuration bean.
 */
// @ConfigBean(value = "fault-tolerance.retries", repeatable = true, wantDefaultConfigBean = true)
@Prototype.Blueprint(builderInterceptor = RetryConfigInterceptor.class)
@Configured(root = true, prefix = "fault-tolerance.retries")
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
    @ConfiguredOption("3")
    int calls();

    /**
     * Base delay between try and retry.
     * Defaults to {@code 200 ms}.
     *
     * @return delay between retries (combines with retry policy)
     */
    @ConfiguredOption("PT0.2S")
    Duration delay();

    /**
     * Delay retry policy factor. If unspecified (value of {@code -1}), Jitter retry policy would be used, unless
     * jitter is also unspecified.
     * <p>
     * Default when {@link Retry.DelayingRetryPolicy} is used is {@code 2}.
     *
     * @return delay factor for delaying retry policy
     */
    @ConfiguredOption("-1")
    double delayFactor();

    /**
     * Jitter for {@link Retry.JitterRetryPolicy}. If unspecified (value of {@code -1}),
     * delaying retry policy is used. If both this value, and {@link #delayFactor()} are specified, delaying retry policy
     * would be used.
     *
     * @return jitter
     */
    @ConfiguredOption("PT-1S")
    Duration jitter();

    /**
     * Overall timeout of all retries combined.
     *
     * @return overall timeout
     */
    @ConfiguredOption("PT1S")
    Duration overallTimeout();

    /**
     * These throwables will not be considered retriable, all other will.
     *
     * @return throwable classes to skip retries
     * @see #applyOn()
     */
    @Prototype.Singular
    Set<Class<? extends Throwable>> skipOn();

    /**
     * These throwables will be considered retriable.
     *
     * @return throwable classes to trigger retries
     * @see #skipOn()
     */
    @Prototype.Singular
    Set<Class<? extends Throwable>> applyOn();

    /**
     * Explicitly configured retry policy.
     *
     * @return retry policy
     */
    Optional<Retry.RetryPolicy> retryPolicy();
}
