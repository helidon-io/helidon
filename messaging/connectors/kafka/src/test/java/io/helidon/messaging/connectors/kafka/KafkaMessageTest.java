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

package io.helidon.messaging.connectors.kafka;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

class KafkaMessageTest {
    private static final String TEST_VALUE = "test";
    private static final String TEST_KEY = "key";
    private static final String HEADER_NAME = "header";
    private static final byte[] HEADER_VALUE = "value".getBytes(StandardCharsets.UTF_8);

    @Test
    void defaultKafkaMessageNack() {
        CompletableFuture<Throwable> nacked = new CompletableFuture<>();

        KafkaMessage<String, String> kafkaMessage = KafkaMessage.of(TEST_KEY,
                                                                    TEST_VALUE,
                                                                    () -> CompletableFuture.completedFuture(null),
                                                                    error -> {
                                                                        nacked.complete(error);
                                                                        return CompletableFuture.completedFuture(null);
                                                                    });

        RuntimeException failure = new RuntimeException("boom");

        assertComplete(kafkaMessage.nack(failure));
        assertSame(failure, nacked.getNow(null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void kafkaMessageWithNackPreservesOriginalAckAndKafkaData() {
        CompletableFuture<Void> acked = new CompletableFuture<>();

        KafkaMessage<String, String> kafkaMessage = KafkaMessage.of(TEST_KEY,
                                                                    TEST_VALUE,
                                                                    () -> {
                                                                        acked.complete(null);
                                                                        return CompletableFuture.completedFuture(null);
                                                                    });
        kafkaMessage.getHeaders().add(HEADER_NAME, HEADER_VALUE);

        KafkaMessage<String, String> wrappedKafkaMessage = kafkaMessage.withNack(error -> CompletableFuture.completedFuture(null));

        assertEquals(TEST_KEY, wrappedKafkaMessage.getKey().orElseThrow());
        assertArrayEquals(HEADER_VALUE, wrappedKafkaMessage.getHeaders().lastHeader(HEADER_NAME).value());

        assertComplete(wrappedKafkaMessage.ack());
        assertComplete(acked);
    }

    @Test
    @SuppressWarnings("unchecked")
    void kafkaMessageWithAckPreservesOriginalNackAndKafkaData() {
        CompletableFuture<Throwable> nacked = new CompletableFuture<>();

        KafkaMessage<String, String> kafkaMessage = KafkaMessage.of(TEST_KEY,
                                                                    TEST_VALUE,
                                                                    () -> CompletableFuture.completedFuture(null),
                                                                    error -> {
                                                                        nacked.complete(error);
                                                                        return CompletableFuture.completedFuture(null);
                                                                    });
        kafkaMessage.getHeaders().add(HEADER_NAME, HEADER_VALUE);

        KafkaMessage<String, String> wrappedKafkaMessage = kafkaMessage.withAck(() -> CompletableFuture.completedFuture(null));

        assertEquals(TEST_KEY, wrappedKafkaMessage.getKey().orElseThrow());
        assertArrayEquals(HEADER_VALUE, wrappedKafkaMessage.getHeaders().lastHeader(HEADER_NAME).value());

        RuntimeException failure = new RuntimeException("boom");
        assertComplete(wrappedKafkaMessage.nack(failure));
        assertSame(failure, nacked.getNow(null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void kafkaMessageWithAckAndNackPreserveKafkaData() {
        CompletableFuture<Void> baseAck = new CompletableFuture<>();
        CompletableFuture<Void> wrappedAck = new CompletableFuture<>();
        CompletableFuture<Throwable> wrappedNack = new CompletableFuture<>();

        KafkaMessage<String, String> kafkaMessage = KafkaMessage.of(TEST_KEY,
                                                                    TEST_VALUE,
                                                                    () -> {
                                                                        baseAck.complete(null);
                                                                        return CompletableFuture.completedFuture(null);
                                                                    });
        kafkaMessage.getHeaders().add(HEADER_NAME, HEADER_VALUE);

        KafkaMessage<String, String> wrappedKafkaMessage = kafkaMessage
                .withAck(() -> {
                    wrappedAck.complete(null);
                    return CompletableFuture.completedFuture(null);
                })
                .withNack(error -> {
                    wrappedNack.complete(error);
                    return CompletableFuture.completedFuture(null);
                });

        assertEquals(TEST_KEY, wrappedKafkaMessage.getKey().orElseThrow());
        assertArrayEquals(HEADER_VALUE, wrappedKafkaMessage.getHeaders().lastHeader(HEADER_NAME).value());

        assertFalse(baseAck.isDone());
        assertComplete(wrappedKafkaMessage.ack());
        assertFalse(baseAck.isDone());
        assertComplete(wrappedAck);

        RuntimeException failure = new RuntimeException("boom");
        assertComplete(wrappedKafkaMessage.nack(failure));
        assertSame(failure, wrappedNack.getNow(null));
    }

    private void assertComplete(CompletionStage<Void> stage) {
        try {
            stage.toCompletableFuture().get(10, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            fail(e);
        }
    }
}
