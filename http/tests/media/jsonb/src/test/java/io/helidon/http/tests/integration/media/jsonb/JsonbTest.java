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

package io.helidon.http.tests.integration.media.jsonb;

import java.util.Objects;
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

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@ServerTest
class JsonbTest {
    // use utf-8 to validate everything works
    private static final JsonbMessage MESSAGE = new JsonbMessage("český řízný text");

    private final Http1Client client;

    JsonbTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.get("/jsonb", (req, res) -> res.send(MESSAGE))
                .post("/jsonb", (req, res) -> {
                    JsonbMessage message = req.content().as(JsonbMessage.class);
                    res.send(new JsonbMessage(message.message));
                });
    }

    @Test
    void testGet() {
        Http1ClientResponse response = client.get("/jsonb")
                .request();

        assertAll(
                () -> assertThat(response.status(), is(Status.OK_200)),
                () -> assertThat("Should contain content type application/json",
                                 response.headers().contentType(),
                                 is(Optional.of(HttpMediaType.create(MediaTypes.APPLICATION_JSON)))),
                () -> assertThat(response.as(JsonbMessage.class), is(MESSAGE)));
    }

    @Test
    void testPost() {
        Http1ClientResponse response = client.method(Method.POST)
                .uri("/jsonb")
                .submit(MESSAGE);

        assertAll(
                () -> assertThat(response.status(), is(Status.OK_200)),
                () -> assertThat("Should contain content type application/json",
                                 response.headers().contentType(),
                                 is(Optional.of(HttpMediaType.create(MediaTypes.APPLICATION_JSON)))),
                () -> assertThat(response.as(JsonbMessage.class), is(MESSAGE)));
    }

    public static class JsonbMessage {
        private String message;

        public JsonbMessage() {
        }

        public JsonbMessage(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        @Override
        public int hashCode() {
            return Objects.hash(message);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof JsonbMessage)) {
                return false;
            }
            JsonbMessage that = (JsonbMessage) o;
            return message.equals(that.message);
        }
    }

}
