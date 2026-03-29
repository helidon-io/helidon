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

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Strict RFC 8259 parser conformance tests for string comparison and escaped-string equivalence.
 */
class Rfc8259StringComparisonTest {

    /**
     * RFC 8259 §8.3
     * Quote: "Implementations that compare strings with escaped characters unconverted may incorrectly find that \"a\\b\" and \"a\\u005Cb\" are not equal."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-8.3
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testEquivalentEscapedStringFormsCompareEqualAfterParsing(ParserMethod parserMethod) {
        JsonString reverseSolidusEscaped = parserMethod.createParser("\"a\\\\b\"").readJsonValue().asString();
        JsonString unicodeEscaped = parserMethod.createParser("\"a\\u005Cb\"").readJsonValue().asString();

        assertAll(
                () -> assertThat(reverseSolidusEscaped.value(), is("a\\b")),
                () -> assertThat(unicodeEscaped.value(), is("a\\b")),
                () -> assertThat(reverseSolidusEscaped, is(unicodeEscaped))
        );
    }

    /**
     * RFC 8259 §8.3
     * Quote: "Software implementations are typically required to test names of object members for equality."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-8.3
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testObjectLookupUsesDecodedCodeUnitsForEscapedMemberNames(ParserMethod parserMethod) {
        JsonObject jsonObject = parserMethod.createParser("{\"a\\u005Cb\":1,\"\\uD83D\\uDE00\":2}").readJsonObject();
        Set<String> keys = jsonObject.keys().stream()
                .map(JsonString::value)
                .collect(Collectors.toSet());

        assertAll(
                () -> assertThat(jsonObject.containsKey("a\\b"), is(true)),
                () -> assertThat(jsonObject.intValue("a\\b").orElseThrow(), is(1)),
                () -> assertThat(jsonObject.containsKey("😀"), is(true)),
                () -> assertThat(jsonObject.intValue("😀").orElseThrow(), is(2)),
                () -> assertThat(keys.contains("a\\b"), is(true)),
                () -> assertThat(keys.contains("😀"), is(true))
        );
    }

    /**
     * RFC 8259 §8.3
     * Quote: "Implementations that transform the textual representation into sequences of Unicode code units and then perform the comparison numerically, code unit by code unit, are interoperable"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-8.3
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testObjectStringValuesMaterializeAsDecodedUnicodeCodeUnits(ParserMethod parserMethod) {
        String expectedEscapedValue = "\"\\\n\t\r" + '\u0001';
        JsonObject jsonObject = parserMethod.createParser(
                "{\"value\":\"\\\"\\\\\\n\\t\\r\\u0001\",\"reverseSolidus\":\"a\\u005Cb\",\"emoji\":\"\\uD83D\\uDE00\"}")
                .readJsonObject();

        assertAll(
                () -> assertThat(jsonObject.stringValue("value").orElseThrow(), is(expectedEscapedValue)),
                () -> assertThat(jsonObject.stringValue("reverseSolidus").orElseThrow(), is("a\\b")),
                () -> assertThat(jsonObject.stringValue("emoji").orElseThrow(), is("😀"))
        );
    }

    /**
     * RFC 8259 §8.3
     * Quote: "Implementations that transform the textual representation into sequences of Unicode code units and then perform the comparison numerically, code unit by code unit, are interoperable"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-8.3
     */
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testArrayStringValuesMaterializeAsDecodedUnicodeCodeUnits(ParserMethod parserMethod) {
        String expectedEscapedValue = "\"\\\n\t\r" + '\u0001';
        JsonArray jsonArray = parserMethod.createParser(
                "[\"\\\"\\\\\\n\\t\\r\\u0001\",\"a\\u005Cb\",\"\\uD83D\\uDE00\"]")
                .readJsonArray();

        assertAll(
                () -> assertThat(jsonArray.get(0, JsonNull.instance()).asString().value(), is(expectedEscapedValue)),
                () -> assertThat(jsonArray.get(1, JsonNull.instance()).asString().value(), is("a\\b")),
                () -> assertThat(jsonArray.get(2, JsonNull.instance()).asString().value(), is("😀"))
        );
    }
}
