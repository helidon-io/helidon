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
package io.helidon.nima.tests.integration.server;

import io.helidon.common.http.Http;
import io.helidon.common.http.PathMatchers;
import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.testing.junit5.webserver.SetUpServer;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.Handler;
import io.helidon.nima.webserver.http.HttpRouting;
import io.helidon.nima.webserver.http1.DefaultHttp1Config;
import io.helidon.nima.webserver.http1.Http1ConnectionProvider;
import io.helidon.nima.webserver.spi.ServerConnectionProvider;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;

@ServerTest
class Continue100ImmediatelyTest {

    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Map<String, CompletableFuture<String>> BLOCKER_MAP = new ConcurrentHashMap<>();
    private static int defaultPort;

    private static Handler anyHandler = (req, res) -> {
        if (Boolean.parseBoolean(req.headers()
                .first(Http.Header.create("test-fail-before-read"))
                .orElse("false"))) {
            res.status(Http.Status.EXPECTATION_FAILED_417).send();
            return;
        }

        if (Boolean.parseBoolean(req.headers()
                .first(Http.Header.create("test-throw-before-read"))
                .orElse("false"))) {
            throw new RuntimeException("BOOM!!!");
        }

        Optional<String> blockId = req.headers()
                .first(Http.Header.create("test-block-id"));
        // Block request content dump if blocker assigned
        blockId.map(BLOCKER_MAP::get)
                .orElse(CompletableFuture.completedFuture(""))
                .get(TEST_TIMEOUT.getSeconds(), TimeUnit.SECONDS);

        // Requesting data from content triggers 100 continue by default
        String s = req.content().as(String.class);

        if (Boolean.parseBoolean(req.headers()
                .first(Http.Header.create("test-throw-after-read"))
                .orElse("false"))) {
            throw new RuntimeException("BOOM!!!");
        }

        res.send("Got " + s.getBytes().length + " bytes of data");
    };

    @SetUpServer
    static void server(WebServer.Builder wsb){
        ServerConnectionProvider http1 = Http1ConnectionProvider.builder()
                .http1Config(DefaultHttp1Config.builder()
                        .continueImmediately(true)
                        .build())
                .build();

        wsb.addConnectionProvider(http1);
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.route(Http.Method.predicate(Http.Method.PUT, Http.Method.POST),
                        PathMatchers.exact("/redirect"), (req, res) ->

                                res.status(Http.Status.MOVED_PERMANENTLY_301)
                                        .header(Http.Header.LOCATION, "/")
                                        // force 301 to not use chunked encoding
                                        // https://github.com/helidon-io/helidon/issues/5713
                                        .header(Http.Header.CONTENT_LENGTH, "0")
                                        .send()
                )
                .route(Http.Method.predicate(Http.Method.PUT, Http.Method.POST),
                        PathMatchers.exact("/"), anyHandler)
                .route(Http.Method.predicate(Http.Method.GET),
                        PathMatchers.exact("/"), (req, res) -> res.status(Http.Status.OK_200).send("GET TEST"));
    }

    public Continue100ImmediatelyTest(WebServer server) {
        defaultPort = server.port();
    }

    @Test
    void continue100ImmediatelyPost() throws Exception {
        String blockId = "continue100ImmediatelyPost - " + UUID.randomUUID();
        BLOCKER_MAP.put(blockId, new CompletableFuture<>());
        try (SocketHttpClient socketHttpClient = SocketHttpClient.create(defaultPort)) {
            String content = "looong payload data";
            socketHttpClient
                    .manualRequest("""                            
                            POST / HTTP/1.1
                            Host: localhost:%d
                            Expect: 100-continue
                            Accept: */*
                            test-block-id: %s
                            Content-Length: %d
                            Content-Type: application/x-www-form-urlencoded
                                                             
                            """, defaultPort, blockId, content.length())
                    // Needs to respond continue before request content is requested
                    .awaitResponse("HTTP/1.1 100 Continue", "\n\n")
                    .then(sc -> BLOCKER_MAP.get(blockId).complete(blockId))
                    // Unblock request data dump
                    .continuePayload(content);

            String received = socketHttpClient.receive();
            assertThat(received, startsWith("HTTP/1.1 200 OK"));
            assertThat(received, endsWith("Got 19 bytes of data"));

            checkKeepAlive(socketHttpClient);
        }
    }

