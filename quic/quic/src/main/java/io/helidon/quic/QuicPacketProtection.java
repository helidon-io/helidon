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

import javax.crypto.ShortBufferException;

interface QuicPacketProtection {
    /**
     * Authentication tag size, in bytes, produced by supported packet-protection AEADs.
     */
    int AUTH_TAG_SIZE = 16;
    /**
     * Ciphertext sample size, in bytes, used by QUIC header protection.
     */
    int HEADER_PROTECTION_SAMPLE_SIZE = 16;

    static QuicPacketProtection create(QuicTls13CipherSuite cipherSuite,
                                       QuicPacketProtectionKeys keys) {
        return create(cipherSuite,
                      keys,
                      QuicAeadLimits.DEFAULT_AES_GCM_CONFIDENTIALITY_LIMIT,
                      QuicAeadLimits.DEFAULT_CHACHA20_POLY1305_CONFIDENTIALITY_LIMIT);
    }

    static QuicPacketProtection create(QuicTls13CipherSuite cipherSuite,
                                       QuicPacketProtectionKeys keys,
                                       long aesGcmConfidentialityLimit,
                                       long chacha20Poly1305ConfidentialityLimit) {
        return switch (cipherSuite) {
            case TLS_AES_128_GCM_SHA256, TLS_AES_256_GCM_SHA384 -> new QuicAesPacketProtection(keys.packetKey(),
                                                                                               keys.iv(),
                                                                                               keys.headerProtectionKey(),
                                                                                               aesGcmConfidentialityLimit);
            case TLS_CHACHA20_POLY1305_SHA256 -> new QuicChaCha20PacketProtection(keys.packetKey(),
                                                                                  keys.iv(),
                                                                                  keys.headerProtectionKey(),
                                                                                  chacha20Poly1305ConfidentialityLimit);
        };
    }

    static void packetIv(byte[] iv, long packetNumber, byte[] destination) {
        System.arraycopy(iv, 0, destination, 0, iv.length);
        int index = destination.length - 1;
        long current = packetNumber;
        while (current > 0) {
            destination[index] ^= (byte) (current & 0xff);
            current >>>= 8;
            index--;
        }
    }

    static long packHeaderProtectionMask(byte[] mask) {
        return ((long) mask[0] & 0xff) << 32
                | ((long) mask[1] & 0xff) << 24
                | ((long) mask[2] & 0xff) << 16
                | ((long) mask[3] & 0xff) << 8
                | (long) mask[4] & 0xff;
    }

    static QuicTransportException internalError(String reason) {
        return new QuicTransportException(reason, 0, QuicTransportErrors.INTERNAL_ERROR);
    }

    static QuicTransportException internalError(String reason, Throwable cause) {
        return new QuicTransportException(reason, 0, QuicTransportErrors.INTERNAL_ERROR.code(), cause);
    }

    static BufferOverflowException bufferOverflow(ShortBufferException cause) {
        BufferOverflowException exception = new BufferOverflowException();
        exception.initCause(cause);
        return exception;
    }

    ByteBuffer computeHeaderProtectionMask(ByteBuffer sample) throws QuicTransportException;

    /**
     * Compute the five-byte header protection mask packed into the low 40 bits of a {@code long}.
     * The first mask byte occupies bits 39 through 32 and the fifth occupies bits 7 through 0.
     *
     * @param sample ciphertext sample
     * @return packed five-byte header protection mask
     * @throws QuicTransportException if the mask cannot be computed
     */
    long computeHeaderProtectionMaskBits(ByteBuffer sample) throws QuicTransportException;

    void encryptPacket(long packetNumber,
                       ByteBuffer packetHeader,
                       ByteBuffer packetPayload,
                       ByteBuffer output) throws QuicTransportException;

    void decryptPacket(long packetNumber,
                       ByteBuffer packet,
                       int headerLength,
                       ByteBuffer output)
            throws QuicPacketAuthenticationException, QuicTransportException;

    long confidentialityLimit();

    long integrityLimit();
}
