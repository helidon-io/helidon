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

package io.helidon.webclient.tests;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import io.helidon.common.configurable.Resource;
import io.helidon.common.tls.Tls;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterConfig;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.service.registry.Services;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.ReleasableResource;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webclient.grpc.GrpcClientMethodDescriptor;
import io.helidon.webclient.grpc.GrpcServiceDescriptor;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http2.Http2ClientProtocolConfig;
import io.helidon.webclient.metrics.WebClientTransportMetrics;
import io.helidon.webclient.websocket.WsClient;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.grpc.GrpcConfig;
import io.helidon.webserver.grpc.GrpcProtocolSelector;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2ConnectionSelector;
import io.helidon.webserver.http2.Http2Upgrader;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.metrics.MetricsObserver;
import io.helidon.webserver.websocket.WsConfig;
import io.helidon.webserver.websocket.WsRouting;
import io.helidon.webserver.websocket.WsUpgrader;
import io.helidon.websocket.WsListener;
import io.helidon.websocket.WsSession;

import com.google.protobuf.StringValue;
import io.grpc.MethodDescriptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ServerCalls;
import org.hamcrest.Matcher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.helidon.common.testing.junit5.MatcherWithRetry.assertThatWithRetry;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class HttpTransportMetricsTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String STREAM_DURATION = "helidon.http.streams.duration";
    private static final String GRPC_SERVICE = "TransportMetrics";
    private static final String GRPC_METHOD = "Echo";

    @ParameterizedTest
    @CsvSource({"http/1.1,false", "h2,false", "grpc,false", "websocket,false",
            "http/1.1,true", "h2,true", "grpc,true", "websocket,true"})
    void disablingStreamDurationPreservesOtherStreamMeters(String protocol, boolean secure) throws Exception {
        var config = MetricsConfig.builder()
                .warnOnMultipleRegistries(false)
                .addMeter(MeterConfig.builder().namePattern(Pattern.compile(Pattern.quote(STREAM_DURATION))).enabled(false).build())
                .build();
        try (var fixture = new Fixture(protocol, secure, Mode.ENABLED, config)) {
            fixture.exchange(Mode.ENABLED);
            for (String role : List.of("client", "server")) {
                await(role + " opened exchange", () -> openedStreams(fixture.registry, protocol, role), is(1L));
                await(role + " completed exchange", () -> completedStreams(fixture.registry, protocol, role), is(1L));
                await(role + " active exchanges", () -> activeStreams(fixture.registry, protocol, role), is(Optional.of(0L)));
                assertThat(role + " duration timer is absent", streamDuration(fixture.registry, protocol, role),
                           is(Optional.empty()));
            }
            assertThat("Disabled stream duration is not published under any tags", fixture.registry.meters().stream()
                    .filter(meter -> meter.id().name().equals(STREAM_DURATION))
                    .toList(), empty());
        }
    }

    @ParameterizedTest
    @CsvSource({"http/1.1,false", "h2,false", "grpc,false", "websocket,false",
            "http/1.1,true", "h2,true", "grpc,true", "websocket,true"})
    void emptyPercentilesPreserveStreamDurationCountAndTotal(String protocol, boolean secure) throws Exception {
        var config = MetricsConfig.builder()
                .warnOnMultipleRegistries(false)
                .addMeter(MeterConfig.builder()
                                  .namePattern(Pattern.compile(Pattern.quote(STREAM_DURATION)))
                                  .percentiles(List.of())
                                  .build())
                .build();
        try (var fixture = new Fixture(protocol, secure, Mode.ENABLED, config)) {
            fixture.exchange(Mode.ENABLED);
            for (String role : List.of("client", "server")) {
                await(role + " completed exchange", () -> completedStreams(fixture.registry, protocol, role), is(1L));
                await(role + " duration count", () -> streamDuration(fixture.registry, protocol, role)
                        .map(Timer::count).orElse(0L), is(1L));
                Timer timer = streamDuration(fixture.registry, protocol, role).orElseThrow();
                assertThat(role + " total duration", timer.totalTime(TimeUnit.NANOSECONDS), greaterThan(0D));
                var snapshot = timer.snapshot();
                assertThat(role + " snapshot count", snapshot.count(), is(1L));
                assertThat(role + " snapshot total", snapshot.total(TimeUnit.NANOSECONDS), greaterThan(0D));
                assertThat(role + " disabled percentiles", snapshot.percentileValues(), emptyIterable());
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"http/1.1,client", "h2,client", "grpc,client", "websocket,client",
            "http/1.1,server", "h2,server", "grpc,server", "websocket,server"})
    void explicitTransportDisableWinsOverRegistryMeterEnable(String protocol, String disabledRole) throws Exception {
        var config = MetricsConfig.builder()
                .warnOnMultipleRegistries(false)
                .addMeter(MeterConfig.builder().namePattern(Pattern.compile(Pattern.quote(STREAM_DURATION))).enabled(true).build())
                .build();
        Mode serverMode = disabledRole.equals("server") ? Mode.DISABLED : Mode.ENABLED;
        Mode clientMode = disabledRole.equals("client") ? Mode.DISABLED : Mode.ENABLED;
        String enabledRole = disabledRole.equals("client") ? "server" : "client";
        try (var fixture = new Fixture(protocol, false, serverMode, config)) {
            fixture.exchange(clientMode);
            await(enabledRole + " observed exchange", () -> completedStreams(fixture.registry, protocol, enabledRole), is(1L));
            await(enabledRole + " allowed duration timer", () -> streamDuration(fixture.registry, protocol, enabledRole)
                    .map(Timer::count).orElse(0L), is(1L));
            assertThat(disabledRole + " transport metrics remain disabled", fixture.registry.meters().stream()
                    .filter(meter -> meter.id().name().startsWith("helidon.http."))
                    .filter(meter -> disabledRole.equals(meter.id().tagsMap().get("role")))
                    .toList(), empty());
        }
    }

    @ParameterizedTest
    @CsvSource({"http/1.1,false", "h2,false", "http/1.1,true", "h2,true"})
    void recordsBothRolesAndDeliveredErrorResponses(String protocol, boolean secure) throws Exception {
        try (var fixture = new Fixture(protocol, secure, true)) {
            WebClient client = fixture.client(Mode.ENABLED, fixture.registry);
            request(client, protocol, "/success", 200);
            request(client, protocol, "/fail", 500);
            long physicalConnections = fixture.connectionIds.size();

            for (String role : List.of("client", "server")) {
                await(role + " opened exchanges", () -> openedStreams(fixture.registry, protocol, role), is(2L));
                await(role + " completed exchanges", () -> completedStreams(fixture.registry, protocol, role), is(2L));
                await(role + " active exchanges", () -> activeStreams(fixture.registry, protocol, role), is(Optional.of(0L)));
                await(role + " established connections", () -> established(fixture.registry, protocol, role),
                      is(physicalConnections));
                if (secure) {
                    await(role + " TLS handshake", () -> counter(fixture.registry, "handshakes",
                            "role", role, "transport", "tcp", "handshake", "tls", "outcome", "success"),
                          is(physicalConnections));
                }
            }

            client.closeResourceAsync().toCompletableFuture().get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            for (String role : List.of("client", "server")) {
                await(role + " closed connection", () -> activeConnections(fixture.registry, protocol, role), is(0L));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"http/1.1", "h2"})
    void absentAndDisabledServicesDoNotPublish(String protocol) throws Exception {
        try (var fixture = new Fixture(protocol, false, false)) {
            WebClient absent = fixture.client(Mode.ABSENT, fixture.registry);
            request(absent, protocol, "/success", 200);
            request(fixture.client(Mode.DISABLED, fixture.registry), protocol, "/success", 200);

            assertThat("Default discovery must not enable transport metrics", absent.prototype().services().stream()
                    .filter(WebClientTransportMetrics.class::isInstance)
                    .map(WebClientTransportMetrics.class::cast)
                    .anyMatch(WebClientTransportMetrics::enabled), is(false));
            assertThat("No transport meters without enabled observation", fixture.registry.meters(), empty());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"http/1.1", "h2"})
    void absentAndDisabledClientsDoNotReuseAnObservedConnection(String protocol) throws Exception {
        try (var fixture = new Fixture(protocol, false, true)) {
            WebClient observed = fixture.client(Mode.ENABLED, fixture.registry);
            String observedConnection = request(observed, protocol, "/success", 200);
            await("Initial client exchange", () -> completedStreams(fixture.registry, protocol, "client"), is(1L));

            String absentConnection = request(fixture.client(Mode.ABSENT, fixture.registry), protocol, "/success", 200);
            String disabledConnection = request(fixture.client(Mode.DISABLED, fixture.registry), protocol, "/success", 200);

            assertThat("Absent client uses a separate physical connection", absentConnection, not(is(observedConnection)));
            assertThat("Disabled client uses a separate physical connection", disabledConnection, not(is(observedConnection)));
            await("All requests reached the server", () -> completedStreams(fixture.registry, protocol, "server"), is(3L));
            assertThat("Unobserved requests do not increment client metrics",
                       completedStreams(fixture.registry, protocol, "client"), is(1L));
            assertThat("No extra observed client connections", established(fixture.registry, protocol, "client"), is(1L));

            assertThat("Observed connection remains reusable", request(observed, protocol, "/success", 200),
                       is(observedConnection));
            await("Next observed client exchange", () -> completedStreams(fixture.registry, protocol, "client"), is(2L));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"http/1.1", "h2"})
    void distinctRegistriesDoNotShareObservedConnectionsOrCounts(String protocol) throws Exception {
        try (var fixture = new Fixture(protocol, false, true)) {
            MeterRegistry otherRegistry = fixture.addRegistry();
            WebClient first = fixture.client(Mode.ENABLED, fixture.registry);
            WebClient second = fixture.client(Mode.ENABLED, otherRegistry);
            String firstConnection = request(first, protocol, "/success", 200);
            String secondConnection = request(second, protocol, "/success", 200);
            request(second, protocol, "/success", 200);

            assertThat("Different metric registries have separate connections", secondConnection, not(is(firstConnection)));
            await("First registry client count", () -> completedStreams(fixture.registry, protocol, "client"), is(1L));
            await("Second registry client count", () -> completedStreams(otherRegistry, protocol, "client"), is(2L));
            await("Server counts both clients", () -> completedStreams(fixture.registry, protocol, "server"), is(3L));
            assertThat("Second registry has no server metrics", openedStreams(otherRegistry, protocol, "server"), is(0L));
            assertThat("First registry has one physical client connection", established(fixture.registry, protocol, "client"),
                       is(1L));
            assertThat("Second registry has one physical client connection", established(otherRegistry, protocol, "client"),
                       is(1L));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"http/1.1", "h2"})
    void compatibleClientsKeepSharedConnectionUntilLastOwnerCloses(String protocol) throws Exception {
        try (var fixture = new Fixture(protocol, false, true)) {
            WebClient first = fixture.client(Mode.ENABLED, fixture.registry);
            WebClient second = fixture.client(Mode.ENABLED, fixture.registry);
            String connection = request(first, protocol, "/success", 200);
            assertThat("Compatible clients share one physical connection", request(second, protocol, "/success", 200),
                       is(connection));
            await("Two exchanges on shared connection", () -> completedStreams(fixture.registry, protocol, "client"), is(2L));

            var firstCompletion = first.closeResourceAsync();
            assertThat("Shared cleanup waits for the remaining owner", firstCompletion.toCompletableFuture().isDone(), is(false));
            assertThat("Closing one owner retains the connection", request(second, protocol, "/success", 200), is(connection));
            await("Remaining client exchange", () -> completedStreams(fixture.registry, protocol, "client"), is(3L));
            assertThat("Shared client connection counted once", established(fixture.registry, protocol, "client"), is(1L));
            assertThat("Shared server connection counted once", established(fixture.registry, protocol, "server"), is(1L));

            var secondCompletion = second.closeResourceAsync();
            CompletableFuture.allOf(firstCompletion.toCompletableFuture(), secondCompletion.toCompletableFuture())
                    .get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            await("Last client owner closes physical connection", () -> activeConnections(fixture.registry, protocol, "client"),
                  is(0L));
            await("Server sees last client owner close", () -> activeConnections(fixture.registry, protocol, "server"), is(0L));
        }
    }

    private static String request(WebClient client, String protocol, String path, int status) {
        var request = client.get(path);
        if (protocol.equals("h2") && client.prototype().baseUri().orElseThrow().scheme().equals("http")) {
            request.protocolId(protocol);
        }
        try (HttpClientResponse response = request.request()) {
            assertThat(path + " response status", response.status().code(), is(status));
            assertThat(path + " negotiated protocol", response.protocolId(), is(protocol));
            return response.as(String.class);
        }
    }

    private static long openedStreams(MeterRegistry registry, String protocol, String role) {
        return counter(registry, "streams.opened", "role", role, "protocol", metricProtocol(protocol), "direction", "bidi",
                       "initiator", role.equals("client") ? "local" : "remote");
    }

    private static long completedStreams(MeterRegistry registry, String protocol, String role) {
        return counter(registry, "streams.closed", "role", role, "protocol", metricProtocol(protocol), "direction", "bidi",
                       "initiator", role.equals("client") ? "local" : "remote", "outcome", "completed");
    }

    private static Optional<Long> activeStreams(MeterRegistry registry, String protocol, String role) {
        return registry.gauge("helidon.http.streams.active", tags(registry.metricsFactory(),
                        "role", role, "protocol", metricProtocol(protocol), "direction", "bidi",
                        "initiator", role.equals("client") ? "local" : "remote"))
                .map(gauge -> gauge.value().longValue());
    }

    private static long established(MeterRegistry registry, String protocol, String role) {
        return counter(registry, "connections.established", "role", role, "transport", "tcp",
                       "protocol", metricProtocol(protocol));
    }

    private static long activeConnections(MeterRegistry registry, String protocol, String role) {
        return gauge(registry, "connections.active", "role", role, "transport", "tcp", "protocol", metricProtocol(protocol));
    }

    private static Optional<Timer> streamDuration(MeterRegistry registry, String protocol, String role) {
        return registry.timer(STREAM_DURATION, tags(registry.metricsFactory(),
                "role", role, "protocol", metricProtocol(protocol), "direction", "bidi",
                "initiator", role.equals("client") ? "local" : "remote", "outcome", "completed"));
    }

    private static String metricProtocol(String protocol) {
        return switch (protocol) {
            case "h2", "grpc" -> "http/2";
            case "websocket" -> "http/1.1";
            default -> protocol;
        };
    }

    private static long counter(MeterRegistry registry, String name, String... tags) {
        return registry.counter("helidon.http." + name, tags(registry.metricsFactory(), tags)).map(Counter::count).orElse(0L);
    }

    private static long gauge(MeterRegistry registry, String name, String... tags) {
        return registry.gauge("helidon.http." + name, tags(registry.metricsFactory(), tags))
                .map(gauge -> gauge.value().longValue()).orElse(-1L);
    }

    private static List<Tag> tags(MetricsFactory factory, String... values) {
        var tags = new ArrayList<Tag>(values.length / 2);
        for (int i = 0; i < values.length; i += 2) {
            tags.add(factory.tagCreate(values[i], values[i + 1]));
        }
        return tags;
    }

    private static <T> void await(String description, Supplier<T> actual, Matcher<? super T> expected) {
        assertThatWithRetry(description, actual, expected, (int) TIMEOUT.toMillis() / 10, 10);
    }

    private static MethodDescriptor<StringValue, StringValue> grpcMethod() {
        return MethodDescriptor.<StringValue, StringValue>newBuilder()
                .setFullMethodName(MethodDescriptor.generateFullMethodName(GRPC_SERVICE, GRPC_METHOD))
                .setType(MethodDescriptor.MethodType.UNARY)
                .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                .build();
    }

    private enum Mode {
        ABSENT,
        DISABLED,
        ENABLED
    }

    private static final class Fixture implements AutoCloseable {
        private final List<ReleasableResource> clients = new ArrayList<>();
        private final List<MeterRegistry> registries = new ArrayList<>();
        private final Set<String> connectionIds = ConcurrentHashMap.newKeySet();
        private final MeterRegistry registry;
        private final String protocol;
        private final boolean secure;
        private final WebServer server;

        private Fixture(String protocol, boolean secure, boolean observedServer) {
            this(protocol, secure, observedServer ? Mode.ENABLED : Mode.ABSENT,
                 MetricsConfig.builder().warnOnMultipleRegistries(false).build());
        }

        private Fixture(String protocol, boolean secure, Mode serverMode, MetricsConfig metricsConfig) {
            this.protocol = protocol;
            this.secure = secure;
            registry = addRegistry(metricsConfig);
            Http2Config http2 = Http2Config.create();
            WsConfig websocket = WsConfig.create();
            var grpcService = ServerServiceDefinition.builder(GRPC_SERVICE)
                    .addMethod(grpcMethod(), ServerCalls.asyncUnaryCall((request, response) -> {
                        response.onNext(request);
                        response.onCompleted();
                    }))
                    .build();
            var builder = WebServer.builder()
                    .host("localhost")
                    .port(-1)
                    .shutdownGracePeriod(Duration.ZERO)
                    .featuresDiscoverServices(false)
                    .protocolsDiscoverServices(false)
                    .addProtocol(websocket)
                    .addConnectionSelector(Http2ConnectionSelector.builder()
                                                   .http2Config(http2)
                                                   .addSubProtocolSelector(GrpcProtocolSelector.create(GrpcConfig.create()))
                                                   .build())
                    .addConnectionSelector(Http1ConnectionSelector.builder()
                                                   .config(Http1Config.create())
                                                   .addUpgrader(Http2Upgrader.create(http2))
                                                   .addUpgrader(WsUpgrader.create(websocket))
                                                   .build())
                    .addRouting(GrpcRouting.builder().service(grpcService))
                    .addRouting(WsRouting.builder().endpoint("/websocket", new WsListener() {
                        @Override
                        public void onMessage(WsSession session, String text, boolean last) {
                            session.send(text, last);
                        }
                    }))
                    .routing(routing -> routing
                            .get("/success", (request, response) -> {
                                connectionIds.add(request.socketId());
                                response.send(request.socketId());
                            })
                            .get("/fail", (request, _) -> {
                                connectionIds.add(request.socketId());
                                throw new IllegalStateException("Intentional business failure for transport metrics");
                            }));
            if (serverMode != Mode.ABSENT) {
                builder.addFeature(ObserveFeature.builder()
                                           .observersDiscoverServices(false)
                                           .addObserver(MetricsObserver.builder()
                                                                .metricsConfig(metricsConfig)
                                                                .meterRegistry(registry)
                                                                .autoHttpMetrics(metrics -> metrics
                                                                        .enabled(serverMode == Mode.ENABLED))
                                                                .build())
                                           .build());
            }
            if (secure) {
                builder.tls(tls -> tls
                        .privateKey(key -> key.keystore(store -> store.passphrase("password")
                                .keystore(Resource.create("server.p12"))))
                        .privateKeyCertChain(key -> key.keystore(store -> store.passphrase("password")
                                .trustStore(true).keystore(Resource.create("server.p12")))));
            }
            server = builder.build().start();
        }

        @Override
        public void close() throws InterruptedException, ExecutionException, TimeoutException {
            CompletableFuture<?>[] completions = clients.stream()
                    .map(ReleasableResource::closeResourceAsync)
                    .map(completion -> completion.toCompletableFuture())
                    .toArray(CompletableFuture<?>[]::new);
            server.stop();
            CompletableFuture.allOf(completions).get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            for (MeterRegistry meterRegistry : registries) {
                await("Transport meter cleanup", () -> meterRegistry.meters().stream()
                        .filter(meter -> meter.id().name().startsWith("helidon.http."))
                        .count(), is(0L));
                meterRegistry.close();
            }
        }

        private MeterRegistry addRegistry() {
            return addRegistry(MetricsConfig.builder().warnOnMultipleRegistries(false).build());
        }

        private MeterRegistry addRegistry(MetricsConfig metricsConfig) {
            MeterRegistry meterRegistry = Services.get(MetricsFactory.class)
                    .createMeterRegistry(metricsConfig);
            registries.add(meterRegistry);
            return meterRegistry;
        }

        private WebClient client(Mode mode, MeterRegistry meterRegistry) {
            var builder = WebClient.builder()
                    .baseUri((secure ? "https" : "http") + "://localhost:" + server.port())
                    .connectTimeout(TIMEOUT)
                    .readTimeout(TIMEOUT)
                    .shareConnectionCache(true)
                    .protocolPreference(List.of(metricProtocol(protocol).equals("http/2") ? "h2" : "http/1.1"))
                    .addProtocolConfig(Http2ClientProtocolConfig.builder().priorKnowledge(true).build());
            if (mode != Mode.ABSENT) {
                builder.addService(WebClientTransportMetrics.builder()
                                           .enabled(mode == Mode.ENABLED)
                                           .meterRegistry(meterRegistry)
                                           .build());
            }
            if (secure) {
                builder.tls(Tls.builder()
                                    .trust(trust -> trust.keystore(store -> store.passphrase("password")
                                            .trustStore(true).keystore(Resource.create("client.p12"))))
                                    .build());
            }
            WebClient client = builder.build();
            clients.add(client);
            return client;
        }

        private void exchange(Mode mode) throws Exception {
            WebClient client = client(mode, registry);
            switch (protocol) {
                case "http/1.1", "h2" -> request(client, protocol, "/success", 200);
                case "grpc" -> {
                    var grpcClient = GrpcClient.builder().from(client.prototype()).enableMetrics(false).build();
                    clients.add(grpcClient);
                    var service = grpcClient.serviceClient(GrpcServiceDescriptor.builder()
                            .serviceName(GRPC_SERVICE)
                            .putMethod(GRPC_METHOD, GrpcClientMethodDescriptor.unary(GRPC_SERVICE, GRPC_METHOD)
                                    .requestType(StringValue.class)
                                    .responseType(StringValue.class)
                                    .build())
                            .build());
                    assertThat("Successful unary response", service.unary(GRPC_METHOD, StringValue.of("echo")),
                               is(StringValue.of("echo")));
                }
                case "websocket" -> websocketExchange(client);
                default -> throw new IllegalArgumentException("Unsupported test protocol: " + protocol);
            }
        }

        private void websocketExchange(WebClient client) throws Exception {
            var opened = new CompletableFuture<WsSession>();
            var received = new CompletableFuture<String>();
            var closed = new CompletableFuture<Integer>();
            clients.add(client.client(Http1Client.PROTOCOL));
            WsClient websocket = client.client(WsClient.PROTOCOL);
            clients.add(websocket);
            websocket.connect("/websocket", new WsListener() {
                @Override
                public void onOpen(WsSession session) {
                    opened.complete(session);
                }

                @Override
                public void onMessage(WsSession session, String text, boolean last) {
                    if (last) {
                        received.complete(text);
                    } else {
                        received.completeExceptionally(new IllegalStateException("Unexpected fragmented echo"));
                    }
                }

                @Override
                public void onError(WsSession session, Throwable failure) {
                    opened.completeExceptionally(failure);
                    received.completeExceptionally(failure);
                    closed.completeExceptionally(failure);
                }

                @Override
                public void onClose(WsSession session, int status, String reason) {
                    closed.complete(status);
                }
            });
            WsSession session = opened.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            try {
                session.send("echo", true);
                assertThat("WebSocket echo after HTTP upgrade", received.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS), is("echo"));
            } finally {
                session.close(1000, "test complete");
                assertThat("WebSocket close status", closed.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS), is(1000));
            }
        }
    }
}
