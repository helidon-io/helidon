/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.config;

import io.helidon.config.spi.ConfigNode;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/*
 * Make sure that when we copy a config that uses list nodes, the list nodes are honored in the copy as well.
 */
class ConfigCopyTest {
    @Test
    void testCopyWithArray() {
        ConfigNode.ListNode listNode = ConfigNode.ListNode.builder()
                .addValue("bbb")
                .addValue("ccc")
                .addValue("ddd")
                .build();

        ConfigNode.ObjectNode otherObject = ConfigNode.ObjectNode.builder()
                .addValue("first", "firstValue")
                .addValue("second", "secondValue")
                .build();

        ConfigNode.ObjectNode rootNode = ConfigNode.ObjectNode.builder()
                .addList("aaa", listNode)
                .addValue("value", "some value")
                .addObject("object", otherObject)
                .build();

        Config original = Config.builder()
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .addSource(io.helidon.config.ConfigSources.create(rootNode))
                .build();

        assertThat("Node type of original aaa",
                   original.get("aaa").type(),
                   is(Config.Type.LIST));

        Config copy = Config.builder()
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .addSource(ConfigSources.create(original))
                .build();

        assertThat("Node type of copied aaa",
                   copy.get("aaa").type(),
                   is(Config.Type.LIST));

        // now also assert that all values were correctly copied
        assertThat(copy.get("value").asString(), is(ConfigValues.simpleValue("some value")));
        assertThat(copy.get("object.first").asString(), is(ConfigValues.simpleValue("firstValue")));
        assertThat(copy.get("object.second").asString(), is(ConfigValues.simpleValue("secondValue")));
        assertThat(copy.get("aaa.0").asString(), is(ConfigValues.simpleValue("bbb")));
    }

    @Test
    void testDottedKey() {
        var withDottedName = ConfigNode.ObjectNode.builder()
                .addValue(Config.Key.escapeName("first.one"), "firstValue")
                .addValue("second", "secondValue")
                .build();

        var rootNode = ConfigNode.ObjectNode.builder()
                .addObject("object", withDottedName)
                .build();

        var originalConfig = Config.builder()
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .addSource(io.helidon.config.ConfigSources.create(rootNode))
                .build();

        assertThat("Dotted name entry in original",
                   originalConfig.get("object." + Config.Key.escapeName("first.one")).asString().get(),
                   is("firstValue"));

        var copyConfig = Config.builder()
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .addSource(ConfigSources.create(originalConfig))
                .build();

        var firstOneConfigValue = copyConfig.get("object." + Config.Key.escapeName("first.one")).asString();
        assertThat("Dotted name key in copy is present",
                   firstOneConfigValue.isPresent(),
                   is(true));
        assertThat("Dotted name value in copy",
                   firstOneConfigValue.get(),
                   is("firstValue"));
    }
}
