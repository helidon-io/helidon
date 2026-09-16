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
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.common.socket.SocketContext;
import io.helidon.quic.QuicEndpoint.QuicDatagram;
import io.helidon.quic.frame.ConnectionCloseFrame;
import io.helidon.quic.packet.QuicPacket.HeadersType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@Isolated("Changes JUL logger levels and handlers")
@ResourceLock("java.util.logging")
class QuicEndpointLoggingTest {
    @Test
    void connectionCloseSummaryDoesNotExposeReason() {
        ConnectionCloseFrame frame = ConnectionCloseFrame.create(1, 0, "secret");

        assertThat(frame.toString(), containsString("reasonLength=6"));
        assertThat(frame.toString(), not(containsString("secret")));
    }

    @Test
    void rawDatagramLoggingRequiresExplicitUnsafeOptIn() {
        try (var logs = new TestLogCapture(Level.FINER)) {
            List<String> messages = logs.messages;
            ByteBuffer payload = ByteBuffer.wrap("secret".getBytes(StandardCharsets.UTF_8));
            InetSocketAddress peer = new InetSocketAddress("127.0.0.1", 443);

            QuicEndpoint.logDatagram(false, "send", "socket connection", peer, payload);

            assertThat(messages.size(), is(1));
            assertThat(messages.getFirst(), containsString("send datagram (6 bytes)"));
            assertThat(messages.getFirst(), not(containsString("secret")));
            assertThat(messages.getFirst(), not(containsString("73 65 63 72 65 74")));

            messages.clear();
            QuicEndpoint.logDatagram(true, "send", "socket connection", peer, payload);

            assertThat(messages.size(), is(2));
            assertThat(messages.stream().anyMatch(message -> message.contains("UNSAFE raw send datagram")), is(true));
            assertThat(messages.stream().anyMatch(message -> message.contains("73 65 63 72 65 74")), is(true));
            assertThat(messages.stream().anyMatch(message -> message.contains("secret")), is(true));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"INFO", "FINE", "FINER"})
    void queuedDatagramsAreWrittenInOrderWithOptionalDiagnostics(String level) throws Exception {
        List<Runnable> tasks = new ArrayList<>();
        List<ByteBuffer> sent = new ArrayList<>();
        QuicPacketReceiver receiver = mock(QuicPacketReceiver.class);
        doAnswer(invocation -> {
            QuicDatagram datagram = invocation.getArgument(0);
            sent.add(datagram.payload());
            return null;
        }).when(receiver).datagramSent(any(QuicDatagram.class));

        try (var logs = new TestLogCapture(Level.parse(level));
             DatagramChannel peerChannel = DatagramChannel.open()) {
            peerChannel.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            QuicEndpoint endpoint = endpoint(tasks::add);
            try {
                SocketAddress peer = peerChannel.getLocalAddress();
                ByteBuffer first = ByteBuffer.wrap(new byte[] {1, 2});
                ByteBuffer second = ByteBuffer.wrap(new byte[] {3, 4, 5});

                endpoint.pushDatagram(receiver, peer, first);
                endpoint.pushDatagram(receiver, peer, second);

                assertThat(first.position(), is(0));
                assertThat(second.position(), is(0));
                assertThat(sent, empty());
                assertThat(endpoint.writeQueueIsEmpty(), is(false));
                assertThat(tasks.size(), is(1));
                List<String> queuedMessages = logs.messages.stream()
                        .filter(message -> message.contains("added to write queue"))
                        .toList();
                if (level.equals("INFO")) {
                    assertThat(queuedMessages, empty());
                } else {
                    assertThat(queuedMessages.size(), is(2));
                    assertThat(queuedMessages,
                               hasItem(containsString("datagram [2 bytes] added to write queue, queue size 1")));
                    assertThat(queuedMessages,
                               hasItem(containsString("datagram [3 bytes] added to write queue, queue size 2")));
                }

                tasks.removeFirst().run();

                assertThat(sent, contains(sameInstance(first), sameInstance(second)));
                assertThat(first.remaining(), is(0));
                assertThat(second.remaining(), is(0));
                assertThat(endpoint.writeQueueIsEmpty(), is(true));
                verify(receiver, never()).datagramDropped(any(QuicDatagram.class));
                verify(receiver, never()).datagramDiscarded(any(QuicDatagram.class));
            } finally {
                endpoint.close();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"OFF", "INFO", "FINE", "FINER"})
    void matchedDatagramReceptionOnlyReadsSocketIdsForEnabledDiagnostics(String level) throws Exception {
        QuicPacketReceiver receiver = diagnosticReceiver();
        try (var logs = new TestLogCapture(Level.parse(level));
             QuicEndpoint endpoint = receivingEndpoint();
             DatagramChannel peerChannel = DatagramChannel.open()) {
            peerChannel.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            var peer = (InetSocketAddress) peerChannel.getLocalAddress();
            QuicConnectionId connectionId = endpoint.idFactory().newConnectionId();
            assertThat(endpoint.addConnectionId(connectionId, receiver), is(true));
            ByteBuffer packet = ByteBuffer.allocate(32)
                    .put((byte) 0x40)
                    .put(connectionId.bytes())
                    .position(24)
                    .put("secret".getBytes(StandardCharsets.UTF_8))
                    .clear();
            ByteBuffer expected = packet.asReadOnlyBuffer();

            receive(endpoint, peerChannel, packet);

            ArgumentCaptor<ByteBuffer> received = ArgumentCaptor.forClass(ByteBuffer.class);
            verify(receiver).processIncoming(eq(peer),
                                             eq(ByteBuffer.wrap(connectionId.bytes())),
                                             eq(HeadersType.SHORT),
                                             received.capture());
            assertThat(received.getValue(), is(expected));
            assertThat(received.getValue().position(), is(0));
            assertThat(received.getValue().limit(), is(expected.limit()));
            verify(receiver, never()).processStatelessReset();
            assertReceiveLogging(logs, level, receiver, expected.remaining(), peer);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"OFF", "INFO", "FINE", "FINER"})
    void statelessResetReceptionOnlyReadsSocketIdsForEnabledDiagnostics(String level) throws Exception {
        QuicPacketReceiver receiver = diagnosticReceiver();
        try (var logs = new TestLogCapture(Level.parse(level));
             QuicEndpoint endpoint = receivingEndpoint();
             DatagramChannel peerChannel = DatagramChannel.open()) {
            peerChannel.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            var peer = (InetSocketAddress) peerChannel.getLocalAddress();
            byte[] token = "secret-reset-key".getBytes(StandardCharsets.UTF_8);
            endpoint.associateStatelessResetToken(QuicPacketReceiver.PeerResetToken.create(token, peer), receiver);
            ByteBuffer packet = ByteBuffer.allocate(21).put((byte) 0x40).putInt(0).put(token).flip();

            receive(endpoint, peerChannel, packet);

            verify(receiver).processStatelessReset();
            verify(receiver, never()).processIncoming(any(), any(), any(), any());
            assertReceiveLogging(logs, level, receiver, 21, peer);
        }
    }

    private static QuicPacketReceiver diagnosticReceiver() {
        QuicPacketReceiver receiver = mock(QuicPacketReceiver.class, withSettings().extraInterfaces(SocketContext.class));
        var socketContext = (SocketContext) receiver;
        when(socketContext.socketId()).thenReturn("receive-socket");
        when(socketContext.childSocketId()).thenReturn("receive-connection");
        when(receiver.accepts(any())).thenReturn(true);
        return receiver;
    }

    private static void receive(QuicEndpoint endpoint, DatagramChannel peer, ByteBuffer packet) throws Exception {
        try (Selector selector = Selector.open()) {
            endpoint.channel().register(selector, SelectionKey.OP_READ);
            int packetSize = packet.remaining();
            assertThat(peer.send(packet, endpoint.localAddress()), is(packetSize));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            int selected = 0;
            while (selected == 0) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    break;
                }
                selected = selector.select(Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
            }
            assertThat("Timed out waiting for the receive-test datagram", selected, is(1));
            selector.selectedKeys().clear();
            endpoint.channelReadLoop();
            assertThat(endpoint.isClosed(), is(false));
        }
    }

