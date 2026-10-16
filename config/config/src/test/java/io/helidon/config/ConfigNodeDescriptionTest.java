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

import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigNode.ListNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigNode.ValueNode;
import io.helidon.config.spi.MergingStrategy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("removal")
class ConfigNodeDescriptionTest {
    @Test
    void legacyImplementationInheritsUnknownDescription() {
        ValueNode legacy = new ValueNode() {
            @Override
            public String get() {
                return "8080";
            }

            @Override
            public Optional<String> value() {
                return Optional.of(get());
            }
        };

        assertThat(legacy.description(), is("N/A"));
        assertThat(ValueNode.create("8080").description(), is("N/A"));
        assertThat(ObjectNode.empty().description(), is("N/A"));
        assertThat(ObjectNode.simple("server.port", "8080").description(), is("N/A"));
        assertThat(ObjectNode.builder().build().description(), is("N/A"));
        assertThat(ListNode.builder().build().description(), is("N/A"));
    }

    @Test
    void legacyInitializerMutatesReceiverAndDescendants() {
        ValueNodeImpl value = ValueNodeImpl.create("8080");
        assertThat(value.initDescription("value-source"), sameInstance(value));
        assertThat(value.description(), is("value-source"));
        value.initDescription(null);
        assertThat(value.description(), is("N/A"));

        ObjectNodeImpl object = ObjectNodeImpl.wrap(ObjectNode.builder()
                .addList("ports", ListNode.builder().addValue(value).build())
                .build());
        assertThat(object.initDescription("object-source"), sameInstance(object));
        ListNode ports = (ListNode) object.get("ports");
        assertThat(object.description(), is("object-source"));
        assertThat(ports.description(), is("object-source"));
        assertThat(ports.getFirst().description(), is("object-source"));
        object.initDescription(null);
        assertThat(object.description(), is("N/A"));
        assertThat(ports.description(), is("N/A"));
        assertThat(ports.getFirst().description(), is("N/A"));
    }

    @Test
    void wrappingRetainsIndependentOriginsAndHybridValue() {
        ValueNode value = new DescribedValue("8080", "value-source");
        ObjectNode child = new DescribedObject(Map.of("port", value), "child-name", "object-source");
        ListNode list = new DescribedList(List.of(child), "list-name", "list-source");
        ObjectNode original = new DescribedObject(Map.of("servers", list), "root-name", "root-source");

        ObjectNode copy = ObjectNode.wrap(original);

        assertThat(copy.value(), is(Optional.of("root-name")));
        assertThat(copy.description(), is("root-source"));
        ListNode copiedList = (ListNode) copy.get("servers");
        assertThat(copiedList.value(), is(Optional.of("list-name")));
        assertThat(copiedList.description(), is("list-source"));
        ObjectNode copiedChild = (ObjectNode) copiedList.getFirst();
        assertThat(copiedChild.value(), is(Optional.of("child-name")));
        assertThat(copiedChild.description(), is("object-source"));
        assertThat(copiedChild.get("port").value(), is(Optional.of("8080")));
        assertThat(copiedChild.get("port").description(), is("value-source"));

        ObjectNode secondCopy = ObjectNode.wrap(copy, Function.identity());
        assertThat(secondCopy.description(), is("root-source"));
        assertThat(((ListNode) secondCopy.get("servers")).getFirst().description(), is("object-source"));
    }

    @Test
    void changingLegacyDescriptionDoesNotRelabelWrappedCopy() {
        ObjectNodeImpl original = ObjectNodeImpl.wrap(ObjectNode.builder()
                .addList("ports", ListNode.builder().addValue("8080").build()).build())
                .initDescription("first-source");
        ObjectNode copy = ObjectNode.wrap(original);

        original.initDescription("second-source");

        assertThat(original.description(), is("second-source"));
        assertThat(((ListNode) original.get("ports")).getFirst().description(), is("second-source"));
        assertThat(copy.description(), is("first-source"));
        assertThat(copy.get("ports").description(), is("first-source"));
        assertThat(((ListNode) copy.get("ports")).getFirst().description(), is("first-source"));
    }

