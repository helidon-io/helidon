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

import java.util.AbstractMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;

/**
 * Implements {@link ObjectNode}.
 *
 * @deprecated use {@link ObjectNode#builder()} or {@link ObjectNode#wrap(ObjectNode)};
 *             this implementation will become package-private
 */
@Deprecated(since = "28.0.0", forRemoval = true)
public class ObjectNodeImpl extends AbstractMap<String, ConfigNode> implements ObjectNode, MergeableNode {

    private final Map<String, ConfigNode> members;
    private final Function<String, String> resolveTokenFunction;
    private final String value;
    private volatile String description;

    ObjectNodeImpl(Map<String, ConfigNode> members, Function<String, String> resolveTokenFunction) {
        this(members, resolveTokenFunction, null);
    }

    ObjectNodeImpl(Map<String, ConfigNode> members, Function<String, String> resolveTokenFunction, String value) {
        this(members, resolveTokenFunction, value, "N/A");
    }

    ObjectNodeImpl(Map<String, ? extends ConfigNode> members, Function<String, String> resolveTokenFunction,
                   String value, String description) {
        this.members = Collections.unmodifiableMap(new LinkedHashMap<>(members));
        this.resolveTokenFunction = resolveTokenFunction;
        this.value = value;
        this.description = Objects.requireNonNullElse(description, "N/A");
    }

    /**
     * Copies an object node into a mergeable object node, retaining source descriptions.
     *
     * @param objectNode original node
     * @return new mergeable object node
     */
    public static ObjectNodeImpl wrap(ObjectNode objectNode) {
        return wrap(objectNode, Function.identity());
    }

    /**
     * Copies an object node into a mergeable object node, retaining source descriptions.
     *
     * @param objectNode           original node
     * @param resolveTokenFunction a token resolver
     * @return new mergeable object node
     */
    public static ObjectNodeImpl wrap(ObjectNode objectNode, Function<String, String> resolveTokenFunction) {
        Objects.requireNonNull(objectNode);
        Objects.requireNonNull(resolveTokenFunction);
        return ObjectNodeBuilderImpl.create(objectNode, resolveTokenFunction)
                .value(objectNode.value())
                .description(objectNode.description())
                .build();
    }

    @Override
    public Set<Entry<String, ConfigNode>> entrySet() {
        return members.entrySet();
    }

    static ObjectNode create(Config config) {
        ObjectNode.Builder root = ObjectNode.builder();

        addObjectNode(root, config);

        return root.build();
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

    private static void addObjectNode(Builder parentBuilder, Config parent) {
        parent.asNodeList().ifPresent(it -> {
            for (Config child : it) {
                String escapedName = Config.Key.escapeName(child.name());
                switch (child.type()) {
                case OBJECT -> {
                    Builder childBuilder = ObjectNode.builder();
                    addObjectNode(childBuilder, child);
                    parentBuilder.addObject(escapedName, childBuilder.build());
                }
                case LIST -> {
                    ListNode.Builder childBuilder = ListNode.builder();
                    addListNode(childBuilder, child);
                    parentBuilder.addList(escapedName, childBuilder.build());
                }
                case VALUE -> {
                    parentBuilder.addValue(escapedName, child.asString().get());
                }
                default -> {
                    // do nothing
                }
                }
            }
        });
    }

    private static void addListNode(ListNode.Builder parentBuilder, Config parent) {
        parent.asNodeList().ifPresent(it -> {
            for (Config child : it) {
                switch (child.type()) {
                case OBJECT -> {
                    Builder childBuilder = ObjectNode.builder();
                    addObjectNode(childBuilder, child);
                    parentBuilder.addObject(childBuilder.build());
                }
                case LIST -> {
                    ListNode.Builder childBuilder = ListNode.builder();
                    addListNode(childBuilder, child);
                    parentBuilder.addList(childBuilder.build());
                }
                case VALUE -> {
                    parentBuilder.addValue(child.asString().get());
                }
                default -> {
                    // do nothing
                }
                }
            }
        });
    }

    private MergeableNode mergeWithValueNode(ValueNodeImpl node) {
        ObjectNodeBuilderImpl builder = ObjectNodeBuilderImpl.create(members, resolveTokenFunction);
        builder.value(node.value());
        builder.description(AbstractNodeBuilderImpl.mergedDescription(this, node));

        return builder.build();
    }

    private MergeableNode mergeWithObjectNode(ObjectNodeImpl node) {
        //merge object 'node' with object this object members
        ObjectNodeBuilderImpl builder = ObjectNodeBuilderImpl.create(members, resolveTokenFunction);
        node.forEach((name, member) -> builder.deepMerge(
                AbstractNodeBuilderImpl.MergingKey.of(ObjectNodeBuilderImpl.encodeDotsInTokenReferences(name)),
                AbstractNodeBuilderImpl.wrap(member)));

        node.value().or(this::value).ifPresent(builder::value);
        builder.description(AbstractNodeBuilderImpl.mergedDescription(this, node));

        return builder.build();
    }

    private MergeableNode mergeWithListNode(ListNodeImpl node) {
        final ObjectNodeBuilderImpl builder = ObjectNodeBuilderImpl.create(members, resolveTokenFunction);

        if (node.hasValue()) {
            builder.value(node.value());
        } else if (hasValue()) {
            builder.value(value);
        }
        builder.description(AbstractNodeBuilderImpl.mergedDescription(this, node));

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
     * @param description diagnostics description; for legacy compatibility, {@code null} resets it to {@code "N/A"}
     * @return this instance
     * @deprecated source descriptions are assigned by Helidon when loading nodes
     */
    @Deprecated(since = "28.0.0", forRemoval = true)
    public ObjectNodeImpl initDescription(String description) {
        // Retain the legacy nullable input during deprecation, without returning null from description().
        this.description = Objects.requireNonNullElse(description, "N/A");
        members.values().forEach(node -> initDescription(node, description));
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
