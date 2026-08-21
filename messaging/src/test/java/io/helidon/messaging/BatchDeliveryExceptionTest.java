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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchDeliveryExceptionTest {
    @Test
    void alignsAncestorOutcomesWithNonContiguousPolicySubset() {
        MessageBatch<String> batch = MessageBatch.create(List.of(Message.create("first"),
                                                                 Message.create("second"),
                                                                 Message.create("third"),
                                                                 Message.create("fourth")));
        IllegalStateException secondFailure = new IllegalStateException("second failed");
        IllegalArgumentException fourthFailure = new IllegalArgumentException("fourth failed");
        IllegalStateException cleanupFailure = new IllegalStateException("cleanup failed");
        BatchDeliveryException failure = new BatchDeliveryException(
                "Partial delivery",
                batch,
                List.of(BatchItemOutcome.succeeded(0),
                        BatchItemOutcome.failed(1, secondFailure),
                        BatchItemOutcome.succeeded(2),
                        BatchItemOutcome.indeterminate(3, fourthFailure)),
                secondFailure);
        failure.addSuppressed(cleanupFailure);
        MessageBatch<String> policyBatch = batch.subset(List.of(1, 3));

        RuntimeException result = BatchDeliveryException.align(policyBatch, failure);

        assertTrue(result instanceof BatchDeliveryException);
        BatchDeliveryException aligned = (BatchDeliveryException) result;
        assertSame(policyBatch, aligned.batch());
        assertSame(secondFailure, aligned.getCause());
        assertEquals(List.of(0, 1), aligned.outcomes().stream().map(BatchItemOutcome::index).toList());
        assertEquals(List.of(BatchItemStatus.FAILED, BatchItemStatus.INDETERMINATE),
                     aligned.outcomes().stream().map(BatchItemOutcome::status).toList());
        assertSame(secondFailure, aligned.outcome(0).failure().orElseThrow());
        assertSame(fourthFailure, aligned.outcome(1).failure().orElseThrow());
        assertEquals(List.of(cleanupFailure), List.of(aligned.getSuppressed()));
    }

    @Test
    void alignsDerivedEnvelopesAndReturnsAlreadyAlignedFailure() {
        MessageBatch<String> batch = MessageBatch.create(List.of(Message.create("first"), Message.create("second")));
        IllegalStateException itemFailure = new IllegalStateException("failed");
        BatchDeliveryException failure = BatchDeliveryException.sequential("Delivery", batch, 0, itemFailure);

        assertSame(failure, BatchDeliveryException.align(batch, failure));

        MessageBatch<String> derived = batch.derive(List.of(Message.create("mapped-first"),
                                                            Message.create("mapped-second")));
        BatchDeliveryException aligned = (BatchDeliveryException) BatchDeliveryException.align(derived, failure);

        assertSame(derived, aligned.batch());
        assertEquals(failure.outcomes().stream().map(BatchItemOutcome::status).toList(),
                     aligned.outcomes().stream().map(BatchItemOutcome::status).toList());
        assertSame(itemFailure, aligned.outcome(0).failure().orElseThrow());
    }

    @Test
    void treatsUnrelatedStructuredFailureAsIndeterminate() {
        MessageBatch<String> source = MessageBatch.create(List.of(Message.create("source")));
        MessageBatch<String> target = MessageBatch.create(List.of(Message.create("target-1"),
                                                                  Message.create("target-2")));
        BatchDeliveryException failure = BatchDeliveryException.notAttempted(
                "Source delivery",
                source,
                new IllegalStateException("source failed"));

        BatchDeliveryException aligned = (BatchDeliveryException) BatchDeliveryException.align(target, failure);

        assertSame(target, aligned.batch());
        assertSame(failure, aligned.getCause());
        assertTrue(aligned.outcomes().stream()
                           .allMatch(outcome -> outcome.status() == BatchItemStatus.INDETERMINATE));
    }

    @Test
    void treatsSucceededProjectionAsIndeterminate() {
        MessageBatch<String> batch = MessageBatch.create(List.of(Message.create("succeeded"),
                                                                 Message.create("failed")));
        BatchDeliveryException failure = BatchDeliveryException.sequential(
                "Source delivery",
                batch,
                1,
                new IllegalStateException("failed"));
        MessageBatch<String> succeededOnly = batch.subset(List.of(0));

        BatchDeliveryException aligned = (BatchDeliveryException) BatchDeliveryException.align(succeededOnly, failure);

        assertSame(succeededOnly, aligned.batch());
        assertSame(failure, aligned.getCause());
        assertEquals(BatchItemStatus.INDETERMINATE, aligned.outcome(0).status());
    }

    @Test
    void leavesUnstructuredFailureUnchanged() {
        MessageBatch<String> batch = MessageBatch.create(Message.create("message"));
        IllegalStateException failure = new IllegalStateException("failed");

        assertSame(failure, BatchDeliveryException.align(batch, failure));
    }
}
