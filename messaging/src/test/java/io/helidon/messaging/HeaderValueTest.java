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

        assertThat(HeaderValue.nullValue(), sameInstance(HeaderValue.NullValue.INSTANCE));
        assertThat(HeaderValue.text("text"), is(new HeaderValue.TextValue("text")));
        assertThat(HeaderValue.booleanValue(true), is(new HeaderValue.BooleanValue(true)));
        assertThat(HeaderValue.integer(42), is(new HeaderValue.IntegerValue(BigInteger.valueOf(42))));
        assertThat(HeaderValue.integer(BigInteger.TEN), is(new HeaderValue.IntegerValue(BigInteger.TEN)));
        assertThat(HeaderValue.decimal(new BigDecimal("12.30")),
                   is(new HeaderValue.DecimalValue(new BigDecimal("12.30"))));
        assertThat(HeaderValue.floatingPoint(1.5F), is(new HeaderValue.Float32Value(1.5F)));
        assertThat(HeaderValue.floatingPoint(2.5D), is(new HeaderValue.Float64Value(2.5D)));
        assertThat(HeaderValue.timestamp(timestamp), is(new HeaderValue.TimestampValue(timestamp)));
        assertThat(HeaderValue.uuid(uuid), is(new HeaderValue.UuidValue(uuid)));
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
}
