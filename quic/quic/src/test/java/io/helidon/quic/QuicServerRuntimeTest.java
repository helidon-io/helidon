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

package io.helidon.quic;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLParameters;

import io.helidon.common.tls.Tls;
import io.helidon.common.tls.TlsMaterial;
import io.helidon.quic.packet.QuicPacket;

import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuicServerRuntimeTest {
    @Test
    void rejectsNullBuilderArguments() {
        QuicServerRuntime.Builder builder = QuicServerRuntime.builder();

        assertThrows(NullPointerException.class, () -> builder.serverId(null));
        assertThrows(NullPointerException.class, () -> builder.tls(null));
        assertThrows(NullPointerException.class, () -> builder.bindAddress(null));
        assertThrows(NullPointerException.class, () -> builder.executor(null));
        assertThrows(NullPointerException.class, () -> builder.applicationErrors(null));
        assertThrows(NullPointerException.class, () -> builder.quicConfig(null));
        assertThrows(NullPointerException.class, () -> builder.handshakeTimeout(null));
    }

    @Test
    void rejectsInvalidHandshakeAdmissionConfiguration() {
        IllegalArgumentException zeroTimeout = assertThrows(
                IllegalArgumentException.class,
                () -> serverBuilder().handshakeTimeout(Duration.ZERO).build());
        IllegalArgumentException negativeTimeout = assertThrows(
                IllegalArgumentException.class,
                () -> serverBuilder().handshakeTimeout(Duration.ofSeconds(-1)).build());
        IllegalArgumentException excessiveTimeout = assertThrows(
                IllegalArgumentException.class,
                () -> serverBuilder().handshakeTimeout(Duration.ofSeconds(Long.MAX_VALUE)).build());
        IllegalArgumentException zeroPending = assertThrows(
                IllegalArgumentException.class,
                () -> serverBuilder().maxPendingHandshakes(0).build());
        IllegalArgumentException negativePending = assertThrows(
                IllegalArgumentException.class,
                () -> serverBuilder().maxPendingHandshakes(-1).build());

        assertThat(zeroTimeout.getMessage(), is("handshakeTimeout must be positive: PT0S"));
        assertThat(negativeTimeout.getMessage(), is("handshakeTimeout must be positive: PT-1S"));
        assertThat(excessiveTimeout.getMessage(),
                   is("handshakeTimeout must fit in nanoseconds: PT2562047788015215H30M7S"));
        assertThat(zeroPending.getMessage(), is("maxPendingHandshakes must be greater than 0: 0"));
        assertThat(negativePending.getMessage(), is("maxPendingHandshakes must be greater than 0: -1"));
    }

    @Test
    void rejectsNullApplicationErrorDescription() {
        try (QuicServerRuntime runtime = QuicServerRuntime.builder()
                .executor(Runnable::run)
                .tls(Tls.builder().trustAll(true).build())
                .quicConfig(QuicConfig.builder()
                                    .availableVersions(List.of(QuicVersion.QUIC_V1))
                                    .buildPrototype())
                .applicationErrors(errorCode -> null)
                .build()) {
            NullPointerException exception = assertThrows(NullPointerException.class,
                                                          () -> runtime.appErrorToString(0x100));

            assertThat(exception.getMessage(), is("application error formatter result"));
        }
    }

    @Test
    void pendingRouteRemovalDoesNotRetainTerminatedConnection() throws Exception {
        AtomicInteger releases = new AtomicInteger();
        AtomicInteger created = new AtomicInteger();
        AtomicReference<QuicServerConnection> firstConnection = new AtomicReference<>();
        AtomicReference<Boolean> removalStarted = new AtomicReference<>();
        QuicServerRuntime runtime = serverBuilder()
                .maxPendingHandshakes(1)
                .connectionAdmission(() -> QuicServerRuntime.ConnectionPermit.accepted(
                        releases::incrementAndGet,
                        () -> { }))
                .observer(new QuicServerRuntime.Observer() {
                    @Override
                    public void connectionCreated(QuicConnection connection) {
                        if (created.getAndIncrement() == 0) {
                            QuicServerConnection serverConnection = (QuicServerConnection) connection;
                            firstConnection.set(serverConnection);
                            removalStarted.set(serverConnection.routeLifecycle().beginRemoval(serverConnection));
                        }
                    }
                })
                .build();
        try {
            runtime.start();
            sendIncompleteInitial(runtime, 2);

            assertThat(removalStarted.get(), is(true));
            assertThat(releases.get(), is(0));
            sendIncompleteInitial(runtime, 3);
            assertThat(created.get(), is(1));

            firstConnection.get().routeLifecycle().completeRemoval();
            firstConnection.get().whenTerminated().toCompletableFuture().get(10, TimeUnit.SECONDS);

            assertThat(releases.get(), is(1));
            sendIncompleteInitial(runtime, 4);
            assertThat(created.get(), is(2));
        } finally {
            runtime.close();
        }
    }

    @Test
    void acceptsExpectedTokenWithServerIssuedConnectionId() throws Exception {
        Tls tls = Tls.builder()
                .privateKey(QuicTlsRfc8448Vectors.rsaPrivateKey())
                .privateKeyCertChain(List.of(QuicTlsRfc8448Vectors.rsaCertificate()))
                .trustAll(true)
                .build();
        QuicConfig config = QuicConfig.builder()
                .availableVersions(List.of(QuicVersion.QUIC_V1))
                .buildPrototype();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            QuicServerRuntime runtime = QuicServerRuntime.builder()
                    .executor(executor)
                    .tls(tls)
                    .quicConfig(config)
                    .build();
            try {
                SSLParameters sslParameters = new SSLParameters();
                sslParameters.setProtocols(new String[] {"TLSv1.3"});
                sslParameters.setApplicationProtocols(new String[] {"h3"});
                QuicServerConnection connection = new QuicServerConnection(
                        QuicVersion.QUIC_V1,
                        runtime,
                        QuicRuntimeConfig.create(config),
                        new InetSocketAddress(InetAddress.getLoopbackAddress(), 4433),
                        sslParameters,
                        runtime.quicTlsContext(),
                        1L);
                try {
                    QuicConnectionId admittedId = QuicConnectionIdFactory.client().newConnectionId();
                    byte[] token = {1, 2, 3};
                    connection.initialize(admittedId, admittedId, null, token, false);

                    assertThat(connection.verifyToken(admittedId, token), is(true));
                    assertThat(connection.verifyToken(connection.localConnectionId().orElseThrow(), token), is(true));
                    assertThat(connection.verifyToken(connection.localConnectionId().orElseThrow(),
                                                      new byte[] {1, 2}),
                               is(false));
                    assertThat(connection.freezeEndpointRoutes().connectionIds(),
                               hasItems(admittedId, connection.localConnectionId().orElseThrow()));
                } finally {
                    connection.terminate(QuicCloseCommand.silent("test cleanup"));
                    connection.whenTerminated().toCompletableFuture().join();
                }
            } finally {
                runtime.close();
            }
        }
    }

    @Test
    void reusesTlsStateUntilReloadAndClosesItWithRuntime() {
        Tls tls = Tls.builder().trustAll(true).build();
        QuicServerRuntime runtime = QuicServerRuntime.builder()
                .executor(Runnable::run)
                .tls(tls)
                .quicConfig(QuicConfig.builder()
                                    .availableVersions(List.of(QuicVersion.QUIC_V1))
                                    .buildPrototype())
                .build();
        try {
            QuicTLSContext initial = runtime.quicTlsContext();
            assertThat(runtime.quicTlsContext(), sameInstance(initial));

            tls.reload(TlsMaterial.builder().trustAll(true).build());

            QuicTLSContext reloaded = runtime.quicTlsContext();
            assertThat(reloaded, not(sameInstance(initial)));
            assertThat(runtime.quicTlsContext(), sameInstance(reloaded));
        } finally {
            runtime.close();
        }

        assertThrows(IllegalStateException.class, runtime::quicTlsContext);
    }

    @Test
    void abortAfterRuntimeSealClosesRetainedEndpoint() throws Exception {
        CountDownLatch releaseStarted = new CountDownLatch(1);
        CountDownLatch continueRelease = new CountDownLatch(1);
        AtomicInteger releasedBeforeEstablished = new AtomicInteger();
        AtomicInteger releasedEstablished = new AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Tls serverTls = Tls.builder()
                    .privateKey(QuicTlsRfc8448Vectors.rsaPrivateKey())
                    .privateKeyCertChain(List.of(QuicTlsRfc8448Vectors.rsaCertificate()))
                    .build();
            QuicConfig config = QuicConfig.builder()
                    .availableVersions(List.of(QuicVersion.QUIC_V1))
                    .buildPrototype();
            QuicServerRuntime runtime = QuicServerRuntime.builder()
                    .executor(executor)
                    .tls(serverTls)
                    .quicConfig(config)
                    .applicationProtocols(List.of("h3"))
                    .bindAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                    .connectionAdmission(() -> QuicServerRuntime.ConnectionPermit.accepted(
                            releasedBeforeEstablished::incrementAndGet,
                            () -> {
                                releasedEstablished.incrementAndGet();
                                releaseStarted.countDown();
                                try {
                                    if (!continueRelease.await(10, TimeUnit.SECONDS)) {
                                        throw new IllegalStateException("Timed out waiting to continue permit release");
                                    }
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new IllegalStateException("Interrupted while waiting to continue permit release", e);
                                }
                            }))
                    .build();
            QuicClientRuntime client = QuicClientRuntime.builder()
                    .executor(executor)
                    .tls(Tls.builder().trustAll(true).build())
                    .quicConfig(config)
                    .build();
            try {
                CompletableFuture<QuicConnection> accepted = runtime.accept();
                InetSocketAddress serverAddress = runtime.localAddress();
                QuicClientConnection clientConnection = client.createConnection(serverAddress,
                                                                                 "rsa",
                                                                                 serverAddress.getPort(),
                                                                                 new String[] {"h3"});
                clientConnection.startHandshake().get(10, TimeUnit.SECONDS);
                accepted.get(10, TimeUnit.SECONDS);
                QuicEndpoint endpoint = runtime.endpoint();

                CompletableFuture<Boolean> closing =
                        CompletableFuture.supplyAsync(() -> runtime.close(Duration.ofSeconds(10)), executor);
                assertThat(releaseStarted.await(10, TimeUnit.SECONDS), is(true));
                assertThat(endpoint.isClosed(), is(false));

                runtime.abort(new IllegalStateException("test failure after seal"));

                assertThat(runtime.isClosed(), is(true));
                assertThat(endpoint.isClosed(), is(true));
                continueRelease.countDown();
                assertThat(closing.get(10, TimeUnit.SECONDS), is(true));
                assertThat(releasedBeforeEstablished.get(), is(0));
                assertThat(releasedEstablished.get(), is(1));
            } finally {
                continueRelease.countDown();
                client.close();
                runtime.close();
            }
        }
    }

    @Test
    void startsIdleTimeoutBeforePublishingServerConnection() throws Exception {
        AtomicInteger releasedBeforeEstablished = new AtomicInteger();
        AtomicInteger releasedEstablished = new AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Tls serverTls = Tls.builder()
                    .privateKey(QuicTlsRfc8448Vectors.rsaPrivateKey())
                    .privateKeyCertChain(List.of(QuicTlsRfc8448Vectors.rsaCertificate()))
                    .build();
            QuicConfig config = QuicConfig.builder()
                    .availableVersions(List.of(QuicVersion.QUIC_V1))
                    .idleTimeout(Duration.ofMillis(250))
                    .buildPrototype();
            QuicServerRuntime runtime = QuicServerRuntime.builder()
                    .executor(executor)
                    .tls(serverTls)
                    .quicConfig(config)
                    .applicationProtocols(List.of("h3"))
                    .bindAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                    .connectionAdmission(() -> QuicServerRuntime.ConnectionPermit.accepted(
                            releasedBeforeEstablished::incrementAndGet,
                            releasedEstablished::incrementAndGet))
                    .build();
            QuicClientRuntime client = QuicClientRuntime.builder()
                    .executor(executor)
                    .tls(Tls.builder().trustAll(true).build())
                    .quicConfig(config)
                    .build();
            try {
                CompletableFuture<QuicConnection> accepted = runtime.accept();
                InetSocketAddress serverAddress = runtime.localAddress();
                QuicClientConnection clientConnection = client.createConnection(serverAddress,
                                                                                 "rsa",
                                                                                 serverAddress.getPort(),
                                                                                 new String[] {"h3"});
                clientConnection.startHandshake().get(10, TimeUnit.SECONDS);
                QuicConnectionImpl serverConnection = (QuicConnectionImpl) accepted.get(10, TimeUnit.SECONDS);

                QuicTermination termination =
                        serverConnection.whenTerminated().toCompletableFuture().get(5, TimeUnit.SECONDS);

                assertThat(termination.origin(), is(QuicTermination.Origin.LOCAL));
                assertThat(termination.kind(), is(QuicTermination.Kind.SILENT));
                assertThat(termination.logMessage(), containsString("server connection idle timed out"));
                assertThat(serverConnection.routeLifecycle().whenRemoved().toCompletableFuture().isDone(), is(true));
                assertThat(releasedBeforeEstablished.get(), is(0));
                assertThat(releasedEstablished.get(), is(1));
            } finally {
                client.close();
                runtime.close();
            }
        }
    }

    @Test
    void deferredStopAbortPreservesConcurrentCloseSnapshot() throws Exception {
        CountDownLatch continueRejection = new CountDownLatch(1);
        CountDownLatch rejectionStarted = new CountDownLatch(1);
        CountDownLatch releaseStarted = new CountDownLatch(1);
        CountDownLatch continueRelease = new CountDownLatch(1);
        AtomicInteger releasedBeforeEstablished = new AtomicInteger();
        AtomicInteger releasedEstablished = new AtomicInteger();
        AtomicInteger syntheticReleases = new AtomicInteger();
        AtomicInteger createdConnections = new AtomicInteger();
        AtomicReference<QuicServerConnection> syntheticConnection = new AtomicReference<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Tls serverTls = Tls.builder()
                    .privateKey(QuicTlsRfc8448Vectors.rsaPrivateKey())
                    .privateKeyCertChain(List.of(QuicTlsRfc8448Vectors.rsaCertificate()))
                    .build();
            QuicConfig config = QuicConfig.builder()
                    .availableVersions(List.of(QuicVersion.QUIC_V1))
                    .buildPrototype();
            QuicServerRuntime runtime = QuicServerRuntime.builder()
                    .executor(executor)
                    .tls(serverTls)
                    .quicConfig(config)
                    .applicationProtocols(List.of("h3"))
                    .bindAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                    .connectionAdmission(() -> createdConnections.get() == 0
                            ? QuicServerRuntime.ConnectionPermit.accepted(
                                    releasedBeforeEstablished::incrementAndGet,
                                    () -> {
                                        releasedEstablished.incrementAndGet();
                                        releaseStarted.countDown();
                                        try {
                                            if (!continueRelease.await(10, TimeUnit.SECONDS)) {
                                                throw new IllegalStateException(
                                                        "Timed out waiting to continue permit release");
                                            }
                                        } catch (InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                            throw new IllegalStateException(
                                                    "Interrupted while waiting to continue permit release",
                                                    e);
                                        }
                                    })
                            : QuicServerRuntime.ConnectionPermit.accepted(
                                    syntheticReleases::incrementAndGet,
                                    () -> { }))
                    .observer(new QuicServerRuntime.Observer() {
                        @Override
                        public void connectionCreated(QuicConnection connection) {
                            if (createdConnections.getAndIncrement() == 1) {
                                syntheticConnection.set((QuicServerConnection) connection);
                            }
                        }
                    })
                    .build();
            QuicClientRuntime client = QuicClientRuntime.builder()
                    .executor(executor)
                    .tls(Tls.builder().trustAll(true).build())
                    .quicConfig(config)
                    .build();
            CompletableFuture<Void> rejectionAction = null;
            CompletableFuture<Void> stopping = null;
            CompletableFuture<Boolean> closing = null;
            try {
                runtime.start();
                CompletableFuture<QuicConnection> accepted = runtime.accept();
                InetSocketAddress serverAddress = runtime.localAddress();
                QuicClientConnection clientConnection = client.createConnection(serverAddress,
                                                                                 "rsa",
                                                                                 serverAddress.getPort(),
                                                                                 new String[] {"h3"});
                clientConnection.startHandshake().get(10, TimeUnit.SECONDS);
                accepted.get(10, TimeUnit.SECONDS);

                sendIncompleteInitial(runtime, 10);
                QuicServerConnection failedConnection = syntheticConnection.get();
                assertThat(failedConnection, notNullValue());
                assertThat(failedConnection.isOpen(), is(true));
                assertThat(failedConnection.routeLifecycle().beginRemoval(failedConnection), is(true));
                failedConnection.routeLifecycle()
                        .completeRemovalExceptionally(new IllegalStateException("test deferred route failure"));

                CompletableFuture<QuicConnection> pendingAccept = runtime.accept();
                rejectionAction = pendingAccept.handle((_, failure) -> {
                    rejectionStarted.countDown();
                    try {
                        if (!continueRejection.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out waiting to record deferred abort");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while waiting to record deferred abort", e);
                    }
                    failedConnection.terminate(QuicCloseCommand.silent("test deferred cleanup failure"));
                    failedConnection.whenTerminated()
                            .handle((_, _) -> null)
                            .toCompletableFuture()
                            .join();
                    return null;
                });
                stopping = CompletableFuture.runAsync(runtime::stopAccepting, executor);
                assertThat(rejectionStarted.await(10, TimeUnit.SECONDS), is(true));

                closing = CompletableFuture.supplyAsync(() -> runtime.close(Duration.ofSeconds(10)), executor);
                long closeDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                boolean closed = false;
                while (!closed && System.nanoTime() < closeDeadline) {
                    try {
                        runtime.quicTlsContext();
                    } catch (IllegalStateException e) {
                        closed = true;
                    }
                    Thread.onSpinWait();
                }
                assertThat(closed, is(true));

                continueRejection.countDown();
                assertThat(releaseStarted.await(10, TimeUnit.SECONDS), is(true));
                assertThat(closing.isDone(), is(false));

                continueRelease.countDown();
                rejectionAction.get(10, TimeUnit.SECONDS);
                stopping.get(10, TimeUnit.SECONDS);
                assertThat(closing.get(10, TimeUnit.SECONDS), is(true));
                assertThat(releasedBeforeEstablished.get(), is(0));
                assertThat(releasedEstablished.get(), is(1));
                assertThat(syntheticReleases.get(), is(1));
            } finally {
                continueRejection.countDown();
                continueRelease.countDown();
                if (rejectionAction != null) {
                    rejectionAction.handle((_, failure) -> null).get(10, TimeUnit.SECONDS);
                }
                if (stopping != null) {
                    stopping.handle((_, failure) -> null).get(10, TimeUnit.SECONDS);
                }
                if (closing != null) {
                    closing.handle((_, failure) -> null).get(10, TimeUnit.SECONDS);
                }
                client.close();
                runtime.close();
            }
        }
    }

    @Test
    void closeWaitsForConnectionSetupWithoutHoldingRuntimeLock() throws Exception {
        CountDownLatch setupStarted = new CountDownLatch(1);
        CountDownLatch continueSetup = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            QuicServerRuntime runtime = serverBuilder()
                    .executor(executor)
                    .quicConfig(blockingSetupConfig(setupStarted, continueSetup))
                    .build();
            CompletableFuture<Void> incoming = null;
            try {
                runtime.start();
                incoming = CompletableFuture.runAsync(() -> sendIncompleteInitial(runtime, 5), executor);
                assertThat(setupStarted.await(10, TimeUnit.SECONDS), is(true));

                CompletableFuture<Boolean> closing =
                        CompletableFuture.supplyAsync(() -> runtime.close(Duration.ofSeconds(10)), executor);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (runtime.accepting() && System.nanoTime() < deadline) {
                    Thread.onSpinWait();
                }

                assertThat(runtime.accepting(), is(false));
                assertThat(closing.isDone(), is(false));
                Throwable acceptFailure = runtime.accept()
                        .handle((_, failure) -> failure)
                        .get(1, TimeUnit.SECONDS);
                assertThat(acceptFailure, instanceOf(IllegalStateException.class));

                continueSetup.countDown();
                incoming.get(10, TimeUnit.SECONDS);
                assertThat(closing.get(10, TimeUnit.SECONDS), is(true));
            } finally {
                continueSetup.countDown();
                if (incoming != null) {
                    incoming.handle((_, _) -> null).get(10, TimeUnit.SECONDS);
                }
                runtime.close();
            }
        }
    }

    @Test
    void closeWaitsForUnpublishedAdmissionRelease() throws Exception {
        CountDownLatch setupStarted = new CountDownLatch(1);
        CountDownLatch continueSetup = new CountDownLatch(1);
        CountDownLatch releaseStarted = new CountDownLatch(1);
        CountDownLatch continueRelease = new CountDownLatch(1);
        AtomicInteger releases = new AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            QuicServerRuntime runtime = serverBuilder()
                    .executor(executor)
                    .quicConfig(blockingSetupConfig(setupStarted, continueSetup))
                    .connectionAdmission(() -> QuicServerRuntime.ConnectionPermit.accepted(
                            () -> {
                                releaseStarted.countDown();
                                try {
                                    if (!continueRelease.await(10, TimeUnit.SECONDS)) {
                                        throw new IllegalStateException(
                                                "Timed out waiting to continue admission release");
                                    }
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new IllegalStateException("Interrupted while waiting to release admission", e);
                                }
                                releases.incrementAndGet();
                            },
                            () -> { }))
                    .build();
            CompletableFuture<Void> incoming = null;
            try {
                runtime.start();
                incoming = CompletableFuture.runAsync(() -> sendIncompleteInitial(runtime, 6), executor);
                assertThat(setupStarted.await(10, TimeUnit.SECONDS), is(true));

                CompletableFuture<Boolean> closing =
                        CompletableFuture.supplyAsync(() -> runtime.close(Duration.ofSeconds(10)), executor);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (runtime.accepting() && System.nanoTime() < deadline) {
                    Thread.onSpinWait();
                }

                assertThat(runtime.accepting(), is(false));
                assertThat(closing.isDone(), is(false));

                continueSetup.countDown();
                assertThat(releaseStarted.await(10, TimeUnit.SECONDS), is(true));
                assertThat(closing.isDone(), is(false));

                continueRelease.countDown();
                incoming.get(10, TimeUnit.SECONDS);
                assertThat(closing.get(10, TimeUnit.SECONDS), is(true));
                assertThat(releases.get(), is(1));
            } finally {
                continueSetup.countDown();
                continueRelease.countDown();
                if (incoming != null) {
                    incoming.handle((_, _) -> null).get(10, TimeUnit.SECONDS);
                }
                runtime.close();
            }
        }
    }

    @Test
    void rejectsLifecycleReentryFromUnpublishedAdmissionRelease() throws Exception {
        AtomicReference<QuicServerRuntime> runtimeReference = new AtomicReference<>();
        AtomicReference<IllegalStateException> stopFailure = new AtomicReference<>();
        AtomicReference<IllegalStateException> resumeFailure = new AtomicReference<>();
        AtomicReference<IllegalStateException> closeFailure = new AtomicReference<>();
        AtomicReference<IllegalStateException> abortFailure = new AtomicReference<>();
        AtomicReference<Boolean> timedCloseResult = new AtomicReference<>();
        AtomicInteger releases = new AtomicInteger();
        CountDownLatch setupStarted = new CountDownLatch(1);
        CountDownLatch continueSetup = new CountDownLatch(1);
        QuicConfig delegate = QuicConfig.builder()
                .availableVersions(List.of(QuicVersion.QUIC_V1))
                .buildPrototype();
        QuicConfig config = mock(QuicConfig.class, AdditionalAnswers.delegatesTo(delegate));
        doAnswer(_ -> {
            setupStarted.countDown();
            try {
                if (!continueSetup.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to continue failed connection setup");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting to fail connection setup", e);
            }
            throw new IllegalStateException("test connection setup failure");
        }).when(config).idleTimeout();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            QuicServerRuntime runtime = serverBuilder()
                    .executor(executor)
                    .quicConfig(config)
                    .connectionAdmission(() -> QuicServerRuntime.ConnectionPermit.accepted(
                            () -> {
                                QuicServerRuntime callbackRuntime = runtimeReference.get();
                                stopFailure.set(assertThrows(IllegalStateException.class,
                                                             callbackRuntime::stopAccepting));
                                resumeFailure.set(assertThrows(IllegalStateException.class,
                                                               callbackRuntime::resumeAccepting));
                                closeFailure.set(assertThrows(IllegalStateException.class, callbackRuntime::close));
                                timedCloseResult.set(callbackRuntime.close(Duration.ofSeconds(1)));
                                abortFailure.set(assertThrows(
                                        IllegalStateException.class,
                                        () -> callbackRuntime.abort(new IllegalStateException("test callback abort"))));
                                releases.incrementAndGet();
                            },
                            () -> { }))
                    .build();
            runtimeReference.set(runtime);
            CompletableFuture<Void> incoming = null;
            try {
                runtime.start();
                incoming = CompletableFuture.runAsync(() -> sendIncompleteInitial(runtime, 7), executor);
                assertThat(setupStarted.await(10, TimeUnit.SECONDS), is(true));
                continueSetup.countDown();
                incoming.get(10, TimeUnit.SECONDS);

                assertThat(stopFailure.get().getMessage(), containsString("connection-permit release callback"));
                assertThat(resumeFailure.get().getMessage(), containsString("connection-permit release callback"));
                assertThat(closeFailure.get().getMessage(), containsString("connection-permit release callback"));
                assertThat(abortFailure.get().getMessage(), containsString("connection-permit release callback"));
                assertThat(timedCloseResult.get(), is(false));
                assertThat(runtime.accepting(), is(true));
                assertThat(releases.get(), is(1));
                assertThat(runtime.close(Duration.ofSeconds(10)), is(true));
            } finally {
                continueSetup.countDown();
                if (incoming != null) {
                    incoming.handle((_, _) -> null).get(10, TimeUnit.SECONDS);
                }
                runtime.close();
            }
        }
    }

    @Test
    void handlesLifecycleReentryFromStopRejectionWithoutSelfWait() throws Exception {
        QuicServerRuntime runtime = serverBuilder().build();
        CompletableFuture<QuicConnection> pendingAccept = runtime.accept();
        AtomicReference<String> phase = new AtomicReference<>("waiting for rejection");
        AtomicReference<Throwable> acceptFailure = new AtomicReference<>();
        AtomicReference<Throwable> stopFailure = new AtomicReference<>();
        AtomicReference<Throwable> resumeFailure = new AtomicReference<>();
        AtomicReference<Throwable> closeFailure = new AtomicReference<>();
        AtomicReference<Throwable> timedCloseFailure = new AtomicReference<>();
        AtomicReference<Boolean> timedCloseResult = new AtomicReference<>();
        AtomicReference<Throwable> abortFailure = new AtomicReference<>();
        AtomicInteger reentrantStops = new AtomicInteger();
        CompletableFuture<Void> rejectionAction = pendingAccept.handle((_, failure) -> {
            acceptFailure.set(failure);
            phase.set("reentrant stop");
            try {
                runtime.stopAccepting();
                reentrantStops.incrementAndGet();
            } catch (Throwable t) {
                stopFailure.set(t);
            }
            phase.set("reentrant resume");
            try {
                runtime.resumeAccepting();
            } catch (Throwable t) {
                resumeFailure.set(t);
            }
            phase.set("reentrant close");
            try {
                runtime.close();
            } catch (Throwable t) {
                closeFailure.set(t);
            }
            phase.set("reentrant timed close");
            try {
                timedCloseResult.set(runtime.close(Duration.ofSeconds(1)));
            } catch (Throwable t) {
                timedCloseFailure.set(t);
            }
            phase.set("reentrant abort");
            try {
                runtime.abort(new IllegalStateException("test stop-rejection callback abort"));
            } catch (Throwable t) {
                abortFailure.set(t);
            }
            phase.set("complete");
            return null;
        });
        AtomicReference<Throwable> outerFailure = new AtomicReference<>();
        CountDownLatch stopReturned = new CountDownLatch(1);
        Thread probe = Thread.ofVirtual()
                .name("quic-stop-reentry-probe")
                .start(() -> {
                    try {
                        runtime.stopAccepting();
                    } catch (Throwable t) {
                        outerFailure.set(t);
                    } finally {
                        stopReturned.countDown();
                    }
                });

        assertThat(probe.isVirtual(), is(true));
        assertThat(probe.isDaemon(), is(true));
        assertThat("Stop rejection remained in phase " + phase.get(),
                   stopReturned.await(10, TimeUnit.SECONDS),
                   is(true));
        rejectionAction.get(10, TimeUnit.SECONDS);
        assertThat(outerFailure.get(), is((Throwable) null));
        assertThat(acceptFailure.get(), instanceOf(IllegalStateException.class));
        assertThat(reentrantStops.get(), is(1));
        assertThat(stopFailure.get(), is((Throwable) null));
        assertThat(resumeFailure.get(), instanceOf(IllegalStateException.class));
        assertThat(resumeFailure.get().getMessage(), containsString("stop-rejection callback"));
        assertThat(closeFailure.get(), instanceOf(IllegalStateException.class));
        assertThat(closeFailure.get().getMessage(), containsString("stop-rejection callback"));
        assertThat(timedCloseFailure.get(), is((Throwable) null));
        assertThat(timedCloseResult.get(), is(false));
        assertThat(abortFailure.get(), instanceOf(IllegalStateException.class));
        assertThat(abortFailure.get().getMessage(), containsString("stop-rejection callback"));
        assertThat(phase.get(), is("complete"));
        assertThat(runtime.accepting(), is(false));
        assertThat(runtime.close(Duration.ofSeconds(10)), is(true));
    }

    @Test
    void concurrentLifecycleCallsWaitForActiveStopCleanup() throws Exception {
        CountDownLatch setupStarted = new CountDownLatch(1);
        CountDownLatch continueSetup = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            QuicServerRuntime runtime = serverBuilder()
                    .executor(executor)
                    .quicConfig(blockingSetupConfig(setupStarted, continueSetup))
                    .build();
            CompletableFuture<Void> firstStop = null;
            CompletableFuture<Void> secondStop = null;
            CompletableFuture<Void> resuming = null;
            CompletableFuture<Boolean> closing = null;
            CompletableFuture<Void> rejectionAction = null;
            CompletableFuture<Void> incoming = null;
            CountDownLatch continueRejection = new CountDownLatch(1);
            try {
                runtime.start();
                CompletableFuture<QuicConnection> pendingAccept = runtime.accept();
                CountDownLatch rejectionStarted = new CountDownLatch(1);
                AtomicReference<Throwable> acceptFailure = new AtomicReference<>();
                rejectionAction = pendingAccept.handle((_, failure) -> {
                    acceptFailure.set(failure);
                    rejectionStarted.countDown();
                    try {
                        if (!continueRejection.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out waiting to continue stop rejection");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while waiting to continue stop rejection", e);
                    }
                    return null;
                });
                incoming = CompletableFuture.runAsync(() -> sendIncompleteInitial(runtime, 8), executor);
                assertThat(setupStarted.await(10, TimeUnit.SECONDS), is(true));

                firstStop = CompletableFuture.runAsync(runtime::stopAccepting, executor);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (runtime.accepting() && System.nanoTime() < deadline) {
                    Thread.onSpinWait();
                }
                assertThat(runtime.accepting(), is(false));

                CountDownLatch secondStarted = new CountDownLatch(1);
                AtomicReference<Thread> secondThread = new AtomicReference<>();
                secondStop = CompletableFuture.runAsync(() -> {
                    secondThread.set(Thread.currentThread());
                    secondStarted.countDown();
                    runtime.stopAccepting();
                }, executor);
                assertThat(secondStarted.await(10, TimeUnit.SECONDS), is(true));
                deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (!secondStop.isDone()
                        && secondThread.get().getState() != Thread.State.WAITING
                        && System.nanoTime() < deadline) {
                    Thread.onSpinWait();
                }

                assertThat(firstStop.isDone(), is(false));
                assertThat(secondStop.isDone(), is(false));
                assertThat(secondThread.get().getState(), is(Thread.State.WAITING));
                assertThat(pendingAccept.isDone(), is(false));

                continueSetup.countDown();
                incoming.get(10, TimeUnit.SECONDS);
                assertThat(rejectionStarted.await(10, TimeUnit.SECONDS), is(true));

                assertThat(acceptFailure.get(), instanceOf(IllegalStateException.class));

                CountDownLatch lifecycleCallsStarted = new CountDownLatch(2);
                AtomicReference<Thread> resumeThread = new AtomicReference<>();
                resuming = CompletableFuture.runAsync(() -> {
                    resumeThread.set(Thread.currentThread());
                    lifecycleCallsStarted.countDown();
                    runtime.resumeAccepting();
                }, executor);
                AtomicReference<Thread> closeThread = new AtomicReference<>();
                closing = CompletableFuture.supplyAsync(() -> {
                    closeThread.set(Thread.currentThread());
                    lifecycleCallsStarted.countDown();
                    return runtime.close(Duration.ofSeconds(10));
                }, executor);
                assertThat(lifecycleCallsStarted.await(10, TimeUnit.SECONDS), is(true));
                deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                boolean resumeWaitObserved = false;
                boolean closeWaitObserved = false;
                while ((!resumeWaitObserved || !closeWaitObserved) && System.nanoTime() < deadline) {
                    resumeWaitObserved |= resumeThread.get().getState() == Thread.State.WAITING;
                    closeWaitObserved |= closeThread.get().getState() == Thread.State.WAITING;
                    Thread.onSpinWait();
                }

                assertThat(firstStop.isDone(), is(false));
                assertThat(secondStop.isDone(), is(false));
                assertThat(rejectionAction.isDone(), is(false));
                assertThat(resuming.isDone(), is(false));
                assertThat(resumeWaitObserved, is(true));
                assertThat(closing.isDone(), is(false));
                assertThat(closeWaitObserved, is(true));

                continueRejection.countDown();
                rejectionAction.get(10, TimeUnit.SECONDS);
                firstStop.get(10, TimeUnit.SECONDS);
                secondStop.get(10, TimeUnit.SECONDS);
                Throwable ordinaryResumeFailure = resuming.handle((_, failure) -> failure)
                        .get(10, TimeUnit.SECONDS);
                assertThat(ordinaryResumeFailure, instanceOf(CompletionException.class));
                assertThat(ordinaryResumeFailure.getCause(), instanceOf(IllegalStateException.class));
                assertThat(closing.get(10, TimeUnit.SECONDS), is(true));
                assertThat(runtime.accepting(), is(false));
            } finally {
                continueSetup.countDown();
                continueRejection.countDown();
                if (incoming != null) {
                    incoming.handle((_, _) -> null).get(10, TimeUnit.SECONDS);
                }
                if (rejectionAction != null) {
                    rejectionAction.handle((_, failure) -> null).get(10, TimeUnit.SECONDS);
                }
                if (firstStop != null) {
                    firstStop.handle((_, failure) -> null).get(10, TimeUnit.SECONDS);
                }
                if (secondStop != null) {
                    secondStop.handle((_, failure) -> null).get(10, TimeUnit.SECONDS);
                }
                if (resuming != null) {
                    resuming.handle((_, failure) -> null).get(10, TimeUnit.SECONDS);
                }
                if (closing != null) {
                    closing.handle((_, failure) -> null).get(10, TimeUnit.SECONDS);
                }
                runtime.close();
            }
        }
    }

    @Test
    void abortWaitsForActiveStopCleanup() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            QuicServerRuntime runtime = serverBuilder().executor(executor).build();
            CompletableFuture<Void> stopping = null;
            CompletableFuture<Void> aborting = null;
            CompletableFuture<Void> rejectionAction = null;
            CountDownLatch continueRejection = new CountDownLatch(1);
            try {
                runtime.start();
                CompletableFuture<QuicConnection> pendingAccept = runtime.accept();
                CountDownLatch rejectionStarted = new CountDownLatch(1);
                AtomicReference<Throwable> acceptFailure = new AtomicReference<>();
                rejectionAction = pendingAccept.handle((_, failure) -> {
                    acceptFailure.set(failure);
                    rejectionStarted.countDown();
                    try {
                        if (!continueRejection.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out waiting to continue stop rejection");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while waiting to continue stop rejection", e);
                    }
                    return null;
                });

                stopping = CompletableFuture.runAsync(runtime::stopAccepting, executor);
                assertThat(rejectionStarted.await(10, TimeUnit.SECONDS), is(true));

                CountDownLatch abortStarted = new CountDownLatch(1);
                AtomicReference<Thread> abortThread = new AtomicReference<>();
                aborting = CompletableFuture.runAsync(() -> {
                    abortThread.set(Thread.currentThread());
                    abortStarted.countDown();
                    runtime.abort(new IllegalStateException("test concurrent abort"));
                }, executor);
                assertThat(abortStarted.await(10, TimeUnit.SECONDS), is(true));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (!aborting.isDone()
                        && abortThread.get().getState() != Thread.State.WAITING
                        && System.nanoTime() < deadline) {
                    Thread.onSpinWait();
                }

                assertThat(acceptFailure.get(), instanceOf(IllegalStateException.class));
                assertThat(stopping.isDone(), is(false));
                assertThat(rejectionAction.isDone(), is(false));
                assertThat(aborting.isDone(), is(false));
                assertThat(abortThread.get().getState(), is(Thread.State.WAITING));

                continueRejection.countDown();
                rejectionAction.get(10, TimeUnit.SECONDS);
                stopping.get(10, TimeUnit.SECONDS);
                aborting.get(10, TimeUnit.SECONDS);
                assertThat(runtime.accepting(), is(false));
            } finally {
                continueRejection.countDown();
                if (rejectionAction != null) {
                    rejectionAction.handle((_, failure) -> null).get(10, TimeUnit.SECONDS);
                }
                if (stopping != null) {
                    stopping.handle((_, failure) -> null).get(10, TimeUnit.SECONDS);
                }
                if (aborting != null) {
                    aborting.handle((_, failure) -> null).get(10, TimeUnit.SECONDS);
                }
                runtime.close();
            }
        }
    }

    @Test
    void defersInternalFailureUntilStopCleanupReleasesAdmission() throws Exception {
        AtomicInteger releases = new AtomicInteger();
        AtomicReference<QuicServerConnection> createdConnection = new AtomicReference<>();
        QuicServerRuntime runtime = serverBuilder()
                .connectionAdmission(() -> QuicServerRuntime.ConnectionPermit.accepted(
                        releases::incrementAndGet,
                        () -> { }))
                .observer(new QuicServerRuntime.Observer() {
                    @Override
                    public void connectionCreated(QuicConnection connection) {
                        createdConnection.set((QuicServerConnection) connection);
                    }
                })
                .build();
        runtime.start();
        sendIncompleteInitial(runtime, 11);
        QuicServerConnection connection = createdConnection.get();
        assertThat(connection, notNullValue());
        assertThat(connection.isOpen(), is(true));
        IllegalStateException routeFailure = new IllegalStateException("test endpoint route cleanup failure");
        assertThat(connection.routeLifecycle().beginRemoval(connection), is(true));
        connection.routeLifecycle().completeRemovalExceptionally(routeFailure);
        AtomicReference<String> phase = new AtomicReference<>("waiting for rejection");
        AtomicReference<Throwable> acceptFailure = new AtomicReference<>();
        AtomicReference<QuicTLSContext> contextDuringCleanup = new AtomicReference<>();
        AtomicInteger releasesDuringCleanup = new AtomicInteger();
        CountDownLatch internalFailureRecorded = new CountDownLatch(1);
        CountDownLatch continueRejection = new CountDownLatch(1);
        CompletableFuture<Void> rejectionAction = runtime.accept().handle((_, failure) -> {
            acceptFailure.set(failure);
            phase.set("recording internal failure");
            connection.terminate(QuicCloseCommand.silent("test deferred internal failure"));
            connection.whenTerminated().handle((_, _) -> null).toCompletableFuture().join();
            contextDuringCleanup.set(runtime.quicTlsContext());
            releasesDuringCleanup.set(releases.get());
            internalFailureRecorded.countDown();
            phase.set("holding stop cleanup");
            try {
                if (!continueRejection.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to continue internal failure rejection");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while holding internal failure rejection", e);
            }
            phase.set("complete");
            return null;
        });
        AtomicReference<Throwable> outerFailure = new AtomicReference<>();
        CountDownLatch stopReturned = new CountDownLatch(1);
        Thread stopProbe = Thread.ofVirtual()
                .name("quic-deferred-abort-stop-probe")
                .start(() -> {
                    try {
                        runtime.stopAccepting();
                    } catch (Throwable t) {
                        outerFailure.set(t);
                    } finally {
                        stopReturned.countDown();
                    }
                });

        assertThat(stopProbe.isVirtual(), is(true));
        assertThat(stopProbe.isDaemon(), is(true));
        assertThat("Deferred abort remained in phase " + phase.get(),
                   internalFailureRecorded.await(10, TimeUnit.SECONDS),
                   is(true));
        AtomicReference<Throwable> resumeFailure = new AtomicReference<>();
        AtomicReference<Thread> resumeThread = new AtomicReference<>();
        CountDownLatch resumeStarted = new CountDownLatch(1);
        CountDownLatch resumeReturned = new CountDownLatch(1);
        Thread resumeProbe = Thread.ofVirtual()
                .name("quic-deferred-abort-resume-probe")
                .start(() -> {
                    resumeThread.set(Thread.currentThread());
                    resumeStarted.countDown();
                    try {
                        runtime.resumeAccepting();
                    } catch (Throwable t) {
                        resumeFailure.set(t);
                    } finally {
                        resumeReturned.countDown();
                    }
                });
        assertThat(resumeProbe.isVirtual(), is(true));
        assertThat(resumeProbe.isDaemon(), is(true));
        assertThat(resumeStarted.await(10, TimeUnit.SECONDS), is(true));
        long resumeDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!resumeReturned.await(0, TimeUnit.NANOSECONDS)
                && resumeThread.get().getState() != Thread.State.WAITING
                && System.nanoTime() < resumeDeadline) {
            Thread.onSpinWait();
        }
        assertThat(resumeReturned.await(100, TimeUnit.MILLISECONDS), is(false));
        assertThat(resumeThread.get().getState(), is(Thread.State.WAITING));

        continueRejection.countDown();
        assertThat("Stop did not return after phase " + phase.get(),
                   stopReturned.await(10, TimeUnit.SECONDS),
                   is(true));
        assertThat(resumeReturned.await(10, TimeUnit.SECONDS), is(true));
        rejectionAction.get(10, TimeUnit.SECONDS);
        assertThat(outerFailure.get(), is((Throwable) null));
        assertThat(resumeFailure.get(), instanceOf(IllegalStateException.class));
        assertThat(acceptFailure.get(), instanceOf(IllegalStateException.class));
        assertThat(contextDuringCleanup.get(), notNullValue());
        assertThat(releasesDuringCleanup.get(), is(1));
        assertThat(releases.get(), is(1));
        assertThat(phase.get(), is("complete"));
        connection.terminate(QuicCloseCommand.silent("test duplicate termination"));
        assertThat(releases.get(), is(1));
        assertThrows(IllegalStateException.class, runtime::quicTlsContext);
        assertThrows(IllegalStateException.class, runtime::resumeAccepting);
        runtime.close();
    }

    @Test
    void stopAndResumeSerializeAcrossConnectionSetup() throws Exception {
        CountDownLatch setupStarted = new CountDownLatch(1);
        CountDownLatch continueSetup = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            QuicServerRuntime runtime = serverBuilder()
                    .executor(executor)
                    .quicConfig(blockingSetupConfig(setupStarted, continueSetup))
                    .build();
            CompletableFuture<Void> incoming = null;
            try {
                runtime.start();
                incoming = CompletableFuture.runAsync(() -> sendIncompleteInitial(runtime, 9), executor);
                assertThat(setupStarted.await(10, TimeUnit.SECONDS), is(true));

                CompletableFuture<Void> stopping = CompletableFuture.runAsync(runtime::stopAccepting, executor);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (runtime.accepting() && System.nanoTime() < deadline) {
                    Thread.onSpinWait();
                }
                CompletableFuture<Void> resuming = CompletableFuture.runAsync(runtime::resumeAccepting, executor);

                assertThat(runtime.accepting(), is(false));
                assertThat(stopping.isDone(), is(false));
                assertThat(resuming.isDone(), is(false));

                continueSetup.countDown();
                incoming.get(10, TimeUnit.SECONDS);
                stopping.get(10, TimeUnit.SECONDS);
                resuming.get(10, TimeUnit.SECONDS);
                assertThat(runtime.accepting(), is(true));
            } finally {
                continueSetup.countDown();
                if (incoming != null) {
                    incoming.handle((_, _) -> null).get(10, TimeUnit.SECONDS);
                }
                runtime.close();
            }
        }
    }

    private static QuicConfig blockingSetupConfig(CountDownLatch setupStarted,
                                                  CountDownLatch continueSetup) {
        QuicConfig delegate = QuicConfig.builder()
                .availableVersions(List.of(QuicVersion.QUIC_V1))
                .buildPrototype();
        QuicConfig config = mock(QuicConfig.class, AdditionalAnswers.delegatesTo(delegate));
        doAnswer(_ -> {
            setupStarted.countDown();
            try {
                if (!continueSetup.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to continue connection setup");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting to continue connection setup", e);
            }
            return delegate.idleTimeout();
        }).when(config).idleTimeout();
        return config;
    }

    private static void sendIncompleteInitial(QuicServerRuntime runtime, int discriminator) {
        byte[] destinationId = new byte[8];
        byte[] sourceId = new byte[8];
        for (int i = 0; i < destinationId.length; i++) {
            destinationId[i] = (byte) (discriminator + i);
            sourceId[i] = (byte) (discriminator + destinationId.length + i);
        }
        ByteBuffer packet = ByteBuffer.allocate(QuicServerIngress.MIN_INITIAL_DATAGRAM_SIZE);
        packet.put((byte) 0xc0);
        packet.putInt(QuicVersion.QUIC_V1.versionNumber());
        packet.put((byte) destinationId.length);
        packet.put(destinationId);
        packet.put((byte) sourceId.length);
        packet.put(sourceId);
        VariableLengthEncoder.encode(packet, 0);
        VariableLengthEncoder.encode(packet, packet.capacity() - packet.position() - 2);
        while (packet.hasRemaining()) {
            packet.put((byte) 0);
        }
        packet.flip();
        runtime.unmatchedQuicPacket(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 10_000 + discriminator),
                QuicPacket.HeadersType.LONG,
                packet);
    }

    private static QuicServerRuntime.Builder serverBuilder() {
        try {
            return QuicServerRuntime.builder()
                    .executor(Runnable::run)
                    .tls(Tls.builder()
                                 .privateKey(QuicTlsRfc8448Vectors.rsaPrivateKey())
                                 .privateKeyCertChain(List.of(QuicTlsRfc8448Vectors.rsaCertificate()))
                                 .trustAll(true)
                                 .build());
        } catch (Exception e) {
            throw new AssertionError("Failed to create QUIC server test identity", e);
        }
    }

}
