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

import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.ServerCdiExtension;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.CDI;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test meta-config on MP with HOCON and JSON parsing.
 *
 * This will use SeContainerInitializer rather than @HelidonTest as the latter does not make meta-config work
 */
class HoconJsonMpMetaConfigTest {
    private static ConfigBean bean;
    private static SeContainer container;

    @BeforeAll
    static void initialize() {
        System.setProperty("io.helidon.config.mp.meta-config", "custom-mp-meta-config.yaml");
        System.setProperty("mp.initializer.allow", "true");
        System.setProperty("mp.initializer.no-warn", "true");

        container = SeContainerInitializer.newInstance()
                .disableDiscovery()
                .addExtensions(ConfigCdiExtension.class, ServerCdiExtension.class, JaxRsCdiExtension.class)
                .addBeanClasses(ConfigBean.class)
                .initialize();

        bean = CDI.current()
                .select(ConfigBean.class)
                .get();
    }

    @AfterAll
    static void destroy() {
        System.clearProperty("io.helidon.config.mp.meta-config");
        System.clearProperty("mp.initializer.allow");
        System.clearProperty("mp.initializer.no-warn");
        container.close();
    }

    @Test
    void TestHoconConfig() {
        assertThat(bean.hocon_string, is("Meta String"));
        assertThat(bean.hocon_number, is(20));
        assertThat(bean.hocon_array_0, is("Meta Array 1"));
        assertThat(bean.hocon_array_1, is("Meta Array 2"));
        assertThat(bean.hocon_array_2, is("Meta Array 3"));
        assertThat(bean.hocon_boolean, is(true));
     }

     @Test
    void TestHoconIncludeConfig() {
        assertThat(bean.hocon_include_string, is("Meta Include String"));
        assertThat(bean.hocon_include_number, is(10));
        assertThat(bean.hocon_include_array_0, is("Meta Include Array 1"));
        assertThat(bean.hocon_include_array_1, is("Meta Include Array 2"));
        assertThat(bean.hocon_include_array_2, is("Meta Include Array 3"));
        assertThat(bean.hocon_include_boolean, is(false));
    }

    @Test
    void TestJsonConfig() {
        assertThat(bean.json_string, is("Meta String"));
        assertThat(bean.json_number, is(20));
        assertThat(bean.json_array_0, is("Meta Array 1"));
        assertThat(bean.json_array_1, is("Meta Array 2"));
        assertThat(bean.json_array_2, is("Meta Array 3"));
        assertThat(bean.json_boolean, is(true));
    }
}
