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

package io.helidon.config.internal;

import java.util.Objects;

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
    public ValueNodeImpl(String value) {
        this.value = value;
        this.description = null;
    }

    @Override
    public String get() {
        return value;
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
        return new ValueNodeImpl(valueNode.get());
    }

    @Override
    public MergeableNode merge(MergeableNode node) {
        switch (node.getNodeType()) {
        case OBJECT:
            return node.merge(this);
        case LIST:
            return node.merge(this);
        case VALUE:
            return node;
        default:
            throw new IllegalArgumentException("Unsupported node type: " + node.getClass().getName());
        }
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

    public String getDescription() {
        return description;
    }

    @Override
    public boolean hasValue() {
        return true;
    }
}
