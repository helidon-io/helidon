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

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.GCMParameterSpec;

final class QuicRetryIntegrity {
    private static final int GCM_TAG_BITS = 128;

    private QuicRetryIntegrity() {
    }

    static void sign(QuicVersion version,
                     ByteBuffer originalConnectionId,
                     ByteBuffer packet,
                     ByteBuffer output) throws BufferOverflowException, QuicTransportException {
        int connectionIdLength = validateConnectionId(originalConnectionId);
        Cipher cipher = retryCipher(version, Cipher.ENCRYPT_MODE);
        ByteBuffer packetData = packet.asReadOnlyBuffer();
        try {
            cipher.updateAAD(new byte[] {(byte) connectionIdLength});
            cipher.updateAAD(originalConnectionId.asReadOnlyBuffer());
            cipher.updateAAD(packetData);
            cipher.doFinal(ByteBuffer.allocate(0), output);
        } catch (ShortBufferException e) {
            throw QuicPacketProtection.bufferOverflow(e);
        } catch (GeneralSecurityException | ProviderException e) {
            throw internalError("Failed to sign packet", e);
        }
    }

    static void verify(QuicVersion version,
                       ByteBuffer originalConnectionId,
                       ByteBuffer packet) throws QuicPacketAuthenticationException, QuicTransportException {
        int connectionIdLength = validateConnectionId(originalConnectionId);
        if (packet.remaining() < QuicAesPacketProtection.AUTH_TAG_SIZE) {
            throw new IllegalArgumentException("Retry packet is too short");
        }
        Cipher cipher = retryCipher(version, Cipher.DECRYPT_MODE);
        ByteBuffer aadPacket = packet.asReadOnlyBuffer();
        aadPacket.limit(aadPacket.limit() - QuicAesPacketProtection.AUTH_TAG_SIZE);
        ByteBuffer tag = packet.asReadOnlyBuffer();
        tag.position(tag.limit() - QuicAesPacketProtection.AUTH_TAG_SIZE);
        try {
            cipher.updateAAD(new byte[] {(byte) connectionIdLength});
            cipher.updateAAD(originalConnectionId.asReadOnlyBuffer());
            cipher.updateAAD(aadPacket);
            ByteBuffer outBuffer = ByteBuffer.allocate(cipher.getOutputSize(tag.remaining()));
            cipher.doFinal(tag, outBuffer);
        } catch (AEADBadTagException e) {
            throw new QuicPacketAuthenticationException("Retry integrity verification failed", e);
        } catch (GeneralSecurityException | ProviderException e) {
            throw internalError("Failed to verify packet", e);
        }
    }

    private static Cipher retryCipher(QuicVersion version, int mode) throws QuicTransportException {
        QuicTlsVersionData versionData = QuicTlsVersionData.forVersion(version);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, versionData.retryKey(), new GCMParameterSpec(GCM_TAG_BITS, versionData.retryIv()));
            return cipher;
        } catch (GeneralSecurityException | ProviderException e) {
            throw internalError("Retry integrity cipher not available", e);
        }
    }

    private static int validateConnectionId(ByteBuffer originalConnectionId) {
        int connectionIdLength = originalConnectionId.remaining();
        if (connectionIdLength < 0 || connectionIdLength >= 256) {
            throw new IllegalArgumentException("connection ID length");
        }
        return connectionIdLength;
    }

    private static QuicTransportException internalError(String reason, Throwable cause) {
        return new QuicTransportException(reason, 0, QuicTransportErrors.INTERNAL_ERROR.code(), cause);
    }
}
