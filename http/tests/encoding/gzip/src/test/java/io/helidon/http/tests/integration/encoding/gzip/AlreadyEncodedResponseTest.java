/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.http.tests.integration.encoding.gzip;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientResponse;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http1.Http1Route;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@ServerTest
class AlreadyEncodedResponseTest {
    private static final String ACCEPT_ENCODING_VALUE = "br;q=1, gzip;q=0.8";
    private static final byte[] PRE_ENCODED_ENTITY = "already-brotli-encoded".getBytes(StandardCharsets.UTF_8);

    private final URI uri;
    private final Http1Client http1Client;
    private final Http2Client http2Client;
    private final HttpClient jdkClient;

    AlreadyEncodedResponseTest(URI uri, Http1Client http1Client, Http2Client http2Client) {
        this.uri = uri;
        this.http1Client = http1Client;
        this.http2Client = http2Client;
        this.jdkClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        builder.route(Http1Route.route(Method.GET,
                                       "/already-encoded-http1",
                                       (req, res) -> alreadyEncodedResponse(res)))
                .route(Http1Route.route(Method.GET,
                                        "/already-encoded-streamed-http1",
                                        (req, res) -> alreadyEncodedStreamedResponse(res)))
                .route(Http2Route.route(Method.GET,
                                        "/already-encoded-http2",
                                        (req, res) -> alreadyEncodedResponse(res)))
                .route(Http2Route.route(Method.GET,
                                        "/already-encoded-streamed-http2",
                                        (req, res) -> alreadyEncodedStreamedResponse(res)));
    }

    @Test
    void testAlreadyEncodedResponseIsNotGzipReencoded() throws IOException, InterruptedException {
        HttpResponse<byte[]> response = jdkClient.send(HttpRequest.newBuilder()
                                                               .header("Accept-Encoding", ACCEPT_ENCODING_VALUE)
                                                               .uri(uri.resolve("/already-encoded-http1"))
                                                               .build(),
                                                       HttpResponse.BodyHandlers.ofByteArray());

        assertAll(
                () -> assertThat(response.statusCode(), is(200)),
                () -> assertThat(response.headers().allValues("Content-Encoding"), is(List.of("br"))),
                () -> assertThat(response.body(), is(PRE_ENCODED_ENTITY))
        );
    }

    @Test
    void testHttp1ClientPassesThroughAlreadyEncodedResponse() {
        try (Http1ClientResponse response = http1Client.get("/already-encoded-http1")
                .header(HeaderNames.ACCEPT_ENCODING, ACCEPT_ENCODING_VALUE)
                .request()) {
            assertAll(
                    () -> assertThat(response.status(), is(Status.OK_200)),
                    () -> assertThat(response.headers().get(HeaderNames.CONTENT_ENCODING).allValues(), is(List.of("br"))),
                    () -> assertThat(response.as(byte[].class), is(PRE_ENCODED_ENTITY))
            );
        }
    }

    @Test
    void testHttp2ClientPassesThroughAlreadyEncodedResponse() {
        try (Http2ClientResponse response = http2Client.get("/already-encoded-http2")
                .header(HeaderNames.ACCEPT_ENCODING, ACCEPT_ENCODING_VALUE)
                .request()) {
            assertAll(
                    () -> assertThat(response.status(), is(Status.OK_200)),
                    () -> assertThat(response.headers().get(HeaderNames.CONTENT_ENCODING).allValues(), is(List.of("br"))),
                    () -> assertThat(response.as(byte[].class), is(PRE_ENCODED_ENTITY))
            );
        }
    }

    @Test
    void testStreamedAlreadyEncodedResponseIsNotGzipReencoded() throws IOException, InterruptedException {
        HttpResponse<byte[]> response = jdkClient.send(HttpRequest.newBuilder()
                                                               .header("Accept-Encoding", ACCEPT_ENCODING_VALUE)
                                                               .uri(uri.resolve("/already-encoded-streamed-http1"))
                                                               .build(),
                                                       HttpResponse.BodyHandlers.ofByteArray());

        assertAll(
                () -> assertThat(response.statusCode(), is(200)),
                () -> assertThat(response.headers().allValues("Content-Encoding"), is(List.of("br"))),
                () -> assertThat(response.body(), is(PRE_ENCODED_ENTITY))
        );
    }

    @Test
    void testHttp1ClientStreamsAlreadyEncodedResponse() throws IOException {
        try (Http1ClientResponse response = http1Client.get("/already-encoded-streamed-http1")
                .header(HeaderNames.ACCEPT_ENCODING, ACCEPT_ENCODING_VALUE)
                .request()) {
            assertStreamedPassThrough(response);
        }
    }

    @Test
    void testHttp2ClientStreamsAlreadyEncodedResponse() throws IOException {
        try (Http2ClientResponse response = http2Client.get("/already-encoded-streamed-http2")
                .header(HeaderNames.ACCEPT_ENCODING, ACCEPT_ENCODING_VALUE)
                .request()) {
            assertStreamedPassThrough(response);
        }
    }

    private static void alreadyEncodedResponse(ServerResponse res) {
        res.status(Status.OK_200);
        res.header(HeaderValues.create(HeaderNames.CONTENT_ENCODING, "br"));
        res.send(PRE_ENCODED_ENTITY);
    }

    private static void alreadyEncodedStreamedResponse(ServerResponse res) throws IOException {
        res.status(Status.OK_200);
        res.header(HeaderValues.create(HeaderNames.CONTENT_ENCODING, "br"));
        try (var outputStream = res.outputStream()) {
            outputStream.write(PRE_ENCODED_ENTITY, 0, 7);
            outputStream.write(PRE_ENCODED_ENTITY, 7, PRE_ENCODED_ENTITY.length - 7);
        }
    }

    private static void assertStreamedPassThrough(HttpClientResponse response) throws IOException {
        assertAll(
                () -> assertThat(response.status(), is(Status.OK_200)),
                () -> assertThat(response.headers().get(HeaderNames.CONTENT_ENCODING).allValues(), is(List.of("br"))),
                () -> assertThat(readInChunks(response.inputStream()), is(PRE_ENCODED_ENTITY))
        );
    }

    private static byte[] readInChunks(InputStream inputStream) throws IOException {
        try (inputStream) {
            byte[] firstChunk = inputStream.readNBytes(7);
            byte[] remaining = inputStream.readAllBytes();
            byte[] result = new byte[firstChunk.length + remaining.length];
            System.arraycopy(firstChunk, 0, result, 0, firstChunk.length);
            System.arraycopy(remaining, 0, result, firstChunk.length, remaining.length);
            return result;
        }
    }
}
