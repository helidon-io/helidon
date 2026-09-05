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

package io.helidon.faulttolerance;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of an unsuccessful contextual {@link Retry} invocation.
 */
public final class RetryOutcome {
    private final Termination termination;
    private final int attempts;
    private final Duration elapsedTime;
    private final Duration lastDelay;
    private final Throwable lastThrowable;

    RetryOutcome(Termination termination,
                 int attempts,
                 Duration elapsedTime,
                 Duration lastDelay,
                 Throwable lastThrowable) {
        this.termination = Objects.requireNonNull(termination);
        this.attempts = attempts;
        this.elapsedTime = Objects.requireNonNull(elapsedTime);
        this.lastDelay = Objects.requireNonNull(lastDelay);
        this.lastThrowable = lastThrowable;
    }

    /**
     * Reason the retry invocation terminated.
     *
     * @return termination reason
     */
    public Termination termination() {
        return termination;
    }

    /**
     * Number of completed invocation attempts.
     *
     * @return number of completed attempts
     */
    public int attempts() {
        return attempts;
    }

    /**
     * Time elapsed since the retry invocation started.
     *
     * @return elapsed time
     */
    public Duration elapsedTime() {
        return elapsedTime;
    }

    /**
     * Delay preceding the last completed invocation attempt.
     * <p>
     * The value is {@link Duration#ZERO} if only the initial attempt completed.
     *
     * @return delay preceding the last completed attempt
     */
    public Duration lastDelay() {
        return lastDelay;
    }

    /**
     * Throwable from the last completed invocation attempt.
     *
     * @return last invocation throwable
     */
    public Optional<Throwable> lastThrowable() {
        return Optional.ofNullable(lastThrowable);
    }

    /**
     * Reason a contextual retry invocation terminated.
     */
    public enum Termination {
        /**
         * The retry policy did not permit another retry.
         */
        RETRIES_EXHAUSTED,

        /**
         * The invocation threw a throwable that is not retriable.
         */
        NOT_RETRYABLE,

        /**
         * The configured overall timeout was reached.
         */
        TIMED_OUT,

        /**
         * The invoking thread was interrupted.
         */
        INTERRUPTED,

        /**
         * The wait strategy declined another attempt.
         */
        CANCELLED,

        /**
         * The wait strategy failed.
         */
        WAIT_FAILED,

        /**
         * The retry policy failed or returned an invalid result.
         */
        RETRY_POLICY_FAILED
    }
}
