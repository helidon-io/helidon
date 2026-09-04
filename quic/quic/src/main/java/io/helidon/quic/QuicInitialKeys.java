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

final class QuicInitialKeys {
    // QUIC Initial secrets are always derived with HKDF-SHA256, before any negotiated TLS cipher suite exists.
    private static final String HKDF_ALGORITHM = "HKDF-SHA256";
    // The Initial secret tree is SHA-256 based, so every derived Initial traffic secret is 32 bytes long.
    private static final int HASH_LENGTH = 32;
    // QUIC Initial protection is fixed to the TLS_AES_128_GCM_SHA256 key schedule by the QUIC TLS RFCs; it is not
    // negotiated from the endpoint TLS cipher-suite list.
    private static final QuicTls13CipherSuite INITIAL_CIPHER_SUITE = QuicTls13CipherSuite.TLS_AES_128_GCM_SHA256;

    private final QuicLongHeaderTrafficKeys trafficKeys;

    private QuicInitialKeys(QuicLongHeaderTrafficKeys trafficKeys) {
        this.trafficKeys = trafficKeys;
    }

    static QuicInitialKeys create(QuicVersion version,
                                  byte[] connectionId,
                                  boolean clientMode) {
        return create(version,
                      connectionId,
                      clientMode,
                      QuicAeadLimits.DEFAULT_AES_GCM_CONFIDENTIALITY_LIMIT,
                      QuicAeadLimits.DEFAULT_CHACHA20_POLY1305_CONFIDENTIALITY_LIMIT);
    }

    static QuicInitialKeys create(QuicVersion version,
                                  byte[] connectionId,
                                  boolean clientMode,
                                  long aesGcmConfidentialityLimit,
                                  long chacha20Poly1305ConfidentialityLimit) {
        QuicTlsVersionData versionData = QuicTlsVersionData.forVersion(version);
        SecretKey initialSecret = QuicTlsHkdf.extractSecret(HKDF_ALGORITHM,
                                                            "TlsInitialSecret",
                                                            versionData.initialSalt(),
                                                            connectionId);
        SecretKey clientSecret = QuicTlsHkdf.expandSecret(HKDF_ALGORITHM,
                                                          initialSecret,
                                                          "client in",
                                                          HASH_LENGTH,
                                                          "TlsClientInitialTrafficSecret");
        SecretKey serverSecret = QuicTlsHkdf.expandSecret(HKDF_ALGORITHM,
                                                          initialSecret,
                                                          "server in",
                                                          HASH_LENGTH,
                                                          "TlsServerInitialTrafficSecret");
        return new QuicInitialKeys(QuicLongHeaderTrafficKeys.create(version,
                                                                    INITIAL_CIPHER_SUITE,
                                                                    clientSecret,
                                                                    serverSecret,
                                                                    clientMode,
                                                                    aesGcmConfidentialityLimit,
                                                                    chacha20Poly1305ConfidentialityLimit));
    }

    int headerProtectionSampleSize() {
        return trafficKeys.headerProtectionSampleSize();
    }

    ByteBuffer computeHeaderProtectionMask(boolean incoming, ByteBuffer sample) throws QuicTransportException {
        return trafficKeys.computeHeaderProtectionMask(incoming, sample);
    }

    long computeHeaderProtectionMaskBits(boolean incoming, ByteBuffer sample) throws QuicTransportException {
        return trafficKeys.computeHeaderProtectionMaskBits(incoming, sample);
    }

    void encryptPacket(long packetNumber,
                       IntFunction<ByteBuffer> headerGenerator,
                       ByteBuffer packetPayload,
                       ByteBuffer output) throws QuicTransportException {
        trafficKeys.encryptPacket(packetNumber, headerGenerator, packetPayload, output);
    }

    void decryptPacket(long packetNumber,
                       int keyPhase,
                       ByteBuffer packet,
                       int headerLength,
                       ByteBuffer output)
            throws QuicPacketAuthenticationException, QuicTransportException {
        trafficKeys.decryptPacket(packetNumber, keyPhase, packet, headerLength, output);
    }
}
