/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

import io.helidon.config.spi.ConfigContent.NodeContent;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigNode.ListNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigNode.ValueNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.EventConfigSource;
import io.helidon.config.spi.LazyConfigSource;
import io.helidon.config.spi.MergingStrategy;
import io.helidon.config.spi.NodeConfigSource;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

@SuppressWarnings("removal")
class ConfigNodeDescriptionRuntimeTest {
    @Test
    void initialLoadAnnotatesEveryNodeWithoutChangingSourceData() {
        ObjectNode original = ObjectNode.builder()
                .addValue("server.port", "8080")
                .addList("servers", ListNode.builder().addObject(ObjectNode.simple("host", "localhost")).build())
                .build();

        ObjectNode loaded = runtime(new FixedSource(original, "application.properties")).load().orElseThrow();

        assertTreeDescription(loaded, "application.properties");
        assertTreeDescription(original, "N/A");
        assertThat(((ObjectNode) loaded.get("server")).get("port").value(), is(Optional.of("8080")));
    }

    @Test
    void reusingSourceFragmentsCannotRelabelEarlierRuntimeTrees() {
        ObjectNode shared = ObjectNode.builder()
                .addList("ports", ListNode.builder().addValue("8080").build())
                .addValue("server.host", "localhost")
                .build();
        ConfigSourcesRuntime firstRuntime = runtime(new FixedSource(shared, "first-source"));
        ObjectNode first = firstRuntime.load().orElseThrow();
        ObjectNode second = runtime(new FixedSource(shared, "second-source")).load().orElseThrow();

        assertTreeDescription(first, "first-source");
        assertTreeDescription(firstRuntime.latest().orElseThrow(), "first-source");
        assertTreeDescription(second, "second-source");
        assertTreeDescription(shared, "N/A");
    }

    @Test
    void sourceLoadingPreservesKnownChildOrigins() {
        ValueNodeImpl known = ValueNodeImpl.create("8080").initDescription("included-file.properties");
        ObjectNode original = ObjectNode.builder().addValue("port", known).addValue("host", "localhost").build();

        ObjectNode loaded = runtime(new FixedSource(original, "application.properties")).load().orElseThrow();

        assertThat(loaded.description(), is("application.properties"));
        assertThat(loaded.get("port").description(), is("included-file.properties"));
        assertThat(loaded.get("host").description(), is("application.properties"));
        assertThat(original.description(), is("N/A"));
        assertThat(original.get("port").description(), is("included-file.properties"));
        assertThat(original.get("host").description(), is("N/A"));
    }

    @Test
    void lazyKeyDiscoveryAssignsOriginToSelectedNestedValue() {
        ValueNode original = ValueNode.create("9090");
        LazySource lazy = new LazySource(Map.of("server.port", original), "lazy-source");
        ConfigSourcesRuntime runtime = runtime(lazy,
                                               new FixedSource(ObjectNode.simple("server.port", "8080"), "eager-source"));

        ObjectNode loaded = runtime.load().orElseThrow();

        ConfigNode port = ((ObjectNode) loaded.get("server")).get("port");
        assertThat(port.value(), is(Optional.of("9090")));
        assertThat(port.description(), is("lazy-source"));
        assertThat(original.description(), is("N/A"));
        assertThat(((ObjectNode) runtime.latest().orElseThrow().get("server")).get("port").description(), is("lazy-source"));
    }

    @Test
    void onDemandLazyLoadReturnsAndStoresDescribedIsolatedTree() {
        ObjectNode shared = ObjectNode.builder()
                .value("server-name")
                .addList("ports", ListNode.builder().addValue("8080").build())
                .build();
        ConfigSourcesRuntime first = runtime(new LazySource(Map.of("server", shared), "first-lazy-source"));
        ConfigSourcesRuntime second = runtime(new LazySource(Map.of("server", shared), "second-lazy-source"));
        assertThat(first.load(), is(Optional.empty()));
        assertThat(second.load(), is(Optional.empty()));

        ConfigNode firstLoaded = first.lazyValue("server").orElseThrow();
        ConfigNode secondLoaded = second.lazyValue("server").orElseThrow();

        assertTreeDescription(firstLoaded, "first-lazy-source");
        assertTreeDescription(secondLoaded, "second-lazy-source");
        assertTreeDescription(first.latest().orElseThrow().get("server"), "first-lazy-source");
        assertTreeDescription(second.latest().orElseThrow().get("server"), "second-lazy-source");
        assertTreeDescription(shared, "N/A");
        assertThat(firstLoaded.value(), is(Optional.of("server-name")));
    }

    @Test
    void rootChangeAnnotatesReplacementAndKeepsPreviousTree() {
        ObjectNode original = ObjectNode.simple("server.port", "8080");
        ChangingSource source = new ChangingSource(original, "event-source");
        ConfigSourcesRuntime runtime = runtime(source);
        ObjectNode first = runtime.load().orElseThrow();
        List<ObjectNode> notifications = new ArrayList<>();
        runtime.changeListener(changed -> notifications.add(changed.orElseThrow()));
        runtime.startChanges();
        try {
            ObjectNode replacement = ObjectNode.builder()
                    .addValue("server.port", "9090")
                    .addList("ports", ListNode.builder().addValue("9090").build())
                    .build();
            source.change("", replacement);

            assertThat(notifications, hasSize(1));
            assertTreeDescription(notifications.getFirst(), "event-source");
            assertTreeDescription(runtime.latest().orElseThrow(), "event-source");
            assertThat(((ObjectNode) first.get("server")).get("port").value(), is(Optional.of("8080")));
            assertTreeDescription(first, "event-source");
            assertTreeDescription(original, "N/A");
            assertTreeDescription(replacement, "N/A");
        } finally {
            runtime.stopChanges();
        }
    }

