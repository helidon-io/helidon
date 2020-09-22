/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.util.Arrays;;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import io.helidon.common.http.Http;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.utils.SocketHttpClient;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsMapContaining.hasEntry;
import static io.helidon.webserver.TransferEncodingTest.cutHeaders;

/**
 * Tests support for compression in the webserver.
 */
public class CompressionTest {
    private static final Logger LOGGER = Logger.getLogger(CompressionTest.class.getName());

    private static WebServer webServer;

    private static WebClient webClient;

    @BeforeAll
    public static void startServer() throws Exception {
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
                        .get("/compressed", (req, res) -> {
                            String payload = "It works!";
                            res.send(payload);
                        })
                        .build())
                .enableCompression(true)        // compression
                .build()
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

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
        List<String> requestHeaders = Arrays.asList("Accept-Encoding: gzip");
        String s = SocketHttpClient.sendAndReceive("/compressed", Http.Method.GET, null,
                requestHeaders, webServer);
        Map<String, String> responseHeaders = cutHeaders(s);
        assertThat(responseHeaders, hasEntry("content-encoding", "gzip"));
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
        List<String> requestHeaders = Arrays.asList("Accept-Encoding: deflate");
        String s = SocketHttpClient.sendAndReceive("/compressed", Http.Method.GET, null,
                requestHeaders, webServer);
        Map<String, String> responseHeaders = cutHeaders(s);
        assertThat(responseHeaders, hasEntry("content-encoding", "deflate"));
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
                .await(10, TimeUnit.SECONDS);
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
                .await(10, TimeUnit.SECONDS);
        assertThat(response.content().as(String.class).get(), equalTo("It works!"));
    }
}
