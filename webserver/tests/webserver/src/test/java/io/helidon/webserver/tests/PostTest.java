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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Random;

import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static io.helidon.http.HeaderNames.CONTENT_LENGTH;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ServerTest
class PostTest {
    private static final byte[] BYTES = new byte[256];
    private static final HeaderName REQUEST_HEADER_NAME = HeaderNames.create("X-REquEst-HEADeR");
    private static final String REQUEST_HEADER_VALUE_STRING = "some nice value";
    private static final Header REQUEST_HEADER_VALUE = HeaderValues.create(REQUEST_HEADER_NAME, REQUEST_HEADER_VALUE_STRING);
    private static final HeaderName RESPONSE_HEADER_NAME = HeaderNames.create("X-REsponSE-HeADER");
    private static final String RESPONSE_HEADER_VALUE_STRING = "another nice value";
    private static final Header RESPONSE_HEADER_VALUE = HeaderValues.create(RESPONSE_HEADER_NAME,
                                                                            RESPONSE_HEADER_VALUE_STRING);

    static {
        Random random = new Random();
        random.nextBytes(BYTES);
    }

    private final Http1Client client;

    PostTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.route(Method.POST, "/string", Handler.create(String.class, Routes::string))
                .route(Method.POST, "/bytes", Handler.create(byte[].class, Routes::bytes))
                .route(Method.POST, "/chunked", Routes::chunked)
                .route(Method.POST, "/headers", Routes::headers)
                .route(Method.POST, "/close", Routes::close);
    }

    @Test
    void testStringRoute() {
        io.helidon.http.Headers headers;
        try (Http1ClientResponse response = client.method(Method.POST)
                .uri("/string")
                .submit("Hello")) {

            assertThat(response.status(), is(Status.OK_200));
            String entity = response.entity().as(String.class);
            assertThat(entity, is("Hello"));
            headers = response.headers();
        }
        assertThat(headers, hasHeader(HeaderValues.create(CONTENT_LENGTH, "5")));
        assertThat(headers, hasHeader(HeaderValues.CONNECTION_KEEP_ALIVE));
    }

    @Test
    void testByteRoute() {
        io.helidon.http.Headers headers;
        try (Http1ClientResponse response = client.method(Method.POST)
                .uri("/bytes")
                .submit(BYTES)) {

            assertThat(response.status(), is(Status.OK_200));
            byte[] entity = response.entity().as(byte[].class);
            assertThat(entity, is(BYTES));
            headers = response.headers();
        }
        assertThat(headers, hasHeader(HeaderValues.create(CONTENT_LENGTH, String.valueOf(BYTES.length))));
        assertThat(headers, hasHeader(HeaderValues.CONNECTION_KEEP_ALIVE));
    }

    @Test
    @Disabled("Optimization kicks in")
    void testChunkedRoute() {
        io.helidon.http.Headers headers;
        try (Http1ClientResponse response = client.method(Method.POST)
                .uri("/chunked")
                .outputStream(outputStream -> {
                    outputStream.write(BYTES);
                    outputStream.close();
                })) {

            assertThat(response.status(), is(Status.OK_200));
            byte[] entity = response.entity().as(byte[].class);
            assertThat(entity, is(BYTES));
            headers = response.headers();
        }
        assertThat(headers, hasHeader(HeaderValues.TRANSFER_ENCODING_CHUNKED));
        assertThat(headers, hasHeader(HeaderValues.CONNECTION_KEEP_ALIVE));
    }

    @Test
    void testHeadersRoute() {
        io.helidon.http.Headers headers;
        try (Http1ClientResponse response = client.method(Method.POST)
                .uri("/headers")
                .header(REQUEST_HEADER_VALUE)
                .submit("Hello")) {

            assertThat(response.status(), is(Status.OK_200));
            String entity = response.entity().as(String.class);
            assertThat(entity, is("Hello"));
            headers = response.headers();
        }
        assertThat(headers, hasHeader(HeaderValues.create(CONTENT_LENGTH, "5")));
        assertThat(headers, hasHeader(HeaderValues.CONNECTION_KEEP_ALIVE));
        assertThat(headers, hasHeader(REQUEST_HEADER_VALUE));
        assertThat(headers, hasHeader(RESPONSE_HEADER_VALUE));
    }

    @Test
    void testCloseRoute() {
        io.helidon.http.Headers headers;
        try (Http1ClientResponse response = client.method(Method.POST)
                .uri("/close")
                .submit("Hello")) {

            assertThat(response.status(), is(Status.NO_CONTENT_204));
            assertThrows(IllegalStateException.class, () -> response.entity().as(String.class));
            headers = response.headers();
        }
        assertThat(headers, hasHeader(HeaderValues.CONNECTION_CLOSE));
    }

    private static class Routes {
        public static void close(ServerRequest req, ServerResponse res) {
            res.header(HeaderValues.CONNECTION_CLOSE);
            res.status(Status.NO_CONTENT_204);
            res.send();
        }

        private static String string(String request) {
            return request;
        }

        private static byte[] bytes(byte[] bytes) {
            return bytes;
        }

        private static void chunked(ServerRequest req, ServerResponse res) {
            byte[] buffer = new byte[512];
            try (InputStream inputStream = req.content().inputStream(); OutputStream outputStream = res.outputStream()) {
                int read;
                while ((read = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, read);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private static void headers(ServerRequest req, ServerResponse res) {
            System.out.println("Headers");
            res.header(req.headers().get(REQUEST_HEADER_NAME));
            res.header(RESPONSE_HEADER_VALUE);
            String entity = req.content().as(String.class);
            res.send(entity);
        }
    }

}