    @Test
    void interfaceBuildersSupportSeededMembersAndTokenResolution() {
        ValueNode port = new DescribedValue("8080", "port-source");
        ObjectNode object = ObjectNode.builder(token -> token.replace("alias", "server"))
                .from(Map.of("alias", port))
                .build();
        assertThat(object.get("server").description(), is("port-source"));
        assertThat(object.get("server").value(), is(Optional.of("8080")));

        ListNode list = ListNode.builder(Function.identity())
                .addNode(port)
                .addNode(object)
                .addNode(ListNode.builder().addValue("9090").build())
                .build();
        assertThat(list, hasSize(3));
        assertThat(list.get(0).description(), is("port-source"));
        assertThat(((ObjectNode) list.get(1)).get("server").description(), is("port-source"));
        assertThat(((ListNode) list.get(2)).getFirst().value(), is(Optional.of("9090")));
    }

    @Test
    void objectBuilderReuseDoesNotChangeEarlierNodes() {
        ObjectNode.Builder builder = ObjectNode.builder()
                .addValue("server.port", new DescribedValue("8080", "first-source"));
        ObjectNode first = builder.build();
        builder.addValue("server.port", new DescribedValue("9090", "second-source"))
                .addValue("added", "later");
        ObjectNode second = builder.build();

        assertThat(first.keySet(), is(Set.of("server")));
        assertThat(((ObjectNode) first.get("server")).get("port").value(), is(Optional.of("8080")));
        assertThat(((ObjectNode) first.get("server")).get("port").description(), is("first-source"));
        assertThat(((ObjectNode) second.get("server")).get("port").value(), is(Optional.of("9090")));
        assertThat(((ObjectNode) second.get("server")).get("port").description(), is("second-source"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void objectSnapshotsPreserveMemberEncounterOrder(boolean reverseOrder) {
        List<String> names = reverseOrder ? List.of("third", "second", "first") : List.of("first", "second", "third");
        Map<String, ConfigNode> members = new LinkedHashMap<>();
        names.forEach(name -> members.put(name, new DescribedValue(name, name + "-source")));
        ObjectNode snapshot = new ObjectNodeImpl(members, Function.identity());

        assertThat(List.copyOf(snapshot.keySet()), is(names));
        members.clear();

        assertThat(List.copyOf(snapshot.keySet()), is(names));
        names.forEach(name -> assertThat(snapshot.get(name).description(), is(name + "-source")));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("added", ValueNode.create("later")));
    }

    @Test
    void listBuilderReuseDoesNotChangeEarlierNodes() {
        ListNode.Builder builder = ListNode.builder().addValue(new DescribedValue("8080", "first-source"));
        ListNode first = builder.build();
        ListNode second = builder.addValue(new DescribedValue("9090", "second-source")).build();

        assertThat(first, hasSize(1));
        assertThat(first.getFirst().description(), is("first-source"));
        assertThat(second, hasSize(2));
        assertThat(second.get(1).description(), is("second-source"));
        assertThrows(UnsupportedOperationException.class, () -> first.add(ValueNode.create("7070")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void mixedObjectMergeKeepsEachChildOrigin(boolean reversePriority) {
        ObjectNode first = described(ObjectNode.simple("app.port", "8080"), "first-source");
        ObjectNode second = described(ObjectNode.simple("app.host", "localhost"), "second-source");

        ObjectNode merged = MergingStrategy.fallback().merge(reversePriority ? List.of(second, first) : List.of(first, second));

        ObjectNode app = (ObjectNode) merged.get("app");
        assertThat(merged.description(), is("N/A"));
        assertThat(app.description(), is("N/A"));
        assertThat(app.get("port").description(), is("first-source"));
        assertThat(app.get("host").description(), is("second-source"));
        assertThat(first.description(), is("first-source"));
        assertThat(second.description(), is("second-source"));
    }

    @Test
    void sameSourceMergeKeepsKnownContainerOrigin() {
        ObjectNode first = described(ObjectNode.simple("app.port", "8080"), "common-source");
        ObjectNode second = described(ObjectNode.simple("app.host", "localhost"), "common-source");

        ObjectNode merged = MergingStrategy.fallback().merge(List.of(first, second));

        assertThat(merged.description(), is("common-source"));
        assertThat(merged.get("app").description(), is("common-source"));
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 10})
    void manySourcesRetainRootValuePriorityChildOriginsAndIsolation(int sourceCount) {
        List<ObjectNode> sources = new ArrayList<>();
        sources.add(ObjectNode.empty());
        for (int i = 0; i < sourceCount; i++) {
            ObjectNode.Builder builder = ObjectNode.builder()
                    .addValue("shared", "value-" + i)
                    .addValue("unique-" + i, "unique-value-" + i)
                    .addValue("nested.entry-" + i, "nested-value-" + i);
            if (i > 0) {
                builder.value("root-value-" + i);
            }
            sources.add(described(builder.build(), "source-" + i));
            sources.add(ObjectNode.empty());
        }

        ObjectNode merged = MergingStrategy.fallback().merge(sources);

        assertThat(merged.value(), is(Optional.of("root-value-1")));
        assertThat(merged.description(), is("source-1"));
        assertThat(merged.get("shared").value(), is(Optional.of("value-0")));
        assertThat(merged.get("shared").description(), is("source-0"));
        ObjectNode nested = (ObjectNode) merged.get("nested");
        assertThat(nested.description(), is("N/A"));
        assertThat(nested.keySet(), hasSize(sourceCount));
        for (int i = 0; i < sourceCount; i++) {
            assertThat(merged.get("unique-" + i).value(), is(Optional.of("unique-value-" + i)));
            assertThat(merged.get("unique-" + i).description(), is("source-" + i));
            assertThat(nested.get("entry-" + i).value(), is(Optional.of("nested-value-" + i)));
            assertThat(nested.get("entry-" + i).description(), is("source-" + i));
            ObjectNode input = sources.get(i * 2 + 1);
            assertThat(input.description(), is("source-" + i));
            assertThat(input.get("shared").value(), is(Optional.of("value-" + i)));
            assertThat(input.get("shared").description(), is("source-" + i));
        }

        ((ObjectNodeImpl) sources.get(1)).initDescription("changed-input");
        assertThat(merged.get("shared").description(), is("source-0"));
        assertThat(merged.get("unique-0").description(), is("source-0"));
        assertThat(nested.get("entry-0").description(), is("source-0"));
        ((ObjectNodeImpl) merged).initDescription("changed-result");
        assertThat(sources.get(3).description(), is("source-1"));
        assertThat(sources.get(3).get("shared").description(), is("source-1"));
    }

    @Test
    void emptyRootsDoNotReplaceKnownContainerOrigin() {
        ObjectNode known = described(ObjectNode.simple("server.port", "8080"), "application-source");
        ObjectNode unrelatedEmpty = described(ObjectNode.builder().build(), "empty-source");

        ObjectNode merged = MergingStrategy.fallback().merge(List.of(ObjectNode.empty(), unrelatedEmpty, known,
                                                                    ObjectNode.empty(), unrelatedEmpty));

        assertThat(merged.description(), is("application-source"));
        assertThat(merged.get("server").description(), is("application-source"));
        assertThat(((ObjectNode) merged.get("server")).get("port").description(), is("application-source"));
        assertThat(known.description(), is("application-source"));
        assertThat(unrelatedEmpty.description(), is("empty-source"));
        assertThat(MergingStrategy.fallback().merge(List.of()).description(), is("N/A"));
        assertThat(MergingStrategy.fallback().merge(List.of(unrelatedEmpty)).description(), is("empty-source"));
    }

    @Test
    void valueOnlyRootContributesItsValueAndOrigin() {
        ObjectNode valueOnly = described(ObjectNode.builder().value("selected-root").build(), "value-source");
        ObjectNode lowerPriority = described(ObjectNode.builder().value("fallback-root").addValue("port", "8080").build(),
                                             "fallback-source");

        ObjectNode merged = MergingStrategy.fallback().merge(List.of(ObjectNode.empty(), valueOnly, lowerPriority,
                                                                    ObjectNode.empty()));

        assertThat(merged.value(), is(Optional.of("selected-root")));
        assertThat(merged.description(), is("value-source"));
        assertThat(merged.get("port").value(), is(Optional.of("8080")));
        assertThat(merged.get("port").description(), is("fallback-source"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rootHybridMergeRetainsWinningValueOrigin(boolean reversePriority) {
        ObjectNode first = described(ObjectNode.builder().value("first-root").addValue("port", "8080").build(),
                                     "first-source");
        ObjectNode second = described(ObjectNode.builder().value("second-root").addValue("host", "localhost").build(),
                                      "second-source");

        ObjectNode merged = MergingStrategy.fallback().merge(reversePriority ? List.of(second, first) : List.of(first, second));

        String winner = reversePriority ? "second" : "first";
        assertThat(merged.value(), is(Optional.of(winner + "-root")));
        assertThat(merged.description(), is(winner + "-source"));
        assertThat(merged.get("port").description(), is("first-source"));
        assertThat(merged.get("host").description(), is("second-source"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void hybridObjectMergeRetainsWinningValueOrigin(boolean scalarFirst) {
        ObjectNode scalar = described(ObjectNode.simple("app", "app-name"), "value-source");
        ObjectNode branch = described(ObjectNode.simple("app.port", "8080"), "branch-source");

        ObjectNode merged = MergingStrategy.fallback().merge(scalarFirst ? List.of(scalar, branch) : List.of(branch, scalar));

        ObjectNode app = (ObjectNode) merged.get("app");
        assertThat(app.value(), is(Optional.of("app-name")));
        assertThat(app.description(), is("value-source"));
        assertThat(app.get("port").description(), is("branch-source"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void hybridListMergeRetainsWinningValueOrigin(boolean scalarFirst) {
        ObjectNode scalar = described(ObjectNode.simple("ports", "port-names"), "value-source");
        ObjectNode branch = described(ObjectNode.builder()
                                             .addList("ports", ListNode.builder().addValue("8080").build()).build(),
                                     "list-source");

        ObjectNode merged = MergingStrategy.fallback().merge(scalarFirst ? List.of(scalar, branch) : List.of(branch, scalar));

        ListNode ports = (ListNode) merged.get("ports");
        assertThat(ports.value(), is(Optional.of("port-names")));
        assertThat(ports.description(), is("value-source"));
        assertThat(ports.getFirst().description(), is("list-source"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void competingHybridValuesFollowSourcePriority(boolean reversePriority) {
        ObjectNode first = described(ObjectNode.builder()
                                            .addObject("app", ObjectNode.builder().value("first-app").addValue("port", "8080")
                                                    .build())
                                            .addList("ports", ListNode.builder().value("first-ports").addValue("8080").build())
                                            .build(), "first-source");
        ObjectNode second = described(ObjectNode.builder()
                                             .addObject("app", ObjectNode.builder().value("second-app").addValue("host", "local")
                                                     .build())
                                             .addList("ports", ListNode.builder().value("second-ports").addValue("9090").build())
                                             .build(), "second-source");

        ObjectNode merged = MergingStrategy.fallback().merge(reversePriority ? List.of(second, first) : List.of(first, second));

        String winner = reversePriority ? "second" : "first";
        assertThat(merged.get("app").value(), is(Optional.of(winner + "-app")));
        assertThat(merged.get("app").description(), is(winner + "-source"));
        assertThat(((ObjectNode) merged.get("app")).get("port").description(), is("first-source"));
        assertThat(((ObjectNode) merged.get("app")).get("host").description(), is("second-source"));
        assertThat(merged.get("ports").value(), is(Optional.of(winner + "-ports")));
        assertThat(merged.get("ports").description(), is(winner + "-source"));
        assertThat(((ListNode) merged.get("ports")).getFirst().description(), is(winner + "-source"));
    }

    @Test
    void dottedPathMergeRetainsExistingDirectValueOrigins() {
        ObjectNode.Builder scalarBuilder = ObjectNode.builder()
                .addValue("app", new DescribedValue("app-name", "value-source"));
        ObjectNode scalarTree = scalarBuilder.addValue("app.port", new DescribedValue("8080", "port-source")).build();
        assertThat(scalarTree.get("app").value(), is(Optional.of("app-name")));
        assertThat(scalarTree.get("app").description(), is("value-source"));
        assertThat(((ObjectNode) scalarTree.get("app")).get("port").description(), is("port-source"));

        ObjectNode hybrid = described(ObjectNode.builder().value("app-name").addValue("port", "8080").build(),
                                     "object-source");
        ObjectNode objectTree = ObjectNode.builder().addObject("app", hybrid)
                .addValue("app.host", new DescribedValue("localhost", "host-source")).build();
        assertThat(objectTree.get("app").description(), is("object-source"));
        assertThat(((ObjectNode) objectTree.get("app")).get("port").description(), is("object-source"));
        assertThat(((ObjectNode) objectTree.get("app")).get("host").description(), is("host-source"));

        ListNode list = ((ListNodeImpl) ListNode.builder().value("port-names").addValue("8080").addValue("9090").build())
                .initDescription("list-source");
        ObjectNode listTree = ObjectNode.builder().addList("ports", list)
                .addValue("ports.0", new DescribedValue("7070", "override-source")).build();
        ListNode ports = (ListNode) listTree.get("ports");
        assertThat(ports.value(), is(Optional.of("port-names")));
        assertThat(ports.description(), is("list-source"));
        assertThat(ports.get(0).description(), is("override-source"));
        assertThat(ports.get(1).description(), is("list-source"));
    }

    @Test
    void mergingFlatKeysPreservesDotsInsideTokenReferences() {
        ValueNode port = new DescribedValue("8080", "token-source");
        String key = "servers.${group.name}.port";
        ObjectNode flat = ObjectNode.builder().addNode(key, port).build();
        ObjectNode expected = ObjectNode.builder().addValue(key, port).build();

        ObjectNode merged = MergingStrategy.fallback().merge(List.of(flat));

        assertThat(merged, is(expected));
        ObjectNode expectedServers = (ObjectNode) expected.get("servers");
        ObjectNode servers = (ObjectNode) merged.get("servers");
        assertThat(servers.keySet(), is(expectedServers.keySet()));
        String groupKey = expectedServers.keySet().iterator().next();
        ObjectNode group = (ObjectNode) servers.get(groupKey);
        assertThat(group.get("port").value(), is(Optional.of("8080")));
        assertThat(group.get("port").description(), is("token-source"));
    }

    @Test
    void mergeDiagnosticsOmitUnknownOriginAndIncludeKnownSources() {
        ObjectNode object = ObjectNode.simple("ports.name", "main");
        ObjectNode list = ObjectNode.builder().addList("ports", ListNode.builder().addValue("8080").build()).build();
        ConfigException unknown = assertThrows(ConfigException.class,
                                              () -> MergingStrategy.fallback().merge(List.of(object, list)));
        assertThat(unknown.getMessage(), containsString("Cannot merge OBJECT"));
        assertThat(unknown.getMessage(), not(containsString("from N/A")));

        ConfigException known = assertThrows(ConfigException.class,
                                            () -> MergingStrategy.fallback().merge(List.of(described(object, "object-source"),
                                                                                          described(list, "list-source"))));
        assertThat(known.getMessage(), containsString("from object-source"));
        assertThat(known.getMessage(), containsString("from list-source"));
    }

    private static ObjectNode described(ObjectNode node, String source) {
        return ObjectNodeImpl.wrap(node).initDescription(source);
    }

    private record DescribedValue(String get, String description) implements ValueNode {
        @Override
        public Optional<String> value() {
            return Optional.of(get);
        }
    }

    private static final class DescribedObject extends AbstractMap<String, ConfigNode> implements ObjectNode {
        private final Map<String, ConfigNode> members;
        private final String value;
        private final String description;

        private DescribedObject(Map<String, ConfigNode> members, String value, String description) {
            this.members = members;
            this.value = value;
            this.description = description;
        }

        @Override
        public Set<Entry<String, ConfigNode>> entrySet() {
            return members.entrySet();
        }

        @Override
        public Optional<String> value() {
            return Optional.of(value);
        }

        @Override
        public String description() {
            return description;
        }
    }

    private static final class DescribedList extends AbstractList<ConfigNode> implements ListNode {
        private final List<ConfigNode> elements;
        private final String value;
        private final String description;

        private DescribedList(List<ConfigNode> elements, String value, String description) {
            this.elements = elements;
            this.value = value;
            this.description = description;
        }

        @Override
        public ConfigNode get(int index) {
            return elements.get(index);
        }

        @Override
        public int size() {
            return elements.size();
        }

        @Override
        public Optional<String> value() {
            return Optional.of(value);
        }

        @Override
        public String description() {
            return description;
        }
    }
}
