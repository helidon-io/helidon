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

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpException;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.ReadableEntity;
import io.helidon.webclient.api.HttpClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientProtocolConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequestEntity;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2ServerRequestEntity;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ServerTest
class EntityByteArrayLengthTest {
    private static final byte[] PAYLOAD = new byte[16384];
    private static final byte[] PREFIX = {7, 8, 9};

    static {
        for (int i = 0; i < PAYLOAD.length; i++) {
            PAYLOAD[i] = (byte) i;
        }
    }

    private final Http1Client http1;
    private final Http2Client http2;

    EntityByteArrayLengthTest(WebServer server) {
        var baseUri = URI.create("http://localhost:" + server.port());
        http1 = Http1Client.builder().baseUri(baseUri).shareConnectionCache(false).build();
        http2 = Http2Client.builder()
                .baseUri(baseUri)
                .protocolConfig(Http2ClientProtocolConfig.builder().priorKnowledge(true).build())
                .shareConnectionCache(false)
                .build();
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        server.addProtocol(Http2Config.create());
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routes) {
        routes.post("/bytes", (req, res) -> res.send(req.content().as(byte[].class)))
                .post("/filtered", (req, res) -> {
                    req.streamFilter(stream -> new SequenceInputStream(new ByteArrayInputStream(PREFIX), stream));
                    res.send(req.content().as(byte[].class));
                })
                .post("/shortened", (req, res) -> {
                    req.streamFilter(stream -> {
                        try {
                            stream.skipNBytes(PREFIX.length);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                        return stream;
                    });
                    res.send(req.content().as(byte[].class));
                })
                .post("/rewritten-length", (req, res) -> {
                    req.header(HeaderValues.create(HeaderNames.CONTENT_LENGTH, "1"));
                    res.send(req.content().as(byte[].class));
                })
                .get("/alive", (req, res) -> res.send("alive"));
    }

    @AfterEach
    void closeClients() {
        http1.closeResource();
        http2.closeResource();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void exactReadAcrossBuffersCompletesEntity(boolean useHttp2) {
        var fixture = new Fixture(useHttp2, PAYLOAD, ContentDecoder.NO_OP, -1, PAYLOAD.length, false);

        assertThat(fixture.entity.as(byte[].class), is(PAYLOAD));
        assertThat(fixture.entity.consumed(), is(true));
        assertThat(fixture.processed.get(), is(1));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void copyPreservesExactLength(boolean useHttp2) {
        var fixture = new Fixture(useHttp2, PAYLOAD, ContentDecoder.NO_OP, -1, PAYLOAD.length + 1, false);
        var copy = fixture.entity.copy(() -> { });

        var failure = assertThrows(UncheckedIOException.class, () -> copy.as(byte[].class));

        assertThat(failure.getCause(), instanceOf(EOFException.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void oldFactoryDoesNotInferLengthFromHeaders(boolean useHttp2) {
        var fixture = new Fixture(useHttp2, PAYLOAD, ContentDecoder.NO_OP, -1, PAYLOAD.length + 1, true);

        assertThat(fixture.entity.as(byte[].class), is(PAYLOAD));
        assertThat(fixture.processed.get(), is(1));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void decoderDisablesExactLength(boolean useHttp2) {
        ContentDecoder decoder = stream -> new SequenceInputStream(new ByteArrayInputStream(PREFIX), stream);
        var fixture = new Fixture(useHttp2, PAYLOAD, decoder, -1, PAYLOAD.length, false);

        assertThat(fixture.entity.as(byte[].class), is(prefixed(PAYLOAD)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void payloadLimitRetainsItsFailure(boolean useHttp2) {
        var fixture = new Fixture(useHttp2, PAYLOAD, ContentDecoder.NO_OP, 32, PAYLOAD.length, false);

        var failure = assertThrows(HttpException.class, () -> fixture.entity.as(byte[].class));

        assertThat(failure.status(), is(Status.REQUEST_ENTITY_TOO_LARGE_413));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void uploadsPreserveBytesAndFollowingRequest(boolean useHttp2) {
        HttpClient<?> client = useHttp2 ? http2 : http1;
        for (int length : new int[] {16384, 1024 * 1024, 1024 * 1024 + 1}) {
            byte[] payload = Arrays.copyOf(PAYLOAD, length);
            try (var response = client.post("/bytes").submit(payload)) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.entity().as(byte[].class), is(payload));
            }
        }
        try (var response = client.get("/alive").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity().as(String.class), is("alive"));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void expandingRequestFilterUsesActualLength(boolean useHttp2) {
        HttpClient<?> client = useHttp2 ? http2 : http1;
        try (var response = client.post("/filtered").submit(PAYLOAD)) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity().as(byte[].class), is(prefixed(PAYLOAD)));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shorteningRequestFilterUsesActualLength(boolean useHttp2) {
        HttpClient<?> client = useHttp2 ? http2 : http1;
        try (var response = client.post("/shortened").submit(PAYLOAD)) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity().as(byte[].class), is(Arrays.copyOfRange(PAYLOAD, PREFIX.length, PAYLOAD.length)));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void applicationHeaderChangeDoesNotChangeTransportLength(boolean useHttp2) {
        HttpClient<?> client = useHttp2 ? http2 : http1;
        try (var response = client.post("/rewritten-length").submit(PAYLOAD)) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity().as(byte[].class), is(PAYLOAD));
        }
    }

    private static byte[] prefixed(byte[] payload) {
        byte[] result = Arrays.copyOf(PREFIX, PREFIX.length + payload.length);
        System.arraycopy(payload, 0, result, PREFIX.length, payload.length);
        return result;
    }

    private static class Fixture {
        private final AtomicInteger processed = new AtomicInteger();
        private final ReadableEntity entity;
        private int offset;

        Fixture(boolean http2, byte[] payload, ContentDecoder decoder, long maxPayloadSize, long exactLength, boolean legacy) {
            Function<Integer, BufferData> reading = _ -> {
                if (offset == payload.length) {
                    return BufferData.empty();
                }
                int count = Math.min(127, payload.length - offset);
                BufferData result = BufferData.createReadOnly(payload, offset, count);
                offset += count;
                return result;
            };
            var headers = ServerRequestHeaders.create(WritableHeaders.create()
                                                               .set(HeaderValues.create(HeaderNames.CONTENT_LENGTH,
                                                                                        Long.toString(exactLength))));
            var context = MediaContext.create();
            UnaryOperator<InputStream> filter = UnaryOperator.identity();
            if (http2) {
                entity = legacy
                        ? Http2ServerRequestEntity.create(filter, decoder, reading, processed::incrementAndGet,
                                                          headers, context, maxPayloadSize, 1024)
                        : Http2ServerRequestEntity.create(filter, decoder, reading, processed::incrementAndGet,
                                                          headers, context, maxPayloadSize, 1024, exactLength);
            } else {
                entity = legacy
                        ? ServerRequestEntity.create(_ -> { }, filter, decoder, reading, processed::incrementAndGet,
                                                     headers, context, maxPayloadSize, 1024)
                        : ServerRequestEntity.create(_ -> { }, filter, decoder, reading, processed::incrementAndGet,
                                                     headers, context, maxPayloadSize, 1024, exactLength);
            }
        }
    }
}
