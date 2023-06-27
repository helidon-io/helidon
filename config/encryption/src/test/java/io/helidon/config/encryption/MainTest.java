/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.config.encryption;

import java.nio.file.Paths;
import java.security.PrivateKey;
import java.util.Base64;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Test Main class (cli).
 */
public class MainTest {
    @Test
    public void testAesEncryption() {
        String masterPassword = "BigMasterPassowrd!!!";
        String secret = "some secret to encrypt";
        String[] args = new String[] {"aes", masterPassword, secret};

        Main.EncryptionCliProcessor ecp = new Main.EncryptionCliProcessor();
        ecp.parse(args);
        assertThat(ecp.getAlgorithm(), is(Main.Algorithm.aes));
        assertThat(ecp.getMasterPassword(), is(masterPassword));
        assertThat(ecp.getSecret(), is(secret));

        String encrypted = ecp.encrypt();

        assertAll(
                () -> assertThat("Encrypted string should contain aes prefix: " + encrypted,
                                 encrypted.startsWith(EncryptionFilter.PREFIX_GCM)),
                () -> assertThat("Encrypted string should contain suffix \"}\": " + encrypted, encrypted.endsWith("}"))
        );

        String orig = EncryptionUtil.decryptAes(ecp.getMasterPassword().toCharArray(),
                                                encrypted.substring(EncryptionFilter.PREFIX_GCM.length(),
                                                                    encrypted.length() - 1));

        assertThat(orig, is(secret));

        Main.main(args);
    }

    @Test
    public void testRsaEncryption() {
        String keystorePath = "src/test/resources/.ssh/keystore.p12";
        String keystorePass = "j4c";
        String secret = "some secret to encrypt";
        String certAlias = "1";

        String[] args = new String[] {"rsa", keystorePath, keystorePass, certAlias, secret};
        PrivateKey pk = Keys.builder()
                .keystore(keystoreBuilder -> keystoreBuilder.keystore(Resource.create(Paths.get(keystorePath)))
                        .keyAlias("1")
                        .passphrase(keystorePass.toCharArray())
                )
                .build()
                .privateKey().orElseThrow(AssertionError::new);

        Main.EncryptionCliProcessor ecp = new Main.EncryptionCliProcessor();
        ecp.parse(args);
        assertThat(ecp.getAlgorithm(), is(Main.Algorithm.rsa));
        assertThat(ecp.getPublicKey(), notNullValue());
        assertThat(ecp.getSecret(), is(secret));

        String encrypted = ecp.encrypt();
        assertAll(() -> assertThat("Encrypted string should start with rsa prefix: " + encrypted,
                                   encrypted.startsWith(EncryptionFilter.PREFIX_RSA)),
                  () -> assertThat("Encrypted string should end with \"}\": " + encrypted, encrypted.endsWith("}")));

        String base64 = encrypted.substring(EncryptionFilter.PREFIX_RSA.length(), encrypted.length() - 1);
        Base64.getDecoder().decode(base64);

        String orig = EncryptionUtil.decryptRsa(pk, base64);

        assertThat(orig, is(secret));
        Main.main(args);
    }
}
