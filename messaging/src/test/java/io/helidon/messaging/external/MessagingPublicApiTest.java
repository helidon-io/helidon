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
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.messaging.BatchDeliveryException;
import io.helidon.messaging.BatchItemOutcome;
import io.helidon.messaging.DeadLetterConfig;
import io.helidon.messaging.FailureDisposition;
import io.helidon.messaging.FailurePolicy;
import io.helidon.messaging.HeaderValue;
import io.helidon.messaging.Message;
import io.helidon.messaging.MessageBatch;
import io.helidon.messaging.MessageHeader;
import io.helidon.messaging.MessageHeaders;
import io.helidon.messaging.MessageMetadata;
import io.helidon.messaging.MessagingEntryPoint;
import io.helidon.messaging.RetryConfig;
import io.helidon.messaging.spi.ConnectorDelivery;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessagingPublicApiTest {
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
        List<Class<?>> valueTypes = List.of(HeaderValue.NullValue.class,
                                            HeaderValue.TextValue.class,
                                            HeaderValue.BinaryValue.class,
                                            HeaderValue.BooleanValue.class,
                                            HeaderValue.IntegerValue.class,
                                            HeaderValue.DecimalValue.class,
                                            HeaderValue.Float32Value.class,
                                            HeaderValue.Float64Value.class,
                                            HeaderValue.TimestampValue.class,
                                            HeaderValue.UuidValue.class,
                                            HeaderValue.NativeValue.class);
        Set<Class<?>> permittedTypes = Set.copyOf(Arrays.asList(HeaderValue.class.getPermittedSubclasses()));

        assertThat(HeaderValue.class.isSealed(), is(true));
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

        MessageHeader header = MessageHeader.create("trace", HeaderValue.text("value"));
        assertThat(header.name(), is("trace"));
        assertThat(header.value(), is(HeaderValue.text("value")));
    }

    @Test
    void headerCollectionsAreUsableOutsideTheirPackage() {
        MessageHeaders headers = MessageHeaders.builder()
                .add("trace", "value")
                .build();
        MessageMetadata metadata = MessageMetadata.builder()
                .set("application.local", HeaderValue.text("value"))
                .build();
        Message<String> message = Message.builder("payload")
                .headers(headers)
                .localMetadata(metadata)
                .build();

        assertThat(message.headers(), is(headers));
        assertThat(message.headers().last("trace").orElseThrow(), is(HeaderValue.text("value")));
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
            public Optional<HeaderValue> headerValue(String name) {
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
                .addHeader("retained", HeaderValue.integer(42))
                .localMetadata("diagnostic", "original")
                .localMetadata("retained", HeaderValue.booleanValue(true));
        Message<String> expected = builder.build();

        assertThrows(NullPointerException.class, () -> builder.header(null, "replacement"));
        assertThrows(NullPointerException.class, () -> builder.header("trace", (String) null));
        assertThrows(NullPointerException.class,
                     () -> builder.header(null, HeaderValue.text("replacement")));
        assertThrows(NullPointerException.class, () -> builder.header("trace", (HeaderValue) null));
        assertThrows(NullPointerException.class, () -> builder.addHeader(null, "appended"));
        assertThrows(NullPointerException.class, () -> builder.addHeader("new", (String) null));
        assertThrows(NullPointerException.class,
                     () -> builder.addHeader(null, HeaderValue.text("appended")));
        assertThrows(NullPointerException.class, () -> builder.addHeader("new", (HeaderValue) null));
        assertThrows(NullPointerException.class, () -> builder.addHeader((MessageHeader) null));
        assertThrows(NullPointerException.class, () -> builder.headers(null));
        assertMessageBuilderState(builder, expected);

        assertThrows(NullPointerException.class, () -> builder.localMetadata(null, "replacement"));
        assertThrows(NullPointerException.class, () -> builder.localMetadata("diagnostic", (String) null));
        assertThrows(NullPointerException.class,
                     () -> builder.localMetadata(null, HeaderValue.text("replacement")));
        assertThrows(NullPointerException.class,
                     () -> builder.localMetadata("diagnostic", (HeaderValue) null));
        assertThrows(NullPointerException.class, () -> builder.localMetadata((MessageMetadata) null));
        assertMessageBuilderState(builder, expected);
    }

    @Test
    void nestedFailurePolicyConfigurationIsUsableOutsideItsPackage() {
        RetryConfig retry = RetryConfig.builder()
                .delay(Duration.ofMillis(250))
                .maxAttempts(3)
                .build();
        DeadLetterConfig deadLetter = DeadLetterConfig.builder()
                .channel("orders-dlq")
                .build();
        FailurePolicy policy = FailurePolicy.builder()
                .retry(retry)
                .onExhausted(FailureDisposition.DEAD_LETTER)
                .deadLetter(deadLetter)
                .build();

        assertThat(policy.retry().delay(), is(Duration.ofMillis(250)));
        assertThat(policy.retry().maxAttempts(), is(3));
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
