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

import java.time.Duration;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FailurePolicyTest {
    @Test
    void testDefaultsAndConfiguredValues() {
        FailurePolicy defaults = FailurePolicy.create();

        assertThat(defaults.retryDelay(), is(Duration.ofSeconds(1)));
        assertThat(defaults.maxAttempts(), is(0));
        assertThat(defaults.onExhausted(), is(FailureDisposition.FAIL));
        assertThat(defaults.deadLetterChannel().isEmpty(), is(true));

        FailurePolicy configured = FailurePolicy.create(Config.just("""
                retry:
                  delay: PT0.25S
                  max-attempts: 3
                on-exhausted: DEAD_LETTER
                dead-letter:
                  channel: orders-dlq
                """, MediaTypes.APPLICATION_YAML));

        assertThat(configured.retryDelay(), is(Duration.ofMillis(250)));
        assertThat(configured.maxAttempts(), is(3));
        assertThat(configured.onExhausted(), is(FailureDisposition.DEAD_LETTER));
        assertThat(configured.deadLetterChannel().orElseThrow(), is("orders-dlq"));
    }

    @Test
    void testRetryValidation() {
        assertThrows(RuntimeException.class,
                     () -> FailurePolicy.builder().retryDelay(Duration.ZERO).build());
        assertThrows(RuntimeException.class,
                     () -> FailurePolicy.builder().retryDelay(Duration.ofNanos(-1)).build());
        assertThrows(RuntimeException.class,
                     () -> FailurePolicy.builder().maxAttempts(-1).build());
        RuntimeException dropFailure = assertThrows(
                RuntimeException.class,
                () -> FailurePolicy.builder()
                        .onExhausted(FailureDisposition.DROP)
                        .build());
        assertThat(dropFailure.getMessage(), containsString("must be greater than zero for DROP"));
    }

    @Test
    void testDeadLetterValidation() {
        assertThrows(RuntimeException.class,
                     () -> FailurePolicy.builder()
                             .onExhausted(FailureDisposition.DEAD_LETTER)
                             .deadLetterChannel("orders-dlq")
                             .build());
        assertThrows(RuntimeException.class,
                     () -> FailurePolicy.builder()
                             .onExhausted(FailureDisposition.DEAD_LETTER)
                             .maxAttempts(3)
                             .build());
        assertThrows(RuntimeException.class,
                     () -> FailurePolicy.builder()
                             .onExhausted(FailureDisposition.DEAD_LETTER)
                             .maxAttempts(3)
                             .deadLetterChannel(" ")
                             .build());
        assertThrows(RuntimeException.class,
                     () -> FailurePolicy.builder()
                             .onExhausted(FailureDisposition.DROP)
                             .maxAttempts(3)
                             .deadLetterChannel("orders-dlq")
                             .build());
    }

    @Test
    void testDeadLetterMessageRetainsOriginalAndOverridesReservedHeaders() {
        Message<String> original = Message.builder("orders")
                .header("trace-id", "trace-1")
                .header(DeadLetterMessage.SOURCE_CHANNEL_HEADER, "spoofed")
                .build();
        IllegalStateException failure = new IllegalStateException();

        DeadLetterMessage<String> deadLetter = DeadLetterMessage.create(original, "orders-in", 3, failure);

        assertThat(deadLetter.originalMessage(), sameInstance(original));
        assertThat(deadLetter.entity(), is("orders"));
        assertThat(deadLetter.sourceChannel(), is("orders-in"));
        assertThat(deadLetter.attempts(), is(3));
        assertThat(deadLetter.failureType(), is(IllegalStateException.class.getName()));
        assertThat(deadLetter.failureMessage(), is(""));
        assertThat(deadLetter.header("trace-id").orElseThrow(), is("trace-1"));
        assertThat(deadLetter.header(DeadLetterMessage.SOURCE_CHANNEL_HEADER).orElseThrow(), is("orders-in"));
        assertThat(deadLetter.header(DeadLetterMessage.ATTEMPTS_HEADER).orElseThrow(), is("3"));
        assertThat(deadLetter.header(DeadLetterMessage.FAILURE_TYPE_HEADER).orElseThrow(),
                   is(IllegalStateException.class.getName()));
        assertThat(deadLetter.header(DeadLetterMessage.FAILURE_MESSAGE_HEADER).orElseThrow(), is(""));
        assertThrows(UnsupportedOperationException.class,
                     () -> deadLetter.headers().put("mutable", "false"));
    }

    @Test
    void testDeadLetterMessageValidatesConstruction() {
        Message<String> message = Message.create("orders");
        RuntimeException failure = new IllegalStateException("failed");

        assertThrows(NullPointerException.class,
                     () -> DeadLetterMessage.create(null, "orders-in", 1, failure));
        assertThrows(IllegalArgumentException.class,
                     () -> DeadLetterMessage.create(message, " ", 1, failure));
        assertThrows(IllegalArgumentException.class,
                     () -> DeadLetterMessage.create(message, "orders-in", 0, failure));
        assertThrows(NullPointerException.class,
                     () -> DeadLetterMessage.create(message, "orders-in", 1, null));
    }
}
