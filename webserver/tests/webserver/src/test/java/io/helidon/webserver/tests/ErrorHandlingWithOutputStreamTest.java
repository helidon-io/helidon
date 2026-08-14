/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import io.helidon.common.buffers.DataReader;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.ErrorHandler;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ServerTest
class ErrorHandlingWithOutputStreamTest {

    private static final HeaderName MAIN_HEADER_NAME = HeaderNames.create("main-handler");
    private static final HeaderName ERROR_HEADER_NAME = HeaderNames.create("error-handler");
    private static final HeaderName STALE_TRAILER_NAME = HeaderNames.create("stale-trailer");
    private static final HeaderName STREAM_RESULT_NAME = HeaderNames.create("stream-result");
    private static final HeaderName CONTENT_DIGEST_NAME = HeaderNames.create("Content-Digest");
    private static final HeaderName CONTENT_MD5_NAME = HeaderNames.create("Content-MD5");
    private static final HeaderName DIGEST_NAME = HeaderNames.create("Digest");
    private static final HeaderName REPR_DIGEST_NAME = HeaderNames.create("Repr-Digest");
    private static final HeaderName CSP_HEADER_NAME = HeaderNames.create("Content-Security-Policy");
    private static final HeaderName RESPONSE_SIGNATURE_NAME = HeaderNames.create("Response-Signature");

    private final Http1Client client;

    ErrorHandlingWithOutputStreamTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void router(HttpRouting.Builder router) {
        router.error(CustomException.class, new CustomRoutingHandler())
                .addFilter((chain, req, res) -> {
                    if (req.path().path().equals("/get-cross-cutting-error")) {
                        res.beforeSend(() -> {
                            res.header(CSP_HEADER_NAME, "default-src 'none'");
                            res.header(HeaderNames.CONTENT_ENCODING, "base64");
                            res.header(HeaderNames.TRAILER, RESPONSE_SIGNATURE_NAME.defaultCase());
                            res.beforeTrailers(trailers -> trailers.set(RESPONSE_SIGNATURE_NAME, "signed"));
                        });
                        res.streamFilter(Base64.getEncoder()::wrap);
                    } else if (req.path().path().equals("/get-outputStream")) {
                        res.entityStreamFilter(_ -> OutputStream.nullOutputStream());
                        res.entityBeforeSend(() -> res.header(HeaderNames.CONTENT_ENCODING, "stale"));
                    }
                    chain.proceed();
                })
                .get("get-cross-cutting-error", (_, _) -> {
                    throw new CustomException();
                })
                .get("get-outputStream", (req, res) -> {
                    res.header(MAIN_HEADER_NAME, "x");
                    res.header(HeaderNames.CONTENT_LENGTH, "1");
                    res.header(HeaderNames.TRANSFER_ENCODING, "chunked");
                    res.header(HeaderNames.TRAILER, "x-stale-trailer");
                    res.header(HeaderNames.CONTENT_RANGE, "bytes 0-0/1");
                    res.header(HeaderNames.CONTENT_TYPE, "application/stale");
                    res.header(HeaderNames.CONTENT_ENCODING, "stale");
                    res.header(HeaderNames.CONTENT_LANGUAGE, "en");
                    res.header(HeaderNames.CONTENT_LOCATION, "/stale");
                    res.header(HeaderNames.CONTENT_DISPOSITION, "attachment");
                    res.header(CONTENT_DIGEST_NAME, "sha-256=:YWJjZA==:");
                    res.header(CONTENT_MD5_NAME, "YWJjZA==");
                    res.header(DIGEST_NAME, "SHA-256=YWJjZA==");
                    res.header(REPR_DIGEST_NAME, "sha-256=:YWJjZA==:");
                    res.header(HeaderNames.ETAG, "\"stale\"");
                    res.header(HeaderNames.LAST_MODIFIED, "stale");
                    res.header(HeaderNames.ACCEPT_RANGES, "bytes");
                    res.header(HeaderNames.CACHE_CONTROL, "no-store");
                    res.header(HeaderNames.VARY, "Origin");
                    res.outputStream();
                    throw new CustomException();
                })
                .get("get-outputStream-stale-trailers", (_, res) -> {
                    res.beforeTrailers(trailers -> trailers.set(STALE_TRAILER_NAME, "stale"));
                    res.streamResult("stale-result");
                    res.outputStream();
                    throw new CustomException();
                })
                .get("get-outputStream-writeOnceThenError", (req, res) -> {
                    res.header(MAIN_HEADER_NAME, "x");
                    OutputStream os = res.outputStream();
                    os.write("writeOnceOnly".getBytes(StandardCharsets.UTF_8));
                    throw new CustomException();
                })
                .get("get-outputStream-writeTwiceThenError", (req, res) -> {
                    res.header(MAIN_HEADER_NAME, "x");
                    OutputStream os = res.outputStream();
                    os.write("writeOnce".getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    os.write("|writeTwice".getBytes(StandardCharsets.UTF_8));
                    throw new CustomException();
                })
                .get("get-outputStream-writeFlushThenError", (req, res) -> {
                    res.header(MAIN_HEADER_NAME, "x");
                    OutputStream os = res.outputStream();
                    os.write("writeOnce".getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    throw new CustomException();
                })
                .get("get-outputStream-tryWithResources", (req, res) -> {
                    res.header(MAIN_HEADER_NAME, "x");
                    try (OutputStream os = res.outputStream()) {
                        os.write("This should not be sent immediately".getBytes(StandardCharsets.UTF_8));
                        throw new CustomException();
                    }
                })
                .get((req, res) -> res.send("ok"));
    }

    @Test
    void testOk() {
        try (var response = client.get().request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity().as(String.class), is("ok"));
        }
    }

    @Test
    void testCrossCuttingResponseHooksSurviveErrorReplacement(WebClient webClient) {
        ClientResponseTyped<String> response = webClient.get("/get-cross-cutting-error")
                .header(HeaderValues.TE_TRAILERS)
                .request(String.class);

        assertAll(
                () -> assertThat(response.headers().get(CSP_HEADER_NAME).get(), is("default-src 'none'")),
                () -> assertThat(response.headers().get(HeaderNames.CONTENT_ENCODING).get(), is("base64")),
                () -> assertThat(response.entity(),
                                 is(Base64.getEncoder()
                                            .encodeToString("CustomErrorContent".getBytes(StandardCharsets.UTF_8)))),
                () -> assertThat(response.trailers().get(RESPONSE_SIGNATURE_NAME).get(), is("signed"))
        );
    }

    @Test
    void testGetOutputStreamThenError_expect_CustomErrorHandlerMessage() {
        try (var response = client.get("/get-outputStream").request()) {
            assertThat(response.status(), is(Status.I_AM_A_TEAPOT_418));
            assertThat(response.entity().as(String.class), is("CustomErrorContent"));
            assertThat(response.headers().contains(ERROR_HEADER_NAME), is(true));
            assertThat(response.headers().contains(MAIN_HEADER_NAME), is(false));
            assertThat(response.headers().contentLength().orElse(-1),
                       is((long) "CustomErrorContent".getBytes(StandardCharsets.UTF_8).length));
            assertThat(response.headers().contains(HeaderNames.TRANSFER_ENCODING), is(false));
            assertThat(response.headers().contains(HeaderNames.TRAILER), is(false));
            assertThat(response.headers().contains(HeaderNames.CONTENT_RANGE), is(false));
            assertThat(response.headers().get(HeaderNames.CONTENT_TYPE).get(), is("text/plain; charset=UTF-8"));
            assertThat(response.headers().contains(HeaderNames.CONTENT_ENCODING), is(false));
            assertThat(response.headers().contains(HeaderNames.CONTENT_LANGUAGE), is(false));
            assertThat(response.headers().contains(HeaderNames.CONTENT_LOCATION), is(false));
            assertThat(response.headers().contains(HeaderNames.CONTENT_DISPOSITION), is(false));
            assertThat(response.headers().contains(CONTENT_DIGEST_NAME), is(false));
            assertThat(response.headers().contains(CONTENT_MD5_NAME), is(false));
            assertThat(response.headers().contains(DIGEST_NAME), is(false));
            assertThat(response.headers().contains(REPR_DIGEST_NAME), is(false));
            assertThat(response.headers().contains(HeaderNames.ETAG), is(false));
            assertThat(response.headers().contains(HeaderNames.LAST_MODIFIED), is(false));
            assertThat(response.headers().contains(HeaderNames.ACCEPT_RANGES), is(false));
            assertThat(response.headers().get(HeaderNames.CACHE_CONTROL).get(), is("no-store"));
            assertThat(response.headers().get(HeaderNames.VARY).get(), is("Origin"));
        }
    }

    @Test
    void testGetOutputStreamThenErrorClearsStaleTrailerState(WebClient webClient) {
        ClientResponseTyped<String> response = webClient.get("/get-outputStream-stale-trailers")
                .header(HeaderValues.TE_TRAILERS)
                .request(String.class);

        assertThat(response.status(), is(Status.I_AM_A_TEAPOT_418));
        assertThat(response.entity(), is("CustomErrorContent"));
        assertThat(response.trailers().contains(STALE_TRAILER_NAME), is(false));
        assertThat(response.trailers().get(STREAM_RESULT_NAME).get(), is(""));
    }

    @Test
    void testGetOutputStreamWriteOnceThenError_expect_CustomErrorHandlerMessage() {
        try (var response = client.get("/get-outputStream-writeOnceThenError").request()) {
            assertThat(response.status(), is(Status.I_AM_A_TEAPOT_418));
            assertThat(response.entity().as(String.class), is("CustomErrorContent"));
            assertThat(response.headers().contains(ERROR_HEADER_NAME), is(true));
            assertThat(response.headers().contains(MAIN_HEADER_NAME), is(false));
        }
    }

    @Test
    void testGetOutputStreamWriteTwiceThenError_expect_invalidResponse() {
        try (Http1ClientResponse response = client.method(Method.GET)
                .uri("/get-outputStream-writeTwiceThenError")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThrows(DataReader.InsufficientDataAvailableException.class, () -> response.entity().as(String.class));
        }
    }

    @Test
    void testGetOutputStreamWriteFlushThenError_expect_invalidResponse() {
        try (Http1ClientResponse response = client.method(Method.GET)
                .uri("/get-outputStream-writeFlushThenError")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThrows(DataReader.InsufficientDataAvailableException.class, () -> response.entity().as(String.class));
        }
    }

    @Test
    void testGetOutputStreamTryWithResourcesThenError_expect_CustomErrorHandlerMessage() {
        try (Http1ClientResponse response = client.method(Method.GET)
                .uri("/get-outputStream-tryWithResources")
                .request()) {

            assertThat(response.status(), is(Status.I_AM_A_TEAPOT_418));
            assertThat(response.entity().as(String.class), is("CustomErrorContent"));
            assertThat(response.headers().contains(ERROR_HEADER_NAME), is(true));
            assertThat(response.headers().contains(MAIN_HEADER_NAME), is(false));
        }
    }

    private static class CustomRoutingHandler implements ErrorHandler<CustomException> {
        @Override
        public void handle(ServerRequest req, ServerResponse res, CustomException throwable) {
            res.status(Status.I_AM_A_TEAPOT_418);
            res.header(ERROR_HEADER_NAME, "z");
            // this is now the responsibility of an error handler, as otherwise we may remove CORS headers etc.
            res.headers().remove(MAIN_HEADER_NAME);
            if (req.path().path().equals("/get-outputStream-stale-trailers")) {
                res.header(HeaderNames.TRAILER, STREAM_RESULT_NAME.defaultCase());
                res.streamFilter(outputStream -> outputStream);
            }
            res.send("CustomErrorContent");
        }
    }

    private static class CustomException extends RuntimeException {

    }
}
