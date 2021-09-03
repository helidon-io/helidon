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

import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.helidon.config.spi.ConfigNode;

/**
 * Common Configuration utilities.
 */
public final class ConfigHelper {
    private ConfigHelper() {
        throw new AssertionError("Instantiation not allowed.");
    }

    /**
     * Create a map of keys to string values from an object node.
     *
     * @param objectNode node to flatten
     * @return a map of all nodes
     */
    public static Map<String, String> flattenNodes(ConfigNode.ObjectNode objectNode) {
        return ConfigHelper.flattenNodes(ConfigKeyImpl.of(), objectNode)
                .filter(e -> e.getValue() instanceof ValueNodeImpl)
                .collect(Collectors.toMap(
                        e -> e.getKey().toString(),
                        e -> Config.Key.escapeName(((ValueNodeImpl) e.getValue()).get())
                ));
    }

    static Map<ConfigKeyImpl, ConfigNode> createFullKeyToNodeMap(ConfigNode.ObjectNode objectNode) {
        Map<ConfigKeyImpl, ConfigNode> result;

        Stream<Map.Entry<ConfigKeyImpl, ConfigNode>> flattenNodes = objectNode.entrySet()
                .stream()
                .map(node -> flattenNodes(ConfigKeyImpl.of(node.getKey()), node.getValue()))
                .reduce(Stream.empty(), Stream::concat);
        result = flattenNodes.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        result.put(ConfigKeyImpl.of(), objectNode);

        return result;
    }

    static Stream<Map.Entry<ConfigKeyImpl, ConfigNode>> flattenNodes(ConfigKeyImpl key, ConfigNode node) {
        switch (node.nodeType()) {
        case OBJECT:
            return ((ConfigNode.ObjectNode) node).entrySet().stream()
                    .map(e -> flattenNodes(key.child(e.getKey()), e.getValue()))
                    .reduce(Stream.of(new AbstractMap.SimpleEntry<>(key, node)), Stream::concat);
        case LIST:
            return IntStream.range(0, ((ConfigNode.ListNode) node).size())
                    .boxed()
                    .map(i -> flattenNodes(key.child(Integer.toString(i)), ((ConfigNode.ListNode) node).get(i)))
                    .reduce(Stream.of(new AbstractMap.SimpleEntry<>(key, node)), Stream::concat);
        case VALUE:
            return Stream.of(new AbstractMap.SimpleEntry<>(key, node));
        default:
            throw new IllegalArgumentException("Invalid node type.");
        }
    }
}
