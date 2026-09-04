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
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.sameInstance;

class QuicServerIngressTest {
    private static final int UNSUPPORTED_VERSION = 0x0a0a0a0a;
    private static final byte[] DESTINATION_ID = bytes(255, 1);
    private static final byte[] SOURCE_ID = bytes(255, 101);
    private static final InetSocketAddress PEER = loopback(7777);

    private final QuicAddressTokenService tokenService = QuicAddressTokenService.create();
    private final QuicConnectionIdFactory connectionIdFactory = QuicConnectionIdFactory.server();
    private final QuicServerIngress ingress = new QuicServerIngress(List.of(QuicVersion.QUIC_V2,
                                                                            QuicVersion.QUIC_V1),
                                                                    false,
                                                                    null);

    @AfterEach
    void closeTokenService() {
        tokenService.close();
    }

    @Test
    void negotiatesUnsupportedVersionUsingOnlyInvariantFields() {
        ByteBuffer packet = longHeaderDatagram(0x80,
                                               UNSUPPORTED_VERSION,
                                               DESTINATION_ID,
                                               SOURCE_ID,
                                               QuicServerIngress.MIN_INITIAL_DATAGRAM_SIZE);
        int inputPosition = packet.position();

        QuicServerIngress.Action action = inspect(packet);

        assertThat(action, instanceOf(QuicServerIngress.Respond.class));
        ByteBuffer response = ((QuicServerIngress.Respond) action).datagram();
        assertThat(response.get() & 0xc0, is(0xc0));
        assertThat(response.getInt(), is(0));
        assertThat(readConnectionId(response), is(SOURCE_ID));
        assertThat(readConnectionId(response), is(DESTINATION_ID));
        assertThat(response.getInt(), is(QuicVersion.QUIC_V2.versionNumber()));
        assertThat(response.getInt(), is(QuicVersion.QUIC_V1.versionNumber()));
        assertThat(response.hasRemaining(), is(false));
        assertThat(packet.position(), is(inputPosition));
    }

    @ParameterizedTest
    @ValueSource(ints = {0x80, 0x90, 0xa0, 0xb0, 0xc0, 0xd0, 0xe0, 0xf0})
    void ignoresVersionSpecificHeaderBitsForUnsupportedVersions(int firstByte) {
        ByteBuffer packet = longHeaderDatagram(firstByte,
                                               0xfacade01,
                                               bytes(8, 1),
                                               bytes(8, 21),
                                               QuicServerIngress.MIN_INITIAL_DATAGRAM_SIZE);

        assertThat(inspect(packet), instanceOf(QuicServerIngress.Respond.class));
    }

    @Test
    void dropsUndersizedUnsupportedVersion() {
        ByteBuffer packet = longHeaderDatagram(0xff,
                                               UNSUPPORTED_VERSION,
                                               DESTINATION_ID,
                                               SOURCE_ID,
                                               QuicServerIngress.MIN_INITIAL_DATAGRAM_SIZE - 1);

        assertThat(inspect(packet), sameInstance(QuicServerIngress.Drop.INSTANCE));
    }

    @Test
    void dropsVersionNegotiationPacket() {
        ByteBuffer packet = longHeaderDatagram(0xff,
                                               0,
                                               DESTINATION_ID,
                                               SOURCE_ID,
                                               QuicServerIngress.MIN_INITIAL_DATAGRAM_SIZE);

        assertThat(inspect(packet), sameInstance(QuicServerIngress.Drop.INSTANCE));
    }

    @Test
    void dropsShortHeaderAndTruncatedInvariantHeader() {
        ByteBuffer shortHeader = longHeaderDatagram(0x40,
                                                    UNSUPPORTED_VERSION,
                                                    bytes(8, 1),
                                                    bytes(8, 21),
                                                    QuicServerIngress.MIN_INITIAL_DATAGRAM_SIZE);

        assertThat(inspect(shortHeader), sameInstance(QuicServerIngress.Drop.INSTANCE));
        assertThat(inspect(ByteBuffer.wrap(new byte[] {(byte) 0x80, 1, 2, 3, 4, 8, 1})),
                   sameInstance(QuicServerIngress.Drop.INSTANCE));
    }

