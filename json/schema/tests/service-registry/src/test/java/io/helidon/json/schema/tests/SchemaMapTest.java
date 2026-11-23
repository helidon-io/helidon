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
import static org.hamcrest.Matchers.containsInAnyOrder;

class SchemaMapTest {
    @Test
    void testGeneratedMapProperties() {
        var properties = JsonParser.create(new SchemaMaps__JsonSchema().jsonSchema())
                .readJsonObject()
                .objectValue("properties").orElseThrow();

        for (String name : List.of("values", "counts", "concreteValues")) {
            assertThat(name, properties.objectValue(name).orElseThrow().stringValue("type").orElseThrow(), is("object"));
        }
        assertThat(properties.objectValue("values").orElseThrow(), is(JsonObject.builder().set("type", "object").build()));
    }

    @Test
    void testMapAnnotations() {
        var root = new SchemaMaps__JsonSchema().schema().rootObject().orElseThrow();
        assertThat(root.objectProperties().keySet(), containsInAnyOrder("values", "counts", "concreteValues"));

        var counts = root.objectProperties().get("counts");
        assertThat(counts.required(), is(true));
        assertThat(counts.title().orElseThrow(), is("Counts"));
        assertThat(counts.description().orElseThrow(), is("Named counts"));
        assertThat(counts.minProperties().orElseThrow(), is(1));
        assertThat(counts.maxProperties().orElseThrow(), is(5));
        assertThat(counts.additionalProperties().orElseThrow(), is(true));
        assertThat(root.objectProperties().get("values").required(), is(false));
    }

    @Test
    void testProviderSchemaRoundTrip() {
        var provider = new SchemaMaps__JsonSchema();
        assertThat(provider.schema().generateObject(), is(JsonParser.create(provider.jsonSchema()).readJsonObject()));
    }

    @Test
    void testOpaquePropertiesRemainUnconstrained() {
        var properties = JsonParser.create(new SchemaOpaqueValues__JsonSchema().jsonSchema())
                .readJsonObject()
                .objectValue("properties").orElseThrow();

        assertThat(properties.objectValue("id").orElseThrow(), is(JsonObject.builder().build()));
        assertThat(properties.objectValue("value").orElseThrow(), is(JsonObject.builder().build()));
    }
}
