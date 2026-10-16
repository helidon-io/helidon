/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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

package io.helidon.config.spi;

import java.util.AbstractMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.helidon.config.spi.ConfigNode.ListNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigNode.ValueNode;

/**
 * An implementation of {@link MergingStrategy} in which nodes
 * from a root earlier in the list of roots have higher priority than nodes from
 * a root later in the list.
 * <p>
 * The merged behavior is as if the resulting merged {@code Config}, when
 * resolving a value of a key, consults the {@code Config} roots in the order
 * they were passed to {@code merge}. As soon as it finds a {@code Config} tree
 * containing a value for the key is it immediately returns that value,
 * disregarding other later config roots.
 */
final class FallbackMergingStrategy implements MergingStrategy {

    @Override
    public ObjectNode merge(List<ObjectNode> rootNodes) {
        ObjectNode.Builder builder = ObjectNode.builder();
        String description = "N/A";
        boolean hasMembers = false;
        boolean hasValue = false;
        for (ObjectNode root : rootNodes.reversed()) {
            String rootDescription = Objects.requireNonNullElse(root.description(), "N/A");
            Optional<String> rootValue = root.value();
            if (rootValue.isPresent()) {
                builder.value(rootValue.get());
                hasValue = true;
                description = rootDescription;
            } else if (!hasValue) {
                if (!hasMembers) {
                    description = rootDescription;
                } else if (!root.isEmpty() && !description.equals(rootDescription)) {
                    description = "N/A";
                }
            }
            root.forEach((key, node) -> addNode(builder, key, node));
            hasMembers |= !root.isEmpty();
        }

        // Assign the root origin once, without repeatedly copying the growing merged tree.
        return ObjectNode.wrap(new MergedRoot(builder.build(), description));
    }

    private static void addNode(ObjectNode.Builder builder, String key, ConfigNode node) {
        switch (node.nodeType()) {
        case OBJECT:
            builder.addObject(key, (ObjectNode) node);
            break;
        case LIST:
            builder.addList(key, (ListNode) node);
            break;
        case VALUE:
            builder.addValue(key, (ValueNode) node);
            break;
        default:
            throw new IllegalArgumentException("Unsupported node type: " + node.getClass().getName());
        }
    }

    private static final class MergedRoot extends AbstractMap<String, ConfigNode> implements ObjectNode {
        private final ObjectNode node;
        private final String description;

        private MergedRoot(ObjectNode node, String description) {
            this.node = node;
            this.description = description;
        }

        @Override
        public Set<Entry<String, ConfigNode>> entrySet() {
            return node.entrySet();
        }

        @Override
        public Optional<String> value() {
            return node.value();
        }

        @Override
        public String description() {
            return description;
        }
    }
}
