/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.pki;

import java.io.InputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utilities to handle PKI keystores, certificates and keys.
 */
final class PkiUtil {
    private static final Logger LOGGER = Logger.getLogger(PkiUtil.class.getName());

    private PkiUtil() {
    }

    static KeyStore loadKeystore(String keystoreType, InputStream storeStream, char[] keystorePassphrase, String message) {
        Objects.requireNonNull(storeStream, "Keystore input stream must not be null");

        try {
            KeyStore ks = KeyStore.getInstance(keystoreType);
            ks.load(storeStream, keystorePassphrase);
            return ks;
        } catch (Exception e) {
            throw new PkiException("Failed to read " + keystoreType + " keystore: " + message, e);
        }
    }

    static PrivateKey loadPrivateKey(KeyStore keyStore, String keyAlias, char[] keyPassphrase) {
        try {
            Key key = keyStore.getKey(keyAlias, keyPassphrase);
            if (key instanceof PrivateKey) {
                return (PrivateKey) key;
            }
            throw new PkiException("Key stored under alias " + keyAlias + " is not a private key, but: " + key);
        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException e) {
            throw new PkiException("Failed to load private key under alias " + keyAlias, e);
        }
    }

    static List<X509Certificate> loadCertChain(KeyStore keyStore, String certAlias) {
        try {
            Certificate[] certificates = keyStore.getCertificateChain(certAlias);
            if (null == certificates) {
                throw new PkiException("There is no X.509 certificate chain under alias " + certAlias);
            }

            return Stream.of(certificates).map(it -> (X509Certificate) it).collect(Collectors.toList());
        } catch (KeyStoreException e) {
            throw new PkiException("Failed to load certificate under alias " + certAlias, e);
        }

    }

    static X509Certificate loadCertificate(KeyStore keyStore, String certAlias) {
        try {
            Certificate certificate = keyStore.getCertificate(certAlias);
            if (null == certificate) {
                throw new PkiException("There is no X.509 certificate under alias " + certAlias);
            }
            if (certificate instanceof X509Certificate) {
                return (X509Certificate) certificate;
            }
            throw new PkiException("Certificate under alias " + certAlias + " is not an X.509 certificate, but: " + certificate);
        } catch (KeyStoreException e) {
            throw new PkiException("Failed to load certificate under alias " + certAlias, e);
        }
    }

    static List<X509Certificate> loadCertificates(KeyStore keyStore) {
        List<X509Certificate> certs = new LinkedList<>();
        try {
            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (keyStore.isCertificateEntry(alias)) {
                    X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias);
                    certs.add(cert);

                    LOGGER.finest(() -> "Added certificate under alis " + alias + " for " + cert
                            .getSubjectDN() + " to list of certificates");
                }
            }
        } catch (KeyStoreException e) {
            throw new PkiException("Failed to load certificates from keystore: " + keyStore, e);
        }

        return certs;
    }
}
