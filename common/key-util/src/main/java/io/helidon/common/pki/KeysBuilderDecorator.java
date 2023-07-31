/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.common.configurable.Resource;
import io.helidon.common.configurable.ResourceException;

class KeysBuilderDecorator implements Prototype.BuilderDecorator<Keys.BuilderBase<?, ?>> {
    private static final System.Logger LOGGER = System.getLogger(Keys.class.getName());

    @Override
    public void decorate(Keys.BuilderBase<?, ?> target) {
        try {
            target.keystore().ifPresent(keystoreConfig -> updateFromKeystore(target, keystoreConfig));
            target.pem().ifPresent(pemConfig -> updateFromPem(target, pemConfig));

            if (target.publicKey().isEmpty() && target.publicCert().isPresent()) {
                // if we have public certificate and not public key, get the public key from the certificate
                target.publicKey(target.publicCert().get().getPublicKey());
            }

        } catch (ResourceException e) {
            throw new PkiException("Failed to load key configuration", e);
        }
    }

    private void updateFromPem(Keys.BuilderBase<?, ?> builder, PemKeys pemConfig) {
        if (builder.privateKey().isEmpty()) {
            pemConfig.key().ifPresent(resource -> {
                char[] passphrase = pemConfig.keyPassphrase().orElse(null);
                builder.privateKey(PemReader.readPrivateKey(resource.stream(), passphrase));
            });
        }
        if (builder.publicKey().isEmpty()) {
            pemConfig.publicKey().ifPresent(resource -> {
                builder.publicKey(PemReader.readPublicKey(resource.stream()));
            });
        }

        List<X509Certificate> certs = pemConfig.certChain()
                .map(resource -> PemReader.readCertificates(resource.stream()))
                .orElseGet(List::of);

        certs.forEach(builder::addCertChain);
        if (!certs.isEmpty() && builder.publicCert().isEmpty()) {
            builder.publicCert(certs.get(0));
        }

        pemConfig.certificates()
                .stream()
                .map(resource -> PemReader.readCertificates(resource.stream()))
                .flatMap(Collection::stream)
                .forEach(builder::addCert);
    }

    private void updateFromKeystore(Keys.BuilderBase<?, ?> builder, KeystoreKeys keystoreConfig) {
        char[] keystorePassword = keystoreConfig.passphrase().orElseGet(() -> new char[0]);
        char[] keyPassword = keystoreConfig.keyPassphrase().orElse(keystorePassword);
        String keystoreType = keystoreConfig.type();
        Resource keystoreResource = keystoreConfig.keystore();
        KeyStore keyStore;

        try (InputStream keystoreStream = keystoreResource.stream()) {
            keyStore = PkiUtil.loadKeystore(keystoreType, keystoreStream, keystorePassword,
                                            keystoreResource.location());
        } catch (IOException e) {
            throw new PkiException("Failed to read keystore from its resource: " + keystoreResource, e);
        }

        Optional<String> keyAliasConfigured = keystoreConfig.keyAlias();
        String keyAlias = keyAliasConfigured.orElse(KeystoreKeysBlueprint.DEFAULT_PRIVATE_KEY_ALIAS);

        if (builder.privateKey().isEmpty()) {
            boolean guessing = keyAliasConfigured.isEmpty();
            try {
                builder.privateKey(PkiUtil.loadPrivateKey(keyStore, keyAlias, keyPassword));
            } catch (Exception e) {
                if (guessing) {
                    LOGGER.log(System.Logger.Level.DEBUG, "Failed to read private key from default alias", e);
                } else {
                    throw e;
                }
            }
        }
        List<X509Certificate> certChain = null;
        if (builder.certChain().isEmpty()) {
            Optional<String> certChainAliasConfigured = keystoreConfig.certChainAlias();
            boolean guessing = certChainAliasConfigured.isEmpty();
            String certChainAlias = certChainAliasConfigured.orElse(keyAlias);

            try {
                certChain = PkiUtil.loadCertChain(keyStore, certChainAlias);
                certChain.forEach(builder::addCertChain);
            } catch (Exception e) {
                if (guessing) {
                    LOGGER.log(System.Logger.Level.DEBUG, "Failed to certificate chain from alias \"" + certChainAlias + "\"", e);
                } else {
                    throw e;
                }
            }
        }
        if (builder.publicCert().isEmpty()) {
            Optional<String> publicCertAliasConfigured = keystoreConfig.certAlias();
            if (publicCertAliasConfigured.isEmpty()) {
                if (!builder.certChain().isEmpty()) {
                    builder.publicCert(builder.certChain().get(0));
                }
            } else {
                builder.publicCert(PkiUtil.loadCertificate(keyStore, publicCertAliasConfigured.get()));
            }
        }
        if (keystoreConfig.trustStore()) {
            PkiUtil.loadCertificates(keyStore).forEach(builder::addCert);
        } else {
            keystoreConfig.certAliases()
                    .forEach(it -> builder.addCert(PkiUtil.loadCertificate(keyStore, it)));
        }
    }
}
