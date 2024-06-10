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

package io.helidon.examples.webserver.opentracing;

import io.helidon.http.Status;
import io.helidon.http.media.jsonp.JsonpSupport;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServer;

import jakarta.json.JsonArray;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static io.helidon.common.testing.junit5.MatcherWithRetry.assertThatWithRetry;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Testcontainers(disabledWithoutDocker = true)
public class MainTest {
    private static WebClient client;
    private static WebServer server;
    private static Http1Client zipkinClient;

    @Container
    private static final GenericContainer<?> container = new GenericContainer<>("openzipkin/zipkin")
            .withExposedPorts(9411)
            .waitingFor(Wait.forHttp("/health").forPort(9411));

    @BeforeAll
    static void checkContainer() {
        server = Main.setupServer(WebServer.builder(), container.getMappedPort(9411));
        client = WebClient.create(config -> config.baseUri("http://localhost:" + server.port())
                .addMediaSupport(JsonpSupport.create()));
        zipkinClient = Http1Client.create(config -> config
                .baseUri("http://localhost:" + container.getMappedPort(9411)));
    }

    @AfterAll
    static void close() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void test() {
        try (Http1ClientResponse response = zipkinClient.get("/zipkin/api/v2/traces").request()) {
            JsonArray array = response.as(JsonArray.class);
            assertThat(response.status(), is(Status.OK_200));
            assertThat(array.isEmpty(), is(true));
        }

        try (HttpClientResponse response = client.get("test").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("Hello World!"));
        }

        assertThatWithRetry("Traces must contains service name",
                MainTest::getZipkinTraces, containsString("demo-first"));
        assertThatWithRetry("Traces must contains pinged endpoint",
                MainTest::getZipkinTraces, containsString(client.get("test").uri().toString()));
    }

    private static String getZipkinTraces() {
        try (Http1ClientResponse response = zipkinClient.get("/zipkin/api/v2/traces").request()) {
            assertThat(response.status(), is(Status.OK_200));
            return response.as(String.class);
        }
    }
}
