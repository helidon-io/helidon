/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

import java.util.Objects;

/**
 * A limit was reached and the submitted task cannot be executed.
 *
 * @see io.helidon.common.concurrency.limits.LimitAlgorithm#call(java.util.concurrent.Callable)
 * @see io.helidon.common.concurrency.limits.LimitAlgorithm#run(Runnable)
 */
public class LimitException extends RuntimeException {
    /**
     * Outcome that caused this exception.
     */
    private final LimitAlgorithm.Outcome outcome;

    /**
     * A new limit exception with a cause.
     *
     * @param cause cause of the limit reached
     * @deprecated use constructor with a cause and outcome instead
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    public LimitException(Exception cause) {
        super(Objects.requireNonNull(cause));
        this.outcome = LimitAlgorithmOutcomeImpl.immediateRejection("unknown", "unknown");
    }

    /**
     * A new limit exception with a cause and outcome.
     *
     * @param cause   cause of the limit reached
     * @param outcome outcome of the algorithm
     */
    public LimitException(Exception cause, LimitAlgorithm.Outcome outcome) {
        super(Objects.requireNonNull(cause));
        this.outcome = Objects.requireNonNull(outcome);
    }

    /**
     * A new limit exception with a message.
     *
     * @param message description of why the limit was reached
     * @deprecated use constructor with a message and outcome instead
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    public LimitException(String message) {
        super(Objects.requireNonNull(message));
        this.outcome = LimitAlgorithmOutcomeImpl.immediateRejection("unknown", "unknown");
    }

    /**
     * A new limit exception with a message and outcome.
     *
     * @param message description of why the limit was reached
     * @param outcome outcome of the algorithm
     */
    public LimitException(String message, LimitAlgorithm.Outcome outcome) {
        super(Objects.requireNonNull(message));

        this.outcome = Objects.requireNonNull(outcome);
    }

    /**
     * Outcome of the algorithm that caused this limit exception.
     *
     * @return outcome associated with this exception, never null
     */
    public LimitAlgorithm.Outcome outcome() {
        return outcome;
    }
}
