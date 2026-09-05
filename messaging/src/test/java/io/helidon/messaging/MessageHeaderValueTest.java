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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageHeaderValueTest {
    @Test
    void createsClosedScalarValues() {
        Instant timestamp = Instant.parse("2026-08-26T10:15:30Z");
        UUID uuid = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");

        MessageHeaderValue.TextValue text = MessageHeaderValue.text("text");
        MessageHeaderValue.BooleanValue booleanValue = MessageHeaderValue.booleanValue(true);
        MessageHeaderValue.IntegerValue integer = MessageHeaderValue.integer(42);
        MessageHeaderValue.DecimalValue decimal = MessageHeaderValue.decimal(new BigDecimal("12.30"));
        MessageHeaderValue.Float32Value float32 = MessageHeaderValue.floatingPoint(1.5F);
        MessageHeaderValue.Float64Value float64 = MessageHeaderValue.floatingPoint(2.5D);
        MessageHeaderValue.TimestampValue timestampValue = MessageHeaderValue.timestamp(timestamp);
        MessageHeaderValue.UuidValue uuidValue = MessageHeaderValue.uuid(uuid);

        assertThat(MessageHeaderValue.nullValue(), sameInstance(MessageHeaderValue.nullValue()));
        assertThat(text.value(), is("text"));
        assertThat(text, is(MessageHeaderValue.text("text")));
        assertThat(booleanValue.value(), is(true));
        assertThat(booleanValue, is(MessageHeaderValue.booleanValue(true)));
        assertThat(integer.value(), is(BigInteger.valueOf(42)));
        assertThat(integer, is(MessageHeaderValue.integer(BigInteger.valueOf(42))));
        assertThat(MessageHeaderValue.integer(BigInteger.TEN), is(MessageHeaderValue.integer(BigInteger.TEN)));
        assertThat(decimal.value(), is(new BigDecimal("12.30")));
        assertThat(decimal, is(MessageHeaderValue.decimal(new BigDecimal("12.30"))));
        assertThat(decimal, not(MessageHeaderValue.decimal(new BigDecimal("12.3"))));
        assertThat(float32.value(), is(1.5F));
        assertThat(float32, is(MessageHeaderValue.floatingPoint(1.5F)));
        assertThat(float64.value(), is(2.5D));
        assertThat(float64, is(MessageHeaderValue.floatingPoint(2.5D)));
        assertThat(timestampValue.value(), is(timestamp));
        assertThat(timestampValue, is(MessageHeaderValue.timestamp(timestamp)));
        assertThat(uuidValue.value(), is(uuid));
        assertThat(uuidValue, is(MessageHeaderValue.uuid(uuid)));

        assertEqualHash(text, MessageHeaderValue.text("text"));
        assertEqualHash(booleanValue, MessageHeaderValue.booleanValue(true));
        assertEqualHash(integer, MessageHeaderValue.integer(42));
        assertEqualHash(decimal, MessageHeaderValue.decimal(new BigDecimal("12.30")));
        assertEqualHash(float32, MessageHeaderValue.floatingPoint(1.5F));
        assertEqualHash(float64, MessageHeaderValue.floatingPoint(2.5D));
        assertEqualHash(timestampValue, MessageHeaderValue.timestamp(timestamp));
        assertEqualHash(uuidValue, MessageHeaderValue.uuid(uuid));
    }

    @Test
    void floatingPointValuesRetainRecordSemantics() {
        assertThat(MessageHeaderValue.floatingPoint(Float.NaN),
                   is(MessageHeaderValue.floatingPoint(Float.intBitsToFloat(0x7f800001))));
        assertThat(MessageHeaderValue.floatingPoint(0.0F), not(MessageHeaderValue.floatingPoint(-0.0F)));
        assertThat(MessageHeaderValue.floatingPoint(Double.NaN),
                   is(MessageHeaderValue.floatingPoint(Double.longBitsToDouble(0x7ff0000000000001L))));
        assertThat(MessageHeaderValue.floatingPoint(0.0D), not(MessageHeaderValue.floatingPoint(-0.0D)));
    }

    @Test
    void scalarValuesRetainRecordStyleDiagnostics() {
        assertThat(MessageHeaderValue.nullValue().toString(), is("INSTANCE"));
        assertThat(MessageHeaderValue.text("text").toString(), is("TextValue[value=text]"));
        assertThat(MessageHeaderValue.booleanValue(true).toString(), is("BooleanValue[value=true]"));
        assertThat(MessageHeaderValue.integer(42).toString(), is("IntegerValue[value=42]"));
        assertThat(MessageHeaderValue.decimal(new BigDecimal("12.30")).toString(), is("DecimalValue[value=12.30]"));
        assertThat(MessageHeaderValue.floatingPoint(1.5F).toString(), is("Float32Value[value=1.5]"));
        assertThat(MessageHeaderValue.floatingPoint(2.5D).toString(), is("Float64Value[value=2.5]"));
    }

    @Test
    void binaryValuesAreImmutableContentValues() {
        byte[] source = {1, 2, 3};
        MessageHeaderValue.BinaryValue value = MessageHeaderValue.binary(source);
        source[0] = 9;

        assertArrayEquals(new byte[] {1, 2, 3}, value.value());
        assertThat(value.size(), is(3));
        assertThat(value, is(MessageHeaderValue.binary(new byte[] {1, 2, 3})));
        assertThat(value.hashCode(), is(MessageHeaderValue.binary(new byte[] {1, 2, 3}).hashCode()));
        assertThat(value, not(MessageHeaderValue.binary(new byte[] {1, 2, 4})));

        byte[] exposed = value.value();
        exposed[1] = 9;
        assertArrayEquals(new byte[] {1, 2, 3}, value.value());
    }

    @Test
    void nativeValuesAreImmutableContentValues() {
        byte[] source = {4, 5, 6};
        MessageHeaderValue.NativeValue value = MessageHeaderValue.nativeValue("amqp:field-table", source);
        source[0] = 9;

        assertThat(value.typeId(), is("amqp:field-table"));
        assertArrayEquals(new byte[] {4, 5, 6}, value.value());
        assertThat(value.size(), is(3));
        assertThat(value, is(MessageHeaderValue.nativeValue("amqp:field-table", new byte[] {4, 5, 6})));
        assertThat(value.hashCode(),
                   is(MessageHeaderValue.nativeValue("amqp:field-table", new byte[] {4, 5, 6}).hashCode()));
        assertThat(value, not(MessageHeaderValue.nativeValue("other:type", new byte[] {4, 5, 6})));
        assertThat(value, not(MessageHeaderValue.nativeValue("amqp:field-table", new byte[] {4, 5, 7})));

        byte[] exposed = value.value();
        exposed[1] = 9;
        assertArrayEquals(new byte[] {4, 5, 6}, value.value());
    }

    @Test
    void rejectsInvalidValues() {
        assertThrows(NullPointerException.class, () -> MessageHeaderValue.text(null));
        assertThrows(NullPointerException.class, () -> MessageHeaderValue.binary(null));
        assertThrows(NullPointerException.class, () -> MessageHeaderValue.integer((BigInteger) null));
        assertThrows(NullPointerException.class, () -> MessageHeaderValue.decimal(null));
        assertThrows(NullPointerException.class, () -> MessageHeaderValue.timestamp(null));
        assertThrows(NullPointerException.class, () -> MessageHeaderValue.uuid(null));
        assertThrows(NullPointerException.class, () -> MessageHeaderValue.nativeValue(null, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> MessageHeaderValue.nativeValue(" ", new byte[0]));
        assertThrows(NullPointerException.class, () -> MessageHeaderValue.nativeValue("test:value", null));
    }

    private static void assertEqualHash(MessageHeaderValue first, MessageHeaderValue second) {
        assertThat(first, is(second));
        assertThat(first.hashCode(), is(second.hashCode()));
    }
}