    @Test
    void admitsStructurallyValidV1Initial() {
        QuicServerIngress.Action action = inspect(initialDatagram(QuicVersion.QUIC_V1, 0xc0));

        assertThat(action, instanceOf(QuicServerIngress.Admit.class));
        QuicServerIngress.Admit admit = (QuicServerIngress.Admit) action;
        assertThat(admit.version(), is(QuicVersion.QUIC_V1));
        assertThat(admit.header().destinationId().asReadOnlyBuffer(), is(ByteBuffer.wrap(bytes(8, 11))));
    }

    @Test
    void admitsStructurallyValidV2Initial() {
        QuicServerIngress.Action action = inspect(initialDatagram(QuicVersion.QUIC_V2, 0xd0));

        assertThat(action, instanceOf(QuicServerIngress.Admit.class));
        assertThat(((QuicServerIngress.Admit) action).version(), is(QuicVersion.QUIC_V2));
    }

    @Test
    void dropsSupportedNonInitialPacket() {
        ByteBuffer packet = initialDatagram(QuicVersion.QUIC_V1, 0xe0);

        assertThat(inspect(packet), sameInstance(QuicServerIngress.Drop.INSTANCE));
    }

    @ParameterizedTest
    @ValueSource(ints = {0x80, 0x90, 0xd0})
    void dropsSupportedInitialWithInvalidVersionSpecificBits(int firstByte) {
        QuicVersion version = firstByte == 0x90 ? QuicVersion.QUIC_V2 : QuicVersion.QUIC_V1;

        assertThat(inspect(initialDatagram(version, firstByte)),
                   sameInstance(QuicServerIngress.Drop.INSTANCE));
    }

    @Test
    void dropsUndersizedSupportedInitial() {
        ByteBuffer packet = initialDatagram(QuicVersion.QUIC_V1,
                                            0xc0,
                                            8,
                                            8,
                                            QuicServerIngress.MIN_INITIAL_DATAGRAM_SIZE - 1,
                                            -1,
                                            false);

        assertThat(inspect(packet), sameInstance(QuicServerIngress.Drop.INSTANCE));
    }

    @Test
    void enforcesSupportedVersionConnectionIdLengths() {
        assertThat(inspect(initialDatagram(QuicVersion.QUIC_V1, 0xc0, 7, 8, 1200, 20, false)),
                   sameInstance(QuicServerIngress.Drop.INSTANCE));
        assertThat(inspect(initialDatagram(QuicVersion.QUIC_V1, 0xc0, 21, 8, 1200, 20, false)),
                   sameInstance(QuicServerIngress.Drop.INSTANCE));
        assertThat(inspect(initialDatagram(QuicVersion.QUIC_V1, 0xc0, 8, 21, 1200, 20, false)),
                   sameInstance(QuicServerIngress.Drop.INSTANCE));
    }

    @Test
    void enforcesMinimumProtectedPacketLength() {
        assertThat(inspect(initialDatagram(QuicVersion.QUIC_V1, 0xc0, 8, 8, 1200, 19, false)),
                   sameInstance(QuicServerIngress.Drop.INSTANCE));
        assertThat(inspect(initialDatagram(QuicVersion.QUIC_V1, 0xc0, 8, 8, 1200, 20, false)),
                   instanceOf(QuicServerIngress.Admit.class));
    }

    @Test
    void acceptsNonMinimalTokenLengthAndCoalescedRemainder() {
        ByteBuffer packet = initialDatagram(QuicVersion.QUIC_V1, 0xc0, 8, 8, 1200, 20, true);

        assertThat(inspect(packet), instanceOf(QuicServerIngress.Admit.class));
    }

