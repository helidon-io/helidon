/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

    private static final Logger LOGGER = Logger.getLogger(PlainTest.class.getName());
    private static final RuntimeException TEST_EXCEPTION = new RuntimeException("BOOM!");
    private static WebServer webServer;

    /**
     * Start the Web Server
     */
    @BeforeAll
    static void startServer() {
        webServer = WebServer.builder()
                .routing(Routing.builder().any((req, res) -> {
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
                                 .get("/force-chunked", (req, res) -> {
                                     res.headers().put(Http.Header.TRANSFER_ENCODING, "chunked");
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
                                 }))
                                 .build())
                .build()
                .start()
                .await(10, TimeUnit.SECONDS);

        LOGGER.info("Started server at: https://localhost:" + webServer.port());
    }

    @AfterAll
    public static void close() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
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
    public void getWithLargePayloadCausesConnectionClose() throws Exception {
        // open
        try (SocketHttpClient s = new SocketHttpClient(webServer)) {
            // get
            s.request(Http.Method.GET, SocketHttpClient.longData(100_000).toString());

            // assert
            assertThat(entityFromResponse(s.receive(), true), is("9\nIt works!\n0\n\n"));
            SocketHttpClient.assertConnectionIsClosed(s);
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
    public void deferredGetWithLargePayloadCausesConnectionClose() throws Exception {
        // open
        try (SocketHttpClient s = new SocketHttpClient(webServer)) {
            // get
            s.request(Http.Method.GET, "/deferred", SocketHttpClient.longData(100_000).toString());
            // assert
            assertThat(entityFromResponse(s.receive(), true), is("d\nI'm deferred!\n0\n\n"));
            SocketHttpClient.assertConnectionIsClosed(s);
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
    public void unconsumedLargePostDataCausesConnectionClose() throws Exception {
        // open
        try (SocketHttpClient s = new SocketHttpClient(webServer)) {
            // get
            s.request(Http.Method.POST, "/unconsumed", SocketHttpClient.longData(100_000).toString());

            // assert
            assertThat(entityFromResponse(s.receive(), true), is("15\nPayload not consumed!\n0\n\n"));
            SocketHttpClient.assertConnectionIsClosed(s);
        }
    }

    @Test
    public void unconsumedDeferredLargePostDataCausesConnectionClose() throws Exception {
        // open
        try (SocketHttpClient s = new SocketHttpClient(webServer)) {
            // get
            s.request(Http.Method.POST, "/deferred", SocketHttpClient.longData(100_000).toString());

            // assert
            assertThat(entityFromResponse(s.receive(), true), is("d\nI'm deferred!\n0\n\n"));
            SocketHttpClient.assertConnectionIsClosed(s);
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
    @Test
    public void testMultiFirstError() throws Exception {
        String s = SocketHttpClient.sendAndReceive("/multiFirstError",
                                                   Http.Method.GET,
                                                   null, webServer);
        System.out.println(s);

        assertThat(s, startsWith("HTTP/1.1 500 Internal Server Error\n"));
        assertThat(headersFromResponse(s), hasKey(equalToIgnoringCase(Http.Header.TRAILER)));
        Map<String, String> trailerHeaders = cutTrailerHeaders(s);
        assertThat(trailerHeaders, hasEntry(equalToIgnoringCase("stream-status"), is("500")));
        assertThat(trailerHeaders, hasEntry(equalToIgnoringCase("stream-result"), is(TEST_EXCEPTION.toString())));
    }

    @Test
    public void testMultiSecondError() throws Exception {
        String s = SocketHttpClient.sendAndReceive("/multiSecondError",
                                                   Http.Method.GET,
                                                   null, webServer);
        assertThat(s, startsWith("HTTP/1.1 200 OK\n"));
        Map<String, String> trailerHeaders = cutTrailerHeaders(s);
        assertThat(trailerHeaders, hasEntry("stream-status", "500"));
        assertThat(trailerHeaders, hasEntry("stream-result", TEST_EXCEPTION.toString()));
    }

    @Test
    public void testMultiThirdError() throws Exception {
        String s = SocketHttpClient.sendAndReceive("/multiThirdError",
                                                   Http.Method.GET,
                                                   null, webServer);
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
    public void testMultiDelayedThirdError() throws Exception {
        String s = SocketHttpClient.sendAndReceive("/multiDelayedThirdError",
                                                   Http.Method.GET,
                                                   null, webServer);
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
