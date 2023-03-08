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

package io.helidon.webserver.http2.test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.Duration;

import io.helidon.common.LogConfig;
import io.helidon.webserver.WebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class H2UpgradeTest {

    private static WebServer webServer;
    private static HttpClient httpClient;

    @BeforeAll
    public static void startServer() {
        LogConfig.configureRuntime();
        webServer = WebServer.builder()
                .defaultSocket(s -> s
                        .bindAddress("localhost")
                        .port(0)
                )
                .routing(r -> r
                        .get("/", (req, res) ->
                                res.send("GET " + req.version()))
                        .post("/", (req, res) ->
                                req.content().as(String.class).thenAccept(s ->
                                        res.send("POST " + req.version() + " " + s)))
                        .put("/", (req, res) ->
                                req.content().as(String.class).thenAccept(s ->
                                        res.send("PUT " + req.version() + " " + s)))
                )
                .build()
                .start()
                .await(Duration.ofSeconds(10));

        httpClient = HttpClient.newHttpClient();
    }

    @AfterAll
    static void afterAll() {
        webServer.shutdown().await(Duration.ofSeconds(10));
    }

    @Test
    void testGetUpgrade() throws IOException, InterruptedException {
        HttpResponse<String> r = httpClientGet("GET", "/", BodyPublishers.noBody());
        assertThat(r.body(), is("GET V2_0"));
    }

    @Test
    void testPostUpgrade() throws IOException, InterruptedException {
        HttpResponse<String> r = httpClientGet("POST", "/", BodyPublishers.ofString("Hello World"));
        assertThat(r.body(), is("POST V2_0 Hello World"));
    }

    @Test
    void testPutUpgrade() throws IOException, InterruptedException {
        HttpResponse<String> r = httpClientGet("PUT", "/", BodyPublishers.ofString("Hello World"));
        assertThat(r.body(), is("PUT V2_0 Hello World"));
    }

    private HttpResponse<String> httpClientGet(String method, String path, HttpRequest.BodyPublisher publisher)
            throws IOException, InterruptedException {
        return httpClient.send(HttpRequest.newBuilder()
                .version(HttpClient.Version.HTTP_2)     // always upgrade, no prior knowledge support
                .uri(URI.create("http://localhost:" + webServer.port() + path))
                .method(method, publisher)
                .build(), HttpResponse.BodyHandlers.ofString());
    }
}
