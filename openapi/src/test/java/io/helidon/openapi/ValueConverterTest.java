/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
 *
 */
package io.helidon.openapi;

import java.util.Calendar;
import java.util.Date;
import java.util.Optional;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for value parsing.
 */
public class ValueConverterTest {

    private static final ValueConverter CONVERTER = ValueConverter.INSTANCE;

    public ValueConverterTest() {
    }

    @Test
    public void testInteger() throws Exception {
        Optional<Integer> i = CONVERTER.toInteger("4");
        assertEquals(4, i.get().intValue());
        i = CONVERTER.toInteger("-2");
        assertEquals(-2, i.get().intValue());

        i = CONVERTER.toInteger(Optional.of("4"));
        assertEquals(4, i.get().intValue());

        assertThrows(IllegalArgumentException.class, () -> {
            CONVERTER.toInteger("2.3");
        });

        assertFalse(CONVERTER.toInteger((String) null).isPresent());
    }

    @Test
    public void testIntegerInt32() {
        Optional<Integer> i = CONVERTER.toIntegerInt32("4");
        assertEquals(4, i.get().intValue());
        i = CONVERTER.toIntegerInt32("-2");
        assertEquals(-2, i.get().intValue());

        i = CONVERTER.toIntegerInt32(Optional.of("4"));
        assertEquals(4, i.get().intValue());

        assertThrows(IllegalArgumentException.class, () -> {
                CONVERTER.toIntegerInt32("2.3");
        });

        assertFalse(CONVERTER.toInteger((String) null).isPresent());
    }

    @Test
    public void testIntegerInt64() {
        Optional<Long> l = CONVERTER.toIntegerInt64("4");
        assertEquals(4l, l.get().longValue());
        l = CONVERTER.toIntegerInt64("-2");
        assertEquals(-2l, l.get().longValue());

        l = CONVERTER.toIntegerInt64(Optional.of("4"));
        assertEquals(-2l, l.get().longValue());

        assertThrows(IllegalArgumentException.class, () -> {
            CONVERTER.toIntegerInt64("2.3");
        });

        assertFalse(CONVERTER.toLong((String) null).isPresent());
    }

    @Test
    public void testNumber() {
        Optional<Double> d = CONVERTER.toNumber("1.3");
        assertEquals(1.3d, d.get().doubleValue());
        d = CONVERTER.toNumber("-1.4");
        assertEquals(-1.4d, d.get().doubleValue());

        d = CONVERTER.toNumber(Optional.of("-1.4"));
        assertEquals(-1.4d, d.get().doubleValue());

        assertThrows(IllegalArgumentException.class, () -> {
            CONVERTER.toNumber("junk");
        });

        assertFalse(CONVERTER.toDouble((String) null).isPresent());
    }

    @Test
    public void testNumberDouble() {
        Optional<Double> d = CONVERTER.toNumber("1.3");
        assertEquals(1.3d, d.get().doubleValue());
        d = CONVERTER.toNumber("-1.4");
        assertEquals(-1.4d, d.get().doubleValue());

        d = CONVERTER.toNumber(Optional.of("-1.4"));
        assertEquals(-1.4d, d.get().doubleValue());

        assertThrows(IllegalArgumentException.class, () -> {
            CONVERTER.toNumber("junk");
        });
    }

    @Test
    public void testNumberFloat() {
        Optional<Float> f = CONVERTER.toNumberFloat("1.3");
        assertEquals(1.3f, f.get().floatValue());
        f = CONVERTER.toNumberFloat("-1.4");
        assertEquals(-1.4f, f.get().floatValue());

        f = CONVERTER.toNumberFloat(Optional.of("-1.4"));
        assertEquals(-1.4f, f.get().floatValue());

        assertThrows(IllegalArgumentException.class, () -> {
            CONVERTER.toNumber("junk");
        });

        assertFalse(CONVERTER.toNumberFloat((String) null).isPresent());
    }

    @Test
    public void testString() {
        Optional<String> s = CONVERTER.toString("hi");
        assertEquals("hi", s.get());

        s = CONVERTER.toString(Optional.of("hi"));
        assertEquals("hi", s.get());

        assertFalse(CONVERTER.toString((String) null).isPresent());
    }

    @Test
    public void testStringByte() {
        Optional<byte[]> b = CONVERTER.toStringByte("SGkgdGhlcmU=");
        assertArrayEquals("Hi there".getBytes(), b.get());

        b = CONVERTER.toStringByte(Optional.of("SGkgdGhlcmU="));
        assertArrayEquals("Hi there".getBytes(), b.get());

        assertFalse(CONVERTER.toStringByte((String) null).isPresent());
    }

    @Test
    public void testStringBinary() {
        Optional<byte[]> b = CONVERTER.toStringBinary("476F6F646279652068657265");
        assertArrayEquals("Goodbye here".getBytes(), b.get());

        b = CONVERTER.toStringBinary(Optional.of("476F6F646279652068657265"));
        assertArrayEquals("Goodbye here".getBytes(), b.get());

        assertFalse(CONVERTER.toStringBinary((String) null).isPresent());
    }

    @Test
    public void testBoolean() {
        Optional<Boolean> b = CONVERTER.toBoolean("true");
        assertTrue(b.get());
        b = CONVERTER.toBoolean("TRUE");
        assertTrue(b.get());
        b = CONVERTER.toBoolean("false");
        assertFalse(b.get());
        b = CONVERTER.toBoolean("FALSE");
        assertFalse(b.get());

        b = CONVERTER.toBoolean(Optional.of("true"));
        assertTrue(b.get());

        b = CONVERTER.toBoolean("junk");
        assertFalse(b.get());

        assertFalse(CONVERTER.toBoolean((String) null).isPresent());
    }

    @Test
    public void testStringDate() {
        Optional<Date> d = CONVERTER.toStringDate("2019-08-01");
        Calendar c = new Calendar.Builder()
                .setDate(2019, 7 /* 0-based! */, 1)
                .build();
        assertEquals(c.toInstant(), d.get().toInstant());

        d = CONVERTER.toStringDate(Optional.of("2019-08-01"));
        assertEquals(c.toInstant(), d.get().toInstant());

        assertThrows(IllegalArgumentException.class, () -> {
            CONVERTER.toStringDate("a-b-c");
        });

        assertFalse(CONVERTER.toStringDate((String) null).isPresent());
    }

    @Test
    public void testStringDateDateTime() {
        Optional<Date> d = CONVERTER.toStringDateDateTime("2019-08-01T12:34:56.987Z+00:00");
        Calendar c = new Calendar.Builder()
                .setTimeZone(TimeZone.getTimeZone("Z+0"))
                .setDate(2019, 7, 1)
                .setTimeOfDay(12, 34, 56, 987)
                .build();
        assertEquals(c.toInstant(), d.get().toInstant());

        d = CONVERTER.toStringDateDateTime("2019-08-01T12:34:56.987Z+00:00");
        assertEquals(c.toInstant(), d.get().toInstant());

        assertThrows(IllegalArgumentException.class, () -> {
            CONVERTER.toStringDateDateTime("a-b-cTw:x;y");
        });

        assertFalse(CONVERTER.toStringDateDateTime((String) null).isPresent());
    }

    @Test
    public void testStringPassword() {
        Optional<char[]> pw = CONVERTER.toStringPassword("hello");
        assertArrayEquals("hello".toCharArray(), pw.get());

        pw = CONVERTER.toStringPassword(Optional.of("hello"));
        assertArrayEquals("hello".toCharArray(), pw.get());

        assertFalse(CONVERTER.toStringPassword((String) null).isPresent());
    }
}
