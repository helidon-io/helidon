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

import java.security.Principal;
import java.security.PrivateKey;
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Objects;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

final class QuicTlsManagerCallbacks {
    private QuicTlsManagerCallbacks() {
    }

    static String chooseClientAlias(X509KeyManager keyManager,
                                    String[] keyTypes,
                                    Principal[] issuers,
                                    QuicTlsCallbackEngine engine) {
        Objects.requireNonNull(keyManager, "keyManager");
        Objects.requireNonNull(keyTypes, "keyTypes");
        requireMode(engine, true);
        try {
            return QuicTlsManagers.extended(keyManager).chooseEngineClientAlias(keyTypes, issuers, engine);
        } catch (ProviderException e) {
            throw QuicTlsHandshakeMessages.internalError("Client key manager failed to choose a certificate alias", e);
        }
    }

    static String chooseServerAlias(X509KeyManager keyManager,
                                    String keyType,
                                    Principal[] issuers,
                                    QuicTlsCallbackEngine engine) {
        Objects.requireNonNull(keyManager, "keyManager");
        Objects.requireNonNull(keyType, "keyType");
        requireMode(engine, false);
        try {
            return QuicTlsManagers.extended(keyManager).chooseEngineServerAlias(keyType, issuers, engine);
        } catch (ProviderException e) {
            throw QuicTlsHandshakeMessages.internalError("Server key manager failed to choose a certificate alias", e);
        }
    }

    static PrivateKey privateKey(X509KeyManager keyManager, String alias) {
        try {
            return keyManager.getPrivateKey(alias);
        } catch (ProviderException e) {
            throw QuicTlsHandshakeMessages.internalError("Key manager failed to provide the local private key", e);
        }
    }

    static X509Certificate[] certificateChain(X509KeyManager keyManager, String alias) {
        try {
            return keyManager.getCertificateChain(alias);
        } catch (ProviderException e) {
            throw QuicTlsHandshakeMessages.internalError("Key manager failed to provide the local certificate chain", e);
        }
    }

    static boolean sameCertificateIdentity(X509Certificate selected, X509Certificate alternative) {
        Objects.requireNonNull(selected, "selected");
        Objects.requireNonNull(alternative, "alternative");
        try {
            PublicKey selectedPublicKey = selected.getPublicKey();
            PublicKey alternativePublicKey = alternative.getPublicKey();
            if (selectedPublicKey == null || alternativePublicKey == null) {
                return false;
            }
            byte[] selectedPublicKeyEncoding = selectedPublicKey.getEncoded();
            byte[] alternativePublicKeyEncoding = alternativePublicKey.getEncoded();
            if (selectedPublicKeyEncoding == null || alternativePublicKeyEncoding == null) {
                return false;
            }
            // Alias enumeration has no SSLEngine context. Restrict alternatives to the identity and usage selected by
            // the key manager so signature-policy recovery cannot cross an SNI or client-certificate identity boundary.
            return selected.getSubjectX500Principal().equals(alternative.getSubjectX500Principal())
                    && Arrays.equals(selectedPublicKeyEncoding, alternativePublicKeyEncoding)
                    && selected.getBasicConstraints() == alternative.getBasicConstraints()
                    && Arrays.equals(selected.getExtensionValue("2.5.29.17"),
                                     alternative.getExtensionValue("2.5.29.17"))
                    && Arrays.equals(selected.getExtensionValue("2.5.29.15"),
                                     alternative.getExtensionValue("2.5.29.15"))
                    && Arrays.equals(selected.getExtensionValue("2.5.29.37"),
                                     alternative.getExtensionValue("2.5.29.37"));
        } catch (ProviderException e) {
            return false;
        }
    }

    static void checkServerTrusted(X509TrustManager trustManager,
                                   X509Certificate[] chain,
                                   String authType,
                                   QuicTlsCallbackEngine engine) {
        Objects.requireNonNull(trustManager, "trustManager");
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(authType, "authType");
        requireMode(engine, true);
        try {
            QuicTlsManagers.extended(trustManager).checkServerTrusted(chain, authType, engine);
        } catch (CertificateException e) {
            throw QuicTlsHandshakeMessages.certificateFailure("Server certificate validation failed", e);
        } catch (ProviderException e) {
            throw QuicTlsHandshakeMessages.internalError("Server trust manager failed", e);
        }
    }

    static void checkClientTrusted(X509TrustManager trustManager,
                                   X509Certificate[] chain,
                                   String authType,
                                   QuicTlsCallbackEngine engine) {
        Objects.requireNonNull(trustManager, "trustManager");
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(authType, "authType");
        requireMode(engine, false);
        try {
            QuicTlsManagers.extended(trustManager).checkClientTrusted(chain, authType, engine);
        } catch (CertificateException e) {
            throw QuicTlsHandshakeMessages.certificateFailure("Client certificate validation failed", e);
        } catch (ProviderException e) {
            throw QuicTlsHandshakeMessages.internalError("Client trust manager failed", e);
        }
    }

    static QuicTlsCallbackEngine callbackEngine(boolean clientMode,
                                                SSLParameters sslParameters,
                                                String[] localSupportedSignatureSchemes,
                                                String[] peerSupportedSignatureSchemes,
                                                String peerHost,
                                                int peerPort) {
        SSLParameters effectiveParameters = sslParameters == null ? QuicTlsParameters.defaultParameters() : sslParameters;
        QuicTlsCallbackEngine engine = peerHost == null
                ? new QuicTlsCallbackEngine(effectiveParameters,
                                            localSupportedSignatureSchemes,
                                            peerSupportedSignatureSchemes)
                : new QuicTlsCallbackEngine(peerHost,
                                            peerPort,
                                            effectiveParameters,
                                            localSupportedSignatureSchemes,
                                            peerSupportedSignatureSchemes);
        engine.setUseClientMode(clientMode);
        return engine;
    }

    private static void requireMode(QuicTlsCallbackEngine engine, boolean clientMode) {
        Objects.requireNonNull(engine, "engine");
        if (engine.getUseClientMode() != clientMode) {
            throw new IllegalArgumentException(clientMode
                                                       ? "Client TLS callback requires a client-mode engine"
                                                       : "Server TLS callback requires a server-mode engine");
        }
    }
}
