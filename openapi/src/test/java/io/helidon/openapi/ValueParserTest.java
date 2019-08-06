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
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for value parsing.
 */
public class ValueParserTest {

    private static final ValueParser PARSER = ValueParser.PARSER;

    public ValueParserTest() {
    }

    @Test
    public void testInteger() throws Exception {
        int i = PARSER.integer("4");
        assertEquals(4, i);
        i = PARSER.integer("-2");
        assertEquals(-2, i);

        assertThrows(IllegalArgumentException.class, () -> {
            PARSER.integer("2.3");
        });
    }

    @Test
    public void testIntegerInt32() {
        int i = PARSER.integerInt32("4");
        assertEquals(4, i);
        i = PARSER.integerInt32("-2");
        assertEquals(-2, i);

        assertThrows(IllegalArgumentException.class, () -> {
                PARSER.integerInt32("2.3");
        });
    }

    @Test
    public void testIntegerInt64() {
        long l = PARSER.integerInt64("4");
        assertEquals(4l, l);
        l = PARSER.integerInt64("-2");
        assertEquals(-2l, l);

        assertThrows(IllegalArgumentException.class, () -> {
            PARSER.integerInt64("2.3");
        });
    }

    @Test
    public void testNumber() {
        double d = PARSER.number("1.3");
        assertEquals(1.3d, d);
        d = PARSER.number("-1.4");
        assertEquals(-1.4d, d);

        assertThrows(IllegalArgumentException.class, () -> {
            PARSER.number("junk");
        });
    }

    @Test
    public void testNumberDouble() {
        double d = PARSER.number("1.3");
        assertEquals(1.3d, d);
        d = PARSER.number("-1.4");
        assertEquals(-1.4d, d);

        assertThrows(IllegalArgumentException.class, () -> {
            PARSER.number("junk");
        });
    }

    @Test
    public void testNumberFloat() {
        float f = PARSER.numberFloat("1.3");
        assertEquals(1.3f, f);
        f = PARSER.numberFloat("-1.4");
        assertEquals(-1.4f, f);

        assertThrows(IllegalArgumentException.class, () -> {
            PARSER.number("junk");
        });
    }

    @Test
    public void testString() {
        String s = PARSER.string("hi");
        assertEquals("hi", s);
    }

    @Test
    public void testStringByte() {
        byte[] b = PARSER.stringByte("SGkgdGhlcmU=");
        assertArrayEquals("Hi there".getBytes(), b);
    }

    @Test
    public void testStringBinary() {
        byte[] b = PARSER.stringBinary("476F6F646279652068657265");
        assertArrayEquals("Goodbye here".getBytes(), b);
    }

    @Test
    public void testBoolean() {
        boolean b = PARSER.bool("true");
        assertTrue(b);
        b = PARSER.bool("TRUE");
        assertTrue(b);
        b = PARSER.bool("false");
        assertFalse(b);
        b = PARSER.bool("FALSE");
        assertFalse(b);

        b = PARSER.bool("junk");
        assertFalse(b);
    }

    @Test
    public void testStringDate() {
        Date d = PARSER.date("2019-08-01");
        Calendar c = new Calendar.Builder()
                .setDate(2019, 7 /* 0-based! */, 1)
                .build();
        assertEquals(c.toInstant(), d.toInstant());

        assertThrows(IllegalArgumentException.class, () -> {
            PARSER.date("a-b-c");
        });
    }

    @Test
    public void testStringDateDateTime() {
        Date d = PARSER.dateDateTime("2019-08-01T12:34:56.987Z+00:00");
        Calendar c = new Calendar.Builder()
                .setTimeZone(TimeZone.getTimeZone("Z+0"))
                .setDate(2019, 7, 1)
                .setTimeOfDay(12, 34, 56, 987)
                .build();
        assertEquals(c.toInstant(), d.toInstant());

        assertThrows(IllegalArgumentException.class, () -> {
            PARSER.dateDateTime("a-b-cTw:x;y");
        });
    }

    @Test
    public void testStringPassword() {
        char[] pw = PARSER.stringPassword("hello");
        assertArrayEquals("hello".toCharArray(), pw);
    }
}
