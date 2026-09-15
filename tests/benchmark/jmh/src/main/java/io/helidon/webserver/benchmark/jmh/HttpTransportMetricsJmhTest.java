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

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.StreamObservation;
import io.helidon.http.metrics.HttpTransportMetrics;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;

import static io.helidon.http.HttpTransportObserver.ConnectionOutcome.NORMAL;
import static io.helidon.http.HttpTransportObserver.Direction.BIDIRECTIONAL;
import static io.helidon.http.HttpTransportObserver.Handshake.QUIC_TLS;
import static io.helidon.http.HttpTransportObserver.Handshake.TLS;
import static io.helidon.http.HttpTransportObserver.HandshakeOutcome.SUCCESS;
import static io.helidon.http.HttpTransportObserver.Initiator.REMOTE;
import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_2;
import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_3;
import static io.helidon.http.HttpTransportObserver.Role.SERVER;
import static io.helidon.http.HttpTransportObserver.StreamOutcome.COMPLETED;
import static io.helidon.http.HttpTransportObserver.TRANSPORT_QUIC;
import static io.helidon.http.HttpTransportObserver.TRANSPORT_TCP;

/**
 * Benchmarks warmed HTTP transport stream metrics for HTTP/2 and HTTP/3.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class HttpTransportMetricsJmhTest {
    private static final int CONTENDED_THREADS = 8;

    @Benchmark
    @Threads(1)
    public void warmedHttp2StreamLifecycleSingleThread(Http2TransportMetricsState state) {
        StreamObservation stream = state.connection().streamOpened(BIDIRECTIONAL, REMOTE);
        stream.close(COMPLETED);
    }

    @Benchmark
    @Threads(CONTENDED_THREADS)
    public void warmedHttp2StreamLifecycleContended(Http2TransportMetricsState state) {
        StreamObservation stream = state.connection().streamOpened(BIDIRECTIONAL, REMOTE);
        stream.close(COMPLETED);
    }

    @Benchmark
    @Threads(1)
    public void warmedHttp3StreamLifecycleSingleThread(Http3TransportMetricsState state) {
        StreamObservation stream = state.connection().streamOpened(BIDIRECTIONAL, REMOTE);
        stream.close(COMPLETED);
    }

    @Benchmark
    @Threads(CONTENDED_THREADS)
    public void warmedHttp3StreamLifecycleContended(Http3TransportMetricsState state) {
        StreamObservation stream = state.connection().streamOpened(BIDIRECTIONAL, REMOTE);
        stream.close(COMPLETED);
    }

    /**
     * Shared, warmed metrics state.
     */
    public abstract static class TransportMetricsState {
        private static final long REGISTRATION_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(10);
        private static final long REGISTRATION_POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(1);

        private final String protocol;
        private MeterRegistry registry;
        private HttpTransportMetrics.Lease lease;
        private ConnectionObservation connection;

        private TransportMetricsState(String protocol) {
            this.protocol = protocol;
        }

        @Setup(Level.Trial)
        public void setup() {
            registry = MetricsFactory.getInstance().createMeterRegistry(MetricsConfig.create());
            lease = HttpTransportMetrics.acquire(registry);
            connection = lease.connectionOpened(SERVER,
                                                PROTOCOL_HTTP_2.equals(protocol) ? TRANSPORT_TCP : TRANSPORT_QUIC,
                                                PROTOCOL_HTTP_2.equals(protocol) ? TLS : QUIC_TLS);
            connection.handshakeStarted().close(SUCCESS);
            connection.protocolSelected(protocol);

            StreamObservation warmupStream = connection.streamOpened(BIDIRECTIONAL, REMOTE);
            warmupStream.close(COMPLETED);

            String protocolTag = PROTOCOL_HTTP_2.equals(protocol) ? "http/2" : "http/3";
            List<Tag> streamTags = List.of(Tag.create("role", "server"),
                                           Tag.create("protocol", protocolTag),
                                           Tag.create("direction", "bidi"),
                                           Tag.create("initiator", "remote"));
            List<Tag> closedStreamTags = List.of(Tag.create("role", "server"),
                                                 Tag.create("protocol", protocolTag),
                                                 Tag.create("direction", "bidi"),
                                                 Tag.create("initiator", "remote"),
                                                 Tag.create("outcome", "completed"));
            long deadline = System.nanoTime() + REGISTRATION_TIMEOUT_NANOS;
            while (registry.counter("http.streams.opened", streamTags).isEmpty()
                    || registry.gauge("http.streams.active", streamTags).isEmpty()
                    || registry.counter("http.streams.closed", closedStreamTags).isEmpty()
                    || registry.timer("http.streams.duration", closedStreamTags).isEmpty()) {
                if (System.nanoTime() >= deadline) {
                    throw new IllegalStateException(
                            "HTTP transport stream meters were not registered before the benchmark");
                }
                LockSupport.parkNanos(REGISTRATION_POLL_NANOS);
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            connection.close(NORMAL);
            lease.close();
            try {
                lease.completion().toCompletableFuture().orTimeout(10, TimeUnit.SECONDS).join();
            } finally {
                registry.close();
            }
        }

        final ConnectionObservation connection() {
            return connection;
        }
    }

    /**
     * HTTP/2 transport metrics state.
     */
    @State(Scope.Benchmark)
    public static class Http2TransportMetricsState extends TransportMetricsState {
        /**
         * Create HTTP/2 state.
         */
        public Http2TransportMetricsState() {
            super(PROTOCOL_HTTP_2);
        }
    }

    /**
     * HTTP/3 transport metrics state.
     */
    @State(Scope.Benchmark)
    public static class Http3TransportMetricsState extends TransportMetricsState {
        /**
         * Create HTTP/3 state.
         */
        public Http3TransportMetricsState() {
            super(PROTOCOL_HTTP_3);
        }
    }
}
