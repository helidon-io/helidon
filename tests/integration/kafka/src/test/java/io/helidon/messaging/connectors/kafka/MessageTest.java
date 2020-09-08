/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.messaging.connectors.kafka;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.fail;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;

class MessageTest {

    private static final String TEST_VALUE = "test";

    @Test
    void defaultMessageAck() {
        assertComplete(Message.of(TEST_VALUE).ack());
        assertComplete(Message.of(TEST_VALUE, () -> CompletableFuture.completedFuture(null)).ack());
    }

    @Test
    void defaultKafkaMessageAck() {
        assertComplete(KafkaMessage.of(TEST_VALUE).ack());
        assertComplete(KafkaMessage.of(TEST_VALUE, () -> CompletableFuture.completedFuture(null)).ack());
        assertComplete(KafkaMessage.of(TEST_VALUE, TEST_VALUE).ack());
    }

    private void assertComplete(CompletionStage<Void> stage) {
        try {
            stage.toCompletableFuture().get(10, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            fail(e);
        }
    }
}