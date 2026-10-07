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

package io.helidon.webserver.tests;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import io.helidon.common.Functions.CheckedFunction;
import io.helidon.common.GenericType;
import io.helidon.common.Size;
import io.helidon.common.parameters.Parameters;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.encoding.deflate.DeflateEncoding;
import io.helidon.http.encoding.gzip.GzipEncoding;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.json.JsonSupport;
import io.helidon.http.media.json.binding.JsonBindingSupport;
import io.helidon.json.JsonObject;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
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
    private static final GenericType<Map<String, String>> MAP_TYPE = new GenericType<>() { };

    private final Http1Client client;

    DecodedEntityLimitTest(Http1Client client) {
        this.client = client;
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        server.contentEncoding(ContentEncodingContext.builder()
                        .addContentEncoding(GzipEncoding.create())
                        .addContentEncoding(DeflateEncoding.create())
                        .build())
                .mediaContext(MediaContext.builder()
                        .mediaSupportsDiscoverServices(false)
                        .addMediaSupport(JsonSupport.create())
                        .addMediaSupport(JsonBindingSupport.create())
                        .build())
                .maxPayloadSize(MAX_PAYLOAD_SIZE)
                .addConnectionSelector(Http1ConnectionSelector.builder()
                        .config(Http1Config.builder()
                                .maxBufferedEntitySize(Size.create(MAX_BUFFERED_ENTITY_SIZE))
                                .build())
                        .build());
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.post("/as-string", (req, res) -> {
                    req.content().as(String.class);
                    res.send();
                })
                .post("/as-bytes", (req, res) -> {
                    req.content().as(byte[].class);
                    res.send();
                })
                .post("/as-form", (req, res) -> {
                    req.content().as(Parameters.class);
                    res.send();
                })
                .post("/as-json", (req, res) -> {
                    req.content().as(JsonObject.class);
                    res.send();
                })
                .post("/as-json-binding", (req, res) -> {
                    req.content().as(MAP_TYPE);
                    res.send();
                })
                .post("/stream", (req, res) -> {
                    try (InputStream stream = req.content().as(InputStream.class)) {
                        stream.transferTo(OutputStream.nullOutputStream());
                        res.send();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .post("/buffer", (req, res) -> {
                    req.content().buffer();
                    res.send(req.content().as(String.class));
                })
                .post("/buffer-only", (req, res) -> {
                    req.content().buffer();
                    res.send();
                })
                .get("/alive", (req, res) -> res.send("alive"));
    }

    static byte[] encode(String entity, CheckedFunction<OutputStream, OutputStream, IOException> factory) {
        try (var baos = new ByteArrayOutputStream()) {
            try (OutputStream output = factory.apply(baos)) {
                output.write(entity.getBytes(StandardCharsets.UTF_8));
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void testGzipDecodedPayloadLimit() {
        try (var response = client.post("/stream")
                .header(HeaderValues.CONTENT_TYPE_TEXT_PLAIN)
                .header(CONTENT_ENCODING_GZIP)
                .submit(GZIP_PAYLOAD)) {
            assertThat(response.status(), is(Status.REQUEST_ENTITY_TOO_LARGE_413));
            assertThat(response.headers(), hasHeader(HeaderValues.CONNECTION_CLOSE));
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
            assertThat(response.headers(), hasHeader(HeaderValues.CONNECTION_CLOSE));
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
    void testMaterializedReaderBoundaries() {
        assertBoundary("/as-bytes", "A".repeat(128), "A".repeat(129), HeaderValues.CONTENT_TYPE_TEXT_PLAIN);
        assertBoundary("/as-string", "A".repeat(128), "A".repeat(129), HeaderValues.CONTENT_TYPE_TEXT_PLAIN);
        assertBoundary("/as-form", "v=" + "A".repeat(126), "v=" + "A".repeat(127),
                       HeaderValues.create(HeaderNames.CONTENT_TYPE, "application/x-www-form-urlencoded"));
        assertBoundary("/as-json", "{\"v\":\"" + "A".repeat(120) + "\"}",
                       "{\"v\":\"" + "A".repeat(121) + "\"}", HeaderValues.CONTENT_TYPE_JSON);
        assertBoundary("/as-json-binding", "{\"v\":\"" + "A".repeat(120) + "\"}",
                       "{\"v\":\"" + "A".repeat(121) + "\"}", HeaderValues.CONTENT_TYPE_JSON);
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

    private void assertBoundary(String path, String accepted, String rejected, Header contentType) {
        assertThat(accepted.getBytes(StandardCharsets.UTF_8).length, is((int) MAX_BUFFERED_ENTITY_SIZE));
        assertThat(rejected.getBytes(StandardCharsets.UTF_8).length, is((int) MAX_BUFFERED_ENTITY_SIZE + 1));
        try (var response = client.post(path).header(contentType).submit(accepted)) {
            assertThat(path + " at limit", response.status(), is(Status.OK_200));
        }
        try (var response = client.post(path).header(contentType).submit(rejected)) {
            assertThat(path + " over limit", response.status(), is(Status.REQUEST_ENTITY_TOO_LARGE_413));
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
            assertThat(response.headers(), hasHeader(HeaderValues.CONNECTION_CLOSE));
        }
    }

}
