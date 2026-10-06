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

package io.helidon.webserver.tests.http2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.encoding.deflate.DeflateEncoding;
import io.helidon.http.encoding.gzip.GzipEncoding;
import io.helidon.http.media.multipart.MultiPart;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientProtocolConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.helidon.http.Method.GET;
import static io.helidon.http.Method.POST;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ServerTest
class MaterializedEntityLimitTest {
    private static final int DEFAULT_LIMIT = 1024 * 1024;
    private static final byte[] LARGE_RESPONSE = new byte[DEFAULT_LIMIT + 1];

    private final Http2Client client;

    MaterializedEntityLimitTest(WebServer server) {
        this.client = Http2Client.builder()
                .baseUri(URI.create("http://localhost:" + server.port()))
                .protocolConfig(Http2ClientProtocolConfig.builder().priorKnowledge(true).build())
                .shareConnectionCache(false)
                .build();
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        server.addProtocol(Http2Config.builder().build())
                .contentEncoding(ContentEncodingContext.builder()
                        .addContentEncoding(GzipEncoding.create())
                        .addContentEncoding(DeflateEncoding.create())
                        .build());
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder rules) {
        rules.route(Http2Route.route(POST, "/bytes", (req, res) -> {
                    req.content().as(byte[].class);
                    res.send();
                }))
                .route(Http2Route.route(POST, "/stream", (req, res) -> {
                    try (InputStream stream = req.content().as(InputStream.class)) {
                        res.send(Long.toString(stream.transferTo(OutputStream.nullOutputStream())));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }))
                .route(Http2Route.route(POST, "/multipart", (req, res) -> {
                    MultiPart parts = req.content().as(MultiPart.class);
                    long count = 0;
                    while (parts.hasNext()) {
                        try (InputStream stream = parts.next().inputStream()) {
                            count += stream.transferTo(OutputStream.nullOutputStream());
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                    res.send(Long.toString(count));
                }))
                .route(Http2Route.route(GET, "/response", (_, res) -> res.send(LARGE_RESPONSE)));
    }

    @AfterEach
    void cleanup() {
        client.closeResource();
    }

    @Test
    void exactlyOneMebibyteIsAccepted() {
        try (var response = client.post("/bytes").submit(new byte[DEFAULT_LIMIT])) {
            assertThat(response.status(), is(Status.OK_200));
        }
    }

    @Test
    void oneByteAboveTheDefaultIsRejected() {
        try (var response = client.post("/bytes").submit(new byte[DEFAULT_LIMIT + 1])) {
            assertThat(response.status(), is(Status.REQUEST_ENTITY_TOO_LARGE_413));
        }
    }

    @Test
    void streamingLargerThanTheDefaultRemainsUsable() {
        byte[] bytes = new byte[DEFAULT_LIMIT + 1];
        try (var response = client.post("/stream").submit(bytes)) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is(Integer.toString(bytes.length)));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"gzip", "deflate"})
    void compressedMultipartRemainsStreamed(String encoding) throws IOException {
        String first = "A".repeat(DEFAULT_LIMIT + 1);
        String second = "B".repeat(31);
        String multipart = "--boundary\r\nContent-Disposition: form-data; name=first\r\n\r\n"
                + first + "\r\n--boundary\r\nContent-Disposition: form-data; name=second\r\n\r\n"
                + second + "\r\n--boundary--\r\n";
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (OutputStream encoder = switch (encoding) {
            case "gzip" -> new GZIPOutputStream(compressed);
            case "deflate" -> new DeflaterOutputStream(compressed);
            default -> throw new IllegalArgumentException("Unexpected encoding: " + encoding);
        }) {
            encoder.write(multipart.getBytes(StandardCharsets.US_ASCII));
        }

        try (var response = client.post("/multipart")
                .header(HeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=boundary")
                .header(HeaderNames.CONTENT_ENCODING, encoding)
                .submit(compressed.toByteArray())) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is(Integer.toString(first.length() + second.length())));
        }
    }

    @ParameterizedTest
    @ValueSource(classes = {byte[].class, String.class})
    void clientMaterializationUsesTheSameDefault(Class<?> type) {
        try (var response = client.get("/response").request()) {
            assertThat(response.status(), is(Status.OK_200));
            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> response.entity().as(type));
            assertThat(exception.getMessage(), containsString("Maximum materialized entity size exceeded"));
            assertThat(response.status(), is(Status.OK_200));
        }
    }
}