    @Test
    void redirect() throws Exception {
        try (SocketHttpClient socketHttpClient = SocketHttpClient.create(defaultPort)) {
            String content = "looong payload data";
            socketHttpClient
                    .manualRequest("""                            
                            POST /redirect HTTP/1.1
                            Host: localhost:%d
                            Expect: 100-continue
                            Accept: */*
                            Content-Length: %d
                            Content-Type: application/x-www-form-urlencoded
                                                             
                            """, defaultPort, content.length())
                    .awaitResponse("HTTP/1.1 100 Continue\n", "\n\n")
                    .continuePayload(content)
                    .awaitResponse("HTTP/1.1 301 Moved Permanently\n", "\n\n");
            socketHttpClient
                    .manualRequest("""                            
                            POST / HTTP/1.1
                            Host: localhost:%d
                            Expect: 100-continue
                            Accept: */*
                            Content-Length: %d
                            Content-Type: application/x-www-form-urlencoded
                                                             
                            """, defaultPort, content.length())
                    .awaitResponse("HTTP/1.1 100 Continue", "\n\n")
                    .continuePayload(content);

            String received = socketHttpClient.receive();
            assertThat(received, startsWith("HTTP/1.1 200 OK"));
            assertThat(received, endsWith("Got 19 bytes of data"));

            checkKeepAlive(socketHttpClient);
        }
    }

    /**
     * RFC9110 10.1.1
     * <p>
     * A client that sends a 100-continue expectation is not required to wait for any specific length of time;
     * such a client MAY proceed to send the content even if it has not yet received a response. Furthermore,
     * since 100 (Continue) responses cannot be sent through an HTTP/1.0 intermediary, such a client SHOULD NOT
     * wait for an indefinite period before sending the content.
     *
     * @throws Exception
     */
    @Test
    void continueWithoutContinue() throws Exception {
        try (SocketHttpClient socketHttpClient = SocketHttpClient.create(defaultPort)) {
            String content = "looong payload data";
            socketHttpClient
                    .manualRequest("""                            
                            POST / HTTP/1.1
                            Host: localhost:%d
                            Expect: 100-continue
                            Accept: */*
                            Content-Length: %d
                            Content-Type: application/x-www-form-urlencoded
                                                             
                            """, defaultPort, content.length())
                    // Don't wait for continue
                    .continuePayload(content)
                    // Skip continue
                    .awaitResponse("HTTP/1.1 100 Continue", "\n\n");

            String received = socketHttpClient.receive();
            assertThat(received, startsWith("HTTP/1.1 200 OK"));
            assertThat(received, endsWith("Got 19 bytes of data"));

            checkKeepAlive(socketHttpClient);
        }
    }

    @Test
    void continue100Put() throws Exception {
        try (SocketHttpClient socketHttpClient = SocketHttpClient.create(defaultPort)) {
            String content = "looong payload data";
            socketHttpClient
                    .manualRequest("""                            
                            PUT / HTTP/1.1
                            Host: localhost:%d
                            Expect: 100-continue
                            Accept: */*
                            Content-Length: %d
                            Content-Type: application/x-www-form-urlencoded
                                                             
                            """, defaultPort, content.length())
                    .awaitResponse("HTTP/1.1 100 Continue", "\n\n")
                    .continuePayload(content);

            String received = socketHttpClient.receive();
            assertThat(received, startsWith("HTTP/1.1 200 OK"));
            assertThat(received, endsWith("Got 19 bytes of data"));

            checkKeepAlive(socketHttpClient);
        }
    }

