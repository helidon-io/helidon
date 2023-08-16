/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
public class MainTest {

    private static HttpClient client;
    private final URI baseUri;

    public MainTest(WebServer server) throws Exception {
        baseUri = URI.create("http://localhost:" + server.port());
        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        Main.setupServer(server, false);
    }

    @Test
    public void testHelloWorld() throws Exception {
        HttpRequest getBooksReq = HttpRequest.newBuilder()
                .uri(baseUri.resolve("/books"))
                .GET()
                .build();

        var getBooksRes = client.send(getBooksReq, HttpResponse.BodyHandlers.ofString());
        assertThat(getBooksRes.statusCode(), is(200));
        assertThat(getBooksRes.version(), is(HttpClient.Version.HTTP_1_1));
        assertThat(getBooksRes.headers().firstValue("content-length").orElse(null), notNullValue());
        assertThat(getBooksRes.body(), is("[]"));

        HttpRequest postBookReq = HttpRequest.newBuilder()
                .uri(baseUri.resolve("/books"))
                .POST(HttpRequest.BodyPublishers.ofString(TestServer.getBookAsJson()))
                .build();

        var postBookRes = client.send(postBookReq, HttpResponse.BodyHandlers.ofString());
        assertThat(postBookRes.statusCode(), is(200));
        assertThat(postBookRes.version(), is(HttpClient.Version.HTTP_1_1));

        HttpRequest getBookReq = HttpRequest.newBuilder()
                .uri(baseUri.resolve("/books/123456"))
                .GET()
                .build();

        var getBookRes = client.send(getBookReq, HttpResponse.BodyHandlers.ofInputStream());

        assertThat(getBookRes.statusCode(), is(200));
        assertThat(getBookRes.version(), is(HttpClient.Version.HTTP_1_1));
        assertThat(getBooksRes.headers().firstValue("content-length").orElse(null), notNullValue());
        JsonReader jsonReader = Json.createReader(getBookRes.body());
        JsonObject jsonObject = jsonReader.readObject();
        assertThat("Checking if correct ISBN", jsonObject.getString("isbn"), is("123456"));


        HttpRequest deleteBookReq = HttpRequest.newBuilder()
                .uri(baseUri.resolve("/books/123456"))
                .DELETE()
                .build();

        var deleteBookRes = client.send(deleteBookReq, HttpResponse.BodyHandlers.ofString());
        assertThat(deleteBookRes.statusCode(), is(200));
        assertThat(deleteBookRes.version(), is(HttpClient.Version.HTTP_1_1));

        HttpRequest getNoBookReq = HttpRequest.newBuilder()
                .uri(baseUri.resolve("/books/123456"))
                .GET()
                .build();

        var getNoBookRes = client.send(getNoBookReq, HttpResponse.BodyHandlers.ofString());

        assertThat(getNoBookRes.statusCode(), is(404));
        assertThat(getNoBookRes.version(), is(HttpClient.Version.HTTP_1_1));
    }
}
