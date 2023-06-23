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

package io.helidon.nima.tests.integration.http2.webserver;

import io.helidon.nima.http2.webserver.Http2ConfigDefault;
import io.helidon.nima.http2.webserver.Http2ConnectionProvider;
import io.helidon.nima.http2.webserver.Http2Route;
import io.helidon.nima.http2.webserver.Http2UpgradeProvider;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.testing.junit5.webserver.SetUpServer;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRouting;
import io.helidon.nima.webserver.http1.Http1ConnectionProvider;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.helidon.common.http.Http.Method.GET;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
public class HeadersTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String DATA = "Helidon!!!".repeat(10);

    @SetUpServer
    static void setUpServer(WebServer.Builder serverBuilder) {
        serverBuilder.port(-1)
                // HTTP/2 prior knowledge config
                .addConnectionProvider(Http2ConnectionProvider.builder()
                        .http2Config(Http2ConfigDefault.builder()
                                .sendErrorDetails(true)
                                .maxHeaderListSize(128_000))
                        .build())
                // HTTP/1.1 -> HTTP/2 upgrade config
                .addConnectionProvider(Http1ConnectionProvider.builder()
                        .addUpgradeProvider(Http2UpgradeProvider.builder()
                                .http2Config(Http2ConfigDefault.builder()
                                        .sendErrorDetails(true)
                                        .maxHeaderListSize(128_000))
                                .build())
                        .build());
    }

    @SetUpRoute
    static void router(HttpRouting.Builder router) {
        router.route(Http2Route.route(GET, "/ping", (req, res) -> res.send("pong")));
        router.route(Http2Route.route(GET, "/cont-out",
                (req, res) -> {
                    for (int i = 0; i < 500; i++) {
                        res.header("test-header-" + i, DATA + i);
                    }
                    res.send();
                }
        ));
        router.route(Http2Route.route(GET, "/cont-in",
                (req, res) -> {
                    String joinedHeaders = req.headers()
                            .stream()
                            .filter(h -> h.name().startsWith("test-header-"))
                            .map(h -> h.name() + "=" + h.value())
                            .collect(Collectors.joining("\n"));
                    res.send(joinedHeaders);
                }
        ));
    }

    @Test
    void serverOutbound(WebServer server) throws IOException, InterruptedException {
        URI base = URI.create("http://localhost:" + server.port());
        HttpClient client = http2Client(base);

        Set<String> expected = new HashSet<>(500);
        for (int i = 0; i < 500; i++) {
            expected.add("test-header-" + i + "=" + DATA + i);
        }

        HttpResponse<String> res = client.send(HttpRequest.newBuilder()
                .timeout(TIMEOUT)
                .uri(base.resolve("/cont-out"))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());

        List<String> actual = res.headers()
                .map()
                .entrySet()
                .stream()
                .filter(e -> e.getKey().startsWith("test-header-"))
                .map(e -> e.getKey() + "=" + String.join("", e.getValue()))
                .toList();

        assertThat(res.statusCode(), is(200));
        assertThat(actual, Matchers.containsInAnyOrder(expected.toArray(new String[0])));
    }

    @Test
    void serverInbound(WebServer server) throws IOException, InterruptedException {
        URI base = URI.create("http://localhost:" + server.port());
        HttpClient client = http2Client(base);

        HttpRequest.Builder req = HttpRequest.newBuilder()
                .timeout(TIMEOUT)
                .GET();

        Set<String> expected = new HashSet<>(500);
        for (int i = 0; i < 800; i++) {
            req.setHeader("test-header-" + i, DATA + i);
            expected.add("test-header-" + i + "=" + DATA + i);
        }

        HttpResponse<String> res = client.send(req.uri(base.resolve("/cont-in")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(res.statusCode(), is(200));
        assertThat(List.of(res.body().split("\n")), Matchers.containsInAnyOrder(expected.toArray(new String[0])));
    }

    @Test
    void serverInboundTooLarge(WebServer server) throws IOException, InterruptedException {
        URI base = URI.create("http://localhost:" + server.port());
        HttpClient client = http2Client(base);

        HttpRequest.Builder req = HttpRequest.newBuilder()
                .timeout(TIMEOUT)
                .GET();

        for (int i = 0; i < 5200; i++) {
            req.setHeader("test-header-" + i, DATA + i);
        }

        // There is no way how to access GO_AWAY status code and additional data with JDK Http client
        Assertions.assertThrows(IOException.class,
                () -> client.send(req.uri(base.resolve("/cont-in")).build(),
                        HttpResponse.BodyHandlers.ofString()));
    }

    private HttpClient http2Client(URI base) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(TIMEOUT)
                .build();

        HttpRequest req = HttpRequest.newBuilder()
                .timeout(TIMEOUT)
                .GET()
                .uri(base.resolve("/ping"))
                .build();

        // Java client can't do the prior knowledge
        client.send(req, HttpResponse.BodyHandlers.ofString());
        return client;
    }

}
