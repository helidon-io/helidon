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

import java.security.ProviderException;
import java.util.Set;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

final class QuicTlsCompatibility {
    // Keep the public create(...) failure prefix stable so compatibility checks and create-time validation
    // report the same public-facing configuration error.
    static final String INVALID_CONFIG_MESSAGE = "Cannot construct a QUIC TLS context with the given TLS configuration";
    // The public validator only admits TLS 1.3 suites whose packet protection Helidon already implements, so
    // isQuicCompatible(...) does not claim support for CCM-only configurations before the transport can use them.
    private static final Set<String> SUPPORTED_CIPHER_SUITES = Set.of(
            QuicTls13CipherSuite.TLS_AES_128_GCM_SHA256.name(),
            QuicTls13CipherSuite.TLS_AES_256_GCM_SHA384.name(),
            QuicTls13CipherSuite.TLS_CHACHA20_POLY1305_SHA256.name());
    // QUIC uses only TLS 1.3, so the validator checks the effective engine configuration for this exact protocol.
    private static final String TLS13 = "TLSv1.3";

    private QuicTlsCompatibility() {
    }

    static boolean isCompatible(QuicTlsConfigSnapshot config) {
        try {
            validatedSslContext(config);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static SSLContext validatedSslContext(QuicTlsConfigSnapshot config) {
        SSLParameters sslParameters = config.sslParameters();
        try {
            QuicTlsParameters.validateSupported(sslParameters);
        } catch (IllegalArgumentException e) {
            throw invalidConfig(e);
        }
        if (!QuicTlsParameters.allowsTls13(sslParameters)) {
            throw invalidConfig(null);
        }
        if (!containsSupportedNamedGroup(sslParameters.getNamedGroups())) {
            throw invalidConfig(null);
        }
        if (!supportsPublicMode(config, sslParameters)) {
            throw invalidConfig(null);
        }

        SSLContext sslContext = config.sslContext();

        validateEffectiveParameters(sslContext, sslParameters);
        return sslContext;
    }

    private static void validateEffectiveParameters(SSLContext sslContext, SSLParameters sslParameters) {
        try {
            SSLEngine sslEngine = sslContext.createSSLEngine();
            sslEngine.setSSLParameters(sslParameters);
            if (!containsTls13(sslEngine.getEnabledProtocols())) {
                throw invalidConfig(null);
            }
            if (!containsSupportedCipherSuite(sslEngine.getEnabledCipherSuites())) {
                throw invalidConfig(null);
            }
        } catch (IllegalArgumentException e) {
            if (INVALID_CONFIG_MESSAGE.equals(e.getMessage())) {
                throw e;
            }
            throw invalidConfig(e);
        } catch (ProviderException | UnsupportedOperationException | IllegalStateException e) {
            throw new IllegalStateException("Failed to validate SSLContext for QUIC TLS", e);
        }
    }

    private static boolean containsTls13(String[] protocols) {
        for (String protocol : protocols) {
            if (TLS13.equals(protocol)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsSupportedCipherSuite(String[] cipherSuites) {
        for (String cipherSuite : cipherSuites) {
            if (SUPPORTED_CIPHER_SUITES.contains(cipherSuite)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsSupportedNamedGroup(String[] namedGroups) {
        if (namedGroups == null || namedGroups.length == 0) {
            return true;
        }
        for (String namedGroup : namedGroups) {
            if (QuicTlsNamedGroup.isSupportedTlsName(namedGroup)) {
                return true;
            }
        }
        return false;
    }

    private static boolean supportsPublicMode(QuicTlsConfigSnapshot config, SSLParameters sslParameters) {
        return QuicTlsTrustManagers.supportsPublicClientEngine(config)
                || QuicTlsKeyManagers.supportsPublicServerEngine(config, sslParameters);
    }

    private static IllegalArgumentException invalidConfig(Throwable cause) {
        if (cause == null) {
            return new IllegalArgumentException(INVALID_CONFIG_MESSAGE);
        }
        return new IllegalArgumentException(INVALID_CONFIG_MESSAGE, cause);
    }
}
