/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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

package io.helidon.reactive.webserver;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.logging.Logger;

import io.helidon.common.http.ClientResponseHeaders;
import io.helidon.common.http.Http;
import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.reactive.webclient.WebClient;
import io.helidon.reactive.webclient.WebClientRequestBuilder;
import io.helidon.reactive.webclient.WebClientResponse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests support for compression in the webserver.
 */
@Deprecated(since = "3.0.0", forRemoval = true)
class CompressionV2ApiTest {
    private static final Logger LOGGER = Logger.getLogger(CompressionV2ApiTest.class.getName());
    private static final Duration TIME_OUT = Duration.of(10, ChronoUnit.SECONDS);

    private static WebServer webServer;

    private static WebClient webClient;

    @BeforeAll
    static void startServer() throws Exception {
        startServer(0);
    }

    @AfterAll
    static void close() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                    .await(TIME_OUT);
        }
    }

    /**
     * Start the Web Server
     *
     * @param port the port on which to start the server; if less than 1,
     * the port is dynamically selected
     */
    private static void startServer(int port) {
        webServer = WebServer.builder()
                .host("localhost")
                .port(port)
                .routing(Routing.builder()
                        .get("/compressed", (req, res) -> {
                            String payload = "It works!";
                            res.send(payload);
                        })
                        .build())
                .enableCompression(true)        // compression
                .build()
                .start()
                .await(TIME_OUT);

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
    void testGzipHeader() throws Exception {
        List<String> requestHeaders = List.of("Accept-Encoding: gzip");
        try (SocketHttpClient c = SocketHttpClient.create(webServer.port())) {
            String s = c.sendAndReceive("/compressed",
                                        Http.Method.GET, null,
                                        requestHeaders);

            ClientResponseHeaders responseHeaders = SocketHttpClient.headersFromResponse(s);
            assertThat(responseHeaders, hasHeader(Http.HeaderValue.create(Http.Header.CONTENT_ENCODING, "gzip")));
        }
    }

    /**
     * Test that "content-encoding" is "deflate". Note that we use a
     * {@code SocketHttpClient} as other clients may remove this header after
     * processing.
     *
     * @throws Exception if error occurs.
     */
    @Test
    void testDeflateHeader() throws Exception {
        List<String> requestHeaders = List.of("Accept-Encoding: deflate");
        try (SocketHttpClient c = SocketHttpClient.create(webServer.port())) {
            String s = c.sendAndReceive("/compressed",
                                        Http.Method.GET, null,
                                        requestHeaders);

            ClientResponseHeaders responseHeaders = SocketHttpClient.headersFromResponse(s);
            assertThat(responseHeaders, hasHeader(Http.HeaderValue.create(Http.Header.CONTENT_ENCODING, "deflate")));
        }
    }

    /**
     * Test that the entity is decompressed using the correct algorithm.
     *
     * @throws Exception if error occurs.
     */
    @Test
    void testGzipContent() throws Exception {
        WebClientRequestBuilder builder = webClient.get();
        builder.headers().add("Accept-Encoding", "gzip");
        WebClientResponse response = builder.path("/compressed")
                .request()
                .await(TIME_OUT);
        assertThat(response.content().as(String.class).get(), equalTo("It works!"));
    }

    /**
     * Test that the entity is decompressed using the correct algorithm.
     *
     * @throws Exception if error occurs.
     */
    @Test
    void testDeflateContent() throws Exception {
        WebClientRequestBuilder builder = webClient.get();
        builder.headers().add("Accept-Encoding", "deflate");
        WebClientResponse response = builder.path("/compressed")
                .request()
                .await(TIME_OUT);
        assertThat(response.content().as(String.class).get(), equalTo("It works!"));
    }
}
