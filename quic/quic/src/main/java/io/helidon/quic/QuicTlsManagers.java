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

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Objects;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

final class QuicTlsManagers {
    private QuicTlsManagers() {
    }

    static X509ExtendedKeyManager extended(X509KeyManager keyManager) {
        Objects.requireNonNull(keyManager, "keyManager");
        if (keyManager instanceof X509ExtendedKeyManager extended) {
            return extended;
        }
        return new X509ExtendedKeyManager() {
            @Override
            public String[] getClientAliases(String keyType, Principal[] issuers) {
                return keyManager.getClientAliases(keyType, issuers);
            }

            @Override
            public String chooseClientAlias(String[] keyTypes, Principal[] issuers, Socket socket) {
                return keyManager.chooseClientAlias(keyTypes, issuers, socket);
            }

            @Override
            public String[] getServerAliases(String keyType, Principal[] issuers) {
                return keyManager.getServerAliases(keyType, issuers);
            }

            @Override
            public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
                return keyManager.chooseServerAlias(keyType, issuers, socket);
            }

            @Override
            public X509Certificate[] getCertificateChain(String alias) {
                return keyManager.getCertificateChain(alias);
            }

            @Override
            public PrivateKey getPrivateKey(String alias) {
                return keyManager.getPrivateKey(alias);
            }

            @Override
            public String chooseEngineClientAlias(String[] keyTypes, Principal[] issuers, SSLEngine engine) {
                return keyManager.chooseClientAlias(keyTypes, issuers, null);
            }

            @Override
            public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
                return keyManager.chooseServerAlias(keyType, issuers, null);
            }
        };
    }

    static X509ExtendedTrustManager extended(X509TrustManager trustManager) {
        Objects.requireNonNull(trustManager, "trustManager");
        if (trustManager instanceof X509ExtendedTrustManager extended) {
            return extended;
        }
        return new X509ExtendedTrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                trustManager.checkClientTrusted(chain, authType);
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                trustManager.checkServerTrusted(chain, authType);
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return trustManager.getAcceptedIssuers();
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
                    throws CertificateException {
                trustManager.checkClientTrusted(chain, authType);
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
                    throws CertificateException {
                trustManager.checkServerTrusted(chain, authType);
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                    throws CertificateException {
                trustManager.checkClientTrusted(chain, authType);
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                    throws CertificateException {
                trustManager.checkServerTrusted(chain, authType);
                QuicTlsEndpointIdentity.checkServerIdentity(chain, engine);
            }
        };
    }
}
