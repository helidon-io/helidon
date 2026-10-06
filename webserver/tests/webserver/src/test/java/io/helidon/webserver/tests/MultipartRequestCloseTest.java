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
import java.time.Duration;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import io.helidon.config.Config;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.encoding.deflate.DeflateEncoding;
import io.helidon.http.encoding.gzip.GzipEncoding;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.multipart.MultiPart;
import io.helidon.http.media.multipart.MultiPartSupport;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class MultipartRequestCloseTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String BOUNDARY = "request-close-boundary";
    private static final String CONTENT_TYPE = "multipart/form-data; boundary=" + BOUNDARY;
    private static final byte[] ENTITY = ("--" + BOUNDARY + "\r\n"
            + "Content-Disposition: form-data; name=value\r\n"
            + "Content-Type: text/plain\r\n\r\nvalue\r\n"
            + "--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.US_ASCII);

    private final int port;

    MultipartRequestCloseTest(WebServer server) {
        this.port = server.port();
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        server.mediaContext(MediaContext.builder()
                        .mediaSupportsDiscoverServices(false)
                        .addMediaSupport(MultiPartSupport.create(Config.empty()))
                        .build())
                .contentEncoding(ContentEncodingContext.builder()
                        .addContentEncoding(GzipEncoding.create())
                        .addContentEncoding(DeflateEncoding.create())
                        .build());
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.post("/iterate", (req, res) -> {
                    int count = 0;
                    try (MultiPart parts = req.content().as(MultiPart.class)) {
                        while (parts.hasNext()) {
                            count += parts.next().as(String.class).length();
                        }
                    }
                    res.send(Integer.toString(count));
                })
                .post("/close", (req, res) -> {
                    int first;
                    try (MultiPart parts = req.content().as(MultiPart.class)) {
                        first = parts.next().inputStream().read();
                    }
                    res.send(Integer.toString(first));
                });
    }

    @ParameterizedTest
    @CsvSource({
            "iterate, identity",
            "iterate, gzip",
            "iterate, deflate",
            "close, identity",
            "close, gzip",
            "close, deflate"
    })
    void multipartCleanupDoesNotWaitForRemainingHttpBody(String route, String encoding) throws IOException {
        byte[] payload = encodedEntity(encoding);
        String expected = route.equals("iterate") ? "5" : Integer.toString('v');
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), (int) TIMEOUT.toMillis());
            socket.setSoTimeout((int) TIMEOUT.toMillis());
            OutputStream output = socket.getOutputStream();
            String headers = "POST /" + route + " HTTP/1.1\r\n"
                    + "Host: localhost:" + port + "\r\n"
                    + "Content-Type: " + CONTENT_TYPE + "\r\n"
                    + "Content-Encoding: " + encoding + "\r\n"
                    + "Content-Length: " + (payload.length + 1) + "\r\n"
                    + "Connection: close\r\n\r\n";
            output.write(headers.getBytes(StandardCharsets.US_ASCII));
            output.write(payload);
            output.flush();

            // Keep the socket open and withhold the last declared HTTP body byte.
            var response = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            assertThat(route + "/" + encoding + " completed before the HTTP body",
                       response.readLine(), startsWith("HTTP/1.1 200"));
            String header = response.readLine();
            assertThat(header, notNullValue());
            while (!header.isEmpty()) {
                header = response.readLine();
                assertThat(header, notNullValue());
            }
            char[] body = new char[expected.length()];
            for (int i = 0; i < body.length; i++) {
                body[i] = (char) response.read();
            }
            assertThat(new String(body), is(expected));
        } catch (SocketTimeoutException e) {
            throw new AssertionError(route + "/" + encoding
                                             + " multipart cleanup waited for withheld HTTP body bytes", e);
        }
    }

    private static byte[] encodedEntity(String encoding) throws IOException {
        if (encoding.equals("identity")) {
            return ENTITY;
        }
        var bytes = new ByteArrayOutputStream();
        try (OutputStream encoder = switch (encoding) {
            case "gzip" -> new GZIPOutputStream(bytes);
            case "deflate" -> new DeflaterOutputStream(bytes);
            default -> throw new IllegalArgumentException("Unexpected encoding: " + encoding);
        }) {
            encoder.write(ENTITY);
        }
        return bytes.toByteArray();
    }
}
