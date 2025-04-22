/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import java.util.Map;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

class SystemPropertiesTest {

    private Config config;

    @BeforeAll
    static void initClass() {
        // Helidon update to MP Config spec, we can specify an empty string as a valid property value
        System.setProperty(SystemPropertiesTest.class.getName(), "${EMPTY}");
        // according to MP spec, this property does not exist
        System.setProperty(SystemPropertiesTest.class.getName() + ".empty", "");
        ConfigProviderResolver configProvider = ConfigProviderResolver.instance();
        configProvider.registerConfig(configProvider.getBuilder()
                                              .addDefaultSources()
                                              .build(),
                                      Thread.currentThread().getContextClassLoader());
    }

    @BeforeEach
    void installConfig() {
        this.config = ConfigProvider.getConfig();
        assertThat(this.config, notNullValue());
    }

    @Test
    void testToMap() {
        io.helidon.config.Config helidonConfig = MpConfig.toHelidonConfig(config);
        Map<String, String> map = helidonConfig.asMap()
                .get();
        assertThat(map, notNullValue());
        assertThat(map.get(SystemPropertiesTest.class.getName()), is(""));
        assertThat(map.get(SystemPropertiesTest.class.getName() + ".empty"), is(nullValue()));
    }

}
