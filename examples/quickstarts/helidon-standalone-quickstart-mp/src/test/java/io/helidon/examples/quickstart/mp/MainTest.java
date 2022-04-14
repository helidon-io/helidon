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

package io.helidon.examples.quickstart.mp;

import io.helidon.microprofile.tests.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@HelidonTest
class MainTest {
    private final WebTarget target;

    @Inject
    MainTest(WebTarget target) {
        this.target = target;
    }

    @Test
    void testHelloWorld() {

        JsonObject jsonObject = target.path("/greet")
                .request()
                .get(JsonObject.class);
        Assertions.assertEquals("Hello World!", jsonObject.getString("message"),
                "default message");

        jsonObject = target.path("/greet/Joe")
                .request()
                .get(JsonObject.class);
        Assertions.assertEquals("Hello Joe!", jsonObject.getString("message"),
                "hello Joe message");

        try (Response r = target.path("/greet/greeting")
                .request()
                .put(Entity.entity("{\"greeting\" : \"Hola\"}", MediaType.APPLICATION_JSON))) {
            Assertions.assertEquals(204, r.getStatus(), "PUT status code");
        }

        jsonObject = target.path("/greet/Jose")
                .request()
                .get(JsonObject.class);
        Assertions.assertEquals("Hola Jose!", jsonObject.getString("message"),
                "hola Jose message");

        try (Response r = target.path("/metrics")
                .request()
                .get()) {
            Assertions.assertEquals(200, r.getStatus(), "GET metrics status code");
        }

        try (Response r = target.path("/health")
                .request()
                .get()) {
            Assertions.assertEquals(200, r.getStatus(), "GET health status code");
        }
    }
}
