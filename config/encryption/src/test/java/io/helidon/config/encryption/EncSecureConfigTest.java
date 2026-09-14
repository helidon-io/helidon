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

package io.helidon.config.encryption;

import java.util.Map;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EncSecureConfigTest {
    private static final char[] MASTER_PASSWORD = "myMasterPasswordForEncryption".toCharArray();
    private static final String SECRET_KEY = "test.secret";

    @Test
    void testEncFromBuilder() {
        String encrypted = EncryptionFilter.PREFIX_ENC
                + EncryptionUtil.encryptAesEnvelope(MASTER_PASSWORD, AbstractSecureConfigTest.TEST_STRING)
                + "}";

        Config config = Config.builder()
                .disableFilterServices()
                .addSource(ConfigSources.create(Map.of(SECRET_KEY, encrypted)))
                .addFilter(EncryptionFilter.builder()
                                   .requireEncryption(true)
                                   .masterPassword(MASTER_PASSWORD)
                                   .privateKey(keyConfig())
                                   .buildProvider())
                .build();

        assertThat(config.get(SECRET_KEY).asString().get(), is(AbstractSecureConfigTest.TEST_STRING));
    }

    @Test
    void testEncFromConfig() {
        String encrypted = EncryptionFilter.PREFIX_ENC
                + EncryptionUtil.encryptAesEnvelope(MASTER_PASSWORD, AbstractSecureConfigTest.TEST_STRING)
                + "}";

        Config config = Config.builder()
                .addSource(ConfigSources.create(Map.of(ConfigProperties.MASTER_PASSWORD_CONFIG_KEY,
                                                       String.valueOf(MASTER_PASSWORD),
                                                       ConfigProperties.REQUIRE_ENCRYPTION_CONFIG_KEY,
                                                       "false",
                                                       SECRET_KEY,
                                                       encrypted)))
                .build();

        assertThat(config.get(SECRET_KEY).asString().get(), is(AbstractSecureConfigTest.TEST_STRING));
    }

    @Test
    void testEncFailsClosedWithoutMasterPassword() {
        String encrypted = EncryptionFilter.PREFIX_ENC
                + EncryptionUtil.encryptAesEnvelope(MASTER_PASSWORD, AbstractSecureConfigTest.TEST_STRING)
                + "}";

        Config config = Config.builder()
                .disableFilterServices()
                .addSource(ConfigSources.create(Map.of(ConfigProperties.REQUIRE_ENCRYPTION_CONFIG_KEY,
                                                       "true",
                                                       SECRET_KEY,
                                                       encrypted)))
                .addFilter(EncryptionFilter.fromConfig())
                .build();

        assertThrows(ConfigEncryptionException.class, () -> config.get(SECRET_KEY).asString().get());
    }

    @Test
    void testEncFailsClosedOnMalformedValue() {
        Config config = Config.builder()
                .disableFilterServices()
                .addSource(ConfigSources.create(Map.of(SECRET_KEY, "${ENC=not really encrypted}")))
                .addFilter(EncryptionFilter.builder()
                                   .requireEncryption(true)
                                   .masterPassword(MASTER_PASSWORD)
                                   .privateKey(keyConfig())
                                   .buildProvider())
                .build();

        assertThrows(ConfigEncryptionException.class, () -> config.get(SECRET_KEY).asString().get());
    }

    private static Keys keyConfig() {
        return Keys.builder()
                .keystore(keystoreBuilder -> keystoreBuilder.keystore(Resource.create(".ssh/keystore.p12"))
                        .keyAlias("1")
                        .passphrase("j4c".toCharArray()))
                .build();
    }
}
