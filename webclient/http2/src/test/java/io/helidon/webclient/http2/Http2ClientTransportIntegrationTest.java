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

package io.helidon.webclient.http2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.http.HttpTransportObserver;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.http.HttpTransportObserver.Direction;
import io.helidon.http.HttpTransportObserver.Handshake;
import io.helidon.http.HttpTransportObserver.HandshakeObservation;
import io.helidon.http.HttpTransportObserver.Initiator;
import io.helidon.http.HttpTransportObserver.Role;
import io.helidon.http.HttpTransportObserver.StreamObservation;
import io.helidon.http.HttpTransportObserver.StreamOutcome;
import io.helidon.http.Status;
import io.helidon.webclient.api.HttpTransportObserverSupport.ObserverLifecycle;
import io.helidon.webclient.api.HttpTransportObserverSupport.ObserverProvider;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

@ServerTest
class Http2ClientTransportIntegrationTest {
    private final String baseUri;
    private final String http1BaseUri;

    Http2ClientTransportIntegrationTest(WebServer server) {
        baseUri = "http://localhost:" + server.port();
        http1BaseUri = "http://localhost:" + server.port("http1");
    }

    @SetUpServer
    static void setUpServer(WebServerConfig.Builder builder) {
        builder.host("localhost")
                .putSocket("http1", socket -> socket
                        .host("localhost")
                        .protocolsDiscoverServices(false)
                        .addConnectionSelector(Http1ConnectionSelector.builder().config(Http1Config.create()).build()))
                .routing("http1", routing -> routing.get("/body", (_, response) -> response.send("response")))
                .routing(routing -> routing
                        .get("/empty", (_, response) -> response.send())
                        .get("/body", (_, response) -> response.send("response"))
                        .get("/error", (_, response) -> response.status(Status.INTERNAL_SERVER_ERROR_500).send("error"))
                        .post("/echo", (request, response) -> response.send(request.content().as(String.class))));
    }

    @Test
    void realExchangesReportClientHttp2AndKeepHttpErrorsCompleted() {
        var recorder = new RecordingService(true);
        Http2Client client = client(recorder, false);
        try {
            try (var response = client.get("/empty").request()) {
                assertThat(response.status(), is(Status.OK_200));
            }
            try (var response = client.post("/echo").submit("request")) {
                assertThat(response.as(String.class), is("request"));
            }
            try (var response = client.get("/error").request()) {
                assertThat(response.status(), is(Status.INTERNAL_SERVER_ERROR_500));
                assertThat(response.as(String.class), is("error"));
            }
        } finally {
            client.closeResource();
        }

        assertThat(recorder.connections.get(), is(1));
        assertThat(recorder.protocols, is(List.of(HttpTransportObserver.PROTOCOL_HTTP_2)));
        assertThat(recorder.streams, is(List.of(StreamOutcome.COMPLETED, StreamOutcome.COMPLETED, StreamOutcome.COMPLETED)));
        assertThat(recorder.closes, is(List.of(ConnectionOutcome.LOCAL_CLOSE)));
        assertThat(recorder.stops.get(), is(1));
        assertThat(recorder.requestCalls.get(), is(0));
    }

