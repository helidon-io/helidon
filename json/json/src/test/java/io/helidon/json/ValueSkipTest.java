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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for ArrayJsonParser value skipping functionality.
 * Covers skipping all JSON value types: objects, arrays, strings, numbers, booleans, and null.
 */
abstract class ValueSkipTest {

    // Basic value skipping tests
    @Test
    public void testSkipString() {
        String json = "\"hello world\" followed content";
        JsonParser parser = createParser(json);
        parser.skip();

        // Parser should be positioned at the end of the string (closing quote)
        assertThat(parser.currentByte(), is((byte) '"'));
        assertThat(parser.hasNext(), is(true));
    }

    @Test
    public void testSkipEmptyString() {
        String json = "\"\" followed content";
        JsonParser parser = createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) '"'));
        assertThat(parser.hasNext(), is(true));
    }

    @Test
    public void testSkipStringWithEscapes() {
        String json = "\"hello \\\"world\\\"\" followed content";
        JsonParser parser = createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) '"'));
        assertThat(parser.hasNext(), is(true));
    }

    @Test
    public void testSkipStringWithUnicode() {
        String json = "\"hello \\u0041 world\" followed content";
        JsonParser parser = createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) '"'));
        assertThat(parser.hasNext(), is(true));
    }

    // Number skipping tests
    @Test
    public void testSkipInteger() {
        String json = "123 followed content";
        JsonParser parser = createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) '3'));
        assertThat(parser.hasNext(), is(true));
    }

    @Test
    public void testSkipNegativeInteger() {
        String json = "-456 followed content";
        JsonParser parser = createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) '6'));
        assertThat(parser.hasNext(), is(true));
    }

    @Test
    public void testSkipFloat() {
        String json = "123.456 followed content";
        JsonParser parser = createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) '6'));
        assertThat(parser.hasNext(), is(true));
    }

    @Test
    public void testSkipScientificNotation() {
        String json = "1.23e10 followed content";
        JsonParser parser = createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) '0'));
        assertThat(parser.hasNext(), is(true));
    }

    @Test
    public void testSkipLargeNumber() {
        String json = "999999999999999999 followed content";
        JsonParser parser = createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) '9'));
        assertThat(parser.hasNext(), is(true));
    }

    // Boolean skipping tests
    @Test
    public void testSkipBooleanTrue() {
        String json = "true followed content";
        JsonParser parser = createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) 'e'));
        assertThat(parser.hasNext(), is(true));
    }

    @Test
    public void testSkipBooleanFalse() {
        String json = "false followed content";
        JsonParser parser = createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) 'e'));
        assertThat(parser.hasNext(), is(true));
    }

    // Null skipping tests
    @Test
    public void testSkipNull() {
        String json = "null followed content";
        JsonParser parser = createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) 'l'));
        assertThat(parser.hasNext(), is(true));
    }

    // Object skipping tests
    @Test
    public void testSkipEmptyObject() {
        String json = "{} followed content";
        JsonParser parser = createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) '}'));
        assertThat(parser.hasNext(), is(true));
    }

    @Test
    public void testSkipSimpleObject() {
        String json = "{\"key\": \"value\"} followed content";
        JsonParser parser = createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) '}'));
        assertThat(parser.hasNext(), is(true));
    }

    @Test
    public void testSkipNestedObject() {
        String json = "{\"outer\": {\"inner\": \"value\"}} followed content";
        JsonParser parser = createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) '}'));
        assertThat(parser.hasNext(), is(true));
        assertThat(parser.nextToken(), is((byte) 'f'));
    }

    @Test
    public void testSkipComplexObject() {
        String json = "{\"string\": \"hello\", \"number\": 123, \"boolean\": true, \"null\": null, \"array\": [1, 2, 3]} "
                + "followed content";
        JsonParser parser = createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) '}'));
        assertThat(parser.hasNext(), is(true));
        assertThat(parser.nextToken(), is((byte) 'f'));
    }

    // Array skipping tests
    @Test
    public void testSkipEmptyArray() {
        String json = "[] followed content";
        JsonParser parser = createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) ']'));
        assertThat(parser.hasNext(), is(true));
    }

    @Test
    public void testSkipSimpleArray() {
        String json = "[1, 2, 3] followed content";
        JsonParser parser = createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) ']'));
        assertThat(parser.hasNext(), is(true));
    }

    @Test
    public void testSkipNestedArray() {
        String json = "[[1, 2], [3, 4]] followed content";
        JsonParser parser = createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) ']'));
        assertThat(parser.hasNext(), is(true));
        assertThat(parser.nextToken(), is((byte) 'f'));
    }

    @Test
    public void testSkipMixedArray() {
        String json = "[\"string\", 123, true, null, {\"key\": \"value\"}] followed content";
        JsonParser parser = createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) ']'));
        assertThat(parser.hasNext(), is(true));
        assertThat(parser.nextToken(), is((byte) 'f'));
    }

    // Complex nested structures
    @Test
    public void testSkipComplexNestedStructure() {
        String json = "{\"data\": {\"items\": [{\"id\": 1, \"values\": [10, 20, {\"nested\": {\"deep\": \"value\"}}]}, {\"id\":"
                + " 2}]}} followed content";
        JsonParser parser = createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) '}'));
        assertThat(parser.hasNext(), is(true));
        assertThat(parser.nextToken(), is((byte) 'f'));
    }

    // Multiple consecutive skips
    @Test
    public void testMultipleSkips() {
        String json = "\"string\" 123 true null {} [] followed content";
        JsonParser parser = createParser(json);

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
    @Test
    public void testSkipInvalidValue() {
        String json = "invalid";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::skip);
    }

    @Test
    public void testSkipIncompleteString() {
        String json = "\"incomplete";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::skip);
    }

    @Test
    public void testSkipIncompleteObject() {
        String json = "{\"incomplete\": ";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::skip);
    }

    @Test
    public void testSkipIncompleteArray() {
        String json = "[1, 2";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::skip);
    }

    // Edge cases with whitespace
    @Test
    public void testSkipWithWhitespace() {
        String json = "  \t\n  \"string\"  \t\n  followed content";
        JsonParser parser = createParser(json);

        // Skip whitespace to value
        parser.nextToken();
        parser.skip();

        assertThat(parser.currentByte(), is((byte) '"'));
        assertThat(parser.hasNext(), is(true));
        assertThat(parser.nextToken(), is((byte) 'f'));
    }

    // Test skip at end of input
    @Test
    public void testSkipAtEndOfInput() {
        String json = "\"last value\"";
        JsonParser parser = createParser(json);
        parser.skip();

        assertThat(parser.currentByte(), is((byte) '"'));
        assertThat(parser.hasNext(), is(false));
    }

    abstract JsonParser createParser(String template);

}
