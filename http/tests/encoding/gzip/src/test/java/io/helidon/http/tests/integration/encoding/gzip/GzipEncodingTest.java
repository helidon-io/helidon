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

package io.helidon.http.tests.integration.encoding.gzip;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http1.Http1Route;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@ServerTest
class GzipEncodingTest {
    private static final String ENTITY = "Some arbitrary text we want to try to compress";
    private static final byte[] GZIP_ENTITY;
    private static final Header CONTENT_ENCODING_GZIP = HeaderValues.create(HeaderNames.CONTENT_ENCODING, "gzip");

    static {
        ByteArrayOutputStream baos;
        try {
            baos = new ByteArrayOutputStream();
            OutputStream os = new GZIPOutputStream(baos);
            os.write(ENTITY.getBytes(StandardCharsets.UTF_8));
            os.close();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create gzipped bytes", e);
        }
        GZIP_ENTITY = baos.toByteArray();
    }

    private final URI uri;
    private final Http1Client http1Client;
    private final Http2Client http2Client;
    private final HttpClient client;

    GzipEncodingTest(URI uri, Http1Client http1Client, Http2Client http2Client) {
        this.uri = uri;
        this.http1Client = http1Client;
        this.http2Client = http2Client;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        builder.route(Http1Route.route(Method.PUT,
                                       "/http1",
                                       (req, res) -> {
                                           String entity = req.content().as(String.class);
                                           if (!ENTITY.equals(entity)) {
                                               res.status(Status.INTERNAL_SERVER_ERROR_500).send("Wrong data");
                                           } else {
                                               res.send(entity);
                                           }
                                       }))
                .route(Http2Route.route(Method.PUT,
                                        "/http2",
                                        (req, res) -> {
                                            String entity = req.content().as(String.class);
                                            if (!ENTITY.equals(entity)) {
                                                res.status(Status.INTERNAL_SERVER_ERROR_500).send("Wrong data");
                                            } else {
                                                res.send(entity);
                                            }
                                        }))
                .route(Http1Route.route(Method.GET,
                                        "/chunked",
                                        (req, res) -> {
                                            res.header(HeaderValues.TRANSFER_ENCODING_CHUNKED);
                                            res.send(ENTITY);
                                        }));
    }

    @Test
    void testGzipJdkClient() throws IOException, InterruptedException {
        testIt("gzip");
    }


    @Test
    void testGzipHttp1Client() {
        testIt(http1Client, "/http1", "gzip");
    }

    @Test
    void testGzipHttp2Client() throws IOException {
        testIt(http2Client, "/http2", "gzip");
    }

    @Test
    void testGzipMultipleAcceptedEncodingsJdkClient() throws IOException, InterruptedException {
        testIt("br;q=0.9, gzip, *;q=0.1");
    }

    @Test
    void testDeflateMultipleAcceptedEncodingsHttp1Client() {
        testIt(http1Client, "/http1", "br;q=0.9, gzip, *;q=0.1");
    }

    @Test
    void testDeflateMultipleAcceptedEncodingsHttp2Client() {
        testIt(http2Client, "/http2", "br;q=0.9, gzip, *;q=0.1");
    }

    @Test
    void testGzipWithChunkedTransferEncoding() {
        ClientResponseTyped<String> response = http1Client.get("/chunked")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip")
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is(ENTITY));
        assertThat(response.headers(), hasHeader(CONTENT_ENCODING_GZIP));
        assertThat(response.headers(), hasHeader(HeaderValues.TRANSFER_ENCODING_CHUNKED));
    }

    void testIt(io.helidon.webclient.api.HttpClient<?> client, String path, String acceptEncodingValue) {
        ClientResponseTyped<String> response = client.put(path)
                .header(HeaderNames.ACCEPT_ENCODING, acceptEncodingValue)
                .header(CONTENT_ENCODING_GZIP)
                .submit(GZIP_ENTITY, String.class);

        Assertions.assertAll(
                () -> assertThat(response.status(), is(Status.OK_200)),
                () -> assertThat(response.entity(), is(ENTITY)),
                () -> assertThat(response.headers(), hasHeader(CONTENT_ENCODING_GZIP))
        );
    }

    void testIt(String acceptEncodingValue) throws IOException, InterruptedException {
        HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder()
                                                            .PUT(HttpRequest.BodyPublishers.ofByteArray(GZIP_ENTITY))
                                                            .header("Accept-Encoding", acceptEncodingValue)
                                                            .headers("Content-Encoding", "gzip")
                                                            .uri(uri.resolve("/http1"))
                                                            .build(),
                                                    HttpResponse.BodyHandlers.ofByteArray());

        byte[] bytes = response.body();
        String responseEntity;
        try {
            GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(bytes));
            responseEntity = new String(gzipInputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            fail("Failed to read gzip response. Entity: " + new String(bytes), e);
            return;
        }

        Assertions.assertAll(
                () -> assertThat(response.statusCode(), is(200)),
                () -> assertThat(responseEntity, is(ENTITY)),
                () -> assertThat(response.headers().firstValue("Content-Encoding"), is(Optional.of("gzip")))
        );
    }
}
