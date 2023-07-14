/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.integration.webserver.upgrade.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.stream.Collectors;

import io.helidon.common.http.Http;
import io.helidon.nima.webclient.WebClient;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientRequest;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests support for compression in the webserver.
 */
public class CompressionTest {
    private static final System.Logger LOGGER = System.getLogger(CompressionTest.class.getName());

    private static WebServer webServer;

    private static Http1Client webClient;

    @BeforeAll
    public static void startServer() {
        webServer = WebServer.builder()
                .host("localhost")
                .routing(r -> r.get("/compressed", (req, res) -> res.send("It works!")))
                //.enableCompression(true)        // compression
                .build()
                .start();

        webClient = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port())
                .validateHeaders(false)
                .keepAlive(true)
                .build();

        LOGGER.log(Level.INFO, "Started server at: https://localhost:" + webServer.port());
    }

    @AfterAll
    public static void close() {
        if (webServer != null) {
            webServer.stop();
        }
    }

    /**
     * Test that "content-encoding" header is "gzip". Note that we use a
     * {@code SocketHttpClient} as other clients may remove this header after
     * processing.
     */
    @Test
    public void testGzipHeader() {
        assertThat(httpGet("""
                GET /compressed HTTP/1.1
                Host: 127.0.0.1
                Accept-Encoding: gzip
                """), containsString("content-encoding: gzip"));
    }

    /**
     * Test that "content-encoding" is "deflate". Note that we use a
     * {@code SocketHttpClient} as other clients may remove this header after
     * processing.
     */
    @Test
    public void testDeflateHeader() {
        assertThat(httpGet("""
                GET /compressed HTTP/1.1
                Host: 127.0.0.1
                Accept-Encoding: deflate
                """), containsString("content-encoding: deflate"));
    }

    /**
     * Test that the entity is decompressed using the correct algorithm.
     */
    @Test
    public void testGzipContent() {
        Http1ClientRequest request = webClient.get();
        request.header(Http.Header.create(Http.Header.ACCEPT_ENCODING, "gzip"));
        try (Http1ClientResponse response = request.path("/compressed").request()) {
            assertThat(response.entity().as(String.class), equalTo("It works!"));
        }
    }

    /**
     * Test that the entity is decompressed using the correct algorithm.
     */
    @Test
    public void testDeflateContent() {
        Http1ClientRequest builder = webClient.get();
        builder.header(Http.Header.create(Http.Header.ACCEPT_ENCODING, "deflate"));
        try (Http1ClientResponse response = builder.path("/compressed").request()) {
            assertThat(response.entity().as(String.class), equalTo("It works!"));
        }
    }

    private String httpGet(String req) {
        try (Socket socket = new Socket("localhost", webServer.port())) {
            socket.setSoTimeout(15000);
            var os = socket.getOutputStream();
            os.write((req + "\n").replaceAll("\n", "\r\n").getBytes());
            os.flush();
            return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                    .lines()
                    .takeWhile(Objects::nonNull)
                    .takeWhile(s -> !s.isEmpty())
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
