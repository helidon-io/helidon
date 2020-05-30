/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

import java.util.concurrent.ExecutionException;

import javax.json.JsonArray;
import javax.json.JsonObject;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Single;
import io.helidon.media.common.MessageBodyReaderContext;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The JsonContentReaderTest.
 */
public class JsonpReaderTest {

    private final static MessageBodyReaderContext CONTEXT = MessageBodyReaderContext.create();

    private final static JsonpBodyReader READER = JsonpSupport.create().newReader();

    @Test
    public void simpleJsonObject() throws Exception {
        JsonObject jsonObject = readJsonObject("{ \"p\" : \"val\" }");
        assertThat(jsonObject, is(notNullValue()));
        assertThat(jsonObject.getJsonString("p").getString(), is(equalTo("val")));
    }

    @Test
    public void incompatibleTypes() throws Exception {
        assertThrows(ExecutionException.class, () -> readJsonArray("{ \"p\" : \"val\" }"));
    }

    @Test
    public void simpleJsonArray() throws Exception {
        JsonArray array = readJsonArray("[ \"val\" ]");
        assertThat(array, is(notNullValue()));
        assertThat(array.getString(0), is(equalTo("val")));
    }

    @Test
    public void invalidJson() throws Exception {
        assertThrows(ExecutionException.class, () -> readJsonObject("{ \"p\" : \"val\" "));
    }

    private static JsonObject readJsonObject(String json) throws Exception {
        return READER.read(Single.just(DataChunk.create(json.getBytes())), GenericType.create(JsonObject.class), CONTEXT).get();
    }

    private static JsonArray readJsonArray(String json) throws Exception {
        return READER.read(Single.just(DataChunk.create(json.getBytes())), GenericType.create(JsonArray.class), CONTEXT).get();
    }
}
