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
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuicTls13ConnectionSecretsTest {
    private static final HexFormat HEX = HexFormat.of();
    private static final QuicVersion VERSION = QuicVersion.QUIC_V1;
    private static final QuicTls13CipherSuite CIPHER_SUITE = QuicTls13CipherSuite.TLS_AES_128_GCM_SHA256;
    private static final int HEADER_LENGTH = 4;
    private static final int PAYLOAD_LENGTH = 1;
    // These RFC 8448 values are reused here so the new connection-secret foundation is verified against the same
    // published TLS 1.3 handshake and application secret tree as the lower-level secret-schedule tests.
    private static final String SIMPLE_SHARED_SECRET =
            "8bd4054fb55b9d63fdfbacf9f04b9f0d35e6d63f537563efd46272900f89492d";
    private static final String SIMPLE_SERVER_HELLO_HASH =
            "860c06edc07858ee8e78f0e7428c58edd6b43f2ca3e6e95f02ed063cf0e1cad8";
    private static final String SIMPLE_HANDSHAKE_SECRET =
            "1dc826e93606aa6fdc0aadc12f741b01046aa6b99f691ed221a9f0ca043fbeac";
    private static final String SIMPLE_CLIENT_HANDSHAKE_TRAFFIC_SECRET =
            "b3eddb126e067f35a780b3abf45e2d8f3b1a950738f52e9600746a0e27a55a21";
    private static final String SIMPLE_SERVER_HANDSHAKE_TRAFFIC_SECRET =
            "b67b7d690cc16c4e75e54213cb2d37b4e9c912bcded9105d42befd59d391ad38";
    private static final String SIMPLE_MASTER_SECRET =
            "18df06843d13a08bf2a449844c5f8a478001bc4d4c627984d5a41da8d0402919";
    private static final String SIMPLE_SERVER_FINISHED_HASH =
            "9608102a0f1ccc6db6250b7b7e417b1a000eaada3daae4777a7686c9ff83df13";
    private static final String SIMPLE_CLIENT_APPLICATION_TRAFFIC_SECRET =
            "9e40646ce79a7f9dc05af8889bce6552875afa0b06df0087f792ebb7c17504a5";
    private static final String SIMPLE_SERVER_APPLICATION_TRAFFIC_SECRET =
            "a11af9f05531f856ad47116b45a950328204b4f44bfb6b3a4b4f1f3fcb631643";
    // These synthetic transcript hashes and sample bytes are fixed-width SHA-256-shaped values that let the generated
    // key-share tests drive deterministic Handshake and 1-RTT packet protection without depending on a full TLS core.
    private static final String HANDSHAKE_TRANSCRIPT_HASH =
            "00112233445566778899aabbccddeeff102132435465768798a9bacbdcedfe0f";
    private static final String APPLICATION_TRANSCRIPT_HASH =
            "f0e1d2c3b4a5968778695a4b3c2d1e0f112233445566778899aabbccddeeff00";
    private static final String HEADER_PROTECTION_SAMPLE =
            "0102030405060708090a0b0c0d0e0f10";

    @Test
    void shouldDeriveKnownTrafficSecretsFromRfc8448SharedSecret() throws Exception {
        QuicTls13ConnectionSecrets secrets =
                QuicTls13ConnectionSecrets.create(VERSION, CIPHER_SUITE, bytes(SIMPLE_SHARED_SECRET), true);

        assertSecret(secrets.handshakeSecret(), SIMPLE_HANDSHAKE_SECRET);
        assertSecret(secrets.clientHandshakeTrafficSecret(bytes(SIMPLE_SERVER_HELLO_HASH)),
                     SIMPLE_CLIENT_HANDSHAKE_TRAFFIC_SECRET);
        assertSecret(secrets.serverHandshakeTrafficSecret(bytes(SIMPLE_SERVER_HELLO_HASH)),
                     SIMPLE_SERVER_HANDSHAKE_TRAFFIC_SECRET);
        assertSecret(secrets.masterSecret(), SIMPLE_MASTER_SECRET);
        assertSecret(secrets.clientApplicationTrafficSecret(bytes(SIMPLE_SERVER_FINISHED_HASH)),
                     SIMPLE_CLIENT_APPLICATION_TRAFFIC_SECRET);
        assertSecret(secrets.serverApplicationTrafficSecret(bytes(SIMPLE_SERVER_FINISHED_HASH)),
                     SIMPLE_SERVER_APPLICATION_TRAFFIC_SECRET);
    }

    @Test
    void shouldDeriveMatchingHandshakeAndOneRttKeysFromGeneratedKeyShares() throws Exception {
        SecureRandom secureRandom = new SecureRandom();
        QuicTlsLocalKeyShares clientLocalKeyShares =
                QuicTlsLocalKeyShares.create(List.of(QuicTlsNamedGroup.X25519), secureRandom);
        QuicTlsKeySharePossession serverKeyShare = QuicTlsKeySharePossession.create(QuicTlsNamedGroup.X25519, secureRandom);

        QuicTlsServerHelloMessage serverHello = QuicTlsServerHelloMessage.create(
                0x0303,
                new byte[QuicTlsCodecSupport.RANDOM_LENGTH],
                new byte[0],
                0x1301,
                0,
                List.of(
                        QuicTlsExtension.create(QuicTlsExtensions.KEY_SHARE,
                                                QuicTlsKeyShares.encodeServerHello(serverKeyShare.keyShareEntry())),
                        QuicTlsExtension.create(QuicTlsExtensions.SUPPORTED_VERSIONS,
                                                QuicTlsSupportedVersions.encodeServerHello(
                                                        QuicTlsSupportedVersions.TLS_1_3))));

        QuicTls13ConnectionSecrets clientSecrets =
                QuicTls13ConnectionSecrets.forServerHello(VERSION, clientLocalKeyShares, serverHello, true);
        QuicTls13ConnectionSecrets serverSecrets =
                QuicTls13ConnectionSecrets.create(VERSION,
                                                  CIPHER_SUITE,
                                                  serverKeyShare,
                                                  clientLocalKeyShares.keyShareEntries().getFirst(),
                                                  false);

        byte[] handshakeTranscriptHash = bytes(HANDSHAKE_TRANSCRIPT_HASH);
        byte[] applicationTranscriptHash = bytes(APPLICATION_TRANSCRIPT_HASH);

        QuicLongHeaderTrafficKeys clientHandshakeKeys = clientSecrets.deriveHandshakeTrafficKeys(handshakeTranscriptHash);
        QuicLongHeaderTrafficKeys serverHandshakeKeys = serverSecrets.deriveHandshakeTrafficKeys(handshakeTranscriptHash);
        assertThat(hex(clientHandshakeKeys.computeHeaderProtectionMask(false,
                                                                       ByteBuffer.wrap(
                                                                               bytes(HEADER_PROTECTION_SAMPLE)))),
                   is(hex(serverHandshakeKeys.computeHeaderProtectionMask(true,
                                                                          ByteBuffer.wrap(
                                                                                  bytes(HEADER_PROTECTION_SAMPLE))))));
        assertThat(decryptHandshake(serverHandshakeKeys, encryptHandshake(clientHandshakeKeys, 7, (byte) 0x31)),
                   is((byte) 0x31));
        assertThat(decryptHandshake(clientHandshakeKeys, encryptHandshake(serverHandshakeKeys, 8, (byte) 0x32)),
                   is((byte) 0x32));

        QuicOneRttTrafficKeys clientOneRttKeys = clientSecrets.deriveOneRttTrafficKeys(applicationTranscriptHash);
        clientOneRttKeys.oneRttContext(() -> -1);
        QuicOneRttTrafficKeys serverOneRttKeys = serverSecrets.deriveOneRttTrafficKeys(applicationTranscriptHash);
        serverOneRttKeys.oneRttContext(() -> -1);

        assertThat(hex(clientOneRttKeys.computeHeaderProtectionMask(false,
                                                                    ByteBuffer.wrap(bytes(HEADER_PROTECTION_SAMPLE)))),
                   is(hex(serverOneRttKeys.computeHeaderProtectionMask(true,
                                                                       ByteBuffer.wrap(bytes(HEADER_PROTECTION_SAMPLE))))));
        assertThat(decryptOneRtt(serverOneRttKeys, encryptOneRtt(clientOneRttKeys, 11, (byte) 0x41).packet()),
                   is((byte) 0x41));
        assertThat(decryptOneRtt(clientOneRttKeys, encryptOneRtt(serverOneRttKeys, 12, (byte) 0x42).packet()),
                   is((byte) 0x42));
    }

    @Test
    void shouldExposeOrderedLocalKeyShares() throws Exception {
        QuicTlsLocalKeyShares localKeyShares = QuicTlsLocalKeyShares.create(
                List.of(QuicTlsNamedGroup.X25519, QuicTlsNamedGroup.SECP256_R1),
                new SecureRandom());

        assertThat(localKeyShares.namedGroups(), equalTo(List.of(QuicTlsNamedGroup.X25519, QuicTlsNamedGroup.SECP256_R1)));
        assertThat(localKeyShares.keyShareEntries().stream().map(QuicTlsKeyShareEntry::namedGroup).toList(),
                   equalTo(List.of(QuicTlsNamedGroup.X25519, QuicTlsNamedGroup.SECP256_R1)));
    }

    @Test
    void shouldClearLocallyDerivedSharedSecretAfterCreatingSecrets() {
        byte[] sharedSecret = bytes(SIMPLE_SHARED_SECRET);
        QuicTlsKeySharePossession local = mock(QuicTlsKeySharePossession.class);
        QuicTlsKeyShareEntry peer = QuicTlsKeyShareEntry.create(QuicTlsNamedGroup.X25519, new byte[32]);
        when(local.sharedSecret(peer)).thenReturn(sharedSecret);

        QuicTls13ConnectionSecrets secrets = QuicTls13ConnectionSecrets.create(VERSION, CIPHER_SUITE, local, peer, true);

        assertSecret(secrets.handshakeSecret(), SIMPLE_HANDSHAKE_SECRET);
        assertThat(sharedSecret, equalTo(new byte[sharedSecret.length]));
    }

    @Test
    void shouldClearLocallyDerivedSharedSecretWhenCreatingSecretsFails() {
        byte[] sharedSecret = bytes(SIMPLE_SHARED_SECRET);
        QuicTlsKeySharePossession local = mock(QuicTlsKeySharePossession.class);
        QuicTlsKeyShareEntry peer = QuicTlsKeyShareEntry.create(QuicTlsNamedGroup.X25519, new byte[32]);
        when(local.sharedSecret(peer)).thenReturn(sharedSecret);

        assertThrows(NullPointerException.class,
                     () -> QuicTls13ConnectionSecrets.create(VERSION, null, local, peer, true));

        assertThat(sharedSecret, equalTo(new byte[sharedSecret.length]));
    }

    @Test
    void shouldLeaveCallerOwnedSharedSecretIntact() {
        byte[] sharedSecret = bytes(SIMPLE_SHARED_SECRET);

        QuicTls13ConnectionSecrets secrets = QuicTls13ConnectionSecrets.create(VERSION, CIPHER_SUITE, sharedSecret, true);

        assertSecret(secrets.handshakeSecret(), SIMPLE_HANDSHAKE_SECRET);
        assertThat(sharedSecret, equalTo(bytes(SIMPLE_SHARED_SECRET)));
    }

    @Test
    void shouldClearSharedSecretDerivedFromServerHello() {
        byte[] sharedSecret = bytes(SIMPLE_SHARED_SECRET);
        QuicTlsLocalKeyShares local = mock(QuicTlsLocalKeyShares.class);
        QuicTlsKeyShareEntry peer = QuicTlsKeyShareEntry.create(QuicTlsNamedGroup.X25519, new byte[32]);
        when(local.sharedSecret(peer)).thenReturn(sharedSecret);

        QuicTls13ConnectionSecrets secrets =
                QuicTls13ConnectionSecrets.forServerHello(VERSION, local, serverHello(peer), true);

        assertSecret(secrets.handshakeSecret(), SIMPLE_HANDSHAKE_SECRET);
        assertThat(sharedSecret, equalTo(new byte[sharedSecret.length]));
    }

    @Test
    void shouldClearSharedSecretDerivedFromServerHelloWhenCreatingSecretsFails() {
        byte[] sharedSecret = bytes(SIMPLE_SHARED_SECRET);
        QuicTlsLocalKeyShares local = mock(QuicTlsLocalKeyShares.class);
        QuicTlsKeyShareEntry peer = QuicTlsKeyShareEntry.create(QuicTlsNamedGroup.X25519, new byte[32]);
        when(local.sharedSecret(peer)).thenReturn(sharedSecret);

        assertThrows(NullPointerException.class,
                     () -> QuicTls13ConnectionSecrets.forServerHello(null, local, serverHello(peer), true));

        assertThat(sharedSecret, equalTo(new byte[sharedSecret.length]));
    }

    private static QuicTlsServerHelloMessage serverHello(QuicTlsKeyShareEntry peer) {
        return QuicTlsServerHelloMessage.create(
                0x0303,
                new byte[QuicTlsCodecSupport.RANDOM_LENGTH],
                new byte[0],
                CIPHER_SUITE.codePoint(),
                0,
                List.of(QuicTlsExtension.create(QuicTlsExtensions.KEY_SHARE, QuicTlsKeyShares.encodeServerHello(peer)),
                        QuicTlsExtension.create(QuicTlsExtensions.SUPPORTED_VERSIONS,
                                                QuicTlsSupportedVersions.encodeServerHello(
                                                        QuicTlsSupportedVersions.TLS_1_3))));
    }

    private static byte[] encryptHandshake(QuicLongHeaderTrafficKeys keys, long packetNumber, byte payloadByte) throws Exception {
        ByteBuffer packet = ByteBuffer.allocate(HEADER_LENGTH + PAYLOAD_LENGTH + QuicPacketProtection.AUTH_TAG_SIZE);
        packet.put(longHeader(packetNumber));
        ByteBuffer header = packet.slice(0, HEADER_LENGTH);
        packet.position(HEADER_LENGTH);
        keys.encryptPacket(packetNumber,
                           _ -> header.position(0).asReadOnlyBuffer(),
                           ByteBuffer.wrap(new byte[] {payloadByte}),
                           packet);
        return packet.array();
    }

    private static byte decryptHandshake(QuicLongHeaderTrafficKeys keys, byte[] encodedPacket) throws Exception {
        ByteBuffer packet = ByteBuffer.wrap(encodedPacket.clone());
        ByteBuffer input = packet.asReadOnlyBuffer();
        packet.position(HEADER_LENGTH);
        long packetNumber = decodePacketNumber(packet.array());
        keys.decryptPacket(packetNumber, -1, input, HEADER_LENGTH, packet);
        return packet.array()[HEADER_LENGTH];
    }

    private static OneRttPacket encryptOneRtt(QuicOneRttTrafficKeys keys, long packetNumber, byte payloadByte) throws Exception {
        ByteBuffer packet = ByteBuffer.allocate(HEADER_LENGTH + PAYLOAD_LENGTH + QuicPacketProtection.AUTH_TAG_SIZE);
        byte[] headerBytes = shortHeader(packetNumber, 0);
        packet.put(headerBytes);
        packet.position(HEADER_LENGTH);

        ByteBuffer header = packet.slice(0, HEADER_LENGTH);
        AtomicInteger keyPhase = new AtomicInteger(-1);
        keys.encryptPacket(packetNumber,
                           phase -> {
                               keyPhase.set(phase);
                               header.put(0, shortHeaderFirstByte(phase));
                               return header.position(0).asReadOnlyBuffer();
                           },
                           ByteBuffer.wrap(new byte[] {payloadByte}),
                           packet);
        return new OneRttPacket(packet.array(), keyPhase.get());
    }

    private static byte decryptOneRtt(QuicOneRttTrafficKeys keys, byte[] encodedPacket) throws Exception {
        ByteBuffer packet = ByteBuffer.wrap(encodedPacket.clone());
        ByteBuffer input = packet.asReadOnlyBuffer();
        packet.position(HEADER_LENGTH);
        long packetNumber = decodePacketNumber(packet.array());
        int keyPhase = (packet.get(0) >>> 2) & 0x1;
        keys.decryptPacket(packetNumber, keyPhase, input, HEADER_LENGTH, packet);
        return packet.array()[HEADER_LENGTH];
    }

    private static byte[] longHeader(long packetNumber) {
        return new byte[] {
                (byte) 0xE0,
                (byte) ((packetNumber >>> 16) & 0xFF),
                (byte) ((packetNumber >>> 8) & 0xFF),
                (byte) (packetNumber & 0xFF)
        };
    }

    private static byte[] shortHeader(long packetNumber, int keyPhase) {
        return new byte[] {
                shortHeaderFirstByte(keyPhase),
                (byte) ((packetNumber >>> 16) & 0xFF),
                (byte) ((packetNumber >>> 8) & 0xFF),
                (byte) (packetNumber & 0xFF)
        };
    }

    private static byte shortHeaderFirstByte(int keyPhase) {
        return (byte) (0x40 | ((keyPhase & 0x1) << 2));
    }

    private static long decodePacketNumber(byte[] packet) {
        return ((packet[1] & 0xFFL) << 16)
                | ((packet[2] & 0xFFL) << 8)
                | (packet[3] & 0xFFL);
    }

    private static byte[] bytes(String hex) {
        return HEX.parseHex(hex.replaceAll("\\s+", ""));
    }

    private static String hex(ByteBuffer buffer) {
        ByteBuffer copy = buffer.duplicate();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return HEX.formatHex(bytes);
    }

    private static void assertSecret(SecretKey secret, String expectedHex) {
        assertThat(HEX.formatHex(secret.getEncoded()), is(expectedHex));
    }

    private record OneRttPacket(byte[] packet, int keyPhase) {
    }
}
