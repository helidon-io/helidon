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
 * Strict RFC 8259 generator conformance tests for JSON string emission.
 */
class Rfc8259GeneratorStringTest {

    /**
     * RFC 8259 §7
     * Quote: "All Unicode characters may be placed within the quotation marks, except for the characters that MUST be escaped: quotation mark, reverse solidus, and the control characters (U+0000 through U+001F)."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-7
     */
    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    void testWritesStringValuesWithMandatoryEscapes(GeneratorMethod generatorMethod) {
        String value = "\"\\\n\t\r" + '\u0001';

        runGeneratedTextScenario(generatorMethod, generator -> generator.write(value), json -> {
            assertThat(json, is("\"\\\"\\\\\\n\\t\\r\\u0001\""));

            JsonParser parser = JsonParser.create(json);
            assertThat(parser.readString(), is(value));
            assertThat(parser.hasNext(), is(false));
        }, false);
    }

    /**
     * RFC 8259 §7
     * Quote: "All Unicode characters may be placed within the quotation marks, except for the characters that MUST be escaped: quotation mark, reverse solidus, and the control characters (U+0000 through U+001F)."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-7
     */
    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    void testWritesObjectMemberNamesAndStringValuesWithMandatoryEscapes(GeneratorMethod generatorMethod) {
        String value = "v\"\\\n\t\r" + '\u0001';

        runGeneratedTextScenario(generatorMethod, generator -> generator.writeObjectStart()
                .write("value", value)
                .writeObjectEnd(), json -> {
            assertThat(json, is("{\"value\":\"v\\\"\\\\\\n\\t\\r\\u0001\"}"));

            JsonParser parser = JsonParser.create(json);
            JsonObject jsonObject = parser.readJsonObject();
            assertThat(jsonObject.stringValue("value").orElseThrow(), is(value));
            assertThat(parser.hasNext(), is(false));
        }, false);
    }

    /**
     * RFC 8259 §7
     * Quote: "All Unicode characters may be placed within the quotation marks, except for the characters that MUST be escaped: quotation mark, reverse solidus, and the control characters (U+0000 through U+001F)."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-7
     */
    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    void testWritesObjectMemberNamesWithMandatoryEscapes(GeneratorMethod generatorMethod) {
        String key = "k\"\\\n\t\r" + '\u0001';

        runGeneratedTextScenario(generatorMethod, generator -> generator.writeObjectStart()
                .write(key, "ok")
                .writeObjectEnd(), json -> {
            assertThat(json, is("{\"k\\\"\\\\\\n\\t\\r\\u0001\":\"ok\"}"));

            JsonParser parser = JsonParser.create(json);
            JsonObject jsonObject = parser.readJsonObject();
            assertThat(jsonObject.keys().size(), is(1));
            assertThat(parser.hasNext(), is(false));
        }, false);
    }

    /**
     * RFC 8259 §7
     * Quote: "All Unicode characters may be placed within the quotation marks, except for the characters that MUST be escaped: quotation mark, reverse solidus, and the control characters (U+0000 through U+001F)."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-7
     */
    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    void testWritesCharValuesWithMandatoryEscapes(GeneratorMethod generatorMethod) {
        assertAll(
                () -> runGeneratedTextScenario(generatorMethod, generator -> generator.write('"'), json -> {
                    assertThat(json, is("\"\\\"\""));

                    JsonParser parser = JsonParser.create(json);
                    assertThat(parser.readString(), is("\""));
                    assertThat(parser.hasNext(), is(false));
                }, false),
                () -> runGeneratedTextScenario(generatorMethod, generator -> generator.write('\\'), json -> {
                    assertThat(json, is("\"\\\\\""));

                    JsonParser parser = JsonParser.create(json);
                    assertThat(parser.readString(), is("\\"));
                    assertThat(parser.hasNext(), is(false));
                }, false),
                () -> runGeneratedTextScenario(generatorMethod, generator -> generator.write('\n'), json -> {
                    assertThat(json, is("\"\\n\""));

                    JsonParser parser = JsonParser.create(json);
                    assertThat(parser.readString(), is("\n"));
                    assertThat(parser.hasNext(), is(false));
                }, false),
                () -> runGeneratedTextScenario(generatorMethod, generator -> generator.write('\t'), json -> {
                    assertThat(json, is("\"\\t\""));

                    JsonParser parser = JsonParser.create(json);
                    assertThat(parser.readString(), is("\t"));
                    assertThat(parser.hasNext(), is(false));
                }, false),
                () -> runGeneratedTextScenario(generatorMethod, generator -> generator.write('\r'), json -> {
                    assertThat(json, is("\"\\r\""));

                    JsonParser parser = JsonParser.create(json);
                    assertThat(parser.readString(), is("\r"));
                    assertThat(parser.hasNext(), is(false));
                }, false),
                () -> runGeneratedTextScenario(generatorMethod, generator -> generator.write('\u0001'), json -> {
                    assertThat(json, is("\"\\u0001\""));

                    JsonParser parser = JsonParser.create(json);
                    assertThat(parser.readString(), is("\u0001"));
                    assertThat(parser.hasNext(), is(false));
                }, false)
        );
    }

