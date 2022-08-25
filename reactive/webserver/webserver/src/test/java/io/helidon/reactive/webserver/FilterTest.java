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
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.reactive.media.common.MessageBodyWriter;
import io.helidon.reactive.media.common.MessageBodyWriterContext;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class FilterTest {

    private static final Logger LOGGER = Logger.getLogger(FilterTest.class.getName());
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static WebServer webServer;
    private static final AtomicLong filterItemCounter = new AtomicLong(0);
    private static SocketHttpClient client;

    /**
     * Start the Web Server
     *
     * @param port the port on which to start the server; if less than 1,
     *             the port is dynamically selected
     * @throws Exception in case of an error
     */
    private static void startServer(int port) {
        webServer = WebServer.builder()
                .defaultSocket(s -> s
                        .host("localhost")
                        .port(port)
                )
                .routing(r -> r
                        .any((req, res) -> {
                            res.headers().set(Http.HeaderValues.TRANSFER_ENCODING_CHUNKED);
                            req.next();
                        })
                        .get("/dataChunkPublisher", (req, res) -> {
                            res.registerFilter(pub -> Multi.create(pub)
                                    .peek(chunk -> filterItemCounter.incrementAndGet()));
                            res.send(Single.just("Test").map(s -> DataChunk.create(s.getBytes())));
                        })
                        .get("/dataChunkPublisherNoFilters", (req, res) -> {
                            res.registerFilter(pub -> Multi.create(pub)
                                    .peek(chunk -> filterItemCounter.incrementAndGet()));
                            res.send(Single.just("Test").map(s -> DataChunk.create(s.getBytes())), false);
                        })
                        .get("/customWriter", (req, res) -> {
                            res.registerFilter(pub -> Multi.create(pub)
                                    .peek(chunk -> filterItemCounter.incrementAndGet()));
                            res.send(ctx -> {
                                return ctx.marshall(Single.just("Test"), new MessageBodyWriter<>() {

                                    @Override
                                    public PredicateResult accept(final GenericType<?> type,
                                                                  final MessageBodyWriterContext context) {
                                        return PredicateResult.SUPPORTED;
                                    }

                                    @Override
                                    public Flow.Publisher<DataChunk> write(final Single<? extends String> single,
                                                                           final GenericType<? extends String> type,
                                                                           final MessageBodyWriterContext context) {
                                        return single.map(s -> DataChunk.create(s.getBytes()));
                                    }
                                }, GenericType.create(String.class));
                            });
                        })
                )
                .build()
                .start()
                .await(TIMEOUT);

        client = SocketHttpClient.create(webServer.port());
        LOGGER.info("Started server at: https://localhost:" + webServer.port());
    }

    @BeforeAll
    static void startServer() throws Exception {
        // start the server at a free port
        startServer(0);
    }

    @AfterAll
    static void close() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                    .await(TIMEOUT);
        }
        if (client != null) {
            client.close();
        }
    }

    @BeforeEach
    void resetClient() {
        client.disconnect();
        client.connect();
    }

    @Test
    void customWriterTest() {
        filterItemCounter.set(0);
        String response = client.sendAndReceive("/customWriter", Http.Method.GET, null);
        assertThat(response, containsString("200 OK"));
        assertThat("Filter should been called once.",
                filterItemCounter.get(), is(1L));
    }

    @Test
    void dataChunkPublisherTest() {
        filterItemCounter.set(0);
        String response = client.sendAndReceive("/dataChunkPublisher", Http.Method.GET, null);
        assertThat(response, containsString("200 OK"));
        assertThat("Filter should been called once.",
                filterItemCounter.get(), is(1L));
    }

    @Test
    void dataChunkPublisherNoFiltersTest() {
        filterItemCounter.set(0);
        client.sendAndReceive("/dataChunkPublisherNoFilters", Http.Method.GET, null);
        assertThat("Filter shouldn't been called.",
                filterItemCounter.get(), is(0L));
    }
}
