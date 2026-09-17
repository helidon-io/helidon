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

import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.tls.Tls;
import io.helidon.quic.QuicTLSEngine.HandshakeState;
import io.helidon.quic.QuicTLSEngine.KeySpace;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class QuicMalformedDatagramTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int DATAGRAM_SIZE = 1200;
    private static final int UNSUPPORTED_VERSION = 0x0a0a0a0a;
    private static final String APPLICATION_PROTOCOL = "quic-test";

    @ParameterizedTest
    @EnumSource(value = QuicVersion.class, names = {"QUIC_V1", "QUIC_V2"})
    void shouldPreserveEstablishedConnectionAfterTruncatedCoalescedHeaderFromAnotherSource(QuicVersion version)
            throws Exception {
        assertEstablishedConnectionSurvives(version, connection -> {
            byte[] destinationId = connection.localConnectionId().orElseThrow().bytes();
            return malformedDatagram(version, destinationId);
        });
    }

    @ParameterizedTest
    @EnumSource(value = QuicVersion.class, names = {"QUIC_V1", "QUIC_V2"})
    void shouldPreserveEstablishedConnectionAfterRetrySourceIdOverlapsIntegrityTagFromAnotherSource(QuicVersion version)
            throws Exception {
        assertEstablishedConnectionSurvives(version, connection -> {
            QuicConnectionId admittedId = connection.initialConnectionId().orElseThrow();
            assertThat("admitted Initial destination ID remains a routing alias", connection.connectionIds(),
                       hasItem(admittedId));
            assertThat("Retry integrity uses the admitted Initial destination ID", connection.originalServerConnId(),
                       is(admittedId));
            assertThat("public Retry integrity keys remain available",
                       connection.tlsEngine().keysAvailable(KeySpace.RETRY), is(true));
            return malformedRetryDatagram(version, admittedId);
        });
    }

    private static void assertEstablishedConnectionSurvives(QuicVersion version,
                                                            Function<QuicServerConnection, byte[]> datagramFactory)
            throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        QuicConfig config = QuicConfig.builder()
                .availableVersions(List.of(version))
                .idleTimeout(Duration.ZERO)
                .buildPrototype();
        Tls serverTls = Tls.builder()
                .privateKey(QuicTlsRfc8448Vectors.rsaPrivateKey())
                .privateKeyCertChain(List.of(QuicTlsRfc8448Vectors.rsaCertificate()))
                .build();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try (var client = QuicClientRuntime.builder()
                .executor(executor)
                .tls(Tls.builder().trustAll(true).build())
                .quicConfig(config)
                .build();
                var source = new DatagramSocket(new InetSocketAddress(loopback, 0))) {
            QuicServerRuntime server = QuicServerRuntime.builder()
                    // Keep endpoint dispatch and connection packet processing synchronous for the wire marker below.
                    .executor(Runnable::run)
                    .tls(serverTls)
                    .quicConfig(config)
                    .applicationProtocols(List.of(APPLICATION_PROTOCOL))
                    .bindAddress(new InetSocketAddress(loopback, 0))
                    .build();
            try {
                server.start();
                var accepted = server.accept();
                InetSocketAddress serverAddress = server.localAddress();
                // The RFC 8448 certificate identifies "rsa"; endpoint verification remains enabled.
                QuicClientConnection clientConnection = client.createConnection(serverAddress,
                                                                                 "rsa",
                                                                                 serverAddress.getPort(),
                                                                                 new String[] {APPLICATION_PROTOCOL});
                clientConnection.startHandshake().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                var serverConnection = (QuicServerConnection) accepted.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                var clientSession = new QuicApplicationSession(clientConnection, TIMEOUT);
                var serverSession = new QuicApplicationSession(serverConnection, TIMEOUT);

                assertExchange(executor, clientSession, serverSession, "before malformed datagram");
                assertThat("client negotiated the configured version", clientConnection.quicVersion(), is(version));
                assertThat("server negotiated the configured version", serverConnection.quicVersion(), is(version));
                assertThat("server handshake is confirmed", serverConnection.tlsEngine().handshakeState(),
                           is(HandshakeState.HANDSHAKE_CONFIRMED));
                assertThat("server has naturally discarded Initial keys", serverConnection.tlsEngine().keysAvailable(
                        KeySpace.INITIAL), is(false));
                assertThat("malformed datagram comes from a separate UDP source", source.getLocalSocketAddress(),
                           not(serverConnection.peerAddress()));
                byte[] destinationId = serverConnection.localConnectionId().orElseThrow().bytes();

                byte[] malformed = datagramFactory.apply(serverConnection);
                source.send(new DatagramPacket(malformed, malformed.length, serverAddress));
                awaitReceiveMarker(source, serverAddress, destinationId, version);

                assertThat("server remains unterminated after malformed datagram processing",
                           serverConnection.termination(), is(Optional.empty()));
                assertThat("server remains open after malformed datagram processing", serverConnection.isOpen(), is(true));
                assertExchange(executor, clientSession, serverSession, "after malformed datagram");
            } finally {
                assertThat("server selector stops", server.close(TIMEOUT), is(true));
            }
        } finally {
            executor.shutdownNow();
            assertThat("client and stream tasks stop",
                       executor.awaitTermination(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                       is(true));
        }
    }

    private static byte[] malformedDatagram(QuicVersion version, byte[] destinationId) {
        int initialFlags = switch (version) {
            case QUIC_V1 -> 0xc0;
            case QUIC_V2 -> 0xd0;
        };
        ByteBuffer datagram = ByteBuffer.allocate(DATAGRAM_SIZE);
        datagram.put((byte) initialFlags).putInt(version.versionNumber());
        datagram.put((byte) destinationId.length).put(destinationId);
        datagram.put((byte) 0); // Empty source connection ID.
        datagram.put((byte) 0); // Empty Initial token.
        int tailOffset = DATAGRAM_SIZE - 7;
        int protectedLength = tailOffset - datagram.position() - Short.BYTES;
        datagram.putShort((short) (0x4000 | protectedLength));
        // The discarded Initial keys cause this unauthenticated, zero-filled packet to be skipped.
        datagram.position(tailOffset);
        // The second header claims 20 destination-ID bytes, but the datagram contains only one.
        datagram.put((byte) initialFlags).putInt(version.versionNumber()).put((byte) 20).put((byte) 0);
        return datagram.array();
    }

    private static byte[] malformedRetryDatagram(QuicVersion version, QuicConnectionId admittedId) {
        int retryFlags = switch (version) {
            case QUIC_V1 -> 0xf0;
            case QUIC_V2 -> 0xc0;
        };
        ByteBuffer datagram = ByteBuffer.allocate(7 + admittedId.length() + 16);
        datagram.put((byte) retryFlags).putInt(version.versionNumber());
        // The admitted Initial ID is both a live server routing alias and the public Retry integrity input.
        datagram.put((byte) admittedId.length()).put(admittedId.asReadOnlyBuffer());
        datagram.put((byte) 1); // Claim one source-ID byte, but append the integrity tag immediately.
        QuicRetryIntegrity.sign(version,
                                admittedId.asReadOnlyBuffer(),
                                datagram.asReadOnlyBuffer().flip(),
                                datagram);
        return datagram.array();
    }

    private static void awaitReceiveMarker(DatagramSocket source,
                                           InetSocketAddress serverAddress,
                                           byte[] destinationId,
                                           QuicVersion version) throws Exception {
        byte[] markerId = {7, 6, 5, 4, 3, 2, 1, 0};
        ByteBuffer marker = ByteBuffer.allocate(DATAGRAM_SIZE);
        marker.put((byte) 0xc0).putInt(UNSUPPORTED_VERSION);
        marker.put((byte) destinationId.length).put(destinationId);
        marker.put((byte) markerId.length).put(markerId);
        // This controlled-loopback barrier relies on delivery order from one UDP socket, not a protocol-wide guarantee.
        // The direct server executor finishes preceding connection input before processing this marker's response.
        source.send(new DatagramPacket(marker.array(), DATAGRAM_SIZE, serverAddress));
        source.setSoTimeout(Math.toIntExact(TIMEOUT.toMillis()));
        DatagramPacket response = new DatagramPacket(new byte[DATAGRAM_SIZE], DATAGRAM_SIZE);
        source.receive(response);

        assertThat("marker response comes from the server", response.getSocketAddress(), is(serverAddress));
        assertThat("Version Negotiation response length", response.getLength(),
                   is(7 + markerId.length + destinationId.length + Integer.BYTES));
        ByteBuffer packet = ByteBuffer.wrap(response.getData(), response.getOffset(), response.getLength());
        assertThat("marker response has a long header", packet.get() & 0x80, is(0x80));
        assertThat("marker response is Version Negotiation", packet.getInt(), is(0));
        assertThat("marker destination connection ID", readConnectionId(packet), is(markerId));
        assertThat("marker source connection ID", readConnectionId(packet), is(destinationId));
        assertThat("marker advertises the configured version", packet.getInt(), is(version.versionNumber()));
        assertThat("marker response has no unexpected trailing bytes", packet.hasRemaining(), is(false));
    }

    private static byte[] readConnectionId(ByteBuffer packet) {
        byte[] connectionId = new byte[Byte.toUnsignedInt(packet.get())];
        packet.get(connectionId);
        return connectionId;
    }

    private static void assertExchange(ExecutorService executor,
                                       QuicSession client,
                                       QuicSession server,
                                       String message) throws Exception {
        byte[] expected = message.getBytes(StandardCharsets.UTF_8);
        var serverExchange = executor.submit(() -> {
            var stream = (QuicBidirectionalStream) server.acceptStream();
            byte[] received = readStream(stream);
            stream.writeFinal(BufferData.create(received));
            return received;
        });
        var clientExchange = executor.submit(() -> {
            QuicBidirectionalStream stream = client.openBidirectionalStream();
            stream.writeFinal(BufferData.create(expected));
            return readStream(stream);
        });
        try {
            assertThat("server receives stream data " + message,
                       serverExchange.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is(expected));
            assertThat("client receives stream echo " + message,
                       clientExchange.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is(expected));
        } finally {
            serverExchange.cancel(true);
            clientExchange.cancel(true);
        }
    }

    private static byte[] readStream(QuicReceiveStream stream) {
        var result = new ByteArrayOutputStream();
        for (var next = stream.read(); next.isPresent(); next = stream.read()) {
            result.writeBytes(next.orElseThrow().readBytes());
        }
        return result.toByteArray();
    }
}
