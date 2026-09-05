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

final class BatchDeliveryExceptionSupport {
    private BatchDeliveryExceptionSupport() {
    }

    static BatchDeliveryException sequential(String operation,
                                             MessageBatch<?> batch,
                                             int failedIndex,
                                             Throwable cause) {
        String actualOperation = Objects.requireNonNull(operation, "operation");
        MessageBatch<?> actualBatch = Objects.requireNonNull(batch, "batch");
        Throwable actualCause = Objects.requireNonNull(cause, "cause");
        checkIndex(actualBatch, failedIndex);
        List<BatchItemOutcome> outcomes = new ArrayList<>(actualBatch.size());
        for (int i = 0; i < actualBatch.size(); i++) {
            if (i < failedIndex) {
                outcomes.add(BatchItemOutcome.succeeded(i));
            } else if (i == failedIndex) {
                outcomes.add(BatchItemOutcome.indeterminate(i, actualCause));
            } else {
                outcomes.add(BatchItemOutcome.notAttempted(i));
            }
        }
        return new BatchDeliveryException(actualOperation + " failed at batch index " + failedIndex,
                                          actualCause,
                                          actualBatch,
                                          outcomes);
    }

    static BatchDeliveryException attemptedPrefix(String operation,
                                                  MessageBatch<?> batch,
                                                  int failedIndex,
                                                  Throwable cause) {
        String actualOperation = Objects.requireNonNull(operation, "operation");
        MessageBatch<?> actualBatch = Objects.requireNonNull(batch, "batch");
        Throwable actualCause = Objects.requireNonNull(cause, "cause");
        checkIndex(actualBatch, failedIndex);
        List<BatchItemOutcome> outcomes = new ArrayList<>(actualBatch.size());
        for (int i = 0; i < actualBatch.size(); i++) {
            outcomes.add(i <= failedIndex
                                 ? BatchItemOutcome.indeterminate(i, actualCause)
                                 : BatchItemOutcome.notAttempted(i));
        }
        return new BatchDeliveryException(actualOperation + " failed at batch index " + failedIndex,
                                          actualCause,
                                          actualBatch,
                                          outcomes);
    }

    static BatchDeliveryException indeterminate(String operation,
                                                MessageBatch<?> batch,
                                                Throwable cause) {
        String actualOperation = Objects.requireNonNull(operation, "operation");
        MessageBatch<?> actualBatch = Objects.requireNonNull(batch, "batch");
        Throwable actualCause = Objects.requireNonNull(cause, "cause");
        List<BatchItemOutcome> outcomes = new ArrayList<>(actualBatch.size());
        for (int i = 0; i < actualBatch.size(); i++) {
            outcomes.add(BatchItemOutcome.indeterminate(i, actualCause));
        }
        return new BatchDeliveryException(actualOperation + " failed with indeterminate batch outcome",
                                          actualCause,
                                          actualBatch,
                                          outcomes);
    }

    static BatchDeliveryException notAttempted(String operation,
                                               MessageBatch<?> batch,
                                               Throwable cause) {
        String actualOperation = Objects.requireNonNull(operation, "operation");
        MessageBatch<?> actualBatch = Objects.requireNonNull(batch, "batch");
        Throwable actualCause = Objects.requireNonNull(cause, "cause");
        List<BatchItemOutcome> outcomes = new ArrayList<>(actualBatch.size());
        for (int i = 0; i < actualBatch.size(); i++) {
            outcomes.add(BatchItemOutcome.notAttempted(i));
        }
        return new BatchDeliveryException(actualOperation + " failed before attempting the batch",
                                          actualCause,
                                          actualBatch,
                                          outcomes);
    }

    static RuntimeException align(MessageBatch<?> batch, RuntimeException failure) {
        MessageBatch<?> actualBatch = Objects.requireNonNull(batch, "batch");
        RuntimeException actualFailure = Objects.requireNonNull(failure, "failure");
        if (!(actualFailure instanceof BatchDeliveryException batchFailure)) {
            return actualFailure;
        }
        if (batchFailure.batch() == actualBatch) {
            return batchFailure;
        }

        List<BatchItemOutcome> alignedOutcomes = new ArrayList<>(actualBatch.size());
        for (int i = 0; i < actualBatch.size(); i++) {
            int sourceIndex = actualBatch.lineageIndexIn(batchFailure.batch(), i);
            if (sourceIndex < 0) {
                return indeterminate("Batch delivery failure alignment", actualBatch, batchFailure);
            }
            alignedOutcomes.add(reindex(i, batchFailure.outcome(sourceIndex)));
        }
        if (alignedOutcomes.stream().allMatch(outcome -> outcome.status() == BatchItemStatus.SUCCEEDED)) {
            return indeterminate("Batch delivery failure alignment", actualBatch, batchFailure);
        }
        BatchDeliveryException aligned = new BatchDeliveryException(batchFailure.getMessage(),
                                                                     batchFailure.getCause(),
                                                                     actualBatch,
                                                                     alignedOutcomes);
        for (Throwable suppressed : batchFailure.getSuppressed()) {
            aligned.addSuppressed(suppressed);
        }
        return aligned;
    }

    private static void checkIndex(MessageBatch<?> batch, int index) {
        if (index < 0 || index >= batch.size()) {
            throw new IndexOutOfBoundsException(index);
        }
    }

    private static BatchItemOutcome reindex(int index, BatchItemOutcome outcome) {
        return switch (outcome.status()) {
        case SUCCEEDED -> BatchItemOutcome.succeeded(index);
        case FAILED -> BatchItemOutcome.failed(index, outcome.failure().orElse(null));
        case NOT_ATTEMPTED -> BatchItemOutcome.notAttempted(index);
        case INDETERMINATE -> BatchItemOutcome.indeterminate(index, outcome.failure().orElse(null));
        };
    }
}
