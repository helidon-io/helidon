/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.ConfigValue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.config.encryption.AbstractSecureConfigTest.TEST_STRING;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;

/**
 * Tests rsa support in config.
 */
public class RsaSecureConfigTest {

    private static Config config;
    private static Config configRequiresEncryption;

    @BeforeAll
    public static void initClass() {
        config = Config.create().get("rsa-current");

        configRequiresEncryption = Config.builder()
                .addSource(ConfigSources.create(Map.of(ConfigProperties.REQUIRE_ENCRYPTION_CONFIG_KEY, "true")))
                .addSource(ConfigSources.classpath("application.yaml"))
                .build()
                .get("rsa-current");

        assertThat("We must have the correct configuration file", config.get("pwd3").type().isLeaf());
        assertThat("We must have the correct configuration file", configRequiresEncryption.get("pwd3").type().isLeaf());
    }

    @Test
    public void testWrongAsymmetric() {
        testPassword(config, "pwd10", "${RSA-P=not really encrypted}");
    }


    @Test
    public void testAsymmetric() {
        testPassword(config, "pwd3", TEST_STRING);
        testPassword(config, "pwd5", "");
        testPassword(configRequiresEncryption, "pwd3", TEST_STRING);
        testPassword(configRequiresEncryption, "pwd5", "");
    }

    @Test
    public void testPasswordArrayAsymetric() {
        ConfigValue<List<String>> passwordsOpt = config.get("passwords")
                .asList(String.class);

        assertThat("Passwords must be present", passwordsOpt.isPresent());

        List<String> passwords = passwordsOpt.get();
        assertThat(passwords, hasSize(1));
        assertThat(passwords, contains(TEST_STRING));
    }

    void testPassword(Config config, String key, String expectedValue) {
        assertThat(config.get(key).asString().get(), is(expectedValue));
    }

}
