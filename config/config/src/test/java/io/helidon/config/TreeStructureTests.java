/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test that nodes correctly return empty even for tree.
 */
public class TreeStructureTests {
    @Test
    void testEmptyLeafAndTreeNodes() {
        Config config = Config.builder(ConfigSources.create(
                Map.of("a", "rootValue",
                       "a.b", "leafTreeNode",
                       "a.b.c", "leafNode",
                       "b.c", "leafNode",
                       "c.a.0", "first",
                       "c.a.1", "second",
                       "c.a.2", "third")
        )).build();

        assertThat(config.get("a").asString(), is(ConfigValues.simpleValue("rootValue")));
        assertThat(config.get("a.b").asString(), is(ConfigValues.simpleValue("leafTreeNode")));
        assertThat(config.get("a.b.c").asString(), is(ConfigValues.simpleValue("leafNode")));

        assertThat(config.get("b").asString(), is(ConfigValues.empty()));
        assertThat(config.get("b.c").asString(), is(ConfigValues.simpleValue("leafNode")));

        assertThat(config.get("c").asString(), is(ConfigValues.empty()));
        assertThat(config.get("c.a").asString(), is(ConfigValues.empty()));
        assertThat(config.get("c.a").asList(String.class).get(), is(List.of("first", "second", "third")));
    }

    @Test
    void testListAndDirectValue() {
        Config config = Config.builder(ConfigSources.create(
                Map.of("c.a", "treeAndLeafNode",
                                        "c.a.0", "first",
                                        "c.a.1", "second",
                                        "c.a.2", "third")
        )).build();

        assertThat(config.get("c").asString(), is(ConfigValues.empty()));
        assertThat(config.get("c.a").asString(), is(ConfigValues.simpleValue("treeAndLeafNode")));
        assertThat(config.get("c.a").asList(String.class).get(), is(List.of("first", "second", "third")));
    }
}
