/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.sse.SseEvent;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.sse.SseSink;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ServerTest
class SseHttp2Test {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.route(Http2Route.route(Method.GET, "/sse", (req, res) -> {
                    try (SseSink sink = res.sink(SseSink.TYPE)) {
                        sink.emit(SseEvent.create("first"))
                                .emit(SseEvent.create("second"));
                    }
                }))
                .route(Http2Route.route(Method.GET, "/ping", (req, res) -> res.send("ok")));
    }

    @Test
    void testSseClose(WebServer server) throws IOException, InterruptedException {
        URI uri = URI.create("http://localhost:" + server.port());
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(TIMEOUT)
                .build();

        HttpResponse<String> sseResponse = client.send(HttpRequest.newBuilder()
                                                               .uri(uri.resolve("/sse"))
                                                               .timeout(TIMEOUT)
                                                               .header(HeaderNames.ACCEPT.lowerCase(), "text/event-stream")
                                                               .GET()
                                                               .build(),
                                                       HttpResponse.BodyHandlers.ofString());

        assertThat(sseResponse.statusCode(), is(Status.OK_200.code()));
        assertThat(sseResponse.version(), is(HttpClient.Version.HTTP_2));
        assertThat(sseResponse.headers().firstValue(HeaderNames.CONTENT_TYPE.lowerCase()).orElseThrow(),
                   is("text/event-stream"));
        assertThat(sseResponse.body(), is("data:first\n\ndata:second\n\n"));

        // verify the SSE sink closed only the HTTP/2 stream and left the connection usable
        HttpResponse<String> pingResponse = client.send(HttpRequest.newBuilder()
                                                                .uri(uri.resolve("/ping"))
                                                                .timeout(TIMEOUT)
                                                                .GET()
                                                                .build(),
                                                        HttpResponse.BodyHandlers.ofString());

        assertThat(pingResponse.statusCode(), is(Status.OK_200.code()));
        assertThat(pingResponse.version(), is(HttpClient.Version.HTTP_2));
        assertThat(pingResponse.body(), is("ok"));
    }
}
