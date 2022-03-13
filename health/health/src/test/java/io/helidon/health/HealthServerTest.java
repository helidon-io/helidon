/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.health;

import java.io.StringReader;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.media.common.MessageBodyReadableContent;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

class HealthServerTest {

    private static final Logger LOGGER = Logger.getLogger(HealthServerTest.class.getName());

    private static WebServer webServer;

    private static WebClient webClient;

    @BeforeAll
    static void startup() throws InterruptedException, ExecutionException, TimeoutException {
        HealthSupport healthSupport = HealthSupport.builder().build();
        webServer = startServer(healthSupport);
        webClient = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port() + "/")
                .addMediaSupport(JsonpSupport.create())
                .build();
    }

    @AfterAll
    static void shutdown() {
        shutdownServer(webServer);
    }

    @Test
    void testGetAll() {
        checkResponse(webClient::get, "health", true);
    }

    @Test
    void testHeadAll() {
        checkResponse(webClient::head, "health", false);
    }

    @Test
    void testGetLive() {
        checkResponse(webClient::get, "health/live", true);
    }

    @Test
    void testHeadLive() {
        checkResponse(webClient::head, "health/live", false);
    }

    @Test
    void testGetReady() {
        checkResponse(webClient::get, "health/ready", true);
    }

    @Test
    void testHeadReady() {
        checkResponse(webClient::head, "health/ready", false);
    }

    @Test
    void testGetStarted() {
        checkResponse(webClient::get, "health/started", true);
    }

    @Test
    void testHeadStarted() {
        checkResponse(webClient::head, "health/started", false);
    }

    private static void checkResponse(Supplier<WebClientRequestBuilder> requestFactory,
                                      String requestPath,
                                      boolean expectContent) {

        WebClientResponse response = null;

        try {
            response = requestFactory.get()
                    .path(requestPath)
                    .accept(MediaType.APPLICATION_JSON)
                    .request()
                    .await();

            int expectedStatus = expectContent ? Http.Status.OK_200.code() : Http.Status.NO_CONTENT_204.code();
            assertThat("health response status", response.status().code(), is(expectedStatus));
            MessageBodyReadableContent content = response.content();
            String textContent = null;
            try {
                textContent = content.as(String.class).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }

            assertThat("content length", textContent.length(), expectContent ? greaterThan(0) : equalTo(0));

            if (expectContent) {
                JsonObject health = Json.createReader(new StringReader(textContent)).readObject();
                assertThat("health status", health.getString("status"), is("UP"));
            }
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    static WebServer startServer(
            Service... services) throws
            InterruptedException, ExecutionException, TimeoutException {
        WebServer result = WebServer.builder(
                        Routing.builder()
                                .register(services)
                                .build())
                .port(0)
                .build()
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
        LOGGER.log(Level.INFO, "Started server at: https://localhost:{0}", result.port());
        return result;
    }

    static void shutdownServer(WebServer server) {
        server.shutdown();
    }

}
