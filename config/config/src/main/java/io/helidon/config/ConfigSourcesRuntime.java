/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.MergingStrategy;

/**
 * Runtime of all configured configuration sources.
 */
final class ConfigSourcesRuntime {
    private final List<RuntimeWithData> loadedData = new LinkedList<>();

    private final List<ConfigSourceRuntimeImpl> allSources;
    private final MergingStrategy mergingStrategy;
    private volatile Consumer<Optional<ObjectNode>> changeListener;

    ConfigSourcesRuntime(List<ConfigSourceRuntimeImpl> allSources,
                         MergingStrategy mergingStrategy) {
        this.allSources = allSources;
        this.mergingStrategy = mergingStrategy;
    }

    // for the purpose of tests
    static ConfigSourcesRuntime empty() {
        return new ConfigSourcesRuntime(List.of(new ConfigSourceRuntimeImpl(null, ConfigSources.empty())),
                                        MergingStrategy.fallback());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ConfigSourcesRuntime that = (ConfigSourcesRuntime) o;
        return allSources.equals(that.allSources);
    }

    @Override
    public int hashCode() {
        return Objects.hash(allSources);
    }

    @Override
    public String toString() {
        return allSources.toString();
    }

    void changeListener(Consumer<Optional<ObjectNode>> changeListener) {
        this.changeListener = changeListener;
    }

    void startChanges() {
        loadedData.stream()
                .filter(loaded -> loaded.runtime().changesSupported())
                .forEach(loaded -> loaded.runtime()
                        .onChange((key, configNode) -> {
                            loaded.data(processChange(loaded.data, key, configNode));
                            changeListener.accept(latest());
                        }));
    }

    private Optional<ObjectNode> processChange(Optional<ObjectNode> oldData, String changedKey, ConfigNode changeNode) {
        ConfigKeyImpl key = ConfigKeyImpl.of(changedKey);
        ObjectNode changeObjectNode = toObjectNode(changeNode);

        if (key.isRoot()) {
            // we have a root, no merging with original data, just return it
            return Optional.of(changeObjectNode);
        }

        ObjectNode newRootNode = ObjectNode.builder().addObject(changedKey, changeObjectNode).build();

        // old data was empty, this is the only data we have
        if (oldData.isEmpty()) {
            return Optional.of(newRootNode);
        }

        // we had data, now we have new data (not on root), let's merge
        return Optional.of(mergingStrategy.merge(List.of(newRootNode, oldData.get())));
    }

    private ObjectNode toObjectNode(ConfigNode changeNode) {
        switch (changeNode.nodeType()) {
        case OBJECT:
            return (ObjectNode) changeNode;
        case LIST:
            return ObjectNode.builder().addList("", (ConfigNode.ListNode) changeNode).build();
        case VALUE:
            return ObjectNode.builder().value(((ConfigNode.ValueNode) changeNode).get()).build();
        default:
            throw new IllegalArgumentException("Unsupported node type: " + changeNode.nodeType());
        }
    }

    synchronized Optional<ObjectNode> latest() {
        List<ObjectNode> objectNodes = loadedData.stream()
                .map(RuntimeWithData::data)
                .flatMap(Optional::stream)
                .collect(Collectors.toList());

        return Optional.of(mergingStrategy.merge(objectNodes));
    }

    synchronized Optional<ObjectNode> load() {

        for (ConfigSourceRuntimeImpl source : allSources) {
            if (source.isLazy()) {
                loadedData.add(new RuntimeWithData(source, Optional.empty()));
            } else {
                loadedData.add(new RuntimeWithData(source, source.load()
                        .map(ObjectNodeImpl::wrap)
                        .map(objectNode -> objectNode.initDescription(source.description()))));
            }
        }

        Set<String> allKeys = loadedData.stream()
                .map(RuntimeWithData::data)
                .flatMap(Optional::stream)
                .flatMap(this::streamKeys)
                .collect(Collectors.toSet());

        if (allKeys.isEmpty()) {
            return Optional.empty();
        }

        // now we have all the keys, let's load them from the lazy sources
        for (RuntimeWithData data : loadedData) {
            if (data.runtime().isLazy()) {
                data.data(loadLazy(data.runtime(), allKeys));
            }
        }

        List<ObjectNode> objectNodes = loadedData.stream()
                .map(RuntimeWithData::data)
                .flatMap(Optional::stream)
                .collect(Collectors.toList());

        return Optional.of(mergingStrategy.merge(objectNodes));
    }

    private Optional<ObjectNode> loadLazy(ConfigSourceRuntime runtime, Set<String> allKeys) {
        Map<String, ConfigNode> nodes = new HashMap<>();
        for (String key : allKeys) {
            runtime.node(key).ifPresent(it -> nodes.put(key, it));
        }

        if (nodes.isEmpty()) {
            return Optional.empty();
        }

        ObjectNode.Builder builder = ObjectNode.builder();

        nodes.forEach(builder::addNode);

        return Optional.of(builder.build());
    }

    private Stream<String> streamKeys(ObjectNode objectNode) {
        return ConfigHelper.createFullKeyToNodeMap(objectNode)
                .keySet()
                .stream()
                .map(ConfigKeyImpl::toString);
    }

    private static final class RuntimeWithData {
        private final ConfigSourceRuntimeImpl runtime;
        private Optional<ObjectNode> data;

        private RuntimeWithData(ConfigSourceRuntimeImpl runtime, Optional<ObjectNode> data) {
            this.runtime = runtime;
            this.data = data;
        }

        private void data(Optional<ObjectNode> data) {
            this.data = data;
        }

        private ConfigSourceRuntimeImpl runtime() {
            return runtime;
        }

        private Optional<ObjectNode> data() {
            return data;
        }

        @Override
        public String toString() {
            return runtime.toString();
        }
    }
}
