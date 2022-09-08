/*
 * Copyright (c) 2017, 2022 Oracle and/or its affiliates.
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

package io.helidon.reactive.webserver;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.helidon.common.http.ClientResponseHeaders;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Multi;
import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.reactive.webclient.WebClient;

import org.hamcrest.collection.IsIterableWithSize;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.common.http.Http.HeaderValues.TRANSFER_ENCODING_CHUNKED;
import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.noHeader;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.collection.IsMapContaining.hasEntry;

/**
 * The PlainTest.
 */
class PlainTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(25);
    private static final Logger LOGGER = Logger.getLogger(PlainTest.class.getName());
    private static final RuntimeException TEST_EXCEPTION = new RuntimeException("BOOM!");
    private static WebServer webServer;
    private static SocketHttpClient client;

    /**
     * Start the Web Server
     */
    @BeforeAll
    static void startServer() {
        webServer = WebServer.builder()
                .defaultSocket(s -> s
                        .host("localhost")
                )
                .routing(r -> r.any((req, res) -> {
                            res.headers().set(TRANSFER_ENCODING_CHUNKED);
                            req.next();
                        })
                        .any("/exception", (req, res) -> {
                            throw new RuntimeException("my always thrown exception");
                        })
                        .get("/", (req, res) -> {
                            res.send("It works!");
                        })
                        .post("/unconsumed", (req, res) -> res.send("Payload not consumed!"))
                        .any("/deferred", (req, res) -> ForkJoinPool.commonPool().submit(() -> {
                            Thread.yield();
                            res.send("I'm deferred!");
                        }))
                        .trace("/trace", (req, res) -> {
                            res.send("In trace!");
                        })
                        .get("/force-chunked", (req, res) -> {
                            res.headers().set(TRANSFER_ENCODING_CHUNKED);
                            res.send("abcd");
                        })
                        .get("/multi", (req, res) -> {
                            res.send(Multi.just("test 1", "test 2", "test 3")
                                             .map(String::getBytes)
                                             .map(DataChunk::create));
                        })
                        .get("/multiFirstError", (req, res) -> {
                            res.send(Multi.error(TEST_EXCEPTION));
                        })
                        .get("/multiSecondError", (req, res) -> {
                            res.send(Multi.concat(Multi.just("test1\n").map(s -> DataChunk.create(s.getBytes())),
                                                  Multi.error(TEST_EXCEPTION)));
                        })
                        .get("/multiThirdError", (req, res) -> {
                            res.send(Multi.concat(Multi.just("test1\n").map(s -> DataChunk.create(s.getBytes())),
                                                  Multi.error(TEST_EXCEPTION)));
                        })
                        .get("/multiDelayedThirdError", (req, res) -> {
                            res.send(Multi.interval(100, 100, TimeUnit.MILLISECONDS,
                                                    Executors.newSingleThreadScheduledExecutor())
                                             .peek(i -> {
                                                 if (i > 2) {
                                                     throw TEST_EXCEPTION;
                                                 }
                                             })
                                             .map(i -> DataChunk.create(("test " + i).getBytes())));
                        })
                        .get("/multi", (req, res) -> {
                            res.send(Multi.just("test1", "test2")
                                             .map(i -> DataChunk.create(String.valueOf(i).getBytes())));
                        })
                        .get("/absoluteUri", (req, res) -> {
                            res.send(req.absoluteUri().toString());
                        })
                        .any(Handler.create(String.class, (req, res, entity) -> {
                            res.send("It works! Payload: " + entity);
                        })))
                .build()
                .start()
                .await(TIMEOUT);

        client = SocketHttpClient.create(webServer.port());

        LOGGER.info("Started server at: https://localhost:" + webServer.port());
    }

    @AfterAll
    static void close() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                    .await(TIMEOUT);
        }
        if (client != null) {
            client.close();
        }
    }

    @BeforeEach
    void resetSocketClient() {
        client.disconnect();
        client.connect();
    }
    @Test
    void getTest() {
        String s = client.sendAndReceive(Http.Method.GET, null);
        ClientResponseHeaders headers = SocketHttpClient.headersFromResponse(s);
        assertThat(headers, hasHeader(Http.HeaderValues.CONNECTION_KEEP_ALIVE));
        assertThat(SocketHttpClient.entityFromResponse(s, false), is("9\nIt works!\n0\n\n"));
    }

    @Test
    void getDeferredTest() {
        String s = client.sendAndReceive("/deferred", Http.Method.GET, null);
        assertThat(SocketHttpClient.entityFromResponse(s, true), is("d\nI'm deferred!\n0\n\n"));
    }

    @Test
    void getWithPayloadDeferredTest() {
        String s = client.sendAndReceive("/deferred", Http.Method.GET, "illegal-payload");
        assertThat(SocketHttpClient.entityFromResponse(s, true), is("d\nI'm deferred!\n0\n\n"));
    }

    @Test
    void getWithLargePayloadDeferredTest() {
        String s = client.sendAndReceive("/deferred",
                                         Http.Method.GET,
                                         SocketHttpClient.longData(100_000).toString());
        assertThat(SocketHttpClient.entityFromResponse(s, true), is("d\nI'm deferred!\n0\n\n"));
    }

    @Test
    void getWithPayloadTest() {
        String s = client.sendAndReceive(Http.Method.GET, "test-payload");
        assertThat(SocketHttpClient.entityFromResponse(s, true), is("9\nIt works!\n0\n\n"));
    }

    @Test
    void postNoPayloadTest() {
        String s = client.sendAndReceive(Http.Method.POST, null);
        assertThat(SocketHttpClient.entityFromResponse(s, true), is("13\nIt works! Payload: \n0\n\n"));
    }

    @Test
    void simplePostTest() {
        String s = client.sendAndReceive(Http.Method.POST, "test-payload");
        assertThat(SocketHttpClient.entityFromResponse(s, true), is("1f\nIt works! Payload: test-payload\n0\n\n"));
    }

    @Test
    void twoGetsTest() {
        getTest();
        getTest();
    }

    @Test
    void twoGetsWithPayloadTest() {
        getWithPayloadTest();
        getWithPayloadTest();
    }

    @Test
    void testTwoGetsTheSameConnection() throws Exception {
        // open
        try (SocketHttpClient s = SocketHttpClient.create(webServer.port())) {
            // get
            s.request(Http.Method.GET);
            // assert
            assertThat(SocketHttpClient.entityFromResponse(s.receive(), true), is("9\nIt works!\n0\n\n"));
            // get
            s.request(Http.Method.GET);
            // assert
            assertThat(SocketHttpClient.entityFromResponse(s.receive(), true), is("9\nIt works!\n0\n\n"));
        }
    }

    @Test
    void testTwoPostsTheSameConnection() throws Exception {
        // open
        try (SocketHttpClient s = SocketHttpClient.create(webServer.port())) {
            // post
            s.request(Http.Method.POST, "test-payload-1");
            // assert
            assertThat(SocketHttpClient.entityFromResponse(s.receive(), true),
                       is("21\nIt works! Payload: test-payload-1\n0\n\n"));
            // post
            s.request(Http.Method.POST, "test-payload-2");
            // assert
            assertThat(SocketHttpClient.entityFromResponse(s.receive(), true),
                       is("21\nIt works! Payload: test-payload-2\n0\n\n"));
        }
    }

    @Test
    void postGetPostGetTheSameConnection() throws Exception {
        try (SocketHttpClient s = SocketHttpClient.create(webServer.port())) {
            // post
            s.request(Http.Method.POST, "test-payload-1");
            // assert
            assertThat(SocketHttpClient.entityFromResponse(s.receive(), true),
                       is("21\nIt works! Payload: test-payload-1\n0\n\n"));
            // get
            s.request(Http.Method.GET);
            // assert
            assertThat(SocketHttpClient.entityFromResponse(s.receive(), true), is("9\nIt works!\n0\n\n"));
            // post
            s.request(Http.Method.POST, "test-payload-2");
            // assert
            assertThat(SocketHttpClient.entityFromResponse(s.receive(), true),
                       is("21\nIt works! Payload: test-payload-2\n0\n\n"));
            // get
            s.request(Http.Method.GET);
            // assert
            assertThat(SocketHttpClient.entityFromResponse(s.receive(), true), is("9\nIt works!\n0\n\n"));
        }
    }

    @Test
    void getWithLargePayloadDoesNotCauseConnectionClose() throws Exception {
        // open
        try (SocketHttpClient s = SocketHttpClient.create(webServer.port())) {
            // get
            s.request(Http.Method.GET, SocketHttpClient.longData(100_000).toString());

            // assert
            assertThat(SocketHttpClient.entityFromResponse(s.receive(), true), is("9\nIt works!\n0\n\n"));
            s.assertConnectionIsOpen();
        }
    }

    @Test
    void traceWithAnyPayloadCausesConnectionCloseButDoesNotFail() throws Exception {
        // open
        try (SocketHttpClient s = SocketHttpClient.create(webServer.port())) {
            // get
            s.request(Http.Method.TRACE, "/trace", "small");

            // assert
            assertThat(SocketHttpClient.entityFromResponse(s.receive(), true), is("9\nIn trace!\n0\n\n"));
            s.assertConnectionIsClosed();
        }
    }

    @Test
    void traceWithAnyPayloadCausesConnectionCloseAndBadRequestWhenHandled() throws Exception {
        // open
        try (SocketHttpClient s = SocketHttpClient.create(webServer.port())) {
            // get
            s.request(Http.Method.TRACE, "small");

            // assert that the Handler.of ContentReader transforms the exception to 400 error
            assertThat(s.receive(), startsWith("HTTP/1.1 400 Bad Request\n"));
            s.assertConnectionIsClosed();
        }
    }

    @Test
    void deferredGetWithLargePayloadDoesNotCauseConnectionClose() throws Exception {
        // open
        try (SocketHttpClient s = SocketHttpClient.create(webServer.port())) {
            // get
            s.request(Http.Method.GET, "/deferred", SocketHttpClient.longData(100_000).toString());
            // assert
            assertThat(SocketHttpClient.entityFromResponse(s.receive(), true), is("d\nI'm deferred!\n0\n\n"));
            s.assertConnectionIsOpen();
        }
    }

    @Test
    void getWithIllegalSmallEnoughPayloadDoesntCauseConnectionClose() throws Exception {
        // open
        try (SocketHttpClient s = SocketHttpClient.create(webServer.port())) {
            // get
            s.request(Http.Method.GET, "illegal-but-small-enough-payload");

            // assert
            assertThat(SocketHttpClient.entityFromResponse(s.receive(), true), is("9\nIt works!\n0\n\n"));
            s.assertConnectionIsOpen();
        }
    }

    @Test
    void unconsumedSmallPostDataDoesNotCauseConnectionClose() throws Exception {
        // open
        try (SocketHttpClient s = SocketHttpClient.create(webServer.port())) {
            // get
            s.request(Http.Method.POST, "/unconsumed", "not-consumed-payload");
            String received = s.receive();
            System.out.println(received);
            // assert
            assertThat(SocketHttpClient.entityFromResponse(received, true), is("15\nPayload not consumed!\n0\n\n"));
            s.assertConnectionIsOpen();
        }
    }

    @Test
    void unconsumedLargePostDataDoesNotCauseConnectionClose() throws Exception {
        // open
        try (SocketHttpClient s = SocketHttpClient.create(webServer.port())) {
            // get
            s.request(Http.Method.POST, "/unconsumed", SocketHttpClient.longData(100_000).toString());

            // assert
            assertThat(SocketHttpClient.entityFromResponse(s.receive(), true), is("15\nPayload not consumed!\n0\n\n"));
            s.assertConnectionIsOpen();
        }
    }

    @Test
    void unconsumedDeferredLargePostDataDoesNotCauseConnectionClose() throws Exception {
        // open
        try (SocketHttpClient s = SocketHttpClient.create(webServer.port())) {
            // get
            s.request(Http.Method.POST, "/deferred", SocketHttpClient.longData(100_000).toString());

            // assert
            assertThat(SocketHttpClient.entityFromResponse(s.receive(), true), is("d\nI'm deferred!\n0\n\n"));
            s.assertConnectionIsOpen();
        }
    }

    @Test
    void errorHandlerWithGetPayloadDoesNotCauseConnectionClose() throws Exception {
        // open
        try (SocketHttpClient s = SocketHttpClient.create(webServer.port())) {
            // get
            s.request(Http.Method.GET, "/exception", "not-consumed-payload");

            // assert
            assertThat(s.receive(), startsWith("HTTP/1.1 500 Internal Server Error\n"));
            s.assertConnectionIsOpen();
        }
    }

    @Test
    void errorHandlerWithPostDataDoesNotCauseConnectionClose() throws Exception {
        // open
        try (SocketHttpClient s = SocketHttpClient.create(webServer.port())) {
            // get
            s.request(Http.Method.POST, "/exception", "not-consumed-payload");

            // assert
            assertThat(s.receive(), startsWith("HTTP/1.1 500 Internal Server Error\n"));
            s.assertConnectionIsOpen();
        }
    }

    @Test
    void testConnectionCloseWhenKeepAliveOff() throws Exception {
        try (SocketHttpClient s = SocketHttpClient.create(webServer.port())) {
            // get
            s.request(Http.Method.GET, "/", null, List.of("Connection: close"));

            // assert
            assertThat(SocketHttpClient.entityFromResponse(s.receive(), true), is("9\nIt works!\n0\n\n"));
            s.assertConnectionIsClosed();
        }
    }

    @Test
    void testForcedChunkedWithConnectionCloseHeader() {
        String s = client.sendAndReceive("/force-chunked",
                                         Http.Method.GET,
                                         null,
                                         List.of("Connection: close"));
        ClientResponseHeaders headers = SocketHttpClient.headersFromResponse(s);
        assertThat(headers, noHeader(Http.Header.CONNECTION));
        assertThat(headers, hasHeader(TRANSFER_ENCODING_CHUNKED));

        assertThat(SocketHttpClient.entityFromResponse(s, false), is("4\nabcd\n0\n\n"));
    }

    @Test
    void testConnectionCloseHeader() {
        String s = client.sendAndReceive("/",
                                         Http.Method.GET,
                                         null,
                                         List.of("Connection: close"));
        ClientResponseHeaders headers = SocketHttpClient.headersFromResponse(s);
        assertThat(headers, noHeader(Http.Header.CONNECTION));
        assertThat(SocketHttpClient.entityFromResponse(s, false), is("9\nIt works!\n0\n\n"));
    }

    @Test
    void testBadURL() {
        String s = client.sendAndReceive("/?p=|",
                                         Http.Method.GET,
                                         null,
                                         List.of("Connection: close"));
        assertThat(s, containsString("400 Bad Request"));
        ClientResponseHeaders headers = SocketHttpClient.headersFromResponse(s);
        assertThat(headers, hasHeader(Http.Header.CONTENT_TYPE));
        assertThat(headers, hasHeader(Http.Header.CONTENT_LENGTH));
    }

    @Test
    void testBadContentType() {
        String s = client.sendAndReceive("/",
                                         Http.Method.GET,
                                         null,
                                         List.of("Content-Type: %", "Connection: close"));
        assertThat(s, containsString("400 Bad Request"));
        ClientResponseHeaders headers = SocketHttpClient.headersFromResponse(s);
        assertThat(headers, hasHeader(Http.Header.CONTENT_TYPE));
        assertThat(headers, hasHeader(Http.Header.CONTENT_LENGTH));
    }

    @Test
    void testAbsouteUri() {
        String result = WebClient.create()
                .get()
                .uri("http://localhost:" + webServer.port() + "/absoluteUri?a=b")
                .request(String.class)
                .await(5, TimeUnit.SECONDS);

        assertThat(result, containsString("http://"));
        assertThat(result, containsString(String.valueOf(webServer.port())));
        assertThat(result, endsWith("/absoluteUri?a=b"));
    }

    @Test
    void testMulti() {
        String s = client.sendAndReceive("/multi",
                                         Http.Method.GET,
                                         null);
        assertThat(s, startsWith("HTTP/1.1 200 OK\n"));
        List<String> chunks = Arrays.stream(s.split("\\n[0-9]\\n?\\s*"))
                .skip(1)
                .collect(Collectors.toList());
        assertThat(chunks, contains("test 1", "test 2", "test 3"));
        Map<String, String> trailerHeaders = cutTrailerHeaders(s);
        assertThat(trailerHeaders.entrySet(), IsIterableWithSize.iterableWithSize(0));
    }

    /**
     * HTTP/1.1 500 Internal Server Error
     * Date: Thu, 9 Jul 2020 14:01:14 +0200
     * trailer: stream-status,stream-result
     * transfer-encoding: chunked
     * connection: keep-alive
     *
     * 0
     * stream-status: 500
     * stream-result: java.lang.RuntimeException: BOOM!
     */
    @Test
    void testMultiFirstError() {
        String s = client.sendAndReceive("/multiFirstError",
                                         Http.Method.GET,
                                         null);

        assertThat(s, startsWith("HTTP/1.1 500 Internal Server Error\n"));
        assertThat(SocketHttpClient.headersFromResponse(s), hasHeader(Http.Header.TRAILER));
        Map<String, String> trailerHeaders = cutTrailerHeaders(s);
        assertThat(trailerHeaders, hasEntry(equalToIgnoringCase("stream-status"), is("500")));
        assertThat(trailerHeaders, hasEntry(equalToIgnoringCase("stream-result"), is(TEST_EXCEPTION.toString())));
    }

    @Test
    void testMultiSecondError() {
        String s = client.sendAndReceive("/multiSecondError",
                                         Http.Method.GET,
                                         null);
        assertThat(s, startsWith("HTTP/1.1 200 OK\n"));
        Map<String, String> trailerHeaders = cutTrailerHeaders(s);
        assertThat(trailerHeaders, hasEntry("stream-status", "500"));
        assertThat(trailerHeaders, hasEntry("stream-result", TEST_EXCEPTION.toString()));
    }

    @Test
    void testMultiThirdError() {
        String s = client.sendAndReceive("/multiThirdError",
                                         Http.Method.GET,
                                         null);
        assertThat(s, startsWith("HTTP/1.1 200 OK\n"));
        Map<String, String> trailerHeaders = cutTrailerHeaders(s);
        assertThat(trailerHeaders, hasEntry("stream-status", "500"));
        assertThat(trailerHeaders, hasEntry("stream-result", TEST_EXCEPTION.toString()));
    }

    /**
     * HTTP/1.1 200 OK
     * Date: Thu, 9 Jul 2020 13:57:27 +0200
     * transfer-encoding: chunked
     * connection: keep-alive
     *
     * 6
     * test 0
     * 6
     * test 1
     * 6
     * test 2
     * 0
     * stream-status: 500
     * stream-result: java.lang.RuntimeException: BOOM!
     */
    @Test
    void testMultiDelayedThirdError() {
        String s = client.sendAndReceive("/multiDelayedThirdError",
                                         Http.Method.GET,
                                         null);
        assertThat(s, startsWith("HTTP/1.1 200 OK\n"));
        Map<String, String> headers = cutTrailerHeaders(s);
        assertThat(headers, hasEntry("stream-status", "500"));
        assertThat(headers, hasEntry("stream-result", TEST_EXCEPTION.toString()));
    }

    private Map<String, String> cutTrailerHeaders(String response) {
        Pattern trailerHeaderPattern = Pattern.compile("([^\\t,\\n,\\f,\\r, ,;,:,=]+)\\:\\s?([^\\n]+)");
        assertThat(response, notNullValue());
        int index = response.indexOf("\n0\n");
        return Arrays.stream(response.substring(index).split("\n"))
                .map(trailerHeaderPattern::matcher)
                .filter(Matcher::matches)
                .collect(HashMap::new, (map, matcher) -> map.put(matcher.group(1), matcher.group(2)), (m1, m2) -> {
                });
    }
}
