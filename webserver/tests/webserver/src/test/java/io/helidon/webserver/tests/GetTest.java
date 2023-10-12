/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.Random;

import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static io.helidon.http.HeaderNames.CONTENT_LENGTH;
import static io.helidon.http.Method.GET;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class GetTest {
    private static final byte[] BYTES = new byte[256];
    private static final HeaderName REQUEST_HEADER_NAME = HeaderNames.create("X-REquEst-HEADeR");
    private static final String REQUEST_HEADER_VALUE_STRING = "some nice value";
    private static final Header REQUEST_HEADER_VALUE = HeaderValues.create(REQUEST_HEADER_NAME, REQUEST_HEADER_VALUE_STRING);
    private static final HeaderName RESPONSE_HEADER_NAME = HeaderNames.create("X-REsponSE-HeADER");
    private static final String RESPONSE_HEADER_VALUE_STRING = "another nice value";
    private static final Header RESPONSE_HEADER_VALUE = HeaderValues.create(RESPONSE_HEADER_NAME,
                                                                            RESPONSE_HEADER_VALUE_STRING);
    public static final Header CONTENT_LENGTH_5 = HeaderValues.create(CONTENT_LENGTH, "5");

    static {
        Random random = new Random();
        random.nextBytes(BYTES);
    }

    private final Http1Client client;

    GetTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.route(GET, "/string", Routes::string)
                .route(GET, "/bytes", Routes::bytes)
                .route(GET, "/chunked", Routes::chunked)
                .route(GET, "/headers", Routes::headers)
                .route(GET, "/close", Routes::close)
                .route(GET, "/optional", Routes::optional);
    }

    @Test
    void testStringRoute() {
        try (Http1ClientResponse response = client.get("/string")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            String entity = response.entity().as(String.class);
            assertThat(entity, is("Hello"));
            io.helidon.http.Headers headers = response.headers();
            assertThat(headers, hasHeader(CONTENT_LENGTH_5));
            assertThat(headers, hasHeader(HeaderValues.CONNECTION_KEEP_ALIVE));
        }
    }

    @Test
    void testByteRoute() {
        try (Http1ClientResponse response = client.get("/bytes")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            byte[] entity = response.entity().as(byte[].class);
            assertThat(entity, is(BYTES));
            io.helidon.http.Headers headers = response.headers();
            assertThat(headers, hasHeader(HeaderValues.create(CONTENT_LENGTH, String.valueOf(BYTES.length))));
            assertThat(headers, hasHeader(HeaderValues.CONNECTION_KEEP_ALIVE));
        }
    }

    @Test
    @Disabled("Optimization kicks in")
    void testChunkedRoute() {
        try (Http1ClientResponse response = client.get("/chunked")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            byte[] entity = response.entity().as(byte[].class);
            assertThat(entity, is(BYTES));
            io.helidon.http.Headers headers = response.headers();
            assertThat(headers, hasHeader(HeaderValues.TRANSFER_ENCODING_CHUNKED));
            assertThat(headers, hasHeader(HeaderValues.CONNECTION_KEEP_ALIVE));
        }

    }

    @Test
    void testHeadersRoute() {
        try (Http1ClientResponse response = client.get("/headers")
                .header(REQUEST_HEADER_VALUE)
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            String entity = response.entity().as(String.class);
            assertThat(entity, is("Hello"));
            io.helidon.http.Headers headers = response.headers();

            assertThat("Should contain echoed request header", headers, hasHeader(REQUEST_HEADER_VALUE));
            assertThat("Should contain configured response header", headers, hasHeader(RESPONSE_HEADER_VALUE));
            assertThat(headers, hasHeader(CONTENT_LENGTH_5));
            assertThat(headers, hasHeader(HeaderValues.CONNECTION_KEEP_ALIVE));
        }
    }

    @Test
    void testCloseRoute() {
        try (Http1ClientResponse response = client.get("/close")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            String entity = response.entity().as(String.class);
            assertThat(entity, is("Hello"));
            io.helidon.http.Headers headers = response.headers();
            assertThat(headers, hasHeader(CONTENT_LENGTH_5));
            assertThat(headers, hasHeader(HeaderValues.CONNECTION_CLOSE));
        }
    }

    @Test
    void testOptionalResponseWithValue() {
        try (Http1ClientResponse response = client.get("/optional")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            String entity = response.entity().as(String.class);
            assertThat(entity, is("return value"));
        }
    }

    @Test
    void testOptionalResponseEmpty() {
        try (Http1ClientResponse response = client.get("/optional")
                .queryParam("empty", "true")
                .request()) {

            assertThat(response.status(), is(Status.NOT_FOUND_404));
            assertThat(response.headers(), hasHeader(HeaderValues.CONTENT_LENGTH_ZERO));
        }
    }

    private static class Routes {
        public static void optional(ServerRequest req, ServerResponse res) {
            String empty = req.query().first("empty").orElse("false");

            if ("false".equals(empty)) {
                res.send(Optional.of("return value"));
            } else {
                res.send(Optional.empty());
            }
        }

        private static void close(ServerRequest req, ServerResponse res) {
            res.header(HeaderValues.CONNECTION_CLOSE);
            res.send("Hello");
        }

        private static String string() {
            return "Hello";
        }

        private static byte[] bytes() {
            return BYTES;
        }

        private static void chunked(ServerRequest req, ServerResponse res) {
            try (OutputStream outputStream = res.outputStream()) {
                outputStream.write(BYTES);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private static void headers(ServerRequest req, ServerResponse res) {
            res.header(req.headers().get(REQUEST_HEADER_NAME));
            res.header(RESPONSE_HEADER_VALUE);
            res.send("Hello");
        }
    }

}
