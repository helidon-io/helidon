/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.openapi;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.smallrye.openapi.runtime.io.Format;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonReaderFactory;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

class SerializerTest {
    private static final JsonReaderFactory JSON_READER_FACTORY
            = Json.createReaderFactory(Collections.emptyMap());
    private static ParserHelper helper;

    private static Map<Class<?>, ExpandedTypeDescription> implsToTypes;

    @BeforeAll
    static void prepareHelper() {
        helper = ParserHelper.create();
        implsToTypes = ExpandedTypeDescription.buildImplsToTypes(helper);
    }

    @Test
    public void testJSONSerialization() throws IOException {
        OpenAPI openAPI = ParserTest.parse(helper, "/openapi-greeting.yml");
        Writer writer = new StringWriter();
        Serializer.serialize(helper.types(), implsToTypes, openAPI, Format.JSON, writer);
        JsonStructure json = jsonFromReader(new StringReader(writer.toString()));

        String greetGreetingPut = "/paths/~1greet~1greeting/put";
        assertThat(json.getValue(greetGreetingPut + "/x-put-ext-0").toString(), is("\"put-ext-0-value\""));
        assertThat(json.getValue(greetGreetingPut + "/x-put-ext-1").toString(), is("\"put-ext-1-value\""));
        assertThat(json.getValue(greetGreetingPut + "/responses/x-responses-ext").toString(),
                   is("\"responses-ext-0-value\""));
        assertThat(json.getValue(greetGreetingPut + "/responses/204/x-204-ext").toString(),
                   is("\"204-ext-value\""));
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

        JsonValue xInt = json.getValue("/x-int");
        assertThat(xInt.getValueType(), is(JsonValue.ValueType.NUMBER));
        assertThat(Integer.valueOf(xInt.toString()), is(117));

        JsonValue xBoolean = json.getValue("/x-boolean");
        assertThat(xBoolean.getValueType(), is(JsonValue.ValueType.TRUE));

        JsonValue xStrings = json.getValue("/x-string-array");
        assertThat(xStrings.getValueType(), is(JsonValue.ValueType.ARRAY));
        JsonArray xStringArray = xStrings.asJsonArray();
        assertThat(xStringArray.size(), is(2));
        checkJsonStringValue(xStringArray.get(0), "one");
        checkJsonStringValue(xStringArray.get(1), "two");

        JsonValue xObjects = json.getValue("/x-object-array");
        assertThat(xObjects.getValueType(), is(JsonValue.ValueType.ARRAY));
        JsonArray xObjectArray = xObjects.asJsonArray();
        assertThat(xObjectArray.size(), is(2));
        first = xObjectArray.get(0);
        assertThat(first.getValueType(), is(JsonValue.ValueType.OBJECT));
        firstObj = first.asJsonObject();
        checkJsonPathStringValue(firstObj, "/name", "item-1");
        checkJsonPathIntValue(firstObj, "/value", 16);
        second = xObjectArray.get(1);
        assertThat(second.getValueType(), is(JsonValue.ValueType.OBJECT));
        secondObj = second.asJsonObject();
        checkJsonPathStringValue(secondObj, "/name", "item-2");
        checkJsonPathIntValue(secondObj, "/value", 18);

    }

    private void checkJsonPathStringValue(JsonObject jsonObject, String pointer, String expected) {
        checkJsonStringValue(jsonObject.getValue(pointer), expected);
    }

    private void checkJsonStringValue(JsonValue jsonValue, String expected) {
        assertThat(jsonValue.getValueType(), is(JsonValue.ValueType.STRING));
        assertThat(jsonValue.toString(), is("\"" + expected + "\""));
    }

    private void checkJsonPathIntValue(JsonObject jsonObject, String pointer, int expected) {
        checkJsonIntValue(jsonObject.getValue(pointer), expected);
    }

    private void checkJsonIntValue(JsonValue val, int expected) {
        assertThat(val.getValueType(), is(JsonValue.ValueType.NUMBER));
        assertThat(Integer.valueOf(val.toString()), is(expected));
    }

    @Test
    public void testYAMLSerialization() throws IOException {
        OpenAPI openAPI = ParserTest.parse(helper, "/openapi-greeting.yml");
        Writer writer = new StringWriter();
        Serializer.serialize(helper.types(), implsToTypes, openAPI, Format.YAML, writer);
        try (Reader reader = new StringReader(writer.toString())) {
            openAPI = OpenAPIParser.parse(helper.types(), reader);
        }
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

    @Test
    void testRefSerializationAsOpenAPI() throws IOException {
        OpenAPI openAPI = ParserTest.parse(helper, "/petstore.yaml");
        Writer writer = new StringWriter();
        Serializer.serialize(helper.types(), implsToTypes, openAPI, Format.YAML, writer);

        try (Reader reader = new StringReader(writer.toString())) {
            openAPI = OpenAPIParser.parse(helper.types(), reader);
        }

        String ref = openAPI.getPaths()
                .getPathItem("/pets")
                .getGET()
                .getResponses()
                .getDefaultValue()
                .getContent()
                .getMediaType("application/json")
                .getSchema()
                .getRef();
        assertThat("/pets.GET.responses.default.content.application/json.schema.ref", ref,
                is(equalTo("#/components/schemas/Error")));
    }

    @Test
    void testRefSerializationAsText() throws IOException {
        // This test basically replicates the other ref test but without re-parsing, just in case there might be
        // compensating bugs in the parsing and the serialization.
        Pattern refPattern = Pattern.compile("\\s\\$ref\\: '([^']+)");

        OpenAPI openAPI = ParserTest.parse(helper, "/petstore.yaml");
        Writer writer = new StringWriter();
        Serializer.serialize(helper.types(), implsToTypes, openAPI, Format.YAML, writer);

        try (LineNumberReader reader = new LineNumberReader(new StringReader(writer.toString()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher refMatcher = refPattern.matcher(line);
                if (refMatcher.matches()) {
                    assertThat("Apparent reference to component", refMatcher.group(1), startsWith("#/components"));
                }
            }
        }
    }

    private static JsonStructure jsonFromReader(Reader reader) {
        JsonReader jsonReader = JSON_READER_FACTORY.createReader(reader);
        JsonStructure result = jsonReader.read();
        jsonReader.close();
        return result;
    }
}