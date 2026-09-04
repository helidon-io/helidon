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

package io.helidon.webclient.tests.http3;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import io.helidon.common.tls.Tls;
import io.helidon.webclient.http3.Http3Client;

public final class Http3TlsSupport {
    private static final char[] KEY_PASSWORD = "password".toCharArray();
    private static final String SERVER_KEYSTORE = "server.p12";
    private static final String CLIENT_TRUSTSTORE = "client.p12";

    private Http3TlsSupport() {
    }

    public static Http3TlsMaterials load() throws Exception {
        KeyStore serverKeyStore = loadStore(SERVER_KEYSTORE);
        KeyStore clientTrustStore = loadStore(CLIENT_TRUSTSTORE);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(serverKeyStore, KEY_PASSWORD);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(clientTrustStore);

        SSLContext serverContext = SSLContext.getInstance("TLS");
        serverContext.init(kmf.getKeyManagers(), null, new SecureRandom());

        SSLContext clientContext = SSLContext.getInstance("TLS");
        clientContext.init(null, tmf.getTrustManagers(), new SecureRandom());

        String keyAlias = findKeyAlias(serverKeyStore);
        PrivateKey privateKey = (PrivateKey) serverKeyStore.getKey(keyAlias, KEY_PASSWORD);
        List<X509Certificate> serverChain = certificateChain(serverKeyStore, keyAlias);
        List<X509Certificate> trustCertificates = loadCertificates(clientTrustStore);

        return new Http3TlsMaterials(serverContext,
                                     clientContext,
                                     privateKey,
                                     serverChain,
                                     trustCertificates,
                                     extractKeyManager(kmf.getKeyManagers()),
                                     extractTrustManager(tmf.getTrustManagers()));
    }

    private static KeyStore loadStore(String resourceName) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream stream = Http3TlsSupport.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (stream == null) {
                throw new IllegalStateException("Missing test resource: " + resourceName);
            }
            keyStore.load(stream, KEY_PASSWORD);
        }
        return keyStore;
    }

    private static String findKeyAlias(KeyStore keyStore) throws Exception {
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (keyStore.isKeyEntry(alias)) {
                return alias;
            }
        }
        throw new IllegalStateException("Keystore does not contain a private key");
    }

    private static List<X509Certificate> certificateChain(KeyStore keyStore, String alias) throws Exception {
        Certificate[] chain = keyStore.getCertificateChain(alias);
        if (chain == null || chain.length == 0) {
            throw new IllegalStateException("No certificate chain for alias " + alias);
        }
        List<X509Certificate> certificates = new ArrayList<>(chain.length);
        for (Certificate certificate : chain) {
            if (certificate instanceof X509Certificate x509Certificate) {
                certificates.add(x509Certificate);
            }
        }
        return List.copyOf(certificates);
    }

    private static List<X509Certificate> loadCertificates(KeyStore keyStore) throws Exception {
        List<X509Certificate> certificates = new ArrayList<>();
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            Certificate certificate = keyStore.getCertificate(aliases.nextElement());
            if (certificate instanceof X509Certificate x509Certificate) {
                certificates.add(x509Certificate);
            }
        }
        return List.copyOf(certificates);
    }

    private static X509KeyManager extractKeyManager(KeyManager[] keyManagers) {
        for (KeyManager keyManager : keyManagers) {
            if (keyManager instanceof X509KeyManager x509KeyManager) {
                return x509KeyManager;
            }
        }
        return null;
    }

    private static X509TrustManager extractTrustManager(TrustManager[] trustManagers) {
        for (TrustManager trustManager : trustManagers) {
            if (trustManager instanceof X509TrustManager x509TrustManager) {
                return x509TrustManager;
            }
        }
        return null;
    }

    public static final class Http3TlsMaterials {
        private final SSLContext serverContext;
        private final SSLContext clientContext;
        private final PrivateKey privateKey;
        private final List<X509Certificate> privateKeyChain;
        private final List<X509Certificate> trustCertificates;
        private final X509KeyManager keyManager;
        private final X509TrustManager trustManager;

        Http3TlsMaterials(SSLContext serverContext,
                          SSLContext clientContext,
                          PrivateKey privateKey,
                          List<X509Certificate> privateKeyChain,
                          List<X509Certificate> trustCertificates,
                          X509KeyManager keyManager,
                          X509TrustManager trustManager) {
            this.serverContext = serverContext;
            this.clientContext = clientContext;
            this.privateKey = privateKey;
            this.privateKeyChain = privateKeyChain;
            this.trustCertificates = trustCertificates;
            this.keyManager = keyManager;
            this.trustManager = trustManager;
        }

        public Tls serverTls() {
            return Tls.builder()
                    .privateKey(privateKey)
                    .privateKeyCertChain(privateKeyChain)
                    .build();
        }

        public SSLContext serverSslContext() {
            return serverContext;
        }

        public SSLContext clientSslContext() {
            return clientContext;
        }

        public Tls clientTls() {
            return Tls.builder()
                    .trust(trust -> trust.certs(trustCertificates))
                    .build();
        }

        public Tls clientTlsHttp3() {
            return Tls.builder()
                    .trust(trust -> trust.certs(trustCertificates))
                    .applicationProtocols(List.of(Http3Client.PROTOCOL_ID))
                    .enabledProtocols(List.of("TLSv1.3"))
                    .build();
        }

        public Tls clientTlsWithEnabledProtocols(List<String> protocols) {
            return Tls.builder()
                    .trust(trust -> trust.certs(trustCertificates))
                    .enabledProtocols(protocols)
                    .build();
        }

        List<X509Certificate> trustCertificates() {
            return trustCertificates;
        }

        X509TrustManager trustManager() {
            return trustManager;
        }

        X509KeyManager keyManager() {
            return keyManager;
        }
    }
}
