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
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import io.helidon.common.GenericType;
import io.helidon.common.Size;
import io.helidon.config.Config;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.Status;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.encoding.deflate.DeflateEncoding;
import io.helidon.http.encoding.gzip.GzipEncoding;
import io.helidon.http.media.EntityReader;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.MediaSupport;
import io.helidon.http.media.ReadableEntity;
import io.helidon.http.media.jackson.JacksonSupport;
import io.helidon.http.media.jsonb.JsonbSupport;
import io.helidon.http.media.multipart.MultiPart;
import io.helidon.http.media.multipart.MultiPartSupport;
import io.helidon.http.media.multipart.ReadablePart;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MultipartMaterializationLimitTest {
    private static final int PART_LIMIT = 1024;
    private static final String BOUNDARY = "materialization-boundary";
    private static final String CONTENT_TYPE = "multipart/form-data; boundary=" + BOUNDARY;
    private static final HeaderName CONNECTION_ID = HeaderNames.create("X-Connection-Id");

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
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void multipartCleanupCompletesClientResponse(boolean decorated, boolean readerFailure) throws IOException {
        try (var connection = new Connection(Kind.STRING, decorated)) {
            MultiPart parts = connection.client.get("/streamed").requestEntity(MultiPart.class);
            CompletableFuture<?> completed = connection.completed.get();
            try (parts) {
                ReadablePart first = parts.next();
                assertThat("Lazy reader retains the response", completed.isDone(), is(false));
                if (readerFailure) {
                    IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                                () -> first.as(String.class));
                    assertThat(failure.getMessage(), containsString("Maximum materialized entity size exceeded"));
                } else {
                    assertThat(first.inputStream().read(), is((int) 'A'));
                }
            }
            assertThat("Multipart cleanup completes a response obtained through requestEntity",
                       completed.isDone(), is(true));
            try (var next = connection.client.get("/accepted").request()) {
                assertThat(next.status(), is(Status.OK_200));
                assertThat(readParts(next.as(MultiPart.class), Kind.STRING), is((long) PART_LIMIT));
            }
        }
    }

    @Test
    void fullMultipartIterationCompletesClientResponseBeforeHttpEpilogue() {
        try (var connection = new Connection(Kind.STRING);
             MultiPart parts = connection.client.get("/epilogue").requestEntity(MultiPart.class)) {
            CompletableFuture<?> completed = connection.completed.get();
            assertThat("Lazy reader retains the response", completed.isDone(), is(false));
            assertThat(readParts(parts, Kind.STRING), is((long) PART_LIMIT));
            assertThat("MIME completion releases the response with unread HTTP epilogue",
                       completed.isDone(), is(true));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"identity", "gzip", "deflate"})
    void closingUniteratedMultipartCompletesResponseWithDrainingDecorator(String encoding) {
        try (var connection = new Connection(Kind.STRING, true, true, encoding)) {
            MultiPart parts = connection.client.get("/accepted").requestEntity(MultiPart.class);
            CompletableFuture<?> completed = connection.completed.get();
            parts.close();
            assertThat("Closing multipart before iteration completes its response", completed.isDone(), is(true));
            assertThat("Response cleanup closes the service decorator", connection.decoratorCloses.get(), is(1));
            assertThat("The service decorator can drain its delegate during cleanup",
                       connection.decoratorCloseFailure.get(), nullValue());
            try (var response = connection.client.get("/accepted").request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.headers().contains(HeaderNames.CONTENT_LENGTH), is(true));
                if (!encoding.equals("identity")) {
                    assertThat(response.headers().get(HeaderNames.CONTENT_ENCODING).get(), is(encoding));
                }
                assertThat(readParts(response.as(MultiPart.class), Kind.STRING), is((long) PART_LIMIT));
            }
            assertThat("Both service responses close their decorators", connection.decoratorCloses.get(), is(2));
            assertThat("Decoding and draining the follow-up response succeeds",
                       connection.decoratorCloseFailure.get(), nullValue());
        }
    }

    @ParameterizedTest
    @CsvSource({"false, identity", "true, identity", "false, gzip", "true, gzip", "false, deflate", "true, deflate"})
    void fullyConsumedFixedLengthMultipartPreservesConnectionReuse(boolean decorated, String encoding) {
        try (var connection = new Connection(Kind.STRING, decorated, decorated, encoding)) {
            String firstConnection;
            try (var response = connection.client.get("/accepted").request()) {
                firstConnection = response.headers().get(CONNECTION_ID).get();
                assertThat(readParts(response.as(MultiPart.class), Kind.STRING), is((long) PART_LIMIT));
            }
            try (var response = connection.client.get("/accepted").request()) {
                assertThat("Fully consumed fixed-length multipart permits keep-alive reuse",
                           response.headers().get(CONNECTION_ID).get(), is(firstConnection));
                assertThat(readParts(response.as(MultiPart.class), Kind.STRING), is((long) PART_LIMIT));
            }
            assertThat("Full consumption permits decorator cleanup without decoding failures",
                       connection.decoratorCloseFailure.get(), nullValue());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fullyConsumedMultipartCompletesRetainedEntityAndCopyOnce(boolean decorated) {
        try (var connection = new Connection(Kind.STRING, decorated);
             var response = connection.client.get("/accepted").request()) {
            ReadableEntity original = response.entity();
            var completions = new AtomicInteger();
            ReadableEntity copy = original.copy(completions::incrementAndGet);
            try (MultiPart parts = copy.as(MultiPart.class)) {
                assertThat(original.consumed(), is(false));
                assertThat(copy.consumed(), is(false));
                assertThat(readParts(parts, Kind.STRING), is((long) PART_LIMIT));
                assertThat("Full multipart iteration completes the retained entity", original.consumed(), is(true));
                assertThat("Full multipart iteration completes its copy", copy.consumed(), is(true));
                assertThat(completions.get(), is(1));
                original.consume();
                copy.consume();
                assertThat("Repeated consumption preserves single completion", completions.get(), is(1));
            }
            assertThat("Closing the completed multipart preserves single completion", completions.get(), is(1));
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

    private static byte[] encodedMultipart(Kind kind, String encoding) {
        byte[] parts = multipart(kind, PART_LIMIT);
        if (encoding.equals("identity")) {
            return parts;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (OutputStream encoder = switch (encoding) {
            case "gzip" -> new GZIPOutputStream(bytes);
            case "deflate" -> new DeflaterOutputStream(bytes);
            default -> throw new IllegalArgumentException("Unexpected encoding: " + encoding);
        }) {
            encoder.write(parts);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
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
        private final Http1Client client;
        private final AtomicReference<CompletableFuture<?>> completed = new AtomicReference<>();
        private final AtomicInteger decoratorCloses = new AtomicInteger();
        private final AtomicReference<Exception> decoratorCloseFailure = new AtomicReference<>();

        private Connection(Kind kind) {
            this(kind, false);
        }

        private Connection(Kind kind, boolean decorated) {
            this(kind, decorated, false);
        }

        private Connection(Kind kind, boolean decorated, boolean drainOnClose) {
            this(kind, decorated, drainOnClose, "identity");
        }

        private Connection(Kind kind, boolean decorated, boolean drainOnClose, String encoding) {
            MediaContext context = mediaContext(kind);
            byte[] accepted = encodedMultipart(kind, encoding);
            server = WebServer.builder()
                    .host("127.0.0.1")
                    .port(0)
                    .mediaContext(context)
                    .contentEncoding(ContentEncodingContext.builder()
                            .addContentEncoding(GzipEncoding.create())
                            .addContentEncoding(DeflateEncoding.create())
                            .build())
                    .addConnectionSelector(Http1ConnectionSelector.builder()
                            .config(Http1Config.builder().maxBufferedEntitySize(Size.create(PART_LIMIT)).build())
                            .build())
                    .routing(rules -> rules
                            .post("/parts",
                                  (req, res) -> res.send(Long.toString(readParts(req.content().as(MultiPart.class), kind))))
                            .get("/accepted", (req, res) -> {
                                res.headers().set(HeaderNames.CONTENT_TYPE, CONTENT_TYPE);
                                res.headers().set(CONNECTION_ID, req.socketId());
                                res.headers().set(HeaderNames.CONTENT_LENGTH, Integer.toString(accepted.length));
                                if (!encoding.equals("identity")) {
                                    res.headers().set(HeaderNames.CONTENT_ENCODING, encoding);
                                }
                                res.send(accepted);
                            })
                            .get("/rejected", (_, res) -> {
                                res.headers().set(HeaderNames.CONTENT_TYPE, CONTENT_TYPE);
                                res.send(multipart(kind, PART_LIMIT + 1));
                            })
                            .get("/multiple", (_, res) -> {
                                res.headers().set(HeaderNames.CONTENT_TYPE, CONTENT_TYPE);
                                res.send(multipart(kind, PART_LIMIT, PART_LIMIT));
                            })
                            .get("/streamed", (_, res) -> {
                                res.headers().set(HeaderNames.CONTENT_TYPE, CONTENT_TYPE);
                                res.send(multipart(kind, 3 * PART_LIMIT, 31));
                            })
                            .get("/epilogue", (_, res) -> {
                                res.headers().set(HeaderNames.CONTENT_TYPE, CONTENT_TYPE);
                                byte[] parts = multipart(kind, PART_LIMIT);
                                byte[] body = Arrays.copyOf(parts, parts.length + 3 * PART_LIMIT);
                                Arrays.fill(body, parts.length, body.length, (byte) 'E');
                                res.send(body);
                            }))
                    .build()
                    .start();
            client = Http1Client.builder()
                    .baseUri("http://127.0.0.1:" + server.port())
                    .mediaContext(context)
                    .contentEncoding(ContentEncodingContext.builder()
                            .addContentEncoding(GzipEncoding.create())
                            .addContentEncoding(DeflateEncoding.create())
                            .build())
                    .shareConnectionCache(false)
                    .readTimeout(Duration.ofSeconds(10))
                    .addService((chain, request) -> {
                        completed.set(request.whenComplete().toCompletableFuture());
                        var response = chain.proceed(request);
                        if (decorated) {
                            return WebClientServiceResponse.builder(response)
                                    .inputStream(new FilterInputStream(response.inputStream().orElseThrow()) {
                                        @Override
                                        public void close() throws IOException {
                                            try (InputStream delegate = in) {
                                                if (drainOnClose) {
                                                    delegate.transferTo(OutputStream.nullOutputStream());
                                                }
                                            } catch (IOException | RuntimeException e) {
                                                decoratorCloseFailure.set(e);
                                                throw e;
                                            } finally {
                                                decoratorCloses.incrementAndGet();
                                            }
                                        }
                                    })
                                    .build();
                        }
                        return response;
                    })
                    .protocolConfig(it -> it.maxBufferedEntitySize(Size.create(PART_LIMIT)))
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
