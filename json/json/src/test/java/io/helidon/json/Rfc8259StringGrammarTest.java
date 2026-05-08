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
 * Strict RFC 8259 parser conformance tests for JSON string grammar.
 */
class Rfc8259StringGrammarTest {

    /**
     * RFC 8259 §7
     * Quote: "Any character may be escaped."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-7
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testAcceptsMandatoryEscapeForms(ParserMethod parserMethod) {
        assertAll(
                () -> runWholeTextScenario(parserMethod, "\"\\\"\\\\\\/\\b\\f\\n\\r\\t\"", parser -> {
                    assertThat(parser.readString(), is("\"\\/\b\f\n\r\t"));
                }, false),
                () -> runWholeTextScenario(parserMethod, "\"\\u0041\\u0042\\u0043\"", parser -> {
                    assertThat(parser.readString(), is("ABC"));
                }, false)
        );
    }

    /**
     * RFC 8259 §7
     * Quote: "the character is represented as a 12-character sequence, encoding the UTF-16 surrogate pair."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-7
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testAcceptsEscapedSupplementaryUnicodeCharacters(ParserMethod parserMethod) {
        assertAll(
                () -> runWholeTextScenario(parserMethod, "\"\\uD83D\\uDE00\"", parser -> {
                    assertThat(parser.readString(), is("😀"));
                }, false),
                () -> runWholeTextScenario(parserMethod, "\"\\uD834\\uDD1E\"", parser -> {
                    assertThat(parser.readString(), is("\uD834\uDD1E"));
                }, false)
        );
    }

    /**
     * RFC 8259 §7
     * Quote: "%x75 4HEXDIG )  ; uXXXX                U+XXXX"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-7
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testRejectsInvalidUnicodeEscapeForms(ParserMethod parserMethod) {
        assertAll(
                () -> runWholeTextScenario(parserMethod, "\"\\u12G4\"", JsonParser::readString, true),
                () -> runWholeTextScenario(parserMethod, "\"\\u123\"", JsonParser::readString, true)
        );
    }

    /**
     * RFC 8259 §7
     * Quote: "escape ("
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-7
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testRejectsInvalidEscapeSequences(ParserMethod parserMethod) {
        assertAll(
                () -> runWholeTextScenario(parserMethod, "\"\\z\"", JsonParser::readString, true),
                () -> runWholeTextScenario(parserMethod, "\"\\x\"", JsonParser::readString, true)
        );
    }

    /**
     * RFC 8259 §7
     * Quote: "quotation mark, reverse solidus, and the control characters (U+0000 through U+001F)."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-7
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testRejectsUnescapedControlCharacters(ParserMethod parserMethod) {
        assertAll(
                () -> runWholeTextScenario(parserMethod, "\"" + '\u0000' + "\"", JsonParser::readString, true),
                () -> runWholeTextScenario(parserMethod, "\"" + '\u0001' + "\"", JsonParser::readString, true),
                () -> runWholeTextScenario(parserMethod, "\"" + '\b' + "\"", JsonParser::readString, true),
                () -> runWholeTextScenario(parserMethod, "\"" + '\f' + "\"", JsonParser::readString, true),
                () -> runWholeTextScenario(parserMethod, "\"" + '\t' + "\"", JsonParser::readString, true),
                () -> runWholeTextScenario(parserMethod, "\"" + '\n' + "\"", JsonParser::readString, true),
                () -> runWholeTextScenario(parserMethod, "\"" + '\r' + "\"", JsonParser::readString, true),
                () -> runWholeTextScenario(parserMethod, "\"" + '\u001F' + "\"", JsonParser::readString, true)
        );
    }

    /**
     * RFC 8259 §7
     * Quote: "A string begins and ends with quotation marks."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-7
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testRejectsUnterminatedStrings(ParserMethod parserMethod) {
        runWholeTextScenario(parserMethod, "\"unterminated", JsonParser::readString, true);
    }

    /**
     * RFC 8259 §8.2
     * Quote: "The behavior of software that receives JSON texts containing such values is unpredictable;"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-8.2
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testRejectsUnpairedSurrogatesPerHelidonPolicy(ParserMethod parserMethod) {
        assertAll(
                () -> runWholeTextScenario(parserMethod, "\"\\uD83D\"", JsonParser::readString, true),
                () -> runWholeTextScenario(parserMethod, "\"\\uDE00\"", JsonParser::readString, true),
                () -> runWholeTextScenario(parserMethod, "\"\\uD83DA\"", JsonParser::readString, true)
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
                fail("Expected invalid JSON string text to be rejected");
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
                fail("Expected invalid JSON string text to be rejected");
            }
        } catch (JsonException expected) {
            if (!expectRejection) {
                throw expected;
            }
        }
    }
}
