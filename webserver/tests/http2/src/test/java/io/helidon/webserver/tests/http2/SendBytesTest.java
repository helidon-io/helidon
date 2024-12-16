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

package io.helidon.webserver.tests.http2;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import io.helidon.http.Status;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class SendBytesTest {
    private static final int START = 16;
    private static final int LENGTH = 9;
    private static final String ENTITY = "The quick brown fox jumps over the lazy dog";

    private final HttpClient client;
    private final URI uri;

    SendBytesTest(URI uri) {
        this.uri = uri;
        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/sendAll", (req, res) ->
                        res.send(ENTITY.getBytes(StandardCharsets.UTF_8)))
                .get("/sendPart", (req, res) ->
                        res.send(ENTITY.getBytes(StandardCharsets.UTF_8), START, LENGTH));
    }

    /**
     * Test getting all the entity.
     */
    @Test
    void testAll() throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder()
                                                            .uri(uri.resolve("/sendAll"))
                                                            .GET()
                                                            .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode(), is(Status.OK_200.code()));
        assertThat(response.version(), is(HttpClient.Version.HTTP_2));
        String entity = response.body();
        assertThat(entity, is(ENTITY));
    }

    /**
     * Test getting part of the entity.
     */
    @Test
    void testPart() throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder()
                                                            .uri(uri.resolve("/sendPart"))
                                                            .GET()
                                                            .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode(), is(Status.OK_200.code()));
        assertThat(response.version(), is(HttpClient.Version.HTTP_2));
        String entity = response.body();
        assertThat(entity, is(ENTITY.substring(START, START + LENGTH)));
    }
}
