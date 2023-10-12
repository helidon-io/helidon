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

package io.helidon.webserver.tests.http2;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http1.Http1Route;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static io.helidon.http.Method.GET;
import static io.helidon.http.Method.PUT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;

@ServerTest
class ConsumeTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String PATH = "/path";

    private final URI uri;

    ConsumeTest(URI uri) {
        this.uri = uri;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.route(Http1Route.route(GET, PATH, (req, res) -> res.send()))
                .route(Http2Route.route(PUT, PATH, (req, res) -> {
                    // Intentionally unconsumed content
                    // req.content().consume();
                    res.send();
                }));
    }

    @Test
    void unconsumedContent() throws InterruptedException, IOException {
        URI resource = uri.resolve(PATH);
        var httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        // Do upgrade, java http client can't do prior knowledge
        httpClient.send(HttpRequest.newBuilder()
                                .GET()
                                .version(HttpClient.Version.HTTP_2)
                                .uri(resource)
                                .build(), HttpResponse.BodyHandlers.ofString()).body();

        for (int i = 0; i < 5; i++) {
            var body = HttpRequest.BodyPublishers.ofString("test".repeat(30_000));
            HttpResponse<String> res = null;
            try {
                res = httpClient.send(HttpRequest.newBuilder()
                                              .version(HttpClient.Version.HTTP_2)
                                              .uri(resource)
                                              .method("PUT", body)
                                              .build(), HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                fail("Iteration " + i + " failed!", e);
            }
            assertThat(res.statusCode(), is(200));
        }
    }
}
