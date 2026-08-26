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
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonNumberTest {

    @Test
    void rejectsExcessiveBigIntegerExpansion() {
        for (String literal : new String[] {"1e4097", "-1e4097"}) {
            JsonObject object = JsonParser.create("{\"n\":" + literal + "}").readJsonObject();
            JsonNumber number = object.value("n").orElseThrow().asNumber();

            assertThat(number.bigDecimalValue().scale(), is(-4_097));
            JsonException exception = assertThrows(JsonException.class, number::bigIntegerValue);
            assertThat(exception.getMessage(), containsString("4096"));
        }
    }

    @Test
    void convertsBigIntegerAtExpansionLimit() {
        BigInteger magnitude = BigInteger.TEN.pow(4_096);
        for (String literal : new String[] {"1e4096", "-1e4096"}) {
            JsonNumber number = JsonNumber.create(new BigDecimal(literal));
            BigInteger expected = literal.startsWith("-") ? magnitude.negate() : magnitude;

            assertThat(number.bigIntegerValue(), is(expected));
        }
    }

    @Test
    void convertsLargeScaleZeroBigInteger() {
        BigInteger integer = BigInteger.TEN.pow(4_097);
        JsonNumber number = JsonNumber.create(new BigDecimal(integer));

        assertThat(number.bigIntegerValue(), is(integer));
    }

    @Test
    void rejectsMinimumScaleWithoutCallingBigDecimalConversion() {
        JsonNumber number = JsonNumber.create(new MinimumScaleBigDecimal());

        assertThrows(JsonException.class, number::bigIntegerValue);
    }

    @Test
    void convertsFractionWithNoIntegerDigitsToZero() {
        JsonNumber number = JsonNumber.create(new BigDecimal(BigInteger.ONE, Integer.MAX_VALUE));

        assertThat(number.bigIntegerValue(), is(BigInteger.ZERO));
    }

    @Test
    void convertsZeroWithMinimumScaleToZero() {
        JsonNumber number = JsonNumber.create(new BigDecimal(BigInteger.ZERO, Integer.MIN_VALUE));

        assertThat(number.bigIntegerValue(), is(BigInteger.ZERO));
        assertThat(number.toString(), is("0"));
    }

    @Test
    void mutableParserInputRetainsAggregateBigIntegerExpansionBudget() {
        byte[] json = "100000".getBytes(StandardCharsets.US_ASCII);
        JsonNumber number = JsonParser.create(json).readJsonNumber();
        BigInteger expected = BigInteger.TEN.pow(4_096);

        byte[] expansion = "1e4096".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(expansion, 0, json, 0, expansion.length);

        for (int i = 0; i < 16; i++) {
            assertThat(number.bigIntegerValue(), is(expected));
        }
        JsonException exception = assertThrows(JsonException.class, number::bigIntegerValue);
        assertThat(exception.getMessage(), containsString("65536"));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void parsedTreeSharesAggregateBigIntegerExpansionBudget(ParserMethod parserMethod) {
        BigInteger expected = BigInteger.TEN.pow(4_096);
        String middle = "1e4096,".repeat(14) + "1e4096";
        String json = "{\"first\":1e4096,\"values\":[" + middle + "],\"last\":1e4096}";
        JsonObject object = parserMethod.createParser(json).readJsonObject();

        assertThat(object.bigIntegerValue("first").orElseThrow(), is(expected));
        for (JsonValue value : object.value("values").orElseThrow().asArray().values()) {
            assertThat(value.asNumber().bigIntegerValue(), is(expected));
        }
        JsonException exception = assertThrows(JsonException.class, () -> object.bigIntegerValue("last"));
        assertThat(exception.getMessage(), containsString("65536"));

        JsonObject freshObject = parserMethod.createParser(json).readJsonObject();
        assertThat(freshObject.bigIntegerValue("first").orElseThrow(), is(expected));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void fractionalExponentSharesAggregateBigIntegerExpansionBudget(ParserMethod parserMethod) {
        for (String number : List.of("1.23e4098", "1.23E+4098")) {
            String json = "[" + (number + ",").repeat(16) + number + "]";
            JsonArray array = parserMethod.createParser(json).readJsonArray();

            for (int i = 0; i < 16; i++) {
                array.get(i).orElseThrow().asNumber().bigIntegerValue();
            }
            JsonException exception = assertThrows(JsonException.class,
                                                   () -> array.get(16).orElseThrow().asNumber().bigIntegerValue());
            assertThat(exception.getMessage(), containsString("65536"));
        }
    }

    private static final class MinimumScaleBigDecimal extends BigDecimal {
        private static final long serialVersionUID = 1L;

        private MinimumScaleBigDecimal() {
            super(BigInteger.ONE, Integer.MIN_VALUE);
        }

        @Override
        public BigInteger toBigInteger() {
            throw new AssertionError("BigDecimal conversion should not be invoked");
        }
    }
}
