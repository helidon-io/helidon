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

package io.helidon.webclient.tests;

import static io.helidon.http.Method.GET;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;

import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpForward.Scheme;

@ServerTest
class HttpMockProxyTest {

    private static final int PROXY_PORT = 1080;
    private final WebClient webClient;
    private final int serverPort;
    private ClientAndServer mockServer;

    HttpMockProxyTest(WebServer server) {
        this.serverPort = server.port();
        this.webClient = WebClient.builder()
                .baseUri("http://localhost:" + serverPort)
                .proxy(Proxy.builder().host("localhost").port(PROXY_PORT).build())
                .build();
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.route(GET, "/get", Routes::get);
    }

    @BeforeEach
    public void before() {
        mockServer = startClientAndServer(PROXY_PORT);
        mockServer.when(request()).forward(forward().withScheme(Scheme.HTTP).withHost("localhost").withPort(serverPort)); 
    }

    @AfterEach
    public void after() {
        mockServer.stop();
    }

    @Test
    public void issue9022() {
        try (HttpClientResponse response = webClient.get("/get").request()) {
            assertThat(response.status(), is(Status.OK_200));
            String entity = response.entity().as(String.class);
            assertThat(entity, is("Hello"));
        }
    }

    private static class Routes {
        private static String get() {
            return "Hello";
        }
    }
}
