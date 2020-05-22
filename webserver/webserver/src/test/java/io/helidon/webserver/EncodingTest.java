/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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
 * The PlainTest.
 */
public class EncodingTest {

    private static final Logger LOGGER = Logger.getLogger(EncodingTest.class.getName());

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
                                 .get("/foo", (req, res) -> res.send("It works!"))
                                 .get("/foo/{bar}", (req, res) -> res.send(req.path().param("bar")))
                                 .any(Handler.create(String.class, (req, res, entity) -> res.send("Oops " + entity)))
                                 .build())
                .build()
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        LOGGER.info("Started server at: https://localhost:" + webServer.port());
    }

    /**
     * Test path decoding and matching.
     *
     * @throws Exception If an error occurs.
     */
    @Test
    public void testEncodedUrl() throws Exception {
        String s = SocketHttpClient.sendAndReceive("/f%6F%6F", Http.Method.GET, null, webServer);
        assertThat(cutPayloadAndCheckHeadersFormat(s), is("It works!"));
        Map<String, String> headers = cutHeaders(s);
        assertThat(headers, hasEntry("connection", "keep-alive"));
    }

    /**
     * Test path decoding with params and matching.
     *
     * @throws Exception If an error occurs.
     */
    @Test
    public void testEncodedUrlParams() throws Exception {
        String s = SocketHttpClient.sendAndReceive("/f%6F%6F/b%61%72", Http.Method.GET, null, webServer);
        assertThat(cutPayloadAndCheckHeadersFormat(s), is("bar"));
        Map<String, String> headers = cutHeaders(s);
        assertThat(headers, hasEntry("connection", "keep-alive"));
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
}