    @Test
    void dropsDeclaredPacketLengthBeyondDatagram() {
        ByteBuffer packet = initialDatagram(QuicVersion.QUIC_V1,
                                            0xc0,
                                            8,
                                            8,
                                            1200,
                                            VariableLengthEncoder.MAX_ENCODED_INTEGER,
                                            false);

        assertThat(inspect(packet), sameInstance(QuicServerIngress.Drop.INSTANCE));
    }

    @Test
    void dropsMalformedSupportedInitial() {
        ByteBuffer packet = initialDatagram(QuicVersion.QUIC_V1, 0xc0);
        int tokenLengthOffset = 7 + 8 + 8;
        packet.put(tokenLengthOffset, (byte) 0x7f);
        packet.put(tokenLengthOffset + 1, (byte) 0xff);

        assertThat(inspect(packet), sameInstance(QuicServerIngress.Drop.INSTANCE));
    }

    @Test
    void appliesRetryPolicyWithoutCopyingOversizedTokens() {
        byte[] oversized = new byte[QuicAddressTokenService.MAX_TOKEN_SIZE + 1];
        ByteBuffer packet = initialDatagram(QuicVersion.QUIC_V1,
                                            0xc0,
                                            bytes(8, 11),
                                            bytes(8, 31),
                                            oversized);
        assertThat(inspect(packet), sameInstance(QuicServerIngress.Drop.INSTANCE));
        assertThat(retryIngress().inspect(PEER, connectionIdFactory, packet),
                   instanceOf(QuicServerIngress.Respond.class));

        oversized[0] = 1;
        oversized[1] = QuicAddressTokenService.TokenKind.RETRY.marker();
        ByteBuffer retryToken = initialDatagram(QuicVersion.QUIC_V1,
                                                0xc0,
                                                bytes(8, 11),
                                                bytes(8, 31),
                                                oversized);
        assertThat(retryIngress().inspect(PEER, connectionIdFactory, retryToken),
                   sameInstance(QuicServerIngress.Drop.INSTANCE));
    }

    @Test
    void createsAndValidatesV1Retry() {
        assertRetryRoundTrip(QuicVersion.QUIC_V1, 0xc0);
    }

    @Test
    void createsAndValidatesV2Retry() {
        assertRetryRoundTrip(QuicVersion.QUIC_V2, 0xd0);
    }

    @Test
    void admitsValidRetryAndRestoresConnectionIdContext() {
        RetryExchange exchange = retryExchange(QuicVersion.QUIC_V1, 0xc0);
        QuicServerIngress retryIngress = retryIngress();
        ByteBuffer retriedInitial = initialDatagram(QuicVersion.QUIC_V1,
                                                    0xc0,
                                                    exchange.retrySourceId(),
                                                    exchange.clientSourceId(),
                                                    exchange.token());

        QuicServerIngress.Admit admit = (QuicServerIngress.Admit) retryIngress.inspect(PEER,
                                                                                       connectionIdFactory,
                                                                                       retriedInitial);
        assertThat(admit.addressValidated(), is(true));
        assertThat(admit.originalDestinationId().bytes(), is(exchange.originalDestinationId()));
        assertThat(admit.retrySourceId().bytes(), is(exchange.retrySourceId()));
        assertThat(admit.token(), is(exchange.token()));
    }

    @Test
    void neverSendsASecondRetryForRecognizableRetryToken() {
        RetryExchange exchange = retryExchange(QuicVersion.QUIC_V1, 0xc0);
        byte[] tamperedToken = exchange.token().clone();
        tamperedToken[tamperedToken.length - 1] ^= 1;
        ByteBuffer retriedInitial = initialDatagram(QuicVersion.QUIC_V1,
                                                    0xc0,
                                                    exchange.retrySourceId(),
                                                    exchange.clientSourceId(),
                                                    tamperedToken);

        assertThat(retryIngress().inspect(PEER, connectionIdFactory, retriedInitial),
                   sameInstance(QuicServerIngress.Drop.INSTANCE));
    }

