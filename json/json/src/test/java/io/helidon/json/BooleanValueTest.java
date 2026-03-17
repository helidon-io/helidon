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
 * Tests for ArrayJsonParser boolean parsing functionality.
 * Covers true/false values, case sensitivity, and edge cases.
 */
class BooleanValueTest {

    // Basic boolean parsing tests
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseBooleanTrue(ParserMethod parserMethod) {
        String json = "true";
        JsonParser parser = parserMethod.createParser(json);
        boolean result = parser.readBoolean();

        assertThat(result, is(true));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseBooleanFalse(ParserMethod parserMethod) {
        String json = "false";
        JsonParser parser = parserMethod.createParser(json);
        boolean result = parser.readBoolean();

        assertThat(result, is(false));
        assertThat(parser.hasNext(), is(false));
    }

    // Case sensitivity tests (JSON booleans are case-sensitive)
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseBooleanTrueCaseSensitive(ParserMethod parserMethod) {
        String json = "True";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseBooleanFalseCaseSensitive(ParserMethod parserMethod) {
        String json = "False";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseBooleanTrueUppercase(ParserMethod parserMethod) {
        String json = "TRUE";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseBooleanFalseUppercase(ParserMethod parserMethod) {
        String json = "FALSE";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseBooleanTrueMixedCase(ParserMethod parserMethod) {
        String json = "tRuE";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseBooleanFalseMixedCase(ParserMethod parserMethod) {
        String json = "fAlSe";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    // Invalid boolean formats
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseBooleanInvalidFormat(ParserMethod parserMethod) {
        String json = "yes";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseBooleanNumericOne(ParserMethod parserMethod) {
        String json = "1";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseBooleanNumericZero(ParserMethod parserMethod) {
        String json = "0";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseBooleanEmptyString(ParserMethod parserMethod) {
        String json = "\"\"";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseBooleanStringTrue(ParserMethod parserMethod) {
        String json = "\"true\"";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseBooleanStringFalse(ParserMethod parserMethod) {
        String json = "\"false\"";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseBooleanNull(ParserMethod parserMethod) {
        String json = "null";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseBooleanEmptyObject(ParserMethod parserMethod) {
        String json = "{}";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseBooleanEmptyArray(ParserMethod parserMethod) {
        String json = "[]";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    // Partial matches that should fail
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseBooleanTruePartialMatch(ParserMethod parserMethod) {
        String json = "tru";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseBooleanFalsePartialMatch(ParserMethod parserMethod) {
        String json = "fals";
        JsonParser parser = parserMethod.createParser(json);

        assertThrows(JsonException.class, parser::readBoolean);
    }

}
