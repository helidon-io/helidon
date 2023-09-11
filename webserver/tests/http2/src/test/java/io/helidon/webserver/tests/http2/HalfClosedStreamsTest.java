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
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static io.helidon.http.Method.GET;
import static io.helidon.http.Method.POST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@ServerTest
class HalfClosedStreamsTest {

    private final int port;
    private final URI uri;

    HalfClosedStreamsTest(WebServer server) {
        this.port = server.port();
        uri = URI.create("http://localhost:" + port + "/test");
    }

    @SetUpRoute
    static void router(HttpRouting.Builder router) {
        Handler peerPortRturningHandler = (req, res) -> res.send(String.valueOf(req.remotePeer().port()));
        router.route(GET, "/test", peerPortRturningHandler)
                .route(Http2Route.route(POST, "/test", peerPortRturningHandler));
    }

    @Test
    void closeDataFrameAfterHandlerIsDone() {
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_2)
                .build()) {

            // Upgrade to http2 GET (jdk client doesn't support prior-knowledge)
            int expectedClientPort = getClientPort(client);

            PipedInputStream in = new PipedInputStream();
            PipedOutputStream out = new PipedOutputStream(in);

            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofInputStream(() -> in);

            // Force the client to send end-stream flagged data frame after server handler finished
            Thread.ofVirtual().start(() -> {
                try {
                    Thread.sleep(300);
                    out.close();
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });

            var respPut = client.send(HttpRequest.newBuilder().POST(bodyPublisher).uri(uri).build(),
                                      HttpResponse.BodyHandlers.ofString());

            assertThat(respPut.statusCode(), is(200));
            assertThat(Integer.parseInt(respPut.body()), is(expectedClientPort));

            // Another call would either fail, or be executed on a new connection(different client port)
            // in case locally half-closed stream is dropped after handler finishes
            int latestClientPort = getClientPort(client);
            assertThat(latestClientPort, is(expectedClientPort));

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    private int getClientPort(HttpClient client) {
        HttpResponse<String> r = null;
        try {
            r = client.send(HttpRequest.newBuilder()
                                    .GET()
                                    .uri(uri).build(),
                            HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            fail(e);
        }
        assertThat(r.statusCode(), is(200));
        assertTrue(r.body().matches("\\d+"));
        return Integer.parseInt(r.body());
    }
}
