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

import java.util.List;

import io.helidon.config.Config;
import io.helidon.config.ConfigValue;
import io.helidon.config.ConfigValues;
import io.helidon.config.MissingValueException;

import org.junit.jupiter.api.Test;

import static io.helidon.config.ConfigValues.simpleValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test encryption and configuration.
 */
abstract class AbstractSecureConfigTest {
    static final String TEST_STRING = "Überstring";

    abstract Config getConfig();

    abstract Config getConfigRequiresEncryption();

    @Test
    void testDeep() {
        assertThat(getConfig().get("pwd11").asString(), is(ConfigValues.simpleValue("known_password")));
        assertThat(getConfig().get("pwd12").asString(), is(ConfigValues.simpleValue("known_password")));
    }

    @Test
    void testClearText() {
        testPassword(getConfig(), "pwd1", "known_password");
    }

    @Test
    void testClearTextNotAllowed() {
        assertThrows(ConfigEncryptionException.class, () ->
                testPassword(getConfigRequiresEncryption(), "pwd1", "known_password"));
    }

    @Test
    void testAlias() {
        testPassword(getConfig(), "pwd2", "known_password");
    }

    @Test
    void testMissingAlias() {
        assertThrows(MissingValueException.class, () ->
                testPassword(getConfig(), "pwd8", "missing..."));
    }

    @Test
    void testEncryptedAlias() {
        testPassword(getConfig(), "pwd7", TEST_STRING);
    }

    @Test
    void testAliasClearTextNotAllowed() {
        assertThrows(ConfigEncryptionException.class, () ->
                testPassword(getConfigRequiresEncryption(), "pwd2", "known_password"));
    }

    @Test
    void testSymmetric() {
        testPassword(getConfig(), "pwd4", TEST_STRING);
        testPassword(getConfig(), "pwd6", "");
    }

    @Test
    public void testPasswordArray() {
        ConfigValue<List<String>> passwordsOpt = getConfig().get("passwords")
                .asList(String.class);

        assertThat("Passwords must be present", passwordsOpt.isPresent());

        List<String> passwords = passwordsOpt.get();
        assertThat(passwords, hasSize(2));
        assertThat(passwords, contains(TEST_STRING, ""));
    }

    @Test
    public void testConfigList() {
        ConfigValue<List<Config>> objects = getConfig().get("objects").asNodeList();

        assertThat("Objects should be present in config", objects.isPresent());

        List<? extends Config> configSources = objects.get();
        assertThat("there should be two objects", configSources.size(), is(2));

        Config config = configSources.get(0);
        assertThat(config.get("pwd").asString(), is(simpleValue(TEST_STRING)));

        config = configSources.get(1);
        assertThat(config.get("pwd").asString(), is(simpleValue("")));
    }

    @Test
    public void testConfigListMissing() {
        ConfigValue<List<Config>> objects = getConfig().get("notThereAtAll").asList(Config.class);
        assertThat(objects, is(ConfigValues.empty()));
    }

    @Test
    public void testPasswordArrayMissing() {
        ConfigValue<List<String>> passwordsOpt = getConfig().get("notThereAtAll")
                .asList(String.class);

        assertThat(passwordsOpt, is(ConfigValues.empty()));
    }

    @Test
    public void testCustomEnc() {
        assertThat(getConfigRequiresEncryption().get("customEnc").asString(),
                   is(simpleValue("${URGH=argh}")));
    }

    @Test
    public void testMissing() {
        assertThat(getConfigRequiresEncryption().get("thisDoesNotExist").asString(),
                   is(ConfigValues.empty()));
    }

    void testPassword(Config config, String key, String expectedValue) {
        assertThat(config.get(key).asString().get(), is(expectedValue));
    }
}
