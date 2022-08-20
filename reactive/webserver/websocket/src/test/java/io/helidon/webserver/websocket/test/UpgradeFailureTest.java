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
 */
package io.helidon.webserver.websocket.test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import io.helidon.common.LogConfig;
import io.helidon.webserver.Http1Route;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.common.http.Http.Method.GET;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class UpgradeFailureTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static WebServer server;
    private static HttpClient httpClient;

    @BeforeAll
    static void beforeAll() {
        LogConfig.configureRuntime();
        server = WebServer.builder()
                .defaultSocket(s -> s
                        .host("localhost")
                        .port(0)
                )
                .routing(r -> r
                        .route(Http1Route.route(GET, "/versionspecific", (req, res) -> res.send("HTTP/1.1 route")))
                )
                .build()
                .start()
                .await(TIMEOUT);
        httpClient = HttpClient.newHttpClient();
    }

    @AfterAll
    static void afterAll() {
        server.shutdown()
                .await(TIMEOUT);
    }

    @Test
    void noCodecUpgrade() throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder()
                // H2 upgrade
                .version(HttpClient.Version.HTTP_2)
                .uri(URI.create("http://localhost:" + server.port() + "/versionspecific"))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());

        // There is no http2 codec on classpath, expect h2 update to fallback to http/1.1
        assertThat(response.body(), is("HTTP/1.1 route"));
    }
}
