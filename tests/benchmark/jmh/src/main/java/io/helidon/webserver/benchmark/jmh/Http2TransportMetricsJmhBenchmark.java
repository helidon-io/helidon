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

package io.helidon.webserver.benchmark.jmh;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigNode.ListNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.service.registry.Services;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2ConnectionSelector;
import io.helidon.webserver.http2.Http2Upgrader;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.metrics.AutoHttpMetricsConfig;
import io.helidon.webserver.observe.metrics.MetricsObserver;

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
 * Real HTTP/2 keep-alive exchanges with absent, disabled, or enabled automatic HTTP metrics.
 * The same classpath and explicit discovery settings apply to all modes. The JDK client avoids adding Helidon client metrics.
 * Server sockets set {@code TCP_NODELAY=true} consistently before and after, avoiding Nagle delays for small responses.
 * Measurements include client work and response validation, so they do not establish whole-server throughput improvements.
 * Set {@code expectHttp2Streams=false} only for a baseline without HTTP/2 stream publishing. This parameter changes setup
 * and teardown validation only; the timed exchange is identical. Enabled metrics always require physical connection meters.
 * The {@code percentiles=false} setting disables only the stream duration timer's local percentiles. Setup requires runtime
 * support for this per-meter override and verifies the actual timer snapshot when HTTP/2 stream publishing is expected.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@State(Scope.Benchmark)
public class Http2TransportMetricsJmhBenchmark {
    private static final Duration EXCHANGE_TIMEOUT = Duration.ofSeconds(10);
    private static final String STREAM_DURATION = "helidon.http.streams.duration";
    private static final List<Double> DEFAULT_PERCENTILES = List.of(0.5, 0.75, 0.95, 0.98, 0.99, 0.999);
    private static final byte[] RESPONSE_BYTES = "Hello, World!".getBytes(StandardCharsets.US_ASCII);
    private static final MetricsFactory METRICS_FACTORY = Services.get(MetricsFactory.class);
    private static final List<Tag> CONNECTION_TAGS = List.of(METRICS_FACTORY.tagCreate("role", "server"),
                                                            METRICS_FACTORY.tagCreate("transport", "tcp"),
                                                            METRICS_FACTORY.tagCreate("handshake", "none"));
    private static final List<Tag> STREAM_TAGS = List.of(METRICS_FACTORY.tagCreate("role", "server"),
                                                        METRICS_FACTORY.tagCreate("protocol", "http/2"),
                                                        METRICS_FACTORY.tagCreate("direction", "bidi"),
                                                        METRICS_FACTORY.tagCreate("initiator", "remote"));
    private static final List<Tag> COMPLETED_STREAM_TAGS = List.of(METRICS_FACTORY.tagCreate("role", "server"),
                                                                  METRICS_FACTORY.tagCreate("protocol", "http/2"),
                                                                  METRICS_FACTORY.tagCreate("direction", "bidi"),
                                                                  METRICS_FACTORY.tagCreate("initiator", "remote"),
                                                                  METRICS_FACTORY.tagCreate("outcome", "completed"));

    @Param({"absent", "disabled", "enabled"})
    private String metricsMode;

    @Param({"true"})
    private boolean expectHttp2Streams;

    @Param({"true"})
    private boolean percentiles = true;

    private MeterRegistry registry;
    private WebServer server;
    private HttpClient client;
    private HttpRequest request;
    private HttpRequest connectionRequest;
    private byte[] socketId;
    private long warmedStreamCount;
    private long warmedOpenedStreamCount;
    private long warmedTimerCount;

