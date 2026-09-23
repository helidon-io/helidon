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

import java.security.GeneralSecurityException;
import java.security.ProviderException;
import java.util.Arrays;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.SecretKey;

import io.helidon.common.buffers.BufferData;

final class QuicTls13SecretSchedule {
    // TLS 1.3 treats absent PSKs and empty transcript contexts as zero-length byte strings, so a shared constant keeps
    // the no-PSK and empty-context derivations explicit.
    private static final byte[] EMPTY = BufferData.EMPTY_BYTES;
    // Synthetic algorithm name for HKDF outputs that are still TLS traffic secrets, not packet keys yet.
    private static final String TLS_SECRET_ALGORITHM = "TlsSecret";

    private final QuicTls13CipherSuite cipherSuite;

    QuicTls13SecretSchedule(QuicTls13CipherSuite cipherSuite) {
        this.cipherSuite = Objects.requireNonNull(cipherSuite, "cipherSuite");
    }

    static QuicTls13SecretSchedule forCipherSuite(String cipherSuite) {
        return new QuicTls13SecretSchedule(QuicTls13CipherSuite.forName(cipherSuite));
    }

    SecretKey extractEarlySecret(byte[] psk) {
        return QuicTlsHkdf.extractSecret(cipherSuite.hkdfAlgorithm(),
                                         TLS_SECRET_ALGORITHM,
                                         new byte[cipherSuite.hashLength()],
                                         inputSecret(psk));
    }

    SecretKey deriveHandshakeSecret(SecretKey earlySecret, byte[] sharedSecret) {
        SecretKey derivedSalt = deriveSecret(earlySecret, "derived", cipherSuite.emptyHash());
        byte[] inputSecret = inputSecret(sharedSecret);
        try {
            return QuicTlsHkdf.extractSecret(cipherSuite.hkdfAlgorithm(),
                                             TLS_SECRET_ALGORITHM,
                                             derivedSalt.getEncoded(),
                                             inputSecret);
        } finally {
            Arrays.fill(inputSecret, (byte) 0);
        }
    }

    SecretKey deriveResumptionBinderKey(SecretKey earlySecret) {
        return deriveSecret(earlySecret, "res binder", cipherSuite.emptyHash());
    }

    SecretKey deriveMasterSecret(SecretKey handshakeSecret) {
        SecretKey derivedSalt = deriveSecret(handshakeSecret, "derived", cipherSuite.emptyHash());
        return QuicTlsHkdf.extractSecret(cipherSuite.hkdfAlgorithm(),
                                         TLS_SECRET_ALGORITHM,
                                         derivedSalt.getEncoded(),
                                         zeroSecret());
    }

    SecretKey deriveClientHandshakeTrafficSecret(SecretKey handshakeSecret, byte[] transcriptHash) {
        return deriveSecret(handshakeSecret, "c hs traffic", transcriptHash);
    }

    SecretKey deriveClientEarlyTrafficSecret(SecretKey earlySecret, byte[] transcriptHash) {
        return deriveSecret(earlySecret, "c e traffic", transcriptHash);
    }

    SecretKey deriveServerHandshakeTrafficSecret(SecretKey handshakeSecret, byte[] transcriptHash) {
        return deriveSecret(handshakeSecret, "s hs traffic", transcriptHash);
    }

    SecretKey deriveClientApplicationTrafficSecret(SecretKey masterSecret, byte[] transcriptHash) {
        return deriveSecret(masterSecret, "c ap traffic", transcriptHash);
    }

    SecretKey deriveServerApplicationTrafficSecret(SecretKey masterSecret, byte[] transcriptHash) {
        return deriveSecret(masterSecret, "s ap traffic", transcriptHash);
    }

    SecretKey deriveExporterMasterSecret(SecretKey masterSecret, byte[] transcriptHash) {
        return deriveSecret(masterSecret, "exp master", transcriptHash);
    }

    SecretKey deriveResumptionMasterSecret(SecretKey masterSecret, byte[] transcriptHash) {
        return deriveSecret(masterSecret, "res master", transcriptHash);
    }

    SecretKey deriveResumptionPsk(SecretKey resumptionMasterSecret, byte[] ticketNonce) {
        return QuicTlsHkdf.expandSecret(cipherSuite.hkdfAlgorithm(),
                                        resumptionMasterSecret,
                                        "resumption",
                                        normalize(ticketNonce),
                                        cipherSuite.hashLength(),
                                        TLS_SECRET_ALGORITHM);
    }

    SecretKey deriveFinishedKey(SecretKey trafficSecret) {
        return QuicTlsHkdf.expandSecret(cipherSuite.hkdfAlgorithm(),
                                        trafficSecret,
                                        "finished",
                                        cipherSuite.hashLength(),
                                        cipherSuite.hmacAlgorithm());
    }

    byte[] computeVerifyData(SecretKey finishedKey, byte[] transcriptHash) {
        try {
            Mac mac = Mac.getInstance(cipherSuite.hmacAlgorithm());
            mac.init(finishedKey);
            return mac.doFinal(normalize(transcriptHash));
        } catch (GeneralSecurityException | ProviderException | IllegalStateException e) {
            throw new QuicTransportException("Failed to compute TLS verify data",
                                             0,
                                             QuicTransportErrors.INTERNAL_ERROR.code(),
                                             e);
        }
    }

    SecretKey deriveNextPacketProtectionSecret(QuicVersion version, SecretKey currentTrafficSecret) {
        return QuicTlsHkdf.expandSecret(cipherSuite.hkdfAlgorithm(),
                                        currentTrafficSecret,
                                        QuicTlsVersionData.forVersion(version).keyUpdateLabel(),
                                        cipherSuite.hashLength(),
                                        TLS_SECRET_ALGORITHM);
    }

    QuicPacketProtectionKeys derivePacketProtectionKeys(QuicVersion version, SecretKey trafficSecret) {
        return QuicPacketProtectionKeys.derive(version, cipherSuite, trafficSecret);
    }

    private static byte[] normalize(byte[] data) {
        return data == null ? EMPTY : data.clone();
    }

    private SecretKey deriveSecret(SecretKey secret,
                                   String label,
                                   byte[] transcriptHash) {
        return QuicTlsHkdf.expandSecret(cipherSuite.hkdfAlgorithm(),
                                        secret,
                                        label,
                                        normalize(transcriptHash),
                                        cipherSuite.hashLength(),
                                        TLS_SECRET_ALGORITHM);
    }

    private byte[] inputSecret(byte[] secret) {
        return (secret == null || secret.length == 0) ? zeroSecret() : secret.clone();
    }

    private byte[] zeroSecret() {
        return new byte[cipherSuite.hashLength()];
    }
}
