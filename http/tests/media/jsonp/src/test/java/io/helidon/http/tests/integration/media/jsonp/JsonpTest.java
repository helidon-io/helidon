/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.http.tests.integration.media.jsonp;

import java.util.Map;
import java.util.Optional;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.HttpMediaType;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@ServerTest
class JsonpTest {
    // use utf-8 to validate everything works
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());
    private static final JsonObject MESSAGE = JSON.createObjectBuilder().add("message", "český řízný text").build();

    private final Http1Client client;

    JsonpTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.get("/jsonp", (req, res) -> res.send(MESSAGE))
                .post("/jsonp", (req, res) -> {
                    JsonObject message = req.content().as(JsonObject.class);
                    res.send(JSON.createObjectBuilder().add("message", message.getString("message")).build());
                });
    }

    @Test
    void testGet() {
        Http1ClientResponse response = client.get("/jsonp")
                .request();

        assertAll(
                () -> assertThat(response.status(), is(Status.OK_200)),
                () -> assertThat("Should contain content type application/json",
                                 response.headers().contentType(),
                                 is(Optional.of(HttpMediaType.create(MediaTypes.APPLICATION_JSON)))),
                () -> assertThat(response.as(JsonObject.class), is(MESSAGE)));
    }

    @Test
    void testPost() {
        Http1ClientResponse response = client.method(Method.POST)
                .uri("/jsonp")
                .submit(MESSAGE);

        assertAll(
                () -> assertThat(response.status(), is(Status.OK_200)),
                // todo matcher for headers
                () -> assertThat("Should contain content type application/json",
                                 response.headers().contentType(),
                                 is(Optional.of(HttpMediaType.create(MediaTypes.APPLICATION_JSON)))),
                () -> assertThat(response.as(JsonObject.class), is(MESSAGE)));
    }
}
