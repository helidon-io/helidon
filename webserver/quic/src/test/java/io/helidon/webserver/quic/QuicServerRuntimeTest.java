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

package io.helidon.webserver.quic;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import io.helidon.common.configurable.Resource;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.quic.QuicClientConnection;
import io.helidon.quic.QuicClientRuntime;
import io.helidon.quic.QuicCloseCommand;
import io.helidon.quic.QuicConfig;
import io.helidon.quic.QuicConnection;
import io.helidon.quic.QuicServerRuntime;
import io.helidon.quic.QuicTransportErrors;
import io.helidon.quic.QuicVersion;
import io.helidon.quic.SequentialScheduler;
import io.helidon.quic.VariableLengthEncoder;
import io.helidon.quic.stream.QuicSenderStream;
import io.helidon.quic.stream.QuicStreamWriter;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicServerRuntimeTest {
    private static final String ALPN = "helidon-test-quic";
    private static final char[] KEY_PASSWORD = "changeit".toCharArray();
    private static final String SERVER_KEYSTORE = "io/helidon/quic/server-keystore.p12";
    private static final String CLIENT_TRUSTSTORE = "io/helidon/quic/client-truststore.p12";

    @Test
    void shouldSealPendingAcceptsWithoutClosingBorrowedExecutor() throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        QuicServerRuntime server = createServer(executor);
        CompletableFuture<QuicConnection> first = server.accept();
        CompletableFuture<QuicConnection> second = server.accept();
        try {
            server.close();
            server.close();

            assertThrows(ExecutionException.class, () -> first.get(10, TimeUnit.SECONDS));
            assertThrows(ExecutionException.class, () -> second.get(10, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, server::localAddress);
            assertThat(executor.isShutdown(), is(false));
            assertThat(executor.submit(() -> "available").get(10, TimeUnit.SECONDS), is("available"));
        } finally {
            server.close();
            executor.close();
        }
    }

    @Test
    void shouldNotDeadlockWhenPendingAcceptCompletionClosesServer() throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        QuicServerRuntime server = createServer(executor);
        CountDownLatch reentrantCloseCompleted = new CountDownLatch(1);
        try {
            server.accept().whenComplete((connection, failure) -> {
                server.close();
                reentrantCloseCompleted.countDown();
            });

            assertThat(server.close(Duration.ofSeconds(5)), is(true));
            assertThat(reentrantCloseCompleted.await(5, TimeUnit.SECONDS), is(true));
        } finally {
            server.close();
            executor.close();
        }
    }

    @Test
    void shouldSkipCanceledAcceptAndReleaseBoundPort() throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        QuicServerRuntime server = createServer(executor);
        QuicClientRuntime client = createClient(executor);
        InetSocketAddress serverAddress = server.localAddress();
        try {
            CompletableFuture<QuicConnection> canceled = server.accept();
            assertThat(canceled.cancel(false), is(true));
            CompletableFuture<QuicConnection> accepted = server.accept();

            QuicClientConnection clientConnection = createConnection(client, serverAddress);
            clientConnection.startHandshake().get(20, TimeUnit.SECONDS);

            assertThat(accepted.get(20, TimeUnit.SECONDS), instanceOf(QuicConnection.class));
        } finally {
            client.close();
            server.close();
        }

        try (DatagramChannel channel = DatagramChannel.open()) {
            channel.bind(serverAddress);
            assertThat(channel.getLocalAddress(), is(serverAddress));
        } finally {
            executor.close();
        }
    }

    @Test
    void shouldSealRuntimeAfterFatalFailure() throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        QuicServerRuntime runtime = QuicServerRuntime.builder()
                .executor(executor)
                .quicConfig(quicConfig())
                .tls(serverTls())
                .applicationProtocols(List.of(ALPN))
                .bindAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                .build();
        CompletableFuture<QuicConnection> pending = runtime.accept();
        try {
            runtime.runtimeFailed(new IOException("test transport failure"));

            assertThrows(ExecutionException.class, () -> pending.get(10, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, runtime::localAddress);
            assertThat(executor.isShutdown(), is(false));
        } finally {
            runtime.close();
            executor.close();
        }
    }

    @Test
    void shouldIsolateObserverFailuresFromConnectionAcceptance() throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger callbacks = new AtomicInteger();
        QuicServerRuntime runtime = QuicServerRuntime.builder()
                .executor(executor)
                .quicConfig(quicConfig())
                .tls(serverTls())
                .applicationProtocols(List.of(ALPN))
                .bindAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                .observer(new QuicServerRuntime.Observer() {
                    @Override
                    public void connectionCreated(QuicConnection connection) {
                        callbacks.incrementAndGet();
                        throw new IllegalStateException("connection observer failed");
                    }

                    @Override
                    public void handshakeSucceeded(QuicConnection connection) {
                        callbacks.incrementAndGet();
                        throw new IllegalStateException("handshake observer failed");
                    }
                })
                .build();
        QuicClientRuntime client = createClient(executor);
        try {
            CompletableFuture<QuicConnection> accepted = runtime.accept();
            QuicClientConnection clientConnection = createConnection(client, runtime.localAddress());

            clientConnection.startHandshake().get(20, TimeUnit.SECONDS);

            assertThat(accepted.get(20, TimeUnit.SECONDS), instanceOf(QuicConnection.class));
            assertThat(callbacks.get(), is(2));
        } finally {
            client.close();
            runtime.close();
            executor.close();
        }
    }

    @Test
    void shouldRecoverHandshakeReservationAfterListenerRejectionOrFailure() throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger admissionAttempts = new AtomicInteger();
        AtomicInteger createdConnections = new AtomicInteger();
        CountDownLatch rejectedAttempts = new CountDownLatch(4);
        QuicServerRuntime runtime = QuicServerRuntime.builder()
                .executor(executor)
                .quicConfig(quicConfig())
                .tls(serverTls())
                .applicationProtocols(List.of(ALPN))
                .bindAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                .maxPendingHandshakes(1)
                .connectionAdmission(() -> {
                    int attempt = admissionAttempts.incrementAndGet();
                    if (attempt <= 4) {
                        rejectedAttempts.countDown();
                    }
                    return switch (attempt) {
                        case 1, 4 -> QuicServerRuntime.ConnectionPermit.rejected();
                        case 2 -> null;
                        case 3 -> throw new IllegalStateException("test listener admission failure");
                        default -> QuicServerRuntime.ConnectionPermit.accepted(() -> { }, () -> { });
                    };
                })
                .observer(new QuicServerRuntime.Observer() {
                    @Override
                    public void connectionCreated(QuicConnection connection) {
                        createdConnections.incrementAndGet();
                    }
                })
                .build();
        QuicClientRuntime client = createClient(executor);
        try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))) {
            InetSocketAddress serverAddress = runtime.localAddress();
            for (int i = 0; i < 4; i++) {
                byte[] initial = invalidInitialDatagram(301L + i);
                socket.send(new DatagramPacket(initial, initial.length, serverAddress));
            }

            assertThat(rejectedAttempts.await(20, TimeUnit.SECONDS), is(true));
            assertThat(admissionAttempts.get(), is(4));
            assertThat(createdConnections.get(), is(0));

            CompletableFuture<QuicConnection> accepted = runtime.accept();
            QuicClientConnection clientConnection = createConnection(client, serverAddress);
            clientConnection.startHandshake().get(20, TimeUnit.SECONDS);

            assertThat(accepted.get(20, TimeUnit.SECONDS), instanceOf(QuicConnection.class));
            assertThat(admissionAttempts.get(), is(5));
            assertThat(createdConnections.get(), is(1));
        } finally {
            client.close();
            runtime.close();
            executor.close();
        }
    }

    @Test
    void shouldNegotiateUnsupportedVersionBeforeAdmission() throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger admissionAttempts = new AtomicInteger();
        AtomicInteger createdConnections = new AtomicInteger();
        QuicServerRuntime runtime = QuicServerRuntime.builder()
                .executor(executor)
                .quicConfig(quicConfig(List.of(QuicVersion.QUIC_V2, QuicVersion.QUIC_V1)))
                .tls(serverTls())
                .applicationProtocols(List.of(ALPN))
                .bindAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                .connectionAdmission(() -> {
                    admissionAttempts.incrementAndGet();
                    return QuicServerRuntime.ConnectionPermit.rejected();
                })
                .observer(new QuicServerRuntime.Observer() {
                    @Override
                    public void connectionCreated(QuicConnection connection) {
                        createdConnections.incrementAndGet();
                    }
                })
                .build();
        byte[] destinationId = bytes(21, 1);
        byte[] sourceId = bytes(32, 51);
        ByteBuffer requestBuffer = ByteBuffer.allocate(1200);
        requestBuffer.put((byte) 0x80);
        requestBuffer.putInt(0x0a0a0a0a);
        requestBuffer.put((byte) destinationId.length);
        requestBuffer.put(destinationId);
        requestBuffer.put((byte) sourceId.length);
        requestBuffer.put(sourceId);
        byte[] request = requestBuffer.array();
        try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))) {
            socket.setSoTimeout(5000);
            socket.send(new DatagramPacket(request, request.length, runtime.localAddress()));
            byte[] received = new byte[1200];
            DatagramPacket responsePacket = new DatagramPacket(received, received.length);
            socket.receive(responsePacket);

            ByteBuffer response = ByteBuffer.wrap(responsePacket.getData(), 0, responsePacket.getLength());
            assertThat(response.get() & 0xc0, is(0xc0));
            assertThat(response.getInt(), is(0));
            assertThat(readConnectionId(response), is(sourceId));
            assertThat(readConnectionId(response), is(destinationId));
            assertThat(response.getInt(), is(QuicVersion.QUIC_V2.versionNumber()));
            assertThat(response.getInt(), is(QuicVersion.QUIC_V1.versionNumber()));
            assertThat(response.hasRemaining(), is(false));
            assertThat(admissionAttempts.get(), is(0));
            assertThat(createdConnections.get(), is(0));
        } finally {
            runtime.close();
            executor.close();
        }
    }

    @Test
    void shouldAcceptQuicV2Initial() throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        QuicServerRuntime server = createServer(executor, List.of(QuicVersion.QUIC_V2));
        QuicClientRuntime client = createClient(executor, List.of(QuicVersion.QUIC_V2));
        try {
            CompletableFuture<QuicConnection> accepted = server.accept();
            QuicClientConnection clientConnection = createConnection(client, server.localAddress());

            clientConnection.startHandshake().get(20, TimeUnit.SECONDS);

            assertThat(accepted.get(20, TimeUnit.SECONDS), instanceOf(QuicConnection.class));
        } finally {
            client.close();
            server.close();
            executor.close();
        }
    }

    @Test
    void shouldRetryThenReuseNewTokenWithQuicV1() throws Exception {
        shouldRetryThenReuseNewToken(QuicVersion.QUIC_V1, List.of(QuicVersion.QUIC_V1));
    }

    @Test
    void shouldRetryThenReuseNewTokenWithQuicV2() throws Exception {
        shouldRetryThenReuseNewToken(QuicVersion.QUIC_V2, List.of(QuicVersion.QUIC_V2));
    }

    @Test
    void shouldReuseVersionScopedNewTokenAfterVersionNegotiation() throws Exception {
        shouldRetryThenReuseNewToken(QuicVersion.QUIC_V2,
                                     List.of(QuicVersion.QUIC_V1, QuicVersion.QUIC_V2));
    }

    @Test
    void shouldIgnorePermitWhenAcceptingStopsDuringAdmission() throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicReference<QuicServerRuntime> runtimeRef = new AtomicReference<>();
        AtomicInteger admissionAttempts = new AtomicInteger();
        AtomicInteger ignored = new AtomicInteger();
        AtomicInteger succeeded = new AtomicInteger();
        CountDownLatch released = new CountDownLatch(1);
        CountDownLatch readmitted = new CountDownLatch(1);
        QuicServerRuntime runtime = QuicServerRuntime.builder()
                .executor(executor)
                .quicConfig(quicConfig())
                .tls(serverTls())
                .applicationProtocols(List.of(ALPN))
                .bindAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                .maxPendingHandshakes(1)
                .connectionAdmission(() -> {
                    if (admissionAttempts.incrementAndGet() > 1) {
                        readmitted.countDown();
                        return QuicServerRuntime.ConnectionPermit.rejected();
                    }
                    runtimeRef.get().stopAccepting();
                    return QuicServerRuntime.ConnectionPermit.accepted(() -> {
                        ignored.incrementAndGet();
                        released.countDown();
                    }, () -> {
                        succeeded.incrementAndGet();
                        released.countDown();
                    });
                })
                .build();
        runtimeRef.set(runtime);
        QuicClientRuntime client = createClient(executor);
        try {
            QuicClientConnection clientConnection = createConnection(client, runtime.localAddress());
            clientConnection.startHandshake();

            assertThat(released.await(10, TimeUnit.SECONDS), is(true));
            runtime.resumeAccepting();
            byte[] initial = invalidInitialDatagram(401);
            try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))) {
                socket.send(new DatagramPacket(initial, initial.length, runtime.localAddress()));
            }

            assertThat(readmitted.await(10, TimeUnit.SECONDS), is(true));
            assertThat(admissionAttempts.get(), greaterThanOrEqualTo(2));
            assertThat(ignored.get(), is(1));
            assertThat(succeeded.get(), is(0));
        } finally {
            client.close();
            runtime.close();
            executor.close();
        }
    }

    @Test
    void shouldReleaseEstablishedPermitExactlyOnceAfterCleanup() throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger ignored = new AtomicInteger();
        AtomicInteger succeeded = new AtomicInteger();
        CountDownLatch released = new CountDownLatch(1);
        QuicServerRuntime runtime = QuicServerRuntime.builder()
                .executor(executor)
                .quicConfig(quicConfig())
                .tls(serverTls())
                .applicationProtocols(List.of(ALPN))
                .bindAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                .connectionAdmission(() -> QuicServerRuntime.ConnectionPermit.accepted(() -> {
                    ignored.incrementAndGet();
                    released.countDown();
                }, () -> {
                    succeeded.incrementAndGet();
                    released.countDown();
                }))
                .build();
        QuicClientRuntime client = createClient(executor);
        try {
            CompletableFuture<QuicConnection> accepted = runtime.accept();
            QuicClientConnection clientConnection = createConnection(client, runtime.localAddress());
            clientConnection.startHandshake().get(20, TimeUnit.SECONDS);
            QuicConnection serverConnection = accepted.get(20, TimeUnit.SECONDS);

            QuicCloseCommand command = QuicCloseCommand.application(0, "test complete");
            serverConnection.terminate(command);
            serverConnection.terminate(command);
            runtime.close();
            runtime.close();

            assertThat(released.await(10, TimeUnit.SECONDS), is(true));
            assertThat(ignored.get(), is(0));
            assertThat(succeeded.get(), is(1));
        } finally {
            client.close();
            runtime.close();
            executor.close();
        }
    }

    @Test
    void shouldBoundInvalidInitialStateAndRecoverAfterTimeout() throws Exception {
        int pendingLimit = 4;
        Duration handshakeTimeout = Duration.ofSeconds(5);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger admissionAttempts = new AtomicInteger();
        AtomicInteger createdConnections = new AtomicInteger();
        AtomicInteger handshakeSuccesses = new AtomicInteger();
        AtomicInteger releasedBeforeEstablished = new AtomicInteger();
        AtomicInteger timeoutTerminations = new AtomicInteger();
        CountDownLatch pendingLimitReached = new CountDownLatch(pendingLimit);
        CountDownLatch pendingLimitReleased = new CountDownLatch(pendingLimit);
        CountDownLatch pendingLimitTimedOut = new CountDownLatch(pendingLimit);
        CountDownLatch expiredDestinationReadmitted = new CountDownLatch(1);
        QuicServerRuntime runtime = QuicServerRuntime.builder()
                .executor(executor)
                .quicConfig(quicConfig())
                .tls(serverTls())
                .applicationProtocols(List.of(ALPN))
                .bindAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                .handshakeTimeout(handshakeTimeout)
                .maxPendingHandshakes(pendingLimit)
                .connectionAdmission(() -> {
                    admissionAttempts.incrementAndGet();
                    return QuicServerRuntime.ConnectionPermit.accepted(() -> {
                        releasedBeforeEstablished.incrementAndGet();
                        pendingLimitReleased.countDown();
                    }, () -> { });
                })
                .observer(new QuicServerRuntime.Observer() {
                    @Override
                    public void connectionCreated(QuicConnection connection) {
                        int created = createdConnections.incrementAndGet();
                        pendingLimitReached.countDown();
                        if (created == pendingLimit + 1) {
                            expiredDestinationReadmitted.countDown();
                        }
                        connection.whenTerminated().thenAccept(termination -> {
                            if (termination.cause().orElse(null) instanceof TimeoutException) {
                                timeoutTerminations.incrementAndGet();
                                pendingLimitTimedOut.countDown();
                            }
                        });
                    }

                    @Override
                    public void handshakeSucceeded(QuicConnection connection) {
                        handshakeSuccesses.incrementAndGet();
                    }
                })
                .build();
        QuicClientRuntime client = createClient(executor);
        byte[] firstInvalidInitial = invalidInitialDatagram(1);
        try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))) {
            socket.setSoTimeout(5000);
            InetSocketAddress serverAddress = runtime.localAddress();
            for (int i = 0; i < pendingLimit * 10; i++) {
                byte[] initial = i == 0 ? firstInvalidInitial : invalidInitialDatagram(i + 1L);
                socket.send(new DatagramPacket(initial, initial.length, serverAddress));
            }
            byte[] versionNegotiation = unsupportedVersionDatagram();
            socket.send(new DatagramPacket(versionNegotiation, versionNegotiation.length, serverAddress));
            byte[] response = new byte[1200];
            socket.receive(new DatagramPacket(response, response.length));

            assertThat(pendingLimitReached.await(10, TimeUnit.SECONDS), is(true));
            assertThat(admissionAttempts.get(), is(pendingLimit));
            assertThat(createdConnections.get(), is(pendingLimit));
            assertThat(handshakeSuccesses.get(), is(0));

            assertThat(pendingLimitReleased.await(10, TimeUnit.SECONDS), is(true));
            assertThat(pendingLimitTimedOut.await(10, TimeUnit.SECONDS), is(true));
            assertThat(releasedBeforeEstablished.get(), is(pendingLimit));
            assertThat(timeoutTerminations.get(), is(pendingLimit));

            socket.send(new DatagramPacket(firstInvalidInitial, firstInvalidInitial.length, serverAddress));
            assertThat(expiredDestinationReadmitted.await(10, TimeUnit.SECONDS), is(true));

            CompletableFuture<QuicConnection> accepted = runtime.accept();
            QuicClientConnection clientConnection = createConnection(client, serverAddress);
            clientConnection.startHandshake().get(20, TimeUnit.SECONDS);

            assertThat(accepted.get(20, TimeUnit.SECONDS), instanceOf(QuicConnection.class));
            assertThat(handshakeSuccesses.get(), is(1));
        } finally {
            client.close();
            runtime.close();
            executor.close();
        }
    }

    @Test
    void shouldDispatchHandshakeTimeoutCleanupOutsideTimerProcessor() throws Exception {
        Duration handshakeTimeout = Duration.ofSeconds(1);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger createdConnections = new AtomicInteger();
        CountDownLatch connectionsCreated = new CountDownLatch(2);
        CountDownLatch firstTerminationEntered = new CountDownLatch(1);
        CountDownLatch continueFirstTermination = new CountDownLatch(1);
        CountDownLatch secondTerminationCompleted = new CountDownLatch(1);
        QuicServerRuntime runtime = QuicServerRuntime.builder()
                .executor(executor)
                .quicConfig(quicConfig())
                .tls(serverTls())
                .applicationProtocols(List.of(ALPN))
                .bindAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                .handshakeTimeout(handshakeTimeout)
                .maxPendingHandshakes(2)
                .observer(new QuicServerRuntime.Observer() {
                    @Override
                    public void connectionCreated(QuicConnection connection) {
                        int connectionNumber = createdConnections.incrementAndGet();
                        connectionsCreated.countDown();
                        if (connectionNumber == 1) {
                            connection.whenTerminated().thenRun(() -> {
                                firstTerminationEntered.countDown();
                                try {
                                    if (!continueFirstTermination.await(10, TimeUnit.SECONDS)) {
                                        throw new IllegalStateException("Timed out waiting to continue first termination");
                                    }
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new IllegalStateException("Interrupted while waiting to continue first termination", e);
                                }
                            });
                        } else {
                            connection.whenTerminated().thenRun(secondTerminationCompleted::countDown);
                        }
                    }
                })
                .build();
        try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))) {
            InetSocketAddress serverAddress = runtime.localAddress();
            byte[] firstInitial = invalidInitialDatagram(201);
            byte[] secondInitial = invalidInitialDatagram(202);
            socket.send(new DatagramPacket(firstInitial, firstInitial.length, serverAddress));
            socket.send(new DatagramPacket(secondInitial, secondInitial.length, serverAddress));

            assertThat(connectionsCreated.await(10, TimeUnit.SECONDS), is(true));
            assertThat(firstTerminationEntered.await(10, TimeUnit.SECONDS), is(true));
            assertThat(secondTerminationCompleted.await(2, TimeUnit.SECONDS), is(true));
        } finally {
            continueFirstTermination.countDown();
            runtime.close();
            executor.close();
        }
    }

    @Test
    void shouldNotRegisterRoutesAfterSynchronousObserverTermination() throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger admissionAttempts = new AtomicInteger();
        AtomicInteger createdConnections = new AtomicInteger();
        AtomicInteger releasedConnections = new AtomicInteger();
        CountDownLatch firstConnectionCreated = new CountDownLatch(1);
        CountDownLatch firstConnectionReleased = new CountDownLatch(1);
        CountDownLatch bothConnectionsCreated = new CountDownLatch(2);
        CountDownLatch bothConnectionsReleased = new CountDownLatch(2);
        QuicServerRuntime runtime = QuicServerRuntime.builder()
                .executor(executor)
                .quicConfig(quicConfig())
                .tls(serverTls())
                .applicationProtocols(List.of(ALPN))
                .bindAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                .maxPendingHandshakes(1)
                .connectionAdmission(() -> {
                    admissionAttempts.incrementAndGet();
                    return QuicServerRuntime.ConnectionPermit.accepted(() -> {
                        if (releasedConnections.incrementAndGet() == 1) {
                            firstConnectionReleased.countDown();
                        }
                        bothConnectionsReleased.countDown();
                    }, () -> { });
                })
                .observer(new QuicServerRuntime.Observer() {
                    @Override
                    public void connectionCreated(QuicConnection connection) {
                        if (createdConnections.incrementAndGet() == 1) {
                            firstConnectionCreated.countDown();
                        }
                        bothConnectionsCreated.countDown();
                        connection.terminate(QuicCloseCommand.transport(QuicTransportErrors.PROTOCOL_VIOLATION,
                                                                        "test observer termination"));
                    }
                })
                .build();
        byte[] initial = invalidInitialDatagram(101);
        try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))) {
            InetSocketAddress serverAddress = runtime.localAddress();
            socket.send(new DatagramPacket(initial, initial.length, serverAddress));
            assertThat(firstConnectionCreated.await(10, TimeUnit.SECONDS), is(true));
            assertThat(firstConnectionReleased.await(10, TimeUnit.SECONDS), is(true));

            socket.send(new DatagramPacket(initial, initial.length, serverAddress));
            assertThat(bothConnectionsCreated.await(10, TimeUnit.SECONDS), is(true));
            assertThat(bothConnectionsReleased.await(10, TimeUnit.SECONDS), is(true));

            assertThat(admissionAttempts.get(), is(2));
            assertThat(createdConnections.get(), is(2));
            assertThat(releasedConnections.get(), is(2));
        } finally {
            runtime.close();
            executor.close();
        }
    }

    @Test
    void shouldExpireAuthenticatedStallAndCancelSuccessfulDeadlines() throws Exception {
        Duration handshakeTimeout = Duration.ofSeconds(3);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicLong admittedAt = new AtomicLong();
        AtomicLong releasedAt = new AtomicLong();
        AtomicInteger handshakeSuccesses = new AtomicInteger();
        AtomicInteger timeoutTerminations = new AtomicInteger();
        CountDownLatch stalledConnectionCreated = new CountDownLatch(1);
        CountDownLatch stalledConnectionReleased = new CountDownLatch(1);
        CountDownLatch stalledConnectionTimedOut = new CountDownLatch(1);
        QuicServerRuntime runtime = QuicServerRuntime.builder()
                .executor(executor)
                .quicConfig(quicConfig())
                .tls(serverTls())
                .applicationProtocols(List.of(ALPN))
                .bindAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                .handshakeTimeout(handshakeTimeout)
                .maxPendingHandshakes(1)
                .connectionAdmission(() -> {
                    admittedAt.compareAndSet(0, System.nanoTime());
                    return QuicServerRuntime.ConnectionPermit.accepted(() -> {
                        releasedAt.compareAndSet(0, System.nanoTime());
                        stalledConnectionReleased.countDown();
                    }, () -> { });
                })
                .observer(new QuicServerRuntime.Observer() {
                    @Override
                    public void connectionCreated(QuicConnection connection) {
                        stalledConnectionCreated.countDown();
                        connection.whenTerminated().thenAccept(termination -> {
                            if (termination.cause().orElse(null) instanceof TimeoutException) {
                                timeoutTerminations.incrementAndGet();
                                stalledConnectionTimedOut.countDown();
                            }
                        });
                    }

                    @Override
                    public void handshakeSucceeded(QuicConnection connection) {
                        handshakeSuccesses.incrementAndGet();
                    }
                })
                .build();
        QuicClientRuntime client = createClient(executor);
        try (NatRebindingProxy proxy = new NatRebindingProxy(executor, runtime.localAddress(), false)) {
            QuicClientConnection stalled = createConnection(client, proxy.clientAddress());
            stalled.startHandshake();

            assertThat(stalledConnectionCreated.await(10, TimeUnit.SECONDS), is(true));
            assertThat(stalledConnectionReleased.await(10, TimeUnit.SECONDS), is(true));
            assertThat(stalledConnectionTimedOut.await(10, TimeUnit.SECONDS), is(true));
            long elapsed = releasedAt.get() - admittedAt.get();
            assertThat(elapsed, greaterThanOrEqualTo(handshakeTimeout.minusMillis(100).toNanos()));
            assertThat(elapsed, lessThan(handshakeTimeout.plusSeconds(5).toNanos()));
            assertThat(proxy.clientPackets(), greaterThan(1));
            assertThat(timeoutTerminations.get(), is(1));
            assertThat(handshakeSuccesses.get(), is(0));

            CompletableFuture<QuicConnection> firstAccepted = runtime.accept();
            QuicClientConnection firstClient = createConnection(client, runtime.localAddress());
            firstClient.startHandshake().get(20, TimeUnit.SECONDS);
            QuicConnection firstServer = firstAccepted.get(20, TimeUnit.SECONDS);

            CompletableFuture<QuicConnection> secondAccepted = runtime.accept();
            QuicClientConnection secondClient = createConnection(client, runtime.localAddress());
            secondClient.startHandshake().get(20, TimeUnit.SECONDS);
            QuicConnection secondServer = secondAccepted.get(20, TimeUnit.SECONDS);

            LockSupport.parkNanos(handshakeTimeout.plusMillis(500).toNanos());
            assertThat(firstServer.isOpen(), is(true));
            assertThat(secondServer.isOpen(), is(true));
            assertThat(handshakeSuccesses.get(), is(2));
        } finally {
            client.close();
            runtime.close();
            executor.close();
        }
    }

    @Test
    void shouldRetainPreHandshakeAdmissionUntilClosingRoutesExpire() throws Exception {
        int pendingLimit = 2;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger admissionAttempts = new AtomicInteger();
        AtomicInteger createdConnections = new AtomicInteger();
        AtomicInteger handshakeSuccesses = new AtomicInteger();
        AtomicInteger releasedBeforeEstablished = new AtomicInteger();
        CountDownLatch pendingConnectionsCreated = new CountDownLatch(pendingLimit);
        CountDownLatch pendingConnectionsTerminated = new CountDownLatch(pendingLimit);
        CountDownLatch pendingConnectionsReleased = new CountDownLatch(pendingLimit);
        List<QuicConnection> pendingConnections = new CopyOnWriteArrayList<>();
        QuicServerRuntime runtime = QuicServerRuntime.builder()
                .executor(executor)
                .quicConfig(quicConfig())
                .tls(serverTls())
                .applicationProtocols(List.of(ALPN))
                .bindAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                .retryEnabled(true)
                .handshakeTimeout(Duration.ofSeconds(30))
                .maxPendingHandshakes(pendingLimit)
                .connectionAdmission(() -> {
                    admissionAttempts.incrementAndGet();
                    return QuicServerRuntime.ConnectionPermit.accepted(() -> {
                        releasedBeforeEstablished.incrementAndGet();
                        pendingConnectionsReleased.countDown();
                    }, () -> { });
                })
                .observer(new QuicServerRuntime.Observer() {
                    @Override
                    public void connectionCreated(QuicConnection connection) {
                        createdConnections.incrementAndGet();
                        pendingConnections.add(connection);
                        pendingConnectionsCreated.countDown();
                        connection.whenTerminated().thenRun(pendingConnectionsTerminated::countDown);
                    }

                    @Override
                    public void handshakeSucceeded(QuicConnection connection) {
                        handshakeSuccesses.incrementAndGet();
                    }
                })
                .build();
        QuicClientRuntime client = createClient(executor);
        try (NatRebindingProxy firstProxy = new NatRebindingProxy(executor,
                                                                  runtime.localAddress(),
                                                                  NatRebindingProxy.ServerPacketMode.RETRY_ONLY);
             NatRebindingProxy secondProxy = new NatRebindingProxy(executor,
                                                                   runtime.localAddress(),
                                                                   NatRebindingProxy.ServerPacketMode.RETRY_ONLY);
             NatRebindingProxy blockedProxy = new NatRebindingProxy(executor,
                                                                    runtime.localAddress(),
                                                                    NatRebindingProxy.ServerPacketMode.RETRY_ONLY)) {
            QuicClientConnection firstPending = createConnection(client, firstProxy.clientAddress());
            QuicClientConnection secondPending = createConnection(client, secondProxy.clientAddress());
            firstPending.startHandshake();
            secondPending.startHandshake();

            assertThat(pendingConnectionsCreated.await(10, TimeUnit.SECONDS), is(true));
            assertThat(firstProxy.firstNonRetryServerPacket.await(10, TimeUnit.SECONDS), is(true));
            assertThat(secondProxy.firstNonRetryServerPacket.await(10, TimeUnit.SECONDS), is(true));
            QuicConnection locallyClosed = pendingConnections.stream()
                    .filter(connection -> connection.remotePeer().port() == firstProxy.initialUpstreamAddress().getPort())
                    .findFirst()
                    .orElseThrow();
            locallyClosed.terminate(
                    QuicCloseCommand.transport(QuicTransportErrors.PROTOCOL_VIOLATION, "test protected close"));
            secondPending.terminate(
                    QuicCloseCommand.transport(QuicTransportErrors.PROTOCOL_VIOLATION, "test peer close"));

            assertThat(pendingConnectionsTerminated.await(10, TimeUnit.SECONDS), is(true));
            assertThat(releasedBeforeEstablished.get(), is(0));

            createConnection(client, blockedProxy.clientAddress()).startHandshake();

            assertThat(blockedProxy.retrySeen.await(10, TimeUnit.SECONDS), is(true));
            assertThat(blockedProxy.secondClientPacket.await(10, TimeUnit.SECONDS), is(true));
            LockSupport.parkNanos(Duration.ofMillis(500).toNanos());
            assertThat(admissionAttempts.get(), is(pendingLimit));
            assertThat(createdConnections.get(), is(pendingLimit));
            assertThat(handshakeSuccesses.get(), is(0));
            assertThat(releasedBeforeEstablished.get(), is(0));

            assertThat(pendingConnectionsReleased.await(10, TimeUnit.SECONDS), is(true));
            CompletableFuture<QuicConnection> accepted = runtime.accept();
            QuicClientConnection recovering = createConnection(client, runtime.localAddress());
            CompletableFuture<Void> recoveringHandshake = recovering.startHandshake();

            recoveringHandshake.get(20, TimeUnit.SECONDS);
            accepted.get(20, TimeUnit.SECONDS);

            assertThat(admissionAttempts.get(), is(pendingLimit + 1));
            assertThat(createdConnections.get(), is(pendingLimit + 1));
            assertThat(handshakeSuccesses.get(), is(1));
            assertThat(releasedBeforeEstablished.get(), is(pendingLimit));
        } finally {
            client.close();
            runtime.close();
            executor.close();
        }
    }

    @Test
    void shouldReleaseClosingRouteWhenRuntimeCloses() throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger releasedBeforeEstablished = new AtomicInteger();
        AtomicReference<QuicConnection> pendingConnection = new AtomicReference<>();
        CountDownLatch connectionCreated = new CountDownLatch(1);
        CountDownLatch connectionTerminated = new CountDownLatch(1);
        CountDownLatch connectionReleased = new CountDownLatch(1);
        QuicServerRuntime runtime = QuicServerRuntime.builder()
                .executor(executor)
                .quicConfig(quicConfig())
                .tls(serverTls())
                .applicationProtocols(List.of(ALPN))
                .bindAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                .retryEnabled(true)
                .handshakeTimeout(Duration.ofSeconds(30))
                .maxPendingHandshakes(1)
                .connectionAdmission(() -> QuicServerRuntime.ConnectionPermit.accepted(() -> {
                    releasedBeforeEstablished.incrementAndGet();
                    connectionReleased.countDown();
                }, () -> { }))
                .observer(new QuicServerRuntime.Observer() {
                    @Override
                    public void connectionCreated(QuicConnection connection) {
                        pendingConnection.set(connection);
                        connectionCreated.countDown();
                        connection.whenTerminated().thenRun(connectionTerminated::countDown);
                    }
                })
                .build();
        QuicClientRuntime client = createClient(executor);
        try (NatRebindingProxy proxy = new NatRebindingProxy(executor,
                                                             runtime.localAddress(),
                                                             NatRebindingProxy.ServerPacketMode.RETRY_ONLY)) {
            createConnection(client, proxy.clientAddress()).startHandshake();

            assertThat(connectionCreated.await(10, TimeUnit.SECONDS), is(true));
            assertThat(proxy.firstNonRetryServerPacket.await(10, TimeUnit.SECONDS), is(true));
            pendingConnection.get().terminate(
                    QuicCloseCommand.transport(QuicTransportErrors.PROTOCOL_VIOLATION, "test protected close"));
            assertThat(connectionTerminated.await(10, TimeUnit.SECONDS), is(true));
            assertThat(releasedBeforeEstablished.get(), is(0));

            assertThat(runtime.close(Duration.ofSeconds(10)), is(true));
            assertThat(connectionReleased.await(10, TimeUnit.SECONDS), is(true));
            assertThat(releasedBeforeEstablished.get(), is(1));
        } finally {
            client.close();
            runtime.close();
            executor.close();
        }
    }

    @Test
    void shouldBoundRetryValidatedHandshakeAndRecoverAfterTimeout() throws Exception {
        Duration handshakeTimeout = Duration.ofSeconds(3);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger admissionAttempts = new AtomicInteger();
        AtomicInteger createdConnections = new AtomicInteger();
        AtomicInteger handshakeSuccesses = new AtomicInteger();
        AtomicInteger releasedBeforeEstablished = new AtomicInteger();
        CountDownLatch firstConnectionCreated = new CountDownLatch(1);
        CountDownLatch firstConnectionReleased = new CountDownLatch(1);
        CountDownLatch firstConnectionTimedOut = new CountDownLatch(1);
        QuicServerRuntime runtime = QuicServerRuntime.builder()
                .executor(executor)
                .quicConfig(quicConfig())
                .tls(serverTls())
                .applicationProtocols(List.of(ALPN))
                .bindAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                .retryEnabled(true)
                .handshakeTimeout(handshakeTimeout)
                .maxPendingHandshakes(1)
                .connectionAdmission(() -> {
                    admissionAttempts.incrementAndGet();
                    return QuicServerRuntime.ConnectionPermit.accepted(() -> {
                        releasedBeforeEstablished.incrementAndGet();
                        firstConnectionReleased.countDown();
                    }, () -> { });
                })
                .observer(new QuicServerRuntime.Observer() {
                    @Override
                    public void connectionCreated(QuicConnection connection) {
                        if (createdConnections.incrementAndGet() == 1) {
                            firstConnectionCreated.countDown();
                        }
                        connection.whenTerminated().thenAccept(termination -> {
                            if (termination.cause().orElse(null) instanceof TimeoutException) {
                                firstConnectionTimedOut.countDown();
                            }
                        });
                    }

                    @Override
                    public void handshakeSucceeded(QuicConnection connection) {
                        handshakeSuccesses.incrementAndGet();
                    }
                })
                .build();
        QuicClientRuntime client = createClient(executor);
        try (NatRebindingProxy proxy =
                     new NatRebindingProxy(executor,
                                           runtime.localAddress(),
                                           NatRebindingProxy.ServerPacketMode.RETRY_ONLY)) {
            QuicClientConnection stalled = createConnection(client, proxy.clientAddress());
            stalled.startHandshake();

            assertThat(firstConnectionCreated.await(10, TimeUnit.SECONDS), is(true));
            assertThat(proxy.retryPackets(), greaterThan(0));
            proxy.dropClientPackets();

            CompletableFuture<QuicConnection> accepted = runtime.accept();
            QuicClientConnection recovering = createConnection(client, runtime.localAddress());
            CompletableFuture<Void> recoveringHandshake = recovering.startHandshake();

            LockSupport.parkNanos(Duration.ofMillis(500).toNanos());
            assertThat(admissionAttempts.get(), is(1));
            assertThat(createdConnections.get(), is(1));
            assertThat(handshakeSuccesses.get(), is(0));

            assertThat(firstConnectionReleased.await(10, TimeUnit.SECONDS), is(true));
            assertThat(firstConnectionTimedOut.await(10, TimeUnit.SECONDS), is(true));
            recoveringHandshake.get(20, TimeUnit.SECONDS);
            accepted.get(20, TimeUnit.SECONDS);

            assertThat(admissionAttempts.get(), is(2));
            assertThat(createdConnections.get(), is(2));
            assertThat(handshakeSuccesses.get(), is(1));
            assertThat(releasedBeforeEstablished.get(), is(1));
        } finally {
            client.close();
            runtime.close();
            executor.close();
        }
    }

    @Test
    void shouldKeepConnectionIdentityAcrossPassiveNatRebinding() throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        QuicServerRuntime server = createServer(executor);
        QuicClientRuntime client = createClient(executor);
        try (NatRebindingProxy proxy = new NatRebindingProxy(executor, server.localAddress())) {
            CompletableFuture<QuicConnection> accepted = server.accept();
            QuicClientConnection clientConnection = createConnection(client, proxy.clientAddress());
            clientConnection.startHandshake().get(20, TimeUnit.SECONDS);
            QuicConnection serverConnection = accepted.get(20, TimeUnit.SECONDS);
            String socketId = serverConnection.socketId();
            String connectionId = serverConnection.childSocketId();
            assertThat(serverConnection.remotePeer().port(), is(proxy.initialUpstreamAddress().getPort()));

            CountDownLatch firstStreamReceived = new CountDownLatch(1);
            CountDownLatch secondStreamReceived = new CountDownLatch(1);
            CountDownLatch thirdStreamReceived = new CountDownLatch(1);
            AtomicInteger streamsReceived = new AtomicInteger();
            serverConnection.addRemoteStreamListener(stream -> {
                int received = streamsReceived.incrementAndGet();
                if (received == 1) {
                    firstStreamReceived.countDown();
                } else if (received == 2) {
                    secondStreamReceived.countDown();
                } else {
                    thirdStreamReceived.countDown();
                }
                return true;
            });

            sendOneByte(clientConnection);
            assertThat(firstStreamReceived.await(10, TimeUnit.SECONDS), is(true));
            proxy.rebind();
            sendOneByte(clientConnection);
            assertThat(secondStreamReceived.await(10, TimeUnit.SECONDS), is(true));
            sendOneByte(clientConnection);
            assertThat(thirdStreamReceived.await(10, TimeUnit.SECONDS), is(true));
            assertThat(serverConnection.socketId(), is(socketId));
            assertThat(serverConnection.childSocketId(), is(connectionId));
            assertThat(serverConnection.remotePeer().port(), is(proxy.reboundUpstreamAddress().getPort()));
        } finally {
            client.close();
            server.close();
            executor.close();
        }
    }

    private static QuicServerRuntime createServer(ExecutorService executor) throws Exception {
        return createServer(executor, List.of(QuicVersion.QUIC_V1));
    }

    private static QuicServerRuntime createServer(ExecutorService executor, List<QuicVersion> versions) throws Exception {
        return QuicServerRuntime.builder()
                .executor(executor)
                .quicConfig(quicConfig(versions))
                .tls(serverTls())
                .applicationProtocols(List.of(ALPN))
                .bindAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                .build();
    }

    private static void sendOneByte(QuicClientConnection connection) throws Exception {
        QuicSenderStream stream = connection.openNewLocalUniStream(Duration.ofSeconds(5))
                .get(10, TimeUnit.SECONDS);
        QuicStreamWriter writer = stream.connectWriter(SequentialScheduler.lockingScheduler(() -> {
        }));
        writer.scheduleForWritingAndGetDispatchCompletion(BufferData.create(new byte[] {1}), true)
                .get(10, TimeUnit.SECONDS);
    }

    private static QuicClientConnection createConnection(QuicClientRuntime client, InetSocketAddress peerAddress) {
        return client.createConnection(peerAddress,
                                       peerAddress.getHostString(),
                                       peerAddress.getPort(),
                                       new String[] {ALPN});
    }

    private static QuicClientRuntime createClient(ExecutorService executor) {
        return createClient(executor, List.of(QuicVersion.QUIC_V1));
    }

    private static QuicClientRuntime createClient(ExecutorService executor, List<QuicVersion> versions) {
        return QuicClientRuntime.builder()
                .executor(executor)
                .quicConfig(quicConfig(versions))
                .tls(clientTls())
                .build();
    }

    private static QuicConfig quicConfig() {
        return quicConfig(List.of(QuicVersion.QUIC_V1));
    }

    private static QuicConfig quicConfig(List<QuicVersion> versions) {
        return QuicConfig.builder()
                .availableVersions(versions)
                .buildPrototype();
    }

    private static byte[] readConnectionId(ByteBuffer packet) {
        byte[] connectionId = new byte[Byte.toUnsignedInt(packet.get())];
        packet.get(connectionId);
        return connectionId;
    }

    private static byte[] bytes(int length, int start) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) (start + i);
        }
        return bytes;
    }

    private static byte[] invalidInitialDatagram(long destination) {
        ByteBuffer packet = ByteBuffer.allocate(1200);
        byte[] destinationId = ByteBuffer.allocate(Long.BYTES).putLong(destination).array();
        byte[] sourceId = bytes(8, 31);
        packet.put((byte) 0xc0);
        packet.putInt(QuicVersion.QUIC_V1.versionNumber());
        packet.put((byte) destinationId.length);
        packet.put(destinationId);
        packet.put((byte) sourceId.length);
        packet.put(sourceId);
        VariableLengthEncoder.encode(packet, 0);
        VariableLengthEncoder.encode(packet, packet.capacity() - packet.position() - 2);
        return packet.array();
    }

    private static byte[] unsupportedVersionDatagram() {
        ByteBuffer packet = ByteBuffer.allocate(1200);
        byte[] destinationId = bytes(8, 11);
        byte[] sourceId = bytes(8, 31);
        packet.put((byte) 0x80);
        packet.putInt(0x0a0a0a0a);
        packet.put((byte) destinationId.length);
        packet.put(destinationId);
        packet.put((byte) sourceId.length);
        packet.put(sourceId);
        return packet.array();
    }

    private static Tls serverTls() throws Exception {
        Keys keys = Keys.builder()
                .keystore(store -> store
                        .passphrase(new String(KEY_PASSWORD))
                        .keyAlias("server")
                        .certChainAlias("server")
                        .keystore(Resource.create(SERVER_KEYSTORE)))
                .build();

        return Tls.builder()
                .privateKey(keys.privateKey().orElseThrow())
                .privateKeyCertChain(keys.certChain())
                .build();
    }

    private static Tls clientTls() {
        return Tls.builder()
                .trust(trust -> trust.keystore(store -> store
                        .passphrase(new String(KEY_PASSWORD))
                        .trustStore(true)
                        .keystore(Resource.create(CLIENT_TRUSTSTORE))))
                .build();
    }

    private void shouldRetryThenReuseNewToken(QuicVersion version,
                                              List<QuicVersion> clientVersions) throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger admissionAttempts = new AtomicInteger();
        AtomicInteger createdConnections = new AtomicInteger();
        QuicConfig serverConfig = quicConfig(List.of(version));
        QuicServerRuntime runtime = QuicServerRuntime.builder()
                .executor(executor)
                .quicConfig(serverConfig)
                .retryEnabled(true)
                .tls(serverTls())
                .applicationProtocols(List.of(ALPN))
                .bindAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                .connectionAdmission(() -> {
                    admissionAttempts.incrementAndGet();
                    return QuicServerRuntime.ConnectionPermit.accepted(() -> {
                    }, () -> {
                    });
                })
                .observer(new QuicServerRuntime.Observer() {
                    @Override
                    public void connectionCreated(QuicConnection connection) {
                        createdConnections.incrementAndGet();
                    }
                })
                .build();
        QuicClientRuntime client = QuicClientRuntime.builder()
                .executor(executor)
                .quicConfig(quicConfig(clientVersions))
                .tls(clientTls())
                .build();
        try (NatRebindingProxy proxy = new NatRebindingProxy(executor, runtime.localAddress())) {
            InetSocketAddress peerAddress = proxy.clientAddress();
            CompletableFuture<QuicConnection> firstAccepted = runtime.accept();
            QuicClientConnection first = client.createConnection(peerAddress,
                                                                 "localhost",
                                                                 peerAddress.getPort(),
                                                                 new String[] {ALPN});
            first.startHandshake().get(20, TimeUnit.SECONDS);
            firstAccepted.get(20, TimeUnit.SECONDS);
            int retryPackets = proxy.retryPackets();
            assertThat(retryPackets, greaterThan(0));

            byte[] newToken = null;
            long tokenDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (newToken == null && System.nanoTime() < tokenDeadline) {
                newToken = client.initialTokenFor(peerAddress, version).orElse(null);
                if (newToken == null) {
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                }
            }
            assertThat(newToken == null, is(false));
            client.registerInitialToken(peerAddress, version, newToken);
            proxy.rebind();

            CompletableFuture<QuicConnection> secondAccepted = runtime.accept();
            QuicClientConnection second = client.createConnection(peerAddress,
                                                                  "localhost",
                                                                  peerAddress.getPort(),
                                                                  new String[] {ALPN});
            second.startHandshake().get(20, TimeUnit.SECONDS);
            secondAccepted.get(20, TimeUnit.SECONDS);

            assertThat(proxy.retryPackets(), is(retryPackets));
            assertThat(admissionAttempts.get(), is(2));
            assertThat(createdConnections.get(), is(2));
        } finally {
            client.close();
            runtime.close();
            executor.close();
        }
    }

    private static final class NatRebindingProxy implements AutoCloseable {
        private static final int MAX_DATAGRAM_SIZE = 65535;

        private final DatagramChannel clientChannel;
        private final DatagramChannel initialUpstream;
        private final DatagramChannel reboundUpstream;
        private final InetSocketAddress serverAddress;
        private final AtomicReference<DatagramChannel> activeUpstream;
        private final AtomicReference<SocketAddress> clientPeer = new AtomicReference<>();
        private final AtomicInteger retryPackets = new AtomicInteger();
        private final AtomicInteger clientPackets = new AtomicInteger();
        private final CountDownLatch retrySeen = new CountDownLatch(1);
        private final CountDownLatch secondClientPacket = new CountDownLatch(1);
        private final CountDownLatch firstNonRetryServerPacket = new CountDownLatch(1);
        private final ServerPacketMode serverPacketMode;
        private volatile boolean forwardClientPackets = true;

        private NatRebindingProxy(ExecutorService executor, InetSocketAddress serverAddress) {
            this(executor, serverAddress, ServerPacketMode.ALL);
        }

        private NatRebindingProxy(ExecutorService executor,
                                  InetSocketAddress serverAddress,
                                  boolean forwardServerPackets) {
            this(executor, serverAddress, forwardServerPackets ? ServerPacketMode.ALL : ServerPacketMode.NONE);
        }

        private NatRebindingProxy(ExecutorService executor,
                                  InetSocketAddress serverAddress,
                                  ServerPacketMode serverPacketMode) {
            this.serverAddress = serverAddress;
            this.serverPacketMode = serverPacketMode;
            try {
                clientChannel = DatagramChannel.open();
                clientChannel.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                initialUpstream = DatagramChannel.open();
                initialUpstream.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                reboundUpstream = DatagramChannel.open();
                reboundUpstream.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to initialize the QUIC NAT rebinding proxy.", e);
            }
            activeUpstream = new AtomicReference<>(initialUpstream);
            executor.submit(this::forwardClientTraffic);
            executor.submit(() -> forwardServerTraffic(initialUpstream));
            executor.submit(() -> forwardServerTraffic(reboundUpstream));
        }

        @Override
        public void close() throws IOException {
            clientChannel.close();
            initialUpstream.close();
            reboundUpstream.close();
        }

        private static InetSocketAddress localAddress(DatagramChannel channel, String channelName) {
            try {
                return (InetSocketAddress) channel.getLocalAddress();
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to obtain the " + channelName + " proxy address.", e);
            }
        }

        private InetSocketAddress clientAddress() {
            return localAddress(clientChannel, "client");
        }

        private InetSocketAddress initialUpstreamAddress() {
            return localAddress(initialUpstream, "initial upstream");
        }

        private InetSocketAddress reboundUpstreamAddress() {
            return localAddress(reboundUpstream, "rebound upstream");
        }

        private void rebind() {
            activeUpstream.set(reboundUpstream);
        }

        private int retryPackets() {
            return retryPackets.get();
        }

        private int clientPackets() {
            return clientPackets.get();
        }

        private void dropClientPackets() {
            forwardClientPackets = false;
        }

        private void forwardClientTraffic() {
            ByteBuffer buffer = ByteBuffer.allocate(MAX_DATAGRAM_SIZE);
            while (clientChannel.isOpen()) {
                try {
                    SocketAddress source = clientChannel.receive(buffer);
                    if (source == null) {
                        continue;
                    }
                    clientPeer.set(source);
                    int packetCount = clientPackets.incrementAndGet();
                    buffer.flip();
                    if (forwardClientPackets) {
                        activeUpstream.get().send(buffer, serverAddress);
                    }
                    if (packetCount >= 2) {
                        secondClientPacket.countDown();
                    }
                    buffer.clear();
                } catch (IOException e) {
                    if (clientChannel.isOpen()) {
                        throw new IllegalStateException("Client-side proxy forwarding failed", e);
                    }
                }
            }
        }

        private void forwardServerTraffic(DatagramChannel upstream) {
            ByteBuffer buffer = ByteBuffer.allocate(MAX_DATAGRAM_SIZE);
            while (upstream.isOpen()) {
                try {
                    upstream.receive(buffer);
                    boolean retryPacket = false;
                    if (buffer.position() >= 5) {
                        int firstByte = Byte.toUnsignedInt(buffer.get(0));
                        int versionNumber = buffer.getInt(1);
                        var version = QuicVersion.of(versionNumber);
                        if (version.isPresent()) {
                            int retryType = version.orElseThrow() == QuicVersion.QUIC_V1 ? 0xf0 : 0xc0;
                            retryPacket = (firstByte & 0xf0) == retryType;
                            if (retryPacket) {
                                retryPackets.incrementAndGet();
                                retrySeen.countDown();
                            }
                        }
                    }
                    if (!retryPacket) {
                        firstNonRetryServerPacket.countDown();
                    }
                    SocketAddress destination = clientPeer.get();
                    if (destination != null
                            && (serverPacketMode == ServerPacketMode.ALL
                            || (serverPacketMode == ServerPacketMode.RETRY_ONLY && retryPacket))) {
                        buffer.flip();
                        clientChannel.send(buffer, destination);
                    }
                    buffer.clear();
                } catch (IOException e) {
                    if (upstream.isOpen()) {
                        throw new IllegalStateException("Server-side proxy forwarding failed", e);
                    }
                }
            }
        }

        private enum ServerPacketMode {
            ALL,
            RETRY_ONLY,
            NONE
        }
    }
}
