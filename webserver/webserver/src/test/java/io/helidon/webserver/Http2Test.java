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

package io.helidon.webserver;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import io.helidon.common.http.Http;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class Http2Test {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private static WebServer webServer;

    @BeforeAll
    static void beforeAll() {
        webServer = WebServer.builder(Routing.builder()
                        .get("/testHttpVersion", (req, res) -> res.send(req.version().value()))
                        .build())
                .experimental(ExperimentalConfiguration.builder()
                        .http2(Http2Configuration.builder()
                                .enable(true)
                                .build())
                        .build())
                .host("localhost")
                .build()
                .start()
                .await(TIMEOUT);
    }

    @AfterAll
    static void afterAll() {
        webServer.shutdown().await(TIMEOUT);
    }

    @Test
    void http11Version() throws IOException, InterruptedException {
        assertThat(getHttpVersion(HttpClient.Version.HTTP_1_1), is(Http.Version.V1_1.value()));
    }

    @Test
    void http20Version() throws IOException, InterruptedException {
        assertThat(getHttpVersion(HttpClient.Version.HTTP_2), is(Http.Version.V2_0.value()));
    }


    private String getHttpVersion(HttpClient.Version version) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + webServer.port() + "/testHttpVersion"))
                .version(version)
                .GET()
                .build();

        HttpResponse<String> response = HttpClient
                .newBuilder()
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());

        return response.body();
    }
}
