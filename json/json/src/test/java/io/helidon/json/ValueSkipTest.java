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
 * Tests for ArrayJsonParser value skipping functionality.
 * Covers skipping all JSON value types: objects, arrays, strings, numbers, booleans, and null.
 */
class ValueSkipTest {

    // Basic value skipping tests
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testSkipString(ParserMethod parserMethod) {
        String json = "\"hello world\" followed content";
        JsonParser parser = parserMethod.createParser(json);
        parser.skip();

        // Parser should be positioned at the end of the string (closing quote)
        assertThat(parser.currentByte(), is((byte) '"'));
        assertThat(parser.hasNext(), is(true));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testSkipEmptyString(ParserMethod parserMethod) {
        String json = "\"\" followed content";
        JsonParser parser = parserMethod.createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) '"'));
        assertThat(parser.hasNext(), is(true));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testSkipStringWithEscapes(ParserMethod parserMethod) {
        String json = "\"hello \\\"world\\\"\" followed content";
        JsonParser parser = parserMethod.createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) '"'));
        assertThat(parser.hasNext(), is(true));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testSkipStringWithUnicode(ParserMethod parserMethod) {
        String json = "\"hello \\u0041 world\" followed content";
        JsonParser parser = parserMethod.createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) '"'));
        assertThat(parser.hasNext(), is(true));
    }

    // Number skipping tests
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testSkipInteger(ParserMethod parserMethod) {
        String json = "123 followed content";
        JsonParser parser = parserMethod.createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) '3'));
        assertThat(parser.hasNext(), is(true));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testSkipNegativeInteger(ParserMethod parserMethod) {
        String json = "-456 followed content";
        JsonParser parser = parserMethod.createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) '6'));
        assertThat(parser.hasNext(), is(true));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testSkipFloat(ParserMethod parserMethod) {
        String json = "123.456 followed content";
        JsonParser parser = parserMethod.createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) '6'));
        assertThat(parser.hasNext(), is(true));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testSkipScientificNotation(ParserMethod parserMethod) {
        String json = "1.23e10 followed content";
        JsonParser parser = parserMethod.createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) '0'));
        assertThat(parser.hasNext(), is(true));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testSkipLargeNumber(ParserMethod parserMethod) {
        String json = "999999999999999999 followed content";
        JsonParser parser = parserMethod.createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) '9'));
        assertThat(parser.hasNext(), is(true));
    }

    // Boolean skipping tests
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testSkipBooleanTrue(ParserMethod parserMethod) {
        String json = "true followed content";
        JsonParser parser = parserMethod.createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) 'e'));
        assertThat(parser.hasNext(), is(true));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testSkipBooleanFalse(ParserMethod parserMethod) {
        String json = "false followed content";
        JsonParser parser = parserMethod.createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) 'e'));
        assertThat(parser.hasNext(), is(true));
    }

    // Null skipping tests
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testSkipNull(ParserMethod parserMethod) {
        String json = "null followed content";
        JsonParser parser = parserMethod.createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) 'l'));
        assertThat(parser.hasNext(), is(true));
    }

    // Object skipping tests
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testSkipEmptyObject(ParserMethod parserMethod) {
        String json = "{} followed content";
        JsonParser parser = parserMethod.createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) '}'));
        assertThat(parser.hasNext(), is(true));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testSkipSimpleObject(ParserMethod parserMethod) {
        String json = "{\"key\": \"value\"} followed content";
        JsonParser parser = parserMethod.createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) '}'));
        assertThat(parser.hasNext(), is(true));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testSkipNestedObject(ParserMethod parserMethod) {
        String json = "{\"outer\": {\"inner\": \"value\"}} followed content";
        JsonParser parser = parserMethod.createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) '}'));
        assertThat(parser.hasNext(), is(true));
        assertThat(parser.nextToken(), is((byte) 'f'));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testSkipComplexObject(ParserMethod parserMethod) {
        String json = "{\"string\": \"hello\", \"number\": 123, \"boolean\": true, \"null\": null, \"array\": [1, 2, 3]} "
                + "followed content";
        JsonParser parser = parserMethod.createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) '}'));
        assertThat(parser.hasNext(), is(true));
        assertThat(parser.nextToken(), is((byte) 'f'));
    }

    // Array skipping tests
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testSkipEmptyArray(ParserMethod parserMethod) {
        String json = "[] followed content";
        JsonParser parser = parserMethod.createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) ']'));
        assertThat(parser.hasNext(), is(true));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testSkipSimpleArray(ParserMethod parserMethod) {
        String json = "[1, 2, 3] followed content";
        JsonParser parser = parserMethod.createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) ']'));
        assertThat(parser.hasNext(), is(true));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testSkipNestedArray(ParserMethod parserMethod) {
        String json = "[[1, 2], [3, 4]] followed content";
        JsonParser parser = parserMethod.createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) ']'));
        assertThat(parser.hasNext(), is(true));
        assertThat(parser.nextToken(), is((byte) 'f'));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testSkipMixedArray(ParserMethod parserMethod) {
        String json = "[\"string\", 123, true, null, {\"key\": \"value\"}] followed content";
        JsonParser parser = parserMethod.createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) ']'));
        assertThat(parser.hasNext(), is(true));
        assertThat(parser.nextToken(), is((byte) 'f'));
    }

    // Complex nested structures
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testSkipComplexNestedStructure(ParserMethod parserMethod) {
        String json = "{\"data\": {\"items\": [{\"id\": 1, \"values\": [10, 20, {\"nested\": {\"deep\": \"value\"}}]}, {\"id\":"
                + " 2}]}} followed content";
        JsonParser parser = parserMethod.createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) '}'));
        assertThat(parser.hasNext(), is(true));
        assertThat(parser.nextToken(), is((byte) 'f'));
    }

    // Multiple consecutive skips
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testMultipleSkips(ParserMethod parserMethod) {
        String json = "\"string\" 123 true null {} [] followed content";
        JsonParser parser = parserMethod.createParser(json);

        // Skip string
        parser.skip();
        assertThat(parser.currentByte(), is((byte) '"'));

        // Skip number
        parser.nextToken(); // advance to number
        parser.skip();
        assertThat(parser.currentByte(), is((byte) '3'));

        // Skip boolean
        parser.nextToken(); // advance to boolean
        parser.skip();
        assertThat(parser.currentByte(), is((byte) 'e'));

        // Skip null
        parser.nextToken(); // advance to null
        parser.skip();
        assertThat(parser.currentByte(), is((byte) 'l'));

        // Skip object
        parser.nextToken(); // advance to object
        parser.skip();
        assertThat(parser.currentByte(), is((byte) '}'));

        // Skip array
        parser.nextToken(); // advance to array
        parser.skip();
        assertThat(parser.currentByte(), is((byte) ']'));

        assertThat(parser.hasNext(), is(true));
        assertThat(parser.nextToken(), is((byte) 'f'));
    }

    // Error cases
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testSkipInvalidValue(ParserMethod parserMethod) {
        String json = "invalid";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::skip);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testSkipIncompleteString(ParserMethod parserMethod) {
        String json = "\"incomplete";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::skip);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testSkipIncompleteObject(ParserMethod parserMethod) {
        String json = "{\"incomplete\": ";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::skip);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testSkipIncompleteArray(ParserMethod parserMethod) {
        String json = "[1, 2";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::skip);
    }

    // Edge cases with whitespace
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testSkipWithWhitespace(ParserMethod parserMethod) {
        String json = "  \t\n  \"string\"  \t\n  followed content";
        JsonParser parser = parserMethod.createParser(json);

        // Skip whitespace to value
        parser.nextToken();
        parser.skip();

        assertThat(parser.currentByte(), is((byte) '"'));
        assertThat(parser.hasNext(), is(true));
        assertThat(parser.nextToken(), is((byte) 'f'));
    }

    // Test skip at end of input
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testSkipAtEndOfInput(ParserMethod parserMethod) {
        String json = "\"last value\"";
        JsonParser parser = parserMethod.createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) '"'));
        assertThat(parser.hasNext(), is(false));
    }

}
