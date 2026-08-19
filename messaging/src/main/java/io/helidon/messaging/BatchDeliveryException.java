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

import java.util.ArrayList;
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
     * @param message failure message
     * @param batch original batch
     * @param outcomes one ordered outcome for every original item
     * @param cause primary cause
     */
    public BatchDeliveryException(String message,
                                  MessageBatch<?> batch,
                                  List<BatchItemOutcome> outcomes,
                                  Throwable cause) {
        super(message, cause);
        this.batch = Objects.requireNonNull(batch);
        this.outcomes = validate(batch, outcomes);
    }

    /**
     * Create a sequential failure with a successful prefix, one indeterminate item, and an unattempted suffix.
     *
     * @param operation operation description
     * @param batch original batch
     * @param failedIndex index whose operation threw
     * @param cause failure
     * @return structured failure
     */
    public static BatchDeliveryException sequential(String operation,
                                                    MessageBatch<?> batch,
                                                    int failedIndex,
                                                    Throwable cause) {
        Objects.requireNonNull(batch);
        if (failedIndex < 0 || failedIndex >= batch.size()) {
            throw new IndexOutOfBoundsException(failedIndex);
        }
        List<BatchItemOutcome> outcomes = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            if (i < failedIndex) {
                outcomes.add(BatchItemOutcome.succeeded(i));
            } else if (i == failedIndex) {
                outcomes.add(BatchItemOutcome.indeterminate(i, cause));
            } else {
                outcomes.add(BatchItemOutcome.notAttempted(i));
            }
        }
        return new BatchDeliveryException(operation + " failed at batch index " + failedIndex,
                                          batch,
                                          outcomes,
                                          cause);
    }

    /**
     * Create a processing failure for an invoked prefix whose downstream completion is indeterminate, followed by an
     * untouched suffix.
     *
     * @param operation operation description
     * @param batch original batch
     * @param failedIndex last invoked index, whose operation threw
     * @param cause failure
     * @return structured failure
     */
    public static BatchDeliveryException attemptedPrefix(String operation,
                                                         MessageBatch<?> batch,
                                                         int failedIndex,
                                                         Throwable cause) {
        Objects.requireNonNull(batch);
        if (failedIndex < 0 || failedIndex >= batch.size()) {
            throw new IndexOutOfBoundsException(failedIndex);
        }
        List<BatchItemOutcome> outcomes = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            outcomes.add(i <= failedIndex
                                 ? BatchItemOutcome.indeterminate(i, cause)
                                 : BatchItemOutcome.notAttempted(i));
        }
        return new BatchDeliveryException(operation + " failed at batch index " + failedIndex,
                                          batch,
                                          outcomes,
                                          cause);
    }

    /**
     * Create a failure for which every item outcome is indeterminate.
     *
     * @param operation operation description
     * @param batch original batch
     * @param cause failure
     * @return structured failure
     */
    public static BatchDeliveryException indeterminate(String operation,
                                                       MessageBatch<?> batch,
                                                       Throwable cause) {
        Objects.requireNonNull(batch);
        List<BatchItemOutcome> outcomes = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            outcomes.add(BatchItemOutcome.indeterminate(i, cause));
        }
        return new BatchDeliveryException(operation + " failed with indeterminate batch outcome",
                                          batch,
                                          outcomes,
                                          cause);
    }

    /**
     * Create a failure that occurred before any item was attempted.
     *
     * @param operation operation description
     * @param batch original batch
     * @param cause failure
     * @return structured failure
     */
    public static BatchDeliveryException notAttempted(String operation,
                                                      MessageBatch<?> batch,
                                                      Throwable cause) {
        Objects.requireNonNull(batch);
        List<BatchItemOutcome> outcomes = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            outcomes.add(BatchItemOutcome.notAttempted(i));
        }
        return new BatchDeliveryException(operation + " failed before attempting the batch",
                                          batch,
                                          outcomes,
                                          cause);
    }

    /**
     * Align a failure with the exact batch supplied to a failure policy.
     * <p>
     * A structured failure produced for an ancestor batch is projected through the retained delivery lineage and its
     * outcomes are reindexed to the target batch. An unrelated structured failure cannot be indexed safely and is
     * therefore represented conservatively as indeterminate for the complete target batch. Non-structured failures are
     * returned unchanged. Projection preserves the original primary cause and suppressed diagnostics.
     *
     * @param batch exact failure-policy batch
     * @param failure delivery failure
     * @return failure aligned with {@code batch}
     */
    public static RuntimeException align(MessageBatch<?> batch, RuntimeException failure) {
        MessageBatch<?> actualBatch = Objects.requireNonNull(batch);
        RuntimeException actualFailure = Objects.requireNonNull(failure);
        if (!(actualFailure instanceof BatchDeliveryException batchFailure)) {
            return actualFailure;
        }
        if (batchFailure.batch == actualBatch) {
            return batchFailure;
        }

        List<BatchItemOutcome> alignedOutcomes = new ArrayList<>(actualBatch.size());
        for (int i = 0; i < actualBatch.size(); i++) {
            int sourceIndex = actualBatch.lineageIndexIn(batchFailure.batch, i);
            if (sourceIndex < 0) {
                return indeterminate("Batch delivery failure alignment", actualBatch, batchFailure);
            }
            alignedOutcomes.add(reindex(i, batchFailure.outcome(sourceIndex)));
        }
        if (alignedOutcomes.stream().allMatch(outcome -> outcome.status() == BatchItemStatus.SUCCEEDED)) {
            return indeterminate("Batch delivery failure alignment", actualBatch, batchFailure);
        }
        BatchDeliveryException aligned = new BatchDeliveryException(batchFailure.getMessage(),
                                                                     actualBatch,
                                                                     alignedOutcomes,
                                                                     batchFailure.getCause());
        for (Throwable suppressed : batchFailure.getSuppressed()) {
            aligned.addSuppressed(suppressed);
        }
        return aligned;
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

    private static BatchItemOutcome reindex(int index, BatchItemOutcome outcome) {
        return switch (outcome.status()) {
        case SUCCEEDED -> BatchItemOutcome.succeeded(index);
        case FAILED -> BatchItemOutcome.failed(index, outcome.failure().orElse(null));
        case NOT_ATTEMPTED -> BatchItemOutcome.notAttempted(index);
        case INDETERMINATE -> BatchItemOutcome.indeterminate(index, outcome.failure().orElse(null));
        };
    }

    private static List<BatchItemOutcome> validate(MessageBatch<?> batch, List<BatchItemOutcome> outcomes) {
        List<BatchItemOutcome> snapshot = List.copyOf(Objects.requireNonNull(outcomes));
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
