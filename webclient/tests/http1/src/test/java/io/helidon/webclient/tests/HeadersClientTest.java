/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.webclient.tests;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import io.helidon.common.media.type.ParserMode;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.HttpMediaType;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.logging.common.LogConfig;
import io.helidon.webclient.api.HttpClientRequest;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;

import io.vertx.core.Vertx;
import io.vertx.core.http.Http2Settings;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HeadersClientTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Header INVALID_CONTENT_TYPE_VALUE =
            HeaderValues.create(HeaderNames.CONTENT_TYPE, "invalid header value");
    private static final Header INVALID_CONTENT_TYPE_TEXT =
            HeaderValues.create(HeaderNames.CONTENT_TYPE, "text");
    private static final Header RELAXED_CONTENT_TYPE_TEXT =
            HeaderValues.create(HeaderNames.CONTENT_TYPE, "text/plain");
    private static final HeaderName PORT_HEADER_NAME = HeaderNames.create("Trailer-port");
    private static final Header BEFORE_HEADER = HeaderValues.create("test", "before");
    private static final Header TRAILER_HEADER = HeaderValues.create("Trailer-header", "trailer-test");
    private static final String DATA = "Helidon!!!".repeat(10);
    private static final Vertx VERTX = Vertx.vertx();
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private static HttpServer SERVER;
    private static WebClient CLIENT;

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
                    case "/test/invalidContentType" -> {
                        res.putHeader(INVALID_CONTENT_TYPE_VALUE.name(), INVALID_CONTENT_TYPE_VALUE.get());
                        res.end();
                    }
                    // Returns Content-Type: text instead of text/plain
                    case "/test/invalidTextContentType" -> {
                        res.putHeader(INVALID_CONTENT_TYPE_TEXT.name(), INVALID_CONTENT_TYPE_TEXT.get());
                        res.end();
                    }
                    case "/trailer" -> {
                        res.putHeader(BEFORE_HEADER.name(), BEFORE_HEADER.get());
                        res.putHeader("Trailer", List.<String>of("Trailer-header", "Trailer-port"));
                        res.setChunked(true);
                        res.write(DATA);
                        res.putTrailer(TRAILER_HEADER.name(), TRAILER_HEADER.get());
                        res.putTrailer(PORT_HEADER_NAME.defaultCase(), String.valueOf(req.remoteAddress().port()));
                        res.end();
                    }
                    case "/no-trailer" -> {
                        res.putHeader(BEFORE_HEADER.name(), BEFORE_HEADER.get());
                        res.setChunked(true);
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

        int port = SERVER.actualPort();
        CLIENT = WebClient.builder()
                .baseUri("http://localhost:" + port + "/")
                .build();
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


    // Verify that invalid content type is present in response headers and is accessible
    @Test
    public void testInvalidContentType() {
        try (HttpClientResponse res = CLIENT.method(Method.GET)
                .path("/test/invalidContentType")
                .request()) {
            assertThat(res.status(), is(Status.OK_200));
            assertThat(res.headers(), hasHeader(INVALID_CONTENT_TYPE_VALUE));
        }
    }

    // Verify that "Content-Type: text" header parsing fails in strict mode
    @Test
    public void testInvalidTextContentTypeStrict() {
        try (HttpClientResponse res = CLIENT.method(Method.GET)
                .protocolId(Http1Client.PROTOCOL_ID)
                .path("/test/invalidTextContentType")
                .request()) {
            assertThat(res.status(), is(Status.OK_200));
            Headers h = res.headers();
            // Raw protocol data value
            assertThat(res.headers(), hasHeader(INVALID_CONTENT_TYPE_TEXT));
            Header rawContentType = h.get(HeaderNames.CONTENT_TYPE);
            assertThat(rawContentType.get(), is(INVALID_CONTENT_TYPE_TEXT.get()));
            // Media type parsed value is invalid, IllegalArgumentException shall be thrown
            var ex = assertThrows(IllegalArgumentException.class, h::contentType);
            assertThat(ex.getMessage(), is("Cannot parse media type: text"));
        }
    }

    // Verify that "Content-Type: text" header parsing returns text/plain in relaxed mode
    @Test
    public void testInvalidTextContentTypeRelaxed() {
        WebClient client = WebClient.builder()
                .from(HeadersClientTest.CLIENT.prototype())
                .mediaTypeParserMode(ParserMode.RELAXED)
                .build();
        try (HttpClientResponse res = client.method(Method.GET)
                .protocolId(Http1Client.PROTOCOL_ID)
                .path("/test/invalidTextContentType")
                .request()) {
            assertThat(res.status(), is(Status.OK_200));
            Headers h = res.headers();
            // Raw protocol data value
            assertThat(res.headers(), hasHeader(INVALID_CONTENT_TYPE_TEXT));
            // Media type parsed value
            Optional<HttpMediaType> contentType = h.contentType();
            assertThat(contentType.isPresent(), is(true));
            assertThat(contentType.get().text(), is(RELAXED_CONTENT_TYPE_TEXT.getString()));
        }
    }

    @Test
    void trailerHeader() {
        HttpClientRequest request = CLIENT
                .method(Method.GET)
                .path("/trailer");

        int prevPort = -1;

        for (int i = 0; i < 3; i++) {
            try (HttpClientResponse res = request.request()) {
                assertThat(res.headers(), hasHeader(BEFORE_HEADER));
                assertThrows(IllegalStateException.class, res::trailers);
                assertThat(res.as(String.class), is(DATA));
                assertThat(res.trailers(), hasHeader(TRAILER_HEADER));

                Headers t = res.trailers();
                Optional<Integer> clientPort = t.first(PORT_HEADER_NAME).map(Integer::parseInt);
                if (clientPort.isPresent()) {
                    if (prevPort != -1) {
                        // Check cached connection reuse
                        assertThat(clientPort.get(), is(prevPort));
                    }
                    prevPort = clientPort.get();
                }
            }
        }
    }
    @Test
    void noTrailerHeader() {
        HttpClientRequest request = CLIENT
                .method(Method.GET)
                .path("/no-trailer");

            try (HttpClientResponse res = request.request()) {
                assertThat(res.headers(), hasHeader(BEFORE_HEADER));
                assertThat(res.trailers().size(), is(0));
                assertThat(res.as(String.class), is(DATA));
                assertThat(res.trailers().size(), is(0));
            }

    }

}
