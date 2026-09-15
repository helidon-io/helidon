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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.quic.QuicEndpoint.QuicEndpointFactory;
import io.helidon.quic.QuicEndpoint.UnmatchedDatagram;
import io.helidon.quic.packet.QuicPacket.HeadersType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuicEndpointIngressTest {
    private static final InetSocketAddress PEER = new InetSocketAddress(InetAddress.getLoopbackAddress(), 4433);

    @Test
    void wildcardEphemeralEndpointReceivesIpv4() throws Exception {
        try (Fixture fixture = fixture(new InetSocketAddress(0))) {
            assertReceivesFrom(fixture, StandardProtocolFamily.INET, "127.0.0.1");
        }
    }

    @Test
    void wildcardEphemeralEndpointReceivesIpv4AndIpv6() throws Exception {
        assumeTrue(ipv6LoopbackAvailable(), "IPv6 loopback is not available.");

        try (Fixture fixture = fixture(new InetSocketAddress(0))) {
            assertReceivesFrom(fixture, StandardProtocolFamily.INET, "127.0.0.1");
            assertReceivesFrom(fixture, StandardProtocolFamily.INET6, "::1");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"::", "::1"})
    void configuredIpv6EndpointReceivesIpv6(String host) throws Exception {
        assumeTrue(ipv6LoopbackAvailable(), "IPv6 loopback is not available.");

        try (Fixture fixture = fixture(new InetSocketAddress(InetAddress.getByName(host), 0))) {
            assertReceivesFrom(fixture, StandardProtocolFamily.INET6, "::1");
        }
    }

    @Test
    void occupiedFixedPortIsRejected() throws Exception {
        try (DatagramChannel blocker = DatagramChannel.open(StandardProtocolFamily.INET)) {
            blocker.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
            InetSocketAddress bindAddress = new InetSocketAddress(((InetSocketAddress) blocker.getLocalAddress()).getPort());

            UncheckedIOException failure = assertThrows(UncheckedIOException.class, () -> fixture(bindAddress).close());

            assertThat(failure.getCause(), instanceOf(BindException.class));
        }
    }

    @Test
    void emptyDatagramDoesNotDiscardFollowingDatagram() throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
             Fixture fixture = fixture(() -> {
             }, false, Runnable::run, true);
             DatagramChannel peer = DatagramChannel.open()) {
            peer.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            ByteBuffer packet = longHeader(0xc0, QuicVersion.QUIC_V1.versionNumber(), new byte[8]);
            ByteBuffer expected = packet.asReadOnlyBuffer();
            CompletableFuture<ByteBuffer> received = new CompletableFuture<>();
            doAnswer(invocation -> {
                ByteBuffer payload = invocation.getArgument(2);
                received.complete(ByteBuffer.allocate(payload.remaining()).put(payload.duplicate()).flip());
                return null;
            }).when(fixture.instance()).unmatchedQuicPacket(eq(peer.getLocalAddress()), eq(HeadersType.LONG), any());
            Future<?> reader = executor.submit(fixture.endpoint()::channelReadLoop);
            try {
                peer.send(ByteBuffer.allocate(0), fixture.endpoint().localAddress());
                assertThat(peer.send(packet, fixture.endpoint().localAddress()), is(expected.remaining()));

                assertThat(received.get(5, TimeUnit.SECONDS), is(expected));
                assertThat(fixture.endpoint().isClosed(), is(false));
            } finally {
                fixture.endpoint().close();
                reader.get(5, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void endpointCloseDrainsQueuedDatagramAccountingBeforeStoppedTaskCanRun() throws Exception {
        AtomicReference<Runnable> scheduledRead = new AtomicReference<>();
        try (Fixture fixture = fixture(() -> {
        }, false, scheduledRead::set);
             DatagramChannel peer = DatagramChannel.open();
             Selector selector = Selector.open()) {
            fixture.endpoint().channel().register(selector, SelectionKey.OP_READ);
            peer.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            ByteBuffer packet = longHeader(0xc0, QuicVersion.QUIC_V1.versionNumber(), new byte[8]);
            peer.send(packet, fixture.endpoint().localAddress());
            assertThat(selector.select(5000), is(1));
            selector.selectedKeys().clear();

            fixture.endpoint().channelReadLoop();

            assertThat(fixture.endpoint().buffered() > 0, is(true));
            assertThat(scheduledRead.get() != null, is(true));
            fixture.endpoint().close();
            assertThat(fixture.endpoint().buffered(), is(0));

            scheduledRead.get().run();
            assertThat(fixture.endpoint().buffered(), is(0));
        }
    }

    @Test
    void endpointSchedulerRejectionDrainsQueuedDatagramAccounting() throws Exception {
        try (Fixture fixture = fixture(() -> {
        }, false, _ -> {
            throw new RejectedExecutionException("test rejection");
        });
             DatagramChannel peer = DatagramChannel.open();
             Selector selector = Selector.open()) {
            fixture.endpoint().channel().register(selector, SelectionKey.OP_READ);
            peer.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            ByteBuffer packet = longHeader(0xc0, QuicVersion.QUIC_V1.versionNumber(), new byte[8]);
            peer.send(packet, fixture.endpoint().localAddress());
            assertThat(selector.select(5000), is(1));
            selector.selectedKeys().clear();

            fixture.endpoint().channelReadLoop();

            assertThat(fixture.endpoint().isClosed(), is(true));
            assertThat(fixture.endpoint().buffered(), is(0));
            verify(fixture.instance()).runtimeFailed(any(RejectedExecutionException.class));
        }
    }

    @Test
    void unsupportedVersionBypassesActiveConnectionId() {
        try (Fixture fixture = fixture()) {
            byte[] connectionId = new byte[8];
            QuicConnectionImpl connection = registerConnection(fixture, connectionId);
            ByteBuffer packet = longHeader(0x80, 0x0a0a0a0a, connectionId);
            UnmatchedDatagram datagram = UnmatchedDatagram.create(PEER, packet);

            fixture.endpoint().unmatchedQuicPacket(datagram, HeadersType.LONG, packet);

            verify(fixture.instance()).unmatchedQuicPacket(eq(PEER), eq(HeadersType.LONG), same(packet));
            verify(connection, never()).processIncoming(any(), any(), any(), any());
        }
    }

    @Test
    void availableVersionStillRematchesActiveConnectionId() {
        try (Fixture fixture = fixture()) {
            byte[] connectionId = new byte[8];
            QuicConnectionImpl connection = registerConnection(fixture, connectionId);
            ByteBuffer packet = longHeader(0xc0, QuicVersion.QUIC_V1.versionNumber(), connectionId);
            UnmatchedDatagram datagram = UnmatchedDatagram.create(PEER, packet);

            fixture.endpoint().unmatchedQuicPacket(datagram, HeadersType.LONG, packet);

            ArgumentCaptor<ByteBuffer> connectionIdCaptor = ArgumentCaptor.forClass(ByteBuffer.class);
            verify(connection).processIncoming(eq(PEER), connectionIdCaptor.capture(), eq(HeadersType.LONG), same(packet));
            verify(fixture.instance(), never()).unmatchedQuicPacket(any(), any(), any());
            assertThat(connectionIdCaptor.getValue(), is(ByteBuffer.wrap(connectionId)));
            assertThat(packet.position(), is(0));
        }
    }

    @Test
    void truncatedLongHeaderDoesNotDispatchToActiveConnection() {
        ByteBuffer packet = ByteBuffer.allocate(5)
                .put((byte) 0xc0)
                .putInt(QuicVersion.QUIC_V1.versionNumber())
                .flip();

        assertMalformedLongHeaderIsNotDispatched(packet);
    }

    @Test
    void oversizedLongHeaderConnectionIdDoesNotDispatchToActiveConnection() {
        int length = QuicConnectionId.MAX_CONNECTION_ID_LENGTH + 1;
        assertMalformedLongHeaderIsNotDispatched(longHeaderWithConnectionIdLength(length, length));
    }

    @Test
    void incompleteLongHeaderConnectionIdDoesNotDispatchToActiveConnection() {
        assertMalformedLongHeaderIsNotDispatched(longHeaderWithConnectionIdLength(8, 7));
    }

    @Test
    void statelessResetIngressDispatchesWithoutEndpointWarningOrError() throws Exception {
        Logger logger = Logger.getLogger(QuicEndpoint.class.getName());
        Level previousLevel = logger.getLevel();
        boolean previousUseParentHandlers = logger.getUseParentHandlers();
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);

        try (Fixture fixture = fixture();
             DatagramChannel peer = DatagramChannel.open();
             DatagramChannel otherPeer = DatagramChannel.open();
             Selector selector = Selector.open()) {
            fixture.endpoint().channel().register(selector, SelectionKey.OP_READ);
            peer.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            otherPeer.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            InetSocketAddress peerAddress = (InetSocketAddress) peer.getLocalAddress();
            QuicPacketReceiver receiver = mock(QuicPacketReceiver.class);
            byte[] token = new byte[16];
            token[0] = 1;
            fixture.endpoint().associateStatelessResetToken(
                    QuicPacketReceiver.PeerResetToken.create(token, peerAddress), receiver);
            sendStatelessReset(fixture, otherPeer, selector, token);
            verify(receiver, never()).processStatelessReset();

            sendStatelessReset(fixture, peer, selector, token);

            verify(receiver).processStatelessReset();
            assertThat(records.stream()
                               .noneMatch(record -> record.getLevel().intValue() >= Level.WARNING.intValue()),
                       is(true));
            assertThat(records.stream()
                               .noneMatch(record -> record.getMessage().contains("Processing stateless reset")),
                       is(true));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
            handler.close();
        }
    }

    @Test
    void removalIncludesResetTokenAssociatedWhileRoutesFreeze() throws Exception {
        try (Fixture fixture = fixture();
             DatagramChannel peer = DatagramChannel.open();
             Selector selector = Selector.open();
             ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            fixture.endpoint().channel().register(selector, SelectionKey.OP_READ);
            peer.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            InetSocketAddress peerAddress = (InetSocketAddress) peer.getLocalAddress();
            QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
            QuicEndpointRouteLifecycle lifecycle = registerConnection(fixture, connection, new byte[8]);
            List<QuicConnectionId> connectionIds = connection.connectionIds();
            byte[] token = new byte[16];
            token[0] = 2;
            QuicPacketReceiver.PeerResetToken resetToken =
                    QuicPacketReceiver.PeerResetToken.create(token, peerAddress);
            CountDownLatch freezeEntered = new CountDownLatch(1);
            CountDownLatch allowFreeze = new CountDownLatch(1);
            doAnswer(invocation -> {
                freezeEntered.countDown();
                assertThat(allowFreeze.await(5, TimeUnit.SECONDS), is(true));
                return new QuicConnectionImpl.EndpointRoutes(connectionIds, List.of(resetToken));
            }).when(connection).freezeEndpointRoutes();

            Future<?> removal = executor.submit(() -> fixture.endpoint().removeConnection(connection));
            assertThat(freezeEntered.await(5, TimeUnit.SECONDS), is(true));
            fixture.endpoint().associateStatelessResetToken(resetToken, connection);
            allowFreeze.countDown();
            removal.get(5, TimeUnit.SECONDS);

            sendStatelessReset(fixture, peer, selector, token);
            verify(connection, never()).processStatelessReset();
            assertThat(lifecycle.whenRemoved().toCompletableFuture().isDone(), is(true));
        }
    }

    @Test
    void closingAndDrainingReuseFrozenResetTokenRoutes() throws Exception {
        try (Fixture fixture = fixture();
             DatagramChannel peer = DatagramChannel.open();
             Selector selector = Selector.open();
             ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            fixture.endpoint().channel().register(selector, SelectionKey.OP_READ);
            peer.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            InetSocketAddress peerAddress = (InetSocketAddress) peer.getLocalAddress();
            QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
            QuicEndpointRouteLifecycle lifecycle = registerConnection(fixture, connection, new byte[8]);
            List<QuicConnectionId> connectionIds = connection.connectionIds();
            byte[] token = new byte[16];
            token[0] = 3;
            QuicPacketReceiver.PeerResetToken resetToken =
                    QuicPacketReceiver.PeerResetToken.create(token, peerAddress);
            QuicConnectionImpl.EndpointRoutes routes =
                    new QuicConnectionImpl.EndpointRoutes(connectionIds, List.of(resetToken));
            CountDownLatch freezeEntered = new CountDownLatch(1);
            CountDownLatch allowFreeze = new CountDownLatch(1);
            doAnswer(invocation -> {
                freezeEntered.countDown();
                assertThat(allowFreeze.await(5, TimeUnit.SECONDS), is(true));
                return routes;
            }).when(connection).freezeEndpointRoutes();
            QuicPathManager pathManager = mock(QuicPathManager.class);
            QuicPathManager.ClosingPath closingPath = mock(QuicPathManager.ClosingPath.class);
            when(connection.pathManager()).thenReturn(pathManager);
            when(pathManager.closingPath()).thenReturn(closingPath);
            when(connection.peerPtoMs()).thenReturn(10_000L);

            Future<?> closing = executor.submit(() -> fixture.endpoint().closing(connection, ByteBuffer.allocate(1)));
            assertThat(freezeEntered.await(5, TimeUnit.SECONDS), is(true));
            fixture.endpoint().associateStatelessResetToken(resetToken, connection);
            allowFreeze.countDown();
            closing.get(5, TimeUnit.SECONDS);

            fixture.endpoint().forgetStatelessResetToken(resetToken, connection);
            fixture.endpoint().draining(connection);
            sendStatelessReset(fixture, peer, selector, token);

            verify(connection, never()).processStatelessReset();
            assertThat(lifecycle.whenRemoved().toCompletableFuture().isDone(), is(true));
        }
    }

    @Test
    void closingTimerFailureRemovesTransferredRoutes() throws Exception {
        RuntimeException failure = new IllegalStateException("timer notification failed");
        try (Fixture fixture = fixture(() -> {
            throw failure;
        });
             DatagramChannel peer = DatagramChannel.open();
             Selector selector = Selector.open()) {
            fixture.endpoint().channel().register(selector, SelectionKey.OP_READ);
            peer.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            InetSocketAddress peerAddress = (InetSocketAddress) peer.getLocalAddress();
            byte[] connectionId = new byte[8];
            QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
            QuicEndpointRouteLifecycle lifecycle = registerConnection(fixture, connection, connectionId);
            List<QuicConnectionId> connectionIds = connection.connectionIds();
            byte[] token = new byte[16];
            token[0] = 4;
            QuicPacketReceiver.PeerResetToken resetToken =
                    QuicPacketReceiver.PeerResetToken.create(token, peerAddress);
            fixture.endpoint().associateStatelessResetToken(resetToken, connection);
            doReturn(new QuicConnectionImpl.EndpointRoutes(connectionIds, List.of(resetToken)))
                    .when(connection)
                    .freezeEndpointRoutes();
            QuicPathManager pathManager = mock(QuicPathManager.class);
            QuicPathManager.ClosingPath closingPath = mock(QuicPathManager.ClosingPath.class);
            when(connection.pathManager()).thenReturn(pathManager);
            when(pathManager.closingPath()).thenReturn(closingPath);
            when(closingPath.accepts(PEER)).thenReturn(true);
            when(connection.peerPtoMs()).thenReturn(1_000L);

            RuntimeException thrown = null;
            try {
                fixture.endpoint().closing(connection, ByteBuffer.allocate(1));
            } catch (RuntimeException e) {
                thrown = e;
            }

            assertThat(thrown, is(failure));
            assertThat(lifecycle.whenRemoved().toCompletableFuture().isCompletedExceptionally(), is(true));
            assertThat(fixture.endpoint().timer().nextDeadline(), is(Deadline.MAX));
            ByteBuffer packet = longHeader(0xc0, QuicVersion.QUIC_V1.versionNumber(), connectionId);
            UnmatchedDatagram datagram = UnmatchedDatagram.create(PEER, packet);
            fixture.endpoint().unmatchedQuicPacket(datagram, HeadersType.LONG, packet);
            verify(fixture.instance()).unmatchedQuicPacket(eq(PEER), eq(HeadersType.LONG), same(packet));
            verify(connection, never()).processIncoming(any(), any(), any(), any());

            sendStatelessReset(fixture, peer, selector, token);
            verify(connection, never()).processStatelessReset();
        }
    }

    @Test
    void drainingTimerFailureRemovesTransferredRoutesAndTokens() throws Exception {
        RuntimeException failure = new IllegalStateException("draining timer notification failed");
        AtomicInteger notifications = new AtomicInteger();
        try (Fixture fixture = fixture(() -> {
            if (notifications.incrementAndGet() == 2) {
                throw failure;
            }
        });
             DatagramChannel peer = DatagramChannel.open();
             Selector selector = Selector.open()) {
            fixture.endpoint().channel().register(selector, SelectionKey.OP_READ);
            peer.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            InetSocketAddress peerAddress = (InetSocketAddress) peer.getLocalAddress();
            byte[] connectionId = new byte[8];
            connectionId[0] = 16;
            byte[] token = new byte[16];
            token[0] = 16;
            QuicPacketReceiver.PeerResetToken resetToken =
                    QuicPacketReceiver.PeerResetToken.create(token, peerAddress);
            QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
            QuicEndpointRouteLifecycle lifecycle = registerConnection(fixture, connection, connectionId);
            AtomicInteger completions = new AtomicInteger();
            lifecycle.whenRemoved().whenComplete((result, throwable) -> completions.incrementAndGet());
            List<QuicConnectionId> connectionIds = connection.connectionIds();
            doReturn(new QuicConnectionImpl.EndpointRoutes(connectionIds, List.of(resetToken)))
                    .when(connection)
                    .freezeEndpointRoutes();
            fixture.endpoint().associateStatelessResetToken(resetToken, connection);
            QuicPathManager pathManager = mock(QuicPathManager.class);
            QuicPathManager.ClosingPath closingPath = mock(QuicPathManager.ClosingPath.class);
            when(connection.pathManager()).thenReturn(pathManager);
            when(pathManager.closingPath()).thenReturn(closingPath);
            when(connection.peerPtoMs()).thenReturn(1_000L);

            fixture.endpoint().closing(connection, ByteBuffer.allocate(1));
            QuicEndpoint.ClosedConnection predecessor = (QuicEndpoint.ClosedConnection)
                    fixture.endpoint().findQuicConnectionFor(PEER, ByteBuffer.wrap(connectionId), true);

            RuntimeException thrown = assertThrows(RuntimeException.class, () -> fixture.endpoint().draining(connection));

            assertThat(thrown, is(failure));
            assertThat(predecessor.handle(), is(Deadline.MAX));
            assertThat(lifecycle.whenRemoved().toCompletableFuture().isCompletedExceptionally(), is(true));
            assertThat(completions.get(), is(1));
            assertThat(fixture.endpoint().timer().nextDeadline(), is(Deadline.MAX));
            assertThat(fixture.endpoint().findQuicConnectionFor(PEER, ByteBuffer.wrap(connectionId), true),
                       is((QuicPacketReceiver) null));

            Logger logger = Logger.getLogger(QuicEndpoint.class.getName());
            Level previousLevel = logger.getLevel();
            boolean previousUseParentHandlers = logger.getUseParentHandlers();
            List<LogRecord> records = new ArrayList<>();
            Handler handler = new Handler() {
                @Override
                public void publish(LogRecord record) {
                    records.add(record);
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            };
            handler.setLevel(Level.ALL);
            logger.addHandler(handler);
            logger.setUseParentHandlers(false);
            logger.setLevel(Level.ALL);
            try {
                sendStatelessReset(fixture, peer, selector, token);
                assertThat(records.stream().anyMatch(record -> record.getMessage().contains("Not a stateless reset")),
                           is(true));
            } finally {
                logger.removeHandler(handler);
                logger.setLevel(previousLevel);
                logger.setUseParentHandlers(previousUseParentHandlers);
                handler.close();
            }
        }
    }

    @Test
    void blockedTimerNotificationDoesNotBlockWritesRoutesOrClose() throws Exception {
        BlockingNotifier notifier = new BlockingNotifier();
        try (Fixture fixture = fixture(notifier, true);
             ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            byte[] connectionId = new byte[8];
            connectionId[0] = 5;
            QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
            QuicEndpointRouteLifecycle lifecycle = registerConnection(fixture, connection, connectionId);
            List<QuicConnectionId> connectionIds = connection.connectionIds();
            doReturn(new QuicConnectionImpl.EndpointRoutes(connectionIds, List.of()))
                    .when(connection)
                    .freezeEndpointRoutes();
            QuicPathManager pathManager = mock(QuicPathManager.class);
            QuicPathManager.ClosingPath closingPath = mock(QuicPathManager.ClosingPath.class);
            when(connection.pathManager()).thenReturn(pathManager);
            when(pathManager.closingPath()).thenReturn(closingPath);
            when(connection.peerPtoMs()).thenReturn(1_000L);

            Future<?> closing = executor.submit(() -> fixture.endpoint().closing(connection, ByteBuffer.allocate(1)));
            assertThat(notifier.entered.await(5, TimeUnit.SECONDS), is(true));
            try {
                QuicPacketReceiver unrelated = mock(QuicPacketReceiver.class);
                byte[] unrelatedId = new byte[8];
                unrelatedId[0] = 6;
                Future<Boolean> routeUpdate = executor.submit(() ->
                        fixture.endpoint().addConnectionId(PeerConnectionId.create(unrelatedId), unrelated));
                assertThat(routeUpdate.get(5, TimeUnit.SECONDS), is(true));

                QuicPacketReceiver writer = mock(QuicPacketReceiver.class);
                Future<?> write = executor.submit(() ->
                        fixture.endpoint().pushDatagram(writer,
                                                        new InetSocketAddress(InetAddress.getLoopbackAddress(), 9),
                                                        ByteBuffer.wrap(new byte[] {1})));
                write.get(5, TimeUnit.SECONDS);
                verify(writer).datagramSent(any());

                Future<?> close = executor.submit(fixture.endpoint()::close);
                close.get(5, TimeUnit.SECONDS);
                assertThat(lifecycle.whenRemoved().toCompletableFuture().isDone(), is(false));
            } finally {
                notifier.release.countDown();
            }

            closing.get(5, TimeUnit.SECONDS);
            assertThat(lifecycle.whenRemoved().toCompletableFuture().isDone(), is(true));
            assertThat(fixture.endpoint().timer().nextDeadline(), is(Deadline.MAX));
        }
    }

    @Test
    void packetReceivedBeforeTimerArmExtendsClosingDeadline() throws Exception {
        BlockingNotifier notifier = new BlockingNotifier();
        try (Fixture fixture = fixture(notifier);
             ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            byte[] connectionId = new byte[8];
            connectionId[0] = 7;
            QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
            QuicEndpointRouteLifecycle lifecycle = registerConnection(fixture, connection, connectionId);
            List<QuicConnectionId> connectionIds = connection.connectionIds();
            doReturn(new QuicConnectionImpl.EndpointRoutes(connectionIds, List.of()))
                    .when(connection)
                    .freezeEndpointRoutes();
            QuicPathManager pathManager = mock(QuicPathManager.class);
            QuicPathManager.ClosingPath closingPath = mock(QuicPathManager.ClosingPath.class);
            when(connection.pathManager()).thenReturn(pathManager);
            when(pathManager.closingPath()).thenReturn(closingPath);
            when(connection.peerPtoMs()).thenReturn(1_000L);

            Future<?> closing = executor.submit(() -> fixture.endpoint().closing(connection, ByteBuffer.allocate(1)));
            assertThat(notifier.entered.await(5, TimeUnit.SECONDS), is(true));
            QuicEndpoint.ClosedConnection closedConnection;
            Deadline initialDeadline;
            try {
                closedConnection = (QuicEndpoint.ClosedConnection)
                        fixture.endpoint().findQuicConnectionFor(PEER, ByteBuffer.wrap(connectionId), true);
                initialDeadline = closedConnection.deadline();
                closedConnection.processIncoming(PEER,
                                                 ByteBuffer.wrap(connectionId),
                                                 HeadersType.SHORT,
                                                 ByteBuffer.allocate(1));
            } finally {
                notifier.release.countDown();
            }
            closing.get(5, TimeUnit.SECONDS);

            fixture.endpoint().timer().processEventsAndReturnNextDeadline(initialDeadline, Runnable::run);

            assertThat(fixture.endpoint().findQuicConnectionFor(PEER, ByteBuffer.wrap(connectionId), true),
                       is(closedConnection));
            assertThat(lifecycle.whenRemoved().toCompletableFuture().isDone(), is(false));
        }
    }

    @Test
    void drainingTransferWhileClosingTimerArmIsBlockedCancelsPredecessor() throws Exception {
        BlockingNotifier notifier = new BlockingNotifier();
        try (Fixture fixture = fixture(notifier);
             ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            byte[] connectionId = new byte[8];
            connectionId[0] = 12;
            QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
            QuicEndpointRouteLifecycle lifecycle = registerConnection(fixture, connection, connectionId);
            List<QuicConnectionId> connectionIds = connection.connectionIds();
            doReturn(new QuicConnectionImpl.EndpointRoutes(connectionIds, List.of()))
                    .when(connection)
                    .freezeEndpointRoutes();
            QuicPathManager pathManager = mock(QuicPathManager.class);
            QuicPathManager.ClosingPath closingPath = mock(QuicPathManager.ClosingPath.class);
            when(connection.pathManager()).thenReturn(pathManager);
            when(pathManager.closingPath()).thenReturn(closingPath);
            when(connection.peerPtoMs()).thenReturn(1_000L);

            Future<?> closing = executor.submit(() -> fixture.endpoint().closing(connection, ByteBuffer.allocate(1)));
            assertThat(notifier.entered.await(5, TimeUnit.SECONDS), is(true));
            QuicEndpoint.ClosedConnection predecessor = (QuicEndpoint.ClosedConnection)
                    fixture.endpoint().findQuicConnectionFor(PEER, ByteBuffer.wrap(connectionId), true);
            Future<?> draining = executor.submit(() -> fixture.endpoint().draining(connection));
            QuicPacketReceiver successor;
            try {
                long waitUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                do {
                    successor = fixture.endpoint().findQuicConnectionFor(PEER,
                                                                         ByteBuffer.wrap(connectionId),
                                                                         true);
                    if (successor != predecessor) {
                        break;
                    }
                    Thread.onSpinWait();
                } while (System.nanoTime() < waitUntil);
                assertThat(successor instanceof QuicEndpoint.DrainingConnection, is(true));
                assertThat(predecessor.handle(), is(Deadline.MAX));
            } finally {
                notifier.release.countDown();
            }

            closing.get(5, TimeUnit.SECONDS);
            draining.get(5, TimeUnit.SECONDS);
            assertThat(fixture.endpoint().findQuicConnectionFor(PEER, ByteBuffer.wrap(connectionId), true),
                       is(successor));
            assertThat(fixture.endpoint().timer().nextDeadline(),
                       is(((QuicEndpoint.ClosedConnection) successor).deadline()));
            assertThat(lifecycle.whenRemoved().toCompletableFuture().isDone(), is(false));
        }
    }

    @Test
    void staleClosingTimerCannotRemoveDrainingRoutes() {
        try (Fixture fixture = fixture()) {
            byte[] connectionId = new byte[8];
            connectionId[0] = 8;
            QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
            QuicEndpointRouteLifecycle lifecycle = registerConnection(fixture, connection, connectionId);
            List<QuicConnectionId> connectionIds = connection.connectionIds();
            doReturn(new QuicConnectionImpl.EndpointRoutes(connectionIds, List.of()))
                    .when(connection)
                    .freezeEndpointRoutes();
            QuicPathManager pathManager = mock(QuicPathManager.class);
            QuicPathManager.ClosingPath closingPath = mock(QuicPathManager.ClosingPath.class);
            when(connection.pathManager()).thenReturn(pathManager);
            when(pathManager.closingPath()).thenReturn(closingPath);
            when(connection.peerPtoMs()).thenReturn(10_000L);

            fixture.endpoint().closing(connection, ByteBuffer.allocate(1));
            QuicEndpoint.ClosedConnection closing = (QuicEndpoint.ClosedConnection)
                    fixture.endpoint().findQuicConnectionFor(PEER, ByteBuffer.wrap(connectionId), true);
            fixture.endpoint().draining(connection);
            QuicPacketReceiver draining =
                    fixture.endpoint().findQuicConnectionFor(PEER, ByteBuffer.wrap(connectionId), true);

            assertThat(closing.handle(), is(Deadline.MAX));
            assertThat(((QuicEndpoint.ClosedConnection) draining).closingPath(), sameInstance(closingPath));
            verify(pathManager, times(1)).closingPath();
            assertThat(fixture.endpoint().findQuicConnectionFor(PEER, ByteBuffer.wrap(connectionId), true),
                       is(draining));
            assertThat(lifecycle.whenRemoved().toCompletableFuture().isDone(), is(false));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shutdownWaitsForRoutePublicationThatAlreadyObservedOpenEndpoint(boolean abort) throws Exception {
        try (Fixture fixture = fixture();
             ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            byte[] connectionId = new byte[8];
            connectionId[0] = 9;
            QuicConnectionId route = PeerConnectionId.create(connectionId);
            QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
            QuicEndpointRouteLifecycle lifecycle = spy(new QuicEndpointRouteLifecycle(connection));
            CountDownLatch publicationEntered = new CountDownLatch(1);
            CountDownLatch allowPublication = new CountDownLatch(1);
            doAnswer(invocation -> {
                publicationEntered.countDown();
                assertThat(allowPublication.await(5, TimeUnit.SECONDS), is(true));
                return invocation.callRealMethod();
            }).when(lifecycle).register(connection, 1);
            when(connection.connectionIds()).thenReturn(List.of(route));
            doReturn(new QuicConnectionImpl.EndpointRoutes(List.of(route), List.of()))
                    .when(connection)
                    .freezeEndpointRoutes();
            when(connection.isOpen()).thenReturn(true);
            when(connection.routeLifecycle()).thenReturn(lifecycle);
            when(connection.withStreamDispatchLock(any())).thenAnswer(invocation ->
                    invocation.<BooleanSupplier>getArgument(0).getAsBoolean());
            when(connection.withConnectionIdLock(any())).thenAnswer(invocation ->
                    invocation.<BooleanSupplier>getArgument(0).getAsBoolean());

            Future<?> registration = executor.submit(() -> fixture.endpoint().registerNewConnection(connection));
            assertThat(publicationEntered.await(5, TimeUnit.SECONDS), is(true));
            Future<?> shutdown = executor.submit(() -> {
                if (abort) {
                    fixture.endpoint().abort(new IllegalStateException("test endpoint abort"));
                } else {
                    fixture.endpoint().close();
                }
            });
            Future<Boolean> retirement = executor.submit(() ->
                    fixture.endpoint().removeConnectionId(route, connection));
            try {
                long waitUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!fixture.endpoint().isClosed() && System.nanoTime() < waitUntil) {
                    Thread.onSpinWait();
                }
                assertThat(fixture.endpoint().isClosed(), is(true));
                assertThat(shutdown.isDone(), is(false));
            } finally {
                allowPublication.countDown();
            }

            registration.get(5, TimeUnit.SECONDS);
            assertThat(retirement.get(5, TimeUnit.SECONDS), is(false));
            shutdown.get(5, TimeUnit.SECONDS);

            assertThat(lifecycle.whenRemoved().toCompletableFuture().isDone(), is(true));
            assertThat(fixture.endpoint().findQuicConnectionFor(PEER, ByteBuffer.wrap(connectionId), true),
                       is((QuicPacketReceiver) null));
        }
    }

    @Test
    void registrationRejectsConnectionIdCollisionAndCountsDistinctOwnedRoutes() {
        try (Fixture fixture = fixture()) {
            QuicConnectionId shared = PeerConnectionId.create(new byte[] {13});
            QuicConnectionId firstUnique = PeerConnectionId.create(new byte[] {14});
            QuicConnectionId secondUnique = PeerConnectionId.create(new byte[] {15});
            QuicConnectionImpl first = mock(QuicConnectionImpl.class);
            QuicEndpointRouteLifecycle firstLifecycle =
                    prepareConnectionForRegistration(first, List.of(shared, shared, firstUnique));
            fixture.endpoint().registerNewConnection(first);
            QuicConnectionImpl second = mock(QuicConnectionImpl.class);
            QuicEndpointRouteLifecycle secondLifecycle =
                    prepareConnectionForRegistration(second, List.of(shared, secondUnique));

            assertThrows(IllegalStateException.class, () -> fixture.endpoint().registerNewConnection(second));

            assertThat(fixture.endpoint().findQuicConnectionFor(PEER, shared.asReadOnlyBuffer(), true), is(first));
            assertThat(fixture.endpoint().findQuicConnectionFor(PEER, secondUnique.asReadOnlyBuffer(), true),
                       is((QuicPacketReceiver) null));
            assertThat(fixture.endpoint().removeConnectionId(firstUnique, first), is(true));
            assertThat(fixture.endpoint().removeConnectionId(shared, first), is(false));
            assertThat(fixture.endpoint().findQuicConnectionFor(PEER, shared.asReadOnlyBuffer(), true), is(first));
            assertThat(firstLifecycle.whenRemoved().toCompletableFuture().isDone(), is(false));
            assertThat(secondLifecycle.whenRemoved().toCompletableFuture().isDone(), is(true));
        }
    }

    @Test
    void closingDeadlineStartsWhenRouteLockCanPublish() throws Exception {
        try (Fixture fixture = fixture();
             ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            byte[] connectionId = new byte[8];
            connectionId[0] = 10;
            QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
            registerConnection(fixture, connection, connectionId);
            List<QuicConnectionId> connectionIds = connection.connectionIds();
            CountDownLatch routesFrozen = new CountDownLatch(1);
            doAnswer(invocation -> {
                routesFrozen.countDown();
                return new QuicConnectionImpl.EndpointRoutes(connectionIds, List.of());
            }).when(connection).freezeEndpointRoutes();
            QuicPathManager pathManager = mock(QuicPathManager.class);
            QuicPathManager.ClosingPath closingPath = mock(QuicPathManager.ClosingPath.class);
            when(connection.pathManager()).thenReturn(pathManager);
            when(pathManager.closingPath()).thenReturn(closingPath);
            when(connection.peerPtoMs()).thenReturn(25L);

            byte[] blockerId = new byte[8];
            blockerId[0] = 11;
            QuicConnectionId blockerRoute = PeerConnectionId.create(blockerId);
            QuicConnectionImpl blocker = mock(QuicConnectionImpl.class);
            QuicEndpointRouteLifecycle blockerLifecycle = spy(new QuicEndpointRouteLifecycle(blocker));
            CountDownLatch routeLockHeld = new CountDownLatch(1);
            CountDownLatch releaseRouteLock = new CountDownLatch(1);
            doAnswer(invocation -> {
                routeLockHeld.countDown();
                assertThat(releaseRouteLock.await(5, TimeUnit.SECONDS), is(true));
                return invocation.callRealMethod();
            }).when(blockerLifecycle).register(blocker, 1);
            when(blocker.connectionIds()).thenReturn(List.of(blockerRoute));
            doReturn(new QuicConnectionImpl.EndpointRoutes(List.of(blockerRoute), List.of()))
                    .when(blocker)
                    .freezeEndpointRoutes();
            when(blocker.isOpen()).thenReturn(true);
            when(blocker.routeLifecycle()).thenReturn(blockerLifecycle);
            when(blocker.withStreamDispatchLock(any())).thenAnswer(invocation ->
                    invocation.<BooleanSupplier>getArgument(0).getAsBoolean());
            when(blocker.withConnectionIdLock(any())).thenAnswer(invocation ->
                    invocation.<BooleanSupplier>getArgument(0).getAsBoolean());

            Future<?> blockerRegistration = executor.submit(() -> fixture.endpoint().registerNewConnection(blocker));
            assertThat(routeLockHeld.await(5, TimeUnit.SECONDS), is(true));
            Future<?> closing = executor.submit(() -> fixture.endpoint().closing(connection, ByteBuffer.allocate(1)));
            assertThat(routesFrozen.await(5, TimeUnit.SECONDS), is(true));
            try {
                try {
                    closing.get(200, TimeUnit.MILLISECONDS);
                    throw new AssertionError("Closing route published while the route lock was held");
                } catch (TimeoutException expected) {
                    // Expected: route publication is waiting behind the blocked registration.
                }
            } finally {
                Deadline releaseTime = TimeSource.now();
                releaseRouteLock.countDown();
                blockerRegistration.get(5, TimeUnit.SECONDS);
                closing.get(5, TimeUnit.SECONDS);
                QuicPacketReceiver receiver =
                        fixture.endpoint().findQuicConnectionFor(PEER, ByteBuffer.wrap(connectionId), true);
                assertThat(receiver instanceof QuicEndpoint.ClosedConnection, is(true));
                assertThat(((QuicEndpoint.ClosedConnection) receiver).deadline().isAfter(releaseTime), is(true));
            }
        }
    }

    private static Fixture fixture() {
        return fixture(() -> {
        });
    }

    private static Fixture fixture(InetSocketAddress address) {
        return fixture(() -> {
        }, false, Runnable::run, false, address);
    }

    private static Fixture fixture(Runnable timerNotifier) {
        return fixture(timerNotifier, false);
    }

    private static Fixture fixture(Runnable timerNotifier, boolean sendAsync) {
        return fixture(timerNotifier, sendAsync, Runnable::run);
    }

    private static Fixture fixture(Runnable timerNotifier, boolean sendAsync, Executor executor) {
        return fixture(timerNotifier, sendAsync, executor, false);
    }

    private static Fixture fixture(Runnable timerNotifier, boolean sendAsync, Executor executor, boolean blocking) {
        return fixture(timerNotifier,
                       sendAsync,
                       executor,
                       blocking,
                       new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
    }

    private static Fixture fixture(Runnable timerNotifier,
                                   boolean sendAsync,
                                   Executor executor,
                                   boolean blocking,
                                   InetSocketAddress address) {
        QuicConfig userConfig = QuicConfig.create();
        QuicRuntimeConfig defaults = QuicRuntimeConfig.create(userConfig);
        QuicRuntimeConfig.Endpoint defaultEndpoint = defaults.endpoint();
        QuicRuntimeConfig runtimeConfig = new QuicRuntimeConfig(
                userConfig,
                new QuicRuntimeConfig.Endpoint(defaultEndpoint.channelType(),
                                               defaultEndpoint.selectorThreading(),
                                               defaultEndpoint.pollerUsePlatformThreads(),
                                               defaultEndpoint.maxEndpoints(),
                                               sendAsync,
                                               defaultEndpoint.maxBufferedHigh(),
                                               defaultEndpoint.maxBufferedLow(),
                                               defaultEndpoint.useDirectBufferPool(),
                                               defaultEndpoint.defaultDatagramSize()),
                defaults.recovery(),
                defaults.transportParameters(),
                defaults.confidentialityLimits());
        QuicInstance instance = mock(QuicInstance.class);
        when(instance.quicConfig()).thenReturn(userConfig);
        when(instance.executor()).thenReturn(executor);
        when(instance.isClient()).thenReturn(false);
        when(instance.isVersionAvailable(QuicVersion.QUIC_V1)).thenReturn(true);
        when(instance.availableVersions()).thenReturn(List.of(QuicVersion.QUIC_V1));
        when(instance.instanceId()).thenReturn("ingress-test");
        QuicEndpointFactory factory = QuicEndpointFactory.create();
        QuicTimerQueue timer = new QuicTimerQueue(timerNotifier, () -> "ingress-test-timer");
        QuicEndpoint endpoint = blocking
                ? factory.createVirtualThreadedEndpoint(instance, runtimeConfig, "ingress-test", address, timer)
                : factory.createSelectableEndpoint(instance, runtimeConfig, "ingress-test", address, timer);
        return new Fixture(instance, endpoint);
    }

    private static void assertReceivesFrom(Fixture fixture, StandardProtocolFamily family, String host) throws Exception {
        try (DatagramChannel peer = DatagramChannel.open(family);
             Selector selector = Selector.open()) {
            InetAddress loopback = InetAddress.getByName(host);
            peer.bind(new InetSocketAddress(loopback, 0));
            fixture.endpoint().channel().register(selector, SelectionKey.OP_READ);
            ByteBuffer packet = longHeader(0xc0, QuicVersion.QUIC_V1.versionNumber(), new byte[8]);
            ByteBuffer expected = packet.asReadOnlyBuffer();
            CompletableFuture<ByteBuffer> received = new CompletableFuture<>();
            doAnswer(invocation -> {
                ByteBuffer payload = invocation.getArgument(2);
                received.complete(ByteBuffer.allocate(payload.remaining()).put(payload.duplicate()).flip());
                return null;
            }).when(fixture.instance()).unmatchedQuicPacket(eq(peer.getLocalAddress()), eq(HeadersType.LONG), any());
            int endpointPort = ((InetSocketAddress) fixture.endpoint().localAddress()).getPort();

            assertThat(peer.send(packet, new InetSocketAddress(loopback, endpointPort)), is(expected.remaining()));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            int selected = 0;
            while (selected == 0) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    break;
                }
                selected = selector.select(Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
            }
            assertThat("Timed out waiting for a datagram from " + host, selected, is(1));
            selector.selectedKeys().clear();
            fixture.endpoint().channelReadLoop();

            assertThat(received.get(5, TimeUnit.SECONDS), is(expected));
            assertThat(fixture.endpoint().isClosed(), is(false));
        }
    }

    private static boolean ipv6LoopbackAvailable() {
        try (DatagramChannel peer = DatagramChannel.open(StandardProtocolFamily.INET6)) {
            peer.bind(new InetSocketAddress(InetAddress.getByName("::1"), 0));
            return true;
        } catch (IOException | UnsupportedOperationException e) {
            return false;
        }
    }

    private static QuicConnectionImpl registerConnection(Fixture fixture, byte[] connectionId) {
        QuicConnectionImpl connection = mock(QuicConnectionImpl.class);
        registerConnection(fixture, connection, connectionId);
        return connection;
    }

    private static QuicEndpointRouteLifecycle registerConnection(Fixture fixture,
                                                                  QuicConnectionImpl connection,
                                                                  byte[] connectionId) {
        List<QuicConnectionId> connectionIds = List.of(PeerConnectionId.create(connectionId));
        QuicEndpointRouteLifecycle lifecycle = prepareConnectionForRegistration(connection, connectionIds);
        fixture.endpoint().registerNewConnection(connection);
        return lifecycle;
    }

    private static QuicEndpointRouteLifecycle prepareConnectionForRegistration(QuicConnectionImpl connection,
                                                                                List<QuicConnectionId> connectionIds) {
        when(connection.connectionIds()).thenReturn(connectionIds);
        doReturn(new QuicConnectionImpl.EndpointRoutes(connectionIds, List.of()))
                .when(connection)
                .freezeEndpointRoutes();
        when(connection.accepts(PEER)).thenReturn(true);
        when(connection.isOpen()).thenReturn(true);
        QuicEndpointRouteLifecycle lifecycle = new QuicEndpointRouteLifecycle(connection);
        when(connection.routeLifecycle()).thenReturn(lifecycle);
        when(connection.withStreamDispatchLock(any())).thenAnswer(invocation ->
                invocation.<BooleanSupplier>getArgument(0).getAsBoolean());
        when(connection.withConnectionIdLock(any())).thenAnswer(invocation ->
                invocation.<BooleanSupplier>getArgument(0).getAsBoolean());
        return lifecycle;
    }

    private static void sendStatelessReset(Fixture fixture,
                                           DatagramChannel peer,
                                           Selector selector,
                                           byte[] token) throws Exception {
        ByteBuffer reset = ByteBuffer.allocate(21);
        reset.put((byte) 0x40);
        reset.putInt(0);
        reset.put(token);
        reset.flip();
        peer.send(reset, fixture.endpoint().localAddress());
        assertThat(selector.select(5000), is(1));
        selector.selectedKeys().clear();
        fixture.endpoint().channelReadLoop();
    }

    private static void assertMalformedLongHeaderIsNotDispatched(ByteBuffer packet) {
        try (Fixture fixture = fixture()) {
            QuicConnectionImpl connection = registerConnection(fixture, new byte[8]);
            UnmatchedDatagram datagram = UnmatchedDatagram.create(PEER, packet);

            fixture.endpoint().unmatchedQuicPacket(datagram, HeadersType.LONG, packet);

            verify(fixture.instance()).unmatchedQuicPacket(eq(PEER), eq(HeadersType.LONG), same(packet));
            verify(connection, never()).processIncoming(any(), any(), any(), any());
            assertThat(packet.position(), is(0));
        }
    }

    private static ByteBuffer longHeaderWithConnectionIdLength(int declaredLength, int availableBytes) {
        return ByteBuffer.allocate(6 + availableBytes)
                .put((byte) 0xc0)
                .putInt(QuicVersion.QUIC_V1.versionNumber())
                .put((byte) declaredLength)
                .put(new byte[availableBytes])
                .flip();
    }

    private static ByteBuffer longHeader(int firstByte, int version, byte[] destinationId) {
        ByteBuffer packet = ByteBuffer.allocate(QuicConfigSupport.MINIMUM_DATAGRAM_SIZE);
        packet.put((byte) firstByte);
        packet.putInt(version);
        packet.put((byte) destinationId.length);
        packet.put(destinationId);
        packet.put((byte) 8);
        packet.put(new byte[8]);
        packet.position(packet.limit());
        return packet.flip();
    }

    private static final class BlockingNotifier implements Runnable {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public void run() {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Timed out waiting to release timer notification");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

    private record Fixture(QuicInstance instance, QuicEndpoint endpoint) implements AutoCloseable {
        @Override
        public void close() {
            endpoint.close();
        }
    }
}
