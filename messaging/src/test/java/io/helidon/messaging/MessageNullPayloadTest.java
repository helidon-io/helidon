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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageNullPayloadTest {
    @Test
    void publicFactoriesRejectNullPayloads() {
        assertThrows(NullPointerException.class, () -> Message.create(null));
        assertThrows(NullPointerException.class, () -> Message.builder(null));
    }

    @Test
    void defaultMessageRejectsNullPayload() {
        assertThrows(NullPointerException.class, () -> new DefaultMessage<>(null, MessageHeaders.empty()));
        assertThrows(NullPointerException.class, () -> new DefaultMessage<>("payload", null));
    }

    @Test
    void channelRejectsNullPayloadFromCustomMessage() {
        Message<String> nullMessage = new Message<>() {
            @Override
            public String entity() {
                return null;
            }

            @Override
            public MessageHeaders headers() {
                return MessageHeaders.empty();
            }
        };
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> channel = builder.channel("messages", String.class);
        builder.payloadSink(channel, ignored -> { });

        try (MessagingGraph graph = builder.build()) {
            graph.start();

            NullPointerException failure = assertThrows(NullPointerException.class,
                                                        () -> graph.emitter(channel).emit(nullMessage));

            assertThat(failure.getMessage(), is("Message entity"));
        }
    }

    @Test
    void channelRejectsOrdinaryMessageWhoseEntityFails() {
        AtomicInteger entityCalls = new AtomicInteger();
        MessagingException entityFailure = new MessagingException("entity unavailable");
        Message<String> unavailable = unavailableMessage(entityCalls, entityFailure);
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> channel = builder.channel("unavailable", String.class);
        builder.messageSink(channel, ignored -> { });

        try (MessagingGraph graph = builder.build()) {
            graph.start();

            MessagingException failure = assertThrows(MessagingException.class,
                                                       () -> graph.emitter(channel).emit(unavailable));

            assertThat(failure, sameInstance(entityFailure));
            assertThat(entityCalls.get(), is(1));
        }
    }

    @Test
    void channelRoutesDeadLetterWhoseEntityFails() {
        AtomicInteger entityCalls = new AtomicInteger();
        Message<String> unavailable = unavailableMessage(
                entityCalls,
                new MessagingException("entity unavailable"));
        DeadLetterMessage<String> deadLetter = DeadLetterMessage.create(
                unavailable,
                "source",
                1,
                new MessagingException("mapping failed"));
        AtomicReference<Message<String>> delivered = new AtomicReference<>();
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> channel = builder.channel("dead-letter", String.class);
        builder.messageSink(channel, delivered::set);

        try (MessagingGraph graph = builder.build()) {
            graph.start();
            graph.emitter(channel).emit(deadLetter);
        }

        assertThat(delivered.get(), is(deadLetter));
        assertThat(entityCalls.get(), is(1));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void channelStillValidatesAccessibleDeadLetterPayloadType() {
        DeadLetterMessage<Integer> deadLetter = DeadLetterMessage.create(
                Message.create(42),
                "source",
                1,
                new MessagingException("handler failed"));
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> channel = builder.channel("dead-letter", String.class);
        builder.messageSink(channel, ignored -> { });

        try (MessagingGraph graph = builder.build()) {
            graph.start();

            IllegalArgumentException failure = assertThrows(
                    IllegalArgumentException.class,
                    () -> graph.emitter(channel).emit((Message) deadLetter));

            assertThat(failure.getMessage(), is("Channel expected payload type class java.lang.String"
                                                        + " but received java.lang.Integer"));
        }
    }

    @Test
    void imperativePayloadProcessorRejectsNullResult() {
        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> source = builder.channel("source", String.class);
        MessagingChannel<String> target = builder.channel("target", String.class);
        builder.payloadProcessor(source, target, ignored -> null)
                .payloadSink(target, ignored -> { });

        try (MessagingGraph graph = builder.build()) {
            graph.start();
            BatchDeliveryException failure = assertThrows(BatchDeliveryException.class,
                                                          () -> graph.emitter(source).emit("payload"));

            assertThat(failure.getCause(), instanceOf(NullPointerException.class));
            assertThat(failure.outcome(0).status(), is(BatchItemStatus.INDETERMINATE));
        }
    }

    private static Message<String> unavailableMessage(AtomicInteger entityCalls,
                                                       MessagingException entityFailure) {
        return new Message<>() {
            @Override
            public String entity() {
                entityCalls.incrementAndGet();
                throw entityFailure;
            }

            @Override
            public MessageHeaders headers() {
                return MessageHeaders.empty();
            }
        };
    }
}
