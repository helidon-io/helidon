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

import java.util.Objects;
import java.util.Optional;

import io.helidon.config.spi.ConfigNode.ValueNode;

/**
 * Implements {@link ValueNode}.
 *
 * @deprecated use {@link ValueNode#create(String)}; this implementation will become package-private
 */
@Deprecated(since = "28.0.0", forRemoval = true)
public class ValueNodeImpl implements ValueNode, MergeableNode {

    private final String value;
    private volatile String description;

    /**
     * Initialize node.
     *
     * @param value node value
     */
    protected ValueNodeImpl(String value) {
        this(value, "N/A");
    }

    ValueNodeImpl(String value, String description) {
        this.value = Objects.requireNonNull(value);
        this.description = Objects.requireNonNullElse(description, "N/A");
    }

    @Override
    public Optional<String> value() {
        return Optional.of(value);
    }

    @Override
    public String get() {
        return value;
    }

    /**
     * Create a value node for the provided value.
     *
     * @param value value of this node
     * @return value node for the value
     */
    public static ValueNodeImpl create(String value) {
        return new ValueNodeImpl(value);
    }

    /**
     * Wraps value node into mergeable value node.
     *
     * @param valueNode original node
     * @return new mergeable node retaining the original source description
     */
    static ValueNodeImpl wrap(ValueNode valueNode) {
        return new ValueNodeImpl(valueNode.get(), valueNode.description());
    }

    @Override
    public MergeableNode merge(MergeableNode node) {
        switch (node.nodeType()) {
        case OBJECT:
            return mergeWithObjectNode((ObjectNodeImpl) node);
        case LIST:
            return mergeWithListNode((ListNodeImpl) node);
        case VALUE:
            return node;
        default:
            throw new IllegalArgumentException("Unsupported node type: " + node.getClass().getName());
        }
    }

    private MergeableNode mergeWithListNode(ListNodeImpl node) {
        if (node.hasValue()) {
            // will not merge, as the new node has priority over this node and we only have a value
            return node;
        }

        // and this will work fine, as the list node does not have a value, so we just add a value from this node
        return node.merge(this);
    }

    private MergeableNode mergeWithObjectNode(ObjectNodeImpl node) {
        // merge this value node with an object node
        ObjectNodeBuilderImpl builder = ObjectNodeBuilderImpl.create();

        node.forEach((name, member) -> builder
                .deepMerge(AbstractNodeBuilderImpl.MergingKey.of(name), AbstractNodeBuilderImpl.wrap(member)));

        node.value().or(this::value).ifPresent(builder::value);
        builder.description(AbstractNodeBuilderImpl.mergedDescription(this, node));

        return builder.build();
    }

    @Override
    public String toString() {
        return "\"" + value + "\"";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ValueNodeImpl valueNode = (ValueNodeImpl) o;
        return Objects.equals(value, valueNode.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    /**
     * Initialize diagnostics description of source of node instance.
     *
     * @param description diagnostics description; for legacy compatibility, {@code null} resets it to {@code "N/A"}
     * @return this instance
     * @deprecated source descriptions are assigned by Helidon when loading nodes
     */
    @Deprecated(since = "28.0.0", forRemoval = true)
    public ValueNodeImpl initDescription(String description) {
        // Retain the legacy nullable input during deprecation, without returning null from description().
        this.description = Objects.requireNonNullElse(description, "N/A");
        return this;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public boolean hasValue() {
        return true;
    }
}
