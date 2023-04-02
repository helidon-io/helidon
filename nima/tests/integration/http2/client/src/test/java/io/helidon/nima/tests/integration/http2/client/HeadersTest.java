/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.tests.integration.http2.client;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.logging.common.LogConfig;
import io.helidon.nima.http2.webclient.Http2;
import io.helidon.nima.http2.webclient.Http2ClientResponse;
import io.helidon.nima.webclient.WebClient;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class HeadersTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String DATA = "Helidon!!!".repeat(10);
    private static final Vertx vertx = Vertx.vertx();
    private static final ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();
    private static HttpServer server;
    private static int port;

    @BeforeAll
    static void beforeAll() throws ExecutionException, InterruptedException, TimeoutException {
        LogConfig.configureRuntime();
        server = vertx.createHttpServer()
                .requestHandler(req -> {
                    HttpServerResponse res = req.response();
                    switch (req.path()) {
                        case "/trailer" -> {
                            res.putHeader("test", "before");
                            res.write(DATA);
                            res.putTrailer("Trailer-header", "trailer-test");
                            res.end();
                        }
                        case "/cont" -> {
                            for (int i = 0; i < 500; i++) {
                                res.headers().add("test-header-" + i, DATA);
                            }
                            res.write(DATA);
                            res.end();
                        }
                        default -> res.setStatusCode(404).end();
                    }
                })
                .listen(0)
                .toCompletionStage()
                .toCompletableFuture()
                .get(TIMEOUT.toMillis(), MILLISECONDS);

        port = server.actualPort();
    }

    @Test
    @Disabled//FIXME: trailer headers are not implemented yet
    void trailerHeader() {
        try (Http2ClientResponse res = WebClient.builder(Http2.PROTOCOL)
                .baseUri("http://localhost:" + port + "/")
                .build()
                .method(Http.Method.GET)
                .path("/trailer")
                .priorKnowledge(true)
                .request()) {
            Headers h = res.headers();
            assertThat(h.first(Http.Header.create("test")).orElse(null), is("before"));
            assertThat(res.as(String.class), is(DATA));
            assertThat(h.first(Http.Header.create("Trailer-header")).orElse(null), is("trailer-test"));
        }
    }

    @Test
    void continuation() {
        try (Http2ClientResponse res = WebClient.builder(Http2.PROTOCOL)
                .baseUri("http://localhost:" + port + "/")
                .build()
                .method(Http.Method.GET)
                .path("/cont")
                .priorKnowledge(true)
                .request()) {

            Headers h = res.headers();
            for (int i = 0; i < 500; i++) {
                String name = "test-header-" + i;
                assertThat("Headers " + name, h.first(Http.Header.create(name)).orElse(null), is(DATA));
            }

            assertThat(res.as(String.class), is(DATA));
        }
    }

    @AfterAll
    static void afterAll() {
        server.close();
        vertx.close();
        exec.shutdown();
        try {
            if (!exec.awaitTermination(TIMEOUT.toMillis(), MILLISECONDS)) {
                exec.shutdownNow();
            }
        } catch (InterruptedException e) {
            exec.shutdownNow();
        }
    }
}
