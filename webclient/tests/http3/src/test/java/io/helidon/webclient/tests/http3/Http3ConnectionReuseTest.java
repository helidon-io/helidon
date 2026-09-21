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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.tls.Tls;
import io.helidon.common.tls.TlsMaterial;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http3.Http3Protocol;
import io.helidon.quic.QuicConnection;
import io.helidon.quic.QuicStreamLimitException;
import io.helidon.webclient.api.ClientAltSvcConfig;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientProtocolConfig;
import io.helidon.webclient.http3.Http3ClientResponse;
import io.helidon.webserver.http3.Http3RawTestServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.http.HttpTransportObserver.ConnectionOutcome.LOCAL_CLOSE;
import static io.helidon.http.HttpTransportObserver.Direction.BIDIRECTIONAL;
import static io.helidon.http.HttpTransportObserver.Handshake.QUIC_TLS;
import static io.helidon.http.HttpTransportObserver.HandshakeOutcome.SUCCESS;
import static io.helidon.http.HttpTransportObserver.Initiator.LOCAL;
import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_3;
import static io.helidon.http.HttpTransportObserver.Role.CLIENT;
import static io.helidon.http.HttpTransportObserver.StreamOutcome.COMPLETED;
import static io.helidon.http.HttpTransportObserver.TRANSPORT_QUIC;
import static io.helidon.webclient.tests.http3.Http3ClientTestSupport.strictClientBuilder;
import static io.helidon.webclient.tests.http3.Http3ClientTestSupport.strictWebClientBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isIn;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3ConnectionReuseTest {
    private TestEnvironment environment;

    @BeforeEach
    void beforeEach() throws Exception {
        environment = TestEnvironment.createSharedListener(routing -> routing
                .get("/socket-id", (req, res) -> res.send(req.socketId())));
    }

    @AfterEach
    void afterEach() {
        if (environment != null) {
            environment.close();
        }
    }

    @Test
    void shouldReuseConnectionForTypedHttp3Client() {
        RecordingTransportObserverService observer = new RecordingTransportObserverService();
        Http3Client client = strictClientBuilder()
                .baseUri(environment.baseUri())
                .tls(environment.clientTlsHttp3())
                .servicesDiscoverServices(false)
                .addService(observer)
                .build();

        try {
            String firstSocketId;
            try (Http3ClientResponse first = client.get("/socket-id").request()) {
                assertThat(first.status(), is(Status.OK_200));
                assertThat(first.protocolId(), is(Http3Client.PROTOCOL_ID));
                firstSocketId = first.as(String.class);
            }

            try (Http3ClientResponse second = client.get("/socket-id").request()) {
                assertThat(second.status(), is(Status.OK_200));
                assertThat(second.protocolId(), is(Http3Client.PROTOCOL_ID));
                assertThat(second.as(String.class), is(firstSocketId));
            }
        } finally {
            client.closeResource();
        }

        assertThat(observer.requests(), is(2));
        assertThat(observer.registrationsOpened(), is(1));
        assertThat(observer.registrationsClosed(), is(1));
        assertThat(observer.registrationCompletions(), is(1));
        assertThat(observer.connections().size(), is(1));

        RecordingTransportObserverService.ConnectionRecord connection = observer.connections().getFirst();
        assertThat(connection.role(), is(CLIENT));
        assertThat(connection.transport(), is(TRANSPORT_QUIC));
        assertThat(connection.handshake(), is(QUIC_TLS));
        assertThat(connection.handshakeOutcome().orTimeout(10, TimeUnit.SECONDS).join(),
                   is(SUCCESS));
        assertThat(connection.protocols(), contains(PROTOCOL_HTTP_3));
        assertThat(connection.streams().size(), is(2));
        assertThat(connection.streams().stream()
                           .map(RecordingTransportObserverService.StreamRecord::direction)
                           .toList(),
                   everyItem(is(BIDIRECTIONAL)));
        assertThat(connection.streams().stream()
                           .map(RecordingTransportObserverService.StreamRecord::initiator)
                           .toList(),
                   everyItem(is(LOCAL)));
        assertThat(connection.streams().stream()
                           .map(it -> it.outcome().orTimeout(10, TimeUnit.SECONDS).join())
                           .toList(),
                   everyItem(is(COMPLETED)));
        assertThat(connection.outcome().orTimeout(10, TimeUnit.SECONDS).join(),
                   is(LOCAL_CLOSE));

        List<String> events = observer.events();
        assertThat(events.indexOf("connection-open"), lessThan(events.indexOf("handshake-start")));
        assertThat(events.indexOf("handshake-SUCCESS"), lessThan(events.indexOf("protocol-" + PROTOCOL_HTTP_3)));
        assertThat(events.indexOf("connection-LOCAL_CLOSE"), lessThan(events.indexOf("registration-close")));
    }

    @Test
    void shouldReuseConnectionForExplicitGenericHttp3Request() {
        WebClient client = strictWebClientBuilder()
                .baseUri(environment.baseUri())
                .tls(environment.clientTlsHttp3())
                .addProtocolPreference(Http3Client.PROTOCOL_ID)
                .addProtocolPreference(Http1Client.PROTOCOL_ID)
                .build();

        try {
            String firstSocketId;
            try (HttpClientResponse first = client.get("/socket-id")
                    .protocolId(Http3Client.PROTOCOL_ID)
                    .request()) {
                assertThat(first.status(), is(Status.OK_200));
                assertThat(first.protocolId(), is(Http3Client.PROTOCOL_ID));
                firstSocketId = first.as(String.class);
            }

            try (HttpClientResponse second = client.get("/socket-id")
                    .protocolId(Http3Client.PROTOCOL_ID)
                    .request()) {
                assertThat(second.status(), is(Status.OK_200));
                assertThat(second.protocolId(), is(Http3Client.PROTOCOL_ID));
                assertThat(second.as(String.class), is(firstSocketId));
            }
        } finally {
            client.closeResource();
        }
    }

    @Test
    void shouldExpandToConfiguredCapacityWithoutOvershootingAndReuseBothConnections() throws Exception {
        StreamHold hold = new StreamHold();
        List<QuicConnection> acceptedConnections = new CopyOnWriteArrayList<>();
        try (Http3RawTestServer server = Http3RawTestServer.create(hold::handle, acceptedConnections::add, 1)) {
            Http3Client client = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .connectionCacheSize(2)
                    .build();

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                try (Http3ClientResponse heldResponse = client.get(StreamHold.HOLD_PATH).request()) {
                    assertThat(heldResponse.status(), is(Status.OK_200));
                    String firstConnectionId = hold.connectionId();
                    awaitAcceptedConnections(acceptedConnections, 1);

                    CountDownLatch start = new CountDownLatch(1);
                    CountDownLatch ready = new CountDownLatch(8);
                    List<CompletableFuture<String>> concurrentRequests = new ArrayList<>();
                    for (int i = 0; i < 8; i++) {
                        concurrentRequests.add(CompletableFuture.supplyAsync(() -> {
                            ready.countDown();
                            await(start);
                            return requestConnectionId(client, "/concurrent-" + Thread.currentThread().threadId());
                        }, executor));
                    }
                    await(ready);
                    start.countDown();
                    CompletableFuture.allOf(concurrentRequests.toArray(CompletableFuture<?>[]::new))
                            .get(20, TimeUnit.SECONDS);

                    List<String> concurrentConnectionIds = concurrentRequests.stream()
                            .map(CompletableFuture::join)
                            .toList();
                    String secondConnectionId = concurrentConnectionIds.getFirst();
                    assertThat(secondConnectionId, is(not(firstConnectionId)));
                    assertThat(concurrentConnectionIds, everyItem(is(secondConnectionId)));
                    assertThat(acceptedConnections.size(), is(2));

                    hold.complete();
                    assertThat(heldResponse.as(String.class), is(firstConnectionId));

                    String reusedConnectionId = requestConnectionId(client, "/reuse");
                    assertThat(reusedConnectionId, isIn(List.of(firstConnectionId, secondConnectionId)));
                    assertThat(acceptedConnections.size(), is(2));
                }
                client.closeResource();
                for (QuicConnection connection : acceptedConnections) {
                    connection.whenTerminated().toCompletableFuture().get(10, TimeUnit.SECONDS);
                    assertThat(connection.isOpen(), is(false));
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldWaitForStreamCreditAtCapacityOne() throws Exception {
        StreamHold hold = new StreamHold();
        List<QuicConnection> acceptedConnections = new CopyOnWriteArrayList<>();
        try (Http3RawTestServer server = Http3RawTestServer.create(hold::handle, acceptedConnections::add, 1)) {
            Http3Client client = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .connectionCacheSize(1)
                    .build();

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                try (Http3ClientResponse heldResponse = client.get(StreamHold.HOLD_PATH).request()) {
                    assertThat(heldResponse.status(), is(Status.OK_200));
                    String firstConnectionId = hold.connectionId();
                    awaitAcceptedConnections(acceptedConnections, 1);

                    CountDownLatch requestStarted = new CountDownLatch(1);
                    CompletableFuture<String> waitingRequest = CompletableFuture.supplyAsync(() -> {
                        requestStarted.countDown();
                        return requestConnectionId(client, "/wait-for-credit");
                    }, executor);
                    await(requestStarted);
                    assertThrows(TimeoutException.class,
                                 () -> waitingRequest.get(500, TimeUnit.MILLISECONDS));
                    assertThat(acceptedConnections.size(), is(1));

                    hold.complete();
                    assertThat(heldResponse.as(String.class), is(firstConnectionId));
                    assertThat(waitingRequest.get(10, TimeUnit.SECONDS), is(firstConnectionId));
                    assertThat(acceptedConnections.size(), is(1));
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldCompleteEquivalentQueuedRequestsOnceAndReuseDrainedConnection() throws Exception {
        StreamHold hold = new StreamHold("/hold-equivalent-requests");
        AtomicInteger queuedRequests = new AtomicInteger();
        AtomicInteger drainedRequests = new AtomicInteger();
        List<QuicConnection> acceptedConnections = new CopyOnWriteArrayList<>();
        try (Http3RawTestServer server = Http3RawTestServer.create((request, connection, _, stream) -> {
                 String path = request.path().orElseThrow();
                 if (hold.path().equals(path)) {
                     hold.begin(connection, stream);
                     return null;
                 }
                 if ("/equivalent-queued".equals(path)) {
                     queuedRequests.incrementAndGet();
                 } else if ("/after-equivalent-queue".equals(path)) {
                     drainedRequests.incrementAndGet();
                 }
                 return Http3RawTestServer.text(Status.OK_200.code(), connection.childSocketId());
             }, acceptedConnections::add, 1)) {
            Http3ClientProtocolConfig.Builder protocolConfig = Http3ClientProtocolConfig.builder()
                    .streamOpenTimeout(Duration.ofSeconds(30));
            Http3Client client = strictClientBuilder(protocolConfig)
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .connectionCacheSize(1)
                    .build();
            List<AtomicInteger> completions = new ArrayList<>();

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                try (Http3ClientResponse heldResponse = client.get(hold.path()).request()) {
                    assertThat(heldResponse.status(), is(Status.OK_200));
                    String connectionId = hold.connectionId();
                    assertThat(acceptedConnections.size(), is(1));

                    CountDownLatch requestsStarted = new CountDownLatch(3);
                    List<CompletableFuture<String>> waitingRequests = new ArrayList<>();
                    for (int i = 0; i < 3; i++) {
                        AtomicInteger completionCount = new AtomicInteger();
                        completions.add(completionCount);
                        waitingRequests.add(CompletableFuture.supplyAsync(() -> {
                            requestsStarted.countDown();
                            return requestConnectionId(client, "/equivalent-queued");
                        }, executor).whenComplete((_, _) -> completionCount.incrementAndGet()));
                    }
                    await(requestsStarted);
                    for (CompletableFuture<String> waitingRequest : waitingRequests) {
                        assertThrows(TimeoutException.class,
                                     () -> waitingRequest.get(500, TimeUnit.MILLISECONDS));
                    }
                    assertThat(queuedRequests.get(), is(0));
                    assertThat(acceptedConnections.size(), is(1));

                    hold.complete();
                    assertThat(heldResponse.as(String.class), is(connectionId));
                    for (CompletableFuture<String> waitingRequest : waitingRequests) {
                        assertThat(waitingRequest.get(10, TimeUnit.SECONDS), is(connectionId));
                    }
                    assertThat(queuedRequests.get(), is(3));
                    assertThat(requestConnectionId(client, "/after-equivalent-queue"), is(connectionId));
                    assertThat(drainedRequests.get(), is(1));
                    assertThat(acceptedConnections.size(), is(1));
                } finally {
                    try {
                        hold.completeIfRegistered();
                    } finally {
                        client.closeResource();
                    }
                }
            } finally {
                client.closeResource();
            }

            assertThat(completions.stream().map(AtomicInteger::get).toList(), is(List.of(1, 1, 1)));
            assertThat(queuedRequests.get(), is(3));
            assertThat(drainedRequests.get(), is(1));
        }
    }

    @Test
    void shouldUseRecoveredSessionWhileAnotherSessionHandshakeRemainsPending() throws Exception {
        StreamHold hold = new StreamHold("/hold-during-handshake");
        AtomicInteger handshakeRequests = new AtomicInteger();
        AtomicInteger recoveryRequests = new AtomicInteger();
        List<QuicConnection> acceptedConnections = new CopyOnWriteArrayList<>();
        CountDownLatch secondConnectionAccepted = new CountDownLatch(1);
        CountDownLatch continueSecondHandshake = new CountDownLatch(1);
        try (Http3RawTestServer server = Http3RawTestServer.create((request, connection, _, stream) -> {
                 if (hold.path().equals(request.path().orElseThrow())) {
                     hold.begin(connection, stream);
                     return null;
                 }
                 if ("/create-pending-handshake".equals(request.path().orElseThrow())) {
                     handshakeRequests.incrementAndGet();
                 } else if ("/recover-during-handshake".equals(request.path().orElseThrow())) {
                     recoveryRequests.incrementAndGet();
                 }
                 return Http3RawTestServer.text(Status.OK_200.code(), connection.childSocketId());
             }, connection -> {
                 acceptedConnections.add(connection);
                 if (acceptedConnections.size() == 2) {
                     secondConnectionAccepted.countDown();
                     await(continueSecondHandshake);
                 }
             }, 1)) {
            Http3ClientProtocolConfig.Builder protocolConfig = Http3ClientProtocolConfig.builder()
                    .handshakeTimeout(Duration.ofSeconds(30))
                    .streamOpenTimeout(Duration.ofSeconds(30));
            Http3Client client = strictClientBuilder(protocolConfig)
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .connectionCacheSize(2)
                    .build();

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                try (Http3ClientResponse heldResponse = client.get(hold.path()).request()) {
                    assertThat(heldResponse.status(), is(Status.OK_200));
                    String firstConnectionId = hold.connectionId();
                    awaitAcceptedConnections(acceptedConnections, 1);

                    CompletableFuture<String> handshakeRequest = CompletableFuture.supplyAsync(
                            () -> requestConnectionId(client, "/create-pending-handshake"),
                            executor);
                    await(secondConnectionAccepted);
                    assertThrows(TimeoutException.class,
                                 () -> handshakeRequest.get(500, TimeUnit.MILLISECONDS));

                    CountDownLatch recoveryStarted = new CountDownLatch(1);
                    CompletableFuture<String> recoveryRequest = CompletableFuture.supplyAsync(() -> {
                        recoveryStarted.countDown();
                        return requestConnectionId(client, "/recover-during-handshake");
                    }, executor);
                    await(recoveryStarted);
                    assertThrows(TimeoutException.class,
                                 () -> recoveryRequest.get(500, TimeUnit.MILLISECONDS));
                    assertThat(handshakeRequests.get(), is(0));
                    assertThat(recoveryRequests.get(), is(0));
                    assertThat(acceptedConnections.size(), is(2));

                    hold.complete();
                    assertThat(heldResponse.as(String.class), is(firstConnectionId));
                    assertThat(recoveryRequest.get(10, TimeUnit.SECONDS), is(firstConnectionId));
                    assertRequestCountRemains(recoveryRequests, 1);
                    assertThat(handshakeRequest.isDone(), is(false));
                    assertThat(handshakeRequests.get(), is(0));
                    assertThat(continueSecondHandshake.getCount(), is(1L));
                    assertThat(acceptedConnections.size(), is(2));

                    continueSecondHandshake.countDown();
                    assertThat(handshakeRequest.get(10, TimeUnit.SECONDS), is(not(firstConnectionId)));
                    assertRequestCountRemains(handshakeRequests, 1);
                    assertThat(acceptedConnections.size(), is(2));
                } finally {
                    continueSecondHandshake.countDown();
                    hold.completeIfRegistered();
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldTimeOutQueuedRequestWithoutRetiringReusableSession() throws Exception {
        StreamHold hold = new StreamHold("/hold-for-timeout");
        AtomicInteger timedOutRequests = new AtomicInteger();
        List<QuicConnection> acceptedConnections = new CopyOnWriteArrayList<>();
        try (Http3RawTestServer server = Http3RawTestServer.create((request, connection, _, stream) -> {
                 if (hold.path().equals(request.path().orElseThrow())) {
                     hold.begin(connection, stream);
                     return null;
                 }
                 if ("/stream-open-timeout".equals(request.path().orElseThrow())) {
                     timedOutRequests.incrementAndGet();
                 }
                 return Http3RawTestServer.text(Status.OK_200.code(), connection.childSocketId());
             }, acceptedConnections::add, 1)) {
            Http3ClientProtocolConfig.Builder protocolConfig = Http3ClientProtocolConfig.builder()
                    .streamOpenTimeout(Duration.ofMillis(250));
            Http3Client client = strictClientBuilder(protocolConfig)
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .connectionCacheSize(1)
                    .build();

            try (Http3ClientResponse heldResponse = client.get(hold.path()).request()) {
                assertThat(heldResponse.status(), is(Status.OK_200));
                String connectionId = hold.connectionId();
                awaitAcceptedConnections(acceptedConnections, 1);

                RuntimeException failure = assertThrows(
                        RuntimeException.class,
                        () -> client.get("/stream-open-timeout").priorKnowledge(true).request());
                assertThat(causedBy(failure, QuicStreamLimitException.class), is(true));
                assertThat(timedOutRequests.get(), is(0));
                assertThat(acceptedConnections.size(), is(1));

                hold.complete();
                assertThat(heldResponse.as(String.class), is(connectionId));
                assertThat(requestConnectionId(client, "/reuse-after-stream-open-timeout"), is(connectionId));
                assertRequestCountRemains(timedOutRequests, 0);
                assertThat(acceptedConnections.size(), is(1));
            } finally {
                hold.completeIfRegistered();
                client.closeResource();
            }
        }
    }

    @Test
    void shouldWakeOnCreditFromNonPreferredSessionAtFullCapacity() throws Exception {
        StreamHold firstHold = new StreamHold("/hold-first");
        StreamHold preferredHold = new StreamHold("/hold-preferred");
        AtomicInteger queuedRequests = new AtomicInteger();
        List<QuicConnection> acceptedConnections = new CopyOnWriteArrayList<>();
        try (Http3RawTestServer server = Http3RawTestServer.create((request, connection, _, stream) -> {
                 String path = request.path().orElseThrow();
                 if (firstHold.path().equals(path)) {
                     firstHold.begin(connection, stream);
                     return null;
                 }
                 if (preferredHold.path().equals(path)) {
                     preferredHold.begin(connection, stream);
                     return null;
                 }
                 if ("/wake-non-preferred".equals(path)) {
                     queuedRequests.incrementAndGet();
                 }
                 return Http3RawTestServer.text(Status.OK_200.code(), connection.childSocketId());
             }, acceptedConnections::add, 1)) {
            Http3Client client = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .connectionCacheSize(2)
                    .build();

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                try (Http3ClientResponse firstResponse = client.get(firstHold.path()).request();
                     Http3ClientResponse preferredResponse = client.get(preferredHold.path()).request()) {
                    assertThat(firstResponse.status(), is(Status.OK_200));
                    assertThat(preferredResponse.status(), is(Status.OK_200));
                    String firstConnectionId = firstHold.connectionId();
                    String preferredConnectionId = preferredHold.connectionId();
                    assertThat(preferredConnectionId, is(not(firstConnectionId)));
                    awaitAcceptedConnections(acceptedConnections, 2);

                    CountDownLatch requestStarted = new CountDownLatch(1);
                    CompletableFuture<String> waitingRequest = CompletableFuture.supplyAsync(() -> {
                        requestStarted.countDown();
                        return requestConnectionId(client, "/wake-non-preferred");
                    }, executor);
                    await(requestStarted);
                    assertThrows(TimeoutException.class,
                                 () -> waitingRequest.get(500, TimeUnit.MILLISECONDS));
                    assertThat(queuedRequests.get(), is(0));
                    assertThat(acceptedConnections.size(), is(2));

                    firstHold.complete();
                    assertThat(firstResponse.as(String.class), is(firstConnectionId));
                    assertThat(waitingRequest.get(10, TimeUnit.SECONDS), is(firstConnectionId));
                    assertThat(queuedRequests.get(), is(1));
                    assertThat(preferredHold.completed(), is(false));
                    assertThat(acceptedConnections.size(), is(2));

                    preferredHold.complete();
                    assertThat(preferredResponse.as(String.class), is(preferredConnectionId));
                } finally {
                    firstHold.completeIfRegistered();
                    preferredHold.completeIfRegistered();
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldOpenOneRequestWhenBothFullPoolSessionsRegainCreditTogether() throws Exception {
        StreamHold firstHold = new StreamHold("/race-hold-first");
        StreamHold secondHold = new StreamHold("/race-hold-second");
        AtomicInteger queuedRequests = new AtomicInteger();
        List<QuicConnection> acceptedConnections = new CopyOnWriteArrayList<>();
        try (Http3RawTestServer server = Http3RawTestServer.create((request, connection, _, stream) -> {
                 String path = request.path().orElseThrow();
                 if (firstHold.path().equals(path)) {
                     firstHold.begin(connection, stream);
                     return null;
                 }
                 if (secondHold.path().equals(path)) {
                     secondHold.begin(connection, stream);
                     return null;
                 }
                 if ("/race-one-request".equals(path)) {
                     queuedRequests.incrementAndGet();
                 }
                 return Http3RawTestServer.text(Status.OK_200.code(), connection.childSocketId());
             }, acceptedConnections::add, 1)) {
            Http3Client client = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .connectionCacheSize(2)
                    .build();

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                try (Http3ClientResponse firstResponse = client.get(firstHold.path()).request();
                     Http3ClientResponse secondResponse = client.get(secondHold.path()).request()) {
                    assertThat(firstResponse.status(), is(Status.OK_200));
                    assertThat(secondResponse.status(), is(Status.OK_200));
                    String firstConnectionId = firstHold.connectionId();
                    String secondConnectionId = secondHold.connectionId();
                    assertThat(secondConnectionId, is(not(firstConnectionId)));
                    awaitAcceptedConnections(acceptedConnections, 2);

                    CountDownLatch requestStarted = new CountDownLatch(1);
                    CompletableFuture<String> waitingRequest = CompletableFuture.supplyAsync(() -> {
                        requestStarted.countDown();
                        return requestConnectionId(client, "/race-one-request");
                    }, executor);
                    await(requestStarted);
                    assertThrows(TimeoutException.class,
                                 () -> waitingRequest.get(500, TimeUnit.MILLISECONDS));

                    CountDownLatch releaseReady = new CountDownLatch(2);
                    CountDownLatch releaseStart = new CountDownLatch(1);
                    CompletableFuture<Void> releaseFirst = CompletableFuture.runAsync(() -> {
                        releaseReady.countDown();
                        await(releaseStart);
                        firstHold.complete();
                    }, executor);
                    CompletableFuture<Void> releaseSecond = CompletableFuture.runAsync(() -> {
                        releaseReady.countDown();
                        await(releaseStart);
                        secondHold.complete();
                    }, executor);
                    await(releaseReady);
                    releaseStart.countDown();
                    CompletableFuture.allOf(releaseFirst, releaseSecond).get(10, TimeUnit.SECONDS);
                    assertThat(firstResponse.as(String.class), is(firstConnectionId));
                    assertThat(secondResponse.as(String.class), is(secondConnectionId));
                    assertThat(waitingRequest.get(10, TimeUnit.SECONDS),
                               isIn(List.of(firstConnectionId, secondConnectionId)));
                    assertRequestCountRemains(queuedRequests, 1);
                    assertThat(acceptedConnections.size(), is(2));
                } finally {
                    firstHold.completeIfRegistered();
                    secondHold.completeIfRegistered();
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldOpenEachQueuedRequestOnceWhenBothFullPoolSessionsRegainCredit() throws Exception {
        StreamHold firstHold = new StreamHold("/two-hold-first");
        StreamHold secondHold = new StreamHold("/two-hold-second");
        AtomicInteger firstQueuedRequests = new AtomicInteger();
        AtomicInteger secondQueuedRequests = new AtomicInteger();
        List<QuicConnection> acceptedConnections = new CopyOnWriteArrayList<>();
        try (Http3RawTestServer server = Http3RawTestServer.create((request, connection, _, stream) -> {
                 String path = request.path().orElseThrow();
                 if (firstHold.path().equals(path)) {
                     firstHold.begin(connection, stream);
                     return null;
                 }
                 if (secondHold.path().equals(path)) {
                     secondHold.begin(connection, stream);
                     return null;
                 }
                 if ("/queued-first".equals(path)) {
                     firstQueuedRequests.incrementAndGet();
                 } else if ("/queued-second".equals(path)) {
                     secondQueuedRequests.incrementAndGet();
                 }
                 return Http3RawTestServer.text(Status.OK_200.code(), connection.childSocketId());
             }, acceptedConnections::add, 1)) {
            Http3Client client = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .connectionCacheSize(2)
                    .build();

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                try (Http3ClientResponse firstResponse = client.get(firstHold.path()).request();
                     Http3ClientResponse secondResponse = client.get(secondHold.path()).request()) {
                    assertThat(firstResponse.status(), is(Status.OK_200));
                    assertThat(secondResponse.status(), is(Status.OK_200));
                    String firstConnectionId = firstHold.connectionId();
                    String secondConnectionId = secondHold.connectionId();
                    assertThat(secondConnectionId, is(not(firstConnectionId)));
                    awaitAcceptedConnections(acceptedConnections, 2);

                    CountDownLatch requestsStarted = new CountDownLatch(2);
                    CompletableFuture<String> firstRequest = CompletableFuture.supplyAsync(() -> {
                        requestsStarted.countDown();
                        return requestConnectionId(client, "/queued-first");
                    }, executor);
                    CompletableFuture<String> secondRequest = CompletableFuture.supplyAsync(() -> {
                        requestsStarted.countDown();
                        return requestConnectionId(client, "/queued-second");
                    }, executor);
                    await(requestsStarted);
                    assertThrows(TimeoutException.class,
                                 () -> firstRequest.get(500, TimeUnit.MILLISECONDS));
                    assertThrows(TimeoutException.class,
                                 () -> secondRequest.get(500, TimeUnit.MILLISECONDS));

                    CountDownLatch releaseReady = new CountDownLatch(2);
                    CountDownLatch releaseStart = new CountDownLatch(1);
                    CompletableFuture<Void> releaseFirst = CompletableFuture.runAsync(() -> {
                        releaseReady.countDown();
                        await(releaseStart);
                        firstHold.complete();
                    }, executor);
                    CompletableFuture<Void> releaseSecond = CompletableFuture.runAsync(() -> {
                        releaseReady.countDown();
                        await(releaseStart);
                        secondHold.complete();
                    }, executor);
                    await(releaseReady);
                    releaseStart.countDown();
                    CompletableFuture.allOf(releaseFirst, releaseSecond).get(10, TimeUnit.SECONDS);
                    assertThat(firstResponse.as(String.class), is(firstConnectionId));
                    assertThat(secondResponse.as(String.class), is(secondConnectionId));
                    assertThat(firstRequest.get(10, TimeUnit.SECONDS),
                               isIn(List.of(firstConnectionId, secondConnectionId)));
                    assertThat(secondRequest.get(10, TimeUnit.SECONDS),
                               isIn(List.of(firstConnectionId, secondConnectionId)));
                    assertRequestCountRemains(firstQueuedRequests, 1);
                    assertRequestCountRemains(secondQueuedRequests, 1);
                    assertThat(acceptedConnections.size(), is(2));
                } finally {
                    firstHold.completeIfRegistered();
                    secondHold.completeIfRegistered();
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldReplaceDrainingSessionWhileItsRequestFinishes() throws Exception {
        StreamHold hold = new StreamHold();
        AtomicReference<Http3RawTestServer> serverRef = new AtomicReference<>();
        List<QuicConnection> acceptedConnections = new CopyOnWriteArrayList<>();
        try (Http3RawTestServer server = Http3RawTestServer.create((request, connection, streamId, stream) -> {
                 if (StreamHold.HOLD_PATH.equals(request.path().orElseThrow())) {
                     serverRef.get()
                             .sendGoAway(connection, streamId + 4)
                             .orTimeout(10, TimeUnit.SECONDS)
                             .join();
                     hold.begin(connection, stream);
                     return null;
                 }
                 return Http3RawTestServer.text(Status.OK_200.code(), connection.childSocketId());
             }, serverRef, acceptedConnections::add, 1)) {
            Http3Client client = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .connectionCacheSize(1)
                    .build();

            try (Http3ClientResponse heldResponse = client.get(StreamHold.HOLD_PATH).request()) {
                assertThat(heldResponse.status(), is(Status.OK_200));
                String firstConnectionId = hold.connectionId();
                awaitAcceptedConnections(acceptedConnections, 1);
                QuicConnection drainingConnection = acceptedConnections.getFirst();

                String replacementConnectionId = requestConnectionId(client, "/replacement");
                awaitAcceptedConnections(acceptedConnections, 2);
                assertThat(replacementConnectionId, is(not(firstConnectionId)));
                assertThat(drainingConnection.isOpen(), is(true));

                hold.complete();
                assertThat(heldResponse.as(String.class), is(firstConnectionId));
                drainingConnection.whenTerminated().toCompletableFuture().get(10, TimeUnit.SECONDS);

                assertThat(requestConnectionId(client, "/reuse-replacement"), is(replacementConnectionId));
                assertThat(acceptedConnections.size(), is(2));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldKeepSmallerFirstSharedPoolCapacityForTarget() throws Exception {
        StreamHold hold = new StreamHold();
        List<QuicConnection> acceptedConnections = new CopyOnWriteArrayList<>();
        try (Http3RawTestServer server = Http3RawTestServer.create(hold::handle, acceptedConnections::add, 1)) {
            Tls tls = server.clientTlsHttp3();
            Http3Client firstClient = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(tls)
                    .shareConnectionCache(true)
                    .connectionCacheSize(1)
                    .build();
            Http3Client secondClient = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(tls)
                    .shareConnectionCache(true)
                    .connectionCacheSize(2)
                    .build();

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                try (Http3ClientResponse heldResponse = firstClient.get(StreamHold.HOLD_PATH).request()) {
                    assertThat(heldResponse.status(), is(Status.OK_200));
                    String firstConnectionId = hold.connectionId();
                    awaitAcceptedConnections(acceptedConnections, 1);

                    CountDownLatch requestStarted = new CountDownLatch(1);
                    CompletableFuture<String> waitingRequest = CompletableFuture.supplyAsync(() -> {
                        requestStarted.countDown();
                        return requestConnectionId(secondClient, "/shared-first-capacity");
                    }, executor);
                    await(requestStarted);
                    assertThrows(TimeoutException.class,
                                 () -> waitingRequest.get(500, TimeUnit.MILLISECONDS));
                    assertThat(acceptedConnections.size(), is(1));

                    hold.complete();
                    assertThat(heldResponse.as(String.class), is(firstConnectionId));
                    assertThat(waitingRequest.get(10, TimeUnit.SECONDS), is(firstConnectionId));
                    assertThat(acceptedConnections.size(), is(1));
                }
            } finally {
                try {
                    secondClient.closeResource();
                } finally {
                    firstClient.closeResource();
                }
            }
        }
    }

    @Test
    void shouldKeepLargerFirstSharedPoolCapacityForTarget() throws Exception {
        StreamHold hold = new StreamHold();
        List<QuicConnection> acceptedConnections = new CopyOnWriteArrayList<>();
        try (Http3RawTestServer server = Http3RawTestServer.create(hold::handle, acceptedConnections::add, 1)) {
            Tls tls = server.clientTlsHttp3();
            Http3Client firstClient = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(tls)
                    .shareConnectionCache(true)
                    .connectionCacheSize(2)
                    .build();
            Http3Client secondClient = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(tls)
                    .shareConnectionCache(true)
                    .connectionCacheSize(1)
                    .build();

            try (Http3ClientResponse heldResponse = firstClient.get(StreamHold.HOLD_PATH).request()) {
                assertThat(heldResponse.status(), is(Status.OK_200));
                String firstConnectionId = hold.connectionId();
                awaitAcceptedConnections(acceptedConnections, 1);

                String secondConnectionId = requestConnectionId(secondClient, "/shared-larger-first-capacity");
                awaitAcceptedConnections(acceptedConnections, 2);
                assertThat(secondConnectionId, is(not(firstConnectionId)));

                hold.complete();
                assertThat(heldResponse.as(String.class), is(firstConnectionId));
                assertThat(requestConnectionId(secondClient, "/shared-larger-first-reuse"),
                           isIn(List.of(firstConnectionId, secondConnectionId)));
                assertThat(acceptedConnections.size(), is(2));
            } finally {
                try {
                    secondClient.closeResource();
                } finally {
                    firstClient.closeResource();
                }
            }
        }
    }

    @Test
    void shouldRetireEveryPooledSessionAfterTlsReloadWithoutRemovingReplacement() throws Exception {
        StreamHold oldHold = new StreamHold();
        StreamHold replacementHold = new StreamHold("/hold-after-reload");
        List<QuicConnection> acceptedConnections = new CopyOnWriteArrayList<>();
        try (Http3RawTestServer server = Http3RawTestServer.create((request, connection, _, stream) -> {
                 String path = request.path().orElseThrow();
                 if (oldHold.path().equals(path)) {
                     oldHold.begin(connection, stream);
                     return null;
                 }
                 if (replacementHold.path().equals(path)) {
                     replacementHold.begin(connection, stream);
                     return null;
                 }
                 return Http3RawTestServer.text(Status.OK_200.code(), connection.childSocketId());
             }, acceptedConnections::add, 1)) {
            Tls tls = server.clientTlsHttp3();
            Http3Client client = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(tls)
                    .connectionCacheSize(2)
                    .build();

            try (Http3ClientResponse heldResponse = client.get(oldHold.path()).request()) {
                assertThat(heldResponse.status(), is(Status.OK_200));
                String firstConnectionId = oldHold.connectionId();
                String secondConnectionId = requestConnectionId(client, "/second-before-reload");
                awaitAcceptedConnections(acceptedConnections, 2);
                assertThat(secondConnectionId, is(not(firstConnectionId)));

                QuicConnection activeOldConnection = acceptedConnections.getFirst();
                QuicConnection idleOldConnection = acceptedConnections.get(1);
                tls.reload(TlsMaterial.builder().trustAll(true).build());

                try (Http3ClientResponse replacementHeldResponse = client.get(replacementHold.path()).request()) {
                    assertThat(replacementHeldResponse.status(), is(Status.OK_200));
                    String heldReplacementId = replacementHold.connectionId();
                    String availableReplacementId = requestConnectionId(client, "/after-reload");
                    awaitAcceptedConnections(acceptedConnections, 4);
                    assertThat(heldReplacementId,
                               not(isIn(List.of(firstConnectionId, secondConnectionId))));
                    assertThat(availableReplacementId,
                               not(isIn(List.of(firstConnectionId, secondConnectionId, heldReplacementId))));
                    assertThat(activeOldConnection.isOpen(), is(true));
                    idleOldConnection.whenTerminated().toCompletableFuture().get(10, TimeUnit.SECONDS);

                    oldHold.complete();
                    assertThat(heldResponse.as(String.class), is(firstConnectionId));
                    activeOldConnection.whenTerminated().toCompletableFuture().get(10, TimeUnit.SECONDS);

                    assertThat(requestConnectionId(client, "/reuse-after-old-callbacks"),
                               is(availableReplacementId));
                    assertThat(acceptedConnections.size(), is(4));

                    replacementHold.complete();
                    assertThat(replacementHeldResponse.as(String.class), is(heldReplacementId));
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldRetireEveryAlternativeRouteSessionWithoutRemovingRelearnedReplacement() throws Exception {
        StreamHold oldHold = new StreamHold();
        StreamHold replacementHold = new StreamHold("/hold-relearned-alternative");
        List<QuicConnection> alternativeConnections = new CopyOnWriteArrayList<>();
        try (Http3RawTestServer alternative = Http3RawTestServer.create((request, connection, _, stream) -> {
                 String path = request.path().orElseThrow();
                 if (oldHold.path().equals(path)) {
                     oldHold.begin(connection, stream);
                     return null;
                 }
                 if (replacementHold.path().equals(path)) {
                     replacementHold.begin(connection, stream);
                     return null;
                 }
                 if ("/clear-alternative".equals(path)) {
                     Headers headers = WritableHeaders.create()
                             .add(HeaderValues.create(HeaderNames.ALT_SVC, "clear"));
                     return Http3RawTestServer.response(Status.OK_200.code(),
                                                        headers,
                                                        connection.childSocketId()
                                                                .getBytes(StandardCharsets.UTF_8));
                 }
                 return Http3RawTestServer.text(Status.OK_200.code(), connection.childSocketId());
             }, alternativeConnections::add, 1)) {
            String altSvc = "h3=\":" + alternative.uri("/").getPort() + "\"; ma=60";
            try (Http3RawTestServer origin = Http3RawTestServer.create((_, connection, _, _) -> {
                     Headers headers = WritableHeaders.create()
                             .add(HeaderValues.create(HeaderNames.ALT_SVC, altSvc));
                     return Http3RawTestServer.response(Status.OK_200.code(),
                                                        headers,
                                                        connection.childSocketId()
                                                                .getBytes(StandardCharsets.UTF_8));
                 })) {
                Http3Client client = strictClientBuilder()
                        .baseUri(origin.baseUri())
                        .tls(origin.clientTlsHttp3())
                        .altSvc(ClientAltSvcConfig.create())
                        .connectionCacheSize(2)
                        .build();

                try {
                    requestConnectionId(client, "/learn-alternative");
                    try (Http3ClientResponse heldResponse = client.get(oldHold.path()).request()) {
                        assertThat(heldResponse.status(), is(Status.OK_200));
                        String firstAlternativeId = oldHold.connectionId();
                        awaitAcceptedConnections(alternativeConnections, 1);

                        String secondAlternativeId = requestConnectionId(client, "/clear-alternative");
                        awaitAcceptedConnections(alternativeConnections, 2);
                        assertThat(secondAlternativeId, is(not(firstAlternativeId)));
                        QuicConnection activeOldConnection = alternativeConnections.getFirst();
                        QuicConnection idleOldConnection = alternativeConnections.get(1);

                        requestConnectionId(client, "/relearn-alternative");
                        try (Http3ClientResponse replacementHeldResponse =
                                     client.get(replacementHold.path()).request()) {
                            assertThat(replacementHeldResponse.status(), is(Status.OK_200));
                            String heldReplacementId = replacementHold.connectionId();
                            String availableReplacementId = requestConnectionId(client, "/replacement-alternative");
                            awaitAcceptedConnections(alternativeConnections, 4);
                            assertThat(heldReplacementId,
                                       not(isIn(List.of(firstAlternativeId, secondAlternativeId))));
                            assertThat(availableReplacementId,
                                       not(isIn(List.of(firstAlternativeId,
                                                       secondAlternativeId,
                                                       heldReplacementId))));
                            assertThat(activeOldConnection.isOpen(), is(true));
                            idleOldConnection.whenTerminated().toCompletableFuture().get(10, TimeUnit.SECONDS);

                            oldHold.complete();
                            assertThat(heldResponse.as(String.class), is(firstAlternativeId));
                            activeOldConnection.whenTerminated().toCompletableFuture().get(10, TimeUnit.SECONDS);

                            assertThat(requestConnectionId(client, "/reuse-relearned-alternative"),
                                       is(availableReplacementId));
                            assertThat(alternativeConnections.size(), is(4));

                            replacementHold.complete();
                            assertThat(replacementHeldResponse.as(String.class), is(heldReplacementId));
                        }
                    }
                } finally {
                    client.closeResource();
                }
            }
        }
    }

    @Test
    void shouldCloseCacheWhileSessionCreationWaitsForCriticalStreamCredit() throws Exception {
        List<QuicConnection> acceptedConnections = new CopyOnWriteArrayList<>();
        try (Http3RawTestServer server = Http3RawTestServer.create(
                (_, connection, _, _) -> Http3RawTestServer.text(Status.OK_200.code(), connection.childSocketId()),
                acceptedConnections::add,
                1,
                2)) {
            Http3ClientProtocolConfig.Builder protocolConfig = Http3ClientProtocolConfig.builder()
                    .streamOpenTimeout(Duration.ofSeconds(30));
            Http3Client client = strictClientBuilder(protocolConfig)
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .connectionCacheSize(2)
                    .build();

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                try {
                    CompletableFuture<String> request = CompletableFuture.supplyAsync(
                            () -> requestConnectionId(client, "/pending-creation"),
                            executor);
                    awaitAcceptedConnections(acceptedConnections, 1);
                    assertThat(request.isDone(), is(false));

                    CompletableFuture.runAsync(client::closeResource, executor).get(10, TimeUnit.SECONDS);
                    assertThrows(ExecutionException.class, () -> request.get(10, TimeUnit.SECONDS));
                    acceptedConnections.getFirst()
                            .whenTerminated()
                            .toCompletableFuture()
                            .get(10, TimeUnit.SECONDS);
                    assertThat(acceptedConnections.getFirst().isOpen(), is(false));
                } finally {
                    client.closeResource();
                }
            }
        }
    }

    @Test
    void shouldCloseCacheWhileRequestsWaitForStreamCredit() throws Exception {
        StreamHold hold = new StreamHold("/hold-before-cache-close");
        AtomicInteger handledQueuedRequests = new AtomicInteger();
        AtomicInteger firstCompletions = new AtomicInteger();
        AtomicInteger secondCompletions = new AtomicInteger();
        List<QuicConnection> acceptedConnections = new CopyOnWriteArrayList<>();
        try (Http3RawTestServer server = Http3RawTestServer.create((request, connection, _, stream) -> {
                 if (hold.path().equals(request.path().orElseThrow())) {
                     hold.begin(connection, stream);
                     return null;
                 }
                 handledQueuedRequests.incrementAndGet();
                 return Http3RawTestServer.text(Status.OK_200.code(), connection.childSocketId());
             }, acceptedConnections::add, 1)) {
            Http3ClientProtocolConfig.Builder protocolConfig = Http3ClientProtocolConfig.builder()
                    .streamOpenTimeout(Duration.ofSeconds(30));
            Http3Client client = strictClientBuilder(protocolConfig)
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .connectionCacheSize(1)
                    .build();

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
                 Http3ClientResponse heldResponse = client.get(hold.path()).request()) {
                assertThat(heldResponse.status(), is(Status.OK_200));
                awaitAcceptedConnections(acceptedConnections, 1);

                CountDownLatch requestsStarted = new CountDownLatch(2);
                CompletableFuture<String> firstRequest = CompletableFuture.supplyAsync(() -> {
                    requestsStarted.countDown();
                    return requestConnectionId(client, "/queued-before-close-first");
                }, executor);
                CompletableFuture<String> secondRequest = CompletableFuture.supplyAsync(() -> {
                    requestsStarted.countDown();
                    return requestConnectionId(client, "/queued-before-close-second");
                }, executor);
                firstRequest.whenComplete((_, _) -> firstCompletions.incrementAndGet());
                secondRequest.whenComplete((_, _) -> secondCompletions.incrementAndGet());
                await(requestsStarted);
                assertThrows(TimeoutException.class,
                             () -> firstRequest.get(500, TimeUnit.MILLISECONDS));
                assertThrows(TimeoutException.class,
                             () -> secondRequest.get(500, TimeUnit.MILLISECONDS));
                assertThat(handledQueuedRequests.get(), is(0));
                assertThat(acceptedConnections.size(), is(1));

                CompletableFuture.runAsync(client::closeResource, executor).get(10, TimeUnit.SECONDS);
                assertThrows(ExecutionException.class, () -> firstRequest.get(10, TimeUnit.SECONDS));
                assertThrows(ExecutionException.class, () -> secondRequest.get(10, TimeUnit.SECONDS));
                assertThat(firstCompletions.get(), is(1));
                assertThat(secondCompletions.get(), is(1));
                assertRequestCountRemains(handledQueuedRequests, 0);
                acceptedConnections.getFirst()
                        .whenTerminated()
                        .toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);
                assertThat(acceptedConnections.getFirst().isOpen(), is(false));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldReuseDynamicQpackAcrossRepeatedResponsesOnSameConnection() throws Exception {
        HeaderName qpackUser = HeaderNames.create("x-qpack-user");
        HeaderName qpackEnv = HeaderNames.create("x-qpack-env");
        HeaderName qpackCluster = HeaderNames.create("x-qpack-cluster");
        String userValue = "alpha-user-1234567890";
        String envValue = "dev-eu-central-1";
        String clusterValue = "shared-http3-connection";
        Headers repeatedResponseHeaders = WritableHeaders.create()
                .add(HeaderValues.create(qpackUser, userValue))
                .add(HeaderValues.create(qpackEnv, envValue))
                .add(HeaderValues.create(qpackCluster, clusterValue));
        List<Integer> encodedHeadersLengths = new CopyOnWriteArrayList<>();
        try (Http3RawTestServer server = Http3RawTestServer.create((request, connection, streamId, stream) -> {
                 encodedHeadersLengths.add(stream.writeResponseHeaders(Status.OK_200.code(),
                                                                        repeatedResponseHeaders,
                                                                        false));
                 byte[] connectionId = connection.childSocketId().getBytes(StandardCharsets.UTF_8);
                 stream.writeData(connectionId, true);
                 return null;
             })) {
            Http3ClientProtocolConfig.Builder protocolConfig = Http3ClientProtocolConfig.builder()
                    .qpackMaxTableCapacity(4_096)
                    .qpackBlockedStreams(16);
            Http3Client client = strictClientBuilder(protocolConfig)
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .build();

            try {
                String firstConnectionId;
                try (Http3ClientResponse first = client.get("/dynamic-qpack").request()) {
                    assertThat(first.status(), is(Status.OK_200));
                    assertThat(first.protocolId(), is(Http3Client.PROTOCOL_ID));
                    assertThat(first.headers().first(qpackUser).orElseThrow(), is(userValue));
                    assertThat(first.headers().first(qpackEnv).orElseThrow(), is(envValue));
                    assertThat(first.headers().first(qpackCluster).orElseThrow(), is(clusterValue));
                    firstConnectionId = first.as(String.class);
                }

                try (Http3ClientResponse second = client.get("/dynamic-qpack").request()) {
                    assertThat(second.status(), is(Status.OK_200));
                    assertThat(second.protocolId(), is(Http3Client.PROTOCOL_ID));
                    assertThat(second.headers().first(qpackUser).orElseThrow(), is(userValue));
                    assertThat(second.headers().first(qpackEnv).orElseThrow(), is(envValue));
                    assertThat(second.headers().first(qpackCluster).orElseThrow(), is(clusterValue));
                    assertThat(second.as(String.class), is(firstConnectionId));
                }

                try (Http3ClientResponse third = client.get("/dynamic-qpack").request()) {
                    assertThat(third.status(), is(Status.OK_200));
                    assertThat(third.protocolId(), is(Http3Client.PROTOCOL_ID));
                    assertThat(third.headers().first(qpackUser).orElseThrow(), is(userValue));
                    assertThat(third.headers().first(qpackEnv).orElseThrow(), is(envValue));
                    assertThat(third.headers().first(qpackCluster).orElseThrow(), is(clusterValue));
                    assertThat(third.as(String.class), is(firstConnectionId));
                }

                assertThat(encodedHeadersLengths.size(), is(3));
                assertThat(Math.min(encodedHeadersLengths.get(1), encodedHeadersLengths.get(2)),
                           lessThan(encodedHeadersLengths.get(0)));
            } finally {
                client.closeResource();
            }
        }
    }

    private static String requestConnectionId(Http3Client client, String path) {
        try (Http3ClientResponse response = client.get(path).request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
            return response.as(String.class);
        }
    }

    private static boolean causedBy(Throwable failure, Class<? extends Throwable> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            Throwable next = current.getCause();
            if (next == current) {
                return false;
            }
            current = next;
        }
        return false;
    }

    private static void awaitAcceptedConnections(List<QuicConnection> acceptedConnections, int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (acceptedConnections.size() < expected && System.nanoTime() < deadline) {
            try {
                TimeUnit.MILLISECONDS.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while awaiting HTTP/3 connection acceptance", e);
            }
        }
        assertThat(acceptedConnections.size(), is(expected));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out awaiting concurrent HTTP/3 requests");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while awaiting concurrent HTTP/3 requests", e);
        }
    }

    private static void assertRequestCountRemains(AtomicInteger requests, int expected) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(250);
        while (requests.get() == expected && System.nanoTime() < deadline) {
            try {
                TimeUnit.MILLISECONDS.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while checking HTTP/3 request count", e);
            }
        }
        assertThat(requests.get(), is(expected));
    }

    private static final class StreamHold {
        private static final String HOLD_PATH = "/hold";

        private final String path;
        private final AtomicReference<Http3RawTestServer.StreamControl> stream = new AtomicReference<>();
        private final AtomicReference<String> connectionId = new AtomicReference<>();
        private final AtomicBoolean completed = new AtomicBoolean();

        private StreamHold() {
            this(HOLD_PATH);
        }

        private StreamHold(String path) {
            this.path = Objects.requireNonNull(path, "path");
        }

        private Http3RawTestServer.BufferedResponse handle(Http3Protocol.DecodedRequestHead request,
                                                           QuicConnection connection,
                                                           long ignoredStreamId,
                                                           Http3RawTestServer.StreamControl streamControl) {
            if (!path.equals(request.path().orElseThrow())) {
                return Http3RawTestServer.text(Status.OK_200.code(), connection.childSocketId());
            }
            begin(connection, streamControl);
            return null;
        }

        private void begin(QuicConnection connection, Http3RawTestServer.StreamControl streamControl) {
            if (!stream.compareAndSet(null, streamControl)) {
                throw new IllegalStateException("HTTP/3 hold stream is already registered");
            }
            connectionId.set(connection.childSocketId());
            streamControl.writeResponseHeaders(Status.OK_200.code(), WritableHeaders.create(), false);
        }

        private String path() {
            return path;
        }

        private String connectionId() {
            return connectionId.get();
        }

        private boolean completed() {
            return completed.get();
        }

        private void complete() {
            String id = connectionId.get();
            Http3RawTestServer.StreamControl streamControl = stream.get();
            if (id == null || streamControl == null) {
                throw new IllegalStateException("HTTP/3 hold stream is not registered");
            }
            if (completed.compareAndSet(false, true)) {
                streamControl.writeData(id.getBytes(StandardCharsets.UTF_8), true);
            }
        }

        private void completeIfRegistered() {
            if (stream.get() != null) {
                complete();
            }
        }
    }
}
