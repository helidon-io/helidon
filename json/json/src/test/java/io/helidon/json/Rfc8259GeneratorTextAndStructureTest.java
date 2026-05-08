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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Strict RFC 8259 generator conformance tests for JSON texts, objects, and arrays.
 */
class Rfc8259GeneratorTextAndStructureTest {

    /**
     * RFC 8259 §2
     * Quote: "A JSON text is a serialized value."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-2
     */
    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    void testWritesScalarRootValuesAsJsonTexts(GeneratorMethod generatorMethod) {
        assertAll(
                () -> runGeneratedTextScenario(generatorMethod, generator -> generator.write(true), json -> {
                    assertThat(json, is("true"));

                    JsonParser parser = JsonParser.create(json);
                    assertThat(parser.readBoolean(), is(true));
                    assertThat(parser.hasNext(), is(false));
                }, false),
                () -> runGeneratedTextScenario(generatorMethod, generator -> generator.writeNull(), json -> {
                    assertThat(json, is("null"));

                    JsonParser parser = JsonParser.create(json);
                    assertThat(parser.readJsonValue().type(), is(JsonValueType.NULL));
                    assertThat(parser.hasNext(), is(false));
                }, false)
        );
    }

    /**
     * RFC 8259 §4
     * Quote: "An object structure is represented as a pair of curly brackets surrounding zero or more name/value pairs (or members)."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-4
     */
    @ParameterizedTest
    @EnumSource(GeneratorMethod.class)
    void testWritesObjectTexts(GeneratorMethod generatorMethod) {
        runGeneratedTextScenario(generatorMethod, generator -> generator.writeObjectStart()
                .write("name", "Ada")
                .write("active", true)
                .write("count", 2)
                .writeObjectEnd(), json -> {
            assertThat(json, is("{\"name\":\"Ada\",\"active\":true,\"count\":2}"));

            JsonParser parser = JsonParser.create(json);
            JsonObject jsonObject = parser.readJsonObject();
            assertThat(jsonObject.stringValue("name").orElseThrow(), is("Ada"));
            assertThat(jsonObject.booleanValue("active").orElseThrow(), is(true));
            assertThat(jsonObject.intValue("count").orElseThrow(), is(2));
            assertThat(parser.hasNext(), is(false));
        }, false);
    }

    /**
     * RFC 8259 §5
     * Quote: "An array structure is represented as square brackets surrounding zero or more values (or elements)."
     * Spec: https://www.rfc-editor.org/rfc/rfc8259#section-5
     */
    @Test
    void testWritesArrayTexts() {
        runGeneratedTextScenario(GeneratorMethod.WRITER, generator -> generator.writeArrayStart()
                .write(1)
                .write("two")
                .writeNull()
                .writeObjectStart()
                .write("nested", false)
                .writeObjectEnd()
                .writeArrayEnd(), json -> {
            assertThat(json, is("[1,\"two\",null,{\"nested\":false}]"));

            JsonParser parser = JsonParser.create(json);
            JsonArray jsonArray = parser.readJsonArray();
            assertThat(jsonArray.values().size(), is(4));
            assertThat(jsonArray.get(0, JsonNull.instance()).asNumber().intValue(), is(1));
            assertThat(jsonArray.get(1, JsonNull.instance()).asString().value(), is("two"));
            assertThat(jsonArray.get(2, JsonNull.instance()).type(), is(JsonValueType.NULL));
            assertThat(jsonArray.get(3, JsonNull.instance()).asObject().booleanValue("nested").orElseThrow(), is(false));
            assertThat(parser.hasNext(), is(false));
        }, false);

        runGeneratedTextScenario(GeneratorMethod.OUTPUT_STREAM, generator -> generator.writeArrayStart()
                .write(1)
                .write("two")
                .writeNull()
                .writeObjectStart()
                .write("nested", false)
                .writeObjectEnd()
                .writeArrayEnd(), json -> {
            assertThat(json, is("[1,\"two\",null,{\"nested\":false}]"));

            JsonParser parser = JsonParser.create(json);
            JsonArray jsonArray = parser.readJsonArray();
            assertThat(jsonArray.values().size(), is(4));
            assertThat(jsonArray.get(0, JsonNull.instance()).asNumber().intValue(), is(1));
            assertThat(jsonArray.get(1, JsonNull.instance()).asString().value(), is("two"));
            assertThat(jsonArray.get(2, JsonNull.instance()).type(), is(JsonValueType.NULL));
            assertThat(jsonArray.get(3, JsonNull.instance()).asObject().booleanValue("nested").orElseThrow(), is(false));
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
                fail("Expected generator to reject invalid JSON text generation");
            }
            generatedTextAssertions.accept(target.generatedJson());
        } catch (RuntimeException expected) {
            if (!expectRejection) {
                throw expected;
            }
        }
    }
}
