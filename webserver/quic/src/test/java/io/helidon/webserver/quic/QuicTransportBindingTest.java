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

import java.io.UncheckedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.concurrency.limits.FixedLimit;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.configurable.Resource;
import io.helidon.common.context.Context;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.common.uri.UriAuthority;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigNode.ListNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.media.MediaContext;
import io.helidon.quic.QuicClientConnection;
import io.helidon.quic.QuicClientRuntime;
import io.helidon.quic.QuicCloseCommand;
import io.helidon.quic.QuicConfig;
import io.helidon.quic.QuicConnection;
import io.helidon.quic.QuicVersion;
import io.helidon.quic.SequentialScheduler;
import io.helidon.quic.VariableLengthEncoder;
import io.helidon.quic.stream.QuicBidiStream;
import io.helidon.quic.stream.QuicReceiverStream;
import io.helidon.quic.stream.QuicSenderStream;
import io.helidon.quic.stream.QuicStreamReader;
import io.helidon.quic.stream.QuicStreamWriter;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.ListenerTlsContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.SniContext;
import io.helidon.webserver.SniMatchType;
import io.helidon.webserver.TransportBindingContext;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.quic.spi.QuicSubProtocolConfig;
import io.helidon.webserver.quic.spi.QuicSubProtocolProvider;
import io.helidon.webserver.quic.spi.QuicSubProtocolRuntime;
import io.helidon.webserver.spi.PortTransportBinding;
import io.helidon.webserver.spi.TransportBinding;
import io.helidon.webserver.spi.TransportBindingFactory;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicTransportBindingTest {
    private static final String LISTENER_NAME = "test-quic-listener";
    private static final String TEST_PROVIDER_TYPE = "test-echo";
    private static final String ALPHA_NAME = "alpha";
    private static final String BETA_NAME = "beta";
    private static final String ALPHA_ALPN = "helidon-test-alpha";
    private static final String BETA_ALPN = "helidon-test-beta";
    private static final char[] KEY_PASSWORD = "changeit".toCharArray();
    private static final String SERVER_KEYSTORE = "io/helidon/quic/server-keystore.p12";
    private static final String CLIENT_TRUSTSTORE = "io/helidon/quic/client-truststore.p12";
    private static final Runnable HOLD_ANY = () -> { };
    private static final Map<String, BlockingQueue<QuicConnection>> ACCEPTED_CONNECTIONS = new ConcurrentHashMap<>();
    private static final Map<String, RuntimeCounters> RUNTIME_COUNTERS = new ConcurrentHashMap<>();
    private static final Map<String, Runnable> RUNTIME_START_ACTIONS = new ConcurrentHashMap<>();

    @Test
    void shouldDispatchDifferentAlpnsOverSameQuicTransportBinding() throws Exception {
        ACCEPTED_CONNECTIONS.clear();
        TlsContexts tlsContexts = tlsContexts();
        ListenerConfig listenerConfig = ListenerConfig.builder()
                .name(LISTENER_NAME)
                .tls(tlsContexts.serverTls())
                .addProtocol(alphaProtocol())
                .addProtocol(betaProtocol())
                .build();
        QuicTransportConfig bindingConfig = QuicTransportConfig.builder()
                .quic(it -> it.availableVersions(List.of(QuicVersion.QUIC_V1)))
                .build();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
             QuicClientRuntime client = QuicClientRuntime.builder()
                     .executor(executor)
                     .quicConfig(QuicConfig.builder()
                                         .availableVersions(List.of(QuicVersion.QUIC_V1))
                                         .buildPrototype())
                     .tls(tlsContexts.clientTls())
                     .build()) {
            TransportBindingContext context = new TestTransportBindingContext(listenerConfig, executor);
            PortTransportBinding binding = (PortTransportBinding) QuicTransportBindingFactory.create(bindingConfig)
                    .create(context);
            assertThat(binding.type(), is(QuicTransportBindingTypes.QUIC));

            try {
                binding.start();
                InetSocketAddress serverAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), binding.port());

                assertRoundTrip(client, serverAddress, ALPHA_NAME, ALPHA_ALPN, "alpha-payload", "alpha:alpha-payload");
                assertRoundTrip(client, serverAddress, BETA_NAME, BETA_ALPN, "beta-payload", "beta:beta-payload");
            } finally {
                binding.stop(Duration.ofSeconds(5));
            }
        } finally {
            ACCEPTED_CONNECTIONS.clear();
        }
    }

    @Test
    void shouldDispatchWhitespaceOnlyOpaqueAlpn() throws Exception {
        ACCEPTED_CONNECTIONS.clear();
        String protocolName = "opaque";
        String opaqueAlpn = " ";
        TlsContexts tlsContexts = tlsContexts();
        ListenerConfig listenerConfig = ListenerConfig.builder()
                .name(LISTENER_NAME)
                .tls(tlsContexts.serverTls())
                .addProtocol(new TestEchoQuicSubProtocolConfig(TEST_PROVIDER_TYPE,
                                                               protocolName,
                                                               opaqueAlpn,
                                                               protocolName,
                                                               false))
                .build();
        QuicTransportConfig bindingConfig = QuicTransportConfig.builder()
                .quic(it -> it.availableVersions(List.of(QuicVersion.QUIC_V1)))
                .build();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
             QuicClientRuntime client = QuicClientRuntime.builder()
                     .executor(executor)
                     .quicConfig(QuicConfig.builder()
                                         .availableVersions(List.of(QuicVersion.QUIC_V1))
                                         .buildPrototype())
                     .tls(tlsContexts.clientTls())
                     .build()) {
            TransportBindingContext context = new TestTransportBindingContext(listenerConfig, executor);
            PortTransportBinding binding = (PortTransportBinding) QuicTransportBindingFactory.create(bindingConfig)
                    .create(context);

            try {
                binding.start();
                InetSocketAddress serverAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), binding.port());

                assertRoundTrip(client, serverAddress, protocolName, opaqueAlpn, "payload", "opaque:payload");
            } finally {
                binding.stop(Duration.ofSeconds(5));
            }
        } finally {
            ACCEPTED_CONNECTIONS.clear();
        }
    }

    @Test
    void shouldConsistentlyUseTransportDerivedAlpnFallback() throws Exception {
        QuicTransportConfig bindingConfig = QuicTransportConfig.builder()
                .quic(it -> it.availableVersions(List.of(QuicVersion.QUIC_V1)))
                .build();
        TransportBindingFactory bindingFactory = QuicTransportBindingFactory.create(bindingConfig);

        assertConsistentPreference(bindingFactory,
                                   List.of(alphaProtocol(), betaProtocol()),
                                   ALPHA_NAME,
                                   ALPHA_ALPN,
                                   BETA_NAME,
                                   new String[] {BETA_ALPN, ALPHA_ALPN});
        assertConsistentPreference(bindingFactory,
                                   List.of(betaProtocol(), alphaProtocol()),
                                   ALPHA_NAME,
                                   ALPHA_ALPN,
                                   BETA_NAME,
                                   new String[] {BETA_ALPN, ALPHA_ALPN});
    }

    @Test
    void shouldConsistentlyUseConfiguredAlpnPreference() throws Exception {
        Config config = Config.create(ConfigSources.create(
                ObjectNode.builder()
                        .addList("alpn-preference",
                                 ListNode.builder()
                                         .addValue(BETA_ALPN)
                                         .addValue(ALPHA_ALPN)
                                         .build())
                        .build()));
        QuicTransportBindingProvider provider = new QuicTransportBindingProvider();
        TransportBindingFactory bindingFactory = provider.create(config);

        assertConsistentPreference(bindingFactory,
                                   List.of(alphaProtocol(), betaProtocol()),
                                   BETA_NAME,
                                   BETA_ALPN,
                                   ALPHA_NAME,
                                   new String[] {ALPHA_ALPN, BETA_ALPN});
    }

    @Test
    void shouldDispatchAcceptedConnectionsThroughListenerExecutor() throws Exception {
        ACCEPTED_CONNECTIONS.clear();
        RUNTIME_START_ACTIONS.clear();
        TlsContexts tlsContexts = tlsContexts();
        ListenerConfig listenerConfig = ListenerConfig.builder()
                .name(LISTENER_NAME)
                .tls(tlsContexts.serverTls())
                .addProtocol(alphaProtocol())
                .build();
        QuicTransportConfig bindingConfig = QuicTransportConfig.builder()
                .quic(it -> it.availableVersions(List.of(QuicVersion.QUIC_V1)))
                .build();

        try (HoldingExecutorService serverExecutor =
                     new HoldingExecutorService(Executors.newVirtualThreadPerTaskExecutor());
             ExecutorService clientExecutor = Executors.newVirtualThreadPerTaskExecutor();
             QuicClientRuntime client = QuicClientRuntime.builder()
                     .executor(clientExecutor)
                     .quicConfig(QuicConfig.builder()
                                         .availableVersions(List.of(QuicVersion.QUIC_V1))
                                         .buildPrototype())
                     .tls(tlsContexts.clientTls())
                     .build()) {
            TransportBindingContext context = new TestTransportBindingContext(listenerConfig, serverExecutor);
            PortTransportBinding binding = (PortTransportBinding) QuicTransportBindingFactory.create(bindingConfig)
                    .create(context);
            RUNTIME_START_ACTIONS.put(ALPHA_NAME, serverExecutor::holdNext);

            try {
                binding.start();
                Runnable acceptTask = serverExecutor.awaitHeld();
                InetSocketAddress serverAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), binding.port());
                QuicClientConnection first = client.createConnection(serverAddress,
                                                                     serverAddress.getHostString(),
                                                                     serverAddress.getPort(),
                                                                     new String[] {ALPHA_ALPN});
                first.startHandshake().get(20, TimeUnit.SECONDS);

                assertThat(acceptedConnections(ALPHA_NAME).poll(200, TimeUnit.MILLISECONDS), nullValue());
                serverExecutor.release(acceptTask);
                assertThat(awaitAcceptedConnection(ALPHA_NAME), notNullValue());

                serverExecutor.hold(acceptTask);
                QuicClientConnection second = client.createConnection(serverAddress,
                                                                      serverAddress.getHostString(),
                                                                      serverAddress.getPort(),
                                                                      new String[] {ALPHA_ALPN});
                second.startHandshake().get(20, TimeUnit.SECONDS);
                Runnable resumedAcceptTask = serverExecutor.awaitHeld();

                assertThat(resumedAcceptTask, sameInstance(acceptTask));
                assertThat(acceptedConnections(ALPHA_NAME).poll(200, TimeUnit.MILLISECONDS), nullValue());
                serverExecutor.release(resumedAcceptTask);
                assertThat(awaitAcceptedConnection(ALPHA_NAME), notNullValue());
            } finally {
                binding.stop(Duration.ofSeconds(5));
            }
        } finally {
            ACCEPTED_CONNECTIONS.clear();
            RUNTIME_START_ACTIONS.clear();
        }
    }

    @Test
    void shouldForwardRetryPolicyFromBindingConfiguration() throws Exception {
        TlsContexts tlsContexts = tlsContexts();
        ListenerConfig listenerConfig = ListenerConfig.builder()
                .name(LISTENER_NAME)
                .tls(tlsContexts.serverTls())
                .addProtocol(alphaProtocol())
                .build();
        QuicTransportConfig bindingConfig = QuicTransportConfig.builder()
                .retryEnabled(true)
                .quic(it -> it.availableVersions(List.of(QuicVersion.QUIC_V1)))
                .build();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            PortTransportBinding binding = (PortTransportBinding) QuicTransportBindingFactory.create(bindingConfig)
                    .create(new TestTransportBindingContext(listenerConfig, executor));
            try {
                binding.start();
                byte[] destinationId = new byte[8];
                byte[] sourceId = new byte[8];
                sourceId[0] = 1;
                ByteBuffer initial = ByteBuffer.allocate(1200);
                initial.put((byte) 0xc0);
                initial.putInt(QuicVersion.QUIC_V1.versionNumber());
                initial.put((byte) destinationId.length);
                initial.put(destinationId);
                initial.put((byte) sourceId.length);
                initial.put(sourceId);
                VariableLengthEncoder.encode(initial, 0);
                VariableLengthEncoder.encode(initial, initial.capacity() - initial.position() - 2);

                try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))) {
                    socket.setSoTimeout(5000);
                    socket.send(new DatagramPacket(initial.array(), initial.capacity(),
                                                   new InetSocketAddress(InetAddress.getLoopbackAddress(), binding.port())));
                    byte[] received = new byte[1200];
                    DatagramPacket responsePacket = new DatagramPacket(received, received.length);
                    socket.receive(responsePacket);

                    ByteBuffer response = ByteBuffer.wrap(received, 0, responsePacket.getLength());
                    assertThat(response.get() & 0xf0, is(0xf0));
                    assertThat(response.getInt(), is(QuicVersion.QUIC_V1.versionNumber()));
                    byte[] responseDestinationId = new byte[Byte.toUnsignedInt(response.get())];
                    response.get(responseDestinationId);
                    assertThat(responseDestinationId, is(sourceId));
                    int responseSourceLength = Byte.toUnsignedInt(response.get());
                    assertThat(responseSourceLength > 0, is(true));
                    response.position(response.position() + responseSourceLength);
                    assertThat(response.remaining() > 16, is(true));
                }
            } finally {
                binding.stop(Duration.ofSeconds(5));
            }
        }
    }

    @Test
    void shouldInheritAlreadyBoundListenerPortWhenConfiguredForDynamicPort() throws Exception {
        TlsContexts tlsContexts = tlsContexts();
        ListenerConfig listenerConfig = ListenerConfig.builder()
                .name(LISTENER_NAME)
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .tls(tlsContexts.serverTls())
                .addProtocol(alphaProtocol())
                .addProtocol(betaProtocol())
                .build();
        QuicTransportConfig bindingConfig = QuicTransportConfig.builder()
                .quic(it -> it.availableVersions(List.of(QuicVersion.QUIC_V1)))
                .build();

        try (ServerSocketChannel tcpSocket = ServerSocketChannel.open();
             ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            tcpSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            int inheritedPort = ((InetSocketAddress) tcpSocket.getLocalAddress()).getPort();

            TransportBindingContext context = new TestTransportBindingContext(listenerConfig,
                                                                             executor,
                                                                             OptionalInt.of(inheritedPort));
            PortTransportBinding binding = (PortTransportBinding) QuicTransportBindingFactory.create(bindingConfig)
                    .create(context);

            try {
                assertThat(binding.security(), is(TransportBinding.Security.TLS));
                assertThat(binding.configuredEndpoint(), containsString(":" + inheritedPort));
                binding.start();

                assertThat(binding.port(), equalTo(inheritedPort));
            } finally {
                binding.stop(Duration.ofSeconds(5));
            }
        }
    }

    @Test
    void shouldAddListenerContextToEndpointStartupFailure() throws Exception {
        TlsContexts tlsContexts = tlsContexts();

        try (DatagramChannel occupiedPort = DatagramChannel.open();
             ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            occupiedPort.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            int port = ((InetSocketAddress) occupiedPort.getLocalAddress()).getPort();
            ListenerConfig listenerConfig = ListenerConfig.builder()
                    .name(LISTENER_NAME)
                    .address(InetAddress.getLoopbackAddress())
                    .port(port)
                    .tls(tlsContexts.serverTls())
                    .addProtocol(alphaProtocol())
                    .build();
            QuicTransportConfig bindingConfig = QuicTransportConfig.builder()
                    .quic(it -> it.availableVersions(List.of(QuicVersion.QUIC_V1)))
                    .build();
            PortTransportBinding binding = (PortTransportBinding) QuicTransportBindingFactory.create(bindingConfig)
                    .create(new TestTransportBindingContext(listenerConfig, executor));

            UncheckedIOException failure = assertThrows(UncheckedIOException.class, binding::start);

            assertThat(failure.getMessage(), containsString("Failed to start QUIC listener for socket " + LISTENER_NAME));
            assertThat(failure.getMessage(), containsString("Failed to create QUIC endpoint"));
            assertThat(failure.getCause(), notNullValue());
            assertThat(binding.port(), is(-1));
            assertThat(binding.stop(Duration.ofSeconds(5)), is(TransportBinding.ShutdownResult.GRACEFUL));
        }
    }

    @Test
    void shouldRejectInsufficientPeerUniStreamsBeforeCreatingEndpoint() throws Exception {
        RUNTIME_COUNTERS.clear();
        String protocolName = "requires-three";
        TlsContexts tlsContexts = tlsContexts();

        try (DatagramChannel occupiedPort = DatagramChannel.open();
             ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            occupiedPort.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            int port = ((InetSocketAddress) occupiedPort.getLocalAddress()).getPort();
            ListenerConfig listenerConfig = ListenerConfig.builder()
                    .name(LISTENER_NAME)
                    .address(InetAddress.getLoopbackAddress())
                    .port(port)
                    .tls(tlsContexts.serverTls())
                    .addProtocol(protocolWithMinimumPeerUniStreams(protocolName,
                                                                  "helidon-test-requires-three",
                                                                  3))
                    .build();
            QuicTransportConfig bindingConfig = QuicTransportConfig.builder()
                    .quic(it -> it.availableVersions(List.of(QuicVersion.QUIC_V1))
                            .maxUniStreams(2))
                    .build();
            PortTransportBinding binding = (PortTransportBinding) QuicTransportBindingFactory.create(bindingConfig)
                    .create(new TestTransportBindingContext(listenerConfig, executor));

            ConfigException failure = assertThrows(ConfigException.class, binding::start);

            assertThat(failure.getMessage(), containsString("Listener " + LISTENER_NAME));
            assertThat(failure.getMessage(), containsString(TEST_PROVIDER_TYPE + "(" + protocolName + ")"));
            assertThat(failure.getMessage(), containsString("QuicConfig.maxUniStreams to be at least 3"));
            assertThat(failure.getMessage(), containsString("binding configures 2"));
            assertThat(binding.port(), is(-1));
            assertThat(RUNTIME_COUNTERS.get(protocolName).starts.get(), is(0));
            assertThat(RUNTIME_COUNTERS.get(protocolName).closes.get(), is(1));
            assertThat(binding.stop(Duration.ZERO), is(TransportBinding.ShutdownResult.GRACEFUL));
        } finally {
            RUNTIME_COUNTERS.clear();
        }
    }

    @Test
    void shouldUseLargestPeerUniStreamRequirementRatherThanSum() throws Exception {
        RUNTIME_COUNTERS.clear();
        TlsContexts tlsContexts = tlsContexts();
        ListenerConfig listenerConfig = ListenerConfig.builder()
                .name(LISTENER_NAME)
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .tls(tlsContexts.serverTls())
                .addProtocol(protocolWithMinimumPeerUniStreams(ALPHA_NAME, ALPHA_ALPN, 3))
                .addProtocol(protocolWithMinimumPeerUniStreams(BETA_NAME, BETA_ALPN, 3))
                .build();
        QuicTransportConfig bindingConfig = QuicTransportConfig.builder()
                .quic(it -> it.availableVersions(List.of(QuicVersion.QUIC_V1))
                        .maxUniStreams(3))
                .build();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            PortTransportBinding binding = (PortTransportBinding) QuicTransportBindingFactory.create(bindingConfig)
                    .create(new TestTransportBindingContext(listenerConfig, executor));
            try {
                binding.start();

                assertThat(binding.port() > 0, is(true));
            } finally {
                binding.stop(Duration.ofSeconds(5));
            }
        } finally {
            RUNTIME_COUNTERS.clear();
        }
    }

    @Test
    void shouldAcceptDefaultRuntimeRequirementWithZeroPeerUniStreams() throws Exception {
        RUNTIME_COUNTERS.clear();
        TlsContexts tlsContexts = tlsContexts();
        ListenerConfig listenerConfig = ListenerConfig.builder()
                .name(LISTENER_NAME)
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .tls(tlsContexts.serverTls())
                .addProtocol(alphaProtocol())
                .build();
        QuicTransportConfig bindingConfig = QuicTransportConfig.builder()
                .quic(it -> it.availableVersions(List.of(QuicVersion.QUIC_V1))
                        .maxUniStreams(0))
                .build();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            PortTransportBinding binding = (PortTransportBinding) QuicTransportBindingFactory.create(bindingConfig)
                    .create(new TestTransportBindingContext(listenerConfig, executor));
            try {
                binding.start();

                assertThat(binding.port() > 0, is(true));
            } finally {
                binding.stop(Duration.ofSeconds(5));
            }
        } finally {
            RUNTIME_COUNTERS.clear();
        }
    }

    @Test
    void shouldRejectNegativePeerUniStreamRequirement() throws Exception {
        RUNTIME_COUNTERS.clear();
        String protocolName = "negative-requirement";
        TlsContexts tlsContexts = tlsContexts();
        ListenerConfig listenerConfig = ListenerConfig.builder()
                .name(LISTENER_NAME)
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .tls(tlsContexts.serverTls())
                .addProtocol(protocolWithMinimumPeerUniStreams(protocolName,
                                                              "helidon-test-negative-requirement",
                                                              -1))
                .build();
        QuicTransportConfig bindingConfig = QuicTransportConfig.builder()
                .quic(it -> it.maxUniStreams(0))
                .build();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            PortTransportBinding binding = (PortTransportBinding) QuicTransportBindingFactory.create(bindingConfig)
                    .create(new TestTransportBindingContext(listenerConfig, executor));

            ConfigException failure = assertThrows(ConfigException.class, binding::start);

            assertThat(failure.getMessage(), containsString("Listener " + LISTENER_NAME));
            assertThat(failure.getMessage(), containsString(TEST_PROVIDER_TYPE + "(" + protocolName + ")"));
            assertThat(failure.getMessage(), containsString("negative minimumPeerUniStreams requirement -1"));
            assertThat(binding.port(), is(-1));
            assertThat(RUNTIME_COUNTERS.get(protocolName).starts.get(), is(0));
            assertThat(RUNTIME_COUNTERS.get(protocolName).closes.get(), is(1));
        } finally {
            RUNTIME_COUNTERS.clear();
        }
    }

    @Test
    void shouldRejectNullGracePeriodWithoutStoppingListener() throws Exception {
        TlsContexts tlsContexts = tlsContexts();
        ListenerConfig listenerConfig = ListenerConfig.builder()
                .name(LISTENER_NAME)
                .tls(tlsContexts.serverTls())
                .addProtocol(alphaProtocol())
                .build();
        QuicTransportConfig bindingConfig = QuicTransportConfig.builder()
                .quic(it -> it.availableVersions(List.of(QuicVersion.QUIC_V1)))
                .build();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            PortTransportBinding binding = (PortTransportBinding) QuicTransportBindingFactory.create(bindingConfig)
                    .create(new TestTransportBindingContext(listenerConfig, executor));
            try {
                binding.start();
                int port = binding.port();

                assertThrows(NullPointerException.class, () -> binding.stop(null));

                assertThat(binding.port(), is(port));
            } finally {
                binding.stop(Duration.ofSeconds(5));
            }
        }
    }

    @Test
    void shouldAlwaysReportTlsSecurity() throws Exception {
        ListenerConfig listenerConfig = ListenerConfig.builder()
                .name(LISTENER_NAME)
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .addProtocol(alphaProtocol())
                .build();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            TransportBinding binding = QuicTransportBindingFactory.create(QuicTransportConfig.create())
                    .create(new TestTransportBindingContext(listenerConfig, executor));

            assertThat(binding.type(), is(QuicTransportBindingTypes.QUIC));
            assertThat(binding.holdsIdleConnectionPermit(), is(false));
            assertThat(binding.security(), is(TransportBinding.Security.TLS));
        }
    }

    @Test
    void shouldAllowListenerVirtualHosts() throws Exception {
        TlsContexts tlsContexts = tlsContexts();
        ListenerConfig listenerConfig = ListenerConfig.builder()
                .name(LISTENER_NAME)
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .tls(tlsContexts.serverTls())
                .addProtocol(alphaProtocol())
                .build();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            TestTransportBindingContext context = new TestTransportBindingContext(listenerConfig,
                                                                                   executor,
                                                                                   OptionalInt.empty(),
                                                                                   FixedLimit.create(),
                                                                                   true);

            PortTransportBinding binding = (PortTransportBinding) QuicTransportBindingFactory
                    .create(QuicTransportConfig.create())
                    .create(context);
            binding.start();
            assertThat(binding.port() > 0, is(true));
            assertThat(binding.stop(Duration.ofSeconds(5)), is(TransportBinding.ShutdownResult.GRACEFUL));
        }
    }

    @Test
    void shouldRecreateRuntimeAfterSuspendAndResume() throws Exception {
        ACCEPTED_CONNECTIONS.clear();
        RUNTIME_COUNTERS.clear();
        TlsContexts tlsContexts = tlsContexts();
        ListenerConfig listenerConfig = ListenerConfig.builder()
                .name(LISTENER_NAME)
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .tls(tlsContexts.serverTls())
                .addProtocol(alphaProtocol())
                .build();
        QuicTransportConfig bindingConfig = QuicTransportConfig.builder()
                .quic(it -> it.availableVersions(List.of(QuicVersion.QUIC_V1)))
                .build();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
             QuicClientRuntime client = QuicClientRuntime.builder()
                     .executor(executor)
                     .quicConfig(QuicConfig.builder()
                                         .availableVersions(List.of(QuicVersion.QUIC_V1))
                                         .buildPrototype())
                     .tls(tlsContexts.clientTls())
                     .build()) {
            TransportBindingContext context = new TestTransportBindingContext(listenerConfig, executor);
            PortTransportBinding binding = (PortTransportBinding) QuicTransportBindingFactory.create(bindingConfig)
                    .create(context);

            try {
                binding.start();
                assertRoundTrip(client,
                                new InetSocketAddress(InetAddress.getLoopbackAddress(), binding.port()),
                                ALPHA_NAME,
                                ALPHA_ALPN,
                                "before-suspend",
                                "alpha:before-suspend");

                binding.suspend();

                assertThat(binding.port(), is(-1));
                assertThat(RUNTIME_COUNTERS.get(ALPHA_NAME).starts.get(), is(1));
                assertThat(RUNTIME_COUNTERS.get(ALPHA_NAME).closes.get(), is(1));

                binding.resume();

                assertThat(binding.port() > 0, is(true));
                assertThat(RUNTIME_COUNTERS.get(ALPHA_NAME).starts.get(), is(2));
                assertRoundTrip(client,
                                new InetSocketAddress(InetAddress.getLoopbackAddress(), binding.port()),
                                ALPHA_NAME,
                                ALPHA_ALPN,
                                "after-resume",
                                "alpha:after-resume");
            } finally {
                binding.stop(Duration.ofSeconds(5));
            }

            assertThat(RUNTIME_COUNTERS.get(ALPHA_NAME).closes.get(), is(2));
        } finally {
            ACCEPTED_CONNECTIONS.clear();
            RUNTIME_COUNTERS.clear();
        }
    }

    @Test
    void shouldShareOneGracefulCloseDeadlineAcrossProtocolRuntimes() throws Exception {
        RUNTIME_COUNTERS.clear();
        TlsContexts tlsContexts = tlsContexts();
        ListenerConfig listenerConfig = ListenerConfig.builder()
                .name(LISTENER_NAME)
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .tls(tlsContexts.serverTls())
                .addProtocol(alphaProtocol())
                .addProtocol(betaProtocol())
                .build();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            PortTransportBinding binding = (PortTransportBinding) QuicTransportBindingFactory
                    .create(QuicTransportConfig.create())
                    .create(new TestTransportBindingContext(listenerConfig, executor));
            try {
                binding.start();
                RUNTIME_COUNTERS.get(ALPHA_NAME).gracefulCloseAction.set(() -> {
                    try {
                        new CountDownLatch(1).await(100, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });

                Duration gracePeriod = Duration.ofMillis(500);
                TransportBinding.ShutdownResult result = binding.stop(gracePeriod);
                Duration alphaBudget = RUNTIME_COUNTERS.get(ALPHA_NAME).gracePeriod.get();
                Duration betaBudget = RUNTIME_COUNTERS.get(BETA_NAME).gracePeriod.get();

                assertThat(result, is(TransportBinding.ShutdownResult.GRACEFUL));
                assertThat(alphaBudget.compareTo(gracePeriod) <= 0, is(true));
                assertThat(betaBudget.compareTo(alphaBudget.minusMillis(50)) < 0, is(true));
            } finally {
                binding.stop(Duration.ZERO);
            }
        } finally {
            RUNTIME_COUNTERS.clear();
        }
    }

    @Test
    void shouldCloseAllSubProtocolRuntimesWhenStartupFails() throws Exception {
        RUNTIME_COUNTERS.clear();
        TlsContexts tlsContexts = tlsContexts();
        String failingName = "failing";
        ListenerConfig listenerConfig = ListenerConfig.builder()
                .name(LISTENER_NAME)
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .tls(tlsContexts.serverTls())
                .addProtocol(alphaProtocol())
                .addProtocol(new TestEchoQuicSubProtocolConfig(TEST_PROVIDER_TYPE,
                                                               failingName,
                                                               "helidon-test-failing",
                                                               failingName,
                                                               true))
                .addProtocol(betaProtocol())
                .build();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            PortTransportBinding binding = (PortTransportBinding) QuicTransportBindingFactory
                    .create(QuicTransportConfig.create())
                    .create(new TestTransportBindingContext(listenerConfig, executor));

            IllegalStateException failure = assertThrows(IllegalStateException.class, binding::start);

            assertThat(failure.getMessage(), containsString("test QUIC protocol start failed " + failingName));
            assertThat(binding.port(), is(-1));
            assertThat(RUNTIME_COUNTERS.get(ALPHA_NAME).starts.get(), is(1));
            assertThat(RUNTIME_COUNTERS.get(failingName).starts.get(), is(1));
            assertThat(RUNTIME_COUNTERS.get(BETA_NAME).starts.get(), is(0));
            assertThat(RUNTIME_COUNTERS.get(ALPHA_NAME).closes.get(), is(1));
            assertThat(RUNTIME_COUNTERS.get(failingName).closes.get(), is(1));
            assertThat(RUNTIME_COUNTERS.get(BETA_NAME).closes.get(), is(1));

            binding.stop(Duration.ofSeconds(5));

            assertThat(RUNTIME_COUNTERS.get(ALPHA_NAME).closes.get(), is(1));
            assertThat(RUNTIME_COUNTERS.get(failingName).closes.get(), is(1));
            assertThat(RUNTIME_COUNTERS.get(BETA_NAME).closes.get(), is(1));
        } finally {
            RUNTIME_COUNTERS.clear();
        }
    }

    @Test
    void shouldApplyListenerConnectionLimitToQuicConnections() throws Exception {
        ACCEPTED_CONNECTIONS.clear();
        TlsContexts tlsContexts = tlsContexts();
        ListenerConfig listenerConfig = ListenerConfig.builder()
                .name(LISTENER_NAME)
                .tls(tlsContexts.serverTls())
                .addProtocol(alphaProtocol())
                .addProtocol(betaProtocol())
                .build();
        QuicTransportConfig bindingConfig = QuicTransportConfig.builder()
                .quic(it -> it.availableVersions(List.of(QuicVersion.QUIC_V1)))
                .build();
        Limit connectionLimit = FixedLimit.create(builder -> builder.permits(1));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
             QuicClientRuntime client = QuicClientRuntime.builder()
                     .executor(executor)
                     .quicConfig(QuicConfig.builder()
                                         .availableVersions(List.of(QuicVersion.QUIC_V1))
                                         .buildPrototype())
                     .tls(tlsContexts.clientTls())
                     .build()) {
            TransportBindingContext context = new TestTransportBindingContext(listenerConfig,
                                                                              executor,
                                                                              OptionalInt.empty(),
                                                                              connectionLimit);
            PortTransportBinding binding = (PortTransportBinding) QuicTransportBindingFactory.create(bindingConfig)
                    .create(context);

            try {
                binding.start();
                InetSocketAddress serverAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), binding.port());
                QuicClientConnection first = client.createConnection(serverAddress,
                                                                     serverAddress.getHostString(),
                                                                     serverAddress.getPort(),
                                                                     new String[] {ALPHA_ALPN});
                first.startHandshake().get(20, TimeUnit.SECONDS);
                QuicConnection firstAccepted = awaitAcceptedConnection(ALPHA_NAME);
                assertThat(firstAccepted, notNullValue());

                QuicClientConnection second = client.createConnection(serverAddress,
                                                                      serverAddress.getHostString(),
                                                                      serverAddress.getPort(),
                                                                      new String[] {ALPHA_ALPN});
                CompletableFuture<?> secondHandshake = second.startHandshake();
                assertThrows(TimeoutException.class, () -> secondHandshake.get(1, TimeUnit.SECONDS));

                firstAccepted.terminate(QuicCloseCommand.application(0, "test permit release"));
                secondHandshake.get(20, TimeUnit.SECONDS);
                assertThat(awaitAcceptedConnection(ALPHA_NAME), notNullValue());
                assertEcho(second, "second", "alpha:second");
            } finally {
                binding.stop(Duration.ofSeconds(5));
            }
        } finally {
            ACCEPTED_CONNECTIONS.clear();
        }
    }

    private static void assertConsistentPreference(TransportBindingFactory bindingFactory,
                                                   List<? extends QuicSubProtocolConfig> protocols,
                                                   String expectedProtocolName,
                                                   String expectedAlpn,
                                                   String unexpectedProtocolName,
                                                   String[] clientAlpns) throws Exception {
        ACCEPTED_CONNECTIONS.clear();
        TlsContexts tlsContexts = tlsContexts();
        ListenerConfig listenerConfig = ListenerConfig.builder()
                .name(LISTENER_NAME)
                .tls(tlsContexts.serverTls())
                .protocols(protocols)
                .build();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
             QuicClientRuntime client = QuicClientRuntime.builder()
                     .executor(executor)
                     .quicConfig(QuicConfig.builder()
                                         .availableVersions(List.of(QuicVersion.QUIC_V1))
                                         .buildPrototype())
                     .tls(tlsContexts.clientTls())
                     .build()) {
            TransportBindingContext context = new TestTransportBindingContext(listenerConfig, executor);
            PortTransportBinding binding = (PortTransportBinding) bindingFactory.create(context);

            try {
                binding.start();
                InetSocketAddress serverAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), binding.port());

                for (int i = 0; i < 16; i++) {
                    QuicClientConnection connection = client.createConnection(serverAddress,
                                                                              serverAddress.getHostString(),
                                                                              serverAddress.getPort(),
                                                                              clientAlpns);
                    connection.startHandshake().get(20, TimeUnit.SECONDS);

                    assertThat(connection.applicationProtocol().orElseThrow(), is(expectedAlpn));
                    QuicConnection accepted = awaitAcceptedConnection(expectedProtocolName);
                    assertThat(accepted, notNullValue());
                    accepted.terminate(QuicCloseCommand.application(0, "test preference connection complete"));
                }
                assertThat(acceptedConnections(unexpectedProtocolName).poll(200, TimeUnit.MILLISECONDS), nullValue());
            } finally {
                binding.stop(Duration.ofSeconds(5));
            }
        } finally {
            ACCEPTED_CONNECTIONS.clear();
        }
    }

    private static void assertRoundTrip(QuicClientRuntime client,
                                        InetSocketAddress serverAddress,
                                        String protocolName,
                                        String alpn,
                                        String payload,
                                        String expectedResponse) throws Exception {
        QuicClientConnection clientConnection = client.createConnection(serverAddress,
                                                                        serverAddress.getHostString(),
                                                                        serverAddress.getPort(),
                                                                        new String[] {alpn});
        clientConnection.startHandshake().get(20, TimeUnit.SECONDS);
        assertThat(clientConnection.applicationProtocol().orElseThrow(), equalTo(alpn));
        assertThat(awaitAcceptedConnection(protocolName), notNullValue());

        QuicBidiStream clientStream = clientConnection.openNewLocalBidiStream(Duration.ofSeconds(5))
                .get(10, TimeUnit.SECONDS);
        CompletableFuture<byte[]> responseFuture = readAll(clientStream);
        writeAll(clientStream, payload.getBytes(StandardCharsets.UTF_8));

        assertThat(new String(responseFuture.get(20, TimeUnit.SECONDS), StandardCharsets.UTF_8),
                   equalTo(expectedResponse));
    }

    private static void assertEcho(QuicClientConnection clientConnection,
                                   String payload,
                                   String expectedResponse) throws Exception {
        QuicBidiStream clientStream = clientConnection.openNewLocalBidiStream(Duration.ofSeconds(5))
                .get(10, TimeUnit.SECONDS);
        CompletableFuture<byte[]> responseFuture = readAll(clientStream);
        writeAll(clientStream, payload.getBytes(StandardCharsets.UTF_8));

        assertThat(new String(responseFuture.get(20, TimeUnit.SECONDS), StandardCharsets.UTF_8),
                   equalTo(expectedResponse));
    }

    private static QuicConnection awaitAcceptedConnection(String protocolName) throws InterruptedException {
        return acceptedConnections(protocolName).poll(20, TimeUnit.SECONDS);
    }

    private static BlockingQueue<QuicConnection> acceptedConnections(String protocolName) {
        return ACCEPTED_CONNECTIONS.computeIfAbsent(protocolName, _ -> new LinkedBlockingQueue<>());
    }

    private static TestEchoQuicSubProtocolConfig alphaProtocol() {
        return new TestEchoQuicSubProtocolConfig(TEST_PROVIDER_TYPE,
                                                 ALPHA_NAME,
                                                 ALPHA_ALPN,
                                                 ALPHA_NAME,
                                                 false);
    }

    private static TestEchoQuicSubProtocolConfig betaProtocol() {
        return new TestEchoQuicSubProtocolConfig(TEST_PROVIDER_TYPE,
                                                 BETA_NAME,
                                                 BETA_ALPN,
                                                 BETA_NAME,
                                                 false);
    }

    private static TestEchoQuicSubProtocolConfig protocolWithMinimumPeerUniStreams(String name,
                                                                                   String alpn,
                                                                                   long minimum) {
        return new TestEchoQuicSubProtocolConfig(TEST_PROVIDER_TYPE,
                                                 name,
                                                 alpn,
                                                 name,
                                                 false,
                                                 minimum);
    }

    private static void attachEchoListener(QuicConnection connection, String prefix) {
        connection.addRemoteStreamListener(stream -> {
            if (!(stream instanceof QuicBidiStream bidiStream)) {
                return false;
            }
            CompletableFuture<byte[]> received = readAll(bidiStream);
            QuicStreamWriter writer = bidiStream.connectWriter(SequentialScheduler.lockingScheduler(() -> {
            }));
            received.whenComplete((bytes, throwable) -> {
                if (throwable != null) {
                    return;
                }
                byte[] response = (prefix + ":" + new String(bytes, StandardCharsets.UTF_8))
                        .getBytes(StandardCharsets.UTF_8);
                writer.scheduleForWriting(BufferData.create(response), true);
            });
            return true;
        });
    }

    private static void writeAll(QuicSenderStream stream, byte[] bytes) {
        QuicStreamWriter writer = stream.connectWriter(SequentialScheduler.lockingScheduler(() -> {
        }));
        writer.scheduleForWriting(BufferData.create(bytes), true);
    }

    private static CompletableFuture<byte[]> readAll(QuicReceiverStream stream) {
        CompletableFuture<byte[]> result = new CompletableFuture<>();
        BufferData output = BufferData.growing(256);
        AtomicReference<QuicStreamReader> readerRef = new AtomicReference<>();
        SequentialScheduler scheduler = SequentialScheduler.lockingScheduler(() -> {
            try {
                QuicStreamReader reader = readerRef.get();
                for (;;) {
                    Optional<BufferData> next = reader.poll();
                    if (next.isEmpty()) {
                        return;
                    }
                    BufferData buffer = next.orElseThrow();
                    if (buffer == QuicStreamReader.EOF) {
                        result.complete(output.readBytes());
                        return;
                    }
                    output.write(buffer);
                }
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        QuicStreamReader reader = stream.connectReader(scheduler);
        readerRef.set(reader);
        reader.start();
        return result;
    }

    private static TlsContexts tlsContexts() throws Exception {
        Keys keys = Keys.builder()
                .keystore(store -> store
                        .passphrase(new String(KEY_PASSWORD))
                        .keyAlias("server")
                        .certChainAlias("server")
                        .keystore(Resource.create(SERVER_KEYSTORE)))
                .build();

        Tls serverTls = Tls.builder()
                .privateKey(keys.privateKey().orElseThrow())
                .privateKeyCertChain(keys.certChain())
                .build();
        Tls clientTls = Tls.builder()
                .trust(trust -> trust.keystore(store -> store
                        .passphrase(new String(KEY_PASSWORD))
                        .trustStore(true)
                        .keystore(Resource.create(CLIENT_TRUSTSTORE))))
                .build();
        return new TlsContexts(serverTls, clientTls);
    }

    public static final class EchoTestQuicSubProtocolProvider
            implements QuicSubProtocolProvider<TestEchoQuicSubProtocolConfig> {
        /**
         * Public constructor required by {@link java.util.ServiceLoader}.
         */
        public EchoTestQuicSubProtocolProvider() {
        }

        @Override
        public String configKey() {
            return TEST_PROVIDER_TYPE;
        }

        @Override
        public Class<TestEchoQuicSubProtocolConfig> protocolConfigType() {
            return TestEchoQuicSubProtocolConfig.class;
        }

        @Override
        public QuicSubProtocolRuntime create(TransportBindingContext context,
                                             TestEchoQuicSubProtocolConfig config) {
            RuntimeCounters counters = RUNTIME_COUNTERS.computeIfAbsent(config.name(), _ -> new RuntimeCounters());
            return new QuicSubProtocolRuntime() {
                private final AtomicBoolean closed = new AtomicBoolean();

                @Override
                public List<String> alpnIds() {
                    return List.of(config.alpn());
                }

                @Override
                public long minimumPeerUniStreams() {
                    return config.minimumPeerUniStreams();
                }

                @Override
                public void accept(QuicConnection connection) {
                    attachEchoListener(connection, config.responsePrefix());
                    acceptedConnections(config.name()).offer(connection);
                }

                @Override
                public void start() {
                    counters.starts.incrementAndGet();
                    RUNTIME_START_ACTIONS.getOrDefault(config.name(), () -> { }).run();
                    if (config.failOnStart()) {
                        throw new IllegalStateException("test QUIC protocol start failed " + config.name());
                    }
                }

                @Override
                public TransportBinding.ShutdownResult closeGracefully(Duration gracePeriod) {
                    counters.gracePeriod.set(gracePeriod);
                    counters.gracefulCloseAction.get().run();
                    close();
                    return TransportBinding.ShutdownResult.GRACEFUL;
                }

                @Override
                public void close() {
                    if (closed.compareAndSet(false, true)) {
                        counters.closes.incrementAndGet();
                    }
                }
            };
        }
    }

    private record TlsContexts(Tls serverTls, Tls clientTls) {
    }

    private record TestEchoQuicSubProtocolConfig(String type,
                                                 String name,
                                                 String alpn,
                                                 String responsePrefix,
                                                 boolean failOnStart,
                                                 long minimumPeerUniStreams) implements QuicSubProtocolConfig {
        private TestEchoQuicSubProtocolConfig(String type,
                                              String name,
                                              String alpn,
                                              String responsePrefix,
                                              boolean failOnStart) {
            this(type, name, alpn, responsePrefix, failOnStart, 0);
        }
    }

    private static final class HoldingExecutorService extends AbstractExecutorService {

        private final ExecutorService delegate;
        private final AtomicReference<Runnable> target = new AtomicReference<>();
        private final AtomicReference<Thread> targetThread = new AtomicReference<>();
        private final AtomicReference<CompletableFuture<Runnable>> held = new AtomicReference<>();

        private HoldingExecutorService(ExecutorService delegate) {
            this.delegate = delegate;
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }

        @Override
        public void execute(Runnable command) {
            Runnable expected = target.get();
            Thread expectedThread = targetThread.get();
            if (expected != null
                    && (expected == HOLD_ANY || expected == command)
                    && (expectedThread == null || expectedThread == Thread.currentThread())
                    && target.compareAndSet(expected, null)) {
                targetThread.set(null);
                held.get().complete(command);
                return;
            }
            delegate.execute(command);
        }

        private void holdNext() {
            hold(HOLD_ANY, Thread.currentThread());
        }

        private void hold(Runnable task) {
            hold(task, null);
        }

        private void hold(Runnable task, Thread thread) {
            CompletableFuture<Runnable> heldTask = new CompletableFuture<>();
            held.set(heldTask);
            targetThread.set(thread);
            if (!target.compareAndSet(null, task)) {
                throw new IllegalStateException("A listener executor task is already held");
            }
        }

        private Runnable awaitHeld() throws Exception {
            return held.get().get(20, TimeUnit.SECONDS);
        }

        private void release(Runnable task) throws Exception {
            CompletableFuture<Void> completed = new CompletableFuture<>();
            delegate.execute(() -> {
                try {
                    task.run();
                    completed.complete(null);
                } catch (RuntimeException | Error t) {
                    completed.completeExceptionally(t);
                }
            });
            completed.get(20, TimeUnit.SECONDS);
        }
    }

    private static final class RuntimeCounters {
        private final AtomicInteger starts = new AtomicInteger();
        private final AtomicInteger closes = new AtomicInteger();
        private final AtomicReference<Duration> gracePeriod = new AtomicReference<>();
        private final AtomicReference<Runnable> gracefulCloseAction = new AtomicReference<>(() -> {
        });
    }

    private static final class TestTransportBindingContext implements TransportBindingContext, ListenerContext {
        private final ListenerConfig listenerConfig;
        private final ExecutorService executor;
        private final OptionalInt boundPort;
        private final boolean virtualHostsEnabled;
        private final Limit requestLimit = FixedLimit.create();
        private final Limit connectionLimit;

        private TestTransportBindingContext(ListenerConfig listenerConfig, ExecutorService executor) {
            this(listenerConfig, executor, OptionalInt.empty(), FixedLimit.create(), false);
        }

        private TestTransportBindingContext(ListenerConfig listenerConfig,
                                            ExecutorService executor,
                                            OptionalInt boundPort) {
            this(listenerConfig, executor, boundPort, FixedLimit.create(), false);
        }

        private TestTransportBindingContext(ListenerConfig listenerConfig,
                                            ExecutorService executor,
                                            OptionalInt boundPort,
                                            Limit connectionLimit) {
            this(listenerConfig, executor, boundPort, connectionLimit, false);
        }

        private TestTransportBindingContext(ListenerConfig listenerConfig,
                                            ExecutorService executor,
                                            OptionalInt boundPort,
                                            Limit connectionLimit,
                                            boolean virtualHostsEnabled) {
            this.listenerConfig = listenerConfig;
            this.executor = executor;
            this.boundPort = boundPort;
            this.connectionLimit = connectionLimit;
            this.virtualHostsEnabled = virtualHostsEnabled;
        }

        @Override
        public SocketAddress configuredAddress() {
            return listenerConfig.bindAddress()
                    .orElseGet(() -> new InetSocketAddress(listenerConfig.address(), Math.max(0, listenerConfig.port())));
        }

        @Override
        public ListenerContext listenerContext() {
            return this;
        }

        @Override
        public Router router() {
            return Router.empty();
        }

        @Override
        public OptionalInt boundPort() {
            return boundPort;
        }

        @Override
        public Limit requestLimit() {
            return requestLimit;
        }

        @Override
        public Limit connectionLimit() {
            return connectionLimit;
        }

        @Override
        public ListenerTlsContext listenerTls() {
            return new TestListenerTlsContext(listenerConfig, virtualHostsEnabled);
        }

        @Override
        public void fatalBindingFailure(TransportBinding binding, Throwable cause) {
            throw new IllegalStateException("Fatal binding failure", cause);
        }

        @Override
        public Context context() {
            return Context.create();
        }

        @Override
        public MediaContext mediaContext() {
            return MediaContext.create();
        }

        @Override
        public ContentEncodingContext contentEncodingContext() {
            return ContentEncodingContext.create();
        }

        @Override
        public DirectHandlers directHandlers() {
            return DirectHandlers.create();
        }

        @Override
        public ListenerConfig config() {
            return listenerConfig;
        }

        @Override
        public ExecutorService executor() {
            return executor;
        }
    }

    private record TestListenerTlsContext(ListenerConfig listenerConfig,
                                          boolean virtualHostsEnabled) implements ListenerTlsContext {
        @Override
        public Tls tls() {
            return listenerConfig.tls().orElseGet(() -> Tls.builder().enabled(false).build());
        }

        @Override
        public boolean virtualHostsEnabled() {
            return virtualHostsEnabled;
        }

        @Override
        public void validateVirtualHosts() {
        }

        @Override
        public Selection select(String presentedHost) {
            return Selection.create(tls(),
                                    new TestSniContext(Optional.of(presentedHost),
                                                       Optional.empty(),
                                                       SniMatchType.FALLBACK_UNMATCHED));
        }

        @Override
        public Selection selectWithoutSni() {
            return Selection.create(tls(),
                                    new TestSniContext(Optional.empty(), Optional.empty(), SniMatchType.FALLBACK_MISSING));
        }
    }

    private record TestSniContext(Optional<String> presentedHost,
                                  Optional<String> matchedHost,
                                  SniMatchType matchType) implements SniContext {
        @Override
        public AuthorityCheck checkAuthority(UriAuthority authority) {
            return AuthorityCheck.ALLOWED;
        }
    }
}
