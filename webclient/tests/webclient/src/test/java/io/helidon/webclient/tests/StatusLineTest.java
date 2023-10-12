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

package io.helidon.webclient.tests;

import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class StatusLineTest {
    private static volatile Http1Client client;
    private final WebClient testClient;

    StatusLineTest(WebServer server, WebClient testClient) {
        client = Http1Client.builder()
                .baseUri("http://localhost:" + server.port())
                .build();
        this.testClient = testClient;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/status/{code}", (
                        (req, res) -> {
                            int code = Integer.parseInt(req.path().pathParameters().get("code"));
                            res.status(code);
                            res.send();
                        }))
                .get("/{code}", (req, res) -> {
                    int code = Integer.parseInt(req.path().pathParameters().get("code"));
                    try (HttpClientResponse clientRes = client.get("/status/" + code).request()) {
                        res.send("got : " + clientRes.status().code());
                    }
                });
    }

    @Test
    void testStatusLine() {
        String response = testClient.get("/210")
                .requestEntity(String.class);

        assertThat(response, is("got : 210"));
    }
}
