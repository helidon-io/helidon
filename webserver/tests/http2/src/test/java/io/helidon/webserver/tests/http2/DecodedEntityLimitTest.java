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
                    req.content().as(String.class);
                    res.send();
                }))
                .route(Http2Route.route(GET, "/alive", (req, res) -> res.send("alive")));
    }

    @AfterEach
    void cleanup() {
        client.closeResource();
    }

    @Test
    void testGzipDecodedPayloadLimit() {
        try (var response = client.post("/as-string")
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
        try (var response = client.post("/as-string")
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
}
