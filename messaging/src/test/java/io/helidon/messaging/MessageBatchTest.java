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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

        assertThat(batch.id(), is("batch-1"));
        assertThat(batch.size(), is(2));
        assertThat(batch.get(0), sameInstance(first));
        assertThat(batch.get(1), sameInstance(second));
        assertThat(batch.payloads(), is(List.of("one", "two")));
        assertThat(toList(batch), is(batch.messages()));
        assertThrows(UnsupportedOperationException.class, () -> batch.messages().clear());
        assertThrows(UnsupportedOperationException.class, () -> batch.payloads().clear());
    }

    @Test
    void generatedBuilderCreatesIndependentImmutableBatches() {
        Message<String> first = Message.create("one");
        Message<String> second = Message.create("two");
        Message<String> third = Message.create("three");
        List<Message<String>> appendedMessages = List.of(second);
        MessageBatchConfig.Builder<String> builder = MessageBatch.<String>builder()
                .id("batch-1")
                .add(first)
                .addMessages(appendedMessages);

        MessageBatchConfig<String> config = builder.buildPrototype();
        MessageBatch<String> firstBatch = config.build();
        MessageBatch<String> secondBatch = config.build();

        assertThat(config.id(), is("batch-1"));
        assertThat(config.messages(), is(List.of(first, second)));
        assertThrows(UnsupportedOperationException.class, () -> config.messages().clear());
        assertThat(firstBatch.payloads(), is(List.of("one", "two")));
        assertThat(secondBatch.payloads(), is(List.of("one", "two")));
        assertThat(firstBatch.sameDelivery(secondBatch), is(false));

        List<Message<String>> replacementMessages = List.of(third);
        MessageBatch<String> replacement = builder.messages(replacementMessages).build();
        assertThat(replacement.id(), is("batch-1"));
        assertThat(replacement.payloads(), is(List.of("three")));
        assertThat(firstBatch.payloads(), is(List.of("one", "two")));
    }

    @Test
    void generatedBuilderUsesStableDefaultId() {
        Message<String> message = Message.create("one");
        MessageBatchConfig.Builder<String> builder = MessageBatch.<String>builder().add(message);

        MessageBatch<String> first = builder.build();
        MessageBatch<String> second = builder.build();
        MessageBatch<String> other = MessageBatch.<String>builder().add(message).build();

        assertThat(UUID.fromString(first.id()).toString(), is(first.id()));
        assertThat(first.id().length() <= MessageBatch.MAX_ID_LENGTH, is(true));
        assertThat(second.id(), is(first.id()));
        assertThat(first.sameDelivery(second), is(false));
        assertThat(other.id().equals(first.id()), is(false));
    }

    @Test
    void createAcceptsCovariantMessages() {
        Message<Integer> message = Message.create(42);

        MessageBatch<Number> batch = MessageBatch.<Number>create(List.of(message));

        assertThat(batch.get(0), sameInstance(message));
        assertThat(batch.payloads(), is(List.of(42)));
    }

    @Test
    void materializesPayloadsOnlyOnDemand() {
        AtomicInteger entityCalls = new AtomicInteger();
        MessagingException unavailable = new MessagingException("entity unavailable");
        Message<String> message = new Message<>() {
            @Override
            public String entity() {
                entityCalls.incrementAndGet();
                throw unavailable;
            }

            @Override
            public MessageHeaders headers() {
                return MessageHeaders.empty();
            }
        };

        MessageBatch<String> batch = MessageBatch.create(message);

        assertThat(batch.get(0), sameInstance(message));
        assertThat(entityCalls.get(), is(0));
        assertThat(assertThrows(MessagingException.class, batch::payloads), sameInstance(unavailable));
        assertThat(entityCalls.get(), is(1));
    }

    @Test
    void cachesMaterializedPayloadSnapshot() {
        AtomicInteger entityCalls = new AtomicInteger();
        Message<String> message = new Message<>() {
            @Override
            public String entity() {
                entityCalls.incrementAndGet();
                return "value";
            }

            @Override
            public MessageHeaders headers() {
                return MessageHeaders.empty();
            }
        };
        MessageBatch<String> batch = MessageBatch.create(message);

        List<String> first = batch.payloads();
        List<String> second = batch.payloads();

        assertThat(first, is(List.of("value")));
        assertThat(second, sameInstance(first));
        assertThat(entityCalls.get(), is(1));
        assertThrows(UnsupportedOperationException.class, () -> first.add("other"));
    }

    @Test
    void rejectsNullDuringPayloadMaterialization() {
        Message<String> message = new Message<>() {
            @Override
            public String entity() {
                return null;
            }

            @Override
            public MessageHeaders headers() {
                return MessageHeaders.empty();
            }
        };
        MessageBatch<String> batch = MessageBatch.create(message);

        NullPointerException failure = assertThrows(NullPointerException.class, batch::payloads);

        assertThat(failure.getMessage(), is("Message entity"));
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

        assertThat(original.sameDelivery(derived), is(true));
        assertThat(original.sameDelivery(copiedIdentity), is(false));
        assertThat(original.sameDelivery(retry), is(false));
        assertThat(retry.sameDelivery(derivedRetry), is(true));
        assertThat(retry.isRetainedSubsetOf(original), is(true));
        assertThat(copiedIdentity.isRetainedSubsetOf(original), is(false));
        assertThat(derivedRetry.isRetainedSubsetOf(original), is(false));
        assertThat(retry.payloads(), is(List.of("one", "three")));
        assertThat(retry.id(), is("batch-1"));
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

        assertThat(selected.payloads(), is(List.of("one", "three")));
        assertThat(selected.get(0), sameInstance(first));
        assertThat(selected.get(1), sameInstance(third));
        assertThat(selected.isRetainedSubsetOf(original), is(true));
        assertThat(completeSelection.sameDelivery(original), is(true));
    }

    @Test
    void rejectsInvalidConstruction() {
        assertThrows(IllegalArgumentException.class, () -> MessageBatch.create(List.of()));
        assertThrows(IllegalArgumentException.class, () -> MessageBatch.builder().build());
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

        assertThat(sequential.batch(), sameInstance(batch));
        assertThat(sequential.getCause(), sameInstance(failure));
        assertThat(sequential.outcome(0).status(), is(BatchItemStatus.SUCCEEDED));
        assertThat(sequential.outcome(1).status(), is(BatchItemStatus.INDETERMINATE));
        assertThat(sequential.outcome(1).failure().orElseThrow(), sameInstance(failure));
        assertThat(sequential.outcome(2).status(), is(BatchItemStatus.NOT_ATTEMPTED));
        assertThat(sequential.outcome(2).failure().isPresent(), is(false));
        assertThrows(UnsupportedOperationException.class, () -> sequential.outcomes().clear());

        BatchDeliveryException indeterminate = BatchDeliveryException.indeterminate("send", batch, failure);
        assertThat(indeterminate.outcomes().stream()
                           .allMatch(outcome -> outcome.status() == BatchItemStatus.INDETERMINATE), is(true));

        BatchDeliveryException attemptedPrefix = BatchDeliveryException.attemptedPrefix("process", batch, 1, failure);
        assertThat(attemptedPrefix.outcomes().stream().map(BatchItemOutcome::status).toList(),
                   is(List.of(BatchItemStatus.INDETERMINATE,
                              BatchItemStatus.INDETERMINATE,
                              BatchItemStatus.NOT_ATTEMPTED)));

        BatchDeliveryException notAttempted = BatchDeliveryException.notAttempted("send", batch, failure);
        assertThat(notAttempted.outcomes().stream()
                           .allMatch(outcome -> outcome.status() == BatchItemStatus.NOT_ATTEMPTED), is(true));
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
