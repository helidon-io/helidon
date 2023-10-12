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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.logging.common.LogConfig;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientResponse;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.Http2Settings;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HeadersClientTest {

    private static final System.Logger LOGGER = System.getLogger(HeadersClientTest.class.getName());
    private static final Header BEFORE_HEADER = HeaderValues.create("test", "before");
    private static final Header TRAILER_HEADER = HeaderValues.create("Trailer-header", "trailer-test");
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String DATA = "Helidon!!!".repeat(10);
    private static final Vertx VERTX = Vertx.vertx(new VertxOptions().setBlockedThreadCheckInterval(1000*60*60));
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private static HttpServer SERVER;
    private static Http2Client CLIENT;
    private static HttpClient VERTX_CLIENT;

    @BeforeAll
    static void beforeAll() throws ExecutionException, InterruptedException, TimeoutException {
        LogConfig.configureRuntime();
        SERVER = VERTX.createHttpServer(new HttpServerOptions()
                        .setInitialSettings(new Http2Settings()
                                .setMaxHeaderListSize(Integer.MAX_VALUE)
                        )
                )
                .requestHandler(req -> {
                    HttpServerResponse res = req.response();
                    switch (req.path()) {
                        case "/upgrade" -> res.end();
                        case "/trailer" -> {
                            res.putHeader(BEFORE_HEADER.name(), BEFORE_HEADER.get());
                            res.putHeader(HeaderNames.TRAILER.defaultCase(), "Trailer-header");
                            res.write(DATA);
                            res.putTrailer(TRAILER_HEADER.name(), TRAILER_HEADER.get());
                            res.end();
                        }
                        case "/no-trailer" -> {
                            res.putHeader(BEFORE_HEADER.name(), BEFORE_HEADER.get());
                            res.write(DATA);
                            res.write(DATA);
                            res.write(DATA);
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
                        case "/cont-trailer" -> {
                            // Push vertx to split trailers to multiple frames - continuations
                            Map<String, String> trailers = new HashMap<>(500);
                            for (int i = 0; i < 500; i++) {
                                trailers.put("test-trailer-" + i, DATA);
                            }
                            res.headers().add(HeaderNames.TRAILER.defaultCase(), trailers.keySet());
                            res.write(DATA);
                            res.trailers().addAll(trailers);
                            res.end();
                        }
                        case "/100-continue-not" -> {
                            res.setStatusCode(418).send();
                        }
                        case "/100-continue" -> {
                            AtomicBoolean continueSent = new AtomicBoolean(false);
                            req.body(event -> {
                                if(continueSent.get()) {
                                    res.putHeader(HeaderNames.CONTENT_LENGTH.defaultCase(),
                                                  String.valueOf(event.result().length()));
                                    res.write(event.result().toString());
                                } else {
                                    res.setStatusCode(500);
                                    res.write("Got data before 100 continue!");
                                }
                                res.end();
                            });
                            res.putHeader("before_100", "test");
                            req.end();
                            VERTX.executeBlocking(future -> {
                                try {
                                    Thread.sleep(200);
                                } catch (InterruptedException e) {
                                    LOGGER.log(System.Logger.Level.INFO, "100 test interrupted", e);
                                }
                                continueSent.set(true);
                                if (!"true".equals(req.getHeader("no-continue"))) {
                                    res.writeContinue();
                                }
                                res.putHeader("after_100", "test");
                                future.complete();
                            });
                        }
                        default -> res.setStatusCode(404).end();
                    }
                })
                .listen(0)
                .toCompletionStage()
                .toCompletableFuture()
                .get(TIMEOUT.toMillis(), MILLISECONDS);

        int port = SERVER.actualPort();
        CLIENT = Http2Client.builder()
                .baseUri("http://localhost:" + port + "/")
                .readContinueTimeout(Duration.ofSeconds(2))
                .build();

        HttpClientOptions clientOptions = new HttpClientOptions()
                .setProtocolVersion(HttpVersion.HTTP_2);
        VERTX_CLIENT = VERTX.createHttpClient(clientOptions);
    }

    @AfterAll
    static void afterAll() {
        SERVER.close();
        VERTX.close();
        EXECUTOR.shutdown();
        try {
            if (!EXECUTOR.awaitTermination(TIMEOUT.toMillis(), MILLISECONDS)) {
                EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            EXECUTOR.shutdownNow();
        }
    }

    @Test
    void trailerHeader() {
        try (Http2ClientResponse res = CLIENT
                .method(Method.GET)
                .path("/trailer")
                .priorKnowledge(true)
                .request()) {
            assertThat(res.headers(), hasHeader(BEFORE_HEADER));
            assertThrows(IllegalStateException.class, res::trailers);
            assertThat(res.as(String.class), is(DATA));
            assertThat(res.trailers(), hasHeader(TRAILER_HEADER));
        }
    }

    @Test
    void continue100Vertx() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<String> finished = new CompletableFuture<>();

        VERTX_CLIENT.request(HttpMethod.GET, SERVER.actualPort(), "localhost", "/upgrade")
                .onSuccess(HttpClientRequest::end)
                .toCompletionStage().toCompletableFuture().join();
        VERTX_CLIENT.request(HttpMethod.PUT, SERVER.actualPort(), "localhost", "/100-continue")
                .onSuccess(request -> {
                    request.response().onSuccess(response -> {
                        response.end();
                        response.body().onSuccess(event -> finished.complete(event.toString()));
                    }).onFailure(finished::completeExceptionally);

                    request.putHeader("Expect", "100-Continue");

                    request.continueHandler(v -> {
                        // OK to send rest of body
                        request.putHeader(HeaderNames.CONTENT_LENGTH.defaultCase(), String.valueOf(DATA.length()));
                        request.write(DATA);
                        request.end();
                    });

                    request.sendHead();
                });

        assertThat(finished.get(TIMEOUT.toMillis(), MILLISECONDS), is(DATA));
    }

    @Test
    void continue100() {
        try (Http2ClientResponse res = CLIENT
                .method(Method.PUT)
                .path("/100-continue")
                .priorKnowledge(true)
                .header(HeaderValues.EXPECT_100)
                .submit(DATA)) {
            assertThat(res.as(String.class), is(DATA));
            Header before100Header = HeaderValues.create("before_100", "test");
            Header after100Header = HeaderValues.create("after_100", "test");
            assertThat(res.headers(), hasHeader(before100Header));
            assertThat(res.headers(), hasHeader(after100Header));
        }
    }

    @Test
    void continue100not() {
        try (Http2ClientResponse res = CLIENT
                .method(Method.PUT)
                .path("/100-continue-not")
                .priorKnowledge(true)
                .header(HeaderValues.EXPECT_100)
                .submit(DATA)) {
            assertThat(res.status(), is(Status.I_AM_A_TEAPOT_418));
        }
    }

    @Test
    void continue100Timeout() {
        try (Http2ClientResponse res = CLIENT
                .method(Method.PUT)
                .path("/100-continue")
                .priorKnowledge(true)
                .header(HeaderValues.EXPECT_100)
                .header(HeaderValues.create("no-continue", "true"))
                .submit(DATA)) {
            assertThat(res.as(String.class), is(DATA));
            Header before100Header = HeaderValues.create("before_100", "test");
            Header after100Header = HeaderValues.create("after_100", "test");
            assertThat(res.headers(), hasHeader(before100Header));
            assertThat(res.headers(), hasHeader(after100Header));
        }
    }


    @Test
    void noTrailerHeader() {
        try (Http2ClientResponse res = CLIENT
                .method(Method.GET)
                .path("/no-trailer")
                .priorKnowledge(true)
                .request()) {
            assertThat(res.headers(), hasHeader(BEFORE_HEADER));
            assertThat(res.as(String.class), is(DATA+DATA+DATA));
            IllegalStateException ex = assertThrows(IllegalStateException.class, res::trailers);
            assertThat(ex.getMessage(), is("No trailers are expected."));
        }
    }

    @Test
    void trailerHeaderContinuation() {
        try (Http2ClientResponse res = CLIENT
                .method(Method.GET)
                .path("/cont-trailer")
                .priorKnowledge(true)
                .request()) {

            assertThat(res.as(String.class), is(DATA));
            assertThat(res.trailers().size(), is(500));
            for (int i = 0; i < 500; i++) {
                Header indexedTrailerHeader = HeaderValues.create("test-trailer-" + i, DATA);
                assertThat(res.trailers(), hasHeader(indexedTrailerHeader));
            }
        }
    }

    @Test
    void continuationInbound() {
        try (Http2ClientResponse res = CLIENT
                .method(Method.GET)
                .path("/cont-in")
                .priorKnowledge(true)
                .request()) {

            for (int i = 0; i < 500; i++) {
                Header indexedHeader = HeaderValues.create("test-header-" + i, DATA);
                assertThat(res.headers(), hasHeader(indexedHeader));
            }

            assertThat(res.as(String.class), is(DATA));
        }
    }

    @Test
    void continuationOutbound() {
        Set<String> expected = new HashSet<>(500);
        try (Http2ClientResponse res = CLIENT
                .method(Method.GET)
                .path("/cont-out")
                .priorKnowledge(true)
                .headers(hv -> {
                    for (int i = 0; i < 500; i++) {
                        hv.add(HeaderValues.create("test-header-" + i, DATA + i));
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
        try (Http2ClientResponse res = CLIENT
                .method(Method.POST)
                .path("/cont-out")
                .priorKnowledge(true)
                .headers(hv -> {
                    for (int i = 0; i < 500; i++) {
                        hv.add(HeaderValues.createCached("test-header-" + i, DATA + i));
                        expected.add("test-header-" + i + "=" + DATA + i);
                    }
                })
                .submit(DATA)) {
            String actual = res.as(String.class);
            assertThat(List.of(actual.split("\\n")), containsInAnyOrder(expected.toArray(new String[0])));
        }
    }
}
