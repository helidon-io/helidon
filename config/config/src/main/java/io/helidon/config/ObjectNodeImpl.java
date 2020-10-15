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

import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import io.helidon.config.AbstractNodeBuilderImpl.MergingKey;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;

/**
 * Implements {@link ObjectNode}.
 */
public class ObjectNodeImpl extends AbstractMap<String, ConfigNode> implements ObjectNode, MergeableNode {

    private final Map<String, ConfigNode> members;
    private final Function<String, String> resolveTokenFunction;
    private final String value;
    private String description;

    ObjectNodeImpl(Map<String, ConfigNode> members, Function<String, String> resolveTokenFunction) {
        this(members, resolveTokenFunction, null);
    }

    ObjectNodeImpl(Map<String, ConfigNode> members, Function<String, String> resolveTokenFunction, String value) {
        this.members = members;
        this.resolveTokenFunction = resolveTokenFunction;
        this.value = value;
    }

    /**
     * Wraps value node into mergeable value node.
     *
     * @param objectNode original node
     * @return new instance of mergeable node or original node if already was mergeable.
     */
    public static ObjectNodeImpl wrap(ObjectNode objectNode) {
        return wrap(objectNode, Function.identity());
    }

    /**
     * Wraps value node into mergeable value node.
     *
     * @param objectNode           original node
     * @param resolveTokenFunction a token resolver
     * @return new instance of mergeable node or original node if already was mergeable.
     */
    public static ObjectNodeImpl wrap(ObjectNode objectNode, Function<String, String> resolveTokenFunction) {
        return ObjectNodeBuilderImpl.create(objectNode, resolveTokenFunction)
                .value(objectNode.value())
                .build();
    }

    @Override
    public Set<Entry<String, ConfigNode>> entrySet() {
        return members.entrySet();
    }

    static void initDescription(ConfigNode node, String description) {
        switch (node.nodeType()) {
        case OBJECT:
            ((ObjectNodeImpl) node).initDescription(description);
            break;
        case LIST:
            ((ListNodeImpl) node).initDescription(description);
            break;
        case VALUE:
            ((ValueNodeImpl) node).initDescription(description);
            break;
        default:
            throw new IllegalArgumentException("Unsupported node type: " + node.getClass().getName());
        }
    }

    @Override
    public MergeableNode merge(MergeableNode node) {
        switch (node.nodeType()) {
        case OBJECT:
            return mergeWithObjectNode((ObjectNodeImpl) node);
        case LIST:
            return mergeWithListNode((ListNodeImpl) node);
        case VALUE:
            return mergeWithValueNode((ValueNodeImpl) node);
        default:
            throw new IllegalArgumentException("Unsupported node type: " + node.getClass().getName());
        }
    }

    private MergeableNode mergeWithValueNode(ValueNodeImpl node) {
        ObjectNodeBuilderImpl builder = ObjectNodeBuilderImpl.create(members, resolveTokenFunction);
        builder.value(node.value());

        return builder.build();
    }

    private MergeableNode mergeWithObjectNode(ObjectNodeImpl node) {
        //merge object 'node' with object this object members
        ObjectNodeBuilderImpl builder = ObjectNodeBuilderImpl.create(members, resolveTokenFunction);
        node.forEach((name, member) -> builder.deepMerge(MergingKey.of(name), AbstractNodeBuilderImpl.wrap(member)));

        node.value().or(this::value).ifPresent(builder::value);

        return builder.build();
    }

    private MergeableNode mergeWithListNode(ListNodeImpl node) {
        final ObjectNodeBuilderImpl builder = ObjectNodeBuilderImpl.create(members, resolveTokenFunction);

        if (node.hasValue()) {
            builder.value(node.value());
        } else if (hasValue()) {
            builder.value(value);
        }

        AtomicInteger index = new AtomicInteger(0);
        node.forEach(configNode -> {
            int i = index.getAndIncrement();
            builder.merge(String.valueOf(i), (MergeableNode) configNode);
        });

        return builder.build();
    }

    @Override
    public String toString() {
        if (null == value) {
            return "ObjectNode[" + members.size() + "]" + super.toString();
        }
        return "ObjectNode(\"" + value + "\")[" + members.size() + "]" + super.toString();
    }

    /**
     * Initialize diagnostics description of source of node instance.
     *
     * @param description diagnostics description
     * @return this instance
     */
    public ObjectNodeImpl initDescription(String description) {
        this.description = description;
        members.values().forEach(node -> initDescription(node, description));
        return this;
    }

    /**
     * Description of this node.
     * @return node description
     */
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
