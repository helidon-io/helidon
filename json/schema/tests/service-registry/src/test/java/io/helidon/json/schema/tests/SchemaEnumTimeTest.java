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

import java.util.List;

import io.helidon.json.JsonObject;
import io.helidon.json.JsonParser;

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
                   is(JsonObject.builder().set("type", "string").setStrings("enum", List.of("ON", "OFF")).build()));
    }

    @Test
    void testDateProperty() {
        var properties = JsonParser.create(new SchemaItemRecord__JsonSchema().jsonSchema())
                .readJsonObject()
                .objectValue("properties").orElseThrow();
        assertThat(properties.objectValue("since").orElseThrow(),
                   is(JsonObject.builder().set("type", "string").set("format", "date").build()));
    }

    @Test
    void testProviderSchema() {
        var provider = new SchemaItemRecord__JsonSchema();
        var root = provider.schema().rootObject().orElseThrow();
        assertThat(root.stringProperties().size(), is(3));
        assertThat(root.stringProperties().get("state").enumValues(), is(List.of("ON", "OFF")));
        assertThat(root.stringProperties().get("since").format().orElseThrow(), is("date"));
        assertThat(provider.schema().generateObject(), is(JsonParser.create(provider.jsonSchema()).readJsonObject()));
    }

    @Test
    void testTimeRepresentations() {
        var root = new SchemaTimeValues__JsonSchema().schema().rootObject().orElseThrow();
        assertThat(root.stringProperties().size(), is(11));
        assertThat(root.stringProperties().get("date").format().orElseThrow(), is("date"));
        assertThat(root.stringProperties().get("date").required(), is(true));
        assertThat(root.stringProperties().get("date").description().orElseThrow(), is("A date"));
        assertThat(root.stringProperties().get("instant").format().orElseThrow(), is("date-time"));
        assertThat(root.stringProperties().get("month").enumValues(),
                   is(List.of("JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY", "AUGUST",
                              "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER")));
        assertThat(root.stringProperties().get("month").title().orElseThrow(), is("Month"));
        assertThat(root.stringProperties().get("month").required(), is(true));
        assertThat(root.stringProperties().get("dayOfWeek").enumValues(),
                   is(List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")));
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
        assertThat(root.enumValues(), is(List.of("ON", "OFF")));
        assertThat(root.description().orElseThrow(), is("An enabled or disabled state"));
        assertThat(provider.schema().generateObject(), is(JsonParser.create(provider.jsonSchema()).readJsonObject()));
    }
}
