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

package io.helidon.webclient.benchmark.jmh;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.service.registry.Services;
import io.helidon.webclient.api.HttpClient;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.ReleasableResource;
import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webclient.grpc.GrpcClientMethodDescriptor;
import io.helidon.webclient.grpc.GrpcServiceClient;
import io.helidon.webclient.grpc.GrpcServiceDescriptor;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.webclient.websocket.WsClient;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.grpc.GrpcConfig;
import io.helidon.webserver.grpc.GrpcProtocolSelector;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2ConnectionSelector;
import io.helidon.webserver.websocket.WsConfig;
import io.helidon.webserver.websocket.WsRouting;
import io.helidon.webserver.websocket.WsUpgrader;
import io.helidon.websocket.WsListener;
import io.helidon.websocket.WsSession;

import io.grpc.MethodDescriptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ServerCalls;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;

/**
 * Client transport metrics cost for real local HTTP/1, HTTP/2, gRPC unary, and WebSocket echo exchanges.
 * Server transport metrics and the separate gRPC method metrics are disabled. Client objects persist for a trial;
 * HTTP and WebSocket rows retain their original physical connection. Helidon's gRPC unary implementation opens and
 * closes a physical connection for each call, so its row includes connection establishment and TLS when selected.
 * WebSocket rows measure messages after one HTTP upgrade and require HTTP stream counts to remain unchanged.
 * Client and server sockets set {@code TCP_NODELAY=true} consistently before and after, avoiding Nagle delays for small frames.
 *
 * <p>Use an exact include for {@code ClientTransportMetricsJmhBenchmark.exchange}, with the GC profiler when comparing
 * allocation. This benchmark includes client, local server, and response validation work; it does not establish the
 * whole-server five-percent overhead target. TLS requires the existing {@code client.p12} and {@code server.p12}
 * benchmark fixtures from {@code target/test-classes} on the runtime classpath.
 *
 * <p>The same compiled harness can run against older client artifacts with {@code metricsMode=absent}. The new metrics
 * service and asynchronous cleanup API are resolved only outside measured exchanges, using reflection for compatibility.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@State(Scope.Benchmark)
public class ClientTransportMetricsJmhBenchmark {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String BODY = "Hello, World!";
    private static final byte[] BODY_BYTES = BODY.getBytes(StandardCharsets.US_ASCII);
    private static final String GRPC_SERVICE = "ClientTransportMetricsBenchmark";
    private static final String GRPC_METHOD = "Echo";
    private static final String METRICS_SERVICE = "io.helidon.webclient.metrics.WebClientTransportMetrics";

    private final ArrayBlockingQueue<String> websocketResponses = new ArrayBlockingQueue<>(1);
    private final CompletableFuture<WsSession> websocketOpened = new CompletableFuture<>();
    private final CompletableFuture<Void> websocketClosed = new CompletableFuture<>();
    private final AtomicReference<Throwable> websocketFailure = new AtomicReference<>();

    @Param({"http1", "http2", "grpc", "websocket"})
    private String protocol;

    @Param({"absent", "disabled", "enabled"})
    private String metricsMode;

    @Param({"false", "true"})
    private boolean tls;

    private MeterRegistry registry;
    private WebServer server;
    private Object clientResource;
    private HttpClient<?> httpClient;
    private GrpcServiceClient grpcClient;
    private WsSession websocket;
    private String connectionId;
    private String expectedProtocol;
    private List<Tag> connectionTags;
    private List<Tag> streamTags;
    private List<Tag> completedTags;
    private long warmedStreamCount;

    @Setup
    public void setup() {
        if (!List.of("absent", "disabled", "enabled").contains(metricsMode)) {
            throw new IllegalArgumentException("Unknown metrics mode: " + metricsMode);
        }
        expectedProtocol = switch (protocol) {
            case "http1", "websocket" -> "http/1.1";
            case "http2", "grpc" -> "http/2";
            default -> throw new IllegalArgumentException("Unknown protocol: " + protocol);
        };
        var metricsFactory = Services.get(MetricsFactory.class);
        connectionTags = List.of(metricsFactory.tagCreate("role", "client"), metricsFactory.tagCreate("transport", "tcp"),
                                 metricsFactory.tagCreate("handshake", tls ? "tls" : "none"));
        streamTags = List.of(metricsFactory.tagCreate("role", "client"), metricsFactory.tagCreate("protocol", expectedProtocol),
                             metricsFactory.tagCreate("direction", "bidi"), metricsFactory.tagCreate("initiator", "local"));
        completedTags = List.of(metricsFactory.tagCreate("role", "client"), metricsFactory.tagCreate("protocol", expectedProtocol),
                                metricsFactory.tagCreate("direction", "bidi"), metricsFactory.tagCreate("initiator", "local"),
                                metricsFactory.tagCreate("outcome", "completed"));
        try {
            registry = metricsFactory.createMeterRegistry(
                    MetricsConfig.builder().warnOnMultipleRegistries(false).build());
            server = server();
            server.start();
            configureClient();
            if (httpClient != null) {
                connectionId = new String(httpExchange("/connection"), StandardCharsets.US_ASCII);
            } else if (websocket != null) {
                connectionId = websocket.socketContext().socketId();
            }
            exchange();
            verifyConnection();
            if ("enabled".equals(metricsMode)) {
                awaitTransportMeters();
                warmedStreamCount = completedStreamCount();
            } else {
                verifyTransportMetricsAbsent();
            }
        } catch (RuntimeException failure) {
            try {
                closeResources();
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    @TearDown
    public void tearDown() {
        try {
            if ("enabled".equals(metricsMode)) {
                long actual = completedStreamCount();
                boolean websocketMessages = "websocket".equals(protocol);
                if (websocketMessages ? actual != warmedStreamCount : actual <= warmedStreamCount) {
                    throw new IllegalStateException("Unexpected HTTP stream progress for " + protocol
                                                            + ": warmed=" + warmedStreamCount + ", final=" + actual);
                }
                verifyOnlyClientTransportMetrics();
            } else {
                verifyTransportMetricsAbsent();
            }
            verifyConnection();
        } finally {
            closeResources();
        }
    }

    @Benchmark
    public Object exchange() {
        if (websocket != null) {
            websocket.send(BODY, true);
            String response;
            try {
                response = websocketResponses.poll(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted during WebSocket exchange", e);
            }
            if (!BODY.equals(response) || websocketFailure.get() != null) {
                throw new IllegalStateException("Unexpected WebSocket response: " + response, websocketFailure.get());
            }
            return response;
        }
        byte[] response = grpcClient == null ? httpExchange("/benchmark") : grpcClient.unary(GRPC_METHOD, BODY_BYTES);
        if (!Arrays.equals(response, BODY_BYTES)) {
            throw new IllegalStateException("Unexpected " + protocol + " response: " + Arrays.toString(response));
        }
        return response;
    }

    private static MethodDescriptor.Builder<byte[], byte[]> grpcMethod() {
        return MethodDescriptor.<byte[], byte[]>newBuilder()
                .setFullMethodName(MethodDescriptor.generateFullMethodName(GRPC_SERVICE, GRPC_METHOD))
                .setType(MethodDescriptor.MethodType.UNARY)
                .setRequestMarshaller(ByteArrayMarshaller.INSTANCE)
                .setResponseMarshaller(ByteArrayMarshaller.INSTANCE);
    }

    private static Tls serverTls() {
        Keys keys = Keys.builder()
                .keystore(store -> store.keystore(Resource.create("server.p12")).passphrase("password"))
                .build();
        return Tls.builder().privateKey(keys).privateKeyCertChain(keys).build();
    }

    private static Tls clientTls() {
        return Tls.builder()
                .trust(trust -> trust.keystore(store -> store.passphrase("password")
                        .trustStore(true)
                        .keystore(Resource.create("client.p12"))))
                .build();
    }

    private static <T> T await(CompletionStage<T> stage) {
        try {
            return stage.toCompletableFuture().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during benchmark lifecycle", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Benchmark lifecycle did not complete successfully", e);
        }
    }

    private static IllegalStateException reflectionFailure(String message, ReflectiveOperationException failure) {
        if (failure instanceof InvocationTargetException invocation && invocation.getCause() instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(message, failure);
    }

    private WebServer server() {
        WsConfig wsConfig = WsConfig.create();
        var service = ServerServiceDefinition.builder(GRPC_SERVICE)
                .addMethod(grpcMethod().build(), ServerCalls.asyncUnaryCall((request, response) -> {
                    response.onNext(request);
                    response.onCompleted();
                }))
                .build();
        return WebServer.builder()
                .host("127.0.0.1")
                .port(-1)
                .connectionOptions(options -> options.tcpNoDelay(true))
                .featuresDiscoverServices(false)
                .protocolsDiscoverServices(false)
                .addProtocol(wsConfig)
                .tls(tls ? serverTls() : Tls.builder().enabled(false).build())
                .addConnectionSelector(Http2ConnectionSelector.builder()
                                               .http2Config(Http2Config.create())
                                               .addSubProtocolSelector(GrpcProtocolSelector.create(GrpcConfig.create()))
                                               .build())
                .addConnectionSelector(Http1ConnectionSelector.builder()
                                               .config(Http1Config.create())
                                               .addUpgrader(WsUpgrader.create(wsConfig))
                                               .build())
                .routing(routing -> routing
                        .get("/benchmark", (_, response) -> response.send(BODY_BYTES))
                        .get("/connection", (request, response) -> response.send(request.socketId())))
                .addRouting(GrpcRouting.builder().service(service))
                .addRouting(WsRouting.builder().endpoint("/websocket", new WsListener() {
                    @Override
                    public void onOpen(WsSession session) {
                        if (!"1.1".equals(session.prologue().protocolVersion())) {
                            websocketFailure.set(new IllegalStateException("WebSocket upgrade did not use HTTP/1.1"));
                        }
                    }

                    @Override
                    public void onMessage(WsSession session, String text, boolean last) {
                        session.send(text, last);
                    }
                }))
                .build();
    }

    private void configureClient() {
        String uri = (tls ? "https" : "http") + "://localhost:" + server.port();
        Tls clientTls = tls ? clientTls() : Tls.builder().enabled(false).build();
        List<WebClientService> services = "absent".equals(metricsMode) ? List.of() : List.of(metricsService());
        switch (protocol) {
            case "http1" -> {
                httpClient = Http1Client.builder().baseUri(uri).tls(clientTls).proxy(Proxy.noProxy())
                        .socketOptions(options -> options.tcpNoDelay(true))
                        .shareConnectionCache(false).servicesDiscoverServices(false).services(services)
                        .connectTimeout(TIMEOUT).readTimeout(TIMEOUT).build();
                clientResource = httpClient;
            }
            case "http2" -> {
                httpClient = Http2Client.builder().baseUri(uri).tls(clientTls).proxy(Proxy.noProxy())
                        .socketOptions(options -> options.tcpNoDelay(true))
                        .shareConnectionCache(false).servicesDiscoverServices(false).services(services)
                        .protocolConfig(config -> config.priorKnowledge(!tls))
                        .connectTimeout(TIMEOUT).readTimeout(TIMEOUT).build();
                clientResource = httpClient;
            }
            case "grpc" -> {
                var client = GrpcClient.builder().baseUri(uri).tls(clientTls).proxy(Proxy.noProxy())
                        .socketOptions(options -> options.tcpNoDelay(true))
                        .shareConnectionCache(false).servicesDiscoverServices(false).services(services)
                        .enableMetrics(false).connectTimeout(TIMEOUT).readTimeout(TIMEOUT).build();
                clientResource = client;
                grpcClient = client.serviceClient(GrpcServiceDescriptor.builder()
                                                         .serviceName(GRPC_SERVICE)
                                                         .putMethod(GRPC_METHOD, GrpcClientMethodDescriptor.create(
                                                                 GRPC_SERVICE, GRPC_METHOD, grpcMethod()))
                                                         .build());
            }
            case "websocket" -> {
                var client = WsClient.builder().baseUri(uri).tls(clientTls).proxy(Proxy.noProxy())
                        .socketOptions(options -> options.tcpNoDelay(true))
                        .shareConnectionCache(false).servicesDiscoverServices(false).services(services)
                        .connectTimeout(TIMEOUT).readTimeout(TIMEOUT).build();
                clientResource = client;
                client.connect("/websocket", websocketListener());
                websocket = await(websocketOpened);
            }
            default -> throw new IllegalArgumentException("Unknown protocol: " + protocol);
        }
    }

    private WsListener websocketListener() {
        return new WsListener() {
            @Override
            public void onOpen(WsSession session) {
                websocketOpened.complete(session);
            }

            @Override
            public void onMessage(WsSession session, String text, boolean last) {
                if (!last || !websocketResponses.offer(text)) {
                    websocketFailure.compareAndSet(null, new IllegalStateException("Unexpected WebSocket response framing"));
                }
            }

            @Override
            public void onError(WsSession session, Throwable failure) {
                websocketFailure.compareAndSet(null, failure);
                websocketOpened.completeExceptionally(failure);
                websocketClosed.completeExceptionally(failure);
            }

            @Override
            public void onClose(WsSession session, int status, String reason) {
                websocketClosed.complete(null);
            }
        };
    }

    private WebClientService metricsService() {
        try {
            Object builder = Class.forName(METRICS_SERVICE).getMethod("builder").invoke(null);
            builder.getClass().getMethod("enabled", boolean.class).invoke(builder, "enabled".equals(metricsMode));
            builder.getClass().getMethod("meterRegistry", MeterRegistry.class).invoke(builder, registry);
            return (WebClientService) builder.getClass().getMethod("build").invoke(builder);
        } catch (ReflectiveOperationException e) {
            throw reflectionFailure("Cannot configure client transport metrics for " + metricsMode, e);
        }
    }

    private byte[] httpExchange(String path) {
        try (var response = httpClient.get(path).request()) {
            String wireProtocol = "http2".equals(protocol) ? Http2Client.PROTOCOL_ID : Http1Client.PROTOCOL_ID;
            if (response.status().code() != 200 || !wireProtocol.equals(response.protocolId())) {
                throw new IllegalStateException("Expected " + wireProtocol + " status 200, received "
                                                        + response.protocolId() + " status " + response.status());
            }
            return response.as(byte[].class);
        }
    }

    private void verifyConnection() {
        if (httpClient != null) {
            String actual = new String(httpExchange("/connection"), StandardCharsets.US_ASCII);
            if (connectionId.isEmpty() || !connectionId.equals(actual)) {
                throw new IllegalStateException(protocol + " did not retain its original physical connection");
            }
        } else if (websocket != null
                && (connectionId.isEmpty() || !connectionId.equals(websocket.socketContext().socketId())
                || websocketClosed.isDone() || websocketFailure.get() != null)) {
            throw new IllegalStateException("WebSocket did not retain its original healthy session", websocketFailure.get());
        }
    }

    private void awaitTransportMeters() {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (completedStreamCount() == 0
                || registry.counter("helidon.http.connections.opened", connectionTags).map(Counter::count).orElse(0L) == 0
                || registry.counter("helidon.http.streams.opened", streamTags).map(Counter::count).orElse(0L) == 0) {
            if (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline) {
                throw new IllegalStateException("Expected client transport metrics were not published for " + protocol);
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        verifyOnlyClientTransportMetrics();
    }

    private long completedStreamCount() {
        return registry.counter("helidon.http.streams.closed", completedTags).map(Counter::count).orElse(0L);
    }

    private void verifyOnlyClientTransportMetrics() {
        for (var meter : registry.meters()) {
            if (meter.id().name().startsWith("helidon.http.")
                    && !"client".equals(meter.id().tagsMap().get("role"))) {
                throw new IllegalStateException("Unexpected non-client HTTP transport meter: " + meter.id());
            }
        }
    }

    private void verifyTransportMetricsAbsent() {
        for (var meter : registry.meters()) {
            if (meter.id().name().startsWith("helidon.http.")) {
                throw new IllegalStateException("Unexpected transport meter in " + metricsMode + " mode: " + meter.id());
            }
        }
    }

    private void closeResources() {
        CompletionStage<?> clientClosed;
        try {
            if (websocket != null) {
                websocket.terminate();
            }
            clientClosed = closeClient();
        } finally {
            if (server != null) {
                server.stop();
            }
        }
        await(clientClosed);
        if (registry != null) {
            registry.close();
        }
    }

    private CompletionStage<?> closeClient() {
        if (clientResource instanceof ReleasableResource resource) {
            try {
                return (CompletionStage<?>) ReleasableResource.class.getMethod("closeResourceAsync").invoke(resource);
            } catch (NoSuchMethodException _) {
                resource.closeResource();
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw reflectionFailure("Cannot close benchmark client", e);
            }
        }
        return CompletableFuture.completedStage(null);
    }

    private enum ByteArrayMarshaller implements MethodDescriptor.Marshaller<byte[]> {
        INSTANCE;

        @Override
        public InputStream stream(byte[] value) {
            return new ByteArrayInputStream(value);
        }

        @Override
        public byte[] parse(InputStream stream) {
            try {
                return stream.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
