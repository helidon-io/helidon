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
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
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

        assertThat(defaults.retry().delay(), is(Duration.ofSeconds(1)));
        assertThat(defaults.retry().maxAttempts(), is(0));
        assertThat(defaults.onExhausted(), is(FailureDisposition.FAIL));
        assertThat(defaults.deadLetter().isEmpty(), is(true));

        FailurePolicy configured = FailurePolicy.create(Config.just("""
                retry:
                  delay: PT0.25S
                  max-attempts: 3
                on-exhausted: DEAD_LETTER
                dead-letter:
                  channel: orders-dlq
                """, MediaTypes.APPLICATION_YAML));

        assertThat(configured.retry().delay(), is(Duration.ofMillis(250)));
        assertThat(configured.retry().maxAttempts(), is(3));
        assertThat(configured.onExhausted(), is(FailureDisposition.DEAD_LETTER));
        assertThat(configured.deadLetter().orElseThrow().channel(), is("orders-dlq"));
    }

    @Test
    void testRetryValidation() {
        assertThrows(RuntimeException.class,
                     () -> FailurePolicy.builder()
                             .retry(RetryConfig.builder().delay(Duration.ZERO).build())
                             .build());
        assertThrows(RuntimeException.class,
                     () -> FailurePolicy.builder()
                             .retry(RetryConfig.builder().delay(Duration.ofNanos(-1)).build())
                             .build());
        assertThrows(RuntimeException.class,
                     () -> FailurePolicy.builder()
                             .retry(RetryConfig.builder().maxAttempts(-1).build())
                             .build());
        RuntimeException dropFailure = assertThrows(
                RuntimeException.class,
                () -> FailurePolicy.builder()
                        .onExhausted(FailureDisposition.DROP)
                        .build());
        assertThat(dropFailure.getMessage(), containsString("must be greater than zero for DROP"));
    }

    @Test
    void testCustomRetryImplementationsCannotBypassPolicyValidation() {
        RuntimeException nullDelay = assertThrows(
                RuntimeException.class,
                () -> FailurePolicy.builder().retry(retryConfig(null, 1)).build());
        assertThat(nullDelay.getMessage(), containsString("delay must be greater than zero"));
        RuntimeException zeroDelay = assertThrows(
                RuntimeException.class,
                () -> FailurePolicy.builder().retry(retryConfig(Duration.ZERO, 1)).build());
        assertThat(zeroDelay.getMessage(), containsString("delay must be greater than zero"));
        RuntimeException negativeAttempts = assertThrows(
                RuntimeException.class,
                () -> FailurePolicy.builder().retry(retryConfig(Duration.ofSeconds(1), -1)).build());
        assertThat(negativeAttempts.getMessage(), containsString("max-attempts must be zero or greater"));
    }

    @Test
    void testCustomNestedConfigsAreSnapshottedWithStableValueEquality() {
        MutableRetryConfig mutableRetry = new MutableRetryConfig(Duration.ofMillis(25), 3);
        MutableDeadLetterConfig mutableDeadLetter = new MutableDeadLetterConfig("orders-dlq");
        FailurePolicy policy = FailurePolicy.builder()
                .retry(mutableRetry)
                .onExhausted(FailureDisposition.DEAD_LETTER)
                .deadLetter(mutableDeadLetter)
                .build();
        FailurePolicy equivalent = FailurePolicy.builder()
                .retry(retryConfig(Duration.ofMillis(25), 3))
                .onExhausted(FailureDisposition.DEAD_LETTER)
                .deadLetter(() -> "orders-dlq")
                .build();

        assertThat(policy.retry(), not(sameInstance(mutableRetry)));
        assertThat(policy.deadLetter().orElseThrow(), not(sameInstance(mutableDeadLetter)));
        assertThat(policy, is(equivalent));
        assertThat(equivalent, is(policy));
        assertThat(policy.hashCode(), is(equivalent.hashCode()));
        int stableHashCode = policy.hashCode();

        mutableRetry.update(Duration.ZERO, -1);
        mutableDeadLetter.update("changed-dlq");

        assertThat(policy.retry().delay(), is(Duration.ofMillis(25)));
        assertThat(policy.retry().maxAttempts(), is(3));
        assertThat(policy.deadLetter().orElseThrow().channel(), is("orders-dlq"));
        assertThat(policy, is(equivalent));
        assertThat(equivalent, is(policy));
        assertThat(policy.hashCode(), is(stableHashCode));
    }

    @Test
    void testCustomNestedConfigValuesAreSampledOnlyOnce() {
        AtomicInteger delayReads = new AtomicInteger();
        AtomicInteger maxAttemptsReads = new AtomicInteger();
        AtomicInteger channelReads = new AtomicInteger();
        RetryConfig retry = new RetryConfig() {
            @Override
            public Duration delay() {
                return delayReads.getAndIncrement() == 0 ? Duration.ofMillis(25) : Duration.ZERO;
            }

            @Override
            public int maxAttempts() {
                return maxAttemptsReads.getAndIncrement() == 0 ? 3 : -1;
            }
        };
        DeadLetterConfig deadLetter = () -> channelReads.getAndIncrement() == 0 ? "orders-dlq" : " ";

        FailurePolicy policy = FailurePolicy.builder()
                .retry(retry)
                .onExhausted(FailureDisposition.DEAD_LETTER)
                .deadLetter(deadLetter)
                .build();

        assertThat(policy.retry().delay(), is(Duration.ofMillis(25)));
        assertThat(policy.retry().maxAttempts(), is(3));
        assertThat(policy.deadLetter().orElseThrow().channel(), is("orders-dlq"));
        assertThat(delayReads.get(), is(1));
        assertThat(maxAttemptsReads.get(), is(1));
        assertThat(channelReads.get(), is(1));
    }

    @Test
    void testDeadLetterValidation() {
        RuntimeException missingChannel = assertThrows(RuntimeException.class, DeadLetterConfig::create);
        assertThat(missingChannel.getMessage(), containsString("channel must be configured"));
        RuntimeException emptyDeadLetter = assertThrows(
                RuntimeException.class,
                () -> FailurePolicy.create(Config.just("""
                        retry:
                          max-attempts: 1
                        on-exhausted: DEAD_LETTER
                        dead-letter: {}
                        """, MediaTypes.APPLICATION_YAML)));
        assertThat(emptyDeadLetter.getMessage(), containsString("channel must be configured"));
        assertThrows(RuntimeException.class,
                     () -> FailurePolicy.builder()
                             .onExhausted(FailureDisposition.DEAD_LETTER)
                             .deadLetter(DeadLetterConfig.builder().channel("orders-dlq").build())
                             .build());
        assertThrows(RuntimeException.class,
                     () -> FailurePolicy.builder()
                             .onExhausted(FailureDisposition.DEAD_LETTER)
                             .retry(RetryConfig.builder().maxAttempts(3).build())
                             .build());
        assertThrows(RuntimeException.class,
                     () -> FailurePolicy.builder()
                             .onExhausted(FailureDisposition.DEAD_LETTER)
                             .retry(RetryConfig.builder().maxAttempts(3).build())
                             .deadLetter(DeadLetterConfig.builder().channel(" ").build())
                             .build());
        assertThrows(RuntimeException.class,
                     () -> FailurePolicy.builder()
                             .onExhausted(FailureDisposition.DROP)
                             .retry(RetryConfig.builder().maxAttempts(3).build())
                             .deadLetter(DeadLetterConfig.builder().channel("orders-dlq").build())
                             .build());

        DeadLetterConfig invalid = () -> " ";
        RuntimeException customFailure = assertThrows(
                RuntimeException.class,
                () -> FailurePolicy.builder()
                        .retry(RetryConfig.builder().maxAttempts(1).build())
                        .onExhausted(FailureDisposition.DEAD_LETTER)
                        .deadLetter(invalid)
                        .build());
        assertThat(customFailure.getMessage(), containsString("channel must not be blank"));
        DeadLetterConfig nullChannel = () -> null;
        RuntimeException nullFailure = assertThrows(
                RuntimeException.class,
                () -> FailurePolicy.builder()
                        .retry(RetryConfig.builder().maxAttempts(1).build())
                        .onExhausted(FailureDisposition.DEAD_LETTER)
                        .deadLetter(nullChannel)
                        .build());
        assertThat(nullFailure.getMessage(), containsString("channel must be configured"));
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
                .addHeader("binary", MessageHeaderValue.binary(new byte[] {1, 2}))
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
                              MessageHeader.create("binary", MessageHeaderValue.binary(new byte[] {1, 2})),
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
                .set(DeadLetterMessage.FAILURE_MESSAGE_METADATA, MessageHeaderValue.binary(new byte[] {1}))
                .build();
        DeadLetterMessage<String> wrongKind = new MetadataDeadLetterMessage(wrongKindMetadata);
        assertThat(wrongKind.failureType(), is("failure.Type"));
        IllegalStateException wrongFailureMessage = assertThrows(IllegalStateException.class,
                                                                  wrongKind::failureMessage);
        assertThat(wrongFailureMessage.getMessage(), containsString(DeadLetterMessage.FAILURE_MESSAGE_METADATA));
        assertThat(wrongFailureMessage.getMessage(), containsString("not a text value"));
    }

    private static RetryConfig retryConfig(Duration delay, int maxAttempts) {
        return new RetryConfig() {
            @Override
            public Duration delay() {
                return delay;
            }

            @Override
            public int maxAttempts() {
                return maxAttempts;
            }
        };
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

    private static final class MutableRetryConfig implements RetryConfig {
        private Duration delay;
        private int maxAttempts;

        private MutableRetryConfig(Duration delay, int maxAttempts) {
            this.delay = delay;
            this.maxAttempts = maxAttempts;
        }

        @Override
        public Duration delay() {
            return delay;
        }

        @Override
        public int maxAttempts() {
            return maxAttempts;
        }

        private void update(Duration delay, int maxAttempts) {
            this.delay = delay;
            this.maxAttempts = maxAttempts;
        }
    }

    private static final class MutableDeadLetterConfig implements DeadLetterConfig {
        private String channel;

        private MutableDeadLetterConfig(String channel) {
            this.channel = channel;
        }

        @Override
        public String channel() {
            return channel;
        }

        private void update(String channel) {
            this.channel = channel;
        }
    }
}
