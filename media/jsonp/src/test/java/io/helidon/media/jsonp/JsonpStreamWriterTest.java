/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.media.jsonp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonReaderFactory;
import javax.json.JsonStructure;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.HashParameters;
import io.helidon.common.reactive.Multi;
import io.helidon.media.common.MessageBodyOperator;
import io.helidon.media.common.MessageBodyWriterContext;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * The JsonContentReaderTest.
 */
public class JsonpStreamWriterTest {
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());
    private static final JsonReaderFactory JSON_PARSER = Json.createReaderFactory(Map.of());

    private static final MessageBodyWriterContext CONTEXT = MessageBodyWriterContext.create(HashParameters.create());
    private static final JsonpBodyStreamWriter WRITER = JsonpSupport.create().newStreamWriter();
    private static final GenericType<JsonObject> JSON_OBJECT = GenericType.create(JsonObject.class);
    private static final GenericType<JsonArray> JSON_ARRAY = GenericType.create(JsonArray.class);
    private static final GenericType<JsonpStreamWriterTest> MY_TYPE = GenericType.create(JsonpStreamWriterTest.class);

    @Test
    void testAcceptedTypes() {
        assertAll(
                () -> assertThat("JsonObject accepted",
                                 WRITER.accept(JSON_OBJECT, CONTEXT),
                                 is(MessageBodyOperator.PredicateResult.SUPPORTED)),
                () -> assertThat("JsonArray accepted",
                                 WRITER.accept(JSON_ARRAY, CONTEXT),
                                 is(MessageBodyOperator.PredicateResult.SUPPORTED)),
                () -> assertThat("Pojo not accepted",
                                 WRITER.accept(MY_TYPE, CONTEXT),
                                 is(MessageBodyOperator.PredicateResult.NOT_SUPPORTED))
        );
    }

    @Test
    void simpleJsonObject() {
        Multi<JsonObject> publisher = Multi.just(createObject(0));
        JsonArray result = writeJsonObjects(publisher);

        assertThat(result, hasSize(1));
        JsonObject first = result.getJsonObject(0);
        assertThat(first.getString("p"), is("val_0"));
    }

    @Test
    void aFewJsonObjects() {
        Multi<JsonObject> publisher = Multi.just(createObject(0), createObject(1), createObject(2));
        JsonArray result = writeJsonObjects(publisher);

        assertThat(result, hasSize(3));
        JsonObject object = result.getJsonObject(0);
        assertThat(object.getString("p"), is("val_0"));
        object = result.getJsonObject(1);
        assertThat(object.getString("p"), is("val_1"));
        object = result.getJsonObject(2);
        assertThat(object.getString("p"), is("val_2"));
    }

    @Test
    void simpleJsonArray() {
        Multi<JsonArray> publisher = Multi.just(createArray(1));
        JsonArray result = writeJsonArrays(publisher);

        assertThat(result, hasSize(1));
        JsonArray array = result.getJsonArray(0);
        JsonObject object = array.getJsonObject(0);
        assertThat(object.getString("p"), is("val_0_0"));
    }

    @Test
    void aFewJsonArrays() {
        Multi<JsonArray> publisher = Multi.just(createArray(3));
        JsonArray result = writeJsonArrays(publisher);

        assertThat(result, hasSize(3));
        for (int i = 0; i < 3; i++) {
            JsonArray array = result.getJsonArray(i);
            JsonObject object = array.getJsonObject(0);
            assertThat(object.getString("p"), is("val_" + i + "_" + 0));
        }

    }

    private static JsonArray[] createArray(int size) {
        JsonArray[] arrays = new JsonArray[size];

        for (int i = 0; i < size; i++) {
            JsonArrayBuilder arrayBuilder = JSON.createArrayBuilder();
            arrayBuilder.add(createObject(i, 0));
            arrays[i] = arrayBuilder.build();
        }

        return arrays;
    }

    private static JsonObject createObject(int arrayIndex, int objectIndex) {
        return JSON.createObjectBuilder()
                .add("p", "val_" + arrayIndex + "_" + objectIndex)
                .build();
    }

    private static JsonObject createObject(int index) {
        return JSON.createObjectBuilder()
                .add("p", "val_" + index)
                .build();
    }

    private JsonArray writeJsonObjects(Multi<JsonObject> publisher) {
        return write(publisher, JSON_OBJECT);
    }

    private JsonArray writeJsonArrays(Multi<JsonArray> publisher) {
        return write(publisher, JSON_ARRAY);
    }

    private JsonArray write(Multi<? extends JsonStructure> publisher, GenericType<? extends JsonStructure> type) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        WRITER.write(publisher, type, CONTEXT)
                .map(DataChunk::bytes)
                .forEach(it -> {
                    try {
                        baos.write(it);
                    } catch (IOException ignored) {
                        // ignored
                    }
                })
                .await();

        return JSON_PARSER.createReader(new ByteArrayInputStream(baos.toByteArray())).readArray();
    }
}
