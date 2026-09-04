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

import javax.net.ssl.SSLParameters;
import javax.net.ssl.X509KeyManager;

final class QuicTlsKeyManagers {
    // The Helidon-owned server path can select certificates on public APIs only when the key material is exposed as an
    // X509KeyManager; an opaque SSLContext alone is not enough to recover aliases, chains, or private keys.
    static final String SERVER_KEY_MANAGER_REQUIRED_MESSAGE =
            "Helidon-owned QUIC TLS server mode requires an accessible X509KeyManager";

    private QuicTlsKeyManagers() {
    }

    static boolean supportsPublicServerEngine(QuicTlsConfigSnapshot config, SSLParameters sslParameters) {
        return effectiveKeyManager(config) != null
                && (!requiresClientAuth(sslParameters) || QuicTlsTrustManagers.optionalTrustManager(config) != null);
    }

    static X509KeyManager requiredKeyManager(QuicTlsConfigSnapshot config) {
        X509KeyManager keyManager = effectiveKeyManager(config);
        if (keyManager == null) {
            throw new IllegalArgumentException(SERVER_KEY_MANAGER_REQUIRED_MESSAGE);
        }
        return keyManager;
    }

    static String unsupportedServerModeMessage(QuicTlsConfigSnapshot config, SSLParameters sslParameters) {
        if (effectiveKeyManager(config) == null) {
            return SERVER_KEY_MANAGER_REQUIRED_MESSAGE;
        }
        if (requiresClientAuth(sslParameters) && QuicTlsTrustManagers.optionalTrustManager(config) == null) {
            return QuicTlsTrustManagers.SERVER_CLIENT_AUTH_TRUST_MANAGER_REQUIRED_MESSAGE;
        }
        return SERVER_KEY_MANAGER_REQUIRED_MESSAGE;
    }

    private static X509KeyManager effectiveKeyManager(QuicTlsConfigSnapshot config) {
        Objects.requireNonNull(config, "config");
        return config.keyManager().orElse(null);
    }

    private static boolean requiresClientAuth(SSLParameters sslParameters) {
        Objects.requireNonNull(sslParameters, "sslParameters");
        return sslParameters.getNeedClientAuth() || sslParameters.getWantClientAuth();
    }
}
