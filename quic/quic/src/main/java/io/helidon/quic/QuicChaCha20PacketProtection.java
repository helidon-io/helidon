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
import java.security.GeneralSecurityException;
import java.security.ProviderException;
import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.ChaCha20ParameterSpec;
import javax.crypto.spec.IvParameterSpec;

final class QuicChaCha20PacketProtection implements QuicPacketProtection {
    // RFC 9001 Section 6.6 caps ChaCha20-Poly1305 at 2^36 failed decryptions before the connection must be abandoned.
    private static final long INTEGRITY_LIMIT = 1L << 36;

    private final SecretKey packetKey;
    private final byte[] iv;
    private final SecretKey headerProtectionKey;
    private final long confidentialityLimit;
    private final Cipher packetCipher;
    private final Cipher headerProtectionCipher;
    private final byte[] packetNonce;
    private final byte[] headerProtectionNonce = new byte[12];
    private final byte[] headerProtectionOutput = new byte[5];
    private final Lock packetCipherLock = new ReentrantLock();
    private final Lock headerProtectionCipherLock = new ReentrantLock();

    QuicChaCha20PacketProtection(SecretKey packetKey,
                                 byte[] iv,
                                 SecretKey headerProtectionKey,
                                 long confidentialityLimit) {
        this.packetKey = packetKey;
        this.iv = iv.clone();
        this.packetNonce = new byte[this.iv.length];
        this.headerProtectionKey = headerProtectionKey;
        this.confidentialityLimit = confidentialityLimit;
        try {
            this.packetCipher = Cipher.getInstance("ChaCha20-Poly1305");
            this.headerProtectionCipher = Cipher.getInstance("ChaCha20");
        } catch (GeneralSecurityException | ProviderException e) {
            throw QuicPacketProtection.internalError("Failed to initialize ChaCha20 packet protection", e);
        }
    }

    @Override
    public ByteBuffer computeHeaderProtectionMask(ByteBuffer sample) throws QuicTransportException {
        byte[] output = new byte[5];
        computeHeaderProtectionMask(sample, output);
        return ByteBuffer.wrap(output);
    }

    @Override
    public long computeHeaderProtectionMaskBits(ByteBuffer sample) throws QuicTransportException {
        return computeHeaderProtectionMask(sample, headerProtectionOutput);
    }

    private long computeHeaderProtectionMask(ByteBuffer sample, byte[] output) throws QuicTransportException {
        if (sample.remaining() != HEADER_PROTECTION_SAMPLE_SIZE) {
            throw new IllegalArgumentException("Invalid sample size");
        }

        headerProtectionCipherLock.lock();
        try {
            int offset = sample.position();
            int counter = sample.get(offset) & 0xff
                    | (sample.get(offset + 1) & 0xff) << 8
                    | (sample.get(offset + 2) & 0xff) << 16
                    | (sample.get(offset + 3) & 0xff) << 24;
            for (int i = 0; i < headerProtectionNonce.length; i++) {
                headerProtectionNonce[i] = sample.get(offset + Integer.BYTES + i);
            }
            ChaCha20ParameterSpec ivSpec = new ChaCha20ParameterSpec(headerProtectionNonce, counter);
            Arrays.fill(output, (byte) 0);
            // Reusing the same IV is expected for header protection, so decrypt mode avoids provider complaints.
            headerProtectionCipher.init(Cipher.DECRYPT_MODE, headerProtectionKey, ivSpec);
            headerProtectionCipher.doFinal(output, 0, output.length, output);
            return QuicPacketProtection.packHeaderProtectionMask(output);
        } catch (GeneralSecurityException | ProviderException e) {
            throw QuicPacketProtection.internalError("Failed to compute header protection mask", e);
        } finally {
            headerProtectionCipherLock.unlock();
        }
    }

    @Override
    public void encryptPacket(long packetNumber,
                              ByteBuffer packetHeader,
                              ByteBuffer packetPayload,
                              ByteBuffer output) throws QuicTransportException, BufferOverflowException {
        packetCipherLock.lock();
        try {
            QuicPacketProtection.packetIv(iv, packetNumber, packetNonce);
            IvParameterSpec ivSpec = new IvParameterSpec(packetNonce);
            packetCipher.init(Cipher.ENCRYPT_MODE, packetKey, ivSpec);
            packetCipher.updateAAD(packetHeader);
            packetCipher.doFinal(packetPayload, output);
        } catch (ShortBufferException e) {
            throw QuicPacketProtection.bufferOverflow(e);
        } catch (GeneralSecurityException | ProviderException e) {
            throw QuicPacketProtection.internalError("Encryption failed", e);
        } finally {
            packetCipherLock.unlock();
        }
    }

    @Override
    public void decryptPacket(long packetNumber,
                              ByteBuffer packet,
                              int headerLength,
                              ByteBuffer output)
            throws QuicPacketAuthenticationException, QuicTransportException, BufferOverflowException {
        packetCipherLock.lock();
        try {
            QuicPacketProtection.packetIv(iv, packetNumber, packetNonce);
            IvParameterSpec ivSpec = new IvParameterSpec(packetNonce);
            packetCipher.init(Cipher.DECRYPT_MODE, packetKey, ivSpec);
            int limit = packet.limit();
            packet.limit(packet.position() + headerLength);
            packetCipher.updateAAD(packet);
            packet.limit(limit);
            packetCipher.doFinal(packet, output);
        } catch (AEADBadTagException e) {
            throw new QuicPacketAuthenticationException("Packet authentication failed", e);
        } catch (ShortBufferException e) {
            throw QuicPacketProtection.bufferOverflow(e);
        } catch (GeneralSecurityException | ProviderException e) {
            throw QuicPacketProtection.internalError("Decryption failed", e);
        } finally {
            packetCipherLock.unlock();
        }
    }

    @Override
    public long confidentialityLimit() {
        return confidentialityLimit;
    }

    @Override
    public long integrityLimit() {
        return INTEGRITY_LIMIT;
    }
}
