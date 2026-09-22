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

package io.helidon.webclient.tests.http3;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.Status;
import io.helidon.http.http3.Http3ErrorCode;
import io.helidon.http.http3.Http3GoAway;
import io.helidon.http.http3.Http3Protocol;
import io.helidon.http.http3.Http3Settings;
import io.helidon.http.http3.Http3StreamSupport;
import io.helidon.quic.QuicCloseCommand;
import io.helidon.quic.QuicConnection;
import io.helidon.quic.QuicConnectionImpl;
import io.helidon.quic.QuicTermination;
import io.helidon.quic.stream.QuicSenderStream;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientProtocolConfig;
import io.helidon.webclient.http3.Http3ClientResponse;
import io.helidon.webserver.TcpTransportConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http3.Http3RawTestServer;

import org.junit.jupiter.api.Test;

import static io.helidon.http.HttpTransportObserver.StreamOutcome.COMPLETED;
import static io.helidon.http.HttpTransportObserver.StreamOutcome.REJECTED;
import static io.helidon.webclient.tests.http3.Http3ClientTestSupport.strictClientBuilder;
import static io.helidon.webclient.tests.http3.Http3ClientTestSupport.strictWebClientBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3GoAwayTest {
    private static final String ACCEPTED_POST_GOAWAY_PATH = "/accepted-post-goaway";
    private static final String CONNECTION_REJECTED_PATH = "/connection-rejected";
    private static final String REQUEST_REJECTED_PATH = "/request-rejected";
    private static final String STREAM_STOP_PATH = "/stream-stop";
    private static final String STREAM_STOP_REUSE_PATH = "/stream-stop-reuse";
    private static final String VERSION_FALLBACK_PATH = "/version-fallback";
    private static final String VERSION_FALLBACK_REUSE_PATH = "/version-fallback-reuse";

    @Test
    void shouldRetireTypedHttp3ClientConnectionAfterServerGoAway() throws Exception {
        AtomicReference<Http3RawTestServer> serverRef = new AtomicReference<>();
        AtomicBoolean goAwaySent = new AtomicBoolean();
        List<QuicConnection> acceptedConnections = new CopyOnWriteArrayList<>();

        try (Http3RawTestServer server = Http3RawTestServer.create((_, connection, streamId, _) -> {
                 if (goAwaySent.compareAndSet(false, true)) {
                     serverRef.get().sendGoAway(connection, streamId + 4);
                 }
                 return Http3RawTestServer.text(200, connection.childSocketId());
             }, serverRef, acceptedConnections::add)) {
            Http3Client client = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .build();

            try {
                String firstConnectionId;
                try (Http3ClientResponse first = client.get("/goaway").request()) {
                    assertThat(first.status(), is(Status.OK_200));
                    assertThat(first.protocolId(), is(Http3Client.PROTOCOL_ID));
                    firstConnectionId = first.as(String.class);
                }

                awaitAcceptedConnections(acceptedConnections, 1);
                QuicTermination termination = acceptedConnections.getFirst()
                        .whenTerminated()
                        .toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);
                assertThat(termination.origin(), is(QuicTermination.Origin.PEER));
                assertThat(termination.kind(), is(QuicTermination.Kind.CONNECTION_CLOSE));
                assertThat(termination.layer(), is(QuicTermination.Layer.APPLICATION));
                assertThat(termination.errorCode().orElseThrow(), is(Http3ErrorCode.NO_ERROR.code()));

                try (Http3ClientResponse second = client.get("/goaway").request()) {
                    assertThat(second.status(), is(Status.OK_200));
                    assertThat(second.protocolId(), is(Http3Client.PROTOCOL_ID));
                    assertThat(second.as(String.class), is(not(firstConnectionId)));
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldRetireExplicitGenericHttp3ConnectionAfterServerGoAway() throws Exception {
        AtomicReference<Http3RawTestServer> serverRef = new AtomicReference<>();
        AtomicBoolean goAwaySent = new AtomicBoolean();

        try (Http3RawTestServer server = Http3RawTestServer.create((_, connection, streamId, _) -> {
                 if (goAwaySent.compareAndSet(false, true)) {
                     serverRef.get().sendGoAway(connection, streamId + 4);
                 }
                 return Http3RawTestServer.text(200, connection.childSocketId());
             }, serverRef)) {
            WebClient client = strictWebClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .addProtocolPreference(Http3Client.PROTOCOL_ID)
                    .addProtocolPreference(Http1Client.PROTOCOL_ID)
                    .build();

            try {
                try (HttpClientResponse first = client.get("/goaway")
                             .protocolId(Http3Client.PROTOCOL_ID)
                             .request();
                     HttpClientResponse second = client.get("/goaway")
                             .protocolId(Http3Client.PROTOCOL_ID)
                             .request()) {
                    assertThat(first.status(), is(Status.OK_200));
                    assertThat(first.protocolId(), is(Http3Client.PROTOCOL_ID));
                    String firstConnectionId = first.as(String.class);

                    assertThat(second.status(), is(Status.OK_200));
                    assertThat(second.protocolId(), is(Http3Client.PROTOCOL_ID));
                    assertThat(second.as(String.class), is(not(firstConnectionId)));
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldRejectIncreasingGoAwayWithoutSendingDiagnosticByDefault() throws Exception {
        assertIncreasingPeerGoAway(false, "");
    }

    @Test
    void shouldSendIncreasingGoAwayDiagnosticWhenConfigured() throws Exception {
        assertIncreasingPeerGoAway(true, "HTTP/3 GOAWAY identifier increased from 8 to 12");
    }

    @Test
    void shouldNotRetryAcceptedTypedHttp3PostAfterGoAwayReset() throws Exception {
        AtomicReference<Http3RawTestServer> serverRef = new AtomicReference<>();
        AtomicInteger requestCount = new AtomicInteger();

        try (Http3RawTestServer server = Http3RawTestServer.create((request, connection, streamId, stream) -> {
                 if (!ACCEPTED_POST_GOAWAY_PATH.equals(request.path().orElseThrow())) {
                     return Http3RawTestServer.text(Status.NOT_FOUND_404.code(), request.path().orElseThrow());
                 }

                 int attempt = requestCount.incrementAndGet();
                 if (attempt == 1) {
                     serverRef.get().sendGoAway(connection, streamId + 4);
                     try {
                         TimeUnit.MILLISECONDS.sleep(50);
                     } catch (InterruptedException e) {
                         Thread.currentThread().interrupt();
                         throw new IllegalStateException("Interrupted while waiting to reset the HTTP/3 stream.", e);
                     }
                     stream.reset(Http3ErrorCode.INTERNAL_ERROR.code());
                     return null;
                 }

                 return Http3RawTestServer.text(Status.OK_200.code(), "retried");
             }, serverRef)) {
            Http3Client client = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .build();

            try {
                assertThrows(RuntimeException.class, () -> client.post(ACCEPTED_POST_GOAWAY_PATH).submit("payload"));
                assertThat(requestCount.get(), is(1));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldRetryTypedHttp3PostWhenServerRejectsRequestBeforeProcessing() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        RecordingTransportObserverService observer = new RecordingTransportObserverService();

        try (Http3RawTestServer server = Http3RawTestServer.create((request, _, _, stream) -> {
                 if (!REQUEST_REJECTED_PATH.equals(request.path().orElseThrow())) {
                     return Http3RawTestServer.text(Status.NOT_FOUND_404.code(), request.path().orElseThrow());
                 }

                 if (requestCount.incrementAndGet() == 1) {
                    stream.reset(Http3ErrorCode.REQUEST_REJECTED.code());
                    return null;
                 }

                 return Http3RawTestServer.text(Status.OK_200.code(), "retry-ok");
             })) {
            Http3Client client = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .servicesDiscoverServices(false)
                    .addService(observer)
                    .build();

            try {
                try (Http3ClientResponse response = client.post(REQUEST_REJECTED_PATH).submit("payload")) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.as(String.class), is("retry-ok"));
                    assertThat(requestCount.get(), is(2));
                }
                assertThat(observer.connections().stream()
                                   .flatMap(it -> it.streams().stream())
                                   .map(it -> it.outcome().orTimeout(10, TimeUnit.SECONDS).join())
                                   .toList(),
                           contains(REJECTED, COMPLETED));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldRetryTypedHttp3PostAfterRequestRejectedStopSending() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        CountDownLatch retryObserved = new CountDownLatch(1);
        RecordingTransportObserverService observer = new RecordingTransportObserverService();

        try (Http3RawTestServer server = Http3RawTestServer.create((request, connection, streamId, _) -> {
                 if (!REQUEST_REJECTED_PATH.equals(request.path().orElseThrow())) {
                     return Http3RawTestServer.text(Status.NOT_FOUND_404.code(), request.path().orElseThrow());
                 }

                 if (requestCount.incrementAndGet() == 1) {
                     ((QuicConnectionImpl) connection).scheduleStopSendingFrame(streamId,
                                                                                Http3ErrorCode.REQUEST_REJECTED.code());
                     if (!await(retryObserved)) {
                         return Http3RawTestServer.text(Status.INTERNAL_SERVER_ERROR_500.code(),
                                                       "Retry was not observed");
                     }
                     return null;
                 }

                 retryObserved.countDown();
                 return Http3RawTestServer.text(Status.OK_200.code(), "retry-ok");
             })) {
            Http3Client client = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .servicesDiscoverServices(false)
                    .addService(observer)
                    .build();

            try {
                try (Http3ClientResponse response = client.post(REQUEST_REJECTED_PATH).submit("payload")) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.as(String.class), is("retry-ok"));
                    assertThat(requestCount.get(), is(2));
                }
                assertThat(observer.connections().stream()
                                   .flatMap(it -> it.streams().stream())
                                   .map(it -> it.outcome().orTimeout(10, TimeUnit.SECONDS).join())
                                   .toList(),
                           contains(REJECTED, COMPLETED));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldNotRetryPostForConnectionLevelRequestRejectedCode() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();

        try (Http3RawTestServer server = Http3RawTestServer.create((request, connection, _, _) -> {
                 if (!CONNECTION_REJECTED_PATH.equals(request.path().orElseThrow())) {
                     return Http3RawTestServer.text(Status.NOT_FOUND_404.code(), request.path().orElseThrow());
                 }

                 requestCount.incrementAndGet();
                 connection.terminate(QuicCloseCommand.application(Http3ErrorCode.REQUEST_REJECTED.code(),
                                                                   "connection-level rejection"));
                 return null;
             })) {
            Http3Client client = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .build();

            try {
                assertThrows(RuntimeException.class,
                             () -> client.post(CONNECTION_REJECTED_PATH).submit("payload"));
                assertThat(requestCount.get(), is(1));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldNotReplayOneShotBodyAfterStreamRejection() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        AtomicInteger producerCount = new AtomicInteger();

        try (Http3RawTestServer server = Http3RawTestServer.create((_, _, _, stream) -> {
                 requestCount.incrementAndGet();
                 stream.reset(Http3ErrorCode.REQUEST_REJECTED.code());
                 return null;
             })) {
            Http3Client client = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .build();

            try {
                assertThrows(RuntimeException.class,
                             () -> client.post(REQUEST_REJECTED_PATH).outputStream(outputStream -> {
                                 producerCount.incrementAndGet();
                                 outputStream.write("payload".getBytes(StandardCharsets.UTF_8));
                                 outputStream.close();
                             }));
                assertThat(producerCount.get(), is(1));
                assertThat(requestCount.get(), is(1));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldFallbackToHttp1AfterStreamVersionFallback() throws Exception {
        AtomicInteger http3RequestCount = new AtomicInteger();
        AtomicInteger http1RequestCount = new AtomicInteger();
        AtomicReference<QuicConnection> firstConnection = new AtomicReference<>();
        CountDownLatch versionFallbackObserved = new CountDownLatch(1);

        try (Http3RawTestServer server = Http3RawTestServer.create((request, connection, streamId, stream) -> {
                 http3RequestCount.incrementAndGet();
                 if (VERSION_FALLBACK_PATH.equals(request.path().orElseThrow())) {
                     firstConnection.set(connection);
                     String requestBody;
                     try {
                         requestBody = new String(stream.requestBodyInputStream().readAllBytes(),
                                                  StandardCharsets.UTF_8);
                     } catch (IOException e) {
                         throw new UncheckedIOException("Failed to read the HTTP/3 request body.", e);
                     }
                     if (!"payload".equals(requestBody)) {
                         throw new IllegalStateException("Unexpected request body: " + requestBody);
                     }
                     ((QuicConnectionImpl) connection).scheduleStopSendingFrame(
                             streamId,
                             Http3ErrorCode.VERSION_FALLBACK.code());
                     if (!await(versionFallbackObserved)) {
                         return Http3RawTestServer.text(Status.INTERNAL_SERVER_ERROR_500.code(),
                                                       "Version fallback was not observed");
                     }
                     return null;
                 }
                 return Http3RawTestServer.text(Status.OK_200.code(),
                                                connection == firstConnection.get() ? "same-connection" : "new-connection");
             })) {
            Http3TlsSupport.Http3TlsMaterials tlsMaterials = Http3TlsSupport.load();
            int port = URI.create(server.baseUri()).getPort();
            WebServer fallbackServer = WebServer.builder()
                    .address(InetAddress.getLoopbackAddress())
                    .port(port)
                    .bindingsDiscoverServices(false)
                    .addBinding(TcpTransportConfig.create())
                    .protocolsDiscoverServices(false)
                    .tls(tlsMaterials.serverTls())
                    .addProtocol(Http1Config.create())
                    .routing(routing -> routing.post(VERSION_FALLBACK_PATH, (_, response) -> {
                        http1RequestCount.incrementAndGet();
                        versionFallbackObserved.countDown();
                        response.send("fallback-ok");
                    }))
                    .build()
                    .start();
            try {
                WebClient client = WebClient.builder()
                        .baseUri(server.baseUri())
                        .shareConnectionCache(false)
                        .proxy(Proxy.noProxy())
                        .tls(tlsMaterials.clientTls())
                        .addProtocolPreference(Http3Client.PROTOCOL_ID)
                        .addProtocolPreference(Http1Client.PROTOCOL_ID)
                        .addProtocolConfig(Http3ClientProtocolConfig.create())
                        .build();
                try {
                    try (HttpClientResponse response = client.post(VERSION_FALLBACK_PATH)
                            .protocolId(Http3Client.PROTOCOL_ID)
                            .submit("payload")) {
                        assertThat(response.status(), is(Status.OK_200));
                        assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                        assertThat(response.as(String.class), is("fallback-ok"));
                    }
                    try (HttpClientResponse response = client.get(VERSION_FALLBACK_REUSE_PATH)
                            .protocolId(Http3Client.PROTOCOL_ID)
                            .request()) {
                        assertThat(response.status(), is(Status.OK_200));
                        assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                        assertThat(response.as(String.class), is("same-connection"));
                    }
                    assertThat(http3RequestCount.get(), is(2));
                    assertThat(http1RequestCount.get(), is(1));
                } finally {
                    client.closeResource();
                }
            } finally {
                fallbackServer.stop();
            }
        }
    }

    @Test
    void shouldKeepConnectionAfterStreamErrorStopsActiveUpload() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        AtomicReference<QuicConnection> firstConnection = new AtomicReference<>();
        CountDownLatch producerStarted = new CountDownLatch(1);

        try (Http3RawTestServer server = Http3RawTestServer.create((request, connection, streamId, _) -> {
                 requestCount.incrementAndGet();
                 if (STREAM_STOP_PATH.equals(request.path().orElseThrow())) {
                     firstConnection.set(connection);
                     if (!await(producerStarted)) {
                         return Http3RawTestServer.text(Status.INTERNAL_SERVER_ERROR_500.code(),
                                                       "Request producer did not start");
                     }
                     ((QuicConnectionImpl) connection).scheduleStopSendingFrame(
                             streamId,
                             Http3ErrorCode.MESSAGE_ERROR.code());
                     return null;
                 }
                 return Http3RawTestServer.text(Status.OK_200.code(),
                                                connection == firstConnection.get() ? "same-connection" : "new-connection");
             })) {
            Http3Client client = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .build();
            byte[] chunk = new byte[64 * 1024];
            try {
                assertThrows(RuntimeException.class,
                             () -> client.post(STREAM_STOP_PATH).outputStream(outputStream -> {
                                 producerStarted.countDown();
                                 for (int i = 0; i < 256; i++) {
                                     outputStream.write(chunk);
                                 }
                                 outputStream.close();
                             }));
                try (Http3ClientResponse response = client.get(STREAM_STOP_REUSE_PATH).request()) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.as(String.class), is("same-connection"));
                }
                assertThat(requestCount.get(), is(2));
            } finally {
                client.closeResource();
            }
        }
    }

    private static void awaitAcceptedConnections(List<QuicConnection> acceptedConnections, int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (acceptedConnections.size() < expected && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        assertThat(acceptedConnections.size(), is(expected));
    }

    private static boolean await(CountDownLatch latch) {
        try {
            return latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while awaiting HTTP/3 test coordination.", e);
        }
    }

    private static void assertIncreasingPeerGoAway(boolean sendErrorDetails, String expectedPeerReason) throws Exception {
        CompletableFuture<QuicTermination> peerTermination = new CompletableFuture<>();
        try (Http3RawTestServer server = Http3RawTestServer.createRawPeer(connection -> {
                 connection.whenTerminated().whenComplete((termination, throwable) -> {
                     if (throwable == null) {
                         peerTermination.complete(termination);
                     } else {
                         peerTermination.completeExceptionally(throwable);
                     }
                 });
                 QuicSenderStream controlStream = connection.openNewLocalUniStream(Duration.ofSeconds(10)).join();
                 BufferData controlData = BufferData.growing(128);
                 controlData.write(Http3Protocol.controlStreamPreamble(Http3Settings.create(0, 0)));
                 controlData.write(Http3Protocol.goAwayFrame(Http3GoAway.requestStream(8)));
                 controlData.write(Http3Protocol.goAwayFrame(Http3GoAway.requestStream(12)));
                 Http3StreamSupport.writeAll(controlStream, controlData.readBytes(), false, connection);
             })) {
            Http3Client client = strictClientBuilder(Http3ClientProtocolConfig.builder()
                                                             .sendErrorDetails(sendErrorDetails))
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .build();
            try {
                assertThrows(RuntimeException.class, () -> client.get("/invalid-goaway").request());
                QuicTermination termination = peerTermination.get(10, TimeUnit.SECONDS);
                assertThat(termination.origin(), is(QuicTermination.Origin.PEER));
                assertThat(termination.kind(), is(QuicTermination.Kind.CONNECTION_CLOSE));
                assertThat(termination.layer(), is(QuicTermination.Layer.APPLICATION));
                assertThat(termination.errorCode().orElseThrow(), is(Http3ErrorCode.ID_ERROR.code()));
                assertThat(termination.peerReason().orElse(""), is(expectedPeerReason));
            } finally {
                client.closeResource();
            }
        }
    }
}
