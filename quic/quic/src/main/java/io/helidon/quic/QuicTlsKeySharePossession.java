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
import java.util.concurrent.atomic.AtomicReference;

import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;

import io.helidon.common.NativeImageHelper;

final class QuicTlsKeySharePossession {
    private static final ClassValue<Boolean> ATTEMPT_DESTRUCTION = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            // Native images keep the direct best-effort call without requiring dynamic reflection metadata.
            if (NativeImageHelper.isNativeImage()) {
                return true;
            }
            try {
                // The inherited default only throws; provider implementations still receive a call for each key.
                return type.getMethod("destroy").getDeclaringClass() != Destroyable.class;
            } catch (NoSuchMethodException | SecurityException _) {
                return true;
            }
        }
    };

    private final QuicTlsNamedGroup namedGroup;
    private final PublicKey publicKey;
    private final AtomicReference<PrivateKey> privateKey;

    private QuicTlsKeySharePossession(QuicTlsNamedGroup namedGroup, PrivateKey privateKey, PublicKey publicKey) {
        this.namedGroup = Objects.requireNonNull(namedGroup, "namedGroup");
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey");
        this.privateKey = new AtomicReference<>(Objects.requireNonNull(privateKey, "privateKey"));
    }

    static QuicTlsKeySharePossession create(QuicTlsNamedGroup namedGroup, SecureRandom secureRandom) {
        KeyPair keyPair = namedGroup.generateKeyPair(Objects.requireNonNull(secureRandom, "secureRandom"));
        PrivateKey privateKey = keyPair.getPrivate();
        try {
            return new QuicTlsKeySharePossession(namedGroup, privateKey, keyPair.getPublic());
        } catch (RuntimeException | Error e) {
            if (privateKey != null) {
                destroy(privateKey);
            }
            throw e;
        }
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
        PrivateKey localPrivateKey = privateKey.getAndSet(null);
        if (localPrivateKey == null) {
            throw new IllegalStateException("TLS key share private key has been discarded");
        }
        try {
            Objects.requireNonNull(peerKeyShare, "peerKeyShare");
            if (peerKeyShare.namedGroup() != namedGroup) {
                throw QuicTlsHandshakeMessages.illegalParameter("Peer key share group does not match possession");
            }
            return namedGroup.deriveSharedSecret(localPrivateKey, peerKeyShare.keyExchange());
        } finally {
            destroy(localPrivateKey);
        }
    }

    void discard() {
        PrivateKey localPrivateKey = privateKey.getAndSet(null);
        if (localPrivateKey != null) {
            destroy(localPrivateKey);
        }
    }

    private static void destroy(PrivateKey privateKey) {
        if (!ATTEMPT_DESTRUCTION.get(privateKey.getClass())) {
            return;
        }
        try {
            privateKey.destroy();
        } catch (DestroyFailedException | RuntimeException _) {
            // Provider cleanup failure must not replace derivation errors or prevent other keys from being discarded.
        }
    }
}
