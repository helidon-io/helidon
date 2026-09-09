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

import java.util.List;
import java.util.Objects;

import io.helidon.common.Api;

/**
 * Structured partial or indeterminate batch delivery failure.
 */
@Api.Preview
public final class BatchDeliveryException extends MessagingException {
    /** Original delivery batch. */
    private final MessageBatch<?> batch;
    /** Ordered delivery outcomes. */
    private final List<BatchItemOutcome> outcomes;

    /**
     * Create a structured batch failure.
     *
     * @param message non-null failure message
     * @param cause non-null primary cause
     * @param batch non-null original batch
     * @param outcomes non-null ordered outcome for every original item
     * @throws NullPointerException if any argument or outcome is {@code null}
     * @throws IllegalArgumentException if the outcomes do not align with the batch, or every outcome succeeded
     */
    public BatchDeliveryException(String message,
                                  Throwable cause,
                                  MessageBatch<?> batch,
                                  List<BatchItemOutcome> outcomes) {
        super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause"));
        this.batch = Objects.requireNonNull(batch, "batch");
        this.outcomes = validate(batch, outcomes);
    }

    /**
     * Original batch.
     *
     * @return batch
     */
    public MessageBatch<?> batch() {
        return batch;
    }

    /**
     * Ordered item outcomes.
     *
     * @return outcomes
     */
    public List<BatchItemOutcome> outcomes() {
        return outcomes;
    }

    /**
     * Outcome for one original batch index.
     *
     * @param index item index
     * @return outcome
     */
    public BatchItemOutcome outcome(int index) {
        return outcomes.get(index);
    }

    private static List<BatchItemOutcome> validate(MessageBatch<?> batch, List<BatchItemOutcome> outcomes) {
        List<BatchItemOutcome> snapshot = List.copyOf(Objects.requireNonNull(outcomes, "outcomes"));
        if (snapshot.size() != batch.size()) {
            throw new IllegalArgumentException("Batch outcome count " + snapshot.size()
                                                       + " does not match batch size " + batch.size());
        }
        boolean hasUnresolvedItem = false;
        for (int i = 0; i < snapshot.size(); i++) {
            BatchItemOutcome outcome = snapshot.get(i);
            if (outcome.index() != i) {
                throw new IllegalArgumentException("Batch outcome at position " + i
                                                           + " has index " + outcome.index());
            }
            hasUnresolvedItem |= outcome.status() != BatchItemStatus.SUCCEEDED;
        }
        if (!hasUnresolvedItem) {
            throw new IllegalArgumentException("Batch delivery exception must contain at least one unresolved item");
        }
        return snapshot;
    }
}
