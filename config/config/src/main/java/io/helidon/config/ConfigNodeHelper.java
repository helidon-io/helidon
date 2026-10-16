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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigNode.ListNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigNode.ValueNode;

/**
 * Copies source data into runtime-owned nodes, retaining known origins and supplying missing source descriptions.
 */
@SuppressWarnings("removal")
final class ConfigNodeHelper {
    private ConfigNodeHelper() {
    }

    static MergeableNode fromSource(ConfigNode node, String sourceDescription) {
        String description = node.description();
        if (description == null || description.equals("N/A")) {
            description = Objects.requireNonNullElse(sourceDescription, "N/A");
        }

        return switch (node.nodeType()) {
        case OBJECT -> {
            ObjectNode object = (ObjectNode) node;
            Map<String, ConfigNode> members = new LinkedHashMap<>();
            object.forEach((name, child) -> members.put(name, fromSource(child, sourceDescription)));
            yield new ObjectNodeImpl(Collections.unmodifiableMap(members),
                                     Function.identity(),
                                     object.value().orElse(null),
                                     description);
        }
        case LIST -> {
            ListNode list = (ListNode) node;
            List<MergeableNode> elements = list.stream()
                    .map(child -> fromSource(child, sourceDescription))
                    .toList();
            yield new ListNodeImpl(elements, list.value().orElse(null), description);
        }
        case VALUE -> new ValueNodeImpl(((ValueNode) node).get(), description);
        };
    }
}
