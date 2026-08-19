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

package io.helidon.messaging;

import java.util.Objects;
import java.util.Optional;

import io.helidon.common.Api;

/**
 * Indexed outcome of one message in a failed batch delivery.
 */
@Api.Preview
public final class BatchItemOutcome {
    private final int index;
    private final BatchItemStatus status;
    private final Throwable failure;

    private BatchItemOutcome(int index, BatchItemStatus status, Throwable failure) {
        if (index < 0) {
            throw new IllegalArgumentException("Batch item index must be zero or greater");
        }
        this.index = index;
        this.status = Objects.requireNonNull(status);
        this.failure = failure;
    }

    /**
     * Create a successful outcome.
     *
     * @param index item index
     * @return outcome
     */
    public static BatchItemOutcome succeeded(int index) {
        return new BatchItemOutcome(index, BatchItemStatus.SUCCEEDED, null);
    }

    /**
     * Create a confirmed failed outcome.
     *
     * @param index item index
     * @param failure failure cause
     * @return outcome
     */
    public static BatchItemOutcome failed(int index, Throwable failure) {
        return new BatchItemOutcome(index, BatchItemStatus.FAILED, failure);
    }

    /**
     * Create a not-attempted outcome.
     *
     * @param index item index
     * @return outcome
     */
    public static BatchItemOutcome notAttempted(int index) {
        return new BatchItemOutcome(index, BatchItemStatus.NOT_ATTEMPTED, null);
    }

    /**
     * Create an indeterminate outcome.
     *
     * @param index item index
     * @param failure failure cause
     * @return outcome
     */
    public static BatchItemOutcome indeterminate(int index, Throwable failure) {
        return new BatchItemOutcome(index, BatchItemStatus.INDETERMINATE, failure);
    }

    /**
     * Original batch index.
     *
     * @return index
     */
    public int index() {
        return index;
    }

    /**
     * Item status.
     *
     * @return status
     */
    public BatchItemStatus status() {
        return status;
    }

    /**
     * Item-specific failure, if available.
     *
     * @return failure
     */
    public Optional<Throwable> failure() {
        return Optional.ofNullable(failure);
    }
}
