/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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

import java.util.Map;

import io.helidon.common.http.Http;
import io.helidon.common.http.Http.Header;
import io.helidon.common.http.Http.HeaderValues;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.testing.junit5.webserver.SocketHttpClient;
import io.helidon.nima.webserver.http.HttpRules;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsMapContaining.hasEntry;

/**
 * Tests transfer encoding.
 */
@ServerTest
class TransferEncodingTest {
    private final SocketHttpClient socketHttpClient;

    TransferEncodingTest(SocketHttpClient socketHttpClient) {
        this.socketHttpClient = socketHttpClient;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/length", (req, res) -> {
                    String payload = "It works!";
                    res.headers().add(Header.CONTENT_LENGTH.withValue(payload.length()));
                    res.send(payload);
                })
                .get("/chunked", (req, res) -> {
                    String payload = "It works!";
                    res.headers().add(HeaderValues.TRANSFER_ENCODING_CHUNKED);
                    res.send(payload);
                })
                .get("/optimized", (req, res) -> {
                    String payload = "It works!";
                    res.send(payload);
                })
                .get("/empty", (req, res) -> {
                    res.send();
                })
                .get("/emptychunked", (req, res) -> {
                    res.headers().add(HeaderValues.TRANSFER_ENCODING_CHUNKED);
                    res.send();
                });
    }

    /**
     * Test content length when no payload in response.
     */
    @Test
    void testEmptyContentLength() {
        String s = socketHttpClient.sendAndReceive("/empty", Http.Method.GET, null);

        Map<String, String> headers = SocketHttpClient.headersFromResponse(s);
        assertThat(headers, hasEntry("Content-Length", "0"));
    }

    /**
     * Test when no payload in response but response was forced to chunked.
     */
    @Test
    void testEmptyChunked() {
        String s = socketHttpClient.sendAndReceive("/emptychunked", Http.Method.GET, null);
        Map<String, String> headers = SocketHttpClient.headersFromResponse(s);
        assertThat(headers, hasEntry("Transfer-Encoding", "chunked"));
    }

    /**
     * Test content length
     */
    @Test
    void testContentLength() {
        String s = socketHttpClient.sendAndReceive("/length", Http.Method.GET, null);
        assertThat(cutPayloadAndCheckHeadersFormat(s), is("It works!"));
        Map<String, String> headers = SocketHttpClient.headersFromResponse(s);
        assertThat(headers, hasEntry("Content-Length", "9"));
    }

    /**
     * Test chunked encoding.
     */
    @Test
    void testChunkedEncoding() {
        String s = socketHttpClient.sendAndReceive("/chunked", Http.Method.GET, null);
        assertThat(cutPayloadAndCheckHeadersFormat(s), is("9\nIt works!\n0\n\n"));
        Map<String, String> headers = SocketHttpClient.headersFromResponse(s);
        assertThat(headers, hasEntry("Transfer-Encoding", "chunked"));
    }

    /**
     * Test optimized or content length in this case.
     */
    @Test
    void testOptimized() {
        String s = socketHttpClient.sendAndReceive("/optimized", Http.Method.GET, null);
        assertThat(cutPayloadAndCheckHeadersFormat(s), is("It works!"));
        Map<String, String> headers = SocketHttpClient.headersFromResponse(s);
        assertThat(headers, hasEntry("Content-Length", "9"));
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
}
