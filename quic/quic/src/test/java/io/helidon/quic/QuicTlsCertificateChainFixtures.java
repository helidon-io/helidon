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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;
import java.util.Properties;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;

final class QuicTlsCertificateChainFixtures {
    private static final Properties FIXTURES = new Properties();

    static {
        try (InputStream input = Objects.requireNonNull(
                QuicTlsCertificateChainFixtures.class.getResourceAsStream(
                        "/io/helidon/quic/tls-signature-chains.properties"),
                "TLS signature-chain fixtures")) {
            FIXTURES.load(input);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private QuicTlsCertificateChainFixtures() {
    }

    static X509Certificate[] chain(String leafName, String rootName) {
        return new X509Certificate[] {certificate(leafName), certificate(rootName)};
    }

    static X509ExtendedKeyManager ignoringSignaturePolicyKeyManager(boolean withCompatibleAlternative) {
        return new IgnoringSignaturePolicyKeyManager(withCompatibleAlternative);
    }

    private static X509Certificate certificate(String name) {
        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) certificateFactory.generateCertificate(
                    new ByteArrayInputStream(encoded(name)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to load TLS signature-chain certificate fixture " + name, e);
        }
    }

    private static byte[] encoded(String name) {
        String value = FIXTURES.getProperty(name);
        if (value == null) {
            throw new IllegalArgumentException("Unknown TLS signature-chain fixture: " + name);
        }
        return Base64.getDecoder().decode(value);
    }

    private static final class IgnoringSignaturePolicyKeyManager extends X509ExtendedKeyManager {
        private static final String INCOMPATIBLE_ALIAS = "incompatible";
        private static final String COMPATIBLE_ALIAS = "compatible";

        private final boolean withCompatibleAlternative;
        private final PrivateKey privateKey;
        private final X509Certificate[] incompatibleChain = chain("leaf-rsae-sha384", "rsae-root");
        private final X509Certificate[] compatibleChain = chain("leaf-rsae-sha256", "rsae-root");

        private IgnoringSignaturePolicyKeyManager(boolean withCompatibleAlternative) {
            this.withCompatibleAlternative = withCompatibleAlternative;
            try {
                this.privateKey = KeyFactory.getInstance("RSA")
                        .generatePrivate(new PKCS8EncodedKeySpec(encoded("leaf-private-key")));
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("Failed to load the TLS signature-chain private key fixture", e);
            }
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return supports(keyType) ? aliases() : null;
        }

        @Override
        public String chooseClientAlias(String[] keyTypes, Principal[] issuers, Socket socket) {
            return supports(keyTypes) ? INCOMPATIBLE_ALIAS : null;
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            return supports(keyType) ? aliases() : null;
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            return supports(keyType) ? INCOMPATIBLE_ALIAS : null;
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return switch (alias) {
                case INCOMPATIBLE_ALIAS -> incompatibleChain.clone();
                case COMPATIBLE_ALIAS -> compatibleChain.clone();
                default -> null;
            };
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return INCOMPATIBLE_ALIAS.equals(alias) || COMPATIBLE_ALIAS.equals(alias) ? privateKey : null;
        }

        @Override
        public String chooseEngineClientAlias(String[] keyTypes, Principal[] issuers, SSLEngine engine) {
            return supports(keyTypes) ? INCOMPATIBLE_ALIAS : null;
        }

        @Override
        public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
            return supports(keyType) ? INCOMPATIBLE_ALIAS : null;
        }

        private static boolean supports(String keyType) {
            return "RSA".equalsIgnoreCase(keyType);
        }

        private static boolean supports(String[] keyTypes) {
            if (keyTypes == null) {
                return false;
            }
            for (String keyType : keyTypes) {
                if (supports(keyType)) {
                    return true;
                }
            }
            return false;
        }

        private String[] aliases() {
            return withCompatibleAlternative
                    ? new String[] {INCOMPATIBLE_ALIAS, COMPATIBLE_ALIAS}
                    : new String[] {INCOMPATIBLE_ALIAS};
        }
    }
}
