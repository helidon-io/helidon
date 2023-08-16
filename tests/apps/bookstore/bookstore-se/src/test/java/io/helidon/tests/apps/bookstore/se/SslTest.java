/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

package io.helidon.tests.apps.bookstore.se;

import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webserver.WebServer;

import io.helidon.webserver.WebServerConfig;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests SSL/TLS with HTTP 1.1.
 */
@ServerTest
public class SslTest {

    private static HttpClient client;
    private final URI baseSslUri;

    public SslTest(WebServer server) throws Exception {
        SSLContext sslContext = TestServer.setupSSLTrust();
        baseSslUri = URI.create("https://localhost:" + server.port());
        client = HttpClient.newBuilder()
                .sslContext(sslContext)
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        Main.setupServer(server, true);
    }

    @Test
    public void testHelloWorldSsl() throws Exception {
        HttpRequest getBooksReq = HttpRequest.newBuilder()
                .uri(baseSslUri.resolve("/books"))
                .GET()
                .build();

        var getBooksRes = client.send(getBooksReq, HttpResponse.BodyHandlers.ofString());
        assertThat(getBooksRes.statusCode(), is(200));
        assertThat(getBooksRes.version(), is(HttpClient.Version.HTTP_1_1));

        HttpRequest postBookReq = HttpRequest.newBuilder()
                .uri(baseSslUri.resolve("/books"))
                .POST(HttpRequest.BodyPublishers.ofString(TestServer.getBookAsJson()))
                .build();

        var postBookRes = client.send(postBookReq, HttpResponse.BodyHandlers.ofString());
        assertThat(postBookRes.statusCode(), is(200));
        assertThat(postBookRes.version(), is(HttpClient.Version.HTTP_1_1));

        HttpRequest getBookReq = HttpRequest.newBuilder()
                .uri(baseSslUri.resolve("/books/123456"))
                .GET()
                .build();

        var getBookRes = client.send(getBookReq, HttpResponse.BodyHandlers.ofString());

        assertThat(getBookRes.statusCode(), is(200));
        assertThat(getBookRes.version(), is(HttpClient.Version.HTTP_1_1));

        HttpRequest deleteBookReq = HttpRequest.newBuilder()
                .uri(baseSslUri.resolve("/books/123456"))
                .DELETE()
                .build();

        var deleteBookRes = client.send(deleteBookReq, HttpResponse.BodyHandlers.ofString());
        assertThat(deleteBookRes.statusCode(), is(200));
        assertThat(deleteBookRes.version(), is(HttpClient.Version.HTTP_1_1));

        HttpRequest getNoBookReq = HttpRequest.newBuilder()
                .uri(baseSslUri.resolve("/books/123456"))
                .GET()
                .build();

        var getNoBookRes = client.send(getNoBookReq, HttpResponse.BodyHandlers.ofString());

        assertThat(getNoBookRes.statusCode(), is(404));
        assertThat(getNoBookRes.version(), is(HttpClient.Version.HTTP_1_1));
    }
}
