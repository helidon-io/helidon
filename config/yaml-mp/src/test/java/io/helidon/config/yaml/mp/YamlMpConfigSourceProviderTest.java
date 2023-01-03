/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.config.yaml.mp;

import java.util.Map;
import java.util.NoSuchElementException;

import io.helidon.config.mp.MpConfigSources;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class YamlMpConfigSourceProviderTest {
    private static ConfigProviderResolver configResolver;
    private Config config;
    private static final String MP_CONFIG_PROFILE = "mp.config.profile";
    public static final String PROFILE_TYPE_CONFIG_KEY = "profile.type";
    public static final String PROFILE_VALUE_CONFIG_KEY = "profile.value";
    public static final String DEFAULT_PROFILE = "default";
    public static final String DEV_PROFILE = "dev";
    public static final String TEST_PROFILE = "test";
    public static final String DEFAULT_PROFILE_VALUE = "Production";
    public static final Map<String, String> profileConfigValues = Map.of(
            DEFAULT_PROFILE, DEFAULT_PROFILE_VALUE,
            DEV_PROFILE, "Development",
            TEST_PROFILE, "Test"
    );

    @BeforeAll
    static void init() {
        configResolver = ConfigProviderResolver.instance();
    }

    @AfterAll
    static void reset() {
        System.clearProperty(MP_CONFIG_PROFILE);
    }

    @BeforeEach
    void resetConfig() {
        if (config == null) {
            System.clearProperty(MP_CONFIG_PROFILE);
            configResolver.releaseConfig(ConfigProvider.getConfig());
        } else {
            configResolver.releaseConfig(config);
            config = null;
        }
    }

    @Test
    void testNoProfileFromSystemPropertyWithOverride() {
        // if using DEFAULT_PROFILE, mp.config.profile system property will not be set
        validateUsingSystemProperty(DEFAULT_PROFILE, PROFILE_TYPE_CONFIG_KEY, profileConfigValues.get(DEFAULT_PROFILE));
    }

    @Test
    void testDevProfileFromSystemPropertyWithOverride() {
        validateUsingSystemProperty(DEV_PROFILE, PROFILE_TYPE_CONFIG_KEY, profileConfigValues.get(DEV_PROFILE));
    }

    @Test
    void testTestProfileFromSystemPropertyWithOverride() {
        validateUsingSystemProperty(TEST_PROFILE, PROFILE_VALUE_CONFIG_KEY, DEFAULT_PROFILE_VALUE);
    }

    @Test
    void testNoProfileFromSystemPropertyWithNoOverride() {
        // if using DEFAULT_PROFILE, mp.config.profile will not be added in the config source
        validateUsingSystemProperty(DEFAULT_PROFILE, PROFILE_TYPE_CONFIG_KEY, profileConfigValues.get(DEFAULT_PROFILE));
    }

    @Test
    void testDevProfileFromSystemPropertyWithNoOverride() {
        validateUsingSystemProperty(TEST_PROFILE, PROFILE_TYPE_CONFIG_KEY, profileConfigValues.get(TEST_PROFILE));
    }

    @Test
    void testTestProfileFromSystemPropertyWithNoOverride() {
        validateUsingSystemProperty(DEV_PROFILE, PROFILE_TYPE_CONFIG_KEY, profileConfigValues.get(DEV_PROFILE));
    }

    @Test
    void testNoProfileFromFromConfigSourceWithOverride() {
        // if using DEFAULT_PROFILE, mp.config.profile will not be added in the config source
        validateUsingConfigSource(DEFAULT_PROFILE, PROFILE_VALUE_CONFIG_KEY, DEFAULT_PROFILE_VALUE);
    }

    @Test
    void testDevProfileFromFromConfigSourceWithOverride() {
        validateUsingConfigSource(DEV_PROFILE, PROFILE_VALUE_CONFIG_KEY, DEFAULT_PROFILE_VALUE);
    }

    @Test
    void testTestProfileFromFromConfigSourceWithOverride() {
        validateUsingConfigSource(TEST_PROFILE, PROFILE_VALUE_CONFIG_KEY, DEFAULT_PROFILE_VALUE);
    }

    @Test
    void testNoProfileFromFromConfigSourceWithNoOverride() {
        // if using DEFAULT_PROFILE, mp.config.profile will not be added in the config source
        validateUsingConfigSource(DEFAULT_PROFILE, PROFILE_VALUE_CONFIG_KEY, DEFAULT_PROFILE_VALUE);
    }

    @Test
    void testDevProfileFromFromConfigSourceWithNoOverride() {
        validateUsingConfigSource(DEV_PROFILE, PROFILE_VALUE_CONFIG_KEY, DEFAULT_PROFILE_VALUE);
    }

    @Test
    void testTestProfileFromFromConfigSourceWithNoOverride() {
        validateUsingConfigSource(TEST_PROFILE, PROFILE_VALUE_CONFIG_KEY, DEFAULT_PROFILE_VALUE);
    }

    private void validateUsingSystemProperty(String profile, String profileConfigKey, String profileConfigValue) {
        // Don't set mp.config.profile system property if using DEFAULT_PROFILE
        if (!profile.equals(DEFAULT_PROFILE)) {
            System.setProperty(MP_CONFIG_PROFILE, profile);
        }
        validateConfigValues(profile, profileConfigKey, profileConfigValue);
    }

    private void validateUsingConfigSource(String profile, String profileConfigKey, String profileConfigValue){
        // Don't set mp.config.profile in config source if using DEFAULT_PROFILE
        Map<String, String> configMap = profile == DEFAULT_PROFILE ? Map.of() : Map.of(MP_CONFIG_PROFILE, profile);
        Config mpConfigProfile = configResolver.getBuilder()
                .addDiscoveredSources()
                .withSources(MpConfigSources.create(configMap))
                .build();
        configResolver.registerConfig(mpConfigProfile, Thread.currentThread().getContextClassLoader());
        validateConfigValues(profile, profileConfigKey, profileConfigValue);
    }

    private void validateConfigValues(String profile, String profileConfigKey, String profileConfigValue) {
        config = ConfigProvider.getConfig();
        // If using DEFAULT_PROFILE, mp.config.profile should not exist in the Config
        if (profile == DEFAULT_PROFILE) {
            assertThrows(NoSuchElementException.class, () -> {
                config.getValue(MP_CONFIG_PROFILE, String.class);
            });
        } else {
            assertThat(config.getValue(MP_CONFIG_PROFILE, String.class), is(profile));
        }
        assertThat(config.getValue(profileConfigKey, String.class), is(profileConfigValue));
    }
}
