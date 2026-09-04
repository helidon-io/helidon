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

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Objects;

final class QuicTlsKeySharePossession {
    private final QuicTlsNamedGroup namedGroup;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    private QuicTlsKeySharePossession(QuicTlsNamedGroup namedGroup, PrivateKey privateKey, PublicKey publicKey) {
        this.namedGroup = Objects.requireNonNull(namedGroup, "namedGroup");
        this.privateKey = Objects.requireNonNull(privateKey, "privateKey");
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey");
    }

    static QuicTlsKeySharePossession create(QuicTlsNamedGroup namedGroup, SecureRandom secureRandom) {
        KeyPair keyPair = namedGroup.generateKeyPair(Objects.requireNonNull(secureRandom, "secureRandom"));
        return new QuicTlsKeySharePossession(namedGroup, keyPair.getPrivate(), keyPair.getPublic());
    }

    QuicTlsNamedGroup namedGroup() {
        return namedGroup;
    }

    QuicTlsKeyShareEntry keyShareEntry() {
        try {
            return QuicTlsKeyShareEntry.create(namedGroup, namedGroup.encodePublicKey(publicKey));
        } catch (IllegalArgumentException e) {
            throw QuicTlsHandshakeMessages.internalError("Failed to encode the local TLS key share", e);
        }
    }

    byte[] sharedSecret(QuicTlsKeyShareEntry peerKeyShare) {
        Objects.requireNonNull(peerKeyShare, "peerKeyShare");
        if (peerKeyShare.namedGroup() != namedGroup) {
            throw QuicTlsHandshakeMessages.illegalParameter("Peer key share group does not match possession");
        }
        return namedGroup.deriveSharedSecret(privateKey, peerKeyShare.keyExchange());
    }
}
