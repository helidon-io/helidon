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

import java.nio.ByteBuffer;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicOneRttTrafficKeysTest {
    private static final HexFormat HEX = HexFormat.of();
    // These short-header helpers intentionally use the RFC ChaCha20-Poly1305 traffic secret so the manager is tested
    // against the same public vectors as the lower-level packet-protection primitives.
    private static final String ONERTT_SECRET =
            "9ac312a7f877468ebe69422748ad00a15443f18203a07d6060f688f30f21632b";
    private static final QuicTls13CipherSuite CIPHER_SUITE = QuicTls13CipherSuite.TLS_CHACHA20_POLY1305_SHA256;
    // These helpers use a fixed short-header layout with a 3-byte packet number, matching the RFC packet vectors and
    // leaving exactly one payload byte plus the AEAD tag so header-protection samples remain available.
    private static final int HEADER_LENGTH = 4;
    private static final int PACKET_NUMBER_OFFSET = 1;
    private static final int PACKET_NUMBER_LENGTH = HEADER_LENGTH - PACKET_NUMBER_OFFSET;
    private static final int PAYLOAD_LENGTH = 1;
    private static final int HEADER_MASK = 0x1f;
    // RFC 9001 Appendix A.5 QUIC v1 ChaCha20-Poly1305 short-header packet vector.
    private static final OneRttPacketVector V1_VECTOR = new OneRttPacketVector(
            QuicVersion.QUIC_V1,
            "4200bff4",
            "01",
            654360564L,
            "4cfe4189655e5cd55c41f69080575d7999c25a5bfb");
    // RFC 9369 Appendix A.5 QUIC v2 ChaCha20-Poly1305 short-header packet vector.
    private static final OneRttPacketVector V2_VECTOR = new OneRttPacketVector(
            QuicVersion.QUIC_V2,
            "4200bff4",
            "01",
            654360564L,
            "5558b1c60ae7b6b932bc27d786f4bc2bb20f2162ba");

    @ParameterizedTest
    @MethodSource("vectors")
    void shouldEncryptKnownChaCha20OneRttPacket(OneRttPacketVector vector) throws Exception {
        QuicOneRttTrafficKeys keys = create(vector.version(), true);
        keys.oneRttContext(() -> -1);

        WirePacket packet = encryptVector(keys, vector);

        assertThat(HEX.formatHex(packet.packet()), is(vector.packet()));
        assertThat(packet.keyPhase(), is(0));
    }

    @ParameterizedTest
    @MethodSource("vectors")
    void shouldDecryptKnownChaCha20OneRttPacket(OneRttPacketVector vector) throws Exception {
        QuicOneRttTrafficKeys keys = create(vector.version(), false);
        keys.oneRttContext(() -> -1);

        DecryptedPacket packet = decrypt(keys, vector.packetNumber(), HEX.parseHex(vector.packet()));

        assertThat(packet.keyPhase(), is(0));
        assertThat(HEX.formatHex(packet.packet()).substring(0,
                                                            vector.unprotectedHeader().length()
                                                                    + vector.payloadPlaintext().length()),
                   is(vector.unprotectedHeader() + vector.payloadPlaintext()));
    }

    @Test
    void shouldInitiateKeyUpdateBeforeConfidentialityLimit() throws Exception {
        QuicOneRttTrafficKeys local = create(QuicVersion.QUIC_V1, true, 5);
        local.oneRttContext(() -> -1);
        QuicOneRttTrafficKeys peer = create(QuicVersion.QUIC_V1, false, 5);
        peer.oneRttContext(() -> -1);

        decrypt(local, 10, encrypt(peer, 10, (byte) 0x11).packet());

        int[] phases = new int[5];
        for (int i = 0; i < phases.length; i++) {
            phases[i] = encrypt(local, 20 + i, (byte) (0x20 + i)).keyPhase();
        }

        assertThat(phases[0], is(0));
        assertThat(phases[1], is(0));
        assertThat(phases[2], is(0));
        assertThat(phases[3], is(0));
        assertThat(phases[4], is(1));
    }

    @Test
    void shouldRejectPacketsOnceAeadLimitIsReached() throws Exception {
        QuicOneRttTrafficKeys local = create(QuicVersion.QUIC_V1, true, 1);
        local.oneRttContext(() -> -1);

        assertThat(encrypt(local, 1, (byte) 0x01).keyPhase(), is(0));
        assertThat(encrypt(local, 2, (byte) 0x02).keyPhase(), is(0));

        QuicTransportException ex = assertThrows(QuicTransportException.class,
                                                 () -> encrypt(local, 3, (byte) 0x03));

        assertThat(ex.errorCode(), is(QuicTransportErrors.AEAD_LIMIT_REACHED.code()));
    }

    @Test
    void shouldRollOverWhenPeerInitiatesKeyUpdate() throws Exception {
        MutableOneRttContext localContext = new MutableOneRttContext();
        MutableOneRttContext peerContext = new MutableOneRttContext();
        QuicOneRttTrafficKeys local = create(QuicVersion.QUIC_V1, true, 1);
        local.oneRttContext(localContext);
        QuicOneRttTrafficKeys peer = create(QuicVersion.QUIC_V1, false, 1);
        peer.oneRttContext(peerContext);

        decrypt(peer, 1, encrypt(local, 1, (byte) 0x11).packet());
        decrypt(local, 10, encrypt(peer, 10, (byte) 0x21).packet());

        WirePacket updatedPacket = encrypt(peer, 11, (byte) 0x22);
        DecryptedPacket decrypted = decrypt(local, 11, updatedPacket.packet());

        assertThat(updatedPacket.keyPhase(), is(1));
        assertThat(decrypted.keyPhase(), is(1));
        assertThat(encrypt(local, 20, (byte) 0x31).keyPhase(), is(1));
    }

    @Test
    void shouldReportKeyUpdateErrorWhenPeerAcknowledgesNewKeysButUsesOldOnes() throws Exception {
        MutableOneRttContext localContext = new MutableOneRttContext();
        QuicOneRttTrafficKeys local = create(QuicVersion.QUIC_V1, true, 1);
        local.oneRttContext(localContext);
        QuicOneRttTrafficKeys peer = create(QuicVersion.QUIC_V1, false, 1);
        peer.oneRttContext(() -> -1);

        encrypt(local, 1, (byte) 0x11);
        decrypt(local, 10, encrypt(peer, 10, (byte) 0x21).packet());

        WirePacket updatedPacket = encrypt(local, 2, (byte) 0x12);
        assertThat(updatedPacket.keyPhase(), is(1));
        localContext.ack(updatedPacket.packetNumber());

        WirePacket oldKeyPacket = encrypt(peer, 11, (byte) 0x22);
        assertThat(oldKeyPacket.keyPhase(), is(0));

        QuicTransportException ex = assertThrows(QuicTransportException.class,
                                                 () -> decrypt(local,
                                                               oldKeyPacket.packetNumber(),
                                                               oldKeyPacket.packet()));

        assertThat(ex.errorCode(), is(QuicTransportErrors.KEY_UPDATE_ERROR.code()));
    }

    @Test
    void shouldPropagateTypedAuthenticationFailure() throws Exception {
        QuicOneRttTrafficKeys local = create(QuicVersion.QUIC_V1, true);
        local.oneRttContext(() -> -1);
        QuicOneRttTrafficKeys peer = create(QuicVersion.QUIC_V1, false);
        peer.oneRttContext(() -> -1);
        WirePacket encoded = encrypt(peer, 10, (byte) 0x21);
        ByteBuffer packet = ByteBuffer.wrap(encoded.packet().clone());
        unprotect(packet, local);
        int keyPhase = (packet.get(0) & 0x04) >> 2;
        packet.put(packet.limit() - 1, (byte) (packet.get(packet.limit() - 1) ^ 1));
        ByteBuffer input = packet.asReadOnlyBuffer();
        packet.position(HEADER_LENGTH);

        assertThrows(QuicPacketAuthenticationException.class,
                     () -> local.decryptPacket(encoded.packetNumber(), keyPhase, input, HEADER_LENGTH, packet));
    }

    private static Stream<OneRttPacketVector> vectors() {
        return Stream.of(V1_VECTOR, V2_VECTOR);
    }

    private static QuicOneRttTrafficKeys create(QuicVersion version, boolean clientMode) throws Exception {
        return create(version,
                      clientMode,
                      QuicAeadLimits.DEFAULT_CHACHA20_POLY1305_CONFIDENTIALITY_LIMIT);
    }

    private static QuicOneRttTrafficKeys create(QuicVersion version,
                                                boolean clientMode,
                                                long chacha20Poly1305ConfidentialityLimit) throws Exception {
        SecretKeySpec secret = new SecretKeySpec(HEX.parseHex(ONERTT_SECRET), "TlsSecret");
        return QuicOneRttTrafficKeys.create(version,
                                            CIPHER_SUITE,
                                            secret,
                                            secret,
                                            clientMode,
                                            QuicAeadLimits.DEFAULT_AES_GCM_CONFIDENTIALITY_LIMIT,
                                            chacha20Poly1305ConfidentialityLimit);
    }

    private static WirePacket encryptVector(QuicOneRttTrafficKeys keys, OneRttPacketVector vector) throws Exception {
        ByteBuffer packet = ByteBuffer.allocate(vector.packet().length() / 2);
        packet.put(HEX.parseHex(vector.unprotectedHeader()));
        packet.position(vector.headerLength());

        AtomicLong keyPhase = new AtomicLong(-1);
        ByteBuffer header = packet.slice(0, vector.headerLength());
        keys.encryptPacket(vector.packetNumber(),
                           kp -> {
                               keyPhase.set(kp);
                               header.put(0, (byte) ((header.get(0) & ~0x04) | (kp << 2)));
                               return header.position(0).asReadOnlyBuffer();
                           },
                           ByteBuffer.wrap(HEX.parseHex(vector.payloadPlaintext())),
                           packet);
        protect(packet, vector, keys);
        return new WirePacket(vector.packetNumber(), packet.array(), (int) keyPhase.get());
    }

    private static WirePacket encrypt(QuicOneRttTrafficKeys keys,
                                      long packetNumber,
                                      byte payloadByte) throws Exception {
        ByteBuffer packet = ByteBuffer.allocate(HEADER_LENGTH + PAYLOAD_LENGTH + QuicPacketProtection.AUTH_TAG_SIZE);
        putHeader(packet, packetNumber, 0);
        packet.position(HEADER_LENGTH);

        AtomicLong keyPhase = new AtomicLong(-1);
        ByteBuffer header = packet.slice(0, HEADER_LENGTH);
        keys.encryptPacket(packetNumber,
                           kp -> {
                               keyPhase.set(kp);
                               header.put(0, firstByte(kp));
                               return header.position(0).asReadOnlyBuffer();
                           },
                           ByteBuffer.wrap(new byte[] {payloadByte}),
                           packet);
        protect(packet, keys);
        return new WirePacket(packetNumber, packet.array(), (int) keyPhase.get());
    }

    private static DecryptedPacket decrypt(QuicOneRttTrafficKeys keys,
                                           long packetNumber,
                                           byte[] encodedPacket) throws Exception {
        ByteBuffer packet = ByteBuffer.wrap(encodedPacket.clone());
        unprotect(packet, keys);
        int keyPhase = (packet.get(0) & 0x04) >> 2;
        ByteBuffer input = packet.asReadOnlyBuffer();
        packet.position(HEADER_LENGTH);
        keys.decryptPacket(packetNumber, keyPhase, input, HEADER_LENGTH, packet);
        return new DecryptedPacket(keyPhase, packet.array());
    }

    private static void protect(ByteBuffer packet,
                                OneRttPacketVector vector,
                                QuicOneRttTrafficKeys keys) throws QuicTransportException, QuicKeyUnavailableException {
        ByteBuffer sample = packet.slice(vector.packetNumberOffset() + 4,
                                         QuicPacketProtection.HEADER_PROTECTION_SAMPLE_SIZE);
        ByteBuffer mask = keys.computeHeaderProtectionMask(false, sample);
        packet.put(0, (byte) (packet.get(0) ^ (mask.get() & HEADER_MASK)));
        maskPacketNumber(packet, vector.packetNumberOffset(), vector.packetNumberLength(), mask);
    }

    private static void protect(ByteBuffer packet,
                                QuicOneRttTrafficKeys keys) throws QuicTransportException, QuicKeyUnavailableException {
        ByteBuffer sample = packet.slice(PACKET_NUMBER_OFFSET + 4, QuicPacketProtection.HEADER_PROTECTION_SAMPLE_SIZE);
        ByteBuffer mask = keys.computeHeaderProtectionMask(false, sample);
        packet.put(0, (byte) (packet.get(0) ^ (mask.get() & HEADER_MASK)));
        maskPacketNumber(packet, PACKET_NUMBER_OFFSET, PACKET_NUMBER_LENGTH, mask);
    }

    private static void unprotect(ByteBuffer packet,
                                  QuicOneRttTrafficKeys keys) throws QuicTransportException, QuicKeyUnavailableException {
        ByteBuffer sample = packet.slice(PACKET_NUMBER_OFFSET + 4, QuicPacketProtection.HEADER_PROTECTION_SAMPLE_SIZE);
        ByteBuffer mask = keys.computeHeaderProtectionMask(true, sample);
        packet.put(0, (byte) (packet.get(0) ^ (mask.get() & HEADER_MASK)));
        maskPacketNumber(packet, PACKET_NUMBER_OFFSET, PACKET_NUMBER_LENGTH, mask);
    }

    private static void maskPacketNumber(ByteBuffer packet,
                                         int packetNumberOffset,
                                         int packetNumberLength,
                                         ByteBuffer mask) {
        for (int i = 0; i < packetNumberLength; i++) {
            packet.put(packetNumberOffset + i, (byte) (packet.get(packetNumberOffset + i) ^ mask.get()));
        }
    }

    private static void putHeader(ByteBuffer packet, long packetNumber, int keyPhase) {
        packet.put(firstByte(keyPhase));
        packet.put((byte) ((packetNumber >>> 16) & 0xFF));
        packet.put((byte) ((packetNumber >>> 8) & 0xFF));
        packet.put((byte) (packetNumber & 0xFF));
    }

    private static byte firstByte(int keyPhase) {
        return (byte) (0x42 | (keyPhase << 2));
    }

    private record WirePacket(long packetNumber, byte[] packet, int keyPhase) {
    }

    private record DecryptedPacket(int keyPhase, byte[] packet) {
    }

    private record OneRttPacketVector(QuicVersion version,
                                      String unprotectedHeader,
                                      String payloadPlaintext,
                                      long packetNumber,
                                      String packet) {
        private int headerLength() {
            return unprotectedHeader.length() / 2;
        }

        private int packetNumberOffset() {
            return 1;
        }

        private int packetNumberLength() {
            return headerLength() - packetNumberOffset();
        }
    }

    private static final class MutableOneRttContext implements QuicOneRttContext {
        private final AtomicLong largestPeerAckedPacketNumber = new AtomicLong(-1);

        @Override
        public long largestPeerAcknowledgedPacketNumber() {
            return largestPeerAckedPacketNumber.get();
        }

        private void ack(long packetNumber) {
            largestPeerAckedPacketNumber.set(packetNumber);
        }
    }
}
