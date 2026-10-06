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
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import io.helidon.common.Size;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.encoding.deflate.DeflateEncoding;
import io.helidon.http.encoding.gzip.GzipEncoding;
import io.helidon.http.media.multipart.MultiPart;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ServerTest
class MaterializedEntityLimitTest {
    private static final int DEFAULT_LIMIT = 1024 * 1024; // 1 MiB
    private static final byte[] LARGE_RESPONSE = new byte[DEFAULT_LIMIT + 1];

    private final Http1Client client;
    private final int port;

    MaterializedEntityLimitTest(Http1Client client, WebServer server) {
        this.client = client;
        this.port = server.port();
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        server.contentEncoding(ContentEncodingContext.builder()
                .addContentEncoding(GzipEncoding.create())
                .addContentEncoding(DeflateEncoding.create())
                .build());
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.post("/bytes", (req, res) -> res.send(Integer.toString(req.content().as(byte[].class).length)))
                .post("/string", (req, res) -> res.send(Integer.toString(req.content().as(String.class).length())))
                .post("/stream", (req, res) -> {
                    try (InputStream stream = req.content().as(InputStream.class)) {
                        res.send(Long.toString(stream.transferTo(OutputStream.nullOutputStream())));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .post("/multipart", (req, res) -> {
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
                })
                .get("/response", (_, res) -> res.send(LARGE_RESPONSE))
                .get("/outbound-response", (req, res) -> {
                    Http1Client outbound = Http1Client.builder()
                            .baseUri("http://localhost:" + req.localPeer().port())
                            .build();
                    try (var response = outbound.get("/response").request()) {
                        res.send(response.entity().as(byte[].class));
                    } finally {
                        outbound.closeResource();
                    }
                });
    }

    @Test
    void defaultLimitAcceptsExactlyOneMebibyte() {
        byte[] bytes = new byte[DEFAULT_LIMIT];
        try (var response = client.post("/bytes").submit(bytes)) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is(Integer.toString(DEFAULT_LIMIT)));
        }
    }

    @Test
    void defaultLimitRejectsOneByteMore() {
        byte[] bytes = new byte[DEFAULT_LIMIT + 1];
        try (var response = client.post("/bytes").submit(bytes)) {
            assertThat(response.status(), is(Status.REQUEST_ENTITY_TOO_LARGE_413));
        }
    }

    @Test
    void stringLimitCountsUtf8BytesNotCharacters() {
        String accepted = "ř".repeat(DEFAULT_LIMIT / 2);
        try (var response = client.post("/string")
                .header(HeaderValues.CONTENT_TYPE_TEXT_PLAIN)
                .submit(accepted)) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is(Integer.toString(accepted.length())));
        }

        String rejected = accepted + "ř";
        assertThat(rejected.length() < DEFAULT_LIMIT, is(true));
        try (var response = client.post("/string")
                .header(HeaderValues.CONTENT_TYPE_TEXT_PLAIN)
                .submit(rejected)) {
            assertThat(response.status(), is(Status.REQUEST_ENTITY_TOO_LARGE_413));
        }
    }

    @Test
    void largeStreamRemainsUsable() {
        byte[] bytes = new byte[DEFAULT_LIMIT + 1];
        try (var response = client.post("/stream").submit(bytes)) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is(Integer.toString(bytes.length)));
        }
    }

    @Test
    void largeMultipartPartRemainsStreamed() {
        String value = "A".repeat(DEFAULT_LIMIT + 1);
        String multipart = "--boundary\r\nContent-Disposition: form-data; name=payload\r\n\r\n"
                + value + "\r\n--boundary--\r\n";
        try (var response = client.post("/multipart")
                .header(HeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=boundary")
                .submit(multipart.getBytes(StandardCharsets.US_ASCII))) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is(Integer.toString(value.length())));
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

    @Test
    void outboundClientMaterializationFailureIsAServerError() {
        try (var response = client.get("/outbound-response").request()) {
            assertThat(response.status(), is(Status.INTERNAL_SERVER_ERROR_500));
        }
    }

    @Test
    void clientCanIncreaseItsMaterializedEntityLimit() {
        Http1Client largerClient = Http1Client.builder()
                .baseUri("http://localhost:" + port)
                .protocolConfig(it -> it.maxBufferedEntitySize(Size.create(2, Size.Unit.MIB)))
                .build();
        try (var response = largerClient.get("/response").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity().as(byte[].class).length, is(LARGE_RESPONSE.length));
        } finally {
            largerClient.closeResource();
        }
    }
}
