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
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class BatchDefaultsTest {
    @Test
    void emitterWrapsSingleMessageInBatch() {
        AtomicReference<MessageBatch<?>> received = new AtomicReference<>();
        Emitter<String> emitter = batch -> received.set(batch);
        Message<String> message = Message.create("one");

        emitter.emitMessage(message);

        assertEquals(1, received.get().size());
        assertSame(message, received.get().get(0));
    }

    @Test
    void messagingRuntimeWrapsSingleMessageInBatch() {
        AtomicReference<MessageBatch<?>> received = new AtomicReference<>();
        AtomicReference<String> receivedChannel = new AtomicReference<>();
        MessagingRuntime runtime = new MessagingRuntime() {
            @Override
            public <T> void emitBatch(String channel, MessageBatch<? extends T> batch) {
                receivedChannel.set(channel);
                received.set(batch);
            }
        };
        Message<String> message = Message.create("one");

        runtime.emit("orders", message);

        assertEquals("orders", receivedChannel.get());
        assertEquals(1, received.get().size());
        assertSame(message, received.get().get(0));
    }

    @Test
    void outgoingConnectorWrapsSingleMessageInBatch() {
        AtomicReference<MessageBatch<?>> received = new AtomicReference<>();
        OutgoingConnector connector = new OutgoingConnector() {
            @Override
            public void start() {
            }

            @Override
            public void sendBatch(MessageBatch<?> batch) {
                received.set(batch);
            }

            @Override
            public void forceClose() {
            }

            @Override
            public void close() {
            }
        };
        Message<String> message = Message.create("one");

        connector.send(message);

        assertEquals(1, received.get().size());
        assertSame(message, received.get().get(0));
    }

    @Test
    void consumerRegistrationReceivesOnlyBatches() {
        AtomicReference<MessageBatch<?>> received = new AtomicReference<>();
        ConsumerRegistration registration = new ConsumerRegistration() {
            @Override
            public String channel() {
                return "orders";
            }

            @Override
            public Class<?> payloadType() {
                return String.class;
            }

            @Override
            public void dispatch(MessageBatch<?> batch) {
                received.set(batch);
            }
        };
        MessageBatch<String> batch = batch();

        registration.dispatch(batch);

        assertSame(batch, received.get());
    }

    private static MessageBatch<String> batch() {
        return MessageBatch.create(List.of(Message.create("one"),
                                           Message.create("two"),
                                           Message.create("three")));
    }

}
