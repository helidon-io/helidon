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
import io.helidon.microprofile.tests.junit5.*;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;

@HelidonTest
@DisableDiscovery
// Helidon MP Extensions
@AddExtension(ServerCdiExtension.class)
@AddExtension(JaxRsCdiExtension.class)
@AddExtension(ConfigCdiExtension.class)
@AddBean(value = ConfigBean.class, scope = Dependent.class)
public class HoconJsonMpConfigTest {
    @Inject
    private ConfigBean bean;

    @Test
    void TestHoconConfig() {
        Assertions.assertEquals(bean.hocon_string, "String");
        Assertions.assertEquals(bean.hocon_number, 10);
        Assertions.assertEquals(bean.hocon_array_0, "Array 1");
        Assertions.assertEquals(bean.hocon_array_1, "Array 2");
        Assertions.assertEquals(bean.hocon_array_2, "Array 3");
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
        Assertions.assertEquals(bean.json_string, "String");
        Assertions.assertEquals(bean.json_number, 10);
        Assertions.assertEquals(bean.json_array_0, "Array 1");
        Assertions.assertEquals(bean.json_array_1, "Array 2");
        Assertions.assertEquals(bean.json_array_2, "Array 3");
        Assertions.assertTrue(bean.json_boolean);
    }
}
