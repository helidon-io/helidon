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

package io.helidon.examples.nima.faulttolerance;

import io.helidon.Main;
import io.helidon.common.http.Http;
import io.helidon.nima.webclient.WebClient;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.WebServer;
import io.helidon.pico.api.PicoServices;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class FaultToleranceTest {
    private static Http1Client webClient;
    private static Http1Client adminWebClient;

    @BeforeAll
    static void init() {
        Main.main(new String[0]);
        WebServer webServer = PicoServices.realizedServices()
                .lookup(WebServer.class)
                .get();

        if (!webServer.isRunning()) {
            fail("Webserver should be running, but is shut down");
        }

        webClient = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port())
                .build();

        adminWebClient = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port("admin"))
                .build();
    }

    @AfterAll
    static void shutDown() {
        PicoServices.picoServices()
                .map(PicoServices::shutdown);
    }

    @Test
    void testAdminEndpointIsOnAdminSocket() {
        String response = adminWebClient.get("/admin")
                .request(String.class);

        assertThat(response, startsWith("This is the admin endpoint"));
    }

    @Test
    void testAdminEndpointIsNotOnDefaultSocket() {
        try (Http1ClientResponse response = webClient.get("/admin")
                .request()) {
            assertThat(response.entity().as(String.class), response.status(), is(Http.Status.NOT_FOUND_404));
        }
    }

    @Test
    void testGreetSimple() {
        String response = webClient.get("/greet")
                .request(String.class);
        assertThat(response, is("Hello World!"));
    }

    @Test
    void testGreetNamed() {
        String response = webClient.get("/greet/helidon")
                .request(String.class);

        assertThat(response, startsWith("Hello helidon! Requested host: localhost:"));
    }

    @Test
    void testGreetNamedFallback() {
        String response = webClient.get("/greet/helidon")
                .queryParam("throw", "true")
                .request(String.class);

        assertThat(response, startsWith("Fallback for"));
    }
}
