/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.examples.openapi;

import io.helidon.microprofile.examples.openapi.internal.SimpleAPIModelReader;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonPointer;
import jakarta.json.JsonString;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
class MainTest {

    private final WebTarget target;

    @Inject
    MainTest(WebTarget target) {
        this.target = target;
    }

    @Test
    void testHelloWorld() {
        GreetingMessage message = target.path("/greet")
                .request()
                .get(GreetingMessage.class);
        assertThat("default message", message.getMessage(),
                   is("Hello World!"));

        message = target.path("/greet/Joe")
                .request()
                .get(GreetingMessage.class);
        assertThat("hello Joe message", message.getMessage(),
                   is("Hello Joe!"));

        try (Response r = target.path("/greet/greeting")
                .request()
                .put(Entity.entity("{\"message\" : \"Hola\"}", MediaType.APPLICATION_JSON))) {
            assertThat("PUT status code", r.getStatus(), is(204));
        }

        message = target.path("/greet/Jose")
                .request()
                .get(GreetingMessage.class);
        assertThat("hola Jose message", message.getMessage(),
                   is("Hola Jose!"));
    }

    @Test
    public void testOpenAPI() {
        JsonObject jsonObject = target.path("/openapi")
                .request(MediaType.APPLICATION_JSON)
                .get(JsonObject.class);
        JsonObject paths = jsonObject.get("paths").asJsonObject();

        JsonPointer jp = Json.createPointer("/" + escape(SimpleAPIModelReader.MODEL_READER_PATH) + "/get/summary");
        JsonString js = (JsonString) jp.getValue(paths);
        assertThat("/test/newpath GET summary did not match", js.getString(), is(SimpleAPIModelReader.SUMMARY));

        jp = Json.createPointer("/" + escape(SimpleAPIModelReader.DOOMED_PATH));
        assertThat("/test/doomed should not appear but does", jp.containsValue(paths), is(false));

        jp = Json.createPointer("/" + escape("/greet") + "/get/summary");
        js = (JsonString) jp.getValue(paths);
        assertThat("/greet GET summary did not match", js.getString(), is("Returns a generic greeting"));
    }

    private static String escape(String path) {
        return path.replace("/", "~1");
    }
}