    @Test
    void validNewTokenIgnoresPortAndValidatesTheAddress() {
        byte[] token = tokenService.newToken(loopback(9999), QuicVersion.QUIC_V1).orElseThrow();
        byte[] destinationId = bytes(8, 11);
        byte[] sourceId = bytes(8, 31);

        QuicServerIngress.Admit admit = (QuicServerIngress.Admit) retryIngress().inspect(
                PEER,
                connectionIdFactory,
                initialDatagram(QuicVersion.QUIC_V1, 0xc0, destinationId, sourceId, token));

        assertThat(admit.addressValidated(), is(true));
        assertThat(admit.originalDestinationId().bytes(), is(destinationId));
        assertThat(admit.retrySourceId(), is((QuicConnectionId) null));
    }

    @Test
    void invalidNewTokenFollowsTheConfiguredPolicy() {
        byte[] wrongAddressToken = tokenService.newToken(loopbackAddress(7777, 2), QuicVersion.QUIC_V1).orElseThrow();
        ByteBuffer request = initialDatagram(QuicVersion.QUIC_V1,
                                             0xc0,
                                             bytes(8, 11),
                                             bytes(8, 31),
                                             wrongAddressToken);

        assertThat(retryIngress().inspect(PEER, connectionIdFactory, request),
                   instanceOf(QuicServerIngress.Respond.class));

        QuicServerIngress.Admit admitted = (QuicServerIngress.Admit) inspect(request);
        assertThat(admitted.addressValidated(), is(false));
        assertThat(admitted.token(), is(wrongAddressToken));
    }

    private void assertRetryRoundTrip(QuicVersion version, int firstByte) {
        RetryExchange exchange = retryExchange(version, firstByte);
        assertThat(exchange.responseSize(), lessThanOrEqualTo(3 * QuicServerIngress.MIN_INITIAL_DATAGRAM_SIZE));

        QuicAddressTokenService.ValidatedToken validated = tokenService.validate(
                PEER,
                version,
                PeerConnectionId.create(exchange.retrySourceId()),
                PeerConnectionId.create(exchange.clientSourceId()),
                exchange.token()).orElseThrow();
        assertThat(validated.kind(), is(QuicAddressTokenService.TokenKind.RETRY));
        assertThat(validated.originalDestinationId().bytes(), is(exchange.originalDestinationId()));
        assertThat(validated.retrySourceId().bytes(), is(exchange.retrySourceId()));
    }

    private RetryExchange retryExchange(QuicVersion version, int firstByte) {
        byte[] originalDestinationId = bytes(8, 11);
        byte[] clientSourceId = bytes(8, 31);
        ByteBuffer request = initialDatagram(version, firstByte, originalDestinationId, clientSourceId, new byte[0]);
        int requestPosition = request.position();
        QuicServerIngress.Action action = retryIngress().inspect(PEER, connectionIdFactory, request);
        assertThat(action, instanceOf(QuicServerIngress.Respond.class));
        assertThat(request.position(), is(requestPosition));

        ByteBuffer response = ((QuicServerIngress.Respond) action).datagram();
        QuicRetryIntegrity.verify(version,
                                  ByteBuffer.wrap(originalDestinationId),
                                  response.asReadOnlyBuffer());
        int expectedType = version == QuicVersion.QUIC_V1 ? 0xf0 : 0xc0;
        assertThat(response.get() & 0xf0, is(expectedType));
        assertThat(response.getInt(), is(version.versionNumber()));
        assertThat(readConnectionId(response), is(clientSourceId));
        byte[] retrySourceId = readConnectionId(response);
        assertThat(Arrays.equals(retrySourceId, originalDestinationId), is(false));
        byte[] token = new byte[response.remaining() - 16];
        response.get(token);
        assertThat(response.remaining(), is(16));
        return new RetryExchange(originalDestinationId,
                                 clientSourceId,
                                 retrySourceId,
                                 token,
                                 response.limit());
    }

