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
import java.util.Optional;

import javax.crypto.SecretKey;

import io.helidon.common.buffers.BufferData;

final class QuicTls13ConnectionSecrets {
    // Full handshakes still use the empty-PSK branch of the TLS 1.3 secret tree, but the resumption path can now
    // pass an explicit cached PSK when the server selects an offered identity.
    private static final byte[] EMPTY_PSK = BufferData.EMPTY_BYTES;

    private final QuicVersion version;
    private final QuicTls13CipherSuite cipherSuite;
    private final QuicTls13SecretSchedule secretSchedule;
    private final SecretKey handshakeSecret;
    private final boolean clientMode;
    private final QuicAeadLimits.Confidentiality confidentialityLimits;

    private QuicTls13ConnectionSecrets(QuicVersion version,
                                       QuicTls13CipherSuite cipherSuite,
                                       QuicTls13SecretSchedule secretSchedule,
                                       SecretKey handshakeSecret,
                                       boolean clientMode,
                                       QuicAeadLimits.Confidentiality confidentialityLimits) {
        this.version = Objects.requireNonNull(version, "version");
        this.cipherSuite = Objects.requireNonNull(cipherSuite, "cipherSuite");
        this.secretSchedule = Objects.requireNonNull(secretSchedule, "secretSchedule");
        this.handshakeSecret = Objects.requireNonNull(handshakeSecret, "handshakeSecret");
        this.clientMode = clientMode;
        this.confidentialityLimits = Objects.requireNonNull(confidentialityLimits, "confidentialityLimits");
    }

    static QuicTls13ConnectionSecrets create(QuicVersion version,
                                             QuicTls13CipherSuite cipherSuite,
                                             byte[] sharedSecret,
                                             boolean clientMode) {
        return create(version,
                      cipherSuite,
                      EMPTY_PSK,
                      sharedSecret,
                      clientMode,
                      QuicAeadLimits.Confidentiality.defaults());
    }

    static QuicTls13ConnectionSecrets create(QuicVersion version,
                                             QuicTls13CipherSuite cipherSuite,
                                             byte[] sharedSecret,
                                             boolean clientMode,
                                             long aesGcmConfidentialityLimit,
                                             long chacha20Poly1305ConfidentialityLimit) {
        return create(version,
                      cipherSuite,
                      EMPTY_PSK,
                      sharedSecret,
                      clientMode,
                      new QuicAeadLimits.Confidentiality(aesGcmConfidentialityLimit,
                                                         chacha20Poly1305ConfidentialityLimit));
    }

    static QuicTls13ConnectionSecrets create(QuicVersion version,
                                             QuicTls13CipherSuite cipherSuite,
                                             byte[] psk,
                                             byte[] sharedSecret,
                                             boolean clientMode) {
        return create(version,
                      cipherSuite,
                      psk,
                      sharedSecret,
                      clientMode,
                      QuicAeadLimits.Confidentiality.defaults());
    }

    static QuicTls13ConnectionSecrets create(QuicVersion version,
                                             QuicTls13CipherSuite cipherSuite,
                                             byte[] psk,
                                             byte[] sharedSecret,
                                             boolean clientMode,
                                             QuicAeadLimits.Confidentiality confidentialityLimits) {
        Objects.requireNonNull(psk, "psk");
        Objects.requireNonNull(sharedSecret, "sharedSecret");
        QuicTls13SecretSchedule secretSchedule = new QuicTls13SecretSchedule(cipherSuite);
        SecretKey earlySecret = secretSchedule.extractEarlySecret(psk);
        SecretKey handshakeSecret = secretSchedule.deriveHandshakeSecret(earlySecret, sharedSecret);
        return new QuicTls13ConnectionSecrets(version,
                                              cipherSuite,
                                              secretSchedule,
                                              handshakeSecret,
                                              clientMode,
                                              confidentialityLimits);
    }

