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

package io.helidon.examples.quickstart.mp;

import io.helidon.microprofile.tests.junit5.HelidonTest;

import jakarta.inject.Inject;
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

        try (Response r = target.path("/metrics")
                .request()
                .get()) {
            assertThat("GET metrics status code", r.getStatus(), is(200));
        }

        try (Response r = target.path("/health")
                .request()
                .get()) {
            assertThat("GET health status code", r.getStatus(), is(200));
        }
    }
}
