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

import java.util.Objects;

import javax.crypto.SecretKey;

final class QuicPacketProtectionKeys {
    private final SecretKey packetKey;
    private final byte[] iv;
    private final SecretKey headerProtectionKey;

    private QuicPacketProtectionKeys(SecretKey packetKey,
                                     byte[] iv,
                                     SecretKey headerProtectionKey) {
        this.packetKey = packetKey;
        this.iv = iv.clone();
        this.headerProtectionKey = headerProtectionKey;
    }

    static QuicPacketProtectionKeys derive(QuicVersion version,
                                           QuicTls13CipherSuite cipherSuite,
                                           SecretKey trafficSecret) {
        return derive(version, cipherSuite, trafficSecret, null);
    }

    static QuicPacketProtectionKeys derive(QuicVersion version,
                                           QuicTls13CipherSuite cipherSuite,
                                           SecretKey trafficSecret,
                                           SecretKey headerProtectionKey) {
        QuicTlsVersionData versionData = QuicTlsVersionData.forVersion(version);
        SecretKey packetKey = QuicTlsHkdf.expandSecret(cipherSuite.hkdfAlgorithm(),
                                                       trafficSecret,
                                                       versionData.keyLabel(),
                                                       cipherSuite.packetKeyLength(),
                                                       cipherSuite.packetKeyAlgorithm());
        byte[] iv = QuicTlsHkdf.expandData(cipherSuite.hkdfAlgorithm(),
                                           trafficSecret,
                                           versionData.ivLabel(),
                                           cipherSuite.ivLength());
        SecretKey effectiveHeaderProtectionKey = headerProtectionKey == null
                ? QuicTlsHkdf.expandSecret(cipherSuite.hkdfAlgorithm(),
                                           trafficSecret,
                                           versionData.hpLabel(),
                                           cipherSuite.packetKeyLength(),
                                           cipherSuite.packetKeyAlgorithm())
                : Objects.requireNonNull(headerProtectionKey, "headerProtectionKey");
        return new QuicPacketProtectionKeys(packetKey, iv, effectiveHeaderProtectionKey);
    }

    SecretKey packetKey() {
        return packetKey;
    }

    byte[] iv() {
        return iv.clone();
    }

    SecretKey headerProtectionKey() {
        return headerProtectionKey;
    }
}
