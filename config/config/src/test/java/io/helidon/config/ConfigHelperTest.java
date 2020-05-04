/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

import java.util.Map;

import io.helidon.config.spi.ConfigNode;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ConfigHelperTest {
    @Test
    void testFlattenNodes() {
        ConfigNode.ObjectNode node = ConfigNode.ObjectNode.builder()
                .addValue("simple", "value")
                .addList("list", ConfigNode.ListNode.builder()
                        .addValue("first")
                        .addValue("second")
                        .build())
                .addObject("object", ConfigNode.ObjectNode.builder()
                        .addValue("value", "value2")
                        .build())
                .build();

        Map<String, String> map = ConfigHelper.flattenNodes(node);
        Map<String, String> expected = Map.of(
                "simple", "value",
                "list.0", "first",
                "list.1", "second",
                "object.value", "value2"
        );
        assertThat(map, is(expected));
    }
}