/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.media.jsonp.server;

import io.helidon.common.GenericType;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.TimeUnit;

import javax.json.JsonArray;
import javax.json.JsonException;
import javax.json.JsonObject;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Multi;
import io.helidon.media.common.MessageBodyReaderContext;

import org.hamcrest.core.Is;
import org.hamcrest.core.IsInstanceOf;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The JsonContentReaderTest.
 */
public class JsonContentReaderTest {

    private final static MessageBodyReaderContext CONTEXT = MessageBodyReaderContext.create();

    @Test
    public void simpleJsonObject() throws Exception {
        Publisher<DataChunk> chunks = Multi.just("{ \"p\" : \"val\" }").map(s -> DataChunk.create(s.getBytes()));

        CompletionStage<? extends JsonObject> stage = JsonSupport.create()
                .reader()
                .read(chunks, GenericType.create(JsonObject.class), CONTEXT)
                .toStage();

        JsonObject jsonObject = stage.toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertThat(jsonObject.getJsonString("p").getString(), Is.is("val"));
    }

    @Test
    public void incompatibleTypes() throws Exception {
        Publisher<DataChunk> chunks = Multi.just("{ \"p\" : \"val\" }").map(s -> DataChunk.create(s.getBytes()));

        CompletionStage<? extends JsonArray> stage = JsonSupport.create()
                .reader()
                .read(chunks, GenericType.create(JsonArray.class), CONTEXT)
                .toStage();

        try {
            JsonArray array = stage.thenApply(o -> {
                fail("Shouldn't occur because of JSON exception!");
                return o;
            }).toCompletableFuture().get(10, TimeUnit.SECONDS);
            fail("Should have failed because an expected array is actually an object: " + array);
        } catch (ExecutionException e) {
            assertThat(e.getCause(), IsInstanceOf.instanceOf(JsonException.class));
        }
    }

    @Test
    public void simpleJsonArray() throws Exception {
        Publisher<DataChunk> chunks = Multi.just("[ \"val\" ]").map(s -> DataChunk.create(s.getBytes()));

        CompletionStage<? extends JsonArray> stage = JsonSupport.create()
                .reader()
                .read(chunks, GenericType.create(JsonArray.class), CONTEXT)
                .toStage();

        JsonArray array = stage.toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertThat(array.getString(0), Is.is("val"));
    }

    @Test
    public void invalidJson() throws Exception {
        Publisher<DataChunk> chunks = Multi.just("{ \"p\" : \"val\" ").map(s -> DataChunk.create(s.getBytes()));

        CompletionStage<? extends JsonObject> stage = JsonSupport.create()
                .reader()
                .read(chunks, GenericType.create(JsonObject.class), CONTEXT)
                .toStage();
        try {
            stage.toCompletableFuture().get(10, TimeUnit.SECONDS);
            fail("Should have thrown an exception");
        } catch (ExecutionException e) {
            assertThat(stage.toCompletableFuture().isCompletedExceptionally(), is(true));
            assertThat(e.getCause(), IsInstanceOf.instanceOf(JsonException.class));
        }
    }

    @Test
    public void defaultJsonSupportAsSingleton() {
        assertThat(JsonSupport.create(), sameInstance(JsonSupport.create()));
    }
}
