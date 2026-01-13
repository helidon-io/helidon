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
 * Tests for ArrayJsonParser string handling functionality.
 * Covers basic strings, escaped characters, Unicode, UTF-8 multibyte sequences, and edge cases.
 */
abstract class StringValueTest {

    // Basic ASCII string tests
    @Test
    public void testBasicAsciiString() {
        String json = "\"hello world\"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("hello world"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testEmptyString() {
        String json = "\"\"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is(""));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testSingleCharacterString() {
        String json = "\"a\"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("a"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testStringWithSpaces() {
        String json = "\"  hello   world  \"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("  hello   world  "));
        assertThat(parser.hasNext(), is(false));
    }

    // Escaped character tests
    @Test
    public void testEscapedQuote() {
        String json = "\"He said \\\"hello\\\"\"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("He said \"hello\""));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testEscapedBackslash() {
        String json = "\"path\\\\to\\\\file\"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("path\\to\\file"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testEscapedSolidus() {
        String json = "\"http:\\/\\/example.com\"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("http://example.com"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testEscapedBackspace() {
        String json = "\"line1\\bline2\"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("line1\bline2"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testEscapedFormFeed() {
        String json = "\"page1\\fpage2\"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("page1\fpage2"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testEscapedNewline() {
        String json = "\"line1\\nline2\"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("line1\nline2"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testEscapedCarriageReturn() {
        String json = "\"line1\\rline2\"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("line1\rline2"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testEscapedTab() {
        String json = "\"col1\\tcol2\"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("col1\tcol2"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testMultipleEscapedCharacters() {
        String json = "\"\\n\\t\\r\\f\\b\\\\\\\"\\\"\"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("\n\t\r\f\b\\\"\""));
        assertThat(parser.hasNext(), is(false));
    }

    // Unicode escape sequence tests (\\uXXXX)
    @Test
    public void testUnicodeEscapeBasic() {
        String json = "\"\\u0041\"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("A"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testUnicodeEscapeLowercase() {
        String json = "\"\\u0061\"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("a"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testUnicodeEscapeEuroSymbol() {
        String json = "\"\\u20AC\"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("€"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testUnicodeEscapeMultiple() {
        String json = "\"\\u0048\\u0065\\u006C\\u006C\\u006F\"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("Hello"));
        assertThat(parser.hasNext(), is(false));
    }

    // UTF-8 multibyte sequence tests
    @Test
    public void testUtf8TwoByteSequence() {
        // U+00A9 (copyright symbol) - 2 bytes in UTF-8: C2 A9
        String json = "\"©\"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("©"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testUtf8ThreeByteSequence() {
        // U+20AC (euro symbol) - 3 bytes in UTF-8: E2 82 AC
        String json = "\"€\"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("€"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testUtf8FourByteSequence() {
        // U+1F600 (grinning face emoji) - 4 bytes in UTF-8: F0 9F 98 80
        String json = "\"😀\"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("😀"));
        assertThat(parser.hasNext(), is(false));
    }

    // Surrogate pair tests
    @Test
    public void testSurrogatePairInJson() {
        // U+1F600 (grinning face) as surrogate pair in JSON: \uD83D\uDE00
        String json = "\"\\uD83D\\uDE00\"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("😀"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testMultipleSurrogatePairs() {
        // Multiple emojis with surrogate pairs
        String json = "\"\\uD83D\\uDE00\\uD83D\\uDE01\\uD83D\\uDE02\"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("😀😁😂"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testMixedAsciiAndSurrogates() {
        String json = "\"Hello \\uD83D\\uDE00 World\"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("Hello 😀 World"));
        assertThat(parser.hasNext(), is(false));
    }

    // Error case tests
    @Test
    public void testInvalidUnicodeEscape() {
        // Invalid hex digit 'G'
        String json = "\"\\u004G\"";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readString);
    }

    @Test
    public void testIncompleteUnicodeEscape() {
        String json = "\"\\u004\"";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readString);
    }

    @Test
    public void testInvalidUtf8Sequence() {
        // Invalid UTF-8: isolated continuation byte - create directly with bytes
        byte[] invalidUtf8 = new byte[] {'"', (byte) 0x80, '"'};
        ArrayJsonParser parser = new ArrayJsonParser(invalidUtf8);

        assertThrows(JsonException.class, parser::readString);
    }

    @Test
    public void testIncompleteUtf8Sequence() {
        // Incomplete 3-byte sequence: E2 82 (missing AC) - create directly with bytes
        byte[] incompleteUtf8 = new byte[] {'"', (byte) 0xE2, (byte) 0x82, '"'};
        ArrayJsonParser parser = new ArrayJsonParser(incompleteUtf8);

        assertThrows(JsonException.class, parser::readString);
    }

    @Test
    public void testUnterminatedString() {
        String json = "\"hello";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readString);
    }

    @Test
    public void testInvalidEscapeSequence() {
        String json = "\"\\z\"";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readString);
    }

    @Test
    public void testHighSurrogateWithoutLowSurrogate() {
        // High surrogate followed by regular character instead of low surrogate
        String json = "\"\\uD83DA\"";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readString);
    }

    @Test
    public void testLowSurrogateWithoutHighSurrogate() {
        // Low surrogate without preceding high surrogate
        String json = "\"\\uDE00\"";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readString);
    }

    // Edge case tests
    @Test
    public void testStringWithNullCharacter() {
        String json = "\"hello\\u0000world\"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("hello\u0000world"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testStringWithControlCharacters() {
        String json = "\"\\u0001\\u0002\\u001F\"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("\u0001\u0002\u001F"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testVeryLongString() {
        int count = 10000;
        String expected = "a".repeat(count);
        String json = "\"" + expected + "\"";

        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result.length(), is(count));
        assertThat(result, is(expected));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testStringWithEscapedQuotesAtBoundaries() {
        String json = "\"\\\"hello\\\"world\\\"\"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("\"hello\"world\""));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testComplexMixedString() {
        // Mix of ASCII, escaped chars, Unicode escapes, and UTF-8 multibyte chars
        String json = "\"Hello\\n\\u0041\\u4E2D\\u6587©\\uD83D\\uDE00\"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("Hello\nA中文©😀"));
        assertThat(parser.hasNext(), is(false));
    }

    // Test readChar method specifically (uses decodeUtf8ToChar)
    @Test
    public void testReadCharAscii() {
        String json = "\"A\"";
        JsonParser parser = createParser(json);
        char result = parser.readChar();

        assertEquals('A', result);
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testReadCharUnicodeEscape() {
        String json = "\"\\u0042\"";
        JsonParser parser = createParser(json);
        char result = parser.readChar();

        assertEquals('B', result);
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testReadCharUtf8TwoByte() {
        String json = "\"©\"";
        JsonParser parser = createParser(json);
        char result = parser.readChar();

        assertEquals('©', result);
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testReadCharUtf8ThreeByte() {
        String json = "\"€\"";
        JsonParser parser = createParser(json);
        char result = parser.readChar();

        assertEquals('€', result);
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testReadCharRejectsSurrogate() {
        // This should fail because surrogate pairs require 2 chars
        String json = "\"\\uD83D\\uDE00\"";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readChar);
    }

    // Additional corner cases
    @Test
    public void testStringWithAllWhitespace() {
        String json = "\"\\n\\t\\r \"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("\n\t\r "));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testStringWithOnlyEscapedCharacters() {
        String json = "\"\\n\\t\\r\\f\\b\\\\\\\"\"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("\n\t\r\f\b\\\""));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testStringWithEscapedNull() {
        String json = "\"\\u0000\"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("\u0000"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testStringWithMaximumUnicodeCodePoint() {
        // U+10FFFF - maximum valid Unicode code point
        String json = "\"\\uDBFF\\uDFFF\"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("\uDBFF\uDFFF"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testStringWithMixedSurrogatesAndRegular() {
        // Mix of surrogate pairs and regular characters
        String json = "\"A\\uD83D\\uDE00B\\uD83D\\uDE01C\"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("A😀B😁C"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testStringWithConsecutiveEscapedSequences() {
        String json = "\"\\\\\\n\\\\\\t\\\\\\\"\"";
        JsonParser parser = createParser(json);
        String result = parser.readString();

        assertThat(result, is("\\\n\\\t\\\""));
        assertThat(parser.hasNext(), is(false));
    }

    abstract JsonParser createParser(String template);

}
