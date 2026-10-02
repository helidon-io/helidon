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

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
import io.helidon.webclient.api.HttpTransportObserverSupport.ObserverLifecycle;
import io.helidon.webclient.api.HttpTransportObserverSupport.ObserverProvider;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.grpc.GrpcRouting;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Empty;
import com.google.protobuf.EmptyProto;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrpcTransportObservationTest {
    private static final Descriptors.FileDescriptor PROTO = descriptor();

    @Test
    void successfulDirectCallsRetainObserverUntilClientCloses() throws InterruptedException {
        RecordingProvider provider = new RecordingProvider(true);
        WebServer server = startServer();
        GrpcClient client = client(server.port(), provider);
        try {
            for (int i = 0; i < 2; i++) {
                completeCall(client);
                ObservedConnection connection = provider.connections.get(i);
                assertThat("physical connection must close after its direct call",
                           connection.closed.await(5, TimeUnit.SECONDS),
                           is(true));
                assertThat(connection.role, is(Role.CLIENT));
                assertThat(connection.transport, is(HttpTransportObserver.TRANSPORT_TCP));
                assertThat(connection.handshake, is(Handshake.NONE));
                assertThat(connection.protocol.get(), is(HttpTransportObserver.PROTOCOL_HTTP_2));
                assertThat(connection.streams.size(), is(1));
                assertThat(connection.streams.getFirst().get(), is(StreamOutcome.COMPLETED));
            }
            assertThat("gRPC must acquire only its own observer lease", provider.starts.get(), is(1));
            assertThat("lease must remain alive between calls", provider.stops.get(), is(0));
            assertThat(client.closeResourceAsync().toCompletableFuture().isDone(), is(true));
            assertThat(provider.stops.get(), is(1));
            assertThat(provider.activeWhenStopped.get(), is(0));
        } finally {
            client.closeResource();
            server.stop();
        }
    }

    @Test
    void unusedAndDisabledClientsDoNotAcquireObservers() throws InterruptedException {
        RecordingProvider unused = new RecordingProvider(true);
        client(1, unused).closeResource();
        assertThat(unused.scopes.get(), is(0));
        assertThat(unused.starts.get(), is(0));
        assertThat(unused.stops.get(), is(0));

        RecordingProvider disabled = new RecordingProvider(false);
        WebServer server = startServer();
        GrpcClient client = client(server.port(), disabled);
        try {
            completeCall(client);
        } finally {
            client.closeResource();
            server.stop();
        }
        assertThat(disabled.scopes.get(), is(0));
        assertThat(disabled.starts.get(), is(0));
        assertThat(disabled.connections.size(), is(0));
    }

    @Test
    void failedDirectConnectDoesNotPublishPhysicalConnection() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = socket.getLocalPort();
        }
        RecordingProvider provider = new RecordingProvider(true);
        GrpcClient client = client(port, provider);
        try {
            ClientCall<Empty, Empty> call = newCall(client);
            assertThrows(RuntimeException.class, () -> call.start(new ClientCall.Listener<>() { }, new Metadata()));
            assertThat("refused connect did not establish a physical connection", provider.connections.size(), is(0));
            assertThat("observer lease was acquired for the connection attempt", provider.starts.get(), is(1));
            assertThat("client retains its observer lease after a failed attempt", provider.stops.get(), is(0));
        } finally {
            client.closeResource();
        }
        assertThat(provider.stops.get(), is(1));
        assertThat(provider.activeWhenStopped.get(), is(0));
    }

    @Test
    void lastSharedClientClosesActiveDirectConnectionsBeforeObserverRelease() throws InterruptedException {
        RecordingProvider provider = new RecordingProvider(true);
        WebServer server = startServer();
        GrpcClient first = client(server.port(), provider);
        GrpcClient second = client(server.port(), provider);
        ClientCall<Empty, Empty> firstCall = newCall(first);
        ClientCall<Empty, Empty> secondCall = newCall(second);
        try {
            firstCall.start(new ClientCall.Listener<>() { }, new Metadata());
            secondCall.start(new ClientCall.Listener<>() { }, new Metadata());
            assertThat(provider.connections.size(), is(2));
            CompletionStage<Void> firstCleanup = first.closeResourceAsync();
            assertThat(provider.starts.get(), is(1));
            assertThat(provider.stops.get(), is(0));
            assertThat("sharing client's cleanup is still pending",
                       firstCleanup.toCompletableFuture().isDone(),
                       is(false));
            assertThat(provider.connections.getFirst().closed.getCount(), is(1L));

            CompletionStage<Void> secondCleanup = second.closeResourceAsync();

            for (ObservedConnection connection : provider.connections) {
                assertThat("last owner must close active connections",
                           connection.closed.await(5, TimeUnit.SECONDS),
                           is(true));
                assertThat(connection.outcome.get(), is(ConnectionOutcome.LOCAL_CLOSE));
            }
            assertThat(provider.stops.get(), is(1));
            assertThat(provider.activeWhenStopped.get(), is(0));
            assertThat(firstCleanup.toCompletableFuture().isDone(), is(true));
            assertThat(secondCleanup.toCompletableFuture().isDone(), is(true));
        } finally {
            firstCall.cancel("test complete", null);
            secondCall.cancel("test complete", null);
            first.closeResource();
            second.closeResource();
            server.stop();
        }
    }

    private static GrpcClient client(int port, RecordingProvider provider) {
        return GrpcClient.builder()
                .baseUri("http://localhost:" + port)
                .connectTimeout(Duration.ofSeconds(2))
                .shareConnectionCache(true)
                .addService(provider)
                .build();
    }

    private static void completeCall(GrpcClient client) throws InterruptedException {
        ClientCall<Empty, Empty> call = newCall(client);
        CountDownLatch closed = new CountDownLatch(1);
        AtomicReference<Status> status = new AtomicReference<>();
        AtomicInteger responses = new AtomicInteger();
        try {
            call.start(new ClientCall.Listener<>() {
                @Override
                public void onMessage(Empty message) {
                    responses.incrementAndGet();
                }

                @Override
                public void onClose(Status receivedStatus, Metadata trailers) {
                    status.set(receivedStatus);
                    closed.countDown();
                }
            }, new Metadata());
            call.request(Integer.MAX_VALUE);
            call.sendMessage(Empty.getDefaultInstance());
            call.halfClose();
            assertThat("gRPC response must complete", closed.await(5, TimeUnit.SECONDS), is(true));
            assertThat(status.get().getCode(), is(Status.Code.OK));
            assertThat(responses.get(), is(1));
        } finally {
            call.cancel("test complete", null);
        }
    }

    @SuppressWarnings("unchecked")
    private static ClientCall<Empty, Empty> newCall(GrpcClient client) {
        GrpcClientMethodDescriptor method = GrpcClientMethodDescriptor.bidirectional("transport.Echo", "Exchange")
                .requestType(Empty.class)
                .responseType(Empty.class)
                .build();
        return client.channel().newCall((MethodDescriptor<Empty, Empty>) (MethodDescriptor<?, ?>) method.descriptor(),
                                        CallOptions.DEFAULT);
    }

    private static WebServer startServer() {
        return WebServer.builder()
                .addRouting(GrpcRouting.builder()
                        .<Empty, Empty>bidi(PROTO, "transport.Echo", "Exchange", observer -> new StreamObserver<>() {
                            @Override
                            public void onNext(Empty value) {
                                observer.onNext(value);
                            }

                            @Override
                            public void onError(Throwable failure) {
                            }

                            @Override
                            public void onCompleted() {
                                observer.onCompleted();
                            }
                        }))
                .build()
                .start();
    }

    private static Descriptors.FileDescriptor descriptor() {
        var method = DescriptorProtos.MethodDescriptorProto.newBuilder()
                .setName("Exchange")
                .setInputType(".google.protobuf.Empty")
                .setOutputType(".google.protobuf.Empty")
                .setClientStreaming(true)
                .setServerStreaming(true);
        var service = DescriptorProtos.ServiceDescriptorProto.newBuilder().setName("Echo").addMethod(method);
        var file = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("transport.proto")
                .setPackage("transport")
                .addDependency("google/protobuf/empty.proto")
                .addService(service)
                .build();
        try {
            return Descriptors.FileDescriptor.buildFrom(file,
                                                         new Descriptors.FileDescriptor[] {EmptyProto.getDescriptor()});
        } catch (Descriptors.DescriptorValidationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static final class RecordingProvider implements WebClientService, ObserverProvider, HttpTransportObserver {
        private final boolean enabled;
        private final AtomicInteger scopes = new AtomicInteger();
        private final AtomicInteger starts = new AtomicInteger();
        private final AtomicInteger stops = new AtomicInteger();
        private final AtomicInteger activeWhenStopped = new AtomicInteger();
        private final List<ObservedConnection> connections = new CopyOnWriteArrayList<>();

        private RecordingProvider(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public Object scope() {
            scopes.incrementAndGet();
            return this;
        }

        @Override
        public ObserverLifecycle createObserver() {
            return new ObserverLifecycle() {
                @Override
                public HttpTransportObserver start() {
                    starts.incrementAndGet();
                    return RecordingProvider.this;
                }

                @Override
                public CompletionStage<Void> stop() {
                    activeWhenStopped.set((int) connections.stream().filter(it -> it.outcome.get() == null).count());
                    stops.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                }
            };
        }

        @Override
        public ConnectionObservation connectionOpened(Role role, String transport, Handshake handshake) {
            var connection = new ObservedConnection(role, transport, handshake);
            connections.add(connection);
            return connection;
        }

        @Override
        public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
            throw new IllegalStateException("Transport-only service entered request chain");
        }
    }

    private static final class ObservedConnection implements ConnectionObservation {
        private final Role role;
        private final String transport;
        private final Handshake handshake;
        private final AtomicReference<String> protocol = new AtomicReference<>();
        private final AtomicReference<ConnectionOutcome> outcome = new AtomicReference<>();
        private final CountDownLatch closed = new CountDownLatch(1);
        private final List<AtomicReference<StreamOutcome>> streams = new CopyOnWriteArrayList<>();

        private ObservedConnection(Role role, String transport, Handshake handshake) {
            this.role = role;
            this.transport = transport;
            this.handshake = handshake;
        }

        @Override
        public HandshakeObservation handshakeStarted() {
            return HandshakeObservation.noop();
        }

        @Override
        public void protocolSelected(String protocol) {
            this.protocol.set(protocol);
        }

        @Override
        public StreamObservation streamOpened(Direction direction, Initiator initiator) {
            assertThat(direction, is(Direction.BIDIRECTIONAL));
            assertThat(initiator, is(Initiator.LOCAL));
            var stream = new AtomicReference<StreamOutcome>();
            streams.add(stream);
            return outcome -> stream.compareAndSet(null, outcome);
        }

        @Override
        public void close(ConnectionOutcome outcome) {
            if (this.outcome.compareAndSet(null, outcome)) {
                StreamOutcome remaining = outcome == ConnectionOutcome.ERROR || outcome == ConnectionOutcome.TIMEOUT
                        ? StreamOutcome.ERROR : StreamOutcome.CANCELLED;
                streams.forEach(stream -> stream.compareAndSet(null, remaining));
                closed.countDown();
            }
        }
    }
}
