/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests;

import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertAll;

@ServerTest
class RequestedUriTest {
    private final WebServer webServer;

    RequestedUriTest(WebServer webServer) {
        this.webServer = webServer;
    }

    @SetUpServer
    static void setUp(WebServerConfig.Builder server) {
        // default is localhost only, we need all, so it listens on ipv6 as well
        server.host("0.0.0.0");
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/uri", (req, res) -> {
            try {
                res.send(req.requestedUri().toUri().toString());
            } catch (Exception e) {
                e.printStackTrace();
                res.status(Status.INTERNAL_SERVER_ERROR_500)
                        .send(e.getClass().getName() + ":" + e.getMessage());
            }
        });
    }

    @Test
    void ipV6Test() {
        int port = webServer.port();
        WebClient client = WebClient.builder()
                .baseUri("http://[::1]:" + port)
                .build();

        ClientResponseTyped<String> response = client.get()
                .path("/uri")
                .request(String.class);

        assertAll(
                () -> assertThat(response.entity(), is("http://[::1]:" + port + "/uri")),
                () -> assertThat(response.status(), is(Status.OK_200))
        );
    }

    @Test
    void schemeCaseTest() {
        int port = webServer.port();
        WebClient client = WebClient.builder()
                .baseUri("hTTp://localhost:" + port)
                .build();
        ClientResponseTyped<String> response = client.get()
                .path("/uri")
                .request(String.class);
        assertThat(response.status(), is(Status.OK_200));
    }
}
