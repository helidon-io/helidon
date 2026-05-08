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

import java.util.function.Consumer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Strict RFC 8259 parser conformance tests for JSON texts, literals, objects, and arrays.
 */
class Rfc8259TextAndStructureTest {

    /**
     * RFC 8259 §2
     * Quote: "A JSON text is a serialized value."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-2
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testAcceptsAnySerializedValueAsJsonText(ParserMethod parserMethod) {
        assertAll(
                () -> runWholeTextScenario(parserMethod, "{\"key\":1}", parser -> {
                    assertThat(parser.readJsonValue().type(), is(JsonValueType.OBJECT));
                }, false),
                () -> runWholeTextScenario(parserMethod, "[1,true,null]", parser -> {
                    assertThat(parser.readJsonValue().type(), is(JsonValueType.ARRAY));
                }, false),
                () -> runWholeTextScenario(parserMethod, "\"text\"", parser -> {
                    assertThat(parser.readJsonValue().type(), is(JsonValueType.STRING));
                }, false),
                () -> runWholeTextScenario(parserMethod, "42", parser -> {
                    assertThat(parser.readJsonValue().type(), is(JsonValueType.NUMBER));
                }, false),
                () -> runWholeTextScenario(parserMethod, "true", parser -> {
                    assertThat(parser.readJsonValue().type(), is(JsonValueType.BOOLEAN));
                }, false),
                () -> runWholeTextScenario(parserMethod, "false", parser -> {
                    assertThat(parser.readJsonValue().type(), is(JsonValueType.BOOLEAN));
                }, false),
                () -> runWholeTextScenario(parserMethod, "null", parser -> {
                    assertThat(parser.readJsonValue().type(), is(JsonValueType.NULL));
                }, false)
        );
    }

    /**
     * RFC 8259 §2
     * Quote: "JSON-text = ws value ws"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-2
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testAcceptsLeadingAndTrailingWhitespaceAroundJsonText(ParserMethod parserMethod) {
        runWholeTextScenario(parserMethod,
                             " \t\r\n { \"value\" : [ true , false , null ] } \r\n ",
                             parser -> {
                                 JsonObject jsonObject = parser.readJsonValue().asObject();
                                 assertThat(jsonObject.containsKey("value"), is(true));
                                 assertThat(jsonObject.arrayValue("value").orElseThrow().values().size(), is(3));
                             },
                             false);
    }

    /**
     * RFC 8259 §2
     * Quote: "JSON-text = ws value ws"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-2
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testRejectsEmptyAndWhitespaceOnlyJsonText(ParserMethod parserMethod) {
        assertAll(
                () -> runWholeTextScenario(parserMethod, "", JsonParser::readJsonValue, true),
                () -> runWholeTextScenario(parserMethod, " \t\r\n ", JsonParser::readJsonValue, true)
        );
    }

    /**
     * RFC 8259 §2
     * Quote: "ws = *( %x20 / %x09 / %x0A / %x0D )"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-2
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testRejectsNonRfcWhitespaceBetweenTokens(ParserMethod parserMethod) {
        assertAll(
                () -> runWholeTextScenario(parserMethod, "[1," + '\u000B' + "2]", JsonParser::readJsonValue, true),
                () -> runWholeTextScenario(parserMethod,
                                           "{\"key\"" + '\u000C' + ":" + '\u000B' + "1}",
                                           JsonParser::readJsonValue,
                                           true)
        );
    }

    /**
     * RFC 8259 §3
     * Quote: "The literal names MUST be lowercase.  No other literal names are allowed."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-3
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testRejectsUppercaseLiteralNames(ParserMethod parserMethod) {
        assertAll(
                () -> runWholeTextScenario(parserMethod, "TRUE", JsonParser::readJsonValue, true),
                () -> runWholeTextScenario(parserMethod, "FALSE", JsonParser::readJsonValue, true),
                () -> runWholeTextScenario(parserMethod, "NULL", JsonParser::readJsonValue, true)
        );
    }

    /**
     * RFC 8259 §3
     * Quote: "The literal names MUST be lowercase.  No other literal names are allowed."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-3
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testRejectsPartialAndMixedCaseNullLiteralNames(ParserMethod parserMethod) {
        assertAll(
                () -> runWholeTextScenario(parserMethod, "Null", JsonParser::readJsonValue, true),
                () -> runWholeTextScenario(parserMethod, "nul", JsonParser::readJsonValue, true),
                () -> runWholeTextScenario(parserMethod, "nu", JsonParser::readJsonValue, true),
                () -> runWholeTextScenario(parserMethod, "n", JsonParser::readJsonValue, true)
        );
    }

    /**
     * RFC 8259 §2
     * Quote: "A JSON text is a serialized value."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-2
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testRejectsMultipleSerializedValues(ParserMethod parserMethod) {
        assertAll(
                () -> runWholeTextScenario(parserMethod, "true false", JsonParser::readJsonValue, true),
                () -> runWholeTextScenario(parserMethod, "{\"a\":1} [2]", JsonParser::readJsonValue, true),
                () -> runWholeTextScenario(parserMethod, "null 0", JsonParser::readJsonValue, true)
        );
    }

    /**
     * RFC 8259 §4
     * Quote: "A name is a string."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-4
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testRejectsNonStringObjectNames(ParserMethod parserMethod) {
        assertAll(
                () -> runWholeTextScenario(parserMethod, "{key:1}", JsonParser::readJsonValue, true),
                () -> runWholeTextScenario(parserMethod, "{1:2}", JsonParser::readJsonValue, true),
                () -> runWholeTextScenario(parserMethod, "{true:false}", JsonParser::readJsonValue, true)
        );
    }

    /**
     * RFC 8259 §4
     * Quote: "A single colon comes after each name, separating the name from the value."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-4
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testRejectsObjectsWithoutColonSeparator(ParserMethod parserMethod) {
        assertAll(
                () -> runWholeTextScenario(parserMethod, "{\"key\" 1}", JsonParser::readJsonValue, true),
                () -> runWholeTextScenario(parserMethod, "{\"key\",1}", JsonParser::readJsonValue, true)
        );
    }

    /**
     * RFC 8259 §4
     * Quote: "A single comma separates a value from a following name."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-4
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testRejectsObjectsWithoutCommaSeparator(ParserMethod parserMethod) {
        assertAll(
                () -> runWholeTextScenario(parserMethod, "{\"a\":1 \"b\":2}", JsonParser::readJsonValue, true),
                () -> runWholeTextScenario(parserMethod, "{\"a\":1\"b\":2}", JsonParser::readJsonValue, true)
        );
    }

    /**
     * RFC 8259 §4
     * Quote: "object = begin-object [ member *( value-separator member ) ] end-object"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-4
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testRejectsTrailingCommaInObject(ParserMethod parserMethod) {
        assertAll(
                () -> runWholeTextScenario(parserMethod, "{\"a\":1,}", JsonParser::readJsonValue, true),
                () -> runWholeTextScenario(parserMethod, "{\"a\":1,\"b\":2,}", JsonParser::readJsonValue, true)
        );
    }

    /**
     * RFC 8259 §4
     * Quote: "object = begin-object [ member *( value-separator member ) ] end-object"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-4
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testRejectsObjectsWithLeadingCommaOrMissingMember(ParserMethod parserMethod) {
        assertAll(
                () -> runWholeTextScenario(parserMethod, "{,\"a\":1}", JsonParser::readJsonValue, true),
                () -> runWholeTextScenario(parserMethod, "{\"a\":1,,\"b\":2}", JsonParser::readJsonValue, true)
        );
    }

    /**
     * RFC 8259 §5
     * Quote: "Elements are separated by commas."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-5
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testRejectsArraysWithoutCommaSeparator(ParserMethod parserMethod) {
        assertAll(
                () -> runWholeTextScenario(parserMethod, "[1 2]", JsonParser::readJsonValue, true),
                () -> runWholeTextScenario(parserMethod, "[true null]", JsonParser::readJsonValue, true)
        );
    }

    /**
     * RFC 8259 §5
     * Quote: "array = begin-array [ value *( value-separator value ) ] end-array"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-5
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testRejectsTrailingCommaInArray(ParserMethod parserMethod) {
        assertAll(
                () -> runWholeTextScenario(parserMethod, "[1,]", JsonParser::readJsonValue, true),
                () -> runWholeTextScenario(parserMethod, "[1,2,]", JsonParser::readJsonValue, true)
        );
    }

    /**
     * RFC 8259 §5
     * Quote: "array = begin-array [ value *( value-separator value ) ] end-array"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-5
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testRejectsArraysWithLeadingCommaOrMissingElement(ParserMethod parserMethod) {
        assertAll(
                () -> runWholeTextScenario(parserMethod, "[,1]", JsonParser::readJsonValue, true),
                () -> runWholeTextScenario(parserMethod, "[1,,2]", JsonParser::readJsonValue, true)
        );
    }

    private static void runWholeTextScenario(ParserMethod parserMethod,
                                             String json,
                                             Consumer<JsonParser> parserAssertions,
                                             boolean expectRejection) {
        try {
            JsonParser parser = parserMethod.createParser(json);
            parserAssertions.accept(parser);
            if (!parser.hasNext()) {
                if (!expectRejection) {
                    return;
                }
                fail("Expected invalid JSON text to be rejected: " + json);
            }
            try {
                byte token = parser.nextToken();
                if (expectRejection) {
                    return;
                }
                fail("Unexpected trailing token: " + Parsers.toPrintableForm(token));
            } catch (JsonException expectedTrailingWhitespaceOnly) {
                if (!expectRejection) {
                    return;
                }
                fail("Expected invalid JSON text to be rejected: " + json);
            }
        } catch (JsonException expected) {
            if (!expectRejection) {
                throw expected;
            }
        }
    }
}
