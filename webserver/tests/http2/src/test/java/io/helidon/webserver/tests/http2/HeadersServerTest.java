/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientProtocolConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2ConnectionSelector;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.http2.Http2Upgrader;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static io.helidon.http.Method.GET;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@ServerTest
public class HeadersServerTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String DATA = "Helidon!!!".repeat(10);
    private static final Header TEST_TRAILER_HEADER = HeaderValues.create("test-trailer", "trailer-value");
    private final Http2Client client;

    HeadersServerTest(WebServer server) {
        client = Http2Client.builder()
                .baseUri("http://localhost:" + server.port())
                .protocolConfig(Http2ClientProtocolConfig.builder().priorKnowledge(true).build())
                .build();
    }

    @SetUpServer
    static void setUpServer(WebServerConfig.Builder serverBuilder) {
        Http2Config http2Config = Http2Config.builder()
                .sendErrorDetails(true)
                .maxHeaderListSize(128_000)
                .build();

        serverBuilder.port(-1)
                .protocolsDiscoverServices(false)
                // HTTP/2 prior knowledge config
                .addConnectionSelector(Http2ConnectionSelector.builder()
                                               .http2Config(http2Config)
                                               .build())
                // HTTP/1.1 -> HTTP/2 upgrade config
                .addConnectionSelector(Http1ConnectionSelector.builder()
                                               .config(Http1Config.create())
                        .addUpgrader(Http2Upgrader.create(http2Config))
                        .build());
    }

    @SetUpRoute
    static void router(HttpRouting.Builder router) {
        router.error(IllegalStateException.class, (req, res, t) -> res.status(500).send(t.getMessage()));
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
                                                  .map(h -> h.name() + "=" + h.get())
                                                  .collect(Collectors.joining("\n"));
                                          res.send(joinedHeaders);
                                      }
        ));
        router.route(Http2Route.route(GET, "/trailers-stream",
                                      (req, res) -> {
                                          res.header(HeaderNames.TRAILER, TEST_TRAILER_HEADER.name());
                                          try (var os = res.outputStream()) {
                                              os.write(DATA.getBytes());
                                              os.write(DATA.getBytes());
                                              os.write(DATA.getBytes());
                                              res.trailers().add(TEST_TRAILER_HEADER);
                                          }
                                      }
        ));
        router.route(Http2Route.route(GET, "/trailers-stream-result",
                                      (req, res) -> {
                                          try (var os = res.outputStream()) {
                                              os.write(DATA.getBytes());
                                              os.write(DATA.getBytes());
                                              os.write(DATA.getBytes());
                                              res.streamResult("Kaboom!");
                                          }
                                      }
        ));
        router.route(Http2Route.route(GET, "/trailers",
                                      (req, res) -> {
                                          res.header(HeaderNames.TRAILER, TEST_TRAILER_HEADER.name());
                                          res.trailers().add(TEST_TRAILER_HEADER);
                                          res.send(DATA.repeat(3));
                                      }
        ));
        router.route(Http2Route.route(GET, "/trailers-no-trailers",
                                      (req, res) -> {
                                          res.trailers().add(TEST_TRAILER_HEADER);
                                          res.send(DATA);
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
        try (HttpClient client = http2Client(base)) {

            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .timeout(TIMEOUT)
                    .GET();

            for (int i = 0; i < 5200; i++) {
                req.setHeader("test-header-" + i, DATA + i);
            }

            // There is no way how to access GO_AWAY status code and additional data with JDK Http client
            try {
                HttpResponse<String> response = client.send(req.uri(base.resolve("/cont-in")).build(),
                                                            HttpResponse.BodyHandlers.ofString());

                int status = response.statusCode();
                // since Java 24, we get a 400 back and not an exception
                assertThat("IOException or status 400 was expected, but go status " + response.statusCode()
                                   + ", headers: " + response.headers()
                                   + ", and response: " + response.body(),
                           status,
                           is(400));
            } catch (IOException e) {
                // this is expected
            }
        }
    }

    @Test
    void trailersEntity() throws IOException {
        ClientResponseTyped<InputStream> res = client
                .get("/trailers")
                .request(InputStream.class);
        try (var is = res.entity()) {
            is.readAllBytes();
        }
        assertThat(res.trailers(), hasHeader(TEST_TRAILER_HEADER));
    }

    @Test
    void trailersStream() throws IOException {
        ClientResponseTyped<InputStream> res = client
                .get("/trailers-stream")
                .request(InputStream.class);
        try (var is = res.entity()) {
            is.readAllBytes();
        }
        assertThat(res.trailers(), hasHeader(TEST_TRAILER_HEADER));
    }

    @Test
    void trailersStreamResult() throws IOException {
        ClientResponseTyped<InputStream> res = client
                .get("/trailers-stream-result")
                .header(HeaderValues.TE_TRAILERS)
                .request(InputStream.class);
        try (var is = res.entity()) {
            is.readAllBytes();
        }
        assertThat(res.trailers(), hasHeader(HeaderValues.create("stream-result", "Kaboom!")));
    }

    @Test
    void trailersNoTrailers() {
        ClientResponseTyped<String> res = client
                .get("/trailers-no-trailers")
                .request(String.class);

        assertThat(res.status(), is(Status.INTERNAL_SERVER_ERROR_500));
        assertThat(res.entity(), is(
                "Trailers are supported only when request came with 'TE: trailers' header or "
                        + "response headers have trailer names definition 'Trailer: <trailer-name>'"));
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
