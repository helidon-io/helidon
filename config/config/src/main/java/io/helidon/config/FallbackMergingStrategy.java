/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.List;

import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigNode.ListNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigNode.ValueNode;

/**
 * An implementation of {@link ConfigSources.MergingStrategy} in which nodes
 * from a root earlier in the list of roots have higher priority than nodes from
 * a root later in the list.
 * <p>
 * The merged behavior is as if the resulting merged {@code Config}, when
 * resolving a value of a key, consults the {@code Config} roots in the order
 * they were passed to {@code merge}. As soon as it finds a {@code Config} tree
 * containing a value for the key is it immediately returns that value,
 * disregarding other later config roots.
 *
 * @see CompositeConfigSource
 */
final class FallbackMergingStrategy implements ConfigSources.MergingStrategy {

    @Override
    public ObjectNode merge(List<ObjectNode> rootNodes) {
        Collections.reverse(rootNodes);

        ObjectNode.Builder builder = ObjectNode.builder();

        rootNodes.forEach(root -> root.forEach((key, node) -> addNode(builder, key, node)));

        return builder.build();
    }

    private static ObjectNode.Builder addNode(ObjectNode.Builder builder, String key, ConfigNode node) {
        switch (node.getNodeType()) {
        case OBJECT:
            return builder.addObject(key, (ObjectNode) node);
        case LIST:
            return builder.addList(key, (ListNode) node);
        case VALUE:
            return builder.addValue(key, (ValueNode) node);
        default:
            throw new IllegalArgumentException("Unsupported node type: " + node.getClass().getName());
        }
    }

}
