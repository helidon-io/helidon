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
import java.util.function.Consumer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Strict RFC 8259 generator conformance tests for JSON number emission.
 */
class Rfc8259GeneratorNumberTest {

    /**
     * RFC 8259 §6
     * Quote: "A number is represented in base 10 using decimal digits."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-6
     */
    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    void testWritesFinitePrimitiveNumbersAsJsonNumbers(GeneratorMethod generatorMethod) {
        assertAll(
                () -> runGeneratedTextScenario(generatorMethod, generator -> generator.write((byte) 127), json -> {
                    assertThat(json, is("127"));

                    JsonParser parser = JsonParser.create(json);
                    JsonValue value = parser.readJsonValue();
                    assertThat(value.type(), is(JsonValueType.NUMBER));
                    assertThat(value.asNumber().bigDecimalValue(), is(new BigDecimal("127")));
                    assertThat(parser.hasNext(), is(false));
                }, false),
                () -> runGeneratedTextScenario(generatorMethod, generator -> generator.write((short) -32768), json -> {
                    assertThat(json, is("-32768"));

                    JsonParser parser = JsonParser.create(json);
                    JsonValue value = parser.readJsonValue();
                    assertThat(value.type(), is(JsonValueType.NUMBER));
                    assertThat(value.asNumber().bigDecimalValue(), is(new BigDecimal("-32768")));
                    assertThat(parser.hasNext(), is(false));
                }, false),
                () -> runGeneratedTextScenario(generatorMethod, generator -> generator.write(42), json -> {
                    assertThat(json, is("42"));

                    JsonParser parser = JsonParser.create(json);
                    JsonValue value = parser.readJsonValue();
                    assertThat(value.type(), is(JsonValueType.NUMBER));
                    assertThat(value.asNumber().bigDecimalValue(), is(new BigDecimal("42")));
                    assertThat(parser.hasNext(), is(false));
                }, false),
                () -> runGeneratedTextScenario(generatorMethod, generator -> generator.write(1234567890123456789L), json -> {
                    assertThat(json, is("1234567890123456789"));

                    JsonParser parser = JsonParser.create(json);
                    JsonValue value = parser.readJsonValue();
                    assertThat(value.type(), is(JsonValueType.NUMBER));
                    assertThat(value.asNumber().bigDecimalValue(), is(new BigDecimal("1234567890123456789")));
                    assertThat(parser.hasNext(), is(false));
                }, false),
                () -> runGeneratedTextScenario(generatorMethod, generator -> generator.write(3.5f), json -> {
                    assertThat(json, is("3.5"));

                    JsonParser parser = JsonParser.create(json);
                    JsonValue value = parser.readJsonValue();
                    assertThat(value.type(), is(JsonValueType.NUMBER));
                    assertThat(value.asNumber().bigDecimalValue(), is(new BigDecimal("3.5")));
                    assertThat(parser.hasNext(), is(false));
                }, false),
                () -> runGeneratedTextScenario(generatorMethod, generator -> generator.write(-2.25d), json -> {
                    assertThat(json, is("-2.25"));

                    JsonParser parser = JsonParser.create(json);
                    JsonValue value = parser.readJsonValue();
                    assertThat(value.type(), is(JsonValueType.NUMBER));
                    assertThat(value.asNumber().bigDecimalValue(), is(new BigDecimal("-2.25")));
                    assertThat(parser.hasNext(), is(false));
                }, false)
        );
    }

    /**
     * RFC 8259 §6
     * Quote: "A number is represented in base 10 using decimal digits."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-6
     */
    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    void testWritesLongMinimumValueAsJsonNumber(GeneratorMethod generatorMethod) {
        runGeneratedTextScenario(generatorMethod, generator -> generator.write(Long.MIN_VALUE), json -> {
            assertThat(json, is("-9223372036854775808"));

            JsonParser parser = JsonParser.create(json);
            JsonValue value = parser.readJsonValue();
            assertThat(value.type(), is(JsonValueType.NUMBER));
            assertThat(value.asNumber().bigDecimalValue(), is(new BigDecimal("-9223372036854775808")));
            assertThat(parser.hasNext(), is(false));
        }, false);
    }

    /**
     * RFC 8259 §6
     * Quote: "A number is represented in base 10 using decimal digits."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-6
     */
    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    void testWritesBigDecimalAndBigIntegerRootValuesAsJsonNumbers(GeneratorMethod generatorMethod) {
        assertAll(
                () -> runGeneratedTextScenario(generatorMethod,
                                              generator -> generator.write(new BigDecimal("123.456")),
                                              json -> {
                                                  assertThat(json, is("123.456"));

                                                  JsonParser parser = JsonParser.create(json);
                                                  JsonValue value = parser.readJsonValue();
                                                  assertThat(value.type(), is(JsonValueType.NUMBER));
                                                  assertThat(value.asNumber().bigDecimalValue(),
                                                             is(new BigDecimal("123.456")));
                                                  assertThat(parser.hasNext(), is(false));
                                              },
                                              false),
                () -> runGeneratedTextScenario(generatorMethod,
                                              generator -> generator.write(new BigInteger("123456789012345678901234567890")),
                                              json -> {
                                                  assertThat(json, is("123456789012345678901234567890"));

                                                  JsonParser parser = JsonParser.create(json);
                                                  JsonValue value = parser.readJsonValue();
                                                  assertThat(value.type(), is(JsonValueType.NUMBER));
                                                  assertThat(value.asNumber().bigDecimalValue(),
                                                             is(new BigDecimal("123456789012345678901234567890")));
                                                  assertThat(parser.hasNext(), is(false));
                                              },
                                              false)
        );
    }

    /**
     * RFC 8259 §6
     * Quote: "A number is represented in base 10 using decimal digits."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-6
     */
    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    void testWritesBigDecimalAndBigIntegerObjectMembersAsJsonNumbers(GeneratorMethod generatorMethod) {
        runGeneratedTextScenario(generatorMethod, generator -> generator.writeObjectStart()
                .write("decimal", new BigDecimal("123.456"))
                .write("integer", new BigInteger("123456789012345678901234567890"))
                .writeObjectEnd(), json -> {
            assertThat(json, is("{\"decimal\":123.456,\"integer\":123456789012345678901234567890}"));

            JsonParser parser = JsonParser.create(json);
            JsonObject jsonObject = parser.readJsonObject();
            assertThat(jsonObject.value("decimal").orElseThrow().type(), is(JsonValueType.NUMBER));
            assertThat(jsonObject.numberValue("decimal").orElseThrow(), is(new BigDecimal("123.456")));
            assertThat(jsonObject.value("integer").orElseThrow().type(), is(JsonValueType.NUMBER));
            assertThat(jsonObject.numberValue("integer").orElseThrow(), is(new BigDecimal("123456789012345678901234567890")));
            assertThat(parser.hasNext(), is(false));
        }, false);
    }

    private static void runGeneratedTextScenario(GeneratorMethod generatorMethod,
                                                 Consumer<JsonGenerator> generatorWrites,
                                                 Consumer<String> generatedTextAssertions,
                                                 boolean expectRejection) {
        try {
            GeneratorMethod.Target target = generatorMethod.createTarget();
            try (JsonGenerator generator = target.createGenerator()) {
                generatorWrites.accept(generator);
            }
            if (expectRejection) {
                fail("Expected generator to reject invalid JSON number generation");
            }
            generatedTextAssertions.accept(target.generatedJson());
        } catch (RuntimeException expected) {
            if (!expectRejection) {
                throw expected;
            }
        }
    }
}
