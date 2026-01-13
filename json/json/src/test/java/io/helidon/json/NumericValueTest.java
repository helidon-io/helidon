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

package io.helidon.json;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for ArrayJsonParser number parsing functionality.
 * Covers integers, floating point numbers, scientific notation, and edge cases.
 */
abstract class NumericValueTest {

    // Integer parsing tests
    @Test
    public void testParseByteZero() {
        String json = "0";
        JsonParser parser = createParser(json);
        byte result = parser.readByte();

        assertThat(result, is((byte) 0));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseBytePositive() {
        String json = "127";
        JsonParser parser = createParser(json);
        byte result = parser.readByte();

        assertThat(result, is(Byte.MAX_VALUE));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseByteNegative() {
        String json = "-128";
        JsonParser parser = createParser(json);
        byte result = parser.readByte();

        assertThat(result, is(Byte.MIN_VALUE));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseByteOverflow() {
        String json = "128";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readByte);
    }

    @Test
    public void testParseShortZero() {
        String json = "0";
        JsonParser parser = createParser(json);
        short result = parser.readShort();

        assertThat(result, is((short) 0));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseShortMaxValue() {
        String json = "32767";
        JsonParser parser = createParser(json);
        short result = parser.readShort();

        assertThat(result, is(Short.MAX_VALUE));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseShortMinValue() {
        String json = "-32768";
        JsonParser parser = createParser(json);
        short result = parser.readShort();

        assertThat(result, is(Short.MIN_VALUE));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseShortOverflow() {
        String json = "32768";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readShort);
    }

    @Test
    public void testParseIntZero() {
        String json = "0";
        JsonParser parser = createParser(json);
        int result = parser.readInt();

        assertThat(result, is(0));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseIntMaxValue() {
        String json = "2147483647";
        JsonParser parser = createParser(json);
        int result = parser.readInt();

        assertThat(result, is(Integer.MAX_VALUE));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseIntMinValue() {
        String json = "-2147483648";
        JsonParser parser = createParser(json);
        int result = parser.readInt();

        assertThat(result, is(Integer.MIN_VALUE));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseIntOverflow() {
        String json = "2147483648";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readInt);
    }

    @Test
    public void testParseLongZero() {
        String json = "0";
        JsonParser parser = createParser(json);
        long result = parser.readLong();

        assertThat(result, is(0L));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseLongMaxValue() {
        String json = "9223372036854775807";
        JsonParser parser = createParser(json);
        long result = parser.readLong();

        assertThat(result, is(Long.MAX_VALUE));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseLongMinValue() {
        String json = "-9223372036854775808";
        JsonParser parser = createParser(json);
        long result = parser.readLong();

        assertThat(result, is(Long.MIN_VALUE));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseLongOverflow() {
        String json = "9223372036854775808";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readLong);
    }

    // Floating point parsing tests
    @Test
    public void testParseFloatZero() {
        String json = "0.0";
        JsonParser parser = createParser(json);
        float result = parser.readFloat();

        assertEquals(0.0f, result, 0.0001f);
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseFloatPositive() {
        String json = "123.456";
        JsonParser parser = createParser(json);
        float result = parser.readFloat();

        assertEquals(123.456f, result, 0.0001f);
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseFloatNegative() {
        String json = "-123.456";
        JsonParser parser = createParser(json);
        float result = parser.readFloat();

        assertEquals(-123.456f, result, 0.0001f);
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseFloatScientificPositive() {
        String json = "1.23e10";
        JsonParser parser = createParser(json);
        float result = parser.readFloat();

        assertEquals(1.23e10f, result, 1e6f); // Allow some precision loss
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseFloatScientificNegative() {
        String json = "1.23e-10";
        JsonParser parser = createParser(json);
        float result = parser.readFloat();

        assertEquals(1.23e-10f, result, 1e-14f);
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseFloatScientificUppercase() {
        String json = "1.23E5";
        JsonParser parser = createParser(json);
        float result = parser.readFloat();

        assertEquals(1.23E5f, result, 0.1f);
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseFloatIntegerPartOnly() {
        String json = "42";
        JsonParser parser = createParser(json);
        float result = parser.readFloat();

        assertEquals(42.0f, result, 0.0001f);
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseFloatFractionalPartOnly() {
        String json = "0.5";
        JsonParser parser = createParser(json);
        float result = parser.readFloat();

        assertEquals(0.5f, result, 0.0001f);
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseDoubleZero() {
        String json = "0.0";
        JsonParser parser = createParser(json);
        double result = parser.readDouble();

        assertEquals(0.0, result, 0.0001);
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseDoublePositive() {
        String json = "123.456789";
        JsonParser parser = createParser(json);
        double result = parser.readDouble();

        assertEquals(123.456789, result, 0.0001);
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseDoubleNegative() {
        String json = "-123.456789";
        JsonParser parser = createParser(json);
        double result = parser.readDouble();

        assertEquals(-123.456789, result, 0.0001);
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseDoubleScientificLarge() {
        String json = "1.23e100";
        JsonParser parser = createParser(json);
        double result = parser.readDouble();

        assertEquals(1.23e100, result, 1e96);
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseDoubleScientificSmall() {
        String json = "1.23e-100";
        JsonParser parser = createParser(json);
        double result = parser.readDouble();

        assertEquals(1.23e-100, result, 1e-104);
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseDoubleMaxPrecision() {
        String json = "0.123456789012345678901234567890123456789";
        JsonParser parser = createParser(json);
        double result = parser.readDouble();

        // Double has about 15 decimal digits of precision
        assertThat(result, is(Double.parseDouble(json)));
        assertThat(parser.hasNext(), is(false));
    }

    // Edge cases and error conditions
    @Test
    public void testParseIntLeadingZeros() {
        String json = "007";
        JsonParser parser = createParser(json);
        int result = parser.readInt();

        assertThat(result, is(7));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseFloatLeadingZeros() {
        String json = "007.5";
        JsonParser parser = createParser(json);
        float result = parser.readFloat();

        assertEquals(7.5f, result, 0.0001f);
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseFloatMultipleDecimalPoints() {
        String json = "1.2.3";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readFloat);
    }

    @Test
    public void testParseFloatMultipleExponents() {
        String json = "1.2e3e4";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readFloat);
    }

    @Test
    public void testParseFloatExponentWithoutNumber() {
        String json = "1.2e";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readFloat);
    }

    @Test
    public void testParseIntNonNumeric() {
        String json = "abc";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readInt);
    }

    @Test
    public void testParseFloatNonNumeric() {
        String json = "abc";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readFloat);
    }

    @Test
    public void testParseFloatJustExponent() {
        String json = "e10";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readFloat);
    }

    @Test
    public void testParseFloatExponentWithPlus() {
        String json = "1.23e+10";
        JsonParser parser = createParser(json);
        float result = parser.readFloat();

        assertEquals(1.23e10f, result, 1e6f);
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseFloatExponentWithMinus() {
        String json = "1.23e-5";
        JsonParser parser = createParser(json);
        float result = parser.readFloat();

        assertEquals(1.23e-5f, result, 1e-9f);
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseFloatLargeExponent() {
        String json = "1.0e1000";
        JsonParser parser = createParser(json);
        float result = parser.readFloat();

        // Very large exponents should result in infinity
        assertEquals(Float.POSITIVE_INFINITY, result, 0.0f);
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseFloatSmallExponent() {
        String json = "1.0e-1000";
        JsonParser parser = createParser(json);
        float result = parser.readFloat();

        // Very small exponents should result in zero
        assertEquals(0.0f, result, 0.0f);
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseDoubleLargeExponent() {
        String json = "1.0e1000";
        JsonParser parser = createParser(json);
        double result = parser.readDouble();

        // Very large exponents should result in infinity
        assertEquals(Double.POSITIVE_INFINITY, result, 0.0);
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseDoubleSmallExponent() {
        String json = "1.0e-1000";
        JsonParser parser = createParser(json);
        double result = parser.readDouble();

        // Very small exponents should result in zero
        assertEquals(0.0, result, 0.0);
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseLongVeryLargeNumber() {
        String json = "999999999999999999999999999999";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readLong);
    }

    @Test
    public void testParseIntVeryLargeNumber() {
        String json = "999999999999999999999999999999";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readInt);
    }

    // Test different number formats and representations
    @Test
    public void testParseFloatNegativeExponent() {
        String json = "123.456E-10";
        JsonParser parser = createParser(json);
        float result = parser.readFloat();

        assertEquals(123.456e-10f, result, 1e-14f);
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseDoubleNegativeExponent() {
        String json = "123.456E-10";
        JsonParser parser = createParser(json);
        double result = parser.readDouble();

        assertEquals(123.456e-10, result, 1e-14);
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseFloatUppercaseExponent() {
        String json = "1.5E5";
        JsonParser parser = createParser(json);
        float result = parser.readFloat();

        assertEquals(150000.0f, result, 0.1f);
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseDoubleUppercaseExponent() {
        String json = "1.5E5";
        JsonParser parser = createParser(json);
        double result = parser.readDouble();

        assertEquals(150000.0, result, 0.1);
        assertThat(parser.hasNext(), is(false));
    }

    abstract JsonParser createParser(String template);

}
