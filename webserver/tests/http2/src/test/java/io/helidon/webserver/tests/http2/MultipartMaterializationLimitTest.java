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
import java.nio.charset.StandardCharsets;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import io.helidon.common.GenericType;
import io.helidon.common.Size;
import io.helidon.config.Config;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.Status;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.encoding.deflate.DeflateEncoding;
import io.helidon.http.encoding.gzip.GzipEncoding;
import io.helidon.http.media.EntityReader;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.MediaSupport;
import io.helidon.http.media.jackson.JacksonSupport;
import io.helidon.http.media.jsonb.JsonbSupport;
import io.helidon.http.media.multipart.MultiPart;
import io.helidon.http.media.multipart.MultiPartSupport;
import io.helidon.http.media.multipart.ReadablePart;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientProtocolConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2Route;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.helidon.http.Method.GET;
import static io.helidon.http.Method.POST;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MultipartMaterializationLimitTest {
    private static final int PART_LIMIT = 1024;
    private static final String BOUNDARY = "materialization-boundary";
    private static final String CONTENT_TYPE = "multipart/form-data; boundary=" + BOUNDARY;

    @ParameterizedTest
    @EnumSource(value = Kind.class, names = {"STRING", "JSON_B", "JACKSON", "CUSTOM"})
    void serverUsesConfiguredLimitForPartMaterialization(Kind kind) {
        try (var connection = new Connection(kind)) {
            connection.assertRequest(multipart(kind, PART_LIMIT), Status.OK_200, valueLength(kind, PART_LIMIT));
            connection.assertRequest(multipart(kind, PART_LIMIT + 1), Status.REQUEST_ENTITY_TOO_LARGE_413, 0);
        }
    }

    @ParameterizedTest
    @EnumSource(value = Kind.class, names = {"STRING", "JSON_B", "JACKSON", "CUSTOM"})
    void clientUsesConfiguredLimitForPartMaterialization(Kind kind) {
        try (var connection = new Connection(kind)) {
            try (var response = connection.client.get("/accepted").request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(readParts(response.as(MultiPart.class), kind), is(valueLength(kind, PART_LIMIT)));
            }
            try (var response = connection.client.get("/rejected").request()) {
                assertThat(response.status(), is(Status.OK_200));
                MultiPart parts = response.as(MultiPart.class);
                assertThat(parts.hasNext(), is(true));
                ReadablePart part = parts.next();
                IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                               () -> readPart(part, kind));
                assertThat(exception.getMessage(), containsString("Maximum materialized entity size exceeded"));
                assertThat(response.status(), is(Status.OK_200));
            }
        }
    }

    @Test
    void separatePartsHaveIndependentBudgets() {
        try (var connection = new Connection(Kind.STRING)) {
            byte[] payload = multipart(Kind.STRING, PART_LIMIT, PART_LIMIT);
            connection.assertRequest(payload, Status.OK_200, 2L * PART_LIMIT);
            try (var response = connection.client.get("/multiple").request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(readParts(response.as(MultiPart.class), Kind.STRING), is(2L * PART_LIMIT));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = Kind.class, names = {"INPUT_STREAM", "CUSTOM_STREAM"})
    void streamingPartsCanExceedConfiguredLimit(Kind kind) {
        try (var connection = new Connection(kind)) {
            byte[] payload = multipart(kind, 3 * PART_LIMIT, 31);
            connection.assertRequest(payload, Status.OK_200, 3L * PART_LIMIT + 31);
            try (var response = connection.client.get("/streamed").request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(readParts(response.as(MultiPart.class), kind), is(3L * PART_LIMIT + 31));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"gzip", "deflate"})
    void compressedMultipartUsesDecodedPartSize(String encoding) throws IOException {
        try (var connection = new Connection(Kind.STRING)) {
            for (int length : new int[] {PART_LIMIT, PART_LIMIT + 1}) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (OutputStream encoder = switch (encoding) {
                    case "gzip" -> new GZIPOutputStream(bytes);
                    case "deflate" -> new DeflaterOutputStream(bytes);
                    default -> throw new IllegalArgumentException("Unexpected encoding: " + encoding);
                }) {
                    encoder.write(multipart(Kind.STRING, length));
                }
                try (var response = connection.client.post("/parts")
                        .header(HeaderNames.CONTENT_TYPE, CONTENT_TYPE)
                        .header(HeaderNames.CONTENT_ENCODING, encoding)
                        .submit(bytes.toByteArray())) {
                    assertThat(response.status(),
                               is(length == PART_LIMIT ? Status.OK_200 : Status.REQUEST_ENTITY_TOO_LARGE_413));
                    if (length == PART_LIMIT) {
                        assertThat(response.as(String.class), is(Integer.toString(PART_LIMIT)));
                    }
                }
            }
        }
    }

    private static long readParts(MultiPart parts, Kind kind) {
        long count = 0;
        while (parts.hasNext()) {
            count += readPart(parts.next(), kind);
        }
        return count;
    }

    private static long readPart(ReadablePart part, Kind kind) {
        return switch (kind) {
            case STRING -> part.as(String.class).length();
            case JSON_B, JACKSON -> part.as(Payload.class).value.length();
            case CUSTOM, CUSTOM_STREAM -> part.as(PartSize.class).bytes;
            case INPUT_STREAM -> {
                try (InputStream stream = part.inputStream()) {
                    yield stream.transferTo(OutputStream.nullOutputStream());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
    }

    private static long valueLength(Kind kind, int partBytes) {
        return switch (kind) {
            case JSON_B, JACKSON -> partBytes - 12L;
            default -> partBytes;
        };
    }

    private static byte[] multipart(Kind kind, int... partSizes) {
        StringBuilder payload = new StringBuilder();
        for (int i = 0; i < partSizes.length; i++) {
            boolean json = kind == Kind.JSON_B || kind == Kind.JACKSON;
            String value = "A".repeat(partSizes[i] - (json ? 12 : 0));
            String content = json ? "{\"value\":\"" + value + "\"}" : value;
            assertThat(content.getBytes(StandardCharsets.US_ASCII).length, is(partSizes[i]));
            payload.append("--").append(BOUNDARY).append("\r\n")
                    .append("Content-Disposition: form-data; name=part-").append(i).append("\r\n")
                    .append("Content-Type: ").append(json ? "application/json" : "text/plain").append("\r\n\r\n")
                    .append(content).append("\r\n");
        }
        payload.append("--").append(BOUNDARY).append("--\r\n");
        return payload.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static MediaContext mediaContext(Kind kind) {
        var builder = MediaContext.builder()
                .mediaSupportsDiscoverServices(false)
                .addMediaSupport(MultiPartSupport.create(Config.empty()));
        switch (kind) {
            case JSON_B -> builder.addMediaSupport(JsonbSupport.create());
            case JACKSON -> builder.addMediaSupport(JacksonSupport.create());
            case CUSTOM, CUSTOM_STREAM -> builder.addMediaSupport(new PartSizeSupport(kind == Kind.CUSTOM_STREAM));
            default -> { }
        }
        return builder.build();
    }

    private enum Kind {
        STRING,
        JSON_B,
        JACKSON,
        CUSTOM,
        INPUT_STREAM,
        CUSTOM_STREAM
    }

    public static final class Payload {
        public String value;
    }

    private static final class Connection implements AutoCloseable {
        private final WebServer server;
        private final Http2Client client;

        private Connection(Kind kind) {
            MediaContext context = mediaContext(kind);
            server = WebServer.builder()
                    .host("127.0.0.1")
                    .port(0)
                    .mediaContext(context)
                    .contentEncoding(ContentEncodingContext.builder()
                            .addContentEncoding(GzipEncoding.create())
                            .addContentEncoding(DeflateEncoding.create())
                            .build())
                    .addProtocol(Http2Config.builder().maxBufferedEntitySize(Size.create(PART_LIMIT)).build())
                    .routing(rules -> rules
                            .route(Http2Route.route(POST, "/parts",
                                    (req, res) -> res.send(Long.toString(readParts(req.content().as(MultiPart.class), kind)))))
                            .route(Http2Route.route(GET, "/accepted", (req, res) -> {
                                res.headers().set(HeaderNames.CONTENT_TYPE, CONTENT_TYPE);
                                res.send(multipart(kind, PART_LIMIT));
                            }))
                            .route(Http2Route.route(GET, "/rejected", (req, res) -> {
                                res.headers().set(HeaderNames.CONTENT_TYPE, CONTENT_TYPE);
                                res.send(multipart(kind, PART_LIMIT + 1));
                            }))
                            .route(Http2Route.route(GET, "/multiple", (req, res) -> {
                                res.headers().set(HeaderNames.CONTENT_TYPE, CONTENT_TYPE);
                                res.send(multipart(kind, PART_LIMIT, PART_LIMIT));
                            }))
                            .route(Http2Route.route(GET, "/streamed", (req, res) -> {
                                res.headers().set(HeaderNames.CONTENT_TYPE, CONTENT_TYPE);
                                res.send(multipart(kind, 3 * PART_LIMIT, 31));
                            })))
                    .build()
                    .start();
            client = Http2Client.builder()
                    .baseUri("http://127.0.0.1:" + server.port())
                    .mediaContext(context)
                    .protocolConfig(Http2ClientProtocolConfig.builder()
                            .priorKnowledge(true)
                            .maxBufferedEntitySize(Size.create(PART_LIMIT))
                            .build())
                    .shareConnectionCache(false)
                    .build();
        }

        @Override
        public void close() {
            try {
                client.closeResource();
            } finally {
                server.stop();
            }
        }

        private void assertRequest(byte[] payload, Status status, long count) {
            try (var response = client.post("/parts")
                    .header(HeaderNames.CONTENT_TYPE, CONTENT_TYPE)
                    .submit(payload)) {
                assertThat(response.status(), is(status));
                if (status == Status.OK_200) {
                    assertThat(response.as(String.class), is(Long.toString(count)));
                }
            }
        }
    }

    private static final class PartSize {
        private final long bytes;

        private PartSize(long bytes) {
            this.bytes = bytes;
        }
    }

    private static final class PartSizeSupport implements MediaSupport {
        private final boolean streaming;

        private PartSizeSupport(boolean streaming) {
            this.streaming = streaming;
        }

        @Override
        public String name() {
            return "part-size";
        }

        @Override
        public String type() {
            return "part-size";
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> ReaderResponse<T> reader(GenericType<T> type, Headers headers) {
            if (type.rawType() == PartSize.class) {
                return new ReaderResponse<>(SupportLevel.SUPPORTED,
                                            () -> (EntityReader<T>) new PartSizeReader(streaming));
            }
            return ReaderResponse.unsupported();
        }

        @Override
        public <T> ReaderResponse<T> reader(GenericType<T> type, Headers requestHeaders, Headers responseHeaders) {
            return reader(type, responseHeaders);
        }
    }

    private static final class PartSizeReader implements EntityReader<PartSize> {
        private final boolean streaming;

        private PartSizeReader(boolean streaming) {
            this.streaming = streaming;
        }

        @Override
        public boolean streaming() {
            return streaming;
        }

        @Override
        public PartSize read(GenericType<PartSize> type, InputStream stream, Headers headers) {
            try (stream) {
                return new PartSize(streaming
                        ? stream.transferTo(OutputStream.nullOutputStream())
                        : stream.readAllBytes().length);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (RuntimeException e) {
                throw new RuntimeException("Custom provider wrapped input failure", e);
            }
        }

        @Override
        public PartSize read(GenericType<PartSize> type, InputStream stream, Headers requestHeaders, Headers responseHeaders) {
            return read(type, stream, responseHeaders);
        }
    }
}
