/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

import java.nio.charset.Charset;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webclient.WebClientResponse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that verify 413 conditions with very large payloads.
 */
public class MaxPayloadSizeTest {
    private static final Logger LOGGER = Logger.getLogger(MaxPayloadSizeTest.class.getName());

    private static final long MAX_PAYLOAD_SIZE = 128L;
    private static final String PAYLOAD = new String(new char[1024]).replace('\0', 'A');

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
                        .post("/maxpayload", (req, res) -> {
                            req.content().subscribe(new Flow.Subscriber<>() {

                                @Override
                                public void onSubscribe(Flow.Subscription subscription) {
                                    subscription.request(Integer.MAX_VALUE);
                                }

                                @Override
                                public void onNext(DataChunk item) {
                                }

                                @Override
                                public void onError(Throwable t) {
                                    res.status(Http.Status.INTERNAL_SERVER_ERROR_500).send(t.getMessage());
                                }

                                @Override
                                public void onComplete() {
                                    res.status(Http.Status.OK_200).send();
                                }
                            });
                        })
                        .build())
                .maxPayloadSize(MAX_PAYLOAD_SIZE)
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
     * If content length is greater than max, a 413 must be returned. No actual
     * payload in this case.
     */
    @Test
    public void testContentLengthExceeded() {
        WebClientRequestBuilder builder = webClient.post();
        builder.headers().add("Content-Length", "512");        // over max
        WebClientResponse response = builder.path("/maxpayload")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .request()
                .await(5, TimeUnit.SECONDS);
        assertThat(response.status().code(), is(Http.Status.REQUEST_ENTITY_TOO_LARGE_413.code()));
    }

    /**
     * If content length is greater than max, a 413 must be returned.
     */
    @Test
    public void testContentLengthExceededWithPayload() {
        WebClientRequestBuilder builder = webClient.post();
        WebClientResponse response = builder.path("/maxpayload")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .submit(PAYLOAD)
                .await(5, TimeUnit.SECONDS);
        assertThat(response.status().code(), is(Http.Status.REQUEST_ENTITY_TOO_LARGE_413.code()));
    }

    /**
     * If actual payload length is greater than max when using chunked encoding, a 413
     * must be returned. Given that this publisher can write up to 3 chunks (using chunked
     * encoding), we also check for a connection reset exception condition.
     */
    @Test
    public void testActualLengthExceededWithPayload() {
        try {
            WebClientRequestBuilder builder = webClient.post();
            WebClientResponse response = builder.path("/maxpayload")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .submit(new PayloadPublisher(PAYLOAD, 3))
                    .await(5, TimeUnit.SECONDS);
            assertThat(response.status().code(), is(Http.Status.REQUEST_ENTITY_TOO_LARGE_413.code()));
        } catch (CompletionException e) {
            assertTrue(isConnectionReset(e));
        }
    }

    /**
     * Tests mixed requests, some that exceed limits, others that do not.
     */
    @Test
    public void testMixedGoodAndBadPayloads() {
        WebClientRequestBuilder builder = webClient.post();
        WebClientResponse response = builder.path("/maxpayload")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .submit(PAYLOAD.substring(0, 100))
                .await(5, TimeUnit.SECONDS);
        assertThat(response.status().code(), is(Http.Status.OK_200.code()));

        builder = webClient.post();
        response = builder.path("/maxpayload")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .submit(new PayloadPublisher(PAYLOAD, 1))
                .await(5, TimeUnit.SECONDS);
        assertThat(response.status().code(), is(Http.Status.REQUEST_ENTITY_TOO_LARGE_413.code()));

        builder = webClient.post();
        response = builder.path("/maxpayload")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .submit(PAYLOAD.substring(0, (int) MAX_PAYLOAD_SIZE))
                .await(5, TimeUnit.SECONDS);
        assertThat(response.status().code(), is(Http.Status.OK_200.code()));
    }

    /**
     * Publishes the same chunk multiple times. If number of times is greater than
     * one, Helidon will send content using chunked encoding.
     */
    static class PayloadPublisher implements Flow.Publisher<DataChunk> {

        private final String chunk;
        private int count;

        PayloadPublisher(String chunk, int count) {
            this.chunk = chunk;
            this.count = Math.max(count, 0);
        }

        @Override
        public void subscribe(Flow.Subscriber<? super DataChunk> subscriber) {
            subscriber.onSubscribe(new Flow.Subscription() {
                private int nestedonNext = 0;
                private boolean onCompleteCalled = false;

                @Override
                public void request(long n) {
                    if (n != 1) {
                        throw new UnsupportedOperationException("Request count must be 1");
                    }
                    if (count-- > 0) {
                        nestedonNext++;
                        subscriber.onNext(DataChunk.create(chunk.getBytes(Charset.defaultCharset())));
                        nestedonNext--;
                    }
                    if (count <= 0 && nestedonNext == 0 && !onCompleteCalled) {
                        onCompleteCalled = true;
                        subscriber.onComplete();
                    }
                }

                @Override
                public void cancel() {
                }
            });
        }
    }

    /**
     * On a 413 result, the server shall attempt to close the connection (Netty channel).
     * In some scenarios (slow systems?), the client may receive a connection reset
     * while attempting to write more data.
     *
     * @param e a completion exception
     * @return boolean indicating if this is a connection reset scenario
     */
    private static boolean isConnectionReset(CompletionException e) {
        return e.getCause() instanceof IllegalStateException && e.getMessage().contains("Connection reset");
    }
}
