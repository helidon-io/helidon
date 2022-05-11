/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.integration.webserver.upgrade.test;import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.WebServer;

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
    private static final Logger LOGGER = Logger.getLogger(CompressionTest.class.getName());
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private static WebServer webServer;

    private static WebClient webClient;

    @BeforeAll
    public static void startServer() {
        startServer(0);
    }

    @AfterAll
    public static void close() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                    .await(TIMEOUT);
        }
    }

    /**
     * Start the Web Server
     *
     * @param port the port on which to start the server; if less than 1,
     *             the port is dynamically selected
     */
    private static void startServer(int port) {
        webServer = WebServer.builder()
                .host("localhost")
                .port(port)
                .routing(r -> r.get("/compressed", (req, res) -> res.send("It works!")))
                .enableCompression(true)        // compression
                .build()
                .start()
                .await(TIMEOUT);

        webClient = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port())
                .validateHeaders(false)
                .keepAlive(true)
                .build();

        LOGGER.info("Started server at: https://localhost:" + webServer.port());
    }

    /**
     * Test that "content-encoding" header is "gzip". Note that we use a
     * {@code SocketHttpClient} as other clients may remove this header after
     * processing.
     *
     * @throws Exception if error occurs.
     */
    @Test
    public void testGzipHeader() throws Exception {
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
     *
     * @throws Exception if error occurs.
     */
    @Test
    public void testDeflateHeader() throws Exception {
        assertThat(httpGet("""
                GET /compressed HTTP/1.1
                Host: 127.0.0.1
                Accept-Encoding: deflate       
                """), containsString("content-encoding: deflate"));
    }

    /**
     * Test that the entity is decompressed using the correct algorithm.
     *
     * @throws Exception if error occurs.
     */
    @Test
    public void testGzipContent() throws Exception {
        WebClientRequestBuilder builder = webClient.get();
        builder.headers().add("Accept-Encoding", "gzip");
        WebClientResponse response = builder.path("/compressed")
                .request()
                .await(TIMEOUT);
        assertThat(response.content().as(String.class).get(), equalTo("It works!"));
    }

    /**
     * Test that the entity is decompressed using the correct algorithm.
     *
     * @throws Exception if error occurs.
     */
    @Test
    public void testDeflateContent() throws Exception {
        WebClientRequestBuilder builder = webClient.get();
        builder.headers().add("Accept-Encoding", "deflate");
        WebClientResponse response = builder.path("/compressed")
                .request()
                .await(TIMEOUT);
        assertThat(response.content().as(String.class).get(), equalTo("It works!"));
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
