/*
 * Copyright (c) 2019, 2026 Oracle and/or its affiliates.
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
package io.helidon.dbclient.mongodb;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.dbclient.mongodb.StatementParsers.NamedParser;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link StatementParsers}.
 */
public class StatementParsersTest {

    private static final String EXPONENT_NUMBER = "1e0";
    private static final String PRECISE_NUMBER = "12345678901234567890.123456789";
    private static final String STRUCTURAL_VALUE = "0,\"unexpected\":true";

    /**
     * Test simple MongoDb statement with parameters and mapping.
     */
    @Test
    void testStatementWithParameters() {
        String stmtIn = "{ id: { $gt: $idmin }, id: { $lt: $idmax } }";
        Map<String, Integer> mapping = new HashMap<>(2);
        mapping.put("idmin", 1);
        mapping.put("idmax", 7);
        String stmtExp = stmtIn
                .replace("$idmin", String.valueOf(mapping.get("idmin")))
                .replace("$idmax", String.valueOf(mapping.get("idmax")));
        NamedParser parser = new NamedParser(stmtIn, mapping);
        String stmtOut = parser.convert();
        assertThat(stmtOut, is(stmtExp));
    }

    @Test
    void testNonNumericNumberIsString() {
        assertThat(StatementParsers.toJson(number(STRUCTURAL_VALUE)), is("\"0,\\\"unexpected\\\":true\""));
    }

    @Test
    void testValidNumberRemainsNumeric() {
        assertThat(StatementParsers.toJson(new AtomicInteger(42)), is("42"));
    }

    @Test
    void testNegativeZeroRemainsSigned() {
        DoubleAccumulator number = new DoubleAccumulator((left, right) -> right, -0.0);

        Document parsed = Document.parse("{\"n\":" + StatementParsers.toJson(number) + "}");

        assertThat(Double.doubleToRawLongBits(parsed.getDouble("n")),
                   is(Double.doubleToRawLongBits(-0.0)));
    }

    @Test
    void testArbitraryPrecisionNumberRemainsExact() {
        assertThat(StatementParsers.toJson(number(PRECISE_NUMBER)), is(PRECISE_NUMBER));
    }

    @Test
    void testExponentNumberRemainsExact() {
        assertThat(StatementParsers.toJson(number(EXPONENT_NUMBER)), is(EXPONENT_NUMBER));
    }

    @Test
    void testInvalidJsonNumbersAreStrings() {
        for (String value : List.of("+1", ".5", "1.", "01", "1e+")) {
            assertThat(StatementParsers.toJson(number(value)), is('"' + value + '"'));
        }
    }

    private static Number number(String value) {
        return new BigDecimal(0) {
            @Override
            public String toString() {
                return value;
            }
        };
    }

}