    @Test
    void fragmentChangesRetainUnaffectedAndExplicitOrigins() {
        ObjectNode included = ObjectNodeImpl.wrap(ObjectNode.simple("host", "localhost"))
                .initDescription("included-file");
        ObjectNode initial = ObjectNode.builder().addObject("server", included).build();
        ChangingSource source = new ChangingSource(initial, "event-source");
        ConfigSourcesRuntime runtime = runtime(source);
        runtime.load();
        List<ObjectNode> notifications = new ArrayList<>();
        runtime.changeListener(changed -> notifications.add(changed.orElseThrow()));
        runtime.startChanges();
        try {
            ValueNode port = ValueNode.create("8080");
            source.change("server.port", port);
            ObjectNode afterValue = notifications.getLast();
            ObjectNode server = (ObjectNode) afterValue.get("server");
            assertThat(server.get("port").description(), is("event-source"));
            assertThat(server.get("host").description(), is("included-file"));
            assertThat(port.description(), is("N/A"));

            ListNode ports = ListNode.builder().value("port-names").addValue("9090").build();
            source.change("server.ports", ports);
            ListNode loadedPorts = (ListNode) ((ObjectNode) notifications.getLast().get("server")).get("ports");
            assertTreeDescription(loadedPorts, "event-source");
            assertThat(loadedPorts.value(), is(Optional.of("port-names")));
            assertTreeDescription(ports, "N/A");

            ObjectNode tls = ObjectNode.builder().addValue("enabled", "true").build();
            source.change("server.tls", tls);
            ObjectNode latestServer = (ObjectNode) runtime.latest().orElseThrow().get("server");
            assertTreeDescription(latestServer.get("tls"), "event-source");
            assertThat(latestServer.get("host").description(), is("included-file"));
            assertThat(latestServer.get("port").description(), is("event-source"));
            assertThat(((ObjectNode) afterValue.get("server")).keySet(), is(Set.of("host", "port")));
            assertThat(notifications, hasSize(3));
            assertTreeDescription(tls, "N/A");
        } finally {
            runtime.stopChanges();
        }
    }

    @Test
    void sharedChangeFragmentDoesNotContaminateOtherSources() {
        ChangingSource firstSource = new ChangingSource(ObjectNode.simple("existing", "first"), "first-source");
        ChangingSource secondSource = new ChangingSource(ObjectNode.simple("existing", "second"), "second-source");
        ConfigSourcesRuntime first = runtime(firstSource);
        ConfigSourcesRuntime second = runtime(secondSource);
        first.load();
        second.load();
        first.changeListener(_ -> { });
        second.changeListener(_ -> { });
        first.startChanges();
        second.startChanges();
        try {
            ObjectNode shared = ObjectNode.builder()
                    .addList("ports", ListNode.builder().addValue("8080").build()).build();
            firstSource.change("server", shared);
            ObjectNode firstSnapshot = first.latest().orElseThrow();
            secondSource.change("server", shared);

            assertTreeDescription(firstSnapshot.get("server"), "first-source");
            assertTreeDescription(first.latest().orElseThrow().get("server"), "first-source");
            assertTreeDescription(second.latest().orElseThrow().get("server"), "second-source");
            assertTreeDescription(shared, "N/A");
        } finally {
            first.stopChanges();
            second.stopChanges();
        }
    }

    private static ConfigSourcesRuntime runtime(ConfigSource... sources) {
        List<ConfigSourceRuntimeImpl> runtimes = Arrays.stream(sources)
                .map(source -> new ConfigSourceRuntimeImpl(null, source))
                .toList();
        return new ConfigSourcesRuntime(runtimes, MergingStrategy.fallback());
    }

    private static void assertTreeDescription(ConfigNode node, String description) {
        assertThat(node.nodeType() + " node " + node.value(), node.description(), is(description));
        switch (node.nodeType()) {
        case OBJECT -> ((ObjectNode) node).forEach((_, child) -> assertTreeDescription(child, description));
        case LIST -> ((ListNode) node).forEach(child -> assertTreeDescription(child, description));
        case VALUE -> { }
        }
    }

    private record FixedSource(ObjectNode root, String description) implements NodeConfigSource {
        @Override
        public Optional<NodeContent> load() {
            return Optional.of(NodeContent.builder().node(root).build());
        }
    }

    private record LazySource(Map<String, ConfigNode> nodes, String description) implements ConfigSource, LazyConfigSource {
        @Override
        public Optional<ConfigNode> node(String key) {
            return Optional.ofNullable(nodes.get(key));
        }
    }

    private static final class ChangingSource implements NodeConfigSource, EventConfigSource {
        private final ObjectNode initial;
        private final String description;
        private BiConsumer<String, ConfigNode> listener;

        private ChangingSource(ObjectNode initial, String description) {
            this.initial = initial;
            this.description = description;
        }

        @Override
        public Optional<NodeContent> load() {
            return Optional.of(NodeContent.builder().node(initial).build());
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public void onChange(BiConsumer<String, ConfigNode> changedNode) {
            listener = changedNode;
        }

        private void change(String key, ConfigNode node) {
            listener.accept(key, node);
        }
    }
}
