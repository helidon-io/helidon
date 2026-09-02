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
import java.util.Set;

import io.helidon.messaging.BatchDeliveryException;
import io.helidon.messaging.BatchItemOutcome;
import io.helidon.messaging.HeaderValue;
import io.helidon.messaging.Message;
import io.helidon.messaging.MessageBatch;
import io.helidon.messaging.MessageHeader;
import io.helidon.messaging.MessageMetadata;
import io.helidon.messaging.MessagingEntryPoint;
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
    void localMetadataIsUsableOutsideItsPackage() {
        MessageMetadata metadata = MessageMetadata.builder()
                .set("application.local", HeaderValue.text("value"))
                .build();
        Message<String> message = Message.builder("payload").localMetadata(metadata).build();

        assertThat(message.localMetadata(), is(metadata));
        assertThat(message.localMetadata().text("application.local").orElseThrow(), is("value"));
        assertThat(message.headers().isEmpty(), is(true));
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
}
