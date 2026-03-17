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
 * Tests for ArrayJsonParser string handling functionality.
 * Covers basic strings, escaped characters, Unicode, UTF-8 multibyte sequences, and edge cases.
 */
class StringValueTest {

    // Basic ASCII string tests
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testBasicAsciiString(ParserMethod parserMethod) {
        String json = "\"hello world\"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("hello world"));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testEmptyString(ParserMethod parserMethod) {
        String json = "\"\"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is(""));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testSingleCharacterString(ParserMethod parserMethod) {
        String json = "\"a\"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("a"));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testStringWithSpaces(ParserMethod parserMethod) {
        String json = "\"  hello   world  \"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("  hello   world  "));
        assertThat(parser.hasNext(), is(false));
    }

    // Escaped character tests
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testEscapedQuote(ParserMethod parserMethod) {
        String json = "\"He said \\\"hello\\\"\"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("He said \"hello\""));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testEscapedBackslash(ParserMethod parserMethod) {
        String json = "\"path\\\\to\\\\file\"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("path\\to\\file"));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testEscapedSolidus(ParserMethod parserMethod) {
        String json = "\"http:\\/\\/example.com\"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("http://example.com"));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testEscapedBackspace(ParserMethod parserMethod) {
        String json = "\"line1\\bline2\"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("line1\bline2"));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testEscapedFormFeed(ParserMethod parserMethod) {
        String json = "\"page1\\fpage2\"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("page1\fpage2"));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testEscapedNewline(ParserMethod parserMethod) {
        String json = "\"line1\\nline2\"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("line1\nline2"));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testEscapedCarriageReturn(ParserMethod parserMethod) {
        String json = "\"line1\\rline2\"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("line1\rline2"));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testEscapedTab(ParserMethod parserMethod) {
        String json = "\"col1\\tcol2\"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("col1\tcol2"));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testMultipleEscapedCharacters(ParserMethod parserMethod) {
        String json = "\"\\n\\t\\r\\f\\b\\\\\\\"\\\"\"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("\n\t\r\f\b\\\"\""));
        assertThat(parser.hasNext(), is(false));
    }

    // Unicode escape sequence tests (\\uXXXX)
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testUnicodeEscapeBasic(ParserMethod parserMethod) {
        String json = "\"\\u0041\"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("A"));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testUnicodeEscapeLowercase(ParserMethod parserMethod) {
        String json = "\"\\u0061\"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("a"));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testUnicodeEscapeEuroSymbol(ParserMethod parserMethod) {
        String json = "\"\\u20AC\"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("€"));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testUnicodeEscapeMultiple(ParserMethod parserMethod) {
        String json = "\"\\u0048\\u0065\\u006C\\u006C\\u006F\"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("Hello"));
        assertThat(parser.hasNext(), is(false));
    }

    // UTF-8 multibyte sequence tests
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testUtf8TwoByteSequence(ParserMethod parserMethod) {
        // U+00A9 (copyright symbol) - 2 bytes in UTF-8: C2 A9
        String json = "\"©\"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("©"));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testUtf8ThreeByteSequence(ParserMethod parserMethod) {
        // U+20AC (euro symbol) - 3 bytes in UTF-8: E2 82 AC
        String json = "\"€\"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("€"));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testUtf8FourByteSequence(ParserMethod parserMethod) {
        // U+1F600 (grinning face emoji) - 4 bytes in UTF-8: F0 9F 98 80
        String json = "\"😀\"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("😀"));
        assertThat(parser.hasNext(), is(false));
    }

    // Surrogate pair tests
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testSurrogatePairInJson(ParserMethod parserMethod) {
        // U+1F600 (grinning face) as surrogate pair in JSON: \uD83D\uDE00
        String json = "\"\\uD83D\\uDE00\"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("😀"));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testMultipleSurrogatePairs(ParserMethod parserMethod) {
        // Multiple emojis with surrogate pairs
        String json = "\"\\uD83D\\uDE00\\uD83D\\uDE01\\uD83D\\uDE02\"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("😀😁😂"));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testMixedAsciiAndSurrogates(ParserMethod parserMethod) {
        String json = "\"Hello \\uD83D\\uDE00 World\"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("Hello 😀 World"));
        assertThat(parser.hasNext(), is(false));
    }

    // Error case tests
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testInvalidUnicodeEscape(ParserMethod parserMethod) {
        // Invalid hex digit 'G'
        String json = "\"\\u004G\"";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readString);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testIncompleteUnicodeEscape(ParserMethod parserMethod) {
        String json = "\"\\u004\"";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readString);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testInvalidUtf8Sequence(ParserMethod parserMethod) {
        // Invalid UTF-8: isolated continuation byte - create directly with bytes
        byte[] invalidUtf8 = new byte[] {'"', (byte) 0x80, '"'};
        JsonParserArray parser = new JsonParserArray(invalidUtf8);

        assertThrows(JsonException.class, parser::readString);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testIncompleteUtf8Sequence(ParserMethod parserMethod) {
        // Incomplete 3-byte sequence: E2 82 (missing AC) - create directly with bytes
        byte[] incompleteUtf8 = new byte[] {'"', (byte) 0xE2, (byte) 0x82, '"'};
        JsonParserArray parser = new JsonParserArray(incompleteUtf8);

        assertThrows(JsonException.class, parser::readString);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testUnterminatedString(ParserMethod parserMethod) {
        String json = "\"hello";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readString);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testInvalidEscapeSequence(ParserMethod parserMethod) {
        String json = "\"\\z\"";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readString);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testHighSurrogateWithoutLowSurrogate(ParserMethod parserMethod) {
        // High surrogate followed by regular character instead of low surrogate
        String json = "\"\\uD83DA\"";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readString);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testLowSurrogateWithoutHighSurrogate(ParserMethod parserMethod) {
        // Low surrogate without preceding high surrogate
        String json = "\"\\uDE00\"";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readString);
    }

    // Edge case tests
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testStringWithNullCharacter(ParserMethod parserMethod) {
        String json = "\"hello\\u0000world\"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("hello\u0000world"));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testStringWithControlCharacters(ParserMethod parserMethod) {
        String json = "\"\\u0001\\u0002\\u001F\"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("\u0001\u0002\u001F"));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testVeryLongString(ParserMethod parserMethod) {
        int count = 10000;
        String expected = "a".repeat(count);
        String json = "\"" + expected + "\"";

        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result.length(), is(count));
        assertThat(result, is(expected));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testStringWithEscapedQuotesAtBoundaries(ParserMethod parserMethod) {
        String json = "\"\\\"hello\\\"world\\\"\"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("\"hello\"world\""));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testComplexMixedString(ParserMethod parserMethod) {
        // Mix of ASCII, escaped chars, Unicode escapes, and UTF-8 multibyte chars
        String json = "\"Hello\\n\\u0041\\u4E2D\\u6587©\\uD83D\\uDE00\"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("Hello\nA中文©😀"));
        assertThat(parser.hasNext(), is(false));
    }

    // Test readChar method specifically (uses decodeUtf8ToChar)
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testReadCharAscii(ParserMethod parserMethod) {
        String json = "\"A\"";
        JsonParser parser = parserMethod.createParser(json);
        char result = parser.readChar();

        assertEquals('A', result);
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testReadCharUnicodeEscape(ParserMethod parserMethod) {
        String json = "\"\\u0042\"";
        JsonParser parser = parserMethod.createParser(json);
        char result = parser.readChar();

        assertEquals('B', result);
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testReadCharUtf8TwoByte(ParserMethod parserMethod) {
        String json = "\"©\"";
        JsonParser parser = parserMethod.createParser(json);
        char result = parser.readChar();

        assertEquals('©', result);
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testReadCharUtf8ThreeByte(ParserMethod parserMethod) {
        String json = "\"€\"";
        JsonParser parser = parserMethod.createParser(json);
        char result = parser.readChar();

        assertEquals('€', result);
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testReadCharRejectsSurrogate(ParserMethod parserMethod) {
        // This should fail because surrogate pairs require 2 chars
        String json = "\"\\uD83D\\uDE00\"";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readChar);
    }

    // Additional corner cases
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testStringWithAllWhitespace(ParserMethod parserMethod) {
        String json = "\"\\n\\t\\r \"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("\n\t\r "));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testStringWithOnlyEscapedCharacters(ParserMethod parserMethod) {
        String json = "\"\\n\\t\\r\\f\\b\\\\\\\"\"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("\n\t\r\f\b\\\""));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testStringWithEscapedNull(ParserMethod parserMethod) {
        String json = "\"\\u0000\"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("\u0000"));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testStringWithMaximumUnicodeCodePoint(ParserMethod parserMethod) {
        // U+10FFFF - maximum valid Unicode code point
        String json = "\"\\uDBFF\\uDFFF\"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("\uDBFF\uDFFF"));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testStringWithMixedSurrogatesAndRegular(ParserMethod parserMethod) {
        // Mix of surrogate pairs and regular characters
        String json = "\"A\\uD83D\\uDE00B\\uD83D\\uDE01C\"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("A😀B😁C"));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testStringWithConsecutiveEscapedSequences(ParserMethod parserMethod) {
        String json = "\"\\\\\\n\\\\\\t\\\\\\\"\"";
        JsonParser parser = parserMethod.createParser(json);
        String result = parser.readString();

        assertThat(result, is("\\\n\\\t\\\""));
        assertThat(parser.hasNext(), is(false));
    }

}
