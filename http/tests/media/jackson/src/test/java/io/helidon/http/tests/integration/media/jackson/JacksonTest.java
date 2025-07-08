/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.http.tests.integration.media.jackson;

import java.util.Objects;
import java.util.Optional;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.HttpMediaType;
import io.helidon.http.HttpMediaTypes;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@ServerTest
class JacksonTest {
    // use utf-8 to validate everything works
    private static final JacksonMessage MESSAGE = new JacksonMessage("český řízný text");

    private final Http1Client client;

    JacksonTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.get("/jackson", (req, res) -> res.send(MESSAGE))
                .post("/jackson", (req, res) -> {
                    JacksonMessage message = req.content().as(JacksonMessage.class);
                    res.send(new JacksonMessage(message.message));
                });
    }

    @Test
    void testGet() {
        var response = client.get("/jackson")
                .request(JacksonMessage.class);

        assertAll(
                () -> assertThat(response.status(), is(Status.OK_200)),
                () -> assertThat("Should contain content type application/json",
                                 response.headers().contentType(),
                                 is(Optional.of(HttpMediaType.create(MediaTypes.APPLICATION_JSON)))),
                () -> assertThat(response.entity(), is(MESSAGE)));
    }

    @Test
    void testGetWithAcceptUtf8() {
        var response = client.get("/jackson")
                .accept(HttpMediaTypes.JSON_UTF_8)
                .request(JacksonMessage.class);

        assertAll(
                () -> assertThat(response.status(), is(Status.OK_200)),
                () -> assertThat("Should contain content type application/json",
                                 response.headers().contentType(),
                                 is(Optional.of(HttpMediaType.create(MediaTypes.APPLICATION_JSON)))),
                () -> assertThat(response.entity(), is(MESSAGE)));
    }

    @Test
    void testPost() {
        var response = client.method(Method.POST)
                .uri("/jackson")
                .submit(MESSAGE, JacksonMessage.class);

        assertAll(
                () -> assertThat(response.status(), is(Status.OK_200)),
                () -> assertThat("Should contain content type application/json",
                                 response.headers().contentType(),
                                 is(Optional.of(HttpMediaType.create(MediaTypes.APPLICATION_JSON)))),
                () -> assertThat(response.entity(), is(MESSAGE)));
    }

    public static class JacksonMessage {
        private String message;

        public JacksonMessage() {
        }

        public JacksonMessage(String message) {
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
            if (!(o instanceof JacksonMessage)) {
                return false;
            }
            JacksonMessage that = (JacksonMessage) o;
            return message.equals(that.message);
        }
    }

}
