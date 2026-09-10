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
import java.util.Set;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.faulttolerance.Retry;
import io.helidon.faulttolerance.RetryConfig;

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
        RetryConfig defaultRetry = defaults.retry().prototype();

        assertThat(defaultRetry.delay(), is(Duration.ofSeconds(1)));
        assertThat(defaultRetry.calls(), is(Integer.MAX_VALUE));
        assertThat(defaultRetry.delayFactor(), is(2.0));
        assertThat(defaultRetry.jitterFactor(), is(-1.0));
        assertThat(defaultRetry.maxDelay().orElseThrow(), is(Duration.ofMinutes(1)));
        assertThat(defaultRetry.overallTimeout(), is(Duration.ofNanos(Long.MAX_VALUE)));
        assertThat(defaultRetry.applyOn(), is(Set.of(RuntimeException.class)));
        assertThat(defaults.onExhausted(), is(FailureDisposition.FAIL));
        assertThat(defaults.deadLetter().isEmpty(), is(true));

        FailurePolicy configured = FailurePolicy.create(Config.just("""
                retry:
                  delay: PT0.25S
                  calls: 3
                on-exhausted: DEAD_LETTER
                dead-letter:
                  channel: orders-dlq
                """, MediaTypes.APPLICATION_YAML));

        RetryConfig configuredRetry = configured.retry().prototype();
        assertThat(configuredRetry.delay(), is(Duration.ofMillis(250)));
        assertThat(configuredRetry.calls(), is(3));
        assertThat(configuredRetry.delayFactor(), is(-1.0));
        assertThat(configuredRetry.jitterFactor(), is(-1.0));
        assertThat(configuredRetry.maxDelay().isEmpty(), is(true));
        assertThat(configuredRetry.overallTimeout(), is(RetryConfig.DEFAULT_OVERALL_TIMEOUT));
        assertThat(configured.onExhausted(), is(FailureDisposition.DEAD_LETTER));
        assertThat(configured.deadLetter().orElseThrow().channel(), is("orders-dlq"));

        Retry explicit = Retry.builder()
                .calls(1)
                .delay(Duration.ZERO)
                .overallTimeout(Duration.ofSeconds(5))
                .build();
        FailurePolicy configThenProgrammatic = FailurePolicy.builder()
                .config(Config.just("retry.delay: PT0.25S", MediaTypes.APPLICATION_YAML))
                .retry(explicit)
                .build();
        assertThat(configThenProgrammatic.retry(), sameInstance(explicit));

        FailurePolicy programmaticThenConfig = FailurePolicy.builder()
                .retry(explicit)
                .config(Config.just("retry.delay: PT0.25S", MediaTypes.APPLICATION_YAML))
                .build();
        assertThat(programmaticThenConfig.retry().prototype().delay(), is(Duration.ofMillis(250)));
        assertThat(programmaticThenConfig.retry().prototype().calls(), is(RetryConfig.DEFAULT_CALLS));
    }

    @Test
    void testAbsentRetryUsesSharedInstance() {
        Retry shared = FailurePolicy.create().retry();

        assertThat(FailurePolicy.create().retry(), sameInstance(shared));
        assertThat(FailurePolicy.builder().onExhausted(FailureDisposition.DROP).build().retry(), sameInstance(shared));
        assertThat(FailurePolicy.create(Config.just("on-exhausted: DROP", MediaTypes.APPLICATION_YAML)).retry(),
                   sameInstance(shared));
    }

    @Test
    void testExplicitRetryAfterConfigWinsWhenValuesInitiallyMatch() {
        Config config = Config.just("""
                retry:
                  delay: PT0.25S
                """, MediaTypes.APPLICATION_YAML);
        Retry explicit = Retry.builder().config(config.get("retry")).build();

        FailurePolicy policy = FailurePolicy.builder()
                .config(config)
                .retry(explicit)
                .build();

        assertThat(policy.retry(), sameInstance(explicit));
        assertThat(policy.retry().prototype().calls(), is(3));
        assertThat(policy.retry().prototype().overallTimeout(), is(Duration.ofSeconds(1)));
    }

    @Test
    void testRetryValidation() {
        assertThrows(RuntimeException.class,
                     () -> FailurePolicy.builder()
                             .retry(Retry.builder().delay(Duration.ofNanos(-1)).build())
                             .build());
        assertThrows(RuntimeException.class,
                     () -> FailurePolicy.builder()
                             .retry(Retry.builder().calls(0).build())
                             .build());
        FailurePolicy drop = FailurePolicy.builder()
                .onExhausted(FailureDisposition.DROP)
                .build();
        assertThat(drop.onExhausted(), is(FailureDisposition.DROP));
    }

    @Test
    void testNestedConfigsAreSnapshotted() {
        Retry retry = Retry.builder().from(FailurePolicy.create().retry().prototype())
                .delay(Duration.ofMillis(25))
                .calls(3)
                .build();
        var deadLetter = DeadLetterConfig.builder().channel("orders-dlq");
        FailurePolicy policy = FailurePolicy.builder()
                .retry(retry)
                .onExhausted(FailureDisposition.DEAD_LETTER)
                .deadLetter(deadLetter.build())
                .build();

        deadLetter.channel("changed-dlq");

        assertThat(policy.retry(), sameInstance(retry));
        assertThat(policy.retry().prototype().delay(), is(Duration.ofMillis(25)));
        assertThat(policy.retry().prototype().calls(), is(3));
        assertThat(policy.deadLetter().orElseThrow().channel(), is("orders-dlq"));
    }

    @Test
    void testDeadLetterValidation() {
        RuntimeException missingChannel = assertThrows(RuntimeException.class, DeadLetterConfig::create);
        assertThat(missingChannel.getMessage(), containsString("channel must be configured"));
        RuntimeException emptyDeadLetter = assertThrows(
                RuntimeException.class,
                () -> FailurePolicy.create(Config.just("""
                        retry:
                          calls: 1
                        on-exhausted: DEAD_LETTER
                        dead-letter: {}
                        """, MediaTypes.APPLICATION_YAML)));
        assertThat(emptyDeadLetter.getMessage(), containsString("channel must be configured"));
        RuntimeException blankChannel = assertThrows(RuntimeException.class,
                () -> DeadLetterConfig.builder().channel(" ").build());
        assertThat(blankChannel.getMessage(), containsString("channel must not be blank"));
        assertThrows(RuntimeException.class,
                     () -> FailurePolicy.builder()
                             .onExhausted(FailureDisposition.DROP)
                             .deadLetter(DeadLetterConfig.builder().channel("orders-dlq").build())
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
                .addHeader("binary", MessageHeaderValue.BinaryValue.create(new byte[] {1, 2}))
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
                              MessageHeader.create("binary", MessageHeaderValue.BinaryValue.create(new byte[] {1, 2})),
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

        NullPointerException nullMessage = assertThrows(
                NullPointerException.class,
                () -> DeadLetterMessage.create(null, "orders-in", 1, failure));
        assertThat(nullMessage.getMessage(), is("originalMessage"));

        NullPointerException nullChannel = assertThrows(
                NullPointerException.class,
                () -> DeadLetterMessage.create(message, null, 1, failure));
        assertThat(nullChannel.getMessage(), is("sourceChannel"));

        assertThrows(IllegalArgumentException.class,
                     () -> DeadLetterMessage.create(message, " ", 1, failure));
        assertThrows(IllegalArgumentException.class,
                     () -> DeadLetterMessage.create(message, "orders-in", 0, failure));

        NullPointerException nullFailure = assertThrows(
                NullPointerException.class,
                () -> DeadLetterMessage.create(message, "orders-in", 1, null));
        assertThat(nullFailure.getMessage(), is("failure"));
    }

    @Test
    void testDeadLetterDefaultAccessorsRequireTextMetadata() {
        DeadLetterMessage<String> missing = new MetadataDeadLetterMessage(MessageMetadata.empty());

        IllegalStateException missingFailureType = assertThrows(IllegalStateException.class, missing::failureType);
        assertThat(missingFailureType.getMessage(), containsString(DeadLetterMessage.FAILURE_TYPE_METADATA));

        MessageMetadata wrongKindMetadata = MessageMetadata.builder()
                .set(DeadLetterMessage.FAILURE_TYPE_METADATA, "failure.Type")
                .set(DeadLetterMessage.FAILURE_MESSAGE_METADATA, MessageHeaderValue.BinaryValue.create(new byte[] {1}))
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
