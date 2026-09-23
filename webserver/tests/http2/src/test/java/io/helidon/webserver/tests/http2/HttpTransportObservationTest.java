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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.http.HttpTransportObserver;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.http.HttpTransportObserver.Direction;
import io.helidon.http.HttpTransportObserver.Handshake;
import io.helidon.http.HttpTransportObserver.HandshakeObservation;
import io.helidon.http.HttpTransportObserver.Initiator;
import io.helidon.http.HttpTransportObserver.StreamObservation;
import io.helidon.http.HttpTransportObserver.StreamOutcome;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameType;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2Ping;
import io.helidon.http.http2.Http2RstStream;
import io.helidon.webserver.HttpTransportObserverSupport;
import io.helidon.webserver.HttpTransportObserverSupport.ObserverLifecycle;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.spi.ServerFeature;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webserver.testing.junit5.http2.Http2TestClient;
import io.helidon.webserver.testing.junit5.http2.Http2TestConnection;

import org.junit.jupiter.api.Test;

import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_1_1;
import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_2;
import static io.helidon.http.Method.GET;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@ServerTest
class HttpTransportObservationTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Recorder RECORDER = new Recorder();
    private static final BlockedRequest CANCELLED = new BlockedRequest();
    private static final BlockedRequest SIBLING = new BlockedRequest();
    private static final BlockedRequest SHUTDOWN = new BlockedRequest();

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        Keys keys = Keys.builder()
                .keystore(store -> store.passphrase("password").keystore(Resource.create("server.p12")))
                .build();
        server.shutdownGracePeriod(Duration.ZERO)
                .addFeature(RECORDER)
                .putSocket("https", socket -> socket
                        .tls(Tls.builder().privateKey(keys).privateKeyCertChain(keys).build())
                        .routing(HttpTransportObservationTest::routing));
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        routing.get("/ok", (_, response) -> response.send("ok"))
                .get("/fail", (_, _) -> {
                    throw new IllegalStateException("Intentional delivered HTTP 500");
                })
                .get("/cancel", (_, response) -> CANCELLED.handle(response))
                .get("/sibling", (_, response) -> SIBLING.handle(response))
                .get("/shutdown", (_, response) -> SHUTDOWN.handle(response));
    }

    @Test
    void observesProtocolStreamsResetsAndShutdown(WebServer server, Http2TestClient client)
            throws IOException, InterruptedException {
        try (Http2TestConnection connection = client.createConnection()) {
            connection.completeHandshake(TIMEOUT);
            RecordedConnection observed = next(RECORDER.opened, "prior-knowledge connection");
            assertThat(observed.handshake, is(Handshake.NONE));
            assertThat(observed.events, contains("protocol:" + PROTOCOL_HTTP_2));

            request(connection, 1, "/ok");
            response(connection, 1, 200);
            completed(next(observed.opened, "successful stream"), StreamOutcome.COMPLETED);
            request(connection, 3, "/fail");
            response(connection, 3, 500);
            completed(next(observed.opened, "HTTP 500 stream"), StreamOutcome.COMPLETED);

            request(connection, 5, "/cancel");
            await("cancel handler entry", CANCELLED.started);
            RecordedStream cancelled = next(observed.opened, "cancelled stream");
            request(connection, 7, "/sibling");
            await("sibling handler entry", SIBLING.started);
            RecordedStream sibling = next(observed.opened, "sibling stream");
            assertThat("Both multiplexed requests remain active", observed.active.get(), is(2));

            connection.writer().write(new Http2RstStream(Http2ErrorCode.CANCEL)
                                              .toFrameData(null, 5, Http2Flag.NoFlags.create()));
            connection.writer().write(Http2Ping.create().toFrameData());
            assertThat("Reset processing barrier", connection.assertNextFrame(Http2FrameType.PING, TIMEOUT)
                    .header().flags(Http2FrameTypes.PING).ack(), is(true));
            completed(cancelled, StreamOutcome.RESET);
            assertThat("Reset leaves the sibling active", observed.active.get(), is(1));
            assertThat("Reset does not close the sibling observation", sibling.closes.get(), is(0));
            CANCELLED.release.countDown();
            SIBLING.release.countDown();
            response(connection, 7, 200);
            completed(sibling, StreamOutcome.COMPLETED);
            await("cancel handler exit", CANCELLED.finished);
            await("sibling handler exit", SIBLING.finished);

            request(connection, 9, "/ok");
            response(connection, 9, 200);
            completed(next(observed.opened, "request after reset"), StreamOutcome.COMPLETED);

            tlsRequests(server);
            upgradeRequest(server);

            request(connection, 11, "/shutdown");
            await("shutdown handler entry", SHUTDOWN.started);
            RecordedStream pending = next(observed.opened, "shutdown stream");
            assertThat("Shutdown starts with one active stream", observed.active.get(), is(1));
            server.stop();
            await("physical connection close", observed.closed);
            assertThat("Shutdown closes the pending observation", pending.closes.get(), is(1));
            assertThat("Shutdown closes the physical connection once", observed.closes.get(), is(1));
            assertThat("No active streams after shutdown", observed.active.get(), is(0));
            assertThat("Six actual requests on the prior-knowledge connection", observed.streams.size(), is(6));

            for (RecordedConnection recorded : RECORDER.connections) {
                await("connection close", recorded.closed);
                assertThat("Connection callback ordering and serialization", recorded.violations, empty());
                assertThat("Exactly one physical close", recorded.closes.get(), is(1));
                assertThat("No remaining active streams", recorded.active.get(), is(0));
                for (RecordedStream stream : recorded.streams) {
                    assertThat("Exactly one close per opened stream", stream.closes.get(), is(1));
                }
            }
            assertThat("Prior knowledge, TLS, and h2c use three physical connections",
                       RECORDER.connections.size(), is(3));
        } finally {
            CANCELLED.release.countDown();
            SIBLING.release.countDown();
            SHUTDOWN.release.countDown();
            server.stop();
        }
    }

    private static void tlsRequests(WebServer server) throws IOException, InterruptedException {
        Tls tls = Tls.builder()
                .trust(trust -> trust.keystore(store -> store.passphrase("password")
                        .trustStore(true).keystore(Resource.create("client.p12"))))
                .build();
        try (HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2)
                .sslContext(tls.sslContext()).connectTimeout(TIMEOUT).build()) {
            String baseUri = "https://localhost:" + server.port("https");
            jdkRequest(client, baseUri + "/ok", 200);
            RecordedConnection observed = next(RECORDER.opened, "TLS connection");
            assertThat(observed.handshake, is(Handshake.TLS));
            completed(next(observed.opened, "TLS successful stream"), StreamOutcome.COMPLETED);
            jdkRequest(client, baseUri + "/fail", 500);
            completed(next(observed.opened, "TLS HTTP 500 stream"), StreamOutcome.COMPLETED);
            assertThat("Handshake completes before selecting HTTP/2 or opening a stream",
                       observed.events.stream().limit(3).toList(),
                       contains("handshake:start", "handshake:SUCCESS", "protocol:" + PROTOCOL_HTTP_2));
            assertThat("TLS connection carries both requests", observed.streams.size(), is(2));
        }
    }

    private static void upgradeRequest(WebServer server) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2)
                .connectTimeout(TIMEOUT).build()) {
            jdkRequest(client, "http://localhost:" + server.port() + "/ok", 200);
            RecordedConnection observed = next(RECORDER.opened, "h2c upgrade connection");
            RecordedStream upgrade = next(observed.opened, "HTTP/1 upgrade exchange");
            completed(upgrade, StreamOutcome.COMPLETED);
            RecordedStream request = next(observed.opened, "HTTP/2 upgraded request");
            completed(request, StreamOutcome.COMPLETED);
            assertThat("Upgrade keeps the HTTP/1 exchange before the HTTP/2 request", observed.events,
                       contains("protocol:" + PROTOCOL_HTTP_1_1, "stream:open", "stream:COMPLETED",
                                "protocol:" + PROTOCOL_HTTP_2, "stream:open", "stream:COMPLETED"));
        }
    }

    private static void jdkRequest(HttpClient client, String uri, int status) throws IOException, InterruptedException {
        var response = client.send(HttpRequest.newBuilder(URI.create(uri)).timeout(TIMEOUT).GET().build(),
                                   HttpResponse.BodyHandlers.ofString());
        assertThat("Negotiated protocol for " + uri, response.version(), is(HttpClient.Version.HTTP_2));
        assertThat("Delivered status for " + uri, response.statusCode(), is(status));
    }

    private static void request(Http2TestConnection connection, int streamId, String path) {
        Http2Headers headers = Http2Headers.create(WritableHeaders.create())
                .method(GET)
                .path(path)
                .scheme(connection.clientUri().scheme())
                .authority(connection.clientUri().authority());
        connection.writer().writeHeaders(headers, streamId,
                                         Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS
                                                                     | Http2Flag.END_OF_STREAM),
                                         FlowControl.Outbound.NOOP);
    }

    private static void response(Http2TestConnection connection, int streamId, int status) {
        boolean headersReceived = false;
        boolean ended = false;
        while (!ended) {
            var frame = connection.awaitNextFrame(TIMEOUT);
            assertThat("Response frame for stream " + streamId, frame, notNullValue());
            if (frame.header().type() == Http2FrameType.WINDOW_UPDATE) {
                continue;
            }
            assertThat("Response stream ID", frame.header().streamId(), is(streamId));
            if (frame.header().type() == Http2FrameType.HEADERS) {
                assertThat("Response status", connection.assertHeaders(frame, streamId).status().code(), is(status));
                headersReceived = true;
                ended = frame.header().flags(Http2FrameTypes.HEADERS).endOfStream();
            } else {
                assertThat("Response frame type", frame.header().type(), is(Http2FrameType.DATA));
                ended = frame.header().flags(Http2FrameTypes.DATA).endOfStream();
            }
        }
        assertThat("Response included its status headers", headersReceived, is(true));
    }

    private static void completed(RecordedStream stream, StreamOutcome outcome) throws InterruptedException {
        await("stream close with " + outcome, stream.closed);
        assertThat("Stream outcome", stream.outcome, is(outcome));
        assertThat("Exactly one stream close", stream.closes.get(), is(1));
    }

    private static void await(String reason, CountDownLatch latch) throws InterruptedException {
        assertThat("Timed out waiting for " + reason, latch.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is(true));
    }

    private static <T> T next(BlockingQueue<T> queue, String description) throws InterruptedException {
        T value = queue.poll(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        assertThat("Timed out waiting for " + description, value, notNullValue());
        return value;
    }

    private static final class BlockedRequest {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final CountDownLatch finished = new CountDownLatch(1);

        private void handle(ServerResponse response) {
            started.countDown();
            try {
                if (!release.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException("Test did not release the request handler");
                }
                response.send("released");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                finished.countDown();
            }
        }
    }

    private static final class Recorder implements HttpTransportObserver, ObserverLifecycle, ServerFeature {
        private final BlockingQueue<RecordedConnection> opened = new LinkedBlockingQueue<>();
        private final ConcurrentLinkedQueue<RecordedConnection> connections = new ConcurrentLinkedQueue<>();

        @Override
        public String name() {
            return "transport-observation-test";
        }

        @Override
        public String type() {
            return name();
        }

        @Override
        public void setup(ServerFeatureContext context) {
            for (String socket : List.of(WebServer.DEFAULT_SOCKET_NAME, "https")) {
                assertThat("Observer registration",
                           HttpTransportObserverSupport.addObserver(context, socket, this), is(true));
            }
        }

        @Override
        public HttpTransportObserver start() {
            return this;
        }

        @Override
        public CompletionStage<Void> stop() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public ConnectionObservation connectionOpened(Role role, String transport, Handshake handshake) {
            RecordedConnection connection = new RecordedConnection(handshake);
            if (role != Role.SERVER || !TRANSPORT_TCP.equals(transport)) {
                connection.violations.add("Unexpected connection: " + role + "/" + transport);
            }
            connections.add(connection);
            opened.add(connection);
            return connection;
        }
    }

    private static final class RecordedConnection implements ConnectionObservation {
        private final Handshake handshake;
        private final BlockingQueue<RecordedStream> opened = new LinkedBlockingQueue<>();
        private final ConcurrentLinkedQueue<RecordedStream> streams = new ConcurrentLinkedQueue<>();
        private final ConcurrentLinkedQueue<String> events = new ConcurrentLinkedQueue<>();
        private final ConcurrentLinkedQueue<String> violations = new ConcurrentLinkedQueue<>();
        private final AtomicInteger callbacks = new AtomicInteger();
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger closes = new AtomicInteger();
        private final CountDownLatch closed = new CountDownLatch(1);
        private volatile boolean protocolSelected;

        private RecordedConnection(Handshake handshake) {
            this.handshake = handshake;
        }

        @Override
        public HandshakeObservation handshakeStarted() {
            callback("handshake:start", () -> { });
            return outcome -> callback("handshake:" + outcome, () -> { });
        }

        @Override
        public void protocolSelected(String protocol) {
            callback("protocol:" + protocol, () -> protocolSelected = true);
        }

        @Override
        public StreamObservation streamOpened(Direction direction, Initiator initiator) {
            RecordedStream stream = new RecordedStream(this);
            callback("stream:open", () -> {
                if (!protocolSelected || direction != Direction.BIDIRECTIONAL || initiator != Initiator.REMOTE) {
                    violations.add("Stream opened before protocol selection or with incorrect direction/initiator");
                }
                active.incrementAndGet();
                streams.add(stream);
                opened.add(stream);
            });
            return stream;
        }

        @Override
        public void close(ConnectionOutcome outcome) {
            callback("connection:" + outcome, () -> {
                closes.incrementAndGet();
                if (active.get() != 0) {
                    violations.add("Physical close before closing all streams");
                }
            });
            closed.countDown();
        }

        private void callback(String event, Runnable action) {
            if (callbacks.incrementAndGet() != 1) {
                violations.add("Concurrent callback: " + event);
            }
            try {
                if (closes.get() != 0) {
                    violations.add("Callback after connection close: " + event);
                }
                events.add(event);
                action.run();
            } finally {
                callbacks.decrementAndGet();
            }
        }
    }

    private static final class RecordedStream implements StreamObservation {
        private final RecordedConnection connection;
        private final AtomicInteger closes = new AtomicInteger();
        private final CountDownLatch closed = new CountDownLatch(1);
        private volatile StreamOutcome outcome;

        private RecordedStream(RecordedConnection connection) {
            this.connection = connection;
        }

        @Override
        public void close(StreamOutcome outcome) {
            connection.callback("stream:" + outcome, () -> {
                this.outcome = outcome;
                closes.incrementAndGet();
                connection.active.decrementAndGet();
            });
            closed.countDown();
        }
    }
}
