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

import io.helidon.config.mp.MpConfigSources;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MpEncryptionFilterTest {
    private static final char[] MASTER_PASSWORD = "myMasterPasswordForEncryption".toCharArray();
    private static final String SECRET_KEY = "test.secret";
    private static final String SECRET_VALUE = "secretValue";

    @Test
    void testEncValue() {
        Config config = config(Map.of(ConfigProperties.MASTER_PASSWORD_CONFIG_KEY,
                                      String.valueOf(MASTER_PASSWORD),
                                      ConfigProperties.REQUIRE_ENCRYPTION_CONFIG_KEY,
                                      "false",
                                      SECRET_KEY,
                                      encryptedSecret()));

        assertThat(config.getValue(SECRET_KEY, String.class), is(SECRET_VALUE));
    }

    @Test
    void testEncFailsClosedWithoutMasterPassword() {
        Config config = config(Map.of(ConfigProperties.REQUIRE_ENCRYPTION_CONFIG_KEY,
                                      "true",
                                      SECRET_KEY,
                                      encryptedSecret()));

        assertThrows(ConfigEncryptionException.class, () -> config.getValue(SECRET_KEY, String.class));
    }

    @Test
    void testEncFailsClosedOnMalformedValue() {
        Config config = config(Map.of(ConfigProperties.MASTER_PASSWORD_CONFIG_KEY,
                                      String.valueOf(MASTER_PASSWORD),
                                      ConfigProperties.REQUIRE_ENCRYPTION_CONFIG_KEY,
                                      "false",
                                      SECRET_KEY,
                                      "${ENC=not really encrypted}"));

        assertThrows(ConfigEncryptionException.class, () -> config.getValue(SECRET_KEY, String.class));
    }

    private static Config config(Map<String, String> values) {
        return ConfigProviderResolver.instance()
                .getBuilder()
                .withSources(MpConfigSources.create(values))
                .build();
    }

    private static String encryptedSecret() {
        return MpEncryptionFilter.PREFIX_ENC
                + EncryptionUtil.encryptAesEnvelope(MASTER_PASSWORD, SECRET_VALUE)
                + "}";
    }
}
