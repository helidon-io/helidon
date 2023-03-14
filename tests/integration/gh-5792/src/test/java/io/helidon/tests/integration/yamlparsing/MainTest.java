/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.yamlparsing;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.Collections;
import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;

import io.helidon.common.http.MediaType;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class MainTest {

    private static final JsonBuilderFactory JSON_BUILDER = Json.createBuilderFactory(Collections.emptyMap());
    private static final JsonObject TEST_JSON_OBJECT = JSON_BUILDER.createObjectBuilder()
                .add("greeting", "Hola")
                .build();

    private static WebServer webServer;
    private static WebClient webClient;

    @BeforeAll
    static void startTheServer() {
        webServer = Main.startServer().await();

        webClient = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port())
                .addMediaSupport(JsonpSupport.create())
                .build();
    }

    @AfterAll
    static void stopServer() throws ExecutionException, InterruptedException, TimeoutException {
        if (webServer != null) {
            webServer.shutdown()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
        }
    }


    @Test
    void testOpenApi() {
        String get = webClient.get()
                .path("/openapi")
                .accept(MediaType.APPLICATION_OPENAPI_YAML)
                .request(String.class)
                .await();

        assertThat(get, containsString("title: Swagger Petstore"));
    }

    @Test
    void testSimpleGreet() {
        JsonObject jsonObject = webClient.get()
                                         .path("/simple-greet")
                                         .request(JsonObject.class)
                                         .await();
        assertThat(jsonObject.getString("message"), is("Hello World!"));
    }
    @Test
    void testGreetings() {
        JsonObject jsonObject;
        WebClientResponse response;

        jsonObject = webClient.get()
                .path("/greet/Joe")
                .request(JsonObject.class)
                .await();
        assertThat(jsonObject.getString("message"), is("Hello Joe!"));

        response = webClient.put()
                .path("/greet/greeting")
                .submit(TEST_JSON_OBJECT)
                .await();
        assertThat(response.status().code(), is(204));

        jsonObject = webClient.get()
                .path("/greet/Joe")
                .request(JsonObject.class)
                .await();
        assertThat(jsonObject.getString("message"), is("Hola Joe!"));
    }

    @Test
    void verifySnakeYamlVersion() {
        String selectedSnakeYamlVersion = System.getProperty("selected-snakeyaml-version");
        assertThat("Spec of selected SnakeYAML version", selectedSnakeYamlVersion, is(notNullValue()));

        String classpath = System.getProperty("java.class.path");
        assertThat("SnakeYAML version in classpath",
                   classpath,
                   containsString("snakeyaml-" + selectedSnakeYamlVersion));
    }
}
