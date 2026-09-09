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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageHeaderValueTest {
    @Test
    void createsClosedScalarValues() {
        Instant timestamp = Instant.parse("2026-08-26T10:15:30Z");
        UUID uuid = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");

        MessageHeaderValue.TextValue text = MessageHeaderValue.TextValue.create("text");
        MessageHeaderValue.BooleanValue booleanValue = MessageHeaderValue.BooleanValue.create(true);
        MessageHeaderValue.IntegerValue integer = MessageHeaderValue.IntegerValue.create(42);
        MessageHeaderValue.DecimalValue decimal = MessageHeaderValue.DecimalValue.create(new BigDecimal("12.30"));
        MessageHeaderValue.Float32Value float32 = MessageHeaderValue.Float32Value.create(1.5F);
        MessageHeaderValue.Float64Value float64 = MessageHeaderValue.Float64Value.create(2.5D);
        MessageHeaderValue.TimestampValue timestampValue = MessageHeaderValue.TimestampValue.create(timestamp);
        MessageHeaderValue.UuidValue uuidValue = MessageHeaderValue.UuidValue.create(uuid);

        assertThat(MessageHeaderValue.NullValue.create(), sameInstance(MessageHeaderValue.NullValue.create()));
        assertThat(text.value(), is("text"));
        assertThat(text, is(MessageHeaderValue.TextValue.create("text")));
        assertThat(booleanValue.value(), is(true));
        assertThat(booleanValue, is(MessageHeaderValue.BooleanValue.create(true)));
        assertThat(integer.value(), is(BigInteger.valueOf(42)));
        assertThat(integer, is(MessageHeaderValue.IntegerValue.create(BigInteger.valueOf(42))));
        assertThat(MessageHeaderValue.IntegerValue.create(BigInteger.TEN),
                   is(MessageHeaderValue.IntegerValue.create(BigInteger.TEN)));
        assertThat(decimal.value(), is(new BigDecimal("12.30")));
        assertThat(decimal, is(MessageHeaderValue.DecimalValue.create(new BigDecimal("12.30"))));
        assertThat(decimal, not(MessageHeaderValue.DecimalValue.create(new BigDecimal("12.3"))));
        assertThat(float32.value(), is(1.5F));
        assertThat(float32, is(MessageHeaderValue.Float32Value.create(1.5F)));
        assertThat(float64.value(), is(2.5D));
        assertThat(float64, is(MessageHeaderValue.Float64Value.create(2.5D)));
        assertThat(timestampValue.value(), is(timestamp));
        assertThat(timestampValue, is(MessageHeaderValue.TimestampValue.create(timestamp)));
        assertThat(uuidValue.value(), is(uuid));
        assertThat(uuidValue, is(MessageHeaderValue.UuidValue.create(uuid)));

        assertEqualHash(text, MessageHeaderValue.TextValue.create("text"));
        assertEqualHash(booleanValue, MessageHeaderValue.BooleanValue.create(true));
        assertEqualHash(integer, MessageHeaderValue.IntegerValue.create(42));
        assertEqualHash(decimal, MessageHeaderValue.DecimalValue.create(new BigDecimal("12.30")));
        assertEqualHash(float32, MessageHeaderValue.Float32Value.create(1.5F));
        assertEqualHash(float64, MessageHeaderValue.Float64Value.create(2.5D));
        assertEqualHash(timestampValue, MessageHeaderValue.TimestampValue.create(timestamp));
        assertEqualHash(uuidValue, MessageHeaderValue.UuidValue.create(uuid));
    }

    @Test
    void floatingPointValuesRetainRecordSemantics() {
        assertThat(MessageHeaderValue.Float32Value.create(Float.NaN),
                   is(MessageHeaderValue.Float32Value.create(Float.intBitsToFloat(0x7f800001))));
        assertThat(MessageHeaderValue.Float32Value.create(0.0F),
                   not(MessageHeaderValue.Float32Value.create(-0.0F)));
        assertThat(MessageHeaderValue.Float64Value.create(Double.NaN),
                   is(MessageHeaderValue.Float64Value.create(Double.longBitsToDouble(0x7ff0000000000001L))));
        assertThat(MessageHeaderValue.Float64Value.create(0.0D),
                   not(MessageHeaderValue.Float64Value.create(-0.0D)));
    }

    @Test
    void scalarValuesRetainRecordStyleDiagnostics() {
        assertThat(MessageHeaderValue.NullValue.create().toString(), is("INSTANCE"));
        assertThat(MessageHeaderValue.TextValue.create("text").toString(), is("TextValue[value=text]"));
        assertThat(MessageHeaderValue.BooleanValue.create(true).toString(), is("BooleanValue[value=true]"));
        assertThat(MessageHeaderValue.IntegerValue.create(42).toString(), is("IntegerValue[value=42]"));
        assertThat(MessageHeaderValue.DecimalValue.create(new BigDecimal("12.30")).toString(),
                   is("DecimalValue[value=12.30]"));
        assertThat(MessageHeaderValue.Float32Value.create(1.5F).toString(), is("Float32Value[value=1.5]"));
        assertThat(MessageHeaderValue.Float64Value.create(2.5D).toString(), is("Float64Value[value=2.5]"));
    }

    @Test
    void binaryValuesAreImmutableContentValues() {
        byte[] source = {1, 2, 3};
        MessageHeaderValue.BinaryValue value = MessageHeaderValue.BinaryValue.create(source);
        source[0] = 9;

        assertThat(value.value(), is(new byte[] {1, 2, 3}));
        assertThat(value.size(), is(3));
        assertThat(value, is(MessageHeaderValue.BinaryValue.create(new byte[] {1, 2, 3})));
        assertThat(value.hashCode(), is(MessageHeaderValue.BinaryValue.create(new byte[] {1, 2, 3}).hashCode()));
        assertThat(value, not(MessageHeaderValue.BinaryValue.create(new byte[] {1, 2, 4})));

        byte[] exposed = value.value();
        exposed[1] = 9;
        assertThat(value.value(), is(new byte[] {1, 2, 3}));
    }

    @Test
    void nativeValuesAreImmutableContentValues() {
        byte[] source = {4, 5, 6};
        MessageHeaderValue.NativeValue value = MessageHeaderValue.NativeValue.create("amqp:field-table", source);
        source[0] = 9;

        assertThat(value.typeId(), is("amqp:field-table"));
        assertThat(value.value(), is(new byte[] {4, 5, 6}));
        assertThat(value.size(), is(3));
        assertThat(value, is(MessageHeaderValue.NativeValue.create("amqp:field-table", new byte[] {4, 5, 6})));
        assertThat(value.hashCode(),
                   is(MessageHeaderValue.NativeValue.create("amqp:field-table", new byte[] {4, 5, 6}).hashCode()));
        assertThat(value, not(MessageHeaderValue.NativeValue.create("other:type", new byte[] {4, 5, 6})));
        assertThat(value, not(MessageHeaderValue.NativeValue.create("amqp:field-table", new byte[] {4, 5, 7})));

        byte[] exposed = value.value();
        exposed[1] = 9;
        assertThat(value.value(), is(new byte[] {4, 5, 6}));
    }

    @Test
    void rejectsInvalidValues() {
        assertThrows(NullPointerException.class, () -> MessageHeaderValue.TextValue.create(null));
        assertThrows(NullPointerException.class, () -> MessageHeaderValue.BinaryValue.create(null));
        assertThrows(NullPointerException.class, () -> MessageHeaderValue.IntegerValue.create((BigInteger) null));
        assertThrows(NullPointerException.class, () -> MessageHeaderValue.DecimalValue.create(null));
        assertThrows(NullPointerException.class, () -> MessageHeaderValue.TimestampValue.create(null));
        assertThrows(NullPointerException.class, () -> MessageHeaderValue.UuidValue.create(null));
        assertThrows(NullPointerException.class, () -> MessageHeaderValue.NativeValue.create(null, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> MessageHeaderValue.NativeValue.create(" ", new byte[0]));
        assertThrows(NullPointerException.class, () -> MessageHeaderValue.NativeValue.create("test:value", null));
    }

    private static void assertEqualHash(MessageHeaderValue first, MessageHeaderValue second) {
        assertThat(first, is(second));
        assertThat(first.hashCode(), is(second.hashCode()));
    }
}
