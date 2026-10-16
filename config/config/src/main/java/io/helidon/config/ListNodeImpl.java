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

package io.helidon.config;

import java.util.AbstractList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigNode.ListNode;

import static io.helidon.config.AbstractNodeBuilderImpl.formatFrom;

/**
 * Implements {@link ListNode}.
 */
class ListNodeImpl extends AbstractList<ConfigNode> implements ListNode, MergeableNode {

    private final List<MergeableNode> elements;
    private final String value;
    private volatile String description;

    ListNodeImpl(List<MergeableNode> elements, String value) {
        this(elements, value, "N/A");
    }

    ListNodeImpl(List<MergeableNode> elements, String value, String description) {
        this.elements = List.copyOf(elements);
        this.value = value;
        this.description = Objects.requireNonNullElse(description, "N/A");
    }

    /**
     * Wraps list node into mergeable list node.
     *
     * @param listNode original node
     * @return new mergeable node retaining the original source descriptions
     */
    static ListNodeImpl wrap(ListNode listNode, Function<String, String> resolveTokenFunction) {
        return ListNodeBuilderImpl.from(listNode, resolveTokenFunction)
                .value(listNode.value())
                .description(listNode.description())
                .build();
    }

    @Override
    public MergeableNode get(int index) {
        return elements.get(index);
    }

    @Override
    public int size() {
        return elements.size();
    }

    @Override
    public MergeableNode merge(MergeableNode node) {
        switch (node.nodeType()) {
        case OBJECT:
            return mergeWithObject((ObjectNodeImpl) node);
        case LIST:
            return mergeWithList((ListNodeImpl) node);
        case VALUE:
            return new ListNodeImpl(elements, ((ValueNodeImpl) node).get(), node.description());
        default:
            throw new IllegalArgumentException("Unsupported node type: " + node.getClass().getName());
        }
    }

    private MergeableNode mergeWithList(ListNodeImpl node) {
        if (node.hasValue()) {
            return node;
        }

        if (hasValue()) {
            return new ListNodeImpl(node.elements, value, description);
        }

        return node;
    }

    /**
     * Merges list with specified object.
     *
     * @param node object node to be merged with
     * @return new instance of node that contains merged list and object
     */
    private MergeableNode mergeWithObject(ObjectNodeImpl node) {
        Set<String> unprocessedPeerNames = new HashSet<>(node.keySet());

        final ListNodeBuilderImpl builder = new ListNodeBuilderImpl(Function.identity());

        if (node.hasValue()) {
            builder.value(node.value());
        } else if (hasValue()) {
            builder.value(value);
        }
        builder.description(AbstractNodeBuilderImpl.mergedDescription(this, node));

        for (int i = 0; i < elements.size(); i++) {
            MergeableNode element = elements.get(i);
            String name = String.valueOf(i);
            if (unprocessedPeerNames.contains(name)) {
                unprocessedPeerNames.remove(name);
                element = element.merge((MergeableNode) node.get(name));
            }
            builder.addNode(element);
        }
        if (!unprocessedPeerNames.isEmpty()) {
            throw new ConfigException(
                    String.format("Cannot merge OBJECT members %s%s with an LIST node%s.",
                                  unprocessedPeerNames,
                                  formatFrom(node.description()),
                                  formatFrom(description)));
        } else {
            return builder.build();
        }
    }

    @Override
    public String toString() {
        return "ListNode[" + elements.size() + "]" + super.toString() + "=" + value;
    }

    /**
     * Initialize diagnostics description of source of node instance.
     *
     * @param description diagnostics description; for legacy compatibility, {@code null} resets it to {@code "N/A"}
     * @return this instance
     * @deprecated source descriptions are assigned by Helidon when loading nodes
     */
    @Deprecated(since = "28.0.0", forRemoval = true)
    public ListNodeImpl initDescription(String description) {
        this.description = Objects.requireNonNullElse(description, "N/A");
        elements.forEach(node -> ObjectNodeImpl.initDescription(node, description));
        return this;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public boolean hasValue() {
        return null != value;
    }

    @Override
    public Optional<String> value() {
        return Optional.ofNullable(value);
    }
}
