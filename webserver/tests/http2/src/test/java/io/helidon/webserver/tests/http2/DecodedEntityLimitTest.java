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

import io.helidon.common.Functions.CheckedFunction;
import io.helidon.common.Size;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.encoding.deflate.DeflateEncoding;
import io.helidon.http.encoding.gzip.GzipEncoding;
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

import static io.helidon.http.Method.GET;
import static io.helidon.http.Method.POST;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class DecodedEntityLimitTest {
    private static final long MAX_PAYLOAD_SIZE = 2048;
    private static final long MAX_BUFFERED_ENTITY_SIZE = 128;
    private static final String PAYLOAD = "A".repeat(4096);
    private static final String BUFFER_PAYLOAD = "B".repeat(4096);
    private static final Header CONTENT_ENCODING_GZIP = HeaderValues.create(HeaderNames.CONTENT_ENCODING, "gzip");
    private static final Header CONTENT_ENCODING_DEFLATE = HeaderValues.create(HeaderNames.CONTENT_ENCODING, "deflate");
    private static final byte[] GZIP_PAYLOAD = encode(PAYLOAD, GZIPOutputStream::new);
    private static final byte[] DEFLATE_PAYLOAD = encode(PAYLOAD, DeflaterOutputStream::new);
    private static final byte[] GZIP_BUFFER_PAYLOAD = encode(BUFFER_PAYLOAD, GZIPOutputStream::new);

    private final Http2Client client;

    DecodedEntityLimitTest(WebServer server) {
        this.client = Http2Client.builder()
                .baseUri(URI.create("http://localhost:" + server.port()))
                .protocolConfig(Http2ClientProtocolConfig.builder().priorKnowledge(true).build())
                .shareConnectionCache(false)
                .build();
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        server.contentEncoding(ContentEncodingContext.builder()
                        .addContentEncoding(GzipEncoding.create())
                        .addContentEncoding(DeflateEncoding.create())
                        .build())
                .maxPayloadSize(MAX_PAYLOAD_SIZE)
                .addProtocol(Http2Config.builder()
                        .maxBufferedEntitySize(Size.create(MAX_BUFFERED_ENTITY_SIZE))
                        .build());
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder rules) {
        rules.route(Http2Route.route(POST, "/as-string", (req, res) -> {
                    req.content().as(String.class);
                    res.send();
                }))
                .route(Http2Route.route(POST, "/buffer", (req, res) -> {
                    req.content().buffer();
                    res.send(req.content().as(String.class));
                }))
                .route(Http2Route.route(POST, "/buffer-only", (req, res) -> {
                    req.content().buffer();
                    res.send();
                }))
                .route(Http2Route.route(POST, "/as-bytes", (req, res) -> {
                    req.content().as(byte[].class);
                    res.send();
                }))
                .route(Http2Route.route(POST, "/stream", (req, res) -> {
                    try (InputStream stream = req.content().as(InputStream.class)) {
                        stream.transferTo(OutputStream.nullOutputStream());
                        res.send();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }))
                .route(Http2Route.route(GET, "/alive", (req, res) -> res.send("alive")));
    }

    @Test
    void testDecodedByteArrayLimit() {
        for (var encoding : new Header[] {CONTENT_ENCODING_GZIP, CONTENT_ENCODING_DEFLATE}) {
            byte[] payload = encoding == CONTENT_ENCODING_GZIP ? GZIP_PAYLOAD : DEFLATE_PAYLOAD;
            try (var response = client.post("/as-bytes")
                    .header(encoding)
                    .submit(payload)) {
                assertThat(response.status(), is(Status.REQUEST_ENTITY_TOO_LARGE_413));
            }
            try (var response = client.get("/alive").request()) {
                assertThat(response.entity().as(String.class), is("alive"));
            }
        }
    }

    @AfterEach
    void cleanup() {
        client.closeResource();
    }

    @Test
    void testGzipDecodedPayloadLimit() {
        try (var response = client.post("/stream")
                .header(HeaderValues.CONTENT_TYPE_TEXT_PLAIN)
                .header(CONTENT_ENCODING_GZIP)
                .submit(GZIP_PAYLOAD)) {
            assertThat(response.status(), is(Status.REQUEST_ENTITY_TOO_LARGE_413));
        }

        try (var response = client.get("/alive").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity().as(String.class), is("alive"));
        }
    }

    @Test
    void testDeflateDecodedPayloadLimit() {
        try (var response = client.post("/stream")
                .header(HeaderValues.CONTENT_TYPE_TEXT_PLAIN)
                .header(CONTENT_ENCODING_DEFLATE)
                .submit(DEFLATE_PAYLOAD)) {
            assertThat(response.status(), is(Status.REQUEST_ENTITY_TOO_LARGE_413));
        }

        try (var response = client.get("/alive").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity().as(String.class), is("alive"));
        }
    }

    @Test
    void testGzipDecodedBufferLimit() {
        assertThat(GZIP_BUFFER_PAYLOAD.length < MAX_BUFFERED_ENTITY_SIZE, is(true));

        try (var response = client.post("/buffer")
                .header(HeaderValues.CONTENT_TYPE_TEXT_PLAIN)
                .header(CONTENT_ENCODING_GZIP)
                .submit(GZIP_BUFFER_PAYLOAD)) {
            assertThat(response.status(), is(Status.REQUEST_ENTITY_TOO_LARGE_413));
        }

        try (var response = client.get("/alive").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity().as(String.class), is("alive"));
        }
    }

    @Test
    void testBufferOnlyEnforcesDecodedSize() {
        assertBufferedSize("A".repeat(128), Status.OK_200);
        assertBufferedSize("A".repeat(129), Status.REQUEST_ENTITY_TOO_LARGE_413);

        byte[] gzip = encode("A".repeat(129), GZIPOutputStream::new);
        byte[] deflate = encode("A".repeat(129), DeflaterOutputStream::new);
        assertThat(gzip.length < MAX_BUFFERED_ENTITY_SIZE, is(true));
        assertThat(deflate.length < MAX_BUFFERED_ENTITY_SIZE, is(true));
        assertBufferedCompressionLimit(gzip, CONTENT_ENCODING_GZIP);
        assertBufferedCompressionLimit(deflate, CONTENT_ENCODING_DEFLATE);
    }

    @Test
    void testGzipBufferReplayIsDecodedOnce() {
        String decoded = "B".repeat(128);
        try (var response = client.post("/buffer")
                .header(HeaderValues.CONTENT_TYPE_TEXT_PLAIN)
                .header(CONTENT_ENCODING_GZIP)
                .submit(encode(decoded, GZIPOutputStream::new))) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is(decoded));
        }
    }

    @Test
    void testMaterializedReaderBoundary() {
        try (var response = client.post("/as-bytes").submit("A".repeat(128))) {
            assertThat(response.status(), is(Status.OK_200));
        }
        try (var response = client.post("/as-bytes").submit("A".repeat(129))) {
            assertThat(response.status(), is(Status.REQUEST_ENTITY_TOO_LARGE_413));
        }
    }

    @Test
    void testStreamingUnderConfiguredPayloadLimit() {
        try (var response = client.post("/stream").submit("A".repeat(1024))) {
            assertThat(response.status(), is(Status.OK_200));
        }
    }

    @Test
    void testCompressedMaterializationLimit() {
        String expanded = "A".repeat(129);
        byte[] gzip = encode(expanded, GZIPOutputStream::new);
        byte[] deflate = encode(expanded, DeflaterOutputStream::new);
        assertThat(gzip.length < MAX_BUFFERED_ENTITY_SIZE, is(true));
        assertThat(deflate.length < MAX_BUFFERED_ENTITY_SIZE, is(true));
        assertCompressedLimit(gzip, CONTENT_ENCODING_GZIP);
        assertCompressedLimit(deflate, CONTENT_ENCODING_DEFLATE);
    }

    private static byte[] encode(String entity, CheckedFunction<OutputStream, OutputStream, IOException> factory) {
        try (var baos = new ByteArrayOutputStream()) {
            try (OutputStream output = factory.apply(baos)) {
                output.write(entity.getBytes(StandardCharsets.UTF_8));
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void assertCompressedLimit(byte[] compressed, Header encoding) {
        try (var response = client.post("/as-string")
                .header(HeaderValues.CONTENT_TYPE_TEXT_PLAIN)
                .header(encoding)
                .submit(compressed)) {
            assertThat(response.status(), is(Status.REQUEST_ENTITY_TOO_LARGE_413));
        }
    }

    private void assertBufferedSize(String entity, Status status) {
        try (var response = client.post("/buffer-only").submit(entity)) {
            assertThat(response.status(), is(status));
        }
    }

    private void assertBufferedCompressionLimit(byte[] compressed, Header encoding) {
        try (var response = client.post("/buffer-only")
                .header(encoding)
                .submit(compressed)) {
            assertThat(response.status(), is(Status.REQUEST_ENTITY_TOO_LARGE_413));
        }
    }

}
