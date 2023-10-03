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

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import io.helidon.common.http.MediaType;
import io.helidon.examples.openapi.internal.SimpleAPIModelReader;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.WebServer;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonPointer;
import jakarta.json.JsonString;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class MainTest {

    private static WebServer webServer;
    private static WebClient webClient;

    private static final JsonBuilderFactory JSON_BF = Json.createBuilderFactory(Collections.emptyMap());
    private static final JsonObject TEST_JSON_OBJECT;

    static {
        TEST_JSON_OBJECT = JSON_BF.createObjectBuilder()
                .add("greeting", "Hola")
                .build();
    }

    @BeforeAll
    public static void startTheServer() {
        webServer = Main.startServer().await();

        webClient = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port())
                .addMediaSupport(JsonpSupport.create())
                .build();
    }

    @AfterAll
    public static void stopServer() {
        if (webServer != null) {
            webServer.shutdown()
                    .await(10, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testHelloWorld() {
        webClient.get()
                .path("/greet")
                .request(JsonObject.class)
                .thenAccept(jsonObject -> assertThat(jsonObject.getString("greeting"), is("Hello World!")))
                .await();

        webClient.get()
                .path("/greet/Joe")
                .request(JsonObject.class)
                .thenAccept(jsonObject -> assertThat(jsonObject.getString("greeting"), is("Hello Joe!")))
                .await();

        webClient.put()
                .path("/greet/greeting")
                .submit(TEST_JSON_OBJECT)
                .thenAccept(response -> assertThat(response.status().code(), is(204)))
                .thenCompose(nothing -> webClient.get()
                        .path("/greet/Joe")
                        .request(JsonObject.class))
                .thenAccept(jsonObject -> assertThat(jsonObject.getString("greeting"), is("Hola Joe!")))
                .await();

        webClient.get()
                .path("/health")
                .request()
                .thenAccept(response -> {
                    assertThat(response.status().code(), is(200));
                    response.close();
                })
                .await();

        webClient.get()
                .path("/metrics")
                .request()
                .thenAccept(response -> {
                    assertThat(response.status().code(), is(200));
                    response.close();
                })
                .await();
    }

    @Test
    public void testOpenAPI() {
        /*
         * If you change the OpenAPI endpoint path in application.yaml, then
         * change the following path also.
         */
        JsonObject jsonObject = webClient.get()
                .accept(MediaType.APPLICATION_JSON)
                .path("/openapi")
                .request(JsonObject.class)
                .await();
        JsonObject paths = jsonObject.getJsonObject("paths");

        JsonPointer jp = Json.createPointer("/" + escape("/greet/greeting") + "/put/summary");
        JsonString js = (JsonString) jp.getValue(paths);
        assertThat("/greet/greeting.put.summary not as expected", js.getString(), is("Set the greeting prefix"));

        jp = Json.createPointer("/" + escape(SimpleAPIModelReader.MODEL_READER_PATH)
                                        + "/get/summary");
        js = (JsonString) jp.getValue(paths);
        assertThat("summary added by model reader does not match", js.getString(),
                is(SimpleAPIModelReader.SUMMARY));

        jp = Json.createPointer("/" + escape(SimpleAPIModelReader.DOOMED_PATH));
        assertThat("/test/doomed should not appear but does", jp.containsValue(paths), is(false));
    }

    private static String escape(String path) {
        return path.replace("/", "~1");
    }

}
