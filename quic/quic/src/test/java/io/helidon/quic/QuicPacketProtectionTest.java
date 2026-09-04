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

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import javax.crypto.AEADBadTagException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicPacketProtectionTest {
    private static final HexFormat HEX = HexFormat.of();
    // The short-header vectors below are the ChaCha20-Poly1305 QUIC examples from RFC 9001 and RFC 9369, so one
    // shared schedule constant keeps the packet-protection and key-update assertions anchored to that cipher suite.
    private static final QuicTls13SecretSchedule SCHEDULE =
            new QuicTls13SecretSchedule(QuicTls13CipherSuite.TLS_CHACHA20_POLY1305_SHA256);
    // RFC 9001 Appendix A.5 QUIC v1 ChaCha20-Poly1305 short-header packet vector.
    private static final ChaChaPacketVector V1_VECTOR = new ChaChaPacketVector(
            QuicVersion.QUIC_V1,
            "9ac312a7f877468ebe69422748ad00a15443f18203a07d6060f688f30f21632b",
            "c6d98ff3441c3fe1b2182094f69caa2ed4b716b65488960a7a984979fb23e1c8",
            "e0459b3474bdd0e44a41c144",
            "25a282b9e82f06f21f488917a4fc8f1b73573685608597d0efcb076b0ab7a7a4",
            "1223504755036d556342ee9361d253421a826c9ecdf3c7148684b36b714881f9",
            654360564L,
            "4200bff4",
            "01",
            "655e5cd55c41f69080575d7999c25a5bfb",
            "5e5cd55c41f69080575d7999c25a5bfb",
            "aefefe7d03",
            "4cfe4189",
            "4cfe4189655e5cd55c41f69080575d7999c25a5bfb");
    // RFC 9369 Appendix A.5 QUIC v2 ChaCha20-Poly1305 short-header packet vector.
    private static final ChaChaPacketVector V2_VECTOR = new ChaChaPacketVector(
            QuicVersion.QUIC_V2,
            "9ac312a7f877468ebe69422748ad00a15443f18203a07d6060f688f30f21632b",
            "3bfcddd72bcf02541d7fa0dd1f5f9eeea817e09a6963a0e6c7df0f9a1bab90f2",
            "a6b5bc6ab7dafce30ffff5dd",
            "d659760d2ba434a226fd37b35c69e2da8211d10c4f12538787d65645d5d1b8e2",
            "c69374c49e3d2a9466fa689e49d476db5d0dfbc87d32ceeaa6343fd0ae4c7d88",
            654360564L,
            "4200bff4",
            "01",
            "0ae7b6b932bc27d786f4bc2bb20f2162ba",
            "e7b6b932bc27d786f4bc2bb20f2162ba",
            "97580e32bf",
            "5558b1c6",
            "5558b1c60ae7b6b932bc27d786f4bc2bb20f2162ba");

    @ParameterizedTest
    @MethodSource("vectors")
    void shouldDerivePacketProtectionKeysFromKnownVector(ChaChaPacketVector vector) throws Exception {
        QuicPacketProtectionKeys keys = deriveKeys(vector);

        assertThat(HEX.formatHex(keys.packetKey().getEncoded()), is(vector.key()));
        assertThat(HEX.formatHex(keys.iv()), is(vector.iv()));
        assertThat(HEX.formatHex(keys.headerProtectionKey().getEncoded()), is(vector.hp()));
        assertThat(HEX.formatHex(SCHEDULE.deriveNextPacketProtectionSecret(vector.version(),
                                                                           trafficSecret(vector))
                                         .getEncoded()),
                   is(vector.ku()));
    }

    @ParameterizedTest
    @MethodSource("vectors")
    void shouldEncryptChaCha20PacketFromKnownVector(ChaChaPacketVector vector) throws Exception {
        QuicPacketProtection protection = QuicPacketProtection.create(QuicTls13CipherSuite.TLS_CHACHA20_POLY1305_SHA256,
                                                                      deriveKeys(vector));
        byte[] plaintext = HEX.parseHex(vector.payloadPlaintext());
        ByteBuffer packet = ByteBuffer.allocate(vector.packet().length() / 2);
        packet.put(HEX.parseHex(vector.unprotectedHeader()));
        packet.put(plaintext);
        ByteBuffer header = packet.slice(0, vector.headerLength()).asReadOnlyBuffer();
        ByteBuffer payload = packet.slice(vector.headerLength(), plaintext.length);

        packet.position(vector.headerLength());
        protection.encryptPacket(vector.packetNumber(), header, payload, packet);
        assertThat(HEX.formatHex(packet.array()).substring(vector.headerLength() * 2), is(vector.payloadCiphertext()));

        protect(packet, vector, protection);

        assertThat(HEX.formatHex(packet.array()), is(vector.packet()));
    }

    @ParameterizedTest
    @MethodSource("vectors")
    void shouldDecryptChaCha20PacketFromKnownVector(ChaChaPacketVector vector) throws Exception {
        QuicPacketProtection protection = QuicPacketProtection.create(QuicTls13CipherSuite.TLS_CHACHA20_POLY1305_SHA256,
                                                                      deriveKeys(vector));
        ByteBuffer packet = ByteBuffer.wrap(HEX.parseHex(vector.packet()));

        unprotect(packet, vector, protection);
        ByteBuffer input = packet.asReadOnlyBuffer();
        packet.position(vector.headerLength());
        protection.decryptPacket(vector.packetNumber(), input, vector.headerLength(), packet);

        String expected = vector.unprotectedHeader() + vector.payloadPlaintext();
        assertThat(HEX.formatHex(packet.array()).substring(0, expected.length()), is(expected));
    }

    @ParameterizedTest
    @MethodSource("vectors")
    void shouldTranslateChaCha20AuthenticationFailure(ChaChaPacketVector vector) throws Exception {
        QuicPacketProtection protection = QuicPacketProtection.create(QuicTls13CipherSuite.TLS_CHACHA20_POLY1305_SHA256,
                                                                      deriveKeys(vector));
        ByteBuffer packet = ByteBuffer.wrap(HEX.parseHex(vector.packet()));
        unprotect(packet, vector, protection);
        packet.put(packet.limit() - 1, (byte) (packet.get(packet.limit() - 1) ^ 1));

        QuicPacketAuthenticationException failure =
                assertThrows(QuicPacketAuthenticationException.class,
                             () -> protection.decryptPacket(vector.packetNumber(),
                                                            packet.asReadOnlyBuffer(),
                                                            vector.headerLength(),
                                                            ByteBuffer.allocate(vector.payloadPlaintext().length() / 2)));
        assertThat(failure.getCause(), instanceOf(AEADBadTagException.class));
    }

    @ParameterizedTest
    @MethodSource("vectors")
    void shouldTranslateChaCha20EncryptionBufferOverflow(ChaChaPacketVector vector) throws Exception {
        QuicPacketProtection protection = QuicPacketProtection.create(QuicTls13CipherSuite.TLS_CHACHA20_POLY1305_SHA256,
                                                                      deriveKeys(vector));
        ByteBuffer packet = ByteBuffer.wrap(HEX.parseHex(vector.unprotectedHeader()));

        BufferOverflowException failure =
                assertThrows(BufferOverflowException.class,
                             () -> protection.encryptPacket(vector.packetNumber(),
                                                            packet.asReadOnlyBuffer(),
                                                            ByteBuffer.wrap(HEX.parseHex(vector.payloadPlaintext())),
                                                            ByteBuffer.allocate(0)));
        assertThat(failure.getCause(), instanceOf(ShortBufferException.class));
    }

    @ParameterizedTest
    @MethodSource("vectors")
    void shouldTranslateChaCha20DecryptionBufferOverflow(ChaChaPacketVector vector) throws Exception {
        QuicPacketProtection protection = QuicPacketProtection.create(QuicTls13CipherSuite.TLS_CHACHA20_POLY1305_SHA256,
                                                                      deriveKeys(vector));
        ByteBuffer packet = ByteBuffer.wrap(HEX.parseHex(vector.packet()));
        unprotect(packet, vector, protection);

        BufferOverflowException failure =
                assertThrows(BufferOverflowException.class,
                             () -> protection.decryptPacket(vector.packetNumber(),
                                                            packet.asReadOnlyBuffer(),
                                                            vector.headerLength(),
                                                            ByteBuffer.allocate(0)));
        assertThat(failure.getCause(), instanceOf(ShortBufferException.class));
    }

    @ParameterizedTest
    @MethodSource("vectors")
    void shouldEncryptChaCha20PacketUsingLongHeaderTrafficKeysFromKnownVector(
            ChaChaPacketVector vector) throws Exception {
        SecretKeySpec secret = trafficSecret(vector);
        QuicLongHeaderTrafficKeys keys = QuicLongHeaderTrafficKeys.create(vector.version(),
                                                                          QuicTls13CipherSuite.TLS_CHACHA20_POLY1305_SHA256,
                                                                          secret,
                                                                          secret,
                                                                          true);
        byte[] plaintext = HEX.parseHex(vector.payloadPlaintext());
        ByteBuffer packet = ByteBuffer.allocate(vector.packet().length() / 2);
        packet.put(HEX.parseHex(vector.unprotectedHeader()));
        packet.put(plaintext);
        ByteBuffer payload = packet.slice(vector.headerLength(), plaintext.length);

        packet.position(vector.headerLength());
        keys.encryptPacket(vector.packetNumber(),
                           _ -> packet.slice(0, vector.headerLength()).asReadOnlyBuffer(),
                           payload,
                           packet);
        assertThat(HEX.formatHex(packet.array()).substring(vector.headerLength() * 2),
                   is(vector.payloadCiphertext()));

        protect(packet, vector, keys);

        assertThat(HEX.formatHex(packet.array()), is(vector.packet()));
    }

    @ParameterizedTest
    @EnumSource(QuicTls13CipherSuite.class)
    void shouldEncryptPacketUsingExactOverlappingHeapPayload(QuicTls13CipherSuite cipherSuite) throws Exception {
        int prefixLength = 5;
        int headerLength = 11;
        int payloadLength = 37;
        int suffixLength = 7;
        long packetNumber = 0x0102_0304L;
        byte sentinel = (byte) 0x5a;
        byte[] headerBytes = new byte[headerLength];
        byte[] payloadBytes = new byte[payloadLength];
        for (int i = 0; i < headerBytes.length; i++) {
            headerBytes[i] = (byte) (0x20 + i);
        }
        for (int i = 0; i < payloadBytes.length; i++) {
            payloadBytes[i] = (byte) (0x40 + i);
        }

        QuicPacketProtection oracle = createProtection(cipherSuite);
        int protectedPacketLength = headerLength + payloadLength + QuicPacketProtection.AUTH_TAG_SIZE;
        ByteBuffer expected = ByteBuffer.allocate(protectedPacketLength);
        expected.put(headerBytes);
        oracle.encryptPacket(packetNumber,
                             ByteBuffer.wrap(headerBytes),
                             ByteBuffer.wrap(payloadBytes),
                             expected);

        QuicPacketProtection protection = createProtection(cipherSuite);
        byte[] backing = new byte[prefixLength + protectedPacketLength + suffixLength];
        Arrays.fill(backing, sentinel);
        ByteBuffer packet = ByteBuffer.wrap(backing);
        packet.position(prefixLength);
        packet.put(headerBytes);
        int payloadStart = packet.position();
        packet.put(payloadBytes);
        int packetLimit = prefixLength + protectedPacketLength;
        packet.limit(packetLimit);
        ByteBuffer header = packet.slice(prefixLength, headerLength);
        ByteBuffer payload = packet.slice(payloadStart, payloadLength);
        int headerLimit = header.limit();
        int payloadLimit = payload.limit();
        packet.position(payloadStart);
        assertThat(payload.isReadOnly(), is(false));
        assertThat(payload.array(), sameInstance(packet.array()));
        assertThat(payload.arrayOffset() + payload.position(),
                   is(packet.arrayOffset() + packet.position()));

        protection.encryptPacket(packetNumber, header, payload, packet);

        assertThat(header.position(), is(headerLimit));
        assertThat(header.limit(), is(headerLimit));
        assertThat(payload.position(), is(payloadLimit));
        assertThat(payload.limit(), is(payloadLimit));
        assertThat(packet.position(), is(packetLimit));
        assertThat(packet.limit(), is(packetLimit));
        assertThat(hex(packet.slice(prefixLength, protectedPacketLength)),
                   is(HEX.formatHex(expected.array())));
        assertThat(hex(packet.slice(prefixLength, headerLength)), is(HEX.formatHex(headerBytes)));
        for (int i = 0; i < prefixLength; i++) {
            assertThat(backing[i], is(sentinel));
        }
        for (int i = packetLimit; i < backing.length; i++) {
            assertThat(backing[i], is(sentinel));
        }
    }

    @ParameterizedTest
    @EnumSource(QuicTls13CipherSuite.class)
    void shouldPackHeaderProtectionMaskWithoutExposingScratch(QuicTls13CipherSuite cipherSuite) throws Exception {
        byte[] sampleBytes = new byte[QuicPacketProtection.HEADER_PROTECTION_SAMPLE_SIZE];
        for (int i = 0; i < sampleBytes.length; i++) {
            sampleBytes[i] = (byte) (0x30 + i);
        }
        QuicPacketProtection protection = createProtection(cipherSuite);
        ByteBuffer legacy = protection.computeHeaderProtectionMask(ByteBuffer.wrap(sampleBytes));
        long expected = ((long) legacy.get(0) & 0xff) << 32
                | ((long) legacy.get(1) & 0xff) << 24
                | ((long) legacy.get(2) & 0xff) << 16
                | ((long) legacy.get(3) & 0xff) << 8
                | (long) legacy.get(4) & 0xff;

        long packed = protection.computeHeaderProtectionMaskBits(ByteBuffer.wrap(sampleBytes));

        assertThat(packed, is(expected));
        assertThat(packed >>> 40, is(0L));
        byte firstMaskByte = legacy.get(0);
        legacy.put(0, (byte) (firstMaskByte ^ 0x7f));
        ByteBuffer secondLegacy = protection.computeHeaderProtectionMask(ByteBuffer.wrap(sampleBytes));
        assertThat(secondLegacy.get(0), is(firstMaskByte));
        assertThat(legacy.get(0), is((byte) (firstMaskByte ^ 0x7f)));
        assertThat(protection.computeHeaderProtectionMaskBits(ByteBuffer.wrap(sampleBytes)), is(expected));
    }

    @ParameterizedTest
    @EnumSource(QuicTls13CipherSuite.class)
    void shouldProtectPacketsAndHeadersConcurrently(QuicTls13CipherSuite cipherSuite) throws Exception {
        int taskCount = 4;
        byte[] headerBytes = new byte[24];
        byte[] payloadBytes = new byte[128];
        Arrays.fill(headerBytes, (byte) 0x31);
        Arrays.fill(payloadBytes, (byte) 0x71);
        QuicPacketProtection shared = createProtection(cipherSuite);
        QuicPacketProtection oracle = createProtection(cipherSuite);
        CyclicBarrier start = new CyclicBarrier(taskCount);
        ExecutorService executor = Executors.newFixedThreadPool(taskCount);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < taskCount; i++) {
                int taskIndex = i;
                results.add(executor.submit(() -> {
                    byte[] sampleBytes = new byte[QuicPacketProtection.HEADER_PROTECTION_SAMPLE_SIZE];
                    Arrays.fill(sampleBytes, (byte) (0x40 + taskIndex));
                    ByteBuffer packet = ByteBuffer.allocate(
                            headerBytes.length + payloadBytes.length + QuicPacketProtection.AUTH_TAG_SIZE);
                    packet.put(headerBytes);
                    start.await();
                    shared.encryptPacket(0x1000L + taskIndex,
                                         ByteBuffer.wrap(headerBytes),
                                         ByteBuffer.wrap(payloadBytes),
                                         packet);
                    long mask = shared.computeHeaderProtectionMaskBits(ByteBuffer.wrap(sampleBytes));
                    return HEX.formatHex(packet.array()) + ':' + Long.toHexString(mask);
                }));
            }

            for (int i = 0; i < taskCount; i++) {
                byte[] sampleBytes = new byte[QuicPacketProtection.HEADER_PROTECTION_SAMPLE_SIZE];
                Arrays.fill(sampleBytes, (byte) (0x40 + i));
                ByteBuffer expected = ByteBuffer.allocate(
                        headerBytes.length + payloadBytes.length + QuicPacketProtection.AUTH_TAG_SIZE);
                expected.put(headerBytes);
                oracle.encryptPacket(0x1000L + i,
                                     ByteBuffer.wrap(headerBytes),
                                     ByteBuffer.wrap(payloadBytes),
                                     expected);
                long expectedMask = oracle.computeHeaderProtectionMaskBits(ByteBuffer.wrap(sampleBytes));
                assertThat(results.get(i).get(),
                           is(HEX.formatHex(expected.array()) + ':' + Long.toHexString(expectedMask)));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @ParameterizedTest
    @EnumSource(QuicTls13CipherSuite.class)
    void shouldRecoverFromBadTagBeforeValidDecrypt(QuicTls13CipherSuite cipherSuite) throws Exception {
        byte[] headerBytes = new byte[24];
        byte[] payloadBytes = new byte[64];
        Arrays.fill(headerBytes, (byte) 0x31);
        Arrays.fill(payloadBytes, (byte) 0x71);
        long packetNumber = 0x0102_0304L;
        QuicPacketProtection protection = createProtection(cipherSuite);
        ByteBuffer encrypted = ByteBuffer.allocate(
                headerBytes.length + payloadBytes.length + QuicPacketProtection.AUTH_TAG_SIZE);
        encrypted.put(headerBytes);
        protection.encryptPacket(packetNumber,
                                 ByteBuffer.wrap(headerBytes),
                                 ByteBuffer.wrap(payloadBytes),
                                 encrypted);
        byte[] validPacket = encrypted.array().clone();
        byte[] badPacket = validPacket.clone();
        badPacket[badPacket.length - 1] ^= 1;

        assertThrows(QuicPacketAuthenticationException.class,
                     () -> protection.decryptPacket(packetNumber,
                                                    ByteBuffer.wrap(badPacket),
                                                    headerBytes.length,
                                                    ByteBuffer.allocate(payloadBytes.length)));
        ByteBuffer decrypted = ByteBuffer.allocate(payloadBytes.length);
        protection.decryptPacket(packetNumber,
                                 ByteBuffer.wrap(validPacket),
                                 headerBytes.length,
                                 decrypted);

        assertThat(decrypted.array(), is(payloadBytes));
    }

    @ParameterizedTest
    @MethodSource("vectors")
    void shouldDecryptChaCha20PacketUsingLongHeaderTrafficKeysFromKnownVector(
            ChaChaPacketVector vector) throws Exception {
        SecretKeySpec secret = trafficSecret(vector);
        QuicLongHeaderTrafficKeys keys = QuicLongHeaderTrafficKeys.create(vector.version(),
                                                                          QuicTls13CipherSuite.TLS_CHACHA20_POLY1305_SHA256,
                                                                          secret,
                                                                          secret,
                                                                          true);
        ByteBuffer packet = ByteBuffer.wrap(HEX.parseHex(vector.packet()));

        unprotect(packet, vector, keys);
        ByteBuffer input = packet.asReadOnlyBuffer();
        packet.position(vector.headerLength());
        keys.decryptPacket(vector.packetNumber(), -1, input, vector.headerLength(), packet);

        String expected = vector.unprotectedHeader() + vector.payloadPlaintext();
        assertThat(HEX.formatHex(packet.array()).substring(0, expected.length()), is(expected));
    }

    private static Stream<ChaChaPacketVector> vectors() {
        return Stream.of(V1_VECTOR, V2_VECTOR);
    }

    private static void protect(ByteBuffer packet,
                                ChaChaPacketVector vector,
                                QuicPacketProtection protection) throws QuicTransportException {
        applyHeaderProtection(packet, vector, protection, vector.protectedHeader());
    }

    private static void unprotect(ByteBuffer packet,
                                  ChaChaPacketVector vector,
                                  QuicPacketProtection protection) throws QuicTransportException {
        applyHeaderProtection(packet, vector, protection, vector.unprotectedHeader());
    }

    private static void protect(ByteBuffer packet,
                                ChaChaPacketVector vector,
                                QuicLongHeaderTrafficKeys keys) throws QuicTransportException {
        applyHeaderProtection(packet, vector, keys, false, vector.protectedHeader());
    }

    private static void unprotect(ByteBuffer packet,
                                  ChaChaPacketVector vector,
                                  QuicLongHeaderTrafficKeys keys) throws QuicTransportException {
        applyHeaderProtection(packet, vector, keys, true, vector.unprotectedHeader());
    }

    private static void applyHeaderProtection(ByteBuffer packet,
                                              ChaChaPacketVector vector,
                                              QuicPacketProtection protection,
                                              String expectedHeaderAfterMask) throws QuicTransportException {
        ByteBuffer sample = packet.slice(vector.packetNumberOffset() + 4,
                                         QuicPacketProtection.HEADER_PROTECTION_SAMPLE_SIZE);
        assertThat(hex(sample), is(vector.sample()));

        ByteBuffer mask = protection.computeHeaderProtectionMask(sample);
        assertThat(hex(mask), is(vector.mask()));

        packet.put(0, (byte) (packet.get(0) ^ (mask.get() & 0x1f)));
        maskPacketNumber(packet, vector.packetNumberOffset(), vector.packetNumberLength(), mask);
        assertThat(HEX.formatHex(packet.array()).substring(0, vector.headerLength() * 2),
                   is(expectedHeaderAfterMask));
    }

    private static void applyHeaderProtection(ByteBuffer packet,
                                              ChaChaPacketVector vector,
                                              QuicLongHeaderTrafficKeys keys,
                                              boolean incoming,
                                              String expectedHeaderAfterMask) throws QuicTransportException {
        ByteBuffer sample = packet.slice(vector.packetNumberOffset() + 4,
                                         QuicPacketProtection.HEADER_PROTECTION_SAMPLE_SIZE);
        assertThat(hex(sample), is(vector.sample()));

        ByteBuffer mask = keys.computeHeaderProtectionMask(incoming, sample);
        assertThat(hex(mask), is(vector.mask()));

        packet.put(0, (byte) (packet.get(0) ^ (mask.get() & 0x1f)));
        maskPacketNumber(packet, vector.packetNumberOffset(), vector.packetNumberLength(), mask);
        assertThat(HEX.formatHex(packet.array()).substring(0, vector.headerLength() * 2),
                   is(expectedHeaderAfterMask));
    }

    private static void maskPacketNumber(ByteBuffer packet,
                                         int packetNumberOffset,
                                         int packetNumberLength,
                                         ByteBuffer mask) {
        for (int i = 0; i < packetNumberLength; i++) {
            packet.put(packetNumberOffset + i, (byte) (packet.get(packetNumberOffset + i) ^ mask.get()));
        }
    }

    private static QuicPacketProtectionKeys deriveKeys(ChaChaPacketVector vector) throws Exception {
        return SCHEDULE.derivePacketProtectionKeys(vector.version(), trafficSecret(vector));
    }

    private static SecretKeySpec trafficSecret(ChaChaPacketVector vector) {
        return new SecretKeySpec(HEX.parseHex(vector.secret()), "TlsSecret");
    }

    private static QuicPacketProtection createProtection(QuicTls13CipherSuite cipherSuite) {
        byte[] trafficSecret = new byte[cipherSuite.hashLength()];
        Arrays.fill(trafficSecret, (byte) 0x7b);
        QuicPacketProtectionKeys keys =
                QuicPacketProtectionKeys.derive(QuicVersion.QUIC_V1,
                                                cipherSuite,
                                                new SecretKeySpec(trafficSecret, "TlsSecret"));
        return QuicPacketProtection.create(cipherSuite, keys);
    }

    private static String hex(ByteBuffer buffer) {
        ByteBuffer copy = buffer.duplicate();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return HEX.formatHex(bytes);
    }

    // Each record instance mirrors one RFC packet-protection example end-to-end so the test can check derivation,
    // payload protection, header protection, and packet reconstruction from the same source vector.
    private record ChaChaPacketVector(QuicVersion version,
                                      String secret,
                                      String key,
                                      String iv,
                                      String hp,
                                      String ku,
                                      long packetNumber,
                                      String unprotectedHeader,
                                      String payloadPlaintext,
                                      String payloadCiphertext,
                                      String sample,
                                      String mask,
                                      String protectedHeader,
                                      String packet) {
        int headerLength() {
            return unprotectedHeader.length() / 2;
        }

        int packetNumberOffset() {
            return 1;
        }

        int packetNumberLength() {
            return headerLength() - packetNumberOffset();
        }
    }
}
