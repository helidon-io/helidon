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

package io.helidon.config.spi;

import java.util.Collections;
import java.util.List;

import io.helidon.config.ConfigNode;
import io.helidon.config.ConfigNode.ObjectNode;

public class FallbackMergingStrategy implements MergingStrategy {
    @Override
    public ObjectNode merge(List<ConfigNode> rootNodes) {
        Collections.reverse(rootNodes);

        ObjectNode.Builder builder = ObjectNode.builder();

        for (ConfigNode rootNode : rootNodes) {
            // override possible direct value
            rootNode.get().ifPresent(builder::value);

            switch (rootNode.nodeType()) {
            case OBJECT:
                ((ObjectNode)rootNode).forEach((key, node) -> addNode(builder, key, node));
                break;
            case LIST:
                ConfigNode.ListNode listNode = (ConfigNode.ListNode) rootNode;
                int index = 0;
                for (ConfigNode configNode : listNode) {
                    addNode(builder, String.valueOf(index), configNode);
                }
                break;
            default:
                // do nothing
            }
        }

        return builder.build();
    }

    private static void addNode(ObjectNode.Builder builder, String key, ConfigNode node) {
        switch (node.nodeType()) {
        case OBJECT:
            builder.addObject(key, (ObjectNode) node);
            break;
        case LIST:
            builder.addList(key, (ConfigNode.ListNode) node);
            break;
        case VALUE:
            builder.addValue(key, (ConfigNode.ValueNode) node);
            break;
        default:
            throw new IllegalArgumentException("Unsupported node type: " + node.getClass().getName());
        }
    }
}
