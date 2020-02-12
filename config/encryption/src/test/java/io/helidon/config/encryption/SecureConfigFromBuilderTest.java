/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.KeyConfig;
import io.helidon.config.Config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test config encryption filter configured through a builder.
 */
public class SecureConfigFromBuilderTest extends AbstractSecureConfigTest {
    private static Config config;
    private static Config configRequiresEncryption;

    @BeforeAll
    public static void initClass() {
        // fromConfig tests with pkcs12, so here I test with unix like private key
        KeyConfig keyConfig = KeyConfig.keystoreBuilder()
                .keystore(Resource.create(".ssh/keystore.p12"))
                .keyAlias("1")
                .keystorePassphrase("j4c".toCharArray())
                .build();

        config = Config.builder()
                .disableFilterServices()
                .addFilter(EncryptionFilter.builder()
                                   .requireEncryption(false)
                                   .masterPassword("myMasterPasswordForEncryption".toCharArray())
                                   .privateKey(keyConfig)
                                   .buildProvider())
                .build().get("aes-current");

        configRequiresEncryption = Config.builder()
                .disableFilterServices()
                .addFilter(EncryptionFilter.builder()
                                   .requireEncryption(true)
                                   .masterPassword("myMasterPasswordForEncryption".toCharArray())
                                   .privateKey(keyConfig)
                                   .buildProvider())
                .build().get("aes-current");

        assertThat("We must have the correct configuration file", config.get("pwd1").type().isLeaf());
        assertThat("We must have the correct configuration file", configRequiresEncryption.get("pwd1").type().isLeaf());
    }

    @Override
    Config getConfig() {
        return config;
    }

    @Override
    Config getConfigRequiresEncryption() {
        return configRequiresEncryption;
    }

    @Test
    public void testWrongSymmetric() {
        testPassword(getConfig(), "pwd9", "${GCM=not really encrypted}");
    }
}
