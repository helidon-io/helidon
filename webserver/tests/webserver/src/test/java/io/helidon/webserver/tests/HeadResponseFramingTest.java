/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import io.helidon.http.HeaderNames;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class HeadResponseFramingTest {
    private static final byte[] HEADER_END = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

    private final URI uri;

    HeadResponseFramingTest(URI uri) {
        this.uri = uri;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.head("/head/{type}", (req, res) -> {
                    res.header(HeaderNames.TRANSFER_ENCODING, "chunked");
                    if (req.path().path().endsWith("/trailers")) {
                        res.header(HeaderNames.TRAILER, "x-trailer");
                        res.beforeTrailers(trailers -> trailers.set(HeaderNames.create("x-trailer"), "value"));
                    }
                    res.outputStream().close();
                })
                .get("/head/{type}", (req, res) -> {
                    res.header(HeaderNames.TRANSFER_ENCODING, "chunked");
                    res.send("representation");
                })
                .get("/next", (req, res) -> res.send("next"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"plain", "trailers"})
    void headThenGetPreservesResponseBoundary(String type) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(uri.getHost(), uri.getPort()), 5000);
            socket.setSoTimeout(5000);
            var output = socket.getOutputStream();
            InputStream input = socket.getInputStream();
            output.write(("HEAD /head/" + type + " HTTP/1.1\r\nHost: localhost\r\n\r\n")
                                 .getBytes(StandardCharsets.US_ASCII));
            output.flush();

            String headHeaders = readHeaders(input);
            assertThat(headHeaders, startsWith("HTTP/1.1 200 OK\r\n"));
            assertThat(headHeaders, containsString("Transfer-Encoding: chunked\r\n"));
            assertThat(headHeaders, not(containsString("Content-Length:")));

            output.write("GET /next HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"
                                 .getBytes(StandardCharsets.US_ASCII));
            output.flush();

            assertThat("Response after HEAD", readHeaders(input), startsWith("HTTP/1.1 200 OK\r\n"));
            assertThat(new String(input.readAllBytes(), StandardCharsets.US_ASCII), is("next"));
        }
    }

    private static String readHeaders(InputStream input) throws IOException {
        var headers = new ByteArrayOutputStream();
        int matched = 0;
        while (headers.size() < 16384) {
            int value = input.read();
            if (value == -1) {
                throw new EOFException("Connection closed before response headers were complete");
            }
            headers.write(value);
            if (value == HEADER_END[matched]) {
                matched++;
            } else {
                matched = value == '\r' ? 1 : 0;
            }
            if (matched == HEADER_END.length) {
                return headers.toString(StandardCharsets.US_ASCII);
            }
        }
        throw new IOException("Response headers exceeded 16384 bytes");
    }
}
