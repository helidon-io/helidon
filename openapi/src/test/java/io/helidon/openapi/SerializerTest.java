/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
 *
 */
package io.helidon.openapi;

import io.smallrye.openapi.runtime.io.OpenApiSerializer;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonStructure;
import javax.json.JsonValue;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

class SerializerTest {

    @Test
    public void testJSONSerialization() throws IOException {
        OpenAPI openAPI = ParserTest.parse("/openapi-greeting.yml", OpenAPISupport.OpenAPIMediaType.YAML);
        Writer writer = new StringWriter();
        Serializer.serialize(openAPI, OpenApiSerializer.Format.JSON, writer);
        JsonStructure json = TestUtil.jsonFromReader(new StringReader(writer.toString()));

        assertThat(json.getValue("/x-my-personal-map/owner/last").toString(), is("\"Myself\""));
        JsonValue otherItem = json.getValue("/x-other-item");
        assertThat(otherItem.getValueType(), is(JsonValue.ValueType.NUMBER));
        assertThat(Double.valueOf(otherItem.toString()), is(10.0));

        JsonValue seq = json.getValue("/info/x-my-personal-seq");
        assertThat(seq.getValueType(), is(JsonValue.ValueType.ARRAY));
        JsonArray seqArray = seq.asJsonArray();
        JsonValue first = seqArray.get(0);
        assertThat(first.getValueType(), is(JsonValue.ValueType.OBJECT));
        JsonObject firstObj = first.asJsonObject();
        checkJsonPathStringValue(firstObj, "/who", "Prof. Plum");
        checkJsonPathStringValue(firstObj, "/why", "felt like it");

        JsonValue second = seqArray.get(1);
        assertThat(second.getValueType(), is(JsonValue.ValueType.OBJECT));
        JsonObject secondObj = second.asJsonObject();
        checkJsonPathStringValue(secondObj, "/when", "yesterday");
        checkJsonPathStringValue(secondObj, "/how", "with the lead pipe");
    }

    private void checkJsonPathStringValue(JsonObject jsonObject, String pointer, String expected) {
        JsonValue who = jsonObject.getValue(pointer);
        assertThat(who.getValueType(), is(JsonValue.ValueType.STRING));
        assertThat(who.toString(), is("\"" + expected + "\""));
    }

    @Test
    public void testYAMLSerialization() throws IOException {
        OpenAPI openAPI = ParserTest.parse("/openapi-greeting.yml", OpenAPISupport.OpenAPIMediaType.YAML);
        Writer writer = new StringWriter();
        Serializer.serialize(openAPI, OpenApiSerializer.Format.YAML, writer);
        openAPI = Parser.parseYAML(new StringReader(writer.toString()));
        Object candidateMap = openAPI.getExtensions()
                .get("x-my-personal-map");
        assertThat(candidateMap, is(instanceOf(Map.class)));

        Map<?, ?> map = (Map) candidateMap;
        Object candidateOwnerMap = map.get("owner");
        assertThat(candidateOwnerMap, is(instanceOf(Map.class)));

        Map<?, ?> ownerMap = (Map) candidateOwnerMap;
        assertThat(ownerMap.get("last"), is("Myself"));

        List<String> required = openAPI.getPaths().getPathItem("/greet/greeting")
                .getPUT()
                .getRequestBody()
                .getContent()
                .getMediaType("application/json")
                .getSchema()
                .getRequired();
        assertThat(required, hasItem("greeting"));
    }
}
