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
import java.util.function.IntFunction;

import javax.crypto.SecretKey;

final class QuicLongHeaderTrafficKeys {
    private final QuicPacketProtection readProtection;
    private final QuicPacketProtection writeProtection;

    private QuicLongHeaderTrafficKeys(QuicPacketProtection readProtection,
                                      QuicPacketProtection writeProtection) {
        this.readProtection = readProtection;
        this.writeProtection = writeProtection;
    }

    static QuicLongHeaderTrafficKeys create(QuicVersion version,
                                            QuicTls13CipherSuite cipherSuite,
                                            SecretKey clientTrafficSecret,
                                            SecretKey serverTrafficSecret,
                                            boolean clientMode) {
        return create(version,
                      cipherSuite,
                      clientTrafficSecret,
                      serverTrafficSecret,
                      clientMode,
                      QuicAeadLimits.DEFAULT_AES_GCM_CONFIDENTIALITY_LIMIT,
                      QuicAeadLimits.DEFAULT_CHACHA20_POLY1305_CONFIDENTIALITY_LIMIT);
    }

    static QuicLongHeaderTrafficKeys create(QuicVersion version,
                                            QuicTls13CipherSuite cipherSuite,
                                            SecretKey clientTrafficSecret,
                                            SecretKey serverTrafficSecret,
                                            boolean clientMode,
                                            long aesGcmConfidentialityLimit,
                                            long chacha20Poly1305ConfidentialityLimit) {
        QuicPacketProtection clientProtection = protection(version,
                                                           cipherSuite,
                                                           clientTrafficSecret,
                                                           aesGcmConfidentialityLimit,
                                                           chacha20Poly1305ConfidentialityLimit);
        QuicPacketProtection serverProtection = protection(version,
                                                           cipherSuite,
                                                           serverTrafficSecret,
                                                           aesGcmConfidentialityLimit,
                                                           chacha20Poly1305ConfidentialityLimit);
        return clientMode
                ? new QuicLongHeaderTrafficKeys(serverProtection, clientProtection)
                : new QuicLongHeaderTrafficKeys(clientProtection, serverProtection);
    }

    int headerProtectionSampleSize() {
        return QuicPacketProtection.HEADER_PROTECTION_SAMPLE_SIZE;
    }

    ByteBuffer computeHeaderProtectionMask(boolean incoming, ByteBuffer sample) throws QuicTransportException {
        return (incoming ? readProtection : writeProtection).computeHeaderProtectionMask(sample);
    }

    long computeHeaderProtectionMaskBits(boolean incoming, ByteBuffer sample) throws QuicTransportException {
        return (incoming ? readProtection : writeProtection).computeHeaderProtectionMaskBits(sample);
    }

    void encryptPacket(long packetNumber,
                       IntFunction<ByteBuffer> headerGenerator,
                       ByteBuffer packetPayload,
                       ByteBuffer output) throws QuicTransportException {
        ByteBuffer header = headerGenerator.apply(0);
        writeProtection.encryptPacket(packetNumber, header, packetPayload, output);
    }

    void decryptPacket(long packetNumber,
                       int keyPhase,
                       ByteBuffer packet,
                       int headerLength,
                       ByteBuffer output)
            throws QuicPacketAuthenticationException, QuicTransportException {
        if (keyPhase != -1) {
            throw new IllegalArgumentException("Unexpected key phase value: " + keyPhase);
        }
        readProtection.decryptPacket(packetNumber, packet, headerLength, output);
    }

    private static QuicPacketProtection protection(QuicVersion version,
                                                   QuicTls13CipherSuite cipherSuite,
                                                   SecretKey trafficSecret,
                                                   long aesGcmConfidentialityLimit,
                                                   long chacha20Poly1305ConfidentialityLimit) {
        QuicPacketProtectionKeys keys = QuicPacketProtectionKeys.derive(version, cipherSuite, trafficSecret);
        return QuicPacketProtection.create(cipherSuite,
                                           keys,
                                           aesGcmConfidentialityLimit,
                                           chacha20Poly1305ConfidentialityLimit);
    }
}
