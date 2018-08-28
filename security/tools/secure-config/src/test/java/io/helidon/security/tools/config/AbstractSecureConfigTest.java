/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.tools.config;

import java.util.List;
import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.config.MissingValueException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test encryption and configuration.
 */
public abstract class AbstractSecureConfigTest {
    public static final String TEST_STRING = "Überstring";

    abstract Config getConfig();

    abstract Config getConfigRequiresEncryption();

    @Test
    public void testDeep() {
        String value = getConfig().get("pwd11").asString();
        assertThat(value, is("known_password"));

        value = getConfig().get("pwd12").asString();
        assertThat(value, is("known_password"));
    }

    @Test
    public void testClearText() throws Exception {
        testPassword(getConfig(), "pwd1", "known_password", "clear text");
    }

    @Test
    public void testClearTextNotAllowed() {
        assertThrows(SecureConfigException.class, () ->
                testPassword(getConfigRequiresEncryption(), "pwd1", "known_password", "clear_text"));
    }

    @Test
    public void testAlias() throws Exception {
        testPassword(getConfig(), "pwd2", "known_password", "aliased");
    }

    @Test
    public void testMissingAlias() {
        assertThrows(MissingValueException.class, () ->
                testPassword(getConfig(), "pwd8", "missing...", "alias"));
    }

    @Test
    public void testEncryptedAlias() throws Exception {
        testPassword(getConfig(), "pwd7", TEST_STRING, "aliased");
    }

    @Test
    public void testAliasClearTextNotAllowed() {
        assertThrows(SecureConfigException.class, () ->
                testPassword(getConfigRequiresEncryption(), "pwd2", "known_password", "aliased"));
    }

    @Test
    public void testSymmetric() throws Exception {
        testPassword(getConfig(), "pwd4", TEST_STRING, "symmetric");
        testPassword(getConfig(), "pwd6", "", "symmetric");
    }

    @Test
    public void testWrongSymmetric() {
        testPassword(getConfig(), "pwd9", "${AES=not really encrypted}", "symmetric");
        testPassword(getConfig(), "pwd10", "${RSA=not really encrypted}", "asymmetric");
    }

    @Test
    public void testAsymmetric() throws Exception {
        testPassword(getConfig(), "pwd3", TEST_STRING, "asymmetric");
        testPassword(getConfig(), "pwd5", "", "asymmetric");
        testPassword(getConfigRequiresEncryption(), "pwd3", TEST_STRING, "asymmetric");
        testPassword(getConfigRequiresEncryption(), "pwd5", "", "asymmetric");
    }

    @Test
    public void testPasswordArray() {
        Optional<List<String>> passwordsOpt = getConfig().get("passwords").asOptionalList(String.class);
        assertThat("Passwords must be present", passwordsOpt.isPresent());
        List<String> passwords = passwordsOpt.get();
        assertEquals(TEST_STRING, passwords.get(0));
        assertEquals(TEST_STRING, passwords.get(1));
        assertEquals("", passwords.get(2));

        assertEquals(3, passwords.size());
    }

    @Test
    public void testConfigList() {
        Optional<List<Config>> objects = getConfig().get("objects").asOptionalList(Config.class);

        assertThat("Objects should be present in config", objects.isPresent());

        List<? extends Config> configSources = objects.get();
        assertThat("there should be two objects", configSources.size(), is(2));

        Config config = configSources.get(0);
        assertEquals(TEST_STRING, config.get("pwd").asString());

        config = configSources.get(1);
        assertEquals("", config.get("pwd").asString());
    }

    @Test
    public void testConfigListMissing() {
        Optional<List<Config>> objects = getConfig().get("notThereAtAll").asOptionalList(Config.class);
        assertFalse(objects.isPresent());
    }

    @Test
    public void testPasswordArrayMissing() {
        Optional<List<String>> passwordsOpt = getConfig().get("notThereAtAll").asOptionalList(String.class);
        assertFalse(passwordsOpt.isPresent());
    }

    @Test
    public void testCustomEnc() {
        assertEquals("${URGH=argh}", getConfigRequiresEncryption().get("customEnc").asString());
    }

    @Test
    public void testMissing() {
        assertFalse(getConfigRequiresEncryption().get("thisDoesNotExist").value().isPresent());
    }

    void testPassword(Config config, String key, String expectedValue, String type) {
        Optional<String> optional = config.get(key).value();
        assertThat("Property " + key + " must exist", optional.isPresent(), is(true));
        assertThat("Property \"" + key + "\" of type: " + type, optional.get(), is(expectedValue));
    }
}
