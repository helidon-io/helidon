/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.EventConfigSource;
import io.helidon.config.spi.LazyConfigSource;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.fail;

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

    @Test
    void testLazyEventSource() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        Map<String, String> map = new HashMap<>();
        map.put("tree.node2", "value2");

        TestLazyEventSource testLazySource = new TestLazyEventSource(map);

        Config config = Config.builder()
                .addSource(testLazySource)
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        assertThat(testLazySource.changeListener, notNullValue());

        var node = config.get("tree.node2");
        assertThat(node.asString().get(), is("value2"));

        AtomicReference<String> value = new AtomicReference<>();
        node.onChange(it -> {
            // changes are executed on an executor service
            value.set(it.asString().get());
            latch.countDown();
        });

        testLazySource.change("tree.node2", "value3");

        // we should get the change almost immediately
        if (latch.await(5, TimeUnit.SECONDS)) {

            assertThat(value.get(), notNullValue());
            assertThat(value.get(), is("value3"));
        } else {
            fail("Changed config was not received.");
        }
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

    private static class TestLazyEventSource implements LazyConfigSource, ConfigSource, EventConfigSource {
        private final List<String> requestedNodes = new ArrayList<>();
        private final Map<String, String> values;

        private volatile BiConsumer<String, ConfigNode> changeListener;

        private TestLazyEventSource(Map<String, String> values) {
            this.values = values;
        }

        @Override
        public Optional<ConfigNode> node(String key) {
            requestedNodes.add(key);
            return Optional.ofNullable(values.get(key))
                    .map(ConfigNode.ValueNode::create);
        }

        @Override
        public void onChange(BiConsumer<String, ConfigNode> changedNode) {
            this.changeListener = changedNode;
        }

        private void change(String key, String value) {
            values.put(key, value);
            if (changeListener != null) {
                changeListener.accept(key, ConfigNode.ValueNode.create(value));
            }
        }
    }
}
