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
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.ProviderException;

enum QuicTls13CipherSuite {
    TLS_AES_128_GCM_SHA256(0x1301,
                           "SHA-256",
                           "HKDF-SHA256",
                           "HmacSHA256",
                           "AES",
                           32,
                           16,
                           12),
    TLS_AES_256_GCM_SHA384(0x1302,
                           "SHA-384",
                           "HKDF-SHA384",
                           "HmacSHA384",
                           "AES",
                           48,
                           32,
                           12),
    TLS_CHACHA20_POLY1305_SHA256(0x1303,
                                 "SHA-256",
                                 "HKDF-SHA256",
                                 "HmacSHA256",
                                 "ChaCha20",
                                 32,
                                 32,
                                 12);

    private final int codePoint;
    private final String hashAlgorithm;
    private final String hkdfAlgorithm;
    private final String hmacAlgorithm;
    private final String packetKeyAlgorithm;
    private final int hashLength;
    private final int packetKeyLength;
    private final int ivLength;

    QuicTls13CipherSuite(int codePoint,
                         String hashAlgorithm,
                         String hkdfAlgorithm,
                         String hmacAlgorithm,
                         String packetKeyAlgorithm,
                         int hashLength,
                         int packetKeyLength,
                         int ivLength) {
        this.codePoint = codePoint;
        this.hashAlgorithm = hashAlgorithm;
        this.hkdfAlgorithm = hkdfAlgorithm;
        this.hmacAlgorithm = hmacAlgorithm;
        this.packetKeyAlgorithm = packetKeyAlgorithm;
        this.hashLength = hashLength;
        this.packetKeyLength = packetKeyLength;
        this.ivLength = ivLength;
    }

    static QuicTls13CipherSuite forName(String cipherSuite) {
        try {
            return valueOf(cipherSuite);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported TLS 1.3 cipher suite: " + cipherSuite, e);
        }
    }

    static QuicTls13CipherSuite forCodePoint(int codePoint) {
        for (QuicTls13CipherSuite value : values()) {
            if (value.codePoint == codePoint) {
                return value;
            }
        }
        throw new IllegalArgumentException(String.format("Unsupported TLS 1.3 cipher suite: 0x%04x", codePoint));
    }

    static QuicTls13CipherSuite fromServerHello(ByteBuffer message) throws QuicTransportException {
        int cipherSuite = QuicTlsServerHelloMessage.decode(message).cipherSuite();

        try {
            return forCodePoint(cipherSuite);
        } catch (IllegalArgumentException e) {
            throw QuicTlsHandshakeMessages.decodeError(
                    String.format("Unsupported TLS 1.3 cipher suite: 0x%04x", cipherSuite));
        }
    }

    int codePoint() {
        return codePoint;
    }

    String hkdfAlgorithm() {
        return hkdfAlgorithm;
    }

    String hmacAlgorithm() {
        return hmacAlgorithm;
    }

    String packetKeyAlgorithm() {
        return packetKeyAlgorithm;
    }

    int hashLength() {
        return hashLength;
    }

    int packetKeyLength() {
        return packetKeyLength;
    }

    int ivLength() {
        return ivLength;
    }

    boolean sameHash(QuicTls13CipherSuite other) {
        return other != null
                && hashLength == other.hashLength
                && hkdfAlgorithm.equals(other.hkdfAlgorithm)
                && hmacAlgorithm.equals(other.hmacAlgorithm);
    }

    byte[] digest(byte[] data) {
        try {
            MessageDigest digest = newDigest();
            digest.update(data);
            return digest.digest();
        } catch (ProviderException e) {
            throw new QuicTransportException("Failed to hash TLS data",
                                             0,
                                             QuicTransportErrors.INTERNAL_ERROR.code(),
                                             e);
        }
    }

    byte[] emptyHash() {
        try {
            return newDigest().digest();
        } catch (ProviderException e) {
            throw new QuicTransportException("Failed to hash empty TLS data",
                                             0,
                                             QuicTransportErrors.INTERNAL_ERROR.code(),
                                             e);
        }
    }

    MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(hashAlgorithm);
        } catch (GeneralSecurityException | ProviderException e) {
            throw new QuicTransportException("Failed to initialize TLS hash algorithm: " + hashAlgorithm,
                                             0,
                                             QuicTransportErrors.INTERNAL_ERROR.code(),
                                             e);
        }
    }
}