    static QuicTls13ConnectionSecrets create(QuicVersion version,
                                             QuicTls13CipherSuite cipherSuite,
                                             QuicTlsKeySharePossession localKeyShare,
                                             QuicTlsKeyShareEntry peerKeyShare,
                                             boolean clientMode) {
        return create(version,
                      cipherSuite,
                      EMPTY_PSK,
                      localKeyShare,
                      peerKeyShare,
                      clientMode,
                      QuicAeadLimits.Confidentiality.defaults());
    }

    static QuicTls13ConnectionSecrets create(QuicVersion version,
                                             QuicTls13CipherSuite cipherSuite,
                                             QuicTlsKeySharePossession localKeyShare,
                                             QuicTlsKeyShareEntry peerKeyShare,
                                             boolean clientMode,
                                             long aesGcmConfidentialityLimit,
                                             long chacha20Poly1305ConfidentialityLimit) {
        return create(version,
                      cipherSuite,
                      EMPTY_PSK,
                      localKeyShare,
                      peerKeyShare,
                      clientMode,
                      new QuicAeadLimits.Confidentiality(aesGcmConfidentialityLimit,
                                                         chacha20Poly1305ConfidentialityLimit));
    }

    static QuicTls13ConnectionSecrets create(QuicVersion version,
                                             QuicTls13CipherSuite cipherSuite,
                                             byte[] psk,
                                             QuicTlsKeySharePossession localKeyShare,
                                             QuicTlsKeyShareEntry peerKeyShare,
                                             boolean clientMode) {
        return create(version,
                      cipherSuite,
                      psk,
                      localKeyShare,
                      peerKeyShare,
                      clientMode,
                      QuicAeadLimits.Confidentiality.defaults());
    }

    static QuicTls13ConnectionSecrets create(QuicVersion version,
                                             QuicTls13CipherSuite cipherSuite,
                                             byte[] psk,
                                             QuicTlsKeySharePossession localKeyShare,
                                             QuicTlsKeyShareEntry peerKeyShare,
                                             boolean clientMode,
                                             QuicAeadLimits.Confidentiality confidentialityLimits) {
        Objects.requireNonNull(psk, "psk");
        Objects.requireNonNull(localKeyShare, "localKeyShare");
        return create(version,
                      cipherSuite,
                      psk,
                      localKeyShare.sharedSecret(peerKeyShare),
                      clientMode,
                      confidentialityLimits);
    }

    static QuicTls13ConnectionSecrets forServerHello(QuicVersion version,
                                                     QuicTlsLocalKeyShares localKeyShares,
                                                     QuicTlsServerHelloMessage serverHello,
                                                     boolean clientMode) throws QuicTransportException {
        return forServerHello(version,
                              localKeyShares,
                              serverHello,
                              EMPTY_PSK,
                              clientMode,
                              QuicAeadLimits.Confidentiality.defaults());
    }

    static QuicTls13ConnectionSecrets forServerHello(QuicVersion version,
                                                     QuicTlsLocalKeyShares localKeyShares,
                                                     QuicTlsServerHelloMessage serverHello,
                                                     boolean clientMode,
                                                     long aesGcmConfidentialityLimit,
                                                     long chacha20Poly1305ConfidentialityLimit) throws QuicTransportException {
        return forServerHello(version,
                              localKeyShares,
                              serverHello,
                              EMPTY_PSK,
                              clientMode,
                              new QuicAeadLimits.Confidentiality(aesGcmConfidentialityLimit,
                                                                 chacha20Poly1305ConfidentialityLimit));
    }

    static QuicTls13ConnectionSecrets forServerHello(QuicVersion version,
                                                     QuicTlsLocalKeyShares localKeyShares,
                                                     QuicTlsServerHelloMessage serverHello,
                                                     byte[] psk,
                                                     boolean clientMode) throws QuicTransportException {
        return forServerHello(version,
                              localKeyShares,
                              serverHello,
                              psk,
                              clientMode,
                              QuicAeadLimits.Confidentiality.defaults());
    }