    @Setup
    public void setup() {
        boolean metricsEnabled = switch (metricsMode) {
            case "absent", "disabled" -> false;
            case "enabled" -> true;
            default -> throw new IllegalArgumentException("Unknown metrics mode: " + metricsMode);
        };
        try {
            registry = METRICS_FACTORY
                    .createMeterRegistry(metricsConfig());
            Http2Config http2Config = Http2Config.create();
            var serverBuilder = WebServer.builder()
                    .host("127.0.0.1")
                    .port(-1)
                    .connectionOptions(options -> options.tcpNoDelay(true))
                    .featuresDiscoverServices(false)
                    .protocolsDiscoverServices(false)
                    .addConnectionSelector(Http2ConnectionSelector.builder()
                                                   .http2Config(http2Config)
                                                   .build())
                    .addConnectionSelector(Http1ConnectionSelector.builder()
                                                   .config(Http1Config.create())
                                                   .addUpgrader(Http2Upgrader.create(http2Config))
                                                   .build())
                    .routing(routing -> routing
                            .get("/benchmark", (_, response) -> response.send(RESPONSE_BYTES))
                            .get("/connection", (request, response) -> response.send(request.socketId())));
            if (!"absent".equals(metricsMode)) {
                serverBuilder.addFeature(ObserveFeature.builder()
                                                 .observersDiscoverServices(false)
                                                 .addObserver(MetricsObserver.builder()
                                                                      .meterRegistry(registry)
                                                                      .autoHttpMetrics(AutoHttpMetricsConfig.builder()
                                                                                               .enabled(metricsEnabled)
                                                                                               .build())
                                                                      .build())
                                                 .build());
            }
            server = serverBuilder.build();
            server.start();
            client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_2)
                    .connectTimeout(EXCHANGE_TIMEOUT)
                    .build();
            request = request("/benchmark");
            connectionRequest = request("/connection");
            socketId = exchange(connectionRequest);
            keepAliveExchange();
            verifyConnection();
            if (metricsEnabled) {
                awaitTransportMeters();
                if (expectHttp2Streams) {
                    verifyStreamPercentiles();
                    snapshotStreamMeters();
                    keepAliveExchange();
                    await(this::streamMetersAdvanced, "HTTP/2 stream counters and timer to advance");
                    snapshotStreamMeters();
                }
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
                if (!physicalConnectionMetersActive()) {
                    throw new IllegalStateException("Physical connection metrics are not active");
                }
                if (expectHttp2Streams) {
                    if (!streamMetersAdvanced()) {
                        throw new IllegalStateException("HTTP/2 stream meters did not advance: opened "
                                                                + warmedOpenedStreamCount + " -> " + openedStreamCount()
                                                                + ", closed " + warmedStreamCount + " -> " + completedStreamCount()
                                                                + ", timer " + warmedTimerCount + " -> " + streamTimerCount());
                    }
                }
            } else {
                verifyTransportMetricsAbsent();
            }
            verifyConnection();
        } finally {
            closeResources();
        }
    }

    @Benchmark
    public byte[] keepAliveExchange() {
        byte[] body = exchange(request);
        if (!Arrays.equals(body, RESPONSE_BYTES)) {
            throw new IllegalStateException("Unexpected HTTP/2 response body: " + Arrays.toString(body));
        }
        return body;
    }

    private HttpRequest request(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + server.port() + path))
                .timeout(EXCHANGE_TIMEOUT)
                .build();
    }

    private byte[] exchange(HttpRequest request) {
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.version() != HttpClient.Version.HTTP_2 || response.statusCode() != 200) {
                throw new IllegalStateException("Expected HTTP/2 status 200, received "
                                                        + response.version() + " status " + response.statusCode());
            }
            return response.body();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during HTTP/2 exchange", e);
        }
    }

    private void verifyConnection() {
        byte[] actual = exchange(connectionRequest);
        if (socketId.length == 0 || !Arrays.equals(socketId, actual)) {
            throw new IllegalStateException("HTTP/2 benchmark did not retain its original connection");
        }
    }

    private void awaitTransportMeters() {
        await(() -> physicalConnectionMetersActive() && (!expectHttp2Streams || streamMetersActive()),
              "HTTP transport meters (expectHttp2Streams=" + expectHttp2Streams + ")");
    }

    private void await(BooleanSupplier condition, String description) {
        long deadline = System.nanoTime() + EXCHANGE_TIMEOUT.toNanos();
        while (!condition.getAsBoolean()) {
            if (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline) {
                throw new IllegalStateException("Timed out waiting for " + description);
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
    }

    private boolean physicalConnectionMetersActive() {
        return registry.counter("helidon.http.connections.opened", CONNECTION_TAGS).map(Counter::count).orElse(0L) > 0
                && registry.meters().stream()
                        .anyMatch(meter -> meter instanceof Gauge<?> gauge
                                && meter.id().name().equals("helidon.http.connections.active")
                                && gauge.value().doubleValue() > 0);
    }

    private boolean streamMetersActive() {
        return completedStreamCount() > 0
                && openedStreamCount() > 0
                && streamTimerCount() > 0
                && registry.gauge("helidon.http.streams.active", STREAM_TAGS).isPresent();
    }

    private boolean streamMetersAdvanced() {
        return completedStreamCount() > warmedStreamCount
                && openedStreamCount() > warmedOpenedStreamCount
                && streamTimerCount() > warmedTimerCount;
    }

    private void snapshotStreamMeters() {
        warmedStreamCount = completedStreamCount();
        warmedOpenedStreamCount = openedStreamCount();
        warmedTimerCount = streamTimerCount();
    }

    private long openedStreamCount() {
        return registry.counter("helidon.http.streams.opened", STREAM_TAGS).map(Counter::count).orElse(0L);
    }

    private long streamTimerCount() {
        return registry.timer(STREAM_DURATION, COMPLETED_STREAM_TAGS).map(Timer::count).orElse(0L);
    }

    private void verifyStreamPercentiles() {
        Timer timer = registry.timer(STREAM_DURATION, COMPLETED_STREAM_TAGS).orElseThrow();
        List<Double> actual = new ArrayList<>();
        timer.snapshot().percentileValues().forEach(value -> actual.add(value.percentile()));
        List<Double> expected = percentiles ? DEFAULT_PERCENTILES : List.of();
        if (!actual.equals(expected)) {
            throw new IllegalStateException("Unexpected stream duration percentiles: expected "
                                                    + expected + ", actual " + actual);
        }
    }

    private MetricsConfig metricsConfig() {
        Config config = Config.empty();
        if (!percentiles) {
            ObjectNode meter = ObjectNode.builder()
                    .addValue("name", STREAM_DURATION)
                    .addList("percentiles", ListNode.builder().build())
                    .build();
            config = Config.just(ConfigSources.create(ObjectNode.builder()
                                                              .addList("meters", ListNode.builder().addObject(meter).build())
                                                              .build()));
        }
        return MetricsConfig.builder().config(config).warnOnMultipleRegistries(false).build();
    }

    private long completedStreamCount() {
        return registry.counter("helidon.http.streams.closed", COMPLETED_STREAM_TAGS).map(Counter::count).orElse(0L);
    }

    private void verifyTransportMetricsAbsent() {
        for (var meter : registry.meters()) {
            String name = meter.id().name();
            if (name.startsWith("helidon.http.connections.")
                    || name.startsWith("helidon.http.handshakes.")
                    || name.startsWith("helidon.http.streams.")) {
                throw new IllegalStateException("Unexpected transport meter in " + metricsMode + " mode: " + name);
            }
        }
    }

    private void closeResources() {
        try {
            if (client != null) {
                client.close();
            }
        } finally {
            try {
                if (server != null) {
                    server.stop();
                }
            } finally {
                if (registry != null) {
                    registry.close();
                }
            }
        }
    }
}
