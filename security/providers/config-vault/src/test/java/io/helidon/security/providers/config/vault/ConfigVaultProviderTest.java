/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.security.providers.config.vault;

import java.nio.charset.StandardCharsets;

import io.helidon.common.crypto.CryptoException;
import io.helidon.config.Config;
import io.helidon.security.Security;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class ConfigVaultProviderTest {
    private static Security security;
    private static Security builtSecurity;

    @BeforeAll
    static void initClass() {
        Config config = Config.create();
        ConfigVaultProvider provider = ConfigVaultProvider.builder()
                .config(config.get("security.0.config-vault"))
                .build();

        security = Security.builder()
                .config(config.get("security"))
                .build();

        builtSecurity = Security.builder()
                .addSecret("password", provider, ConfigVaultProvider.SecretConfig.create("configured-password"))
                .addEncryption("config-vault-configured",
                               provider,
                               ConfigVaultProvider.EncryptionConfig.create("configured-password".toCharArray()))
                .build();
    }

    @Test
    void testEncryptionFromConfig() {
        String secretString = "my secret";
        byte[] secret = secretString.getBytes(StandardCharsets.UTF_8);

        String encryptedDefault = security.encrypt("config-vault-default", secret).await();
        String encryptedOverride = security.encrypt("config-vault-override", secret).await();

        assertThat(encryptedOverride, not(encryptedDefault));

        byte[] decrypted = security.decrypt("config-vault-default", encryptedDefault).await();
        assertThat(new String(decrypted), is(secretString));

        decrypted = security.decrypt("config-vault-override", encryptedOverride).await();
        assertThat(new String(decrypted), is(secretString));

        // now make sure we used a different password
        Assertions.assertThrows(CryptoException.class,
                                () -> security.decrypt("config-vault-override", encryptedDefault).await());

        Assertions.assertThrows(CryptoException.class,
                                () -> security.decrypt("config-vault-default", encryptedOverride).await());
    }

    @Test
    void testSecretFromConfig() {
        String password = security.secret("password", "default-value").await();

        assertThat(password, is("secret-password"));
    }

    @Test
    void testSecretFromBuilt() {
        String password = builtSecurity.secret("password", "default-value").await();

        assertThat(password, is("configured-password"));
    }
}