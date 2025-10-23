/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

import io.helidon.common.buffers.DataReader;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.Status;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

@ServerTest
class ErrorHandlingWithOutputStreamTest {

    private static final HeaderName MAIN_HEADER_NAME = HeaderNames.create("main-handler");
    private static final HeaderName ERROR_HEADER_NAME = HeaderNames.create("error-handler");

    private final Http1Client client;

    ErrorHandlingWithOutputStreamTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void router(HttpRouting.Builder router) {
        router.error(CustomException.class, new CustomRoutingHandler())
                .get("get-outputStream", (req, res) -> {
                    res.header(MAIN_HEADER_NAME, "x");
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
    void testGetOutputStreamThenError_expect_CustomErrorHandlerMessage() {
        try (var response = client.get("/get-outputStream").request()) {
            assertThat(response.status(), is(Status.I_AM_A_TEAPOT_418));
            assertThat(response.entity().as(String.class), is("CustomErrorContent"));
            assertThat(response.headers().contains(ERROR_HEADER_NAME), is(true));
            assertThat(response.headers().contains(MAIN_HEADER_NAME), is(false));
        }
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
            res.send("CustomErrorContent");
        }
    }

    private static class CustomException extends RuntimeException {

    }
}
