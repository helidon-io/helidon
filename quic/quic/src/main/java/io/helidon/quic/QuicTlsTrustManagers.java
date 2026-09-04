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

import javax.net.ssl.X509TrustManager;

final class QuicTlsTrustManagers {
    // The Helidon-owned client path can validate peers on public APIs only when it has an actual trust manager; if a
    // caller supplied only an opaque SSLContext, the client engine cannot recover the embedded trust configuration.
    static final String CLIENT_TRUST_MANAGER_REQUIRED_MESSAGE =
            "Helidon-owned QUIC TLS client mode requires an accessible X509TrustManager";
    // Server-side client authentication needs the same accessible trust-manager surface so the Helidon-owned QUIC TLS
    // path can validate peer certificates without reaching into the SSLContext's provider internals.
    static final String SERVER_CLIENT_AUTH_TRUST_MANAGER_REQUIRED_MESSAGE =
            "Helidon-owned QUIC TLS server mode requires an accessible X509TrustManager for client authentication";
    private QuicTlsTrustManagers() {
    }

    static boolean supportsPublicClientEngine(QuicTlsConfigSnapshot config) {
        return optionalTrustManager(config) != null;
    }

    static X509TrustManager requiredTrustManager(QuicTlsConfigSnapshot config) {
        X509TrustManager trustManager = optionalTrustManager(config);
        if (trustManager == null) {
            throw new IllegalArgumentException(CLIENT_TRUST_MANAGER_REQUIRED_MESSAGE);
        }
        return trustManager;
    }

    static X509TrustManager optionalTrustManager(QuicTlsConfigSnapshot config) {
        Objects.requireNonNull(config, "config");
        return config.trustManager().orElse(null);
    }
}
