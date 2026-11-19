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

import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BigIntegerParserTest {
    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "-0.0, 0",
            "1.0, 1",
            "1e3, 1000",
            "1E+3, 1000",
            "1.234e3, 1234",
            "1.9, 1",
            "-1.9, -1",
            "19e-1, 1",
            "-19e-1, -1",
            "1e-4097, 0",
            "0e4097, 0",
            "9223372036854775808, 9223372036854775808",
            "123456789012345678901234567890.9, 123456789012345678901234567890"
    })
    void readsNumericValue(String literal, String integer) {
        BigInteger expected = new BigInteger(integer);
        List<JsonParser> parsers = List.of(
                ParserMethod.ARRAY.createParser(literal),
                ParserMethod.STREAM.createParser(literal),
                JsonParser.create(new StringReader(literal)),
                JsonParser.create(JsonParser.create(literal).readJsonNumber()),
                JsonParser.create(JsonNumber.create(new BigDecimal(literal))));

        for (JsonParser parser : parsers) {
            assertThat(parser.getClass().getSimpleName(), parser.readBigInteger(), is(expected));
            assertThat(parser.hasNext(), is(false));
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void consumesQuotedAndUnquotedNumbers(ParserMethod method) {
        JsonParser parser = method.createParser("[\"1.9\",1e3,-1.9,123456789012345678901234567890,7]");
        List<BigInteger> expected = List.of(BigInteger.ONE,
                                           BigInteger.valueOf(1000),
                                           BigInteger.valueOf(-1),
                                           new BigInteger("123456789012345678901234567890"),
                                           BigInteger.valueOf(7));
        for (int i = 0; i < expected.size(); i++) {
            parser.nextToken();
            assertThat(parser.readBigInteger(), is(expected.get(i)));
            assertThat(parser.nextToken(), is(i + 1 == expected.size() ? (byte) ']' : (byte) ','));
        }
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.", ".5", "-.5", "+1.0", "01.0", "00e3", "-01e3", "1.e3", "1.E-3", "--1",
                            "1e", "1e+", "\"1e3x\"", "\"1.9",
                            "null", "true", "{}", "[]"})
    void rejectsInvalidNumericInput(String literal) {
        for (ParserMethod method : ParserMethod.values()) {
            JsonParser parser = method.createParser(literal);
            assertThrows(JsonException.class, parser::readBigInteger, method + ": " + literal);
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void limitsDecimalExpansion(ParserMethod method) {
        BigInteger magnitude = BigInteger.TEN.pow(4096);
        assertThat(method.createParser("1e4096").readBigInteger(), is(magnitude));
        assertThat(method.createParser("-1e4096").readBigInteger(), is(magnitude.negate()));

        JsonParser parser = method.createParser("1e4097");
        JsonException exception = assertThrows(JsonException.class, parser::readBigInteger);
        assertThat(exception.getMessage(), containsString("4096"));

        String explicitDigits = "1" + "0".repeat(4097);
        assertThat(method.createParser(explicitDigits).readBigInteger(), is(BigInteger.TEN.pow(4097)));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void sharesAggregateExpansionBudgetWithJsonNumbers(ParserMethod method) {
        JsonParser parser = method.createParser("[" + "1e4096,".repeat(16) + "1e4096]");
        BigInteger expected = BigInteger.TEN.pow(4096);
        for (int i = 0; i < 16; i++) {
            assertThat(parser.nextToken(), is((byte) '1'));
            BigInteger actual = i < 8 ? parser.readBigInteger() : parser.readJsonNumber().bigIntegerValue();
            assertThat(actual, is(expected));
            assertThat(parser.nextToken(), is((byte) ','));
        }
        assertThat(parser.nextToken(), is((byte) '1'));
        JsonException exception = assertThrows(JsonException.class, parser::readBigInteger);
        assertThat(exception.getMessage(), containsString("65536"));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void resetDoesNotRestoreExpansionBudget(ParserMethod method) {
        JsonParser parser = method.createParser("[1e4096]");
        BigInteger expected = BigInteger.TEN.pow(4096);
        assertThat(parser.nextToken(), is((byte) '1'));
        for (int i = 0; i < 16; i++) {
            parser.mark();
            assertThat(parser.readBigInteger(), is(expected));
            parser.resetToMark();
        }
        JsonException exception = assertThrows(JsonException.class, parser::readBigInteger);
        assertThat(exception.getMessage(), containsString("65536"));
    }
}
