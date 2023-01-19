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

package io.helidon.webserver;

import io.helidon.common.LogConfig;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.webserver.utils.SocketHttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;

class Continue100Test {

    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(10);
    private static final String CONTINUE_IMMEDIATELY_SOCKET = "continueImmediately";
    private static final Map<String, CompletableFuture<String>> BLOCKER_MAP = new ConcurrentHashMap<>();
    private static WebServer webServer;
    private static int defaultPort;
    private static int contImmediatelyPort;

    @BeforeAll
    static void beforeAll() {
        Logger.getLogger("io.helidon.webserver.HttpInitializer").setLevel(Level.FINE);
        LogConfig.configureRuntime();

        Routing routing = Routing.builder()
                .any("/redirect", (req, res) ->
                        res.status(301)
                                .addHeader(Http.Header.LOCATION, "/")
                                // force 301 to not use chunked encoding
                                // https://github.com/helidon-io/helidon/issues/5713
                                .addHeader(Http.Header.CONTENT_LENGTH, "0")
                                .send()
                )
                .any("/", (req, res) -> {
                    if (Boolean.parseBoolean(req.headers().first("test-fail-before-read").orElse("false"))) {
                        res.status(Http.Status.EXPECTATION_FAILED_417).send();
                        return;
                    }

                    Optional<String> blockId = req.headers().first("test-block-id");
                    // Block request content dump if blocker assigned
                    Single.create(blockId.map(BLOCKER_MAP::get).orElse(CompletableFuture.completedFuture("")))
                            .flatMapSingle(unused -> req.content().as(String.class))
                            // Requesting date from content triggers 100 continue by default
                            .forSingle(s -> {
                                res.send("Got " + s.getBytes().length + " bytes of data");
                            });
                }).build();

        webServer = WebServer.builder()
                .routing(() -> routing)
                .addSocket(SocketConfiguration.builder()
                        .name(CONTINUE_IMMEDIATELY_SOCKET)
                        .continueImmediately(true)
                        .build(), routing)
                .build()
                .start()
                .await(TEST_TIMEOUT);

        defaultPort = webServer.port();
        contImmediatelyPort = webServer.port(CONTINUE_IMMEDIATELY_SOCKET);
    }

    @AfterAll
    static void afterAll() {
        webServer.shutdown().await(TEST_TIMEOUT);
    }

    @Test
    void continue100Post() throws Exception {
        try (SocketHttpClient socketHttpClient = new SocketHttpClient(defaultPort)) {
            String content = "looong payload data";
            socketHttpClient
                    .manualRequest("""                            
                            POST / HTTP/1.1
                            Host: localhost:%d
                            Expect: 100-continue
                            Accept: */*
                            test-fail-before-read: false
                            Content-Length: %d
                            Content-Type: application/x-www-form-urlencoded
                                                             
                            """, defaultPort, content.length())
                    .awaitResponse("HTTP/1.1 100 Continue", "\n\n")
                    .continuePayload(content);

            String received = socketHttpClient.receive();
            assertThat(received, startsWith("HTTP/1.1 200 OK"));
            assertThat(received, endsWith("Got 19 bytes of data"));
        }
    }
    @Test
    void continue100ImmediatelyPost() throws Exception {
        String blockId = "continue100ImmediatelyPost - " + UUID.randomUUID();
        BLOCKER_MAP.put(blockId, new CompletableFuture<>());
        try (SocketHttpClient socketHttpClient = new SocketHttpClient(contImmediatelyPort)) {
            String content = "looong payload data";
            socketHttpClient
                    .manualRequest("""                            
                            POST / HTTP/1.1
                            Host: localhost:%d
                            Expect: 100-continue
                            Accept: */*
                            test-fail-before-read: false
                            test-block-id: %s
                            Content-Length: %d
                            Content-Type: application/x-www-form-urlencoded
                                                             
                            """, contImmediatelyPort, blockId, content.length())
                    // Needs to respond continue before request content is requested
                    .awaitResponse("HTTP/1.1 100 Continue", "\n\n")
                    // Unblock request data dump
                    .then(sc -> BLOCKER_MAP.get(blockId).complete(blockId))
                    .continuePayload(content);

            String received = socketHttpClient.receive();
            assertThat(received, startsWith("HTTP/1.1 200 OK"));
            assertThat(received, endsWith("Got 19 bytes of data"));
        }
    }

