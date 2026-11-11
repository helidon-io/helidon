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

package io.helidon.json.schema.tests;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.helidon.json.JsonArray;
import io.helidon.json.JsonNull;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonParser;
import io.helidon.json.JsonValue;

import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class SchemaEnumTimeTest {
    @Test
    void testEnumProperty() {
        var properties = JsonParser.create(new SchemaItemRecord__JsonSchema().jsonSchema())
                .readJsonObject()
                .objectValue("properties").orElseThrow();
        assertThat(properties.objectValue("state").orElseThrow(),
                   jsonEqualTo(JsonObject.builder().set("type", "string").setStrings("enum", List.of("ON", "OFF")).build()));
    }

    @Test
    void testDateProperty() {
        var properties = JsonParser.create(new SchemaItemRecord__JsonSchema().jsonSchema())
                .readJsonObject()
                .objectValue("properties").orElseThrow();
        assertThat(properties.objectValue("since").orElseThrow(),
                   jsonEqualTo(JsonObject.builder().set("type", "string").set("format", "date").build()));
    }

    @Test
    void testProviderSchema() {
        var provider = new SchemaItemRecord__JsonSchema();
        var root = provider.schema().rootObject().orElseThrow();
        assertThat(root.stringProperties().size(), is(3));
        assertThat(root.stringProperties().get("state").enumValues().orElseThrow(),
                   is(JsonArray.createStrings(List.of("ON", "OFF")).values()));
        assertThat(root.stringProperties().get("since").format().orElseThrow(), is("date"));
        assertThat(JsonParser.create(provider.schema().generate()).readJsonObject(),
                   jsonEqualTo(JsonParser.create(provider.jsonSchema()).readJsonObject()));
    }

    @Test
    void testTimeRepresentations() {
        var root = new SchemaTimeValues__JsonSchema().schema().rootObject().orElseThrow();
        assertThat(root.stringProperties().size(), is(11));
        assertThat(root.stringProperties().get("date").format().orElseThrow(), is("date"));
        assertThat(root.stringProperties().get("date").required(), is(true));
        assertThat(root.stringProperties().get("date").description().orElseThrow(), is("A date"));
        assertThat(root.stringProperties().get("instant").format().orElseThrow(), is("date-time"));
        assertThat(root.stringProperties().get("month").enumValues().orElseThrow(),
                   is(JsonArray.createStrings(List.of("JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY",
                                                      "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER")).values()));
        assertThat(root.stringProperties().get("month").title().orElseThrow(), is("Month"));
        assertThat(root.stringProperties().get("month").required(), is(true));
        assertThat(root.stringProperties().get("dayOfWeek").enumValues().orElseThrow(),
                   is(JsonArray.createStrings(List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY",
                                                      "SUNDAY")).values()));
        for (String name : List.of("localDateTime", "localTime", "offsetDateTime", "offsetTime",
                                   "zonedDateTime", "duration", "period")) {
            assertThat(name + " must not claim an incompatible standard format",
                       root.stringProperties().get(name).format().isEmpty(), is(true));
        }
    }

    @Test
    void testRootEnumUsesNames() {
        var provider = new SchemaState__JsonSchema();
        var root = provider.schema().rootString().orElseThrow();
        assertThat(root.enumValues().orElseThrow(), is(JsonArray.createStrings(List.of("ON", "OFF")).values()));
        assertThat(root.description().orElseThrow(), is("An enabled or disabled state"));
        assertThat(JsonParser.create(provider.schema().generate()).readJsonObject(),
                   jsonEqualTo(JsonParser.create(provider.jsonSchema()).readJsonObject()));
    }

    @Test
    void testEmptyEnumKeepsConstraint() {
        var provider = new SchemaEmptyState__JsonSchema();
        var generated = JsonParser.create(provider.jsonSchema()).readJsonObject();
        assertThat(generated.arrayValue("enum").orElseThrow().values(), is(List.of()));
        assertThat(provider.schema().rootString().orElseThrow().enumValues().orElseThrow(), is(List.of()));
        assertThat(JsonParser.create(provider.schema().generate()).readJsonObject(), jsonEqualTo(generated));
    }

    // JsonObject on 4.x does not compare exact key/value associations. Compare typed values instead.
    private static Matcher<Object> jsonEqualTo(Object expected) {
        return new FeatureMatcher<>(is(jsonValue(expected)), "JSON value", "JSON value") {
            @Override
            protected Object featureValueOf(Object actual) {
                return jsonValue(actual);
            }
        };
    }

    private static Object jsonValue(Object value) {
        if (value instanceof List<?> values) {
            return values.stream().map(SchemaEnumTimeTest::jsonValue).toList();
        }
        JsonValue json = (JsonValue) value;
        return switch (json.type()) {
            case STRING -> json.asString().value();
            case NUMBER -> json.asNumber().bigDecimalValue();
            case BOOLEAN -> json.asBoolean().value();
            case NULL -> JsonNull.instance();
            case UNKNOWN -> throw new AssertionError("Unknown JSON value: " + json);
            case ARRAY -> jsonValue(json.asArray().values());
            case OBJECT -> {
                Map<String, Object> properties = new LinkedHashMap<>();
                json.asObject().keysAsStrings()
                        .forEach(key -> properties.put(key, jsonValue(json.asObject().value(key).orElseThrow())));
                yield properties;
            }
        };
    }
}
