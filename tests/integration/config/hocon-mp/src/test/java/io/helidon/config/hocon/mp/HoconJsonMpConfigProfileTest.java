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

package io.helidon.config.hocon.mp;

import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.ServerCdiExtension;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.enterprise.inject.spi.CDI;

/**
 * Test Hocon/Json config profile on MP.
 *
 * This will use SeContainerInitializer rather than @HelidonTest so that io.helidon.config.mp.MpConfigProviderResolver.getConfig()
 * will initially be empty and will trigger io.helidon.config.mp.MpConfigProviderResolver.buildConfig and eventually
 * lead to config.profile being processed via io.helidon.config.hocon.HoconConfigParser.parse.
 */
public class HoconJsonMpConfigProfileTest {
    private static ConfigBean bean;
    private static SeContainer container;

    @BeforeAll
    static void initializeContainerandBean() {
        System.setProperty("config.profile", "dev");
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
    static void destroyContainer() {
        System.clearProperty("config.profile");
        System.clearProperty("mp.initializer.allow");
        System.clearProperty("mp.initializer.no-warn");
        container.close();
    }

    @Test
    void TestHoconConfig() {
        Assertions.assertEquals(bean.hocon_string, "Overriden String");
        Assertions.assertEquals(bean.hocon_number, 10);
        Assertions.assertEquals(bean.hocon_array_0, "Overriden Array 1");
        Assertions.assertEquals(bean.hocon_array_1, "Overriden Array 2");
        Assertions.assertEquals(bean.hocon_array_2, "Overriden Array 3");
        Assertions.assertTrue(bean.hocon_boolean);
     }

    @Test
    void TestHoconIncludeConfig() {
        Assertions.assertEquals(bean.hocon_include_string, "Include String");
        Assertions.assertEquals(bean.hocon_include_number, 20);
        Assertions.assertEquals(bean.hocon_include_array_0, "Include Array 1");
        Assertions.assertEquals(bean.hocon_include_array_1, "Include Array 2");
        Assertions.assertEquals(bean.hocon_include_array_2, "Include Array 3");
        Assertions.assertFalse(bean.hocon_include_boolean);
    }

    @Test
    void TestJsonConfig() {
        Assertions.assertEquals(bean.json_string, "Overriden String");
        Assertions.assertEquals(bean.json_number, 10);
        Assertions.assertEquals(bean.json_array_0, "Overriden Array 1");
        Assertions.assertEquals(bean.json_array_1, "Overriden Array 2");
        Assertions.assertEquals(bean.json_array_2, "Overriden Array 3");
        Assertions.assertTrue(bean.json_boolean);
    }
}
