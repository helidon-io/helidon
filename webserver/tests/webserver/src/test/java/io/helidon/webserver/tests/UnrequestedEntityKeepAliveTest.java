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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import io.helidon.http.Status;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.encoding.deflate.DeflateEncoding;
import io.helidon.http.encoding.gzip.GzipEncoding;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

@ServerTest
class UnrequestedEntityKeepAliveTest {
    private final int port;

    UnrequestedEntityKeepAliveTest(WebServer server) {
        port = server.port();
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        server.contentEncoding(ContentEncodingContext.builder()
                                       .addContentEncoding(GzipEncoding.create())
                                       .addContentEncoding(DeflateEncoding.create())
                                       .build());
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.post("/ignore", (_, res) -> res.status(Status.NO_CONTENT_204).send())
                .get("/next", (_, res) -> res.status(Status.NO_CONTENT_204).send());
    }

    @ParameterizedTest
    @CsvSource({"identity, length", "identity, chunked", "gzip, length", "gzip, chunked",
                "deflate, length", "deflate, chunked"})
    void subsequentRequestCompletesAfterUntouchedBody(String encoding, String framing) throws IOException {
        byte[] body = "untouched request".getBytes(StandardCharsets.US_ASCII);
        byte[] wire = body;
        if (!encoding.equals("identity")) {
            var compressed = new ByteArrayOutputStream();
            try (OutputStream encoder = encoding.equals("gzip")
                    ? new GZIPOutputStream(compressed)
                    : new DeflaterOutputStream(compressed)) {
                encoder.write(body);
            }
            wire = compressed.toByteArray();
        }
        String variant = encoding + "/" + framing;
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), 10000);
            socket.setSoTimeout(10000);
            var output = socket.getOutputStream();
            String framingHeader = framing.equals("length")
                    ? "Content-Length: " + wire.length + "\r\n"
                    : "Transfer-Encoding: chunked\r\n";
            String headers = "POST /ignore HTTP/1.1\r\nHost: localhost\r\n" + framingHeader
                    + "Content-Encoding: " + encoding + "\r\n\r\n";
            output.write(headers.getBytes(StandardCharsets.US_ASCII));
            if (framing.equals("chunked")) {
                output.write((Integer.toHexString(wire.length) + "\r\n").getBytes(StandardCharsets.US_ASCII));
            }
            output.write(wire);
            if (framing.equals("chunked")) {
                output.write("\r\n0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            }
            output.flush();
            var input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            for (int request = 1; request <= 2; request++) {
                String status;
                try {
                    status = input.readLine();
                } catch (SocketTimeoutException e) {
                    throw new AssertionError("No response for request " + request + ", " + variant, e);
                }
                String diagnostic = variant + ", response " + request;
                assertThat(diagnostic + " status", status, notNullValue());
                assertThat(diagnostic + " status", status, startsWith("HTTP/1.1 204"));
                String header;
                do {
                    header = input.readLine();
                    assertThat(diagnostic + " header terminator", header, notNullValue());
                } while (!header.isEmpty());
                if (request == 1) {
                    output.write("GET /next HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"
                                         .getBytes(StandardCharsets.US_ASCII));
                    output.flush();
                }
            }
        }
    }
}
