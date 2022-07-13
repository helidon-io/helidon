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

import io.helidon.config.ConfigException;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class YamlMpMetaConfigTest {
    private static ConfigProviderResolver configResolver;
    private Config config;
    private static final String META_CONFIG_SYSTEM_PROPERTY = "io.helidon.config.mp.meta-config";

    @BeforeAll
    static void getProviderResolver() {
        configResolver = ConfigProviderResolver.instance();
    }
    @AfterAll
    static void resetSystemProperties() {
        System.clearProperty(META_CONFIG_SYSTEM_PROPERTY);
    }

    @BeforeEach
    void resetConfig() {
        if (config == null) {
            // first run - need to remove existing props
            System.clearProperty(META_CONFIG_SYSTEM_PROPERTY);
            configResolver.releaseConfig(ConfigProvider.getConfig());
        } else {
            configResolver.releaseConfig(config);
            config = null;
        }
    }

    @Test
    void testMetaClasspath() {
        System.setProperty(META_CONFIG_SYSTEM_PROPERTY, "custom-mp-meta-config-classpath.yaml");
        validateConfig();
    }

    @Test
    void testMetaPath() {
        System.setProperty(META_CONFIG_SYSTEM_PROPERTY, "custom-mp-meta-config-path.yaml");
        validateConfig();
    }

    private void validateConfig() {
        config = ConfigProvider.getConfig();

        // Main file
        assertThat(config.getValue("string", String.class), is("String"));
        assertThat(config.getValue("number", String.class), is("175"));
        assertThat(config.getValue("array.0", String.class), is("One"));
        assertThat(config.getValue("array.1", String.class), is("Two"));
        assertThat(config.getValue("array.2", String.class), is("Three"));
        assertThat(config.getValue("boolean", String.class), is("true"));
    }

    @Test
    void testMetaClasspathConfigProfile() {
        System.setProperty(META_CONFIG_SYSTEM_PROPERTY, "custom-mp-meta-config-classpath-profile.yaml");
        validateConfigProfile();
    }

    @Test
    void testMetaPathConfigProfile() {
        System.setProperty(META_CONFIG_SYSTEM_PROPERTY, "custom-mp-meta-config-path-profile.yaml");
        validateConfigProfile();
    }

    private void validateConfigProfile() {
        config = ConfigProvider.getConfig();

        // Main file
        assertThat(config.getValue("string", String.class), is("String_dev"));
        assertThat(config.getValue("number", String.class), is("250"));
        assertThat(config.getValue("array.0", String.class), is("One"));
        assertThat(config.getValue("array.1", String.class), is("Two"));
        assertThat(config.getValue("array.2", String.class), is("Three"));
        assertThat(config.getValue("boolean", String.class), is("true"));
        assertThat(config.getValue("extra", String.class), is("Extra"));
    }

    @Test
    void testMetaOrdinal() {
        System.setProperty(META_CONFIG_SYSTEM_PROPERTY, "ordinal-mp-meta-config.yaml");
        config = ConfigProvider.getConfig();

        assertThat(config.getValue("string", String.class), is("String"));
        assertThat(config.getValue("number", String.class), is("175"));
        assertThat(config.getValue("array.0", String.class), is("First"));
        assertThat(config.getValue("array.1", String.class), is("Second"));
        assertThat(config.getValue("array.2", String.class), is("Third"));
        assertThat(config.getValue("boolean", String.class), is("false"));
    }

    @Test
    void testMetaNonExistentNotOptional() {
        System.setProperty(META_CONFIG_SYSTEM_PROPERTY, "custom-mp-meta-config-path-not-optional.yaml");
        try {
            config = ConfigProvider.getConfig();
            Assertions.fail("Expecting meta-config to fail due to not optional non-existent config source");
        } catch (ConfigException e) {}
    }
}
