/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;
import java.util.Optional;

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
        KeyConfig publicKey = KeyConfig.create(config.get("public-key"));

        assertThat(publicKey.certChain().size(), is(0));
        assertThat(publicKey.privateKey().isPresent(), is(false));
        assertThat(publicKey.publicCert().isPresent(), is(true));
        assertThat(publicKey.publicKey().isPresent(), is(true));
    }

    @Test
    void testConfigPrivateKey() {
        KeyConfig keyConfig = KeyConfig.create(config.get("private-key"));

        assertThat(keyConfig.certChain().size(), is(0));
        assertThat(keyConfig.privateKey().isPresent(), is(true));
        assertThat(keyConfig.publicCert().isPresent(), is(false));
        assertThat(keyConfig.publicKey().isPresent(), is(false));
    }

    @Test
    void testConfigCertChain() {
        KeyConfig publicKey = KeyConfig.create(config.get("cert-chain"));

        assertThat(publicKey.certChain().size(), is(1));
        // private key is loaded by default, as it uses the alias "1"
        assertThat(publicKey.privateKey().isPresent(), is(true));
        // public cert is loaded by default from cert chain
        assertThat(publicKey.publicCert().isPresent(), is(true));
        assertThat(publicKey.publicKey().isPresent(), is(true));
    }

    @Test
    void testConfigWrongPath() {
        assertThrows(PkiException.class, () -> KeyConfig.create(config.get("wrong-path")));
    }

    @Test
    void testConfigPartInvalid() {
        KeyConfig invalid = KeyConfig.create(config.get("partially-invalid"));
        assertThat(invalid.privateKey().isPresent(), is(false));
        assertThat(invalid.publicCert().isPresent(), is(false));
        assertThat(invalid.publicKey().isPresent(), is(false));
        assertThat(invalid.certChain().isEmpty(), is(true));
    }

    @Test
    void testConfigInvalid() {
        KeyConfig invalid = KeyConfig.create(config.get("invalid"));
        assertThat(invalid.privateKey().isPresent(), is(false));
        assertThat(invalid.publicCert().isPresent(), is(false));
        assertThat(invalid.publicKey().isPresent(), is(false));
        assertThat(invalid.certChain().isEmpty(), is(true));
    }

    @Test
    void testResourcePath() {
        KeyConfig keyConfig = KeyConfig.create(config.get("resource-path"));

        assertThat(keyConfig.certChain().size(), is(0));
        assertThat(keyConfig.privateKey().isPresent(), is(true));
        assertThat(keyConfig.publicCert().isPresent(), is(false));
        assertThat(keyConfig.publicKey().isPresent(), is(false));
    }

    @Test
    void testContent() {
        KeyConfig keyConfig = KeyConfig.create(config.get("content"));

        assertThat(keyConfig.certChain().size(), is(0));
        assertThat(keyConfig.privateKey().isPresent(), is(true));
        assertThat(keyConfig.publicCert().isPresent(), is(false));
        assertThat(keyConfig.publicKey().isPresent(), is(false));
    }

    @Test
    void testDoublePath() {
        KeyConfig keyConfig = KeyConfig.create(config.get("double-path"));

        assertThat(keyConfig.certChain().size(), is(0));
        assertThat(keyConfig.privateKey().isPresent(), is(true));
        assertThat(keyConfig.publicCert().isPresent(), is(false));
        assertThat(keyConfig.publicKey().isPresent(), is(false));
    }

    @Test
    void testPem() {
        KeyConfig conf = KeyConfig.pemBuilder()
                .certChain(Resource.create("keystore/public_key_cert.pem"))
                .key(Resource.create("keystore/id_rsa.p8"))
                .keyPassphrase("heslo".toCharArray())
                .build();

        assertThat("Private key should not be empty", conf.privateKey(), not(Optional.empty()));
        assertThat("Public key should not be empty", conf.publicKey(), not(Optional.empty()));
        assertThat("Public cert should not be empty", conf.publicCert(), not(Optional.empty()));

        conf.privateKey().ifPresent(it -> assertThat(it, instanceOf(RSAPrivateKey.class)));
        conf.publicKey().ifPresent(it -> assertThat(it, instanceOf(RSAPublicKey.class)));
        conf.publicCert().ifPresent(it -> assertThat(it, instanceOf(X509Certificate.class)));
        assertThat(conf.certChain().isEmpty(), is(false));
        assertThat(conf.certChain().size(), is(1));
        assertThat(conf.certChain().get(0), instanceOf(X509Certificate.class));
    }

    @Test
    void testPemConfig() {
        KeyConfig conf = KeyConfig.create(config.get("pem"));

        assertThat("Private key should not be empty", conf.privateKey(), not(Optional.empty()));
        assertThat("Public key should not be empty", conf.publicKey(), not(Optional.empty()));
        assertThat("Public cert should not be empty", conf.publicCert(), not(Optional.empty()));

        conf.privateKey().ifPresent(it -> assertThat(it, instanceOf(RSAPrivateKey.class)));
        conf.publicKey().ifPresent(it -> assertThat(it, instanceOf(RSAPublicKey.class)));
        conf.publicCert().ifPresent(it -> assertThat(it, instanceOf(X509Certificate.class)));
        assertThat(conf.certChain().isEmpty(), is(false));
        assertThat(conf.certChain().size(), is(1));
        assertThat(conf.certChain().get(0), instanceOf(X509Certificate.class));
    }

    @Test
    void testPemConfigNoPasswordNoChain() {
        KeyConfig conf = KeyConfig.create(config.get("pem-not-encrypted"));

        assertThat("Private key should not be empty", conf.privateKey(), not(Optional.empty()));
        conf.privateKey().ifPresent(it -> assertThat(it, instanceOf(RSAPrivateKey.class)));

        assertThat(conf.publicKey(), is(Optional.empty()));
        assertThat(conf.publicCert(), is(Optional.empty()));
        assertThat("Cert chain must be empty", conf.certChain(), is(List.of()));
    }
}
