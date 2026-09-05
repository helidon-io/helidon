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
 * Context of an invocation attempt handled by {@link Retry}.
 * <p>
 * The first attempt has an attempt number of {@code 1}, a previous delay of {@link Duration#ZERO}, and no previous
 * throwable. Each subsequent attempt provides the delay preceding that attempt and the throwable from the previous
 * attempt. The context is created immediately before invoking the function for an attempt.
 */
public final class RetryContext {
    private final int attempt;
    private final Duration elapsedTime;
    private final Duration previousDelay;
    private final Throwable previousThrowable;

    RetryContext(int attempt, Duration elapsedTime, Duration previousDelay, Throwable previousThrowable) {
        this.attempt = attempt;
        this.elapsedTime = Objects.requireNonNull(elapsedTime);
        this.previousDelay = Objects.requireNonNull(previousDelay);
        this.previousThrowable = previousThrowable;
    }

    /**
     * Number of the current invocation attempt.
     *
     * @return 1-based invocation attempt number
     */
    public int attempt() {
        return attempt;
    }

    /**
     * Time elapsed since the retry invocation started when this attempt began.
     *
     * @return elapsed time
     */
    public Duration elapsedTime() {
        return elapsedTime;
    }

    /**
     * Requested delay preceding this attempt.
     * <p>
     * The value is {@link Duration#ZERO} for the initial attempt.
     *
     * @return requested delay preceding this attempt
     */
    public Duration previousDelay() {
        return previousDelay;
    }

    /**
     * Throwable from the preceding attempt.
     *
     * @return preceding throwable, or empty for the initial attempt
     */
    public Optional<Throwable> previousThrowable() {
        return Optional.ofNullable(previousThrowable);
    }
}
