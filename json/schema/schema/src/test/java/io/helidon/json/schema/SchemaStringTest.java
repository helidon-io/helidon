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

import io.helidon.json.JsonObject;
import io.helidon.json.JsonParser;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

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
        assertThat(string.enumValues(), is(List.of("2026-11-10", "2026-01-01")));
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
        assertThat(object.stringProperties().get("state").enumValues(), is(List.of("PENDING", "ACTIVE", "CLOSED")));
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
        assertThat(items.enumValues(), is(List.of("14:30:00Z", "09:00:00Z")));
        assertThat(schema.generateObjectNoKeywords(), is(expected));
        assertThat(Schema.parse(schema.generate()).generateObjectNoKeywords(), is(expected));
    }

    @Test
    void testUnrestrictedString() {
        Schema schema = Schema.builder().rootString(SchemaString.create()).build();
        SchemaString string = schema.rootString().orElseThrow();
        assertThat(string.format(), is(Optional.empty()));
        assertThat(string.enumValues(), is(List.of()));
        assertThat(schema.generateObjectNoKeywords().containsKey("format"), is(false));
        assertThat(schema.generateObjectNoKeywords().containsKey("enum"), is(false));

        SchemaString parsed = Schema.parse(schema.generate()).rootString().orElseThrow();
        assertThat(parsed.format(), is(Optional.empty()));
        assertThat(parsed.enumValues(), is(List.of()));
    }
}
