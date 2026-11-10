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

package io.helidon.json.schema;

import java.util.List;
import java.util.Optional;

import io.helidon.json.JsonException;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonParser;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SchemaStringTest {

    @Test
    void testProgrammaticMetadata() {
        Schema schema = Schema.builder()
                .rootObject(object -> object
                        .addStringProperty("state", state -> state
                                .addEnumValue("PENDING")
                                .addEnumValue("ACTIVE")
                                .addEnumValue("CLOSED"))
                        .addStringProperty("date", date -> date.format("date")))
                .build();

        JsonObject properties = schema.generateObjectNoKeywords().objectValue("properties").orElseThrow();
        JsonObject state = properties.objectValue("state").orElseThrow();
        assertThat(state.stringValue("type").orElseThrow(), is("string"));
        assertThat(state.arrayValue("enum").orElseThrow().values().stream()
                           .map(value -> value.asString().value())
                           .toList(), is(List.of("PENDING", "ACTIVE", "CLOSED")));
        JsonObject date = properties.objectValue("date").orElseThrow();
        assertThat(date.stringValue("type").orElseThrow(), is("string"));
        assertThat(date.stringValue("format").orElseThrow(), is("date"));
        assertThat(date.containsKey("enum"), is(false));
    }

    @Test
    void testRootRoundTrip() {
        JsonObject expected = JsonParser.create("""
                {
                    "type": "string",
                    "format": "date",
                    "enum": ["2026-11-10", "2026-01-01"],
                    "minLength": 1,
                    "maxLength": 10,
                    "pattern": "[0-9-]+"
                }
                """).readJsonObject();

        Schema schema = Schema.parse(expected.toString());
        SchemaString string = schema.rootString().orElseThrow();
        assertThat(string.format(), is(Optional.of("date")));
        assertThat(string.enumValues().orElseThrow(), is(List.of("2026-11-10", "2026-01-01")));
        assertThat(string.minLength(), is(Optional.of(1L)));
        assertThat(string.maxLength(), is(Optional.of(10L)));
        assertThat(string.pattern(), is(Optional.of("[0-9-]+")));
        assertThat(schema.generateObjectNoKeywords(), is(expected));
        assertThat(Schema.parse(schema.generate()).generateObjectNoKeywords(), is(expected));
    }

    @Test
    void testNestedPropertyRoundTrip() {
        JsonObject expected = JsonParser.create("""
                {
                    "type": "object",
                    "properties": {
                        "state": {
                            "type": "string",
                            "enum": ["PENDING", "ACTIVE", "CLOSED"]
                        },
                        "updated": {
                            "type": "string",
                            "format": "date-time"
                        }
                    }
                }
                """).readJsonObject();

        Schema schema = Schema.parse(expected.toString());
        SchemaObject object = schema.rootObject().orElseThrow();
        assertThat(object.stringProperties().get("state").enumValues().orElseThrow(),
                   is(List.of("PENDING", "ACTIVE", "CLOSED")));
        assertThat(object.stringProperties().get("updated").format(), is(Optional.of("date-time")));
        assertThat(schema.generateObjectNoKeywords(), is(expected));
        assertThat(Schema.parse(schema.generate()).generateObjectNoKeywords(), is(expected));
    }

    @Test
    void testArrayItemRoundTrip() {
        JsonObject expected = JsonParser.create("""
                {
                    "type": "array",
                    "items": {
                        "type": "string",
                        "format": "time",
                        "enum": ["14:30:00Z", "09:00:00Z"]
                    }
                }
                """).readJsonObject();

        Schema schema = Schema.parse(expected.toString());
        SchemaString items = (SchemaString) schema.rootArray().orElseThrow().items().orElseThrow();
        assertThat(items.format(), is(Optional.of("time")));
        assertThat(items.enumValues().orElseThrow(), is(List.of("14:30:00Z", "09:00:00Z")));
        assertThat(schema.generateObjectNoKeywords(), is(expected));
        assertThat(Schema.parse(schema.generate()).generateObjectNoKeywords(), is(expected));
    }

    @Test
    void testMixedTypeRootEnumRoundTrip() {
        Schema schema = Schema.parse("""
                {
                    "type": "string",
                    "enum": ["ON", null, 42, true, {}, [], "OFF"]
                }
                """);
        JsonObject expected = JsonParser.create("""
                {"type": "string", "enum": ["ON", "OFF"]}
                """).readJsonObject();

        assertThat(schema.rootString().orElseThrow().enumValues().orElseThrow(), is(List.of("ON", "OFF")));
        assertThat(schema.generateObjectNoKeywords(), is(expected));
        assertThat(Schema.parse(schema.generate()).generateObjectNoKeywords(), is(expected));
    }

    @Test
    void testMixedTypePropertyEnumRoundTrip() {
        Schema schema = Schema.parse("""
                {
                    "type": "object",
                    "properties": {
                        "state": {"type": "string", "enum": ["ON", null]}
                    },
                    "required": ["state"]
                }
                """);
        JsonObject expected = JsonParser.create("""
                {
                    "type": "object",
                    "properties": {
                        "state": {"type": "string", "enum": ["ON"]}
                    },
                    "required": ["state"]
                }
                """).readJsonObject();

        SchemaString state = schema.rootObject().orElseThrow().stringProperties().get("state");
        assertThat(state.enumValues().orElseThrow(), is(List.of("ON")));
        assertThat(state.required(), is(true));
        assertThat(schema.generateObjectNoKeywords(), is(expected));
        assertThat(Schema.parse(schema.generate()).generateObjectNoKeywords(), is(expected));
    }

    @Test
    void testMixedTypeArrayItemEnumRoundTrip() {
        Schema schema = Schema.parse("""
                {
                    "type": "array",
                    "items": {"type": "string", "enum": ["ON", 42]}
                }
                """);
        JsonObject expected = JsonParser.create("""
                {
                    "type": "array",
                    "items": {"type": "string", "enum": ["ON"]}
                }
                """).readJsonObject();

        SchemaString items = (SchemaString) schema.rootArray().orElseThrow().items().orElseThrow();
        assertThat(items.enumValues().orElseThrow(), is(List.of("ON")));
        assertThat(schema.generateObjectNoKeywords(), is(expected));
        assertThat(Schema.parse(schema.generate()).generateObjectNoKeywords(), is(expected));
    }

    @Test
    void testEnumsWithoutStringValuesRemainRestricted() {
        List<String> templates = List.of(
                """
                {"type": "string", "enum": %s}
                """,
                """
                {"type": "object", "properties": {"state": {"type": "string", "enum": %s}}}
                """,
                """
                {"type": "array", "items": {"type": "string", "enum": %s}}
                """);
        for (String template : templates) {
            JsonObject expected = JsonParser.create(template.formatted("[]")).readJsonObject();
            for (String values : List.of("[null]", "[42]", "[true]", "[{}]", "[[]]", "[null, 42, true, {}, []]")) {
                String input = template.formatted(values);
                Schema schema = Schema.parse(input);
                assertThat(input, schema.generateObjectNoKeywords(), is(expected));
                assertThat(input, Schema.parse(schema.generate()).generateObjectNoKeywords(), is(expected));
            }
        }
    }

    @Test
    void testNonStringRequiredPropertyRejected() {
        assertThrows(JsonException.class, () -> Schema.parse("""
                {
                    "type": "object",
                    "properties": {
                        "state": {"type": "string", "enum": ["ON"]}
                    },
                    "required": ["state", 42]
                }
                """));
    }

    @Test
    void testUnrestrictedString() {
        Schema schema = Schema.builder().rootString(SchemaString.create()).build();
        SchemaString string = schema.rootString().orElseThrow();
        assertThat(string.format(), is(Optional.empty()));
        assertThat(string.enumValues(), is(Optional.empty()));
        assertThat(schema.generateObjectNoKeywords().containsKey("format"), is(false));
        assertThat(schema.generateObjectNoKeywords().containsKey("enum"), is(false));

        SchemaString parsed = Schema.parse(schema.generate()).rootString().orElseThrow();
        assertThat(parsed.format(), is(Optional.empty()));
        assertThat(parsed.enumValues(), is(Optional.empty()));
    }

    @Test
    void testExplicitEmptyEnum() {
        Schema schema = Schema.builder().rootString(string -> string.enumValues(List.of())).build();

        assertThat(schema.rootString().orElseThrow().enumValues().orElseThrow(), is(List.of()));
        assertThat(schema.generateObjectNoKeywords().arrayValue("enum").orElseThrow().values(), is(List.of()));
    }

    @Test
    void testEmptyEnumRoundTrip() {
        JsonObject expected = JsonParser.create("""
                {
                    "type": "string",
                    "enum": []
                }
                """).readJsonObject();

        Schema schema = Schema.parse(expected.toString());
        assertThat(schema.rootString().orElseThrow().enumValues().orElseThrow(), is(List.of()));
        assertThat(schema.generateObjectNoKeywords(), is(expected));
        assertThat(Schema.parse(schema.generate()).generateObjectNoKeywords(), is(expected));
    }

    @Test
    void testRemoveEnumRestrictionFromCopy() {
        SchemaString restricted = SchemaString.builder().enumValues(List.of()).build();
        SchemaString.Builder copy = SchemaString.builder().from(restricted);
        Schema copiedSchema = Schema.builder().rootString(copy.build()).build();
        assertThat(copiedSchema.generateObjectNoKeywords().arrayValue("enum").orElseThrow().values(), is(List.of()));

        Schema unrestricted = Schema.builder().rootString(copy.clearEnumValues().build()).build();
        assertThat(unrestricted.generateObjectNoKeywords().containsKey("enum"), is(false));
        assertThat(Schema.builder().rootString(restricted).build().generateObjectNoKeywords().containsKey("enum"), is(true));
    }
}
