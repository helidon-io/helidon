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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for ArrayJsonParser number parsing functionality.
 * Covers integers, floating point numbers, scientific notation, and edge cases.
 */
class NumericValueTest {

    // Integer parsing tests
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseByteZero(ParserMethod parserMethod) {
        String json = "0";
        JsonParser parser = parserMethod.createParser(json);
        byte result = parser.readByte();

        assertThat(result, is((byte) 0));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseBytePositive(ParserMethod parserMethod) {
        String json = "127";
        JsonParser parser = parserMethod.createParser(json);
        byte result = parser.readByte();

        assertThat(result, is(Byte.MAX_VALUE));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseByteNegative(ParserMethod parserMethod) {
        String json = "-128";
        JsonParser parser = parserMethod.createParser(json);
        byte result = parser.readByte();

        assertThat(result, is(Byte.MIN_VALUE));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseByteOverflow(ParserMethod parserMethod) {
        String json = "128";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readByte);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseShortZero(ParserMethod parserMethod) {
        String json = "0";
        JsonParser parser = parserMethod.createParser(json);
        short result = parser.readShort();

        assertThat(result, is((short) 0));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseShortMaxValue(ParserMethod parserMethod) {
        String json = "32767";
        JsonParser parser = parserMethod.createParser(json);
        short result = parser.readShort();

        assertThat(result, is(Short.MAX_VALUE));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseShortMinValue(ParserMethod parserMethod) {
        String json = "-32768";
        JsonParser parser = parserMethod.createParser(json);
        short result = parser.readShort();

        assertThat(result, is(Short.MIN_VALUE));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseShortOverflow(ParserMethod parserMethod) {
        String json = "32768";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readShort);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseIntZero(ParserMethod parserMethod) {
        String json = "0";
        JsonParser parser = parserMethod.createParser(json);
        int result = parser.readInt();

        assertThat(result, is(0));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseIntMaxValue(ParserMethod parserMethod) {
        String json = "2147483647";
        JsonParser parser = parserMethod.createParser(json);
        int result = parser.readInt();

        assertThat(result, is(Integer.MAX_VALUE));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseIntMinValue(ParserMethod parserMethod) {
        String json = "-2147483648";
        JsonParser parser = parserMethod.createParser(json);
        int result = parser.readInt();

        assertThat(result, is(Integer.MIN_VALUE));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseIntOverflow(ParserMethod parserMethod) {
        String json = "2147483648";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readInt);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseLongZero(ParserMethod parserMethod) {
        String json = "0";
        JsonParser parser = parserMethod.createParser(json);
        long result = parser.readLong();

        assertThat(result, is(0L));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseLongMaxValue(ParserMethod parserMethod) {
        String json = "9223372036854775807";
        JsonParser parser = parserMethod.createParser(json);
        long result = parser.readLong();

        assertThat(result, is(Long.MAX_VALUE));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseLongMinValue(ParserMethod parserMethod) {
        String json = "-9223372036854775808";
        JsonParser parser = parserMethod.createParser(json);
        long result = parser.readLong();

        assertThat(result, is(Long.MIN_VALUE));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseLongOverflow(ParserMethod parserMethod) {
        String json = "9223372036854775808";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readLong);
    }

    // Floating point parsing tests
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseFloatZero(ParserMethod parserMethod) {
        String json = "0.0";
        JsonParser parser = parserMethod.createParser(json);
        float result = parser.readFloat();

        assertEquals(0.0f, result, 0.0001f);
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseFloatPositive(ParserMethod parserMethod) {
        String json = "123.456";
        JsonParser parser = parserMethod.createParser(json);
        float result = parser.readFloat();

        assertEquals(123.456f, result, 0.0001f);
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseFloatNegative(ParserMethod parserMethod) {
        String json = "-123.456";
        JsonParser parser = parserMethod.createParser(json);
        float result = parser.readFloat();

        assertEquals(-123.456f, result, 0.0001f);
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseFloatScientificPositive(ParserMethod parserMethod) {
        String json = "1.23e10";
        JsonParser parser = parserMethod.createParser(json);
        float result = parser.readFloat();

        assertEquals(1.23e10f, result, 1e6f); // Allow some precision loss
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseFloatScientificNegative(ParserMethod parserMethod) {
        String json = "1.23e-10";
        JsonParser parser = parserMethod.createParser(json);
        float result = parser.readFloat();

        assertEquals(1.23e-10f, result, 1e-14f);
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseFloatScientificUppercase(ParserMethod parserMethod) {
        String json = "1.23E5";
        JsonParser parser = parserMethod.createParser(json);
        float result = parser.readFloat();

        assertEquals(1.23E5f, result, 0.1f);
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseFloatIntegerPartOnly(ParserMethod parserMethod) {
        String json = "42";
        JsonParser parser = parserMethod.createParser(json);
        float result = parser.readFloat();

        assertEquals(42.0f, result, 0.0001f);
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseFloatFractionalPartOnly(ParserMethod parserMethod) {
        String json = "0.5";
        JsonParser parser = parserMethod.createParser(json);
        float result = parser.readFloat();

        assertEquals(0.5f, result, 0.0001f);
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseDoubleZero(ParserMethod parserMethod) {
        String json = "0.0";
        JsonParser parser = parserMethod.createParser(json);
        double result = parser.readDouble();

        assertEquals(0.0, result, 0.0001);
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseDoublePositive(ParserMethod parserMethod) {
        String json = "123.456789";
        JsonParser parser = parserMethod.createParser(json);
        double result = parser.readDouble();

        assertEquals(123.456789, result, 0.0001);
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseDoubleNegative(ParserMethod parserMethod) {
        String json = "-123.456789";
        JsonParser parser = parserMethod.createParser(json);
        double result = parser.readDouble();

        assertEquals(-123.456789, result, 0.0001);
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseDoubleScientificLarge(ParserMethod parserMethod) {
        String json = "1.23e100";
        JsonParser parser = parserMethod.createParser(json);
        double result = parser.readDouble();

        assertEquals(1.23e100, result, 1e96);
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseDoubleScientificSmall(ParserMethod parserMethod) {
        String json = "1.23e-100";
        JsonParser parser = parserMethod.createParser(json);
        double result = parser.readDouble();

        assertEquals(1.23e-100, result, 1e-104);
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseDoubleMaxPrecision(ParserMethod parserMethod) {
        String json = "0.123456789012345678901234567890123456789";
        JsonParser parser = parserMethod.createParser(json);
        double result = parser.readDouble();

        // Double has about 15 decimal digits of precision
        assertThat(result, is(Double.parseDouble(json)));
        assertThat(parser.hasNext(), is(false));
    }

    // Edge cases and error conditions
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseIntLeadingZeros(ParserMethod parserMethod) {
        String json = "007";
        JsonParser parser = parserMethod.createParser(json);
        int result = parser.readInt();

        assertThat(result, is(7));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseFloatLeadingZeros(ParserMethod parserMethod) {
        String json = "007.5";
        JsonParser parser = parserMethod.createParser(json);
        float result = parser.readFloat();

        assertEquals(7.5f, result, 0.0001f);
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseFloatMultipleDecimalPoints(ParserMethod parserMethod) {
        String json = "1.2.3";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readFloat);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseFloatMultipleExponents(ParserMethod parserMethod) {
        String json = "1.2e3e4";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readFloat);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseFloatExponentWithoutNumber(ParserMethod parserMethod) {
        String json = "1.2e";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readFloat);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseIntNonNumeric(ParserMethod parserMethod) {
        String json = "abc";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readInt);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseFloatNonNumeric(ParserMethod parserMethod) {
        String json = "abc";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readFloat);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseFloatJustExponent(ParserMethod parserMethod) {
        String json = "e10";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readFloat);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testCreateExceptionWithCausePreservesParserContext(ParserMethod parserMethod) {
        JsonParser parser = parserMethod.createParser("123");
        NumberFormatException cause = new NumberFormatException("boom");

        JsonException exception = parser.createException("Invalid number", cause);

        assertThat(exception.getCause(), is(cause));
        assertThat(exception.getMessage().contains("Invalid number"), is(true));
        assertThat(exception.getMessage().contains("Error at JSON index"), is(true));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testReadBigIntegerPreservesNumberFormatCause(ParserMethod parserMethod) {
        JsonParser parser = parserMethod.createParser("1.0");

        JsonException exception = assertThrows(JsonException.class, parser::readBigInteger);

        assertThat(exception.getCause() instanceof NumberFormatException, is(true));
        assertThat(exception.getMessage().contains("Invalid number"), is(true));
        assertThat(exception.getMessage().contains("Error at JSON index"), is(true));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseFloatExponentWithPlus(ParserMethod parserMethod) {
        String json = "1.23e+10";
        JsonParser parser = parserMethod.createParser(json);
        float result = parser.readFloat();

        assertEquals(1.23e10f, result, 1e6f);
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseFloatExponentWithMinus(ParserMethod parserMethod) {
        String json = "1.23e-5";
        JsonParser parser = parserMethod.createParser(json);
        float result = parser.readFloat();

        assertEquals(1.23e-5f, result, 1e-9f);
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseFloatLargeExponent(ParserMethod parserMethod) {
        String json = "1.0e1000";
        JsonParser parser = parserMethod.createParser(json);
        float result = parser.readFloat();

        // Very large exponents should result in infinity
        assertEquals(Float.POSITIVE_INFINITY, result, 0.0f);
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseFloatSmallExponent(ParserMethod parserMethod) {
        String json = "1.0e-1000";
        JsonParser parser = parserMethod.createParser(json);
        float result = parser.readFloat();

        // Very small exponents should result in zero
        assertEquals(0.0f, result, 0.0f);
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseDoubleLargeExponent(ParserMethod parserMethod) {
        String json = "1.0e1000";
        JsonParser parser = parserMethod.createParser(json);
        double result = parser.readDouble();

        // Very large exponents should result in infinity
        assertEquals(Double.POSITIVE_INFINITY, result, 0.0);
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseDoubleSmallExponent(ParserMethod parserMethod) {
        String json = "1.0e-1000";
        JsonParser parser = parserMethod.createParser(json);
        double result = parser.readDouble();

        // Very small exponents should result in zero
        assertEquals(0.0, result, 0.0);
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseLongVeryLargeNumber(ParserMethod parserMethod) {
        String json = "999999999999999999999999999999";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readLong);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseIntVeryLargeNumber(ParserMethod parserMethod) {
        String json = "999999999999999999999999999999";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readInt);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseIntFromDecimalSkipsFraction(ParserMethod parserMethod) {
        // Reading an int from a decimal number should consume the integer part (123),
        // leave the parser positioned at the last digit of the fractional part ('6'),
        // and allow parsing to continue.
        String json = "123.456 true";
        JsonParser parser = parserMethod.createParser(json);

        int result = parser.readInt();
        assertThat(result, is(123));

        // Parser should now be at '6' (last digit of the fraction) and still have data left
        assertThat(parser.currentByte(), is((byte) '6'));
        assertThat(parser.hasNext(), is(true));

        // Continue parsing to ensure the parser can proceed to the next token (should skip whitespace)
        byte nextToken = parser.nextToken();
        assertThat(nextToken, is((byte) 't'));
        assertThat(parser.readBoolean(), is(true));
        assertThat(parser.hasNext(), is(false));
    }

    // Test different number formats and representations
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseFloatNegativeExponent(ParserMethod parserMethod) {
        String json = "123.456E-10";
        JsonParser parser = parserMethod.createParser(json);
        float result = parser.readFloat();

        assertEquals(123.456e-10f, result, 1e-14f);
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseDoubleNegativeExponent(ParserMethod parserMethod) {
        String json = "123.456E-10";
        JsonParser parser = parserMethod.createParser(json);
        double result = parser.readDouble();

        assertEquals(123.456e-10, result, 1e-14);
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseFloatUppercaseExponent(ParserMethod parserMethod) {
        String json = "1.5E5";
        JsonParser parser = parserMethod.createParser(json);
        float result = parser.readFloat();

        assertEquals(150000.0f, result, 0.1f);
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseDoubleUppercaseExponent(ParserMethod parserMethod) {
        String json = "1.5E5";
        JsonParser parser = parserMethod.createParser(json);
        double result = parser.readDouble();

        assertEquals(150000.0, result, 0.1);
        assertThat(parser.hasNext(), is(false));
    }

}
