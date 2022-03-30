/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

package io.helidon.examples.quickstart.se;

import java.util.Collections;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;

import io.helidon.common.http.Http;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.testsupport.SetUpServer;
import io.helidon.webserver.testsupport.WebServerTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@WebServerTest
class MainTest {

    private static final JsonBuilderFactory JSON_BUILDER = Json.createBuilderFactory(Collections.emptyMap());

    private final WebClient webClient;

    MainTest(WebClient webClient) {
        this.webClient = webClient;
    }

    @SetUpServer
    static void server(WebServer.Builder builder) {
        Main.configure(builder);
    }

    @Test
    void testDefaultGreeting() {
        JsonObject jsonObject = webClient.get()
                .path("/greet")
                .request(JsonObject.class)
                .await();
        assertEquals("Hello World!", jsonObject.getString("message"));
    }

    @Test
    void testNamedGreeting() {
        JsonObject jsonObject = webClient.get()
                .path("/greet/Joe")
                .request(JsonObject.class)
                .await();
        assertEquals("Hello Joe!", jsonObject.getString("message"));
    }

    @Test
    void testChangeGreeting() {
        WebClientResponse response = webClient.put()
                .path("/greet/greeting")
                .submit(JSON_BUILDER.createObjectBuilder()
                                .add("greeting", "Hola")
                                .build())
                .await();
        assertEquals(Http.Status.NO_CONTENT_204, response.status());

        // make sure it was changed
        JsonObject jsonObject = webClient.get()
                .path("/greet/Joe")
                .request(JsonObject.class)
                .await();
        assertEquals("Hola Joe!", jsonObject.getString("message"));

        // change back to original value
        response = webClient.put()
                .path("/greet/greeting")
                .submit(JSON_BUILDER.createObjectBuilder()
                                .add("greeting", "Hello")
                                .build())
                .await();
        assertEquals(Http.Status.NO_CONTENT_204, response.status());
    }

    @Test
    void testHealth() {
        WebClientResponse response = webClient.get()
                .path("/health")
                .request()
                .await();
        assertEquals(200, response.status().code());
    }

    @Test
    void testMetrics() {
        WebClientResponse response = webClient.get()
                .path("/metrics")
                .request()
                .await();
        assertEquals(200, response.status().code());
    }

}
