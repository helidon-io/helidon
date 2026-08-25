/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientRequest;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.noHeader;
import static io.helidon.http.Status.INTERNAL_SERVER_ERROR_500;
import static io.helidon.http.Status.OK_200;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class KeepAliveTest {
    private final Http1Client webClient;
    private final URI uri;

    KeepAliveTest(Http1Client client, URI uri) {
        this.webClient = client;
        this.uri = uri;
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        server.writeQueueLength(2);
        server.smartAsyncWrites(true);
    }

    @SetUpRoute
    static void router(HttpRouting.Builder router) {
        router.route(Method.PUT, "/plain", (req, res) -> {
            try (InputStream in = req.content().inputStream()) {
                byte[] buffer = new byte[128];
                while (in.read(buffer) > 0) {
                    // just ignore it
                }
                res.send("done");
            } catch (Exception e) {
                res.status(INTERNAL_SERVER_ERROR_500)
                        .send(e.getMessage());
            }
        }).route(Method.PUT, "/close", (req, res) -> {
            byte[] buffer = new byte[10];
            try (InputStream in = req.content().inputStream()) {
                in.read(buffer);
                throw new RuntimeException("BOOM!");
            } catch (IOException e) {
                res.status(INTERNAL_SERVER_ERROR_500)
                        .send(e.getMessage());
            }
        }).get("/request-close", (_, res) -> res.send("done"))
                .get("/response-close", (_, res) -> res.header(HeaderValues.CONNECTION_CLOSE)
                        .send("done"))
                .get("/request-close-stream", (_, res) -> sendStreamingResponse(res))
                .get("/response-close-stream", (_, res) -> {
                    res.header(HeaderValues.CONNECTION_CLOSE);
                    sendStreamingResponse(res);
                })
                .route(Method.PUT, "/response-close-entity", (req, res) -> {
                    req.content().as(String.class);
                    res.header(HeaderValues.CONNECTION_CLOSE)
                            .send("done");
                });
    }

    @RepeatedTest(100)
    void sendWithKeepAlive() {
        try (HttpClientResponse response = testCall(webClient, true, "/plain", OK_200)) {
            assertThat(response.headers(), noHeader(HeaderNames.CONNECTION));
        }

    }

    @RepeatedTest(100)
    void sendWithoutKeepAlive() {
        try (HttpClientResponse response = testCall(webClient, false, "/plain", OK_200)) {
            assertThat(response.headers(), hasHeader(HeaderValues.CONNECTION_CLOSE));
        }
    }

    @RepeatedTest(100)
    void sendWithKeepAliveExpectKeepAlive() {
        // we attempt to fully consume request entity, if succeeded, we keep connection keep-alive
        try (HttpClientResponse response = testCall(webClient, true, "/close", INTERNAL_SERVER_ERROR_500)) {
            assertThat(response.headers(), noHeader(HeaderNames.CONNECTION));
        }
    }

    @Test
    void requestConnectionCloseClosesSocket() throws Exception {
        assertConnectionIsClosed(Method.GET, "/request-close", null, List.of("Connection: close"));
    }

    @Test
    void requestConnectionCloseWithEntityClosesSocket() throws Exception {
        assertConnectionIsClosed(Method.PUT, "/plain", "content", List.of("Connection: close"));
    }

    @Test
    void responseConnectionCloseClosesSocket() throws Exception {
        assertConnectionIsClosed(Method.GET, "/response-close", null, List.of());
    }

    @Test
    void responseConnectionCloseWithEntityClosesSocket() throws Exception {
        assertConnectionIsClosed(Method.PUT, "/response-close-entity", "content", List.of());
    }

    @Test
    void requestConnectionCloseWithStreamingResponseClosesSocket() throws Exception {
        assertConnectionIsClosed(Method.GET,
                                 "/request-close-stream",
                                 null,
                                 List.of("Connection: close"));
    }

    @Test
    void responseConnectionCloseWithStreamingResponseClosesSocket() throws Exception {
        assertConnectionIsClosed(Method.GET, "/response-close-stream", null, List.of());
    }

    private static HttpClientResponse testCall(Http1Client client,
                                               boolean keepAlive,
                                               String path,
                                               Status expectedStatus) {

        Http1ClientRequest request = client.method(Method.PUT)
                .uri(path);

        if (!keepAlive) {
            request.header(HeaderValues.CONNECTION_CLOSE);
        }

        Http1ClientResponse response = request
                .outputStream(it -> {
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.close();
                });

        assertThat(response.status(), is(expectedStatus));

        return response;
    }

    private static void sendStreamingResponse(ServerResponse response) {
        try (OutputStream outputStream = response.outputStream()) {
            outputStream.write("first".getBytes(StandardCharsets.UTF_8));
            outputStream.write("second".getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void assertConnectionIsClosed(Method method, String path, String payload, List<String> headers) throws Exception {
        try (SocketHttpClient client = SocketHttpClient.create(uri.getHost(), uri.getPort(), Duration.ofSeconds(2))) {
            String response = client.sendAndReceive(method, path, payload, headers);
            assertThat(SocketHttpClient.statusFromResponse(response), is(OK_200));
            assertThat(SocketHttpClient.headersFromResponse(response), hasHeader(HeaderValues.CONNECTION_CLOSE));
            client.assertConnectionIsClosed();
        }
    }

}
