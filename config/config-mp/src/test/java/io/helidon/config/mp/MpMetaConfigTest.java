/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import java.util.LinkedList;
import java.util.List;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.iterableWithSize;

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
    void testMetaYaml() {
        System.setProperty(MpMetaConfig.META_CONFIG_SYSTEM_PROPERTY, "custom-mp-meta-config.yaml");
        config = ConfigProvider.getConfig();

        // validate the config sources
        Iterable<ConfigSource> configSources = config.getConfigSources();
        List<String> sourceNames = new LinkedList<>();
        configSources.forEach(it -> sourceNames.add(it.getName()));

        assertThat(sourceNames, iterableWithSize(2));
        assertThat(sourceNames.get(0), is("CLASSPATH"));
        assertThat(config.getValue("value", String.class), is("classpath"));
    }

    @Test
    void testMetaProperties() {
        System.setProperty(MpMetaConfig.META_CONFIG_SYSTEM_PROPERTY, "custom-mp-meta-config.properties");
        config = ConfigProvider.getConfig();
        assertThat(config.getValue("value", String.class), is("path"));
    }

    @Test
    void testMetaDefault() {
        config = ConfigProvider.getConfig();
        assertThat(config.getValue("value", String.class), is("default"));
    }
}
