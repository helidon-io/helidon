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

import java.util.List;

import io.helidon.common.GenericType;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessagingChannelHandleTest {
    @Test
    void testCreateFromClass() {
        MessagingChannel<String> channel = MessagingChannel.create("orders", String.class);

        assertThat(channel.name(), is("orders"));
        assertThat(channel.payloadType(), is(GenericType.create(String.class)));
    }

    @Test
    void testCreatePreservesParameterizedPayloadType() {
        GenericType<List<String>> payloadType = new GenericType<>() { };

        MessagingChannel<List<String>> channel = MessagingChannel.create("orders", payloadType);

        assertThat(channel.name(), is("orders"));
        assertThat(channel.payloadType(), sameInstance(payloadType));
    }

    @Test
    void testBuilderCreatesIndependentImmutableHandles() {
        MessagingChannel.Builder<String> builder = MessagingChannel.<String>builder()
                .name("orders")
                .payloadType(GenericType.create(String.class));
        MessagingChannel<String> original = builder.build();
        MessagingChannel<String> equivalent = MessagingChannel.create("orders", String.class);
        MessagingChannel<String> renamed = builder.name("renamed").build();

        assertThat(original.name(), is("orders"));
        assertThat(renamed.name(), is("renamed"));
        assertThat(equivalent, is(original));
        assertThat(equivalent, not(sameInstance(original)));
    }

    @Test
    void testBlankNamesAreRejected() {
        for (String name : List.of("", " ", "\t")) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                                                              () -> MessagingChannel.create(name, String.class));
            assertThat(failure.getMessage(), is("Messaging channel name must not be blank"));
        }
    }

    @Test
    void testPrimitivePayloadTypesAreRejected() {
        IllegalArgumentException fromClass = assertThrows(IllegalArgumentException.class,
                                                            () -> MessagingChannel.create("orders", int.class));
        IllegalArgumentException fromGenericType = assertThrows(IllegalArgumentException.class,
                () -> MessagingChannel.create("orders", GenericType.create(int.class)));

        assertThat(fromClass.getMessage(), is("Messaging channel payload type must not be primitive: int"));
        assertThat(fromGenericType.getMessage(), is("Messaging channel payload type must not be primitive: int"));
    }

    @Test
    void testNullConstructionArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> MessagingChannel.create(null, String.class));
        assertThrows(NullPointerException.class, () -> MessagingChannel.create("orders", (Class<String>) null));
        assertThrows(NullPointerException.class, () -> MessagingChannel.create("orders", (GenericType<String>) null));
    }
}