    private QuicServerIngress retryIngress() {
        return new QuicServerIngress(List.of(QuicVersion.QUIC_V2, QuicVersion.QUIC_V1), true, tokenService);
    }

    private QuicServerIngress.Action inspect(ByteBuffer packet) {
        return ingress.inspect(PEER, connectionIdFactory, packet);
    }

    private static ByteBuffer initialDatagram(QuicVersion version, int firstByte) {
        return initialDatagram(version,
                               firstByte,
                               8,
                               8,
                               QuicServerIngress.MIN_INITIAL_DATAGRAM_SIZE,
                               -1,
                               false);
    }

    private static ByteBuffer initialDatagram(QuicVersion version,
                                              int firstByte,
                                              int destinationLength,
                                              int sourceLength,
                                              int datagramSize,
                                              long declaredPacketLength,
                                              boolean nonMinimalTokenLength) {
        byte[] destinationId = bytes(destinationLength, 11);
        byte[] sourceId = bytes(sourceLength, 31);
        return initialDatagram(version,
                               firstByte,
                               destinationId,
                               sourceId,
                               datagramSize,
                               declaredPacketLength,
                               nonMinimalTokenLength,
                               new byte[0]);
    }

    private static ByteBuffer initialDatagram(QuicVersion version,
                                              int firstByte,
                                              byte[] destinationId,
                                              byte[] sourceId,
                                              byte[] token) {
        return initialDatagram(version,
                               firstByte,
                               destinationId,
                               sourceId,
                               QuicServerIngress.MIN_INITIAL_DATAGRAM_SIZE,
                               -1,
                               false,
                               token);
    }

    private static ByteBuffer initialDatagram(QuicVersion version,
                                              int firstByte,
                                              byte[] destinationId,
                                              byte[] sourceId,
                                              int datagramSize,
                                              long declaredPacketLength,
                                              boolean nonMinimalTokenLength,
                                              byte[] token) {
        ByteBuffer packet = ByteBuffer.allocate(datagramSize);
        packet.put((byte) firstByte);
        packet.putInt(version.versionNumber());
        packet.put((byte) destinationId.length);
        packet.put(destinationId);
        packet.put((byte) sourceId.length);
        packet.put(sourceId);
        if (nonMinimalTokenLength) {
            packet.put((byte) 0x40);
            packet.put((byte) token.length);
        } else {
            VariableLengthEncoder.encode(packet, token.length);
        }
        packet.put(token);
        if (declaredPacketLength < 0) {
            int packetLengthSize = 2;
            declaredPacketLength = packet.capacity() - packet.position() - packetLengthSize;
        }
        VariableLengthEncoder.encode(packet, declaredPacketLength);
        while (packet.hasRemaining()) {
            packet.put((byte) 0);
        }
        return packet.flip();
    }

    private static ByteBuffer longHeaderDatagram(int firstByte,
                                                 int version,
                                                 byte[] destinationId,
                                                 byte[] sourceId,
                                                 int size) {
        ByteBuffer packet = ByteBuffer.allocate(size);
        packet.put((byte) firstByte);
        packet.putInt(version);
        packet.put((byte) destinationId.length);
        packet.put(destinationId);
        packet.put((byte) sourceId.length);
        packet.put(sourceId);
        while (packet.hasRemaining()) {
            packet.put((byte) 0);
        }
        return packet.flip();
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

    private static InetSocketAddress loopback(int port) {
        return new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
    }

    private static InetSocketAddress loopbackAddress(int port, int lastOctet) {
        try {
            return new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, (byte) lastOctet}), port);
        } catch (UnknownHostException e) {
            throw new AssertionError(e);
        }
    }

    private record RetryExchange(byte[] originalDestinationId,
                                 byte[] clientSourceId,
                                 byte[] retrySourceId,
                                 byte[] token,
                                 int responseSize) {
    }
}
