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

package io.helidon.examples.faulttolerance;

import io.helidon.Main;
import io.helidon.http.Http;
import io.helidon.inject.api.InjectionServices;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class FaultToleranceTest {
    private static WebClient webClient;
    private static WebClient adminWebClient;

    @BeforeAll
    static void init() {
        Main.main(new String[0]);
        WebServer webServer = InjectionServices.realizedServices()
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
        InjectionServices.injectionServices()
                .map(InjectionServices::shutdown);
    }

    @Test
    void testAdminEndpointIsOnAdminSocket() {
        String response = adminWebClient.get("/admin")
                .requestEntity(String.class);

        assertThat(response, startsWith("This is the admin endpoint"));
    }

    @Test
    void testAdminEndpointIsNotOnDefaultSocket() {
        ClientResponseTyped<String> response = webClient.get("/admin")
                .request(String.class);
        assertThat(response.entity(), response.status(), is(Http.Status.NOT_FOUND_404));
    }

    @Test
    void testGreetSimple() {
        String response = webClient.get("/greet")
                .requestEntity(String.class);
        assertThat(response, is("Hello World!"));
    }

    @Test
    void testGreetNamed() {
        String response = webClient.get("/greet/helidon")
                .requestEntity(String.class);

        assertThat(response, startsWith("Hello helidon! Requested host: localhost:"));
    }

    @Test
    void testGreetNamedFallback() {
        String response = webClient.get("/greet/helidon")
                .queryParam("throw", "true")
                .requestEntity(String.class);

        assertThat(response, startsWith("Fallback for"));
    }
}
