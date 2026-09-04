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

import java.security.AlgorithmConstraints;
import java.security.AlgorithmParameters;
import java.security.CryptoPrimitive;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.Set;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import io.helidon.common.tls.Tls;
import io.helidon.common.tls.TlsConfig;
import io.helidon.common.tls.TlsManager;
import io.helidon.common.tls.TlsMaterial;

final class QuicTlsTestSupport {
    private static final AlgorithmConstraints ALL_PERMITTING_ALGORITHM_CONSTRAINTS = new AlgorithmConstraints() {
        @Override
        public boolean permits(Set<CryptoPrimitive> primitives, String algorithm, AlgorithmParameters parameters) {
            return true;
        }

        @Override
        public boolean permits(Set<CryptoPrimitive> primitives, Key key) {
            return true;
        }

        @Override
        public boolean permits(Set<CryptoPrimitive> primitives,
                               String algorithm,
                               Key key,
                               AlgorithmParameters parameters) {
            return true;
        }
    };

    private QuicTlsTestSupport() {
    }

    static AlgorithmConstraints allPermittingAlgorithmConstraints() {
        return ALL_PERMITTING_ALGORITHM_CONSTRAINTS;
    }

    static Tls tls(X509KeyManager keyManager, X509TrustManager trustManager) {
        return tlsBuilder(keyManager, trustManager).build();
    }

    static TlsConfig.Builder tlsBuilder(X509KeyManager keyManager, X509TrustManager trustManager) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            KeyManager[] keyManagers = keyManager == null ? null : new KeyManager[] {keyManager};
            TrustManager[] trustManagers = trustManager == null ? null : new TrustManager[] {trustManager};
            sslContext.init(keyManagers, trustManagers, new SecureRandom());
            return Tls.builder()
                    .manager(manager(sslContext, keyManager, trustManager));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to create test TLS configuration", e);
        }
    }

    static TlsManager manager(SSLContext sslContext,
                              X509KeyManager keyManager,
                              X509TrustManager trustManager) {
        return new FixedTlsManager(sslContext, keyManager, trustManager);
    }

    static X509TrustManager defaultTrustManager() {
        try {
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init((KeyStore) null);
            for (TrustManager trustManager : factory.getTrustManagers()) {
                if (trustManager instanceof X509TrustManager x509TrustManager) {
                    return x509TrustManager;
                }
            }
            throw new IllegalStateException("Default TrustManagerFactory did not provide an X509TrustManager");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to create default test trust manager", e);
        }
    }

    private static final class FixedTlsManager implements TlsManager {
        private final SSLContext sslContext;
        private final X509KeyManager keyManager;
        private final X509TrustManager trustManager;

        private FixedTlsManager(SSLContext sslContext,
                                X509KeyManager keyManager,
                                X509TrustManager trustManager) {
            this.sslContext = sslContext;
            this.keyManager = keyManager;
            this.trustManager = trustManager;
        }

        @Override
        public String name() {
            return "test";
        }

        @Override
        public String type() {
            return "fixed";
        }

        @Override
        public void init(TlsConfig tls) {
        }

        @Override
        public void reload(TlsMaterial material) {
            throw new UnsupportedOperationException("Fixed test TLS manager does not support reload");
        }

        @Override
        public SSLContext sslContext() {
            return sslContext;
        }

        @Override
        public Optional<X509KeyManager> keyManager() {
            return Optional.ofNullable(keyManager);
        }

        @Override
        public Optional<X509TrustManager> trustManager() {
            return Optional.ofNullable(trustManager);
        }
    }
}