    @Test
    void concurrentStreamsPublishSequentialCallbacks() throws Exception {
        var recorder = new RecordingService(true);
        Http2Client client = client(recorder, false);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> requests = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                requests.add(executor.submit(() -> {
                    try (var response = client.get("/body").request()) {
                        assertThat(response.as(String.class), is("response"));
                    }
                }));
            }
            for (var request : requests) {
                request.get(10, TimeUnit.SECONDS);
            }
        } finally {
            client.closeResource();
        }

        assertThat(recorder.streams.size(), is(24));
        assertThat(recorder.streams, everyItem(is(StreamOutcome.COMPLETED)));
        assertThat(recorder.overlaps.get(), is(0));
    }

    @Test
    void h2cUpgradeRetainsPhysicalObservationAcrossProtocols() {
        var recorder = new RecordingService(true);
        Http2Client client = Http2Client.builder()
                .baseUri(baseUri)
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .addService(recorder)
                .build();
        try {
            request(client);
        } finally {
            client.closeResource();
        }

        assertThat(recorder.connections.get(), is(1));
        assertThat(recorder.protocols,
                   is(List.of(HttpTransportObserver.PROTOCOL_HTTP_1_1, HttpTransportObserver.PROTOCOL_HTTP_2)));
        assertThat(recorder.streams, is(List.of(StreamOutcome.COMPLETED, StreamOutcome.COMPLETED)));
        assertThat(recorder.closes, is(List.of(ConnectionOutcome.LOCAL_CLOSE)));
    }

    @Test
    void http1FallbackRetainsTransportObservation() {
        var recorder = new RecordingService(true);
        Http2Client client = Http2Client.builder()
                .baseUri(http1BaseUri)
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .addService(recorder)
                .build();
        try {
            request(client);
        } finally {
            client.closeResource();
        }

        assertThat(recorder.connections.get(), is(1));
        assertThat(recorder.protocols, is(List.of(HttpTransportObserver.PROTOCOL_HTTP_1_1)));
        assertThat(recorder.streams, is(List.of(StreamOutcome.COMPLETED)));
        assertThat(recorder.closes, is(List.of(ConnectionOutcome.LOCAL_CLOSE)));
    }

    @Test
    void sharedScopeKeepsConnectionUntilLastClientCloses() throws Exception {
        var recorder = new RecordingService(true);
        Http2Client first = client(recorder, true);
        Http2Client second = client(recorder, true);
        try {
            request(first);
            request(second);
            assertThat(((Http2ClientImpl) first).connectionCache(), sameInstance(((Http2ClientImpl) second).connectionCache()));
            assertThat(recorder.connections.get(), is(1));

            CompletionStage<Void> firstCompletion = first.closeResourceAsync();
            assertThat(firstCompletion.toCompletableFuture().isDone(), is(false));
            firstCompletion.toCompletableFuture().complete(null);
            assertThat(first.closeResourceAsync().toCompletableFuture().isDone(), is(false));
            assertThat(recorder.closes, is(List.of()));
            assertThat(recorder.stops.get(), is(0));
            request(second);
            assertThat(recorder.connections.get(), is(1));
        } finally {
            first.closeResource();
            second.closeResource();
        }

        first.closeResourceAsync().toCompletableFuture().get(10, TimeUnit.SECONDS);
        second.closeResourceAsync().toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertThat(recorder.closes, is(List.of(ConnectionOutcome.LOCAL_CLOSE)));
        assertThat(recorder.stops.get(), is(1));
    }

    @Test
    void asyncCloseIncludesFallbackObserverCleanup() throws Exception {
        var stopCompletion = new CompletableFuture<Void>();
        var recorder = new RecordingService(true, stopCompletion);
        Http2Client client = Http2Client.builder()
                .baseUri(http1BaseUri)
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .addService(recorder)
                .build();
        try {
            request(client);
            CompletionStage<Void> completion = client.closeResourceAsync();

            assertThat(recorder.closes, is(List.of(ConnectionOutcome.LOCAL_CLOSE)));
            assertThat(completion.toCompletableFuture().isDone(), is(false));
            stopCompletion.complete(null);
            completion.toCompletableFuture().get(10, TimeUnit.SECONDS);
        } finally {
            stopCompletion.complete(null);
            client.closeResource();
        }
    }

    @Test
    void separateScopesNeverShareObservedConnections() {
        var firstRecorder = new RecordingService(true);
        var secondRecorder = new RecordingService(true);
        Http2Client first = client(firstRecorder, true);
        Http2Client second = client(secondRecorder, true);
        try {
            request(first);
            request(second);
            assertThat(((Http2ClientImpl) first).connectionCache(),
                       not(sameInstance(((Http2ClientImpl) second).connectionCache())));
            assertThat(firstRecorder.connections.get(), is(1));
            assertThat(secondRecorder.connections.get(), is(1));
        } finally {
            first.closeResource();
            second.closeResource();
        }
        assertThat(firstRecorder.streams, is(List.of(StreamOutcome.COMPLETED)));
        assertThat(secondRecorder.streams, is(List.of(StreamOutcome.COMPLETED)));
    }

    @Test
    void disabledObservationPreservesUnobservedSharedCacheWithoutResolvingScope() {
        var disabled = new RecordingService(false);
        Http2Client client = client(disabled, true);
        try {
            assertThat(((Http2ClientImpl) client).connectionCache(), sameInstance(Http2ConnectionCache.shared()));
            assertThat(disabled.scopeCalls.get(), is(0));
            assertThat(disabled.starts.get(), is(0));
        } finally {
            client.closeResource();
        }
        assertThat(disabled.stops.get(), is(0));
    }

    @Test
    void unusedObservedClientNeverAcquiresObserverLease() {
        var recorder = new RecordingService(true);
        client(recorder, true).closeResource();

        assertThat(recorder.scopeCalls.get(), is(0));
        assertThat(recorder.starts.get(), is(0));
        assertThat(recorder.stops.get(), is(0));
    }

    private static void request(Http2Client client) {
        try (var response = client.get("/body").request()) {
            assertThat(response.as(String.class), is("response"));
        }
    }

    private Http2Client client(RecordingService service, boolean shared) {
        return Http2Client.builder()
                .baseUri(baseUri)
                .servicesDiscoverServices(false)
                .shareConnectionCache(shared)
                .protocolConfig(config -> config.priorKnowledge(true))
                .addService(service)
                .build();
    }

    private static final class RecordingService implements WebClientService, ObserverProvider {
        private final boolean enabled;
        private final CompletionStage<Void> stopCompletion;
        private final AtomicInteger scopeCalls = new AtomicInteger();
        private final AtomicInteger starts = new AtomicInteger();
        private final AtomicInteger stops = new AtomicInteger();
        private final AtomicInteger requestCalls = new AtomicInteger();
        private final AtomicInteger connections = new AtomicInteger();
        private final AtomicInteger overlaps = new AtomicInteger();
        private final List<String> protocols = new CopyOnWriteArrayList<>();
        private final List<StreamOutcome> streams = new CopyOnWriteArrayList<>();
        private final List<ConnectionOutcome> closes = new CopyOnWriteArrayList<>();

        private RecordingService(boolean enabled) {
            this(enabled, CompletableFuture.completedStage(null));
        }

        private RecordingService(boolean enabled, CompletionStage<Void> stopCompletion) {
            this.enabled = enabled;
            this.stopCompletion = stopCompletion;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public Object scope() {
            scopeCalls.incrementAndGet();
            return this;
        }

        @Override
        public ObserverLifecycle createObserver() {
            return new ObserverLifecycle() {
                private volatile boolean connectionUsed;

                @Override
                public HttpTransportObserver start() {
                    starts.incrementAndGet();
                    return (role, transport, handshake) -> {
                        connectionUsed = true;
                        return connectionOpened(role, transport, handshake);
                    };
                }

                @Override
                public CompletionStage<Void> stop() {
                    stops.incrementAndGet();
                    return connectionUsed ? stopCompletion : CompletableFuture.completedStage(null);
                }
            };
        }

        @Override
        public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
            requestCalls.incrementAndGet();
            return chain.proceed(request);
        }

        private ConnectionObservation connectionOpened(Role role, String transport, Handshake handshake) {
            assertThat(role, is(Role.CLIENT));
            assertThat(transport, is(HttpTransportObserver.TRANSPORT_TCP));
            assertThat(handshake, is(Handshake.NONE));
            connections.incrementAndGet();
            return new ConnectionObservation() {
                private final AtomicInteger active = new AtomicInteger();

                @Override
                public HandshakeObservation handshakeStarted() {
                    return HandshakeObservation.noop();
                }

                @Override
                public void protocolSelected(String protocol) {
                    enter();
                    protocols.add(protocol);
                    active.decrementAndGet();
                }

                @Override
                public StreamObservation streamOpened(Direction direction, Initiator initiator) {
                    enter();
                    assertThat(direction, is(Direction.BIDIRECTIONAL));
                    assertThat(initiator, is(Initiator.LOCAL));
                    active.decrementAndGet();
                    return outcome -> {
                        enter();
                        streams.add(outcome);
                        active.decrementAndGet();
                    };
                }

                @Override
                public void close(ConnectionOutcome outcome) {
                    enter();
                    closes.add(outcome);
                    active.decrementAndGet();
                }

                private void enter() {
                    if (active.incrementAndGet() != 1) {
                        overlaps.incrementAndGet();
                    }
                    Thread.yield();
                }
            };
        }
    }
}