    static QuicTls13ConnectionSecrets forServerHello(QuicVersion version,
                                                     QuicTlsLocalKeyShares localKeyShares,
                                                     QuicTlsServerHelloMessage serverHello,
                                                     byte[] psk,
                                                     boolean clientMode,
                                                     QuicAeadLimits.Confidentiality confidentialityLimits)
            throws QuicTransportException {
        Objects.requireNonNull(psk, "psk");
        Objects.requireNonNull(localKeyShares, "localKeyShares");
        QuicTlsServerHelloMessage hello = Objects.requireNonNull(serverHello, "serverHello");
        if (hello.helloRetryRequest()) {
            throw QuicTlsHandshakeMessages.unexpectedMessage("Cannot derive connection secrets from HelloRetryRequest");
        }
        Optional<QuicTlsKeyShareEntry> peerKeyShare = hello.keyShare();
        if (peerKeyShare.isEmpty()) {
            throw QuicTlsHandshakeMessages.decodeError("Malformed ServerHello message: missing key_share extension");
        }

        QuicTls13CipherSuite cipherSuite;
        try {
            cipherSuite = QuicTls13CipherSuite.forCodePoint(hello.cipherSuite());
        } catch (IllegalArgumentException e) {
            throw QuicTlsHandshakeMessages.decodeError(
                    String.format("Unsupported TLS 1.3 cipher suite: 0x%04x", hello.cipherSuite()));
        }
        return create(version,
                      cipherSuite,
                      psk,
                      localKeyShares.sharedSecret(peerKeyShare.get()),
                      clientMode,
                      confidentialityLimits);
    }

    QuicTls13CipherSuite cipherSuite() {
        return cipherSuite;
    }

    SecretKey handshakeSecret() {
        return handshakeSecret;
    }

    SecretKey clientHandshakeTrafficSecret(byte[] serverHelloTranscriptHash) {
        return secretSchedule.deriveClientHandshakeTrafficSecret(handshakeSecret, serverHelloTranscriptHash);
    }

    SecretKey serverHandshakeTrafficSecret(byte[] serverHelloTranscriptHash) {
        return secretSchedule.deriveServerHandshakeTrafficSecret(handshakeSecret, serverHelloTranscriptHash);
    }

    QuicLongHeaderTrafficKeys deriveHandshakeTrafficKeys(byte[] serverHelloTranscriptHash) {
        return QuicLongHeaderTrafficKeys.create(version,
                                                cipherSuite,
                                                clientHandshakeTrafficSecret(serverHelloTranscriptHash),
                                                serverHandshakeTrafficSecret(serverHelloTranscriptHash),
                                                clientMode,
                                                confidentialityLimits.aesGcm(),
                                                confidentialityLimits.chacha20Poly1305());
    }

    SecretKey masterSecret() {
        return secretSchedule.deriveMasterSecret(handshakeSecret);
    }

    SecretKey clientApplicationTrafficSecret(byte[] applicationTrafficTranscriptHash) {
        return secretSchedule.deriveClientApplicationTrafficSecret(masterSecret(), applicationTrafficTranscriptHash);
    }

    SecretKey serverApplicationTrafficSecret(byte[] applicationTrafficTranscriptHash) {
        return secretSchedule.deriveServerApplicationTrafficSecret(masterSecret(), applicationTrafficTranscriptHash);
    }

    byte[] deriveResumptionPsk(byte[] clientFinishedTranscriptHash, byte[] ticketNonce) {
        SecretKey resumptionMasterSecret = secretSchedule.deriveResumptionMasterSecret(masterSecret(),
                                                                                       clientFinishedTranscriptHash);
        return secretSchedule.deriveResumptionPsk(resumptionMasterSecret, ticketNonce).getEncoded();
    }

    QuicOneRttTrafficKeys deriveOneRttTrafficKeys(byte[] applicationTrafficTranscriptHash) {
        return QuicOneRttTrafficKeys.create(version,
                                            cipherSuite,
                                            clientApplicationTrafficSecret(applicationTrafficTranscriptHash),
                                            serverApplicationTrafficSecret(applicationTrafficTranscriptHash),
                                            clientMode,
                                            confidentialityLimits.aesGcm(),
                                            confidentialityLimits.chacha20Poly1305());
    }
}