    private static void assertReceiveLogging(TestLogCapture logs,
                                              String level,
                                              QuicPacketReceiver receiver,
                                              int packetSize,
                                              InetSocketAddress peer) {
        var socketContext = (SocketContext) receiver;
        if (level.equals("OFF") || level.equals("INFO")) {
            verify(socketContext, never()).socketId();
            verify(socketContext, never()).childSocketId();
            assertThat(logs.messages, empty());
            return;
        }
        verify(socketContext).socketId();
        verify(socketContext).childSocketId();
        List<String> summaries = logs.messages.stream()
                .filter(message -> message.startsWith("[receive-socket receive-connection] recv datagram"))
                .toList();
        assertThat(summaries, contains(allOf(containsString("recv datagram (" + packetSize + " bytes)"),
                                             containsString(":" + peer.getPort()))));
        List<String> raw = logs.messages.stream()
                .filter(message -> message.contains("UNSAFE raw recv datagram"))
                .toList();
        if (level.equals("FINER")) {
            assertThat(raw, contains(allOf(containsString("[receive-socket receive-connection]"),
                                           containsString("recv datagram (" + packetSize + " bytes)"),
                                           containsString(":" + peer.getPort()),
                                           containsString("73 65 63 72 65 74"),
                                           containsString("secret"))));
        } else {
            assertThat(raw, empty());
        }
    }

