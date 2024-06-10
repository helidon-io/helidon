/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.webserver.observe.metrics;

import java.util.List;

import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.http.Method;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;

import io.helidon.webserver.testing.junit5.SetUpRoute;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class TestInvalidAcceptType {

    private final SocketHttpClient client;

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.post("/echo", (req, res) -> {
            JsonObject message = req.content().as(JsonObject.class);
            res.send(message);
        });
    }

    TestInvalidAcceptType(SocketHttpClient client) {
        this.client = client;
    }

    @Test
    void checkResponseCode() {
        String response = client.sendAndReceive(Method.POST,
                "/echo",
                "{ }",
                List.of("Accept: application.json"));       // invalid media type
        assertThat(response, containsString("400 Bad Request"));
    }

    @Test
    void checkResponseCodeMetrics() {
        String response = client.sendAndReceive(Method.GET,
                "/observe/metrics",
                null,
                List.of("Accept: application.json"));       // invalid media type
        assertThat(response, containsString("400 Bad Request"));
    }
}
