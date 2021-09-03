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

import java.util.Objects;
import java.util.Optional;

import io.helidon.config.spi.ConfigNode.ValueNode;

/**
 * Implements {@link ValueNode}.
 */
public class ValueNodeImpl implements ValueNode, MergeableNode {

    private final String value;
    private String description;

    /**
     * Initialize node.
     *
     * @param value node value
     */
    protected ValueNodeImpl(String value) {
        this.value = value;
        this.description = null;
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
     * @return new instance of mergeable node or original node if already was mergeable.
     */
    static ValueNodeImpl wrap(ValueNode valueNode) {
        if (valueNode instanceof ValueNodeImpl) {
            return (ValueNodeImpl) valueNode;
        }
        return ValueNodeImpl.create(valueNode.get());
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
     * @param description diagnostics description
     * @return this instance
     */
    public ValueNodeImpl initDescription(String description) {
        this.description = description;
        return this;
    }

    /**
     * Description of this node.
     * @return description of the node.
     */
    public String description() {
        return description;
    }

    @Override
    public boolean hasValue() {
        return true;
    }
}
