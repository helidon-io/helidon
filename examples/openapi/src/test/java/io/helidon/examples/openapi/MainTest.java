/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.openapi;

import java.util.Map;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonPointer;
import jakarta.json.JsonString;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
public class MainTest {

    private static final JsonBuilderFactory JSON_BF = Json.createBuilderFactory(Map.of());
    private static final JsonObject TEST_JSON_OBJECT = JSON_BF.createObjectBuilder()
            .add("greeting", "Hola")
            .build();

    private final Http1Client client;

    public MainTest(Http1Client client) {
        this.client = client;
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder server) {
        Main.setup(server);
    }

    @Test
    public void testHelloWorld() {
        try (Http1ClientResponse response = client.get("/greet").request()) {
            JsonObject jsonObject = response.as(JsonObject.class);
            assertThat(jsonObject.getString("greeting"), is("Hello World!"));
        }

        try (Http1ClientResponse response = client.get("/greet/Joe").request()) {
            JsonObject jsonObject = response.as(JsonObject.class);
            assertThat(jsonObject.getString("greeting"), is("Hello Joe!"));
        }

        try (Http1ClientResponse response = client.put("/greet/greeting").submit(TEST_JSON_OBJECT)) {
            assertThat(response.status().code(), is(204));
        }

        try (Http1ClientResponse response = client.get("/greet/Joe").request()) {
            JsonObject jsonObject = response.as(JsonObject.class);
            assertThat(jsonObject.getString("greeting"), is("Hola Joe!"));
        }

        try (Http1ClientResponse response = client.get("/observe/health").request()) {
            assertThat(response.status(), is(Status.NO_CONTENT_204));
        }

        try (Http1ClientResponse response = client.get("/observe/metrics").request()) {
            assertThat(response.status().code(), is(200));
        }
    }

    @Test
    public void testOpenAPI() {
        JsonObject jsonObject = client.get("/openapi")
                .accept(MediaTypes.APPLICATION_JSON)
                .requestEntity(JsonObject.class);
        JsonObject paths = jsonObject.getJsonObject("paths");

        JsonPointer jp = Json.createPointer("/" + escape("/greet/greeting") + "/put/summary");
        JsonString js = (JsonString) jp.getValue(paths);
        assertThat("/greet/greeting.put.summary not as expected", js.getString(), is("Set the greeting prefix"));
    }

    private static String escape(String path) {
        return path.replace("/", "~1");
    }

}
