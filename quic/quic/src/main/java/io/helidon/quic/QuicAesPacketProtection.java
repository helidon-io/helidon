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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.GCMParameterSpec;

final class QuicAesPacketProtection implements QuicPacketProtection {
    private static final int GCM_TAG_BITS = 128;
    // RFC 9001 Section 6.6 caps AES-GCM at 2^52 failed decryptions before the connection must be abandoned.
    private static final long INTEGRITY_LIMIT = 1L << 52;

    private final SecretKey packetKey;
    private final byte[] iv;
    private final SecretKey headerProtectionKey;
    private final long confidentialityLimit;
    private final Cipher packetCipher;
    private final Cipher headerProtectionCipher;
    private final byte[] packetNonce;
    private final byte[] headerProtectionBlock = new byte[HEADER_PROTECTION_SAMPLE_SIZE];
    private final Lock packetCipherLock = new ReentrantLock();
    private final Lock headerProtectionCipherLock = new ReentrantLock();

    QuicAesPacketProtection(SecretKey packetKey,
                            byte[] iv,
                            SecretKey headerProtectionKey,
                            long confidentialityLimit) {
        this.packetKey = packetKey;
        this.iv = iv.clone();
        this.packetNonce = new byte[this.iv.length];
        this.headerProtectionKey = headerProtectionKey;
        this.confidentialityLimit = confidentialityLimit;
        try {
            this.packetCipher = Cipher.getInstance("AES/GCM/NoPadding");
            this.headerProtectionCipher = Cipher.getInstance("AES/ECB/NoPadding");
        } catch (GeneralSecurityException | ProviderException e) {
            throw QuicPacketProtection.internalError("Failed to initialize AES packet protection", e);
        }
    }

    @Override
    public ByteBuffer computeHeaderProtectionMask(ByteBuffer sample) throws QuicTransportException {
        byte[] output = new byte[HEADER_PROTECTION_SAMPLE_SIZE];
        computeHeaderProtectionMask(sample, output);
        return ByteBuffer.wrap(output);
    }

    @Override
    public long computeHeaderProtectionMaskBits(ByteBuffer sample) throws QuicTransportException {
        return computeHeaderProtectionMask(sample, headerProtectionBlock);
    }

    @Override
    public void encryptPacket(long packetNumber,
                              ByteBuffer packetHeader,
                              ByteBuffer packetPayload,
                              ByteBuffer output) throws QuicTransportException, BufferOverflowException {
        packetCipherLock.lock();
        try {
            QuicPacketProtection.packetIv(iv, packetNumber, packetNonce);
            GCMParameterSpec ivSpec = new GCMParameterSpec(GCM_TAG_BITS, packetNonce);
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
            GCMParameterSpec ivSpec = new GCMParameterSpec(GCM_TAG_BITS, packetNonce);
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

    private long computeHeaderProtectionMask(ByteBuffer sample, byte[] output) throws QuicTransportException {
        if (sample.remaining() != HEADER_PROTECTION_SAMPLE_SIZE) {
            throw new IllegalArgumentException("Invalid sample size");
        }
        headerProtectionCipherLock.lock();
        try {
            sample.get(output);
            headerProtectionCipher.init(Cipher.ENCRYPT_MODE, headerProtectionKey);
            headerProtectionCipher.doFinal(output, 0, output.length, output, 0);
            return QuicPacketProtection.packHeaderProtectionMask(output);
        } catch (GeneralSecurityException | ProviderException e) {
            throw QuicPacketProtection.internalError("Failed to compute header protection mask", e);
        } finally {
            headerProtectionCipherLock.unlock();
        }
    }
}
