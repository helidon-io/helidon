/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
import java.net.http.HttpResponse;
import java.time.Duration;

import io.helidon.common.LogConfig;
import io.helidon.common.http.Http;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.Http1Route;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http2.Http2Route;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import static io.helidon.common.http.Http.Method.GET;

class H2Test {

    private static WebServer webServer;
    private static HttpClient httpClient;
    private static WebClient webClient;

    @BeforeAll
    public static void startServer() throws Exception {
        LogConfig.configureRuntime();
        webServer = WebServer.builder()
                .defaultSocket(s -> s
                        .bindAddress("localhost")
                        .port(0)
                )
                .routing(r -> r
                        .get("/", (req, res) -> res.send("HTTP Version " + req.version()))
                        .route(Http1Route.route(GET, "/versionspecific", (req, res) -> res.send("HTTP/1.1 route")))
                        .route(Http2Route.route(GET, "/versionspecific", (req, res) -> res.send("HTTP/2 route")))

                        .route(Http1Route.route(GET, "/versionspecific1", (req, res) -> res.send("HTTP/1.1 route")))
                        .route(Http2Route.route(GET, "/versionspecific2", (req, res) -> res.send("HTTP/2 route")))
                )
                .build()
                .start()
                .await(Duration.ofSeconds(10));

        httpClient = HttpClient.newHttpClient();
        webClient = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port())
                .build();
    }

    @AfterAll
    static void afterAll() {
        webServer.shutdown().await(Duration.ofSeconds(10));
    }

    @Test
    void genericHttp20() throws IOException, InterruptedException {
        assertThat(httpClientGet("/", HttpClient.Version.HTTP_2).body(), is("HTTP Version V2_0"));
        assertThat(webClientGet("/", Http.Version.V2_0).content().as(String.class).await(), is("HTTP Version V2_0"));
    }

    @Test
    void genericHttp11() throws IOException, InterruptedException {
        assertThat(httpClientGet("/", HttpClient.Version.HTTP_1_1).body(), is("HTTP Version V1_1"));
        assertThat(webClientGet("/", Http.Version.V1_1).content().as(String.class).await(), is("HTTP Version V1_1"));
    }

    @Test
    void versionSpecificHttp11() throws IOException, InterruptedException {
        assertThat(httpClientGet("/versionspecific", HttpClient.Version.HTTP_1_1).body(), is("HTTP/1.1 route"));
        assertThat(webClientGet("/versionspecific", Http.Version.V1_1).content().as(String.class).await(), is("HTTP/1.1 route"));
    }

    @Test
    void versionSpecificHttp20() throws IOException, InterruptedException {
        assertThat(httpClientGet("/versionspecific", HttpClient.Version.HTTP_2).body(), is("HTTP/2 route"));
        assertThat(webClientGet("/versionspecific", Http.Version.V2_0).content().as(String.class).await(), is("HTTP/2 route"));
    }

    @Test
    void versionSpecificHttp11Negative() throws IOException, InterruptedException {
        assertThat(httpClientGet("/versionspecific1", HttpClient.Version.HTTP_2).statusCode(), is(404));
        assertThat(webClientGet("/versionspecific1", Http.Version.V2_0).status().code(), is(404));
    }

    @Test
    void versionSpecificHttp20Negative() throws IOException, InterruptedException {
        assertThat(httpClientGet("/versionspecific2", HttpClient.Version.HTTP_1_1).statusCode(), is(404));
        assertThat(webClientGet("/versionspecific2", Http.Version.V1_1).status().code(), is(404));
    }


    private HttpResponse<String> httpClientGet(String path, HttpClient.Version version) throws IOException, InterruptedException {
        return httpClient.send(HttpRequest.newBuilder()
                .version(version)
                .uri(URI.create("http://localhost:" + webServer.port() + path))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private WebClientResponse webClientGet(String path, Http.Version version) {
        return webClient.get()
                .httpVersion(version)
                .path(path)
                .request()
                .await(Duration.ofSeconds(10));
    }
}
