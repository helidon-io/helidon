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

package io.helidon.config.tests.lazy.source;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.LazyConfigSource;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

public class LazySourceTest {
    @Test
    void testLazySource() {
        Map<String, String> map = new HashMap<>();
        map.put("tree.node2", "value2");

        TestLazySource testLazySource = new TestLazySource(map);

        Config config = Config.builder()
                .addSource(testLazySource)
                .addSource(ConfigSources.create(Map.of("tree.node1", "value1")))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        assertThat(config.get("tree.node1").as(String.class).get(), is("value1"));

        // when using lazy config source, we defer the loading of the key until it is actually requested
        assertThat("tree.node2 should exist", config.get("tree.node2").exists(), is(true));
        assertThat(config.get("tree.node2").as(String.class).get(), is("value2"));
        assertThat("tree.node3 should not exist", config.get("tree.node3").exists(), is(false));

        // config tree is immutable once created - so we ignore values that appear in the source later in time,
        // as we have already resolved that the node is not present
        map.put("tree.node3", "value3");
        assertThat("tree.node3 should not exist, as it was already cached as not existing",
                   config.get("tree.node3").exists(),
                   is(false));

        // each node should have been requested from the config source, starting from root
        assertThat(testLazySource.requestedNodes, containsInAnyOrder("", "tree", "tree.node1", "tree.node2", "tree.node3"));
    }

    private static class TestLazySource implements LazyConfigSource, ConfigSource {
        private final List<String> requestedNodes = new ArrayList<>();
        private final Map<String, String> values;

        private TestLazySource(Map<String, String> values) {
            this.values = values;
        }

        @Override
        public Optional<ConfigNode> node(String key) {
            requestedNodes.add(key);
            return Optional.ofNullable(values.get(key))
                    .map(ConfigNode.ValueNode::create);
        }
    }
}
