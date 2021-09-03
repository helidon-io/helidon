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
 *
 */

package io.helidon.webserver;

import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.media.common.MessageBodyWriter;
import io.helidon.media.common.MessageBodyWriterContext;
import io.helidon.webserver.utils.SocketHttpClient;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class FilterTest {

    private static final Logger LOGGER = Logger.getLogger(FilterTest.class.getName());
    private static WebServer webServer;
    private static final AtomicLong filterItemCounter = new AtomicLong(0);

    /**
     * Start the Web Server
     *
     * @param port the port on which to start the server; if less than 1,
     *             the port is dynamically selected
     * @throws Exception in case of an error
     */
    private static void startServer(int port) {
        webServer = WebServer.builder()
                .port(port)
                .routing(Routing.builder().any((req, res) -> {
                    res.headers().add(Http.Header.TRANSFER_ENCODING, "chunked");
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
                        .build())
                .build()
                .start()
                .await(10, TimeUnit.SECONDS);

        LOGGER.info("Started server at: https://localhost:" + webServer.port());
    }

    @BeforeAll
    public static void startServer() throws Exception {
        // start the server at a free port
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

    @Test
    public void customWriterTest() throws Exception {
        filterItemCounter.set(0);
        SocketHttpClient.sendAndReceive("/customWriter", Http.Method.GET, null, webServer);
        assertThat("Filter should been called only once, but was called " + filterItemCounter.get() + " times.",
                filterItemCounter.get(), is(equalTo(1L)));
    }

    @Test
    public void dataChunkPublisherTest() throws Exception {
        filterItemCounter.set(0);
        SocketHttpClient.sendAndReceive("/dataChunkPublisher", Http.Method.GET, null, webServer);
        assertThat("Filter should been called only once, but was called " + filterItemCounter.get() + " times.",
                filterItemCounter.get(), is(equalTo(1L)));
    }

    @Test
    public void dataChunkPublisherNoFiltersTest() throws Exception {
        filterItemCounter.set(0);
        SocketHttpClient.sendAndReceive("/dataChunkPublisherNoFilters", Http.Method.GET, null, webServer);
        assertThat("Filter shouldn't been called, but was called " + filterItemCounter.get() + " times.",
                filterItemCounter.get(), is(equalTo(0L)));
    }
}