    private static QuicEndpoint receivingEndpoint() {
        QuicConfig userConfig = QuicConfig.builder().unsafeRawData(true).build();
        QuicInstance instance = mock(QuicInstance.class);
        when(instance.quicConfig()).thenReturn(userConfig);
        when(instance.executor()).thenReturn(Runnable::run);
        when(instance.isClient()).thenReturn(true);
        when(instance.instanceId()).thenReturn("diagnostics-read-test");
        return QuicEndpoint.QuicEndpointFactory.create()
                .createSelectableEndpoint(instance,
                                          QuicRuntimeConfig.create(userConfig),
                                          "diagnostics-read-test",
                                          new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                                          new QuicTimerQueue(() -> {
                                          }, () -> "diagnostics-read-test-timer"));
    }

    private static QuicEndpoint endpoint(Executor executor) {
        QuicConfig userConfig = QuicConfig.create();
        QuicRuntimeConfig defaults = QuicRuntimeConfig.create(userConfig);
        QuicRuntimeConfig.Endpoint endpoint = defaults.endpoint();
        QuicRuntimeConfig runtimeConfig = new QuicRuntimeConfig(
                userConfig,
                new QuicRuntimeConfig.Endpoint(endpoint.channelType(),
                                               endpoint.selectorThreading(),
                                               endpoint.pollerUsePlatformThreads(),
                                               endpoint.maxEndpoints(),
                                               true,
                                               endpoint.maxBufferedHigh(),
                                               endpoint.maxBufferedLow(),
                                               endpoint.useDirectBufferPool(),
                                               endpoint.defaultDatagramSize()),
                defaults.recovery(),
                defaults.transportParameters(),
                defaults.confidentialityLimits());
        QuicInstance instance = mock(QuicInstance.class);
        when(instance.quicConfig()).thenReturn(userConfig);
        when(instance.executor()).thenReturn(executor);
        when(instance.isClient()).thenReturn(true);
        when(instance.instanceId()).thenReturn("diagnostics-write-test");
        return QuicEndpoint.QuicEndpointFactory.create()
                .createVirtualThreadedEndpoint(instance,
                                               runtimeConfig,
                                               "diagnostics-write-test",
                                               new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                                               new QuicTimerQueue(() -> {
                                               }, () -> "diagnostics-write-test-timer"));
    }

    private static final class TestLogCapture implements AutoCloseable {
        private final Logger logger = Logger.getLogger(QuicEndpoint.class.getName());
        private final Level previousLevel = logger.getLevel();
        private final boolean previousUseParentHandlers = logger.getUseParentHandlers();
        private final List<String> messages = new ArrayList<>();
        private final Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };

        private TestLogCapture(Level level) {
            handler.setLevel(Level.ALL);
            logger.addHandler(handler);
            logger.setUseParentHandlers(false);
            logger.setLevel(level);
        }

        @Override
        public void close() {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
            handler.close();
        }
    }
}
