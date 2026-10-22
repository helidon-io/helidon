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
package io.helidon.webserver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.FormParams;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.media.common.ContentReaders;
import io.helidon.media.common.MessageBodyReader;
import io.helidon.media.common.MessageBodyReaderContext;
import io.helidon.media.multipart.MultiPartSupport;
import io.helidon.media.multipart.ReadableBodyPart;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webclient.WebClientResponse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MaterializedEntityLimitTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static WebServer server;
    private static WebClient client;

    @BeforeAll
    static void start() {
        server = WebServer.builder()
                .host("127.0.0.1")
                .port(0)
                .maxBufferedEntitySize(8)
                .addMediaSupport(MultiPartSupport.create())
                .addReader(new LengthReader())
                .addRouting(routing())
                .build()
                .start()
                .await(TIMEOUT);
        client = WebClient.builder().baseUri("http://127.0.0.1:" + server.port()).build();
    }

    @AfterAll
    static void stop() {
        if (server != null) {
            server.shutdown().await(TIMEOUT);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/text", "/bytes", "/custom", "/form"})
    void ordinaryConversionsReturn413(String path) {
        WebClientResponse response = client.post().path(path)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).submit("key=12345").await(TIMEOUT);
        try {
            assertThat(response.status(), is(Http.Status.REQUEST_ENTITY_TOO_LARGE_413));
        } finally {
            response.close().await(TIMEOUT);
        }
    }

    @Test
    void conversionAtLimitSucceeds() {
        WebClientResponse response = client.post().path("/text").submit("12345678").await(TIMEOUT);
        try {
            assertThat(response.status(), is(Http.Status.OK_200));
            assertThat(response.content().as(String.class).await(TIMEOUT), is("12345678"));
        } finally {
            response.close().await(TIMEOUT);
        }
    }

    @Test
    void directRequestStreamingExceedsMaterializationLimit() {
        WebClientResponse response = client.post().path("/stream").submit("streaming request").await(TIMEOUT);
        try {
            assertThat(response.status(), is(Http.Status.OK_200));
            assertThat(response.content().as(String.class).await(TIMEOUT), is("17"));
        } finally {
            response.close().await(TIMEOUT);
        }
    }

    @Test
    void clientResponseConversionsAreBounded() {
        WebClient bounded = WebClient.builder().baseUri("http://127.0.0.1:" + server.port())
                .maxBufferedEntitySize(8).build();
        WebClientResponse response = bounded.get().path("/response").request().await(TIMEOUT);
        try {
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> response.content().as(String.class).get(10, TimeUnit.SECONDS));
            assertThat(failure.getCause(), instanceOf(IllegalStateException.class));
            assertThat(failure.getCause().getMessage(), is("Maximum materialized entity size exceeded"));
        } finally {
            response.close().await(TIMEOUT);
        }
    }

    @Test
    void configuredClientCanIncreaseBudget() {
        Config config = Config.create(ConfigSources.create(Map.of("max-buffered-entity-size", "32")));
        WebClient configured = WebClient.builder().config(config)
                .baseUri("http://127.0.0.1:" + server.port()).build();
        WebClientResponse response = configured.get().path("/response").request().await(TIMEOUT);
        try {
            assertThat(response.content().as(String.class).await(TIMEOUT), is("client response entity"));
        } finally {
            response.close().await(TIMEOUT);
        }
    }

    @Test
    void perRequestReaderContextCanOverrideClientBudget() {
        WebClientRequestBuilder request = client.get().path("/response");
        request.readerContext().maxBufferedEntitySize(8);
        WebClientResponse response = request.request().await(TIMEOUT);
        try {
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> response.content().as(String.class).get(10, TimeUnit.SECONDS));
            assertThat(failure.getCause(), instanceOf(IllegalStateException.class));
        } finally {
            response.close().await(TIMEOUT);
        }
    }

    @Test
    void clientStreamRemainsUnlimited() throws Exception {
        WebClient bounded = WebClient.builder().baseUri("http://127.0.0.1:" + server.port())
                .maxBufferedEntitySize(0).build();
        WebClientResponse response = bounded.get().path("/response").request().await(TIMEOUT);
        try (InputStream stream = response.content().as(InputStream.class).await(TIMEOUT)) {
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8), is("client response entity"));
        } finally {
            response.close().await(TIMEOUT);
        }
    }

    @ParameterizedTest
    @ValueSource(classes = {String.class, byte[].class})
    void clientCountsDecodedResponseBytes(Class<?> type) {
        WebClient bounded = WebClient.builder().baseUri("http://127.0.0.1:" + server.port())
                .maxBufferedEntitySize(64).build();
        WebClientResponse response = bounded.get().path("/gzip-response").request().await(TIMEOUT);
        try {
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> response.content().as(type).get(10, TimeUnit.SECONDS));
            assertThat(failure.getCause(), instanceOf(IllegalStateException.class));
        } finally {
            response.close().await(TIMEOUT);
        }
    }

    @Test
    void decodedResponseAtLimitSucceeds() {
        WebClient bounded = WebClient.builder().baseUri("http://127.0.0.1:" + server.port())
                .maxBufferedEntitySize(1024).build();
        WebClientResponse response = bounded.get().path("/gzip-response").request().await(TIMEOUT);
        try {
            assertThat(response.content().as(String.class).await(TIMEOUT), is("x".repeat(1024)));
        } finally {
            response.close().await(TIMEOUT);
        }
    }

    @Test
    void reusingClientBuilderPreservesEachClientBudget() {
        WebClient.Builder builder = WebClient.builder().baseUri("http://127.0.0.1:" + server.port())
                .maxBufferedEntitySize(8);
        WebClient bounded = builder.build();
        WebClient larger = builder.maxBufferedEntitySize(32).build();
        WebClientResponse smallResponse = bounded.get().path("/response").request().await(TIMEOUT);
        try {
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> smallResponse.content().as(String.class).get(10, TimeUnit.SECONDS));
            assertThat(failure.getCause(), instanceOf(IllegalStateException.class));
        } finally {
            smallResponse.close().await(TIMEOUT);
        }
        WebClientResponse largeResponse = larger.get().path("/response").request().await(TIMEOUT);
        try {
            assertThat(largeResponse.content().as(String.class).await(TIMEOUT), is("client response entity"));
        } finally {
            largeResponse.close().await(TIMEOUT);
        }
    }

    @Test
    void multipartPartsHaveIndependentBudgets() {
        WebClientResponse response = client.post().path("/parts")
                .contentType(MediaType.parse("multipart/form-data; boundary=boundary"))
                .submit(multipart("12345678", "abcdefgh")).await(TIMEOUT);
        try {
            assertThat(response.status(), is(Http.Status.OK_200));
            assertThat(response.content().as(String.class).await(TIMEOUT), is("2"));
        } finally {
            response.close().await(TIMEOUT);
        }
    }

    @Test
    void oversizedMultipartPartReturns413() {
        WebClientResponse response = client.post().path("/parts")
                .contentType(MediaType.parse("multipart/form-data; boundary=boundary"))
                .submit(multipart("123456789", "abc")).await(TIMEOUT);
        try {
            assertThat(response.status(), is(Http.Status.REQUEST_ENTITY_TOO_LARGE_413));
        } finally {
            response.close().await(TIMEOUT);
        }
    }

    @Test
    void socketConfigurationExposesTheBudget() {
        Config config = Config.create(ConfigSources.create(Map.of("max-buffered-entity-size", "17")));
        SocketConfiguration configured = SocketConfiguration.builder().config(config).build();
        assertThat(configured.maxBufferedEntitySize(), is(17L));
        assertThat(server.configuration().maxBufferedEntitySize(), is(8L));
        assertThrows(IllegalArgumentException.class, () -> SocketConfiguration.builder().maxBufferedEntitySize(-1));
        assertThrows(IllegalArgumentException.class, () -> WebClient.builder().maxBufferedEntitySize(-1));
    }

    @Test
    void independentPayloadLimitStillAppliesToStreaming() {
        WebServer payloadLimited = WebServer.builder().host("127.0.0.1").port(0)
                .maxPayloadSize(4).maxBufferedEntitySize(32).addRouting(routing()).build().start().await(TIMEOUT);
        try {
            WebClient other = WebClient.builder().baseUri("http://127.0.0.1:" + payloadLimited.port()).build();
            WebClientResponse response = other.post().path("/stream").submit("12345").await(TIMEOUT);
            try {
                assertThat(response.status(), is(Http.Status.REQUEST_ENTITY_TOO_LARGE_413));
            } finally {
                response.close().await(TIMEOUT);
            }
        } finally {
            payloadLimited.shutdown().await(TIMEOUT);
        }
    }

    private static Routing routing() {
        byte[] compressed;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write("x".repeat(1024).getBytes(StandardCharsets.UTF_8));
            gzip.finish();
            compressed = bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Routing.builder()
                .post("/text", Handler.create(String.class, (req, res, value) -> res.send(value)))
                .post("/bytes", Handler.create(byte[].class, (req, res, value) -> res.send(String.valueOf(value.length))))
                .post("/custom", Handler.create(Integer.class, (req, res, value) -> res.send(value.toString())))
                .post("/form", Handler.create(FormParams.class, (req, res, value) -> res.send("accepted")))
                .post("/stream", (req, res) -> Multi.create(req.content()).reduce(() -> 0L, (total, chunk) -> {
                    try {
                        return total + chunk.remaining();
                    } finally {
                        chunk.release();
                    }
                }).thenAccept(length -> res.send(length.toString())).exceptionally(error -> {
                    req.next(error);
                    return null;
                }))
                .post("/parts", (req, res) -> req.content().asStream(ReadableBodyPart.class)
                        .flatMap(part -> part.content().as(String.class)).collectList()
                        .thenAccept(parts -> res.send(String.valueOf(parts.size()))).exceptionally(error -> {
                            req.next(error);
                            return null;
                        }))
                .get("/response", (req, res) -> res.send("client response entity"))
                .get("/gzip-response", (req, res) -> {
                    res.headers().add(Http.Header.CONTENT_ENCODING, "gzip");
                    res.send(compressed);
                })
                .build();
    }

    private static String multipart(String first, String second) {
        return "--boundary\r\nContent-Type: text/plain\r\n\r\n" + first
                + "\r\n--boundary\r\nContent-Type: text/plain\r\n\r\n" + second + "\r\n--boundary--\r\n";
    }

    private static final class LengthReader implements MessageBodyReader<Integer> {
        @Override
        public PredicateResult accept(GenericType<?> type, MessageBodyReaderContext context) {
            return PredicateResult.supports(Integer.class, type);
        }

        @Override
        public <U extends Integer> Single<U> read(Publisher<DataChunk> publisher,
                                                 GenericType<U> type,
                                                 MessageBodyReaderContext context) {
            return ContentReaders.readString(publisher, StandardCharsets.UTF_8).map(String::length).map(type::cast);
        }
    }
}
