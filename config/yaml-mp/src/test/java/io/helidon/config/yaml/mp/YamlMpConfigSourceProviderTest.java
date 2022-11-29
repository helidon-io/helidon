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
    public static final String PROFILE_CONFIG_KEY = "profile.type";
    public static final String DEFAULT_PROFILE = "default";
    public static final String DEV_PROFILE = "dev";
    public static final String TEST_PROFILE = "test";

    public static final Map<String, String> profileConfigValues = Map.of(
            DEFAULT_PROFILE, "Production",
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
    void testNoProfile() {
        // if using DEFAULT_PROFILE, mp.config.profile system property will not be set
        validateUsingSystemProperty(DEFAULT_PROFILE);
    }

    @Test
    void testWithDevelopmentProfileFromSystemProperty() {
        validateUsingSystemProperty(DEV_PROFILE);
    }

    @Test
    void testWithTestProfileFromSystemProperty() {
        validateUsingSystemProperty(TEST_PROFILE);
    }

    @Test
    void testNoProfileFromConfigSource() {
        // if using DEFAULT_PROFILE, mp.config.profile will not be added in the config source
        validateUsingConfigSource(DEFAULT_PROFILE);
    }

    @Test
    void testWithDevelopmentProfileFromConfigSource() {
        validateUsingConfigSource(DEV_PROFILE);
    }

    @Test
    void testWithTestProfileFromConfigSource() {
        validateUsingConfigSource(TEST_PROFILE);
    }

    private void validateUsingSystemProperty(String profile) {
        // Don't set mp.config.profile system property if using DEFAULT_PROFILE
        if (profile != DEFAULT_PROFILE) {
            System.setProperty(MP_CONFIG_PROFILE, profile);
        }
        validateConfigValues(profile, profileConfigValues.get(profile));
    }

    private void validateUsingConfigSource(String profile) {
        // Don't set mp.config.profile in config source if using DEFAULT_PROFILE
        Map<String, String> configMap = profile == DEFAULT_PROFILE ? Map.of() : Map.of(MP_CONFIG_PROFILE, profile);
        Config mpConfigProfile = configResolver.getBuilder()
                .addDiscoveredSources()
                .withSources(MpConfigSources.create(configMap))
                .build();
        configResolver.registerConfig(mpConfigProfile, Thread.currentThread().getContextClassLoader());
        validateConfigValues(profile, profileConfigValues.get(profile));
    }

    private void validateConfigValues(String profile, String profileConfigValue) {
        config = ConfigProvider.getConfig();
        // If using DEFAULT_PROFILE, mp.config.profile should not exist in the Config
        if (profile == DEFAULT_PROFILE) {
            assertThrows(NoSuchElementException.class, () -> {
                config.getValue(MP_CONFIG_PROFILE, String.class);
            });
        } else {
            assertThat(config.getValue(MP_CONFIG_PROFILE, String.class), is(profile));
        }
        assertThat(config.getValue(PROFILE_CONFIG_KEY, String.class), is(profileConfigValue));
    }
}
