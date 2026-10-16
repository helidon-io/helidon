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
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webclient.grpc.GrpcClientMethodDescriptor;
import io.helidon.webclient.grpc.GrpcServiceClient;
import io.helidon.webclient.grpc.GrpcServiceDescriptor;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.grpc.GrpcRouting;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
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
@Fork(3)
@State(Scope.Benchmark)
public class GrpcTransportCompatibilityJmhBenchmark {
    private static final String SERVICE_NAME = "GrpcTransportCompatibilityBenchmark";
    private static final String SERVER_STREAMING = "ServerStreaming";
    private static final String CLIENT_STREAMING = "ClientStreaming";
    private static final String BIDIRECTIONAL = "Bidirectional";
    private static final String EARLY_CLOSE = "EarlyClose";
    private static final int MESSAGE_COUNT = 8;

    private final ReentrantLock callStartupLock = new ReentrantLock();

    @Param({"65530", "65531", "131072"})
    private int payloadSize;

    @Param({"false", "true"})
    private boolean deadlineEnabled;

    private byte[] payload;
    private WebServer server;
    private GrpcClient grpcClient;
    private GrpcServiceClient client;
    private Channel channel;

    @Setup
    public void setup() {
        payload = new byte[payloadSize];
        Arrays.fill(payload, (byte) 1);

        ServerServiceDefinition service = ServerServiceDefinition.builder(SERVICE_NAME)
                .addMethod(method(SERVER_STREAMING, MethodDescriptor.MethodType.SERVER_STREAMING).build(),
                           ServerCalls.asyncServerStreamingCall((request, observer) -> sendResponses(observer)))
                .addMethod(method(EARLY_CLOSE, MethodDescriptor.MethodType.SERVER_STREAMING).build(),
                           ServerCalls.asyncServerStreamingCall((request, observer) -> sendResponses(observer)))
                .addMethod(method(CLIENT_STREAMING, MethodDescriptor.MethodType.CLIENT_STREAMING).build(),
                           ServerCalls.asyncClientStreamingCall(this::clientStreaming))
                .addMethod(method(BIDIRECTIONAL, MethodDescriptor.MethodType.BIDI_STREAMING).build(),
                           ServerCalls.asyncBidiStreamingCall(this::bidirectional))
                .build();

        server = WebServer.builder()
                .addRouting(GrpcRouting.builder().service(service))
                .build()
                .start();

        grpcClient = GrpcClient.builder()
                .baseUri("http://localhost:" + server.port())
                .tls(tls -> tls.enabled(false))
                .build();
        ClientInterceptor deadlineInterceptor = new ClientInterceptor() {
            @Override
            public <ReqT, ResT> ClientCall<ReqT, ResT> interceptCall(MethodDescriptor<ReqT, ResT> method,
                                                                  CallOptions options,
                                                                  Channel next) {
                // Each call gets its own deadline. Keep it beyond the complete steady-state trial.
                return next.newCall(method, deadlineEnabled ? options.withDeadlineAfter(10, TimeUnit.MINUTES) : options);
            }
        };
        channel = grpcClient.channel(deadlineInterceptor);
        client = grpcClient.serviceClient(GrpcServiceDescriptor.builder()
                                                   .serviceName(SERVICE_NAME)
                                                   .addInterceptor(deadlineInterceptor)
                                                   .putMethod(SERVER_STREAMING,
                                                              clientMethod(SERVER_STREAMING,
                                                                           MethodDescriptor.MethodType.SERVER_STREAMING))
                                                   .putMethod(CLIENT_STREAMING,
                                                              clientMethod(CLIENT_STREAMING,
                                                                           MethodDescriptor.MethodType.CLIENT_STREAMING))
                                                   .putMethod(BIDIRECTIONAL,
                                                              clientMethod(BIDIRECTIONAL,
                                                                           MethodDescriptor.MethodType.BIDI_STREAMING))
                                                   .build());
    }

    @TearDown
    public void tearDown() {
        server.stop();
    }

    @Benchmark
    public void serverStreaming(Blackhole blackhole) {
        Iterator<byte[]> responses = client.serverStream(SERVER_STREAMING, payload);
        responses.forEachRemaining(blackhole::consume);
    }

    @Benchmark
    public void clientStreaming(Blackhole blackhole) {
        blackhole.consume(client.clientStream(CLIENT_STREAMING, requests()));
    }

    @Benchmark
    public void bidirectional(Blackhole blackhole) {
        Iterator<byte[]> responses = client.bidi(BIDIRECTIONAL, requests());
        responses.forEachRemaining(blackhole::consume);
    }

    @Benchmark
    public void bidirectionalSteadyState(BidirectionalStream stream, Blackhole blackhole) throws InterruptedException {
        blackhole.consume(stream.exchange(payload));
    }

