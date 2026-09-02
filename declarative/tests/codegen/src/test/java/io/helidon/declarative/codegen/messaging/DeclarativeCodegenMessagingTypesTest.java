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

package io.helidon.declarative.codegen.messaging;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.messaging.BatchDeliveryException;
import io.helidon.messaging.BatchItemOutcome;
import io.helidon.messaging.ConsumerRegistration;
import io.helidon.messaging.Emitter;
import io.helidon.messaging.EmitterRegistration;
import io.helidon.messaging.FailureDisposition;
import io.helidon.messaging.FailurePolicy;
import io.helidon.messaging.HeaderValue;
import io.helidon.messaging.Message;
import io.helidon.messaging.MessageBatch;
import io.helidon.messaging.Messaging;
import io.helidon.messaging.MessagingEntryPoint;
import io.helidon.messaging.MessagingException;
import io.helidon.messaging.MessagingRuntime;
import io.helidon.messaging.ProcessorRegistration;

import org.hamcrest.collection.IsEmptyCollection;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class DeclarativeCodegenMessagingTypesTest {
    @Test
    void testTypes() {
        Field[] declaredFields = MessagingTypes.class.getDeclaredFields();

        Set<String> toCheck = new HashSet<>();
        Set<String> checked = new HashSet<>();
        Map<String, Field> fields = new HashMap<>();

        for (Field declaredField : declaredFields) {
            String name = declaredField.getName();
            if (!declaredField.getType().equals(TypeName.class)) {
                continue;
            }
            assertThat(name + " must be static", Modifier.isStatic(declaredField.getModifiers()), is(true));
            assertThat(name + " must be final", Modifier.isFinal(declaredField.getModifiers()), is(true));
            toCheck.add(name);
            fields.put(name, declaredField);
        }

        checkField(toCheck, checked, fields, "ARRAY_LIST", ArrayList.class);
        checkField(toCheck, checked, fields, "CONSUMER_REGISTRATION", ConsumerRegistration.class);
        checkField(toCheck, checked, fields, "BATCH_DELIVERY_EXCEPTION", BatchDeliveryException.class);
        checkField(toCheck, checked, fields, "BATCH_ITEM_OUTCOME", BatchItemOutcome.class);
        checkField(toCheck, checked, fields, "EMITTER", Emitter.class);
        checkField(toCheck, checked, fields, "EMITTER_REGISTRATION", EmitterRegistration.class);
        checkField(toCheck, checked, fields, "ENTITY", Messaging.Entity.class);
        checkField(toCheck, checked, fields, "FAILURE_DISPOSITION", FailureDisposition.class);
        checkField(toCheck, checked, fields, "FAILURE_POLICY", FailurePolicy.class);
        checkField(toCheck, checked, fields, "HEADER_PARAM", Messaging.HeaderParam.class);
        checkField(toCheck, checked, fields, "HEADER_VALUE", HeaderValue.class);
        checkField(toCheck, checked, fields, "MESSAGE", Message.class);
        checkField(toCheck, checked, fields, "MESSAGE_BATCH", MessageBatch.class);
        checkField(toCheck,
                   checked,
                   fields,
                   "MESSAGING_ENTRY_POINT_BATCH_HANDLER",
                   MessagingEntryPoint.BatchHandler.class);
        checkField(toCheck, checked, fields, "MESSAGING_ENTRY_POINT_HANDLER", MessagingEntryPoint.Handler.class);
        checkField(toCheck, checked, fields, "MESSAGING_ENTRY_POINTS", MessagingEntryPoint.EntryPoints.class);
        checkField(toCheck, checked, fields, "MESSAGING_EXCEPTION", MessagingException.class);
        checkField(toCheck, checked, fields, "MESSAGING_RUNTIME", MessagingRuntime.class);
        checkField(toCheck, checked, fields, "OBJECTS", Objects.class);
        checkField(toCheck, checked, fields, "ON_FAILURE", Messaging.OnFailure.class);
        checkField(toCheck, checked, fields, "RECEIVE_FROM", Messaging.ReceiveFrom.class);
        checkField(toCheck, checked, fields, "SEND_TO", Messaging.SendTo.class);
        checkField(toCheck, checked, fields, "PROCESSOR_REGISTRATION", ProcessorRegistration.class);

        assertThat("If the collection is not empty, please add an appropriate checkField line to this test",
                   toCheck,
                   IsEmptyCollection.empty());
    }

    private void checkField(Set<String> namesToCheck,
                            Set<String> checkedNames,
                            Map<String, Field> namesToFields,
                            String name,
                            Class<?> expectedType) {
        Field field = namesToFields.get(name);
        assertThat("Field " + name + " does not exist in the class", field, notNullValue());
        try {
            namesToCheck.remove(name);
            if (checkedNames.add(name)) {
                TypeName value = (TypeName) field.get(null);
                assertThat("Field " + name, value, is(TypeName.create(expectedType)));
            } else {
                fail("Field " + name + " is checked more than once");
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
