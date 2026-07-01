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

package io.helidon.tests.benchmark.jmh.grpc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webclient.grpc.GrpcClientMethodDescriptor;
import io.helidon.webclient.grpc.GrpcServiceClient;
import io.helidon.webclient.grpc.GrpcServiceDescriptor;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.grpc.GrpcStreams;

import io.grpc.MethodDescriptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class GrpcStreamingJmhBenchmark {
    private static final String SERVICE_NAME = "GrpcStreamingBenchmark";
    private static final String SERVER_STREAMING = "ServerStreaming";
    private static final String CLIENT_STREAMING = "ClientStreaming";
    private static final String BIDIRECTIONAL = "Bidirectional";
    private static final String LEGACY_SERVER_STREAMING = "LegacyServerStreaming";
    private static final String LEGACY_CLIENT_STREAMING = "LegacyClientStreaming";
    private static final String LEGACY_BIDIRECTIONAL = "LegacyBidirectional";
    private static final int SMALL_MESSAGE_COUNT = 2048;
    private static final int LARGE_MESSAGE_COUNT = 8;
    private static final int SLOW_CONSUMER_TOKENS_PER_INVOCATION = 128_000_000;

    @Param({"1024", "131072"})
    private int payloadSize;

    private byte[] payload;
    private WebServer server;
    private GrpcServiceClient client;

    @Setup
    public void setup() {
        payload = new byte[payloadSize];
        Arrays.fill(payload, (byte) 1);

        ServerServiceDefinition service = ServerServiceDefinition.builder(SERVICE_NAME)
                .addMethod(method(SERVER_STREAMING, MethodDescriptor.MethodType.SERVER_STREAMING).build(),
                           ServerCalls.asyncServerStreamingCall((request, observer) ->
                                   GrpcStreams.serverStreaming(() -> Stream.generate(() -> payload)
                                                                           .limit(messageCount()),
                                                               observer)))
                .addMethod(method(CLIENT_STREAMING, MethodDescriptor.MethodType.CLIENT_STREAMING).build(),
                           ServerCalls.asyncClientStreamingCall(observer ->
                                   GrpcStreams.clientStreaming(requests -> {
                                       requests.forEach(ignored -> { });
                                       return payload;
                                   }, observer)))
                .addMethod(method(BIDIRECTIONAL, MethodDescriptor.MethodType.BIDI_STREAMING).build(),
                           ServerCalls.asyncBidiStreamingCall(observer ->
                                   GrpcStreams.bidirectional(requests -> requests.map(request -> request), observer)))
                .addMethod(method(LEGACY_SERVER_STREAMING, MethodDescriptor.MethodType.SERVER_STREAMING).build(),
                           ServerCalls.asyncServerStreamingCall((request, observer) ->
                                   LegacyGrpcStreams.serverStreaming(Stream.generate(() -> payload)
                                                                             .limit(messageCount()),
                                                                     observer)))
                .addMethod(method(LEGACY_CLIENT_STREAMING, MethodDescriptor.MethodType.CLIENT_STREAMING).build(),
                           ServerCalls.asyncClientStreamingCall(observer ->
                                   LegacyGrpcStreams.clientStreaming(payload, observer)))
                .addMethod(method(LEGACY_BIDIRECTIONAL, MethodDescriptor.MethodType.BIDI_STREAMING).build(),
                           ServerCalls.asyncBidiStreamingCall(LegacyGrpcStreams::bidirectional))
                .build();

        server = WebServer.builder()
                .addRouting(GrpcRouting.builder().service(service))
                .build()
                .start();

        GrpcClient grpcClient = GrpcClient.builder()
                .baseUri("http://localhost:" + server.port())
                .tls(tls -> tls.enabled(false))
                .build();
        client = grpcClient.serviceClient(GrpcServiceDescriptor.builder()
                                                   .serviceName(SERVICE_NAME)
                                                   .putMethod(SERVER_STREAMING,
                                                              GrpcClientMethodDescriptor.create(
                                                                      SERVICE_NAME,
                                                                      SERVER_STREAMING,
                                                                      method(SERVER_STREAMING,
                                                                             MethodDescriptor.MethodType.SERVER_STREAMING)))
                                                   .putMethod(CLIENT_STREAMING,
                                                              GrpcClientMethodDescriptor.create(
                                                                      SERVICE_NAME,
                                                                      CLIENT_STREAMING,
                                                                      method(CLIENT_STREAMING,
                                                                             MethodDescriptor.MethodType.CLIENT_STREAMING)))
                                                   .putMethod(BIDIRECTIONAL,
                                                              GrpcClientMethodDescriptor.create(
                                                                      SERVICE_NAME,
                                                                      BIDIRECTIONAL,
                                                                      method(BIDIRECTIONAL,
                                                                             MethodDescriptor.MethodType.BIDI_STREAMING)))
                                                   .putMethod(LEGACY_SERVER_STREAMING,
                                                              GrpcClientMethodDescriptor.create(
                                                                      SERVICE_NAME,
                                                                      LEGACY_SERVER_STREAMING,
                                                                      method(LEGACY_SERVER_STREAMING,
                                                                             MethodDescriptor.MethodType.SERVER_STREAMING)))
                                                   .putMethod(LEGACY_CLIENT_STREAMING,
                                                              GrpcClientMethodDescriptor.create(
                                                                      SERVICE_NAME,
                                                                      LEGACY_CLIENT_STREAMING,
                                                                      method(LEGACY_CLIENT_STREAMING,
                                                                             MethodDescriptor.MethodType.CLIENT_STREAMING)))
                                                   .putMethod(LEGACY_BIDIRECTIONAL,
                                                              GrpcClientMethodDescriptor.create(
                                                                      SERVICE_NAME,
                                                                      LEGACY_BIDIRECTIONAL,
                                                                      method(LEGACY_BIDIRECTIONAL,
                                                                             MethodDescriptor.MethodType.BIDI_STREAMING)))
                                                   .build());
    }

    @TearDown
    public void tearDown() {
        server.stop();
    }

    @Benchmark
    public void serverStreaming(Blackhole blackhole) {
        try (Stream<byte[]> responses = client.serverStreaming(SERVER_STREAMING, payload)) {
            responses.forEach(blackhole::consume);
        }
    }

    @Benchmark
    public void legacyServerStreaming(Blackhole blackhole) {
        Iterator<byte[]> responses = client.serverStream(LEGACY_SERVER_STREAMING, payload);
        responses.forEachRemaining(blackhole::consume);
    }

    @Benchmark
    public void serverStreamingNewClientLegacyServer(Blackhole blackhole) {
        try (Stream<byte[]> responses = client.serverStreaming(LEGACY_SERVER_STREAMING, payload)) {
            responses.forEach(blackhole::consume);
        }
    }

    @Benchmark
    public void serverStreamingLegacyClientNewServer(Blackhole blackhole) {
        Iterator<byte[]> responses = client.serverStream(SERVER_STREAMING, payload);
        responses.forEachRemaining(blackhole::consume);
    }

    @Benchmark
    public void clientStreaming(Blackhole blackhole) {
        blackhole.consume(client.clientStreaming(CLIENT_STREAMING, requests()));
    }

    @Benchmark
    public void legacyClientStreaming(Blackhole blackhole) {
        blackhole.consume(client.clientStream(LEGACY_CLIENT_STREAMING, requests().iterator()));
    }

    @Benchmark
    public void clientStreamingNewClientLegacyServer(Blackhole blackhole) {
        blackhole.consume(client.clientStreaming(LEGACY_CLIENT_STREAMING, requests()));
    }

    @Benchmark
    public void clientStreamingLegacyClientNewServer(Blackhole blackhole) {
        blackhole.consume(client.clientStream(CLIENT_STREAMING, requests().iterator()));
    }

    @Benchmark
    public void bidirectional(Blackhole blackhole) {
        try (Stream<byte[]> responses = client.bidirectional(BIDIRECTIONAL, requests())) {
            responses.forEach(blackhole::consume);
        }
    }

    @Benchmark
    public void legacyBidirectional(Blackhole blackhole) {
        Iterator<byte[]> responses = client.bidi(LEGACY_BIDIRECTIONAL, requests().iterator());
        responses.forEachRemaining(blackhole::consume);
    }

    @Benchmark
    public void bidirectionalNewClientLegacyServer(Blackhole blackhole) {
        try (Stream<byte[]> responses = client.bidirectional(LEGACY_BIDIRECTIONAL, requests())) {
            responses.forEach(blackhole::consume);
        }
    }

    @Benchmark
    public void bidirectionalLegacyClientNewServer(Blackhole blackhole) {
        Iterator<byte[]> responses = client.bidi(BIDIRECTIONAL, requests().iterator());
        responses.forEachRemaining(blackhole::consume);
    }

    @Benchmark
    public void bidirectionalSlowConsumer(Blackhole blackhole) {
        try (Stream<byte[]> responses = client.bidirectional(BIDIRECTIONAL, requests())) {
            responses.forEach(response -> {
                blackhole.consume(response);
                Blackhole.consumeCPU(SLOW_CONSUMER_TOKENS_PER_INVOCATION / messageCount());
            });
        }
    }

    private Stream<byte[]> requests() {
        return IntStream.range(0, messageCount()).mapToObj(ignored -> payload);
    }

    private int messageCount() {
        return payloadSize > 64 * 1024 ? LARGE_MESSAGE_COUNT : SMALL_MESSAGE_COUNT;
    }

    private static MethodDescriptor.Builder<byte[], byte[]> method(String methodName,
                                                                    MethodDescriptor.MethodType methodType) {
        return MethodDescriptor.<byte[], byte[]>newBuilder()
                .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, methodName))
                .setType(methodType)
                .setRequestMarshaller(ByteArrayMarshaller.INSTANCE)
                .setResponseMarshaller(ByteArrayMarshaller.INSTANCE);
    }

    /**
     * Snapshot of the collecting server adapters that preceded the resource-owning streaming API.
     */
    private static final class LegacyGrpcStreams {
        private LegacyGrpcStreams() {
        }

        private static void serverStreaming(Stream<byte[]> responses, StreamObserver<byte[]> responseObserver) {
            try (responses) {
                sendResponses(responses::iterator, responseObserver);
            } catch (Throwable t) {
                responseObserver.onError(t);
            }
        }

        private static void serverStreaming(Iterable<byte[]> responses, StreamObserver<byte[]> responseObserver) {
            try {
                sendResponses(responses, responseObserver);
            } catch (Throwable t) {
                responseObserver.onError(t);
            }
        }

        private static StreamObserver<byte[]> clientStreaming(byte[] response,
                                                              StreamObserver<byte[]> responseObserver) {
            List<byte[]> requests = new ArrayList<>();
            return new StreamObserver<>() {
                @Override
                public void onNext(byte[] request) {
                    requests.add(request);
                }

                @Override
                public void onError(Throwable throwable) {
                    responseObserver.onError(throwable);
                }

                @Override
                public void onCompleted() {
                    try {
                        List.copyOf(requests).forEach(ignored -> { });
                        responseObserver.onNext(response);
                        responseObserver.onCompleted();
                    } catch (Throwable t) {
                        responseObserver.onError(t);
                    }
                }
            };
        }

        private static StreamObserver<byte[]> bidirectional(StreamObserver<byte[]> responseObserver) {
            List<byte[]> requests = new ArrayList<>();
            return new StreamObserver<>() {
                @Override
                public void onNext(byte[] request) {
                    requests.add(request);
                }

                @Override
                public void onError(Throwable throwable) {
                    responseObserver.onError(throwable);
                }

                @Override
                public void onCompleted() {
                    serverStreaming(List.copyOf(requests), responseObserver);
                }
            };
        }

        private static void sendResponses(Iterable<byte[]> responses, StreamObserver<byte[]> responseObserver) {
            Outbound outbound = new Outbound(responseObserver);
            for (byte[] response : responses) {
                if (!outbound.awaitReady()) {
                    return;
                }
                responseObserver.onNext(response);
            }
            if (!outbound.cancelled()) {
                responseObserver.onCompleted();
            }
        }

        private static final class Outbound {
            private final ServerCallStreamObserver<?> serverObserver;
            private final ReentrantLock lock = new ReentrantLock();
            private final Condition ready = lock.newCondition();
            private boolean cancelled;

            private Outbound(StreamObserver<?> responseObserver) {
                if (responseObserver instanceof ServerCallStreamObserver<?> observer) {
                    serverObserver = observer;
                    try {
                        observer.setOnCancelHandler(this::cancel);
                    } catch (IllegalStateException _) {
                        // This reproduces the previous adapter's late-registration behavior.
                    }
                } else {
                    serverObserver = null;
                }
            }

            private boolean awaitReady() {
                if (serverObserver == null) {
                    return true;
                }
                lock.lock();
                try {
                    while (!serverObserver.isReady() && !serverObserver.isCancelled() && !cancelled) {
                        if (!ready.await(10, TimeUnit.MILLISECONDS) && serverObserver.isReady()) {
                            return true;
                        }
                    }
                    return !serverObserver.isCancelled() && !cancelled;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                } finally {
                    lock.unlock();
                }
            }

            private boolean cancelled() {
                return serverObserver != null && (serverObserver.isCancelled() || cancelled);
            }

            private void cancel() {
                lock.lock();
                try {
                    cancelled = true;
                    ready.signalAll();
                } finally {
                    lock.unlock();
                }
            }
        }
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
