/*
 * Copyright (c) 2017, 2023 Oracle and/or its affiliates.
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

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Multi;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.utils.SocketHttpClient;

import org.hamcrest.collection.IsIterableWithSize;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.helidon.webserver.utils.SocketHttpClient.entityFromResponse;
import static io.helidon.webserver.utils.SocketHttpClient.headersFromResponse;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.collection.IsMapContaining.hasEntry;
import static org.hamcrest.collection.IsMapContaining.hasKey;

/**
 * The PlainTest.
 */
public class PlainTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(25);
    private static final Logger LOGGER = Logger.getLogger(PlainTest.class.getName());
    private static final RuntimeException TEST_EXCEPTION = new RuntimeException("BOOM!");
    private static WebServer webServer;
    private static String singleEntityResponse = "SingleEntity";
    private static String contentLengthDataPrefix = "Server:";
    private static String sSEEntityResponse = "id: 1\ndata: foo\nevent: bar";

    /**
     * Start the Web Server
     */
    @BeforeAll
    static void startServer() {
        webServer = WebServer.builder()
                .defaultSocket(s -> s
                        .host("localhost")
                )
                .routing(r -> r
                        // ex. path = "/emptyResponse200" or "/emptyResponse301"
                        .get("/emptyResponse*", (req, res) -> {
                            setResponseStatusCodeFromPath(req, res);
                            res.send();
                        })
                        // ex. path = "/singleResponse200" or "/singleResponse301"
                        .get("/singleEntityResponse*", (req, res) -> {
                            setResponseStatusCodeFromPath(req, res);
                            res.send(singleEntityResponse);
                        })
                        .get("/SSE", (req, res) -> {
                            res.headers().add(Http.Header.CONTENT_TYPE, "text/event-stream");
                            res.send(sSEEntityResponse);
                        })
                        // ex. path = "/multiFirstError" or "/multiFirstErrorChunked"
                        .get("/multiFirstError*", (req, res) -> {
                            setResponseToChunkFromPath(req, res);
                            res.send(Multi.error(TEST_EXCEPTION));
                        })
                        // ex. path = "/multiSecondError" or "/multiSecondErrorChunked"
                        .get("/multiSecondError*", (req, res) -> {
                            setResponseToChunkFromPath(req, res);
                            res.send(Multi.concat(Multi.just("test1\n").map(s -> DataChunk.create(s.getBytes())),
                                                  Multi.error(TEST_EXCEPTION)));
                        })
                        // ex. path = "/multiThirdError" or "/multiThirdErrorChunked"
                        .get("/multiThirdError*", (req, res) -> {
                            setResponseToChunkFromPath(req, res);
                            res.send(Multi.concat(Multi.just("test1\n").map(s -> DataChunk.create(s.getBytes())),
                                                  Multi.just("test2\n").map(s -> DataChunk.create(s.getBytes())),
                                                  Multi.error(TEST_EXCEPTION)));
                        })
                        .get("/contentLength*", (req, res) -> {
                            String path = req.path().toString();
                            int noOfChunks = Integer.valueOf(path.substring(path.length() - 1));
                            res.addHeader(Http.Header.CONTENT_LENGTH, String.valueOf((contentLengthDataPrefix.length() + 1) * noOfChunks))
                                    .send(Multi.range(0, noOfChunks)
                                                  .map(i -> contentLengthDataPrefix + i)
                                                  .map(String::getBytes)
                                                  .map(DataChunk::create));
                        })
                        .any((req, res) -> {
                            res.headers().add(Http.Header.TRANSFER_ENCODING, "chunked");
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
                        .get("/force-chunked*", (req, res) -> {
                            res.headers().put(Http.Header.TRANSFER_ENCODING, "chunked");
                            if (req.path().toString().contains("-emptyResponse")) {
                                // ex. path = "/force-chunked-emptyResponse200" or "/force-chunked-emptyResponse301"
                                setResponseStatusCodeFromPath(req, res);
                                res.send();
                            } else {
                                res.send("abcd");
                            }
                        })
                        .get("/multi", (req, res) -> {
                            res.send(Multi.just("test 1", "test 2", "test 3")
                                             .map(String::getBytes)
                                             .map(DataChunk::create));
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

        LOGGER.info("Started server at: https://localhost:" + webServer.port());
    }

    @AfterAll
    public static void close() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                    .await(TIMEOUT);
        }
    }

    @Test
    public void getTest() throws Exception {
        String s = SocketHttpClient.sendAndReceive(Http.Method.GET, null, webServer);
        Map<String, String> headers = headersFromResponse(s);
        assertThat(headers, hasEntry(equalToIgnoringCase("connection"), is("keep-alive")));
        assertThat(entityFromResponse(s, false), is("9\nIt works!\n0\n\n"));
    }

    @Test
    public void getDeferredTest() throws Exception {
        String s = SocketHttpClient.sendAndReceive("/deferred", Http.Method.GET, null, webServer);
        assertThat(entityFromResponse(s, true), is("d\nI'm deferred!\n0\n\n"));
    }

    @Test
    public void getWithPayloadDeferredTest() throws Exception {
        String s = SocketHttpClient.sendAndReceive("/deferred", Http.Method.GET, "illegal-payload", webServer);
        assertThat(entityFromResponse(s, true), is("d\nI'm deferred!\n0\n\n"));
    }

    @Test
    public void getWithLargePayloadDeferredTest() throws Exception {
        String s = SocketHttpClient.sendAndReceive("/deferred",
                                                   Http.Method.GET,
                                                   SocketHttpClient.longData(100_000).toString(),
                                                   webServer);
        assertThat(entityFromResponse(s, true), is("d\nI'm deferred!\n0\n\n"));
    }

    @Test
    public void getWithPayloadTest() throws Exception {
        String s = SocketHttpClient.sendAndReceive(Http.Method.GET, "test-payload", webServer);
        assertThat(entityFromResponse(s, true), is("9\nIt works!\n0\n\n"));
    }

    @Test
    public void postNoPayloadTest() throws Exception {
        String s = SocketHttpClient.sendAndReceive(Http.Method.POST, null, webServer);
        assertThat(entityFromResponse(s, true), is("13\nIt works! Payload: \n0\n\n"));
    }

    @Test
    public void simplePostTest() throws Exception {
        String s = SocketHttpClient.sendAndReceive(Http.Method.POST, "test-payload", webServer);
        assertThat(entityFromResponse(s, true), is("1f\nIt works! Payload: test-payload\n0\n\n"));
    }

    @Test
    public void twoGetsTest() throws Exception {
        getTest();
        getTest();
    }

    @Test
    public void twoGetsWithPayloadTest() throws Exception {
        getWithPayloadTest();
        getWithPayloadTest();
    }

    @Test
    public void testTwoGetsTheSameConnection() throws Exception {
        // open
        try (SocketHttpClient s = new SocketHttpClient(webServer)) {
            // get
            s.request(Http.Method.GET);
            // assert
            assertThat(entityFromResponse(s.receive(), true), is("9\nIt works!\n0\n\n"));
            // get
            s.request(Http.Method.GET);
            // assert
            assertThat(entityFromResponse(s.receive(), true), is("9\nIt works!\n0\n\n"));
        }
    }

    @Test
    public void testTwoPostsTheSameConnection() throws Exception {
        // open
        try (SocketHttpClient s = new SocketHttpClient(webServer)) {
            // post
            s.request(Http.Method.POST, "test-payload-1");
            // assert
            assertThat(entityFromResponse(s.receive(), true), is("21\nIt works! Payload: test-payload-1\n0\n\n"));
            // post
            s.request(Http.Method.POST, "test-payload-2");
            // assert
            assertThat(entityFromResponse(s.receive(), true), is("21\nIt works! Payload: test-payload-2\n0\n\n"));
        }
    }

    @Test
    public void postGetPostGetTheSameConnection() throws Exception {
        try (SocketHttpClient s = new SocketHttpClient(webServer)) {
            // post
            s.request(Http.Method.POST, "test-payload-1");
            // assert
            assertThat(entityFromResponse(s.receive(), true), is("21\nIt works! Payload: test-payload-1\n0\n\n"));
            // get
            s.request(Http.Method.GET);
            // assert
            assertThat(entityFromResponse(s.receive(), true), is("9\nIt works!\n0\n\n"));
            // post
            s.request(Http.Method.POST, "test-payload-2");
            // assert
            assertThat(entityFromResponse(s.receive(), true), is("21\nIt works! Payload: test-payload-2\n0\n\n"));
            // get
            s.request(Http.Method.GET);
            // assert
            assertThat(entityFromResponse(s.receive(), true), is("9\nIt works!\n0\n\n"));
        }
    }

    @Test
    public void getWithLargePayloadDoesNotCauseConnectionClose() throws Exception {
        // open
        try (SocketHttpClient s = new SocketHttpClient(webServer)) {
            // get
            s.request(Http.Method.GET, SocketHttpClient.longData(100_000).toString());

            // assert
            assertThat(entityFromResponse(s.receive(), true), is("9\nIt works!\n0\n\n"));
            SocketHttpClient.assertConnectionIsOpen(s);
        }
    }

    @Test
    public void traceWithAnyPayloadCausesConnectionCloseButDoesNotFail() throws Exception {
        // open
        try (SocketHttpClient s = new SocketHttpClient(webServer)) {
            // get
            s.request(Http.Method.TRACE, "/trace", "small");

            // assert
            assertThat(entityFromResponse(s.receive(), true), is("9\nIn trace!\n0\n\n"));
            SocketHttpClient.assertConnectionIsClosed(s);
        }
    }

    @Test
    public void traceWithAnyPayloadCausesConnectionCloseAndBadRequestWhenHandled() throws Exception {
        // open
        try (SocketHttpClient s = new SocketHttpClient(webServer)) {
            // get
            s.request(Http.Method.TRACE, "small");

            // assert that the Handler.of ContentReader transforms the exception to 400 error
            assertThat(s.receive(), startsWith("HTTP/1.1 400 Bad Request\n"));
            SocketHttpClient.assertConnectionIsClosed(s);
        }
    }

    @Test
    public void deferredGetWithLargePayloadDoesNotCauseConnectionClose() throws Exception {
        // open
        try (SocketHttpClient s = new SocketHttpClient(webServer)) {
            // get
            s.request(Http.Method.GET, "/deferred", SocketHttpClient.longData(100_000).toString());
            // assert
            assertThat(entityFromResponse(s.receive(), true), is("d\nI'm deferred!\n0\n\n"));
            SocketHttpClient.assertConnectionIsOpen(s);
        }
    }

    @Test
    public void getWithIllegalSmallEnoughPayloadDoesntCauseConnectionClose() throws Exception {
        // open
        try (SocketHttpClient s = new SocketHttpClient(webServer)) {
            // get
            s.request(Http.Method.GET, "illegal-but-small-enough-payload");

            // assert
            assertThat(entityFromResponse(s.receive(), true), is("9\nIt works!\n0\n\n"));
            SocketHttpClient.assertConnectionIsOpen(s);
        }
    }

    @Test
    public void unconsumedSmallPostDataDoesNotCauseConnectionClose() throws Exception {
        // open
        try (SocketHttpClient s = new SocketHttpClient(webServer)) {
            // get
            s.request(Http.Method.POST, "/unconsumed", "not-consumed-payload");
            String received = s.receive();
            System.out.println(received);
            // assert
            assertThat(entityFromResponse(received, true), is("15\nPayload not consumed!\n0\n\n"));
            SocketHttpClient.assertConnectionIsOpen(s);
        }
    }

    @Test
    public void unconsumedLargePostDataDoesNotCauseConnectionClose() throws Exception {
        // open
        try (SocketHttpClient s = new SocketHttpClient(webServer)) {
            // get
            s.request(Http.Method.POST, "/unconsumed", SocketHttpClient.longData(100_000).toString());

            // assert
            assertThat(entityFromResponse(s.receive(), true), is("15\nPayload not consumed!\n0\n\n"));
            SocketHttpClient.assertConnectionIsOpen(s);
        }
    }

    @Test
    public void unconsumedDeferredLargePostDataDoesNotCauseConnectionClose() throws Exception {
        // open
        try (SocketHttpClient s = new SocketHttpClient(webServer)) {
            // get
            s.request(Http.Method.POST, "/deferred", SocketHttpClient.longData(100_000).toString());

            // assert
            assertThat(entityFromResponse(s.receive(), true), is("d\nI'm deferred!\n0\n\n"));
            SocketHttpClient.assertConnectionIsOpen(s);
        }
    }

    @Test
    public void errorHandlerWithGetPayloadDoesNotCauseConnectionClose() throws Exception {
        // open
        try (SocketHttpClient s = new SocketHttpClient(webServer)) {
            // get
            s.request(Http.Method.GET, "/exception", "not-consumed-payload");

            // assert
            assertThat(s.receive(), startsWith("HTTP/1.1 500 Internal Server Error\n"));
            SocketHttpClient.assertConnectionIsOpen(s);
        }
    }

    @Test
    public void errorHandlerWithPostDataDoesNotCauseConnectionClose() throws Exception {
        // open
        try (SocketHttpClient s = new SocketHttpClient(webServer)) {
            // get
            s.request(Http.Method.POST, "/exception", "not-consumed-payload");

            // assert
            assertThat(s.receive(), startsWith("HTTP/1.1 500 Internal Server Error\n"));
            SocketHttpClient.assertConnectionIsOpen(s);
        }
    }

    @Test
    public void testConnectionCloseWhenKeepAliveOff() throws Exception {
        try (SocketHttpClient s = new SocketHttpClient(webServer)) {
            // get
            s.request(Http.Method.GET, "/", null, List.of("Connection: close"));

            // assert
            assertThat(entityFromResponse(s.receive(), true), is("9\nIt works!\n0\n\n"));
            SocketHttpClient.assertConnectionIsClosed(s);
        }
    }

    @Test
    public void testForcedChunkedWithConnectionCloseHeader() throws Exception {
        String s = SocketHttpClient.sendAndReceive("/force-chunked",
                                                   Http.Method.GET,
                                                   null,
                                                   List.of("Connection: close"),
                                                   webServer);
        Map<String, String> headers = headersFromResponse(s);
        assertThat(headers, not(hasKey(equalToIgnoringCase("connection"))));
        assertThat(headers, hasEntry(equalToIgnoringCase(Http.Header.TRANSFER_ENCODING), is("chunked")));

        assertThat(entityFromResponse(s, false), is("4\nabcd\n0\n\n"));
    }

    @Test
    public void testConnectionCloseHeader() throws Exception {
        String s = SocketHttpClient.sendAndReceive("/",
                                                   Http.Method.GET,
                                                   null,
                                                   List.of("Connection: close"),
                                                   webServer);
        Map<String, String> headers = headersFromResponse(s);
        assertThat(headers, not(hasKey(equalToIgnoringCase("connection"))));
        assertThat(entityFromResponse(s, false), is("9\nIt works!\n0\n\n"));
    }

    @Test
    public void testBadURL() throws Exception {
        String s = SocketHttpClient.sendAndReceive("/?p=|",
                                                   Http.Method.GET,
                                                   null,
                                                   List.of("Connection: close"),
                                                   webServer);
        assertThat(s, containsString("400 Bad Request"));
        Map<String, String> headers = headersFromResponse(s);
        assertThat(headers, hasKey(equalToIgnoringCase("content-type")));
        assertThat(headers, hasKey(equalToIgnoringCase("content-length")));
    }

    @Test
    public void testBadContentType() throws Exception {
        String s = SocketHttpClient.sendAndReceive("/",
                                                   Http.Method.GET,
                                                   null,
                                                   List.of("Content-Type: %", "Connection: close"),
                                                   webServer);
        assertThat(s, containsString("400 Bad Request"));
        Map<String, String> headers = headersFromResponse(s);
        assertThat(headers, hasKey(equalToIgnoringCase("content-type")));
        assertThat(headers, hasKey(equalToIgnoringCase("content-length")));
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
    public void testMulti() throws Exception {
        String s = SocketHttpClient.sendAndReceive("/multi",
                                                   Http.Method.GET,
                                                   null, webServer);
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
    @ParameterizedTest
    @ValueSource(strings = {"", "Chunked"})
    public void testMultiFirstError(String pathSuffix) throws Exception {
        String s = SocketHttpClient.sendAndReceive("/multiFirstError" + pathSuffix,
                                                   Http.Method.GET,
                                                   null, webServer);

        // Response status has not been sent yet before the exception took place, so server has still a chance to set it to 500.
        assertThat(s, startsWith("HTTP/1.1 500 Internal Server Error\n"));
        Map<String, String> headers = headersFromResponse(s);
        assertThat(headers, hasEntry(equalToIgnoringCase(Http.Header.TRANSFER_ENCODING), is("chunked")));

        assertThat(headersFromResponse(s), hasKey(equalToIgnoringCase(Http.Header.TRAILER)));
        Map<String, String> trailerHeaders = cutTrailerHeaders(s);
        assertThat(trailerHeaders, hasEntry(equalToIgnoringCase("stream-status"), is("500")));
        assertThat(trailerHeaders, hasEntry(equalToIgnoringCase("stream-result"), is(TEST_EXCEPTION.toString())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Chunked"})
    public void testMultiSecondError(String pathSuffix) throws Exception {
        String s = SocketHttpClient.sendAndReceive("/multiSecondError" + pathSuffix,
                                                   Http.Method.GET,
                                                   null, webServer);
        // When response is not chunked and no content-length is set, and the error takes place after the 1st chunk, the
        // status has not been sent yet, so it still has a chance to set it to 500. On the other hand, if response is set to
        // chunked, the status has already been sent before the error took place and hence will get the actual status code.
        String expectedStatus = pathSuffix.isEmpty() ? "500 Internal Server Error" : "200 OK";
        assertThat(s, startsWith("HTTP/1.1 " + expectedStatus + "\n"));
        Map<String, String> headers = headersFromResponse(s);
        assertThat(headers, hasEntry(equalToIgnoringCase(Http.Header.TRANSFER_ENCODING), is("chunked")));
        Map<String, String> trailerHeaders = cutTrailerHeaders(s);
        assertThat(trailerHeaders, hasEntry("stream-status", "500"));
        assertThat(trailerHeaders, hasEntry("stream-result", TEST_EXCEPTION.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Chunked"})
    public void testMultiThirdError(String pathSuffix) throws Exception {
        String s = SocketHttpClient.sendAndReceive("/multiThirdError" + pathSuffix,
                                                   Http.Method.GET,
                                                   null, webServer);
        // Response status has already been sent before the exception took place, hence getting the 200 instead of 500.
        assertThat(s, startsWith("HTTP/1.1 200 OK\n"));
        Map<String, String> headers = headersFromResponse(s);
        assertThat(headers, hasEntry(equalToIgnoringCase(Http.Header.TRANSFER_ENCODING), is("chunked")));
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
    public void testMultiDelayedThirdError() throws Exception {
        String s = SocketHttpClient.sendAndReceive("/multiDelayedThirdError",
                                                   Http.Method.GET,
                                                   null, webServer);
        assertThat(s, startsWith("HTTP/1.1 200 OK\n"));
        Map<String, String> headers = cutTrailerHeaders(s);
        assertThat(headers, hasEntry("stream-status", "500"));
        assertThat(headers, hasEntry("stream-result", TEST_EXCEPTION.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"301", "200"})
    void testEmptyResponse(String responseStatus) throws Exception {
        // ex. path = "/emptyResponse200" or "/emptyResponse301"
        String s = SocketHttpClient.sendAndReceive("/emptyResponse" + responseStatus,
                                                   Http.Method.GET,
                                                   null, webServer);
        assertThat(s, startsWith("HTTP/1.1 " + responseStatus));
        Map<String, String> headers = headersFromResponse(s);
        assertThat(headers, not(hasKey(Http.Header.TRANSFER_ENCODING)));
        assertThat(headers, hasEntry(equalToIgnoringCase(Http.Header.CONTENT_LENGTH), is("0")));
        // Verify that there is no entity
        assertThat(entityFromResponse(s, false), is(""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"301", "200"})
    void testForcedChunkedEmptyResponse(String responseStatus) throws Exception {
        // ex. path = "/force-chunked-emptyResponse200" or "/force-chunked-emptyResponse301"
        String s = SocketHttpClient.sendAndReceive("/force-chunked-emptyResponse" + responseStatus,
                                                   Http.Method.GET,
                                                   null, webServer);
        assertThat(s, startsWith("HTTP/1.1 " + responseStatus));
        Map<String, String> headers = headersFromResponse(s);
        assertThat(headers, hasEntry(equalToIgnoringCase(Http.Header.TRANSFER_ENCODING), is("chunked")));
        assertThat(headers, not(hasKey(Http.Header.CONTENT_LENGTH)));
        // Verify that there is no entity
        assertThat(entityFromResponse(s, false), is("0\n\n"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"301", "200"})
    void testSingleEntityResponse(String responseStatus) throws Exception {
        // ex. path = "/singleResponse200" or "/singleResponse301"
        String s = SocketHttpClient.sendAndReceive("/singleEntityResponse" + responseStatus,
                                                   Http.Method.GET,
                                                   null, webServer);
        assertThat(s, startsWith("HTTP/1.1 " + responseStatus));
        Map<String, String> headers = headersFromResponse(s);
        assertThat(headers, not(hasKey(Http.Header.TRANSFER_ENCODING)));
        assertThat(headers,
                   hasEntry(equalToIgnoringCase(Http.Header.CONTENT_LENGTH), is(String.valueOf(singleEntityResponse.length()))));
        // Verify that entity received is correct
        assertThat(entityFromResponse(s, false), is(singleEntityResponse));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 5})
    void testContentLength(int entityCount) throws Exception {
        // ex. path = "/contentLength0" or "/contentLength1" or "/contentLength5"
        String s = SocketHttpClient.sendAndReceive("/contentLength" + entityCount,
                                                   Http.Method.GET,
                                                   null, webServer);
        assertThat(s, startsWith("HTTP/1.1 200 OK\n"));
        Map<String, String> headers = headersFromResponse(s);
        String contentLength = String.valueOf((contentLengthDataPrefix.length() + 1) * entityCount);
        assertThat(headers, hasEntry(equalToIgnoringCase(Http.Header.CONTENT_LENGTH), is(contentLength)));
        String expectedResponse = "";
        for (int i = 0; i < entityCount; i++) {
            expectedResponse += contentLengthDataPrefix + i;
        }
        assertThat(entityFromResponse(s, false), is(expectedResponse));
    }

    @Test
    void testSSEShouldBeChunked() throws Exception {
        String s = SocketHttpClient.sendAndReceive("/SSE",
                                                   Http.Method.GET,
                                                   null, webServer);
        assertThat(s, startsWith("HTTP/1.1 200 OK"));
        Map<String, String> headers = headersFromResponse(s);
        assertThat(headers, hasEntry(equalToIgnoringCase(Http.Header.TRANSFER_ENCODING), is("chunked")));
        assertThat(headers, hasEntry(equalToIgnoringCase(Http.Header.CONNECTION), is("keep-alive")));
        assertThat(headers, not(hasKey(Http.Header.CONTENT_LENGTH)));
        // Verify that entity received is correct
        assertThat(entityFromResponse(s, false),
                   is(Integer.toHexString(sSEEntityResponse.length()) + "\n" + sSEEntityResponse + "\n0\n\n"));
    }

    // Extract last 3 string from path and use as response status code
    private static void setResponseStatusCodeFromPath(ServerRequest req, ServerResponse res) {
        String path = req.path().toString();
        int responseStatus = Integer.valueOf(path.substring(path.length() - 3));
        res.status(responseStatus);
    }

    private static void setResponseToChunkFromPath(ServerRequest req, ServerResponse res) {
        if (req.path().toString().endsWith("Chunked")) {
            res.headers().add(Http.Header.TRANSFER_ENCODING, "chunked");
        }
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
