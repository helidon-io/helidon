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
package io.helidon.webserver.tests;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.PathMatchers;
import io.helidon.http.Status;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;

@ServerTest
class Continue100Test {

    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Map<String, CompletableFuture<String>> BLOCKER_MAP = new ConcurrentHashMap<>();
    private static int defaultPort;

    private static Handler anyHandler = (req, res) -> {
        if (Boolean.parseBoolean(req.headers()
                .first(HeaderNames.create("test-fail-before-read"))
                .orElse("false"))) {
            res.status(Status.EXPECTATION_FAILED_417).send();
            return;
        }

        if (Boolean.parseBoolean(req.headers()
                .first(HeaderNames.create("test-throw-before-read"))
                .orElse("false"))) {
            throw new RuntimeException("BOOM!!!");
        }

        Optional<String> blockId = req.headers()
                .first(HeaderNames.create("test-block-id"));
        // Block request content dump if blocker assigned
        blockId.map(BLOCKER_MAP::get)
                .orElse(CompletableFuture.completedFuture(""))
                .get(TEST_TIMEOUT.getSeconds(), TimeUnit.SECONDS);

        // Requesting data from content triggers 100 continue by default
        String s = req.content().as(String.class);

        if (Boolean.parseBoolean(req.headers()
                .first(HeaderNames.create("test-throw-after-read"))
                .orElse("false"))) {
            throw new RuntimeException("BOOM!!!");
        }

        res.send("Got " + s.getBytes().length + " bytes of data");
    };

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.route(Method.predicate(Method.PUT, Method.POST),
                     PathMatchers.exact("/redirect"), (req, res) ->

                                res.status(Status.MOVED_PERMANENTLY_301)
                                        .header(HeaderNames.LOCATION, "/")
                                        // force 301 to not use chunked encoding
                                        // https://github.com/helidon-io/helidon/issues/5713
                                        .header(HeaderNames.CONTENT_LENGTH, "0")
                                        .send()
                )
                .route(Method.predicate(Method.PUT, Method.POST),
                       PathMatchers.exact("/"), anyHandler)
                .route(Method.predicate(Method.PUT),
                       PathMatchers.exact("/chunked"), (req, res) -> {
                            try (InputStream is = req.content().inputStream();
                                    OutputStream os = res.outputStream()) {
                                new ByteArrayInputStream(is.readAllBytes()).transferTo(os);
                            }
                        })
                .route(Method.predicate(Method.GET),
                       PathMatchers.exact("/"), (req, res) -> res.status(Status.OK_200).send("GET TEST"));
    }

    public Continue100Test(WebServer server) {
        defaultPort = server.port();
    }

    @Test
    void continue100ChunkedPut() throws Exception {
        try (SocketHttpClient socketHttpClient = SocketHttpClient.create(defaultPort)) {
            socketHttpClient
                    .manualRequest("""                            
                            PUT /chunked HTTP/1.1
                            Host: localhost:%d
                            Expect: 100-continue
                            Accept: */*
                            Transfer-Encoding: chunked
                            Content-Type: text/plain
                                                             
                            """, defaultPort)
                    .awaitResponse("HTTP/1.1 100 Continue", "\n\n")
                    .sendChunk("This ")
                    .sendChunk("is ")
                    .sendChunk("chunked!")
                    .sendChunk("");

            String received = socketHttpClient.receive();
            assertThat(received, startsWith("HTTP/1.1 200 OK"));
            assertThat(received, endsWith("This is chunked!"));

            checkKeepAlive(socketHttpClient);
        }
    }
    @Test
    void continue100Post() throws Exception {
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
                    .awaitResponse("HTTP/1.1 100 Continue", "\n\n")
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
                                                             
                            """, defaultPort, content.length());

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
                                                             
                            """, defaultPort, content.length());

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
                                                             
                            """, defaultPort, content.length());

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
