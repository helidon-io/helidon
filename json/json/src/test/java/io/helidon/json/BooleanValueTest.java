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
 * Tests for ArrayJsonParser boolean parsing functionality.
 * Covers true/false values, case sensitivity, and edge cases.
 */
abstract class BooleanValueTest {

    // Basic boolean parsing tests
    @Test
    public void testParseBooleanTrue() {
        String json = "true";
        JsonParser parser = createParser(json);
        boolean result = parser.readBoolean();

        assertThat(result, is(true));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseBooleanFalse() {
        String json = "false";
        JsonParser parser = createParser(json);
        boolean result = parser.readBoolean();

        assertThat(result, is(false));
        assertThat(parser.hasNext(), is(false));
    }

    // Case sensitivity tests (JSON booleans are case-sensitive)
    @Test
    public void testParseBooleanTrueCaseSensitive() {
        String json = "True";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    @Test
    public void testParseBooleanFalseCaseSensitive() {
        String json = "False";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    @Test
    public void testParseBooleanTrueUppercase() {
        String json = "TRUE";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    @Test
    public void testParseBooleanFalseUppercase() {
        String json = "FALSE";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    @Test
    public void testParseBooleanTrueMixedCase() {
        String json = "tRuE";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    @Test
    public void testParseBooleanFalseMixedCase() {
        String json = "fAlSe";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    // Invalid boolean formats
    @Test
    public void testParseBooleanInvalidFormat() {
        String json = "yes";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    @Test
    public void testParseBooleanNumericOne() {
        String json = "1";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    @Test
    public void testParseBooleanNumericZero() {
        String json = "0";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    @Test
    public void testParseBooleanEmptyString() {
        String json = "\"\"";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    @Test
    public void testParseBooleanStringTrue() {
        String json = "\"true\"";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    @Test
    public void testParseBooleanStringFalse() {
        String json = "\"false\"";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    @Test
    public void testParseBooleanNull() {
        String json = "null";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    @Test
    public void testParseBooleanEmptyObject() {
        String json = "{}";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    @Test
    public void testParseBooleanEmptyArray() {
        String json = "[]";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    // Partial matches that should fail
    @Test
    public void testParseBooleanTruePartialMatch() {
        String json = "tru";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    @Test
    public void testParseBooleanFalsePartialMatch() {
        String json = "fals";
        JsonParser parser = createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    // Test readCharArray for booleans
    @Test
    public void testReadCharArrayBooleanTrue() {
        String json = "true";
        JsonParser parser = createParser(json);
        char[] result = parser.readCharArray();

        assertThat(result, is("true".toCharArray()));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testReadCharArrayBooleanFalse() {
        String json = "false";
        JsonParser parser = createParser(json);
        char[] result = parser.readCharArray();

        assertThat(result, is("false".toCharArray()));
        assertThat(parser.hasNext(), is(false));
    }

    abstract JsonParser createParser(String template);

}
