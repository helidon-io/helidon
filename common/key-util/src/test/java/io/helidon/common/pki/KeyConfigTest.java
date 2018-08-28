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

import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Optional;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.configurable.Resource;
import io.helidon.config.Config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link KeyConfig}.
 */
class KeyConfigTest {
    private static Config config;

    @BeforeAll
    static void init() {
        config = Config.create();
    }

    @Test
    void testConfigPublicKey() {
        KeyConfig publicKey = KeyConfig.fromConfig(config.get("public-key"));

        assertThat(publicKey.getCertChain().size(), is(0));
        assertThat(publicKey.getPrivateKey().isPresent(), is(false));
        assertThat(publicKey.getPublicCert().isPresent(), is(true));
        assertThat(publicKey.getPublicKey().isPresent(), is(true));
    }

    @Test
    void testConfigPrivateKey() {
        KeyConfig keyConfig = KeyConfig.fromConfig(config.get("private-key"));

        assertThat(keyConfig.getCertChain().size(), is(0));
        assertThat(keyConfig.getPrivateKey().isPresent(), is(true));
        assertThat(keyConfig.getPublicCert().isPresent(), is(false));
        assertThat(keyConfig.getPublicKey().isPresent(), is(false));
    }

    @Test
    void testConfigCertChain() {
        KeyConfig publicKey = KeyConfig.fromConfig(config.get("cert-chain"));

        assertThat(publicKey.getCertChain().size(), is(1));
        // private key is loaded by default, as it uses the alias "1"
        assertThat(publicKey.getPrivateKey().isPresent(), is(true));
        // public cert is loaded by default from cert chain
        assertThat(publicKey.getPublicCert().isPresent(), is(true));
        assertThat(publicKey.getPublicKey().isPresent(), is(true));
    }

    @Test
    void testConfigWrongPath() {
        assertThrows(PkiException.class, () -> KeyConfig.fromConfig(config.get("wrong-path")));
    }

    @Test
    void testConfigPartInvalid() {
        KeyConfig invalid = KeyConfig.fromConfig(config.get("partially-invalid"));
        assertThat(invalid.getPrivateKey().isPresent(), is(false));
        assertThat(invalid.getPublicCert().isPresent(), is(false));
        assertThat(invalid.getPublicKey().isPresent(), is(false));
        assertThat(invalid.getCertChain().isEmpty(), is(true));
    }

    @Test
    void testConfigInvalid() {
        KeyConfig invalid = KeyConfig.fromConfig(config.get("invalid"));
        assertThat(invalid.getPrivateKey().isPresent(), is(false));
        assertThat(invalid.getPublicCert().isPresent(), is(false));
        assertThat(invalid.getPublicKey().isPresent(), is(false));
        assertThat(invalid.getCertChain().isEmpty(), is(true));
    }

    @Test
    void testResourcePath() {
        KeyConfig keyConfig = KeyConfig.fromConfig(config.get("resource-path"));

        assertThat(keyConfig.getCertChain().size(), is(0));
        assertThat(keyConfig.getPrivateKey().isPresent(), is(true));
        assertThat(keyConfig.getPublicCert().isPresent(), is(false));
        assertThat(keyConfig.getPublicKey().isPresent(), is(false));
    }

    @Test
    void testContent() {
        KeyConfig keyConfig = KeyConfig.fromConfig(config.get("content"));

        assertThat(keyConfig.getCertChain().size(), is(0));
        assertThat(keyConfig.getPrivateKey().isPresent(), is(true));
        assertThat(keyConfig.getPublicCert().isPresent(), is(false));
        assertThat(keyConfig.getPublicKey().isPresent(), is(false));
    }

    @Test
    void testDoublePath() {
        KeyConfig keyConfig = KeyConfig.fromConfig(config.get("double-path"));

        assertThat(keyConfig.getCertChain().size(), is(0));
        assertThat(keyConfig.getPrivateKey().isPresent(), is(true));
        assertThat(keyConfig.getPublicCert().isPresent(), is(false));
        assertThat(keyConfig.getPublicKey().isPresent(), is(false));
    }

    @Test
    void testPem() {
        KeyConfig conf = KeyConfig.pemBuilder()
                .certChain(Resource.from("keystore/public_key_cert.pem"))
                .key(Resource.from("keystore/id_rsa.p8"))
                .keyPassphrase("heslo".toCharArray())
                .build();

        assertThat("Private key should not be empty", conf.getPrivateKey(), not(Optional.empty()));
        assertThat("Public key should not be empty", conf.getPublicKey(), not(Optional.empty()));
        assertThat("Public cert should not be empty", conf.getPublicCert(), not(Optional.empty()));

        conf.getPrivateKey().ifPresent(it -> assertThat(it, instanceOf(RSAPrivateKey.class)));
        conf.getPublicKey().ifPresent(it -> assertThat(it, instanceOf(RSAPublicKey.class)));
        conf.getPublicCert().ifPresent(it -> assertThat(it, instanceOf(X509Certificate.class)));
        assertThat(conf.getCertChain().isEmpty(), is(false));
        assertThat(conf.getCertChain().size(), is(1));
        assertThat(conf.getCertChain().get(0), instanceOf(X509Certificate.class));
    }

    @Test
    void testPemConfig() {
        KeyConfig conf = KeyConfig.fromConfig(config.get("pem"));

        assertThat("Private key should not be empty", conf.getPrivateKey(), not(Optional.empty()));
        assertThat("Public key should not be empty", conf.getPublicKey(), not(Optional.empty()));
        assertThat("Public cert should not be empty", conf.getPublicCert(), not(Optional.empty()));

        conf.getPrivateKey().ifPresent(it -> assertThat(it, instanceOf(RSAPrivateKey.class)));
        conf.getPublicKey().ifPresent(it -> assertThat(it, instanceOf(RSAPublicKey.class)));
        conf.getPublicCert().ifPresent(it -> assertThat(it, instanceOf(X509Certificate.class)));
        assertThat(conf.getCertChain().isEmpty(), is(false));
        assertThat(conf.getCertChain().size(), is(1));
        assertThat(conf.getCertChain().get(0), instanceOf(X509Certificate.class));
    }

    @Test
    void testPemConfigNoPasswordNoChain() {
        KeyConfig conf = KeyConfig.fromConfig(config.get("pem-not-encrypted"));

        assertThat("Private key should not be empty", conf.getPrivateKey(), not(Optional.empty()));
        conf.getPrivateKey().ifPresent(it -> assertThat(it, instanceOf(RSAPrivateKey.class)));

        assertThat(conf.getPublicKey(), is(Optional.empty()));
        assertThat(conf.getPublicCert(), is(Optional.empty()));
        assertThat("Cert chain must be empty", conf.getCertChain(), is(CollectionsHelper.listOf()));
    }
}
