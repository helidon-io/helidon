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

package io.helidon.nima.tests.integration.http2.webserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Random;

import io.helidon.common.http.Http;
import io.helidon.common.http.Http.Header;
import io.helidon.common.http.Http.HeaderName;
import io.helidon.common.http.Http.HeaderValue;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webserver.http.Handler;
import io.helidon.nima.webserver.http.HttpRouting;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class PostTest {
    private static final byte[] BYTES = new byte[256];
    private static final HeaderName REQUEST_HEADER_NAME = Header.create("X-REquEst-HEADeR");
    private static final String REQUEST_HEADER_VALUE_STRING = "some nice value";
    private static final HeaderValue REQUEST_HEADER_VALUE = Header.create(REQUEST_HEADER_NAME, REQUEST_HEADER_VALUE_STRING);
    private static final HeaderName RESPONSE_HEADER_NAME = Header.create("X-REsponSE-HeADER");
    private static final String RESPONSE_HEADER_VALUE_STRING = "another nice value";
    private static final HeaderValue RESPONSE_HEADER_VALUE = Header.create(RESPONSE_HEADER_NAME,
                                                                           RESPONSE_HEADER_VALUE_STRING);

    static {
        Random random = new Random();
        random.nextBytes(BYTES);
    }

    private final HttpClient client;
    private final URI uri;

    PostTest(URI uri) {
        this.uri = uri;
        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.route(Http.Method.POST, "/string", Handler.create(String.class, Routes::string))
                .route(Http.Method.POST, "/bytes", Handler.create(byte[].class, Routes::bytes))
                .route(Http.Method.POST, "/streamed", Routes::streamed)
                .route(Http.Method.POST, "/headers", Routes::headers)
                .route(Http.Method.POST, "/nocontent", Routes::noContent);
    }

    @Test
    void testStringRoute() throws IOException, InterruptedException {
        // do a head request (will get 404) to upgrade
        client.send(HttpRequest.newBuilder()
                            .HEAD()
                            .uri(uri.resolve("/string"))
                            .build(), HttpResponse.BodyHandlers.discarding());

        HttpResponse<String> response = client.send(HttpRequest.newBuilder()
                                                            .timeout(Duration.ofSeconds(5))
                                                            .uri(uri.resolve("/string"))
                                                            .POST(HttpRequest.BodyPublishers.ofString("Hello"))
                                                            .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode(), is(Http.Status.OK_200.code()));
        String entity = response.body();
        assertThat(entity, is("Hello"));
        java.net.http.HttpHeaders headers = response.headers();
        assertThat(headers.firstValueAsLong("content-length"), is(OptionalLong.of(5)));
        assertThat(response.version(), is(HttpClient.Version.HTTP_2));
    }

    @Test
    void testByteRoute() throws IOException, InterruptedException {
        // do a head request (will get 404) to upgrade
        client.send(HttpRequest.newBuilder()
                            .HEAD()
                            .uri(uri.resolve("/string"))
                            .build(), HttpResponse.BodyHandlers.discarding());

        HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder()
                                                            .timeout(Duration.ofSeconds(5))
                                                            .uri(uri.resolve("/bytes"))
                                                            .POST(HttpRequest.BodyPublishers.ofByteArray(BYTES))
                                                            .build(), HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode(), is(Http.Status.OK_200.code()));
        byte[] entity = response.body();
        assertThat(entity, is(BYTES));
        java.net.http.HttpHeaders headers = response.headers();
        assertThat(headers.firstValueAsLong("content-length"), is(OptionalLong.of(BYTES.length)));
        assertThat(response.version(), is(HttpClient.Version.HTTP_2));
    }

    @Test
    void testStreamedRoute() throws IOException, InterruptedException {
        // do a head request (will get 404) to upgrade
        client.send(HttpRequest.newBuilder()
                            .HEAD()
                            .uri(uri.resolve("/string"))
                            .build(), HttpResponse.BodyHandlers.discarding());

        HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder()
                                                            .timeout(Duration.ofSeconds(5))
                                                            .uri(uri.resolve("/streamed"))
                                                            .POST(HttpRequest.BodyPublishers.ofByteArray(BYTES))
                                                            .build(), HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode(), is(Http.Status.OK_200.code()));
        byte[] entity = response.body();
        assertThat(entity, is(BYTES));
        java.net.http.HttpHeaders headers = response.headers();
        assertThat(headers.firstValueAsLong("content-length"), is(OptionalLong.of(BYTES.length)));
        assertThat(response.version(), is(HttpClient.Version.HTTP_2));
    }

    @Test
    void testHeadersRoute() throws IOException, InterruptedException {
        // do a head request (will get 404) to upgrade
        client.send(HttpRequest.newBuilder()
                            .HEAD()
                            .uri(uri.resolve("/string"))
                            .build(), HttpResponse.BodyHandlers.discarding());

        HttpResponse<String> response = client.send(HttpRequest.newBuilder()
                                                            .timeout(Duration.ofSeconds(5))
                                                            .uri(uri.resolve("/headers"))
                                                            .header(REQUEST_HEADER_NAME.lowerCase(),
                                                                    REQUEST_HEADER_VALUE_STRING)
                                                            .POST(HttpRequest.BodyPublishers.ofString("Hello"))
                                                            .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode(), is(Http.Status.OK_200.code()));
        String entity = response.body();
        assertThat(entity, is("Hello"));
        java.net.http.HttpHeaders headers = response.headers();
        assertThat(headers.firstValueAsLong("content-length"), is(OptionalLong.of(5)));
        assertThat("Should contain echoed request header",
                   headers.firstValue(REQUEST_HEADER_NAME.lowerCase()),
                   is(Optional.of(REQUEST_HEADER_VALUE_STRING)));
        assertThat("Should contain configured response header",
                   headers.firstValue(RESPONSE_HEADER_NAME.lowerCase()),
                   is(Optional.of(RESPONSE_HEADER_VALUE_STRING)));
        assertThat(response.version(), is(HttpClient.Version.HTTP_2));
    }

    @Test
    void testNoContentRoute() throws IOException, InterruptedException {
        // do a head request (will get 404) to upgrade
        client.send(HttpRequest.newBuilder()
                            .HEAD()
                            .uri(uri.resolve("/nocontent"))
                            .build(), HttpResponse.BodyHandlers.discarding());

        HttpResponse<String> response = client.send(HttpRequest.newBuilder()
                                                            .timeout(Duration.ofSeconds(5))
                                                            .uri(uri.resolve("/nocontent"))
                                                            .header(REQUEST_HEADER_NAME.lowerCase(),
                                                                    REQUEST_HEADER_VALUE_STRING)
                                                            .POST(HttpRequest.BodyPublishers.ofString("Hello"))
                                                            .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode(), is(Http.Status.NO_CONTENT_204.code()));
    }

    private static class Routes {
        public static void noContent(ServerRequest req, ServerResponse res) {
            res.status(Http.Status.NO_CONTENT_204);
            res.send();
        }

        private static String string(String request) {
            return request;
        }

        private static byte[] bytes(byte[] bytes) {
            return bytes;
        }

        private static void streamed(ServerRequest req, ServerResponse res) {
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
            String entity = req.content().as(String.class);
            res.send(entity);
        }
    }

}
