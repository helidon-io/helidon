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

package io.helidon.nima.tests.integration.http2.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.OptionalLong;
import java.util.Random;

import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.Header;
import io.helidon.common.http.Http.HeaderName;
import io.helidon.common.http.Http.HeaderValue;
import io.helidon.nima.http2.webclient.Http2Client;
import io.helidon.nima.http2.webclient.Http2ClientResponse;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.Handler;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class PostTest {
    private static final byte[] BYTES = new byte[256];
    private static final HeaderName REQUEST_HEADER_NAME = Header.create("X-REquEst-HEADeR");
    private static final String REQUEST_HEADER_VALUE_STRING = "some nice value";
    private static final HeaderValue REQUEST_HEADER_VALUE = Header.createCached(REQUEST_HEADER_NAME, REQUEST_HEADER_VALUE_STRING);
    private static final HeaderName RESPONSE_HEADER_NAME = Header.create("X-REsponSE-HeADER");
    private static final String RESPONSE_HEADER_VALUE_STRING = "another nice value";
    private static final HeaderValue RESPONSE_HEADER_VALUE = Header.create(RESPONSE_HEADER_NAME,
                                                                           RESPONSE_HEADER_VALUE_STRING);

    private static WebServer server;
    private static Http2Client client;

    static {
        Random random = new Random();
        random.nextBytes(BYTES);
    }

    @BeforeAll
    static void startServer() {
        server = WebServer.builder()
                .host("localhost")
                .port(-1)
                .routing(routing -> routing
                        .route(Http.Method.POST, "/string", Handler.create(String.class, Routes::string))
                        .route(Http.Method.POST, "/bytes", Handler.create(byte[].class, Routes::bytes))
                        .route(Http.Method.POST, "/chunked", Routes::chunked)
                        .route(Http.Method.POST, "/headers", Routes::headers)
                        .route(Http.Method.POST, "/close", Routes::close)
                )
                .build()
                .start();

        client = Http2Client.builder()
                .baseUri("http://localhost:" + server.port())
                .protocolConfig(it -> it.priorKnowledge(true))
                .build();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void testStringRoute() {
        Http2ClientResponse response = client
                .method(Http.Method.POST)
                .uri("/string")
                .submit("Hello");

        assertThat(response.status(), is(Http.Status.OK_200));
        String entity = response.entity().as(String.class);
        assertThat(entity, is("Hello"));
        Headers headers = response.headers();
        assertThat("Should have correct length", headers.contentLength(), is(OptionalLong.of(5)));
    }

    @Test
    void testByteRoute() {
        Http2ClientResponse response = client
                .method(Http.Method.POST)
                .uri("/bytes")
                .submit(BYTES);

        assertThat(response.status(), is(Http.Status.OK_200));
        byte[] entity = response.entity().as(byte[].class);
        assertThat(entity, is(BYTES));
        Headers headers = response.headers();
        assertThat(headers.contentLength(), is(OptionalLong.of(BYTES.length)));
    }

    @Test
    void testChunkedRoute() {
        Http2ClientResponse response = client
                .method(Http.Method.POST)
                .uri("/chunked")
                .outputStream(outputStream -> {
                    outputStream.write(BYTES);
                    outputStream.close();
                });

        assertThat(response.status(), is(Http.Status.OK_200));
        byte[] entity = response.entity().as(byte[].class);
        assertThat(entity, is(BYTES));
    }

    @Test
    void testHeadersRoute() {
        Http2ClientResponse response = client
                .method(Http.Method.POST)
                .uri("/headers")
                .header(REQUEST_HEADER_VALUE)
                .submit("Hello");

        assertThat(response.status(), is(Http.Status.OK_200));
        String entity = response.entity().as(String.class);
        assertThat(entity, is("Hello"));
        Headers headers = response.headers();
        assertThat(headers.contentLength(), is(OptionalLong.of(5)));
        assertThat(headers, hasHeader(REQUEST_HEADER_VALUE));
        assertThat(headers, hasHeader(RESPONSE_HEADER_VALUE));
    }

    @Test
    void testCloseRoute() {
        Http2ClientResponse response = client
                .method(Http.Method.POST)
                .uri("/close")
                .submit("Hello");

        assertThat(response.status(), is(Http.Status.NO_CONTENT_204));
        String entity = response.entity().as(String.class);
        assertThat(entity, is(""));
    }

    private static class Routes {
        public static void close(ServerRequest req, ServerResponse res) {
            res.status(Http.Status.NO_CONTENT_204);
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
            res.header(req.headers().get(REQUEST_HEADER_NAME));
            res.header(RESPONSE_HEADER_VALUE);
            res.send(req.content().as(String.class));
        }
    }

}
