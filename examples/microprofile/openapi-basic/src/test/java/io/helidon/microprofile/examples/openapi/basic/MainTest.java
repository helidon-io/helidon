/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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

import io.helidon.microprofile.examples.openapi.basic.internal.SimpleAPIModelReader;
import io.helidon.microprofile.server.Server;

import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonPointer;
import jakarta.json.JsonString;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Disabled("3.0.0-JAKARTA") // OpenAPI: Caused by: java.lang.NoSuchMethodError:
        // 'java.util.List org.jboss.jandex.ClassInfo.unsortedFields()'
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
        assertThat("default message", jsonObject.getString("message"),
                is("Hello World!"));

        jsonObject = client
                .target(getConnectionString("/greet/Joe"))
                .request()
                .get(JsonObject.class);
        assertThat("hello Joe message", jsonObject.getString("message"),
                is("Hello Joe!"));

        try (Response r = client
                .target(getConnectionString("/greet/greeting"))
                .request()
                .put(Entity.entity("{\"greeting\" : \"Hola\"}", MediaType.APPLICATION_JSON))) {
            assertThat("PUT status code", r.getStatus(), is(204));
        }

        jsonObject = client
                .target(getConnectionString("/greet/Jose"))
                .request()
                .get(JsonObject.class);
        assertThat("hola Jose message", jsonObject.getString("message"),
                is("Hola Jose!"));

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
        assertThat("/test/newpath GET summary did not match", js.getString(), is(SimpleAPIModelReader.SUMMARY));

        jp = Json.createPointer("/" + escape(SimpleAPIModelReader.DOOMED_PATH));
        assertThat("/test/doomed should not appear but does", jp.containsValue(paths), is(false));

        jp = Json.createPointer("/" + escape("/greet") + "/get/summary");
        js = JsonString.class.cast(jp.getValue(paths));
        assertThat("/greet GET summary did not match", js.getString(), is("Returns a generic greeting"));

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
