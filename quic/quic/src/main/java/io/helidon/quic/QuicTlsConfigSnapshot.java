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
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import io.helidon.common.tls.Tls;
import io.helidon.common.tls.TlsConfig;

final class QuicTlsConfigSnapshot {
    private final long generation;
    private final SSLContext sslContext;
    private final SSLParameters sslParameters;
    private final X509KeyManager keyManager;
    private final X509TrustManager trustManager;
    private final SecureRandom secureRandom;
    private final int sessionCacheSize;
    private final Duration sessionTimeout;
    private final int maxHandshakeMessageSize;
    private final long aesGcmConfidentialityLimit;
    private final long chacha20Poly1305ConfidentialityLimit;

    private QuicTlsConfigSnapshot(long generation,
                                  SSLContext sslContext,
                                  SSLParameters sslParameters,
                                  X509KeyManager keyManager,
                                  X509TrustManager trustManager,
                                  SecureRandom secureRandom,
                                  int sessionCacheSize,
                                  Duration sessionTimeout,
                                  int maxHandshakeMessageSize,
                                  long aesGcmConfidentialityLimit,
                                  long chacha20Poly1305ConfidentialityLimit) {
        this.generation = generation;
        this.sslContext = Objects.requireNonNull(sslContext, "sslContext");
        this.sslParameters = QuicTlsParameters.copy(sslParameters);
        this.keyManager = keyManager;
        this.trustManager = trustManager;
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
        this.sessionCacheSize = sessionCacheSize;
        this.sessionTimeout = Objects.requireNonNull(sessionTimeout, "sessionTimeout");
        this.maxHandshakeMessageSize = maxHandshakeMessageSize;
        this.aesGcmConfidentialityLimit = aesGcmConfidentialityLimit;
        this.chacha20Poly1305ConfidentialityLimit = chacha20Poly1305ConfidentialityLimit;
    }

    static QuicTlsConfigSnapshot create(Tls tls, QuicRuntimeConfig runtimeConfig) {
        Objects.requireNonNull(tls, "tls");
        Objects.requireNonNull(runtimeConfig, "runtimeConfig");
        if (!tls.enabled()) {
            throw new IllegalArgumentException("Cannot construct a QUIC TLS context with disabled TLS");
        }
        SSLParameters sslParameters = tls.sslParameters();
        TlsConfig tlsConfig = tls.prototype();
        SecureRandom secureRandom = tlsConfig.secureRandom().orElse(null);
        if (secureRandom == null) {
            Optional<String> algorithm = tlsConfig.secureRandomAlgorithm();
            Optional<String> provider = tlsConfig.secureRandomProvider();
            try {
                if (algorithm.isEmpty()) {
                    if (provider.isPresent()) {
                        throw new IllegalArgumentException("Invalid configuration of secure random. Provider is configured to "
                                                                   + provider.orElseThrow()
                                                                   + ", but algorithm is not specified");
                    }
                    secureRandom = new SecureRandom();
                } else if (provider.isEmpty()) {
                    secureRandom = SecureRandom.getInstance(algorithm.orElseThrow());
                } else {
                    secureRandom = SecureRandom.getInstance(algorithm.orElseThrow(), provider.orElseThrow());
                }
            } catch (GeneralSecurityException | ProviderException e) {
                throw new IllegalStateException("Failed to create SecureRandom for QUIC TLS", e);
            }
        }
        while (true) {
            long generation = tls.generation();
            SSLContext sslContext = tls.sslContext();
            X509KeyManager keyManager = tls.keyManager().orElse(null);
            X509TrustManager trustManager = tls.resolvedTrustManager().orElse(null);
            if (generation == tls.generation()) {
                QuicAeadLimits.Confidentiality confidentialityLimits = runtimeConfig.confidentialityLimits();
                return new QuicTlsConfigSnapshot(generation,
                                                 sslContext,
                                                 sslParameters,
                                                 keyManager,
                                                 trustManager,
                                                 secureRandom,
                                                 tlsConfig.sessionCacheSize(),
                                                 tlsConfig.sessionTimeout(),
                                                 runtimeConfig.userConfig().maxHandshakeMessageSize(),
                                                 confidentialityLimits.aesGcm(),
                                                 confidentialityLimits.chacha20Poly1305());
            }
        }
    }

    long generation() {
        return generation;
    }

    SSLContext sslContext() {
        return sslContext;
    }

    SSLParameters sslParameters() {
        return QuicTlsParameters.copy(sslParameters);
    }

    Optional<X509KeyManager> keyManager() {
        return Optional.ofNullable(keyManager);
    }

    Optional<X509TrustManager> trustManager() {
        return Optional.ofNullable(trustManager);
    }

    SecureRandom secureRandom() {
        return secureRandom;
    }

    int sessionCacheSize() {
        return sessionCacheSize;
    }

    Duration sessionTimeout() {
        return sessionTimeout;
    }

    int maxHandshakeMessageSize() {
        return maxHandshakeMessageSize;
    }

    long aesGcmConfidentialityLimit() {
        return aesGcmConfidentialityLimit;
    }

    long chacha20Poly1305ConfidentialityLimit() {
        return chacha20Poly1305ConfidentialityLimit;
    }
}
