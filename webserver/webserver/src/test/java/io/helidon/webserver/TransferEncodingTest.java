/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import io.helidon.common.http.Http;
import io.helidon.webserver.utils.SocketHttpClient;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsMapContaining.hasEntry;

/**
 * Tests transfer encoding and optimizations.
 */
public class TransferEncodingTest {

    private static final Logger LOGGER = Logger.getLogger(TransferEncodingTest.class.getName());

    private static WebServer webServer;

    /**
     * Start the Web Server
     *
     * @param port the port on which to start the server; if less than 1,
     * the port is dynamically selected
     * @throws Exception in case of an error
     */
    private static void startServer(int port) throws Exception {
        webServer = WebServer.builder()
                .port(port)
                .routing(Routing.builder()
                        .get("/length", (req, res) -> {
                            String payload = "It works!";
                            res.headers().add("content-length", String.valueOf(payload.length()));
                            res.send(payload);
                        })
                        .get("/chunked", (req, res) -> {
                            String payload = "It works!";
                            res.headers().add("transfer-encoding", "chunked");
                            res.send(payload);
                        })
                        .get("/optimized", (req, res) -> {
                            String payload = "It works!";
                            res.send(payload);
                        })
                        .build())
                .build()
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        LOGGER.info("Started server at: https://localhost:" + webServer.port());
    }

    /**
     * Test content length.
     *
     * @throws Exception If an error occurs.
     */
    @Test
    public void testContentLength() throws Exception {
        String s = SocketHttpClient.sendAndReceive("/length", Http.Method.GET, null, webServer);
        assertThat(cutPayloadAndCheckHeadersFormat(s), is("It works!"));
        Map<String, String> headers = cutHeaders(s);
        assertThat(headers, hasEntry("content-length", "9"));
    }

    /**
     * Test chunked encoding.
     *
     * @throws Exception If an error occurs.
     */
    @Test
    public void testChunkedEncoding() throws Exception {
        String s = SocketHttpClient.sendAndReceive("/chunked", Http.Method.GET, null, webServer);
        assertThat(cutPayloadAndCheckHeadersFormat(s), is("9\nIt works!\n0\n\n"));
        Map<String, String> headers = cutHeaders(s);
        assertThat(headers, hasEntry("transfer-encoding", "chunked"));
    }

    /**
     * Test optimized or content length in this case.
     *
     * @throws Exception If an error occurs.
     */
    @Test
    public void testOptimized() throws Exception {
        String s = SocketHttpClient.sendAndReceive("/optimized", Http.Method.GET, null, webServer);
        assertThat(cutPayloadAndCheckHeadersFormat(s), is("It works!"));
        Map<String, String> headers = cutHeaders(s);
        assertThat(headers, hasEntry("content-length", "9"));
    }

    static Map<String, String> cutHeaders(String response) {
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
}
