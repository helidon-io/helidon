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

package io.helidon.webserver.testing.junit5.http3;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.SocketContext;
import io.helidon.common.tls.Tls;
import io.helidon.http.HeaderNames;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http3.Http3ControlStreamListener;
import io.helidon.http.http3.Http3ControlStreamSupport;
import io.helidon.http.http3.Http3FrameListener;
import io.helidon.http.http3.Http3GoAway;
import io.helidon.http.http3.Http3PeerCriticalStreams;
import io.helidon.http.http3.Http3Protocol;
import io.helidon.http.http3.Http3Settings;
import io.helidon.http.http3.Http3StreamObservation;
import io.helidon.http.http3.Http3StreamSupport;
import io.helidon.http.http3.Http3StreamType;
import io.helidon.quic.QuicConfig;
import io.helidon.quic.QuicConnection;
import io.helidon.quic.QuicRemoteStreamRegistration;
import io.helidon.quic.QuicServerRuntime;
import io.helidon.quic.QuicVersion;
import io.helidon.quic.VariableLengthEncoder;
import io.helidon.quic.stream.QuicBidiStream;
import io.helidon.quic.stream.QuicSenderStream;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;

class Http3LowLevelClientProtocolTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final byte[] BODY = "response-body".getBytes(StandardCharsets.UTF_8);

    @Test
    void returnsFinalResponseAfterInformationalHeaders() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor();
             QuicServerRuntime server = server(executor)) {
            server.start();
            CompletableFuture<QuicConnection> accepted = server.accept();
            try (Http3LowLevelClient client = client(server);
                 Peer peer = new Peer(accepted.get(10, TimeUnit.SECONDS), executor)) {
                peer.prime(false);
                byte[] informational = Http3Protocol.encodeResponseHeaders(103,
                        WritableHeaders.create().set(HeaderNames.LINK, "</style.css>; rel=preload"));
                byte[] finalHeaders = Http3Protocol.encodeResponseHeaders(200,
                        WritableHeaders.create().set(HeaderNames.CONTENT_TYPE, "text/plain"));
                CompletableFuture<Void> sent = peer.respond(frames(informational,
                                                                   finalHeaders,
                                                                   Http3Protocol.encodeDataFrame(BODY)));

                Http3LowLevelClient.DecodedResponse response = client.get("/informational");
                sent.get(10, TimeUnit.SECONDS);

                assertAll(
                        () -> assertThat(response.status(), is(200)),
                        () -> assertThat(response.headers().first(HeaderNames.CONTENT_TYPE).orElseThrow(), is("text/plain")),
                        () -> assertThat(response.headers().contains(HeaderNames.LINK), is(false)),
                        () -> assertThat(response.headersPayloadLength(), is(payloadLength(finalHeaders))),
                        () -> assertThat(response.body(), is(BODY)));
            }
        }
    }

    @Test
    void acknowledgesDynamicTrailersWithoutReplacingFinalHeaderMeasurements() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor();
             QuicServerRuntime server = server(executor)) {
            server.start();
            CompletableFuture<QuicConnection> accepted = server.accept();
            try (Http3LowLevelClient client = client(server);
                 Peer peer = new Peer(accepted.get(10, TimeUnit.SECONDS), executor)) {
                peer.prime(true);
                byte[] finalHeaders = Http3Protocol.encodeResponseHeaders(200,
                        WritableHeaders.create().set(HeaderNames.CONTENT_TYPE, "text/plain"));
                // Required Insert Count = 1, Base = 1, dynamic relative index = 0 (RFC 9204 sections 4.5.1 and 4.5.2).
                byte[] trailers = {0x01, 0x03, 0x02, 0x00, (byte) 0x80};
                CompletableFuture<Void> sent = peer.respond(frames(finalHeaders,
                                                                   Http3Protocol.encodeDataFrame(BODY),
                                                                   trailers));

                Http3LowLevelClient.DecodedResponse response = client.get("/trailers");
                sent.get(10, TimeUnit.SECONDS);

                assertAll(
                        () -> assertThat(response.status(), is(200)),
                        () -> assertThat(response.body(), is(BODY)),
                        () -> assertThat(response.headersPayloadLength(), is(payloadLength(finalHeaders))),
                        () -> assertThat("dynamic trailer must receive a QPACK Section Acknowledgment",
                                         peer.trailerAcknowledged.await(10, TimeUnit.SECONDS),
                                         is(true)));
            }
        }
    }

    private static QuicServerRuntime server(Executor executor) {
        return QuicServerRuntime.builder()
                .executor(executor)
                .bindAddress(new InetSocketAddress("localhost", 0))
                .tls(Http3AbstractTestingTest.serverTls())
                .applicationProtocols(List.of(Http3Protocol.ALPN))
                .quicConfig(QuicConfig.builder().availableVersions(List.of(QuicVersion.QUIC_V1)).buildPrototype())
                .build();
    }

    private static Http3LowLevelClient client(QuicServerRuntime server) {
        Tls tls = Tls.builder()
                .trust(Http3AbstractTestingTest.serverTls().prototype().privateKeyCertChain())
                .applicationProtocols(List.of(Http3Protocol.ALPN))
                .enabledProtocols(List.of("TLSv1.3"))
                .build();
        return Http3LowLevelClient.create(URI.create("https://localhost:" + server.localAddress().getPort() + "/"), tls);
    }

    private static byte[] frames(byte[]... frames) {
        BufferData bytes = BufferData.growing(256);
        for (byte[] frame : frames) {
            bytes.write(frame);
        }
        return bytes.readBytes();
    }

    private static int payloadLength(byte[] frame) {
        ByteBuffer bytes = ByteBuffer.wrap(frame);
        assertThat(VariableLengthEncoder.decode(bytes), is(Http3Protocol.FRAME_HEADERS));
        return Math.toIntExact(VariableLengthEncoder.decode(bytes));
    }

    private static final class Peer implements AutoCloseable {
        private final QuicConnection connection;
        private final Executor executor;
        private final CompletableFuture<QuicBidiStream> request = new CompletableFuture<>();
        private final CompletableFuture<Http3Settings> settings = new CompletableFuture<>();
        private final CountDownLatch trailerAcknowledged = new CountDownLatch(1);
        private final AtomicLong decoderStreamId = new AtomicLong(-1);
        private final Http3PeerCriticalStreams criticalStreams = Http3PeerCriticalStreams.create();
        private final List<Http3StreamObservation> observations = new CopyOnWriteArrayList<>();
        private final QuicRemoteStreamRegistration registration;

        private Peer(QuicConnection connection, Executor executor) {
            this.connection = connection;
            this.executor = executor;
            Http3FrameListener frames = new Http3FrameListener() {
                @Override
                public boolean rawDataEnabled() {
                    return true;
                }

                @Override
                public void streamType(SocketContext context, long streamId, Http3StreamType streamType, int encodedLength) {
                    if (streamType == Http3StreamType.QPACK_DECODER) {
                        decoderStreamId.set(streamId);
                    }
                }

                @Override
                public void rawStreamData(SocketContext context, long streamId, String label, byte[] data) {
                    if (streamId == decoderStreamId.get()) {
                        for (byte value : data) {
                            // Stream 0 has the only dynamic field section; one insert cannot produce a multi-byte increment.
                            if (value == (byte) 0x80) {
                                trailerAcknowledged.countDown();
                            }
                        }
                    }
                }
            };
            Http3ControlStreamListener control = new Http3ControlStreamListener() {
                @Override
                public void onSettings(Http3Settings received) {
                    settings.complete(received);
                }

                @Override
                public void onGoAway(Http3GoAway goAway) {
                }
            };
            registration = connection.addRemoteStreamListener(stream -> {
                if (stream instanceof QuicBidiStream bidi) {
                    request.complete(bidi);
                } else {
                    Http3StreamObservation observation = Http3ControlStreamSupport.observe(stream,
                                                                                           Optional::empty,
                                                                                           criticalStreams,
                                                                                           connection,
                                                                                           frames,
                                                                                           control);
                    observations.add(observation);
                    observation.completion().whenComplete((_, failure) -> {
                        if (failure != null) {
                            settings.completeExceptionally(failure);
                            request.completeExceptionally(failure);
                        }
                    });
                }
                return true;
            });
        }

        @Override
        public void close() {
            registration.close();
            observations.forEach(Http3StreamObservation::close);
            criticalStreams.close(new IllegalStateException("test peer closed"));
            request.cancel(false);
        }

        private void prime(boolean dynamicTrailer) throws Exception {
            openUni(Http3StreamType.CONTROL, Http3Protocol.controlStreamPreamble(Http3Settings.create(0, 0)));
            byte[] encoder = Http3Protocol.qpackUniStreamPreamble(Http3StreamType.QPACK_ENCODER);
            if (dynamicTrailer) {
                assertThat(settings.get(10, TimeUnit.SECONDS).qpackMaxTableCapacity(), greaterThanOrEqualTo(64L));
                // Capacity 64; insert literal name x-trailer with value present (RFC 9204 sections 4.3.1 and 4.3.3).
                encoder = frames(encoder,
                                 new byte[] {0x3f, 0x21, 0x49},
                                 "x-trailer".getBytes(StandardCharsets.US_ASCII),
                                 new byte[] {0x07},
                                 "present".getBytes(StandardCharsets.US_ASCII));
            }
            openUni(Http3StreamType.QPACK_ENCODER, encoder);
            openUni(Http3StreamType.QPACK_DECODER, Http3Protocol.qpackUniStreamPreamble(Http3StreamType.QPACK_DECODER));
        }

        private CompletableFuture<Void> respond(byte[] frames) {
            return request.thenAcceptAsync(stream -> {
                assertThat(stream.streamId(), is(0L));
                Http3StreamSupport.connectWriter(stream, connection).scheduleForWriting(BufferData.create(frames), true);
            }, executor);
        }

        private void openUni(Http3StreamType type, byte[] bytes) throws Exception {
            QuicSenderStream stream = connection.openNewLocalUniStream(TIMEOUT).get(10, TimeUnit.SECONDS);
            Http3StreamSupport.connectWriter(stream, connection, type).scheduleForWriting(BufferData.create(bytes), false);
        }
    }
}
