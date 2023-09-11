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

package io.helidon.webclient.tests.http2;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.Http;
import io.helidon.http.Method;
import io.helidon.logging.common.LogConfig;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientResponse;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.Http2Settings;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

class HeadersTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String DATA = "Helidon!!!".repeat(10);
    private static final Vertx vertx = Vertx.vertx();
    private static final ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();
    private static HttpServer server;
    private static int port;

    @BeforeAll
    static void beforeAll() throws ExecutionException, InterruptedException, TimeoutException {
        LogConfig.configureRuntime();
        server = vertx.createHttpServer(new HttpServerOptions()
                        .setInitialSettings(new Http2Settings()
                                .setMaxHeaderListSize(Integer.MAX_VALUE)
                        )
                )
                .requestHandler(req -> {
                    HttpServerResponse res = req.response();
                    switch (req.path()) {
                        case "/trailer" -> {
                            res.putHeader("test", "before");
                            res.write(DATA);
                            res.putTrailer("Trailer-header", "trailer-test");
                            res.end();
                        }
                        case "/cont-in" -> {
                            for (int i = 0; i < 500; i++) {
                                res.headers().add("test-header-" + i, DATA);
                            }
                            res.write(DATA);
                            res.end();
                        }
                        case "/cont-out" -> {
                            MultiMap headers = req.headers();
                            StringBuilder sb = new StringBuilder();
                            for (Map.Entry<String, String> header : headers) {
                                if (!header.getKey().startsWith("test-header-")) continue;
                                sb.append(header.getKey() + "=" + header.getValue() + "\n");
                            }

                            res.write(sb.toString());
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

    @Test
    //FIXME: #6544 trailer headers are not implemented yet
    @Disabled
    void trailerHeader() {
        try (Http2ClientResponse res = Http2Client.builder()
                .baseUri("http://localhost:" + port + "/")
                .build()
                .method(Method.GET)
                .path("/trailer")
                .priorKnowledge(true)
                .request()) {
            Headers h = res.headers();
            assertThat(h.first(HeaderNames.create("test")).orElse(null), is("before"));
            assertThat(res.as(String.class), is(DATA));
            assertThat(h.first(HeaderNames.create("Trailer-header")).orElse(null), is("trailer-test"));
        }
    }

    @Test
    void continuationInbound() {
        try (Http2ClientResponse res = Http2Client.builder()
                .baseUri("http://localhost:" + port + "/")
                .build()
                .method(Method.GET)
                .path("/cont-in")
                .priorKnowledge(true)
                .request()) {

            Headers h = res.headers();
            for (int i = 0; i < 500; i++) {
                String name = "test-header-" + i;
                assertThat("Headers " + name, h.first(HeaderNames.create(name)).orElse(null), is(DATA));
            }

            assertThat(res.as(String.class), is(DATA));
        }
    }

    @Test
    void continuationOutbound() {
        Set<String> expected = new HashSet<>(500);
        try (Http2ClientResponse res = Http2Client.builder()
                .baseUri("http://localhost:" + port + "/")
                .build()
                .method(Method.GET)
                .path("/cont-out")
                .priorKnowledge(true)
                .headers(hv -> {
                    for (int i = 0; i < 500; i++) {
                        hv.add(Http.Headers.createCached("test-header-" + i, DATA + i));
                        expected.add("test-header-" + i + "=" + DATA + i);
                    }
                })
                .request()) {
            String actual = res.as(String.class);
            assertThat(List.of(actual.split("\\n")), containsInAnyOrder(expected.toArray(new String[0])));
        }
    }

    @Test
    void continuationOutboundPost() {
        Set<String> expected = new HashSet<>(500);
        try (Http2ClientResponse res = Http2Client.builder()
                .baseUri("http://localhost:" + port + "/")
                .build()
                .method(Method.POST)
                .path("/cont-out")
                .priorKnowledge(true)
                .headers(hv -> {
                    for (int i = 0; i < 500; i++) {
                        hv.add(Http.Headers.createCached("test-header-" + i, DATA + i));
                        expected.add("test-header-" + i + "=" + DATA + i);
                    }
                })
                .submit(DATA)) {
            String actual = res.as(String.class);
            assertThat(List.of(actual.split("\\n")), containsInAnyOrder(expected.toArray(new String[0])));
        }
    }
}
