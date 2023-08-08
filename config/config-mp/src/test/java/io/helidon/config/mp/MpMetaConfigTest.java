/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.config.mp;

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


class MpMetaConfigTest {
    private static ConfigProviderResolver configResolver;
    private Config config;

    @BeforeAll
    static void getProviderResolver() {
        configResolver = ConfigProviderResolver.instance();
    }
    @AfterAll
    static void resetSystemProperties() {
        System.clearProperty(MpMetaConfig.META_CONFIG_SYSTEM_PROPERTY);
    }

    @BeforeEach
    void resetConfig() {
        if (config == null) {
            // first run - need to remove existing props
            System.clearProperty(MpMetaConfig.META_CONFIG_SYSTEM_PROPERTY);
            configResolver.releaseConfig(ConfigProvider.getConfig());
        } else {
            configResolver.releaseConfig(config);
            config = null;
        }
    }

    @Test
    void testMetaEnvironmentVariablesSystemProperties() {
        System.setProperty("property1", "value1");
        System.setProperty("property2", "value2");
        System.setProperty(MpMetaConfig.META_CONFIG_SYSTEM_PROPERTY, "custom-mp-meta-config-sysprops-envvars.properties");
        config = ConfigProvider.getConfig();
        assertThat(config.getValue("property1", String.class), is("value1"));
        assertThat(config.getValue("property2", String.class), is("value2"));
        assertThat(config.getValue("foo.bar", String.class), is("mapped-env-value"));
        System.clearProperty("property1");
        System.clearProperty("property2");
    }

    @Test
    void testMetaProperties() {
        System.setProperty(MpMetaConfig.META_CONFIG_SYSTEM_PROPERTY, "custom-mp-meta-config.properties");
        config = ConfigProvider.getConfig();
        assertThat(config.getValue("value", String.class), is("path"));
        assertThat(config.getValue("config_ordinal", Integer.class), is(150));
    }

    @Test
    void testMetaPropertiesOrdinal() {
        System.setProperty(MpMetaConfig.META_CONFIG_SYSTEM_PROPERTY,
                "custom-mp-meta-config-ordinal.properties");
        config = ConfigProvider.getConfig();
        assertThat(config.getValue("value", String.class), is("classpath"));
        assertThat(config.getValue("config_ordinal", Integer.class), is(125));
    }

    @Test
    void testMetaPropertiesClassPathProfile() {
        System.setProperty(MpMetaConfig.META_CONFIG_SYSTEM_PROPERTY,
                "custom-mp-meta-config-classpath-profile.properties");
        config = ConfigProvider.getConfig();
        assertThat(config.getValue("value", String.class), is("classpath_profile"));
        assertThat(config.getValue("config_ordinal", Integer.class), is(125));
    }

    @Test
    void testMetaPropertiesPathProfile() {
        System.setProperty(MpMetaConfig.META_CONFIG_SYSTEM_PROPERTY,
                "custom-mp-meta-config-path-profile.properties");
        config = ConfigProvider.getConfig();
        assertThat(config.getValue("value", String.class), is("path_profile"));
        assertThat(config.getValue("config_ordinal", Integer.class), is(150));
    }

    @Test
    void testMetaPropertiesNotOptional() {
        System.setProperty(MpMetaConfig.META_CONFIG_SYSTEM_PROPERTY,
                "custom-mp-meta-config-not-optional.properties");
        assertThrows(ConfigException.class, ConfigProvider::getConfig, "Expecting meta-config to fail due to not optional non-existent config source");
    }

    @Test
    void testMetaDefault() {
        config = ConfigProvider.getConfig();
        assertThat(config.getValue("value", String.class), is("default"));
    }
}
