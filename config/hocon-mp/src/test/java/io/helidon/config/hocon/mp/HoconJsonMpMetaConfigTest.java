/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.config.hocon.mp;

import io.helidon.config.ConfigException;

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

class HoconJsonMpMetaConfigTest {
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
    void testMetaHoconClasspath() {
        System.setProperty(META_CONFIG_SYSTEM_PROPERTY, "custom-mp-meta-config-hocon-classpath.yaml");
        validateConfig();
    }

    @Test
    void testMetaHoconPath() {
        System.setProperty(META_CONFIG_SYSTEM_PROPERTY, "custom-mp-meta-config-hocon-path.yaml");
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
        // Include file
        assertThat(config.getValue("custom_include.string", String.class), is("Include_String"));
        assertThat(config.getValue("custom_include.number", String.class), is("200"));
        assertThat(config.getValue("custom_include.array.0", String.class), is("First"));
        assertThat(config.getValue("custom_include.array.1", String.class), is("Second"));
        assertThat(config.getValue("custom_include.array.2", String.class), is("Third"));
        assertThat(config.getValue("custom_include.boolean", String.class), is("false"));
    }

    @Test
    void testMetaHoconClasspathConfigProfile() {
        System.setProperty(META_CONFIG_SYSTEM_PROPERTY, "custom-mp-meta-config-hocon-classpath-profile.yaml");
        validateConfigProfile();
    }

    @Test
    void testMetaHoconPathConfigProfile() {
        System.setProperty(META_CONFIG_SYSTEM_PROPERTY, "custom-mp-meta-config-hocon-path-profile.yaml");
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

        // Include file
        assertThat(config.getValue("custom_include.string", String.class), is("Include_String"));
        assertThat(config.getValue("custom_include.number", String.class), is("200"));
        assertThat(config.getValue("custom_include.array.0", String.class), is("Begin"));
        assertThat(config.getValue("custom_include.array.1", String.class), is("Middle"));
        assertThat(config.getValue("custom_include.array.2", String.class), is("End"));
        assertThat(config.getValue("custom_include.boolean", String.class), is("true"));
    }


    @Test
    void testMetaJson() {
        System.setProperty(META_CONFIG_SYSTEM_PROPERTY, "custom-mp-meta-config-json.yaml");
        config = ConfigProvider.getConfig();

        assertThat(config.getValue("string", String.class), is("String"));
        assertThat(config.getValue("number", String.class), is("175"));
        assertThat(config.getValue("array.0", String.class), is("One"));
        assertThat(config.getValue("array.1", String.class), is("Two"));
        assertThat(config.getValue("array.2", String.class), is("Three"));
        assertThat(config.getValue("boolean", String.class), is("true"));
    }

    @Test
    void testMetaOrdinal() {
        System.setProperty(META_CONFIG_SYSTEM_PROPERTY, "ordinal-mp-meta-config-hocon.yaml");
        config = ConfigProvider.getConfig();

        assertThat(config.getValue("string", String.class), is("String"));
        assertThat(config.getValue("number", String.class), is("175"));
        assertThat(config.getValue("array.0", String.class), is("First"));
        assertThat(config.getValue("array.1", String.class), is("Second"));
        assertThat(config.getValue("array.2", String.class), is("Third"));
        assertThat(config.getValue("boolean", String.class), is("false"));
    }

    @Test
    void testMetaHoconNonExistentNotOptional() {
        System.setProperty(META_CONFIG_SYSTEM_PROPERTY, "custom-mp-meta-config-hocon-path-not-optional.yaml");
        assertThrows(ConfigException.class, ConfigProvider::getConfig, "Expecting meta-config to fail due to not optional non-existent config source");
    }
}
