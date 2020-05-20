/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.example.helloworld.implicit;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.spi.CDI;
import javax.json.JsonObject;
import javax.ws.rs.client.ClientBuilder;

import io.helidon.microprofile.server.Server;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Unit test for {@link HelloWorldResource}.
 */
class ImplicitHelloWorldTest {
    private static Server server;


    @BeforeAll
    static void initClass() {
        server = Server.create().start();
    }

    @AfterAll
    static void destroyClass() {
        CDI<Object> current = CDI.current();
        ((SeContainer) current).close();
    }

    @Test
    void testJsonResource() {
        JsonObject jsonObject = ClientBuilder.newClient()
                .target("http://localhost:" + server.port()  + "/helloworld/unit")
                .request()
                .get(JsonObject.class);

        assertAll("JSON fields must match expected injection values",
                  () -> assertThat("Name from request", jsonObject.getString("name"), is("unit")),
                  () -> assertThat("Request id from CDI provider", jsonObject.getInt("requestId"), is(1)),
                  () -> assertThat("App name from config", jsonObject.getString("appName"), is("Hello World Application")),
                  () -> assertThat("Logger name", jsonObject.getString("logger"), is(HelloWorldResource.class.getName()))
        );

    }
}
