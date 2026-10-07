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

import static io.helidon.http.Method.POST;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class BufferedPayloadLimitTest {
    private static final int MAX_PAYLOAD_SIZE = 128;
    private static final int MAX_BUFFERED_ENTITY_SIZE = 256;
    private static final Header CONTENT_ENCODING_GZIP = HeaderValues.create(HeaderNames.CONTENT_ENCODING, "gzip");
    private static final Header CONTENT_ENCODING_DEFLATE = HeaderValues.create(HeaderNames.CONTENT_ENCODING, "deflate");

    private final Http2Client client;

    BufferedPayloadLimitTest(WebServer server) {
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
        rules.route(Http2Route.route(POST, "/buffer-only", (req, res) -> {
            req.content().buffer();
            res.send();
        }));
    }

    @AfterEach
    void cleanup() {
        client.closeResource();
    }

    @Test
    void configuredDecodedPayloadLimitAppliesToBufferOnly() {
        assertBoundary(CONTENT_ENCODING_GZIP, GZIPOutputStream::new);
        assertBoundary(CONTENT_ENCODING_DEFLATE, DeflaterOutputStream::new);
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

    private void assertBoundary(Header encoding, CheckedFunction<OutputStream, OutputStream, IOException> factory) {
        byte[] accepted = encode("A".repeat(MAX_PAYLOAD_SIZE), factory);
        byte[] rejected = encode("A".repeat(MAX_PAYLOAD_SIZE + 1), factory);
        assertThat(rejected.length < MAX_PAYLOAD_SIZE, is(true));
        try (var response = client.post("/buffer-only").header(encoding).submit(accepted)) {
            assertThat(response.status(), is(Status.OK_200));
        }
        try (var response = client.post("/buffer-only").header(encoding).submit(rejected)) {
            assertThat(response.status(), is(Status.REQUEST_ENTITY_TOO_LARGE_413));
        }
    }

}
