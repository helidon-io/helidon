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

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonObjectDuplicateKeyTest {

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void rejectsMalformedOverwrittenStrings(ParserMethod parserMethod) {
        for (String value : List.of("\"\\q\"", "\"\\u12G4\"", "\"\\u123\"", "\"raw\nnewline\"", "\"\\uD800\"")) {
            String json = "{\"a\":" + value + ",\"a\":\"valid\"}";
            assertThrows(JsonException.class,
                         () -> parserMethod.createParser(json).readJsonObject().stringValue("a"),
                         json);
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void rejectsMalformedStringsInOverwrittenContainers(ParserMethod parserMethod) {
        for (String value : List.of("[\"\\q\"]",
                                    "{\"nested\":\"\\q\"}",
                                    "{\"\\q\":\"value\"}",
                                    "[{\"nested\":[\"\\q\"]}]",
                                    "{\"nested\":{\"a\":\"\\q\",\"a\":\"valid\"}}")) {
            String json = "{\"a\":" + value + ",\"a\":\"valid\"}";
            assertThrows(JsonException.class,
                         () -> parserMethod.createParser(json).readJsonObject().stringValue("a"),
                         json);
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void rejectsMalformedOverwrittenStringsInNestedObjects(ParserMethod parserMethod) {
        String json = "{\"outer\":{\"a\":\"\\q\",\"a\":\"valid\"}}";
        assertThrows(JsonException.class,
                     () -> parserMethod.createParser(json).readJsonObject()
                             .objectValue("outer").orElseThrow().stringValue("a"));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void rejectsMalformedOverwrittenStringsInArrayElements(ParserMethod parserMethod) {
        String json = "[{\"a\":\"\\q\",\"a\":\"valid\"}]";
        assertThrows(JsonException.class,
                     () -> parserMethod.createParser(json).readJsonArray()
                             .values().get(0).asObject().stringValue("a"));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void rejectsMalformedSurvivingStrings(ParserMethod parserMethod) {
        for (String json : List.of("{\"a\":\"\\q\"}", "{\"a\":\"valid\",\"a\":\"\\q\"}")) {
            assertThrows(JsonException.class,
                         () -> parserMethod.createParser(json).readJsonObject().stringValue("a"),
                         json);
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void rejectsMalformedOverwrittenStringsWhenSerializing(ParserMethod parserMethod) {
        String json = "{\"a\":[{\"nested\":\"\\q\"}],\"a\":\"valid\"}";
        assertThrows(JsonException.class,
                     () -> parserMethod.createParser(json).readJsonObject().toString());
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void rejectsMalformedValuesBeforePublishingObject(ParserMethod parserMethod) {
        String json = "{\"a\":\"\\q\",\"a\":\"valid\"}";
        assertThrows(JsonException.class, () -> parserMethod.createParser(json).readJsonObject());
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void preservesLastValueForValidDuplicates(ParserMethod parserMethod) {
        for (String value : List.of("\"first\"", "\"\\u0041\\n\"", "[\"first\"]",
                                    "{\"nested\":\"first\"}",
                                    "{\"nested\":[123,true,null,{\"b\":\"first\",\"b\":\"last\"}]}",
                                    "123", "true", "null")) {
            String json = "{\"a\":" + value + ",\"a\":\"valid\"}";
            JsonObject object = parserMethod.createParser(json).readJsonObject();

            assertThat(json, object.stringValue("a").orElseThrow(), is("valid"));
            assertThat(object.keysAsStrings(), contains("a"));
            assertThat(object.stringValue("a").orElseThrow(), is("valid"));
            assertThat(object.toString(), is("{\"a\":\"valid\"}"));
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void resolvesEscapedDuplicateKeys(ParserMethod parserMethod) {
        JsonObject object = parserMethod.createParser("{\"a\":\"first\",\"\\u0061\":\"valid\"}").readJsonObject();

        assertThat(object.stringValue("a").orElseThrow(), is("valid"));
        assertThat(object.keysAsStrings(), contains("a"));

        String json = "{\"a\":\"\\q\",\"\\u0061\":\"valid\"}";
        assertThrows(JsonException.class,
                     () -> parserMethod.createParser(json).readJsonObject().keysAsStrings());
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void allowsTraversingResolvedKeys(ParserMethod parserMethod) {
        String json = "{\"a\":\"first\",\"b\":\"valid\",\"a\":\"valid\"}";
        JsonObject object = parserMethod.createParser(json).readJsonObject();

        assertThat(object.keysAsStrings(), contains("a", "b"));
        for (String key : object.keysAsStrings()) {
            assertThat(object.value(key).orElseThrow().asString().value(), is("valid"));
        }
        assertThat(object.toString(), is("{\"a\":\"valid\",\"b\":\"valid\"}"));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void validatesDiscardedContainersAtMaximumDepth(ParserMethod parserMethod) {
        int depth = JsonParser.MAX_NESTING_DEPTH - 1;
        for (String value : List.of("[".repeat(depth) + "\"first\"" + "]".repeat(depth),
                                    "{\"nested\":".repeat(depth) + "\"first\"" + "}".repeat(depth))) {
            String json = "{\"a\":" + value + ",\"a\":\"valid\"}";
            JsonObject object = parserMethod.createParser(json).readJsonObject();

            assertThat(object.stringValue("a").orElseThrow(), is("valid"));
        }
    }
}
