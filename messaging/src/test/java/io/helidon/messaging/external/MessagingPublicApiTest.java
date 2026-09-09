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

package io.helidon.messaging.external;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.helidon.faulttolerance.RetryConfig;
import io.helidon.messaging.BatchDeliveryException;
import io.helidon.messaging.BatchItemOutcome;
import io.helidon.messaging.ConnectorDelivery;
import io.helidon.messaging.DeadLetterConfig;
import io.helidon.messaging.FailureDisposition;
import io.helidon.messaging.FailurePolicy;
import io.helidon.messaging.MessageHeaderValue;
import io.helidon.messaging.Message;
import io.helidon.messaging.MessageBatch;
import io.helidon.messaging.MessageHeader;
import io.helidon.messaging.MessageHeaders;
import io.helidon.messaging.MessageMetadata;
import io.helidon.messaging.MessagingEntryPoint;
import io.helidon.messaging.MessagingException;
import io.helidon.messaging.MessagingRejectedException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessagingPublicApiTest {
    @Test
    void messagingExceptionsRejectNullConstructorArguments() {
        MessagingRejectedException.Reason reason = MessagingRejectedException.Reason.SATURATED;

        assertThrows(NullPointerException.class, () -> new MessagingException(null));
        assertThrows(NullPointerException.class, () -> new MessagingException("failed", null));
        assertThrows(NullPointerException.class, () -> new MessagingRejectedException(null, reason));
        assertThrows(NullPointerException.class, () -> new MessagingRejectedException("orders", null));
        NullPointerException nullMessage = assertThrows(
                NullPointerException.class,
                () -> new MessagingRejectedException("orders", reason, null));
        assertThat(nullMessage.getMessage(), is("message"));

        NullPointerException nullMessageWithCause = assertThrows(
                NullPointerException.class,
                () -> new MessagingRejectedException("orders", reason, null, new IllegalStateException()));
        assertThat(nullMessageWithCause.getMessage(), is("message"));

        NullPointerException nullCause = assertThrows(
                NullPointerException.class,
                () -> new MessagingRejectedException("orders", reason, "rejected", null));
        assertThat(nullCause.getMessage(), is("cause"));
    }

    @Test
    void publicCallbacksDeclareNoCheckedExceptions() throws NoSuchMethodException {
        assertNoDeclaredExceptions(ConnectorDelivery.class.getMethod("await"));
        assertNoDeclaredExceptions(ConnectorDelivery.class.getMethod("await", Duration.class));
        assertNoDeclaredExceptions(MessagingEntryPoint.Handler.class.getMethod("handle", Object.class, Message.class));
        assertNoDeclaredExceptions(
                MessagingEntryPoint.BatchHandler.class.getMethod("handle", Object.class, MessageBatch.class));
    }

    @Test
    void headerModelsAreClosedFinalFactoryOnlyTypes() {
        List<Class<?>> valueTypes = List.of(MessageHeaderValue.NullValue.class,
                                            MessageHeaderValue.TextValue.class,
                                            MessageHeaderValue.BinaryValue.class,
                                            MessageHeaderValue.BooleanValue.class,
                                            MessageHeaderValue.IntegerValue.class,
                                            MessageHeaderValue.DecimalValue.class,
                                            MessageHeaderValue.Float32Value.class,
                                            MessageHeaderValue.Float64Value.class,
                                            MessageHeaderValue.TimestampValue.class,
                                            MessageHeaderValue.UuidValue.class,
                                            MessageHeaderValue.NativeValue.class);
        Set<Class<?>> permittedTypes = Set.copyOf(Arrays.asList(MessageHeaderValue.class.getPermittedSubclasses()));

        assertThat(MessageHeaderValue.class.isSealed(), is(true));
        assertThat(Arrays.stream(MessageHeaderValue.class.getDeclaredMethods())
                           .noneMatch(method -> Modifier.isStatic(method.getModifiers())),
                   is(true));
        assertThat(permittedTypes, is(Set.copyOf(valueTypes)));
        for (Class<?> type : valueTypes) {
            assertThat(type.getName(), Modifier.isFinal(type.getModifiers()), is(true));
            assertThat(type.getName(), type.isRecord(), is(false));
            assertThat(type.getName(), type.isEnum(), is(false));
            assertThat(type.getName(), type.getConstructors().length, is(0));
        }
        assertThat(Modifier.isFinal(MessageHeader.class.getModifiers()), is(true));
        assertThat(MessageHeader.class.isRecord(), is(false));
        assertThat(MessageHeader.class.getConstructors().length, is(0));

        MessageHeader header = MessageHeader.create("trace", MessageHeaderValue.TextValue.create("value"));
        assertThat(header.name(), is("trace"));
        assertThat(header.value(), is(MessageHeaderValue.TextValue.create("value")));

        List<MessageHeaderValue> createdValues = List.of(
                MessageHeaderValue.NullValue.create(),
                MessageHeaderValue.TextValue.create("text"),
                MessageHeaderValue.BinaryValue.create(new byte[] {1}),
                MessageHeaderValue.BooleanValue.create(true),
                MessageHeaderValue.IntegerValue.create(42),
                MessageHeaderValue.IntegerValue.create(BigInteger.TEN),
                MessageHeaderValue.DecimalValue.create(BigDecimal.TEN),
                MessageHeaderValue.Float32Value.create(1.5F),
                MessageHeaderValue.Float64Value.create(2.5D),
                MessageHeaderValue.TimestampValue.create(Instant.EPOCH),
                MessageHeaderValue.UuidValue.create(new UUID(0, 0)),
                MessageHeaderValue.NativeValue.create("test:value", new byte[] {2}));
        assertThat(createdValues.size(), is(12));
    }

    @Test
    void headerCollectionsAreUsableOutsideTheirPackage() {
        MessageHeaders headers = MessageHeaders.builder()
                .add("trace", "value")
                .build();
        MessageMetadata metadata = MessageMetadata.builder()
                .set("application.local", MessageHeaderValue.TextValue.create("value"))
                .build();
        Message<String> message = Message.builder("payload")
                .headers(headers)
                .localMetadata(metadata)
                .build();

        assertThat(message.headers(), is(headers));
        assertThat(message.headers().last("trace").orElseThrow(), is(MessageHeaderValue.TextValue.create("value")));
        assertThat(message.localMetadata(), is(metadata));
        assertThat(message.localMetadata().text("application.local").orElseThrow(), is("value"));
    }

    @Test
    void messageRejectsNullLookupNamesBeforeReadingHeaders() {
        Message<String> headerValueLookup = new Message<>() {
            @Override
            public String entity() {
                return "payload";
            }

            @Override
            public MessageHeaders headers() {
                throw new AssertionError("Null lookup must be rejected before reading headers");
            }
        };
        Message<String> textHeaderLookup = new Message<>() {
            @Override
            public String entity() {
                return "payload";
            }

            @Override
            public MessageHeaders headers() {
                return MessageHeaders.empty();
            }

            @Override
            public Optional<MessageHeaderValue> headerValue(String name) {
                throw new AssertionError("Null text lookup must be rejected before reading a header value");
            }
        };

        assertThrows(NullPointerException.class, () -> headerValueLookup.headerValue(null));
        assertThrows(NullPointerException.class, () -> textHeaderLookup.header(null));
    }

    @Test
    void messageBuilderRejectsEveryNullArgumentWithoutMutation() {
        assertThrows(NullPointerException.class, () -> Message.builder(null));
        assertThrows(NullPointerException.class, () -> Message.create(null));

        Message.Builder<String> builder = Message.builder("payload")
                .header("trace", "original")
                .addHeader("retained", MessageHeaderValue.IntegerValue.create(42))
                .localMetadata("diagnostic", "original")
                .localMetadata("retained", MessageHeaderValue.BooleanValue.create(true));
        Message<String> expected = builder.build();

        assertThrows(NullPointerException.class, () -> builder.header(null, "replacement"));
        assertThrows(NullPointerException.class, () -> builder.header("trace", (String) null));
        assertThrows(NullPointerException.class,
                     () -> builder.header(null, MessageHeaderValue.TextValue.create("replacement")));
        assertThrows(NullPointerException.class, () -> builder.header("trace", (MessageHeaderValue) null));
        assertThrows(NullPointerException.class, () -> builder.addHeader(null, "appended"));
        assertThrows(NullPointerException.class, () -> builder.addHeader("new", (String) null));
        assertThrows(NullPointerException.class,
                     () -> builder.addHeader(null, MessageHeaderValue.TextValue.create("appended")));
        assertThrows(NullPointerException.class, () -> builder.addHeader("new", (MessageHeaderValue) null));
        assertThrows(NullPointerException.class, () -> builder.addHeader((MessageHeader) null));
        assertThrows(NullPointerException.class, () -> builder.headers(null));
        assertMessageBuilderState(builder, expected);

        assertThrows(NullPointerException.class, () -> builder.localMetadata(null, "replacement"));
        assertThrows(NullPointerException.class, () -> builder.localMetadata("diagnostic", (String) null));
        assertThrows(NullPointerException.class,
                     () -> builder.localMetadata(null, MessageHeaderValue.TextValue.create("replacement")));
        assertThrows(NullPointerException.class,
                     () -> builder.localMetadata("diagnostic", (MessageHeaderValue) null));
        assertThrows(NullPointerException.class, () -> builder.localMetadata((MessageMetadata) null));
        assertMessageBuilderState(builder, expected);
    }

    @Test
    void nestedFailurePolicyConfigurationIsUsableOutsideItsPackage() {
        RetryConfig retry = RetryConfig.builder()
                .delay(Duration.ofMillis(250))
                .calls(3)
                .buildPrototype();
        DeadLetterConfig deadLetter = DeadLetterConfig.builder()
                .channel("orders-dlq")
                .build();
        FailurePolicy policy = FailurePolicy.builder()
                .retry(retry)
                .onExhausted(FailureDisposition.DEAD_LETTER)
                .deadLetter(deadLetter)
                .build();

        assertThat(policy.retry().delay(), is(Duration.ofMillis(250)));
        assertThat(policy.retry().calls(), is(3));
        assertThat(policy.onExhausted(), is(FailureDisposition.DEAD_LETTER));
        assertThat(policy.deadLetter().orElseThrow().channel(), is("orders-dlq"));
    }

    @Test
    void batchDeliveryExceptionSupportsDirectPublicConstructionAndRejectsNulls() {
        MessageBatch<String> batch = MessageBatch.create(Message.create("payload"));
        RuntimeException cause = new IllegalStateException("send failed");
        List<BatchItemOutcome> outcomes = List.of(BatchItemOutcome.notAttempted(0));
        BatchDeliveryException failure = new BatchDeliveryException("Acme send failed",
                                                                     cause,
                                                                     batch,
                                                                     outcomes);

        assertThat(failure.getMessage(), is("Acme send failed"));
        assertThat(failure.getCause(), sameInstance(cause));
        assertThat(failure.batch(), sameInstance(batch));
        assertThat(failure.outcomes(), is(outcomes));
        assertThrows(NullPointerException.class,
                     () -> new BatchDeliveryException(null, cause, batch, outcomes));
        assertThrows(NullPointerException.class,
                     () -> new BatchDeliveryException("Acme send failed", null, batch, outcomes));
        assertThrows(NullPointerException.class,
                     () -> new BatchDeliveryException("Acme send failed", cause, null, outcomes));
        assertThrows(NullPointerException.class,
                     () -> new BatchDeliveryException("Acme send failed", cause, batch, null));
    }

    private static void assertNoDeclaredExceptions(Method method) {
        assertThat(method.getExceptionTypes().length, is(0));
    }

    private static void assertMessageBuilderState(Message.Builder<String> builder, Message<String> expected) {
        Message<String> actual = builder.build();
        assertThat(actual.headers(), is(expected.headers()));
        assertThat(actual.localMetadata(), is(expected.localMetadata()));
    }
}