    @Benchmark
    public void earlyClose(Blackhole blackhole) throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        CountDownLatch terminated = new CountDownLatch(1);
        AtomicReference<byte[]> response = new AtomicReference<>();
        AtomicReference<Status> status = new AtomicReference<>();
        ClientCall<byte[], byte[]> call = channel
                .newCall(method(EARLY_CLOSE, MethodDescriptor.MethodType.SERVER_STREAMING).build(), CallOptions.DEFAULT);
        call.start(new ClientCall.Listener<>() {
            @Override
            public void onMessage(byte[] message) {
                response.compareAndSet(null, message);
                completed.countDown();
            }

            @Override
            public void onClose(Status closeStatus, Metadata trailers) {
                status.set(closeStatus);
                completed.countDown();
                terminated.countDown();
            }
        }, new Metadata());
        call.request(1);
        call.sendMessage(payload);
        call.halfClose();

        if (!completed.await(10, TimeUnit.SECONDS)) {
            call.cancel("Timed out waiting for first response", null);
            throw new IllegalStateException("Timed out waiting for first response");
        }
        call.cancel("Benchmark consumed first response", null);
        if (!terminated.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for benchmark cancellation");
        }
        Status terminalStatus = status.get();
        if (!terminalStatus.isOk() && terminalStatus.getCode() != Status.Code.CANCELLED) {
            throw new IllegalStateException("Call failed before benchmark cancellation: " + terminalStatus);
        }
        byte[] firstResponse = response.get();
        if (firstResponse == null) {
            throw new IllegalStateException("Call closed before first response: " + status.get());
        }
        blackhole.consume(firstResponse);
    }

    private void sendResponses(StreamObserver<byte[]> observer) {
        for (int i = 0; i < MESSAGE_COUNT; i++) {
            observer.onNext(payload);
        }
        observer.onCompleted();
    }

    private StreamObserver<byte[]> clientStreaming(StreamObserver<byte[]> observer) {
        return new StreamObserver<>() {
            @Override
            public void onNext(byte[] request) {
            }

            @Override
            public void onError(Throwable throwable) {
                observer.onError(throwable);
            }

            @Override
            public void onCompleted() {
                observer.onNext(payload);
                observer.onCompleted();
            }
        };
    }

    private StreamObserver<byte[]> bidirectional(StreamObserver<byte[]> observer) {
        return new StreamObserver<>() {
            @Override
            public void onNext(byte[] request) {
                observer.onNext(request);
            }

            @Override
            public void onError(Throwable throwable) {
                observer.onError(throwable);
            }

            @Override
            public void onCompleted() {
                observer.onCompleted();
            }
        };
    }

    private Iterator<byte[]> requests() {
        return IntStream.range(0, MESSAGE_COUNT).mapToObj(ignored -> payload).iterator();
    }

    @State(Scope.Thread)
    public static class BidirectionalStream {
        private final BlockingQueue<byte[]> responses = new ArrayBlockingQueue<>(1);
        private final AtomicReference<Status> closed = new AtomicReference<>();

        private ClientCall<byte[], byte[]> call;

        @Setup
        public void setup(GrpcTransportCompatibilityJmhBenchmark benchmark) {
            // Startup is outside the measured steady state. Serialize it so the compatibility baseline
            // does not race its shared legacy header constants while all measured calls still share one client.
            benchmark.callStartupLock.lock();
            try {
                call = benchmark.channel
                        .newCall(method(BIDIRECTIONAL, MethodDescriptor.MethodType.BIDI_STREAMING).build(),
                                 CallOptions.DEFAULT);
                call.start(new ClientCall.Listener<>() {
                    @Override
                    public void onMessage(byte[] message) {
                        if (!responses.offer(message)) {
                            call.cancel("Benchmark response queue is full", null);
                        }
                    }

                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        closed.set(status);
                    }
                }, new Metadata());
                call.request(1);
            } finally {
                benchmark.callStartupLock.unlock();
            }
        }

        @TearDown
        public void tearDown() {
            call.cancel("Benchmark completed", null);
        }

        private byte[] exchange(byte[] request) throws InterruptedException {
            Status status = closed.get();
            if (status != null) {
                throw new IllegalStateException("Call closed before request: " + status);
            }
            call.sendMessage(request);
            byte[] response = responses.poll(10, TimeUnit.SECONDS);
            status = closed.get();
            if (status != null) {
                throw new IllegalStateException("Call closed during exchange: " + status);
            }
            if (response == null) {
                throw new IllegalStateException("Timed out waiting for response");
            }
            call.request(1);
            return response;
        }
    }

    private static GrpcClientMethodDescriptor clientMethod(String methodName,
                                                           MethodDescriptor.MethodType methodType) {
        return GrpcClientMethodDescriptor.create(SERVICE_NAME, methodName, method(methodName, methodType));
    }

    private static MethodDescriptor.Builder<byte[], byte[]> method(String methodName,
                                                                    MethodDescriptor.MethodType methodType) {
        return MethodDescriptor.<byte[], byte[]>newBuilder()
                .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, methodName))
                .setType(methodType)
                .setRequestMarshaller(ByteArrayMarshaller.INSTANCE)
                .setResponseMarshaller(ByteArrayMarshaller.INSTANCE);
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
