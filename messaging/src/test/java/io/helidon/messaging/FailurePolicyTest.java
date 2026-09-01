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
import java.util.List;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FailurePolicyTest {
    private static final String NON_PORTABLE_FAILURE_TYPE_HEADER =
            "helidon_messaging_dead_letter_failure_type";
    private static final String NON_PORTABLE_FAILURE_MESSAGE_HEADER =
            "helidon_messaging_dead_letter_failure_message";

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
    void testDeadLetterMessageRetainsOriginalAndKeepsFailureDiagnosticsLocal() {
        MessageMetadata originalMetadata = MessageMetadata.builder()
                .set("application.local.trace", "local-trace")
                .set(DeadLetterMessage.FAILURE_TYPE_METADATA, "spoofed-type")
                .set(DeadLetterMessage.FAILURE_MESSAGE_METADATA, "spoofed-message")
                .build();
        Message<String> original = Message.builder("orders")
                .addHeader("trace-id", "trace-1")
                .addHeader(DeadLetterMessage.SOURCE_CHANNEL_HEADER, "spoofed-first")
                .addHeader("binary", HeaderValue.binary(new byte[] {1, 2}))
                .addHeader("trace-id", "trace-2")
                .addHeader(DeadLetterMessage.SOURCE_CHANNEL_HEADER, "spoofed-last")
                .addHeader(DeadLetterMessage.ATTEMPTS_HEADER, "99")
                .addHeader(NON_PORTABLE_FAILURE_TYPE_HEADER, "spoofed-type")
                .addHeader(NON_PORTABLE_FAILURE_MESSAGE_HEADER, "spoofed-message")
                .localMetadata(originalMetadata)
                .build();
        IllegalStateException failure = new IllegalStateException("actual-failure");

        DeadLetterMessage<String> deadLetter = DeadLetterMessage.create(original, "orders-in", 3, failure);

        assertThat(deadLetter.originalMessage(), sameInstance(original));
        assertThat(deadLetter.entity(), is("orders"));
        assertThat(deadLetter.sourceChannel(), is("orders-in"));
        assertThat(deadLetter.attempts(), is(3));
        assertThat(deadLetter.failureType(), is(IllegalStateException.class.getName()));
        assertThat(deadLetter.failureMessage(), is("actual-failure"));
        assertThat(deadLetter.localMetadata().text("application.local.trace").orElseThrow(), is("local-trace"));
        assertThat(deadLetter.localMetadata().text(DeadLetterMessage.FAILURE_TYPE_METADATA).orElseThrow(),
                   is(IllegalStateException.class.getName()));
        assertThat(deadLetter.localMetadata().text(DeadLetterMessage.FAILURE_MESSAGE_METADATA).orElseThrow(),
                   is("actual-failure"));
        assertThat(original.localMetadata().text(DeadLetterMessage.FAILURE_TYPE_METADATA).orElseThrow(),
                   is("spoofed-type"));
        assertThat(original.localMetadata().text(DeadLetterMessage.FAILURE_MESSAGE_METADATA).orElseThrow(),
                   is("spoofed-message"));
        assertThat(deadLetter.headers().entries(),
                   is(List.of(MessageHeader.create("trace-id", "trace-1"),
                              MessageHeader.create("binary", HeaderValue.binary(new byte[] {1, 2})),
                              MessageHeader.create("trace-id", "trace-2"),
                              MessageHeader.create(DeadLetterMessage.SOURCE_CHANNEL_HEADER, "orders-in"),
                              MessageHeader.create(DeadLetterMessage.ATTEMPTS_HEADER, "3"))));
        assertThat(deadLetter.header("trace-id").orElseThrow(), is("trace-2"));
        assertThat(deadLetter.header(DeadLetterMessage.SOURCE_CHANNEL_HEADER).orElseThrow(), is("orders-in"));
        assertThat(deadLetter.header(DeadLetterMessage.ATTEMPTS_HEADER).orElseThrow(), is("3"));
        assertThat(deadLetter.headers().contains(NON_PORTABLE_FAILURE_TYPE_HEADER), is(false));
        assertThat(deadLetter.headers().contains(NON_PORTABLE_FAILURE_MESSAGE_HEADER), is(false));
        assertThat(deadLetter.headers().all(DeadLetterMessage.SOURCE_CHANNEL_HEADER).size(), is(1));
        assertThat(deadLetter.headers().all(DeadLetterMessage.ATTEMPTS_HEADER).size(), is(1));
        assertThat(original.headers().all(DeadLetterMessage.SOURCE_CHANNEL_HEADER).size(), is(2));
        assertThat(original.headers().contains(NON_PORTABLE_FAILURE_TYPE_HEADER), is(true));
        assertThat(original.headers().contains(NON_PORTABLE_FAILURE_MESSAGE_HEADER), is(true));
        assertThrows(UnsupportedOperationException.class,
                     () -> deadLetter.headers().entries().add(MessageHeader.create("mutable", "false")));
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

    @Test
    void testDeadLetterDefaultAccessorsRequireTextMetadata() {
        DeadLetterMessage<String> missing = new MetadataDeadLetterMessage(MessageMetadata.empty());

        IllegalStateException missingFailureType = assertThrows(IllegalStateException.class, missing::failureType);
        assertThat(missingFailureType.getMessage(), containsString(DeadLetterMessage.FAILURE_TYPE_METADATA));

        MessageMetadata wrongKindMetadata = MessageMetadata.builder()
                .set(DeadLetterMessage.FAILURE_TYPE_METADATA, "failure.Type")
                .set(DeadLetterMessage.FAILURE_MESSAGE_METADATA, HeaderValue.binary(new byte[] {1}))
                .build();
        DeadLetterMessage<String> wrongKind = new MetadataDeadLetterMessage(wrongKindMetadata);
        assertThat(wrongKind.failureType(), is("failure.Type"));
        IllegalStateException wrongFailureMessage = assertThrows(IllegalStateException.class,
                                                                  wrongKind::failureMessage);
        assertThat(wrongFailureMessage.getMessage(), containsString(DeadLetterMessage.FAILURE_MESSAGE_METADATA));
        assertThat(wrongFailureMessage.getMessage(), containsString("not a text value"));
    }

    private record MetadataDeadLetterMessage(MessageMetadata localMetadata) implements DeadLetterMessage<String> {
        @Override
        public Message<String> originalMessage() {
            return Message.create("orders");
        }

        @Override
        public String sourceChannel() {
            return "orders-in";
        }

        @Override
        public int attempts() {
            return 1;
        }

        @Override
        public String entity() {
            return "orders";
        }

        @Override
        public MessageHeaders headers() {
            return MessageHeaders.empty();
        }
    }
}
