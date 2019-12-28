/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import io.helidon.common.http.Http;
import io.helidon.webserver.utils.SocketHttpClient;

import org.hamcrest.collection.IsMapContaining;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsMapContaining.hasEntry;

/**
 * The PlainTest.
 */
public class PlainTest {

    private static final Logger LOGGER = Logger.getLogger(PlainTest.class.getName());

    private static WebServer webServer;

    /**
     * Start the Web Server
     *
     * @param port the port on which to start the server; if less than 1,
     *             the port is dynamically selected
     * @throws Exception in case of an error
     */
    private static void startServer(int port) throws Exception {
        webServer = WebServer.create(
                ServerConfiguration.builder().port(port).build(),
                Routing.builder().any((req, res) -> {
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
                       .trace("/trace", (req, res) -> res.send("In trace!"))
                       .get("/force-chunked", (req, res) -> {
                           res.headers().put(Http.Header.TRANSFER_ENCODING, "chunked");
                           res.send("abcd");
                       })
                       .any(Handler.create(String.class, (req, res, entity) -> res.send("It works! Payload: " + entity)))
                       .build())
                             .start()
                             .toCompletableFuture()
                             .get(10, TimeUnit.SECONDS);

        LOGGER.info("Started server at: https://localhost:" + webServer.port());
    }

    @Test
    public void getTest() throws Exception {
        String s = SocketHttpClient.sendAndReceive(Http.Method.GET, null, webServer);
        assertThat(cutPayloadAndCheckHeadersFormat(s), is("9\nIt works!\n0\n\n"));
        Map<String, String> headers = cutHeaders(s);
        assertThat(headers, hasEntry("connection", "keep-alive"));
    }

    @Test
    public void getDeferredTest() throws Exception {
        String s = SocketHttpClient.sendAndReceive("/deferred", Http.Method.GET, null, webServer);
        assertThat(cutPayloadAndCheckHeadersFormat(s), is("d\nI'm deferred!\n0\n\n"));
    }

    @Test
    public void getWithPayloadDeferredTest() throws Exception {
        String s = SocketHttpClient.sendAndReceive("/deferred", Http.Method.GET, "illegal-payload", webServer);
        assertThat(cutPayloadAndCheckHeadersFormat(s), is("d\nI'm deferred!\n0\n\n"));
    }

    @Test
    public void getWithLargePayloadDeferredTest() throws Exception {
        String s = SocketHttpClient.sendAndReceive("/deferred", Http.Method.GET, SocketHttpClient.longData(100_000).toString(), webServer);
        assertThat(cutPayloadAndCheckHeadersFormat(s), is("d\nI'm deferred!\n0\n\n"));
    }

    @Test
    public void getWithPayloadTest() throws Exception {
        String s = SocketHttpClient.sendAndReceive(Http.Method.GET, "test-payload", webServer);
        assertThat(cutPayloadAndCheckHeadersFormat(s), is("9\nIt works!\n0\n\n"));
    }

    @Test
    public void postNoPayloadTest() throws Exception {
        String s = SocketHttpClient.sendAndReceive(Http.Method.POST, null, webServer);
        assertThat(cutPayloadAndCheckHeadersFormat(s), is("13\nIt works! Payload: \n0\n\n"));
    }

    @Test
    public void simplePostTest() throws Exception {
        String s = SocketHttpClient.sendAndReceive(Http.Method.POST, "test-payload", webServer);
        assertThat(cutPayloadAndCheckHeadersFormat(s), is("1f\nIt works! Payload: test-payload\n0\n\n"));
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
            assertThat(cutPayloadAndCheckHeadersFormat(s.receive()), is("9\nIt works!\n0\n\n"));
            // get
            s.request(Http.Method.GET);
            // assert
            assertThat(cutPayloadAndCheckHeadersFormat(s.receive()), is("9\nIt works!\n0\n\n"));
        }
    }

    @Test
    public void testTwoPostsTheSameConnection() throws Exception {
        // open
        try (SocketHttpClient s = new SocketHttpClient(webServer)) {
            // post
            s.request(Http.Method.POST, "test-payload-1");
            // assert
            assertThat(cutPayloadAndCheckHeadersFormat(s.receive()), is("21\nIt works! Payload: test-payload-1\n0\n\n"));
            // post
            s.request(Http.Method.POST, "test-payload-2");
            // assert
            assertThat(cutPayloadAndCheckHeadersFormat(s.receive()), is("21\nIt works! Payload: test-payload-2\n0\n\n"));
        }
    }

    @Test
    public void postGetPostGetTheSameConnection() throws Exception {
        try (SocketHttpClient s = new SocketHttpClient(webServer)) {
            // post
            s.request(Http.Method.POST, "test-payload-1");
            // assert
            assertThat(cutPayloadAndCheckHeadersFormat(s.receive()), is("21\nIt works! Payload: test-payload-1\n0\n\n"));
            // get
            s.request(Http.Method.GET);
            // assert
            assertThat(cutPayloadAndCheckHeadersFormat(s.receive()), is("9\nIt works!\n0\n\n"));
            // post
            s.request(Http.Method.POST, "test-payload-2");
            // assert
            assertThat(cutPayloadAndCheckHeadersFormat(s.receive()), is("21\nIt works! Payload: test-payload-2\n0\n\n"));
            // get
            s.request(Http.Method.GET);
            // assert
            assertThat(cutPayloadAndCheckHeadersFormat(s.receive()), is("9\nIt works!\n0\n\n"));
        }
    }

    @Test
    public void getWithLargePayloadCausesConnectionClose() throws Exception {
        // open
        try (SocketHttpClient s = new SocketHttpClient(webServer)) {
            // get
            s.request(Http.Method.GET, SocketHttpClient.longData(100_000).toString());

            // assert
            assertThat(cutPayloadAndCheckHeadersFormat(s.receive()), is("9\nIt works!\n0\n\n"));
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
            assertThat(cutPayloadAndCheckHeadersFormat(s.receive()), is("9\nIn trace!\n0\n\n"));
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
            assertThat(cutPayloadAndCheckHeadersFormat(s.receive()), is("d\nI'm deferred!\n0\n\n"));
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
            assertThat(cutPayloadAndCheckHeadersFormat(s.receive()), is("9\nIt works!\n0\n\n"));
            SocketHttpClient.assertConnectionIsOpen(s);
        }
    }

    @Test
    public void unconsumedSmallPostDataDoesNotCauseConnectionClose() throws Exception {
        // open
        try (SocketHttpClient s = new SocketHttpClient(webServer)) {
            // get
            s.request(Http.Method.POST, "/unconsumed", "not-consumed-payload");

            // assert
            assertThat(cutPayloadAndCheckHeadersFormat(s.receive()), is("15\nPayload not consumed!\n0\n\n"));
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
            assertThat(cutPayloadAndCheckHeadersFormat(s.receive()), is("15\nPayload not consumed!\n0\n\n"));
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
            assertThat(cutPayloadAndCheckHeadersFormat(s.receive()), is("d\nI'm deferred!\n0\n\n"));
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
            assertThat(cutPayloadAndCheckHeadersFormat(s.receive()), is("9\nIt works!\n0\n\n"));
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
        assertThat(cutPayloadAndCheckHeadersFormat(s), is("4\nabcd\n0\n\n"));
        Map<String, String> headers = cutHeaders(s);
        assertThat(headers, not(IsMapContaining.hasKey("connection")));
        assertThat(headers, hasEntry(Http.Header.TRANSFER_ENCODING.toLowerCase(), "chunked"));
    }

    @Test
    public void testConnectionCloseHeader() throws Exception {
        String s = SocketHttpClient.sendAndReceive("/",
                                                   Http.Method.GET,
                                                   null,
                                                   List.of("Connection: close"),
                                                   webServer);
        assertThat(cutPayloadAndCheckHeadersFormat(s), is("9\nIt works!\n0\n\n"));
        Map<String, String> headers = cutHeaders(s);
        assertThat(headers, not(IsMapContaining.hasKey("connection")));
    }

    @Test
    public void testBadURL() throws Exception {
        String s = SocketHttpClient.sendAndReceive("/?p=|",
                Http.Method.GET,
                null,
                List.of("Connection: close"),
                webServer);
        assertThat(s, containsString("400 Bad Request"));
        Map<String, String> headers = cutHeaders(s);
        assertThat(headers, IsMapContaining.hasKey("content-type"));
        assertThat(headers, IsMapContaining.hasKey("content-length"));
    }

    @Test
    public void testBadContentType() throws Exception {
        String s = SocketHttpClient.sendAndReceive("/",
                Http.Method.GET,
                null,
                List.of("Content-Type: %", "Connection: close"),
                webServer);
        assertThat(s, containsString("400 Bad Request"));
        Map<String, String> headers = cutHeaders(s);
        assertThat(headers, IsMapContaining.hasKey("content-type"));
        assertThat(headers, IsMapContaining.hasKey("content-length"));
    }

    private Map<String, String> cutHeaders(String response) {
        assertThat(response, notNullValue());
        int index = response.indexOf("\n\n");
        if (index < 0) {
            throw new AssertionError("Missing end of headers in response!");
        }
        String hdrsPart = response.substring(0, index);
        String[] lines = hdrsPart.split("\\n");
        if (lines.length <= 1) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new HashMap<>(lines.length - 1);
        boolean first = true;
        for (String line : lines) {
            if (first) {
                first = false;
                continue;
            }
            int i = line.indexOf(':');
            if (i < 0) {
                throw new AssertionError("Header without semicolon - " + line);
            }
            result.put(line.substring(0, i).trim(), line.substring(i + 1).trim());
        }
        return result;
    }

    private String cutPayloadAndCheckHeadersFormat(String response) {
        assertThat(response, notNullValue());
        int index = response.indexOf("\n\n");
        if (index < 0) {
            throw new AssertionError("Missing end of headers in response!");
        }
        String headers = response.substring(0, index);
        String[] lines = headers.split("\\n");
        assertThat(lines[0], startsWith("HTTP/"));
        for (int i = 1; i < lines.length; i++) {
            assertThat(lines[i], containsString(":"));
        }
        return response.substring(index + 2);
    }

    @BeforeAll
    public static void startServer() throws Exception {
        // start the server at a free port
        startServer(0);
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
    public void name() throws Exception {
        for (byte b: "myData".getBytes()) {
            System.out.println(b);
        }
    }
}