    /**
     * RFC 8259 §7
     * Quote: "All Unicode characters may be placed within the quotation marks, except for the characters that MUST be escaped: quotation mark, reverse solidus, and the control characters (U+0000 through U+001F)."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-7
     */
    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    void testWritesCharacterObjectMembersWithMandatoryEscapes(GeneratorMethod generatorMethod) {
        assertAll(
                () -> runGeneratedTextScenario(generatorMethod, generator -> generator.writeObjectStart()
                                .write("char", '"')
                                .writeObjectEnd(),
                                              json -> {
                                                  assertThat(json, is("{\"char\":\"\\\"\"}"));

                                                  JsonParser parser = JsonParser.create(json);
                                                  JsonObject jsonObject = parser.readJsonObject();
                                                  assertThat(jsonObject.keys().size(), is(1));
                                                  assertThat(parser.hasNext(), is(false));
                                              },
                                              false),
                () -> runGeneratedTextScenario(generatorMethod, generator -> generator.writeObjectStart()
                                .write("char", '\n')
                                .writeObjectEnd(),
                                              json -> {
                                                  assertThat(json, is("{\"char\":\"\\n\"}"));

                                                  JsonParser parser = JsonParser.create(json);
                                                  JsonObject jsonObject = parser.readJsonObject();
                                                  assertThat(jsonObject.keys().size(), is(1));
                                                  assertThat(parser.hasNext(), is(false));
                                              },
                                              false),
                () -> runGeneratedTextScenario(generatorMethod, generator -> generator.writeObjectStart()
                                .write("char", '\u0001')
                                .writeObjectEnd(),
                                              json -> {
                                                  assertThat(json, is("{\"char\":\"\\u0001\"}"));

                                                  JsonParser parser = JsonParser.create(json);
                                                  JsonObject jsonObject = parser.readJsonObject();
                                                  assertThat(jsonObject.keys().size(), is(1));
                                                  assertThat(parser.hasNext(), is(false));
                                              },
                                              false)
        );
    }

    /**
     * RFC 8259 §7
     * Quote: "All Unicode characters may be placed within the quotation marks,"
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-7
     */
    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    void testWritesUnicodeStringDataAsEquivalentCharacters(GeneratorMethod generatorMethod) {
        String value = "€😀žluťoučký";

        assertAll(
                () -> runGeneratedTextScenario(generatorMethod, generator -> generator.write(value), json -> {
                    JsonParser parser = JsonParser.create(json);
                    assertThat(parser.readString(), is(value));
                    assertThat(parser.hasNext(), is(false));
                }, false),
                () -> runGeneratedTextScenario(generatorMethod, generator -> generator.writeObjectStart()
                                .write("value", value)
                                .writeObjectEnd(),
                                              json -> {
                                                  JsonParser parser = JsonParser.create(json);
                                                  JsonObject jsonObject = parser.readJsonObject();
                                                  assertThat(jsonObject.stringValue("value").orElseThrow(), is(value));
                                                  assertThat(parser.hasNext(), is(false));
                                              },
                                              false)
        );
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
                fail("Expected generator to reject invalid JSON string generation");
            }
            generatedTextAssertions.accept(target.generatedJson());
        } catch (RuntimeException expected) {
            if (!expectRejection) {
                throw expected;
            }
        }
    }
}
