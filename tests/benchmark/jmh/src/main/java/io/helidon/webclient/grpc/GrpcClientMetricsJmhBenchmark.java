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
package io.helidon.webclient.grpc;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.service.registry.Services;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientUri;

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Measures gRPC call initialization and terminal metric recording without socket I/O.
 * A reused status exception terminates transport setup through the normal call lifecycle;
 * no message serialization or transport construction is measured, and the callback only captures status.
 * The new-channel case misses the channel's method/status cache while reusing registry meters,
 * so it does not introduce unbounded meter cardinality.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
@SuppressWarnings("helidon:api:internal")
public class GrpcClientMetricsJmhBenchmark {
    @Param({"UNARY", "BIDI_STREAMING"})
    public MethodDescriptor.MethodType methodType;

    @Param({"true", "false"})
    public boolean metricsEnabled;

    @Param({"OK", "UNAVAILABLE", "CANCELLED"})
    public Status.Code terminalStatus;

    @Param({"shared", "new-channel"})
    public String channelCache;

    private final Metadata metadata = new Metadata();
    private final TerminalListener listener = new TerminalListener();
    private GrpcClient client;
    private GrpcChannel channel;
    private MeterRegistry registry;
    private MethodDescriptor<byte[], byte[]> descriptor;
    private StatusRuntimeException terminalFailure;

    /**
     * Creates a client and warms the shared channel's metrics for the chosen status.
     */
    @Setup(Level.Trial)
    public void setup() {
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        registry = metricsFactory.createMeterRegistry(MetricsConfig.builder()
                                                             .warnOnMultipleRegistries(false)
                                                             .build());
        client = GrpcClient.builder()
                .baseUri("http://localhost:1")
                .tls(tls -> tls.enabled(false))
                .enableMetrics(metricsEnabled)
                .meterRegistry(registry)
                .build();
        channel = (GrpcChannel) client.channel();
        MethodDescriptor.Marshaller<byte[]> marshaller = new MethodDescriptor.Marshaller<>() {
            @Override
            public InputStream stream(byte[] value) {
                return new ByteArrayInputStream(value);
            }

            @Override
            public byte[] parse(InputStream stream) {
                throw new AssertionError("The metric benchmark must not parse messages");
            }
        };
        descriptor = MethodDescriptor.<byte[], byte[]>newBuilder()
                .setType(methodType)
                .setFullMethodName("benchmark.Metrics/Call")
                .setRequestMarshaller(marshaller)
                .setResponseMarshaller(marshaller)
                .build();
        terminalFailure = Status.fromCode(terminalStatus).asRuntimeException();
        Status status = startAndClose();
        if (status == null || status.getCode() != terminalStatus) {
            throw new AssertionError("Benchmark did not reach the requested terminal status: " + status);
        }
        verifyMetricPath(metricsFactory);
    }

    /**
     * Starts and terminates one call through the production lifecycle.
     *
     * @return status delivered by the terminal callback
     */
    @Benchmark
    public Status startAndClose() {
        GrpcChannel callChannel = channelCache.equals("shared") ? channel : (GrpcChannel) client.channel();
        GrpcBaseClientCall<byte[], byte[]> call = methodType == MethodDescriptor.MethodType.UNARY
                ? new GrpcUnaryClientCall<>(callChannel, descriptor, CallOptions.DEFAULT) {
                    @Override
                    ClientConnection clientConnection(ClientUri clientUri, String authority) {
                        throw terminalFailure;
                    }
                }
                : new GrpcClientCall<>(callChannel, descriptor, CallOptions.DEFAULT) {
                    @Override
                    ClientConnection clientConnection(ClientUri clientUri, String authority) {
                        throw terminalFailure;
                    }
                };
        listener.status = null;
        call.start(listener, metadata);
        return listener.status;
    }

    /**
     * Releases the benchmark-owned metric registry.
     */
    @TearDown(Level.Trial)
    public void tearDown() {
        registry.close();
    }

    private void verifyMetricPath(MetricsFactory metricsFactory) {
        if (!metricsEnabled) {
            if (!registry.meters().isEmpty()) {
                throw new AssertionError("Metrics-disabled calls must not register meters");
            }
            return;
        }
        List<Tag> startedTags = List.of(metricsFactory.tagCreate("grpc.method", descriptor.getFullMethodName()),
                                        metricsFactory.tagCreate("grpc.target", channel.baseUri().toString()));
        if (registry.counter("grpc.client.attempt.started", startedTags).orElseThrow().count() != 1) {
            throw new AssertionError("Benchmark did not record exactly one started call");
        }
        List<Tag> tags = List.of(startedTags.get(0), startedTags.get(1),
                                metricsFactory.tagCreate("grpc.status", terminalStatus.name()));
        // The same fixture can qualify the pre-fix source, which only recorded OK calls.
        long expected = Boolean.parseBoolean(System.getProperty("benchmark.expectedTerminalMetrics", "true"))
                || terminalStatus == Status.Code.OK ? 1 : 0;
        long duration = registry.timer("grpc.client.attempt.duration", tags).map(timer -> timer.count()).orElse(0L);
        long sent = registry.summary("grpc.client.attempt.sent_total_compressed_message_size", tags)
                .map(summary -> summary.count()).orElse(0L);
        long received = registry.summary("grpc.client.attempt.rcvd_total_compressed_message_size", tags)
                .map(summary -> summary.count()).orElse(0L);
        if (duration != expected || sent != expected || received != expected) {
            throw new AssertionError("Terminal metric counts for " + terminalStatus + ": expected " + expected
                                             + ", duration=" + duration + ", sent=" + sent + ", received=" + received);
        }
    }

    private static final class TerminalListener extends ClientCall.Listener<byte[]> {
        private Status status;

        @Override
        public void onClose(Status status, Metadata trailers) {
            this.status = status;
        }
    }
}
