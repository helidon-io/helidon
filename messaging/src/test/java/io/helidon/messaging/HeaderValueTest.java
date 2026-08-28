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

class HeaderValueTest {
    @Test
    void createsClosedScalarValues() {
        Instant timestamp = Instant.parse("2026-08-26T10:15:30Z");
        UUID uuid = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");

        HeaderValue.TextValue text = HeaderValue.text("text");
        HeaderValue.BooleanValue booleanValue = HeaderValue.booleanValue(true);
        HeaderValue.IntegerValue integer = HeaderValue.integer(42);
        HeaderValue.DecimalValue decimal = HeaderValue.decimal(new BigDecimal("12.30"));
        HeaderValue.Float32Value float32 = HeaderValue.floatingPoint(1.5F);
        HeaderValue.Float64Value float64 = HeaderValue.floatingPoint(2.5D);
        HeaderValue.TimestampValue timestampValue = HeaderValue.timestamp(timestamp);
        HeaderValue.UuidValue uuidValue = HeaderValue.uuid(uuid);

        assertThat(HeaderValue.nullValue(), sameInstance(HeaderValue.nullValue()));
        assertThat(text.value(), is("text"));
        assertThat(text, is(HeaderValue.text("text")));
        assertThat(booleanValue.value(), is(true));
        assertThat(booleanValue, is(HeaderValue.booleanValue(true)));
        assertThat(integer.value(), is(BigInteger.valueOf(42)));
        assertThat(integer, is(HeaderValue.integer(BigInteger.valueOf(42))));
        assertThat(HeaderValue.integer(BigInteger.TEN), is(HeaderValue.integer(BigInteger.TEN)));
        assertThat(decimal.value(), is(new BigDecimal("12.30")));
        assertThat(decimal, is(HeaderValue.decimal(new BigDecimal("12.30"))));
        assertThat(decimal, not(HeaderValue.decimal(new BigDecimal("12.3"))));
        assertThat(float32.value(), is(1.5F));
        assertThat(float32, is(HeaderValue.floatingPoint(1.5F)));
        assertThat(float64.value(), is(2.5D));
        assertThat(float64, is(HeaderValue.floatingPoint(2.5D)));
        assertThat(timestampValue.value(), is(timestamp));
        assertThat(timestampValue, is(HeaderValue.timestamp(timestamp)));
        assertThat(uuidValue.value(), is(uuid));
        assertThat(uuidValue, is(HeaderValue.uuid(uuid)));

        assertEqualHash(text, HeaderValue.text("text"));
        assertEqualHash(booleanValue, HeaderValue.booleanValue(true));
        assertEqualHash(integer, HeaderValue.integer(42));
        assertEqualHash(decimal, HeaderValue.decimal(new BigDecimal("12.30")));
        assertEqualHash(float32, HeaderValue.floatingPoint(1.5F));
        assertEqualHash(float64, HeaderValue.floatingPoint(2.5D));
        assertEqualHash(timestampValue, HeaderValue.timestamp(timestamp));
        assertEqualHash(uuidValue, HeaderValue.uuid(uuid));
    }

    @Test
    void floatingPointValuesRetainRecordSemantics() {
        assertThat(HeaderValue.floatingPoint(Float.NaN),
                   is(HeaderValue.floatingPoint(Float.intBitsToFloat(0x7f800001))));
        assertThat(HeaderValue.floatingPoint(0.0F), not(HeaderValue.floatingPoint(-0.0F)));
        assertThat(HeaderValue.floatingPoint(Double.NaN),
                   is(HeaderValue.floatingPoint(Double.longBitsToDouble(0x7ff0000000000001L))));
        assertThat(HeaderValue.floatingPoint(0.0D), not(HeaderValue.floatingPoint(-0.0D)));
    }

    @Test
    void scalarValuesRetainRecordStyleDiagnostics() {
        assertThat(HeaderValue.nullValue().toString(), is("INSTANCE"));
        assertThat(HeaderValue.text("text").toString(), is("TextValue[value=text]"));
        assertThat(HeaderValue.booleanValue(true).toString(), is("BooleanValue[value=true]"));
        assertThat(HeaderValue.integer(42).toString(), is("IntegerValue[value=42]"));
        assertThat(HeaderValue.decimal(new BigDecimal("12.30")).toString(), is("DecimalValue[value=12.30]"));
        assertThat(HeaderValue.floatingPoint(1.5F).toString(), is("Float32Value[value=1.5]"));
        assertThat(HeaderValue.floatingPoint(2.5D).toString(), is("Float64Value[value=2.5]"));
    }

    @Test
    void binaryValuesAreImmutableContentValues() {
        byte[] source = {1, 2, 3};
        HeaderValue.BinaryValue value = HeaderValue.binary(source);
        source[0] = 9;

        assertArrayEquals(new byte[] {1, 2, 3}, value.value());
        assertThat(value.size(), is(3));
        assertThat(value, is(HeaderValue.binary(new byte[] {1, 2, 3})));
        assertThat(value.hashCode(), is(HeaderValue.binary(new byte[] {1, 2, 3}).hashCode()));
        assertThat(value, not(HeaderValue.binary(new byte[] {1, 2, 4})));

        byte[] exposed = value.value();
        exposed[1] = 9;
        assertArrayEquals(new byte[] {1, 2, 3}, value.value());
    }

    @Test
    void nativeValuesAreImmutableContentValues() {
        byte[] source = {4, 5, 6};
        HeaderValue.NativeValue value = HeaderValue.nativeValue("amqp:field-table", source);
        source[0] = 9;

        assertThat(value.typeId(), is("amqp:field-table"));
        assertArrayEquals(new byte[] {4, 5, 6}, value.value());
        assertThat(value.size(), is(3));
        assertThat(value, is(HeaderValue.nativeValue("amqp:field-table", new byte[] {4, 5, 6})));
        assertThat(value.hashCode(),
                   is(HeaderValue.nativeValue("amqp:field-table", new byte[] {4, 5, 6}).hashCode()));
        assertThat(value, not(HeaderValue.nativeValue("other:type", new byte[] {4, 5, 6})));
        assertThat(value, not(HeaderValue.nativeValue("amqp:field-table", new byte[] {4, 5, 7})));

        byte[] exposed = value.value();
        exposed[1] = 9;
        assertArrayEquals(new byte[] {4, 5, 6}, value.value());
    }

    @Test
    void rejectsInvalidValues() {
        assertThrows(NullPointerException.class, () -> HeaderValue.text(null));
        assertThrows(NullPointerException.class, () -> HeaderValue.binary(null));
        assertThrows(NullPointerException.class, () -> HeaderValue.integer((BigInteger) null));
        assertThrows(NullPointerException.class, () -> HeaderValue.decimal(null));
        assertThrows(NullPointerException.class, () -> HeaderValue.timestamp(null));
        assertThrows(NullPointerException.class, () -> HeaderValue.uuid(null));
        assertThrows(NullPointerException.class, () -> HeaderValue.nativeValue(null, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> HeaderValue.nativeValue(" ", new byte[0]));
        assertThrows(NullPointerException.class, () -> HeaderValue.nativeValue("test:value", null));
    }

    private static void assertEqualHash(HeaderValue first, HeaderValue second) {
        assertThat(first, is(second));
        assertThat(first.hashCode(), is(second.hashCode()));
    }
}
