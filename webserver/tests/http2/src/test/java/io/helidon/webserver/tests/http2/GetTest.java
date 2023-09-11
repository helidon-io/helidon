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

package io.helidon.webserver.tests.http2;

import java.io.IOException;
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

import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class GetTest {
    private static final byte[] BYTES = new byte[256];
    private static final HeaderName REQUEST_HEADER_NAME = HeaderNames.create("X-ReQUEst-header");
    private static final String REQUEST_HEADER_VALUE = "some nice value";
    private static final HeaderName RESPONSE_HEADER_NAME = HeaderNames.create("X-REsponSE-HeADER");
    private static final String RESPONSE_HEADER_VALUE_STRING = "another nice value";
    private static final Header RESPONSE_HEADER_VALUE = HeaderValues.create(RESPONSE_HEADER_NAME,
                                                                            RESPONSE_HEADER_VALUE_STRING);

    static {
        Random random = new Random();
        random.nextBytes(BYTES);
    }

    private final HttpClient client;
    private final URI uri;

    GetTest(URI uri) {
        this.uri = uri;
        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.route(Method.GET, "/string", Routes::string)
                .route(Method.GET, "/bytes", Routes::bytes)
                .route(Method.GET, "/stream", Routes::outputStream)
                .route(Method.GET, "/headers", Routes::headers);
    }

    @Test
    void testStringRoute() throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder()
                                                            .timeout(Duration.ofSeconds(5))
                                                            .uri(uri.resolve("/string"))
                                                            .GET()
                                                            .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode(), is(Status.OK_200.code()));
        String entity = response.body();
        assertThat(entity, is("Hello"));
        java.net.http.HttpHeaders headers = response.headers();
        assertThat(headers.firstValueAsLong("content-length"), is(OptionalLong.of(5)));
        assertThat(response.version(), is(HttpClient.Version.HTTP_2));
    }

    @Test
    void testByteRoute() throws IOException, InterruptedException {
        HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder()
                                                            .timeout(Duration.ofSeconds(5))
                                                            .uri(uri.resolve("/bytes"))
                                                            .GET()
                                                            .build(), HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode(), is(Status.OK_200.code()));
        byte[] entity = response.body();
        assertThat(entity, is(BYTES));
        java.net.http.HttpHeaders headers = response.headers();
        assertThat(headers.firstValueAsLong("content-length"), is(OptionalLong.of(BYTES.length)));
        assertThat(response.version(), is(HttpClient.Version.HTTP_2));
    }

    @Test
    void testStreamRoute() throws IOException, InterruptedException {
        HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder()
                                                            .timeout(Duration.ofSeconds(5))
                                                            .uri(uri.resolve("/stream"))
                                                            .GET()
                                                            .build(), HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode(), is(Status.OK_200.code()));
        byte[] entity = response.body();
        assertThat(entity, is(BYTES));
        java.net.http.HttpHeaders headers = response.headers();
        assertThat(headers.firstValueAsLong("content-length"), is(OptionalLong.of(BYTES.length)));
        assertThat(response.version(), is(HttpClient.Version.HTTP_2));
    }

    @Test
    void testHeadersRoute() throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder()
                                                            .timeout(Duration.ofSeconds(5))
                                                            .uri(uri.resolve("/headers"))
                                                            .header(REQUEST_HEADER_NAME.lowerCase(),
                                                                    REQUEST_HEADER_VALUE)
                                                            .GET()
                                                            .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode(), is(Status.OK_200.code()));
        String entity = response.body();
        assertThat(entity, is("Hello"));
        java.net.http.HttpHeaders headers = response.headers();
        assertThat(headers.firstValueAsLong("content-length"), is(OptionalLong.of(5)));
        assertThat("Should contain echoed request header",
                   headers.firstValue(REQUEST_HEADER_NAME.lowerCase()),
                   is(Optional.of(REQUEST_HEADER_VALUE)));
        assertThat("Should contain configured response header",
                   headers.firstValue(RESPONSE_HEADER_NAME.lowerCase()),
                   is(Optional.of(RESPONSE_HEADER_VALUE_STRING)));
        assertThat(response.version(), is(HttpClient.Version.HTTP_2));
    }

    private static class Routes {
        private static String string() {
            return "Hello";
        }

        private static byte[] bytes() {
            return BYTES;
        }

        private static void outputStream(ServerRequest req, ServerResponse res) {
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
