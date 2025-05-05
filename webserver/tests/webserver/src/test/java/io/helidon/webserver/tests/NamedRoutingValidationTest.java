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

package io.helidon.webserver.tests;

import io.helidon.http.Status;
import io.helidon.testing.junit5.Testing;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
This test is aimed at validating that a named socket (listener) exists, if we configure a named routing.
We should either:
- warn
- throw an exception
 */
@Testing.Test
class NamedRoutingValidationTest {
    @Test
    void testValidSocket() {
        var webServer = WebServer.builder()
                .port(0)
                .putSocket("admin", listener -> listener.port(0)
                        .name("admin"))
                .routing(rules -> rules.get("/greet", (req, res) -> res.send("Hello")))
                .routing("admin", rules -> rules.get("/greet", (req, res) -> res.send("Admin")))
                .build();

        webServer.start();
        int defaultPort = webServer.port();
        int adminPort = webServer.port("admin");

        WebClient webClient = null;
        try {
            webClient = WebClient.create();

            String response = webClient.get("http://localhost:" + defaultPort + "/greet")
                    .requestEntity(String.class);
            assertThat(response, is("Hello"));

            response = webClient.get("http://localhost:" + adminPort + "/greet")
                    .requestEntity(String.class);
            assertThat(response, is("Admin"));
        } finally {
            if (webClient != null) {
                webClient.closeResource();
            }
        }
    }

    /*
    Shows an accident of quoting a string in properties
     */
    @Test
    void testInvalidSocket() {
        var webServerBuilder = WebServer.builder()
                .port(0)
                .putSocket("admin", listener -> listener.port(0)
                        .name("admin"))
                .putSocket("internal", listener -> listener.port(0)
                        .name("internal"))
                .routing(rules -> rules.get("/greet", (req, res) -> res.send("Hello")))
                .routing("\"admin\"", rules -> rules.get("/greet", (req, res) -> res.send("Admin")));

        IllegalStateException illegalStateException = assertThrows(IllegalStateException.class, webServerBuilder::build);

        String message = illegalStateException.getMessage();
        assertThat("Message must contain quoted invalid name",
                   message,
                   containsString("\"\"admin\"\""));
        assertThat("Message must contain quoted list of valid names",
                   message,
                   containsString("\"internal, admin, @default\""));
    }

    /*
   Shows an accident of quoting a string in properties
    */
    @Test
    void testInvalidSocketIgnored() {
        var webServer = WebServer.builder()
                .ignoreInvalidNamedRouting(true)
                .port(0)
                .putSocket("admin", listener -> listener.port(0)
                        .name("admin"))
                .putSocket("internal", listener -> listener.port(0)
                        .name("internal"))
                .routing(rules -> rules.get("/greet", (req, res) -> res.send("Hello")))
                .routing("\"admin\"", rules -> rules.get("/greet", (req, res) -> res.send("Admin")))
                .build();

        webServer.start();
        int defaultPort = webServer.port();
        int adminPort = webServer.port("admin");

        WebClient webClient = null;
        try {
            webClient = WebClient.create();

            String response = webClient.get("http://localhost:" + defaultPort + "/greet")
                    .requestEntity(String.class);
            assertThat(response, is("Hello"));

            // should not be found, as hte routing is invalid
            var typedResponse = webClient.get("http://localhost:" + adminPort + "/greet")
                    .request(String.class);
            assertThat(typedResponse.status(), is(Status.NOT_FOUND_404));
        } finally {
            if (webClient != null) {
                webClient.closeResource();
            }
        }
    }
}
