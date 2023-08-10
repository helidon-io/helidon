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
import io.helidon.common.http.Http.HeaderName;
import io.helidon.common.http.Http.HeaderNames;
import io.helidon.nima.http2.webserver.Http2Route;
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
class GetTest {
    private static final byte[] BYTES = new byte[256];
    private static final String REQUEST_HEADER_NAME_STRING = "X-REquEst-HEADeR";
    private static final String REQUEST_HEADER_VALUE_STRING = "some nice value";
    private static final String RESPONSE_HEADER_NAME_STRING = "X-REsponSE-HeADER";
    private static final String RESPONSE_HEADER_VALUE_STRING = "another nice value";
    private static final HeaderName REQUEST_HEADER_NAME = Http.HeaderNames.create(REQUEST_HEADER_NAME_STRING);
    private static final HeaderName RESPONSE_HEADER_NAME = HeaderNames.create(RESPONSE_HEADER_NAME_STRING);
    private static final Http.Header RESPONSE_HEADER_VALUE = Http.Headers.createCached(RESPONSE_HEADER_NAME,
                                                                                       RESPONSE_HEADER_VALUE_STRING);

    static {
        Random random = new Random();
        random.nextBytes(BYTES);
    }

    private final HttpClient httpClient;
    private final URI uri;

    public GetTest(URI uri) {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.uri = uri;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        // enforce http/2 so we know if upgrade failed
        router.route(Http2Route.route(Http.Method.GET, "/string", Handler.create(Routes::string)))
                .route(Http.Method.GET, "/bytes", Routes::bytes)
                .route(Http.Method.GET, "/chunked", Routes::chunked)
                .route(Http.Method.GET, "/headers", Routes::headers);
    }

    @Test
    void testStringRoute() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(uri.resolve("/string"))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode(), is(Http.Status.OK_200.code()));
        assertThat(response.body(), is("Hello"));

        java.net.http.HttpHeaders headers = response.headers();
        assertThat(headers.firstValueAsLong(HeaderNames.CONTENT_LENGTH.defaultCase()),
                   is(OptionalLong.of(5)));
    }

    @Test
    void testByteRoute() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(uri.resolve("/bytes"))
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode(), is(Http.Status.OK_200.code()));
        assertThat(response.body(), is(BYTES));
        java.net.http.HttpHeaders headers = response.headers();
        assertThat(headers.firstValueAsLong(HeaderNames.CONTENT_LENGTH.defaultCase()),
                   is(OptionalLong.of(BYTES.length)));
    }

    @Test
    void testChunkedRoute() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(uri.resolve("/chunked"))
                .timeout(Duration.ofSeconds(5))
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode(), is(Http.Status.OK_200.code()));
        assertThat(response.body(), is(BYTES));
        java.net.http.HttpHeaders headers = response.headers();
        assertThat(headers.firstValueAsLong(HeaderNames.CONTENT_LENGTH.defaultCase()),
                   is(OptionalLong.of(BYTES.length)));
    }

    @Test
    void testHeadersRoute() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(uri.resolve("/headers"))
                .header(REQUEST_HEADER_NAME_STRING, REQUEST_HEADER_VALUE_STRING)
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode(), is(Http.Status.OK_200.code()));
        assertThat(response.body(), is("Hello"));

        java.net.http.HttpHeaders headers = response.headers();
        assertThat(headers.firstValueAsLong(Http.HeaderNames.CONTENT_LENGTH.defaultCase()),
                   is(OptionalLong.of(5)));
        assertThat("Should contain echoed request header",
                   headers.firstValue(REQUEST_HEADER_NAME_STRING), is(Optional.of(REQUEST_HEADER_VALUE_STRING)));
        assertThat("Should contain response header",
                   headers.firstValue(RESPONSE_HEADER_NAME_STRING), is(Optional.of(RESPONSE_HEADER_VALUE_STRING)));
    }

    private static class Routes {
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