    @Test
    void redirect() throws Exception {
        try (SocketHttpClient socketHttpClient = new SocketHttpClient(defaultPort)) {
            String content = "looong payload data";
            socketHttpClient
                    .manualRequest("""                            
                            POST /redirect HTTP/1.1
                            Host: localhost:%d
                            Expect: 100-continue
                            Accept: */*
                            test-fail-before-read: false
                            Content-Length: %d
                            Content-Type: application/x-www-form-urlencoded
                                                             
                            """, defaultPort, content.length())
                    .awaitResponse("HTTP/1.1 301 Moved Permanently\n", "\n\n");
            socketHttpClient
                    .manualRequest("""                            
                            POST / HTTP/1.1
                            Host: localhost:%d
                            Expect: 100-continue
                            Accept: */*
                            test-fail-before-read: false
                            Content-Length: %d
                            Content-Type: application/x-www-form-urlencoded
                                                             
                            """, defaultPort, content.length())
                    .awaitResponse("HTTP/1.1 100 Continue", "\n\n")
                    .continuePayload(content);

            String received = socketHttpClient.receive();
            assertThat(received, startsWith("HTTP/1.1 200 OK"));
            assertThat(received, endsWith("Got 19 bytes of data"));
        }
    }

    /**
     * RFC9110 10.1.1
     *
     * A client that sends a 100-continue expectation is not required to wait for any specific length of time;
     * such a client MAY proceed to send the content even if it has not yet received a response. Furthermore,
     * since 100 (Continue) responses cannot be sent through an HTTP/1.0 intermediary, such a client SHOULD NOT
     * wait for an indefinite period before sending the content.
     *
     * @throws Exception
     */
    @Test
    void continueWithoutContinue() throws Exception {
        try (SocketHttpClient socketHttpClient = new SocketHttpClient(defaultPort)) {
            String content = "looong payload data";
            socketHttpClient
                    .manualRequest("""                            
                            POST / HTTP/1.1
                            Host: localhost:%d
                            Expect: 100-continue
                            Accept: */*
                            test-fail-before-read: false
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
        }
    }
    @Test
    void continue100Put() throws Exception {
        try (SocketHttpClient socketHttpClient = new SocketHttpClient(defaultPort)) {
            String content = "looong payload data";
            socketHttpClient
                    .manualRequest("""                            
                            PUT / HTTP/1.1
                            Host: localhost:%d
                            Expect: 100-continue
                            Accept: */*
                            test-fail-before-read: false
                            Content-Length: %d
                            Content-Type: application/x-www-form-urlencoded
                                                             
                            """, defaultPort, content.length())
                    .awaitResponse("HTTP/1.1 100 Continue", "\n\n")
                    .continuePayload(content);

            String received = socketHttpClient.receive();
            assertThat(received, startsWith("HTTP/1.1 200 OK"));
            assertThat(received, endsWith("Got 19 bytes of data"));
        }
    }

    @Test
    void expectationFailed() throws Exception {
        try (SocketHttpClient socketHttpClient = new SocketHttpClient(defaultPort)) {
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
                                                             
                            """, defaultPort, content.length());

            String received = socketHttpClient.receive();
            assertThat(received, startsWith("HTTP/1.1 417"));
        }
    }

    @Test
    void notFound404() throws Exception {
        try (SocketHttpClient socketHttpClient = new SocketHttpClient(defaultPort)) {
            String content = "looong payload data";
            socketHttpClient
                    .manualRequest("""                            
                            POST /test HTTP/1.1
                            Host: localhost:%d
                            Expect: 100-continue
                            Accept: */*
                            test-fail-before-read: false
                            Content-Length: %d
                            Content-Type: application/x-www-form-urlencoded
                                                             
                            """, defaultPort, content.length());

            String received = socketHttpClient.receive();
            assertThat(received, startsWith("HTTP/1.1 404"));
        }
    }
}
