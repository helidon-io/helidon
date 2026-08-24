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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

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

        assertThat(result, is(instanceOf(BatchDeliveryException.class)));
        BatchDeliveryException aligned = (BatchDeliveryException) result;
        assertThat(aligned.batch(), sameInstance(policyBatch));
        assertThat(aligned.getCause(), sameInstance(secondFailure));
        assertThat(aligned.outcomes().stream().map(BatchItemOutcome::index).toList(), is(List.of(0, 1)));
        assertThat(aligned.outcomes().stream().map(BatchItemOutcome::status).toList(),
                   is(List.of(BatchItemStatus.FAILED, BatchItemStatus.INDETERMINATE)));
        assertThat(aligned.outcome(0).failure().orElseThrow(), sameInstance(secondFailure));
        assertThat(aligned.outcome(1).failure().orElseThrow(), sameInstance(fourthFailure));
        assertThat(List.of(aligned.getSuppressed()), is(List.of(cleanupFailure)));
    }

    @Test
    void alignsDerivedEnvelopesAndReturnsAlreadyAlignedFailure() {
        MessageBatch<String> batch = MessageBatch.create(List.of(Message.create("first"), Message.create("second")));
        IllegalStateException itemFailure = new IllegalStateException("failed");
        BatchDeliveryException failure = BatchDeliveryException.sequential("Delivery", batch, 0, itemFailure);

        assertThat(BatchDeliveryException.align(batch, failure), sameInstance(failure));

        MessageBatch<String> derived = batch.derive(List.of(Message.create("mapped-first"),
                                                            Message.create("mapped-second")));
        BatchDeliveryException aligned = (BatchDeliveryException) BatchDeliveryException.align(derived, failure);

        assertThat(aligned.batch(), sameInstance(derived));
        assertThat(aligned.outcomes().stream().map(BatchItemOutcome::status).toList(),
                   is(failure.outcomes().stream().map(BatchItemOutcome::status).toList()));
        assertThat(aligned.outcome(0).failure().orElseThrow(), sameInstance(itemFailure));
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

        assertThat(aligned.batch(), sameInstance(target));
        assertThat(aligned.getCause(), sameInstance(failure));
        assertThat(aligned.outcomes().stream()
                           .allMatch(outcome -> outcome.status() == BatchItemStatus.INDETERMINATE), is(true));
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

        assertThat(aligned.batch(), sameInstance(succeededOnly));
        assertThat(aligned.getCause(), sameInstance(failure));
        assertThat(aligned.outcome(0).status(), is(BatchItemStatus.INDETERMINATE));
    }

    @Test
    void leavesUnstructuredFailureUnchanged() {
        MessageBatch<String> batch = MessageBatch.create(Message.create("message"));
        IllegalStateException failure = new IllegalStateException("failed");

        assertThat(BatchDeliveryException.align(batch, failure), sameInstance(failure));
    }
}