    @Test
    void continue100PutFailBeforeConsume() throws Exception {
        try (SocketHttpClient socketHttpClient = SocketHttpClient.create(defaultPort)) {
            String content = "looong payload data";
            socketHttpClient
                    .manualRequest("""                            
                            PUT / HTTP/1.1
                            Host: localhost:%d
                            Expect: 100-continue
                            Accept: */*
                            test-throw-before-read: true
                            Content-Length: %d
                            Content-Type: application/x-www-form-urlencoded
                                                             
                            """, defaultPort, content.length())
                    .awaitResponse("HTTP/1.1 100 Continue", "\n\n")
                    .continuePayload(content);

            String received = socketHttpClient.receive();
            assertThat(received, startsWith("HTTP/1.1 500"));
            assertThat(received, endsWith("BOOM!!!"));

            checkKeepAlive(socketHttpClient);
        }
    }

    @Test
    void expectationFailed() throws Exception {
        try (SocketHttpClient socketHttpClient = SocketHttpClient.create(defaultPort)) {
            String content = "looong payload data";
            socketHttpClient
                    .manualRequest("""                            
                            POST / HTTP/1.1
                            Host: localhost:%d
                            Expect: 100-continue
                            Accept: */*
                            test-fail-before-read: true
                            Content-Length: %d
                            Content-Type: application/x-www-form-urlencoded
                                                             
                            """, defaultPort, content.length())
                    .awaitResponse("HTTP/1.1 100 Continue\n", "\n\n")
                    .continuePayload(content);

            String received = socketHttpClient.receive();
            assertThat(received, startsWith("HTTP/1.1 417"));

            checkKeepAlive(socketHttpClient);
        }
    }

    @Test
    void failAfterRead() throws Exception {
        try (SocketHttpClient socketHttpClient = SocketHttpClient.create(defaultPort)) {
            String content = "looong payload data";
            socketHttpClient
                    .manualRequest("""                            
                            POST / HTTP/1.1
                            Host: localhost:%d
                            Expect: 100-continue
                            Accept: */*
                            test-throw-after-read: true
                            Content-Length: %d
                            Content-Type: application/x-www-form-urlencoded
                                                             
                            """, defaultPort, content.length())
                    .awaitResponse("HTTP/1.1 100 Continue", "\n\n")
                    .continuePayload(content);

            String received = socketHttpClient.receive();
            assertThat(received, startsWith("HTTP/1.1 500"));
            assertThat(received, endsWith("BOOM!!!"));

            checkKeepAlive(socketHttpClient);
        }
    }

    @Test
    void notFound404() throws Exception {
        try (SocketHttpClient socketHttpClient = SocketHttpClient.create(defaultPort)) {
            String content = "looong payload data";
            socketHttpClient
                    .manualRequest("""                            
                            POST /test HTTP/1.1
                            Host: localhost:%d
                            Expect: 100-continue
                            Accept: */*
                            Content-Length: %d
                            Content-Type: application/x-www-form-urlencoded
                                                             
                            """, defaultPort, content.length())
                    .awaitResponse("HTTP/1.1 100 Continue\n", "\n\n")
                    .continuePayload(content);

            String received = socketHttpClient.receive();
            assertThat(received, startsWith("HTTP/1.1 404"));

            checkKeepAlive(socketHttpClient);
        }
    }

    private void checkKeepAlive(SocketHttpClient socketHttpClient) throws IOException {
        socketHttpClient.manualRequest("""
                            GET / HTTP/1.1
                            Host: localhost:%d
                            Accept: */*

                            """, defaultPort);

        String received = socketHttpClient.receive();
        assertThat(received, startsWith("HTTP/1.1 200 OK"));
        assertThat(received, endsWith("GET TEST"));
    }
}
