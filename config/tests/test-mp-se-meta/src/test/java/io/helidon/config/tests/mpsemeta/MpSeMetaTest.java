/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.config.tests.mpsemeta;

import io.helidon.common.config.GlobalConfig;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MpSeMetaTest {
    @Order(0)
    @Test
    public void testSeMeta() {
        // this should not fail
        io.helidon.config.Config config = io.helidon.config.Config.create();
        assertThat(config.get("helidon.app.value").asString().asOptional(),
                   optionalValue(is("app-value")));
    }

    @Order(1)
    @Test
    public void testMpMeta() {
        System.setProperty("io.helidon.config.mp.meta-config", "meta-config.yaml");
        Config config = ConfigProvider.getConfig();
        assertThat(config.getValue("helidon.test.value", String.class), is("value"));

        assertThat(GlobalConfig.config()
                           .get("helidon.test.value")
                           .asString()
                           .asOptional(), optionalValue(is("value")));
    }
}
