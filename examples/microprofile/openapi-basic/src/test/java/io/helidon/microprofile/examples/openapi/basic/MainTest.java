/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.microprofile.examples.openapi.basic;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.spi.CDI;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonPointer;
import javax.json.JsonString;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.helidon.microprofile.examples.openapi.basic.internal.SimpleAPIModelReader;
import io.helidon.microprofile.server.Server;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MainTest {
    private static Server server;

    @BeforeAll
    public static void startTheServer() throws Exception {
        server = startServer();
    }

    @Test
    void testHelloWorld() {

        Client client = ClientBuilder.newClient();

        JsonObject jsonObject = client
                .target(getConnectionString("/greet"))
                .request()
                .get(JsonObject.class);
        Assertions.assertEquals("Hello World!", jsonObject.getString("message"),
                "default message");

        jsonObject = client
                .target(getConnectionString("/greet/Joe"))
                .request()
                .get(JsonObject.class);
        Assertions.assertEquals("Hello Joe!", jsonObject.getString("message"),
                "hello Joe message");

        try (Response r = client
                .target(getConnectionString("/greet/greeting"))
                .request()
                .put(Entity.entity("{\"greeting\" : \"Hola\"}", MediaType.APPLICATION_JSON))) {
            Assertions.assertEquals(204, r.getStatus(), "PUT status code");
        }

        jsonObject = client
                .target(getConnectionString("/greet/Jose"))
                .request()
                .get(JsonObject.class);
        Assertions.assertEquals("Hola Jose!", jsonObject.getString("message"),
                "hola Jose message");

        client.close();
    }

    @Test
    public void testOpenAPI() {

        Client client = ClientBuilder.newClient();

        JsonObject jsonObject = client
                .target(getConnectionString("/openapi"))
                .request(MediaType.APPLICATION_JSON)
                .get(JsonObject.class);
        JsonObject paths = jsonObject.get("paths").asJsonObject();

        JsonPointer jp = Json.createPointer("/" + escape(SimpleAPIModelReader.MODEL_READER_PATH) + "/get/summary");
        JsonString js = JsonString.class.cast(jp.getValue(paths));
        Assertions.assertEquals(SimpleAPIModelReader.SUMMARY, js.getString(), "/test/newpath GET summary did not match");

        jp = Json.createPointer("/" + escape(SimpleAPIModelReader.DOOMED_PATH));
        Assertions.assertFalse(jp.containsValue(paths), "/test/doomed should not appear but does");

        jp = Json.createPointer("/" + escape("/greet") + "/get/summary");
        js = JsonString.class.cast(jp.getValue(paths));
        Assertions.assertEquals("Returns a generic greeting", js.getString(), "/greet GET summary did not match");

        client.close();
    }

    @AfterAll
    static void destroyClass() {
        CDI<Object> current = CDI.current();
        ((SeContainer) current).close();
    }

    private String getConnectionString(String path) {
        return "http://localhost:" + server.port() + path;
    }

    /**
     * Start the server.
     * @return the created {@link Server} instance
     */
    static Server startServer() {
        // Server will automatically pick up configuration from
        // microprofile-config.properties
        // and Application classes annotated as @ApplicationScoped
        return Server.create().start();
    }

    private String escape(String path) {
        return path.replace("/", "~1");
    }
}
