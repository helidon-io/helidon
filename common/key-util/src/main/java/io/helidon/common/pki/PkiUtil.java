/*
 * Copyright (c) 2017, 2026 Oracle and/or its affiliates.
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

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.BinaryEncodable;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PEM;
import java.security.PEMDecoder;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.crypto.CryptoException;

/**
 * Utilities to handle PKI keystores, certificates and keys.
 */
final class PkiUtil {
    private static final System.Logger LOGGER = System.getLogger(PkiUtil.class.getName());

    private PkiUtil() {
    }

    static <T extends BinaryEncodable> T readPem(InputStream input, PEMDecoder decoder, Class<T> type) {
        Objects.requireNonNull(input, "PEM input stream must not be null");
        try {
            while (true) {
                // Select the envelope before decoding, so unrelated encrypted keys need no passphrase.
                PEM pem = decoder.decode(input, PEM.class);
                boolean matches = switch (pem.type()) {
                    case "PRIVATE KEY", "ENCRYPTED PRIVATE KEY", "RSA PRIVATE KEY" -> type == PrivateKey.class;
                    case "PUBLIC KEY" -> type == PublicKey.class;
                    default -> false;
                };
                if (matches) {
                    return decoder.decode(pem.toString(), type);
                }
            }
        } catch (IOException | IllegalArgumentException | ClassCastException | CryptoException e) {
            throw new PkiException("Failed to read PEM " + type.getSimpleName() + " from input stream", e);
        } finally {
            closePemStream(input);
        }
    }

    static List<X509Certificate> readPemCertificates(InputStream input) {
        Objects.requireNonNull(input, "Certificate input stream must not be null");
        try {
            var content = new ByteArrayInputStream(input.readAllBytes());
            var decoder = PEMDecoder.of();
            List<X509Certificate> certificates = new ArrayList<>();
            while (true) {
                content.mark(0);
                PEM pem;
                try {
                    // Read the envelope first, so unrelated keys do not need to be decrypted or decoded.
                    pem = decoder.decode(content, PEM.class);
                } catch (EOFException e) {
                    content.reset();
                    // EOF also reports truncated PEM. A tail without a PEM header is a clean end.
                    if (new String(content.readAllBytes(), StandardCharsets.US_ASCII).contains("-----BEGIN ")) {
                        throw e;
                    }
                    break;
                }
                if (pem.type().equals("CERTIFICATE")) {
                    certificates.add(decoder.decode(pem.toString(), X509Certificate.class));
                }
            }
            if (certificates.isEmpty()) {
                throw new PkiException("Found no certificates in input stream");
            }
            return certificates;
        } catch (IOException | IllegalArgumentException | ClassCastException e) {
            throw new PkiException("Failed to read certificates from input stream", e);
        } finally {
            closePemStream(input);
        }
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

                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        LOGGER.log(System.Logger.Level.DEBUG, "Added certificate under alias "
                                + alias
                                + " for "
                                + cert
                                .getIssuerX500Principal().getName()
                                + " to list of certificates");
                    }
                }
            }
        } catch (KeyStoreException e) {
            throw new PkiException("Failed to load certificates from keystore: " + keyStore, e);
        }

        return certs;
    }

    private static void closePemStream(InputStream input) {
        try {
            input.close();
        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.WARNING, "Failed to close a stream.", e);
        }
    }
}
