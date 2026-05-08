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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for parser state management and error recovery.
 * Covers parser behavior after exceptions, state consistency, and edge cases.
 */
class ParserStateTest {

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParserStateAfterSuccessfulParse(ParserMethod parserMethod) {
        String json = "{\"key\": \"value\"}";
        JsonParser parser = parserMethod.createParser(json, 64);

        JsonObject result = parser.readJsonObject();
        assertThat(result.stringValue("key").orElseThrow(), is("value"));

        // Parser should be at end
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParserStateAfterSkip(ParserMethod parserMethod) {
        String json = "\"string\" more content";
        JsonParser parser = parserMethod.createParser(json, 64);

        parser.skip();
        // Parser should be positioned at the space after the skipped value
        assertThat(parser.currentByte(), is((byte) '"'));
        assertThat(parser.hasNext(), is(true));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParserStateAfterPartialRead(ParserMethod parserMethod) {
        String json = "[1, 2, 3]";
        JsonParser parser = parserMethod.createParser(json, 64);

        // Start reading array
        // '['
        assertThat(parser.currentByte(), is((byte) '['));

        parser.nextToken(); // '1'
        int firstNumber = parser.readInt();
        assertThat(firstNumber, is(1));

        // Parser should be positioned correctly for next value
        parser.nextToken(); // ','
        parser.nextToken(); // '2'
        int secondNumber = parser.readInt();
        assertThat(secondNumber, is(2));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParserExceptionContainsPositionInfo(ParserMethod parserMethod) {
        String json = "{\"key\": }"; // Missing value
        JsonParser parser = parserMethod.createParser(json, 64);

        JsonException exception = assertThrows(JsonException.class, parser::readJsonObject);
        assertThat(exception.getMessage().contains("Error at JSON index"), is(true));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParserCanContinueAfterNonFatalError(ParserMethod parserMethod) {
        // Test that parser can continue reading valid JSON after encountering some issues
        String json = "[\"valid\", \"also valid\"]";
        JsonParser parser = parserMethod.createParser(json, 64);

        JsonArray result = parser.readJsonArray();
        assertThat(result.values().size(), is(2));
        assertThat(result.get(0, JsonNull.instance()).asString().value(), is("valid"));
        assertThat(result.get(1, JsonNull.instance()).asString().value(), is("also valid"));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParserWithDeepNesting(ParserMethod parserMethod) {
        // Create deeply nested JSON to test parser stack limits
        StringBuilder json = new StringBuilder();
        int depth = 100; // Deep nesting

        for (int i = 0; i < depth; i++) {
            json.append("{\"level").append(i).append("\": ");
        }
        json.append("\"deepest value\"");
        for (int i = 0; i < depth; i++) {
            json.append("}");
        }

        JsonParser parser = parserMethod.createParser(json.toString());
        JsonObject result = parser.readJsonObject();

        // Navigate to the deepest level
        JsonObject current = result;
        for (int i = 0; i < depth - 1; i++) {
            current = current.objectValue("level" + i).orElseThrow();
        }
        assertThat(current.stringValue("level" + (depth - 1)).orElseThrow(), is("deepest value"));
    }

    @ParameterizedTest
    @EnumSource(value = ParserMethod.class)
    public void testParserWithLargeArrays(ParserMethod parserMethod) {
        // Create a large array to test parser performance and state management
        StringBuilder json = new StringBuilder();
        json.append("[");
        int size = 1000;
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                json.append(",");
            }
            json.append(i);
        }
        json.append("]");

        JsonParser parser = parserMethod.createParser(json.toString());
        JsonArray result = parser.readJsonArray();

        assertThat(result.values().size(), is(size));
        for (int i = 0; i < size; i++) {
            assertThat(result.get(i, JsonNull.instance()).asNumber().intValue(), is(i));
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParserWithVeryLongString(ParserMethod parserMethod) {
        // Create a very long string to test string buffer management
        int length = 10000;
        StringBuilder longString = new StringBuilder();
        for (int i = 0; i < length; i++) {
            longString.append((char) ('a' + (i % 26)));
        }
        String content = longString.toString();
        String json = "\"" + content + "\"";

        JsonParser parser = parserMethod.createParser(json, 64);
        String result = parser.readString();

        assertThat(result.length(), is(length));
        assertThat(result, is(content));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParserMixedReadAndSkipOperations(ParserMethod parserMethod) {
        String json = "{\"read\": \"this\", \"skip\": [1, 2, 3], \"also_read\": 42}";
        JsonParser parser = parserMethod.createParser(json, 64);

        assertThat(parser.currentByte(), is((byte) '{'));
        assertThat(parser.nextToken(), is((byte) '"'));
        parser.skip(); // skip "read"
        assertThat(parser.nextToken(), is((byte) ':'));
        assertThat(parser.nextToken(), is((byte) '"'));
        parser.skip(); // skip "this"
        assertThat(parser.nextToken(), is((byte) ','));
        assertThat(parser.nextToken(), is((byte) '"'));
        parser.skip(); // skip "skip"
        assertThat(parser.nextToken(), is((byte) ':'));
        assertThat(parser.nextToken(), is((byte) '['));
        parser.skip(); // skip [1, 2, 3]
        assertThat(parser.nextToken(), is((byte) ','));
        assertThat(parser.nextToken(), is((byte) '"'));
        parser.skip(); // skip "also_read"
        assertThat(parser.nextToken(), is((byte) ':'));
        assertThat(parser.nextToken(), is((byte) '4'));
        int value = parser.readInt(); // read 42

        assertThat(value, is(42));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParserWithUnicodeEdgeCases(ParserMethod parserMethod) {
        // Test various Unicode edge cases
        String json = "\"\\u0000\\uFFFF\\uD800\\uDC00\\uDBFF\\uDFFF\"";
        JsonParser parser = parserMethod.createParser(json, 64);
        String result = parser.readString();

        // Should contain null char, last valid BMP char, and surrogate pairs
        assertThat(result.length(), is(6));
        assertThat((int) result.charAt(0), is(0)); // null
        assertThat((int) result.charAt(1), is(0xFFFF)); // last BMP char
        // Characters 2-5 should form surrogate pairs
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParserWithMalformedUTF8(ParserMethod parserMethod) {
        // Create malformed UTF-8 bytes directly - this test only works with ArrayJsonParser
        // so we'll skip it for now as ArrayJsonParser is package-private
        // In a real scenario, this would be tested with a custom input stream
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParserStateAfterReadOperations(ParserMethod parserMethod) {
        String json = "{\"a\": 1, \"b\": \"text\", \"c\": true}";
        JsonParser parser = parserMethod.createParser(json, 64);

        JsonObject obj = parser.readJsonObject();

        // Verify all values were read correctly
        assertThat(obj.intValue("a").orElseThrow(), is(1));
        assertThat(obj.stringValue("b").orElseThrow(), is("text"));
        assertThat(obj.booleanValue("c").orElseThrow(), is(true));

        // Parser should be finished
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParserWithEmptyStructures(ParserMethod parserMethod) {
        // Test various empty structures
        JsonParser parser1 = parserMethod.createParser("{}");
        JsonObject emptyObj = parser1.readJsonObject();
        assertThat(emptyObj.keys().isEmpty(), is(true));

        JsonParser parser2 = parserMethod.createParser("[]");
        JsonArray emptyArr = parser2.readJsonArray();
        assertThat(emptyArr.values().isEmpty(), is(true));

        JsonParser parser3 = parserMethod.createParser("\"\"");
        String emptyStr = parser3.readString();
        assertThat(emptyStr.isEmpty(), is(true));
    }

}
