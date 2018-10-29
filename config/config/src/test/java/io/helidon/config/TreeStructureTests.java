/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Optional;

import io.helidon.common.CollectionsHelper;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Test that nodes correctly return empty even for tree.
 */
public class TreeStructureTests {
    @Test
    void testEmptyLeafAndTreeNodes() {
        Config config = Config.withSources(ConfigSources.from(
                CollectionsHelper.mapOf("a", "rootValue",
                                        "a.b", "leafTreeNode",
                                        "a.b.c", "leafNode",
                                        "b.c", "leafNode",
                                        "c.a.0", "first",
                                        "c.a.1", "second",
                                        "c.a.2", "third")
        )).build();

        assertThat(config.get("a").value(), is(Optional.of("rootValue")));
        assertThat(config.get("a.b").value(), is(Optional.of("leafTreeNode")));
        assertThat(config.get("a.b.c").value(), is(Optional.of("leafNode")));

        assertThat(config.get("b").value(), is(Optional.empty()));
        assertThat(config.get("b.c").value(), is(Optional.of("leafNode")));

        assertThat(config.get("c").value(), is(Optional.empty()));
        assertThat(config.get("c.a").value(), is(Optional.empty()));
        assertThat(config.get("c.a").asList(String.class), is(CollectionsHelper.listOf("first", "second", "third")));
    }

    @Test
    void testListAndDirectValue() {
        Config config = Config.withSources(ConfigSources.from(
                CollectionsHelper.mapOf("c.a", "treeAndLeafNode",
                                        "c.a.0", "first",
                                        "c.a.1", "second",
                                        "c.a.2", "third")
        )).build();

        assertThat(config.get("c").value(), is(Optional.empty()));
        assertThat(config.get("c.a").value(), is(Optional.of("treeAndLeafNode")));
        assertThat(config.get("c.a").asList(String.class), is(CollectionsHelper.listOf("first", "second", "third")));
    }
}
