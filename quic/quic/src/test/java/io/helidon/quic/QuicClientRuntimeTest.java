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

import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLParameters;

import io.helidon.common.tls.Tls;
import io.helidon.common.tls.TlsMaterial;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicClientRuntimeTest {

    @Test
    void defaultsToEphemeralWildcardBindAddress() {
        try (QuicClientRuntime runtime = runtimeBuilder(Runnable::run).build()) {
            assertThat(runtime.bindAddress().getPort(), is(0));
            assertThat(runtime.bindAddress().getAddress().isAnyLocalAddress(), is(true));
        }
    }

    @Test
    void preservesExplicitBindAddress() {
        InetSocketAddress bindAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), 12345);
        try (QuicClientRuntime runtime = runtimeBuilder(Runnable::run)
                .bindAddress(bindAddress)
                .build()) {
            assertThat(runtime.bindAddress(), is(bindAddress));
        }
    }

    @Test
    void rejectsNullBuilderArguments() {
        QuicClientRuntime.Builder builder = QuicClientRuntime.builder();

        assertThrows(NullPointerException.class, () -> builder.applicationErrors(null));
        assertThrows(NullPointerException.class, () -> builder.clientId(null));
        assertThrows(NullPointerException.class, () -> builder.tls(null));
        assertThrows(NullPointerException.class, () -> builder.bindAddress(null));
        assertThrows(NullPointerException.class, () -> builder.executor(null));
        assertThrows(NullPointerException.class, () -> builder.quicConfig(null));
    }

    @Test
    void rejectsNullApplicationErrorDescription() {
        try (QuicClientRuntime runtime = runtimeBuilder(Runnable::run)
                .applicationErrors(errorCode -> null)
                .build()) {
            NullPointerException exception = assertThrows(NullPointerException.class,
                                                          () -> runtime.appErrorToString(0x100));

            assertThat(exception.getMessage(), equalTo("application error formatter result"));
        }
    }

    @Test
    void initialResponseTimeoutControlsClientAttempt() {
        Duration timeout = Duration.ofMillis(50);
        try (QuicClientRuntime runtime = runtimeBuilder(Runnable::run)
                .initialResponseTimeout(timeout)
                .build()) {
            QuicConnectionImpl connection = (QuicConnectionImpl) runtime.createConnection(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 443),
                    "localhost",
                    443,
                    new String[] {"h3"});
            CompletableFuture<?> handshake = connection.handshakeFlow().handshakeCF();
            connection.startInitialTimer();

            ExecutionException failure = assertThrows(ExecutionException.class,
                                                      () -> handshake.get(5, TimeUnit.SECONDS));
            assertThat(failure.getCause(), instanceOf(QuicConnectionException.class));
            assertThat(failure.getCause().getMessage(), equalTo("No response from peer after PT0.05S"));
        }
    }

    @Test
    void rejectsInvalidInitialResponseTimeout() {
        IllegalArgumentException zero = assertThrows(IllegalArgumentException.class,
                                                      () -> runtimeBuilder(Runnable::run)
                                                              .initialResponseTimeout(Duration.ZERO)
                                                              .build());
        assertThat(zero.getMessage(), equalTo("initialResponseTimeout must be positive: PT0S"));

        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class,
                                                          () -> runtimeBuilder(Runnable::run)
                                                                  .initialResponseTimeout(Duration.ofSeconds(-1))
                                                                  .build());
        assertThat(negative.getMessage(), equalTo("initialResponseTimeout must be positive: PT-1S"));
    }

    @Test
    void observerFailureDoesNotAffectCreatedConnection() {
        AtomicReference<QuicConnection> observed = new AtomicReference<>();
        AtomicInteger invocations = new AtomicInteger();
        QuicClientRuntime runtime = runtimeBuilder(Runnable::run)
                .observer(connection -> {
                    invocations.incrementAndGet();
                    observed.set(connection);
                    throw new IllegalStateException("observer failed");
                })
                .build();
        try {
            QuicClientConnection connection = runtime.createConnection(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 443),
                    "localhost",
                    443,
                    new String[] {"h3"});

            assertThat(observed.get(), is(connection));
            assertThat(invocations.get(), is(1));
            assertThat(connection.isOpen(), is(true));
        } finally {
            runtime.close();
        }
    }

    @Test
    void closeFromConnectionTerminationCallbackDoesNotWaitOnSameConnection() throws Exception {
        QuicClientRuntime runtime = runtimeBuilder(Runnable::run).build();
        InetSocketAddress peerAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), 443);
        try {
            QuicClientConnection connection = runtime.createConnection(peerAddress,
                                                                       "localhost",
                                                                       443,
                                                                       new String[] {"h3"});
            var closeCallback = connection.whenTerminated().thenRun(runtime::close).toCompletableFuture();

            connection.terminate(QuicCloseCommand.silent("test termination"));

            closeCallback.get(5, TimeUnit.SECONDS);
            assertThat(connection.whenTerminated().toCompletableFuture().isDone(), is(true));
        } finally {
            runtime.close();
        }
    }

    @Test
    void closeOwnsPreHandshakeConnectionWithoutClosingBorrowedExecutor() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        QuicClientRuntime runtime = runtimeBuilder(executor).build();
        InetSocketAddress peerAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), 443);
        try {
            QuicClientConnection connection = runtime.createConnection(peerAddress,
                                                                       "localhost",
                                                                       443,
                                                                       new String[] {"h3"});

            assertThat(connection.isOpen(), is(true));
            runtime.close();

            assertThat(connection.isOpen(), is(false));
            assertThat(connection.termination().isPresent(), is(true));
            assertThat(connection.whenTerminated().toCompletableFuture().isDone(), is(true));
            assertThat(connection.whenTerminated().toCompletableFuture().isCompletedExceptionally(), is(false));
            assertThat(executor.isShutdown(), is(false));
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                            () -> runtime.createConnection(peerAddress,
                                                                                           "localhost",
                                                                                           443,
                                                                                           new String[] {"h3"}));
            assertThat(exception.getMessage(), containsString("runtime is closed"));

            runtime.close();
            assertThat(executor.isShutdown(), is(false));
        } finally {
            runtime.close();
            executor.shutdownNow();
        }
    }

    @Test
    void abortIsTerminalAndDoesNotCloseBorrowedExecutor() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        QuicClientRuntime runtime = runtimeBuilder(executor).build();
        InetSocketAddress peerAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), 443);
        try {
            QuicClientConnection connection = runtime.createConnection(peerAddress,
                                                                       "localhost",
                                                                       443,
                                                                       new String[] {"h3"});

            runtime.abort(new IllegalStateException("transport failed"));

            assertThat(runtime.isClosed(), is(true));
            assertThat(connection.isOpen(), is(false));
            assertThat(connection.termination().isPresent(), is(true));
            assertThat(connection.whenTerminated().toCompletableFuture().isDone(), is(true));
            assertThat(connection.whenTerminated().toCompletableFuture().isCompletedExceptionally(), is(false));
            assertThat(executor.isShutdown(), is(false));
            assertThrows(IllegalStateException.class,
                         () -> runtime.createConnection(peerAddress,
                                                        "localhost",
                                                        443,
                                                        new String[] {"h3"}));

            runtime.abort(new IllegalStateException("ignored"));
            runtime.close();
            assertThat(executor.isShutdown(), is(false));
        } finally {
            runtime.close();
            executor.shutdownNow();
        }
    }

    @Test
    void endpointSetupFailureIsRolledBackBeforePublication() throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        try (DatagramChannel blocker = DatagramChannel.open()) {
            blocker.bind(new InetSocketAddress(loopback, 0));
            InetSocketAddress bindAddress = (InetSocketAddress) blocker.getLocalAddress();
            try (QuicClientRuntime runtime = runtimeBuilder(Runnable::run)
                    .bindAddress(bindAddress)
                    .build()) {
                InetSocketAddress peerAddress = new InetSocketAddress(loopback, 443);

                assertThrows(UncheckedIOException.class,
                             () -> runtime.createConnection(peerAddress,
                                                            "localhost",
                                                            443,
                                                            new String[] {"h3"}));

                blocker.close();
                QuicClientConnection connection = runtime.createConnection(peerAddress,
                                                                            "localhost",
                                                                            443,
                                                                            new String[] {"h3"});
                assertThat(connection.isOpen(), is(true));
            }
        }
    }

    @Test
    void requiresTls13AndApplicationProtocol() {
        NullPointerException executorException = assertThrows(NullPointerException.class,
                                                              () -> runtimeBuilder(null).build());
        assertThat(executorException.getMessage(), equalTo("executor"));

        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setProtocols(new String[] {"TLSv1.2"});
        Tls tls = Tls.builder()
                .trustAll(true)
                .sslParameters(sslParameters)
                .build();

        IllegalArgumentException tlsException = assertThrows(IllegalArgumentException.class,
                                                              () -> runtimeBuilder(Runnable::run)
                                                                      .tls(tls)
                                                                      .build());
        assertThat(tlsException.getMessage(),
                   containsString("Cannot construct a QUIC TLS context with the given TLS configuration"));

        try (QuicClientRuntime runtime = runtimeBuilder(Runnable::run).build()) {
            IllegalArgumentException alpnException = assertThrows(
                    IllegalArgumentException.class,
                    () -> runtime.createConnection(new InetSocketAddress(InetAddress.getLoopbackAddress(), 443),
                                                   "localhost",
                                                   443,
                                                   new String[0]));
            assertThat(alpnException.getMessage(), containsString("at least one ALPN is needed"));
        }
    }

    @Test
    void retainsTransportConfiguration() {
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setProtocols(new String[] {"TLSv1.3"});
        Tls tls = Tls.builder()
                .trustAll(true)
                .sslParameters(sslParameters)
                .build();
        QuicConfig quicConfig = QuicConfig.builder()
                .availableVersions(List.of(QuicVersion.QUIC_V1))
                .maxUdpPayloadSize(1400)
                .maxBidiStreams(12)
                .transportParameters(QuicTransportParametersConfig.builder()
                                             .maxAckDelay(Duration.ofMillis(12))
                                             .buildPrototype())
                .buildPrototype();

        try (QuicClientRuntime runtime = QuicClientRuntime.builder()
                .executor(Runnable::run)
                .tls(tls)
                .quicConfig(quicConfig)
                .build()) {
            sslParameters.setProtocols(new String[] {"TLSv1.2"});
            assertThat(List.of(runtime.sslParameters().getProtocols()), equalTo(List.of("TLSv1.3")));
            assertThat(runtime.quicConfig().maxUdpPayloadSize(), equalTo(1400));
            assertThat(runtime.quicConfig().maxBidiStreams(), equalTo(12L));
            assertThat(runtime.transportParameters()
                               .intParameter(QuicTransportParameters.ParameterId.max_ack_delay),
                       equalTo(12L));
        }
    }

    @Test
    void doesNotCloseBorrowedTlsSessionCache() {
        Tls tls = Tls.builder().trustAll(true).build();
        QuicClientTlsSessionCache sessionCache = QuicClientTlsSessionCache.create(tls);
        sessionCache.delegate().cache("example.com",
                                      443,
                                      new QuicTlsResumptionTicket(QuicVersion.QUIC_V1,
                                                                  QuicTls13CipherSuite.TLS_AES_128_GCM_SHA256,
                                                                  60,
                                                                  7,
                                                                  new byte[] {1},
                                                                  new byte[] {2},
                                                                  new byte[] {3},
                                                                  "h3",
                                                                  new byte[] {4},
                                                                  System.currentTimeMillis()));
        QuicClientRuntime runtime = QuicClientRuntime.builder()
                .executor(Runnable::run)
                .tls(tls)
                .tlsSessionCache(sessionCache)
                .build();

        runtime.close();

        assertThat(sessionCache.size(), is(1));
        sessionCache.close();
        assertThat(sessionCache.size(), is(0));
    }

    @Test
    void rejectsConnectionsAfterTlsReload() {
        Tls tls = Tls.builder().trustAll(true).build();
        try (QuicClientRuntime runtime = QuicClientRuntime.builder()
                .executor(Runnable::run)
                .tls(tls)
                .build()) {
            tls.reload(TlsMaterial.builder().trustAll(true).build());

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> runtime.createConnection(new InetSocketAddress(InetAddress.getLoopbackAddress(), 443),
                                                   "localhost",
                                                   443,
                                                   new String[] {"h3"}));

            assertThat(exception.getMessage(), containsString("TLS configuration was reloaded"));
        }
    }

    @Test
    void rejectsBorrowedTlsSessionCacheFromDifferentTls() {
        Tls cacheTls = Tls.builder().trustAll(true).build();
        Tls runtimeTls = Tls.builder().trustAll(true).build();
        try (QuicClientTlsSessionCache sessionCache = QuicClientTlsSessionCache.create(cacheTls)) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> QuicClientRuntime.builder()
                            .executor(Runnable::run)
                            .tls(runtimeTls)
                            .tlsSessionCache(sessionCache)
                            .build());

            assertThat(exception.getMessage(), containsString("does not belong to the configured TLS generation"));
        }
    }

    @Test
    void rejectsBorrowedTlsSessionCacheAfterReload() {
        Tls tls = Tls.builder().trustAll(true).build();
        try (QuicClientTlsSessionCache sessionCache = QuicClientTlsSessionCache.create(tls)) {
            tls.reload(TlsMaterial.builder().trustAll(true).build());

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> QuicClientRuntime.builder()
                            .executor(Runnable::run)
                            .tls(tls)
                            .tlsSessionCache(sessionCache)
                            .build());

            assertThat(exception.getMessage(), containsString("does not belong to the configured TLS generation"));
        }
    }

    @Test
    void cachesNewestTokenOncePerPeerAndVersionWithDefensiveCopies() {
        QuicConfig config = QuicConfig.builder()
                .availableVersions(List.of(QuicVersion.QUIC_V1, QuicVersion.QUIC_V2))
                .buildPrototype();
        InetSocketAddress peer = new InetSocketAddress(InetAddress.getLoopbackAddress(), 443);
        try (QuicClientRuntime runtime = runtimeBuilder(Runnable::run).quicConfig(config).build()) {
            byte[] v1 = {1, 2, 3};
            byte[] v2 = {4, 5, 6};
            runtime.registerInitialToken(peer, QuicVersion.QUIC_V1, v1);
            runtime.registerInitialToken(peer, QuicVersion.QUIC_V2, v2);
            v1[0] = 99;
            v2[0] = 99;

            byte[] cachedV1 = runtime.initialTokenFor(peer, QuicVersion.QUIC_V1).orElseThrow();
            assertThat(cachedV1, is(new byte[] {1, 2, 3}));
            cachedV1[0] = 88;
            assertThat(runtime.initialTokenFor(peer, QuicVersion.QUIC_V1).isEmpty(), is(true));
            assertThat(runtime.initialTokenFor(peer, QuicVersion.QUIC_V2).orElseThrow(), is(new byte[] {4, 5, 6}));

            runtime.registerInitialToken(peer, QuicVersion.QUIC_V1, new byte[] {7});
            runtime.registerInitialToken(peer, QuicVersion.QUIC_V1, new byte[] {8});
            assertThat(runtime.initialTokenFor(peer, QuicVersion.QUIC_V1).orElseThrow(), is(new byte[] {8}));
        }
    }

    @Test
    void boundsAndClearsInitialTokenCache() {
        InetSocketAddress first = new InetSocketAddress(InetAddress.getLoopbackAddress(), 10_000);
        QuicClientRuntime runtime = runtimeBuilder(Runnable::run).build();
        for (int i = 0; i <= QuicClientRuntime.MAX_INITIAL_TOKENS; i++) {
            runtime.registerInitialToken(new InetSocketAddress(InetAddress.getLoopbackAddress(), 10_000 + i),
                                         QuicVersion.QUIC_V1,
                                         new byte[] {(byte) (i >>> 24),
                                                 (byte) (i >>> 16),
                                                 (byte) (i >>> 8),
                                                 (byte) i});
        }
        assertThat(runtime.initialTokenFor(first, QuicVersion.QUIC_V1).isEmpty(), is(true));
        InetSocketAddress last = new InetSocketAddress(InetAddress.getLoopbackAddress(),
                                                       10_000 + QuicClientRuntime.MAX_INITIAL_TOKENS);
        assertThat(runtime.initialTokenFor(last, QuicVersion.QUIC_V1).isPresent(), is(true));

        runtime.registerInitialToken(first, QuicVersion.QUIC_V1, new byte[] {1});
        runtime.close();
        assertThat(runtime.initialTokenFor(first, QuicVersion.QUIC_V1).isEmpty(), is(true));
    }

    @Test
    void borrowedInitialTokenCacheSurvivesRuntimeRetirement() {
        InetSocketAddress peer = new InetSocketAddress(InetAddress.getLoopbackAddress(), 443);
        QuicClientInitialTokenCache tokenCache = QuicClientInitialTokenCache.create();
        try {
            QuicClientRuntime first = runtimeBuilder(Runnable::run)
                    .initialTokenCache(tokenCache)
                    .build();
            first.registerInitialToken(peer, QuicVersion.QUIC_V1, new byte[] {1, 2, 3});
            first.close();

            try (QuicClientRuntime second = runtimeBuilder(Runnable::run)
                    .initialTokenCache(tokenCache)
                    .build()) {
                assertThat(second.initialTokenFor(peer, QuicVersion.QUIC_V1).orElseThrow(),
                           is(new byte[] {1, 2, 3}));
                assertThat(second.initialTokenFor(peer, QuicVersion.QUIC_V1).isEmpty(), is(true));
                second.registerInitialToken(peer, QuicVersion.QUIC_V1, new byte[] {1, 2, 3});
                assertThat(second.initialTokenFor(peer, QuicVersion.QUIC_V1).orElseThrow(),
                           is(new byte[] {1, 2, 3}));
                assertThat(second.initialTokenFor(peer, QuicVersion.QUIC_V1).isEmpty(), is(true));
            }
        } finally {
            tokenCache.close();
        }
    }

    @Test
    void reportsInitialTokenCacheSize() {
        InetSocketAddress peer = new InetSocketAddress(InetAddress.getLoopbackAddress(), 443);
        byte[] token = {1, 2, 3};
        try (QuicClientInitialTokenCache tokenCache = QuicClientInitialTokenCache.create()) {
            assertThat(tokenCache.size(), is(0));
            tokenCache.register(peer, QuicVersion.QUIC_V1, token);
            assertThat(tokenCache.size(), is(1));
            assertThat(tokenCache.consume(peer, QuicVersion.QUIC_V1).orElseThrow(), is(token));
            assertThat(tokenCache.size(), is(0));
        }
    }

    @Test
    void discardsDuplicateNewTokenAfterConsumption() {
        InetSocketAddress peer = new InetSocketAddress(InetAddress.getLoopbackAddress(), 443);
        try (QuicClientRuntime runtime = runtimeBuilder(Runnable::run).build()) {
            byte[] first = {1, 2, 3};
            runtime.registerNewToken(peer, QuicVersion.QUIC_V1, first);
            assertThat(runtime.initialTokenFor(peer, QuicVersion.QUIC_V1).orElseThrow(), is(first));

            runtime.registerNewToken(peer, QuicVersion.QUIC_V1, first);
            assertThat(runtime.initialTokenFor(peer, QuicVersion.QUIC_V1).isEmpty(), is(true));

            byte[] replacement = {4, 5, 6};
            runtime.registerNewToken(peer, QuicVersion.QUIC_V1, replacement);
            assertThat(runtime.initialTokenFor(peer, QuicVersion.QUIC_V1).orElseThrow(), is(replacement));

            runtime.registerNewToken(peer, QuicVersion.QUIC_V1, first);
            runtime.registerNewToken(peer, QuicVersion.QUIC_V1, replacement);
            assertThat(runtime.initialTokenFor(peer, QuicVersion.QUIC_V1).isEmpty(), is(true));
        }
    }

    @Test
    void discardsReorderedNewTokenBeforeConsumption() {
        InetSocketAddress peer = new InetSocketAddress(InetAddress.getLoopbackAddress(), 443);
        try (QuicClientRuntime runtime = runtimeBuilder(Runnable::run).build()) {
            byte[] first = {1, 2, 3};
            byte[] replacement = {4, 5, 6};
            runtime.registerNewToken(peer, QuicVersion.QUIC_V1, first);
            runtime.registerNewToken(peer, QuicVersion.QUIC_V1, replacement);
            runtime.registerNewToken(peer, QuicVersion.QUIC_V1, first);

            assertThat(runtime.initialTokenFor(peer, QuicVersion.QUIC_V1).orElseThrow(), is(replacement));
            assertThat(runtime.initialTokenFor(peer, QuicVersion.QUIC_V1).isEmpty(), is(true));
        }
    }

    private static QuicClientRuntime.Builder runtimeBuilder(Executor executor) {
        return QuicClientRuntime.builder()
                .executor(executor)
                .quicConfig(QuicConfig.builder()
                                    .availableVersions(List.of(QuicVersion.QUIC_V1))
                                    .buildPrototype())
                .tls(Tls.builder().trustAll(true).build());
    }
}
