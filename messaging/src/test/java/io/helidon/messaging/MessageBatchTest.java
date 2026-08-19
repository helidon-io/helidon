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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageBatchTest {
    @Test
    void createsImmutableEnvelopePreservingSnapshot() {
        Message<String> first = Message.create("one");
        Message<String> second = Message.create("two");
        List<Message<String>> source = new ArrayList<>(List.of(first, second));

        MessageBatch<String> batch = MessageBatch.<String>builder()
                .id("batch-1")
                .messages(source)
                .build();
        source.clear();

        assertEquals("batch-1", batch.id());
        assertEquals(2, batch.size());
        assertSame(first, batch.get(0));
        assertSame(second, batch.get(1));
        assertEquals(List.of("one", "two"), batch.payloads());
        assertEquals(batch.messages(), toList(batch));
        assertThrows(UnsupportedOperationException.class, () -> batch.messages().clear());
        assertThrows(UnsupportedOperationException.class, () -> batch.payloads().clear());
    }

    @Test
    void preservesDeliveryLineageAcrossDerivationAndSubsets() {
        Message<String> first = Message.create("one");
        Message<String> second = Message.create("two");
        Message<String> third = Message.create("three");
        MessageBatch<String> original = MessageBatch.<String>builder()
                .id("batch-1")
                .messages(List.of(first, second, third))
                .build();

        MessageBatch<Integer> derived = original.derive(List.of(Message.create(1),
                                                                 Message.create(2),
                                                                 Message.create(3)));
        MessageBatch<String> retry = original.subset(List.of(0, 2));
        MessageBatch<Integer> derivedRetry = derived.subset(List.of(0, 2));
        MessageBatch<String> copiedIdentity = MessageBatch.<String>builder()
                .id(original.id())
                .messages(original.messages())
                .build();

        assertTrue(original.sameDelivery(derived));
        assertFalse(original.sameDelivery(copiedIdentity));
        assertFalse(original.sameDelivery(retry));
        assertTrue(retry.sameDelivery(derivedRetry));
        assertTrue(retry.isRetainedSubsetOf(original));
        assertFalse(copiedIdentity.isRetainedSubsetOf(original));
        assertFalse(derivedRetry.isRetainedSubsetOf(original));
        assertEquals(List.of("one", "three"), retry.payloads());
        assertEquals("batch-1", retry.id());
        assertThrows(IllegalArgumentException.class, () -> original.derive(List.of(Message.create(1))));
        assertThrows(IllegalArgumentException.class, () -> original.subset(List.of(2, 1)));
        assertThrows(IllegalArgumentException.class, () -> original.subset(List.of(1, 1)));
    }

    @Test
    void selectedSubsetPreservesEnvelopeIdentityAndOrder() {
        Message<String> first = Message.create("one");
        Message<String> second = Message.create("two");
        Message<String> third = Message.create("three");
        MessageBatch<String> original = MessageBatch.create(List.of(first, second, third));

        MessageBatch<String> selected = original.subset(List.of(0, 2));
        MessageBatch<String> completeSelection = original.subset(List.of(0, 1, 2));

        assertEquals(List.of("one", "three"), selected.payloads());
        assertSame(first, selected.get(0));
        assertSame(third, selected.get(1));
        assertTrue(selected.isRetainedSubsetOf(original));
        assertTrue(completeSelection.sameDelivery(original));
    }

    @Test
    void rejectsInvalidConstruction() {
        assertThrows(IllegalArgumentException.class, () -> MessageBatch.create(List.of()));
        assertThrows(IllegalArgumentException.class,
                     () -> MessageBatch.<String>builder().id(" ").add(Message.create("one")).build());
        assertThrows(IllegalArgumentException.class,
                     () -> MessageBatch.<String>builder()
                             .id("x".repeat(MessageBatch.MAX_ID_LENGTH + 1))
                             .add(Message.create("one"))
                             .build());
        assertThrows(NullPointerException.class,
                     () -> MessageBatch.<String>builder().messages(null));
        assertThrows(NullPointerException.class,
                     () -> MessageBatch.<String>builder().add(null));
    }

    @Test
    void describesSequentialAndIndeterminateFailures() {
        MessageBatch<String> batch = MessageBatch.create(List.of(Message.create("one"),
                                                                 Message.create("two"),
                                                                 Message.create("three")));
        IllegalStateException failure = new IllegalStateException("send failed");

        BatchDeliveryException sequential = BatchDeliveryException.sequential("send", batch, 1, failure);

        assertSame(batch, sequential.batch());
        assertSame(failure, sequential.getCause());
        assertEquals(BatchItemStatus.SUCCEEDED, sequential.outcome(0).status());
        assertEquals(BatchItemStatus.INDETERMINATE, sequential.outcome(1).status());
        assertSame(failure, sequential.outcome(1).failure().orElseThrow());
        assertEquals(BatchItemStatus.NOT_ATTEMPTED, sequential.outcome(2).status());
        assertFalse(sequential.outcome(2).failure().isPresent());
        assertThrows(UnsupportedOperationException.class, () -> sequential.outcomes().clear());

        BatchDeliveryException indeterminate = BatchDeliveryException.indeterminate("send", batch, failure);
        assertTrue(indeterminate.outcomes().stream()
                           .allMatch(outcome -> outcome.status() == BatchItemStatus.INDETERMINATE));

        BatchDeliveryException attemptedPrefix = BatchDeliveryException.attemptedPrefix("process", batch, 1, failure);
        assertEquals(List.of(BatchItemStatus.INDETERMINATE,
                             BatchItemStatus.INDETERMINATE,
                             BatchItemStatus.NOT_ATTEMPTED),
                     attemptedPrefix.outcomes().stream().map(BatchItemOutcome::status).toList());

        BatchDeliveryException notAttempted = BatchDeliveryException.notAttempted("send", batch, failure);
        assertTrue(notAttempted.outcomes().stream()
                           .allMatch(outcome -> outcome.status() == BatchItemStatus.NOT_ATTEMPTED));
    }

    @Test
    void validatesStructuredOutcomesAgainstOriginalIndexes() {
        MessageBatch<String> batch = MessageBatch.create(List.of(Message.create("one"), Message.create("two")));

        assertThrows(IllegalArgumentException.class,
                     () -> new BatchDeliveryException("failed",
                                                      batch,
                                                      List.of(BatchItemOutcome.failed(0, new RuntimeException())),
                                                      null));
        assertThrows(IllegalArgumentException.class,
                     () -> new BatchDeliveryException("failed",
                                                      batch,
                                                      List.of(BatchItemOutcome.succeeded(1),
                                                              BatchItemOutcome.notAttempted(0)),
                                                      null));
        assertThrows(IllegalArgumentException.class,
                     () -> new BatchDeliveryException("failed",
                                                      batch,
                                                      List.of(BatchItemOutcome.succeeded(0),
                                                              BatchItemOutcome.succeeded(1)),
                                                      null));
        assertThrows(IndexOutOfBoundsException.class,
                     () -> BatchDeliveryException.sequential("send", batch, 2, new RuntimeException()));
        assertThrows(IllegalArgumentException.class, () -> BatchItemOutcome.succeeded(-1));
    }

    private static <T> List<Message<T>> toList(MessageBatch<T> batch) {
        List<Message<T>> result = new ArrayList<>();
        batch.forEach(result::add);
        return result;
    }
}
