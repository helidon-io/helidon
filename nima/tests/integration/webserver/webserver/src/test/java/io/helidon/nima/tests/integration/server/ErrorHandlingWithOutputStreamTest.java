/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.tests.integration.server;

import io.helidon.common.buffers.DataReader;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.http.ErrorHandler;
import io.helidon.nima.webserver.http.HttpRouting;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ServerTest
class ErrorHandlingWithOutputStreamTest {

    private static final Http.HeaderName MAIN_HEADER_NAME = Http.Header.create("main-handler");
    private static final Http.HeaderName ERROR_HEADER_NAME = Http.Header.create("error-handler");

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
                .get((req, res) -> res.send("ok"));
    }

    @Test
    void testOk() {
        var response = client.get().request();

        assertThat(response.status(), is(Http.Status.OK_200));
        assertThat(response.entity().as(String.class), is("ok"));
    }

    @Test
    void testGetOutputStreamThenError_expect_CustomErrorHandlerMessage() {
        var response = client.get("/get-outputStream").request();

        assertThat(response.status(), is(Http.Status.I_AM_A_TEAPOT_418));
        assertThat(response.entity().as(String.class), is("CustomErrorContent"));
        assertThat(response.headers().contains(ERROR_HEADER_NAME), is(true));
        assertThat(response.headers().contains(MAIN_HEADER_NAME), is(false));
    }

    @Test
    void testGetOutputStreamWriteOnceThenError_expect_CustomErrorHandlerMessage() {
        var response = client.get("/get-outputStream-writeOnceThenError").request();

        assertThat(response.status(), is(Http.Status.I_AM_A_TEAPOT_418));
        assertThat(response.entity().as(String.class), is("CustomErrorContent"));
        assertThat(response.headers().contains(ERROR_HEADER_NAME), is(true));
        assertThat(response.headers().contains(MAIN_HEADER_NAME), is(false));
    }

    @Test
    void testGetOutputStreamWriteTwiceThenError_expect_invalidResponse() {
        try (Http1ClientResponse response = client.method(Http.Method.GET)
                .uri("/get-outputStream-writeTwiceThenError")
                .request()) {
            assertThat(response.status(), is(Http.Status.OK_200));
            assertThrows(DataReader.InsufficientDataAvailableException.class, () -> response.entity().as(String.class));
        }
    }

    @Test
    void testGetOutputStreamWriteFlushThenError_expect_invalidResponse() {
        Headers headers;
        try (Http1ClientResponse response = client.method(Http.Method.GET)
                .uri("/get-outputStream-writeFlushThenError")
                .request()) {

            assertThat(response.status(), is(Http.Status.OK_200));
            assertThrows(DataReader.InsufficientDataAvailableException.class, () -> response.entity().as(String.class));
        }
    }

    private static class CustomRoutingHandler implements ErrorHandler<CustomException> {
        @Override
        public void handle(ServerRequest req, ServerResponse res, CustomException throwable) {
            res.status(Http.Status.I_AM_A_TEAPOT_418);
            res.header(ERROR_HEADER_NAME, "z");
            res.send("CustomErrorContent");
        }
    }

    private static class CustomException extends RuntimeException {

    }
}
