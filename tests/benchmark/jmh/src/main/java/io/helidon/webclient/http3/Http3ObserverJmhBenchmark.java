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

package io.helidon.webclient.http3;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import io.helidon.http.HttpTransportObserver;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.http.HttpTransportObserver.Direction;
import io.helidon.http.HttpTransportObserver.HandshakeObservation;
import io.helidon.http.HttpTransportObserver.Initiator;
import io.helidon.http.HttpTransportObserver.StreamObservation;
import io.helidon.http.Status;
import io.helidon.http.metrics.HttpTransportMetrics;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.webclient.api.HttpTransportObserverSupport.ObserverLifecycle;
import io.helidon.webclient.api.HttpTransportObserverSupport.ObserverProvider;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.spi.WebClientService;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Measures established HTTP/3 GET exchanges with different client transport observers.
 *
 * <p>All workers share one persistent loopback connection. Each request consumes an empty response; the server checks
 * connection reuse equally in every mode. This closed-loop benchmark includes both endpoints, so the GC profiler's
 * allocation per operation includes server and transport work throughout the fork, not just the observer adapter.
 * Run the throughput and sample-time modes explicitly with the desired worker count. Forks need an external timeout
 * covering synchronous client and server shutdown.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Threads(1)
public class Http3ObserverJmhBenchmark {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final List<Tag> CONNECTION_OPEN_TAGS = List.of(Tag.create("role", "client"),
                                                                 Tag.create("transport", "quic"),
                                                                 Tag.create("handshake", "quic-tls"));
    private static final List<Tag> CONNECTION_TAGS = List.of(Tag.create("role", "client"),
                                                            Tag.create("transport", "quic"),
                                                            Tag.create("protocol", "http/3"));
    private static final List<Tag> HANDSHAKE_TAGS = List.of(Tag.create("role", "client"),
                                                           Tag.create("transport", "quic"),
                                                           Tag.create("handshake", "quic-tls"),
                                                           Tag.create("outcome", "success"));
    private static final List<Tag> STREAM_TAGS = List.of(Tag.create("role", "client"),
                                                        Tag.create("protocol", "http/3"),
                                                        Tag.create("direction", "bidi"),
                                                        Tag.create("initiator", "local"));
    private static final List<Tag> CLOSED_STREAM_TAGS = List.of(Tag.create("role", "client"),
                                                               Tag.create("protocol", "http/3"),
                                                               Tag.create("direction", "bidi"),
                                                               Tag.create("initiator", "local"),
                                                               Tag.create("outcome", "completed"));

    private final AtomicInteger observedConnections = new AtomicInteger();

    /**
     * Client transport observer used throughout one trial.
     */
    @Param({"NOOP", "METRICS", "NOOP_CHILDREN"})
    public ObservationMode observationMode;

    private Http3BenchmarkEnvironment environment;
    private Http3Client client;
    private MeterRegistry registry;
    private HttpTransportMetrics.Lease lease;
    private volatile String connectionId;

