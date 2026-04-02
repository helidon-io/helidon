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

import java.math.BigDecimal;
import java.util.function.Consumer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Strict RFC 8259 parser conformance tests for JSON number grammar.
 */
class Rfc8259NumberGrammarTest {

    /**
     * RFC 8259 §6
     * Quote: "A number is represented in base 10 using decimal digits."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-6
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testAcceptsValidIntegerForms(ParserMethod parserMethod) {
        assertAll(
                () -> runWholeTextScenario(parserMethod, "0", parser -> {
                    JsonValue value = parser.readJsonValue();
                    assertThat(value.type(), is(JsonValueType.NUMBER));
                    assertThat(value.asNumber().bigDecimalValue(), is(BigDecimal.ZERO));
                }, false),
                () -> runWholeTextScenario(parserMethod, "12", parser -> {
                    JsonValue value = parser.readJsonValue();
                    assertThat(value.type(), is(JsonValueType.NUMBER));
                    assertThat(value.asNumber().bigDecimalValue(), is(new BigDecimal("12")));
                }, false),
                () -> runWholeTextScenario(parserMethod, "-12", parser -> {
                    JsonValue value = parser.readJsonValue();
                    assertThat(value.type(), is(JsonValueType.NUMBER));
                    assertThat(value.asNumber().bigDecimalValue(), is(new BigDecimal("-12")));
                }, false)
        );
    }

    /**
     * RFC 8259 §6
     * Quote: "A fraction part is a decimal point followed by one or more digits."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-6
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testAcceptsValidFractionForms(ParserMethod parserMethod) {
        assertAll(
                () -> runWholeTextScenario(parserMethod, "0.5", parser -> {
                    JsonValue value = parser.readJsonValue();
                    assertThat(value.type(), is(JsonValueType.NUMBER));
                    assertThat(value.asNumber().bigDecimalValue(), is(new BigDecimal("0.5")));
                }, false),
                () -> runWholeTextScenario(parserMethod, "-0.5", parser -> {
                    JsonValue value = parser.readJsonValue();
                    assertThat(value.type(), is(JsonValueType.NUMBER));
                    assertThat(value.asNumber().bigDecimalValue(), is(new BigDecimal("-0.5")));
                }, false),
                () -> runWholeTextScenario(parserMethod, "123.456", parser -> {
                    JsonValue value = parser.readJsonValue();
                    assertThat(value.type(), is(JsonValueType.NUMBER));
                    assertThat(value.asNumber().bigDecimalValue(), is(new BigDecimal("123.456")));
                }, false)
        );
    }

    /**
     * RFC 8259 §6
     * Quote: "An exponent part begins with the letter E in uppercase or lowercase,"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-6
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testAcceptsValidExponentForms(ParserMethod parserMethod) {
        assertAll(
                () -> runWholeTextScenario(parserMethod, "1e10", parser -> {
                    JsonValue value = parser.readJsonValue();
                    assertThat(value.type(), is(JsonValueType.NUMBER));
                    assertThat(value.asNumber().bigDecimalValue(), is(new BigDecimal("1e10")));
                }, false),
                () -> runWholeTextScenario(parserMethod, "1E10", parser -> {
                    JsonValue value = parser.readJsonValue();
                    assertThat(value.type(), is(JsonValueType.NUMBER));
                    assertThat(value.asNumber().bigDecimalValue(), is(new BigDecimal("1E10")));
                }, false),
                () -> runWholeTextScenario(parserMethod, "1e+10", parser -> {
                    JsonValue value = parser.readJsonValue();
                    assertThat(value.type(), is(JsonValueType.NUMBER));
                    assertThat(value.asNumber().bigDecimalValue(), is(new BigDecimal("1e+10")));
                }, false),
                () -> runWholeTextScenario(parserMethod, "1e-10", parser -> {
                    JsonValue value = parser.readJsonValue();
                    assertThat(value.type(), is(JsonValueType.NUMBER));
                    assertThat(value.asNumber().bigDecimalValue(), is(new BigDecimal("1e-10")));
                }, false),
                () -> runWholeTextScenario(parserMethod, "-123.456e7", parser -> {
                    JsonValue value = parser.readJsonValue();
                    assertThat(value.type(), is(JsonValueType.NUMBER));
                    assertThat(value.asNumber().bigDecimalValue(), is(new BigDecimal("-123.456e7")));
                }, false)
        );
    }

    /**
     * RFC 8259 §6
     * Quote: "A number is represented in base 10 using decimal digits."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-6
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testRejectsNumbersWithLeadingPlus(ParserMethod parserMethod) {
        assertAll(
                () -> runWholeTextScenario(parserMethod, "+1", JsonParser::readJsonValue, true),
                () -> runWholeTextScenario(parserMethod, "+0.5", JsonParser::readJsonValue, true),
                () -> runWholeTextScenario(parserMethod, "+1e2", JsonParser::readJsonValue, true)
        );
    }

    /**
     * RFC 8259 §6
     * Quote: "int = zero / ( digit1-9 *DIGIT )"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-6
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testRejectsNumbersWithoutIntegerComponent(ParserMethod parserMethod) {
        Consumer<JsonParser> parserConsumer = parser -> {
            JsonValue jsonValue = parser.readJsonValue();
            jsonValue.asNumber().doubleValue();
        };
        assertAll(
                () -> runWholeTextScenario(parserMethod, ".1", parserConsumer, true),
                () -> runWholeTextScenario(parserMethod, "-.1", parserConsumer, true)
        );
    }

    /**
     * RFC 8259 §6
     * Quote: "A fraction part is a decimal point followed by one or more digits."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-6
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testRejectsNumbersWithoutFractionDigits(ParserMethod parserMethod) {
        Consumer<JsonParser> parserConsumer = parser -> {
            JsonValue jsonValue = parser.readJsonValue();
            jsonValue.asNumber().doubleValue();
        };
        assertAll(
                () -> runWholeTextScenario(parserMethod, "1.", parserConsumer, true),
                () -> runWholeTextScenario(parserMethod, "0.", parserConsumer, true),
                () -> runWholeTextScenario(parserMethod, "-0.", parserConsumer, true)
        );
    }

    /**
     * RFC 8259 §6
     * Quote: "The E and optional sign are followed by one or more digits."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-6
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testRejectsNumbersWithoutExponentDigits(ParserMethod parserMethod) {
        Consumer<JsonParser> parserConsumer = parser -> {
            JsonValue jsonValue = parser.readJsonValue();
            jsonValue.asNumber().doubleValue();
        };
        assertAll(
                () -> runWholeTextScenario(parserMethod, "1e", parserConsumer, true),
                () -> runWholeTextScenario(parserMethod, "1e+", parserConsumer, true),
                () -> runWholeTextScenario(parserMethod, "1e-", parserConsumer, true)
        );
    }

    /**
     * RFC 8259 §6
     * Quote: "Numeric values that cannot be represented in the grammar below (such as Infinity and NaN) are not permitted."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-6
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testRejectsNonJsonSpecialNumbers(ParserMethod parserMethod) {
        assertAll(
                () -> runWholeTextScenario(parserMethod, "NaN", JsonParser::readJsonValue, true),
                () -> runWholeTextScenario(parserMethod, "Infinity", JsonParser::readJsonValue, true),
                () -> runWholeTextScenario(parserMethod, "-Infinity", JsonParser::readJsonValue, true)
        );
    }

    /**
     * RFC 8259 §6
     * Quote: "A fraction part is a decimal point followed by one or more digits."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-6
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testRejectsMalformedNumberBodies(ParserMethod parserMethod) {
        Consumer<JsonParser> parserConsumer = parser -> {
            JsonValue jsonValue = parser.readJsonValue();
            jsonValue.asNumber().doubleValue();
        };
        assertAll(
                () -> runWholeTextScenario(parserMethod, "1.2.3", parserConsumer, true),
                () -> runWholeTextScenario(parserMethod, "1e2e3", parserConsumer, true)
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
                fail("Expected invalid JSON number text to be rejected: " + json);
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
                fail("Expected invalid JSON number text to be rejected: " + json);
            }
        } catch (JsonException expected) {
            if (!expectRejection) {
                throw expected;
            }
        }
    }
}