    /**
     * Establishes the connection and waits for the enabled metrics to become warm.
     *
     * @throws Exception if setup fails
     */
    @Setup
    public void setUp() throws Exception {
        observedConnections.set(0);
        connectionId = null;
        try {
            HttpTransportObserver observer = switch (observationMode) {
                case NOOP -> HttpTransportObserver.noop();
                case METRICS -> {
                    registry = MetricsFactory.getInstance().createMeterRegistry(MetricsConfig.create());
                    lease = HttpTransportMetrics.acquire(registry);
                    yield lease;
                }
                case NOOP_CHILDREN -> (_, _, _) -> {
                    observedConnections.incrementAndGet();
                    return new NoopChildrenConnection();
                };
            };
            environment = Http3BenchmarkEnvironment.create(routing -> routing.get("/get", (request, response) -> {
                String socketId = request.socketId();
                String expected = connectionId;
                if (expected == null) {
                    // Only the first, single-threaded setup request can establish this value.
                    connectionId = socketId;
                } else if (!expected.equals(socketId)) {
                    throw new IllegalStateException("HTTP/3 observer benchmark connection changed from "
                                                            + expected + " to " + socketId);
                }
                response.send();
            }));
            client = environment.client(environment.baseUri(environment.port()), TIMEOUT, builder -> builder
                    .servicesDiscoverServices(false)
                    .connectionCacheSize(1)
                    .readTimeout(TIMEOUT)
                    .addService(new ObserverService(observer))
                    .protocolConfig(Http3ClientProtocolConfig.builder()
                                            .priorKnowledge(true)
                                            .initialResponseTimeout(TIMEOUT)
                                            .handshakeTimeout(TIMEOUT)
                                            .streamOpenTimeout(TIMEOUT)
                                            .build()));
            establishedGet();
            establishedGet();
            if (observationMode == ObservationMode.METRICS) {
                awaitMeters();
            } else if (observationMode == ObservationMode.NOOP_CHILDREN && observedConnections.get() != 1) {
                throw new IllegalStateException("Expected one observed connection, found " + observedConnections.get());
            }
        } catch (Exception | Error failure) {
            try {
                tearDown();
            } catch (Exception | Error cleanupFailure) {
                if (cleanupFailure != failure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    /**
     * Closes the client and server, then releases the metrics lease before closing its registry.
     *
     * @throws Exception if cleanup fails
     */
    @TearDown
    public void tearDown() throws Exception {
        Throwable failure = null;
        if (client != null) {
            failure = close(client::closeResource, failure);
        }
        if (environment != null) {
            failure = close(environment, failure);
        }
        if (lease != null) {
            failure = close(() -> {
                lease.close();
                lease.completion().toCompletableFuture().get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            }, failure);
        }
        if (registry != null) {
            failure = close(registry::close, failure);
        }
        client = null;
        environment = null;
        lease = null;
        registry = null;
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof Exception exception) {
            throw exception;
        }
        if (failure != null) {
            throw new IllegalStateException("HTTP/3 observer benchmark cleanup failed", failure);
        }
    }

    /**
     * Completes one GET request over the established connection.
     *
     * @return successful HTTP status
     * @throws IOException if consuming the response fails
     */
    @Benchmark
    public Status establishedGet() throws IOException {
        try (Http3ClientResponse response = client.get("/get").request();
             var input = response.inputStream()) {
            if (!Status.OK_200.equals(response.status()) || !Http3Client.PROTOCOL_ID.equals(response.protocolId())) {
                throw new IllegalStateException("Expected successful HTTP/3 exchange, observed "
                                                        + response.status() + " over " + response.protocolId());
            }
            if (input.read() != -1) {
                throw new IllegalStateException("Expected an empty HTTP/3 benchmark response");
            }
            return response.status();
        }
    }

    private static Throwable close(AutoCloseable resource, Throwable failure) {
        try {
            resource.close();
        } catch (Throwable cleanupFailure) {
            if (failure == null) {
                return cleanupFailure;
            }
            if (failure != cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
        return failure;
    }

    private void awaitMeters() throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (registry.counter("helidon.http.connections.opened", CONNECTION_OPEN_TAGS)
                        .filter(meter -> meter.count() == 1).isEmpty()
                || registry.counter("helidon.http.connections.established", CONNECTION_TAGS)
                        .filter(meter -> meter.count() == 1).isEmpty()
                || registry.gauge("helidon.http.connections.active", CONNECTION_TAGS)
                        .filter(meter -> meter.value().longValue() == 1).isEmpty()
                || registry.counter("helidon.http.handshakes", HANDSHAKE_TAGS).filter(meter -> meter.count() == 1).isEmpty()
                || registry.timer("helidon.http.handshakes.duration", HANDSHAKE_TAGS)
                        .filter(meter -> meter.count() == 1).isEmpty()
                || registry.counter("helidon.http.streams.opened", STREAM_TAGS).filter(meter -> meter.count() == 2).isEmpty()
                || registry.gauge("helidon.http.streams.active", STREAM_TAGS)
                        .filter(meter -> meter.value().longValue() == 0).isEmpty()
                || registry.counter("helidon.http.streams.closed", CLOSED_STREAM_TAGS)
                        .filter(meter -> meter.count() == 2).isEmpty()
                || registry.timer("helidon.http.streams.duration", CLOSED_STREAM_TAGS)
                        .filter(meter -> meter.count() == 2).isEmpty()) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Interrupted waiting for HTTP/3 benchmark meters");
            }
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException("HTTP/3 client transport meters were not warmed before the benchmark");
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
    }

    /**
     * Observer scenarios exercise the canonical fast path, enabled metrics, and canonical no-op children.
     */
    public enum ObservationMode {
        /**
         * Exact canonical no-op observer, preserving the transport's disabled-observation fast path.
         */
        NOOP,
        /**
         * Enabled client HTTP transport metrics on a private registry.
         */
        METRICS,
        /**
         * Observed connection returning canonical no-op handshake and stream observations.
         */
        NOOP_CHILDREN
    }

    private record ObserverService(HttpTransportObserver observer)
            implements WebClientService, ObserverProvider {
        @Override
        public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
            return chain.proceed(request);
        }

        @Override
        public boolean enabled() {
            return observer != HttpTransportObserver.noop();
        }

        @Override
        public Object scope() {
            return observer;
        }

        @Override
        public ObserverLifecycle createObserver() {
            // The trial owns the optional metrics lease, including its asynchronous release barrier.
            return new ObserverLifecycle() {
                @Override
                public HttpTransportObserver start() {
                    return ObserverService.this.observer;
                }

                @Override
                public CompletionStage<Void> stop() {
                    return CompletableFuture.completedFuture(null);
                }
            };
        }
    }

    private static final class NoopChildrenConnection implements ConnectionObservation {
        @Override
        public HandshakeObservation handshakeStarted() {
            return HandshakeObservation.noop();
        }

        @Override
        public void protocolSelected(String protocol) {
        }

        @Override
        public StreamObservation streamOpened(Direction direction, Initiator initiator) {
            return StreamObservation.noop();
        }

        @Override
        public void close(ConnectionOutcome outcome) {
        }
    }
}
